#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Generates one MBTiles file per map floor using only the Python standard library.

This script is dependency-free (besides Pillow) and creates a separate,
standard-compliant .mbtiles file for each floor. This is a robust method
for handling multiple map layers.

The script reads existing tile images from the 'map_tiles_temp' directory
if it exists, saving regeneration time.
"""

import os
import shutil
import sqlite3
from datetime import datetime

# --- Configuration ---
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
TEMP_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets', 'map_tiles_temp')
ASSETS_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets')
MIN_ZOOM = 3
NATIVE_ZOOM = 8


def log_time(message):
    """Prints a message with a timestamp."""
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}")


def create_mbtiles_db(db_path, floor_num):
    """Initializes a new MBTiles SQLite database."""
    # Delete the old file if it exists
    if os.path.exists(db_path):
        os.remove(db_path)
        
    db = sqlite3.connect(db_path)
    cursor = db.cursor()
    cursor.execute('CREATE TABLE metadata (name text, value text);')
    cursor.execute('CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);')
    cursor.execute('CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);')
    
    # Add required metadata
    metadata = [
        ('name', f'OSRS Map - Floor {floor_num}'),
        ('format', 'png'),
        ('bounds', '-180.0,-85.0,180.0,85.0'), # Generic bounds
        ('type', 'overlay'),
        ('version', '1.1'), # Updated version
        ('description', f'Tiles for OSRS floor {floor_num}'),
        ('minzoom', str(MIN_ZOOM)),
        ('maxzoom', str(NATIVE_ZOOM)),
    ]
    cursor.executemany('INSERT INTO metadata (name, value) VALUES (?, ?)', metadata)
    db.commit()
    return db


def main():
    """Main execution function."""
    log_time("Starting MBTiles creation process from existing tiles.")

    if not os.path.exists(TEMP_DIR):
        log_time(f"FATAL: Temporary tile directory '{TEMP_DIR}' not found.")
        log_time("A previous successful tile generation run is required.")
        return

    log_time(f"Reading existing tile images from '{TEMP_DIR}'")

    # Assuming there are 4 floors based on the project description
    for floor_num in range(4):
        # This is the corrected line: Use the number directly as the directory name.
        layer_dir_name = str(floor_num)
        source_layer_dir = os.path.join(TEMP_DIR, layer_dir_name)
        mbtiles_filename = f'map_floor_{floor_num}.mbtiles'
        mbtiles_filepath = os.path.join(ASSETS_DIR, mbtiles_filename)

        if not os.path.exists(source_layer_dir):
            log_time(f"WARNING: Source directory '{source_layer_dir}' not found. Skipping floor {floor_num}.")
            continue
        
        log_time(f"--- Creating '{mbtiles_filename}' ---")
        
        db = create_mbtiles_db(mbtiles_filepath, floor_num)
        cursor = db.cursor()
        
        tiles_to_insert = []
        for zoom_str in os.listdir(source_layer_dir):
            zoom_dir = os.path.join(source_layer_dir, zoom_str)
            if not os.path.isdir(zoom_dir): continue
            
            for x_str in os.listdir(zoom_dir):
                x_dir = os.path.join(zoom_dir, x_str)
                if not os.path.isdir(x_dir): continue

                for y_filename in os.listdir(x_dir):
                    if y_filename.endswith('.png'):
                        try:
                            with open(os.path.join(x_dir, y_filename), 'rb') as f:
                                tile_data = f.read()
                            
                            zoom = int(zoom_str)
                            x = int(x_str)
                            # The filename is the TMS Y-coordinate.
                            y = int(os.path.splitext(y_filename)[0])
                            
                            tiles_to_insert.append((zoom, x, y, tile_data))
                        except (IOError, ValueError) as e:
                            log_time(f"Could not process tile {os.path.join(x_dir, y_filename)}: {e}")

        if not tiles_to_insert:
            log_time(f"No tiles found for floor {floor_num}. Skipping database insertion.")
            db.close()
            continue

        log_time(f"Inserting {len(tiles_to_insert)} tiles into the database for floor {floor_num}...")
        cursor.executemany('INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)', tiles_to_insert)
        db.commit()
        db.close()
        log_time(f"--- Finished creating '{mbtiles_filename}' ---")

    log_time("Process finished. All .mbtiles files have been created in the assets folder.")
    log_time("You may now want to delete the temporary directory: rm -rf " + TEMP_DIR)


if __name__ == "__main__":
    main()
