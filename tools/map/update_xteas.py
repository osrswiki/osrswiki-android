#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Fetches the latest OSRS XTEA keys from the OpenRS2 Archive API.
"""

import requests
import json
import os
from datetime import datetime

def main():
    """
    Finds the latest live OSRS cache and downloads its corresponding XTEA key file.
    """
    print("Fetching cache list from OpenRS2...")
    try:
        # 1. Fetch the list of all caches
        caches_url = "https://archive.openrs2.org/caches.json"
        response = requests.get(caches_url, timeout=15)
        response.raise_for_status()
        all_caches = response.json()

        # 2. Find the most recent live OSRS cache with a valid timestamp
        latest_cache = None
        latest_timestamp = datetime.min
        
        for cache in all_caches:
            if cache.get("game") == "oldschool" and cache.get("environment") == "live":
                timestamp_str = cache.get("timestamp")
                if timestamp_str:
                    timestamp = datetime.fromisoformat(timestamp_str.replace("Z", "+00:00"))
                    if latest_cache is None or timestamp > latest_timestamp:
                        latest_timestamp = timestamp
                        latest_cache = cache
        
        if not latest_cache:
            print("Error: Could not find a valid live OSRS cache.")
            return

        cache_id = latest_cache["id"]
        cache_scope = latest_cache["scope"]
        print(f"Found latest live cache: ID={cache_id}, Scope='{cache_scope}', Timestamp={latest_timestamp.isoformat()}")

        # 3. Construct the URL for the keys and download the file
        keys_url = f"https://archive.openrs2.org/caches/{cache_scope}/{cache_id}/keys.json"
        print(f"Downloading keys from: {keys_url}")
        
        keys_response = requests.get(keys_url, timeout=15)
        keys_response.raise_for_status()
        
        # 4. Save the keys to the map-dumper's xtea.json
        script_dir = os.path.dirname(os.path.abspath(__file__))
        output_path = os.path.join(script_dir, "map-dumper", "xtea.json")

        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(keys_response.text)
            
        print(f"\nSuccessfully updated '{os.path.abspath(output_path)}'.")

    except requests.RequestException as e:
        print(f"\nAn error occurred while downloading the files: {e}")
    except Exception as e:
        print(f"\nAn unexpected error occurred: {e}")

if __name__ == "__main__":
    main()
