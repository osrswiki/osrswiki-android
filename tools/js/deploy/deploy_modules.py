#!/usr/bin/env python3
"""
JavaScript Artifact Deployment Script

Deploys the captured MediaWiki artifacts to the Android app's assets directory.
"""

import shutil
import os
from pathlib import Path

# Define source and destination directories
PROJECT_ROOT = Path(__file__).resolve().parents[3]
SOURCE_DIR = PROJECT_ROOT / "tools/js/artifacts"
DEST_DIR = PROJECT_ROOT / "app/src/main/assets/web/mediawiki"

def main():
    """
    Main deployment function.
    """
    print("--- Starting MediaWiki Artifact Deployment ---")

    # 1. Check if the source directory exists
    print(f"[INFO] Source directory: {SOURCE_DIR}")
    if not SOURCE_DIR.exists():
        print(f"[ERROR] Source directory not found. Run the capture script first.")
        return 1

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

    # 3. Copy artifact files and subdirectories recursively
    print("[INFO] Copying artifacts and shims recursively...")
    copied_count = 0
    try:
        # Use shutil.copytree for recursive copy
        shutil.copytree(SOURCE_DIR, DEST_DIR, dirs_exist_ok=True) # dirs_exist_ok=True is for Python 3.8+
        # Count files copied (simple way, not perfect but good enough)
        for root, dirs, files in os.walk(DEST_DIR):
            copied_count += len(files)
        print(f"\n[SUCCESS] Deployment complete. {copied_count} artifacts and shims deployed.")
    except Exception as e:
        print(f"[ERROR] Failed to copy artifacts recursively: {e}")
        return 1

    print("--- MediaWiki Artifact Deployment Finished ---")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
