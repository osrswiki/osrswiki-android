#!/usr/bin/env python3
"""
MediaWiki Module Dependency Resolver

This tool analyzes MediaWiki's module registry and performs complete dependency resolution.
It parses the startup module to extract the module manifest and builds a complete dependency graph.
"""

import json
import re
import sys
import requests
from pathlib import Path
from typing import Dict, List, Set, Any, Optional, Tuple
from collections import defaultdict, deque

class MediaWikiModuleResolver:
    def __init__(self, base_url: str = "https://oldschool.runescape.wiki"):
        self.base_url = base_url
        self.module_registry = {}
        self.dependency_graph = defaultdict(list)
        self.reverse_dependency_graph = defaultdict(list)
        self.module_groups = {}
        
    def load_startup_module(self) -> str:
        """Download and return the startup module content."""
        startup_url = f"{self.base_url}/load.php"
        params = {
            'modules': 'startup',
            'only': 'scripts',
            'skin': 'vector',
            'debug': 'true',
            'lang': 'en-gb'
        }
        
        print(f"[INFO] Downloading startup module from {startup_url}")
        response = requests.get(startup_url, params=params, timeout=30)
        response.raise_for_status()
        
        content = response.text
        print(f"[SUCCESS] Downloaded startup module ({len(content)} bytes)")
        return content
    
    def parse_module_registry(self, startup_content: str) -> Dict[str, Any]:
        """Parse the module registry from startup.js content."""
        
        # Find mw.loader.register calls
        register_pattern = r'mw\.loader\.register\(\s*(\[[\s\S]*?\])\s*\)'
        matches = re.findall(register_pattern, startup_content)
        
        if not matches:
            raise ValueError("Could not find mw.loader.register call in startup module")
        
        print(f"[INFO] Found {len(matches)} register calls")
        
        # Parse the largest register call (main module registry)
        registry_data = max(matches, key=len)
        
        try:
            # Clean up the JavaScript array to make it valid JSON
            cleaned_data = self._clean_js_array(registry_data)
            module_definitions = json.loads(cleaned_data)
            print(f"[SUCCESS] Parsed {len(module_definitions)} module definitions")
            
        except json.JSONDecodeError as e:
            print(f"[ERROR] Failed to parse module registry as JSON: {e}")
            # Fallback: manual parsing
            module_definitions = self._manual_parse_registry(registry_data)
        
        return self._process_module_definitions(module_definitions)
    
    def _clean_js_array(self, js_array: str) -> str:
        """Clean JavaScript array syntax to make it valid JSON."""
        # Remove comments
        cleaned = re.sub(r'//.*?$', '', js_array, flags=re.MULTILINE)
        
        # Handle unquoted object keys and other JS-specific syntax
        # This is a simplified approach - may need enhancement
        return cleaned
    
    def _manual_parse_registry(self, registry_data: str) -> List[List]:
        """Manually parse module registry when JSON parsing fails."""
        print("[WARN] Falling back to manual parsing")
        
        # Extract individual module definitions using regex
        module_pattern = r'\[\s*"([^"]+)"[^\]]*\]'
        matches = re.findall(module_pattern, registry_data)
        
        # For now, create simplified module definitions
        # In a real implementation, we'd need more sophisticated parsing
        module_definitions = []
        for match in matches:
            module_definitions.append([match, "", [], None])
            
        print(f"[INFO] Manual parsing extracted {len(module_definitions)} modules")
        return module_definitions
    
    def _process_module_definitions(self, module_definitions: List[List]) -> Dict[str, Any]:
        """Process raw module definitions into structured registry."""
        registry = {}
        
        for definition in module_definitions:
            if not isinstance(definition, list) or len(definition) < 2:
                continue
                
            module_name = definition[0]
            version = definition[1] if len(definition) > 1 else ""
            dependencies = definition[2] if len(definition) > 2 else []
            group = definition[3] if len(definition) > 3 else None
            
            # Handle dependency indices (some modules reference dependencies by index)
            if isinstance(dependencies, list):
                resolved_deps = []
                for dep in dependencies:
                    if isinstance(dep, int):
                        # Dependency by index - resolve later
                        resolved_deps.append(f"__INDEX_{dep}__")
                    elif isinstance(dep, str):
                        resolved_deps.append(dep)
                dependencies = resolved_deps
            
            registry[module_name] = {
                "version": version,
                "dependencies": dependencies,
                "group": group
            }
            
            # Build dependency graph
            for dep in dependencies:
                if isinstance(dep, str) and not dep.startswith("__INDEX_"):
                    self.dependency_graph[module_name].append(dep)
                    self.reverse_dependency_graph[dep].append(module_name)
        
        self.module_registry = registry
        print(f"[SUCCESS] Built registry with {len(registry)} modules")
        return registry
    
    def resolve_dependencies(self, module_names: List[str]) -> Dict[str, List[str]]:
        """Resolve all transitive dependencies for given modules."""
        
        all_dependencies = set()
        resolution_order = []
        
        def resolve_module(module_name: str, visited: Set[str], path: List[str]):
            """Recursively resolve dependencies for a module."""
            if module_name in visited:
                if module_name in path:
                    print(f"[WARN] Circular dependency detected: {' -> '.join(path + [module_name])}")
                return
            
            visited.add(module_name)
            path.append(module_name)
            
            # Get module info
            module_info = self.module_registry.get(module_name, {})
            dependencies = module_info.get("dependencies", [])
            
            # Resolve each dependency first
            for dep in dependencies:
                if not dep.startswith("__INDEX_"):  # Skip unresolved index references for now
                    resolve_module(dep, visited, path.copy())
                    all_dependencies.add(dep)
            
            # Add this module to resolution order
            if module_name not in resolution_order:
                resolution_order.append(module_name)
            all_dependencies.add(module_name)
            
            path.pop()
        
        # Resolve dependencies for all requested modules
        visited = set()
        for module_name in module_names:
            resolve_module(module_name, visited, [])
        
        return {
            "requested_modules": module_names,
            "all_dependencies": sorted(list(all_dependencies)),
            "resolution_order": resolution_order,
            "dependency_count": len(all_dependencies)
        }
    
    def analyze_infrastructure_modules(self) -> Dict[str, List[str]]:
        """Identify core infrastructure modules that are commonly needed."""
        
        infrastructure_patterns = [
            r'^jquery',
            r'^mediawiki\.',
            r'^oojs',
            r'^mw\.',
        ]
        
        infrastructure_modules = []
        for module_name in self.module_registry:
            for pattern in infrastructure_patterns:
                if re.match(pattern, module_name):
                    infrastructure_modules.append(module_name)
                    break
        
        # Also identify modules that many others depend on
        dependency_counts = defaultdict(int)
        for module_name, dependents in self.reverse_dependency_graph.items():
            dependency_counts[module_name] = len(dependents)
        
        # Modules with high dependency counts are likely infrastructure
        highly_depended_on = [
            module for module, count in dependency_counts.items() 
            if count >= 5  # Threshold for "highly depended on"
        ]
        
        return {
            "infrastructure_modules": sorted(infrastructure_modules),
            "highly_depended_on": sorted(highly_depended_on),
            "core_candidates": sorted(list(set(infrastructure_modules + highly_depended_on)))
        }
    
    def get_module_info(self, module_name: str) -> Dict[str, Any]:
        """Get detailed information about a specific module."""
        if module_name not in self.module_registry:
            return {"error": f"Module '{module_name}' not found in registry"}
        
        module_info = self.module_registry[module_name].copy()
        module_info.update({
            "direct_dependencies": self.dependency_graph.get(module_name, []),
            "dependents": self.reverse_dependency_graph.get(module_name, []),
            "dependency_count": len(self.dependency_graph.get(module_name, [])),
            "dependent_count": len(self.reverse_dependency_graph.get(module_name, []))
        })
        
        return module_info
    
    def find_missing_modules(self, requested_modules: List[str]) -> List[str]:
        """Find modules that are requested but not in the registry."""
        missing = []
        for module in requested_modules:
            if module not in self.module_registry:
                missing.append(module)
        return missing
    
    def generate_loading_plan(self, page_modules: List[str]) -> Dict[str, Any]:
        """Generate a complete loading plan for given page modules."""
        
        # Resolve all dependencies
        resolution = self.resolve_dependencies(page_modules)
        
        # Analyze infrastructure needs
        infrastructure = self.analyze_infrastructure_modules()
        
        # Find missing modules
        missing = self.find_missing_modules(page_modules)
        
        # Categorize modules by type
        gadget_modules = [m for m in resolution["all_dependencies"] if "gadget" in m]
        extension_modules = [m for m in resolution["all_dependencies"] if m.startswith("ext.") and "gadget" not in m]
        core_modules = [m for m in resolution["all_dependencies"] if m in infrastructure["core_candidates"]]
        other_modules = [m for m in resolution["all_dependencies"] if m not in gadget_modules + extension_modules + core_modules]
        
        return {
            "summary": {
                "requested_modules": len(page_modules),
                "total_required_modules": resolution["dependency_count"],
                "core_modules": len(core_modules),
                "extension_modules": len(extension_modules),
                "gadget_modules": len(gadget_modules),
                "other_modules": len(other_modules),
                "missing_modules": len(missing)
            },
            "loading_order": {
                "phase_1_core": core_modules,
                "phase_2_extensions": extension_modules,
                "phase_3_gadgets": gadget_modules,
                "phase_4_other": other_modules
            },
            "all_required_modules": resolution["all_dependencies"],
            "missing_modules": missing,
            "resolution_details": resolution
        }


