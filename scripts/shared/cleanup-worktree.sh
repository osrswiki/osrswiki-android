#!/bin/bash
set -euo pipefail

echo "🧹 Cleaning up worktree session..."

# Clean up worktree (remove this session directory)
CURRENT_DIR=$(pwd)
SESSION_NAME=$(basename "$CURRENT_DIR")

if [[ "$SESSION_NAME" =~ ^claude-[0-9]{8}-[0-9]{6} ]]; then
    echo "🗑️ Removing worktree session: $SESSION_NAME"
    
    # Navigate to main repository and remove this worktree
    MAIN_REPO="/Users/miyawaki/Develop/osrswiki"
    cd "$MAIN_REPO"
    git worktree remove "$CURRENT_DIR" --force
    
    echo "✅ Worktree cleanup complete"
    echo "📁 Returned to: $(pwd)"
else
    echo "⚠️ Not in a Claude session directory, skipping worktree cleanup"
    echo "Current directory: $CURRENT_DIR"
fi

echo "🧹 Worktree cleanup finished!"