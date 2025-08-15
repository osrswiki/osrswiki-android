#!/bin/bash
set -euo pipefail

# iOS Simulator session cleanup - equivalent to Android cleanup-session-device.sh
# Cleans up the iOS Simulator and session files created during development

echo "🧹 Cleaning up iOS Simulator session..."

# Load session environment if available
if [[ -f .claude-env ]]; then
    source .claude-env
    echo "📱 Loaded session environment"
    echo "   • Simulator: ${SIMULATOR_NAME:-unknown}"
    echo "   • UDID: ${IOS_SIMULATOR_UDID:-unknown}"
elif [[ -f .claude-session-simulator ]]; then
    echo "📱 Found legacy session format"
    SESSION_INFO=$(cat .claude-session-simulator)
    SIMULATOR_NAME=$(echo "$SESSION_INFO" | cut -d: -f1)
    IOS_SIMULATOR_UDID=$(echo "$SESSION_INFO" | cut -d: -f2)
    echo "   • Simulator: $SIMULATOR_NAME"
    echo "   • UDID: $IOS_SIMULATOR_UDID"
else
    echo "⚠️  No session information found"
    echo "💡 Specify simulator name and UDID manually:"
    echo "   $0 <simulator-name> <udid>"
    
    if [[ $# -eq 2 ]]; then
        SIMULATOR_NAME="$1"
        IOS_SIMULATOR_UDID="$2"
        echo "📱 Using provided parameters:"
        echo "   • Simulator: $SIMULATOR_NAME"
        echo "   • UDID: $IOS_SIMULATOR_UDID"
    else
        echo "❌ No session or parameters provided"
        exit 1
    fi
fi

# Ensure we're on macOS (required for iOS development)
if [[ "$(uname)" != "Darwin" ]]; then
    echo "❌ iOS development requires macOS. Current platform: $(uname)"
    exit 1
fi

# Check if simulator exists
if ! xcrun simctl list devices | grep -q "$IOS_SIMULATOR_UDID"; then
    echo "⚠️  Simulator $SIMULATOR_NAME ($IOS_SIMULATOR_UDID) not found"
    echo "💡 It may have already been deleted"
else
    # Shutdown simulator if running
    echo "🛑 Shutting down simulator..."
    xcrun simctl shutdown "$IOS_SIMULATOR_UDID" 2>/dev/null || true
    
    # Delete simulator
    echo "🗑️  Deleting simulator: $SIMULATOR_NAME"
    xcrun simctl delete "$IOS_SIMULATOR_UDID"
    
    if [[ $? -eq 0 ]]; then
        echo "✅ Simulator deleted successfully"
    else
        echo "❌ Failed to delete simulator"
        echo "💡 Try manually: xcrun simctl delete $IOS_SIMULATOR_UDID"
    fi
fi

# Clean up session files
echo "🧹 Cleaning session files..."

# Remove session files
files_to_remove=(
    ".claude-session-simulator"
    ".claude-simulator-udid" 
    ".claude-simulator-name"
    ".claude-bundle-id"
    ".claude-env"
)

for file in "${files_to_remove[@]}"; do
    if [[ -f "$file" ]]; then
        rm "$file"
        echo "   • Removed $file"
    fi
done

# Clean up screenshots directory (optional, with confirmation)
if [[ -d "screenshots" ]] && [[ -n "$(ls -A screenshots 2>/dev/null)" ]]; then
    echo ""
    echo "📸 Screenshots directory contains files:"
    ls -la screenshots/ | head -5
    
    if [[ "${AUTO_CLEAN_SCREENSHOTS:-}" == "true" ]]; then
        echo "🧹 Auto-cleaning screenshots..."
        rm -rf screenshots/
        echo "   • Screenshots directory removed"
    else
        echo "💡 To clean screenshots: rm -rf screenshots/"
        echo "💡 Or set AUTO_CLEAN_SCREENSHOTS=true for automatic cleanup"
    fi
fi

# Clean up any derived data or build artifacts
echo "🧹 Cleaning iOS build artifacts..."
if [[ -d "platforms/ios/build" ]]; then
    rm -rf "platforms/ios/build"
    echo "   • Removed iOS build directory"
fi

if [[ -d "platforms/ios/DerivedData" ]]; then
    rm -rf "platforms/ios/DerivedData"
    echo "   • Removed DerivedData directory"
fi

echo ""
echo "✅ iOS Simulator session cleanup complete!"
echo ""
echo "💡 The simulator has been deleted and session files cleaned up."
echo "💡 To start a new session: ./scripts/ios/setup-session-simulator.sh"