#!/usr/bin/env python3
"""
Automated JavaScript Module Pipeline

Unified pipeline that automates the complete process of:
1. Scanning OSRS Wiki for JavaScript widgets
2. Network tracing to capture module loading
3. Extracting MediaWiki modules and external libraries
4. Deploying everything to Android app assets

This is the single entry point for fully automated JS module management.

Usage:
  python tools/js/deploy/automated_pipeline.py --full-automation
  python tools/js/deploy/automated_pipeline.py --pages "Grand_Exchange" "Calculator:Combat" 
  python tools/js/deploy/automated_pipeline.py --trace-only
  python tools/js/deploy/automated_pipeline.py --deploy-only
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path
from typing import List, Optional

# Paths
PROJECT_ROOT = Path(__file__).resolve().parents[3]
TOOLS_JS_DIR = PROJECT_ROOT / "tools/js"
DEPLOY_DIR = TOOLS_JS_DIR / "deploy"
OUT_DIR = TOOLS_JS_DIR / "out"


class AutomatedPipeline:
    """Orchestrates the complete JavaScript module automation pipeline."""
    
    def __init__(self, dry_run: bool = False, verbose: bool = False):
        self.dry_run = dry_run
        self.verbose = verbose
        self.pipeline_log: List[str] = []
        
    def log(self, msg: str, level: str = 'info') -> None:
        """Pipeline logging."""
        prefix = {'info': '[PIPELINE]', 'warn': '[WARNING]', 'error': '[ERROR]'}.get(level, '[PIPELINE]')
        if self.dry_run:
            prefix = f"[DRY-RUN] {prefix}"
        
        timestamp = time.strftime('%H:%M:%S')
        full_msg = f"{timestamp} {prefix} {msg}"
        print(full_msg)
        self.pipeline_log.append(full_msg)
        
    def run_command(self, cmd: List[str], timeout: int = 300) -> bool:
        """Run a subprocess command with proper logging."""
        cmd_str = ' '.join(cmd)
        self.log(f"Running: {cmd_str}")
        
        if self.dry_run:
            self.log(f"[DRY-RUN] Would run: {cmd_str}")
            return True
            
        try:
            result = subprocess.run(
                cmd, 
                capture_output=True, 
                text=True, 
                timeout=timeout,
                cwd=PROJECT_ROOT
            )
            
            # Log output if verbose or if there's an error
            if result.stdout and (self.verbose or result.returncode != 0):
                for line in result.stdout.strip().split('\n'):
                    if line.strip():
                        self.log(f"  STDOUT: {line}")
                        
            if result.stderr and "NotOpenSSLWarning" not in result.stderr:  # Skip SSL warnings
                for line in result.stderr.strip().split('\n'):
                    if line.strip() and "NotOpenSSLWarning" not in line:
                        self.log(f"  STDERR: {line}", 'warn')
            
            if result.returncode == 0:
                self.log(f"✅ Command completed successfully")
                return True
            else:
                self.log(f"❌ Command failed with exit code {result.returncode}", 'error')
                return False
                
        except subprocess.TimeoutExpired:
            self.log(f"❌ Command timed out after {timeout}s", 'error')
            return False
        except Exception as e:
            self.log(f"❌ Command failed: {e}", 'error')
            return False
            
    def scan_widgets(self) -> bool:
        """Run the widget scanner to identify missing JavaScript modules."""
        self.log("🔍 Phase 1: Scanning for JavaScript widgets...")
        
        scanner_script = TOOLS_JS_DIR / "scan_widgets.py"
        if not scanner_script.exists():
            self.log("Widget scanner not found", 'error')
            return False
            
        cmd = [sys.executable, str(scanner_script)]
        if self.dry_run:
            cmd.append("--dry-run")
            
        return self.run_command(cmd)
        
    def trace_network_requests(self, pages: Optional[List[str]] = None) -> bool:
        """Run network tracing to capture module loading patterns."""
        self.log("🌐 Phase 2: Tracing network requests...")
        
        tracer_script = DEPLOY_DIR / "network_tracer.py"
        if not tracer_script.exists():
            self.log("Network tracer not found", 'error')
            return False
            
        cmd = [sys.executable, str(tracer_script)]
        
        if pages:
            cmd.extend(["--pages"] + pages)
        else:
            # Auto-detect pages from scan results
            report_file = OUT_DIR / "report.json"
            if report_file.exists():
                cmd.extend(["--auto-from-scan", str(report_file)])
            else:
                self.log("No scan report found, using default pages", 'warn')
                
        return self.run_command(cmd, timeout=600)  # Network tracing can take longer
        
    def extract_modules(self) -> bool:
        """Extract MediaWiki modules from network traces."""
        self.log("📦 Phase 3: Extracting MediaWiki modules...")
        
        extractor_script = DEPLOY_DIR / "extract_modules.py"
        if not extractor_script.exists():
            self.log("Module extractor not found", 'error')
            return False
            
        cmd = [sys.executable, str(extractor_script)]
        if self.dry_run:
            cmd.append("--dry-run")
            
        return self.run_command(cmd)
        
    def deploy_modules(self) -> bool:
        """Deploy extracted modules and external libraries to app assets."""
        self.log("🚀 Phase 4: Deploying modules to Android app...")
        
        deployer_script = DEPLOY_DIR / "deploy_modules.py"
        if not deployer_script.exists():
            self.log("Module deployer not found", 'error')
            return False
            
        cmd = [sys.executable, str(deployer_script), "--deploy-all"]
        if self.dry_run:
            cmd.append("--dry-run")
            
        return self.run_command(cmd)
        
    def validate_deployment(self) -> bool:
        """Validate that deployment was successful."""
        self.log("✅ Phase 5: Validating deployment...")
        
        # Check that key files exist
        external_dir = PROJECT_ROOT / "app/src/main/assets/web/external"
        deployment_report = PROJECT_ROOT / "app/src/main/assets/deployment_report.json"
        
        missing_files = []
        
        if not external_dir.exists():
            missing_files.append("web/external directory")
        else:
            # Check for key files
            expected_files = [
                "highcharts-stock-chisel.js",  # Critical for GE charts
                "tabber.js",
                "ge_charts_loader.js", 
                "ge_charts_core.js"
            ]
            
            for expected_file in expected_files:
                file_path = external_dir / expected_file
                if not file_path.exists():
                    missing_files.append(f"external/{expected_file}")
                    
        if not deployment_report.exists():
            missing_files.append("deployment_report.json")
            
        if missing_files:
            self.log(f"❌ Validation failed. Missing files: {', '.join(missing_files)}", 'error')
            return False
        else:
            self.log("✅ Validation passed. All expected files are present.")
            return True
            
    def generate_pipeline_report(self) -> None:
        """Generate a comprehensive pipeline execution report."""
        report_file = OUT_DIR / "pipeline_execution.json"
        
        report = {
            'pipeline_timestamp': time.time(),
            'dry_run': self.dry_run,
            'execution_log': self.pipeline_log,
            'output_files': {
                'scan_report': str(OUT_DIR / "report.json"),
                'network_trace': str(OUT_DIR / "network_trace.json"),
                'module_registry': str(OUT_DIR / "module_registry.json"),
                'deployment_report': str(OUT_DIR / "deployment_report.json"),
                'external_extraction': str(OUT_DIR / "external_library_extraction.json")
            },
            'app_integration': {
                'external_assets_dir': str(PROJECT_ROOT / "app/src/main/assets/web/external"),
                'deployment_config': str(PROJECT_ROOT / "app/src/main/assets/deployment_report.json")
            }
        }
        
        if not self.dry_run:
            with open(report_file, 'w') as f:
                json.dump(report, f, indent=2)
            self.log(f"Pipeline execution report saved: {report_file}")
        else:
            self.log(f"[DRY-RUN] Would save pipeline report to {report_file}")
            
    def run_full_pipeline(self, pages: Optional[List[str]] = None) -> bool:
        """Execute the complete automation pipeline."""
        self.log("🚀 Starting full JavaScript module automation pipeline")
        
        phases = [
            ("Widget Scanning", self.scan_widgets),
            ("Network Tracing", lambda: self.trace_network_requests(pages)),
            ("Module Extraction", self.extract_modules),
            ("Module Deployment", self.deploy_modules),
            ("Deployment Validation", self.validate_deployment)
        ]
        
        for phase_name, phase_func in phases:
            self.log(f"Starting {phase_name}...")
            
            if not phase_func():
                self.log(f"❌ Pipeline failed at {phase_name}", 'error')
                return False
                
            self.log(f"✅ {phase_name} completed successfully")
            
        self.log("🎉 Full pipeline completed successfully!")
        self.generate_pipeline_report()
        return True


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Automated JavaScript module pipeline for OSRS Wiki Android app")
    ap.add_argument("--full-automation", action="store_true", help="Run complete pipeline: scan → trace → extract → deploy")
    ap.add_argument("--pages", nargs="*", help="Specific wiki pages to trace (used with --full-automation or --trace-only)")
    ap.add_argument("--trace-only", action="store_true", help="Run only network tracing phase")
    ap.add_argument("--deploy-only", action="store_true", help="Run only module deployment phase")
    ap.add_argument("--dry-run", action="store_true", help="Show what would be done without making changes")
    ap.add_argument("--verbose", action="store_true", help="Show detailed output from all phases")
    args = ap.parse_args(argv)
    
    pipeline = AutomatedPipeline(dry_run=args.dry_run, verbose=args.verbose)
    
    if args.full_automation:
        success = pipeline.run_full_pipeline(args.pages)
    elif args.trace_only:
        success = pipeline.trace_network_requests(args.pages)
    elif args.deploy_only:
        success = pipeline.deploy_modules()
    else:
        print("Error: Must specify --full-automation, --trace-only, or --deploy-only")
        return 1
        
    pipeline.log(f"Pipeline execution {'succeeded' if success else 'failed'}")
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())