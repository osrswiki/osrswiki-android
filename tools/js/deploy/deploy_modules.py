#!/usr/bin/env python3
"""
JS Module Deployment Script

Deploys extracted MediaWiki modules to Android app assets directory.
Handles file copying, renaming, and basic integration tasks.

Phase 1: Basic deployment and app integration
Phase 2: Smart adaptation of MediaWiki compatibility layers
Phase 3: Validation and testing

Usage:
  python tools/js/deploy/deploy_modules.py --deploy-all
  python tools/js/deploy/deploy_modules.py --modules ext.Tabber ext.cite.ux-enhancements
  python tools/js/deploy/deploy_modules.py --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple


# Paths
PROJECT_ROOT = Path(__file__).resolve().parents[3]  # Go up 3 levels from tools/js/deploy/
EXTRACTION_OUT_DIR = PROJECT_ROOT / "tools/js/out"
APP_ASSETS_WEB_DIR = PROJECT_ROOT / "app/src/main/assets/web"
APP_ASSETS_EXTERNAL_DIR = PROJECT_ROOT / "app/src/main/assets/web/external"  # Third-party extracted code
APP_ASSETS_DIR = PROJECT_ROOT / "app/src/main/assets"  # Main assets directory

# Module deployment configuration
DEPLOYMENT_CONFIG = {
    # Format: "extracted_filename": {"target_name": "...", "conditional": "...", "description": "..."}
    "ext_Tabber.js": {
        "target_name": "tabber.js",
        "conditional": ".tabber",  # Load when this CSS selector is present
        "description": "Interactive tabbed interfaces",
        "priority": "high"
    },
    "ext_gadget_GECharts.js": {
        "target_name": "ge_charts_loader.js", 
        "conditional": ".GEdatachart,.GEChartBox,.GEdataprices",  # Current GE chart detection logic
        "description": "Grand Exchange chart loader stub",
        "priority": "high"
    },
    "ext_gadget_GECharts-core.js": {
        "target_name": "ge_charts_core.js",
        "conditional": ".GEdatachart,.GEChartBox,.GEdataprices",  # Load with GE charts
        "description": "Grand Exchange chart implementation core",
        "priority": "high"
    },
    "ext_cite_ux-enhancements.js": {
        "target_name": "citation_enhancements.js",
        "conditional": ".reference",  # Load when references are present
        "description": "Reference and citation UX improvements",
        "priority": "medium"
    }
}

class ModuleDeployer:
    """Handles deployment of extracted MediaWiki modules to app assets."""
    
    def __init__(self, dry_run: bool = False):
        self.dry_run = dry_run
        self.extraction_dir = EXTRACTION_OUT_DIR
        self.app_external_dir = APP_ASSETS_EXTERNAL_DIR  # Deploy to external/ subdirectory
        self.deployed_modules: List[Dict] = []
        self.deployment_log: List[str] = []
        
    def log(self, msg: str, level: str = 'info') -> None:
        """Simple logging with dry-run prefix."""
        prefix = {'info': '[INFO]', 'warn': '[WARN]', 'error': '[ERROR]'}.get(level, '[INFO]')
        if self.dry_run:
            prefix = f"[DRY-RUN] {prefix}"
        print(f"{prefix} {msg}")
        self.deployment_log.append(f"{prefix} {msg}")
        
    def load_extraction_registry(self) -> Dict:
        """Load the extraction registry to get module metadata."""
        registry_file = self.extraction_dir / "module_registry.json"
        if not registry_file.exists():
            self.log("No extraction registry found. Run extraction first.", 'error')
            return {}
            
        try:
            with open(registry_file) as f:
                return json.load(f)
        except Exception as e:
            self.log(f"Failed to load extraction registry: {e}", 'error')
            return {}
            
    def get_available_modules(self) -> List[str]:
        """Get list of available extracted modules."""
        if not self.extraction_dir.exists():
            return []
            
        extracted_files = []
        for file_path in self.extraction_dir.glob("*.js"):
            if file_path.name != "mw_compatibility.js":  # Skip compatibility file
                extracted_files.append(file_path.name)
                
        return extracted_files
        
    def analyze_module_content(self, module_file: Path) -> Dict:
        """Analyze module content for integration insights."""
        try:
            content = module_file.read_text(encoding='utf-8')
            
            analysis = {
                'file_size': len(content),
                'has_mw_compatibility': 'window.mw === \'undefined\'' in content,
                'has_jquery': '$(function' in content or 'jQuery' in content,
                'has_embedded_css': '{"css":' in content,
                'mw_apis_used': [],
                'external_dependencies': [],
                'module_dependencies': [],
                'estimated_runtime_size': 0
            }
            
            # Detect MediaWiki API usage
            mw_patterns = [
                r'mw\.config\.',
                r'mw\.util\.',
                r'mw\.loader\.',
                r'mw\.message\.',
                r'mw\.cookie\.'
            ]
            
            for pattern in mw_patterns:
                if re.search(pattern, content):
                    api_name = pattern.replace(r'mw\.', '').replace(r'\.', '')
                    analysis['mw_apis_used'].append(api_name)
            
            # Detect external library dependencies
            library_patterns = {
                'highcharts-stock.js': [r'\bHighcharts\b', r'\.highcharts\(', r'Highcharts\.'],
                'chart.js': [r'\bChart\.js\b', r'new Chart\(', r'Chart\.'],
                'd3.js': [r'\bd3\b', r'd3\.select', r'd3\.'],
                'jquery.js': [r'\bjQuery\b', r'\$\(', r'jQuery\.'],
                'moment.js': [r'\bmoment\b', r'moment\(', r'moment\.']
            }
            
            for lib_file, patterns in library_patterns.items():
                for pattern in patterns:
                    if re.search(pattern, content):
                        if lib_file not in analysis['external_dependencies']:
                            analysis['external_dependencies'].append(lib_file)
                        break  # Found this library, move to next
            
            # Detect MediaWiki module dependencies (mw.loader.load calls)
            module_load_patterns = [
                r'mw\.loader\.load\([\'"]([^\'\"]+)[\'"]',  # mw.loader.load('module.name')
                r'mw\.loader\.using\([\'"]([^\'\"]+)[\'"]'   # mw.loader.using('module.name')
            ]
            
            for pattern in module_load_patterns:
                matches = re.findall(pattern, content)
                for match in matches:
                    if match not in analysis['module_dependencies']:
                        analysis['module_dependencies'].append(match)
                    
            # Estimate actual module size (excluding compatibility layer)
            if analysis['has_mw_compatibility']:
                # Try to find where the actual module code starts
                module_start = content.find('// Adapted module:')
                if module_start > 0:
                    module_content = content[module_start:]
                    # Find the actual code inside the IIFE
                    code_match = re.search(r'\'use strict\';\s*(.*?)\s*\}\)\(\);', module_content, re.DOTALL)
                    if code_match:
                        analysis['estimated_runtime_size'] = len(code_match.group(1))
                        
            return analysis
            
        except Exception as e:
            self.log(f"Failed to analyze {module_file.name}: {e}", 'warn')
            return {'error': str(e)}
            
    def deploy_module(self, extracted_filename: str) -> bool:
        """Deploy a single module to app assets using ResourceLoader format."""
        if extracted_filename not in DEPLOYMENT_CONFIG:
            self.log(f"No deployment config for {extracted_filename}", 'warn')
            return False
            
        config = DEPLOYMENT_CONFIG[extracted_filename]
        source_file = self.extraction_dir / extracted_filename
        target_filename = config['target_name']
        target_file = self.app_external_dir / target_filename
        
        if not source_file.exists():
            self.log(f"Source file not found: {source_file}", 'error')
            return False
            
        # Read the ResourceLoader-compatible module
        try:
            content = source_file.read_text(encoding='utf-8')
        except Exception as e:
            self.log(f"Failed to read {extracted_filename}: {e}", 'error')
            return False
            
        # Analyze module content
        analysis = self.analyze_module_content(source_file)
        
        self.log(f"Deploying {extracted_filename} -> {target_filename}")
        self.log(f"  Description: {config['description']}")
        self.log(f"  Conditional loading: {config['conditional']}")
        self.log(f"  File size: {analysis.get('file_size', 'unknown')} bytes")
        
        # Extract module name from the original filename
        module_name = extracted_filename.replace('ext_', 'ext.').replace('_', '.').replace('.js', '')
        
        # Get dependencies from the extracted registry
        dependencies = self._get_module_dependencies(module_name)
        
        # Extract the actual module source from ResourceLoader format
        module_source = self._extract_module_source_from_content(content)
        
        # Create ResourceLoader deployment format using mw.loader.implement()
        resourceloader_content = self._create_resourceloader_deployment(
            module_name, module_source, dependencies, config
        )
        
        # Write the ResourceLoader-compatible deployment
        if not self.dry_run:
            try:
                target_file.write_text(resourceloader_content, encoding='utf-8')
                # Make the file read-only to prevent accidental editing
                target_file.chmod(0o444)  # Read-only for owner, group, and others
                self.log(f"  ✅ Deployed as ResourceLoader module to {target_file} (read-only)")
            except Exception as e:
                self.log(f"  ❌ Failed to write: {e}", 'error')
                return False
        else:
            self.log(f"  [DRY-RUN] Would deploy as ResourceLoader module to {target_file}")
            
        # Record deployment
        deployment_record = {
            'extracted_name': extracted_filename,
            'deployed_name': target_filename,
            'module_name': module_name,
            'dependencies': dependencies,
            'config': config,
            'analysis': analysis,
            'deployment_type': 'resourceloader'
        }
        self.deployed_modules.append(deployment_record)

        # Ensure external dependencies are available under app assets
        self._ensure_external_dependencies(analysis.get('external_dependencies', []))

        return True

    def _get_module_dependencies(self, module_name: str) -> List[str]:
        """Get dependencies for a module from the extraction registry."""
        registry = self.load_extraction_registry()
        modules = registry.get('modules', {})
        
        if module_name in modules:
            return modules[module_name].get('dependencies', [])
        
        # Fallback dependency mapping
        dependency_map = {
            'ext.gadget.GECharts': ['jquery'],
            'ext.gadget.GECharts-core': ['jquery', 'mediawiki.api'],
            'ext.Tabber': ['jquery'],
            'ext.cite.ux-enhancements': ['jquery', 'mediawiki.util']
        }
        
        return dependency_map.get(module_name, [])

    def _extract_module_source_from_content(self, content: str) -> str:
        """Extract the actual module source from ResourceLoader-compatible format."""
        # Look for the line with "// Original module source"
        lines = content.split('\n')
        source_start = -1
        
        for i, line in enumerate(lines):
            if '// Original module source' in line:
                source_start = i + 1
                break
        
        if source_start >= 0 and source_start < len(lines):
            # Get everything after the "Original module source" comment
            return '\n'.join(lines[source_start:]).strip()
        
        # Fallback: return content as-is if we can't find the marker
        return content

    def _create_resourceloader_deployment(self, module_name: str, source: str, dependencies: List[str], config: Dict) -> str:
        """Create a ResourceLoader deployment using mw.loader.implement()."""
        # Create the ResourceLoader implementation
        content = f"""/**
 * ResourceLoader Module Deployment: {module_name}
 * 
 * Description: {config.get('description', 'MediaWiki module')}
 * Conditional loading: {config.get('conditional', 'always')}
 * Dependencies: {', '.join(dependencies) if dependencies else 'none'}
 * 
 * This module is deployed using mw.loader.implement() to work with the
 * real MediaWiki ResourceLoader system provided by startup.js.
 */

