#!/bin/bash
# This script runs the MapImageDumper from within its project directory.

# Navigate to this script's directory to ensure relative paths work correctly.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
DUMPER_DIR="$SCRIPT_DIR/map-dumper"
CACHE_DIR="$SCRIPT_DIR/openrs2_cache/cache" # The actual cache data is in a 'cache' subdirectory

if [ ! -d "$DUMPER_DIR" ]; then
    echo "Error: Map dumper project not found at $DUMPER_DIR"
    exit 1
fi

# CD into the dumper project so gradle can find its files.
cd "$DUMPER_DIR" || exit

# Check for the downloaded cache and xtea files before running.
if [ ! -d "$CACHE_DIR" ]; then
    echo "Error: 'openrs2_cache/cache' directory not found."
    echo "Please run 'python3 ../setup_cache.py' first."
    exit 1
fi

if [ ! -f "xtea.json" ]; then
    echo "Error: 'xtea.json' not found inside '$DUMPER_DIR'."
    echo "Please run 'python3 ../update_xteas.py' first."
    exit 1
fi

# Execute the gradle wrapper, pointing to the new cache directory.
echo "Running map dumper... Output will be in $DUMPER_DIR/output/"
# Note the relative path to the cache from the dumper directory
./gradlew run --args="--cachedir \"$CACHE_DIR\" --xteapath \"xtea.json\" --outputdir \"output\""

if [ $? -eq 0 ]; then
    echo "Map dumper finished successfully."
else
    echo "Map dumper failed. Please check the output above for errors."
fi
