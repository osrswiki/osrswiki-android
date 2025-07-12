#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Test script to generate a single MBTiles file from a cropped image for rapid
end-to-end testing in the app.
"""

import os
import shutil
import math
import sqlite3
from datetime import datetime
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

# --- Test Configuration ---
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SOURCE_IMAGE_PATH = os.path.join(PROJECT_ROOT, 'tools', 'map-dumper', 'output', 'img-0-test.png')
OUTPUT_DIR = os.path.join(PROJECT_ROOT, 'tools', 'test_tile_output')
ASSETS_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets')
TILE_SIZE = 256
MIN_ZOOM = 0

with Image.open(SOURCE_IMAGE_PATH) as img:
    source_width, source_height = img.size

max_tiles_dim = math.ceil(max(source_width, source_height) / TILE_SIZE)
NATIVE_ZOOM = (int(max_tiles_dim) - 1).bit_length() if max_tiles_dim > 0 else 0
CANVAS_SIZE = (2**NATIVE_ZOOM) * TILE_SIZE


def log_time(message):
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}")


def generate_base_tiles(padded_image):
    zoom = NATIVE_ZOOM
    log_time(f"Generating base tiles for zoom level {zoom}...")
    zoom_dir = os.path.join(OUTPUT_DIR, str(zoom))
    os.makedirs(zoom_dir, exist_ok=True)
    for x in range(2**zoom):
        col_dir = os.path.join(zoom_dir, str(x))
        for y in range(2**zoom):
            tms_y = (2**zoom - 1) - y
            box = (x * TILE_SIZE, y * TILE_SIZE, (x + 1) * TILE_SIZE, (y + 1) * TILE_SIZE)
            tile = padded_image.crop(box)
            if tile.getextrema() != ((0, 0), (0, 0), (0, 0), (0, 0)):
                if not os.path.exists(col_dir): os.makedirs(col_dir)
                tile.save(os.path.join(col_dir, f"{tms_y}.png"), 'PNG')


def generate_overview_tiles():
    log_time(f"Generating overview tiles...")
    blank_tile = Image.new('RGBA', (TILE_SIZE, TILE_SIZE), (0, 0, 0, 255))
    for zoom in range(NATIVE_ZOOM - 1, MIN_ZOOM - 1, -1):
        source_zoom_dir = os.path.join(OUTPUT_DIR, str(zoom + 1))
        zoom_dir = os.path.join(OUTPUT_DIR, str(zoom))
        os.makedirs(zoom_dir, exist_ok=True)
        for x in range(2**zoom):
            col_dir = os.path.join(zoom_dir, str(x))
            for y in range(2**zoom):
                parent_tms_y = (2**zoom - 1) - y
                child_x1, child_x2 = 2 * x, 2 * x + 1
                child_cartesian_y1, child_cartesian_y2 = 2 * y, 2 * y + 1
                child_tms_y1 = (2**(zoom + 1) - 1) - child_cartesian_y1
                child_tms_y2 = (2**(zoom + 1) - 1) - child_cartesian_y2
                
                combined_image = Image.new('RGBA', (TILE_SIZE * 2, TILE_SIZE * 2))
                child_paths = [
                    os.path.join(source_zoom_dir, str(child_x1), f"{child_tms_y1}.png"),
                    os.path.join(source_zoom_dir, str(child_x2), f"{child_tms_y1}.png"),
                    os.path.join(source_zoom_dir, str(child_x1), f"{child_tms_y2}.png"),
                    os.path.join(source_zoom_dir, str(child_x2), f"{child_tms_y2}.png")
                ]
                paste_positions = [(0, 0), (TILE_SIZE, 0), (0, TILE_SIZE), (TILE_SIZE, TILE_SIZE)]
                
                for i, path in enumerate(child_paths):
                    try:
                        with Image.open(path) as img:
                            combined_image.paste(img, paste_positions[i])
                    except FileNotFoundError:
                        combined_image.paste(blank_tile, paste_positions[i])
                
                overview_tile = combined_image.resize((TILE_SIZE, TILE_SIZE), Image.Resampling.LANCZOS)
                
                if overview_tile.getextrema()[:3] != ((0, 0), (0, 0), (0, 0)):
                    if not os.path.exists(col_dir): os.makedirs(col_dir)
                    overview_tile.save(os.path.join(col_dir, f"{parent_tms_y}.png"), 'PNG')


def create_test_mbtiles():
    mbtiles_filename = 'map_floor_0_test.mbtiles'
    mbtiles_filepath = os.path.join(ASSETS_DIR, mbtiles_filename)
    
    log_time(f"--- Creating '{mbtiles_filename}' ---")
    if os.path.exists(mbtiles_filepath): os.remove(mbtiles_filepath)

    db = sqlite3.connect(mbtiles_filepath)
    cursor = db.cursor()
    cursor.execute('CREATE TABLE metadata (name text, value text);')
    cursor.execute('CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);')
    cursor.execute('CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);')
    
    metadata = [('name', 'Test Map'), ('format', 'png'), ('bounds', '-180,-85,180,85'), ('minzoom', str(MIN_ZOOM)), ('maxzoom', str(NATIVE_ZOOM))]
    cursor.executemany('INSERT INTO metadata (name, value) VALUES (?, ?)', metadata)
    
    tiles_to_insert = []
    for zoom_str in os.listdir(OUTPUT_DIR):
        zoom_dir = os.path.join(OUTPUT_DIR, zoom_str)
        for x_str in os.listdir(zoom_dir):
            x_dir = os.path.join(zoom_dir, x_str)
            for y_filename in os.listdir(x_dir):
                if y_filename.endswith('.png'):
                    with open(os.path.join(x_dir, y_filename), 'rb') as f: tile_data = f.read()
                    tiles_to_insert.append((int(zoom_str), int(x_str), int(os.path.splitext(y_filename)[0]), tile_data))
    
    log_time(f"Inserting {len(tiles_to_insert)} tiles into the test database...")
    cursor.executemany('INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)', tiles_to_insert)
    db.commit()
    db.close()
    log_time(f"--- Finished creating '{mbtiles_filename}' ---")


def main():
    log_time("Starting test tile generation for cropped image.")
    if os.path.exists(OUTPUT_DIR): shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)

    source_image = Image.open(SOURCE_IMAGE_PATH).convert('RGBA')
    
    padded_canvas = Image.new('RGBA', (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    padded_canvas.paste(source_image, (0, 0))
    source_image.close()

    generate_base_tiles(padded_canvas)
    generate_overview_tiles()
    create_test_mbtiles()
    
    padded_canvas.close()
    shutil.rmtree(OUTPUT_DIR) # Clean up the temp pngs
    log_time(f"Test generation finished. '{os.path.join(ASSETS_DIR, 'map_floor_0_test.mbtiles')}' has been created.")


if __name__ == "__main__":
    main()
