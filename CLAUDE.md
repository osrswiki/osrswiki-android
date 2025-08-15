# Claude Code Agent Directives — OSRS Wiki Monorepo

## 🚨 CRITICAL SAFETY RULES — READ FIRST 🚨

### ❌ NEVER DO THESE OPERATIONS
- **NEVER** push directly to `android` or `ios` remotes (use safe deployment scripts)
- **NEVER** create worktrees inside main repository directory
- **NEVER** force push without verification
- **NEVER** run `git subtree push` manually
- **NEVER** mix deployment repos with source repos

### ✅ ALWAYS DO THESE
- **ALWAYS** create worktrees in `~/Develop/osrswiki-sessions/`
- **ALWAYS** use deployment scripts in `~/Deploy/` for android/ios deployment
- **ALWAYS** run health checks: `./scripts/shared/validate-repository-health.sh`
- **ALWAYS** use deployment scripts: `./scripts/shared/deploy-android.sh`
- **ALWAYS** validate merges: `./scripts/shared/validate-merge-operation.sh <branch>`
- **ALWAYS** verify post-merge: `./scripts/shared/validate-post-merge.sh`

### 🏗️ NEW DIRECTORY STRUCTURE (Post-Reorganization)
```
/Users/miyawaki/
├── Develop/
│   ├── osrswiki/                    # Main monorepo (source of truth)
│   └── osrswiki-sessions/           # ALL worktrees go here (never in main repo)
│       ├── claude-YYYYMMDD-topic1/
│       └── claude-YYYYMMDD-topic2/
└── Deploy/
    ├── osrswiki-android/            # Android deployment repo (use deploy scripts)
    ├── osrswiki-ios/                # iOS deployment repo (use deploy scripts)
    └── osrswiki-tooling/            # Private tooling deployment repo
```

### 🔒 SAFETY INFRASTRUCTURE
- **Pre-push hooks**: Block dangerous pushes automatically
- **Validation scripts**: Check repository health before operations
- **Deployment isolation**: Physical separation prevents contamination
- **Recovery procedures**: Documented in RECOVERY.md

