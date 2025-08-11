#!/usr/bin/env python3
"""
OSRS Wiki widget scanner

Scans a set of wiki pages via the MediaWiki API, collects:
- ResourceLoader modules used per page (action=parse&prop=modules)
- HTML class names and data-* attributes (from rendered HTML)

Compares discovered widget-like markers against local app assets under:
- app/src/main/assets/web
- app/src/main/assets/js
- app/src/main/assets/styles

Outputs a JSON report and a brief text summary for quick review.

Usage:
  python tools/wiki_widgets/scan_widgets.py \
    --base https://oldschool.runescape.wiki \
    --limit 200 \
    --out tools/wiki_widgets/out

You can also pass explicit page titles:
  python tools/wiki_widgets/scan_widgets.py --pages Logs "Magic" "The_Gauntlet"

Note: This tool is read-only and does not modify the wiki.
"""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Set, Tuple

import requests


DEFAULT_BASE = "https://oldschool.runescape.wiki"


def api_get(base: str, params: Dict[str, str], session: Optional[requests.Session] = None) -> dict:
    url = base.rstrip("/") + "/api.php"
    p = {
        "format": "json",
        **params,
    }
    sess = session or requests.Session()
    r = sess.get(url, params=p, timeout=30)
    r.raise_for_status()
    return r.json()


def get_allpages(base: str, limit: int = 200, apnamespace: int = 0) -> List[str]:
    titles: List[str] = []
    apcontinue = None
    session = requests.Session()
    while len(titles) < limit:
        params = {
            "action": "query",
            "list": "allpages",
            "aplimit": str(min(500, limit - len(titles))),
            "apnamespace": str(apnamespace),
        }
        if apcontinue:
            params["apcontinue"] = apcontinue
        data = api_get(base, params, session)
        pages = data.get("query", {}).get("allpages", [])
        for p in pages:
            titles.append(p.get("title"))
            if len(titles) >= limit:
                break
        if "continue" in data:
            apcontinue = data["continue"].get("apcontinue")
            if not apcontinue:
                break
        else:
            break
    return titles


def categorize_module(module_name: str) -> str:
    """Categorize a MediaWiki ResourceLoader module by type."""
    if module_name.startswith('ext.gadget.'):
        return 'gadget'
    elif module_name.startswith('ext.'):
        return 'extension'
    elif module_name.startswith('mediawiki.'):
        return 'core'
    elif 'chart' in module_name.lower() or 'graph' in module_name.lower():
        return 'visualization'
    elif 'ui' in module_name.lower() or 'oojs' in module_name.lower():
        return 'interface'
    else:
        return 'other'


def parse_page(base: str, title: str, session: Optional[requests.Session] = None) -> Tuple[List[str], str, Dict[str, List[str]], Dict[str, List[str]]]:
    params = {
        "action": "parse",
        "page": title,
        "prop": "modules|modulescripts|modulestyles|text",
        "disableeditsection": "1",
        "disablelimitreport": "1",
        "disabletoc": "1",
        "wrapoutputclass": "",
    }
    data = api_get(base, params, session)
    parse = data.get("parse") or {}
    modules = parse.get("modules") or []
    module_scripts = parse.get("modulescripts") or []
    module_styles = parse.get("modulestyles") or []
    html = parse.get("text", {}).get("*") or ""
    
    # Separate JavaScript modules from CSS-only modules (Expert 1's fix)
    js_modules = list(set(modules + module_scripts))  # JavaScript modules
    css_modules = list(set(module_styles))            # CSS-only modules
    
    # Create module type mapping
    module_types = {
        'javascript': js_modules,
        'css': css_modules
    }
    
    # Categorize JavaScript modules only (CSS modules tracked separately)
    categorized_modules = {
        'gadget': [],
        'extension': [], 
        'core': [],
        'visualization': [],
        'interface': [],
        'other': []
    }
    
    for module in js_modules:
        category = categorize_module(module)
        categorized_modules[category].append(module)
    
    return modules, html, categorized_modules, module_types


CLASS_RE = re.compile(r'class\s*=\s*"([^"]+)"', re.IGNORECASE)
DATA_ATTR_RE = re.compile(r'\s(data-[a-zA-Z0-9_-]+)(?:\s*=|\s|>)')


def extract_classes(html: str) -> List[str]:
    classes: List[str] = []
    for m in CLASS_RE.finditer(html):
        val = m.group(1)
        for cls in re.split(r"\s+", val.strip()):
            if cls:
                classes.append(cls)
    return classes


