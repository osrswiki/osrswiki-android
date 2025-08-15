# OSRS Wiki Android Development Workflow

## Overview

This document explains the development workflow for the OSRS Wiki Android project when working with Claude Code agents. It covers the rationale behind our processes and provides conceptual background for understanding the automated workflow.

## Roles & Responsibilities

### User Responsibilities (Minimal)
- **Invocation**: Start Claude Code from main directory
- **Monitoring**: Track agent progress and review outputs  
- **Decision Making**: Choose features to implement and review results

### Agent Responsibilities (Autonomous)
- **Session Setup**: Create worktree sessions and configure devices
- **Environment**: Set up isolation and dependencies
- **Development**: Write code, tests, and implement features
- **Quality**: Run builds, tests, and maintain coverage requirements
- **Git Management**: Create branches, commit changes, and push to remote
- **Documentation**: Update code comments and commit messages
- **Problem Solving**: Debug issues and implement solutions
- **Cleanup**: Remove session resources when complete

## Development Environments

### Host Environment (Native)
- **Speed**: Fastest emulator boot and runtime performance
- **Hardware acceleration**: Native GPU rendering
- **Resource usage**: Lower overhead, better battery life
- **Compatibility**: Works with all Android SDK versions

### Container Environment (DevContainer/Docker)
- **Consistency**: Same environment across all developers
- **Isolation**: Complete development environment encapsulation
- **Portability**: Works identically on different host systems
- **GUI Support**: Emulator displays on host screen via X11 forwarding
- **macOS Setup**: Requires XQuartz installation for GUI display
- **Trade-offs**: Slower emulator performance, longer boot times (2-3 minutes)
- **API Updates**: Upgraded to compile with API 36 (2025 best practices)

## User Setup Process

### Starting a New Session

1. **Navigate to base directory**:
   ```bash
   cd /Users/miyawaki/Android/osrswiki-dev/
   ```

2. **Invoke Claude Code**: The agent handles all session creation autonomously
   - Agent creates worktree session: `./claude-YYYYMMDD-HHMMSS-<topic>/`
   - Agent auto-detects environment and sets up appropriate emulator (GUI or headless)
   - Agent creates branch and begins development

### Monitoring a Session

- Watch commit messages for progress updates
- Review WIP commits for current state
- Check device/emulator status if needed

### After Session Completion

- Review final pull request or WIP state
- Agent handles all cleanup automatically (device, worktree removal)

## Architecture Principles

### Parallel Development Model

Our workflow uses **git worktree** to enable true parallel development with multiple Claude Code sessions. This solves the fundamental problem of file conflicts when multiple agents work simultaneously.

**Why Git Worktree?**
- **Isolation**: Each session gets its own directory and file system
- **Independence**: Changes in one session don't affect others
- **Safety**: No git status contamination between sessions
- **Efficiency**: Multiple builds can run simultaneously

**Traditional Problems (Solved)**:
```bash
# OLD: Multiple agents in same directory = conflicts
cd /Users/miyawaki/Android/osrswiki-dev/main/  # Single directory
# Agent 1 changes MainActivity.kt
# Agent 2 changes MainActivity.kt  
# Result: Git chaos, lost work, merge conflicts
```

**Our Solution**:
```bash
# NEW: Each agent gets isolated workspace
cd /Users/miyawaki/Android/osrswiki-dev/
# Agent 1 creates: ./claude-YYYYMMDD-HHMMSS-search-ui/
# Agent 2 creates: ./claude-YYYYMMDD-HHMMSS-api-refactor/
# Result: Clean parallel development
```

### Device Isolation Strategy

Each Claude session requires its own test device to prevent interference. We support multiple approaches:

#### 1. Session-Scoped Emulators - Host Environment (Recommended)
- **Cold boot**: Once per session (~15 seconds)
- **Hot iterations**: Multiple builds/tests on same emulator (~5 seconds each)
- **Resource efficiency**: Emulator stays alive for entire session
- **Cleanup**: Automatic emulator termination when session ends

#### 2. Container Emulators - DevContainer/Docker
- **GUI Support**: Emulator window displays on host via X11 forwarding (interactive debugging)
- **Auto-Detection**: Scripts automatically detect container and configure appropriately
- **Container-optimized**: Uses fixed ports (5554, 5037) with port forwarding
- **Extended boot time**: 2-3 minutes for container initialization
- **Software rendering**: Uses swiftshader_indirect for GPU acceleration
- **API Strategy**: Compiles against API 36, targets API 35 for stability
- **Architecture**: Forced x86_64 for container compatibility

#### 3. Dedicated Emulator Per Session
- **Ultra-isolation**: Each session creates a fresh emulator
- **Higher overhead**: 15-second boot for each test (host) or 2-3 minutes (container)
- **Use case**: When maximum isolation is needed

#### 4. Physical Device Pool
- **Shared hardware**: Multiple physical devices with locking mechanism
- **Reliability**: No emulator quirks or performance issues
- **Limitation**: Requires physical device availability

## Quality Assurance Philosophy

### Layered Testing Strategy

