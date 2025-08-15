# Merge Command

Merge completed feature branch to main and clean up development session with automatic branch cleanup.

## Usage
```bash
/merge
```

Claude automatically merges your feature branch to main, validates the merge, and completely cleans up the session including deleting the feature branch.

## How it works
Claude will:
1. **Final commit and push** any remaining work to feature branch
2. **Switch to main repository** and update from origin
3. **Merge feature branch** to main with conflict resolution if needed
4. **Validate merged code** with quality gates
5. **Push main** to origin after successful validation
6. **Delete feature branch** (local and remote) - it's no longer needed!
7. **Clean up session** (devices, simulators, worktree)

## Merge Process

Claude handles merge operations directly with intelligent conflict resolution and validation:

- **Merge strategy selection**: Choose optimal approach (fast-forward, merge commit)
- **Conflict detection**: Identify and guide through merge conflicts
- **Quality validation**: Run tests and lint checks on merged code
- **Integration verification**: Ensure changes work correctly in main
- **Automatic cleanup**: Remove feature branch and session resources

## Required Actions

**Claude will handle merge operations directly, guiding you through each step.**

### Merge Workflow

Claude will perform the complete merge and cleanup process:

1. **Session Analysis**: Detect current feature branch and session state
2. **Final Commit**: Commit any remaining work and push feature branch
3. **Merge Strategy**: Choose appropriate merge approach (fast-forward, merge commit)
4. **Conflict Resolution**: Guide you through any merge conflicts
5. **Quality Validation**: Run tests and lint checks on merged code
6. **Branch Cleanup**: Delete feature branch after successful merge
7. **Session Cleanup**: Remove worktree and session resources

## Merge Strategies

### Fast-Forward Merge (Preferred)
When main hasn't changed since branch creation:
```bash
# Clean, linear history
git checkout main
git merge --ff-only claude/YYYYMMDD-HHMMSS-topic
```

### Merge Commit
When main has diverged:
```bash
# Preserves feature branch context
git checkout main
git merge --no-ff claude/YYYYMMDD-HHMMSS-topic
```

### Conflict Resolution
If conflicts occur:
1. **Automatic resolution** for simple conflicts
2. **Interactive resolution** for complex conflicts
3. **User guidance** through merge process
4. **Validation** of resolved conflicts

## Quality Gates

Before pushing to main, the merger validates:

### Required Checks
- **All tests pass**: Unit tests and integration tests
- **Lint validation**: Code style and static analysis
- **Coverage maintained**: 65% minimum coverage threshold
- **Build succeeds**: Clean compilation on target platforms

### Validation Commands
```bash
# Android validation (if applicable)
cd platforms/android && ./gradlew testDebugUnitTest lintDebug detekt ktlintCheck koverVerify

# iOS validation (if applicable, macOS only)
cd platforms/ios && xcodebuild test -project OSRSWiki.xcodeproj -scheme OSRSWiki
```

## Cleanup Operations

After successful merge and validation:

### 1. Branch Deletion
```bash
# Delete local feature branch
git branch -d claude/YYYYMMDD-HHMMSS-topic

# Delete remote feature branch
git push origin --delete claude/YYYYMMDD-HHMMSS-topic
```

### 2. Platform-Specific Cleanup

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

### 3. Session Cleanup
```bash
# From worktree session directory:
# Remove platform indicator
rm -f .claude-platform

# From main repository root:
# Remove worktree session directory
cd ~/Develop/osrswiki  # Back to main repo
git worktree remove ~/Develop/osrswiki-sessions/claude-YYYYMMDD-HHMMSS-<topic> --force
```

## User Decision Points

### Merge Approach
- **Direct merge** (default): Merge directly to main
- **Create PR**: Create pull request for review before merge

### Conflict Resolution
- **Automatic resolution**: For simple, non-overlapping conflicts
- **Interactive resolution**: User guidance for complex conflicts
- **Manual resolution**: Hand-off to user for critical conflicts

## Success Criteria

### Merge Success
- Feature branch successfully merged to main
- All quality gates pass on merged code
- Main branch pushed to origin
- Feature branch deleted (local and remote)

### Session Complete
- All session resources cleaned up
- Worktree removed from sessions directory
- Platform-specific cleanup completed
- Repository returned to clean state

## Error Handling

### Merge Conflicts
1. **Automatic resolution**: Simple conflicts resolved automatically
2. **Interactive guidance**: Step-by-step conflict resolution help
3. **Manual intervention**: User resolves complex conflicts
4. **Validation**: Ensure resolved conflicts don't break functionality

### Quality Gate Failures
1. **Test failures**: Fix failing tests before merge
2. **Lint violations**: Auto-fix where possible, manual fix otherwise
3. **Coverage drops**: Add tests to maintain coverage threshold
4. **Build failures**: Resolve compilation errors before merge

### Cleanup Failures
1. **Session device issues**: Force cleanup with manual intervention
2. **Worktree removal issues**: Manual worktree cleanup guidance
3. **Branch deletion failures**: Handle remote branch deletion errors

## Benefits of Automatic Branch Cleanup

### Repository Hygiene
- **No orphaned branches**: Every merged branch is immediately deleted
- **Clean branch list**: Only active development branches remain
- **Clear history**: Main branch shows complete development timeline

### Developer Experience
- **One command**: Complete merge and cleanup in single operation
- **No manual cleanup**: Never forget to delete merged branches
- **Session closure**: Clear mental model of session completion

### Best Practices
- **Ephemeral branches**: Branches exist only while needed
- **Clean collaboration**: Team sees only relevant active branches
- **Simplified workflow**: No decisions about branch lifecycle

## Session Complete - Ready for Deployment!

After the merger completes, you'll have:

### ✅ Integration Complete
- **Feature merged** to main branch with full history
- **Quality validated** - all tests and quality gates pass
- **Main updated** on origin with your changes

### ✅ Session Cleaned
- **Feature branch deleted** (local and remote) - no clutter!
- **Worktree removed** from sessions directory
- **Session devices** stopped and cleaned up
- **Repository pristine** and ready for next session

### ✅ Ready for Deployment
- **Changes in main** - ready for `/deploy` command
- **Quality assured** - passed all validation gates
- **History preserved** - merge commits show feature context

**🎉 Your development session is complete!** The feature is integrated into main and ready for deployment. Use `/deploy` when you're ready to push to target repositories.