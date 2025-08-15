# OSRS Wiki Monorepo Architecture

**Version**: 2.0 (Post-Reorganization)  
**Date**: August 2025  
**Status**: Production-Ready with Safety Infrastructure

## Overview

This document describes the resilient architecture implemented after the catastrophic git incident where 540+ commits were lost due to unsafe git operations. The new architecture prioritizes **safety**, **isolation**, and **prevention** of destructive operations.

## Core Design Principles

### 1. Physical Separation (Defense in Depth)
- **Source separation**: Main development repo physically separated from deployment repos
- **Session isolation**: All worktrees created outside main repository directory
- **Deployment isolation**: Android/iOS deployment repos in separate filesystem locations
- **Backup isolation**: Multiple backup strategies with geographic/temporal separation

### 2. Fail-Safe Defaults
- Git hooks **block** dangerous operations by default
- Scripts **validate** before execution, not after
- Deployment scripts **require** explicit safety checks
- **No direct access** to deployment remotes from development environment

### 3. Recovery-Oriented Design
- **Multiple backup layers**: Automated daily, emergency on-demand, repository bundles
- **Detailed logging**: All operations tracked with timestamps and context
- **Recovery procedures**: Documented step-by-step disaster recovery
- **Validation tools**: Continuous health monitoring and integrity checks

## Directory Architecture

```
/Users/miyawaki/
├── Develop/                         # DEVELOPMENT ZONE
│   ├── osrswiki/                    # 🏠 Main monorepo (source of truth)
│   │   ├── platforms/android/       # Android source code
│   │   ├── platforms/ios/           # iOS source code  
│   │   ├── shared/                  # Cross-platform code
│   │   ├── tools/                   # Asset generation & dev tools
│   │   ├── scripts/                 # Development automation
│   │   ├── .git/hooks/              # Safety enforcement (pre-push)
│   └── osrswiki-sessions/           # 🔒 ISOLATED WORKTREE ZONE
│       ├── claude-YYYYMMDD-topic1/  # Session-specific worktrees
│       └── claude-YYYYMMDD-topic2/  # Never contaminate main repo
├── Deploy/                          # 🚀 DEPLOYMENT ZONE (physically isolated)
│   ├── osrswiki-android/            # Android deployment repo
│   ├── osrswiki-ios/                # iOS deployment repo  
│   └── osrswiki-tooling/            # Private tooling deployment repo
└── Backups/                         # 🛡️ BACKUP ZONE
    └── osrswiki/                    # Automated backup storage
        ├── daily/                   # Daily automated backups (30-day retention)
        ├── emergency/               # On-demand emergency backups
        └── bundles/                 # Git bundle backups for catastrophic recovery
```

## Safety Infrastructure

### Git Hook Protection System

#### Pre-Push Hook (`/.git/hooks/pre-push`)
Prevents catastrophic operations before they occur:

```bash
# Blocked operations:
- Direct push to android/ios remotes
- Force push without backup verification  
- Push from main repository to deployment targets
- Subtree push operations (manual)

# Required validations:
- Repository health check passes
- No uncommitted changes in deployment-critical files
- Backup verification for destructive operations
```

#### Hook Bypass (Emergency Only)
```bash
# Only for legitimate emergencies:
git push --no-verify origin main

# Must be followed immediately by:
./scripts/shared/emergency-backup.sh post-bypass
```

### Validation & Health Monitoring

#### Repository Health Check
```bash
./scripts/shared/validate-repository-health.sh
```

**Validates**:
- Git repository integrity
- No internal worktrees (contamination check)
- Platform directory structure  
- Critical file presence
- Worktree session isolation
- Deployment repo separation

#### Deployment Validation
```bash
./scripts/shared/validate-deployment.sh [android|ios|tooling]
```

**Validates**:
- Deployment target exists and is healthy
- No cross-contamination between source and deployment
- Safe to proceed with deployment operations
- Remote repository state consistency

### Backup Strategy (Multi-Layered)

#### Layer 1: Automated Daily Backups
- **Schedule**: Daily at 2:00 AM via cron
- **Retention**: 30 days automatic cleanup
- **Location**: `~/Backups/osrswiki/daily/`
- **Contents**: Full repository state including worktrees

#### Layer 2: Emergency On-Demand Backups  
- **Trigger**: Before any risky operation
- **Usage**: `./scripts/shared/emergency-backup.sh [context]`
- **Location**: `~/Backups/osrswiki/emergency/`
- **Contents**: Complete repository snapshot with metadata

