#!/usr/bin/env python3
"""
MediaWiki Module Dependency Resolver

This tool analyzes MediaWiki's module registry and performs complete dependency resolution.
It uses the execution-based startup_extractor to get accurate module data without parsing.
"""

import asyncio
import json
import re
import sys
import tempfile
from pathlib import Path
from typing import Dict, List, Set, Any, Optional
from collections import defaultdict, deque
from startup_extractor import StartupExtractor

class MediaWikiModuleResolver:
    def __init__(self, base_url: str = "https://oldschool.runescape.wiki"):
        self.base_url = base_url
        self.module_registry = {}
        self.dependency_graph = {}
        self.reverse_dependency_graph = {}
        self.module_groups = {}
        
    async def load_module_registry(self) -> Dict[str, Any]:
        """Load module registry using execution-based extraction."""
        
        print(f"[INFO] Loading module registry using execution-based extraction")
        
        extractor = StartupExtractor(self.base_url)
        registry_data = await extractor.extract_from_url()
        
        if 'error' in registry_data:
            raise ValueError(f"Failed to extract registry: {registry_data['error']}")
        
        # Store the extracted data
        self.module_registry = registry_data['registry']
        self.dependency_graph = registry_data['dependency_graph']
        self.reverse_dependency_graph = registry_data['reverse_dependency_graph']
        
        print(f"[SUCCESS] Loaded {len(self.module_registry)} modules with {registry_data['total_dependencies']} dependencies")
        
        return registry_data
    
    async def load_from_file(self, registry_file: Path) -> Dict[str, Any]:
        """Load module registry from a previously saved extraction file."""
        
        print(f"[INFO] Loading module registry from file: {registry_file}")
        
        with open(registry_file, 'r') as f:
            registry_data = json.load(f)
        
        if 'error' in registry_data:
            raise ValueError(f"Registry file contains error: {registry_data['error']}")
        
        # Store the loaded data
        self.module_registry = registry_data['registry']
        self.dependency_graph = registry_data['dependency_graph']
        self.reverse_dependency_graph = registry_data['reverse_dependency_graph']
        
        print(f"[SUCCESS] Loaded {len(self.module_registry)} modules with {registry_data['total_dependencies']} dependencies")
        
        return registry_data
    
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


async def main():
    """Main function."""
    if len(sys.argv) < 2:
        print("Usage: python module_resolver.py <method> [analysis_file] [output_file]")
        print()
        print("Methods:")
        print("  live <analysis_file>     - Load registry from live site and resolve dependencies")
        print("  file <registry_file> <analysis_file> - Use saved registry file")
        print()
        print("Examples:")
        print("  python module_resolver.py live logs_analysis.json loading_plan.json")
        print("  python module_resolver.py file startup_execution_final.json logs_analysis.json")
        sys.exit(1)
    
    method = sys.argv[1]
    
    if method == "live":
        if len(sys.argv) < 3:
            print("[ERROR] Live method requires analysis file")
            sys.exit(1)
        analysis_file = Path(sys.argv[2])
        output_file = Path(sys.argv[3] if len(sys.argv) > 3 else "loading_plan.json")
        registry_source = "live"
        
    elif method == "file":
        if len(sys.argv) < 4:
            print("[ERROR] File method requires registry file and analysis file")
            sys.exit(1)
        registry_file = Path(sys.argv[2])
        analysis_file = Path(sys.argv[3])
        output_file = Path(sys.argv[4] if len(sys.argv) > 4 else "loading_plan.json")
        registry_source = "file"
        
        if not registry_file.exists():
            print(f"[ERROR] Registry file not found: {registry_file}")
            sys.exit(1)
    else:
        print(f"[ERROR] Unknown method: {method}")
        sys.exit(1)
    
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
        # Load module registry
        if registry_source == "live":
            registry_data = await resolver.load_module_registry()
        else:
            registry_data = await resolver.load_from_file(registry_file)
        
        # Generate loading plan
        loading_plan = resolver.generate_loading_plan(page_modules)
        
        # Add registry metadata
        loading_plan["registry_info"] = {
            "source": registry_source,
            "extraction_method": registry_data.get("extraction_method", "unknown"),
            "total_modules_available": len(resolver.module_registry),
            "total_dependencies_available": registry_data.get("total_dependencies", 0)
        }
        
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
        
        print(f"\nRegistry info:")
        reg_info = loading_plan["registry_info"]
        for key, value in reg_info.items():
            print(f"  {key.replace('_', ' ').title()}: {value}")
        
        print(f"\nLoading phases:")
        phases = loading_plan["loading_order"]
        for phase, modules in phases.items():
            print(f"  {phase.replace('_', ' ').title()}: {len(modules)} modules")
        
        if loading_plan["missing_modules"]:
            print(f"\n⚠️  Missing modules: {', '.join(loading_plan['missing_modules'])}")
        else:
            print(f"\n✅ All required modules available in registry")
            
    except Exception as e:
        print(f"[ERROR] Resolution failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())