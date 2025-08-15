#!/bin/bash
set -euo pipefail

# Organized screenshot wrapper for Android development
# Usage: ./scripts/android/take-screenshot.sh [description]
# Example: ./scripts/android/take-screenshot.sh "search-results"

# Auto-source environment if available
if [[ -f .claude-env ]]; then
    source .claude-env
elif [[ -f ../.claude-env ]]; then
    source ../.claude-env
fi

# Check for required environment variable
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    echo "❌ ANDROID_SERIAL not set. Please run: source .claude-env" >&2
    exit 1
fi

# Create description parameter with default
DESCRIPTION="${1:-screenshot}"

# Create screenshots directory if it doesn't exist
SCREENSHOTS_DIR="screenshots"
mkdir -p "$SCREENSHOTS_DIR"

# Generate timestamp and filename
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
FILENAME="${TIMESTAMP}-${DESCRIPTION}.png"
FILEPATH="${SCREENSHOTS_DIR}/${FILENAME}"

# Take screenshot
echo "📸 Taking screenshot on device: $ANDROID_SERIAL"
if adb -s "$ANDROID_SERIAL" shell screencap -p /sdcard/temp_screen.png; then
    if adb -s "$ANDROID_SERIAL" pull /sdcard/temp_screen.png "$FILEPATH"; then
        # Clean up temp file on device
        adb -s "$ANDROID_SERIAL" shell rm /sdcard/temp_screen.png
        
        echo "✅ Screenshot saved: $FILEPATH"
        echo "$FILEPATH"  # Return path for scripting
    else
        echo "❌ Failed to pull screenshot from device" >&2
        exit 1
    fi
else
    echo "❌ Failed to take screenshot on device" >&2
    exit 1
fi

# Optional: Create metadata file for session tracking
METADATA_FILE="${SCREENSHOTS_DIR}/.metadata"
if [[ ! -f "$METADATA_FILE" ]]; then
    echo "# Screenshot session metadata" > "$METADATA_FILE"
    echo "session_start=$(date --iso-8601=seconds)" >> "$METADATA_FILE"
    echo "worktree=$(pwd)" >> "$METADATA_FILE"
    echo "device=${ANDROID_SERIAL}" >> "$METADATA_FILE"
fi

# Log screenshot
echo "$(date --iso-8601=seconds) $FILENAME $DESCRIPTION" >> "$METADATA_FILE"