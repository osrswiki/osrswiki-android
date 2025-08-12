#!/usr/bin/env python3
"""
OSRS Wiki Artifact Collector

Captures the necessary JavaScript artifacts from a live wiki page to allow
for a faithful "capture-and-replay" of the MediaWiki ResourceLoader engine
in a controlled environment.

This script captures:
1.  The complete startup module (`startup.js`).
2.  The inline page bootstrap script from the page HTML (`page_bootstrap.js`).
3.  The bundled modules required by that specific page (`page_modules.js`).
"""

import os
import re
import json
import requests
from pathlib import Path

DEFAULT_BASE = "https://oldschool.runescape.wiki"
DEFAULT_TEST_PAGE = "Logs"
ARTIFACTS_DIR = Path(__file__).parent.parent / "artifacts"

def capture_artifacts(base: str, page_name: str, output_dir: Path):
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
        page_url = f"{base}/w/{page_name}"
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

    # 3. Capture the page-specific module bundle
    print("\n[PHASE 3/3] Capturing page module bundle...")
    try:
        if not page_modules:
            print("[WARN] No page-specific modules found to capture. Skipping.")
            return

        bundle_params = {
            'debug': 'true',
            'lang': 'en-gb',
            'modules': '|'.join(page_modules),
            'only': 'scripts',
            'skin': 'vector'
        }
        bundle_res = session.get(f"{base}/load.php", params=bundle_params, timeout=60)
        bundle_res.raise_for_status()
        bundle_content = bundle_res.text

        if not bundle_content.strip():
            raise ValueError("Received empty content for page module bundle.")

        bundle_path = output_dir / "page_modules.js"
        bundle_path.write_text(bundle_content, encoding="utf-8")
        print(f"[SUCCESS] Saved page_modules.js ({len(bundle_content)} bytes)")

    except Exception as e:
        print(f"[ERROR] Failed to capture page module bundle: {e}")
        return


def main():
    """
    Main execution function.
    """
    capture_artifacts(DEFAULT_BASE, DEFAULT_TEST_PAGE, ARTIFACTS_DIR)
    print("\n[COMPLETE] Artifact capture finished.")


if __name__ == "__main__":
    main()