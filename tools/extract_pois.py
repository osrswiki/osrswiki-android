#!/usr/bin/env python3

import os
import re
import json

def parse_java_enum_for_pois(file_path, poi_type):
    """
    Parses a Java enum file to extract POI data.
    Finds enum members and their corresponding WorldPoint coordinates and tooltips.
    """
    pois = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

            pattern = re.compile(
                r'([A-Z_0-9]+)\s*\((.*?new\s+WorldPoint\(\s*(\d+),\s*(\d+),\s*(\d+)\s*\).*?)\)',
                re.DOTALL
            )

            for match in pattern.finditer(content):
                name = match.group(1).replace('_', ' ').title()
                full_args = match.group(2)
                x = int(match.group(3))
                y = int(match.group(4))
                z = int(match.group(5))

                tooltip_match = re.search(r'"([^"]+)"', full_args)
                tooltip = tooltip_match.group(1) if tooltip_match else name

                poi = {
                    "name": tooltip,
                    "x": x,
                    "y": y,
                    "plane": z,
                    "type": poi_type
                }
                pois.append(poi)
    except Exception as e:
        print(f"Could not process file {file_path}: {e}")

    return pois

def main():
    """
    Main function to find POI files, parse them, and write to JSON.
    """
    script_dir = os.path.dirname(os.path.abspath(__file__))
    runelite_src_path = "/home/miyawaki/runelite"
    output_filename = os.path.join(script_dir, "pois.json")

    poi_files = {
        "Dungeon": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java"),
        "Mining": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/MiningSiteLocation.java"),
        "AgilityCourse": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/AgilityCourseLocation.java"),
        "AgilityShortcut": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/game/AgilityShortcut.java"),
        "FairyRing": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/FairyRingLocation.java"),
        "Transportation": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TransportationPointLocation.java"),
        "Fishing": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/FishingSpotLocation.java"),
        "Farming": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/FarmingPatchLocation.java"),
        "RareTree": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/RareTreeLocation.java"),
        "Minigame": os.path.join(runelite_src_path, "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/MinigameLocation.java"),
    }

    all_pois = []
    print("Starting POI extraction...")

    for poi_type, file_path in poi_files.items():
        if os.path.exists(file_path):
            print(f"Processing {poi_type} from {os.path.basename(file_path)}...")
            pois = parse_java_enum_for_pois(file_path, poi_type)
            all_pois.extend(pois)
            print(f"  ...Found {len(pois)} POIs.")
        else:
            print(f"Warning: File not found, skipping: {file_path}")

    with open(output_filename, 'w', encoding='utf-8') as f:
        json.dump(all_pois, f, indent=2)

    print(f"\nExtraction complete. {len(all_pois)} total POIs saved to '{os.path.abspath(output_filename)}'.")

if __name__ == "__main__":
    main()
