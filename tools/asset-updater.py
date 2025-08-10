#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
OSRSWiki Meta Asset Updater

A unified wrapper that orchestrates both map and CSS asset generation tools.
Provides a single entry point for updating all external assets used by the app.

Features:
- Unified interface for map and CSS updates
- Pass-through CLI arguments to underlying tools
- Coordinated progress reporting
- Comprehensive error handling and validation
- Summary reporting of all updates performed
"""

import argparse
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import List, Optional, Tuple


class Colors:
    """ANSI color codes for terminal output"""
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BOLD = '\033[1m'
    CYAN = '\033[96m'
    END = '\033[0m'


class MetaAssetUpdater:
    """Meta wrapper for coordinating all asset update tools"""
    
    def __init__(self):
        self.script_dir = Path(__file__).parent
        self.map_tool = self.script_dir / "map" / "map-asset-generator.py"
        self.css_tool = self.script_dir / "css" / "css-perfect-sync.py"
        
        # Track execution results
        self.results = {
            'map': {'attempted': False, 'success': False, 'message': ''},
            'css': {'attempted': False, 'success': False, 'message': ''}
        }
        
    def log(self, message: str, color: str = Colors.BLUE):
        """Log a message with timestamp and color"""
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        print(f"{color}[{timestamp}] {message}{Colors.END}")
        
    def log_success(self, message: str):
        """Log a success message"""
        self.log(f"✅ {message}", Colors.GREEN)
        
    def log_warning(self, message: str):
        """Log a warning message"""
        self.log(f"⚠️  {message}", Colors.YELLOW)
        
    def log_error(self, message: str):
        """Log an error message"""
        self.log(f"❌ {message}", Colors.RED)
        
    def log_info(self, message: str):
        """Log an info message"""
        self.log(f"ℹ️  {message}", Colors.BLUE)
        
    def log_section(self, message: str):
        """Log a section header"""
        self.log(f"\n{'='*60}", Colors.CYAN)
        self.log(f"🔧 {message.upper()}", Colors.CYAN)
        self.log(f"{'='*60}", Colors.CYAN)
        
    def validate_environment(self) -> bool:
        """Validate that all required tools and dependencies exist"""
        self.log_info("Validating environment and dependencies...")
        
        # Check that individual tools exist
        missing_tools = []
        
        if not self.map_tool.exists():
            missing_tools.append(f"Map tool: {self.map_tool}")
        if not self.css_tool.exists():
            missing_tools.append(f"CSS tool: {self.css_tool}")
            
        if missing_tools:
            self.log_error("Missing required tools:")
            for tool in missing_tools:
                self.log_error(f"  - {tool}")
            return False
            
        # Check that tools are executable
        if not os.access(self.map_tool, os.X_OK):
            self.log_error(f"Map tool is not executable: {self.map_tool}")
            return False
        if not os.access(self.css_tool, os.X_OK):
            self.log_error(f"CSS tool is not executable: {self.css_tool}")
            return False
        
        # Check for micromamba
        micromamba_path = self.script_dir / "bin" / "micromamba"
        if not micromamba_path.exists():
            self.log_error(f"Micromamba not found at {micromamba_path}")
            return False
            
        self.log_success("Environment validation passed")
        return True
        
    def get_micromamba_command_prefix(self) -> List[str]:
        """Get the command prefix for running tools in the osrs-tools environment"""
        micromamba_path = self.script_dir / "bin" / "micromamba"
        return [str(micromamba_path), "run", "-n", "osrs-tools"]
        
    def run_tool(self, tool_path: Path, tool_name: str, args: List[str] = None) -> Tuple[bool, str]:
        """Run an individual tool and return success status and message"""
        if args is None:
            args = []
            
        # Use micromamba to run the tool in the correct environment
        micromamba_prefix = self.get_micromamba_command_prefix()
        cmd = micromamba_prefix + ["python3", str(tool_path)] + args
        self.log_info(f"Executing {tool_name} in osrs-tools environment...")
        self.log(f"Command: {' '.join(cmd)}", Colors.BLUE)
        
        try:
            start_time = time.time()
            result = subprocess.run(
                cmd,
                cwd=self.script_dir,
                capture_output=True,
                text=True,
                timeout=1800  # 30 minute timeout
            )
            
            duration = time.time() - start_time
            
            if result.returncode == 0:
                message = f"{tool_name} completed successfully in {duration:.1f}s"
                self.log_success(message)
                
                # Show relevant output if not too verbose
                if result.stdout.strip():
                    lines = result.stdout.strip().split('\n')
                    # Show last few lines of output for context
                    for line in lines[-5:]:
                        if line.strip():
                            self.log(f"  {line}", Colors.BLUE)
                            
                return True, message
            else:
                error_msg = f"{tool_name} failed with exit code {result.returncode}"
                self.log_error(error_msg)
                
                # Show error output
                if result.stderr.strip():
                    self.log_error("Error output:")
                    for line in result.stderr.strip().split('\n')[-10:]:  # Last 10 lines
                        if line.strip():
                            self.log_error(f"  {line}")
                            
                return False, error_msg
                
        except subprocess.TimeoutExpired:
            error_msg = f"{tool_name} timed out after 30 minutes"
            self.log_error(error_msg)
            return False, error_msg
        except Exception as e:
            error_msg = f"{tool_name} failed with exception: {e}"
            self.log_error(error_msg)
            return False, error_msg
            
    def update_map_assets(self, pass_through_args: List[str] = None) -> bool:
        """Update map assets using the map asset generator"""
        self.log_section("Map Asset Update")
        self.results['map']['attempted'] = True
        
        # Filter pass-through args for map tool (remove CSS-specific args)
        map_args = []
        if pass_through_args:
            # Map tool supports: --force, --dry-run, --verify, --check-freshness
            supported_args = ['--force', '--dry-run', '--verify', '--check-freshness']
            map_args = [arg for arg in pass_through_args if arg in supported_args]
            
        success, message = self.run_tool(self.map_tool, "Map Asset Generator", map_args)
        self.results['map']['success'] = success
        self.results['map']['message'] = message
        
        return success
        
    def update_css_assets(self, pass_through_args: List[str] = None) -> bool:
        """Update CSS assets using the CSS perfect sync tool"""
        self.log_section("CSS Asset Update")
        self.results['css']['attempted'] = True
        
        # CSS perfect sync tool doesn't take arguments currently, so we ignore pass-through args
        success, message = self.run_tool(self.css_tool, "CSS Perfect Sync")
        self.results['css']['success'] = success
        self.results['css']['message'] = message
        
        return success
        
    def print_summary(self):
        """Print a comprehensive summary of all operations performed"""
        self.log_section("Update Summary")
        
        total_attempted = sum(1 for r in self.results.values() if r['attempted'])
        total_successful = sum(1 for r in self.results.values() if r['success'])
        
        if total_attempted == 0:
            self.log_warning("No update operations were attempted")
            return
            
        self.log_info(f"Operations completed: {total_successful}/{total_attempted}")
        
        # Detailed results
        for tool_name, result in self.results.items():
            if result['attempted']:
                status = "✅ SUCCESS" if result['success'] else "❌ FAILED"
                color = Colors.GREEN if result['success'] else Colors.RED
                self.log(f"{status} - {tool_name.upper()}: {result['message']}", color)
                
        # Overall result
        if total_successful == total_attempted:
            self.log_success(f"🎉 All asset updates completed successfully!")
        elif total_successful > 0:
            self.log_warning(f"⚠️  Partial success: {total_successful}/{total_attempted} updates completed")
        else:
            self.log_error("❌ All asset updates failed")
            
    def run_updates(self, update_maps: bool, update_css: bool, pass_through_args: List[str] = None) -> bool:
        """Run the specified updates"""
        if not update_maps and not update_css:
            self.log_error("No update types specified. Use --all, --maps, or --css")
            return False
            
        # Validate environment first
        if not self.validate_environment():
            return False
            
        overall_success = True
        
        # Run updates in logical order (maps first, then CSS)
        if update_maps:
            if not self.update_map_assets(pass_through_args):
                overall_success = False
                
        if update_css:
            if not self.update_css_assets(pass_through_args):
                overall_success = False
                
        return overall_success


def main():
    """Main entry point for the meta asset updater"""
    parser = argparse.ArgumentParser(
        description="OSRSWiki Meta Asset Updater - Unified interface for all asset generation tools",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s --all                    Update both maps and CSS
  %(prog)s --maps --force           Force update map assets only
  %(prog)s --css                    Update CSS assets only
  %(prog)s --all --dry-run          Preview what would be updated
  %(prog)s --all --verify           Verify all assets exist and are up to date
        """
    )
    
    # Update target selection (mutually exclusive group for clarity)
    target_group = parser.add_mutually_exclusive_group(required=True)
    target_group.add_argument(
        "--all",
        action="store_true",
        help="Update both map and CSS assets"
    )
    target_group.add_argument(
        "--maps",
        action="store_true", 
        help="Update only map assets"
    )
    target_group.add_argument(
        "--css",
        action="store_true",
        help="Update only CSS assets"
    )
    
    # Pass-through arguments for underlying tools
    parser.add_argument(
        "--force",
        action="store_true",
        help="Force regeneration even if assets are up to date (maps only)"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be done without executing (maps only)"
    )
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Only verify that assets exist and are accessible (maps only)"
    )
    parser.add_argument(
        "--check-freshness",
        action="store_true",
        help="Only check if local assets are up to date (maps only)"
    )
    
    args = parser.parse_args()
    
    # Print header
    print(f"{Colors.BOLD}🚀 OSRSWiki Meta Asset Updater{Colors.END}")
    print(f"{Colors.BOLD}{'='*60}{Colors.END}")
    
    # Determine what to update
    update_maps = args.all or args.maps
    update_css = args.all or args.css
    
    # Build pass-through arguments
    pass_through_args = []
    if args.force:
        pass_through_args.append('--force')
    if args.dry_run:
        pass_through_args.append('--dry-run')
    if args.verify:
        pass_through_args.append('--verify')
    if args.check_freshness:
        pass_through_args.append('--check-freshness')
    
    # Create updater and run
    updater = MetaAssetUpdater()
    
    try:
        success = updater.run_updates(update_maps, update_css, pass_through_args)
        updater.print_summary()
        
        sys.exit(0 if success else 1)
        
    except KeyboardInterrupt:
        updater.log_warning("Operation cancelled by user")
        updater.print_summary()
        sys.exit(1)
    except Exception as e:
        updater.log_error(f"Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        updater.print_summary()
        sys.exit(1)


if __name__ == "__main__":
    main()