Our testing approach follows a pyramid model:

1. **Unit Tests** (Fast, Many)
   - Business logic validation
   - Data layer testing
   - ViewModel behavior
   - Coverage requirement: 65% minimum

2. **Integration Tests** (Medium speed, Fewer)
   - Component interaction
   - Database operations
   - API integration

3. **UI Tests** (Slow, Targeted)
   - Critical user flows
   - Cross-device compatibility
   - User experience validation

### Coverage Philosophy

We enforce 65% minimum coverage because:
- **Catches regressions**: New changes don't break existing functionality
- **Documents behavior**: Tests serve as living documentation
- **Encourages design**: Testable code is usually better-architected code
- **Realistic threshold**: High enough to be meaningful, achievable for rapid development

**Escape Hatch**: WIP commits can bypass coverage for iteration speed, but must include TODO tickets for completion.

## Git Workflow Rationale

### Conventional Commits

We use structured commit messages for several reasons:
- **Machine parsing**: Tools can auto-generate changelogs
- **Clear intent**: Type prefix immediately indicates change nature
- **Searchability**: Easy to find specific types of changes
- **Review efficiency**: Reviewers understand scope at a glance

### Branch Protection

The `main` branch is protected because:
- **CI validation**: All changes must pass automated tests
- **Code review**: Human oversight catches issues automation misses
- **History cleanliness**: Prevents broken commits in main timeline
- **Rollback safety**: Main branch is always deployable

### WIP Commit Strategy

Work-in-progress commits serve multiple purposes:
- **Session continuity**: Claude sessions can resume incomplete work
- **Backup safety**: Partial progress isn't lost
- **Handoff capability**: Different agents can pick up where others left off
- **Experimentation**: Safe to try approaches without completion pressure

## Security Considerations

### Secrets Management

Never committing secrets prevents:
- **Credential leakage**: API keys exposed in public repositories
- **Security breaches**: Unauthorized access to production systems
- **Compliance violations**: Regulatory requirements for secret handling

### Code Hygiene

Our static analysis tools catch:
- **Security vulnerabilities**: Potential attack vectors in code
- **Performance issues**: Memory leaks, inefficient algorithms
- **Maintainability problems**: Code smells that make future changes harder

## Development Efficiency

### Build Optimization

Configuration cache and parallel execution reduce build times because:
- **Incremental builds**: Only changed components rebuild
- **Parallel task execution**: Multiple CPU cores utilized
- **Dependency caching**: External libraries downloaded once

### Test Optimization

Our test strategy balances speed and coverage:
- **Unit tests first**: Fast feedback loop for immediate validation
- **Selective integration**: Only test critical integration points
- **Strategic UI testing**: Cover user-critical flows without over-testing

## Troubleshooting Common Issues

### Build Problems
- **Configuration cache errors**: Usually indicates plugin incompatibility
- **Gradle daemon issues**: Restart daemon with `./gradlew --stop`
- **Memory problems**: Increase heap size in `gradle.properties`

### Device Issues
- **ADB conflicts**: Use session-specific ADB server ports
- **Emulator crashes**: Increase emulator RAM allocation  
- **Input quirks**: Use shell escaping for special characters
- **Container connectivity**: Emulator may take 2-3 minutes to be ADB-accessible in containers
- **Container performance**: Software rendering may be slower than host emulation
- **No emulator GUI**: Install XQuartz on macOS, ensure DISPLAY variable is set
- **X11 permission denied**: Run `xhost +localhost` on host before starting container

### Git Problems
- **Merge conflicts**: Rebase against main before pushing
- **Detached HEAD**: Always work on named branches
- **Large file issues**: Use Git LFS for assets >50MB

## Best Practices Summary

### For Users (You)
1. **Invoke from base directory** (`/Users/miyawaki/Android/osrswiki-dev/`)
2. **Provide clear task description** when starting the agent
3. **Monitor session progress** through commit messages
4. **Review results** when session completes

### For Agents (Claude)
1. **Create session infrastructure** (worktree, device, branch)
2. **Follow device isolation** to prevent cross-session interference
3. **Commit frequently** at logical checkpoints
4. **Run quality gates** before pushing changes
5. **Document incomplete work** with detailed WIP commits
6. **Clean up resources** when session ends

## Performance Expectations

### Session Timings

#### Host Environment
- **Session setup**: ~30 seconds (worktree + device boot)
- **Build iteration**: ~5-15 seconds (incremental builds)
- **Test cycle**: ~30-60 seconds (unit tests)
- **Full quality gate**: ~2-5 minutes (all checks)

#### Container Environment
- **Session setup**: ~3-5 minutes (container + emulator boot)
- **Build iteration**: ~10-20 seconds (incremental builds)
- **Test cycle**: ~45-90 seconds (unit tests)
- **Full quality gate**: ~3-7 minutes (all checks)

### Resource Requirements
- **Disk space**: ~500MB per active session
- **Memory**: ~2GB per emulator instance
- **CPU**: Scales with parallel session count

This workflow optimizes for **parallel development speed** while maintaining **code quality** and **system stability**.