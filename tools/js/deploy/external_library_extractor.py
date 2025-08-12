#!/usr/bin/env python3
"""
External JavaScript Library Extractor

Automatically downloads external JavaScript libraries detected by network tracing
and deploys them to the Android app assets directory.

This handles CDN libraries like Highcharts, jQuery, D3, etc. that are required
by MediaWiki modules but hosted externally.

Usage:
  python tools/js/deploy/external_library_extractor.py --from-trace tools/js/out/network_trace.json
  python tools/js/deploy/external_library_extractor.py --download-all
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Dict, List, Optional, Set
from urllib.parse import urlparse

try:
    import requests
except ImportError:
    print("Error: requests not installed. Run: pip install requests", file=sys.stderr)
    sys.exit(1)


# Paths
PROJECT_ROOT = Path(__file__).resolve().parents[3]  # Go up 3 levels from tools/js/deploy/
APP_ASSETS_EXTERNAL_DIR = PROJECT_ROOT / "app/src/main/assets/web/external"
TRACE_OUTPUT_DIR = PROJECT_ROOT / "tools/js/out"


class ExternalLibraryExtractor:
    """Downloads and manages external JavaScript libraries for the Android app."""
    
    def __init__(self, dry_run: bool = False):
        self.dry_run = dry_run
        self.external_dir = APP_ASSETS_EXTERNAL_DIR
        self.external_dir.mkdir(parents=True, exist_ok=True)
        self.download_log: List[str] = []
        self.downloaded_libraries: List[Dict] = []
        
    def log(self, msg: str, level: str = 'info') -> None:
        """Simple logging with dry-run prefix."""
        prefix = {'info': '[INFO]', 'warn': '[WARN]', 'error': '[ERROR]'}.get(level, '[INFO]')
        if self.dry_run:
            prefix = f"[DRY-RUN] {prefix}"
        print(f"{prefix} {msg}")
        self.download_log.append(f"{prefix} {msg}")
        
    def standardize_library_filename(self, library_name: str, url: str) -> str:
        """Generate standardized filename for external JavaScript libraries.
        
        Uses pattern-based rules to convert detected library names into 
        standard filenames that match module expectations.
        """
        # Standardization rules: pattern -> standard filename
        standardization_rules = [
            # Highcharts family
            (r'highcharts|highstock|highmaps', 'highcharts-stock.js'),
            
            # jQuery family  
            (r'jquery', 'jquery.js'),
            
            # Chart libraries
            (r'chart\.?js', 'chart.js'),
            (r'plotly', 'plotly.js'),
            (r'd3(?:\.js)?', 'd3.js'),
            
            # Utility libraries
            (r'moment(?:\.js)?', 'moment.js'),
            (r'lodash', 'lodash.js'),
            (r'three(?:\.js)?', 'three.js'),
            
            # UI frameworks
            (r'bootstrap', 'bootstrap.js'),
            (r'datatables?', 'datatables.js'),
            
            # Add more rules as needed...
        ]
        
        library_lower = library_name.lower()
        
        # Apply standardization rules in order
        for pattern, standard_name in standardization_rules:
            if re.search(pattern, library_lower):
                self.log(f"  Standardized '{library_name}' -> '{standard_name}' (pattern: {pattern})")
                return standard_name
                
        # Fallback: clean up the detected name
        return self.sanitize_filename(library_name, url)
        
    def sanitize_filename(self, library_name: str, url: str) -> str:
        """Generate a safe filename for a library based on name and URL (fallback)."""
        parsed = urlparse(url)
        original_filename = Path(parsed.path).name
        
        # If we have a good original filename, use it
        if original_filename and original_filename.endswith('.js'):
            # Clean up the filename but preserve important parts
            clean_name = re.sub(r'[^a-zA-Z0-9._-]', '', original_filename)
            if clean_name:
                return clean_name
                
        # Fallback: generate filename from library name
        safe_name = re.sub(r'[^a-zA-Z0-9._-]', '', library_name.lower())
        if not safe_name:
            safe_name = 'unknown'
            
        # Add .js extension if not present
        if not safe_name.endswith('.js'):
            safe_name += '.js'
            
        return safe_name
        
    def get_file_hash(self, content: bytes) -> str:
        """Get SHA-256 hash of file content for integrity checking."""
        return hashlib.sha256(content).hexdigest()[:16]  # First 16 chars for brevity
        
    def should_download_library(self, library_info: Dict) -> bool:
        """Determine if a library should be downloaded based on various criteria."""
        url = library_info['url']
        library_name = library_info['library']
        parsed = urlparse(url)
        
        # Skip unknown libraries for safety
        if library_name == 'unknown':
            self.log(f"Skipping unknown library: {url}", 'warn')
            return False
            
        # Skip if URL looks suspicious
        if not parsed.scheme in ['https', 'http']:
            self.log(f"Skipping non-HTTP(S) URL: {url}", 'warn')
            return False
            
        # Skip if host looks suspicious
        suspicious_hosts = ['localhost', '127.0.0.1', '0.0.0.0']
        if any(suspicious in parsed.netloc.lower() for suspicious in suspicious_hosts):
            self.log(f"Skipping suspicious host: {url}", 'warn')
            return False
            
        # Skip advertising/tracking scripts
        ad_tracking_indicators = [
            'tags.crwdcntrl.net',
            'secure.cdn.fastclick.net',
            'csync.smartadserver.com',
            'googleadservices.com',
            'doubleclick.net',
            'googlesyndication.com',
            'amazon-adsystem.com',
            'facebook.com/tr',
            'google-analytics.com',
            'googletagmanager.com',
            'hotjar.com',
            'quantserve.com',
            '/ads/',
            '/tracking/',
            '/analytics/',
            'adsystem',
            'advertisement'
        ]
        
        if any(indicator in url.lower() for indicator in ad_tracking_indicators):
            self.log(f"Skipping advertising/tracking script: {library_name} ({url})", 'warn')
            return False
            
        # Allow known legitimate library hosts
        legitimate_hosts = [
            'chisel.weirdgloop.org',  # Weird Gloop CDN (Highcharts for OSRS)
            'cdnjs.cloudflare.com',
            'cdn.jsdelivr.net',
            'unpkg.com',
            'code.highcharts.com',
            'ajax.googleapis.com',
            'maxcdn.bootstrapcdn.com',
            'stackpath.bootstrapcdn.com',
            'cdn.plot.ly',
            'd3js.org',
            'code.jquery.com',
            'cdn.datatables.net'
        ]
        
        # Allow if host is in legitimate list
        if any(host in parsed.netloc.lower() for host in legitimate_hosts):
            return True
            
        # Allow known library names even from other hosts
        legitimate_libraries = [
            'highcharts', 'jquery', 'd3.js', 'chart.js', 'plotly.js',
            'moment.js', 'lodash', 'three.js', 'bootstrap', 'datatables'
        ]
        
        if any(lib in library_name.lower() for lib in legitimate_libraries):
            return True
            
        # Default to rejecting unknown libraries from untrusted hosts
        self.log(f"Skipping unknown library from untrusted host: {library_name} ({parsed.netloc})", 'warn')
        return False
        
    def download_library(self, library_info: Dict) -> Optional[Dict]:
        """Download a single external library."""
        url = library_info['url']
        library_name = library_info['library']
        
        if not self.should_download_library(library_info):
            return None
            
        # Generate target filename using standardization rules
        filename = self.standardize_library_filename(library_name, url)
            
        target_file = self.external_dir / filename
        
        self.log(f"Downloading {library_name}: {url}")
        self.log(f"  Target: {target_file}")
        
        if not self.dry_run:
            try:
                # Download with appropriate headers
                headers = {
                    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                    'Accept': 'application/javascript, text/javascript, */*',
                    'Accept-Encoding': 'gzip, deflate, br'
                }
                
                response = requests.get(url, headers=headers, timeout=30)
                response.raise_for_status()
                
                # Verify content looks like JavaScript
                content = response.content
                content_str = content.decode('utf-8', errors='ignore')
                
                if not self.looks_like_javascript(content_str):
                    self.log(f"  ❌ Downloaded content doesn't look like JavaScript", 'warn')
                    return None
                    
                # Save to file
                target_file.write_bytes(content)
                
                # Verify file was written
                if target_file.exists() and target_file.stat().st_size > 0:
                    file_hash = self.get_file_hash(content)
                    file_size = len(content)
                    
                    self.log(f"  ✅ Downloaded {file_size} bytes (hash: {file_hash})")
                    
                    download_record = {
                        'library_name': library_name,
                        'source_url': url,
                        'filename': filename,
                        'target_path': str(target_file),
                        'file_size': file_size,
                        'file_hash': file_hash,
                        'download_timestamp': time.time(),
                        'source_info': library_info
                    }
                    
                    self.downloaded_libraries.append(download_record)
                    return download_record
                else:
                    self.log(f"  ❌ File not created or empty", 'error')
                    return None
                    
            except Exception as e:
                self.log(f"  ❌ Download failed: {e}", 'error')
                return None
        else:
            self.log(f"  [DRY-RUN] Would download to {target_file}")
            return {
                'library_name': library_name,
                'source_url': url,
                'filename': filename,
                'target_path': str(target_file),
                'dry_run': True
            }
            
    def looks_like_javascript(self, content: str) -> bool:
        """Basic heuristic to verify content looks like JavaScript."""
        # Check for common JavaScript patterns
        js_indicators = [
            'function', 'var ', 'let ', 'const ', 'return', 'if (', 'for (', 
            '$(', 'jQuery', 'window.', 'document.', '=>', 'prototype',
            'undefined', 'null', 'true', 'false'
        ]
        
        # Must have at least a few JavaScript indicators
        indicator_count = sum(1 for indicator in js_indicators if indicator in content)
        
        # Also check it's not obviously HTML/XML
        html_indicators = ['<html', '<head', '<body', '<!DOCTYPE']
        has_html = any(indicator in content.lower() for indicator in html_indicators)
        
        return indicator_count >= 3 and not has_html and len(content.strip()) > 50
        
    def extract_from_trace(self, trace_file: Path) -> bool:
        """Extract external libraries from a network trace file."""
        if not trace_file.exists():
            self.log(f"Trace file not found: {trace_file}", 'error')
            return False
            
        try:
            with open(trace_file) as f:
                trace_data = json.load(f)
        except Exception as e:
            self.log(f"Failed to load trace file: {e}", 'error')
            return False
            
        # Get external library summary
        external_libraries = trace_data.get('external_library_summary', {})
        
        if not external_libraries:
            self.log("No external libraries found in trace", 'warn')
            return False
            
        self.log(f"Found {len(external_libraries)} external libraries in trace:")
        for lib_name, lib_data in external_libraries.items():
            self.log(f"  - {lib_name}: {lib_data['count']} requests, {len(lib_data['urls'])} unique URLs")
            
        # Download each library
        success_count = 0
        total_count = 0
        
        for lib_name, lib_data in external_libraries.items():
            # Take the first URL for each library (usually the most common one)
            for url in lib_data['urls'][:1]:  # Just first URL to avoid duplicates
                total_count += 1
                
                library_info = {
                    'library': lib_name,
                    'url': url,
                    'host': urlparse(url).netloc,
                    'trace_data': lib_data
                }
                
                if self.download_library(library_info):
                    success_count += 1
                    
        self.log(f"Download complete: {success_count}/{total_count} libraries successful")
        return success_count > 0
        
    def generate_extraction_report(self) -> None:
        """Generate a report of downloaded external libraries."""
        if not self.downloaded_libraries:
            return
            
        report_file = TRACE_OUTPUT_DIR / "external_library_extraction.json"
        
        report = {
            'extraction_timestamp': time.time(),
            'downloaded_libraries': self.downloaded_libraries,
            'extraction_log': self.download_log,
            'summary': {
                'total_downloaded': len(self.downloaded_libraries),
                'total_size': sum(lib.get('file_size', 0) for lib in self.downloaded_libraries),
                'target_directory': str(self.external_dir)
            }
        }
        
        if not self.dry_run:
            with open(report_file, 'w') as f:
                json.dump(report, f, indent=2)
            self.log(f"Extraction report saved: {report_file}")
        else:
            self.log(f"[DRY-RUN] Would save extraction report to {report_file}")
            
    def list_downloaded_libraries(self) -> List[str]:
        """List all downloaded external libraries."""
        if not self.external_dir.exists():
            return []
            
        return [f.name for f in self.external_dir.glob("*.js") if f.is_file()]


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Extract external JavaScript libraries for Android app")
    ap.add_argument("--from-trace", help="Extract libraries from network trace JSON file")
    ap.add_argument("--dry-run", action="store_true", help="Show what would be downloaded without making changes")
    ap.add_argument("--list", action="store_true", help="List currently downloaded libraries")
    args = ap.parse_args(argv)
    
    extractor = ExternalLibraryExtractor(dry_run=args.dry_run)
    
    if args.list:
        libraries = extractor.list_downloaded_libraries()
        print(f"Downloaded external libraries ({len(libraries)}):")
        for lib in libraries:
            print(f"  - {lib}")
        return 0
        
    if args.from_trace:
        trace_file = Path(args.from_trace)
        success = extractor.extract_from_trace(trace_file)
        extractor.generate_extraction_report()
        return 0 if success else 1
    else:
        # Default: look for latest network trace
        default_trace = TRACE_OUTPUT_DIR / "network_trace.json"
        if default_trace.exists():
            extractor.log(f"Using default trace file: {default_trace}")
            success = extractor.extract_from_trace(default_trace)
            extractor.generate_extraction_report()
            return 0 if success else 1
        else:
            extractor.log("No trace file specified and no default trace found", 'error')
            extractor.log("Run: python tools/js/deploy/network_tracer.py first", 'error')
            return 1


if __name__ == "__main__":
    raise SystemExit(main())