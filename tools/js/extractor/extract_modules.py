#!/usr/bin/env python3
"""
OSRS Wiki JS Module Extractor

Automatically extracts and adapts MediaWiki ResourceLoader modules for standalone use.
Focuses on gadgets and extensions that provide interactive functionality missing from the app.

Usage:
  python tools/js/extractor/extract_modules.py --modules ext.Tabber ext.cite.ux-enhancements
  python tools/js/extractor/extract_modules.py --auto-from-scan tools/js/out/report.json
  python tools/js/extractor/extract_modules.py --priority-only

Features:
- Automatic module discovery from widget scan reports
- Dependency resolution and extraction
- MediaWiki API compatibility layer injection
- Module adaptation for standalone use
- Performance optimization and bundling
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple, Union
from urllib.parse import urljoin, urlparse

import requests


DEFAULT_BASE = "https://oldschool.runescape.wiki"
DEFAULT_OUT = "tools/js/out"

# Priority modules for initial implementation (high impact, low complexity)
PRIORITY_MODULES = [
    "ext.Tabber",
    "ext.cite.ux-enhancements", 
    "mediawiki.page.gallery"
]

# All modules are handled uniformly by the automated extraction system

# MediaWiki core APIs that need polyfills
MW_API_PATTERNS = [
    r'mw\.loader\.',
    r'mw\.util\.',
    r'mw\.config\.',
    r'mw\.message\.',
    r'mw\.user\.',
    r'mw\.cookie\.',
    r'\$\(',  # jQuery usage
]


class ModuleExtractor:
    """Extracts and adapts MediaWiki ResourceLoader modules."""
    
    def __init__(self, base_url: str = DEFAULT_BASE, output_dir: str = DEFAULT_OUT, resourceloader_mode: bool = False):
        self.base_url = base_url.rstrip('/')
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.session = requests.Session()
        self.session.headers.update({'User-Agent': 'OSRS-Wiki-App-Module-Extractor/1.0'})
        
        # NEW: ResourceLoader mode extracts modules to work with real MediaWiki infrastructure
        self.resourceloader_mode = resourceloader_mode
        
        # Module registry for tracking dependencies and metadata
        self.registry: Dict[str, Dict] = {}
        self.extracted_modules: Set[str] = set()
        
    def log(self, msg: str, level: str = 'info') -> None:
        """Simple logging."""
        prefix = {'info': '[INFO]', 'warn': '[WARN]', 'error': '[ERROR]'}.get(level, '[INFO]')
        print(f"{prefix} {msg}")
        
    def get_startup_manifest(self) -> Optional[Dict]:
        """Get startup manifest to verify module names and get canonical registry."""
        try:
            url = f"{self.base_url}/w/load.php"
            params = {
                'modules': 'startup',
                'only': 'scripts', 
                'skin': 'vector',
                'lang': 'en',
                'debug': 'true'
            }
            
            resp = self.session.get(url, params=params, timeout=30)
            resp.raise_for_status()
            
            content = resp.text
            
            # Extract the module registry from the startup script
            # Look for mw.loader.implement calls containing module registry
            registry_match = re.search(r'mw\.loader\.implement\s*\(\s*"startup"[^}]+registry\s*:\s*(\{[^}]+\})', content)
            if registry_match:
                # This would need proper JSON parsing in a real implementation
                self.log(f"Found startup manifest with registry data")
                return {"registry": registry_match.group(1)}
            
            return None
            
        except Exception as e:
            self.log(f"Failed to get startup manifest: {e}", 'warn')
            return None
    
    def verify_module_name(self, module: str) -> str:
        """Verify and correct module name case using startup manifest."""
        # Expert 1's fix: Check for common case variations
        variations = [
            module,
            module.lower(),
            module.replace('Tabber', 'tabber'),  # Specific fix for ext.Tabber -> ext.tabber
            module.replace('.Tabber', '.tabberNeue')  # Alternative Tabber module name
        ]
        
        for variant in variations:
            if variant != module:
                self.log(f"Trying case variant: {variant}")
            
            # Quick test if this variant exists
            if self._test_module_exists(variant):
                if variant != module:
                    self.log(f"Found correct case: {module} -> {variant}")
                return variant
                
        return module  # Return original if no variant works
    
    def _test_module_exists(self, module: str) -> bool:
        """Quick test if a module name exists without full extraction."""
        try:
            url = f"{self.base_url}/w/load.php"
            params = {
                'modules': module,
                'only': 'scripts',
                'skin': 'vector',
                'lang': 'en',
                'debug': 'false'
            }
            
            resp = self.session.get(url, params=params, timeout=10)
            # If we get anything other than 404, the module probably exists
            return resp.status_code != 404
            
        except Exception:
            return False

    def get_module_info(self, module: str) -> Optional[Dict]:
        """Get module metadata from MediaWiki API."""
        try:
            # Verify module name case first
            verified_module = self.verify_module_name(module)
            return {
                "module": verified_module, 
                "original_name": module,
                "dependencies": [], 
                "type": self._categorize_module(verified_module)
            }
        except Exception as e:
            self.log(f"Failed to get info for {module}: {e}", 'warn')
            return None
            
    def _categorize_module(self, module: str) -> str:
        """Categorize module by type (same logic as scanner)."""
        if module.startswith('ext.gadget.'):
            return 'gadget'
        elif module.startswith('ext.'):
            return 'extension'
        elif module.startswith('mediawiki.'):
            return 'core'
        elif 'chart' in module.lower() or 'graph' in module.lower():
            return 'visualization'
        elif 'ui' in module.lower() or 'oojs' in module.lower():
            return 'interface'
        else:
            return 'other'
            
    def extract_raw_source(self, module: str) -> Optional[str]:
        """Extract raw JavaScript source for a module."""
        methods = [
            self._extract_via_network_trace,  # Use actual network trace URLs first
            self._extract_via_revisions_api,  # Expert 1's recommended method
            self._extract_via_raw_action,
            self._extract_via_resourceloader,
            self._extract_via_extension_check,
        ]
        
        for method in methods:
            try:
                source = method(module)
                if source and len(source.strip()) > 10:  # Basic validation
                    self.log(f"Extracted {len(source)} chars from {module} via {method.__name__}")
                    return source
            except Exception as e:
                self.log(f"Method {method.__name__} failed for {module}: {e}", 'warn')
                
        self.log(f"All extraction methods failed for {module}", 'error')
        return None
    
    def _extract_via_network_trace(self, module: str) -> Optional[str]:
        """Extract via network trace URLs - load from actual ResourceLoader bundles."""
        # Load network trace data if available
        trace_file = self.output_dir / "network_trace.json"
        if not trace_file.exists():
            return None
            
        try:
            with open(trace_file) as f:
                trace_data = json.load(f)
        except Exception:
            return None
            
        module_hosting = trace_data.get('module_hosting', {})
        
        # Find bundle URL that contains our target module
        target_bundle_url = None
        for bundle_modules, info in module_hosting.items():
            # Check if our target module is in this bundle (handles comma-separated lists)
            modules_in_bundle = [m.strip() for m in bundle_modules.split(',')]
            
            # Direct match
            if module in modules_in_bundle:
                sample_urls = info.get('sample_urls', [])
                if sample_urls:
                    target_bundle_url = sample_urls[0]  # Use first URL
                    break
                    
            # For gadgets, also try matching without the ext.gadget. prefix
            if module.startswith('ext.gadget.'):
                gadget_short_name = module.replace('ext.gadget.', '')
                if gadget_short_name in modules_in_bundle:
                    sample_urls = info.get('sample_urls', [])
                    if sample_urls:
                        target_bundle_url = sample_urls[0]  # Use first URL
                        break
                    
        if not target_bundle_url:
            return None
            
        self.log(f"Found {module} in bundle: {target_bundle_url}")
        
        # Download the bundle
        try:
            resp = self.session.get(target_bundle_url, timeout=30)
            resp.raise_for_status()
            bundle_content = resp.text
        except Exception as e:
            self.log(f"Failed to download bundle: {e}", 'warn')
            return None
            
        # Extract the specific module from the bundle
        return self._extract_module_from_bundle(bundle_content, module)
    
    def _extract_module_from_bundle(self, bundle_content: str, target_module: str) -> Optional[str]:
        """Extract a specific module from a ResourceLoader bundle."""
        # ResourceLoader bundles have format: mw.loader.impl(function(){return["module@hash",function($,jQuery,require,module){...}];});
        
        # For gadgets, try both full name and short name
        search_names = [target_module]
        if target_module.startswith('ext.gadget.'):
            short_name = target_module.replace('ext.gadget.', '')
            search_names.append(f'ext.gadget.{short_name}')  # Full form
            search_names.insert(0, short_name)  # Try short name first
            
        for search_module in search_names:
            # Find the specific module implementation
            module_pattern = rf'"({re.escape(search_module)}@[^"]*)",\s*function\([^)]*\)\s*\{{(.*?)\}}\s*(?:,\s*\{{[^}}]*\}})?(?:\s*,\s*\{{[^}}]*\}})?\s*\]\s*;?\s*\}}\)'
            
            match = re.search(module_pattern, bundle_content, re.DOTALL)
            if match:
                module_hash = match.group(1)
                module_code = match.group(2)
                self.log(f"Extracted {target_module} as {search_module} ({len(module_code)} chars) from bundle")
                return module_code
                
            # Alternative pattern - sometimes modules are simpler
            simple_pattern = rf'"({re.escape(search_module)}@[^"]*)",\s*function\([^)]*\)\s*\{{([^}}]+)\}}'
            match = re.search(simple_pattern, bundle_content, re.DOTALL)
            if match:
                module_hash = match.group(1)
                module_code = match.group(2)
                self.log(f"Extracted {target_module} as {search_module} (simple format, {len(module_code)} chars) from bundle")
                return module_code
                
            # Try searching for just the module name without version hash
            loose_pattern = rf'"({re.escape(search_module)}[^"]*)",\s*function\([^)]*\)\s*\{{(.*?)\}}'
            match = re.search(loose_pattern, bundle_content, re.DOTALL)
            if match:
                module_name = match.group(1)
                module_code = match.group(2)
                self.log(f"Extracted {target_module} as {search_module} (loose match as {module_name}, {len(module_code)} chars)")
                return module_code
            
        self.log(f"Could not find {target_module} in bundle", 'warn')
        return None
        
    def _extract_via_raw_action(self, module: str) -> Optional[str]:
        """Extract via MediaWiki raw action (for gadgets)."""
        if not module.startswith('ext.gadget.'):
            return None
            
        # Convert module name to MediaWiki page title
        gadget_name = module.replace('ext.gadget.', '')
        page_title = f"MediaWiki:Gadget-{gadget_name}.js"
        
        url = f"{self.base_url}/w/index.php"
        params = {
            'title': page_title,
            'action': 'raw',
            'ctype': 'text/javascript'
        }
        
        resp = self.session.get(url, params=params, timeout=30)
        resp.raise_for_status()
        
        # Check if we got actual content (not a redirect or error page)
        content = resp.text.strip()
        if not content or content.startswith('<!DOCTYPE') or 'wgRedirectedFrom' in content:
            return None
            
        return content
    
    def _extract_via_revisions_api(self, module: str) -> Optional[str]:
        """Extract via MediaWiki revisions API (Expert 1's recommended method)."""
        if not module.startswith('ext.gadget.'):
            return None
            
        # Convert module name to MediaWiki page title
        gadget_name = module.replace('ext.gadget.', '')
        page_title = f"MediaWiki:Gadget-{gadget_name}.js"
        
        url = f"{self.base_url}/w/api.php"
        params = {
            'action': 'query',
            'format': 'json',
            'prop': 'revisions',
            'rvprop': 'content',
            'titles': page_title,
            'formatversion': '2'  # Use newer API format
        }
        
        resp = self.session.get(url, params=params, timeout=30)
        resp.raise_for_status()
        
        data = resp.json()
        
        # Check if we got valid response
        if 'query' not in data or 'pages' not in data['query']:
            return None
            
        pages = data['query']['pages']
        if not pages or len(pages) == 0:
            return None
            
        page = pages[0]
        
        # Check if page exists and has content
        if 'missing' in page:
            self.log(f"Gadget page {page_title} does not exist")
            return None
            
        if 'revisions' not in page or not page['revisions']:
            return None
            
        revision = page['revisions'][0]
        if 'content' not in revision:
            return None
            
        content = revision['content'].strip()
        
        # Basic validation
        if not content or len(content) < 10:
            return None
            
        self.log(f"Successfully extracted gadget via revisions API: {page_title}")
        return content
        
    def _extract_via_resourceloader(self, module: str) -> Optional[str]:
        """Extract via ResourceLoader load.php endpoint."""
        url = f"{self.base_url}/w/load.php"
        params = {
            'modules': module,
            'only': 'scripts',
            'debug': 'true',  # Expert 1's fix: use 'true' not '1'
            'lang': 'en',     # Expert 1's fix: always specify language
            'skin': 'vector'  # Expert 1's fix: always specify skin
        }
        
        resp = self.session.get(url, params=params, timeout=30)
        resp.raise_for_status()
        
        content = resp.text.strip()
        
        # Check if we got actual content
        if not content or len(content) < 10:
            return None
            
        # ResourceLoader may wrap modules in mw.loader.implement calls
        # Try to extract the actual JavaScript content
        if 'mw.loader.implement' in content:
            # Extract content from mw.loader.implement calls
            import_matches = re.findall(r'mw\.loader\.implement\([^,]+,[^,]*,\s*function\s*\([^)]*\)\s*\{(.*?)\}\s*\)', content, re.DOTALL)
            if import_matches:
                # Combine all function bodies
                content = '\n'.join(import_matches)
            else:
                # Try alternative pattern with string content
                string_matches = re.findall(r'mw\.loader\.implement\([^,]+,\s*"([^"]*)"', content)
                if string_matches:
                    content = '\n'.join(string_matches)
        
        # Skip if it's empty or just module registration
        if len(content.strip()) < 50:
            return None
            
        return content
        
    def _extract_via_extension_check(self, module: str) -> Optional[str]:
        """Check if module exists and try alternative extraction for extensions."""
        # For now, let's try a simple check to see if the module loads at all
        url = f"{self.base_url}/w/load.php"
        params = {
            'modules': module,
            'only': 'scripts',
            'debug': 'false',  # Expert 1's fix: use 'false' not '0'
            'lang': 'en',      # Expert 1's fix: always specify language
            'skin': 'vector'   # Expert 1's fix: always specify skin
        }
        
        resp = self.session.get(url, params=params, timeout=30)
        if resp.status_code == 404:
            # Module doesn't exist
            return None
            
        content = resp.text.strip()
        
        # If we get empty content or just comments, the module might not have JavaScript
        if not content or len(content) < 20 or content.startswith('/*') and content.count('\n') < 3:
            self.log(f"Module {module} exists but has no substantial JavaScript content", 'warn')
            return None
            
        # For some modules like ext.Tabber, the content might be very minimal
        # Let's accept it if we get anything meaningful
        if 'function' in content or '{' in content or content.count(';') > 2:
            return content
            
        return None
        
    def analyze_dependencies(self, source: str, module: str) -> List[str]:
        """Analyze JavaScript source to identify MediaWiki dependencies."""
        dependencies = []
        
        # Check for MediaWiki API usage
        for pattern in MW_API_PATTERNS:
            if re.search(pattern, source):
                dependencies.append(pattern.replace('\\', '').replace('.', '_'))
                
        # Check for explicit module dependencies (mw.loader.using calls)
        using_matches = re.findall(r"mw\.loader\.using\(\s*[\'\"]([^\'\"]+)[\'\"]", source)
        dependencies.extend(using_matches)
        
        # Check for jQuery usage
        if re.search(r'\$\(', source) or 'jQuery' in source:
            dependencies.append('jquery')
            
        self.log(f"Found dependencies for {module}: {dependencies}")
        return list(set(dependencies))  # Remove duplicates
        
    def create_compatibility_shim(self, dependencies: List[str], module: str) -> str:
        """Create MediaWiki API compatibility shim for a module."""
        shims = []
        
        # Basic MediaWiki object setup
        shims.append("""
// MediaWiki API Compatibility Layer
if (typeof window.mw === 'undefined') {
    window.mw = {
        config: {
            get: function(key, fallback) {
                const configs = {
                    'wgPageName': document.title || 'Unknown_Page',
                    'wgNamespaceNumber': 0,
                    'wgTitle': document.title || 'Unknown Page',
                    'wgUserGroups': ['*'],
                    'wgUserName': null
                };
                return configs[key] !== undefined ? configs[key] : fallback;
            }
        },
        loader: {
            using: function(modules, callback) {
                // Simple implementation - assume modules are already loaded
                if (typeof callback === 'function') {
                    setTimeout(callback, 0);
                }
                return Promise.resolve();
            },
            load: function(modules) {
                console.log('[MW-COMPAT] Module load requested:', modules);
            }
        },
        util: {
            getUrl: function(title, params) {
                // Basic URL construction for wiki links
                return '#' + encodeURIComponent(title.replace(/ /g, '_'));
            },
            addCSS: function(css) {
                const style = document.createElement('style');
                style.textContent = css;
                document.head.appendChild(style);
            }
        },
        message: function(key) {
            // Basic message implementation - return the key
            return {
                text: function() { return key; },
                parse: function() { return key; }
            };
        },
        cookie: {
            get: function(name, defaultValue) {
                const value = document.cookie.match('(^|;)\\\\s*' + name + '\\\\s*=\\\\s*([^;]+)');
                return value ? decodeURIComponent(value[2]) : defaultValue;
            },
            set: function(name, value, expires) {
                document.cookie = name + '=' + encodeURIComponent(value) + 
                    (expires ? '; expires=' + expires : '') + '; path=/';
            }
        },
        user: {
            getName: function() { return null; },
            isAnon: function() { return true; },
            options: {
                get: function(key, fallback) { return fallback; }
            }
        }
    };
}

// jQuery compatibility (if not already loaded)
if (typeof window.$ === 'undefined' && typeof window.jQuery !== 'undefined') {
    window.$ = window.jQuery;
}
""")
        
        # Add specific shims based on dependencies
        if 'jquery' in dependencies and 'jQuery' not in str(dependencies):
            shims.append("""
// Basic jQuery-like functionality for simple cases
if (typeof window.$ === 'undefined') {
    window.$ = function(selector) {
        if (typeof selector === 'function') {
            // Document ready
            if (document.readyState === 'complete' || document.readyState === 'interactive') {
                setTimeout(selector, 0);
            } else {
                document.addEventListener('DOMContentLoaded', selector);
            }
            return;
        }
        // Basic element selection
        return {
            ready: function(fn) { $(fn); },
            length: 0,
            each: function() { return this; }
        };
    };
    window.jQuery = window.$;
}
""")
        
        return '\n'.join(shims)
        
    def adapt_module_source(self, source: str, module: str) -> str:
        """Adapt module source for standalone use."""
        adaptations = []
        
        # Wrap in IIFE to prevent global pollution
        adaptations.append(f"// Adapted module: {module}")
        adaptations.append("(function() {")
        adaptations.append("'use strict';")
        adaptations.append("")
        
        # Add the module source
        adaptations.append(source)
        adaptations.append("")
        adaptations.append("})();")
        
        return '\n'.join(adaptations)
        
    def adapt_for_resourceloader(self, source: str, module: str, dependencies: List[str]) -> str:
        """Adapt module source to work with real MediaWiki ResourceLoader."""
        adaptations = []
        
        # Add header comment
        adaptations.append(f"/**")
        adaptations.append(f" * MediaWiki ResourceLoader Module: {module}")
        adaptations.append(f" * ")
        adaptations.append(f" * Extracted from OSRS Wiki server to work with real MediaWiki infrastructure.")
        adaptations.append(f" * Dependencies: {', '.join(dependencies) if dependencies else 'none'}")
        adaptations.append(f" */")
        adaptations.append("")
        
        # In ResourceLoader mode, preserve the original source structure
        # The module should be loaded via mw.loader.implement() by the ResourceLoader system
        adaptations.append("// Original module source (to be loaded via ResourceLoader)")
        adaptations.append(source)
        
        return '\n'.join(adaptations)
        
    def extract_module(self, module: str, include_deps: bool = True) -> bool:
        """Extract a single module with optional dependency resolution."""
        if module in self.extracted_modules:
            self.log(f"Module {module} already extracted")
            return True
            
        # Expert 1's fix: Verify and correct module name case
        verified_module = self.verify_module_name(module)
        if verified_module != module:
            self.log(f"Using corrected module name: {module} -> {verified_module}")
        
        self.log(f"Extracting module: {verified_module} (ResourceLoader mode: {self.resourceloader_mode})")
        
        # Get raw source using verified name
        source = self.extract_raw_source(verified_module)
        if not source:
            self.log(f"Failed to extract source for {verified_module}", 'error')
            return False
            
        # Analyze dependencies
        dependencies = self.analyze_dependencies(source, verified_module)
        
        if self.resourceloader_mode:
            # ResourceLoader mode: preserve original module structure for ResourceLoader
            final_source = self.adapt_for_resourceloader(source, verified_module, dependencies)
        else:
            # Standalone mode: create compatibility shim + adapted source
            shim = self.create_compatibility_shim(dependencies, verified_module)
            adapted_source = self.adapt_module_source(source, verified_module)
            final_source = shim + "\n\n" + adapted_source
        
        # Save to file using verified name
        safe_name = verified_module.replace('.', '_').replace(':', '_')
        output_file = self.output_dir / f"{safe_name}.js"
        output_file.write_text(final_source, encoding='utf-8')
        
        # Update registry with both original and verified names
        self.registry[verified_module] = {
            'name': verified_module,
            'original_name': module if module != verified_module else verified_module,
            'type': self._categorize_module(verified_module),
            'dependencies': dependencies,
            'source_length': len(source),
            'output_file': str(output_file),
            'extracted_at': time.time()
        }
        
        self.extracted_modules.add(verified_module)
        self.log(f"Successfully extracted {verified_module} -> {output_file}")
        
        # TODO: Handle dependency extraction in future iteration
        return True
        
    def get_available_gadgets(self) -> List[str]:
        """Get list of available gadgets from MediaWiki:Gadgets-definition."""
        try:
            url = f"{self.base_url}/w/api.php"
            params = {
                'action': 'query',
                'format': 'json',
                'prop': 'revisions',
                'rvprop': 'content',
                'titles': 'MediaWiki:Gadgets-definition',
                'formatversion': '2'
            }
            
            resp = self.session.get(url, params=params, timeout=30)
            resp.raise_for_status()
            
            data = resp.json()
            
            if 'query' not in data or 'pages' not in data['query']:
                return []
                
            pages = data['query']['pages']
            if not pages or len(pages) == 0:
                return []
                
            page = pages[0]
            if 'missing' in page or 'revisions' not in page:
                return []
                
            content = page['revisions'][0].get('content', '')
            
            # Parse gadget definitions
            gadgets = []
            for line in content.split('\n'):
                line = line.strip()
                if line.startswith('*') and '|' in line:
                    # Extract gadget name (first part before [options])
                    gadget_part = line[1:].strip()  # Remove leading *
                    if '[' in gadget_part:
                        gadget_name = gadget_part.split('[')[0].strip()
                    else:
                        gadget_name = gadget_part.split('|')[0].strip()
                    
                    if gadget_name:
                        full_module_name = f"ext.gadget.{gadget_name}"
                        gadgets.append(full_module_name)
                        
            self.log(f"Found {len(gadgets)} available gadgets")
            return gadgets
            
        except Exception as e:
            self.log(f"Failed to get available gadgets: {e}", 'warn')
            return []

    def extract_from_scan_report(self, report_path: str, priority_only: bool = False) -> List[str]:
        """Extract modules discovered by widget scanner."""
        report_file = Path(report_path)
        if not report_file.exists():
            self.log(f"Scan report not found: {report_path}", 'error')
            return []
            
        with open(report_file) as f:
            report = json.load(f)
            
        # Get JavaScript modules from report (Expert 1's fix - only JS modules)
        modules = []
        if 'javascript_modules' in report:
            for module_info in report['javascript_modules']:
                module_name = module_info.get('module', '')
                if module_name and (not priority_only or module_name in PRIORITY_MODULES):
                    modules.append(module_name)
                    
        # Fallback to original modules list if new format not available
        elif 'modules' in report:
            for module_info in report['modules']:
                module_name = module_info.get('module', '')
                if module_name and (not priority_only or module_name in PRIORITY_MODULES):
                    modules.append(module_name)
                    
        # Include suggested modules inferred from widget markers
        if 'suggested_modules' in report:
            for module_name in report['suggested_modules']:
                if module_name and (not priority_only or module_name in PRIORITY_MODULES):
                    if module_name not in modules:
                        modules.append(module_name)

        # Also check categorized modules
        if 'modules_by_category' in report:
            for category, module_list in report['modules_by_category'].items():
                for module_info in module_list:
                    module_name = module_info.get('module', '')
                    if module_name and (not priority_only or module_name in PRIORITY_MODULES):
                        if module_name not in modules:
                            modules.append(module_name)
                            
        return modules
        
    def save_registry(self) -> None:
        """Save extraction registry to JSON."""
        registry_file = self.output_dir / "module_registry.json"
        with open(registry_file, 'w') as f:
            json.dump({
                'extracted_at': time.time(),
                'total_modules': len(self.registry),
                'modules': self.registry
            }, f, indent=2)
        self.log(f"Saved registry to {registry_file}")


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Extract MediaWiki ResourceLoader modules for standalone use")
    ap.add_argument("--base", default=DEFAULT_BASE, help="Wiki base URL")
    ap.add_argument("--out", default=DEFAULT_OUT, help="Output directory")
    ap.add_argument("--modules", nargs="*", help="Specific modules to extract")
    ap.add_argument("--auto-from-scan", help="Auto-extract from widget scan report JSON")
    ap.add_argument("--priority-only", action="store_true", help="Only extract priority modules")
    ap.add_argument("--list-gadgets", action="store_true", help="List available gadgets and exit")
    ap.add_argument("--resourceloader-mode", action="store_true", help="Extract modules for real MediaWiki ResourceLoader (no compatibility layer)")
    args = ap.parse_args(argv)
    
    extractor = ModuleExtractor(args.base, args.out, args.resourceloader_mode)
    
    # Handle gadget listing
    if args.list_gadgets:
        extractor.log("Fetching available gadgets from MediaWiki:Gadgets-definition...")
        gadgets = extractor.get_available_gadgets()
        
        if gadgets:
            extractor.log(f"Found {len(gadgets)} available gadgets:")
            for gadget in sorted(gadgets):
                print(f"  {gadget}")
        else:
            extractor.log("No gadgets found or failed to fetch gadget list")
            
        return 0
    
    modules_to_extract = []
    
    if args.modules:
        modules_to_extract.extend(args.modules)
    elif args.auto_from_scan:
        modules_to_extract.extend(extractor.extract_from_scan_report(args.auto_from_scan, args.priority_only))
    elif args.priority_only:
        modules_to_extract.extend(PRIORITY_MODULES)
    else:
        extractor.log("No modules specified. Use --modules, --auto-from-scan, or --priority-only", 'error')
        return 1
        
    if not modules_to_extract:
        extractor.log("No modules to extract", 'warn')
        return 0
        
    extractor.log(f"Extracting {len(modules_to_extract)} modules: {modules_to_extract}")
    
    success_count = 0
    for module in modules_to_extract:
        if extractor.extract_module(module):
            success_count += 1
            
    extractor.save_registry()
    
    extractor.log(f"Extraction complete: {success_count}/{len(modules_to_extract)} modules successful")
    return 0 if success_count > 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
