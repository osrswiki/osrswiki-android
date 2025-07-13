# OSRSWiki Asset Generation Tools

This directory contains a set of tools used to extract and process assets required for the OSRSWiki Android application's native map feature.

## Overview

The new native map system relies on several pre-processed assets generated from the OSRS game cache and the RuneLite open-source client. This toolchain is designed to be as automated and future-proof as possible.

The core asset generation workflow is a multi-step process:
1.  **Fetch Decryption Keys**: Automatically download the latest map decryption keys (XTEAs).
2.  **Dump Complete Map Images**: Render the full game world, including terrain, objects, and borders, from the cache.
3.  **Slice Map Tiles**: Process the map images into `.mbtiles` assets for use in the app.
4.  **Extract POIs (Optional)**: Parse the RuneLite source code to generate a JSON file of points of interest.

---

## Environment Setup

All Python scripts in this directory are designed to run within a specific `micromamba` environment to ensure all dependencies are met.

### Prerequisites

-   [Micromamba](https://mamba.readthedocs.io/en/latest/installation.html) must be installed.

### Creating the Environment

If you are setting up this project for the first time, use the `environment.yml` file to create an identical environment.

1.  Navigate to the `tools/` directory in your terminal.
2.  Create the environment from the provided file. This only needs to be done once.
    ```bash
    micromamba create --name osrs-tools --file environment.yml
    ```
3.  Activate the environment before running any scripts. You must do this every time you open a new terminal session.
    ```bash
    micromamba activate osrs-tools
    ```

### Exporting/Updating the Environment File

If you have manually installed or updated packages in your `osrs-tools` environment (e.g., `pip install some-package`), you should regenerate the `environment.yml` file to reflect these changes before committing your work.

```bash
# Make sure the 'osrs-tools' environment is active
micromamba env export --from-history > environment.yml
```

---

## The Asset Generation Pipeline

Follow these steps in order to generate a complete and up-to-date set of map assets.

### Prerequisites

- A complete and up-to-date copy of the Old School RuneScape `jagexcache` directory, placed in `tools/map-dumper/`.
- The `osrs-tools` micromamba environment must be active.
- A Java Development Kit (JDK) version 11 or higher.

### Step 1: Update XTEA Decryption Keys

The `update_xteas.py` script automatically fetches the latest, community-sourced XTEA keys for all map regions from the OpenRS2 Archive.

**This step should be run whenever you want to ensure you are using the most current map data.**

```bash
python3 update_xteas.py
```
This will download and replace the `tools/map-dumper/xtea.json` file.

### Step 2: Dump Complete Map Images

The `map-dumper/` directory contains a Java project that reads the game cache and renders the full game world.

**This step only needs to be re-run when there are significant changes to the game world.**

```bash
./run_dumper.sh
```
The output images (`img-0.png`, `img-1.png`, etc.) will be placed in `tools/map-dumper/output/`.

### Step 3: Slice Tiles into MBTiles

The `slice_tiles.py` script takes the high-resolution images from the `map-dumper` and processes them into the final `.mbtiles` assets used by the Android application.

```bash
python3 slice_tiles.py
```
This will create `map_floor_0.mbtiles`, `map_floor_1.mbtiles`, etc., and place them directly in the Android app's assets folder (`app/src/main/assets/`).

### Step 4 (Optional): Extract POI Data

The `extract_pois.py` script parses a local copy of the RuneLite source code to extract locations for various points of interest (dungeons, shortcuts, etc.). This supplements the native game icons.

**Note:** This currently extracts only a subset of all possible POIs. A future enhancement will be to also dump the native map icons directly from the cache.

```bash
# This script assumes a checkout of the RuneLite repo at /home/miyawaki/runelite
python3 extract_pois.py
```
This generates `pois.json` in the `tools/` directory, which should then be copied to the Android app's assets.

---

## Android App Integration

- **Raster Tiles**: The generated `map_floor_N.mbtiles` files are copied from the app's assets to its internal storage on first launch. They are then loaded as `RasterSource`s in MapLibre.
- **POI Data**: The `pois.json` file will be loaded by the app, converted to a GeoJSON FeatureCollection, and rendered on the map using a `SymbolLayer`.
- **Coordinate System**: The app uses a simple projection to map the game's pixel coordinates to the `LatLng` system used by MapLibre's camera and bounds. This logic is contained within `MapFragment.kt`.
