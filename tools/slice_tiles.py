#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Generates one MBTiles file per map floor in parallel.
This version dynamically calculates zoom levels and uses nearest-neighbor
resampling to preserve a pixelated art style.
"""

import os
import shutil
import sqlite3
import numpy as np
import math
from datetime import datetime
from PIL import Image
from concurrent.futures import ProcessPoolExecutor

Image.MAX_IMAGE_PIXELS = None

# --- Configuration ---
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SOURCE_DIR = os.path.join(PROJECT_ROOT, 'tools', 'map-dumper', 'output')
SOURCE_IMAGES = [f'img-{i}.png' for i in range(4)]
TEMP_OUTPUT_DIR = os.path.join(PROJECT_ROOT, 'tools', 'map_tiles_temp')
ASSETS_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets')

# --- Tile Generation Settings ---
# Set the desired tile size. 4096px provides excellent detail for high-res displays.
TILE_SIZE = 4096
# Generate a complete tile pyramid down to a single tile.
MIN_ZOOM = 0


def log_time(message):
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] [PID:{os.getpid()}] {message}")


def make_background_transparent(img):
    """
    Uses NumPy to make the black background of an image transparent.
    This is significantly faster than ImageDraw.floodfill.
    """
    data = np.array(img)
    r, g, b, a = data.T
    black_areas = (r == 0) & (g == 0) & (b == 0)
    data[..., -1][black_areas.T] = 0
    return Image.fromarray(data)


def is_tile_empty(tile_image, floor):
    """Checks if a tile is entirely black (for floor 0) or transparent."""
    extrema = tile_image.getextrema()
    if floor == 0:
        # For floor 0, the background is solid black.
        is_black_r, is_black_g, is_black_b = extrema[0] == (0, 0), extrema[1] == (0, 0), extrema[2] == (0, 0)
        return is_black_r and is_black_g and is_black_b
    else:
        # For other floors, the background is transparent.
        # An all-zero alpha channel indicates an empty tile.
        return extrema[3] == (0, 0) if len(extrema) > 3 else True


def generate_base_tiles(padded_image, floor, native_zoom):
    """Generates the highest-resolution tiles from the source image."""
    log_time(f"Floor {floor}: Generating base tiles for zoom level {native_zoom}...")
    zoom_dir = os.path.join(TEMP_OUTPUT_DIR, str(floor), str(native_zoom))
    os.makedirs(zoom_dir, exist_ok=True)

    for x in range(2**native_zoom):
        col_dir = os.path.join(zoom_dir, str(x))
        for y in range(2**native_zoom):
            tms_y = (2**native_zoom - 1) - y
            box = (x * TILE_SIZE, y * TILE_SIZE, (x + 1) * TILE_SIZE, (y + 1) * TILE_SIZE)
            tile = padded_image.crop(box)
            if not is_tile_empty(tile, floor):
                if not os.path.exists(col_dir):
                    os.makedirs(col_dir)
                tile.save(os.path.join(col_dir, f"{tms_y}.png"), 'PNG')


def generate_overview_tiles(floor, native_zoom):
    """Generates downsampled overview tiles for lower zoom levels."""
    log_time(f"Floor {floor}: Generating overview tiles...")
    floor_dir = os.path.join(TEMP_OUTPUT_DIR, str(floor))
    blank_tile = Image.new('RGBA', (TILE_SIZE, TILE_SIZE), (0, 0, 0, 0 if floor > 0 else 255))

    for zoom in range(native_zoom - 1, MIN_ZOOM - 1, -1):
        source_zoom_dir = os.path.join(floor_dir, str(zoom + 1))
        zoom_dir = os.path.join(floor_dir, str(zoom))
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

                has_content = False
                for i, path in enumerate(child_paths):
                    try:
                        with Image.open(path) as img:
                            combined_image.paste(img, paste_positions[i])
                            has_content = True
                    except FileNotFoundError:
                        combined_image.paste(blank_tile, paste_positions[i])

                if has_content:
                    # Use NEAREST resampling to maintain sharp, pixelated style.
                    overview_tile = combined_image.resize((TILE_SIZE, TILE_SIZE), Image.Resampling.NEAREST)
                    if not is_tile_empty(overview_tile, floor):
                        if not os.path.exists(col_dir):
                            os.makedirs(col_dir)
                        overview_tile.save(os.path.join(col_dir, f"{parent_tms_y}.png"), 'PNG')


def create_mbtiles_for_floor(floor_num, native_zoom):
    """Packages all generated tiles for a floor into a single .mbtiles file."""
    mbtiles_filename = f'map_floor_{floor_num}.mbtiles'
    mbtiles_filepath = os.path.join(ASSETS_DIR, mbtiles_filename)
    source_layer_dir = os.path.join(TEMP_OUTPUT_DIR, str(floor_num))

    log_time(f"Floor {floor_num}: Creating '{mbtiles_filename}'...")
    if os.path.exists(mbtiles_filepath):
        os.remove(mbtiles_filepath)

    db = sqlite3.connect(mbtiles_filepath)
    cursor = db.cursor()
    cursor.execute('CREATE TABLE metadata (name text, value text);')
    cursor.execute('CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);')
    cursor.execute('CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);')

    metadata = [
        ('name', f'OSRS Map - Floor {floor_num}'), ('format', 'png'),
        ('bounds', '-180.0,-85.0,180.0,85.0'), ('type', 'overlay'), ('version', '1.4'),
        ('description', f'Tiles for OSRS floor {floor_num}'), ('minzoom', str(MIN_ZOOM)),
        ('maxzoom', str(native_zoom)),
    ]
    cursor.executemany('INSERT INTO metadata (name, value) VALUES (?, ?)', metadata)

    tiles_to_insert = []
    for zoom_str in os.listdir(source_layer_dir):
        zoom_dir = os.path.join(source_layer_dir, zoom_str)
        for x_str in os.listdir(zoom_dir):
            x_dir = os.path.join(zoom_dir, x_str)
            for y_filename in os.listdir(x_dir):
                if y_filename.endswith('.png'):
                    with open(os.path.join(x_dir, y_filename), 'rb') as f:
                        tile_data = f.read()
                    tiles_to_insert.append((int(zoom_str), int(x_str), int(os.path.splitext(y_filename)[0]), tile_data))

    log_time(f"Floor {floor_num}: Inserting {len(tiles_to_insert)} tiles into the database...")
    cursor.executemany('INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)', tiles_to_insert)
    db.commit()
    db.close()
    log_time(f"Floor {floor_num}: Finished creating '{mbtiles_filename}'.")


def process_floor(floor):
    """The main processing pipeline for a single map floor."""
    try:
        log_time(f"--- Processing Floor {floor} ---")
        image_name = SOURCE_IMAGES[floor]
        source_image_path = os.path.join(SOURCE_DIR, image_name)
        if not os.path.exists(source_image_path):
            log_time(f"FATAL: Source image not found at '{source_image_path}'")
            return

        with Image.open(source_image_path) as source_image:
            source_image = source_image.convert('RGBA')
            if floor > 0:
                log_time(f"Floor {floor}: Making background transparent...")
                source_image = make_background_transparent(source_image)
            
            # Dynamically calculate zoom and canvas size for this specific image
            source_width, source_height = source_image.size
            max_tiles_dim = math.ceil(max(source_width, source_height) / TILE_SIZE)
            native_zoom = (int(max_tiles_dim) - 1).bit_length() if max_tiles_dim > 0 else 0
            canvas_size = (2**native_zoom) * TILE_SIZE
            log_time(f"Floor {floor}: Source Dims={source_width}x{source_height}, Tile Size={TILE_SIZE}px, Native Zoom={native_zoom}, Canvas={canvas_size}px")
            
            padded_canvas = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
            padded_canvas.paste(source_image, (0, 0))

        generate_base_tiles(padded_canvas, floor, native_zoom)
        generate_overview_tiles(floor, native_zoom)
        create_mbtiles_for_floor(floor, native_zoom)
        
        return f"Floor {floor} processed successfully."
    except Exception as e:
        log_time(f"Floor {floor} FAILED with error: {e}")
        import traceback
        traceback.print_exc()
        raise


def main():
    log_time("Starting parallel map tile generation process.")

    if os.path.exists(TEMP_OUTPUT_DIR):
        shutil.rmtree(TEMP_OUTPUT_DIR)
    os.makedirs(TEMP_OUTPUT_DIR)

    # Clean up old mbtiles files before starting
    for floor_num in range(len(SOURCE_IMAGES)):
        mbtiles_filepath = os.path.join(ASSETS_DIR, f'map_floor_{floor_num}.mbtiles')
        if os.path.exists(mbtiles_filepath):
            os.remove(mbtiles_filepath)

    with ProcessPoolExecutor() as executor:
        results = executor.map(process_floor, range(len(SOURCE_IMAGES)))
        for result in results:
            log_time(result)

    log_time("Cleaning up temporary tile directory...")
    shutil.rmtree(TEMP_OUTPUT_DIR)
    log_time("Process finished. All .mbtiles files have been created.")


if __name__ == "__main__":
    main()
