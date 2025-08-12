#!/usr/bin/env python3
"""
MediaWiki Module Inspector using Playwright

This tool uses Playwright to:
1. Navigate to a MediaWiki page
2. Capture all network requests to load.php
3. Extract the complete module manifest from startup
4. Analyze the actual loading sequence
5. Output structured data about module dependencies

Unlike the MCP wrapper, this uses Playwright directly to avoid token limits.
"""

import asyncio
import json
import re
import sys
from pathlib import Path
from playwright.async_api import async_playwright
from typing import Dict, List, Set, Any, Optional
from urllib.parse import parse_qs, urlparse

class MediaWikiModuleInspector:
    def __init__(self, base_url: str = "https://oldschool.runescape.wiki"):
        self.base_url = base_url
        self.network_requests = []
        self.module_manifest = None
        self.page_modules = []
        self.loaded_modules = set()
        
    async def inspect_page(self, page_title: str) -> Dict[str, Any]:
        """
        Inspect a MediaWiki page and capture all module loading information.
        """
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            page = await browser.new_page()
            
            # Set up network request capture
            page.on("request", self._capture_request)
            page.on("response", self._capture_response)
            
            try:
                # Navigate to the page
                url = f"{self.base_url}/w/{page_title}"
                print(f"[INFO] Navigating to: {url}")
                await page.goto(url, wait_until="networkidle")
                
                # Wait a bit more for any async module loading
                await page.wait_for_timeout(3000)
                
                # Extract module information from the page
                await self._extract_module_info(page)
                
                # Analyze the results
                analysis = self._analyze_results()
                
                print(f"[SUCCESS] Captured {len(self.network_requests)} network requests")
                print(f"[SUCCESS] Found {len(self.loaded_modules)} loaded modules")
                
                return analysis
                
            finally:
                await browser.close()
    
    def _capture_request(self, request):
        """Capture network requests, focusing on MediaWiki load.php calls."""
        if "/load.php" in request.url:
            parsed_url = urlparse(request.url)
            query_params = parse_qs(parsed_url.query)
            
            self.network_requests.append({
                "url": request.url,
                "method": request.method,
                "modules": query_params.get("modules", []),
                "debug": query_params.get("debug", []),
                "only": query_params.get("only", []),
                "skin": query_params.get("skin", []),
                "lang": query_params.get("lang", [])
            })
            
            print(f"[NETWORK] {request.method} {parsed_url.path}?modules={query_params.get('modules', [''])[0]}")
    
    def _capture_response(self, response):
        """Capture response data for load.php requests."""
        # We'll handle response processing separately to avoid large data in memory
        pass
    
    async def _extract_module_info(self, page):
        """Extract module information from the loaded page."""
        
        # Get the module manifest from mw.loader
        module_info = await page.evaluate("""
            () => {
                const result = {
                    hasMediaWiki: typeof window.mw !== 'undefined',
                    hasLoader: typeof window.mw?.loader !== 'undefined',
                    registeredModules: [],
                    moduleStates: {},
                    pageModules: window.RLPAGEMODULES || [],
                    rlConf: window.RLCONF || {},
                    rlState: window.RLSTATE || {}
                };
                
                if (window.mw && window.mw.loader) {
                    // Get all registered modules
                    try {
                        const registry = window.mw.loader.moduleRegistry || {};
                        result.registeredModules = Object.keys(registry);
                        
                        // Get module states
                        for (const moduleName of result.registeredModules) {
                            result.moduleStates[moduleName] = window.mw.loader.getState(moduleName);
                        }
                    } catch (e) {
                        result.error = e.toString();
                    }
                }
                
                // Check for chart elements and other gadget elements
                result.pageElements = {
                    geCharts: document.querySelectorAll('.GEdatachart').length,
                    collapsibleTables: document.querySelectorAll('.mw-collapsible').length,
                    sortableTables: document.querySelectorAll('table.sortable').length,
                    infoboxes: document.querySelectorAll('.infobox').length
                };
                
                return result;
            }
        """)
        
        self.module_manifest = module_info
        self.page_modules = module_info.get("pageModules", [])
        self.loaded_modules = set(module_info.get("registeredModules", []))
        
        print(f"[MODULE INFO] Found {len(self.loaded_modules)} registered modules")
        print(f"[MODULE INFO] Page modules: {len(self.page_modules)}")
        print(f"[MODULE INFO] Page elements: {module_info.get('pageElements', {})}")
    
    def _analyze_results(self) -> Dict[str, Any]:
        """Analyze captured data and provide insights."""
        
        # Analyze network requests
        startup_requests = [r for r in self.network_requests if "startup" in str(r.get("modules", []))]
        module_requests = [r for r in self.network_requests if r not in startup_requests]
        
        # Find all unique modules requested
        all_requested_modules = set()
        for request in self.network_requests:
            modules = request.get("modules", [])
            if modules:
                # Handle pipe-separated module lists
                for module_list in modules:
                    all_requested_modules.update(module_list.split("|"))
        
        # Identify missing dependencies
        missing_deps = self._find_missing_dependencies()
        
        return {
            "summary": {
                "page_modules": len(self.page_modules),
                "registered_modules": len(self.loaded_modules),
                "network_requests": len(self.network_requests),
                "startup_requests": len(startup_requests),
                "module_requests": len(module_requests),
                "unique_requested_modules": len(all_requested_modules),
                "missing_dependencies": len(missing_deps)
            },
            "page_modules": self.page_modules,
            "registered_modules": sorted(list(self.loaded_modules)),
            "requested_modules": sorted(list(all_requested_modules)),
            "missing_dependencies": missing_deps,
            "network_requests": self.network_requests,
            "module_manifest": self.module_manifest,
            "critical_modules": {
                "has_jquery": "jquery" in self.loaded_modules,
                "has_mediawiki_base": "mediawiki.base" in self.loaded_modules,
                "has_oojs": "oojs" in self.loaded_modules,
                "has_oojs_ui": "oojs-ui" in self.loaded_modules or "oojs-ui-core" in self.loaded_modules,
                "has_ge_charts": any("GECharts" in m for m in self.loaded_modules)
            }
        }
    
    def _find_missing_dependencies(self) -> List[str]:
        """Identify modules that are needed but not loaded."""
        # This is a simplified check - we'll improve this with the dependency resolver
        required_infrastructure = {
            "jquery", "mediawiki.base", "oojs", "oojs-ui-core", 
            "oojs-ui-widgets", "oojs-ui-windows"
        }
        
        missing = []
        for module in required_infrastructure:
            if module not in self.loaded_modules:
                missing.append(module)
                
        return missing
    
    def save_results(self, results: Dict[str, Any], output_file: Path):
        """Save analysis results to a JSON file."""
        with open(output_file, 'w') as f:
            json.dump(results, f, indent=2, default=str)
        print(f"[SUCCESS] Results saved to: {output_file}")


async def main():
    """Main function."""
    if len(sys.argv) < 2:
        print("Usage: python playwright_inspector.py <page_title> [output_file]")
        print("Example: python playwright_inspector.py Logs logs_analysis.json")
        sys.exit(1)
    
    page_title = sys.argv[1]
    output_file = Path(sys.argv[2] if len(sys.argv) > 2 else f"{page_title.lower()}_analysis.json")
    
    inspector = MediaWikiModuleInspector()
    
    try:
        results = await inspector.inspect_page(page_title)
        inspector.save_results(results, output_file)
        
        # Print summary
        print("\n" + "="*60)
        print("INSPECTION SUMMARY")
        print("="*60)
        for key, value in results["summary"].items():
            print(f"{key.replace('_', ' ').title()}: {value}")
            
        print(f"\nCritical modules status:")
        for module, status in results["critical_modules"].items():
            status_icon = "✅" if status else "❌"
            print(f"  {status_icon} {module.replace('_', ' ').title()}")
            
        if results["missing_dependencies"]:
            print(f"\n⚠️  Missing dependencies: {', '.join(results['missing_dependencies'])}")
            
    except Exception as e:
        print(f"[ERROR] Inspection failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())