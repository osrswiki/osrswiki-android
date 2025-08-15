#!/bin/bash
set -euo pipefail

echo "📱 Cleaning up Android session device..."

# Read session info if available
if [[ -f .claude-session-device ]]; then
    # Use individual files if available (simpler), fallback to parsing if needed
    if [[ -f .claude-emulator-name ]] && [[ -f .claude-device-serial ]]; then
        EMULATOR_NAME=$(cat .claude-emulator-name)
        DEVICE_SERIAL=$(cat .claude-device-serial)
    else
        # Fallback to parsing for compatibility with older sessions
        SESSION_INFO=$(cat .claude-session-device)
        cat .claude-session-device > /tmp/session_info.txt
        cut -d: -f1 /tmp/session_info.txt > /tmp/emulator_name.txt
        cut -d: -f2 /tmp/session_info.txt > /tmp/device_serial.txt
        EMULATOR_NAME=$(cat /tmp/emulator_name.txt)
        DEVICE_SERIAL=$(cat /tmp/device_serial.txt)
        rm -f /tmp/session_info.txt /tmp/emulator_name.txt /tmp/device_serial.txt
    fi
    
    echo "📱 Found session device: $DEVICE_SERIAL"
    echo "📱 Found emulator: $EMULATOR_NAME"
    
    # Kill emulator if running
    if adb devices | grep -q "$DEVICE_SERIAL"; then
        echo "🛑 Stopping emulator..."
        adb -s "$DEVICE_SERIAL" emu kill || true
        sleep 2
    fi
    
    # Delete AVD
    echo "🗑️ Removing emulator AVD..."
    avdmanager delete avd -n "$EMULATOR_NAME" || true
    
    # Clean up session device files
    rm -f .claude-session-device
    rm -f .claude-device-serial
    rm -f .claude-emulator-name
    rm -f .claude-app-id
    rm -f .claude-env
    
    # Clean up emulator logs
    echo "📝 Removing emulator logs..."
    rm -f emulator.out emulator.err
    
    # Clean up screenshots if directory exists
    if [[ -d screenshots ]]; then
        echo "📸 Cleaning up session screenshots..."
        ./scripts/android/clean-screenshots.sh --max-age 0 >/dev/null 2>&1 || true
        echo "📸 Screenshot cleanup complete"
    fi
    
    echo "✅ Android device cleanup complete"
else
    echo "⚠️ No session device file found, skipping device cleanup"
fi

echo "📱 Android session device cleanup finished!"