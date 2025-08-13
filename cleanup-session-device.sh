#!/bin/bash
set -euo pipefail

echo ">ù Cleaning up session..."

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
    
    echo "=ñ Found session device: $DEVICE_SERIAL"
    echo "=' Found emulator: $EMULATOR_NAME"
    
    # Kill emulator if running
    if adb devices | grep -q "$DEVICE_SERIAL"; then
        echo "=Ñ Stopping emulator..."
        adb -s "$DEVICE_SERIAL" emu kill || true
        sleep 2
    fi
    
    # Delete AVD
    echo "=Ñ Removing emulator AVD..."
    avdmanager delete avd -n "$EMULATOR_NAME" || true
    
    # Clean up session device files
    rm -f .claude-session-device
    rm -f .claude-device-serial
    rm -f .claude-emulator-name
    rm -f .claude-app-id
    rm -f .claude-env
    
    # Clean up emulator logs
    echo "=Ñ Removing emulator logs..."
    rm -f emulator.out emulator.err
    
    echo " Device cleanup complete"
else
    echo "  No session device file found, skipping device cleanup"
fi

# Clean up worktree (remove this session directory)
CURRENT_DIR=$(pwd)
SESSION_NAME=$(basename "$CURRENT_DIR")

if [[ "$SESSION_NAME" =~ ^claude-[0-9]{8}-[0-9]{6} ]]; then
    echo "<? Removing worktree session: $SESSION_NAME"
    
    # Go to main directory and remove this worktree
    cd ../main
    git worktree remove "../$SESSION_NAME" --force
    
    echo " Worktree cleanup complete"
    echo "=Á Returned to: $(pwd)"
else
    echo "  Not in a Claude session directory, skipping worktree cleanup"
    echo "Current directory: $CURRENT_DIR"
fi

echo "<‰ Session cleanup finished!"