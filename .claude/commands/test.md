# Test Command

Run the complete quality gate suite before pushing changes with intelligent platform detection.

## Platform Detection

Claude automatically detects the target platform:

1. **Check `.claude-platform` file** if it exists (created by `/start`)
2. **Check for active session files**:
   - `.claude-session-device` exists → Android platform
   - `.claude-session-simulator` exists → iOS platform  
   - Both exist → Cross-platform session
3. **If unclear**, ask: "Which platform would you like to test: Android, iOS, or both?"

## Required Checks

Execute commands based on detected/selected platform.

**Note**: All commands below can be run from any directory as they include explicit `cd` commands to the correct platform directories.

### Android Tests

1. **Unit Tests**:
   ```bash
   cd platforms/android && ./gradlew testDebugUnitTest
   ```

2. **Static Analysis**:
   ```bash
   cd platforms/android && ./gradlew lintDebug detekt ktlintCheck
   ```

3. **Coverage Verification**:
   ```bash
   cd platforms/android && ./gradlew koverXmlReport koverVerify
   ```

4. **Build Verification**:
   ```bash
   cd platforms/android && ./gradlew assembleDebug
   ```

5. **Optional - UI Tests** (if UI changes made):
   ```bash
   cd platforms/android && ./gradlew connectedDebugAndroidTest
   ```

### iOS Tests

1. **Unit Tests**:
   ```bash
   cd platforms/ios && xcodebuild test -project OSRSWiki.xcodeproj -scheme OSRSWiki -destination 'platform=iOS Simulator,name=iPhone 15 Pro'
   ```

2. **UI Tests**:
   ```bash
   cd platforms/ios && xcodebuild test -project OSRSWiki.xcodeproj -scheme OSRSWikiUITests -destination 'platform=iOS Simulator,name=iPhone 15 Pro'
   ```

3. **Build Verification**:
   ```bash
   cd platforms/ios && xcodebuild build -project OSRSWiki.xcodeproj -scheme OSRSWiki -sdk iphonesimulator -configuration Debug
   ```

4. **Code Quality** (if SwiftLint configured):
   ```bash
   cd platforms/ios && swiftlint
   ```

5. **Optional - Performance Tests**:
   ```bash
   cd platforms/ios && xcodebuild test -project OSRSWiki.xcodeproj -scheme OSRSWiki -destination 'platform=iOS Simulator,name=iPhone 15 Pro' -only-testing:OSRSWikiTests/testPerformanceExample
   ```

### Cross-Platform Tests

For both platforms, execute both test suites sequentially:

1. **Run Android tests first** (faster feedback)
2. **Run iOS tests second** (requires macOS)
3. **Compare results** for feature parity
4. **Verify shared components** work consistently

## On Success
```bash
# From worktree session directory:
# Stage changes
git add -p
# Commit with proper format
# Push to remote branch
```

## On Failure
- Fix issues before proceeding
- Re-run affected checks
- Do not bypass unless using `[WIP]` commit with TODO ticket

This command ensures code quality standards are met before any push to remote repository.