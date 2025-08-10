#!/usr/bin/env python3
"""
CSS Inflation Analyzer - Identifies specific sources of rule bloat
Examines build process artifacts to determine where extra rules originate.
"""

import json
import re
from collections import defaultdict, Counter
from pathlib import Path

class CSSInflationAnalyzer:
    """Analyzes CSS rule inflation patterns and sources."""
    
    def __init__(self):
        self.extra_rules = []
        self.modified_rules = []
        self.missing_rules = []
        self.inflation_patterns = {}
        
    def load_analysis_data(self):
        """Load the rule analysis data."""
        analysis_file = "tools/css/output/css_rule_analysis.json"
        
        with open(analysis_file, 'r') as f:
            data = json.load(f)
            
        self.extra_rules = data.get('only_in_local', [])
        self.missing_rules = data.get('only_in_reference', [])
        self.modified_rules = data.get('different_properties', [])
        
        return data
        
    def analyze_extra_rule_patterns(self):
        """Analyze patterns in extra rules to identify their source."""
        patterns = {
            'state_variants': {
                'rules': [],
                'description': 'Rules with :hover, :active, :focus, :visited states'
            },
            'utility_classes': {
                'rules': [],
                'description': 'Generated utility classes (align-, bg-, text-)'
            },
            'user_specific': {
                'rules': [],  
                'description': 'User-specific styling rules'
            },
            'property_expansions': {
                'rules': [],
                'description': 'Expanded shorthand properties'
            },
            'ooui_variants': {
                'rules': [],
                'description': 'OOUI widget state variations'
            },
            'synthetic_selectors': {
                'rules': [],
                'description': 'Artificially generated selectors'
            },
            'unknown': {
                'rules': [],
                'description': 'Unknown source rules'
            }
        }
        
        for rule in self.extra_rules:
            categorized = False
            
            # Check for state variants
            if any(state in rule for state in [':hover', ':active', ':focus', ':visited', ':checked', ':indeterminate']):
                patterns['state_variants']['rules'].append(rule)
                categorized = True
                
            # Check for utility classes
            elif any(util in rule for util in ['align-', 'table.align-', 'bg-', 'text-']):
                patterns['utility_classes']['rules'].append(rule)
                categorized = True
                
            # Check for user-specific rules
            elif 'User:' in rule or '/w/User:' in rule:
                patterns['user_specific']['rules'].append(rule)
                categorized = True
                
            # Check for OOUI variants
            elif '.oo-ui-' in rule and any(state in rule for state in ['enabled', 'disabled', 'highlighted', 'selected']):
                patterns['ooui_variants']['rules'].append(rule)
                categorized = True
                
            # Check for synthetic selectors (generated combinations)
            elif '+' in rule or '>' in rule or '~' in rule or rule.count('.') > 3:
                patterns['synthetic_selectors']['rules'].append(rule)
                categorized = True
                
            if not categorized:
                patterns['unknown']['rules'].append(rule)
        
        self.inflation_patterns = patterns
        return patterns
    
    def analyze_property_modifications(self):
        """Analyze how properties were modified in existing rules."""
        modification_patterns = {
            'property_additions': [],
            'property_changes': [],
            'value_expansions': []
        }
        
        for rule in self.modified_rules:
            selector = rule['selector']
            ref_props = rule.get('reference_properties', {})
            local_props = rule.get('local_properties', {})
            
            # Handle multiple local rules case
            if 'local_rules' in rule:
                local_props = rule['local_rules'][0] if rule['local_rules'] else {}
            
            # Find added properties
            added_props = set(local_props.keys()) - set(ref_props.keys())
            if added_props:
                modification_patterns['property_additions'].append({
                    'selector': selector,
                    'added_properties': list(added_props),
                    'added_values': {prop: local_props[prop] for prop in added_props}
                })
            
            # Find changed properties
            for prop in ref_props.keys():
                if prop in local_props and ref_props[prop] != local_props[prop]:
                    modification_patterns['property_changes'].append({
                        'selector': selector,
                        'property': prop,
                        'reference_value': ref_props[prop],
                        'local_value': local_props[prop]
                    })
        
        return modification_patterns
    
    def examine_module_contribution(self):
        """Examine which CSS modules contribute most to rule inflation."""
        module_dir = Path("app/src/main/assets/styles/modules")
        module_contributions = {}
        
        for css_file in module_dir.glob("*.css"):
            module_name = css_file.stem
            
            try:
                with open(css_file, 'r') as f:
                    content = f.read()
                
                # Count rules in this module
                rule_count = len(re.findall(r'\{[^}]*\}', content))
                
                # Check for synthetic patterns
                synthetic_count = 0
                if 'nth-of-type' in content:
                    synthetic_count += len(re.findall(r'nth-of-type\(\d+\)', content))
                
                if ':hover' in content or ':active' in content or ':focus' in content:
                    synthetic_count += len(re.findall(r':(hover|active|focus)', content))
                
                module_contributions[module_name] = {
                    'total_rules': rule_count,
                    'synthetic_rules': synthetic_count,
                    'synthetic_ratio': synthetic_count / rule_count if rule_count > 0 else 0
                }
                
            except Exception as e:
                print(f"Error reading {css_file}: {e}")
        
        return module_contributions
    
    def identify_build_process_issues(self):
        """Identify which build process steps are causing rule inflation."""
        issues = []
        
        # Check for utility class generation
        utility_count = len(self.inflation_patterns.get('utility_classes', {}).get('rules', []))
        if utility_count > 50:  # Arbitrary threshold
            issues.append({
                'issue': 'excessive_utility_generation',
                'description': f'{utility_count} utility classes generated that don\'t exist in reference',
                'severity': 'high',
                'likely_source': 'css-generator.py or css-integrator.py'
            })
        
        # Check for state variant explosion
        state_count = len(self.inflation_patterns.get('state_variants', {}).get('rules', []))
        if state_count > 100:
            issues.append({
                'issue': 'state_variant_explosion', 
                'description': f'{state_count} state variants generated beyond reference',
                'severity': 'high',
                'likely_source': 'Property expansion in CSS generation'
            })
        
        # Check for OOUI bloat
        ooui_count = len(self.inflation_patterns.get('ooui_variants', {}).get('rules', []))
        if ooui_count > 30:
            issues.append({
                'issue': 'ooui_variant_bloat',
                'description': f'{ooui_count} OOUI variants not in reference',
                'severity': 'medium',
                'likely_source': 'OOUI widget processing in CSS generator'
            })
        
        return issues
    
    def generate_comprehensive_report(self):
        """Generate a comprehensive inflation analysis report."""
        data = self.load_analysis_data()
        patterns = self.analyze_extra_rule_patterns()
        modifications = self.analyze_property_modifications()  
        modules = self.examine_module_contribution()
        issues = self.identify_build_process_issues()
        
        report = {
            'executive_summary': {
                'total_extra_rules': len(self.extra_rules),
                'total_missing_rules': len(self.missing_rules),
                'total_modified_rules': len(self.modified_rules),
                'inflation_rate': f"{(len(self.extra_rules) / 1023) * 100:.1f}%",
                'coverage_accuracy': f"{(643 / 1023) * 100:.1f}%"  # Identical rules
            },
            'inflation_patterns': patterns,
            'property_modifications': modifications,
            'module_contributions': modules,
            'build_process_issues': issues,
            'recommendations': self.generate_recommendations(issues, patterns)
        }
        
        return report
    
    def generate_recommendations(self, issues, patterns):
        """Generate specific recommendations to fix rule inflation."""
        recommendations = []
        
        # Utility class recommendations
        utility_count = len(patterns.get('utility_classes', {}).get('rules', []))
        if utility_count > 20:
            recommendations.append({
                'priority': 'HIGH',
                'issue': 'Utility class over-generation',
                'action': 'Review css-generator.py utility class generation logic',
                'details': f'Remove {utility_count} auto-generated utility classes not in reference'
            })
        
        # State variant recommendations  
        state_count = len(patterns.get('state_variants', {}).get('rules', []))
        if state_count > 50:
            recommendations.append({
                'priority': 'HIGH', 
                'issue': 'Excessive state variants',
                'action': 'Limit state variant generation to reference CSS only',
                'details': f'Remove {state_count} auto-generated state variants'
            })
        
        # Property expansion recommendations
        if len(self.modified_rules) > 50:
            recommendations.append({
                'priority': 'MEDIUM',
                'issue': 'Property expansion bloat',
                'action': 'Review property expansion logic in build process',
                'details': 'Limit property additions to maintain reference parity'
            })
        
        return recommendations
    
    def save_report(self, output_file: str):
        """Save the comprehensive analysis report."""
        report = self.generate_comprehensive_report()
        
        with open(output_file, 'w') as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        
        print(f"Comprehensive inflation analysis saved to: {output_file}")
        return report
    
    def print_executive_summary(self, report):
        """Print a concise executive summary."""
        summary = report['executive_summary']
        
        print(f"\n=== CSS RULE INFLATION ANALYSIS ===")
        print(f"Rule inflation rate: {summary['inflation_rate']}")
        print(f"Coverage accuracy: {summary['coverage_accuracy']}")
        print(f"Extra rules: {summary['total_extra_rules']}")
        print(f"Missing rules: {summary['total_missing_rules']}")
        print(f"Modified rules: {summary['total_modified_rules']}")
        
        print(f"\n=== TOP INFLATION SOURCES ===")
        patterns = report['inflation_patterns']
        for pattern_name, pattern_data in patterns.items():
            rule_count = len(pattern_data['rules'])
            if rule_count > 0:
                print(f"{pattern_name.title()}: {rule_count} rules")
                print(f"  {pattern_data['description']}")
        
        print(f"\n=== BUILD PROCESS ISSUES ===")
        for issue in report['build_process_issues']:
            print(f"[{issue['severity'].upper()}] {issue['issue']}")
            print(f"  {issue['description']}")
            print(f"  Likely source: {issue['likely_source']}")
        
        print(f"\n=== RECOMMENDATIONS ===")
        for rec in report['recommendations']:
            print(f"[{rec['priority']}] {rec['issue']}")
            print(f"  Action: {rec['action']}")
            print(f"  Details: {rec['details']}")

def main():
    analyzer = CSSInflationAnalyzer()
    report = analyzer.save_report("tools/css/output/css_inflation_analysis.json")
    analyzer.print_executive_summary(report)
    
    return 0

if __name__ == "__main__":
    exit(main())