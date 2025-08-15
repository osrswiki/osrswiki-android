# OSRS Wiki Disaster Recovery Guide

## 🚨 Emergency Response Procedures

This document provides step-by-step recovery procedures for the OSRS Wiki project after the 2025-08-14 reorganization that implemented comprehensive safety measures.

### Quick Emergency Actions

If you're experiencing a crisis **RIGHT NOW**:

1. **STOP** any ongoing operations immediately
2. **CREATE EMERGENCY BACKUP**: `./scripts/shared/emergency-backup.sh crisis`
3. **ASSESS DAMAGE**: `./scripts/shared/validate-repository-health.sh`
4. **FOLLOW RECOVERY PROCEDURES** below based on the issue

---

## Recovery Scenarios

### Scenario 1: Wrong Content Pushed to Deployment Repository

**Symptoms:**
- Android/iOS repository has wrong files
- Commit count drastically changed
- Missing development history

**Recovery Steps:**
```bash
# 1. Create immediate backup
./scripts/shared/emergency-backup.sh wrong-push-recovery

# 2. Go to deployment repository
cd ~/Deploy/osrswiki-android  # or osrswiki-ios

# 3. Check what happened
git log --oneline -10
git remote -v

# 4. If recent push is bad, revert it
git log --oneline -20  # Find the last good commit
LAST_GOOD_COMMIT="<commit-sha-before-bad-push>"
git reset --hard $LAST_GOOD_COMMIT

# 5. Force push to restore (ONLY if you're sure)
git push origin main --force-with-lease

# 6. Verify fix
git log --oneline -5
git rev-list --count HEAD  # Should be 500+ for Android, reasonable for iOS
```

### Scenario 2: Main Repository Corrupted

**Symptoms:**
- Git errors in main repository
- Missing files or directories
- Corrupt git objects

**Recovery Steps:**
```bash
# 1. Navigate to safe location
cd ~

# 2. Find latest backup
ls -la ~/Backups/osrswiki/
# Look for most recent backup directory

# 3. Restore from backup bundle
BACKUP_DIR="~/Backups/osrswiki/emergency-YYYYMMDD-HHMMSS"
cd ~/Develop
mv osrswiki osrswiki-corrupted-$(date +%Y%m%d-%H%M%S)

# 4. Restore from bundle
git clone $BACKUP_DIR/monorepo-main.bundle osrswiki
cd osrswiki

# 5. Verify restoration
git status
git log --oneline -10
ls -la  # Check files are present

# 6. Re-add remotes if needed
git remote add android https://github.com/omiyawaki/osrswiki-android.git
git remote add ios https://github.com/omiyawaki/osrswiki-ios.git
git remote add tooling https://github.com/omiyawaki/osrswiki-tooling.git
```

### Scenario 3: Lost Development Work (Worktree Data Loss)

**Symptoms:**
- Session worktree directory deleted
- Uncommitted changes lost
- Work in progress missing

**Recovery Steps:**
```bash
# 1. Check for backups of session work
ls -la ~/Backups/osrswiki/
# Look for session-specific backups

# 2. Check automated backups
find ~/Backups/osrswiki -name "*session*" -o -name "*worktree*"

# 3. If session was backed up as tar.gz
cd ~/Develop/osrswiki-sessions
tar -xzf ~/Backups/osrswiki/emergency-*/session-*.tar.gz

# 4. If work was committed to a branch, recover from git
cd ~/Develop/osrswiki
git branch -a | grep claude/
git checkout claude/YYYYMMDD-HHMMSS-topic-name
git log --oneline -10

# 5. Recreate worktree if needed
./scripts/shared/create-worktree-session.sh recovery
cd ~/Develop/osrswiki-sessions/claude-*/
# Copy recovered work here
```

### Scenario 4: Complete System Loss

**Symptoms:**
- Entire ~/Develop or ~/Deploy directories gone
- Multiple repositories corrupted
- System-wide issues

**Recovery Steps:**
```bash
# 1. Create recovery workspace
mkdir -p ~/Recovery
cd ~/Recovery

# 2. Find the most recent comprehensive backup
ls -la ~/Backups/osrswiki/
LATEST_BACKUP="$(ls -t ~/Backups/osrswiki/ | grep -E '(emergency|daily-auto)' | head -1)"
echo "Using backup: $LATEST_BACKUP"

# 3. Extract and verify backup
cd ~/Recovery
BACKUP_PATH="~/Backups/osrswiki/$LATEST_BACKUP"

# 4. Restore main repository
git clone "$BACKUP_PATH/monorepo-main.bundle" osrswiki-recovered
cd osrswiki-recovered
git log --oneline -10  # Verify it looks correct

# 5. Restore deployment repositories
cd ~/Recovery
for platform in android ios tooling; do
    if [[ -f "$BACKUP_PATH/deploy-$platform.bundle" ]]; then
        git clone "$BACKUP_PATH/deploy-$platform.bundle" "deploy-$platform-recovered"
        echo "Recovered deployment repo: $platform"
    fi
done

# 6. Verify all recoveries
ls -la ~/Recovery/
echo "Manual verification required before moving to production locations"

# 7. When ready, move to production (CAREFULLY)
# mv ~/Recovery/osrswiki-recovered ~/Develop/osrswiki
# mkdir -p ~/Deploy
# mv ~/Recovery/deploy-*-recovered ~/Deploy/
```

