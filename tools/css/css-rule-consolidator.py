#!/usr/bin/env python3
"""
CSS Rule Consolidator - Consolidates individual CSS rules with identical properties
into combined selectors to reduce rule bloat and match reference CSS efficiency.
"""

import json
import re
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Tuple, Set

class CSSRuleConsolidator:
    """Consolidates CSS rules to reduce bloat and match reference efficiency."""
    
    def __init__(self):
        self.modules_dir = Path("app/src/main/assets/styles/modules")
        self.output_dir = Path("tools/css/output")
        
    def parse_css_file(self, file_path: Path) -> List[Tuple[str, Dict[str, str]]]:
        """Parse CSS file and extract rules as (selector, properties) tuples."""
        rules = []
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
                
            # Remove comments
            content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
            
            # Find CSS rules using regex
            rule_pattern = r'([^{]+)\s*\{([^}]*)\}'
            matches = re.findall(rule_pattern, content)
            
            for selector, properties in matches:
                selector = selector.strip()
                if not selector:
                    continue
                    
                # Parse properties
                prop_dict = {}
                prop_lines = [prop.strip() for prop in properties.split(';') if prop.strip()]
                
                for prop_line in prop_lines:
                    if ':' in prop_line:
                        prop_name, prop_value = prop_line.split(':', 1)
                        prop_dict[prop_name.strip()] = prop_value.strip()
                
                if prop_dict:
                    rules.append((selector, prop_dict))
                    
        except Exception as e:
            print(f"Error parsing {file_path}: {e}")
            
        return rules
    
    def consolidate_rules(self, rules: List[Tuple[str, Dict[str, str]]]) -> List[Tuple[str, Dict[str, str]]]:
        """Consolidate rules with identical properties into combined selectors."""
        # Group rules by identical property sets
        property_groups = defaultdict(list)
        
        for selector, properties in rules:
            # Create a key from sorted properties
            prop_key = json.dumps(properties, sort_keys=True)
            property_groups[prop_key].append(selector)
        
        consolidated = []
        
        for prop_key, selectors in property_groups.items():
            properties = json.loads(prop_key)
            
            if len(selectors) > 1:
                # Consolidate multiple selectors with same properties
                # Sort selectors for consistent output
                sorted_selectors = sorted(selectors)
                combined_selector = ', '.join(sorted_selectors)
                consolidated.append((combined_selector, properties))
                
                print(f"✅ Consolidated {len(selectors)} rules into: {combined_selector[:80]}...")
            else:
                # Keep single selectors as-is
                consolidated.append((selectors[0], properties))
        
        return consolidated
    
    def format_css_output(self, rules: List[Tuple[str, Dict[str, str]]], module_name: str) -> str:
        """Format consolidated rules back into CSS."""
        css_lines = []
        css_lines.append(f"/*")
        css_lines.append(f" * {module_name.upper()} MODULE (CONSOLIDATED)")
        css_lines.append(f" * Rules consolidated for efficiency")
        css_lines.append(f" * Generated: {module_name}.css")
        css_lines.append(f" */")
        css_lines.append("")
        
        for selector, properties in rules:
            css_lines.append(f"{selector} {{")
            
            # Sort properties for consistent output
            sorted_props = sorted(properties.items())
            for prop_name, prop_value in sorted_props:
                css_lines.append(f"    {prop_name}: {prop_value};")
                
            css_lines.append("}")
            css_lines.append("")
        
        return '\n'.join(css_lines)
    
    def consolidate_module_file(self, module_file: Path) -> bool:
        """Consolidate rules in a single module file."""
        print(f"\n=== Consolidating {module_file.name} ===")
        
        # Parse existing rules
        original_rules = self.parse_css_file(module_file)
        print(f"Original rules: {len(original_rules)}")
        
        if not original_rules:
            print("No rules found, skipping")
            return True
        
        # Consolidate rules
        consolidated_rules = self.consolidate_rules(original_rules)
        print(f"Consolidated rules: {len(consolidated_rules)}")
        
        savings = len(original_rules) - len(consolidated_rules)
        if savings > 0:
            print(f"💾 Saved {savings} rules ({savings/len(original_rules)*100:.1f}% reduction)")
        else:
            print("No consolidation possible")
        
        # Write back to file
        module_name = module_file.stem
        css_output = self.format_css_output(consolidated_rules, module_name)
        
        try:
            with open(module_file, 'w', encoding='utf-8') as f:
                f.write(css_output)
            print(f"✅ Updated {module_file.name}")
            return True
        except Exception as e:
            print(f"❌ Error writing {module_file}: {e}")
            return False
    
    def consolidate_all_modules(self) -> Dict[str, int]:
        """Consolidate rules in all module files."""
        if not self.modules_dir.exists():
            print(f"❌ Modules directory not found: {self.modules_dir}")
            return {}
        
        css_files = list(self.modules_dir.glob("*.css"))
        if not css_files:
            print(f"❌ No CSS files found in {self.modules_dir}")
            return {}
        
        print(f"=== CSS RULE CONSOLIDATION ===")
        print(f"Found {len(css_files)} CSS files to consolidate")
        
        results = {}
        total_original = 0
        total_consolidated = 0
        
        for css_file in sorted(css_files):
            # Get before count
            original_rules = self.parse_css_file(css_file)
            original_count = len(original_rules)
            
            # Consolidate
            success = self.consolidate_module_file(css_file)
            
            if success:
                # Get after count
                consolidated_rules = self.parse_css_file(css_file)
                consolidated_count = len(consolidated_rules)
                
                results[css_file.name] = {
                    'original': original_count,
                    'consolidated': consolidated_count,
                    'saved': original_count - consolidated_count
                }
                
                total_original += original_count
                total_consolidated += consolidated_count
            else:
                results[css_file.name] = {'error': True}
        
        print(f"\n=== CONSOLIDATION SUMMARY ===")
        print(f"Total original rules: {total_original}")
        print(f"Total consolidated rules: {total_consolidated}")
        total_saved = total_original - total_consolidated
        print(f"Total rules saved: {total_saved}")
        
        if total_original > 0:
            reduction_percent = (total_saved / total_original) * 100
            print(f"Overall reduction: {reduction_percent:.1f}%")
        
        # Save detailed report
        self.save_consolidation_report(results, total_original, total_consolidated)
        
        return results
    
    def save_consolidation_report(self, results: Dict, total_original: int, total_consolidated: int):
        """Save detailed consolidation report."""
        report = {
            'consolidation_date': str(Path(__file__).stat().st_mtime),
            'total_original_rules': total_original,
            'total_consolidated_rules': total_consolidated,
            'total_rules_saved': total_original - total_consolidated,
            'reduction_percentage': ((total_original - total_consolidated) / total_original * 100) if total_original > 0 else 0,
            'file_results': results
        }
        
        report_file = self.output_dir / "consolidation_report.json"
        self.output_dir.mkdir(exist_ok=True)
        
        with open(report_file, 'w') as f:
            json.dump(report, f, indent=2)
            
        print(f"📄 Detailed report saved to: {report_file}")

def main():
    consolidator = CSSRuleConsolidator()
    results = consolidator.consolidate_all_modules()
    
    # Check if consolidation was successful
    errors = [f for f, r in results.items() if r.get('error')]
    if errors:
        print(f"❌ Errors in files: {', '.join(errors)}")
        return 1
    else:
        print("\n✅ CSS rule consolidation completed successfully!")
        print("\nNext steps:")
        print("1. Run CSS build: python3 tools/css/css-build.py")
        print("2. Run rule analysis: python3 tools/css/css-rule-analyzer.py") 
        print("3. Verify rule count reduction")
        return 0

if __name__ == "__main__":
    exit(main())