// Ensure MediaWiki ResourceLoader is available
if (typeof window.mw !== 'undefined' && window.mw.loader && typeof window.mw.loader.implement === 'function') {{
    // Deploy module using ResourceLoader
    window.mw.loader.implement(
        '{module_name}',
        function($, jQuery, require, module) {{
            {source}
        }},
        {{}}, // CSS (none for now)
        {{}} // Messages (none for now)
    );
    
    console.log('[MODULE-DEPLOY] Deployed {module_name} via mw.loader.implement');
}} else {{
    console.warn('[MODULE-DEPLOY] MediaWiki ResourceLoader not available, cannot deploy {module_name}');
    
    // Fallback: Execute module source directly (not recommended but better than nothing)
    try {{
        (function($, jQuery) {{
            {source}
        }})(window.$ || window.jQuery, window.jQuery);
        console.log('[MODULE-DEPLOY] Executed {module_name} via fallback method');
    }} catch (e) {{
        console.error('[MODULE-DEPLOY] Failed to execute {module_name}:', e);
    }}
}}
"""
        return content

    def _ensure_external_dependencies(self, deps: List[str]) -> None:
        """Attempt to ensure known external libraries are present in app assets.

        For safety, this only downloads a small allowlist of widely-used libs
        to the gitignored web/external/ folder if missing.
        """
        if not deps:
            return
        # Minimal allowlist mapping filenames -> CDN URLs
        CDN = {
            'jquery.js': 'https://code.jquery.com/jquery-3.6.4.min.js',
            'chart.js': 'https://cdn.jsdelivr.net/npm/chart.js@3.9.1/dist/chart.min.js',
            # 'highcharts-stock.js': 'https://code.highcharts.com/stock/highstock.js',  # usually shipped already
        }
        try:
            import requests  # type: ignore
        except Exception:
            self.log('requests not available; skipping external dep download', 'warn')
            return

        for name in deps:
            if name not in CDN:
                continue
            target = self.app_external_dir / name
            if target.exists():
                continue
            url = CDN[name]
            try:
                self.log(f"Downloading external dependency: {name} from {url}")
                resp = requests.get(url, timeout=30)
                resp.raise_for_status()
                target.write_bytes(resp.content)
                self.log(f"  ✅ Saved {name} -> {target}")
            except Exception as e:
                self.log(f"  ❌ Failed to download {name}: {e}", 'warn')
        
    def deploy_modules(self, module_names: List[str] = None) -> bool:
        """Deploy specified modules or all available modules."""
        if module_names is None:
            # Deploy all configured modules that are available
            available = self.get_available_modules()
            module_names = [name for name in available if name in DEPLOYMENT_CONFIG]
            
        if not module_names:
            self.log("No modules to deploy", 'warn')
            return False
            
        self.log(f"Starting deployment of {len(module_names)} modules")
        
        success_count = 0
        for module_name in module_names:
            if self.deploy_module(module_name):
                success_count += 1
                
        self.log(f"Deployment complete: {success_count}/{len(module_names)} modules successful")
        return success_count == len(module_names)
        
    def update_app_integration(self) -> bool:
        """Update PageHtmlBuilder.kt to reference deployed modules."""
        if not self.deployed_modules:
            self.log("No deployed modules to integrate", 'warn')
            return False
            
        self.log("Updating app integration (PageHtmlBuilder.kt)...")
        
        # For Phase 1, just provide instructions. Phase 2 will automate this.
        self.log("⚠️  Manual integration required:")
        self.log("   1. Update PageHtmlBuilder.kt to include deployed modules")
        self.log("   2. Add conditional loading logic for each module:")
        
        for module in self.deployed_modules:
            config = module['config']
            self.log(f"      - {module['deployed_name']}: when {config['conditional']} present")
            
        self.log("   3. Test modules in WebView environment")
        return True
        
    def extract_external_libraries(self) -> bool:
        """Run external library extraction to download required CDN libraries."""
        self.log("Running external library extraction...")
        
        extractor_script = PROJECT_ROOT / "tools/js/deploy/external_library_extractor.py"
        if not extractor_script.exists():
            self.log("External library extractor not found", 'warn')
            return False
            
        try:
            cmd = [sys.executable, str(extractor_script)]
            if self.dry_run:
                cmd.append("--dry-run")
                
            # Run the extractor
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
            
            # Log the output
            if result.stdout:
                for line in result.stdout.strip().split('\n'):
                    if line.strip():
                        self.log(f"  {line}")
                        
            if result.stderr:
                for line in result.stderr.strip().split('\n'):
                    if line.strip() and "NotOpenSSLWarning" not in line:  # Skip SSL warnings
                        self.log(f"  {line}", 'warn')
            
            if result.returncode == 0:
                self.log("External library extraction completed successfully")
                return True
            else:
                self.log(f"External library extraction failed with exit code {result.returncode}", 'error')
                return False
                
        except subprocess.TimeoutExpired:
            self.log("External library extraction timed out", 'error')
            return False
        except Exception as e:
            self.log(f"Failed to run external library extractor: {e}", 'error')
            return False
    
    def deploy_infrastructure(self) -> bool:
        """Deploy MediaWiki infrastructure components."""
        self.log("Deploying MediaWiki infrastructure components...")
        
        infrastructure_script = PROJECT_ROOT / "tools/js/deploy/deploy_infrastructure.py"
        if not infrastructure_script.exists():
            self.log("Infrastructure deployment script not found", 'warn')
            return False
            
        try:
            cmd = [sys.executable, str(infrastructure_script)]
            
            # Run the infrastructure deployer
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            
            # Log the output
            if result.stdout:
                for line in result.stdout.strip().split('\n'):
                    if line.strip():
                        self.log(f"  {line}")
                        
            if result.stderr:
                for line in result.stderr.strip().split('\n'):
                    if line.strip():
                        self.log(f"  {line}", 'warn')
            
            if result.returncode == 0:
                self.log("Infrastructure deployment completed successfully")
                return True
            else:
                self.log(f"Infrastructure deployment failed with exit code {result.returncode}", 'error')
                return False
                
        except subprocess.TimeoutExpired:
            self.log("Infrastructure deployment timed out", 'error')
            return False
        except Exception as e:
            self.log(f"Failed to run infrastructure deployer: {e}", 'error')
            return False
    
    def generate_deployment_report(self) -> None:
        """Generate a deployment report."""
        if not self.deployed_modules:
            return
            
        report_file = self.extraction_dir / "deployment_report.json"
        
        report = {
            'deployment_timestamp': __import__('time').time(),
            'deployed_modules': self.deployed_modules,
            'deployment_log': self.deployment_log,
            'summary': {
                'total_deployed': len(self.deployed_modules),
                'total_size': sum(m['analysis'].get('file_size', 0) for m in self.deployed_modules),
                'integration_pending': True
            }
        }
        
        if not self.dry_run:
            with open(report_file, 'w') as f:
                json.dump(report, f, indent=2)
            self.log(f"Deployment report saved: {report_file}")
            
            # Also copy deployment report to app assets for runtime access
            app_report_file = APP_ASSETS_DIR / "deployment_report.json"
            try:
                shutil.copy2(report_file, app_report_file)
                self.log(f"Deployment report copied to app assets: {app_report_file}")
            except Exception as e:
                self.log(f"Failed to copy deployment report to app assets: {e}", 'warn')
                
            # Copy network trace report to app assets for CDN mapping
            network_trace_file = self.extraction_dir / "network_trace.json"
            app_network_trace_file = APP_ASSETS_DIR / "network_trace.json"
            if network_trace_file.exists():
                try:
                    shutil.copy2(network_trace_file, app_network_trace_file)
                    self.log(f"Network trace report copied to app assets: {app_network_trace_file}")
                except Exception as e:
                    self.log(f"Failed to copy network trace to app assets: {e}", 'warn')
            else:
                self.log("Network trace report not found, CDN mapping will be unavailable", 'warn')
        else:
            self.log(f"[DRY-RUN] Would save deployment report to {report_file}")
            self.log(f"[DRY-RUN] Would copy deployment report to {APP_ASSETS_DIR / 'deployment_report.json'}")
            

def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Deploy extracted MediaWiki modules to Android app assets")
    ap.add_argument("--deploy-all", action="store_true", help="Deploy all available configured modules")
    ap.add_argument("--modules", nargs="*", help="Specific extracted module files to deploy")
    ap.add_argument("--dry-run", action="store_true", help="Show what would be deployed without making changes")
    ap.add_argument("--list-available", action="store_true", help="List available extracted modules")
    args = ap.parse_args(argv)
    
    deployer = ModuleDeployer(dry_run=args.dry_run)
    
    if args.list_available:
        available = deployer.get_available_modules()
        configured = [name for name in available if name in DEPLOYMENT_CONFIG]
        
        print(f"Available extracted modules: {len(available)}")
        for module in available:
            status = "✅ Configured" if module in DEPLOYMENT_CONFIG else "⚠️  Not configured"
            print(f"  - {module} ({status})")
            
        print(f"\nConfigured for deployment: {len(configured)}")
        return 0
    
    # Determine which modules to deploy
    modules_to_deploy = []
    if args.deploy_all:
        available = deployer.get_available_modules()
        modules_to_deploy = [name for name in available if name in DEPLOYMENT_CONFIG]
    elif args.modules:
        modules_to_deploy = args.modules
    else:
        print("Error: Specify --deploy-all, --modules, or --list-available")
        return 1
    
    if not modules_to_deploy:
        print("No modules to deploy")
        return 1
        
    # Run deployment
    success = deployer.deploy_modules(modules_to_deploy)
    
    # Extract external libraries (CDN dependencies)
    external_success = deployer.extract_external_libraries()
    if not external_success:
        deployer.log("Warning: External library extraction failed, but continuing with deployment", 'warn')
    
    # Skip infrastructure deployment - startup.js already provides complete ResourceLoader system
    deployer.log("Skipping infrastructure deployment (startup.js provides complete ResourceLoader system)", 'info')
    
    deployer.update_app_integration() 
    deployer.generate_deployment_report()
    
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())