### 🤖 AGENT SAFETY & MERGE PROCEDURES
- **Agent Capability Limits**: worktree-manager has NO merge capabilities (Bash, Read, Write, LS only)
- **Merge Agent Required**: Use commit-helper or implementer agents for merge operations
- **Pre-Merge Validation**: Run `./scripts/shared/validate-merge-operation.sh <branch>` 
- **Post-Merge Verification**: Run `./scripts/shared/validate-post-merge.sh`
- **Agent File Prevention**: Validation blocks .claude/agents/*.md files from commits
- **Operation Verification**: Scripts detect merge-then-reset sequences and false success reports

## Project Structure
This is a monorepo containing:
- `platforms/android/` - Android app (Kotlin/Gradle)
- `platforms/ios/` - iOS app (Swift/Xcode)
- `shared/` - Cross-platform components
- `tools/` - Asset generation and development tools
- `scripts/` - Development and automation scripts
  - `scripts/android/` - Android-specific development scripts
  - `scripts/ios/` - iOS-specific development scripts
  - `scripts/shared/` - Cross-platform development scripts

## Critical Requirements

### Environment Assumptions
- Start directory: Project root (where this CLAUDE.md is located)
- Android app: `./platforms/android/` subdirectory
- Work directory: Session-specific worktree (agent creates)
- **MUST** use Gradle wrapper: `./gradlew` (never `gradle`)
- **MUST** use session-specific device isolation
- **MUST** enable build optimizations in `gradle.properties`:
  ```
  org.gradle.configuration-cache=true
  org.gradle.parallel=true
  ```

### Session Creation (Required First Step)
```bash
# Create worktree session (run from project root)
TOPIC="<short-topic>"  # e.g., "search-ui", "api-refactor"
./scripts/shared/create-worktree-session.sh "$TOPIC"
cd ~/Develop/osrswiki-sessions/claude-$(date +%Y%m%d-%H%M%S)-$TOPIC

# Setup session device (auto-detects environment)
./setup-session-device.sh

# Load environment variables
source .claude-env

# Verify session isolation
pwd  # Should show: ~/Develop/osrswiki-sessions/claude-YYYYMMDD-HHMMSS-<topic>
adb devices | grep "$ANDROID_SERIAL"
```

### Required App Identifiers
```bash
APPID=$(cd platforms/android && ./gradlew :app:properties -q | awk -F': ' '/applicationId/ {print $2; exit}')
MAIN=$(adb -s "$ANDROID_SERIAL" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$APPID" \
  | tail -n1)
```

## Git Requirements

### Branching (Mandatory)
- **NEVER** commit to `main` during active sessions
- **MUST** create topic branch:
  ```bash
  TOPIC="<short-topic>"
  git fetch origin
  git checkout -b "claude/$(date +%Y%m%d-%H%M%S)-$TOPIC" origin/main
  ```

### Commit Format (Required)
```
<type>(<scope>): <summary>

Why: <one sentence explaining rationale>
Tests: unit|ui|manual|none
```
Valid types: `feat, fix, refactor, chore, build, ci, docs, test, revert`

### Staging & Pushing
- **MUST** stage with: `git add -p`
- **MUST** push after unit tests pass
- For incomplete work, prefix with `[WIP]`

### Safe Merge Procedure (Critical)
**NEVER** use worktree-manager agent for merges. Use commit-helper or implementer agents.

```bash
# 1. Validate before merge (from main repo, not worktree)
cd /Users/miyawaki/Develop/osrswiki
./scripts/shared/validate-merge-operation.sh claude/YYYYMMDD-HHMMSS-topic

# 2. Perform merge only if validation passes
git merge --no-ff claude/YYYYMMDD-HHMMSS-topic

# 3. Immediately validate post-merge
./scripts/shared/validate-post-merge.sh

# 4. If validation fails, investigate immediately
# 5. Only clean up worktree after successful merge validation
```

**Critical Checks**:
- ✅ Agent files (.claude/agents/*.md) excluded from commits  
- ✅ Merge actually succeeded (not followed by reset)
- ✅ Correct files committed with correct commit messages
- ✅ Working directory clean after merge
- ✅ Still on main branch after merge

## Build & Quality Gates (Mandatory)

### Pre-Push Requirements
```bash
cd platforms/android && ./gradlew testDebugUnitTest lintDebug check
```

### Standard Build Tasks
```bash
cd platforms/android
./gradlew assembleDebug
./gradlew uninstallDebug installDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
./gradlew check
```

### Coverage Gate
- **RECOMMENDED** maintain good test coverage
- Coverage tools not currently configured
- Can bypass for development commits

## Device Management (Critical)

### Device Setup
```bash
# Verify device
adb devices
echo "Using device: $ANDROID_SERIAL"

# App management
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" shell am start -W -n "$MAIN"
```

### Screenshots (Organized Management)
```bash
# Take organized screenshot with description
./scripts/android/take-screenshot.sh "search-results"
# Output: screenshots/20250814-151305-search-results.png

# Take quick screenshot (auto-named)
./scripts/android/take-screenshot.sh
# Output: screenshots/20250814-151310-screenshot.png

# List all screenshots with details
./scripts/android/clean-screenshots.sh --list

# Clean screenshots older than 24 hours (default)
./scripts/android/clean-screenshots.sh

# Clean screenshots older than 8 hours
./scripts/android/clean-screenshots.sh --max-age 8

# Preview what would be cleaned (dry run)
./scripts/android/clean-screenshots.sh --dry-run
```

**Organization Benefits:**
- All screenshots stored in `screenshots/` directory within worktree
- Automatic cleanup when worktree is removed
- Timestamped filenames with optional descriptions
- No git pollution (screenshots/ is gitignored)
- Session metadata tracking for debugging

### Input Commands
```bash
adb -s "$ANDROID_SERIAL" shell input text "text%shere"  # Use %s for spaces
adb -s "$ANDROID_SERIAL" shell input keyevent 66        # Enter
adb -s "$ANDROID_SERIAL" shell input tap <x> <y>
adb -s "$ANDROID_SERIAL" shell input keyevent 4         # Back
```

### App State Reset
```bash
adb -s "$ANDROID_SERIAL" shell pm clear "$APPID"
```

## iOS Development (macOS Required)

### iOS Session Creation (Required First Step)
```bash
# Create worktree session (run from project root)
TOPIC="<short-topic>"  # e.g., "search-ui", "api-refactor"
./scripts/shared/create-worktree-session.sh "$TOPIC"
cd "claude-$(date +%Y%m%d-%H%M%S)-$TOPIC"

# Setup iOS Simulator (macOS only)
./setup-session-simulator.sh

# Load environment variables
source .claude-env

# Verify session isolation
pwd  # Should show: <project-root>/claude-YYYYMMDD-HHMMSS-<topic>
echo "Simulator: $SIMULATOR_NAME ($IOS_SIMULATOR_UDID)"
```

### Required iOS Identifiers
```bash
BUNDLE_ID=$(./get-bundle-id.sh)
echo "Bundle ID: $BUNDLE_ID"
```

### iOS Build & Test Requirements

#### Pre-Push Requirements (iOS)
```bash
cd platforms/ios && xcodebuild -project OSRSWiki.xcodeproj -scheme OSRSWiki -configuration Debug test
```

#### Standard iOS Build Tasks
```bash
cd platforms/ios

# Build for simulator
xcodebuild -project OSRSWiki.xcodeproj -scheme OSRSWiki -configuration Debug -sdk iphonesimulator build

# Install on simulator
xcrun simctl install "$IOS_SIMULATOR_UDID" <path-to-app>

# Launch app
xcrun simctl launch "$IOS_SIMULATOR_UDID" "$BUNDLE_ID"

# Run unit tests
xcodebuild -project OSRSWiki.xcodeproj -scheme OSRSWiki -configuration Debug test

# Run UI tests  
xcodebuild -project OSRSWiki.xcodeproj -scheme OSRSWiki -configuration Debug test -testPlan OSRSWikiUITests

# Code formatting (if SwiftFormat is configured)
swiftformat .
```

### iOS Simulator Management

#### Simulator Setup
```bash
# Verify simulator
xcrun simctl list devices
echo "Using simulator: $IOS_SIMULATOR_UDID"

# App management
xcrun simctl install "$IOS_SIMULATOR_UDID" <path-to-app>
xcrun simctl launch "$IOS_SIMULATOR_UDID" "$BUNDLE_ID"
```

#### iOS Screenshots (Organized Management)
```bash
# Take organized screenshot with description
./take-screenshot-ios.sh "search-results"
# Output: screenshots/20250814-151305-search-results.png

# Take quick screenshot (auto-named)
./take-screenshot-ios.sh
# Output: screenshots/20250814-151310-screenshot.png

# List all iOS screenshots with details
./clean-screenshots-ios.sh --list

# Clean iOS screenshots older than 24 hours (default)
./clean-screenshots-ios.sh

# Clean iOS screenshots older than 8 hours
./clean-screenshots-ios.sh --max-age 8

# Preview what would be cleaned (dry run)
./clean-screenshots-ios.sh --dry-run
```

#### iOS Simulator Input Commands
```bash
# Simulator supports touch interactions through GUI
# Use Xcode Simulator menu for device controls
# Or programmatic interaction through accessibility

# Home button equivalent
xcrun simctl spawn "$IOS_SIMULATOR_UDID" launchctl kickstart -k system/com.apple.springboard

# Device shake
xcrun simctl spawn "$IOS_SIMULATOR_UDID" /usr/libexec/devicectl device simulate shake

# Reset content and settings
xcrun simctl erase "$IOS_SIMULATOR_UDID"
```

### iOS Session Management

#### iOS Session Start Checklist
- [ ] Ensure macOS environment (iOS development requires macOS)
- [ ] Create worktree session with topic name
- [ ] Set up iOS Simulator for session
- [ ] Create timestamped branch
- [ ] Verify simulator connection: `xcrun simctl list devices | grep $IOS_SIMULATOR_UDID`
- [ ] Document goal in first commit

#### iOS Session End Requirements
- **Complete feature**: Run full test suite, create PR
- **Incomplete feature**: Push with detailed WIP commit

#### iOS Session Cleanup
```bash
./cleanup-session-simulator.sh
cd /Users/miyawaki/Develop/osrswiki  # Back to main repo
git worktree remove ~/Develop/osrswiki-sessions/claude-YYYYMMDD-HHMMSS-<topic> --force
```

### iOS Platform Requirements

#### Required Development Environment
- **macOS**: iOS development requires macOS (no Linux/Windows support)
- **Xcode**: Latest stable version recommended
- **iOS Simulator**: Included with Xcode
- **Command Line Tools**: `xcode-select --install`

#### iOS App Identifiers
- **Bundle ID**: `com.omiyawaki.osrswiki`
- **Team ID**: Set for development signing
- **App Name**: "OSRS Wiki"

### iOS Error Handling

#### Build Failures
1. Clean build folder: `rm -rf platforms/ios/build platforms/ios/DerivedData`
2. Run `xcodebuild clean`
3. Check simulator status
4. Verify Xcode project settings

#### Test Failures
1. Reset simulator: `xcrun simctl erase "$IOS_SIMULATOR_UDID"`
2. Restart simulator if needed
3. Check test isolation and scheme configuration

#### Simulator Issues
1. Verify `IOS_SIMULATOR_UDID` is set
2. Check simulator status: `xcrun simctl list devices`
3. Boot simulator if needed: `xcrun simctl boot "$IOS_SIMULATOR_UDID"`
4. Use session-specific simulator to avoid conflicts

## Environment & Navigation Best Practices

### Environment Variable Handling
**Avoid Command Substitution**: Use provided helper scripts to prevent permission dialogs:

```bash
# ❌ Avoid - triggers permission dialogs
export ANDROID_SERIAL=$(cat .claude-device-serial)

# ✅ Preferred - auto-sources environment
./run-with-env.sh adb install app.apk
```

**Session Scripts Auto-Source**: Key scripts now auto-load environment:
- `quick-test.sh` - Auto-sources `.claude-env` 
- All new helper scripts include environment loading
- No need to manually source before each command

### Robust UI Navigation
**Replace Fragile Taps**: Use UIAutomator-based scripts instead of coordinate taps:

```bash
# ❌ Fragile - breaks on different screen sizes
adb shell input tap 280 147

# ✅ Robust - finds elements by properties
./scripts/ui-click.sh --text "Search"
./scripts/ui-click.sh --id "com.example:id/button"
./scripts/click-by-text.sh "Navigate up"
```

**Navigation Helper Scripts**:
- `./scripts/ui-click.sh` - Click by text, ID, class, or description
- `./scripts/click-by-text.sh` - Simple text-based clicking
- `./scripts/search-wiki.sh` - Search for wiki pages
- `./scripts/navigate-to-page.sh` - Direct page navigation
- `./scripts/run-with-env.sh` - Run any command with session environment

**UIAutomator Debugging**:
```bash
# Dump UI hierarchy for inspection
./scripts/ui-click.sh --dump-only

# Find available elements by type
xmllint --xpath "//*[@text]/@text" ui-dump.xml
xmllint --xpath "//*[@resource-id]/@resource-id" ui-dump.xml
```

## Testing Requirements

### Unit Test Structure
- Location: `src/test/java/`
- Naming: `ComponentNameTest.kt`
- **MUST** include arrange/act/assert pattern

### Required Test Template
```kotlin
class ComponentTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun `methodName_condition_expectedResult`() {
        // Arrange
        // Act  
        // Assert
    }
}
```

### Instrumented Tests
- Location: `src/androidTest/java/`
- **MUST** use Espresso for UI tests
- **MUST** run with: `./gradlew connectedDebugAndroidTest`

## Container Environment (DevContainer/Docker)

### Container-Specific Features
- **Auto-Detection**: Scripts automatically detect container environment
- **GUI Support**: Emulator window displays on host via X11 forwarding (requires XQuartz on macOS)
- **Fixed Ports**: Uses emulator port 5554, ADB port 5037 (forwarded in devcontainer.json)
- **System Images**: android-35 with x86_64 architecture (optimized for containers)
- **Compilation**: compileSdk=36, targetSdk=35 (latest development tools, stable target)

### Container Session Workflow
```bash
# Standard workflow - same as host environment
./setup-session-device.sh  # Auto-detects container and configures appropriately
source .claude-env         # Load environment with container-specific variables
```

### Container Display Options
- **With X11**: Emulator GUI appears on host display (interactive debugging possible)
- **Headless**: Falls back to no-window mode if display unavailable
- **Automatic**: Scripts detect display availability and choose appropriate mode

### Container Troubleshooting
- **No GUI display**: Install XQuartz on macOS, ensure DISPLAY variable is set
- **Emulator not connecting**: Check port forwarding (5554, 5037) in devcontainer.json
- **ADB permission errors**: Container runs with fixed user permissions
- **Performance**: Container emulator uses software rendering (-gpu swiftshader_indirect)
- **Extended boot time**: Container emulator may take 2-3 minutes to boot completely

### API Version Strategy (2025)
```kotlin
android {
    compileSdk = 36      // Latest stable - access newest features
    targetSdk = 35       // Required by Google Play, stable/tested
    minSdk = 24          // Reasonable backward compatibility
}
```

## Session Management

### Session Start Checklist
- [ ] Create worktree session with topic name
- [ ] Set up isolated session device
- [ ] Create timestamped branch
- [ ] Verify device connection: `adb -s "$ANDROID_SERIAL" shell echo "Device ready"`
- [ ] Document goal in first commit

### Session End Requirements
- **Complete feature**: Run full test suite, create PR
- **Incomplete feature**: Push with detailed WIP commit

### Session Cleanup
```bash
./cleanup-session-device.sh
cd /Users/miyawaki/Develop/osrswiki  # Back to main repo
git worktree remove ~/Develop/osrswiki-sessions/claude-YYYYMMDD-HHMMSS-<topic> --force
```

## Security Requirements

### Prohibited
- **NEVER** commit: API keys, keystores, passwords
- **NEVER** commit to `main` during sessions
- **NEVER** use insecure test data

### Required
- Use `local.properties` for secrets
- Use `.gitignore` for temporary files
- Add `.editorconfig` if missing

## Safe Deployment & Validation (CRITICAL)

### Pre-Deployment Validation
```bash
# Check repository health before any deployment
./scripts/shared/validate-repository-health.sh

# Validate specific deployment target
./scripts/shared/validate-deployment.sh android  # or ios, tooling
```

### Safe Deployment Process
```bash
# Android deployment (NEVER use git subtree push directly)
./scripts/shared/deploy-android.sh

# iOS deployment (macOS only)  
./scripts/shared/deploy-ios.sh

# Monitor deployment status
ls -la ~/Deploy/osrswiki-android/  # Verify files
cd ~/Deploy/osrswiki-android && git log --oneline -5  # Check commits
```

### Recovery Procedures
- **If deployment fails**: Check logs and troubleshoot deployment scripts
- **If wrong content pushed**: Contact maintainer immediately
- **If git crisis**: Refer to RECOVERY.md for troubleshooting procedures

## Error Handling

### Build Failures
1. Disable configuration cache temporarily
2. Run `./gradlew clean`
3. Check device isolation
4. Verify Gradle wrapper usage

### Test Failures
1. Clear app state: `adb -s "$ANDROID_SERIAL" shell pm clear "$APPID"`
2. Restart device if needed
3. Check test isolation

### Device Issues
1. Verify `ANDROID_SERIAL` is set
2. Check device connection: `adb devices`
3. Use session-specific emulator if conflicts

## Remote Mobile Access (Tailscale) - Sudo-Free

### Overview
Secure remote access to your devcontainer from anywhere using Tailscale's mesh VPN. SSH from phones, tablets, or other devices with zero-config networking and no sudo requirements.

### Initial Setup (One-time)
```bash
# After devcontainer is built and running
./scripts/setup-mobile-access.sh

# Follow the authentication URL printed to console
# This creates a permanent Tailscale connection for your container
```

### Daily Usage
```bash
# Start Tailscale daemon (if not already running)
~/start-tailscale.sh

# Check connection status and get mobile connection info
~/tailscale-info.sh

# Quick aliases (available after sourcing .claude-env)
ts-start     # Start Tailscale daemon  
ts-status    # Show connection status and mobile connection commands
ts-ip        # Show Tailscale IP addresses only
```

### Mobile Device Setup
1. **Install Tailscale app** on phone/tablet (iOS/Android)
2. **Sign in** with the same account used for container setup
3. **Automatic discovery** - your container appears in the device list

### Connecting from Mobile
```bash
# Get connection details from container
ts-status

# From mobile SSH client (Termux, Blink, etc.):
ssh vscode@100.x.x.x        # Use the IP shown in ts-status
ssh vscode@osrs-dev-YYYYMMDD # Or use hostname

# For unstable connections (MOSH - more stable):
mosh --port=60000 vscode@100.x.x.x
```

### Mobile-Optimized Session
```bash
# Start optimized terminal session for mobile
./scripts/mobile-session.sh

# This provides:
# - Zellij/tmux multiplexer for persistent sessions
# - Mobile-friendly aliases and shortcuts  
# - Optimized terminal configuration
# - Quick access to common commands
```

### Mobile Development Workflow
```bash
# After connecting via SSH/MOSH from mobile:
source .claude-env              # Load session environment  
./scripts/mobile-session.sh     # Start mobile-optimized session

# Common mobile commands (available in mobile session):
claude                          # Start Claude Code
adb-devices                     # Check Android devices
gradle-build                    # Build the app  
gradle-test                     # Run unit tests
ts-status                       # Check Tailscale connection
```

### Advanced Features

#### Automated Setup (Optional)
```bash
# Set environment variable for automated authentication
export TS_AUTHKEY="tskey-auth-your-key-here"

# Tailscale will authenticate automatically on container start
# Get auth keys from: https://login.tailscale.com/admin/settings/keys
```

#### Multiple Device Access
- Multiple mobile devices can connect simultaneously
- Each gets independent SSH sessions
- Sessions persist across network changes (especially with MOSH)
- Share the same Android emulator and development environment

#### Security Features
- **Zero-config VPN**: No port forwarding or firewall configuration
- **WireGuard encryption**: Modern, secure tunneling protocol
- **Identity-based SSH**: No SSH key management needed
- **Userspace networking**: No root privileges required
- **Container isolation**: All access contained within devcontainer

### Troubleshooting

#### Container Issues
```bash
# Check if Tailscale is running
pgrep tailscaled

# View Tailscale logs
cat ~/.tailscale/tailscaled.log

# Restart Tailscale
~/stop-tailscale.sh && ~/start-tailscale.sh
```

#### Connection Issues
- **"Tailscale not authenticated"**: Run `ts-status` for auth URL
- **"No route to host"**: Ensure mobile device is connected to Tailscale
- **"Connection refused"**: Container may not be running or Tailscale down
- **Slow connections**: Use MOSH instead of SSH for unstable networks

### Benefits of Sudo-Free Setup
- **Secure**: Runs as regular user, no privilege escalation
- **Portable**: Works in restricted/corporate environments  
- **Simple**: No system service management required
- **Reliable**: User-space networking avoids kernel dependencies
- **Debuggable**: All state in user directory, easy troubleshooting

### Limitations
- Requires Tailscale account (free tier sufficient)
- Container restart requires manual Tailscale start
- GUI applications limited to terminal-based tools
- Network performance depends on Tailscale routing

## Pre-Release Checklist (Required)

### Android Checklist
- [ ] Unit tests pass: `./gradlew testDebugUnitTest`
- [ ] Static analysis clean: `./gradlew lintDebug check`
- [ ] All quality gates pass: `./gradlew check`
- [ ] UI tests pass or manual test documented
- [ ] Build succeeds: `./gradlew assembleDebug`
- [ ] No TODO/FIXME in changed lines
- [ ] Commit includes Why and Tests status

### iOS Checklist (macOS only)
- [ ] Unit tests pass: `xcodebuild test -project OSRSWiki.xcodeproj -scheme OSRSWiki`
- [ ] Code formatting clean (SwiftFormat if configured)
- [ ] Build succeeds: `xcodebuild build -project OSRSWiki.xcodeproj -scheme OSRSWiki -sdk iphonesimulator`
- [ ] UI tests pass or manual test documented
- [ ] No TODO/FIXME in changed lines
- [ ] Bundle ID consistent: check with `./get-bundle-id.sh`
- [ ] Commit includes Why and Tests status

### Cross-Platform Requirements
- [ ] Feature parity maintained between Android and iOS (where applicable)
- [ ] Shared components updated if modified
- [ ] Documentation updated for both platforms
- [ ] Platform-specific edge cases considered