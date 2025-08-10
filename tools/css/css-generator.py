#!/usr/bin/env python3
"""
CSS Generation Tool for Missing Wiki Selectors
Generates properly formatted CSS from gap analysis results.
"""

import json
import os
import sys
import re
from typing import Dict, List, Any

class CSSGenerator:
    """Generate CSS from gap analysis results."""
    
    def __init__(self, analysis_file: str = "tools/css/output/css_gap_analysis.json"):
        self.analysis_file = analysis_file
        self.output_file = "tools/css/output/generated_missing_css.css"
        self.priorities = ["content-high", "content-medium", "interactive"]
        
        # Module categories for bulk generation
        self.module_categories = {
            "tables": ["wikitable", "table-", "droptable"],
            "forms": ["input", "button", "form", "fieldset", "textarea", "cdx-"],
            "messagebox": ["messagebox", "errorbox", "warningbox", "successbox"],
            "media": ["thumb", "img", "gallery", "filehistory", "mw-mmv"],
            "navigation": ["navbox", "nav-", "menu", "tabbernav"],
            "layout": ["container", "content", "mw-body", "tile"],
            "gaming": ["quest", "skill", "combat", "item-", "infobox-room"],
            "mediawiki": ["mw-", "smw-", "diff-", "echo-"],
            "interactive": [":hover", ":focus", ":active", "ooui-"],
            "typography": ["h1", "h2", "h3", "h4", "h5", "h6", "font-", "text-"]
        }
        
        # Filters for selectors to skip (admin, user-specific, etc.)
        self.skip_patterns = [
            r'a\[href="/w/User:',  # User links
            r'#mw-',              # MediaWiki admin interface
            r'\.mw-echo-',        # Echo notifications
            r'\.oo-ui-',         # OOUI admin components
            r'#ca-',             # Content actions
            r'#p-',              # Portlets
            r'\.vector-',        # Vector skin
            r'\.citizen-',       # Citizen skin
            r'@media.*print',    # Print media queries
            r'@media.*screen.*max-width.*\d+px', # Small screen mobile queries
            r'\.minerva-',       # Minerva mobile skin
        ]
        
    def should_skip_selector(self, selector: str) -> bool:
        """Check if selector should be skipped based on filters."""
        for pattern in self.skip_patterns:
            if re.search(pattern, selector):
                return True
        return False
    
    def selector_matches_module(self, selector: str, module: str) -> bool:
        """Check if selector belongs to a specific module."""
        if module not in self.module_categories:
            return False
            
        selector_lower = selector.lower()
        patterns = self.module_categories[module]
        
        return any(pattern.lower() in selector_lower for pattern in patterns)
    
    def format_css_properties(self, properties: Dict[str, Any]) -> List[str]:
        """Format CSS properties into valid CSS lines."""
        css_lines = []
        
        for prop, value in properties.items():
            # Handle nested property objects (from incomplete analysis)
            if isinstance(value, dict) and "wiki" in value:
                value = value["wiki"]  # Use wiki version
            
            # Clean up property values
            if isinstance(value, str):
                # Ensure proper CSS formatting
                css_lines.append(f"    {prop}: {value};")
        
        return css_lines
    
    def generate_css_section(self, title: str, selectors: Dict[str, Dict]) -> List[str]:
        """Generate CSS section for a category of selectors."""
        css_lines = []
        css_lines.append(f"\n/* --- {title} --- */")
        
        processed_count = 0
        skipped_count = 0
        
        for selector, data in selectors.items():
            if self.should_skip_selector(selector):
                skipped_count += 1
                continue
                
            # Get properties to implement
            if data.get("status") == "missing_entirely":
                properties = data.get("properties", {})
            elif data.get("status") == "incomplete":
                properties = data.get("missing_properties", {})
            else:
                continue
                
            if not properties:
                continue
                
            # Format the CSS rule
            css_lines.append(f"\n{selector} {{")
            css_lines.extend(self.format_css_properties(properties))
            css_lines.append("}")
            
            processed_count += 1
            
            # Limit to prevent overwhelming output
            if processed_count >= 100:
                css_lines.append(f"\n/* NOTE: Truncated after {processed_count} selectors to prevent overwhelming output */")
                break
        
        css_lines.append(f"\n/* Processed: {processed_count} selectors, Skipped: {skipped_count} selectors */")
        return css_lines
    
    def generate_css(self) -> bool:
        """Generate CSS from analysis file."""
        try:
            with open(self.analysis_file, 'r') as f:
                data = json.load(f)
            
            missing_selectors = data.get("missing_selectors", {})
            incomplete_selectors = data.get("incomplete_selectors", {})
            
            css_output = []
            css_output.append("/* Auto-Generated Missing CSS Rules from Wiki Analysis */")
            css_output.append("/* Generated by generate_missing_css.py */\n")
            
            stats = data.get("statistics", {})
            missing_by_cat = stats.get("missing_by_category", {})
            incomplete_by_cat = stats.get("incomplete_by_category", {})
            
            css_output.append("/*")
            css_output.append(" * Analysis Summary:")
            css_output.append(f" * Wiki Total: {stats.get('wiki_total_selectors', 0)} selectors")
            css_output.append(f" * App Total: {stats.get('app_total_selectors', 0)} selectors")
            css_output.append(" * Missing by category:")
            for cat, count in missing_by_cat.items():
                css_output.append(f" *   {cat}: {count}")
            css_output.append(" */\n")
            
            # Process each priority category
            for priority in self.priorities:
                if priority in missing_selectors:
                    title = f"Missing {priority.title().replace('-', ' ')} Selectors"
                    section = self.generate_css_section(title, missing_selectors[priority])
                    css_output.extend(section)
                
                if priority in incomplete_selectors:
                    title = f"Incomplete {priority.title().replace('-', ' ')} Selectors"
                    section = self.generate_css_section(title, incomplete_selectors[priority])
                    css_output.extend(section)
            
            # Write output file
            with open(self.output_file, 'w') as f:
                f.write('\n'.join(css_output))
            
            print(f"✅ Generated CSS saved to: {self.output_file}")
            print(f"📊 Total lines generated: {len(css_output)}")
            
            return True
            
        except Exception as e:
            print(f"❌ Error generating CSS: {e}")
            return False
    
    def generate_module_css(self, module: str) -> bool:
        """Generate CSS for a specific module."""
        if module not in self.module_categories:
            print(f"❌ Unknown module: {module}")
            print(f"Available modules: {', '.join(self.module_categories.keys())}")
            return False
            
        try:
            with open(self.analysis_file, 'r') as f:
                data = json.load(f)
                
            missing_selectors = data.get("missing_selectors", {})
            incomplete_selectors = data.get("incomplete_selectors", {})
            
            # Filter selectors for this module
            module_missing = {}
            module_incomplete = {}
            
            # Process all categories
            for category, selectors in missing_selectors.items():
                module_missing[category] = {}
                for selector, props in selectors.items():
                    if self.selector_matches_module(selector, module):
                        module_missing[category][selector] = props
            
            for category, selectors in incomplete_selectors.items():
                module_incomplete[category] = {}
                for selector, props in selectors.items():
                    if self.selector_matches_module(selector, module):
                        module_incomplete[category][selector] = props
            
            # Generate CSS output
            css_output = []
            css_output.append(f"/* Auto-Generated {module.upper()} MODULE CSS */")
            css_output.append(f"/* Generated by css-generator.py for module: {module} */")
            css_output.append(f"/* Module patterns: {', '.join(self.module_categories[module])} */\n")
            
            total_selectors = 0
            
            # Process missing selectors
            for category, selectors in module_missing.items():
                if selectors:  # Only if there are selectors in this category
                    title = f"Missing {category.title().replace('-', ' ')} Selectors"
                    section = self.generate_css_section(title, selectors)
                    css_output.extend(section)
                    total_selectors += len(selectors)
            
            # Process incomplete selectors
            for category, selectors in module_incomplete.items():
                if selectors:  # Only if there are selectors in this category
                    title = f"Incomplete {category.title().replace('-', ' ')} Selectors"
                    section = self.generate_css_section(title, selectors)
                    css_output.extend(section)
                    total_selectors += len(selectors)
            
            if total_selectors == 0:
                print(f"✅ No missing or incomplete selectors found for module: {module}")
                return True
            
            # Write module-specific output file
            module_output_file = self.output_file.replace('.css', f'_{module}.css')
            with open(module_output_file, 'w') as f:
                f.write('\n'.join(css_output))
            
            print(f"✅ Generated {module} module CSS: {module_output_file}")
            print(f"📊 Total selectors: {total_selectors}")
            print(f"📊 Total lines: {len(css_output)}")
            
            return True
            
        except Exception as e:
            print(f"❌ Error generating module CSS: {e}")
            return False
    
    def list_modules(self):
        """List available modules and their patterns."""
        print("=== AVAILABLE MODULES ===")
        for module, patterns in self.module_categories.items():
            print(f"\n{module.upper()}:")
            print(f"  Patterns: {', '.join(patterns)}")
    
    def preview_css(self, limit: int = 20) -> str:
        """Generate a preview of what CSS would be created."""
        try:
            with open(self.analysis_file, 'r') as f:
                data = json.load(f)
            
            missing_selectors = data.get("missing_selectors", {})
            
            preview_lines = []
            count = 0
            
            for priority in self.priorities:
                if priority not in missing_selectors:
                    continue
                    
                preview_lines.append(f"\n=== {priority.upper()} PREVIEW ===")
                
                for selector, selector_data in missing_selectors[priority].items():
                    if self.should_skip_selector(selector):
                        continue
                        
                    properties = selector_data.get("properties", {})
                    if properties:
                        preview_lines.append(f"{selector} {{ {len(properties)} properties }}")
                        count += 1
                        
                        if count >= limit:
                            preview_lines.append(f"... (truncated at {limit} selectors)")
                            return '\n'.join(preview_lines)
            
            preview_lines.append(f"\nTotal selectors to generate: {count}")
            return '\n'.join(preview_lines)
            
        except Exception as e:
            return f"Error generating preview: {e}"


