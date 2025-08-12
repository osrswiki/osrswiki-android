#!/usr/bin/env python3
"""
JavaScript Artifact Deployment Script

Deploys the captured MediaWiki artifacts to the Android app's assets directory.
"""

import shutil
from pathlib import Path

# Define source and destination directories
PROJECT_ROOT = Path(__file__).resolve().parents[3]
SOURCE_DIR = PROJECT_ROOT / "tools/js/artifacts"
DEST_DIR = PROJECT_ROOT / "app/src/main/assets/web/mediawiki"

EXPECTED_ARTIFACTS = [
    "startup.js",
    "page_bootstrap.js",
    "page_modules.js",
]

def main():
    """
    Main deployment function.
    """
    print("--- Starting MediaWiki Artifact Deployment ---")

    # 1. Check if the source directory and all artifacts exist
    print(f"[INFO] Source directory: {SOURCE_DIR}")
    if not SOURCE_DIR.exists():
        print(f"[ERROR] Source directory not found. Run the capture script first.")
        return 1

    missing_artifacts = False
    for artifact in EXPECTED_ARTIFACTS:
        if not (SOURCE_DIR / artifact).exists():
            print(f"[ERROR] Missing required artifact: {artifact}")
            missing_artifacts = True
    if missing_artifacts:
        print("[ERROR] Halting deployment due to missing artifacts.")
        return 1
    
    print("[SUCCESS] All required artifacts found in source directory.")

    # 2. Clear and recreate the destination directory
    print(f"[INFO] Destination directory: {DEST_DIR}")
    if DEST_DIR.exists():
        print("[INFO] Clearing existing destination directory...")
        try:
            shutil.rmtree(DEST_DIR)
        except OSError as e:
            print(f"[ERROR] Failed to remove destination directory: {e}")
            return 1
    
    print("[INFO] Creating new destination directory...")
    try:
        DEST_DIR.mkdir(parents=True, exist_ok=True)
    except OSError as e:
        print(f"[ERROR] Failed to create destination directory: {e}")
        return 1

    # 3. Copy artifact files to the destination
    print("[INFO] Copying artifacts...")
    copied_count = 0
    for artifact in EXPECTED_ARTIFACTS:
        source_file = SOURCE_DIR / artifact
        dest_file = DEST_DIR / artifact
        try:
            shutil.copy2(source_file, dest_file)
            print(f"  -> Copied {artifact}")
            copied_count += 1
        except Exception as e:
            print(f"[ERROR] Failed to copy {artifact}: {e}")
            return 1

    print(f"\n[SUCCESS] Deployment complete. {copied_count} artifacts deployed.")
    print("--- MediaWiki Artifact Deployment Finished ---")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())