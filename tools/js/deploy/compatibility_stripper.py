#!/usr/bin/env python3
"""
External Module Compatibility Stripper

Removes embedded MediaWiki and jQuery compatibility stubs from deployed external modules.
These stubs cause race conditions with the shared compatibility layer.

Usage:
  python tools/js/deploy/compatibility_stripper.py --strip-all
  python tools/js/deploy/compatibility_stripper.py --modules ge_charts_loader.js
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import List, Optional, Tuple

# Paths
PROJECT_ROOT = Path(__file__).resolve().parents[3]  # Go up 3 levels from tools/js/deploy/
APP_ASSETS_EXTERNAL_DIR = PROJECT_ROOT / "app/src/main/assets/web/external"

class CompatibilityStripper:
    """Strips embedded compatibility layers from external modules."""
    
    def __init__(self, dry_run: bool = False):
        self.dry_run = dry_run
        self.external_dir = APP_ASSETS_EXTERNAL_DIR
        self.processed_files: List[str] = []
        
    def log(self, msg: str, level: str = 'info') -> None:
        """Simple logging with dry-run prefix."""
        prefix = {'info': '[INFO]', 'warn': '[WARN]', 'error': '[ERROR]'}.get(level, '[INFO]')
        if self.dry_run:
            prefix = f"[DRY-RUN] {prefix}"
        print(f"{prefix} {msg}")
        
    def detect_compatibility_boundaries(self, content: str) -> Tuple[int, int]:
        """Detect start and end of embedded compatibility layer."""
        lines = content.split('\n')
        
        # Find start of compatibility layer (usually starts with MediaWiki comment)
        start_line = -1
        for i, line in enumerate(lines):
            stripped = line.strip()
            if (stripped.startswith('// MediaWiki API Compatibility') or 
                stripped.startswith('if (typeof window.mw === \'undefined\')')):
                start_line = i
                break
                
        if start_line == -1:
            return -1, -1  # No compatibility layer found
            
        # Find end of compatibility layer (look for "// Adapted module:" comment)
        end_line = -1
        for i in range(start_line, len(lines)):
            stripped = lines[i].strip()
            if stripped.startswith('// Adapted module:'):
                end_line = i - 1  # End before the module comment
                break
                
        # If no "Adapted module" comment, look for start of IIFE pattern
        if end_line == -1:
            for i in range(start_line, len(lines)):
                stripped = lines[i].strip()
                if stripped == '(function() {' or stripped.startswith('(function()'):
                    # Found IIFE start, compatibility layer ends before this
                    end_line = i - 1
                    break
                    
        # Fallback: look for jQuery compatibility ending
        if end_line == -1:
            for i in range(start_line, len(lines)):
                stripped = lines[i].strip()
                if 'window.jQuery = window.$;' in stripped:
                    # Found end of jQuery compatibility, look a few lines ahead
                    for j in range(i + 1, min(i + 5, len(lines))):
                        if lines[j].strip() == '':
                            end_line = j
                            break
                    break
        
        return start_line, end_line
        
    def strip_module_compatibility(self, module_file: Path) -> bool:
        """Strip embedded compatibility layer from a single module."""
        if not module_file.exists():
            self.log(f"Module file not found: {module_file}", 'error')
            return False
            
        try:
            # Read original content
            original_content = module_file.read_text(encoding='utf-8')
            original_lines = original_content.split('\n')
            
            # Detect compatibility boundaries
            start_line, end_line = self.detect_compatibility_boundaries(original_content)
            
            if start_line == -1:
                self.log(f"No embedded compatibility layer found in {module_file.name}")
                return True  # Nothing to strip, that's fine
                
            if end_line == -1:
                self.log(f"Could not detect end of compatibility layer in {module_file.name}", 'warn')
                return False
                
            self.log(f"Stripping {module_file.name}:")
            self.log(f"  Compatibility layer: lines {start_line + 1}-{end_line + 1} ({end_line - start_line + 1} lines)")
            
            # Calculate what's being removed
            compatibility_lines = original_lines[start_line:end_line + 1]
            compatibility_content = '\n'.join(compatibility_lines)
            
            # Show preview of what's being stripped
            preview_lines = [line.strip() for line in compatibility_lines[:5] if line.strip()]
            if len(preview_lines) > 0:
                self.log(f"  Removing: {preview_lines[0][:60]}...")
                
            # Extract the actual module code (everything after compatibility layer)
            module_lines = original_lines[end_line + 1:]
            
            # Skip empty lines at the start of module code
            while module_lines and not module_lines[0].strip():
                module_lines.pop(0)
                
            if not module_lines:
                self.log(f"No module code found after stripping compatibility layer", 'error')
                return False
                
            # Reconstruct content without compatibility layer
            stripped_content = '\n'.join(module_lines)
            
            # Show what remains
            module_preview = stripped_content.strip()[:100].replace('\n', ' ')
            self.log(f"  Remaining module code: {module_preview}...")
            self.log(f"  Size reduction: {len(original_content)} → {len(stripped_content)} bytes ({len(original_content) - len(stripped_content)} bytes removed)")
            
            if not self.dry_run:
                # Write stripped content back to file
                module_file.write_text(stripped_content, encoding='utf-8')
                self.log(f"  ✅ Stripped {module_file.name}")
            else:
                self.log(f"  [DRY-RUN] Would strip {module_file.name}")
                
            self.processed_files.append(module_file.name)
            return True
            
        except Exception as e:
            self.log(f"Failed to strip {module_file.name}: {e}", 'error')
            return False
            
    def strip_all_modules(self, specific_modules: Optional[List[str]] = None) -> bool:
        """Strip compatibility layers from all external modules."""
        if not self.external_dir.exists():
            self.log(f"External modules directory not found: {self.external_dir}", 'error')
            return False
            
        # Get list of modules to process
        if specific_modules:
            module_files = []
            for module_name in specific_modules:
                module_path = self.external_dir / module_name
                if module_path.exists():
                    module_files.append(module_path)
                else:
                    self.log(f"Module not found: {module_name}", 'warn')
        else:
            # Process all .js files except external libraries
            module_files = []
            for js_file in self.external_dir.glob("*.js"):
                # Skip known external libraries (they don't have embedded compatibility)
                if js_file.name in ['highcharts-stock.js', 'jquery.js', 'chart.js', 'd3.js']:
                    continue
                module_files.append(js_file)
                
        if not module_files:
            self.log("No modules to process")
            return True
            
        self.log(f"Processing {len(module_files)} external modules...")
        
        success_count = 0
        for module_file in module_files:
            if self.strip_module_compatibility(module_file):
                success_count += 1
                
        self.log(f"Compatibility stripping complete: {success_count}/{len(module_files)} modules processed")
        
        if self.processed_files:
            self.log("Processed modules:")
            for filename in self.processed_files:
                self.log(f"  - {filename}")
                
        return success_count == len(module_files)
        

def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Strip embedded compatibility layers from external modules")
    ap.add_argument("--strip-all", action="store_true", help="Strip compatibility from all external modules")
    ap.add_argument("--modules", nargs="*", help="Specific module files to strip (e.g., ge_charts_loader.js)")
    ap.add_argument("--dry-run", action="store_true", help="Show what would be stripped without making changes")
    ap.add_argument("--list", action="store_true", help="List available external modules")
    args = ap.parse_args(argv)
    
    stripper = CompatibilityStripper(dry_run=args.dry_run)
    
    if args.list:
        if not stripper.external_dir.exists():
            print("External modules directory not found")
            return 1
            
        modules = [f.name for f in stripper.external_dir.glob("*.js")]
        print(f"Available external modules ({len(modules)}):")
        for module in sorted(modules):
            print(f"  - {module}")
        return 0
    
    if args.strip_all:
        success = stripper.strip_all_modules()
        return 0 if success else 1
    elif args.modules:
        success = stripper.strip_all_modules(args.modules)
        return 0 if success else 1
    else:
        print("Error: Specify --strip-all, --modules, or --list")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())