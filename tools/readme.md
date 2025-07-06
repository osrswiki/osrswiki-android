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

## Version Control

A `.gitignore` file is included in this directory to prevent the large cache files and generated output (`output/` directory and `pois.json`) from being committed to the repository. Only the tool source code and scripts should be version-controlled.

