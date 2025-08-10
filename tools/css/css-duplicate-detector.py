#!/usr/bin/env python3
"""
CSS Duplicate Detector
Detects duplicate CSS selectors across multiple CSS files to prevent conflicts.
"""

import re
import os
import json
from collections import defaultdict
from typing import Dict, List, Tuple, Set
import argparse
from pathlib import Path

class CSSDuplicateDetector:
    """Detect and analyze CSS selector duplicates across files."""
    
    def __init__(self, css_dir: str = "app/src/main/assets/styles"):
        self.css_dir = Path(css_dir)
        self.duplicates = defaultdict(list)
        self.all_selectors = {}
        
    def normalize_selector(self, selector: str) -> str:
        """Normalize a CSS selector for comparison."""
        # Remove extra whitespace and normalize
        return ' '.join(selector.strip().split())
    
    def extract_selectors_from_file(self, file_path: Path) -> List[Tuple[str, int]]:
        """Extract all CSS selectors from a file with line numbers."""
        selectors = []
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
                
            # Remove comments first
            content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
            
            # Find all CSS rules
            lines = content.split('\n')
            in_rule = False
            current_selector = ""
            brace_count = 0
            
            for line_num, line in enumerate(lines, 1):
                line = line.strip()
                if not line:
                    continue
                    
                # Count braces to track rule boundaries
                open_braces = line.count('{')
                close_braces = line.count('}')
                
                if not in_rule and open_braces > 0:
                    # Start of a new rule
                    selector_part = line.split('{')[0].strip()
                    if current_selector:
                        current_selector += " " + selector_part
                    else:
                        current_selector = selector_part
                    
                    if current_selector.strip():
                        normalized = self.normalize_selector(current_selector)
                        selectors.append((normalized, line_num))
                    
                    current_selector = ""
                    in_rule = True
                    brace_count = open_braces - close_braces
                    
                elif not in_rule:
                    # Continuing a selector (multi-line)
                    if current_selector:
                        current_selector += " " + line
                    else:
                        current_selector = line
                        
                elif in_rule:
                    # Inside a rule, update brace count
                    brace_count += open_braces - close_braces
                    if brace_count <= 0:
                        in_rule = False
                        
        except Exception as e:
            print(f"Error reading {file_path}: {e}")
            
        return selectors
    
    def scan_css_files(self) -> Dict[str, Dict[str, List[Tuple[str, int]]]]:
        """Scan all CSS files and return selector information."""
        file_selectors = {}
        
        if not self.css_dir.exists():
            print(f"CSS directory not found: {self.css_dir}")
            return file_selectors
            
        # Find all CSS files
        css_files = list(self.css_dir.glob("**/*.css"))
        
        for css_file in css_files:
            relative_path = css_file.relative_to(self.css_dir)
            print(f"Scanning: {relative_path}")
            
            selectors = self.extract_selectors_from_file(css_file)
            file_selectors[str(relative_path)] = {}
            
            for selector, line_num in selectors:
                if selector not in file_selectors[str(relative_path)]:
                    file_selectors[str(relative_path)][selector] = []
                file_selectors[str(relative_path)][selector].append(line_num)
                
                # Track across all files
                if selector not in self.all_selectors:
                    self.all_selectors[selector] = []
                self.all_selectors[selector].append((str(relative_path), line_num))
                
        return file_selectors
    
    def find_duplicates(self) -> Dict[str, List[Tuple[str, List[int]]]]:
        """Find selectors that appear multiple times."""
        duplicates = {}
        
        for selector, locations in self.all_selectors.items():
            if len(locations) > 1:
                # Group by file
                file_occurrences = defaultdict(list)
                for file_path, line_num in locations:
                    file_occurrences[file_path].append(line_num)
                
                duplicates[selector] = [(file, lines) for file, lines in file_occurrences.items()]
                
        return duplicates
    
    def generate_report(self, output_file: str = "tools/css/output/duplicate_report.json"):
        """Generate a comprehensive duplicate report."""
        file_selectors = self.scan_css_files()
        duplicates = self.find_duplicates()
        
        report = {
            "scan_date": __import__('datetime').datetime.now().isoformat(),
            "summary": {
                "total_files_scanned": len(file_selectors),
                "total_unique_selectors": len(self.all_selectors),
                "total_duplicate_selectors": len(duplicates),
                "total_selector_instances": sum(len(locations) for locations in self.all_selectors.values())
            },
            "files_scanned": list(file_selectors.keys()),
            "duplicates": {},
            "file_details": {}
        }
        
        # Add duplicate details
        for selector, locations in duplicates.items():
            report["duplicates"][selector] = {
                "occurrences": len(sum([lines for _, lines in locations], [])),
                "files": {}
            }
            
            for file_path, line_numbers in locations:
                report["duplicates"][selector]["files"][file_path] = {
                    "lines": line_numbers,
                    "count": len(line_numbers)
                }
        
        # Add per-file statistics
        for file_path, selectors in file_selectors.items():
            total_selectors = sum(len(lines) for lines in selectors.values())
            unique_selectors = len(selectors)
            duplicate_count = sum(1 for selector in selectors.keys() if selector in duplicates)
            
            report["file_details"][file_path] = {
                "total_selectors": total_selectors,
                "unique_selectors": unique_selectors,
                "duplicate_selectors": duplicate_count,
                "duplication_rate": round(duplicate_count / unique_selectors * 100, 2) if unique_selectors > 0 else 0
            }
        
        # Save report
        os.makedirs(os.path.dirname(output_file), exist_ok=True)
        with open(output_file, 'w') as f:
            json.dump(report, f, indent=2)
            
        return report
    
    def print_summary(self, report: Dict):
        """Print a human-readable summary."""
        print("\n=== CSS DUPLICATE DETECTION SUMMARY ===")
        summary = report["summary"]
        
        print(f"Files scanned: {summary['total_files_scanned']}")
        print(f"Unique selectors: {summary['total_unique_selectors']}")
        print(f"Duplicate selectors: {summary['total_duplicate_selectors']}")
        print(f"Total selector instances: {summary['total_selector_instances']}")
        
        if summary['total_duplicate_selectors'] > 0:
            duplication_rate = round(summary['total_duplicate_selectors'] / summary['total_unique_selectors'] * 100, 2)
            print(f"Overall duplication rate: {duplication_rate}%")
            
            print(f"\n=== TOP 10 MOST DUPLICATED SELECTORS ===")
            sorted_duplicates = sorted(
                report["duplicates"].items(), 
                key=lambda x: x[1]["occurrences"], 
                reverse=True
            )[:10]
            
            for selector, info in sorted_duplicates:
                print(f"{info['occurrences']}x: {selector}")
                for file_path, file_info in info["files"].items():
                    lines = ", ".join(map(str, file_info["lines"]))
                    print(f"    {file_path}: lines {lines}")
                print()
                
            print(f"\n=== FILES WITH HIGHEST DUPLICATION ===")
            sorted_files = sorted(
                report["file_details"].items(),
                key=lambda x: x[1]["duplication_rate"],
                reverse=True
            )[:5]
            
            for file_path, details in sorted_files:
                print(f"{file_path}: {details['duplication_rate']}% ({details['duplicate_selectors']}/{details['unique_selectors']})")
        else:
            print("✅ No duplicate selectors found!")

def main():
    parser = argparse.ArgumentParser(description="Detect CSS selector duplicates")
    parser.add_argument("--css-dir", default="app/src/main/assets/styles", 
                       help="Directory containing CSS files")
    parser.add_argument("--output", default="tools/css/output/duplicate_report.json",
                       help="Output file for detailed report")
    parser.add_argument("--quiet", action="store_true",
                       help="Only show summary, no detailed output")
    
    args = parser.parse_args()
    
    detector = CSSDuplicateDetector(args.css_dir)
    report = detector.generate_report(args.output)
    
    if not args.quiet:
        detector.print_summary(report)
        print(f"\n📄 Detailed report saved to: {args.output}")

if __name__ == "__main__":
    main()