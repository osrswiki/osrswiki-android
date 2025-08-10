#!/usr/bin/env python3
"""
Advanced CSS Analyzer for comprehensive wiki-app CSS parity checking.
"""

import re
import json
import os
from typing import Dict, List, Set, Tuple, Any
from collections import defaultdict
import argparse

class CSSParser:
    """Advanced CSS parser that handles complex selectors and properties."""
    
    def __init__(self):
        self.css_variables = {}
        self.selectors = {}
        
    def clean_css(self, css_content: str) -> str:
        """Clean CSS content by removing comments and normalizing whitespace."""
        # Remove comments
        css_content = re.sub(r'/\*.*?\*/', '', css_content, flags=re.DOTALL)
        # Normalize whitespace
        css_content = re.sub(r'\s+', ' ', css_content)
        return css_content.strip()
    
    def extract_css_variables(self, css_content: str) -> Dict[str, str]:
        """Extract CSS variables (custom properties) and their values."""
        variables = {}
        
        # Find :root blocks and other variable definitions
        root_pattern = r':root\s*\{([^}]+)\}'
        theme_pattern = r'body\.[^{]*\{([^}]+)\}'
        
        for pattern in [root_pattern, theme_pattern]:
            matches = re.findall(pattern, css_content)
            for match in matches:
                # Extract variable definitions
                var_pattern = r'--([^:]+):\s*([^;]+);'
                for var_name, var_value in re.findall(var_pattern, match):
                    variables[f'--{var_name.strip()}'] = var_value.strip()
        
        return variables
    
    def parse_selector_block(self, selector: str, properties: str) -> Dict[str, Any]:
        """Parse a CSS selector block and extract properties."""
        prop_dict = {}
        
        # Split properties by semicolon and parse
        properties = properties.strip()
        if not properties:
            return prop_dict
            
        # Handle properties that might span multiple lines or contain semicolons in values
        prop_pattern = r'([^:]+):\s*([^;]+);'
        for prop_match in re.findall(prop_pattern, properties + ';'):
            prop_name = prop_match[0].strip()
            prop_value = prop_match[1].strip()
            
            if prop_name and prop_value:
                prop_dict[prop_name] = prop_value
        
        return prop_dict
    
    def parse_css(self, css_content: str) -> Dict[str, Any]:
        """Parse CSS content and extract all selectors with their properties."""
        css_content = self.clean_css(css_content)
        
        # Extract CSS variables first
        self.css_variables = self.extract_css_variables(css_content)
        
        # Parse CSS rules (handle nested blocks, media queries, etc.)
        result = {
            'variables': self.css_variables,
            'selectors': {},
            'media_queries': {},
            'keyframes': {},
        }
        
        # Remove @keyframes and other at-rules for simpler parsing
        css_content = re.sub(r'@keyframes[^{]*\{(?:[^{}]*\{[^}]*\})*[^}]*\}', '', css_content)
        
        # Handle media queries separately
        media_pattern = r'@media\s+([^{]+)\{([^{}]*(?:\{[^}]*\}[^{}]*)*)\}'
        media_matches = re.findall(media_pattern, css_content)
        
        for media_query, media_content in media_matches:
            result['media_queries'][media_query.strip()] = self.parse_selectors(media_content)
        
        # Remove media queries from main content
        css_content = re.sub(media_pattern, '', css_content)
        
        # Parse main selectors
        result['selectors'] = self.parse_selectors(css_content)
        
        return result
    
    def parse_selectors(self, css_content: str) -> Dict[str, Dict[str, str]]:
        """Parse CSS selectors and their properties."""
        selectors = {}
        
        # Match CSS rules: selector { properties }
        rule_pattern = r'([^{}]+)\{([^{}]+)\}'
        
        for match in re.finditer(rule_pattern, css_content):
            selector_group = match.group(1).strip()
            properties = match.group(2).strip()
            
            # Handle multiple selectors separated by commas
            individual_selectors = [s.strip() for s in selector_group.split(',')]
            
            for selector in individual_selectors:
                if selector and not selector.startswith('@'):
                    parsed_props = self.parse_selector_block(selector, properties)
                    if parsed_props:
                        selectors[selector] = parsed_props
        
        return selectors
    
    def categorize_selector(self, selector: str) -> str:
        """Categorize a CSS selector by type for prioritization."""
        selector = selector.lower()
        
        # MediaWiki admin/editor specific
        if any(term in selector for term in ['#ca-', '.mw-edit', '.oo-ui', '.ve-', '#mw-', '.wikiEditor']):
            return 'mediawiki-admin'
        
        # Navigation and UI chrome
        if any(term in selector for term in ['#p-', '.vector-', '#mw-navigation', '.mw-footer']):
            return 'ui-chrome'
        
        # Content rendering (high priority)
        if any(term in selector for term in ['.wikitable', '.infobox', '.navbox', '.messagebox', '.hatnote']):
            return 'content-high'
            
        # Text and typography
        if any(term in selector for term in ['h1', 'h2', 'h3', 'p', '.mw-content', '.mw-parser-output']):
            return 'content-high'
        
        # Table-related (medium-high priority)
        if any(term in selector for term in ['.table-', 'table', 'th', 'td', 'tr']):
            return 'content-medium'
        
        # Forms and interactive elements
        if any(term in selector for term in ['input', 'button', 'form', '.cdx-']):
            return 'interactive'
        
        # Print and accessibility (low priority for mobile app)
        if any(term in selector for term in ['@media print', '.printonly', '.screen-reader']):
            return 'print-accessibility'
        
        return 'other'