def main():
    """Main entry point."""
    import argparse
    
    parser = argparse.ArgumentParser(description="Generate CSS from gap analysis")
    parser.add_argument("--module", help="Generate CSS for specific module only")
    parser.add_argument("--list-modules", action="store_true", 
                       help="List available modules")
    parser.add_argument("--preview", action="store_true",
                       help="Show preview of CSS to be generated")
    parser.add_argument("--analysis-file", 
                       default="tools/css/output/css_gap_analysis.json",
                       help="Path to CSS gap analysis file")
    
    args = parser.parse_args()
    
    generator = CSSGenerator(args.analysis_file)
    
    if args.list_modules:
        generator.list_modules()
        return
    
    if not os.path.exists(generator.analysis_file):
        print(f"❌ Analysis file not found: {generator.analysis_file}")
        print("Run 'python3 tools/css/css-sync-workflow.py' first to generate analysis data.")
        sys.exit(1)
    
    if args.preview:
        print("🔍 CSS Generation Preview:")
        print(generator.preview_css())
        return
    
    if args.module:
        print(f"🚀 Generating CSS for {args.module} module...")
        if generator.generate_module_css(args.module):
            print(f"\n✅ Module CSS generation complete!")
        else:
            print(f"\n❌ Module CSS generation failed!")
            sys.exit(1)
        return
    
    print("🚀 Generating missing CSS from analysis...")
    
    if generator.generate_css():
        print("\n✅ CSS generation complete!")
        print(f"📄 Review the generated CSS in: {generator.output_file}")
        print("\nNext steps:")
        print("1. Review the generated CSS")
        print("2. Append relevant sections to app/src/main/assets/styles/wiki-integration.css")
        print("3. Build the app: ./gradlew assembleDebug")
    else:
        print("❌ CSS generation failed!")
        sys.exit(1)


if __name__ == '__main__':
    main()