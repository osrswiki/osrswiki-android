# OSRS Wiki Deployment Guide

**Version**: 2.0 (Safe Deployment Architecture)  
**Date**: August 2025  
**Status**: Production-Ready

## Overview

This guide documents the safe deployment procedures implemented after the catastrophic git incident. The new deployment system prioritizes **validation**, **isolation**, and **rollback capability** to prevent destructive operations.

## 🚨 CRITICAL DEPLOYMENT RULES

### ❌ NEVER DO THESE OPERATIONS
```bash
# PROHIBITED - Direct subtree push (can destroy deployment repos)
git subtree push --prefix platforms/android android main
git subtree push --prefix platforms/ios ios main

# PROHIBITED - Manual deployment repo manipulation
cd ~/Deploy/osrswiki-android && git push origin main
cd ~/Deploy/osrswiki-ios && git push origin main

# PROHIBITED - Force push without backup
git push --force android main
git push --force ios main
```

### ✅ ALWAYS DO THESE
```bash
# REQUIRED - Use safe deployment scripts
./scripts/shared/deploy-android-safe.sh
./scripts/shared/deploy-ios-safe.sh

# REQUIRED - Validate before deployment
./scripts/shared/validate-repository-health.sh
./scripts/shared/validate-deployment.sh android

# REQUIRED - Backup before risky operations
./scripts/shared/emergency-backup.sh pre-deployment
```

## Deployment Architecture

### Directory Structure
```
/Users/miyawaki/
├── Develop/osrswiki/              # 🏠 Source repository (NEVER deploy from here)
└── Deploy/                        # 🚀 Deployment zone (isolated)
    ├── osrswiki-android/          # Android deployment repo
    ├── osrswiki-ios/              # iOS deployment repo
    └── osrswiki-tooling/          # Private tooling deployment repo
```

### Safety Layers
1. **Physical Separation**: Deployment repos physically separated from source
2. **Validation Gates**: Multiple validation steps before deployment
3. **Backup Strategy**: Automatic backups before deployment operations
4. **Rollback Capability**: Tested rollback procedures for failed deployments
5. **Git Hook Protection**: Prevents direct pushes to deployment remotes

## Android Deployment

### Prerequisites
```bash
# Verify you're in the main repository
pwd  # Should show: /Users/miyawaki/Develop/osrswiki

# Ensure repository health
./scripts/shared/validate-repository-health.sh

# Verify deployment target
./scripts/shared/validate-deployment.sh android

# Create backup (recommended)
./scripts/shared/emergency-backup.sh pre-android-deploy
```

### Safe Android Deployment Process

#### Step 1: Validate Source State
```bash
cd platforms/android

# Run quality gates (REQUIRED)
./gradlew testDebugUnitTest         # Unit tests must pass
./gradlew lintDebug                 # Static analysis clean
./gradlew detekt                    # Code quality check
./gradlew ktlintCheck               # Code style compliance
./gradlew koverVerify               # Coverage >= 65%

# Build verification
./gradlew assembleDebug             # Ensure buildable
```

#### Step 2: Execute Safe Deployment
```bash
cd /Users/miyawaki/Develop/osrswiki  # Return to root

# Execute safe deployment script
./scripts/shared/deploy-android-safe.sh

# Script performs:
# 1. Pre-deployment validation
# 2. Backup current state
# 3. Change to deployment directory
# 4. Fetch and merge from source
# 5. Push to android remote
# 6. Post-deployment verification
```

#### Step 3: Verify Deployment Success
```bash
# Check deployment repository state
cd ~/Deploy/osrswiki-android
git log --oneline -5               # Verify commits pushed
git status                         # Ensure clean state

# Verify remote synchronization
git fetch origin
git status                         # Should show "up to date"
```

### Android Deployment Script Details

#### deploy-android-safe.sh Flow
```bash
#!/bin/bash
# Safe Android deployment with validation

# Phase 1: Pre-deployment validation
echo "🔍 Validating repository health..."
./scripts/shared/validate-repository-health.sh || exit 1

echo "🔍 Validating Android deployment target..."
./scripts/shared/validate-deployment.sh android || exit 1

# Phase 2: Create safety backup
echo "💾 Creating pre-deployment backup..."
./scripts/shared/emergency-backup.sh android-deploy-$(date +%Y%m%d-%H%M%S)

# Phase 3: Change to deployment environment
echo "📁 Switching to deployment directory..."
cd ~/Deploy/osrswiki-android || exit 1

# Phase 4: Safe deployment operation
echo "🚀 Executing deployment..."
git fetch source                   # Fetch from main repo
git merge source/main              # Merge changes
git push origin main               # Push to deployment remote

# Phase 5: Post-deployment verification
echo "✅ Verifying deployment..."
git status                         # Check clean state
git log --oneline -3               # Show recent commits

echo "✅ Android deployment completed successfully"
```