#### Layer 3: Git Bundle Backups
- **Purpose**: Catastrophic recovery (complete git history)
- **Creation**: `git bundle create backup.bundle --all`
- **Restoration**: `git clone backup.bundle recovered-repo`
- **Location**: `~/Backups/osrswiki/bundles/`

## Session Management Architecture

### Session Lifecycle

#### 1. Session Creation
```bash
TOPIC="feature-name"
./scripts/shared/create-worktree-session.sh "$TOPIC"
cd ~/Develop/osrswiki-sessions/claude-$(date +%Y%m%d-%H%M%S)-$TOPIC
```

**Safety Features**:
- Worktree created **outside** main repository
- Automatic device/simulator isolation setup
- Environment variable isolation (`.claude-env`)
- Session-specific git branch creation

#### 2. Session Isolation
Each session provides:
- **Filesystem isolation**: Own directory in `osrswiki-sessions/`
- **Device isolation**: Session-specific Android emulator or iOS simulator
- **Environment isolation**: Session-scoped environment variables
- **Git isolation**: Topic branch from latest main

#### 3. Session Cleanup
```bash
./cleanup-session-device.sh        # Android
./cleanup-session-simulator.sh     # iOS
cd /Users/miyawaki/Develop/osrswiki
git worktree remove ~/Develop/osrswiki-sessions/claude-* --force
```

**Safety Features**:
- Automatic cleanup of session resources
- No contamination of main repository
- Session artifacts contained and disposable

### Session Environment Variables

Each session automatically configures:
```bash
# Android sessions
ANDROID_SERIAL="emulator-5554"     # Session-specific device
ANDROID_HOME="/usr/local/android-sdk"

# iOS sessions (macOS only)  
IOS_SIMULATOR_UDID="session-uuid"  # Session-specific simulator
SIMULATOR_NAME="iPhone 15 Pro"

# Common
PROJECT_ROOT="/Users/miyawaki/Develop/osrswiki"
SESSION_ID="claude-YYYYMMDD-HHMMSS-topic"
```

## Deployment Architecture (Safe by Design)

### Deployment Workflow Safety

#### Traditional (Dangerous) Approach - PROHIBITED
```bash
# ❌ NEVER DO THIS - Direct subtree push
git subtree push --prefix platforms/android android main

# Why dangerous:
- No validation before destructive operation
- Potential for wrong directory/branch push  
- No rollback mechanism
- Can contaminate deployment repo with source files
```

#### New Safe Deployment Approach
```bash
# ✅ SAFE - Validated deployment
./scripts/shared/deploy-android-safe.sh

# Safety features:
- Pre-deployment validation
- Repository health check
- Backup before deployment  
- Isolated deployment environment
- Post-deployment verification
- Rollback capability
```

### Deployment Script Architecture

#### deploy-android-safe.sh Flow
1. **Pre-flight validation**:
   - Repository health check
   - Deployment target validation
   - Working directory verification
2. **Safety backup**:
   - Emergency backup of current state
   - Deployment target backup
3. **Isolated deployment**:
   - Change to deployment directory (`~/Deploy/osrswiki-android/`)
   - Fetch from source with validation
   - Apply changes with verification
4. **Post-deployment validation**:
   - Verify deployment integrity
   - Test basic functionality
   - Log deployment status

#### deploy-ios-safe.sh Flow
Similar to Android but with macOS-specific considerations:
- Xcode project validation
- iOS-specific build requirements
- Bundle ID consistency checks

## Development Workflow Architecture

### Standard Development Cycle

#### 1. Session Start (Isolated Environment)
```bash
# Create session with automatic isolation
TOPIC="new-feature"  
./scripts/shared/create-worktree-session.sh "$TOPIC"
cd ~/Develop/osrswiki-sessions/claude-$(date +%Y%m%d-%H%M%S)-$TOPIC

# Automatic setup provides:
- Isolated device/simulator  
- Topic branch from latest main
- Session-scoped environment
- No risk to main repository
```

#### 2. Development (Safety-First)
```bash
# All development in isolated session
source .claude-env                    # Load session environment
./setup-session-device.sh            # Verify device isolation

# Development with safety nets:
git add -p                           # Selective staging
git commit -m "feat(scope): summary  
Why: Explanation
Tests: unit|manual|none"             # Required format

# Pre-push validation automatic:
git push origin claude/branch        # Triggers health check
```

