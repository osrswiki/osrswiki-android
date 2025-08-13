#!/bin/bash
set -euo pipefail
if [[ ! -f .claude-session-device ]]; then
    echo "❌ No active session. Run ./scripts/start-session.sh first"
    exit 1
fi

export ANDROID_SERIAL=$(cat .claude-session-device | cut -d: -f2)
echo "🔨 Quick build and test on device: $ANDROID_SERIAL"

./gradlew assembleDebug
APPID=$(scripts/get-app-id.sh)
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
MAIN=$(adb -s "$ANDROID_SERIAL" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$APPID" | tail -n1)
adb -s "$ANDROID_SERIAL" shell am start -W -n "$MAIN"
echo "✅ Quick test completed!"