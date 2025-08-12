#!/usr/bin/env python3
"""
Extract OOUI dependencies for GE Charts
Adds missing MediaWiki core modules that GE charts depend on
"""

import requests
import json
import os
from pathlib import Path

def extract_ooui_module(module_name):
    """Extract a MediaWiki module from the wiki."""
    print(f"Extracting {module_name}...")
    
    url = f"https://oldschool.runescape.wiki/load.php?modules={module_name}&only=scripts"
    
    try:
        response = requests.get(url)
        response.raise_for_status()
        
        if len(response.text) < 100:
            print(f"Warning: {module_name} returned very short content: {response.text[:100]}")
            return None
            
        return response.text
    except Exception as e:
        print(f"Failed to extract {module_name}: {e}")
        return None

def main():
    # OOUI modules needed by GE Charts (including the core OOjs library)
    ooui_modules = [
        "oojs",  # Core OOjs library that provides the OO global object
        "oojs-ui-core",
        "oojs-ui.styles.icons-media"
    ]
    
    out_dir = Path("out")
    out_dir.mkdir(exist_ok=True)
    
    # Load existing registry
    registry_path = out_dir / "module_registry.json"
    if registry_path.exists():
        with open(registry_path) as f:
            registry = json.load(f)
    else:
        registry = {
            "extracted_at": 0,
            "total_modules": 0,
            "modules": {}
        }
    
    assets_dir = Path("../../app/src/main/assets/web/external")
    assets_dir.mkdir(parents=True, exist_ok=True)
    
    for module_name in ooui_modules:
        # Skip if already exists
        if module_name in registry["modules"]:
            print(f"Skipping {module_name} - already in registry")
            continue
            
        content = extract_ooui_module(module_name)
        if not content:
            continue
            
        # Generate filename
        safe_name = module_name.replace(".", "_").replace("-", "_")
        filename = f"{safe_name}.js"
        
        # Write to assets
        asset_path = assets_dir / filename
        with open(asset_path, 'w') as f:
            f.write(content)
        print(f"Wrote {asset_path}")
        
        # Add to registry
        registry["modules"][module_name] = {
            "name": module_name,
            "original_name": module_name,
            "type": "mediawiki-core",
            "dependencies": [],  # OOUI has complex deps, will be handled by MediaWiki compatibility layer
            "source_length": len(content),
            "output_file": str(asset_path),
            "extracted_at": __import__('time').time()
        }
    
    # Update registry
    registry["total_modules"] = len(registry["modules"])
    registry["extracted_at"] = __import__('time').time()
    
    with open(registry_path, 'w') as f:
        json.dump(registry, f, indent=2)
    
    print(f"Updated registry with {len(ooui_modules)} OOUI modules")
    print("OOUI modules extracted successfully!")

if __name__ == "__main__":
    main()