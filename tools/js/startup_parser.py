#!/usr/bin/env python3
"""
MediaWiki Startup Module Parser

This module properly parses MediaWiki's startup.js file to extract the complete
module registry with dependency information. Unlike JSON parsing, this handles
the JavaScript array syntax with index-based dependency references.
"""

import re
import json
import sys
from pathlib import Path
from typing import Dict, List, Set, Any, Optional, Tuple

class StartupParser:
    def __init__(self):
        self.module_registry = {}
        self.module_list = []  # Ordered list for index-based resolution
        self.dependency_graph = {}
        self.reverse_dependency_graph = {}
        
    def parse_startup_file(self, startup_content: str) -> Dict[str, Any]:
        """Parse startup.js content and extract complete module registry."""
        
        # Extract the mw.loader.register call
        register_content = self._extract_register_call(startup_content)
        if not register_content:
            raise ValueError("Could not find mw.loader.register call in startup content")
            
        # Parse the module definitions
        module_definitions = self._parse_module_array(register_content)
        if not module_definitions:
            raise ValueError("Could not parse module definitions from register call")
            
        print(f"[SUCCESS] Parsed {len(module_definitions)} module definitions")
        
        # Build the registry and dependency graph
        self._build_registry(module_definitions)
        
        return {
            "registry": self.module_registry,
            "dependency_graph": self.dependency_graph,
            "reverse_dependency_graph": self.reverse_dependency_graph,
            "module_count": len(self.module_registry)
        }
    
    def _extract_register_call(self, content: str) -> str:
        """Extract the mw.loader.register([...]) array from startup content."""
        
        # Find the start of the register call
        register_pattern = r'mw\.loader\.register\(\s*\['
        match = re.search(register_pattern, content)
        
        if not match:
            return None
            
        start_pos = match.start()
        
        # Find the matching closing bracket using bracket counting
        bracket_count = 0
        in_register_call = False
        register_start = None
        register_end = None
        
        i = start_pos
        while i < len(content):
            char = content[i]
            
            if char == '[':
                if not in_register_call:
                    in_register_call = True
                    register_start = i
                bracket_count += 1
            elif char == ']':
                bracket_count -= 1
                if bracket_count == 0 and in_register_call:
                    register_end = i
                    break
                    
            i += 1
        
        if register_start is None or register_end is None:
            return None
            
        # Extract the array content
        array_content = content[register_start:register_end + 1]
        print(f"[INFO] Extracted register array ({len(array_content)} characters)")
        
        return array_content
    
    def _parse_module_array(self, array_content: str) -> List[List]:
        """Parse the JavaScript array into Python data structures."""
        
        # Clean up the content for easier parsing
        cleaned_content = self._clean_for_parsing(array_content)
        
        # Try JSON parsing first (might work if content is clean enough)
        try:
            return json.loads(cleaned_content)
        except json.JSONDecodeError:
            pass
            
        # Fall back to regex-based parsing
        return self._regex_parse_array(array_content)
    
    def _clean_for_parsing(self, content: str) -> str:
        """Clean JavaScript syntax to be more JSON-compatible."""
        
        # Remove JavaScript comments
        content = re.sub(r'//.*?$', '', content, flags=re.MULTILINE)
        content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
        
        # Handle trailing commas
        content = re.sub(r',(\s*[}\]])', r'\1', content)
        
        # Handle unescaped control characters in strings
        # This is a simplified approach - may need refinement
        content = content.replace('\\u0026', '&')
        content = content.replace('\\u003C', '<')
        content = content.replace('\\u003E', '>')
        
        return content
    
    def _regex_parse_array(self, array_content: str) -> List[List]:
        """Parse module definitions using regex when JSON parsing fails."""
        
        print("[INFO] Using regex-based parsing for module array")
        
        modules = []
        
        # More sophisticated pattern to match complete module definitions
        # This pattern needs to handle various formats including modules without dependencies
        module_pattern = r'\[\s*"([^"]+)"(?:\s*,\s*"([^"]*)")?(?:\s*,\s*(\[[^\]]*\]))?(?:\s*,\s*(\d+))?\s*\]'
        
        matches = re.finditer(module_pattern, array_content, re.DOTALL)
        
        for match in matches:
            module_name = match.group(1)
            version = match.group(2) or ""
            deps_content = match.group(3) or "[]"
            group = int(match.group(4)) if match.group(4) else None
            
            # Parse dependencies array
            dependencies = []
            if deps_content and deps_content != "[]":
                # Extract numbers and quoted strings from the dependency array
                dep_matches = re.findall(r'(?:"([^"]+)"|(\d+))', deps_content)
                for string_dep, numeric_dep in dep_matches:
                    if string_dep:
                        dependencies.append(string_dep)
                    elif numeric_dep:
                        dependencies.append(int(numeric_dep))
            
            # Create module definition: [name, version, dependencies, group]
            module_def = [module_name, version, dependencies, group]
            modules.append(module_def)
        
        print(f"[INFO] Regex parsing extracted {len(modules)} modules")
        
        # If we didn't get the expected number of modules (~650), try alternative approach
        if len(modules) < 600:
            print(f"[WARN] Only found {len(modules)} modules, trying alternative parsing...")
            modules = self._alternative_parse_array(array_content)
        
        return modules
    
    def _alternative_parse_array(self, array_content: str) -> List[List]:
        """Alternative parsing approach for complex JavaScript arrays."""
        
        print("[INFO] Using alternative array parsing approach")
        
        modules = []
        
        # Use a more careful approach: find module boundaries by looking for "],\n    ["
        # But also handle end cases like "],\n]" for the last module
        
        # First, clean up the array content
        content = array_content.strip()
        if content.startswith('['):
            content = content[1:]
        if content.endswith(']'):
            content = content[:-1]
        
        # Split by module boundaries
        # Each module should start with [ and end with ]
        module_starts = []
        bracket_depth = 0
        current_pos = 0
        
        for i, char in enumerate(content):
            if char == '[':
                if bracket_depth == 0:
                    module_starts.append(i)
                bracket_depth += 1
            elif char == ']':
                bracket_depth -= 1
                if bracket_depth == 0:
                    # End of a module
                    module_text = content[module_starts[-1]:i+1]
                    module = self._parse_single_module(module_text)
                    if module:
                        modules.append(module)
        
        print(f"[INFO] Alternative parsing extracted {len(modules)} modules")
        return modules
    
    def _parse_single_module(self, module_text: str) -> Optional[List]:
        """Parse a single module definition from text."""
        
        try:
            # Clean and attempt JSON parsing
            cleaned = module_text.strip()
            if not cleaned.startswith('['):
                cleaned = '[' + cleaned
            if not cleaned.endswith(']'):
                cleaned = cleaned + ']'
            
            # Handle escape sequences
            cleaned = cleaned.replace('\\u0026', '&')
            cleaned = cleaned.replace('\\u003C', '<')
            cleaned = cleaned.replace('\\u003E', '>')
            
            # Try JSON parsing
            try:
                return json.loads(cleaned)
            except json.JSONDecodeError:
                pass
            
            # Extract components manually
            lines = cleaned.split('\n')
            module_name = None
            version = ""
            dependencies = []
            group = None
            
            for line in lines:
                line = line.strip().rstrip(',')
                
                # Module name (first quoted string)
                if module_name is None:
                    name_match = re.search(r'"([^"]+)"', line)
                    if name_match:
                        module_name = name_match.group(1)
                        continue
                
                # Version (second quoted string, usually empty)
                if '""' in line and version == "":
                    continue  # Empty version
                
                # Dependencies (numbers in the content)
                if re.search(r'^\s*\d+\s*$', line):
                    dependencies.append(int(line.strip()))
                
                # Group (final number)
                if line.isdigit() and group is None:
                    group = int(line)
            
            if module_name:
                return [module_name, version, dependencies, group]
            
        except Exception as e:
            # Silent failure for malformed modules
            pass
        
        return None
    
    def _build_registry(self, module_definitions: List[List]):
        """Build the registry and dependency graphs from parsed definitions."""
        
        # First pass: create the ordered module list for index resolution
        self.module_list = []
        for definition in module_definitions:
            if isinstance(definition, list) and len(definition) >= 1:
                self.module_list.append(definition[0])
        
        print(f"[INFO] Built module list with {len(self.module_list)} modules")
        
        # Second pass: build registry with resolved dependencies
        for i, definition in enumerate(module_definitions):
            if not isinstance(definition, list) or len(definition) < 2:
                continue
                
            module_name = definition[0]
            version = definition[1] if len(definition) > 1 else ""
            raw_dependencies = definition[2] if len(definition) > 2 else []
            group = definition[3] if len(definition) > 3 else None
            
            # Resolve dependencies
            resolved_dependencies = self._resolve_dependencies(raw_dependencies)
            
            # Store in registry
            self.module_registry[module_name] = {
                "version": version,
                "dependencies": resolved_dependencies,
                "group": group,
                "index": i
            }
            
            # Build dependency graphs
            self.dependency_graph[module_name] = resolved_dependencies
            
            for dep in resolved_dependencies:
                if dep not in self.reverse_dependency_graph:
                    self.reverse_dependency_graph[dep] = []
                self.reverse_dependency_graph[dep].append(module_name)
        
        print(f"[SUCCESS] Built registry with {len(self.module_registry)} modules")
        
        # Report on dependency resolution
        total_deps = sum(len(deps) for deps in self.dependency_graph.values())
        print(f"[INFO] Total dependency relationships: {total_deps}")
        
        # Find most depended-on modules
        dep_counts = [(len(deps), name) for name, deps in self.reverse_dependency_graph.items()]
        dep_counts.sort(reverse=True)
        
        print("[INFO] Most depended-on modules:")
        for count, name in dep_counts[:5]:
            print(f"  {name}: {count} dependents")
    
    def _resolve_dependencies(self, raw_dependencies: List) -> List[str]:
        """Resolve dependency list, converting indices to module names."""
        
        resolved = []
        
        for dep in raw_dependencies:
            if isinstance(dep, str):
                # Already a module name
                resolved.append(dep)
            elif isinstance(dep, int):
                # Index-based dependency - resolve to module name
                if 0 <= dep < len(self.module_list):
                    resolved.append(self.module_list[dep])
                else:
                    print(f"[WARN] Invalid dependency index: {dep} (max: {len(self.module_list) - 1})")
            else:
                print(f"[WARN] Unknown dependency type: {type(dep)} - {dep}")
        
        return resolved
    
    def get_module_info(self, module_name: str) -> Optional[Dict[str, Any]]:
        """Get detailed information about a specific module."""
        
        if module_name not in self.module_registry:
            return None
            
        info = self.module_registry[module_name].copy()
        info.update({
            "direct_dependencies": self.dependency_graph.get(module_name, []),
            "dependents": self.reverse_dependency_graph.get(module_name, []),
            "dependency_count": len(self.dependency_graph.get(module_name, [])),
            "dependent_count": len(self.reverse_dependency_graph.get(module_name, []))
        })
        
        return info
    
    def find_infrastructure_modules(self) -> Dict[str, List[str]]:
        """Identify core infrastructure modules."""
        
        # Patterns for infrastructure modules
        core_patterns = [
            r'^jquery$',
            r'^jquery\.',
            r'^mediawiki\.',
            r'^oojs',
            r'^mw\.',
            r'^site$',
            r'^user$'
        ]
        
        infrastructure = []
        for module_name in self.module_registry:
            for pattern in core_patterns:
                if re.match(pattern, module_name):
                    infrastructure.append(module_name)
                    break
        
        # Also find highly depended-on modules
        highly_depended = []
        for module_name, dependents in self.reverse_dependency_graph.items():
            if len(dependents) >= 10:  # Threshold for "highly depended on"
                highly_depended.append(module_name)
        
        return {
            "infrastructure_modules": sorted(infrastructure),
            "highly_depended_on": sorted(highly_depended),
            "core_candidates": sorted(list(set(infrastructure + highly_depended)))
        }


