#!/bin/bash
set -euo pipefail

# iOS Simulator session setup - equivalent to Android device setup
# This script sets up an iOS Simulator for development sessions

echo "🍎 Setting up iOS Simulator session..."

# Environment detection
echo "🔍 Detecting runtime environment..."

# Detect if running on macOS (required for iOS development)
if [[ "$(uname)" != "Darwin" ]]; then
    echo "❌ iOS development requires macOS. Current platform: $(uname)"
    echo "💡 Consider using:"
    echo "   • GitHub Codespaces with macOS runners"
    echo "   • Remote Mac cloud services"
    echo "   • Local macOS machine"
    exit 1
fi

# Detect container environment
IS_CONTAINER_ENV=false
if [ -n "${IS_CONTAINER:-}" ] || [ -f /.dockerenv ] || [ -f /run/.containerenv ]; then
    IS_CONTAINER_ENV=true
    echo "🐳 Container environment detected (unusual for iOS development)"
else
    echo "💻 macOS host environment detected"
fi

# Check for Xcode and required tools
echo "🔧 Checking iOS development tools..."

# Check for Xcode Command Line Tools
if ! command -v xcodebuild >/dev/null 2>&1; then
    echo "❌ Xcode Command Line Tools not found"
    echo "💡 Install with: xcode-select --install"
    exit 1
fi

# Check for xcrun simctl
if ! command -v xcrun >/dev/null 2>&1; then
    echo "❌ xcrun not found (part of Xcode Command Line Tools)"
    exit 1
fi

# Check for iOS Simulator
if ! xcrun simctl list devices >/dev/null 2>&1; then
    echo "❌ iOS Simulator not available"
    echo "💡 Install Xcode from the App Store or developer.apple.com"
    exit 1
fi

echo "✅ iOS development tools found"

# Detect if we're in a session directory or create new session name
CURRENT_DIR=$(basename "$(pwd)")
if [[ "$CURRENT_DIR" =~ ^claude-[0-9]{8}-[0-9]{6} ]]; then
    SESSION_NAME="$CURRENT_DIR"
    echo "📁 Using existing session: $SESSION_NAME"
else
    SESSION_NAME="claude-$(date +%Y%m%d-%H%M%S)"
    echo "📁 Creating new session: $SESSION_NAME"
fi

# iOS version and device selection
echo "🔍 Detecting available iOS runtimes and devices..."

# Get latest iOS runtime
LATEST_IOS_RUNTIME=$(xcrun simctl list runtimes | grep "iOS" | tail -1 | sed 's/.*iOS \([0-9.]*\).*/\1/' || echo "17.0")
echo "📱 Using iOS runtime: $LATEST_IOS_RUNTIME"

# Choose device type based on preference (iPhone 15 Pro for latest features, fall back to available)
DEVICE_TYPES=(
    "iPhone 15 Pro"
    "iPhone 14 Pro"
    "iPhone 13 Pro"
    "iPhone 12 Pro"
    "iPhone 11"
)

DEVICE_TYPE=""
for device in "${DEVICE_TYPES[@]}"; do
    if xcrun simctl list devicetypes | grep -q "\"$device\""; then
        DEVICE_TYPE="$device"
        echo "📱 Selected device: $DEVICE_TYPE"
        break
    fi
done

if [[ -z "$DEVICE_TYPE" ]]; then
    # Fall back to any available iPhone
    DEVICE_TYPE=$(xcrun simctl list devicetypes | grep iPhone | head -1 | sed 's/.*(\(.*\))/\1/' || echo "com.apple.CoreSimulator.SimDeviceType.iPhone-15-Pro")
    echo "📱 Using fallback device type: $DEVICE_TYPE"
fi

# Create simulator name
SIMULATOR_NAME="osrswiki-$SESSION_NAME"
echo "📱 Creating simulator: $SIMULATOR_NAME"

# Create the simulator device
SIMULATOR_UDID=$(xcrun simctl create "$SIMULATOR_NAME" "$DEVICE_TYPE" "iOS$LATEST_IOS_RUNTIME" 2>/dev/null || echo "")