## iOS Deployment (macOS Only)

### Prerequisites
```bash
# Verify macOS environment
uname -s  # Should show: Darwin

# Ensure Xcode is available
xcode-select -p  # Should show Xcode path

# Verify iOS deployment target
./scripts/shared/validate-deployment.sh ios
```

### Safe iOS Deployment Process

#### Step 1: Validate iOS Source State
```bash
cd platforms/ios

# Run iOS quality gates (REQUIRED)
xcodebuild test -project OSRSWiki.xcodeproj -scheme OSRSWiki
xcodebuild build -project OSRSWiki.xcodeproj -scheme OSRSWiki -sdk iphonesimulator

# Code formatting (if configured)
swiftformat . --lint               # Verify formatting
```

#### Step 2: Execute Safe iOS Deployment
```bash
cd /Users/miyawaki/Develop/osrswiki

# Execute safe iOS deployment
./scripts/shared/deploy-ios-safe.sh

# Similar safety flow as Android:
# 1. Validation + backup
# 2. Deploy to ~/Deploy/osrswiki-ios/
# 3. Push to ios remote
# 4. Verification
```

#### Step 3: iOS-Specific Verification
```bash
cd ~/Deploy/osrswiki-ios

# Verify Xcode project integrity
xcodebuild -list -project OSRSWiki.xcodeproj

# Check bundle ID consistency
grep -r "PRODUCT_BUNDLE_IDENTIFIER" OSRSWiki.xcodeproj/
```

## Tooling Deployment

### Private Tooling Repository
```bash
# Deploy development tools and asset generators
./scripts/shared/deploy-tooling-safe.sh

# Deploys to: ~/Deploy/osrswiki-tooling/
# Contains: Asset generation scripts, development utilities
```

## Deployment Validation

### Pre-Deployment Validation

#### Repository Health Check
```bash
./scripts/shared/validate-repository-health.sh
```

**Validates**:
- Git repository integrity
- No internal worktrees (contamination prevention)
- Platform directories exist and have content
- Critical files present (.gitignore, build files)
- No uncommitted changes in deployment-critical areas

#### Deployment Target Validation
```bash
./scripts/shared/validate-deployment.sh [android|ios|tooling]
```

**Validates**:
- Deployment directory exists (`~/Deploy/osrswiki-*`)
- Deployment repo is healthy git repository
- Remote configuration correct
- Working directory clean
- No conflicts with source repository

### Post-Deployment Validation

#### Deployment Integrity Check
```bash
# Run from deployment directory
cd ~/Deploy/osrswiki-android

# Verify git state
git status                         # Should be clean
git log --oneline -5               # Recent commits
git remote -v                      # Verify remotes

# Verify platform-specific requirements
./gradlew assembleDebug           # Android: builds successfully
xcodebuild build -scheme OSRSWiki # iOS: builds successfully
```

## Backup Strategy

### Pre-Deployment Backups
```bash
# Create timestamped backup before deployment
./scripts/shared/emergency-backup.sh deployment-$(date +%Y%m%d-%H%M%S)

# Backup includes:
# - Complete source repository state
# - All worktrees and sessions
# - Deployment repository states
# - Environment configuration
```

### Backup Verification
```bash
# Test backup system
./scripts/shared/test-backup-system.sh

# Monitor backup health
./scripts/shared/monitor-backups.sh
```

## Rollback Procedures

### Android Rollback
```bash
# Step 1: Identify rollback target
cd ~/Deploy/osrswiki-android
git log --oneline -10              # Find commit to rollback to

# Step 2: Create emergency backup of current state
cd /Users/miyawaki/Develop/osrswiki
./scripts/shared/emergency-backup.sh android-rollback-$(date +%Y%m%d-%H%M%S)

# Step 3: Execute rollback
cd ~/Deploy/osrswiki-android
git reset --hard <commit-hash>     # Reset to target commit
git push --force-with-lease origin main  # Force push with safety

# Step 4: Verify rollback
git status                         # Clean state
git log --oneline -5               # Verify position
```

