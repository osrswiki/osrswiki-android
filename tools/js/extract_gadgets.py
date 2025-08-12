#!/usr/bin/env python3
"""
Extract MediaWiki gadgets and their definitions from the OSRS Wiki.

This script extracts:
1. Gadget definitions from MediaWiki:Gadgets-definition
2. Gadget JavaScript code from MediaWiki:Gadget-*.js pages
3. Gadget dependencies and configuration
"""

import json
import re
import requests
from pathlib import Path
from typing import Dict, List, Optional, Any
import sys


def parse_gadget_definition(line: str) -> Optional[Dict[str, Any]]:
    """Parse a single gadget definition line."""
    if not line or line.startswith('#'):
        return None
    
    # Format: * gadgetname[options]|dependency1,dependency2|page1.js|page2.js|page3.css
    match = re.match(r'^\*\s*([^[\|]+)(?:\[([^\]]*)\])?\|(.*)$', line)
    if not match:
        return None
    
    name = match.group(1).strip()
    options = match.group(2) or ''
    parts = match.group(3).split('|')
    
    # Parse options
    opts = {}
    if options:
        for opt in options.split(','):
            opt = opt.strip()
            if '=' in opt:
                key, value = opt.split('=', 1)
                opts[key.strip()] = value.strip()
            else:
                opts[opt] = True
    
    # Parse dependencies and files
    dependencies = []
    scripts = []
    styles = []
    
    for part in parts:
        part = part.strip()
        if not part:
            continue
        
        # Check if it's a dependency list (contains commas or starts with ext.)
        if ',' in part or part.startswith('ext.'):
            dependencies = [d.strip() for d in part.split(',') if d.strip()]
        # Check if it's a CSS file
        elif part.endswith('.css'):
            styles.append(f"MediaWiki:Gadget-{part}")
        # Otherwise it's a JS file
        elif part.endswith('.js'):
            scripts.append(f"MediaWiki:Gadget-{part}")
        else:
            # Assume JS if no extension
            scripts.append(f"MediaWiki:Gadget-{part}.js")
    
    return {
        'name': name,
        'options': opts,
        'dependencies': dependencies,
        'scripts': scripts,
        'styles': styles
    }


def fetch_gadget_definitions(base_url: str) -> List[Dict[str, Any]]:
    """Fetch and parse gadget definitions from MediaWiki:Gadgets-definition."""
    
    # Fetch the gadget definitions page
    url = f"{base_url}/wiki/MediaWiki:Gadgets-definition?action=raw"
    response = requests.get(url)
    response.raise_for_status()
    
    gadgets = []
    current_section = 'default'
    
    for line in response.text.split('\n'):
        line = line.strip()
        
        # Section header
        if line.startswith('==') and line.endswith('=='):
            current_section = line.strip('= ')
            continue
        
        # Parse gadget definition
        gadget = parse_gadget_definition(line)
        if gadget:
            gadget['section'] = current_section
            gadgets.append(gadget)
    
    return gadgets


def fetch_gadget_code(base_url: str, page_name: str) -> Optional[str]:
    """Fetch JavaScript or CSS code from a MediaWiki page."""
    
    # Convert page name to URL
    url = f"{base_url}/wiki/{page_name}?action=raw"
    
    try:
        response = requests.get(url)
        response.raise_for_status()
        return response.text
    except requests.exceptions.RequestException as e:
        print(f"Failed to fetch {page_name}: {e}", file=sys.stderr)
        return None


def extract_gadgets(base_url: str = "https://oldschool.runescape.wiki") -> Dict[str, Any]:
    """Extract all gadgets and their code from the wiki."""
    
    print("Fetching gadget definitions...")
    gadgets = fetch_gadget_definitions(base_url)
    print(f"Found {len(gadgets)} gadgets")
    
    # Extract code for each gadget
    extracted = {}
    
    for gadget in gadgets:
        name = gadget['name']
        print(f"Extracting gadget: {name}")
        
        gadget_data = {
            'definition': gadget,
            'scripts': {},
            'styles': {}
        }
        
        # Fetch script files
        for script_page in gadget['scripts']:
            print(f"  Fetching script: {script_page}")
            code = fetch_gadget_code(base_url, script_page)
            if code:
                # Clean up the page name for use as a filename
                filename = script_page.replace('MediaWiki:Gadget-', '').replace('/', '_')
                gadget_data['scripts'][filename] = code
        
        # Fetch style files
        for style_page in gadget['styles']:
            print(f"  Fetching style: {style_page}")
            code = fetch_gadget_code(base_url, style_page)
            if code:
                filename = style_page.replace('MediaWiki:Gadget-', '').replace('/', '_')
                gadget_data['styles'][filename] = code
        
        extracted[name] = gadget_data
    
    return extracted


def save_gadgets(gadgets: Dict[str, Any], output_dir: Path):
    """Save extracted gadgets to files."""
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Save gadget definitions
    definitions_file = output_dir / "gadget_definitions.json"
    definitions = {name: data['definition'] for name, data in gadgets.items()}
    with open(definitions_file, 'w') as f:
        json.dump(definitions, f, indent=2)
    print(f"Saved gadget definitions to {definitions_file}")
    
    # Save gadget scripts and styles
    for name, data in gadgets.items():
        # Create directory for this gadget
        gadget_dir = output_dir / name.replace(' ', '_')
        
        # Only create directory if there are files to save
        if data['scripts'] or data['styles']:
            gadget_dir.mkdir(parents=True, exist_ok=True)
            
            # Save scripts
            for filename, code in data['scripts'].items():
                file_path = gadget_dir / filename
                with open(file_path, 'w') as f:
                    f.write(code)
                print(f"  Saved {file_path}")
            
            # Save styles
            for filename, code in data['styles'].items():
                file_path = gadget_dir / filename
                with open(file_path, 'w') as f:
                    f.write(code)
                print(f"  Saved {file_path}")


def main():
    """Main function."""
    
    # Focus on chart-related gadgets first
    priority_gadgets = [
        'GECharts', 'GECharts-core',
        'Charts', 'Charts-core',
        'rsw-util',  # Dependency
    ]
    
    print("Extracting MediaWiki gadgets from OSRS Wiki...")
    
    # Extract all gadgets
    gadgets = extract_gadgets()
    
    # Filter to priority gadgets for now
    filtered = {}
    for name in priority_gadgets:
        if name in gadgets:
            filtered[name] = gadgets[name]
        else:
            print(f"Warning: Priority gadget '{name}' not found")
    
    # Save extracted gadgets
    output_dir = Path(__file__).parent / "gadgets"
    save_gadgets(filtered, output_dir)
    
    print(f"\nExtraction complete! Saved {len(filtered)} gadgets to {output_dir}")
    
    # Print summary
    print("\nGadget Summary:")
    for name, data in filtered.items():
        print(f"\n{name}:")
        print(f"  Dependencies: {', '.join(data['definition']['dependencies']) or 'None'}")
        print(f"  Scripts: {len(data['scripts'])}")
        print(f"  Styles: {len(data['styles'])}")
        print(f"  Options: {data['definition']['options']}")


if __name__ == "__main__":
    main()