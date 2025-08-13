#!/usr/bin/env python3
"""
MediaWiki Page Modules Extractor using Browser Execution

This module extracts page-specific modules (RLPAGEMODULES) by executing JavaScript
in a real browser environment, eliminating the need for fragile regex parsing.

The approach:
1. Load the actual MediaWiki page (mobile or desktop)
2. Execute JavaScript to get RLPAGEMODULES directly from the page
3. Return structured JSON data

This is robust, accurate, and works for both mobile and desktop pages.
"""

import asyncio
import json
import sys
from pathlib import Path
from playwright.async_api import async_playwright
from typing import Dict, List, Any, Optional

class PageModulesExtractor:
    def __init__(self, base_url: str = "https://oldschool.runescape.wiki"):
        self.base_url = base_url
        
    async def extract_page_modules(self, page_name: str, use_mobile: bool = True) -> Dict[str, Any]:
        """Extract page modules by loading the actual page and executing JavaScript."""
        
        # Construct URL
        page_url = f"{self.base_url}/w/{page_name}"
        if use_mobile:
            page_url += "?useformat=mobile"
            
        print(f"[INFO] Extracting page modules from: {page_url}")
        
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            page = await browser.new_page()
            
            try:
                # Navigate to the page and wait for MediaWiki to load
                await page.goto(page_url, wait_until="networkidle")
                
                # Extract page modules and configuration directly from the page
                page_data = await page.evaluate("""
                    () => {
                        const result = {
                            modules: null,
                            config: null,
                            state: null,
                            page_info: {
                                title: typeof mw !== 'undefined' ? mw.config.get('wgPageName') : null,
                                skin: typeof mw !== 'undefined' ? mw.config.get('skin') : null,
                                mobile_mode: typeof mw !== 'undefined' ? mw.config.get('wgMFMode') : null
                            },
                            extraction_method: 'browser_execution'
                        };
                        
                        // Get RLPAGEMODULES
                        if (typeof RLPAGEMODULES !== 'undefined') {
                            result.modules = RLPAGEMODULES;
                        }
                        
                        // Get RLCONF
                        if (typeof RLCONF !== 'undefined') {
                            result.config = RLCONF;
                        }
                        
                        // Get RLSTATE 
                        if (typeof RLSTATE !== 'undefined') {
                            result.state = RLSTATE;
                        }
                        
                        return result;
                    }
                """)
                
                if page_data['modules'] is None:
                    return {
                        "error": "RLPAGEMODULES not found on page",
                        "page_url": page_url,
                        "debug_info": page_data
                    }
                
                print(f"[SUCCESS] Extracted {len(page_data['modules'])} page modules")
                print(f"[INFO] Page info: {page_data['page_info']}")
                
                return {
                    "page_modules": page_data['modules'],
                    "page_config": page_data['config'],
                    "page_state": page_data['state'],
                    "page_info": page_data['page_info'],
                    "extraction_method": page_data['extraction_method'],
                    "source_url": page_url,
                    "mobile_page": use_mobile
                }
                
            finally:
                await browser.close()
    
    async def extract_multiple_pages(self, page_names: List[str], use_mobile: bool = True) -> Dict[str, Any]:
        """Extract modules from multiple pages."""
        
        results = {}
        
        for page_name in page_names:
            print(f"\n[INFO] Processing page: {page_name}")
            try:
                page_result = await self.extract_page_modules(page_name, use_mobile)
                results[page_name] = page_result
            except Exception as e:
                print(f"[ERROR] Failed to extract from {page_name}: {e}")
                results[page_name] = {"error": str(e)}
        
        return {
            "pages": results,
            "summary": {
                "total_pages": len(page_names),
                "successful_extractions": len([r for r in results.values() if "error" not in r]),
                "failed_extractions": len([r for r in results.values() if "error" in r])
            }
        }

    def compare_desktop_vs_mobile(self, page_name: str) -> Dict[str, Any]:
        """Compare modules between desktop and mobile versions of the same page."""
        
        async def _compare():
            desktop_result = await self.extract_page_modules(page_name, use_mobile=False)
            mobile_result = await self.extract_page_modules(page_name, use_mobile=True)
            
            if "error" in desktop_result or "error" in mobile_result:
                return {
                    "error": "Failed to extract from one or both versions",
                    "desktop": desktop_result,
                    "mobile": mobile_result
                }
            
            desktop_modules = set(desktop_result['page_modules'])
            mobile_modules = set(mobile_result['page_modules'])
            
            return {
                "desktop": desktop_result,
                "mobile": mobile_result,
                "comparison": {
                    "desktop_only": sorted(list(desktop_modules - mobile_modules)),
                    "mobile_only": sorted(list(mobile_modules - desktop_modules)),
                    "common": sorted(list(desktop_modules & mobile_modules)),
                    "total_desktop": len(desktop_modules),
                    "total_mobile": len(mobile_modules),
                    "total_common": len(desktop_modules & mobile_modules)
                }
            }
        
        return asyncio.run(_compare())


