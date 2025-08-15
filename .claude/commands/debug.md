# Debug Command

Check session health and troubleshoot issues with platform-aware debugging.

## Platform Detection

Claude automatically determines which debugging commands to show:

1. **Check for active session files**:
   - `.claude-session-device` exists → Show Android debugging
   - `.claude-session-simulator` exists → Show iOS debugging
   - Both exist → Show both sets of commands
2. **Check `.claude-platform` file** for intended platform
3. **If no session active**, show both Android and iOS debugging options

## Android Debugging

### Emulator Logs
When experiencing Android device issues, check the emulator logs:

```bash
# From worktree session directory (where emulator files are located):
# View recent emulator output
tail -20 emulator.out

# Check for emulator errors
grep -i error emulator.err

# Monitor emulator in real-time
tail -f emulator.out

# From any directory:
# Check if emulator is running
ps aux | grep emulator | grep -v grep
```

### Android Device Status
```bash
# Verify device connection
adb devices

# Check device responsiveness
adb -s "$ANDROID_SERIAL" shell echo "Device responding"

# Check device details
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.release  # Android version
adb -s "$ANDROID_SERIAL" shell getprop ro.product.model          # Device model
```

### Android Session Files
- `.claude-session-device` - Contains emulator name and serial
- `emulator.out` - Emulator standard output
- `emulator.err` - Emulator error messages

These files are automatically cleaned up by `cleanup-session-device.sh`.

## iOS Debugging

### Simulator Status
```bash
# Check if simulator is running
xcrun simctl list devices | grep "$IOS_SIMULATOR_UDID"

# Check simulator status
xcrun simctl list devices | grep -A1 -B1 "$SIMULATOR_NAME"

# View simulator details
xcrun simctl list devices | grep "Booted"

# Check simulator hardware info
xcrun simctl spawn "$IOS_SIMULATOR_UDID" uname -a
```

### iOS Simulator Logs
```bash
# View iOS system logs
xcrun simctl spawn "$IOS_SIMULATOR_UDID" log stream

# View app-specific logs (filter by bundle ID)
xcrun simctl spawn "$IOS_SIMULATOR_UDID" log stream --predicate 'process == "OSRSWiki"'

# View crash logs
ls ~/Library/Logs/DiagnosticReports/ | grep -i osrswiki

# Check iOS version and device info
xcrun simctl spawn "$IOS_SIMULATOR_UDID" sw_vers
```

### iOS Session Files
- `.claude-session-simulator` - Contains simulator name and UDID
- `.claude-simulator-udid` - iOS Simulator device identifier
- `.claude-simulator-name` - iOS Simulator instance name
- `.claude-bundle-id` - iOS Bundle identifier

These files are automatically cleaned up by `cleanup-session-simulator.sh`.

## Common Issues

### Android Issues

#### Emulator Won't Start
```bash
# Check for port conflicts
netstat -an | grep 5554

# Look for specific errors
grep -i "error\|fail\|crash" emulator.err

# Check emulator binary
which emulator
```

#### Android App Installation Fails
```bash
# From any directory:
# Check available space
adb -s "$ANDROID_SERIAL" shell df /data

# From worktree session directory:
# Verify APK exists
ls -la platforms/android/app/build/outputs/apk/debug/app-debug.apk

# Check installation errors
adb -s "$ANDROID_SERIAL" install -r platforms/android/app/build/outputs/apk/debug/app-debug.apk

# From any directory:
# Uninstall and reinstall
adb -s "$ANDROID_SERIAL" uninstall "$APPID"
```

#### Android Build Issues
```bash
# Clean and rebuild
cd platforms/android && ./gradlew clean assembleDebug

# Check for dependency issues
cd platforms/android && ./gradlew dependencies

# Gradle daemon issues
cd platforms/android && ./gradlew --stop
```

### iOS Issues

#### Simulator Won't Start
```bash
# Check for running simulators
xcrun simctl list devices | grep Booted

# Try booting manually
xcrun simctl boot "$IOS_SIMULATOR_UDID"

# Check Xcode installation
xcode-select -p
xcrun --show-sdk-path --sdk iphonesimulator
```

#### iOS App Installation Fails
```bash
# From worktree session directory:
# Check if app exists
ls -la platforms/ios/build/Build/Products/Debug-iphonesimulator/OSRSWiki.app

# From any directory:
# Uninstall and reinstall
xcrun simctl uninstall "$IOS_SIMULATOR_UDID" "$BUNDLE_ID"
xcrun simctl install "$IOS_SIMULATOR_UDID" <path-to-app>

# Reset simulator completely
xcrun simctl erase "$IOS_SIMULATOR_UDID"
```

#### iOS Build Issues
```bash
# From worktree session directory:
# Clean build artifacts
rm -rf platforms/ios/build platforms/ios/DerivedData

# Clean using Xcode
cd platforms/ios && xcodebuild clean

# Check scheme and target
cd platforms/ios && xcodebuild -list

# Build with verbose output
cd platforms/ios && xcodebuild -project OSRSWiki.xcodeproj -scheme OSRSWiki -configuration Debug -verbose
```

### Cross-Platform Issues

#### Environment Variables Not Set
```bash
# From worktree session directory:
# Check platform indicator
cat .claude-platform

# Reload environment
source .claude-env

# From any directory:
# Check specific variables
echo "Android: $ANDROID_SERIAL"
echo "iOS: $IOS_SIMULATOR_UDID"
```

#### Session Files Corrupted
```bash
# From worktree session directory:
# Clean and restart session
./cleanup-session-device.sh     # If Android
./cleanup-session-simulator.sh  # If iOS

# From main repository root:
cd ~/Develop/osrswiki && git worktree remove ~/Develop/osrswiki-sessions/<session-name>
```