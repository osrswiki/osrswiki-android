#!/usr/bin/env python3
"""
OSRS Wiki Network Request Tracer

Uses Playwright to load OSRS Wiki pages and capture all network requests
to identify where JavaScript modules are actually hosted.

Usage:
  python tools/js_modules/deploy/network_tracer.py --pages "Grand_Exchange" "Nightmare_Zone"
  python tools/js_modules/deploy/network_tracer.py --auto-from-scan tools/js_modules/out/report.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path
from typing import Dict, List, Optional, Set
from urllib.parse import urljoin, urlparse

try:
    from playwright.sync_api import sync_playwright, Page, Request, Response
except ImportError:
    print("Error: playwright not installed. Run: pip install playwright", file=sys.stderr)
    print("Then run: playwright install chromium", file=sys.stderr)
    sys.exit(1)


DEFAULT_BASE = "https://oldschool.runescape.wiki"
DEFAULT_OUT = "tools/js_modules/out"


class NetworkTracer:
    """Traces network requests to identify JavaScript module hosting locations."""
    
    def __init__(self, base_url: str = DEFAULT_BASE, output_dir: str = DEFAULT_OUT):
        self.base_url = base_url.rstrip('/')
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # Network request tracking
        self.all_requests: List[Dict] = []
        self.js_requests: List[Dict] = []
        self.module_requests: List[Dict] = []
        self.external_hosts: Set[str] = set()
        
    def log(self, msg: str, level: str = 'info') -> None:
        """Simple logging."""
        prefix = {'info': '[INFO]', 'warn': '[WARN]', 'error': '[ERROR]'}.get(level, '[INFO]')
        print(f"{prefix} {msg}")
        
    def is_js_module_request(self, url: str, headers: Dict[str, str]) -> bool:
        """Determine if a request looks like a JavaScript module."""
        parsed = urlparse(url)
        
        # Check if it's a load.php ResourceLoader request
        if 'load.php' in parsed.path:
            return True
            
        # Check for .js files
        if parsed.path.endswith('.js'):
            return True
            
        # Check content-type if available
        content_type = headers.get('content-type', '').lower()
        if 'javascript' in content_type or 'application/json' in content_type:
            return True
            
        # Check for common module parameters
        if any(param in url.lower() for param in ['modules=', 'module=', 'gadget', 'ext.']):
            return True
            
        return False
        
    def analyze_request(self, request: Request) -> Dict:
        """Analyze a network request and extract useful information."""
        url = request.url
        parsed = urlparse(url)
        headers = dict(request.headers)
        
        # Track external hosts
        if parsed.netloc and parsed.netloc != urlparse(self.base_url).netloc:
            self.external_hosts.add(parsed.netloc)
            
        request_info = {
            'url': url,
            'method': request.method,
            'host': parsed.netloc,
            'path': parsed.path,
            'query': parsed.query,
            'headers': headers,
            'resource_type': request.resource_type,
            'is_external': parsed.netloc != urlparse(self.base_url).netloc,
            'timestamp': time.time()
        }
        
        # Extract potential module information from URL
        if 'modules=' in url:
            modules_match = re.search(r'modules=([^&]+)', url)
            if modules_match:
                modules_param = modules_match.group(1)
                # URL decode and split modules
                try:
                    from urllib.parse import unquote
                    decoded_modules = unquote(modules_param)
                    request_info['detected_modules'] = [m.strip() for m in decoded_modules.split('|') if m.strip()]
                except:
                    request_info['detected_modules'] = [modules_param]
                    
        return request_info
        
    def analyze_response(self, response: Response) -> Dict:
        """Analyze a network response for additional insights."""
        response_info = {
            'status': response.status,
            'status_text': response.status_text,
            'headers': dict(response.headers),
            'url': response.url
        }
        
        # Try to get response size
        try:
            # Note: response.body() might not work for all response types
            content_length = response.headers.get('content-length')
            if content_length:
                response_info['content_length'] = int(content_length)
        except:
            pass
            
        return response_info
        
    def trace_page(self, page_title: str, timeout: int = 30000) -> Dict:
        """Trace network requests for a specific wiki page."""
        self.log(f"Tracing network requests for: {page_title}")
        
        page_requests: List[Dict] = []
        page_responses: List[Dict] = []
        
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            context = browser.new_context(
                user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
            )
            page = context.new_page()
            
            # Set up request/response handlers
            def handle_request(request: Request):
                req_info = self.analyze_request(request)
                page_requests.append(req_info)
                self.all_requests.append(req_info)
                
                # Check if this looks like a JS module request
                if self.is_js_module_request(request.url, dict(request.headers)):
                    self.js_requests.append(req_info)
                    if req_info.get('detected_modules'):
                        self.module_requests.append(req_info)
                        self.log(f"  Found module request: {req_info.get('detected_modules')} -> {request.url}")
                        
            def handle_response(response: Response):
                resp_info = self.analyze_response(response)
                page_responses.append(resp_info)
                
            page.on('request', handle_request)
            page.on('response', handle_response)
            
            # Navigate to the page
            wiki_url = f"{self.base_url}/w/{page_title}"
            try:
                page.goto(wiki_url, wait_until='networkidle', timeout=timeout)
                
                # Wait a bit more for any lazy-loaded content
                page.wait_for_timeout(2000)
                
            except Exception as e:
                self.log(f"Error loading {page_title}: {e}", 'error')
            finally:
                browser.close()
                
        page_result = {
            'page_title': page_title,
            'wiki_url': wiki_url,
            'total_requests': len(page_requests),
            'js_requests': [r for r in page_requests if self.is_js_module_request(r['url'], r['headers'])],
            'module_requests': [r for r in page_requests if r.get('detected_modules')],
            'external_hosts': list({urlparse(r['url']).netloc for r in page_requests if r['is_external']}),
            'requests': page_requests,
            'responses': page_responses
        }
        
        self.log(f"  Total requests: {len(page_requests)}")
        self.log(f"  JS/module requests: {len(page_result['js_requests'])}")
        self.log(f"  External hosts: {len(page_result['external_hosts'])}")
        
        return page_result
        
    def trace_multiple_pages(self, page_titles: List[str]) -> Dict:
        """Trace network requests for multiple pages."""
        self.log(f"Starting network trace for {len(page_titles)} pages")
        
        page_results = []
        for i, title in enumerate(page_titles, 1):
            self.log(f"Processing {i}/{len(page_titles)}: {title}")
            page_result = self.trace_page(title)
            page_results.append(page_result)
            
            # Small delay between requests to be respectful
            if i < len(page_titles):
                time.sleep(1)
                
        # Aggregate results
        all_external_hosts = sorted(self.external_hosts)
        all_js_hosts = sorted({urlparse(r['url']).netloc for r in self.js_requests})
        
        # Find most common module hosting patterns
        module_hosts = {}
        module_urls = {}
        
        for req in self.module_requests:
            host = urlparse(req['url']).netloc
            modules = req.get('detected_modules', [])
            
            for module in modules:
                if module not in module_hosts:
                    module_hosts[module] = []
                    module_urls[module] = []
                module_hosts[module].append(host)
                module_urls[module].append(req['url'])
                
        # Deduplicate and summarize
        module_summary = {}
        for module, hosts in module_hosts.items():
            unique_hosts = list(set(hosts))
            unique_urls = list(set(module_urls[module]))
            module_summary[module] = {
                'hosts': unique_hosts,
                'sample_urls': unique_urls[:3],  # Just show first 3 examples
                'total_requests': len(hosts)
            }
        
        results = {
            'meta': {
                'pages_traced': len(page_titles),
                'total_requests': len(self.all_requests),
                'js_requests': len(self.js_requests),
                'module_requests': len(self.module_requests),
                'timestamp': time.time()
            },
            'external_hosts': all_external_hosts,
            'js_hosts': all_js_hosts,
            'module_hosting': module_summary,
            'page_results': page_results
        }
        
        return results
        
    def extract_from_scan_report(self, report_path: str) -> List[str]:
        """Extract page titles that have JavaScript modules from scan report."""
        report_file = Path(report_path)
        if not report_file.exists():
            self.log(f"Scan report not found: {report_path}", 'error')
            return []
            
        with open(report_file) as f:
            report = json.load(f)
            
        # Get pages that had JavaScript modules
        pages_with_js = set()
        
        if 'javascript_modules' in report:
            for module_info in report['javascript_modules']:
                sample_pages = module_info.get('sample_pages', [])
                pages_with_js.update(sample_pages)
                
        # Also check the per-page data if available  
        for page_info in report.get('page_results', []):
            if page_info.get('modules'):
                pages_with_js.add(page_info['title'])
                
        # Limit to reasonable number for testing
        return sorted(list(pages_with_js))[:10]  # Just take first 10 for now
        
    def save_results(self, results: Dict, filename: str = "network_trace.json") -> None:
        """Save network trace results to JSON file."""
        output_file = self.output_dir / filename
        with open(output_file, 'w') as f:
            json.dump(results, f, indent=2, default=str)
        self.log(f"Saved network trace results to: {output_file}")


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Trace network requests to identify JavaScript module hosting")
    ap.add_argument("--base", default=DEFAULT_BASE, help="Wiki base URL")
    ap.add_argument("--out", default=DEFAULT_OUT, help="Output directory")
    ap.add_argument("--pages", nargs="*", help="Specific page titles to trace")
    ap.add_argument("--auto-from-scan", help="Extract pages from widget scan report JSON")
    ap.add_argument("--timeout", type=int, default=30000, help="Page load timeout in milliseconds")
    args = ap.parse_args(argv)
    
    tracer = NetworkTracer(args.base, args.out)
    
    # Determine which pages to trace
    pages_to_trace = []
    
    if args.pages:
        pages_to_trace.extend(args.pages)
    elif args.auto_from_scan:
        pages_to_trace.extend(tracer.extract_from_scan_report(args.auto_from_scan))
    else:
        # Default test pages known to have modules
        pages_to_trace = ["Grand_Exchange", "Nightmare_Zone", "Calculator:Combat"]
        
    if not pages_to_trace:
        tracer.log("No pages to trace", 'error')
        return 1
        
    # Run the network trace
    results = tracer.trace_multiple_pages(pages_to_trace)
    
    # Save results
    tracer.save_results(results)
    
    # Print summary
    tracer.log("\n=== NETWORK TRACE SUMMARY ===")
    tracer.log(f"Pages traced: {results['meta']['pages_traced']}")
    tracer.log(f"Total requests: {results['meta']['total_requests']}")
    tracer.log(f"JavaScript requests: {results['meta']['js_requests']}")
    tracer.log(f"Module requests: {results['meta']['module_requests']}")
    
    if results['external_hosts']:
        tracer.log(f"\nExternal hosts found: {len(results['external_hosts'])}")
        for host in results['external_hosts'][:10]:  # Show first 10
            tracer.log(f"  - {host}")
            
    if results['module_hosting']:
        tracer.log(f"\nModule hosting detected:")
        for module, info in list(results['module_hosting'].items())[:10]:  # Show first 10
            tracer.log(f"  - {module}: {info['hosts']} ({info['total_requests']} requests)")
            
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
