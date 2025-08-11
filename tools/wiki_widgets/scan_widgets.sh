#!/bin/bash
# Compatibility wrapper: scanner moved to tools/js_modules/scanner
echo "[DEPRECATED] tools/wiki_widgets/scan_widgets.sh has moved to tools/js_modules/scanner/scan_widgets.sh" >&2
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
exec "$PROJECT_ROOT/tools/js_modules/scanner/scan_widgets.sh" "$@"

