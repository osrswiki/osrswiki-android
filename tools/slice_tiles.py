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

The process involves two main phases:
1.  Base Tile Generation: The full-resolution padded canvas is sliced into
    256x256 tiles at the highest zoom level (NATIVE_ZOOM).
2.  Overview Tile Generation: Lower zoom level tiles are created by recursively
    combining and downscaling tiles from the next highest zoom level. For
    retina support, it generates both standard (256px) and high-resolution
    512px '@2x' tiles.
3.  Archiving: The entire generated tile directory is compressed into a
    single .zip file to avoid Android build issues with too many assets.
"""

import os
import shutil
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

# The dimension of each square tile in pixels.
TILE_SIZE = 256

# The dimension of the square canvas, calculated as the next power of two
# large enough to hold the source image's largest dimension.
# Source is 45568px, so the next power of two is 2^16 = 65536.
CANVAS_SIZE = 65536

# The native zoom level corresponds to the canvas size.
# 256px * 2^8 = 65536px, so NATIVE_ZOOM is 8.
NATIVE_ZOOM = 8

# The minimum zoom level to generate. The map is visually set to a minZoom of 3.
MIN_ZOOM = 3


def log_time(message):
    """Prints a message with a timestamp."""
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}")


def generate_base_tiles(padded_image):
    """
    Phase 1: Generate tiles for the native zoom level.

    This function slices the padded canvas into a complete grid of 256x256
    tiles for the highest zoom level (NATIVE_ZOOM).
    """
    zoom = NATIVE_ZOOM
    num_tiles = 2**zoom
    log_time(f"Starting Phase 1: Generating {num_tiles * num_tiles} base tiles for zoom level {zoom}...")

    zoom_dir = os.path.join(OUTPUT_DIR, str(zoom))
    os.makedirs(zoom_dir, exist_ok=True)

    for x in range(num_tiles):
        col_dir = os.path.join(zoom_dir, str(x))
        os.makedirs(col_dir, exist_ok=True)
        for y in range(num_tiles):
            # Define the boundaries for cropping the tile from the canvas.
            left = x * TILE_SIZE
            top = y * TILE_SIZE
            right = left + TILE_SIZE
            bottom = top + TILE_SIZE

            box = (left, top, right, bottom)
            tile = padded_image.crop(box)

            # Save the generated tile to the correct z/x/y path.
            tile_path = os.path.join(col_dir, f"{y}.png")
            tile.save(tile_path, 'PNG')

        if (x + 1) % 10 == 0 or x == num_tiles - 1:
            log_time(f"  ...completed generating tiles for column x={x}/{num_tiles - 1}")

    log_time("Phase 1: Base tile generation complete.")


def generate_overview_tiles():
    """
    Phase 2: Generate overview tiles for lower zoom levels.

    This function creates tiles for zoom levels z=(NATIVE_ZOOM - 1) down to z=MIN_ZOOM.
    It generates both standard 256x256 tiles and true high-resolution
    512x512 '@2x' tiles for each level.
    """
    log_time(f"Starting Phase 2: Generating overview tiles from zoom {NATIVE_ZOOM - 1} down to {MIN_ZOOM}...")

    for zoom in range(NATIVE_ZOOM - 1, MIN_ZOOM - 1, -1):
        num_tiles_current_zoom = 2**zoom
        zoom_plus_1 = zoom + 1
        log_time(f"  Generating tiles for zoom level {zoom} from {zoom_plus_1}...")

        zoom_dir = os.path.join(OUTPUT_DIR, str(zoom))
        os.makedirs(zoom_dir, exist_ok=True)

        source_zoom_dir = os.path.join(OUTPUT_DIR, str(zoom_plus_1))

        for x in range(num_tiles_current_zoom):
            col_dir = os.path.join(zoom_dir, str(x))
            os.makedirs(col_dir, exist_ok=True)
            for y in range(num_tiles_current_zoom):
                # Create a new 512x512 image to hold the four source tiles.
                # This will become the standard-resolution tile for this level.
                combined_image = Image.new('RGB', (TILE_SIZE * 2, TILE_SIZE * 2))

                top_left_x, top_left_y = 2 * x, 2 * y

                # Get paths for the four standard-res source tiles from the higher zoom level.
                path_tl = os.path.join(source_zoom_dir, str(top_left_x), f"{top_left_y}.png")
                path_tr = os.path.join(source_zoom_dir, str(top_left_x + 1), f"{top_left_y}.png")
                path_bl = os.path.join(source_zoom_dir, str(top_left_x), f"{top_left_y + 1}.png")
                path_br = os.path.join(source_zoom_dir, str(top_left_x + 1), f"{top_left_y + 1}.png")

                try:
                    img_tl = Image.open(path_tl)
                    img_tr = Image.open(path_tr)
                    img_bl = Image.open(path_bl)
                    img_br = Image.open(path_br)

                    combined_image.paste(img_tl, (0, 0))
                    combined_image.paste(img_tr, (TILE_SIZE, 0))
                    combined_image.paste(img_bl, (0, TILE_SIZE))
                    combined_image.paste(img_br, (TILE_SIZE, TILE_SIZE))
                except FileNotFoundError as e:
                    log_time(f"  WARNING: Missing source tile: {e}. The resulting overview tile will be black.")

                # Save the 512x512 composite as the high-resolution @2x tile.
                retina_tile_path = os.path.join(col_dir, f"{y}@2x.png")
                combined_image.save(retina_tile_path, 'PNG')

                # Resize the 512x512 composite down to a 256x256 standard tile.
                overview_tile = combined_image.resize((TILE_SIZE, TILE_SIZE), Image.Resampling.LANCZOS)
                std_tile_path = os.path.join(col_dir, f"{y}.png")
                overview_tile.save(std_tile_path, 'PNG')

    log_time("Phase 2: Overview tile generation complete.")


def main():
    """
    Main execution function.
    """
    log_time("Starting map tile generation process.")

    # 0. Verify source image exists.
    if not os.path.exists(SOURCE_IMAGE_PATH):
        log_time(f"FATAL: Source image not found at '{SOURCE_IMAGE_PATH}'")
        return

    # 1. Clean and create the output directory for a fresh run.
    log_time(f"Preparing output directory: '{OUTPUT_DIR}'")
    if os.path.exists(OUTPUT_DIR):
        shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)

    # 2. Create the padded canvas.
    log_time(f"Loading source image from '{SOURCE_IMAGE_PATH}'")
    source_image = Image.open(SOURCE_IMAGE_PATH)
    log_time(f"Source image dimensions: {source_image.size[0]}x{source_image.size[1]}")

    log_time(f"Creating a {CANVAS_SIZE}x{CANVAS_SIZE} black canvas...")
    padded_canvas = Image.new('RGB', (CANVAS_SIZE, CANVAS_SIZE), 'black')

    log_time("Pasting source image onto the canvas at position (0, 0)...")
    padded_canvas.paste(source_image, (0, 0))
    # Release memory for the original source image
    source_image.close()

    # 3. Generate all tiles based on the padded canvas.
    generate_base_tiles(padded_canvas)
    generate_overview_tiles()

    # Release memory for the large padded canvas
    padded_canvas.close()

    log_time("Tile generation has finished successfully.")

    # 4. Archive the generated tile directory into a single zip file.
    log_time(f"Archiving tile directory '{OUTPUT_DIR}'...")
    assets_dir = os.path.dirname(OUTPUT_DIR)
    zip_output_base_path = os.path.join(assets_dir, 'map_tiles')

    try:
        shutil.make_archive(zip_output_base_path, 'zip', root_dir=OUTPUT_DIR)
        log_time(f"Successfully created archive at '{zip_output_base_path}.zip'")
    except Exception as e:
        log_time(f"FATAL: Failed to create zip archive: {e}")
        return

    # 5. Clean up the original tile directory.
    log_time(f"Cleaning up original tile directory '{OUTPUT_DIR}'...")
    try:
        shutil.rmtree(OUTPUT_DIR)
        log_time("Cleanup complete.")
    except Exception as e:
        log_time(f"ERROR: Failed to clean up directory: {e}")

    log_time("Process finished. A 'map_tiles.zip' file is now in the assets folder.")


if __name__ == "__main__":
    # Pillow is required. Install with: pip install Pillow
    main()
