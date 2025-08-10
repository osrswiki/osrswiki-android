#!/usr/bin/env python3
"""
CSS Deduplicator - Removes duplicate selectors within individual CSS files
Part of the CSS modular architecture tooling suite.
"""

import os
import re
import json
import argparse
from collections import defaultdict
from typing import Dict, List, Set, Tuple
from pathlib import Path

class CSSDeduplicator:
    """Remove duplicate CSS selectors within individual files."""
    
    def __init__(self, css_dir: str = "app/src/main/assets/styles/modules"):
        self.css_dir = Path(css_dir)
        self.report_file = "tools/css/output/deduplication_report.json"
        
    def parse_css_file(self, file_path: Path) -> List[Tuple[str, List[str], int]]:
        """Parse CSS file and extract rules with their properties and line numbers."""
        rules = []
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Remove comments first
            content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
            
            # Find CSS rules using regex
            rule_pattern = r'([^{]+)\s*\{([^}]*)\}'
            matches = re.findall(rule_pattern, content)
            
            line_num = 1
            for selector, properties in matches:
                selector = selector.strip()
                if selector:
                    prop_lines = [prop.strip() for prop in properties.split(';') if prop.strip()]
                    rules.append((selector, prop_lines, line_num))
                line_num += content[:content.find(selector) + len(selector)].count('\n') + 1
                
        except Exception as e:
            print(f"Error parsing {file_path}: {e}")
            
        return rules
    
    def find_duplicates_in_file(self, file_path: Path) -> Dict[str, List[Tuple[List[str], int]]]:
        """Find duplicate selectors within a single file."""
        rules = self.parse_css_file(file_path)
        selector_occurrences = defaultdict(list)
        
        # Group rules by selector
        for selector, properties, line_num in rules:
            normalized_selector = ' '.join(selector.split())
            selector_occurrences[normalized_selector].append((properties, line_num))
        
        # Find duplicates
        duplicates = {}
        for selector, occurrences in selector_occurrences.items():
            if len(occurrences) > 1:
                duplicates[selector] = occurrences
                
        return duplicates
    
    def deduplicate_file(self, file_path: Path) -> Dict[str, any]:
        """Remove duplicates from a single CSS file."""
        duplicates = self.find_duplicates_in_file(file_path)
        
        if not duplicates:
            return {
                "file": str(file_path.name),
                "duplicates_found": 0,
                "duplicates_removed": 0,
                "rules_before": 0,
                "rules_after": 0
            }
        
        # Read original content
        with open(file_path, 'r', encoding='utf-8') as f:
            original_lines = f.readlines()
        
        rules = self.parse_css_file(file_path)
        
        # Create deduplicated CSS
        seen_selectors = set()
        deduplicated_rules = []
        duplicates_removed = 0
        
        for selector, properties, line_num in rules:
            normalized_selector = ' '.join(selector.split())
            
            if normalized_selector not in seen_selectors:
                seen_selectors.add(normalized_selector)
                # Merge properties from all occurrences of this selector
                all_properties = set()
                for dup_props, _ in duplicates.get(normalized_selector, [(properties, line_num)]):
                    all_properties.update(dup_props)
                
                # Create merged rule
                if all_properties:
                    merged_props = sorted(all_properties)
                    deduplicated_rules.append((selector, merged_props))
            else:
                duplicates_removed += 1
        
        # Write deduplicated file
        new_content = []
        new_content.append(f"/*\n * {file_path.name.upper().replace('.CSS', '')} MODULE (DEDUPLICATED)\n")
        new_content.append(f" * Removed {duplicates_removed} duplicate selectors\n")
        new_content.append(f" * Generated: {file_path.name}\n */\n\n")
        
        for selector, properties in deduplicated_rules:
            new_content.append(f"{selector} {{\n")
            for prop in properties:
                new_content.append(f"    {prop};\n")
            new_content.append("}\n\n")
        
        # Write the deduplicated file
        with open(file_path, 'w', encoding='utf-8') as f:
            f.writelines(new_content)
        
        return {
            "file": str(file_path.name),
            "duplicates_found": len(duplicates),
            "duplicates_removed": duplicates_removed,
            "rules_before": len(rules),
            "rules_after": len(deduplicated_rules),
            "duplicate_selectors": list(duplicates.keys())
        }
    
    def deduplicate_all_files(self) -> Dict[str, any]:
        """Deduplicate all CSS files in the modules directory."""
        if not self.css_dir.exists():
            raise FileNotFoundError(f"CSS directory not found: {self.css_dir}")
        
        results = {
            "deduplication_date": str(Path(__file__).stat().st_mtime),
            "directory": str(self.css_dir),
            "files_processed": [],
            "total_duplicates_found": 0,
            "total_duplicates_removed": 0,
            "total_rules_before": 0,
            "total_rules_after": 0
        }
        
        css_files = list(self.css_dir.glob("*.css"))
        css_files.sort()
        
        print(f"=== DEDUPLICATING {len(css_files)} CSS FILES ===\n")
        
        for css_file in css_files:
            if css_file.name == "extraction_report.json":
                continue
                
            print(f"Deduplicating: {css_file.name}")
            file_result = self.deduplicate_file(css_file)
            
            results["files_processed"].append(file_result)
            results["total_duplicates_found"] += file_result["duplicates_found"]
            results["total_duplicates_removed"] += file_result["duplicates_removed"] 
            results["total_rules_before"] += file_result["rules_before"]
            results["total_rules_after"] += file_result["rules_after"]
            
            if file_result["duplicates_found"] > 0:
                print(f"  ✅ Removed {file_result['duplicates_removed']} duplicates from {file_result['duplicates_found']} selectors")
                print(f"  📊 Rules: {file_result['rules_before']} → {file_result['rules_after']}")
            else:
                print(f"  ✅ No duplicates found")
            print()
        
        # Save report
        os.makedirs(Path(self.report_file).parent, exist_ok=True)
        with open(self.report_file, 'w') as f:
            json.dump(results, f, indent=2)
        
        print(f"=== DEDUPLICATION SUMMARY ===")
        print(f"Files processed: {len(results['files_processed'])}")
        print(f"Total duplicates found: {results['total_duplicates_found']}")
        print(f"Total duplicates removed: {results['total_duplicates_removed']}")
        print(f"Rules before: {results['total_rules_before']}")
        print(f"Rules after: {results['total_rules_after']}")
        print(f"Space saved: {results['total_rules_before'] - results['total_rules_after']} rules")
        print(f"📄 Report saved to: {self.report_file}")
        
        return results

def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="Remove duplicate CSS selectors from modular files")
    parser.add_argument("--css-dir", default="app/src/main/assets/styles/modules",
                       help="Directory containing CSS modules to deduplicate")
    parser.add_argument("--output", help="Output report file")
    
    args = parser.parse_args()
    
    deduplicator = CSSDeduplicator(args.css_dir)
    if args.output:
        deduplicator.report_file = args.output
    
    try:
        deduplicator.deduplicate_all_files()
        print("\n✅ Deduplication completed successfully!")
        print("\nNext steps:")
        print("1. Review the deduplicated files")
        print("2. Run duplicate detector to verify: python3 tools/css/css-duplicate-detector.py --css-dir app/src/main/assets/styles/modules/")
        print("3. Rebuild CSS: python3 tools/css/css-build.py")
        
    except Exception as e:
        print(f"❌ Error during deduplication: {e}")
        return 1
    
    return 0

if __name__ == "__main__":
    exit(main())