#### 3. Quality Gates (Enforced)
```bash
# Android quality gates (required)
cd platforms/android
./gradlew testDebugUnitTest          # Unit tests must pass
./gradlew lintDebug detekt           # Static analysis clean
./gradlew ktlintCheck                # Code style compliance
./gradlew koverVerify                # Coverage threshold (65%)

# iOS quality gates (macOS)
cd platforms/ios
xcodebuild test -project OSRSWiki.xcodeproj -scheme OSRSWiki
```

#### 4. Session End (Clean Isolation)
```bash
# Complete feature: create PR for review
gh pr create --title "feat: Feature summary" --body "..."

# Incomplete feature: push WIP for later
git commit -m "[WIP] feat: Partial implementation"
git push origin claude/branch

# Clean session environment  
./cleanup-session-device.sh
cd /Users/miyawaki/Develop/osrswiki
git worktree remove ~/Develop/osrswiki-sessions/claude-* --force
```

## Technology Stack & Dependencies

### Core Technologies
- **Git**: Version control with safety hooks
- **Gradle**: Android build system (wrapper required)
- **Xcode**: iOS build system (macOS only)
- **Docker**: Optional containerized development
- **Tailscale**: Secure remote access (optional)

### Safety Dependencies
- **bash**: Shell scripting for automation
- **cron**: Automated backup scheduling
- **git-bundle**: Catastrophic recovery
- **xmllint**: UI automation parsing
- **ripgrep**: Fast file searching

### Platform-Specific
- **Android**: API 35 (compileSdk 36), minimum API 24
- **iOS**: Latest stable iOS SDK, minimum iOS 14
- **Container**: X11 forwarding for GUI emulator access

## Monitoring & Observability

### Health Monitoring
```bash
# Daily health check (automated)
./scripts/shared/validate-repository-health.sh

# Backup monitoring  
./scripts/shared/monitor-backups.sh

# Manual health validation
./scripts/shared/test-backup-system.sh
```

### Logging Strategy
- **Session logs**: Each session logs to isolated directory
- **Deployment logs**: All deployments logged with timestamps
- **Error logs**: Failed operations logged for debugging
- **Backup logs**: Backup operations tracked for monitoring

### Alerts & Notifications
- **Backup failures**: Daily backup monitoring
- **Repository health**: Weekly automated validation
- **Deployment issues**: Failed deployments logged and reported
- **Hook violations**: Dangerous operations blocked and logged

## Security Model

### Isolation Boundaries
1. **Development ↔ Deployment**: Physical directory separation
2. **Session ↔ Main**: Worktree isolation outside main repo
3. **Local ↔ Remote**: Safe deployment scripts prevent direct access
4. **Source ↔ Binary**: No compiled artifacts in source control

### Access Controls
- **Git hooks**: Prevent dangerous operations
- **Script validation**: All operations validated before execution
- **Backup verification**: Recovery procedures tested regularly
- **Environment isolation**: Session-specific configurations

### Recovery Procedures
Documented in `RECOVERY.md`:
- **Partial corruption**: Repository health check and targeted repair
- **Complete loss**: Git bundle restoration with history preservation
- **Deployment issues**: Rollback procedures and validation
- **Data recovery**: Multiple backup layer restoration

## Future Improvements

### Planned Enhancements
- **Remote backup**: Cloud storage for disaster recovery
- **Deployment automation**: CI/CD integration with safety validation
- **Health monitoring**: Automated alerting for system issues
- **Performance optimization**: Faster development cycles with maintained safety

### Safety Roadmap
- **Enhanced validation**: More comprehensive pre-operation checks
- **Automated testing**: Integration testing for safety scripts
- **Documentation updates**: Keep procedures current with system evolution
- **Training materials**: Onboarding guides for safe development practices

---

## Summary

This architecture prioritizes **safety over convenience** to prevent catastrophic loss while maintaining development productivity. Every component includes multiple layers of protection:

1. **Prevention**: Git hooks and validation prevent dangerous operations
2. **Isolation**: Physical separation eliminates contamination risks  
3. **Recovery**: Multiple backup strategies ensure data resilience
4. **Monitoring**: Continuous health checks detect issues early
5. **Documentation**: Clear procedures for safe operations and recovery

The system is designed to be "agent-proof" - resistant to both human error and AI agent mistakes that could cause repository damage.