### Scenario 5: Deployment Repository History Loss

**Symptoms:**
- Android repository shows only 9 commits (should be 500+)
- iOS repository missing expected commits
- Deployment history truncated

**Recovery Steps:**
```bash
# 1. Check current state
cd ~/Deploy/osrswiki-android
git log --oneline | wc -l  # Count commits
git log --oneline -10      # See recent history

# 2. Find backup with proper history
ls -la ~/Backups/osrswiki/*/deploy-android.bundle
GOOD_BACKUP="~/Backups/osrswiki/emergency-YYYYMMDD-HHMMSS/deploy-android.bundle"

# 3. Backup current broken state (just in case)
cd ~/Deploy
mv osrswiki-android osrswiki-android-broken-$(date +%Y%m%d-%H%M%S)

# 4. Restore from good backup
git clone "$GOOD_BACKUP" osrswiki-android
cd osrswiki-android

# 5. Verify restoration
git log --oneline | wc -l  # Should be 500+ for Android
git log --oneline -10      # Should show proper development history

# 6. If restore is good, push to remote
git push origin main --force-with-lease
```

---

## Backup Management

### Available Backup Types

1. **Daily Automated Backups** (`daily-auto-*`)
   - Created daily at 2:00 AM
   - Comprehensive bundles of all repositories
   - Automatically cleaned up after 30 days

2. **Emergency Backups** (`emergency-*`)
   - Created on-demand before risky operations
   - Include system state snapshots
   - Manual cleanup required

3. **Pre-deployment Backups** (`pre-*-deploy-*`)
   - Created automatically by safe deployment scripts
   - Contain state before each deployment
   - Useful for deployment rollbacks

### Backup Validation

Before relying on a backup for recovery:

```bash
# Verify bundle integrity
git bundle verify ~/Backups/osrswiki/emergency-*/monorepo-main.bundle

# Check bundle contents without cloning
git bundle list-heads ~/Backups/osrswiki/emergency-*/monorepo-main.bundle

# Verify backup completeness
ls -la ~/Backups/osrswiki/emergency-*/
# Should contain: *.bundle files, *.metadata files, system-state.txt, RESTORE.sh
```

### Backup Monitoring

```bash
# Check backup system status
./scripts/shared/monitor-backups.sh

# View backup logs
less ~/Backups/osrswiki/daily-backup.log

# Test backup system
./scripts/shared/test-backup-system.sh
```

---

## Prevention Measures (Already Implemented)

The reorganization implemented multiple layers of protection:

### Layer 1: Physical Separation
- Development in `~/Develop/osrswiki`
- Sessions in `~/Develop/osrswiki-sessions/` 
- Deployment in `~/Deploy/`

### Layer 2: Git Hooks
- Pre-push hook prevents dangerous pushes
- Validates commit counts and repository structure
- Blocks accidental monorepo pushes to deployment repos

### Layer 3: Validation Scripts
- `validate-repository-health.sh` - Daily health checks
- `validate-deployment.sh` - Pre-deployment validation
- Multiple safety checks before any operation

### Layer 4: Safe Deployment Scripts
- `deploy-android-safe.sh` - Safe Android deployment
- `deploy-ios-safe.sh` - Safe iOS deployment
- Multiple validation steps and automatic backups

### Layer 5: Automated Backups
- Daily backups at 2:00 AM
- Emergency backup capability
- Multiple retention policies

---

## Getting Help

### Self-Diagnosis
1. Run health check: `./scripts/shared/validate-repository-health.sh`
2. Check backup status: `./scripts/shared/monitor-backups.sh`
3. Review recent logs: `less ~/Backups/osrswiki/daily-backup.log`

### Escalation Path
1. Create emergency backup first: `./scripts/shared/emergency-backup.sh help`
2. Document the issue with screenshots/logs
3. Do not attempt complex recovery without guidance
4. Preserve all backup files until recovery is confirmed

### Recovery Verification
After any recovery procedure:
```bash
# Verify main repository
cd ~/Develop/osrswiki
./scripts/shared/validate-repository-health.sh

# Verify deployments
cd ~/Deploy/osrswiki-android && git log --oneline | wc -l
cd ~/Deploy/osrswiki-ios && git log --oneline | wc -l

# Test basic functionality
cd ~/Develop/osrswiki
./scripts/shared/create-worktree-session.sh recovery-test
# Verify worktree is created in ~/Develop/osrswiki-sessions/
```

---

## Appendix: Common Git Commands for Recovery

```bash
# View repository state
git status
git log --oneline -20
git branch -a
git remote -v

# Check repository integrity
git fsck --full
git gc --aggressive

# View file changes
git diff
git diff --cached
git diff HEAD~1

# Safe operations
git stash push -m "emergency stash"
git stash list
git stash pop

# Recovery operations (use with caution)
git reflog  # Shows all recent operations
git reset --hard HEAD~1  # Go back one commit
git checkout <commit-sha>  # Go to specific commit
```

Remember: **When in doubt, create a backup first!**