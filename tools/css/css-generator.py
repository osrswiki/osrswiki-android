#!/usr/bin/env python3
"""
CSS Generation Tool for Missing Wiki Selectors
Generates properly formatted CSS from rule analysis results for 100% coverage.
"""

import json
import os
import sys
import re
from typing import Dict, List, Any, Set
from pathlib import Path

class CSSGenerator:
    """Generate CSS from rule analysis to achieve 100% coverage."""
    
    def __init__(self, analysis_file: str = "tools/css/output/css_rule_analysis.json"):
        self.analysis_file = analysis_file
        self.output_file = "tools/css/output/generated_missing_css.css"
        self.reference_css_file = "reference_wiki.css"
        
        # Module categories for smart assignment
        self.module_categories = {
            "tables": ["wikitable", "table-", "droptable", "pollquestion", "pollbox", "archivepoll"],
            "forms": ["input", "button", "form", "fieldset", "textarea", "cdx-", "oo-ui", "checkbox", "radio", "dropdown"],
            "messagebox": ["messagebox", "errorbox", "warningbox", "successbox", "usermessage", "cdx-message"],
            "media": ["thumb", "img", "gallery", "filehistory", "mw-mmv", "extimage", "file", "noted-item"],
            "navigation": ["navbox", "nav-", "menu", "tabbernav", "mainpage-", "tile-"],
            "layout": ["container", "content", "mw-body", "tile", "header", "footer"],
            "gaming": ["quest", "skill-", "combat", "item-", "infobox-room", "equipment", "inventory", "runepoucht"],
            "mediawiki": ["mw-", "smw-", "diff-", "echo-", "rcfilters"],
            "interactive": [":hover", ":focus", ":active", "oo-ui-", "popup", "tooltip"],
            "other": []  # Catch-all
        }
        
        # Only exclude true admin-only features - NO user-facing filtering
        self.admin_only_patterns = [
            r'^\.ve-ui-',  # Visual Editor (admin editing interface)
            r'^#ca-(edit|history|delete|move|protect)',  # Admin action tabs
        ]
        
    def is_admin_only(self, selector: str) -> bool:
        """Check if selector is truly admin-only (very restrictive)."""
        for pattern in self.admin_only_patterns:
            if re.search(pattern, selector):
                return True
        return False
        
    def extract_properties_from_reference(self, selector: str) -> Dict[str, str]:
        """Extract CSS properties for a selector from reference CSS."""
        if not os.path.exists(self.reference_css_file):
            print(f"Warning: Reference CSS file not found: {self.reference_css_file}")
            return {}
            
        try:
            with open(self.reference_css_file, 'r', encoding='utf-8') as f:
                css_content = f.read()
        except Exception as e:
            print(f"Error reading reference CSS: {e}")
            return {}
            
        # Clean CSS content
        css_content = re.sub(r'/\*.*?\*/', '', css_content, flags=re.DOTALL)
        css_content = re.sub(r'\s+', ' ', css_content)
        
        # For complex selectors with commas, try to find individual components
        if ',' in selector:
            # Try the full selector first
            properties = self._find_selector_properties(css_content, selector)
            if properties:
                return properties
                
            # If not found, try the first component (most likely to have properties)
            first_component = selector.split(',')[0].strip()
            properties = self._find_selector_properties(css_content, first_component)
            if properties:
                return properties
        else:
            return self._find_selector_properties(css_content, selector)
        
        return {}
    
    def _find_selector_properties(self, css_content: str, selector: str) -> Dict[str, str]:
        """Find properties for a single selector in CSS content."""
        # Escape special regex characters in selector
        escaped_selector = re.escape(selector)
        # But allow for whitespace flexibility
        escaped_selector = escaped_selector.replace(r'\ ', r'\s+')
        
        pattern = rf'{escaped_selector}\s*\{{([^}}]+)\}}'
        match = re.search(pattern, css_content, re.IGNORECASE)
        
        if match:
            properties_text = match.group(1).strip()
            properties = {}
            
            # Parse properties
            prop_pattern = r'([^:]+):\s*([^;]+);?'
            for prop_match in re.finditer(prop_pattern, properties_text):
                prop_name = prop_match.group(1).strip()
                prop_value = prop_match.group(2).strip()
                if prop_name and prop_value:
                    properties[prop_name] = prop_value
            
            return properties
        
        return {}
    
    def apply_css_theming(self, properties: Dict[str, str]) -> Dict[str, str]:
        """Apply CSS variable theming to properties."""
        themed_props = {}
        
        # Common color mappings to CSS variables
        color_mappings = {
            '#eaecf0': 'var(--body-border)',
            '#a2a9b1': 'var(--body-mid)', 
            '#f8f9fa': 'var(--body-light)',
            '#ffffff': 'var(--body-main)',
            '#000000': 'var(--text-color)',
            '#0645ad': 'var(--link-color)',
            '#ba0000': 'var(--redlink-color)',
            '#36c': 'var(--ooui-progressive)',
            '#2a4b8d': 'var(--ooui-progressive--hover)',
            '#c8ccd1': 'var(--ooui-normal)',
            '#a2a9b1': 'var(--ooui-normal-border)',
            '#54595d': 'var(--byline-color)',
            '#f6f6f6': 'var(--wikitable-bg)',
            '#eaecf0': 'var(--wikitable-border)',
            '#eaf3ff': 'var(--wikitable-header-bg)',
        }
        
        for prop, value in properties.items():
            # Apply color theming
            themed_value = value
            for hex_color, css_var in color_mappings.items():
                if hex_color.lower() in value.lower():
                    themed_value = themed_value.replace(hex_color, css_var)
                    themed_value = themed_value.replace(hex_color.upper(), css_var)
            
            themed_props[prop] = themed_value
            
        return themed_props
    
    def generate_fallback_properties(self, selector: str) -> Dict[str, str]:
        """Generate reasonable fallback properties for common selector patterns."""
        properties = {}
        selector_lower = selector.lower()
        
        # Color properties for links and buttons
        if any(pattern in selector_lower for pattern in [':hover', ':active', ':focus']):
            if 'button' in selector_lower or 'btn' in selector_lower:
                properties['background-color'] = 'var(--ooui-normal--hover)'
                properties['color'] = 'var(--ooui-text)'
            elif 'link' in selector_lower or ' a' in selector_lower:
                properties['color'] = 'var(--link-color)'
                properties['text-decoration'] = 'underline'
        
        # Background colors for various components
        if 'messagebox' in selector_lower:
            if 'discord' in selector_lower:
                properties['background-color'] = '#5865F2'
                properties['color'] = '#fff'
            elif 'error' in selector_lower:
                properties['background-color'] = 'var(--errorbox-bg)'
                properties['border-color'] = 'var(--errorbox-border)'
            elif 'warning' in selector_lower:
                properties['background-color'] = 'var(--warningbox-bg)'
                properties['border-color'] = 'var(--warningbox-border)'
            else:
                properties['background-color'] = 'var(--body-light)'
                properties['border'] = '1px solid var(--body-border)'
        
        # Table styling
        if any(pattern in selector_lower for pattern in ['table', 'td', 'th']):
            if 'align-center' in selector_lower:
                properties['text-align'] = 'center'
            elif 'align-left' in selector_lower:
                properties['text-align'] = 'left' 
            elif 'align-right' in selector_lower:
                properties['text-align'] = 'right'
            elif 'table-' in selector_lower:
                if 'yes' in selector_lower or 'positive' in selector_lower:
                    properties['background'] = 'var(--table-yes-background)'
                    properties['color'] = 'var(--table-yes-color)'
                elif 'no' in selector_lower or 'negative' in selector_lower:
                    properties['background'] = 'var(--table-no-background)'
                    properties['color'] = 'var(--table-no-color)'
        
        # Form elements
        if any(pattern in selector_lower for pattern in ['input', 'button', 'checkbox', 'radio']):
            if 'oo-ui' in selector_lower:
                properties['background-color'] = 'var(--ooui-normal)'
                properties['color'] = 'var(--ooui-text)'
                properties['border-color'] = 'var(--ooui-normal-border)'
        
        # Gaming elements
        if any(pattern in selector_lower for pattern in ['equipment', 'inventory', 'skill']):
            properties['position'] = 'relative'
            if 'quantity-text' in selector_lower:
                properties['color'] = '#ff0'
                properties['font-family'] = "'RuneScape Small', monospace"
                properties['position'] = 'absolute'
                properties['font-size'] = '12pt'
        
        # Navigation elements  
        if any(pattern in selector_lower for pattern in ['mainpage', 'tile']):
            properties['color'] = 'var(--text-color)'
            if 'twitter' in selector_lower or 'discord' in selector_lower:
                properties['color'] = '#fff'
                properties['font-weight'] = 'bold'
        
        # Text styling
        if any(pattern in selector_lower for pattern in ['h1', 'h2', 'h3', 'h4', 'h5', 'h6']):
            properties['color'] = 'var(--text-color)'
            properties['font-family'] = "'PT Serif','Palatino','Georgia',serif"
        
        # Generic fallbacks
        if not properties and ('color' not in selector_lower):
            # Provide minimal styling for unrecognized selectors
            if any(pattern in selector_lower for pattern in [':hover', ':focus', ':active']):
                properties['opacity'] = '0.8'
            elif any(pattern in selector_lower for pattern in ['background', 'bg']):
                properties['background-color'] = 'var(--body-light)'
        
        return properties
    
    def determine_module(self, selector: str) -> str:
        """Determine which module a selector belongs to."""
        selector_lower = selector.lower()
        
        # Check each module's patterns
        for module, patterns in self.module_categories.items():
            if module == "other":  # Skip catch-all for now
                continue
            for pattern in patterns:
                if pattern.lower() in selector_lower:
                    return module
        
        return "other"  # Default catch-all
    
    def generate_css_rule(self, selector: str, properties: Dict[str, str]) -> str:
        """Generate a formatted CSS rule."""
        if not properties:
            return ""
            
        lines = [f"{selector} {{"]
        for prop, value in properties.items():
            lines.append(f"    {prop}: {value};")
        lines.append("}")
        
        return "\n".join(lines)
    
    def generate_comprehensive_css(self) -> bool:
        """Generate CSS for ALL missing rules to achieve 100% coverage."""
        try:
            with open(self.analysis_file, 'r') as f:
                data = json.load(f)
        except Exception as e:
            print(f"Error reading analysis file: {e}")
            return False
        
        missing_rules = data.get("only_in_reference", [])
        if not missing_rules:
            print("No missing rules found in analysis file")
            return False
        
        print(f"Processing {len(missing_rules)} missing rules...")
        
        # Group rules by target module
        module_rules = {}
        admin_skipped = 0
        processed = 0
        
        for selector in missing_rules:
            # Skip only true admin-only selectors
            if self.is_admin_only(selector):
                admin_skipped += 1
                continue
                
            # Extract properties from reference CSS
            properties = self.extract_properties_from_reference(selector)
            if not properties:
                # Generate fallback properties based on selector patterns
                properties = self.generate_fallback_properties(selector)
                if not properties:
                    print(f"Warning: No properties found for selector: {selector}")
                    continue
            
            # Apply CSS variable theming
            themed_properties = self.apply_css_theming(properties)
            
            # Determine target module
            module = self.determine_module(selector)
            
            if module not in module_rules:
                module_rules[module] = []
            
            module_rules[module].append((selector, themed_properties))
            processed += 1
        
        print(f"Processed: {processed} rules")
        print(f"Admin-only skipped: {admin_skipped} rules") 
        print(f"Target modules: {len(module_rules)} modules")
        
        # Generate CSS output
        css_output = [
            "/* Auto-Generated CSS for 100% Coverage */",
            f"/* Generated from {len(missing_rules)} missing rules */",
            f"/* Processed: {processed} non-admin rules */",
            f"/* Admin-only skipped: {admin_skipped} rules */",
            ""
        ]
        
        for module, rules in module_rules.items():
            css_output.append(f"/* === {module.upper()} MODULE === */")
            css_output.append("")
            
            for selector, properties in rules:
                rule = self.generate_css_rule(selector, properties)
                if rule:
                    css_output.append(rule)
                    css_output.append("")
            
            css_output.append(f"/* End {module} module - {len(rules)} rules */")
            css_output.append("")
        
        # Write output file
        try:
            os.makedirs(os.path.dirname(self.output_file), exist_ok=True)
            with open(self.output_file, 'w', encoding='utf-8') as f:
                f.write("\n".join(css_output))
            
            print(f"✅ Generated comprehensive CSS: {self.output_file}")
            print(f"📊 Total rules generated: {processed}")
            return True
            
        except Exception as e:
            print(f"Error writing CSS file: {e}")
            return False

def main():
    """Main entry point."""
    generator = CSSGenerator()
    
    if len(sys.argv) > 1:
        generator.analysis_file = sys.argv[1]
    
    success = generator.generate_comprehensive_css()
    
    if success:
        print("\n🎯 CSS generation complete - targeting 100% coverage")
        sys.exit(0)
    else:
        print("\n❌ CSS generation failed")
        sys.exit(1)

if __name__ == '__main__':
    main()