class CSSComparator:
    """Compare CSS between wiki and app to identify gaps."""
    
    def __init__(self):
        self.wiki_css = {}
        self.app_css = {}
        
    def load_wiki_css(self, css_content: str):
        """Load and parse wiki CSS."""
        parser = CSSParser()
        self.wiki_css = parser.parse_css(css_content)
    
    def load_app_css_files(self, css_directory: str):
        """Load and parse all app CSS files."""
        parser = CSSParser()
        combined_css = ""
        
        css_files = []
        for root, dirs, files in os.walk(css_directory):
            for file in files:
                if file.endswith('.css'):
                    css_files.append(os.path.join(root, file))
        
        for css_file in css_files:
            try:
                with open(css_file, 'r', encoding='utf-8') as f:
                    combined_css += f"/* From {css_file} */\n" + f.read() + "\n"
            except Exception as e:
                print(f"Error reading {css_file}: {e}")
        
        self.app_css = parser.parse_css(combined_css)
    
    def find_missing_selectors(self) -> Dict[str, Any]:
        """Find selectors present in wiki CSS but missing from app CSS."""
        missing = {}
        
        wiki_selectors = self.wiki_css.get('selectors', {})
        app_selectors = self.app_css.get('selectors', {})
        
        for selector, properties in wiki_selectors.items():
            if selector not in app_selectors:
                parser = CSSParser()
                category = parser.categorize_selector(selector)
                
                if category not in missing:
                    missing[category] = {}
                
                missing[category][selector] = {
                    'properties': properties,
                    'status': 'missing_entirely'
                }
        
        return missing
    
    def find_incomplete_selectors(self) -> Dict[str, Any]:
        """Find selectors that exist but have missing properties."""
        incomplete = {}
        
        wiki_selectors = self.wiki_css.get('selectors', {})
        app_selectors = self.app_css.get('selectors', {})
        
        for selector, wiki_props in wiki_selectors.items():
            if selector in app_selectors:
                app_props = app_selectors[selector]
                missing_props = {}
                
                for prop_name, prop_value in wiki_props.items():
                    if prop_name not in app_props:
                        missing_props[prop_name] = prop_value
                    elif app_props[prop_name] != prop_value:
                        missing_props[prop_name] = {
                            'wiki': prop_value,
                            'app': app_props[prop_name]
                        }
                
                if missing_props:
                    parser = CSSParser()
                    category = parser.categorize_selector(selector)
                    
                    if category not in incomplete:
                        incomplete[category] = {}
                    
                    incomplete[category][selector] = {
                        'missing_properties': missing_props,
                        'status': 'incomplete'
                    }
        
        return incomplete
    
    def generate_analysis_report(self) -> Dict[str, Any]:
        """Generate comprehensive analysis report."""
        missing = self.find_missing_selectors()
        incomplete = self.find_incomplete_selectors()
        
        # Count statistics
        stats = {
            'wiki_total_selectors': len(self.wiki_css.get('selectors', {})),
            'app_total_selectors': len(self.app_css.get('selectors', {})),
            'missing_by_category': {cat: len(selectors) for cat, selectors in missing.items()},
            'incomplete_by_category': {cat: len(selectors) for cat, selectors in incomplete.items()}
        }
        
        return {
            'statistics': stats,
            'missing_selectors': missing,
            'incomplete_selectors': incomplete,
            'wiki_variables': self.wiki_css.get('variables', {}),
            'app_variables': self.app_css.get('variables', {})
        }

def main():
    parser = argparse.ArgumentParser(description='Analyze CSS differences between wiki and app')
    parser.add_argument('--wiki-css', required=True, help='Path to wiki CSS file')
    parser.add_argument('--app-css-dir', required=True, help='Path to app CSS directory')
    parser.add_argument('--output', default='css_analysis_report.json', help='Output JSON report file')
    
    args = parser.parse_args()
    
    # Initialize comparator
    comparator = CSSComparator()
    
    # Load wiki CSS
    with open(args.wiki_css, 'r', encoding='utf-8') as f:
        wiki_content = f.read()
    comparator.load_wiki_css(wiki_content)
    
    # Load app CSS
    comparator.load_app_css_files(args.app_css_dir)
    
    # Generate analysis
    report = comparator.generate_analysis_report()
    
    # Save report
    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    
    # Print summary
    stats = report['statistics']
    print(f"CSS Analysis Complete!")
    print(f"Wiki selectors: {stats['wiki_total_selectors']}")
    print(f"App selectors: {stats['app_total_selectors']}")
    print(f"Missing by category: {stats['missing_by_category']}")
    print(f"Incomplete by category: {stats['incomplete_by_category']}")
    print(f"Report saved to: {args.output}")

if __name__ == '__main__':
    main()