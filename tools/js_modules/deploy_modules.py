#!/usr/bin/env python3
"""
JS Module Deployment Script

Deploys extracted MediaWiki modules to Android app assets directory.
Handles file copying, renaming, and basic integration tasks.

Phase 1: Basic deployment and app integration
Phase 2: Smart adaptation of MediaWiki compatibility layers
Phase 3: Validation and testing

Usage:
  python tools/js_modules/deploy_modules.py --deploy-all
  python tools/js_modules/deploy_modules.py --modules ext.Tabber ext.cite.ux-enhancements
  python tools/js_modules/deploy_modules.py --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple


# Paths
PROJECT_ROOT = Path(__file__).resolve().parents[2]
EXTRACTION_OUT_DIR = PROJECT_ROOT / "tools/js_modules/out"
APP_ASSETS_WEB_DIR = PROJECT_ROOT / "app/src/main/assets/web"
APP_ASSETS_EXTERNAL_DIR = PROJECT_ROOT / "app/src/main/assets/web/external"  # Third-party extracted code

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
        """Deploy a single module to app assets."""
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
            
        # Analyze module content
        analysis = self.analyze_module_content(source_file)
        
        self.log(f"Deploying {extracted_filename} -> {target_filename}")
        self.log(f"  Description: {config['description']}")
        self.log(f"  Conditional loading: {config['conditional']}")
        self.log(f"  File size: {analysis.get('file_size', 'unknown')} bytes")
        
        if analysis.get('estimated_runtime_size'):
            self.log(f"  Actual module size: ~{analysis['estimated_runtime_size']} bytes")
            
        if analysis.get('mw_apis_used'):
            self.log(f"  MediaWiki APIs used: {', '.join(analysis['mw_apis_used'])}")
            
        # Copy file
        if not self.dry_run:
            try:
                shutil.copy2(source_file, target_file)
                self.log(f"  ✅ Copied to {target_file}")
            except Exception as e:
                self.log(f"  ❌ Failed to copy: {e}", 'error')
                return False
        else:
            self.log(f"  [DRY-RUN] Would copy to {target_file}")
            
        # Record deployment
        deployment_record = {
            'extracted_name': extracted_filename,
            'deployed_name': target_filename,
            'config': config,
            'analysis': analysis
        }
        self.deployed_modules.append(deployment_record)
        
        return True
        
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
        else:
            self.log(f"[DRY-RUN] Would save deployment report to {report_file}")
            

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
    deployer.update_app_integration() 
    deployer.generate_deployment_report()
    
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())