def main():
    """Main function."""
    if len(sys.argv) < 2:
        print("Usage: python module_resolver.py <analysis_file> [output_file]")
        print("Example: python module_resolver.py logs_analysis.json loading_plan.json")
        sys.exit(1)
    
    analysis_file = Path(sys.argv[1])
    output_file = Path(sys.argv[2] if len(sys.argv) > 2 else "loading_plan.json")
    
    if not analysis_file.exists():
        print(f"[ERROR] Analysis file not found: {analysis_file}")
        sys.exit(1)
    
    # Load analysis data
    with open(analysis_file) as f:
        analysis_data = json.load(f)
    
    page_modules = analysis_data.get("page_modules", [])
    
    print(f"[INFO] Analyzing dependencies for {len(page_modules)} page modules")
    
    resolver = MediaWikiModuleResolver()
    
    try:
        # Load and parse startup module
        startup_content = resolver.load_startup_module()
        resolver.parse_module_registry(startup_content)
        
        # Generate loading plan
        loading_plan = resolver.generate_loading_plan(page_modules)
        
        # Save results
        with open(output_file, 'w') as f:
            json.dump(loading_plan, f, indent=2)
        
        print(f"[SUCCESS] Loading plan saved to: {output_file}")
        
        # Print summary
        print("\n" + "="*60)
        print("DEPENDENCY RESOLUTION SUMMARY")
        print("="*60)
        summary = loading_plan["summary"]
        for key, value in summary.items():
            print(f"{key.replace('_', ' ').title()}: {value}")
        
        print(f"\nLoading phases:")
        phases = loading_plan["loading_order"]
        for phase, modules in phases.items():
            print(f"  {phase.replace('_', ' ').title()}: {len(modules)} modules")
        
        if loading_plan["missing_modules"]:
            print(f"\n⚠️  Missing modules: {', '.join(loading_plan['missing_modules'])}")
            
    except Exception as e:
        print(f"[ERROR] Resolution failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()