def extract_data_attrs(html: str) -> List[str]:
    return [m.group(1) for m in DATA_ATTR_RE.finditer(html)]


def scan_local_assets(root: Path) -> Tuple[Set[str], List[Path]]:
    web_dir = root / "app" / "src" / "main" / "assets"
    exts = {".js", ".css"}
    files: List[Path] = []
    local_classes: Set[str] = set()

    if not web_dir.exists():
        return local_classes, files

    for sub in [web_dir / "web", web_dir / "js", web_dir / "styles"]:
        if not sub.exists():
            continue
        for path in sub.rglob("*"):
            if path.suffix in exts and path.is_file():
                files.append(path)
                try:
                    text = path.read_text(encoding="utf-8", errors="ignore")
                except Exception:
                    continue
                # Extract class names referenced in CSS and JS selectors.
                # Heuristics: dotted class names inside quotes, and CSS selectors.
                for m in re.finditer(r"[\"']\\?\.([A-Za-z0-9_-]+)", text):
                    local_classes.add(m.group(1))
                for m in re.finditer(r"\.[A-Za-z0-9_-]+", text):
                    # Avoid giant CSS dumps by limiting to reasonable names
                    name = m.group(0)[1:]
                    if 2 <= len(name) <= 64:
                        local_classes.add(name)
    return local_classes, files


