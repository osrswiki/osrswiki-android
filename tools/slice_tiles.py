#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Generates a complete map tile pyramid for a Leaflet.js map viewer.

This script implements a "padding" strategy to handle irregularly sized source
images. It first creates a square canvas that is a power of two and large
enough to contain the source image. The source image is pasted onto this
canvas, and the resulting padded image is then tiled. This ensures that the
generated tile set is complete (not sparse) and aligns with the coordinate
system expected by standard tiling libraries like Leaflet.

The process involves these main phases:
1.  Base Tile Generation: The full-resolution padded canvas is sliced into
    256x256 tiles at the highest zoom level (NATIVE_ZOOM).
2.  Overview Tile Generation: Lower zoom level tiles are created by recursively
    combining and downscaling tiles from the next highest zoom level.
3.  Metadata Generation: The total uncompressed size of all generated image
    tiles is calculated and saved to a metadata file.
4.  Archiving: The entire generated tile directory is compressed into a
    single .zip file, with the metadata file placed first for fast lookups.
"""

import os
import shutil
import zipfile
from datetime import datetime
from PIL import Image

# Disable the decompression bomb check as we are intentionally processing
# a very large image. This must be set before any image is opened.
Image.MAX_IMAGE_PIXELS = None

# --- Configuration ---
# The root directory of the project.
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

# The source image to be tiled.
SOURCE_IMAGE_PATH = os.path.join(PROJECT_ROOT, 'tools', 'map-dumper', 'output', 'img-0.png')

# The temporary destination for the generated map tiles before zipping.
OUTPUT_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets', 'map_tiles')

# The final destination and name for the output zip file.
ZIP_FILE_PATH = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets', 'map_tiles.zip')

# The dimension of each square tile in pixels.
TILE_SIZE = 256

# The dimension of the square canvas.
CANVAS_SIZE = 65536

# The native zoom level.
NATIVE_ZOOM = 8

# The minimum zoom level to generate.
MIN_ZOOM = 3


def log_time(message):
    """Prints a message with a timestamp."""
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}")


def generate_base_tiles(padded_image):
    """Phase 1: Generate tiles for the native zoom level."""
    zoom = NATIVE_ZOOM
    num_tiles = 2**zoom
    log_time(f"Starting Phase 1: Generating {num_tiles * num_tiles} base tiles for zoom level {zoom}...")
    zoom_dir = os.path.join(OUTPUT_DIR, str(zoom))
    os.makedirs(zoom_dir, exist_ok=True)
    for x in range(num_tiles):
        col_dir = os.path.join(zoom_dir, str(x))
        os.makedirs(col_dir, exist_ok=True)
        for y in range(num_tiles):
            box = (x * TILE_SIZE, y * TILE_SIZE, (x + 1) * TILE_SIZE, (y + 1) * TILE_SIZE)
            tile = padded_image.crop(box)
            tile.save(os.path.join(col_dir, f"{y}.png"), 'PNG')
        if (x + 1) % 10 == 0 or x == num_tiles - 1:
            log_time(f"  ...completed generating tiles for column x={x}/{num_tiles - 1}")
    log_time("Phase 1: Base tile generation complete.")


def generate_overview_tiles():
    """Phase 2: Generate overview tiles for lower zoom levels."""
    log_time(f"Starting Phase 2: Generating overview tiles from zoom {NATIVE_ZOOM - 1} down to {MIN_ZOOM}...")
    for zoom in range(NATIVE_ZOOM - 1, MIN_ZOOM - 1, -1):
        num_tiles_current_zoom = 2**zoom
        log_time(f"  Generating tiles for zoom level {zoom} from {zoom + 1}...")
        zoom_dir = os.path.join(OUTPUT_DIR, str(zoom))
        os.makedirs(zoom_dir, exist_ok=True)
        source_zoom_dir = os.path.join(OUTPUT_DIR, str(zoom + 1))
        for x in range(num_tiles_current_zoom):
            col_dir = os.path.join(zoom_dir, str(x))
            os.makedirs(col_dir, exist_ok=True)
            for y in range(num_tiles_current_zoom):
                combined_image = Image.new('RGB', (TILE_SIZE * 2, TILE_SIZE * 2))
                sx, sy = 2 * x, 2 * y
                try:
                    img_tl = Image.open(os.path.join(source_zoom_dir, str(sx), f"{sy}.png"))
                    img_tr = Image.open(os.path.join(source_zoom_dir, str(sx + 1), f"{sy}.png"))
                    img_bl = Image.open(os.path.join(source_zoom_dir, str(sx), f"{sy + 1}.png"))
                    img_br = Image.open(os.path.join(source_zoom_dir, str(sx + 1), f"{sy + 1}.png"))
                    combined_image.paste(img_tl, (0, 0))
                    combined_image.paste(img_tr, (TILE_SIZE, 0))
                    combined_image.paste(img_bl, (0, TILE_SIZE))
                    combined_image.paste(img_br, (TILE_SIZE, TILE_SIZE))
                except FileNotFoundError as e:
                    log_time(f"  WARNING: Missing source tile: {e}. The resulting overview tile will be black.")
                
                combined_image.save(os.path.join(col_dir, f"{y}@2x.png"), 'PNG')
                overview_tile = combined_image.resize((TILE_SIZE, TILE_SIZE), Image.Resampling.LANCZOS)
                overview_tile.save(os.path.join(col_dir, f"{y}.png"), 'PNG')
    log_time("Phase 2: Overview tile generation complete.")


def calculate_and_save_uncompressed_size(directory):
    """Phase 3: Calculate the total size of all generated tiles and save it."""
    log_time("Starting Phase 3: Calculating total uncompressed tile size...")
    total_size = 0
    for dirpath, _, filenames in os.walk(directory):
        for f in filenames:
            if f.endswith('.png'): # Ensure we only count image files
                fp = os.path.join(dirpath, f)
                total_size += os.path.getsize(fp)

    log_time(f"Total uncompressed size: {total_size} bytes")
    size_file_path = os.path.join(directory, 'uncompressed_size.txt')
    with open(size_file_path, 'w') as f:
        f.write(str(total_size))
    log_time(f"Saved size metadata to '{size_file_path}'")
    log_time("Phase 3: Metadata generation complete.")
    return size_file_path


def create_ordered_zip_archive(directory, zip_path, metadata_file):
    """Phase 4: Create a zip archive with the metadata file placed first."""
    log_time(f"Starting Phase 4: Archiving directory '{directory}'...")
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        # Add the metadata file first for quick access
        log_time(f"  Adding metadata file '{os.path.basename(metadata_file)}' to archive.")
        zf.write(metadata_file, os.path.basename(metadata_file))

        # Add all other files
        for dirpath, _, filenames in os.walk(directory):
            for filename in filenames:
                if filename != os.path.basename(metadata_file):
                    filepath = os.path.join(dirpath, filename)
                    arcname = os.path.relpath(filepath, directory)
                    zf.write(filepath, arcname)
    log_time(f"Successfully created ordered archive at '{zip_path}'")
    log_time("Phase 4: Archiving complete.")


def main():
    """Main execution function."""
    log_time("Starting map tile generation process.")
    if not os.path.exists(SOURCE_IMAGE_PATH):
        log_time(f"FATAL: Source image not found at '{SOURCE_IMAGE_PATH}'")
        return

    if os.path.exists(OUTPUT_DIR):
        shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)

    log_time("Creating padded canvas...")
    source_image = Image.open(SOURCE_IMAGE_PATH)
    padded_canvas = Image.new('RGB', (CANVAS_SIZE, CANVAS_SIZE), 'black')
    padded_canvas.paste(source_image, (0, 0))
    source_image.close()

    generate_base_tiles(padded_canvas)
    generate_overview_tiles()
    padded_canvas.close()
    log_time("Tile generation has finished successfully.")

    metadata_file_path = calculate_and_save_uncompressed_size(OUTPUT_DIR)
    
    create_ordered_zip_archive(OUTPUT_DIR, ZIP_FILE_PATH, metadata_file_path)

    log_time(f"Cleaning up temporary tile directory '{OUTPUT_DIR}'...")
    shutil.rmtree(OUTPUT_DIR)
    log_time("Cleanup complete.")
    log_time(f"Process finished. A '{os.path.basename(ZIP_FILE_PATH)}' file is now in the assets folder.")


if __name__ == "__main__":
    main()
