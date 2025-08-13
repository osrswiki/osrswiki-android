#!/bin/bash
set -euo pipefail
echo "🧹 Ending Claude Code session..."
./scripts/cleanup-session-device.sh
echo "✅ Session ended and cleaned up"