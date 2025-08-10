#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Fetches and sets up the latest OSRS cache from the OpenRS2 Archive,
only downloading if the local version is outdated.
"""

import requests
import json
import os
import shutil
import zipfile
import io
from datetime import datetime

def get_latest_osrs_cache_info():
    """Fetches metadata for the most recent live OSRS cache."""
    print("Fetching cache list from OpenRS2...")
    caches_url = "https://archive.openrs2.org/caches.json"
    response = requests.get(caches_url, timeout=30)
    response.raise_for_status()
    all_caches = response.json()

    latest_cache = None
    latest_timestamp = datetime.min.replace(tzinfo=None)

    for cache in all_caches:
        if cache.get("game") == "oldschool" and cache.get("environment") == "live":
            timestamp_str = cache.get("timestamp")
            if timestamp_str:
                timestamp = datetime.fromisoformat(timestamp_str.replace("Z", "")).replace(tzinfo=None)
                if not latest_cache or timestamp > latest_timestamp:
                    latest_timestamp = timestamp
                    latest_cache = cache
    
    if not latest_cache:
        raise RuntimeError("Could not find a valid live OSRS cache from the API.")

    return latest_cache

def main():
    """
    Checks the local cache version against the latest from OpenRS2 and
    downloads the new cache if necessary.
    """
    script_dir = os.path.dirname(os.path.abspath(__file__))
    cache_root_dir = os.path.join(script_dir, "openrs2_cache")
    version_file = os.path.join(cache_root_dir, "cache.version")
    
    try:
        print("Checking for latest cache version...")
        latest_cache_info = get_latest_osrs_cache_info()
        latest_id = latest_cache_info["id"]

        current_id = None
        if os.path.exists(version_file):
            with open(version_file, 'r') as f:
                current_id = int(f.read().strip())
        
        print(f"Latest available cache ID: {latest_id}")
        print(f"Currently installed cache ID: {current_id or 'None'}")

        if current_id == latest_id and os.path.exists(os.path.join(cache_root_dir, "cache")):
            print("Cache is up to date. Nothing to do.")
            return

        print("Local cache is outdated or missing. A new download is required.")
        
        # Remove old cache if it exists
        if os.path.exists(cache_root_dir):
            print(f"Removing old cache directory: {cache_root_dir}")
            shutil.rmtree(cache_root_dir)
        
        os.makedirs(cache_root_dir, exist_ok=True)

        # Download the new cache
        cache_scope = latest_cache_info["scope"]
        download_url = f"https://archive.openrs2.org/caches/{cache_scope}/{latest_id}/disk.zip"
        
        print(f"Downloading complete cache from: {download_url}")
        print("This may take several minutes...")

        r = requests.get(download_url, stream=True, timeout=600)
        r.raise_for_status()
        
        # Extract the zip file
        print("Download complete. Extracting cache...")
        with zipfile.ZipFile(io.BytesIO(r.content)) as z:
            z.extractall(cache_root_dir)
        
        # Write the new version file
        with open(version_file, 'w') as f:
            f.write(str(latest_id))
            
        print(f"\nSuccessfully set up cache version {latest_id} in '{os.path.abspath(cache_root_dir)}'.")

    except requests.RequestException as e:
        print(f"\nAn error occurred while downloading the files: {e}")
    except Exception as e:
        print(f"\nAn unexpected error occurred: {e}")

if __name__ == "__main__":
    main()