async def main():
    """Main function for testing the extractor."""
    if len(sys.argv) < 2:
        print("Usage: python page_modules_extractor.py <page_name> [options]")
        print()
        print("Options:")
        print("  --desktop         Extract from desktop version (default: mobile)")
        print("  --compare         Compare desktop vs mobile modules")
        print("  --output <file>   Save results to file")
        print()
        print("Examples:")
        print("  python page_modules_extractor.py Logs")
        print("  python page_modules_extractor.py Logs --desktop")
        print("  python page_modules_extractor.py Logs --compare")
        print("  python page_modules_extractor.py Logs --output logs_modules.json")
        sys.exit(1)
    
    page_name = sys.argv[1]
    use_mobile = "--desktop" not in sys.argv
    compare_mode = "--compare" in sys.argv
    
    output_file = None
    if "--output" in sys.argv:
        output_index = sys.argv.index("--output")
        if output_index + 1 < len(sys.argv):
            output_file = Path(sys.argv[output_index + 1])
    
    extractor = PageModulesExtractor()
    
    try:
        if compare_mode:
            print(f"[INFO] Comparing desktop vs mobile modules for '{page_name}'")
            result = extractor.compare_desktop_vs_mobile(page_name)
        else:
            mode = "mobile" if use_mobile else "desktop"
            print(f"[INFO] Extracting {mode} modules for '{page_name}'")
            result = await extractor.extract_page_modules(page_name, use_mobile)
        
        if "error" in result:
            print(f"[ERROR] Extraction failed: {result['error']}")
            sys.exit(1)
        
        # Save results
        if output_file:
            with open(output_file, 'w') as f:
                json.dump(result, f, indent=2)
            print(f"[SUCCESS] Results saved to: {output_file}")
        else:
            default_file = f"{page_name.lower()}_modules.json"
            with open(default_file, 'w') as f:
                json.dump(result, f, indent=2)
            print(f"[SUCCESS] Results saved to: {default_file}")
        
        # Print summary
        print("\n" + "="*60)
        if compare_mode:
            print("DESKTOP VS MOBILE COMPARISON")
            print("="*60)
            comp = result['comparison']
            print(f"Desktop modules: {comp['total_desktop']}")
            print(f"Mobile modules: {comp['total_mobile']}")
            print(f"Common modules: {comp['total_common']}")
            print(f"Desktop-only: {len(comp['desktop_only'])}")
            print(f"Mobile-only: {len(comp['mobile_only'])}")
            
            if comp['desktop_only']:
                print(f"\nDesktop-only modules: {comp['desktop_only']}")
            if comp['mobile_only']:
                print(f"\nMobile-only modules: {comp['mobile_only']}")
        else:
            print("PAGE MODULE EXTRACTION SUMMARY")
            print("="*60)
            modules = result['page_modules']
            print(f"Total modules: {len(modules)}")
            print(f"Extraction method: {result['extraction_method']}")
            print(f"Source URL: {result['source_url']}")
            
            # Show first few modules
            print(f"\nFirst 10 modules:")
            for i, module in enumerate(modules[:10]):
                print(f"  {i+1}. {module}")
            if len(modules) > 10:
                print(f"  ... and {len(modules) - 10} more")
            
    except Exception as e:
        print(f"[ERROR] Extraction failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())