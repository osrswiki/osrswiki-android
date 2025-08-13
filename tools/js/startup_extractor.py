#!/usr/bin/env python3
"""
MediaWiki Startup Module Extractor using Browser Execution

This module extracts the MediaWiki module registry by executing startup.js
in a real browser environment, eliminating the need for fragile regex parsing.

The approach:
1. Load startup.js in a minimal HTML page
2. Intercept the mw.loader.register call
3. Extract the complete module registry with resolved dependencies
4. Return structured JSON data

This is robust, accurate, and future-proof.
"""

import asyncio
import json
import sys
import tempfile
import requests
from pathlib import Path
from playwright.async_api import async_playwright
from typing import Dict, List, Set, Any, Optional
from urllib.parse import urlparse

class StartupExtractor:
    def __init__(self, base_url: str = "https://oldschool.runescape.wiki"):
        self.base_url = base_url
        
    async def extract_from_url(self, startup_url: str = None) -> Dict[str, Any]:
        """Extract module registry by downloading and executing startup.js."""
        
        if not startup_url:
            startup_url = f"{self.base_url}/load.php"
            
        # Download startup.js
        print(f"[INFO] Downloading startup module from {startup_url}")
        startup_content = self._download_startup(startup_url)
        
        # Extract using browser execution
        return await self._extract_from_content(startup_content)
    
    async def extract_from_file(self, startup_file: Path) -> Dict[str, Any]:
        """Extract module registry from a local startup.js file."""
        
        print(f"[INFO] Loading startup file: {startup_file}")
        with open(startup_file, 'r', encoding='utf-8') as f:
            startup_content = f.read()
            
        return await self._extract_from_content(startup_content)
    
    async def extract_from_live_page(self, page_url: str) -> Dict[str, Any]:
        """Extract module registry directly from a loaded MediaWiki page."""
        
        print(f"[INFO] Extracting registry from live page: {page_url}")
        
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            page = await browser.new_page()
            
            try:
                # Navigate to the page and wait for MediaWiki to load
                await page.goto(page_url, wait_until="networkidle")
                
                # Extract the complete module registry
                registry_data = await page.evaluate("""
                    () => {
                        if (!window.mw || !window.mw.loader) {
                            return { error: "MediaWiki not loaded" };
                        }
                        
                        const registry = {};
                        const moduleRegistry = window.mw.loader.moduleRegistry || {};
                        
                        // Extract complete module information
                        for (const [name, module] of Object.entries(moduleRegistry)) {
                            registry[name] = {
                                dependencies: module.dependencies || [],
                                group: module.group || null,
                                state: module.state || 'registered',
                                version: module.version || '',
                                script: module.script ? 'present' : 'none'
                            };
                        }
                        
                        // Build dependency graph
                        const dependencyGraph = {};
                        const reverseDependencyGraph = {};
                        
                        for (const [name, info] of Object.entries(registry)) {
                            dependencyGraph[name] = info.dependencies;
                            
                            // Build reverse dependencies
                            for (const dep of info.dependencies) {
                                if (!reverseDependencyGraph[dep]) {
                                    reverseDependencyGraph[dep] = [];
                                }
                                reverseDependencyGraph[dep].push(name);
                            }
                        }
                        
                        return {
                            registry: registry,
                            dependency_graph: dependencyGraph,
                            reverse_dependency_graph: reverseDependencyGraph,
                            module_count: Object.keys(registry).length,
                            extraction_method: 'live_page'
                        };
                    }
                """)
                
                print(f"[SUCCESS] Extracted {registry_data.get('module_count', 0)} modules from live page")
                return registry_data
                
            finally:
                await browser.close()
    
    def _download_startup(self, startup_url: str) -> str:
        """Download startup.js content."""
        
        params = {
            'modules': 'startup',
            'only': 'scripts',
            'skin': 'vector', 
            'debug': 'true',
            'lang': 'en-gb'
        }
        
        response = requests.get(startup_url, params=params, timeout=30)
        response.raise_for_status()
        
        content = response.text
        print(f"[SUCCESS] Downloaded startup module ({len(content)} characters)")
        return content
    
    async def _extract_from_content(self, startup_content: str) -> Dict[str, Any]:
        """Extract module registry by executing startup.js in browser."""
        
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            page = await browser.new_page()
            
            try:
                # Create HTML page that executes startup.js and captures the register call
                html_content = self._create_extraction_page(startup_content)
                
                # Load the page
                await page.set_content(html_content)
                
                # Wait a moment for execution
                await page.wait_for_timeout(1000)
                
                # Debug: Check what's available in the page
                debug_info = await page.evaluate("""
                    () => {
                        return {
                            hasMw: typeof window.mw !== 'undefined',
                            hasLoader: typeof window.mw?.loader !== 'undefined',
                            hasCaptured: typeof window.__capturedModules !== 'undefined',
                            capturedModules: window.__capturedModules,
                            registerCallCount: window.__registerCallCount || 0,
                            executionError: window.__executionError || null,
                            consoleLog: window.__consoleOutput || []
                        };
                    }
                """)
                
                print(f"[DEBUG] Page state: {debug_info}")
                
                # Extract the captured module data
                registry_data = await page.evaluate("""
                    () => {
                        // Try registry data first (direct extraction from moduleRegistry)
                        if (window.__registryData && Object.keys(window.__registryData).length > 0) {
                            const registry = window.__registryData;
                            const dependencyGraph = {};
                            const reverseDependencyGraph = {};
                            
                            // Build dependency graphs
                            for (const [name, info] of Object.entries(registry)) {
                                dependencyGraph[name] = info.dependencies || [];
                                
                                // Build reverse dependencies
                                for (const dep of info.dependencies || []) {
                                    if (!reverseDependencyGraph[dep]) {
                                        reverseDependencyGraph[dep] = [];
                                    }
                                    reverseDependencyGraph[dep].push(name);
                                }
                            }
                            
                            // Count dependency relationships
                            const totalDeps = Object.values(dependencyGraph)
                                .reduce((sum, deps) => sum + deps.length, 0);
                            
                            return {
                                registry: registry,
                                dependency_graph: dependencyGraph,
                                reverse_dependency_graph: reverseDependencyGraph,
                                module_count: Object.keys(registry).length,
                                total_dependencies: totalDeps,
                                extraction_method: 'moduleRegistry_direct'
                            };
                        }
                        
                        // Fall back to captured modules (if interception worked)
                        if (window.__capturedModules && window.__capturedModules.length > 0) {
                            const rawModules = window.__capturedModules;
                            const registry = {};
                            const dependencyGraph = {};
                            const reverseDependencyGraph = {};
                            
                            // Process each module
                            rawModules.forEach((module, index) => {
                                const [name, version, deps, group] = module;
                                
                                // Resolve index-based dependencies
                                const resolvedDeps = (deps || []).map(dep => {
                                    if (typeof dep === 'number' && dep < rawModules.length) {
                                        return rawModules[dep][0]; // Get name of module at index
                                    }
                                    return dep;
                                }).filter(dep => typeof dep === 'string');
                                
                                registry[name] = {
                                    version: version || '',
                                    dependencies: resolvedDeps,
                                    group: group || null,
                                    index: index
                                };
                                
                                dependencyGraph[name] = resolvedDeps;
                                
                                // Build reverse dependencies
                                for (const dep of resolvedDeps) {
                                    if (!reverseDependencyGraph[dep]) {
                                        reverseDependencyGraph[dep] = [];
                                    }
                                    reverseDependencyGraph[dep].push(name);
                                }
                            });
                            
                            // Count dependency relationships
                            const totalDeps = Object.values(dependencyGraph)
                                .reduce((sum, deps) => sum + deps.length, 0);
                            
                            return {
                                registry: registry,
                                dependency_graph: dependencyGraph,
                                reverse_dependency_graph: reverseDependencyGraph,
                                module_count: rawModules.length,
                                total_dependencies: totalDeps,
                                extraction_method: 'register_interception'
                            };
                        }
                        
                        // No data available
                        return { 
                            error: "No module data captured",
                            debug: {
                                hasMw: typeof window.mw !== 'undefined',
                                hasLoader: typeof window.mw?.loader !== 'undefined',
                                hasRegistry: typeof window.mw?.loader?.moduleRegistry !== 'undefined',
                                registrySize: window.mw?.loader?.moduleRegistry ? Object.keys(window.mw.loader.moduleRegistry).length : 0,
                                hasCaptured: typeof window.__capturedModules !== 'undefined',
                                hasRegistryData: typeof window.__registryData !== 'undefined'
                            }
                        };
                    }
                """)
                
                print(f"[SUCCESS] Extracted {registry_data.get('module_count', 0)} modules")
                print(f"[INFO] Total dependency relationships: {registry_data.get('total_dependencies', 0)}")
                
                return registry_data
                
            finally:
                await browser.close()
    
    def _create_extraction_page(self, startup_content: str) -> str:
        """Create HTML page that captures mw.loader.register calls."""
        
        return f"""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>Module Registry Extractor</title>
            <script>
                // Set up capture mechanism before loading startup.js
                window.__capturedModules = null;
                window.__consoleOutput = [];
                
                // Override console.log to capture debug info
                const originalLog = console.log;
                console.log = function(...args) {{
                    window.__consoleOutput.push(args.join(' '));
                    originalLog.apply(console, args);
                }};
                
                // Don't create mw object initially - let startup.js create it
                // We'll intercept the register method after it's created
                
                // Set up observer to detect when mw.loader is created
                let originalMwLoader = null;
                Object.defineProperty(window, 'mw', {{
                    configurable: true,
                    get: function() {{
                        return window._mw;
                    }},
                    set: function(value) {{
                        console.log('mw object is being set');
                        window._mw = value;
                        
                        // Intercept the loader when it's created
                        if (value && value.loader && value.loader.register) {{
                            console.log('Intercepting mw.loader.register');
                            const originalRegister = value.loader.register;
                            value.loader.register = function(modules) {{
                                console.log('REGISTER CALLED: Captured', modules ? modules.length : 0, 'modules');
                                window.__capturedModules = modules;
                                window.__registerCallCount = (window.__registerCallCount || 0) + 1;
                                // Also call the original to maintain functionality
                                return originalRegister.call(this, modules);
                            }};
                        }}
                    }}
                }});
                
                // Stub other globals that might be referenced
                window.$ = function() {{ return {{ ready: function() {{}} }}; }};
                window.jQuery = window.$;
            </script>
        </head>
        <body>
            <h1>Extracting Module Registry...</h1>
            <div id="status">Loading startup.js...</div>
            
            <script>
                // Execute the startup module
                try {{
                    console.log('Starting startup.js execution...');
                    {startup_content}
                    console.log('Finished startup.js execution');
                    
                    // Now intercept register method if it exists
                    if (window.mw && window.mw.loader && window.mw.loader.register) {{
                        console.log('Found mw.loader.register, setting up interception');
                        const originalRegister = window.mw.loader.register;
                        window.mw.loader.register = function(modules) {{
                            console.log('REGISTER CALLED: Captured', modules ? modules.length : 0, 'modules');
                            window.__capturedModules = modules;
                            window.__registerCallCount = (window.__registerCallCount || 0) + 1;
                            // Also call the original to maintain functionality
                            return originalRegister.call(this, modules);
                        }};
                        
                        // Try to trigger any pending register calls
                        console.log('Interception set up, triggering any deferred register calls...');
                        
                        // Check if startup.js has any module registry data we can access directly
                        if (window.mw.loader.moduleRegistry) {{
                            const registry = window.mw.loader.moduleRegistry;
                            const moduleCount = Object.keys(registry).length;
                            console.log('Found moduleRegistry with', moduleCount, 'modules');
                            
                            // Extract the registry data directly since register() already ran
                            window.__capturedModules = [];
                            window.__registryData = {{}};
                            
                            for (const [name, module] of Object.entries(registry)) {{
                                window.__registryData[name] = {{
                                    dependencies: module.dependencies || [],
                                    group: module.group,
                                    state: module.state,
                                    version: module.version || ''
                                }};
                            }}
                            
                            console.log('Extracted registry data for', Object.keys(window.__registryData).length, 'modules');
                        }}
                    }} else {{
                        console.log('mw.loader.register not found after execution');
                    }}
                    
                    console.log('Register call count:', window.__registerCallCount || 0);
                    console.log('Captured modules:', window.__capturedModules ? window.__capturedModules.length : 'null');
                    document.getElementById('status').textContent = 
                        'Captured ' + (window.__capturedModules ? window.__capturedModules.length : 0) + ' modules, ' +
                        'Register calls: ' + (window.__registerCallCount || 0);
                }} catch (e) {{
                    console.error('Error executing startup.js:', e);
                    console.log('Error details:', e.stack);
                    document.getElementById('status').textContent = 'Error: ' + e.message;
                    window.__executionError = e.message;
                }}
            </script>
        </body>
        </html>
        """
    
    def analyze_registry(self, registry_data: Dict[str, Any]) -> Dict[str, Any]:
        """Analyze the extracted registry and provide insights."""
        
        if 'error' in registry_data:
            return {"error": registry_data['error']}
        
        registry = registry_data.get('registry', {})
        dep_graph = registry_data.get('dependency_graph', {})
        reverse_deps = registry_data.get('reverse_dependency_graph', {})
        
        # Find infrastructure modules
        infrastructure_patterns = [
            r'^jquery$',
            r'^jquery\.',
            r'^mediawiki\.',
            r'^oojs',
            r'^mw\.',
            r'^site$',
            r'^user$'
        ]
        
        infrastructure_modules = []
        for module_name in registry:
            for pattern in infrastructure_patterns:
                import re
                if re.match(pattern, module_name):
                    infrastructure_modules.append(module_name)
                    break
        
        # Find most depended-on modules
        dep_counts = [(len(dependents), module) for module, dependents in reverse_deps.items()]
        dep_counts.sort(reverse=True)
        
        # Find modules with most dependencies
        dependency_counts = [(len(deps), module) for module, deps in dep_graph.items()]
        dependency_counts.sort(reverse=True)
        
        return {
            "summary": {
                "total_modules": len(registry),
                "total_dependencies": registry_data.get('total_dependencies', 0),
                "infrastructure_modules": len(infrastructure_modules),
                "extraction_method": registry_data.get('extraction_method', 'unknown')
            },
            "infrastructure_modules": sorted(infrastructure_modules),
            "most_depended_on": [{"module": module, "dependents": count} for count, module in dep_counts[:10]],
            "most_dependencies": [{"module": module, "dependencies": count} for count, module in dependency_counts[:10]],
            "key_modules": {
                "jquery": "jquery" in registry,
                "mediawiki_base": "mediawiki.base" in registry,
                "oojs": "oojs" in registry,
                "oojs_ui_core": "oojs-ui-core" in registry
            }
        }


