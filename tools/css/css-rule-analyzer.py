#!/usr/bin/env python3
"""
CSS Rule Analyzer - Comprehensive comparison between reference and local CSS
Analyzes rule counts, differences, and identifies sources of rule inflation.
"""

import re
import json
import os
from collections import defaultdict, Counter
from typing import Dict, List, Tuple, Set
from pathlib import Path
from dataclasses import dataclass, asdict

@dataclass
class CSSRule:
    """Represents a single CSS rule with its selector and properties."""
    selector: str
    properties: Dict[str, str]
    raw_css: str
    source_file: str = ""
    line_number: int = 0
    
    def __hash__(self):
        return hash((self.selector, tuple(sorted(self.properties.items()))))
    
    def __eq__(self, other):
        return (self.selector == other.selector and 
                self.properties == other.properties)

class CSSRuleAnalyzer:
    """Analyzes CSS rules to identify differences and inflation sources."""
    
    def __init__(self):
        self.reference_rules = []
        self.local_rules = []
        self.analysis_report = {}
        
    def parse_css_file(self, file_path: str, source_name: str = "") -> List[CSSRule]:
        """Parse CSS file and extract all rules."""
        rules = []
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
                
            # Remove comments first
            content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
            
            # Split into individual rules using a more robust regex
            rule_pattern = r'([^{}]+)\s*\{([^{}]*)\}'
            matches = re.finditer(rule_pattern, content)
            
            for match in matches:
                selector = match.group(1).strip()
                properties_block = match.group(2).strip()
                
                if not selector or selector.startswith('@'):
                    continue  # Skip @-rules for now
                
                # Parse properties
                properties = {}
                if properties_block:
                    prop_pattern = r'([^:;]+):\s*([^;]+)(?:;|$)'
                    prop_matches = re.finditer(prop_pattern, properties_block)
                    
                    for prop_match in prop_matches:
                        prop_name = prop_match.group(1).strip()
                        prop_value = prop_match.group(2).strip()
                        if prop_name and prop_value:
                            properties[prop_name] = prop_value
                
                # Create rule object
                rule = CSSRule(
                    selector=self.normalize_selector(selector),
                    properties=properties,
                    raw_css=match.group(0),
                    source_file=source_name,
                    line_number=content[:match.start()].count('\n') + 1
                )
                
                if rule.properties:  # Only add rules that have properties
                    rules.append(rule)
                    
        except Exception as e:
            print(f"Error parsing {file_path}: {e}")
            
        return rules
    
    def normalize_selector(self, selector: str) -> str:
        """Normalize a CSS selector for comparison."""
        # Remove extra whitespace and normalize
        normalized = ' '.join(selector.split())
        # Sort multiple selectors consistently
        if ',' in normalized:
            selectors = [s.strip() for s in normalized.split(',')]
            normalized = ', '.join(sorted(selectors))
        return normalized
    
    def analyze_files(self, reference_file: str, local_file: str):
        """Perform comprehensive analysis of two CSS files."""
        print(f"Parsing reference CSS: {reference_file}")
        self.reference_rules = self.parse_css_file(reference_file, "reference")
        
        print(f"Parsing local CSS: {local_file}")  
        self.local_rules = self.parse_css_file(local_file, "local")
        
        print(f"\nRaw counts:")
        print(f"  Reference rules: {len(self.reference_rules)}")
        print(f"  Local rules: {len(self.local_rules)}")
        print(f"  Difference: {len(self.local_rules) - len(self.reference_rules)} extra local rules")
        
        # Create comparison analysis
        self.create_comparison_analysis()
        
    def create_comparison_analysis(self):
        """Create detailed comparison analysis."""
        # Create lookup sets for efficient comparison
        reference_selectors = {rule.selector for rule in self.reference_rules}
        local_selectors = {rule.selector for rule in self.local_rules}
        
        reference_rules_by_selector = {rule.selector: rule for rule in self.reference_rules}
        local_rules_by_selector = defaultdict(list)
        
        for rule in self.local_rules:
            local_rules_by_selector[rule.selector].append(rule)
        
        # Find differences
        only_in_local = local_selectors - reference_selectors
        only_in_reference = reference_selectors - local_selectors  
        in_both = local_selectors & reference_selectors
        
        # Analyze rules that exist in both
        different_properties = []
        identical_rules = []
        
        for selector in in_both:
            ref_rule = reference_rules_by_selector[selector]
            local_rule_list = local_rules_by_selector[selector]
            
            # Check if local has multiple rules for same selector
            if len(local_rule_list) > 1:
                different_properties.append({
                    'selector': selector,
                    'issue': 'multiple_local_rules',
                    'reference_properties': ref_rule.properties,
                    'local_rules': [rule.properties for rule in local_rule_list]
                })
            else:
                local_rule = local_rule_list[0]
                if ref_rule.properties != local_rule.properties:
                    different_properties.append({
                        'selector': selector,
                        'issue': 'property_difference',
                        'reference_properties': ref_rule.properties,
                        'local_properties': local_rule.properties
                    })
                else:
                    identical_rules.append(selector)
        
        # Analyze extra local rules
        extra_rule_analysis = self.analyze_extra_rules(only_in_local, local_rules_by_selector)
        
        # Store analysis results
        self.analysis_report = {
            'summary': {
                'reference_rule_count': len(self.reference_rules),
                'local_rule_count': len(self.local_rules),
                'extra_local_rules': len(only_in_local),
                'missing_local_rules': len(only_in_reference),
                'rules_in_both': len(in_both),
                'identical_rules': len(identical_rules),
                'modified_rules': len(different_properties)
            },
            'only_in_local': list(only_in_local),  # All extra rules
            'only_in_reference': list(only_in_reference),  # All missing rules
            'different_properties': different_properties[:10],  # Sample of differences
            'extra_rule_patterns': extra_rule_analysis
        }
    
    def analyze_extra_rules(self, extra_selectors: Set[str], local_rules_by_selector: Dict) -> Dict:
        """Analyze patterns in extra local rules to identify their source."""
        patterns = {
            'vendor_prefixes': [],
            'property_expansions': [], 
            'theme_variants': [],
            'responsive_additions': [],
            'utility_classes': [],
            'unknown': []
        }
        
        for selector in extra_selectors:
            rules = local_rules_by_selector[selector]
            rule = rules[0]  # Take first rule for analysis
            
            # Check for vendor prefixes
            if any(prop.startswith(('-webkit-', '-moz-', '-ms-', '-o-')) 
                   for prop in rule.properties.keys()):
                patterns['vendor_prefixes'].append(selector)
                
            # Check for CSS variable usage (theme variants)
            elif any('var(' in val for val in rule.properties.values()):
                patterns['theme_variants'].append(selector)
                
            # Check for media query selectors (responsive)
            elif '@media' in rule.raw_css:
                patterns['responsive_additions'].append(selector)
                
            # Check for utility classes
            elif any(keyword in selector.lower() for keyword in 
                    ['hover', 'focus', 'active', 'before', 'after', 'nth-']):
                patterns['utility_classes'].append(selector)
                
            else:
                patterns['unknown'].append(selector)
        
        # Limit samples to prevent huge output
        for key in patterns:
            patterns[key] = patterns[key][:10]
            
        return patterns
    
    def save_report(self, output_file: str):
        """Save analysis report to JSON file."""
        os.makedirs(os.path.dirname(output_file), exist_ok=True)
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(self.analysis_report, f, indent=2, ensure_ascii=False)
            
        print(f"\nDetailed analysis saved to: {output_file}")
    
    def print_summary(self):
        """Print human-readable summary."""
        report = self.analysis_report
        summary = report['summary']
        
        print(f"\n=== CSS RULE ANALYSIS SUMMARY ===")
        print(f"Reference CSS rules: {summary['reference_rule_count']}")
        print(f"Local CSS rules: {summary['local_rule_count']}")
        print(f"Coverage ratio: {summary['local_rule_count']/summary['reference_rule_count']*100:.1f}%")
        
        print(f"\n=== RULE DIFFERENCES ===")
        print(f"Extra local rules: {summary['extra_local_rules']}")
        print(f"Missing local rules: {summary['missing_local_rules']}")
        print(f"Rules in both: {summary['rules_in_both']}")
        print(f"  - Identical: {summary['identical_rules']}")
        print(f"  - Modified: {summary['modified_rules']}")
        
        print(f"\n=== EXTRA RULE PATTERNS ===")
        patterns = report['extra_rule_patterns']
        for pattern_type, selectors in patterns.items():
            if selectors:
                print(f"{pattern_type.title()}: {len(selectors)} rules")
                for selector in selectors[:3]:  # Show first 3 examples
                    print(f"  - {selector}")
                if len(selectors) > 3:
                    print(f"  ... and {len(selectors)-3} more")

def main():
    analyzer = CSSRuleAnalyzer()
    
    # Analyze the files
    reference_file = "reference_wiki.css"
    local_file = "app/src/main/assets/styles/wiki-integration.css"
    
    if not os.path.exists(reference_file):
        print(f"Error: Reference file {reference_file} not found")
        return 1
        
    if not os.path.exists(local_file):
        print(f"Error: Local file {local_file} not found") 
        return 1
    
    analyzer.analyze_files(reference_file, local_file)
    analyzer.print_summary()
    analyzer.save_report("tools/css/output/css_rule_analysis.json")
    
    return 0

if __name__ == "__main__":
    exit(main())