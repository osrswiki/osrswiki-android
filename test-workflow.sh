#!/bin/bash
set -euo pipefail

echo "🚀 Testing Claude Code Workflow"
echo "📱 Starting session device..."
./scripts/setup-session-device.sh

export ANDROID_SERIAL=$(cat .claude-session-device | cut -d: -f2)
echo "📱 Using device: $ANDROID_SERIAL"

echo "🔨 Building app..."
./gradlew assembleDebug

echo "📦 Installing app..."
APPID=$(scripts/get-app-id.sh)
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk

echo "🚀 Launching app..."
MAIN=$(adb -s "$ANDROID_SERIAL" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$APPID" | tail -n1)

if [[ -n "$MAIN" && "$MAIN" != "No"* ]]; then
    adb -s "$ANDROID_SERIAL" shell am start -W -n "$MAIN"
    echo "✅ App launched successfully: $MAIN"
    
    adb -s "$ANDROID_SERIAL" shell screencap -p /sdcard/success.png
    adb -s "$ANDROID_SERIAL" pull /sdcard/success.png ./workflow_test_success.png
    echo "✅ Screenshot saved: workflow_test_success.png"
else
    echo "❌ Failed to launch app"
    exit 1
fi

echo "🧹 Cleaning up..."
./scripts/cleanup-session-device.sh
echo "🎉 Workflow test completed successfully!"