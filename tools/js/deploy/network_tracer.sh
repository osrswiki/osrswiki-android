#!/bin/bash

# Network tracer wrapper script - automatically uses micromamba environment

set -e

# Get the directory containing this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Check if micromamba is available
if command -v micromamba >/dev/null 2>&1; then
    MICROMAMBA_CMD="micromamba"
elif [ -f "$PROJECT_ROOT/tools/bin/micromamba" ]; then
    MICROMAMBA_CMD="$PROJECT_ROOT/tools/bin/micromamba"
else
    echo "Error: micromamba not found. Please install micromamba first."
    exit 1
fi

# Check if the environment exists, create if needed
ENV_NAME="osrswiki-tools"
if ! $MICROMAMBA_CMD env list | grep -q "^$ENV_NAME"; then
    echo "Creating micromamba environment: $ENV_NAME"
    $MICROMAMBA_CMD create -n "$ENV_NAME" -y python=3.11 requests
    $MICROMAMBA_CMD run -n "$ENV_NAME" pip install playwright
    $MICROMAMBA_CMD run -n "$ENV_NAME" playwright install chromium
fi

# Run the network tracer in the micromamba environment
cd "$PROJECT_ROOT"
$MICROMAMBA_CMD run -n "$ENV_NAME" python tools/js_modules/network_tracer.py "$@"