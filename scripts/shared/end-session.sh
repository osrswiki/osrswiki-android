#!/bin/bash
set -euo pipefail
echo "🧹 Ending Claude Code session..."

# Clean up Android device if we're in an Android session
if [[ -f .claude-session-device ]]; then
    echo "📱 Detected Android session, cleaning up device..."
    ./scripts/android/cleanup-android-device.sh
fi

# Clean up worktree session
echo "🌿 Cleaning up worktree..."
./scripts/shared/cleanup-worktree.sh

echo "✅ Session ended and cleaned up"