#!/bin/bash
# This script runs the MapImageDumper from within its project directory.
# It assumes the cache and xtea files are located inside 'map-dumper'.

# Navigate to this script's directory to ensure relative paths work correctly.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
DUMPER_DIR="$SCRIPT_DIR/map-dumper"

if [ ! -d "$DUMPER_DIR" ]; then
    echo "Error: Map dumper project not found at $DUMPER_DIR"
    exit 1
fi

# CD into the dumper project so gradle can find its files and cache.
cd "$DUMPER_DIR" || exit

# Check for cache and xtea files before running.
if [ ! -d "jagexcache" ]; then
    echo "Error: 'jagexcache' directory not found inside '$DUMPER_DIR'."
    echo "Please copy your cache here before running."
    exit 1
fi

if [ ! -f "xtea.json" ]; then
    echo "Error: 'xtea.json' not found inside '$DUMPER_DIR'."
    echo "Please ensure the key file is present."
    exit 1
fi

# Execute the gradle wrapper.
echo "Running map dumper... Output will be in $DUMPER_DIR/output/"
./gradlew run --args='--cachedir "jagexcache/oldschool/LIVE" --xteapath "xtea.json" --outputdir "output"'

if [ $? -eq 0 ]; then
    echo "Map dumper finished successfully."
else
    echo "Map dumper failed. Please check the output above for errors."
fi
