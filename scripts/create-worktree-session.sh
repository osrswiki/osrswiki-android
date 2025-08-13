#!/bin/bash
set -euo pipefail

# Must be run from main repo directory
if [[ ! -f CLAUDE.md ]]; then
    echo "❌ Must run from main repo directory"
    exit 1
fi

TOPIC="${1:-development}"
SESSION_NAME="claude-$(date +%Y%m%d-%H%M%S)"
BRANCH_NAME="claude/$(date +%Y%m%d-%H%M%S)-$TOPIC"
WORKTREE_DIR="../$SESSION_NAME"

echo "🌿 Creating worktree session: $SESSION_NAME"
echo "📁 Directory: $WORKTREE_DIR" 
echo "🌿 Branch: $BRANCH_NAME"

# Create worktree with new branch
git worktree add "$WORKTREE_DIR" -b "$BRANCH_NAME"

# Set up shared scripts in worktree
cd "$WORKTREE_DIR"

# Create symlink to shared scripts directory
ln -sf ../main/scripts scripts-shared

# Copy essential session scripts for independence
cp ../main/scripts/setup-session-device.sh ./
cp ../main/scripts/cleanup-session-device.sh ./
cp ../main/scripts/get-app-id.sh ./
cp ../main/scripts/quick-test.sh ./
cp ../main/scripts/start-session.sh ./
cp ../main/scripts/end-session.sh ./
cp ../main/scripts/test-workflow.sh ./

# Make copied scripts executable
chmod +x *.sh

echo "✅ Worktree session ready!"
echo ""
echo "💡 To use this session:"
echo "   cd $WORKTREE_DIR"
echo "   ./setup-session-device.sh     # Start emulator (15s)"
echo "   export ANDROID_SERIAL=\$(cat .claude-session-device | cut -d: -f2)"
echo "   ./quick-test.sh              # Fast iterations (5s each)"
echo "   # ... develop ..."
echo "   ./cleanup-session-device.sh  # Clean up"
echo ""
echo "💡 To remove session:"
echo "   cd ../main"
echo "   git worktree remove $SESSION_NAME"
