#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Generates a complete, optimized map tile pyramid for a Leaflet.js map viewer.

This script implements a "padding" strategy to handle irregularly sized source
images. It first creates a square canvas that is a power of two and large
enough to contain the source image. The source image is pasted onto this
canvas, and the resulting padded image is then tiled. This ensures that the
generated tile set is complete (not sparse) and aligns with the coordinate
system expected by standard tiling libraries like Leaflet.

This version is fully automated and optimized:
- It processes all 4 map floors.
- It automatically removes the background from upper-floor images using a
  flood fill algorithm, making them transparent for overlaying.
- It prunes empty tiles (solid black for floor 0, fully transparent for
  upper floors) to significantly reduce the final asset size.
- It generates a metadata file with the total uncompressed size for a
  determinate in-app loading bar.
- It packages the final, pruned tile set into an ordered zip archive.
"""

import os
import shutil
import zipfile
from datetime import datetime
from PIL import Image, ImageDraw

# Disable the decompression bomb check as we are intentionally processing
# a very large image. This must be set before any image is opened.
Image.MAX_IMAGE_PIXELS = None

# --- Configuration ---
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SOURCE_DIR = os.path.join(PROJECT_ROOT, 'tools', 'map-dumper', 'output')
SOURCE_IMAGES = [f'img-{i}.png' for i in range(4)]
OUTPUT_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets', 'map_tiles')
ZIP_FILE_PATH = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets', 'map_tiles.zip')
TILE_SIZE = 256
CANVAS_SIZE = 65536
NATIVE_ZOOM = 8
MIN_ZOOM = 3


def log_time(message):
    """Prints a message with a timestamp."""
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}")


def is_tile_empty(tile_image, floor):
    """
    Checks if a tile is empty. The condition for 'empty' depends on the floor.
    - Floor 0: Empty if solid black.
    - Floors > 0: Empty if fully transparent.
    """
    extrema = tile_image.getextrema()
    if floor == 0:
        # For floor 0, check if the tile is solid black (R, G, B are all 0).
        is_black_r = extrema[0] == (0, 0)
        is_black_g = extrema[1] == (0, 0)
        is_black_b = extrema[2] == (0, 0)
        return is_black_r and is_black_g and is_black_b
    else:
        # For upper floors, check if the tile is fully transparent (alpha is 0).
        is_transparent = extrema[3] == (0, 0)
        return is_transparent


def generate_base_tiles(padded_image, floor):
    """Phase 2: Generate and prune tiles for the native zoom level."""
    zoom = NATIVE_ZOOM
    num_tiles = 2**zoom
    tiles_saved = 0
    log_time(f"Starting Phase 2 (Floor {floor}): Generating and pruning base tiles for zoom level {zoom}...")
    floor_dir = os.path.join(OUTPUT_DIR, str(floor))
    zoom_dir = os.path.join(floor_dir, str(zoom))
    os.makedirs(zoom_dir, exist_ok=True)
    for x in range(num_tiles):
        col_dir = os.path.join(zoom_dir, str(x))
        for y in range(num_tiles):
            box = (x * TILE_SIZE, y * TILE_SIZE, (x + 1) * TILE_SIZE, (y + 1) * TILE_SIZE)
            tile = padded_image.crop(box)
            if not is_tile_empty(tile, floor):
                if not os.path.exists(col_dir):
                    os.makedirs(col_dir)
                tile.save(os.path.join(col_dir, f"{y}.png"), 'PNG')
                tiles_saved += 1
    log_time(f"  (Floor {floor}) Phase 2 Complete. Saved {tiles_saved} non-empty base tiles.")


def generate_overview_tiles(floor):
    """Phase 3: Generate and prune overview tiles for lower zoom levels."""
    log_time(f"Starting Phase 3 (Floor {floor}): Generating and pruning overview tiles...")
    floor_dir = os.path.join(OUTPUT_DIR, str(floor))
    for zoom in range(NATIVE_ZOOM - 1, MIN_ZOOM - 1, -1):
        tiles_saved = 0
        num_tiles_current_zoom = 2**zoom
        source_zoom_dir = os.path.join(floor_dir, str(zoom + 1))
        zoom_dir = os.path.join(floor_dir, str(zoom))
        for x in range(num_tiles_current_zoom):
            col_dir = os.path.join(zoom_dir, str(x))
            for y in range(num_tiles_current_zoom):
                combined_image = Image.new('RGBA', (TILE_SIZE * 2, TILE_SIZE * 2))
                sx, sy = 2 * x, 2 * y
                # The try/except block gracefully handles missing tiles that were pruned.
                try: combined_image.paste(Image.open(os.path.join(source_zoom_dir, str(sx), f"{sy}.png")), (0, 0))
                except FileNotFoundError: pass
                try: combined_image.paste(Image.open(os.path.join(source_zoom_dir, str(sx + 1), f"{sy}.png")), (TILE_SIZE, 0))
                except FileNotFoundError: pass
                try: combined_image.paste(Image.open(os.path.join(source_zoom_dir, str(sx), f"{sy + 1}.png")), (0, TILE_SIZE))
                except FileNotFoundError: pass
                try: combined_image.paste(Image.open(os.path.join(source_zoom_dir, str(sx + 1), f"{sy + 1}.png")), (TILE_SIZE, TILE_SIZE))
                except FileNotFoundError: pass
                
                # Prune the combined retina tile if it's empty
                if not is_tile_empty(combined_image, floor):
                    if not os.path.exists(col_dir):
                        os.makedirs(col_dir)
                    combined_image.save(os.path.join(col_dir, f"{y}@2x.png"), 'PNG')
                
                # Prune the standard overview tile if it's empty
                overview_tile = combined_image.resize((TILE_SIZE, TILE_SIZE), Image.Resampling.LANCZOS)
                if not is_tile_empty(overview_tile, floor):
                    if not os.path.exists(col_dir):
                        os.makedirs(col_dir)
                    overview_tile.save(os.path.join(col_dir, f"{y}.png"), 'PNG')
                    tiles_saved += 1
        log_time(f"  (Floor {floor}) Zoom {zoom}: Saved {tiles_saved} non-empty overview tiles.")
    log_time(f"Phase 3 (Floor {floor}): Overview tile generation complete.")


def calculate_and_save_uncompressed_size(directory):
    """Phase 4: Calculate the total size of all generated tiles and save it."""
    log_time("Starting Phase 4: Calculating total uncompressed tile size for all floors...")
    total_size = 0
    for dirpath, _, filenames in os.walk(directory):
        for f in filenames:
            if f.endswith('.png'):
                fp = os.path.join(dirpath, f)
                total_size += os.path.getsize(fp)
    log_time(f"Total uncompressed size: {total_size} bytes")
    size_file_path = os.path.join(directory, 'uncompressed_size.txt')
    with open(size_file_path, 'w') as f: f.write(str(total_size))
    log_time(f"Saved size metadata to '{size_file_path}'")
    log_time("Phase 4: Metadata generation complete.")
    return size_file_path


def create_ordered_zip_archive(directory, zip_path, metadata_file):
    """Phase 5: Create a zip archive with the metadata file placed first."""
    log_time(f"Starting Phase 5: Archiving directory '{directory}'...")
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        zf.write(metadata_file, os.path.basename(metadata_file))
        for dirpath, _, filenames in os.walk(directory):
            for filename in filenames:
                if filename != os.path.basename(metadata_file):
                    filepath = os.path.join(dirpath, filename)
                    arcname = os.path.relpath(filepath, directory)
                    zf.write(filepath, arcname)
    log_time(f"Successfully created ordered archive at '{zip_path}'")
    log_time("Phase 5: Archiving complete.")


def main():
    """Main execution function."""
    log_time("Starting map tile generation process for all floors.")

    if os.path.exists(OUTPUT_DIR): shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)

    for floor, image_name in enumerate(SOURCE_IMAGES):
        source_image_path = os.path.join(SOURCE_DIR, image_name)
        log_time(f"--- Processing Floor {floor} (Image: {image_name}) ---")

        if not os.path.exists(source_image_path):
            log_time(f"FATAL: Source image not found at '{source_image_path}'"); return

        source_image = Image.open(source_image_path)

        # --- Phase 1: Programmatic Background Removal ---
        if floor > 0:
            log_time(f"  (Floor {floor}) Starting Phase 1: Making background transparent...")
            img_rgba = source_image.convert('RGBA')
            if img_rgba.getpixel((0, 0))[3] != 0:
                ImageDraw.floodfill(img_rgba, (0, 0), (0, 0, 0, 0))
                log_time(f"  (Floor {floor}) Flood fill complete.")
                source_image = img_rgba
            else:
                log_time(f"  (Floor {floor}) Background is already transparent. Skipping flood fill.")
                source_image = img_rgba
        else:
            source_image = source_image.convert('RGBA')

        padded_canvas = Image.new('RGBA', (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
        padded_canvas.paste(source_image, (0, 0), source_image)
        source_image.close()

        generate_base_tiles(padded_canvas, floor)
        generate_overview_tiles(floor)
        padded_canvas.close()
        log_time(f"--- Finished processing Floor {floor} ---")

    log_time("All floors have been processed. Tile generation has finished successfully.")

    metadata_file_path = calculate_and_save_uncompressed_size(OUTPUT_DIR)
    create_ordered_zip_archive(OUTPUT_DIR, ZIP_FILE_PATH, metadata_file_path)

    log_time(f"Cleaning up temporary tile directory '{OUTPUT_DIR}'...")
    shutil.rmtree(OUTPUT_DIR)
    log_time("Cleanup complete.")
    log_time(f"Process finished. A '{os.path.basename(ZIP_FILE_PATH)}' file is now in the assets folder.")


if __name__ == "__main__":
    main()