def main():
    """Test the parser with a startup file."""
    if len(sys.argv) < 2:
        print("Usage: python startup_parser.py <startup_file> [output_file]")
        print("Example: python startup_parser.py artifacts/startup.js startup_registry.json")
        sys.exit(1)
    
    startup_file = Path(sys.argv[1])
    output_file = Path(sys.argv[2] if len(sys.argv) > 2 else "startup_registry.json")
    
    if not startup_file.exists():
        print(f"[ERROR] Startup file not found: {startup_file}")
        sys.exit(1)
    
    print(f"[INFO] Parsing startup file: {startup_file}")
    
    parser = StartupParser()
    
    try:
        # Load and parse the startup file
        with open(startup_file, 'r', encoding='utf-8') as f:
            startup_content = f.read()
        
        print(f"[INFO] Loaded startup file ({len(startup_content)} characters)")
        
        # Parse the registry
        registry_data = parser.parse_startup_file(startup_content)
        
        # Add infrastructure analysis
        infrastructure = parser.find_infrastructure_modules()
        registry_data["infrastructure"] = infrastructure
        
        # Save results
        with open(output_file, 'w') as f:
            json.dump(registry_data, f, indent=2)
        
        print(f"[SUCCESS] Registry saved to: {output_file}")
        
        # Print summary
        print("\n" + "="*60)
        print("STARTUP PARSING SUMMARY")
        print("="*60)
        print(f"Total modules: {registry_data['module_count']}")
        print(f"Infrastructure modules: {len(infrastructure['infrastructure_modules'])}")
        print(f"Highly depended-on: {len(infrastructure['highly_depended_on'])}")
        
        # Show some key modules
        key_modules = ["jquery", "mediawiki.base", "oojs", "oojs-ui-core"]
        print(f"\nKey modules found:")
        for module in key_modules:
            info = parser.get_module_info(module)
            if info:
                print(f"  ✅ {module}: {info['dependency_count']} deps, {info['dependent_count']} dependents")
            else:
                print(f"  ❌ {module}: NOT FOUND")
                
    except Exception as e:
        print(f"[ERROR] Parsing failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()