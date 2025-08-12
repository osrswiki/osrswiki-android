#!/usr/bin/env python3
"""
MediaWiki Infrastructure Deployment Script (Simplified)

Creates only essential deployment metadata for the Android app.
Per expert recommendations, complex infrastructure coordination has been eliminated
in favor of simple sequential script loading.

Usage:
    python tools/js/deploy/deploy_infrastructure.py
"""

import json
import os
import sys
from pathlib import Path


def main():
    # Find the project root and output directory
    script_dir = Path(__file__).parent
    project_root = script_dir.parent.parent.parent  # Go up 3 levels: deploy -> js -> tools -> project_root
    report_path = project_root / "tools" / "js" / "out" / "report.json"
    target_dir = project_root / "app" / "src" / "main" / "assets" / "web" / "app"
    
    print(f"[INFO] Infrastructure deployment simplified per expert recommendations")
    print(f"[INFO] Only creating essential deployment metadata")
    
    if not report_path.exists():
        print(f"[WARN] Report file not found: {report_path}")
        print("[INFO] Creating minimal deployment manifest without report data")
        
        # Create minimal manifest
        manifest = {
            "deployment_timestamp": "no-report-available",
            "source_base": "https://oldschool.runescape.wiki",
            "note": "Expert-simplified infrastructure - no complex coordination needed",
            "architecture": "sequential-script-loading"
        }
    else:
        # Load the scanner report
        try:
            with open(report_path, 'r', encoding='utf-8') as f:
                report = json.load(f)
                
            manifest = {
                "deployment_timestamp": report.get('meta', {}).get('timestamp', ''),
                "source_base": report.get('meta', {}).get('base', ''),
                "components_available": list(report.get('mediawiki_infrastructure', {}).keys()),
                "note": "Expert-simplified infrastructure - complex coordination eliminated",
                "architecture": "sequential-script-loading"
            }
        except Exception as e:
            print(f"[WARN] Failed to load report: {e}")
            manifest = {
                "deployment_timestamp": "error-loading-report",
                "source_base": "https://oldschool.runescape.wiki", 
                "note": "Expert-simplified infrastructure - no complex coordination needed",
                "architecture": "sequential-script-loading"
            }
    
    # Ensure target directory exists
    target_dir.mkdir(parents=True, exist_ok=True)
    
    # Expert recommendation: Eliminate complex infrastructure deployment
    # The sequential script loading approach doesn't need coordination scripts
    print("[INFO] Skipping complex infrastructure deployment per expert recommendations")
    print("[INFO] Sequential script loading eliminates need for RLQ, coordination, and deferred loading")
    
    manifest_path = target_dir / "infrastructure_deployment_manifest.json"
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2)
    print(f"[INFO]   ✅ Created simplified deployment manifest: {manifest_path}")
    
    print(f"\n[SUCCESS] Simplified infrastructure deployment complete!")
    print(f"[INFO] No complex coordination scripts needed with expert-recommended sequential loading")
    
    return 0


if __name__ == "__main__":
    sys.exit(main())