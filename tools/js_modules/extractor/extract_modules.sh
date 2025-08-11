#!/bin/bash
# Wrapper script to run extract_modules.py with the correct micromamba environment

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MICROMAMBA="$PROJECT_ROOT/tools/bin/micromamba"

# Check if local micromamba is available
if [ -f "$MICROMAMBA" ]; then
    echo "Using local micromamba environment osrs-tools..."
    exec "$MICROMAMBA" run -n osrs-tools python3 "$SCRIPT_DIR/extract_modules.py" "$@"
elif command -v micromamba &> /dev/null; then
    echo "Using system micromamba environment osrs-tools..."
    exec micromamba run -n osrs-tools python3 "$SCRIPT_DIR/extract_modules.py" "$@"
elif command -v conda &> /dev/null; then
    echo "Using conda environment osrs-tools..."
    exec conda run -n osrs-tools python3 "$SCRIPT_DIR/extract_modules.py" "$@"
else
    echo "Warning: No conda/micromamba found, trying system Python..."
    echo "You may need to install dependencies: pip install requests"
    exec python3 "$SCRIPT_DIR/extract_modules.py" "$@"
fi