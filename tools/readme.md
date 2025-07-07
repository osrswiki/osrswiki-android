# OSRSWiki Asset Generation Tools

This directory contains a set of tools used to extract and process assets required for the OSRSWiki Android application's map features. These tools are not part of the Android app itself but are essential for generating the data it uses.

## Overview

The new map system in the OSRSWiki app is designed to be self-contained and fully functional offline. To achieve this, it relies on two main types of pre-processed assets:

1.  **Base Map Images**: High-resolution PNG images of the game world for each plane (z-level).
2.  **Point of Interest (POI) Data**: A structured JSON file containing the coordinates and metadata for thousands of in-game locations, such as dungeons, shortcuts, and minigames.

The tools in this directory are used to generate these assets from the official game cache and the RuneLite source code.

---

## 1. Map Image Dumper

The `map-dumper/` directory contains a standalone Java project based on RuneLite's cache-reading library. Its purpose is to render the game world into four high-resolution PNG images.

### Prerequisites

- A complete and up-to-date copy of the Old School RuneScape `jagexcache` directory.
- A Java Development Kit (JDK) version 11 or higher.
- Gradle (The project uses a Gradle Wrapper, which will download the correct version automatically).

### Usage

1.  **Place Cache Files**: Before running, ensure your `jagexcache` directory and a valid `xtea.json` file are placed inside the `tools/map-dumper/` directory.

2.  **Execute the Runner Script**: Navigate to the `tools/` directory in your terminal and run the provided shell script:
    ```bash
    ./run_dumper.sh
    ```
    The script will handle changing into the correct directory and executing the Gradle task with the proper arguments.

3.  **Output**: The process will take several minutes to complete. The final images (`img-0.png`, `img-1.png`, `img-2.png`, `img-3.png`) will be saved in the `tools/map-dumper/output/` directory. These images should then be copied into the Android project's assets.

---

## 2. POI Extractor

The `extract_pois.py` script is a Python utility that parses the RuneLite source code to extract the coordinates and names of thousands of points of interest.

### Prerequisites

- Python 3.
- A local checkout of the RuneLite source code located at `/home/miyawaki/runelite`. (Note: This path is hardcoded in the script and should be updated if the location changes).

### Usage

1.  **Execute the Script**: Navigate to the `tools/` directory in your terminal and run the script:
    ```bash
    python3 extract_pois.py
    ```

2.  **Output**: The script will generate a `pois.json` file in the same `tools/` directory. This file should be copied into the Android project's assets for use by the application.

---

## 3. POI Coordinate Transformation

Aligning the POIs from `pois.json` onto the custom map tiles requires a precise multi-step transformation. The process involves converting coordinates across three different systems: Game World Coordinates, Map Image Pixel Coordinates, and Leaflet CRS Coordinates. The formula was derived by analyzing the `MapImageDumper.java` tool and the RuneLite client's rendering code.

### Transformation Variables

The following variables are used in the transformation equations. Their values are specific to the game cache version used to generate the map assets.

| Variable | Value | Description |
|---|---|---|
| `world_x`, `world_y` | (from POI) | The original game tile coordinate from `pois.json`. |
| `MIN_X_OFFSET` | 1024 | The base X-coordinate of the westernmost map region. |
| `MAX_Y_OFFSET` | 12608 | The base Y-coordinate of the northernmost map region. |
| `REGION_SIZE` | 64 | The dimension (width/height) of a game region in tiles. |
| `PIXELS_PER_TILE`| 4 | The scaling factor; each game tile is a 4x4 square on the map image. |
| `LEAFLET_TILE_SIZE`| 256.0 | The dimension of the tiles used by our Leaflet implementation. |

### Transformation Steps

The full transformation is implemented in the `loadPois` function of the `app/src/main/assets/map.html` file.

#### Step 1: Game World to Image Pixel

This step converts the `WorldPoint` coordinate to a pixel coordinate on the final `img-0.png`. It involves an offset, a Y-axis inversion, scaling, and a final calibration based on visual analysis.

First, a final Y-offset is calculated to account for the Y-inversion happening relative to the top of a region, not just the world origin:
$$ FINAL\_Y\_OFFSET = MAX\_Y\_OFFSET + REGION\_SIZE - 1 $$

Then, the pixel coordinates are calculated. A final calibration of `+4` pixels on the X-axis and `-4` pixels on the Y-axis was required to achieve perfect alignment.

$$ pixel_x = (world_x - MIN\_X\_OFFSET) \times PIXELS\_PER\_TILE + 4 $$
$$ pixel_y = (FINAL\_Y\_OFFSET - world_y) \times PIXELS\_PER\_TILE - 4 $$

#### Step 2: Image Pixel to Leaflet Coordinate

This step normalizes the final pixel coordinate into the `[0..256]` coordinate reference system (CRS) used by our Leaflet map.

$$ leaflet_x = \frac{pixel_x}{LEAFLET\_TILE\_SIZE} $$
$$ leaflet_y = \frac{pixel_y}{LEAFLET\_TILE\_SIZE} $$

These `leaflet_x` and `leaflet_y` values are then passed to the `L.marker` function to place the POI on the map.

---

## 4. Version Control

A `.gitignore` file is included in this directory to prevent the large cache files and generated output (`output/` directory and `pois.json`) from being committed to the repository. Only the tool source code and scripts should be version-controlled.
