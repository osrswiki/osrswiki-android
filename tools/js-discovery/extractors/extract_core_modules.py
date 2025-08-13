#!/usr/bin/env python3
"""
Extract Core MediaWiki Infrastructure Modules

Downloads core MediaWiki ResourceLoader modules (startup, jquery, oojs, etc.)
that are required on every page and deploys them to the Android assets directory.

These modules contain the real MediaWiki ResourceLoader system including
mw.loader.implement() and dependency management.

Usage:
  python tools/js/extract_core_modules.py
  python tools/js/extract_core_modules.py --report tools/js/out/report.json
"""

import argparse
import json
import requests
import sys
from pathlib import Path
from typing import Dict


def extract_and_deploy_core_modules(report_path: str, output_dir: Path) -> None:
    """Extract core modules using proper ResourceLoader bundling methodology."""
    
    # Load scan report
    with open(report_path, 'r') as f:
        report = json.load(f)
    
    core_modules = report.get('core_infrastructure_modules', {})
    if not core_modules:
        print("[ERROR] No core infrastructure modules found in report")
        return
    
    print(f"[INFO] Found {len(core_modules)} core infrastructure modules")
    print("[INFO] Using ResourceLoader bundling methodology (matching live server behavior)")
    
    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Download modules using proper ResourceLoader bundling
    session = requests.Session()
    deployed_modules = {}
    
    # Extract startup separately (it's the only one that uses raw=1 on live server)
    if 'startup' in core_modules:
        startup_info = core_modules['startup']
        print(f"[INFO] Downloading startup module (raw extraction)...")
        
        try:
            response = session.get(startup_info['url'], timeout=30)
            response.raise_for_status()
            
            output_file = output_dir / 'startup.js'
            output_file.write_text(response.text, encoding='utf-8')
            
            deployed_modules['startup'] = {
                'original_name': 'startup',
                'deployed_filename': 'startup.js',
                'url': startup_info['url'],
                'content_length': len(response.text),
                'type': 'core-infrastructure',
                'order': 1,
                'extraction_method': 'raw'
            }
            
            print(f"[INFO]   Saved startup.js ({len(response.text)} bytes)")
            
        except Exception as e:
            print(f"[ERROR] Failed to download startup: {e}")
    
    # Extract other core modules in ResourceLoader bundle format (like live server)
    # Based on network trace: jquery,oojs,site are bundled together
    bundle_url = f"{report['meta']['base']}/load.php?lang=en-gb&modules=jquery,oojs,site&skin=vector"
    print(f"[INFO] Downloading core bundle: jquery,oojs,site")
    
    try:
        response = session.get(bundle_url, timeout=30)
        response.raise_for_status()
        bundle_content = response.text
        
        # Extract individual modules from bundle using ResourceLoader format
        modules_in_bundle = ['jquery', 'oojs', 'site']
        for module_name in modules_in_bundle:
            if module_name in core_modules:
                # Extract this module from the bundle
                module_content = extract_module_from_resourceloader_bundle(bundle_content, module_name)
                
                if module_content:
                    filename = f"{module_name.replace('.', '_')}.js"
                    output_file = output_dir / filename
                    output_file.write_text(module_content, encoding='utf-8')
                    
                    deployed_modules[module_name] = {
                        'original_name': module_name,
                        'deployed_filename': filename,
                        'url': bundle_url,
                        'content_length': len(module_content),
                        'type': 'core-infrastructure',
                        'order': get_module_load_order(module_name),
                        'extraction_method': 'resourceloader_bundle'
                    }
                    
                    print(f"[INFO]   Extracted {module_name}.js ({len(module_content)} bytes) from bundle")
                else:
                    print(f"[WARN] Could not extract {module_name} from bundle")
        
    except Exception as e:
        print(f"[ERROR] Failed to download core bundle: {e}")
    
    # Extract remaining individual modules that aren't in the main bundle
    for module_name, module_info in core_modules.items():
        if module_name not in deployed_modules:
            # Use ResourceLoader bundling (without raw=1)
            clean_url = module_info['url'].replace('&raw=1', '')
            print(f"[INFO] Downloading {module_name} (ResourceLoader format)...")
            
            try:
                response = session.get(clean_url, timeout=30)
                response.raise_for_status()
                
                filename = f"{module_name.replace('.', '_')}.js"
                output_file = output_dir / filename
                output_file.write_text(response.text, encoding='utf-8')
                
                deployed_modules[module_name] = {
                    'original_name': module_name,
                    'deployed_filename': filename,
                    'url': clean_url,
                    'content_length': len(response.text),
                    'type': 'core-infrastructure',
                    'order': get_module_load_order(module_name),
                    'extraction_method': 'resourceloader'
                }
                
                print(f"[INFO]   Saved {filename} ({len(response.text)} bytes)")
                
            except Exception as e:
                print(f"[ERROR] Failed to download {module_name}: {e}")
    
    # Generate deployment manifest
    manifest = {
        'extraction_timestamp': report['meta']['timestamp'],
        'core_modules': deployed_modules,
        'load_order': sorted(deployed_modules.keys(), key=lambda x: deployed_modules[x]['order'])
    }
    
    manifest_file = output_dir / 'core_modules_manifest.json'
    manifest_file.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    
    print(f"\n[SUCCESS] Extracted {len(deployed_modules)} core modules using ResourceLoader methodology")
    print(f"[INFO] Manifest saved to: {manifest_file}")
    print(f"[INFO] Load order: {manifest['load_order']}")