if [[ -z "$SIMULATOR_UDID" ]]; then
    echo "⚠️  Failed to create simulator with latest runtime, trying without version..."
    SIMULATOR_UDID=$(xcrun simctl create "$SIMULATOR_NAME" "$DEVICE_TYPE" | grep -E "[A-F0-9]{8}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{12}" || echo "")
fi

if [[ -z "$SIMULATOR_UDID" ]]; then
    echo "❌ Failed to create iOS Simulator"
    echo "💡 Available device types:"
    xcrun simctl list devicetypes | grep iPhone | head -5
    echo "💡 Available runtimes:"
    xcrun simctl list runtimes | grep iOS | head -3
    exit 1
fi

echo "✅ Simulator created with UDID: $SIMULATOR_UDID"

# Boot the simulator
echo "🚀 Booting iOS Simulator..."
xcrun simctl boot "$SIMULATOR_UDID" >/dev/null 2>&1 || true

# Wait for simulator to boot
echo "⏳ Waiting for simulator to boot..."
timeout=120
while [ $timeout -gt 0 ]; do
    if xcrun simctl list devices | grep "$SIMULATOR_UDID" | grep -q "Booted"; then
        echo "✅ Simulator booted successfully"
        break
    fi
    echo "Waiting for simulator to boot... ($timeout seconds remaining)"
    sleep 2
    timeout=$((timeout - 2))
done

if [ $timeout -le 0 ]; then
    echo "⚠️  Simulator boot timeout, but device may still be functional"
fi

# Open Simulator app to show the device
echo "📱 Opening Simulator app..."
open -a Simulator >/dev/null 2>&1 || echo "⚠️  Could not open Simulator app GUI"

# Save session information for cleanup and environment
echo "💾 Saving session information..."

# Save session info for cleanup
echo "$SIMULATOR_NAME:$SIMULATOR_UDID" > .claude-session-simulator

# Save individual values for easy access (avoids command substitution)
echo "$SIMULATOR_UDID" > .claude-simulator-udid
echo "$SIMULATOR_NAME" > .claude-simulator-name

# Get bundle identifier for the app
BUNDLE_ID="com.omiyawaki.osrswiki"
echo "$BUNDLE_ID" > .claude-bundle-id

# Create session environment file
echo "# Claude Code iOS session environment - source this file instead of using exports with command substitution" > .claude-env
echo "export IOS_SIMULATOR_UDID=\"$SIMULATOR_UDID\"" >> .claude-env
echo "export SIMULATOR_NAME=\"$SIMULATOR_NAME\"" >> .claude-env
echo "export BUNDLE_ID=\"$BUNDLE_ID\"" >> .claude-env
echo "export IS_CONTAINER_ENV=\"$IS_CONTAINER_ENV\"" >> .claude-env
echo "export DEVICE_TYPE=\"$DEVICE_TYPE\"" >> .claude-env
echo "export IOS_RUNTIME=\"$LATEST_IOS_RUNTIME\"" >> .claude-env
echo "# iOS Session created: $(date)" >> .claude-env

echo ""
echo "✅ iOS Simulator session ready!"
echo ""
echo "📱 Device Details:"
echo "   • Name: $SIMULATOR_NAME"
echo "   • UDID: $SIMULATOR_UDID"
echo "   • Type: $DEVICE_TYPE"
echo "   • iOS: $LATEST_IOS_RUNTIME"
echo ""
echo "💡 Usage:"
echo "   source .claude-env                    # Load environment variables"
echo "   ./scripts/ios/quick-test.sh          # Build and deploy app"
echo "   ./scripts/ios/take-screenshot.sh     # Take screenshot"
echo ""
echo "🧹 To clean up this session:"
echo "   ./scripts/ios/cleanup-session-simulator.sh"
echo ""

# Final status check
if xcrun simctl list devices | grep "$SIMULATOR_UDID" | grep -q "Booted"; then
    echo "🎉 Session setup complete! Simulator is ready for development."
else
    echo "⚠️  Session setup complete, but simulator may still be booting."
    echo "   Wait a moment and check Simulator app."
fi