# Start Command

Set up a new isolated worktree session for development work with intelligent platform detection.

## Usage
```bash
/start
```

**Important:**
After typing `/start`, Claude will immediately ask you to describe your task.
Provide your task description so Claude can create meaningful worktree and branch names AND detect the target platform automatically.

⚠️ **Note**: Claude will wait for your task description before creating any infrastructure, ensuring everything is properly named and the correct platform is set up.

## How it works
Claude will:
1. Ask you to describe your task immediately
2. **Detect target platform** from your description (Android, iOS, or both)
3. Generate appropriate topic name based on your description (e.g., "js-modules-analysis")
4. Create worktree session: `claude-YYYYMMDD-HHMMSS-your-topic`
5. Create session branch: `claude/YYYYMMDD-HHMMSS-your-topic`
6. Set up **platform-specific** device isolation and complete session setup
7. Begin working on the task within the isolated session

## Platform Detection

When you describe your task, Claude automatically detects the target platform:

**iOS Indicators:** "iOS", "iPhone", "iPad", "Swift", "SwiftUI", "UIKit", "Xcode", "simulator", "Apple"
**Android Indicators:** "Android", "Kotlin", "Java", "Gradle", "emulator", "APK", "Google Play"
**Cross-platform:** "both platforms", "cross-platform", "Android and iOS"

If unclear from your description, Claude will ask: "Will you be working on Android, iOS, or both platforms?"

## Session Setup Process

Claude handles session setup directly with intelligent platform detection and environment configuration:

- **Task analysis**: Understand scope and complexity from your description
- **Platform detection**: Automatically detect Android, iOS, or cross-platform based on keywords
- **Session infrastructure**: Create worktree, branch, and environment setup
- **Device/simulator setup**: Configure platform-specific development environment
- **Development readiness**: Prepare session for immediate development work

## Required Actions

**Claude will handle session setup directly based on your task description.**

### Session Setup Workflow

1. **Ask user for task description**:
   Stop and ask the user: "Please describe what you'd like to work on in this session."
   Wait for their response.

2. **Direct Session Setup**:
   Claude will directly handle all session setup:
   - **Platform Detection**: Android, iOS, or cross-platform based on task description
   - **Session Creation**: Create worktree, environment, branch, and initial commit
   - **Environment Setup**: Configure platform-specific devices/simulators
   - **Development Readiness**: Prepare workspace for immediate development

### Development Workflow

After session setup, you can work on your task using the standard development workflow:

**Development Process**: 
```
plan (inline) → implement → scaffold → test
```

**Task Examples**:
- **Simple Task**: "Fix search button styling" → Direct implementation
- **Complex Task**: "Add user authentication" → Break down and implement incrementally  
- **Feature Development**: "Build search functionality" → Plan, implement, test systematically

## Session Complete - Development Ready!

After Claude completes session setup, you'll have:

### ✅ Infrastructure Ready
- **Isolated worktree session** in `~/Develop/osrswiki-sessions/claude-YYYYMMDD-HHMMSS-<topic>`
- **Platform-specific environment** configured (Android/iOS/both)
- **Session devices** connected and ready (emulator/simulator)
- **Git branch** created with initial session commit

### ✅ Development Environment
- **Clean workspace** isolated from main repository
- **Platform tools** configured and ready
- **Development scripts** available for build, test, and deployment
- **Session-specific configuration** tailored to your task

### 🎯 What Happens Next

You can now work on your task using:
1. **Plan** your implementation approach (integrated into workflow)
2. **Implement** the code changes
3. **Scaffold** comprehensive tests
4. **Test** with quality gates

Use additional commands like `/merge` when ready to integrate your changes back to main.

**🚀 Development session is active!** You're ready to start working on your task in an isolated, properly configured environment.