def extract_module_from_resourceloader_bundle(bundle_content: str, target_module: str) -> str:
    """Extract a specific module from a ResourceLoader bundle."""
    # ResourceLoader bundles use mw.loader.implement() calls
    # Pattern: mw.loader.implement("module_name", function(){ ... }, {}, {})
    
    # Try to find the mw.loader.implement call for our target module
    import re
    
    # Look for mw.loader.implement calls with the target module
    implement_pattern = rf'mw\.loader\.implement\s*\(\s*["\']({re.escape(target_module)})["\']'
    
    match = re.search(implement_pattern, bundle_content)
    if not match:
        # Module might not be in this bundle
        return None
    
    # Find the complete mw.loader.implement call
    start_pos = match.start()
    
    # Find the matching closing parenthesis
    paren_count = 0
    current_pos = start_pos
    implement_start = None
    
    while current_pos < len(bundle_content):
        char = bundle_content[current_pos]
        if char == '(' and implement_start is None:
            implement_start = current_pos + 1
            paren_count = 1
        elif implement_start is not None:
            if char == '(':
                paren_count += 1
            elif char == ')':
                paren_count -= 1
                if paren_count == 0:
                    # Found the complete implement call
                    implement_call = bundle_content[start_pos:current_pos + 1]
                    return implement_call
        current_pos += 1
    
    return None


def get_module_load_order(module_name: str) -> int:
    """Define the correct load order for core modules."""
    order_map = {
        'startup': 1,        # Must load first - contains ResourceLoader core
        'jquery': 2,         # jQuery foundation
        'oojs': 3,           # OOjs library  
        'mediawiki.base': 4, # MediaWiki core APIs
        'mediawiki.util': 5, # Utility functions
        'mediawiki.Uri': 6,  # URL utilities
        'site': 7            # Site configuration (if present)
    }
    return order_map.get(module_name, 99)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Extract core MediaWiki infrastructure modules")
    parser.add_argument('--report', 
                       default='tools/js/out/report.json',
                       help='Path to scan report JSON file')
    parser.add_argument('--output', 
                       default='app/src/main/assets/web/core',
                       help='Output directory for core modules')
    
    args = parser.parse_args(argv)
    
    report_path = Path(args.report)
    if not report_path.exists():
        print(f"[ERROR] Report file not found: {report_path}")
        return 1
    
    output_dir = Path(args.output)
    
    try:
        extract_and_deploy_core_modules(str(report_path), output_dir)
        return 0
    except Exception as e:
        print(f"[ERROR] Extraction failed: {e}")
        return 1


if __name__ == '__main__':
    sys.exit(main())