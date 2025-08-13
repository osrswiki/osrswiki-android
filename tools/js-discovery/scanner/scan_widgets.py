#!/usr/bin/env python3
"""
OSRS Wiki Artifact Collector (Enhanced with Execution-based Resolution)

Captures the necessary JavaScript artifacts from a live wiki page to allow
for a faithful "capture-and-replay" of the MediaWiki ResourceLoader engine
in a controlled environment.

This enhanced version uses execution-based module resolution to capture
ALL required modules and their dependencies accurately.

This script captures:
1.  The complete startup module (`startup.js`).
2.  The inline page bootstrap script from the page HTML (`page_bootstrap.js`).
3.  The complete module bundle with ALL dependencies (`page_modules.js`).
"""

import asyncio
import os
import re
import json
import requests
import sys
from pathlib import Path

# Add parent directory to path for imports
sys.path.append(str(Path(__file__).parent.parent))
from playwright_inspector import MediaWikiModuleInspector
from module_resolver import MediaWikiModuleResolver
from page_modules_extractor import PageModulesExtractor

DEFAULT_BASE = "https://oldschool.runescape.wiki"
DEFAULT_TEST_PAGE = "Logs"
ARTIFACTS_DIR = Path(__file__).parent.parent / "artifacts"

async def capture_artifacts_enhanced(base: str, page_name: str, output_dir: Path):
    """
    Enhanced artifact capture using execution-based module resolution.
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    session.headers.update({
        "User-Agent": "OSRSWiki-Android-Enhanced-Artifact-Collector/2.0"
    })

    print(f"[INFO] Enhanced artifact capture from base: {base}")
    print(f"[INFO] Test page: {page_name}")
    print(f"[INFO] Output directory: {output_dir}")

    # Phase 1: Capture startup module (unchanged)
    print("\n[PHASE 1/4] Capturing startup module...")
    try:
        startup_params = {
            'debug': 'true',
            'lang': 'en-gb',
            'modules': 'startup',
            'only': 'scripts',
            'skin': 'vector'
        }
        startup_res = session.get(f"{base}/load.php", params=startup_params, timeout=30)
        startup_res.raise_for_status()
        startup_content = startup_res.text

        if not startup_content.strip():
            raise ValueError("Received empty content for startup module.")

        startup_path = output_dir / "startup.js"
        startup_path.write_text(startup_content, encoding="utf-8")
        print(f"[SUCCESS] Saved startup.js ({len(startup_content)} bytes)")

    except Exception as e:
        print(f"[ERROR] Failed to capture startup module: {e}")
        return

    # Phase 2: Extract page modules using browser execution (no regex parsing!)
    print(f"\n[PHASE 2/4] Getting page modules for '{page_name}' via browser execution...")
    try:
        page_extractor = PageModulesExtractor(base)
        page_data = await page_extractor.extract_page_modules(page_name, use_mobile=True)
        
        if "error" in page_data:
            raise ValueError(f"Page module extraction failed: {page_data['error']}")
        
        page_modules = page_data['page_modules']
        print(f"[SUCCESS] Extracted {len(page_modules)} page modules via browser execution")
        print(f"[INFO] Page info: {page_data['page_info']}")
        
        # Save the extracted page data for reference
        page_data_path = output_dir / f"{page_name.lower()}_page_data.json"
        with open(page_data_path, 'w') as f:
            json.dump(page_data, f, indent=2)
        print(f"[INFO] Saved page data to: {page_data_path}")

    except Exception as e:
        print(f"[ERROR] Failed to get page modules: {e}")
        return

    # Phase 3: Use module resolver for complete dependency resolution
    print(f"\n[PHASE 3/4] Resolving complete dependency tree...")
    try:
        resolver = MediaWikiModuleResolver(base)
        
        # Load the module registry using execution-based extraction
        registry_data = await resolver.load_module_registry()
        
        # Generate complete loading plan
        loading_plan = resolver.generate_loading_plan(page_modules)
        
        # Save the loading plan
        plan_path = output_dir / f"{page_name.lower()}_loading_plan.json"
        with open(plan_path, 'w') as f:
            json.dump(loading_plan, f, indent=2)
        print(f"[SUCCESS] Saved loading plan to {plan_path}")
        
        # Get all required modules
        all_required_modules = loading_plan['all_required_modules']
        print(f"[SUCCESS] Resolved {len(page_modules)} page modules to {len(all_required_modules)} total modules")
        print(f"[INFO] Dependency expansion: {len(all_required_modules) / len(page_modules):.1f}x")
        
    except Exception as e:
        print(f"[ERROR] Failed to resolve dependencies: {e}")
        return

    # Phase 4: Capture page bootstrap and complete module bundle
    print(f"\n[PHASE 4/4] Capturing page bootstrap and complete module bundle...")
    try:
        # Create page bootstrap from extracted page data (no more HTML parsing!)
        if not page_data.get('page_config') or not page_data.get('page_state'):
            raise ValueError("Missing page configuration data from browser extraction")

        page_bootstrap_content = f"""var RLCONF = {json.dumps(page_data['page_config'])};
