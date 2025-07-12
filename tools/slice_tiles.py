#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Generates one MBTiles file per map floor in parallel.
This version uses NumPy for a significant speedup in the transparency step.
"""

import os
import shutil
import sqlite3
import numpy as np
from datetime import datetime
from PIL import Image, ImageDraw
from concurrent.futures import ProcessPoolExecutor

Image.MAX_IMAGE_PIXELS = None

# --- Configuration ---
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SOURCE_DIR = os.path.join(PROJECT_ROOT, 'tools', 'map-dumper', 'output')
SOURCE_IMAGES = [f'img-{i}.png' for i in range(4)]
OUTPUT_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets', 'map_tiles_temp')
ASSETS_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets')
TILE_SIZE = 256
CANVAS_SIZE = 65536
NATIVE_ZOOM = 8
MIN_ZOOM = 3

BLANK_TILE_F0 = Image.new('RGBA', (TILE_SIZE, TILE_SIZE), (0, 0, 0, 255))
BLANK_TILE_FX = Image.new('RGBA', (TILE_SIZE, TILE_SIZE), (0, 0, 0, 0))


def log_time(message):
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] [PID:{os.getpid()}] {message}")


def make_background_transparent(img):
    """
    Uses NumPy to make the black background of an image transparent.
    This is significantly faster than ImageDraw.floodfill.
    """
    # Convert the PIL image to a NumPy array
    data = np.array(img)
    
    # Get the RGB channels
    r, g, b, a = data.T
    
    # Find all pixels where red, green, and blue are all 0 (black)
    black_areas = (r == 0) & (g == 0) & (b == 0)
    
    # Set the alpha channel to 0 (transparent) for those pixels
    data[..., -1][black_areas.T] = 0
    
    # Convert the NumPy array back to a PIL image
    return Image.fromarray(data)


def is_tile_empty(tile_image, floor):
    extrema = tile_image.getextrema()
    if floor == 0:
        is_black_r, is_black_g, is_black_b = extrema[0] == (0, 0), extrema[1] == (0, 0), extrema[2] == (0, 0)
        return is_black_r and is_black_g and is_black_b
    else:
        return extrema[3] == (0, 0) if len(extrema) > 3 else False


def generate_base_tiles(padded_image, floor):
    zoom = NATIVE_ZOOM
    num_tiles = 2**zoom
    log_time(f"Floor {floor}: Generating base tiles for zoom level {zoom}...")
    zoom_dir = os.path.join(OUTPUT_DIR, str(floor), str(zoom))
    os.makedirs(zoom_dir, exist_ok=True)
    for x in range(num_tiles):
        col_dir = os.path.join(zoom_dir, str(x))
        for y in range(num_tiles):
            tms_y = (2**zoom - 1) - y
            box = (x * TILE_SIZE, y * TILE_SIZE, (x + 1) * TILE_SIZE, (y + 1) * TILE_SIZE)
            tile = padded_image.crop(box)
            if not is_tile_empty(tile, floor):
                if not os.path.exists(col_dir): os.makedirs(col_dir)
                tile.save(os.path.join(col_dir, f"{tms_y}.png"), 'PNG')


def generate_overview_tiles(floor):
    log_time(f"Floor {floor}: Generating overview tiles...")
    floor_dir = os.path.join(OUTPUT_DIR, str(floor))
    blank_tile = BLANK_TILE_F0 if floor == 0 else BLANK_TILE_FX

    for zoom in range(NATIVE_ZOOM - 1, MIN_ZOOM - 1, -1):
        source_zoom_dir = os.path.join(floor_dir, str(zoom + 1))
        zoom_dir = os.path.join(floor_dir, str(zoom))
        os.makedirs(zoom_dir, exist_ok=True)

        for x in range(2**zoom):
            col_dir = os.path.join(zoom_dir, str(x))
            for y in range(2**zoom):
                parent_tms_y = (2**zoom - 1) - y
                child_x1, child_x2 = 2 * x, 2 * x + 1
                child_cartesian_y1 = 2 * y
                child_cartesian_y2 = 2 * y + 1
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
                
                if not is_tile_empty(overview_tile, floor):
                    if not os.path.exists(col_dir): os.makedirs(col_dir)
                    overview_tile.save(os.path.join(col_dir, f"{parent_tms_y}.png"), 'PNG')


def create_mbtiles_for_floor(floor_num):
    mbtiles_filename = f'map_floor_{floor_num}.mbtiles'
    mbtiles_filepath = os.path.join(ASSETS_DIR, mbtiles_filename)
    source_layer_dir = os.path.join(OUTPUT_DIR, str(floor_num))
    
    log_time(f"Floor {floor_num}: Creating '{mbtiles_filename}'...")
    if os.path.exists(mbtiles_filepath): os.remove(mbtiles_filepath)

    db = sqlite3.connect(mbtiles_filepath)
    cursor = db.cursor()
    cursor.execute('CREATE TABLE metadata (name text, value text);')
    cursor.execute('CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);')
    cursor.execute('CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);')
    
    metadata = [
        ('name', f'OSRS Map - Floor {floor_num}'), ('format', 'png'),
        ('bounds', '-180.0,-85.0,180.0,85.0'), ('type', 'overlay'), ('version', '1.3'),
        ('description', f'Tiles for OSRS floor {floor_num}'), ('minzoom', str(MIN_ZOOM)),
        ('maxzoom', str(NATIVE_ZOOM)),
    ]
    cursor.executemany('INSERT INTO metadata (name, value) VALUES (?, ?)', metadata)
    
    tiles_to_insert = []
    for zoom_str in os.listdir(source_layer_dir):
        zoom_dir = os.path.join(source_layer_dir, zoom_str)
        for x_str in os.listdir(zoom_dir):
            x_dir = os.path.join(zoom_dir, x_str)
            for y_filename in os.listdir(x_dir):
                if y_filename.endswith('.png'):
                    with open(os.path.join(x_dir, y_filename), 'rb') as f: tile_data = f.read()
                    tiles_to_insert.append((int(zoom_str), int(x_str), int(os.path.splitext(y_filename)[0]), tile_data))

    log_time(f"Floor {floor_num}: Inserting {len(tiles_to_insert)} tiles into the database...")
    cursor.executemany('INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)', tiles_to_insert)
    db.commit()
    db.close()
    log_time(f"Floor {floor_num}: Finished creating '{mbtiles_filename}'.")


def process_floor(floor):
    try:
        log_time(f"--- Processing Floor {floor} ---")
        image_name = SOURCE_IMAGES[floor]
        source_image_path = os.path.join(SOURCE_DIR, image_name)
        if not os.path.exists(source_image_path):
            log_time(f"FATAL: Source image not found at '{source_image_path}'")
            return

        source_image = Image.open(source_image_path).convert('RGBA')

        if floor > 0:
            log_time(f"Floor {floor}: Making background transparent using NumPy (fast)...")
            # This is the new, fast method.
            source_image = make_background_transparent(source_image)
        
        padded_canvas = Image.new('RGBA', (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
        padded_canvas.paste(source_image, (0, 0))
        source_image.close()

        generate_base_tiles(padded_canvas, floor)
        generate_overview_tiles(floor)
        padded_canvas.close()
        
        create_mbtiles_for_floor(floor)
        return f"Floor {floor} processed successfully."
    except Exception as e:
        log_time(f"Floor {floor} FAILED with error: {e}")
        raise


def main():
    log_time("Starting parallel map tile generation process.")
    
    if os.path.exists(OUTPUT_DIR): shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)
    for floor_num in range(len(SOURCE_IMAGES)):
        mbtiles_filepath = os.path.join(ASSETS_DIR, f'map_floor_{floor_num}.mbtiles')
        if os.path.exists(mbtiles_filepath): os.remove(mbtiles_filepath)

    with ProcessPoolExecutor() as executor:
        results = executor.map(process_floor, range(len(SOURCE_IMAGES)))
        for result in results:
            log_time(result)

    log_time("Cleaning up temporary tile directory...")
    shutil.rmtree(OUTPUT_DIR)
    log_time("Process finished. All .mbtiles files have been created.")


if __name__ == "__main__":
    main()