async def main():
    """Main function for testing the extractor."""
    if len(sys.argv) < 2:
        print("Usage: python startup_extractor.py <method> [source] [output_file]")
        print()
        print("Methods:")
        print("  file <startup_file>     - Extract from local startup.js file")
        print("  url [startup_url]       - Extract by downloading startup.js")
        print("  live <page_url>         - Extract from live MediaWiki page")
        print()
        print("Examples:")
        print("  python startup_extractor.py file artifacts/startup.js")
        print("  python startup_extractor.py url")
        print("  python startup_extractor.py live https://oldschool.runescape.wiki/w/Logs")
        sys.exit(1)
    
    method = sys.argv[1]
    output_file = Path(sys.argv[-1] if len(sys.argv) > 3 and not sys.argv[-1].startswith('http') else "extracted_registry.json")
    
    extractor = StartupExtractor()
    
    try:
        if method == "file":
            if len(sys.argv) < 3:
                print("[ERROR] File method requires startup file path")
                sys.exit(1)
            startup_file = Path(sys.argv[2])
            registry_data = await extractor.extract_from_file(startup_file)
            
        elif method == "url":
            startup_url = sys.argv[2] if len(sys.argv) > 2 else None
            registry_data = await extractor.extract_from_url(startup_url)
            
        elif method == "live":
            if len(sys.argv) < 3:
                print("[ERROR] Live method requires page URL")
                sys.exit(1)
            page_url = sys.argv[2]
            registry_data = await extractor.extract_from_live_page(page_url)
            
        else:
            print(f"[ERROR] Unknown method: {method}")
            sys.exit(1)
        
        if 'error' in registry_data:
            print(f"[ERROR] Extraction failed: {registry_data['error']}")
            sys.exit(1)
        
        # Analyze the results
        analysis = extractor.analyze_registry(registry_data)
        registry_data["analysis"] = analysis
        
        # Save results
        with open(output_file, 'w') as f:
            json.dump(registry_data, f, indent=2)
        
        print(f"[SUCCESS] Registry saved to: {output_file}")
        
        # Print summary
        print("\n" + "="*60)
        print("MODULE REGISTRY EXTRACTION SUMMARY")
        print("="*60)
        summary = analysis["summary"]
        for key, value in summary.items():
            print(f"{key.replace('_', ' ').title()}: {value}")
        
        print(f"\nKey modules found:")
        for module, found in analysis["key_modules"].items():
            status = "✅" if found else "❌"
            print(f"  {status} {module.replace('_', ' ').title()}")
        
        print(f"\nMost depended-on modules:")
        for item in analysis["most_depended_on"][:5]:
            print(f"  {item['module']}: {item['dependents']} dependents")
            
    except Exception as e:
        print(f"[ERROR] Extraction failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())