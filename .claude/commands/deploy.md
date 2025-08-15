# Deploy Command

Intelligently deploy OSRS Wiki to appropriate repositories with comprehensive safety validation.

## Usage
```bash
/deploy
```

Claude will automatically detect the deployment target based on context and execute the appropriate deployment sequence with full safety validation.

## How it works
Claude will:
1. **Detect deployment target** automatically based on context
2. **Run comprehensive pre-deployment validation**
3. **Execute quality gates** for the detected platform(s)
4. **Deploy using deployment scripts**
6. **Verify deployment success** and provide summary

## Platform Detection

Claude automatically detects the deployment target using multiple indicators:

### Detection Priority:
1. **Session context** - Check `.claude-platform` file if it exists (from `/start`)
2. **Recent git activity** - Analyze recent commits for platform-specific changes
3. **Modified files** - Check staged/unstaged changes in `platforms/android` or `platforms/ios`
4. **Direct indicators** - Keywords in recent commit messages or branch names
5. **User prompt** - Ask user if detection is unclear

### Platform Indicators:
- **Android**: `platforms/android/`, `.gradle`, `.kt`, `AndroidManifest.xml`, "android", "kotlin", "gradle"
- **iOS**: `platforms/ios/`, `.swift`, `.xcodeproj`, "ios", "swift", "xcode", "simulator"
- **Tooling**: `tools/`, `scripts/`, "tools", "scripts", "assets", "build"
- **Cross-platform**: Changes in both platforms or "cross-platform" indicators
- **All**: Major releases, version bumps, or explicit "deploy all" requests

### Deployment Targets:
- **android** - Deploy Android app to public repository
- **ios** - Deploy iOS app to public repository (macOS only)
- **tooling** - Deploy complete monorepo to private repository
- **cross-platform** - Deploy both Android and iOS
- **all** - Deploy to all repositories (tooling, android, ios)

## Required Actions

### 1. Platform Detection and Validation

**⚠️ IMPORTANT**: Execute these steps in separate bash commands. DO NOT combine commands.

1. **Detect deployment target**:
   ```bash
   # From worktree session directory:
   # Check for session platform file
   if [ -f .claude-platform ]; then cat .claude-platform; fi
   ```
   
   ```bash
   # From worktree session directory:
   # Check recent git activity for platform indicators
   git log --oneline -10 --grep='android\|ios\|cross-platform\|deploy'
   ```
   
   ```bash
   # From worktree session directory:
   # Check modified files for platform context
   git diff --name-only HEAD~5..HEAD | grep -E '^platforms/(android|ios)' || echo "No platform-specific changes"
   ```

2. **Determine deployment scope** based on detection results:
   - If `.claude-platform` exists → use that platform
   - If recent changes in `platforms/android/` → set scope to "android"
   - If recent changes in `platforms/ios/` → set scope to "ios"
   - If changes in both platforms → set scope to "cross-platform"
   - If changes in `tools/` or `scripts/` → set scope to "tooling"
   - If unclear → ask user: "What would you like to deploy: android, ios, tooling, cross-platform, or all?"

### 2. Pre-Deployment Validation

Execute validation based on detected deployment scope:

**For All Deployments:**

3. **Repository health validation**:
   ```bash
   # From worktree session directory:
   ./scripts/shared/validate-repository-health.sh
   ```

4. **Check for uncommitted changes**:
   ```bash
   # From worktree session directory:
   git status --porcelain
   ```

**For Android Deployment:**

5. **Android quality gates**:
   ```bash
   cd platforms/android && ./gradlew testDebugUnitTest
   ```
   
   ```bash
   cd platforms/android && ./gradlew lintDebug detekt ktlintCheck
   ```
   
   ```bash
   cd platforms/android && ./gradlew koverXmlReport koverVerify
   ```
   
   ```bash
   cd platforms/android && ./gradlew assembleDebug
   ```

**For iOS Deployment (macOS only):**

6. **Verify macOS environment**:
   ```bash
   uname -s  # Should be Darwin
   ```
   
   ```bash
   xcode-select -p  # Verify Xcode installation
   ```

7. **iOS quality gates**:
   ```bash
   cd platforms/ios && xcodebuild test -project OSRSWiki.xcodeproj -scheme OSRSWiki
   ```
   
   ```bash
   cd platforms/ios && xcodebuild build -project OSRSWiki.xcodeproj -scheme OSRSWiki -sdk iphonesimulator
   ```

**For Tooling Deployment:**

8. **Tooling validation**:
   ```bash
   ls -la tools/ scripts/ shared/
   ```

### 3. User Confirmation

**⚠️ ALWAYS** ask for user confirmation before deployment:

