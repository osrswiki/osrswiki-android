OSRS Wiki Widget Scanner
========================

Purpose
-------
- Crawl a slice of the Old School RuneScape Wiki, detect JavaScript-related ResourceLoader modules and HTML markers that typically power dynamic widgets (e.g., GE charts, maps, collapsibles), and compare these against local app assets.

What it does
------------
- Uses the MediaWiki API `action=parse` to fetch for each page:
  - `modules`: ResourceLoader modules used on the page.
  - `text`: Rendered HTML, which is scanned for class names and `data-*` attributes.
- Scans local assets in `app/src/main/assets/{web,js,styles}` to extract referenced CSS classnames.
- Produces a JSON report and a short text summary highlighting:
  - Most common modules detected and example pages.
  - Frequent widget-like classes not seen in local assets (heuristic).
  - Local assets list and extracted classes for quick cross-reference.

Usage
-----
**Recommended: Use the wrapper script (automatically handles environment):**
- Basic scan (first 200 mainspace pages):
  - `./tools/wiki_widgets/scan_widgets.sh --limit 200 --out tools/wiki_widgets/out`
- Scan specific pages:
  - `./tools/wiki_widgets/scan_widgets.sh --pages "Grand_Exchange" "Magic" "The_Gauntlet"`
- Custom blacklists (exclude modules/classes you've already implemented):
  - `./tools/wiki_widgets/scan_widgets.sh --blacklist-modules "ext.custom" --blacklist-classes "my-widget"`
- Change base wiki URL (if needed):
  - `./tools/wiki_widgets/scan_widgets.sh --base https://oldschool.runescape.wiki`

**Alternative: Direct Python execution (requires manual environment activation):**
- `micromamba run -n osrs-tools python3 tools/wiki_widgets/scan_widgets.py --limit 200`

Outputs
-------
- `tools/wiki_widgets/out/report.json`: Full structured report.
- `tools/wiki_widgets/out/summary.txt`: Human-friendly top lists.

Notes
-----
- The "missing widget-like classes" list is heuristic-based; it's a starting point to spot gaps, not a definitive truth.
- You can tune the heuristics in `is_widgety_class()` in `scan_widgets.py`.
- Map-related modules (`ext.kartographer.*`) and classes are blacklisted by default since the app uses MapLibre instead.
- Use `--blacklist-modules` and `--blacklist-classes` to exclude widgets you've already implemented or don't need.
- Network access is required to query the wiki API.

