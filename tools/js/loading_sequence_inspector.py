#!/usr/bin/env python3
"""
Captures the exact JavaScript that triggers module loading on OSRS Wiki.
"""

import asyncio
from playwright.async_api import async_playwright
import json

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)  # Show browser for debugging
        page = await browser.new_page()
        
        # Enable console logging
        page.on("console", lambda msg: print(f"[CONSOLE] {msg.text}"))
        
        # Navigate to page
        print("[INFO] Navigating to OSRS Wiki Logs page...")
        await page.goto("https://oldschool.runescape.wiki/w/Logs", wait_until="domcontentloaded")
        
        # Wait for startup to load
        await page.wait_for_timeout(2000)
        
        # Inject our monitoring script
        await page.evaluate("""
            // Override mw.loader.load to see what's being loaded
            if (window.mw && window.mw.loader) {
                const originalLoad = window.mw.loader.load;
                window.mw.loader.load = function() {
                    console.log('[LOAD CALLED] Arguments:', JSON.stringify(Array.from(arguments)));
                    return originalLoad.apply(this, arguments);
                };
                
                const originalUsing = window.mw.loader.using;
                window.mw.loader.using = function() {
                    console.log('[USING CALLED] Arguments:', JSON.stringify(Array.from(arguments)));
                    return originalUsing.apply(this, arguments);
                };
            }
            
            // Check if there's any inline script that triggers loading
            const scripts = document.querySelectorAll('script');
            scripts.forEach((script, index) => {
                if (script.innerHTML && script.innerHTML.includes('mw.loader')) {
                    console.log(`[INLINE SCRIPT ${index}] Contains mw.loader code:`, 
                        script.innerHTML.substring(0, 500));
                }
            });
            
            // Check what's in RLQ (ResourceLoader Queue)
            if (window.RLQ) {
                console.log('[RLQ] Queue contents:', JSON.stringify(window.RLQ));
            }
            
            // Check RLPAGEMODULES
            console.log('[RLPAGEMODULES]', JSON.stringify(window.RLPAGEMODULES));
        """)
        
        # Wait to capture any async loading
        await page.wait_for_timeout(5000)
        
        # Get the final state
        result = await page.evaluate("""
            () => {
                return {
                    hasMediaWiki: typeof window.mw !== 'undefined',
                    hasLoader: typeof window.mw?.loader !== 'undefined',
                    hasUsing: typeof window.mw?.loader?.using !== 'undefined',
                    hasLoad: typeof window.mw?.loader?.load !== 'undefined',
                    rlpagemodules: window.RLPAGEMODULES,
                    rlq: window.RLQ || [],
                    moduleStates: window.mw ? Object.keys(window.mw.loader.moduleRegistry || {}).length : 0
                };
            }
        """)
        
        print("\n[FINAL STATE]")
        print(json.dumps(result, indent=2))
        
        await browser.close()

if __name__ == "__main__":
    asyncio.run(main())