var RLSTATE = {json.dumps(page_data['page_state'])};
var RLPAGEMODULES = {json.dumps(page_data['page_modules'])};
"""

        page_bootstrap_path = output_dir / "page_bootstrap.js"
        page_bootstrap_path.write_text(page_bootstrap_content, encoding="utf-8")
        print(f"[SUCCESS] Saved page_bootstrap.js ({len(page_bootstrap_content)} bytes)")

        # Capture complete module bundle in CORRECT DEPENDENCY ORDER
        print(f"[INFO] Capturing {len(all_required_modules)} modules in dependency order")
        
        # Load modules by phase to ensure correct execution order
        all_content = []
        phase_order = ['phase_1_core', 'phase_2_extensions', 'phase_3_gadgets', 'phase_4_other']
        
        for phase_name in phase_order:
            phase_modules = loading_plan['loading_order'][phase_name]
            if not phase_modules:
                continue
                
            print(f"[INFO] Capturing {phase_name}: {len(phase_modules)} modules")
            
            # Split phase into batches if needed
            batch_size = 50
            for i in range(0, len(phase_modules), batch_size):
                batch = phase_modules[i:i + batch_size]
                batch_name = f"{phase_name}_batch_{i//batch_size + 1}" if len(phase_modules) > batch_size else phase_name
                print(f"[INFO] Capturing {batch_name}: {len(batch)} modules")
                
                bundle_params = {
                    'debug': 'true',
                    'lang': 'en-gb',
                    'modules': '|'.join(batch),
                    'only': 'scripts',
                    'skin': 'vector'
                }
                bundle_res = session.get(f"{base}/load.php", params=bundle_params, timeout=120)
                bundle_res.raise_for_status()
                batch_content = bundle_res.text
                
                if batch_content.strip():
                    all_content.append(f"// === {batch_name.upper()} ===\n{batch_content}")

        # Combine all phases in dependency order
        bundle_content = '\n\n'.join(all_content)
        
        if not bundle_content.strip():
            raise ValueError("Received empty content for complete module bundle.")

        bundle_path = output_dir / "page_modules.js"
        bundle_path.write_text(bundle_content, encoding="utf-8")
        print(f"[SUCCESS] Saved page_modules.js ({len(bundle_content)} bytes)")
        
        # Verify that key modules are present
        critical_modules = ['jquery', 'mediawiki.base', 'oojs', 'ext.gadget.GECharts']
        found_modules = []
        missing_modules = []
        
        for module in critical_modules:
            if module in bundle_content:
                found_modules.append(module)
                print(f"[VERIFY] ✅ {module} found in bundle")
            else:
                missing_modules.append(module)
                print(f"[VERIFY] ❌ {module} NOT found in bundle")
        
        # Summary
        print(f"\n[SUMMARY] Enhanced capture completed successfully:")
        print(f"  ✅ Startup module: {startup_path}")
        print(f"  ✅ Page bootstrap: {page_bootstrap_path}")
        print(f"  ✅ Complete bundle: {bundle_path} ({len(all_required_modules)} modules)")
        print(f"  ✅ Page extraction: {page_data_path} (browser execution)")
        print(f"  ✅ Loading plan: {plan_path}")
        print(f"  ✅ Critical modules found: {len(found_modules)}/{len(critical_modules)}")
        
        if missing_modules:
            print(f"  ⚠️  Missing critical modules: {missing_modules}")

    except Exception as e:
        print(f"[ERROR] Failed to capture artifacts: {e}")
        return


def capture_artifacts_legacy(base: str, page_name: str, output_dir: Path):
    """
    Captures and saves the three core ResourceLoader artifacts.
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    session.headers.update({
        "User-Agent": "OSRSWiki-Android-Artifact-Collector/1.0"
    })

    print(f"[INFO] Capturing artifacts from base: {base}")
    print(f"[INFO] Test page: {page_name}")
    print(f"[INFO] Output directory: {output_dir}")

    # 1. Capture the complete startup module
    print("\n[PHASE 1/3] Capturing startup module...")
    try:
        startup_params = {
            'debug': 'true',
            'lang': 'en-gb',
            'modules': 'startup',
            'only': 'scripts',
            'skin': 'vector'
        }
        startup_res = session.get(f"{base}/load.php", params=startup_params, timeout=30)
        startup_res.raise_for_status()
        startup_content = startup_res.text
        startup_path = output_dir / "startup.js"
        startup_path.write_text(startup_content, encoding="utf-8")
        print(f"[SUCCESS] Saved startup.js ({len(startup_content)} bytes)")

    except Exception as e:
        print(f"[ERROR] Failed to capture startup module: {e}")
        return

    # 2. Capture the inline page bootstrap script
    print("\n[PHASE 2/3] Capturing page bootstrap script...")
    page_modules = []
    try:
        # Use mobile URL for Android app compatibility
        page_url = f"{base}/w/{page_name}?useformat=mobile"
        page_res = session.get(page_url, timeout=30)
        page_res.raise_for_status()
        html_content = page_res.text

        # FINAL STRATEGY: Match each variable independently to be more robust.
        config_match = re.search(r"RLCONF=({.*?});", html_content, re.DOTALL)
        state_match = re.search(r"RLSTATE=({.*?});", html_content, re.DOTALL)
        modules_match = re.search(r"RLPAGEMODULES=(\[.*?\]);", html_content, re.DOTALL)

        if not (config_match and state_match and modules_match):
            raise ValueError("Could not find all required variables (RLCONF, RLSTATE, RLPAGEMODULES) in page HTML.")

        rlconf_json = config_match.group(1)
        rlstate_json = state_match.group(1)
        rlpagemodules_json = modules_match.group(1)

        # Reconstruct the executable bootstrap script
        bootstrap_content = f"""
var RLCONF = {rlconf_json};
var RLSTATE = {rlstate_json};
var RLPAGEMODULES = {rlpagemodules_json};
"""
        bootstrap_path = output_dir / "page_bootstrap.js"
        bootstrap_path.write_text(bootstrap_content.strip(), encoding="utf-8")
        print(f"[SUCCESS] Saved page_bootstrap.js ({len(bootstrap_content)} bytes)")

        # Extract modules for the next phase
        page_modules = re.findall(r'"([^"]*)"', rlpagemodules_json)
        if not page_modules:
            raise ValueError("Could not parse module names from RLPAGEMODULES.")

        print(f"[INFO] Found {len(page_modules)} modules to load in the next phase.")

    except Exception as e:
        print(f"[ERROR] Failed to capture page bootstrap: {e}")
        return

    # 3. Capture the complete module bundle (page modules + dependencies)
    print("\n[PHASE 3/3] Capturing complete module bundle with dependencies...")
    try:
        if not page_modules:
            print("[WARN] No page-specific modules found to capture. Skipping.")
            return

        # Core infrastructure modules that are required but not in RLPAGEMODULES
        core_modules = [
            'mediawiki.base',    # Provides mw.loader.using, mw.message, etc.
            'jquery',            # Foundation library
            'oojs',              # Object-oriented JavaScript library
            'oojs-ui',           # UI framework
            'oojs-ui-core',      # UI core components
            'oojs-ui-widgets',   # UI widgets
            'oojs-ui-windows'    # UI windows/dialogs
        ]
        
        # GE Charts specific dependencies that may be missing
        ge_chart_modules = [
            'ext.gadget.GECharts-core',  # Core implementation
            'ext.cite.referencePreviews', # Dependency 530 of GECharts-core
        ]
        
        # Combine all modules: page modules + core infrastructure + GE dependencies
        all_modules = list(set(page_modules + core_modules + ge_chart_modules))
        
        print(f"[INFO] Requesting {len(all_modules)} modules (including dependencies):")
        print(f"[INFO] Page modules: {len(page_modules)}")
        print(f"[INFO] Core modules: {len(core_modules)}")
        print(f"[INFO] GE chart modules: {len(ge_chart_modules)}")
        
        # Log the specific modules being requested
        if len(all_modules) <= 20:  # Don't spam if too many
            print(f"[DEBUG] All modules: {all_modules}")

        bundle_params = {
            'debug': 'true',
            'lang': 'en-gb',
            'modules': '|'.join(all_modules),  # Request ALL modules
            'only': 'scripts',
            'skin': 'vector'
        }
        bundle_res = session.get(f"{base}/load.php", params=bundle_params, timeout=120)  # Longer timeout
        bundle_res.raise_for_status()
        bundle_content = bundle_res.text

        if not bundle_content.strip():
            raise ValueError("Received empty content for complete module bundle.")

        bundle_path = output_dir / "page_modules.js"
        bundle_path.write_text(bundle_content, encoding="utf-8")
        print(f"[SUCCESS] Saved page_modules.js ({len(bundle_content)} bytes)")
        
        # Verify that key modules are present
        critical_modules = ['mediawiki.base', 'ext.gadget.GECharts-core']
        for module in critical_modules:
            if module in bundle_content:
                print(f"[VERIFY] ✅ {module} found in bundle")
            else:
                print(f"[VERIFY] ❌ {module} NOT found in bundle")

    except Exception as e:
        print(f"[ERROR] Failed to capture complete module bundle: {e}")
        return


async def main():
    """
    Main execution function.
    """
    if len(sys.argv) > 1 and sys.argv[1] == "--legacy":
        print("[INFO] Using legacy capture method")
        capture_artifacts_legacy(DEFAULT_BASE, DEFAULT_TEST_PAGE, ARTIFACTS_DIR)
    else:
        print("[INFO] Using enhanced capture method with execution-based resolution")
        await capture_artifacts_enhanced(DEFAULT_BASE, DEFAULT_TEST_PAGE, ARTIFACTS_DIR)
    
    print("\n[COMPLETE] Artifact capture finished.")


if __name__ == "__main__":
    asyncio.run(main())