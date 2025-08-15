#!/bin/bash
# Robust UI automation using UIAutomator dump to find and click elements
# Usage: ./ui-click.sh --text "Search"
#    or: ./ui-click.sh --id "com.example:id/button"
#    or: ./ui-click.sh --desc "Navigation drawer"
#    or: ./ui-click.sh --class "android.widget.Button" --index 0

set -euo pipefail

# Auto-source session environment
if [[ -f .claude-env ]]; then
    source .claude-env
fi

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --text TEXT        Find element by text content"
    echo "  --id ID           Find element by resource-id"
    echo "  --desc DESC       Find element by content description"
    echo "  --class CLASS     Find element by class name (combine with --index)"
    echo "  --index N         Use Nth element when multiple matches (default: 0)"
    echo "  --dump-only       Only dump UI hierarchy to ui-dump.xml"
    echo "  --help            Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 --text 'Search'"
    echo "  $0 --id 'com.omiyawaki.osrswiki:id/search_button'"
    echo "  $0 --desc 'Navigate up'"
    echo "  $0 --class 'android.widget.Button' --index 1"
}

# Parse arguments
SEARCH_TYPE=""
SEARCH_VALUE=""
ELEMENT_INDEX=0
DUMP_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --text)
            SEARCH_TYPE="text"
            SEARCH_VALUE="$2"
            shift 2
            ;;
        --id)
            SEARCH_TYPE="resource-id"
            SEARCH_VALUE="$2"
            shift 2
            ;;
        --desc)
            SEARCH_TYPE="content-desc"
            SEARCH_VALUE="$2"
            shift 2
            ;;
        --class)
            SEARCH_TYPE="class"
            SEARCH_VALUE="$2"
            shift 2
            ;;
        --index)
            ELEMENT_INDEX="$2"
            shift 2
            ;;
        --dump-only)
            DUMP_ONLY=true
            shift
            ;;
        --help)
            show_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Ensure we have device serial
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    echo "❌ ANDROID_SERIAL not set. Run from a session or use ./run-with-env.sh"
    exit 1
fi

echo "📱 Dumping UI hierarchy from $ANDROID_SERIAL..."

# Dump UI hierarchy
if ! adb -s "$ANDROID_SERIAL" shell uiautomator dump /sdcard/ui-dump.xml; then
    echo "❌ Failed to dump UI hierarchy"
    exit 1
fi

# Pull the dump file
if ! adb -s "$ANDROID_SERIAL" pull /sdcard/ui-dump.xml ./ui-dump.xml >/dev/null 2>&1; then
    echo "❌ Failed to pull UI dump file"
    exit 1
fi

if [[ "$DUMP_ONLY" == true ]]; then
    echo "✅ UI hierarchy dumped to ./ui-dump.xml"
    echo "💡 You can inspect it manually or use other options to click elements"
    exit 0
fi

if [[ -z "$SEARCH_TYPE" ]]; then
    echo "❌ No search criteria specified. Use --text, --id, --desc, or --class"
    show_usage
    exit 1
fi

echo "🔍 Searching for element with $SEARCH_TYPE='$SEARCH_VALUE' (index: $ELEMENT_INDEX)"

# Extract bounds for the matching element using xmllint and awk
# The bounds format is: bounds="[left,top][right,bottom]"
BOUNDS=$(xmllint --xpath "//*[@${SEARCH_TYPE}='${SEARCH_VALUE}'][position()=$((ELEMENT_INDEX + 1))]/@bounds" ./ui-dump.xml 2>/dev/null | sed 's/bounds="//; s/"$//' || echo "")

if [[ -z "$BOUNDS" ]]; then
    echo "❌ Element not found with $SEARCH_TYPE='$SEARCH_VALUE' at index $ELEMENT_INDEX"
    echo "💡 Available elements:"
    xmllint --xpath "//*[@${SEARCH_TYPE}]/@${SEARCH_TYPE}" ./ui-dump.xml 2>/dev/null | grep -o "${SEARCH_TYPE}=\"[^\"]*\"" | head -10 || echo "   None found"
    exit 1
fi

echo "📍 Found element with bounds: $BOUNDS"

# Parse bounds: "[left,top][right,bottom]" -> left top right bottom
LEFT=$(echo "$BOUNDS" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\1/')
TOP=$(echo "$BOUNDS" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\2/')
RIGHT=$(echo "$BOUNDS" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\3/')
BOTTOM=$(echo "$BOUNDS" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\4/')

# Calculate center coordinates
CENTER_X=$(( (LEFT + RIGHT) / 2 ))
CENTER_Y=$(( (TOP + BOTTOM) / 2 ))

echo "🎯 Clicking at center: ($CENTER_X, $CENTER_Y)"

# Click the center of the element
if adb -s "$ANDROID_SERIAL" shell input tap "$CENTER_X" "$CENTER_Y"; then
    echo "✅ Successfully clicked element"
else
    echo "❌ Failed to click element"
    exit 1
fi

# Clean up
rm -f ./ui-dump.xml