9. **Display deployment plan**:
   ```bash
   # From worktree session directory:
   echo "🚀 Ready to deploy:"
   echo "Target: [detected-scope]"
   echo "Current commit: $(git log --oneline -1)"
   echo "Modified files: $(git diff --name-only HEAD~1..HEAD | wc -l) files"
   ```

10. **Confirm deployment**:
    Ask: "Proceed with deployment? This will push changes to remote repositories. (y/N)"
    - If No → Exit with message "Deployment cancelled"
    - If Yes → Continue to deployment execution

### 4. Deployment Execution

Execute deployment based on scope:

**For android scope:**

11. **Android deployment**:
    ```bash
    # From worktree session directory:
    ./scripts/shared/deploy-android.sh
    ```

**For ios scope:**

12. **iOS deployment**:
    ```bash
    # From worktree session directory:
    ./scripts/shared/deploy-ios.sh
    ```

**For tooling scope:**

13. **Tooling deployment**:
    ```bash
    # From worktree session directory:
    ./scripts/shared/deploy-tooling.sh
    ```

**For cross-platform scope:**

14. **Sequential platform deployment**:
    ```bash
    # From worktree session directory:
    ./scripts/shared/deploy-android.sh
    ```
    
    ```bash
    # From worktree session directory:
    ./scripts/shared/deploy-ios.sh
    ```

**For all scope:**

15. **Comprehensive deployment** (deploy all platforms sequentially):
    ```bash
    # From worktree session directory:
    ./scripts/shared/deploy-tooling.sh
    ./scripts/shared/deploy-android.sh  
    ./scripts/shared/deploy-ios.sh
    ```

### 5. Post-Deployment Verification

15. **Verify deployment success** (for each deployed platform):

    **Android verification:**
    ```bash
    # Verify Android deployment
    if ls ~/Deploy/osrswiki-android/ >/dev/null 2>&1; then
      cd ~/Deploy/osrswiki-android && git log --oneline -3
    else
      echo "Android deployment directory not found"
    fi
    ```

    **iOS verification:**
    ```bash
    # Verify iOS deployment
    if ls ~/Deploy/osrswiki-ios/ >/dev/null 2>&1; then
      cd ~/Deploy/osrswiki-ios && git log --oneline -3
    else
      echo "iOS deployment directory not found"
    fi
    ```

### 6. Deployment Summary

16. **Provide deployment summary**:
    ```bash
    # From worktree session directory:
    echo "🎉 Deployment completed successfully!"
    echo "Deployed scope: [actual-scope]"
    echo "Commit: $(git log --oneline -1)"
    echo "Timestamp: $(date)"
    ```

17. **Show repository links** (based on what was deployed):
    - Android: https://github.com/omiyawaki/osrswiki-android
    - iOS: https://github.com/omiyawaki/osrswiki-ios
    - Tooling: https://github.com/omiyawaki/osrswiki-tooling

## Error Handling

### Validation Failures
- **Repository health issues** → Display issues and ask if user wants to continue
- **Quality gate failures** → Stop deployment, fix issues first
- **Uncommitted changes** → Warn user, offer to commit or proceed without them
- **Environment issues** → Provide specific setup instructions

### Deployment Failures
- **Script execution failure** → Show error, provide rollback instructions
- **Network/authentication issues** → Provide troubleshooting steps

### Rollback Instructions
If deployment fails, provide rollback guidance:
```bash
# Check deployment repository status
cd ~/Deploy/osrswiki-[platform]
git status
git log --oneline -5
```

## Platform-Specific Notes

### Android Deployment
- Requires passing Android quality gates (tests, lint, coverage)
- Uses history-preserving deployment to maintain remote repository history
- Integrates shared components into Android app structure
- Creates Android-specific `.gitignore` for public repository

### iOS Deployment
- **macOS only** - iOS deployment requires macOS environment
- Requires Xcode and iOS Simulator setup
- Creates Swift bridge documentation for shared components
- Validates Xcode project integrity

### Tooling Deployment
- Deploys complete monorepo to private repository
- Includes all development tools and scripts
- Contains shared components and build automation
- Maintains private development workflows

### Cross-Platform Deployment
- Executes both Android and iOS deployments sequentially
- Ensures feature parity between platforms
- Validates shared components work consistently
- May take longer due to multiple deployment operations

## Safety Features

1. **Comprehensive Validation** - Multiple validation layers prevent broken deployments
2. **Quality Gates** - Platform-specific tests and checks ensure code quality
4. **User Confirmation** - Explicit confirmation required before pushing to remotes
5. **Isolation** - Uses safe deployment scripts with physical separation
6. **Rollback Support** - Clear rollback procedures if deployment fails
7. **Error Recovery** - Detailed error messages and recovery instructions

This command provides a comprehensive, safe, and intelligent deployment workflow that respects the project's safety-first approach while simplifying the deployment process for developers.