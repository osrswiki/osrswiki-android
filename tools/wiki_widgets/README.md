This directory has moved
========================

The wiki widget scanner has been merged into the unified JS modules toolkit.

New location
------------
- Scanner scripts live under: `tools/js_modules/scanner/`
- Unified outputs live under: `tools/js_modules/out/`

Use these commands instead
--------------------------
- Basic scan (first 200 mainspace pages):
  - `./tools/js_modules/scanner/scan_widgets.sh --limit 200 --out tools/js_modules/out`
- Scan specific pages:
  - `./tools/js_modules/scanner/scan_widgets.sh --pages "Grand_Exchange" "Magic" "The_Gauntlet"`
- Custom blacklists (exclude modules/classes you've already implemented):
  - `./tools/js_modules/scanner/scan_widgets.sh --blacklist-modules "ext.custom" --blacklist-classes "my-widget"`
- Change base wiki URL (if needed):
  - `./tools/js_modules/scanner/scan_widgets.sh --base https://oldschool.runescape.wiki`

Direct Python execution
-----------------------
- `micromamba run -n osrs-tools python3 tools/js_modules/scanner/scan_widgets.py --limit 200`

Outputs
-------
- `tools/js_modules/out/report.json`: Full structured report.
- `tools/js_modules/out/summary.txt`: Human-friendly top lists.

Notes
-----
- The functionality is unchanged; only paths are updated.
