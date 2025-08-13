#!/bin/bash
set -euo pipefail

# Architecture detection
if [[ $(uname -m) == "arm64" ]]; then
    IMG="system-images;android-34;google_apis;arm64-v8a"
else
    IMG="system-images;android-34;google_apis;x86_64"
fi

# Create session-specific emulator
SESSION_NAME="claude-$(date +%Y%m%d-%H%M%S)"
EMULATOR_NAME="test-$SESSION_NAME"

echo "Creating emulator: $EMULATOR_NAME"
avdmanager create avd -n "$EMULATOR_NAME" -k "$IMG" -d "pixel_4" -f

# Find free port function
pick_port() {
  for p in $(seq 5554 2 5584); do
    if ! (adb devices | grep -q "emulator-$p"); then
      echo $p; return
    fi
  done
  echo "No free emulator port" >&2; exit 1
}

EMU_PORT=$(pick_port)

# Generate random ADB server port (macOS compatible)
ADB_PORT=$((5037 + RANDOM % 1000))
export ADB_SERVER_PORT=$ADB_PORT

echo "Starting emulator on port: $EMU_PORT"
echo "ADB server port: $ADB_SERVER_PORT"

# Start ADB server for this session
adb start-server

# Start emulator
emulator -avd "$EMULATOR_NAME" -port "$EMU_PORT" \
  -no-snapshot-save -no-boot-anim -noaudio -wipe-data &

# Wait and set target
export ANDROID_SERIAL="emulator-$EMU_PORT"

# Wait for device to be fully ready
echo "Waiting for emulator to boot completely..."
adb -s "$ANDROID_SERIAL" wait-for-device

# Wait for system services to be ready
timeout=120
while [ $timeout -gt 0 ]; do
    if adb -s "$ANDROID_SERIAL" shell service list | grep -q "package" && \
       adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed | grep -q "1"; then
        echo "Android boot completed"
        break
    fi
    echo "Waiting for Android to finish booting... ($timeout seconds remaining)"
    sleep 3
    timeout=$((timeout - 3))
done

# Additional wait for package manager
echo "Waiting for package manager to be ready..."
timeout=60
while [ $timeout -gt 0 ]; do
    if adb -s "$ANDROID_SERIAL" shell pm list packages >/dev/null 2>&1; then
        echo "Package manager ready"
        break
    fi
    echo "Waiting for package manager... ($timeout seconds remaining)"
    sleep 2
    timeout=$((timeout - 2))
done

echo "Device ready: $ANDROID_SERIAL"
echo "To use this device, run: export ANDROID_SERIAL=$ANDROID_SERIAL"
echo "To clean up, run: scripts/cleanup-session-device.sh $EMULATOR_NAME $ANDROID_SERIAL"

# Save session info for cleanup
echo "$EMULATOR_NAME:$ANDROID_SERIAL" > .claude-session-device