def is_widgety_class(name: str) -> bool:
    # Stricter heuristic to avoid layout-only classes like ge-column.
    # 1) Known GE widget classes
    known = {
        "GEChartBox",
        "GEChartItems",
        "GEdatachart",
        "GEdataprices",
    }
    if name in known:
        return True

    # 2) Common interactive features
    patterns = [
        r"(^|-)map($|-)",
        r"(^|-)chart($|-)",
        r"(^|-)collapsible($|-)",
        r"(^|-)sortable($|-)",
        r"(^|-)infobox($|-)",
        r"(^|-)navbox($|-)",
        r"(^|-)video($|-)",
        r"(^|-)calc($|-)",
        r"(^|-)toggle($|-)",
        r"(^|-)tabs?($|-)",
        r"(^|-)carousel($|-)",
        r"(^|-)tooltip($|-)",
        r"(^|-)graph($|-)",
        r"(^|-)timer($|-)",
        r"(^|-)audio($|-)",
        r"(^|-)gallery($|-)",
        r"(^|-)rsmap($|-)",
        r"(^|-)mw-collapsible($|-)",
    ]
    if any(re.search(p, name, re.IGNORECASE) for p in patterns):
        return True

    # 3) Exclude commonly noisy classes explicitly
    excludes = {"ge-column"}
    if name.lower() in excludes:
        return False

    return False


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Scan OSRS Wiki for JS widgets and compare with app assets.")
    ap.add_argument("--base", default=DEFAULT_BASE, help="Wiki base URL (default: %(default)s)")
    ap.add_argument("--limit", type=int, default=200, help="Max pages to scan via AllPages")
    ap.add_argument("--namespace", type=int, default=0, help="Namespace for AllPages (default: 0)")
    ap.add_argument("--pages", nargs="*", help="Explicit page titles to scan (bypass AllPages)")
    ap.add_argument("--out", default="tools/js/out", help="Output directory for reports")
    ap.add_argument("--blacklist-modules", nargs="*", default=["ext.kartographer.frame", "ext.kartographer.link"], 
                    help="Modules to exclude from missing widgets report (default: map-related modules)")
    ap.add_argument("--blacklist-classes", nargs="*", default=["rsmap", "map-container", "kartographer-map", "mw-kartographer-container", "mw-kartographer-map", "mw-kartographer-interactive", "mw-kartographer-mapDialog-foot"], 
                    help="CSS classes to exclude from missing widgets report (default: map-related classes)")
    args = ap.parse_args(argv)

    base = args.base
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    start = time.time()

    if args.pages:
        titles = list(dict.fromkeys(args.pages))
    else:
        titles = get_allpages(base, limit=args.limit, apnamespace=args.namespace)

    session = requests.Session()

    modules_hist: Dict[str, int] = collections.Counter()
    modules_pages: Dict[str, List[str]] = collections.defaultdict(list)
    classes_hist: Dict[str, int] = collections.Counter()
    classes_pages: Dict[str, List[str]] = collections.defaultdict(list)
    data_attrs_hist: Dict[str, int] = collections.Counter()
    
    # Track categorized modules
    module_categories: Dict[str, Dict[str, int]] = {
        'gadget': collections.Counter(),
        'extension': collections.Counter(), 
        'core': collections.Counter(),
        'visualization': collections.Counter(),
        'interface': collections.Counter(),
        'other': collections.Counter()
    }
    module_category_pages: Dict[str, Dict[str, List[str]]] = {
        'gadget': collections.defaultdict(list),
        'extension': collections.defaultdict(list),
        'core': collections.defaultdict(list),
        'visualization': collections.defaultdict(list),
        'interface': collections.defaultdict(list),
        'other': collections.defaultdict(list)
    }
    
    # Track module types separately (Expert 1's recommendation)
    js_modules_hist: Dict[str, int] = collections.Counter()
    css_modules_hist: Dict[str, int] = collections.Counter()
    js_modules_pages: Dict[str, List[str]] = collections.defaultdict(list)
    css_modules_pages: Dict[str, List[str]] = collections.defaultdict(list)

    per_page: List[Dict[str, object]] = []

    for i, title in enumerate(titles, 1):
        try:
            modules, html, categorized_modules, module_types = parse_page(base, title, session)
        except Exception as e:
            print(f"[warn] Failed to parse {title}: {e}", file=sys.stderr)
            continue

        # Track all modules (backward compatibility)
        for m in modules or []:
            modules_hist[m] += 1
            if len(modules_pages[m]) < 5:
                modules_pages[m].append(title)
        
        # Track JavaScript and CSS modules separately (Expert 1's fix)
        for js_module in module_types.get('javascript', []):
            js_modules_hist[js_module] += 1
            if len(js_modules_pages[js_module]) < 5:
                js_modules_pages[js_module].append(title)
                
        for css_module in module_types.get('css', []):
            css_modules_hist[css_module] += 1
            if len(css_modules_pages[css_module]) < 5:
                css_modules_pages[css_module].append(title)
        
        # Track categorized modules
        for category, module_list in categorized_modules.items():
            for module in module_list:
                module_categories[category][module] += 1
                if len(module_category_pages[category][module]) < 5:
                    module_category_pages[category][module].append(title)

        classes = extract_classes(html)
        for c in classes:
            classes_hist[c] += 1
            if len(classes_pages[c]) < 3:
                classes_pages[c].append(title)

        for d in extract_data_attrs(html):
            data_attrs_hist[d] += 1

        if i % 25 == 0 or i == len(titles):
            print(f"Scanned {i}/{len(titles)} pages…")

        per_page.append({
            "title": title,
            "modules": modules,
            "class_count": len(set(classes)),
            "data_attrs_count": len(set(extract_data_attrs(html))),
        })

    repo_root = Path(__file__).resolve().parents[2]
    local_classes, asset_files = scan_local_assets(repo_root)

    # Filter out blacklisted items
    blacklist_modules = set(args.blacklist_modules)
    blacklist_classes = set(args.blacklist_classes)
    
    widgety_classes = {c for c, n in classes_hist.items() if is_widgety_class(c) and c not in blacklist_classes}
    missing_candidates = [
        {
            "class": c,
            "count": classes_hist[c],
            "sample_pages": classes_pages[c][:3],
        }
        for c in sorted(widgety_classes - local_classes, key=lambda x: (-classes_hist[x], x))
    ]

    # Filter modules report to exclude blacklisted modules
    filtered_modules = {m: count for m, count in modules_hist.items() if m not in blacklist_modules}
    modules_report = [
        {
            "module": m,
            "count": filtered_modules[m],
            "sample_pages": modules_pages[m][:5],
            "category": categorize_module(m),
        }
        for m in sorted(filtered_modules.keys(), key=lambda x: (-filtered_modules[x], x))
    ]
    
    # Generate categorized module reports
    categorized_reports = {}
    for category, modules in module_categories.items():
        if not modules:
            continue
        # Filter out blacklisted modules from each category
        filtered_category_modules = {m: count for m, count in modules.items() if m not in blacklist_modules}
        categorized_reports[category] = [
            {
                "module": m,
                "count": count,
                "sample_pages": module_category_pages[category][m][:5],
            }
            for m, count in sorted(filtered_category_modules.items(), key=lambda x: (-x[1], x[0]))
        ]

    # Generate JavaScript vs CSS module reports (Expert 1's critical fix)
    js_modules_filtered = {m: count for m, count in js_modules_hist.items() if m not in blacklist_modules}
    css_modules_filtered = {m: count for m, count in css_modules_hist.items() if m not in blacklist_modules}
    
    js_modules_report = [
        {
            "module": m,
            "count": count,
            "type": "javascript",
            "sample_pages": js_modules_pages[m][:5],
        }
        for m, count in sorted(js_modules_filtered.items(), key=lambda x: (-x[1], x[0]))
    ]
    
    css_modules_report = [
        {
            "module": m,
            "count": count,
            "type": "css",
            "sample_pages": css_modules_pages[m][:5],
        }
        for m, count in sorted(css_modules_filtered.items(), key=lambda x: (-x[1], x[0]))
    ]

    report = {
        "meta": {
            "base": base,
            "scanned_pages": len(titles),
            "duration_sec": round(time.time() - start, 2),
            "timestamp": dt.datetime.utcnow().isoformat() + "Z",
            "blacklisted_modules": sorted(blacklist_modules),
            "blacklisted_classes": sorted(blacklist_classes),
        },
        "modules": modules_report,
        "modules_by_category": categorized_reports,
        "javascript_modules": js_modules_report,
        "css_modules": css_modules_report,
        "top_classes": [
            {
                "class": c,
                "count": classes_hist[c],
                "sample_pages": classes_pages[c][:3],
            }
            for c in sorted(classes_hist.keys(), key=lambda x: (-classes_hist[x], x))[:300]
        ],
        "data_attributes": [
            {"attr": a, "count": n}
            for a, n in sorted(data_attrs_hist.items(), key=lambda x: (-x[1], x[0]))
        ],
        "local_assets": {
            "asset_files": [str(p.relative_to(repo_root)) for p in sorted(asset_files)],
            "classes": sorted(local_classes),
        },
        "missing_widget_classes": missing_candidates,
    }

    json_path = out_dir / "report.json"
    json_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    # Summary text
    summary_lines = []
    summary_lines.append(f"Base: {base}")
    summary_lines.append(f"Pages scanned: {len(titles)}")
    if blacklist_modules or blacklist_classes:
        summary_lines.append(f"Blacklisted modules: {', '.join(sorted(blacklist_modules)) if blacklist_modules else 'none'}")
        summary_lines.append(f"Blacklisted classes: {', '.join(sorted(blacklist_classes)) if blacklist_classes else 'none'}")
    summary_lines.append("")
    summary_lines.append("Top 20 modules:")
    for row in modules_report[:20]:
        category = row.get('category', 'unknown')
        summary_lines.append(f"- {row['module']} [{category}]: {row['count']} (e.g., {', '.join(row['sample_pages'])})")
    summary_lines.append("")
    
    # Add categorized module summary
    summary_lines.append("Modules by category:")
    for category in ['gadget', 'extension', 'visualization', 'interface', 'core', 'other']:
        if category in categorized_reports and categorized_reports[category]:
            count = len(categorized_reports[category])
            total_usage = sum(item['count'] for item in categorized_reports[category])
            top_modules = [item['module'] for item in categorized_reports[category][:3]]
            summary_lines.append(f"- {category.title()}: {count} modules, {total_usage} total uses (top: {', '.join(top_modules)})")
    summary_lines.append("")
    
    # Add JavaScript vs CSS module breakdown (Expert 1's critical insight)
    summary_lines.append(f"JavaScript modules found: {len(js_modules_report)}")
    summary_lines.append("Top 10 JavaScript modules (extractable):")
    for row in js_modules_report[:10]:
        summary_lines.append(f"- {row['module']}: {row['count']} (e.g., {', '.join(row['sample_pages'])})")
    summary_lines.append("")
    
    summary_lines.append(f"CSS-only modules found: {len(css_modules_report)}")  
    summary_lines.append("Top 10 CSS modules (styles only):")
    for row in css_modules_report[:10]:
        summary_lines.append(f"- {row['module']}: {row['count']} (e.g., {', '.join(row['sample_pages'])})")
    summary_lines.append("")
    
    summary_lines.append("Top 20 missing widget-like classes (heuristic):")
    for row in missing_candidates[:20]:
        summary_lines.append(f"- {row['class']}: {row['count']} (e.g., {', '.join(row['sample_pages'])})")

    (out_dir / "summary.txt").write_text("\n".join(summary_lines), encoding="utf-8")

    print(f"Wrote {json_path} and summary.txt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
