#!/bin/bash
set -euo pipefail
echo "🧹 Ending Claude Code session..."
./cleanup-session-device.sh
echo "✅ Session ended and cleaned up"