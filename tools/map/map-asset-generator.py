#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
OSRSWiki Map Asset Generator

A comprehensive wrapper tool that automates the entire map asset generation workflow
with intelligent freshness checking against the OpenRS2 Archive API.

Features:
- Automatic freshness checking against OpenRS2 cache versions
- Complete 4-step workflow automation
- Progress reporting and error handling
- Support for --force, --dry-run, and --verify modes
- Dependency validation
"""

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

# Import requests later in check_dependencies to provide better error message
try:
    import requests
except ImportError:
    requests = None

class Colors:
    """ANSI color codes for terminal output"""
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BOLD = '\033[1m'
    END = '\033[0m'

class MapAssetGenerator:
    def __init__(self):
        self.script_dir = Path(__file__).parent
        self.tools_dir = self.script_dir.parent
        self.project_root = self.tools_dir.parent
        self.assets_dir = self.project_root / "app" / "src" / "main" / "assets"
        self.cache_dir = self.script_dir / "openrs2_cache"
        self.version_file = self.cache_dir / "cache.version"
        self.dumper_output_dir = self.script_dir / "map-dumper" / "output"
        
        # Expected output files
        self.expected_images = [f"img-{i}.png" for i in range(4)]
        self.expected_mbtiles = [f"map_floor_{i}.mbtiles" for i in range(4)]
        
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

    def check_dependencies(self) -> bool:
        """Check if all required dependencies are available"""
        self.log_info("Checking dependencies...")
        
        # Check Python dependencies
        missing_deps = []
        
        if requests is None:
            missing_deps.append("requests")
            
        try:
            import numpy
        except ImportError:
            missing_deps.append("numpy")
            
        try:
            from PIL import Image
        except ImportError:
            missing_deps.append("pillow")
            
        if missing_deps:
            self.log_error(f"Missing Python dependencies: {', '.join(missing_deps)}")
            self.log_error("Please activate the osrs-tools micromamba environment:")
            self.log_error("  micromamba activate osrs-tools")
            return False
            
        # Check Java
        try:
            result = subprocess.run(['java', '-version'], 
                                  capture_output=True, text=True, timeout=10)
            if result.returncode != 0:
                self.log_error("Java not found. Please install JDK 11 or higher")
                return False
        except (subprocess.TimeoutExpired, FileNotFoundError):
            self.log_error("Java not found. Please install JDK 11 or higher")
            return False
            
        # Check required scripts exist
        required_scripts = [
            self.script_dir / "setup_cache.py",
            self.script_dir / "update_xteas.py", 
            self.script_dir / "run_dumper.sh",
            self.script_dir / "slice_tiles.py"
        ]
        
        for script in required_scripts:
            if not script.exists():
                self.log_error(f"Required script not found: {script}")
                return False
                
        self.log_success("All dependencies available")
        return True

    def get_latest_cache_info(self) -> dict:
        """Get the latest OSRS cache information from OpenRS2 API"""
        if requests is None:
            raise RuntimeError("requests module not available. Please activate osrs-tools environment.")
            
        self.log_info("Fetching latest cache information from OpenRS2...")
        
        try:
            response = requests.get("https://archive.openrs2.org/caches.json", timeout=30)
            response.raise_for_status()
            all_caches = response.json()
            
            latest_cache = None
            latest_timestamp = datetime.min.replace(tzinfo=None)
            
            for cache in all_caches:
                if cache.get("game") == "oldschool" and cache.get("environment") == "live":
                    timestamp_str = cache.get("timestamp")
                    if timestamp_str:
                        # Handle both Z and timezone formats
                        timestamp_str = timestamp_str.replace("Z", "")
                        if "+" in timestamp_str:
                            timestamp_str = timestamp_str.split("+")[0]
                        timestamp = datetime.fromisoformat(timestamp_str).replace(tzinfo=None)
                        
                        if not latest_cache or timestamp > latest_timestamp:
                            latest_timestamp = timestamp
                            latest_cache = cache
                            
            if not latest_cache:
                raise RuntimeError("Could not find a valid live OSRS cache")
                
            self.log_success(f"Latest cache: ID {latest_cache['id']} from {latest_timestamp.isoformat()}")
            return latest_cache
            
        except requests.RequestException as e:
            self.log_error(f"Failed to fetch cache information: {e}")
            raise
        except Exception as e:
            self.log_error(f"Error processing cache information: {e}")
            raise

    def get_local_cache_version(self) -> int:
        """Get the currently installed cache version"""
        if not self.version_file.exists():
            return None
            
        try:
            with open(self.version_file, 'r') as f:
                return int(f.read().strip())
        except (ValueError, IOError):
            return None

    def is_cache_outdated(self, force: bool = False) -> tuple[bool, dict]:
        """
        Check if the local cache is outdated compared to the server
        Returns (is_outdated, latest_cache_info)
        """
        if force:
            self.log_info("Forcing update (--force flag specified)")
            return True, self.get_latest_cache_info()
            
        latest_cache = self.get_latest_cache_info()
        latest_id = latest_cache['id']
        current_id = self.get_local_cache_version()
        
        self.log_info(f"Latest available cache ID: {latest_id}")
        self.log_info(f"Currently installed cache ID: {current_id or 'None'}")
        
        is_outdated = current_id != latest_id or not (self.cache_dir / "cache").exists()
        
        if is_outdated:
            self.log_warning("Local cache is outdated or missing")
        else:
            self.log_success("Local cache is up to date")
            
        return is_outdated, latest_cache

    def verify_mbtiles_exist(self) -> bool:
        """Verify that all expected mbtiles files exist"""
        missing_files = []
        for filename in self.expected_mbtiles:
            filepath = self.assets_dir / filename
            if not filepath.exists():
                missing_files.append(filename)
                
        if missing_files:
            self.log_warning(f"Missing mbtiles files: {', '.join(missing_files)}")
            return False
        else:
            self.log_success("All mbtiles files present")
            return True

    def run_command(self, cmd: list, description: str, cwd: Path = None) -> bool:
        """Run a command with proper error handling and logging"""
        self.log_info(f"Running: {description}")
        self.log(f"Command: {' '.join(cmd)}", Colors.BLUE)
        
        try:
            result = subprocess.run(
                cmd, 
                cwd=cwd or self.script_dir,
                capture_output=True, 
                text=True,
                timeout=1800  # 30 minutes timeout
            )
            
            if result.returncode == 0:
                self.log_success(f"Completed: {description}")
                if result.stdout.strip():
                    print(result.stdout)
                return True
            else:
                self.log_error(f"Failed: {description}")
                if result.stderr.strip():
                    print(f"Error output: {result.stderr}")
                if result.stdout.strip():
                    print(f"Standard output: {result.stdout}")
                return False
                
        except subprocess.TimeoutExpired:
            self.log_error(f"Timeout: {description}")
            return False
        except Exception as e:
            self.log_error(f"Exception running {description}: {e}")
            return False

    def step_setup_cache(self) -> bool:
        """Step 1: Setup or update the game cache"""
        return self.run_command(
            [sys.executable, "setup_cache.py"],
            "Setting up game cache"
        )

    def step_update_xteas(self) -> bool:
        """Step 2: Update XTEA decryption keys"""
        return self.run_command(
            [sys.executable, "update_xteas.py"],
            "Updating XTEA keys"
        )

    def step_dump_images(self) -> bool:
        """Step 3: Dump complete map images"""
        # Make sure the script is executable
        dumper_script = self.script_dir / "run_dumper.sh"
        dumper_script.chmod(0o755)
        
        success = self.run_command(
            ["./run_dumper.sh"],
            "Dumping map images"
        )
        
        if success:
            # Verify that expected images were created
            missing_images = []
            for img_name in self.expected_images:
                img_path = self.dumper_output_dir / img_name
                if not img_path.exists():
                    missing_images.append(img_name)
                    
            if missing_images:
                self.log_error(f"Expected images not created: {', '.join(missing_images)}")
                return False
            else:
                self.log_success(f"All {len(self.expected_images)} map images created")
                
        return success

    def step_slice_tiles(self) -> bool:
        """Step 4: Slice tiles into mbtiles"""
        success = self.run_command(
            [sys.executable, "slice_tiles.py"],
            "Slicing tiles into mbtiles"
        )
        
        if success:
            # Verify that mbtiles files were created
            self.verify_mbtiles_exist()
            
        return success

    def run_full_workflow(self, force: bool = False, dry_run: bool = False) -> bool:
        """Run the complete 4-step workflow"""
        self.log(f"{Colors.BOLD}🚀 Starting map asset generation workflow{Colors.END}")
        
        # Check if update is needed
        is_outdated, latest_cache = self.is_cache_outdated(force)
        
        if not is_outdated and self.verify_mbtiles_exist():
            self.log_success("Assets are up to date. Nothing to do.")
            if not force:
                return True
        
        if dry_run:
            self.log_info("DRY RUN: Would execute the following steps:")
            self.log_info("1. Setup/update game cache")
            self.log_info("2. Update XTEA keys") 
            self.log_info("3. Dump map images")
            self.log_info("4. Slice tiles into mbtiles")
            return True
            
        # Execute the workflow
        steps = [
            ("Setup Cache", self.step_setup_cache),
            ("Update XTEAs", self.step_update_xteas), 
            ("Dump Images", self.step_dump_images),
            ("Slice Tiles", self.step_slice_tiles)
        ]
        
        start_time = time.time()
        
        for i, (step_name, step_func) in enumerate(steps, 1):
            self.log(f"{Colors.BOLD}📋 Step {i}/4: {step_name}{Colors.END}")
            
            if not step_func():
                self.log_error(f"Workflow failed at step {i}: {step_name}")
                return False
                
        elapsed = time.time() - start_time
        self.log_success(f"Workflow completed successfully in {elapsed:.1f} seconds")
        
        # Final verification
        if self.verify_mbtiles_exist():
            self.log_success("All map assets generated and verified")
            return True
        else:
            self.log_error("Workflow completed but assets verification failed")
            return False

def main():
    parser = argparse.ArgumentParser(
        description="OSRSWiki Map Asset Generator - Automated workflow with freshness checking"
    )
    parser.add_argument(
        "--force", 
        action="store_true", 
        help="Force regeneration even if assets are up to date"
    )
    parser.add_argument(
        "--dry-run", 
        action="store_true", 
        help="Show what would be done without executing"
    )
    parser.add_argument(
        "--verify", 
        action="store_true", 
        help="Only verify that assets exist and are accessible"
    )
    parser.add_argument(
        "--check-freshness", 
        action="store_true", 
        help="Only check if local assets are up to date"
    )
    
    args = parser.parse_args()
    
    generator = MapAssetGenerator()
    
    # Check dependencies first
    if not generator.check_dependencies():
        sys.exit(1)
    
    try:
        if args.verify:
            # Verification mode
            generator.log_info("Running asset verification...")
            if generator.verify_mbtiles_exist():
                generator.log_success("Asset verification passed")
                sys.exit(0)
            else:
                generator.log_error("Asset verification failed")
                sys.exit(1)
                
        elif args.check_freshness:
            # Freshness check mode
            is_outdated, latest_cache = generator.is_cache_outdated()
            if is_outdated:
                generator.log_warning("Assets are outdated")
                sys.exit(1)
            else:
                generator.log_success("Assets are up to date")
                sys.exit(0)
                
        else:
            # Full workflow mode
            success = generator.run_full_workflow(
                force=args.force, 
                dry_run=args.dry_run
            )
            
            sys.exit(0 if success else 1)
            
    except KeyboardInterrupt:
        generator.log_warning("Operation cancelled by user")
        sys.exit(1)
    except Exception as e:
        generator.log_error(f"Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()