#!/usr/bin/env python3
"""
Complete analysis of MediaWiki loading sequence.
Captures EVERYTHING about how modules actually load.
"""

import asyncio
from playwright.async_api import async_playwright
import json

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()
        
        # Capture ALL console messages
        console_logs = []
        page.on("console", lambda msg: console_logs.append(msg.text))
        
        # Navigate to page
        print("[INFO] Loading OSRS Wiki Logs page...")
        await page.goto("https://oldschool.runescape.wiki/w/Logs", wait_until="domcontentloaded")
        
        # Wait for initial load
        await page.wait_for_timeout(2000)
        
        # Capture the EXACT order of events
        loading_sequence = await page.evaluate("""
            () => {
                const results = {
                    phase1_initial_state: {},
                    phase2_after_startup: {},
                    phase3_after_modules: {},
                    inline_scripts: [],
                    external_scripts: [],
                    rlq_processing: null,
                    actual_loading_trigger: null
                };
                
                // Phase 1: What exists before ANY scripts run?
                // (We can't capture this from inside JS, but we know from HTML)
                results.phase1_initial_state = {
                    note: "Variables defined in HTML before startup.js"
                };
                
                // Phase 2: What does startup.js do?
                results.phase2_after_startup = {
                    has_mw: typeof window.mw !== 'undefined',
                    has_loader: typeof window.mw?.loader !== 'undefined',
                    startup_loads_what: "startup.js calls mw.loader.load(window.RLPAGEMODULES || [])"
                };
                
                // Phase 3: After modules load
                results.phase3_after_modules = {
                    total_modules: window.mw ? Object.keys(window.mw.loader.moduleRegistry || {}).length : 0,
                    rlpagemodules_defined: typeof window.RLPAGEMODULES !== 'undefined',
                    rlpagemodules_count: window.RLPAGEMODULES ? window.RLPAGEMODULES.length : 0
                };
                
                // Find all inline scripts
                const scripts = document.querySelectorAll('script');
                scripts.forEach((script, i) => {
                    if (!script.src) {
                        const content = script.innerHTML.substring(0, 200);
                        if (content.includes('RLPAGEMODULES')) {
                            results.inline_scripts.push({
                                index: i,
                                defines_rlpagemodules: true,
                                content: content
                            });
                        }
                        if (content.includes('RLQ')) {
                            results.inline_scripts.push({
                                index: i,
                                uses_rlq: true,
                                content: content
                            });
                        }
                        if (content.includes('mw.loader')) {
                            results.inline_scripts.push({
                                index: i,
                                uses_mw_loader: true,
                                content: content
                            });
                        }
                    } else {
                        results.external_scripts.push({
                            index: i,
                            src: script.src,
                            is_startup: script.src.includes('startup'),
                            is_load_php: script.src.includes('load.php')
                        });
                    }
                });
                
                // Check RLQ processing
                if (window.RLQ) {
                    results.rlq_processing = {
                        type: typeof window.RLQ,
                        has_push: typeof window.RLQ.push === 'function',
                        explanation: "RLQ is ResourceLoader Queue - processed by startup.js"
                    };
                }
                
                // What actually triggers module loading?
                results.actual_loading_trigger = {
                    startup_js_line: "mw.loader.load( window.RLPAGEMODULES || [] )",
                    when: "During startup.js execution",
                    requirement: "RLPAGEMODULES must be defined BEFORE startup.js runs",
                    current_problem: "If RLPAGEMODULES is undefined when startup.js runs, modules won't load"
                };
                
                return results;
            }
        """)
        
        # Check what modules actually got loaded
        loaded_check = await page.evaluate("""
            () => {
                const gadgets = [];
                const core = [];
                
                if (window.mw && window.mw.loader && window.mw.loader.moduleRegistry) {
                    for (const [name, module] of Object.entries(window.mw.loader.moduleRegistry)) {
                        if (name.includes('gadget')) {
                            gadgets.push(name);
                        } else if (name.includes('mediawiki') || name === 'jquery' || name.includes('oojs')) {
                            core.push(name);
                        }
                    }
                }
                
                return {
                    total_gadgets: gadgets.length,
                    total_core: core.length,
                    has_ge_charts: gadgets.some(g => g.includes('GECharts')),
                    sample_gadgets: gadgets.slice(0, 5),
                    sample_core: core.slice(0, 5)
                };
            }
        """)
        
        # Look at the actual HTML source structure
        html_structure = await page.evaluate("""
            () => {
                const head = document.head;
                const scripts = Array.from(head.querySelectorAll('script'));
                
                // Find the order of important scripts
                let startup_index = -1;
                let rlpagemodules_index = -1;
                
                scripts.forEach((script, i) => {
                    if (script.src && script.src.includes('startup')) {
                        startup_index = i;
                    }
                    if (!script.src && script.innerHTML.includes('RLPAGEMODULES')) {
                        rlpagemodules_index = i;
                    }
                });
                
                return {
                    total_scripts_in_head: scripts.length,
                    startup_script_position: startup_index,
                    rlpagemodules_definition_position: rlpagemodules_index,
                    order_correct: rlpagemodules_index < startup_index && rlpagemodules_index >= 0,
                    explanation: rlpagemodules_index < startup_index ? 
                        "GOOD: RLPAGEMODULES defined before startup.js" : 
                        "BAD: startup.js runs before RLPAGEMODULES is defined"
                };
            }
        """)
        
        print("\n" + "="*60)
        print("COMPLETE LOADING ANALYSIS")
        print("="*60)
        
        print("\n1. LOADING SEQUENCE:")
        print(json.dumps(loading_sequence, indent=2))
        
        print("\n2. MODULES LOADED:")
        print(json.dumps(loaded_check, indent=2))
        
        print("\n3. HTML SCRIPT ORDER:")
        print(json.dumps(html_structure, indent=2))
        
        print("\n4. KEY FINDING:")
        if html_structure['order_correct']:
            print("✅ RLPAGEMODULES is defined BEFORE startup.js")
            print("   This is why modules load correctly on the real wiki!")
        else:
            print("❌ startup.js runs BEFORE RLPAGEMODULES is defined")
            print("   This would prevent modules from loading!")
            
        await browser.close()

if __name__ == "__main__":
    asyncio.run(main())