### iOS Rollback
```bash
# Similar process for iOS
cd ~/Deploy/osrswiki-ios
git log --oneline -10
git reset --hard <commit-hash>
git push --force-with-lease origin main

# Additional iOS verification
xcodebuild build -project OSRSWiki.xcodeproj -scheme OSRSWiki
```

### Source Repository Rollback
```bash
# If source needs rollback (rare)
cd /Users/miyawaki/Develop/osrswiki

# Create backup first
./scripts/shared/emergency-backup.sh source-rollback-$(date +%Y%m%d-%H%M%S)

# Execute rollback
git reset --hard <commit-hash>
git push --force-with-lease origin main

# Re-validate deployment consistency
./scripts/shared/validate-repository-health.sh
```

## Troubleshooting

### Common Deployment Issues

#### Issue: "Repository health check failed"
```bash
# Solution: Run detailed health check
./scripts/shared/validate-repository-health.sh

# Common causes:
# - Worktrees inside main repo (move to ~/Develop/osrswiki-sessions/)
# - Uncommitted changes in critical files
# - Missing platform directories
```

#### Issue: "Deployment target validation failed"
```bash
# Solution: Check deployment directory
ls -la ~/Deploy/osrswiki-android/

# Common causes:
# - Deployment repo doesn't exist (clone it)
# - Working directory not clean (commit or stash changes)
# - Remote configuration incorrect
```

#### Issue: "Git hook blocking push"
```bash
# Solution: Use safe deployment scripts instead
./scripts/shared/deploy-android-safe.sh

# Emergency bypass (use with extreme caution):
git push --no-verify android main
./scripts/shared/emergency-backup.sh post-bypass
```

#### Issue: "Merge conflicts during deployment"
```bash
# Solution: Resolve in deployment directory
cd ~/Deploy/osrswiki-android
git status                         # See conflicted files
# Edit files to resolve conflicts
git add .
git commit -m "Resolve deployment conflicts"
git push origin main
```

### Recovery Procedures

#### Failed Android Deployment
1. **Assess damage**: Check deployment repo state
2. **Create backup**: Emergency backup of current state
3. **Restore from backup**: Use latest pre-deployment backup
4. **Validate restoration**: Run health checks
5. **Retry deployment**: Address original failure cause

#### Failed iOS Deployment
1. **Check Xcode integration**: Verify project builds
2. **Validate bundle ID**: Ensure consistency
3. **Restore if needed**: From backup
4. **Address root cause**: Fix build/config issues

#### Complete Deployment System Failure
1. **Emergency backup**: Current state
2. **Restore deployment repos**: From latest backups
3. **Re-clone if necessary**: From remote repositories
4. **Validate system**: Full health check
5. **Document incident**: For future prevention

## Deployment Schedule & Best Practices

### Recommended Deployment Schedule
- **Android**: After successful PR merge to main
- **iOS**: After Android deployment verification
- **Tooling**: Weekly or as needed for development tools

### Best Practices
1. **Always validate first**: Run health checks before deployment
2. **Backup before deployment**: Create emergency backup
3. **Deploy during low usage**: Minimize user impact
4. **Verify after deployment**: Confirm successful deployment
5. **Monitor post-deployment**: Watch for issues
6. **Document changes**: Log deployment details

### Emergency Deployment
```bash
# For critical hotfixes
./scripts/shared/emergency-backup.sh hotfix-$(date +%Y%m%d-%H%M%S)
./scripts/shared/deploy-android-safe.sh
# Monitor closely post-deployment
```

## Integration with CI/CD (Future)

### Planned Automation
- **Automated validation**: Run quality gates on PR
- **Safe deployment**: Trigger deployment scripts from CI
- **Rollback automation**: Automatic rollback on failure detection
- **Monitoring integration**: Alert on deployment issues

### Current Manual Process
All deployments currently require manual execution of safe deployment scripts to ensure human oversight of critical operations.

---

## Summary

The safe deployment system provides:

1. **Validation-First Approach**: Multiple validation layers prevent broken deployments
2. **Isolation**: Physical separation prevents contamination between source and deployment
3. **Backup Protection**: Comprehensive backup strategy enables quick recovery
4. **Rollback Capability**: Tested procedures for deployment recovery
5. **Human Oversight**: Manual execution ensures careful deployment consideration

This system prioritizes **safety and recoverability** over deployment speed to prevent catastrophic losses.