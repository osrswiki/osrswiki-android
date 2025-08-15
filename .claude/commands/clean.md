# Clean Command

Abandon current worktree session and clean up all resources without merging changes to main.

## Usage
```bash
/clean
```

Claude will safely abandon your current development session, clean up all session resources, and optionally delete the feature branch.

## How it works
Claude will:
1. **Show current work status** - display uncommitted and unpushed changes
2. **Confirm abandonment** - require explicit confirmation to discard work
3. **Offer branch preservation** - option to keep remote branch for potential recovery
4. **Clean up session resources** (devices, simulators, session files)
5. **Remove worktree** from sessions directory
6. **Delete feature branch** (optional, based on user choice)
7. **Return to main repository** in clean state

## ⚠️ Important Warning

**This command will permanently discard all uncommitted work in the current session.**

- Uncommitted changes will be lost forever
- Unpushed commits will be lost if branch is deleted
- Session resources will be completely removed
- This action cannot be undone

## Abandonment Process

Claude handles the complete abandonment and cleanup process with user confirmation:

- **Work assessment**: Review uncommitted changes and unpushed commits
- **User confirmation**: Explicit confirmation required before any destructive actions
- **Branch decision**: Choose whether to preserve or delete the feature branch
- **Resource cleanup**: Clean up session devices, simulators, and files
- **Worktree removal**: Remove session directory and git worktree
- **Repository reset**: Return to main repository in clean state

## Required Actions

**Claude will handle the abandonment process directly, with required user confirmations.**

### Abandonment Workflow

Claude will perform the complete cleanup process after confirmation:

1. **Session Analysis**: Show current work status and assess what will be lost
2. **User Confirmation**: Require explicit confirmation to proceed with abandonment
3. **Branch Decision**: Ask whether to preserve remote branch or delete completely
4. **Resource Cleanup**: Clean up session devices, simulators, and session files
5. **Worktree Removal**: Remove worktree directory and git worktree reference
6. **Return to Main**: Switch back to main repository directory

## Cleanup Operations

After user confirms abandonment:

### 1. Session Resource Cleanup

**For Android sessions:**
```bash
# From worktree session directory:
./cleanup-session-device.sh
```

**For iOS sessions:**
```bash
# From worktree session directory:
./cleanup-session-simulator.sh
```

**For cross-platform sessions:**
```bash
# From worktree session directory:
./cleanup-session-device.sh     # If Android session exists
./cleanup-session-simulator.sh  # If iOS session exists
```

### 2. Session File Cleanup
```bash
# From worktree session directory:
# Remove platform indicator
rm -f .claude-platform

# Remove session environment files
rm -f .claude-env .claude-session-* .claude-device-* .claude-emulator-*

# Clean up screenshots directory
rm -rf screenshots/
```

### 3. Worktree Removal
```bash
# From main repository root:
cd ~/Develop/osrswiki  # Back to main repo
git worktree remove ~/Develop/osrswiki-sessions/claude-YYYYMMDD-HHMMSS-<topic> --force
```

### 4. Branch Cleanup (Optional)

**If user chooses to delete branch:**
```bash
# From main repository root:
# Delete local feature branch
git branch -D claude/YYYYMMDD-HHMMSS-topic

# Delete remote feature branch (if it exists)
git push origin --delete claude/YYYYMMDD-HHMMSS-topic
```

**If user chooses to preserve branch:**
- Remote branch remains available for future recovery
- Local worktree reference is still removed
- Branch can be checked out later if needed

## User Decision Points

### Abandonment Confirmation
**Required confirmation before any cleanup:**
- "Are you sure you want to abandon this session and discard all work? (yes/no)"
- Must type "yes" explicitly to proceed

### Branch Preservation
**After confirming abandonment:**
- **Delete branch** (default): Remove local and remote branch completely
- **Preserve remote branch**: Keep remote branch for potential future recovery

### Work Status Display

Before confirmation, Claude shows:
- **Uncommitted changes**: Files modified but not committed
- **Unpushed commits**: Commits that exist locally but not on remote
- **Branch info**: Current branch name and last commit
- **Session info**: Platform, devices, and session duration

## Safety Features

### Double Confirmation
1. **First confirmation**: Explicit "yes" required to proceed with abandonment
2. **Work status review**: Clear display of what will be lost
3. **Final confirmation**: Confirmation after showing exactly what will be deleted

### Recovery Options
- **Preserve remote branch**: Keep remote branch for potential recovery
- **Session info**: Show commands to potentially recreate similar session
- **Commit suggestion**: Offer to make final commit before abandonment

### Error Prevention
- **Work detection**: Clear warnings when uncommitted work exists
- **Branch status**: Show if branch has been pushed to remote
- **Resource verification**: Confirm session resources before cleanup

## Success Criteria

### Abandonment Complete
- Session resources completely cleaned up
- Worktree removed from sessions directory
- Repository returned to main branch in clean state
- No orphaned processes or files remaining

### Optional Branch Cleanup
- Feature branch deleted (local and remote) if requested
- Or remote branch preserved for potential future recovery
- Git repository in clean, consistent state

## Error Handling

### Cleanup Failures
1. **Session device issues**: Force cleanup with manual intervention
2. **Worktree removal issues**: Manual worktree cleanup guidance
3. **Branch deletion failures**: Handle remote branch deletion errors gracefully

### Recovery Guidance
1. **Preserved branches**: Instructions for checking out preserved remote branch
2. **Partial cleanup**: Manual steps to complete cleanup if script fails
3. **Session recreation**: Commands to create similar session if needed

## Use Cases

### When to Use /clean

**Experimental work:**
- Trying out ideas that didn't work
- Exploring approaches before committing to implementation
- Testing changes that shouldn't be preserved

**Starting over:**
- Realizing current approach is wrong
- Want to restart with different strategy
- Found better solution and current work is obsolete

**Abandoned features:**
- Requirements changed
- Feature no longer needed
- Switching to different priority

### When NOT to Use /clean

**Useful work exists:**
- Use `/merge` to integrate valuable changes
- Consider committing work before abandoning
- Evaluate if any code can be salvaged

**Temporary setback:**
- Build failures can usually be fixed
- Test failures don't require abandonment
- Consider debugging before giving up

## Session Complete - Clean Slate Ready!

After Claude completes the cleanup, you'll have:

### ✅ Clean Repository
- **Returned to main branch** in original repository
- **No session artifacts** remaining in filesystem
- **Git state clean** with no orphaned worktrees or branches
- **Ready for new session** with `/start` command

### ✅ Resources Freed
- **Session devices stopped** and cleaned up (emulators/simulators)
- **Disk space reclaimed** from session directory removal
- **No background processes** from abandoned session
- **Clean development environment** ready for next task

### ✅ Optional Branch Preservation
- **Remote branch preserved** (if selected) for potential future recovery
- **Or complete branch removal** for clean git history
- **Consistent repository state** with no dangling references

**🧹 Development session abandoned!** You're back to a clean state and ready to start fresh with a new session or continue other work.