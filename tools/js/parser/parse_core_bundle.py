#!/usr/bin/env python3
"""
Core Modules Bundle Parser

Parses the comprehensive core modules bundle extracted from the server
and splits it into individual ResourceLoader modules with proper
mw.loader.implement() calls.

This addresses the root cause of UMD environment conflicts by allowing
each module to be loaded through ResourceLoader's proper module system
instead of as a conflicting direct script bundle.
"""

import json
import re
import os
from pathlib import Path
from typing import Dict, List, Tuple


class CoreBundleParser:
    def __init__(self, bundle_path: str, output_dir: str):
        self.bundle_path = Path(bundle_path)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
    def parse_bundle(self) -> Dict[str, str]:
        """Parse the core bundle and extract individual modules."""
        print(f"[INFO] Parsing core bundle: {self.bundle_path}")
        
        with open(self.bundle_path, 'r', encoding='utf-8') as f:
            bundle_content = f.read()
        
        modules = {}
        
        # Extract modules based on known patterns and boundaries
        modules['jquery'] = self._extract_jquery(bundle_content)
        modules['mediawiki.base'] = self._extract_mediawiki_base(bundle_content)
        modules['mediawiki.util'] = self._extract_mediawiki_util(bundle_content) 
        modules['oojs'] = self._extract_oojs(bundle_content)
        
        print(f"[INFO] Extracted {len(modules)} modules from core bundle")
        return modules
    
    def _extract_jquery(self, content: str) -> str:
        """Extract jQuery module from the bundle."""
        # jQuery starts with the comment and UMD wrapper
        start_pattern = r'/\*!\s*\* jQuery JavaScript Library'
        end_pattern = r'\}\)\(window\);'
        
        start_match = re.search(start_pattern, content)
        if not start_match:
            print("[WARN] Could not find jQuery start marker")
            return ""
        
        start_pos = start_match.start()
        
        # Find the end of jQuery (before MediaWiki modules start)
        # Look for the end of jQuery's UMD wrapper
        search_start = start_pos + 1000  # Start searching after the beginning
        end_matches = list(re.finditer(end_pattern, content[search_start:]))
        
        if not end_matches:
            print("[WARN] Could not find jQuery end marker")
            return ""
        
        # Take the first occurrence after the start
        end_pos = search_start + end_matches[0].end()
        
        jquery_content = content[start_pos:end_pos]
        print(f"[INFO] Extracted jQuery: {len(jquery_content)} characters")
        return jquery_content
    
    def _extract_mediawiki_base(self, content: str) -> str:
        """Extract mediawiki.base module containing mw.loader.using."""
        # Look for the mw.loader.using function as a key marker
        using_pattern = r'mw\.loader\.using\s*=\s*function'
        using_match = re.search(using_pattern, content)
        
        if not using_match:
            print("[WARN] Could not find mw.loader.using in bundle")
            return ""
        
        # Search backwards to find the start of the MediaWiki base module
        # Look for module start patterns
        search_area = content[:using_match.start()]
        
        # MediaWiki modules typically start with specific patterns
        base_start_patterns = [
            r'mw\.loader\.implement\(',
            r'/\*\*\s*\* MediaWiki',
            r'window\.mw\s*=',
            r'function.*mediawiki'
        ]
        
        start_pos = 0
        for pattern in base_start_patterns:
            matches = list(re.finditer(pattern, search_area, re.IGNORECASE))
            if matches:
                # Take the last match before mw.loader.using
                start_pos = max(start_pos, matches[-1].start())
        
        # Find end by looking for the next major module or end markers
        search_end = content[using_match.end():]
        end_patterns = [
            r'mw\.util\s*=',  # Start of util module
            r'OO\.ui\s*=',    # Start of OOjs
            r'module\.exports\s*=\s*OO'  # OOjs export
        ]
        
        end_pos = len(content)
        for pattern in end_patterns:
            match = re.search(pattern, search_end)
            if match:
                candidate_end = using_match.end() + match.start()
                end_pos = min(end_pos, candidate_end)
        
        base_content = content[start_pos:end_pos]
        print(f"[INFO] Extracted mediawiki.base: {len(base_content)} characters")
        return base_content
    
    def _extract_mediawiki_util(self, content: str) -> str:
        """Extract mediawiki.util module."""
        # Look for mw.util assignments and functions
        util_pattern = r'mw\.util\s*='
        util_match = re.search(util_pattern, content)
        
        if not util_match:
            print("[WARN] Could not find mw.util in bundle")
            return ""
        
        start_pos = util_match.start()
        
        # Find end by looking for next major module
        search_end = content[start_pos + 1000:]
        end_patterns = [
            r'OO\.ui\s*=',
            r'/\*\*\s*\* OOjs',
            r'module\.exports\s*=\s*OO'
        ]
        
        end_pos = len(content)
        for pattern in end_patterns:
            match = re.search(pattern, search_end)
            if match:
                candidate_end = start_pos + 1000 + match.start()
                end_pos = min(end_pos, candidate_end)
        
        util_content = content[start_pos:end_pos]
        print(f"[INFO] Extracted mediawiki.util: {len(util_content)} characters")
        return util_content
    
    def _extract_oojs(self, content: str) -> str:
        """Extract OOjs module."""
        # OOjs typically starts with its own comment header
        start_patterns = [
            r'/\*\*\s*\* OOjs',
            r'OO\.ui\s*=',
            r'function.*OO\('
        ]
        
        start_pos = 0
        for pattern in start_patterns:
            match = re.search(pattern, content)
            if match:
                start_pos = max(start_pos, match.start())
        
        # OOjs ends with the module.exports assignment we saw
        end_pattern = r'window\.OO\s*=\s*module\.exports;'
        end_match = re.search(end_pattern, content)
        
        if not end_match:
            print("[WARN] Could not find OOjs end marker")
            # Fallback to end of content before mw.loader.state
            state_pattern = r'mw\.loader\.state\('
            state_match = re.search(state_pattern, content)
            if state_match:
                end_pos = state_match.start()
            else:
                end_pos = len(content)
        else:
            end_pos = end_match.end()
        
        oojs_content = content[start_pos:end_pos]
        print(f"[INFO] Extracted oojs: {len(oojs_content)} characters")
        return oojs_content
    
    def create_implement_calls(self, modules: Dict[str, str]) -> Dict[str, str]:
        """Create mw.loader.implement() calls for each extracted module."""
        implement_calls = {}
        
        # Module dependencies based on MediaWiki standards
        module_deps = {
            'jquery': [],
            'oojs': [],
            'mediawiki.base': ['jquery', 'oojs'],
            'mediawiki.util': ['jquery', 'mediawiki.base']
        }
        
        for module_name, module_content in modules.items():
            if not module_content.strip():
                continue
                
            # Create the implement call
            deps = module_deps.get(module_name, [])
            deps_str = json.dumps(deps) if deps else '[]'
            
            implement_call = f"""/**
 * {module_name} module
 * Extracted from server core bundle and properly formatted for ResourceLoader
 */
mw.loader.implement(
    '{module_name}',
    function() {{
        {module_content}
    }},
    {{}}, // styles
    {{}}, // messages  
    {{}}, // templates
    null, // deprecation warning
    {deps_str} // dependencies
);

console.log('[CORE-MODULE] Deployed {module_name} via mw.loader.implement');
"""
            
            implement_calls[module_name] = implement_call
            print(f"[INFO] Created implement call for {module_name}")
        
        return implement_calls
    
    def save_modules(self, implement_calls: Dict[str, str]) -> List[str]:
        """Save individual module files."""
        saved_files = []
        
        for module_name, implement_call in implement_calls.items():
            filename = f"{module_name.replace('.', '_')}.js"
            filepath = self.output_dir / filename
            
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(implement_call)
            
            saved_files.append(str(filepath))
            print(f"[INFO] Saved {module_name} to {filepath}")
        
        return saved_files
    
    def run(self) -> List[str]:
        """Parse bundle and create individual module files."""
        modules = self.parse_bundle()
        implement_calls = self.create_implement_calls(modules)
        saved_files = self.save_modules(implement_calls)
        
        print(f"[SUCCESS] Parsed core bundle into {len(saved_files)} individual modules")
        return saved_files


def main():
    bundle_path = "/Users/miyawaki/Android/osrswiki/app/src/main/assets/web/infrastructure/core_modules_bundle.js"
    output_dir = "/Users/miyawaki/Android/osrswiki/tools/js/out/core_modules"
    
    parser = CoreBundleParser(bundle_path, output_dir)
    saved_files = parser.run()
    
    print("\n[INFO] Created individual core modules:")
    for filepath in saved_files:
        print(f"  - {filepath}")


if __name__ == "__main__":
    main()