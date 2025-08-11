# MediaWiki ResourceLoader Module Extraction Analysis Report

## Executive Summary

The automated extraction of MediaWiki JavaScript modules from the OSRS Wiki has encountered significant technical barriers. Despite implementing multiple extraction strategies and comprehensive tooling, **all extraction attempts have failed** due to access restrictions and architectural limitations. This report documents the attempted approaches, identifies root causes, and provides research questions for expert analysis.

## Attempted Extraction Methods

### 1. Raw Action Method (for Gadgets)
**Target URL Pattern:** `https://oldschool.runescape.wiki/w/index.php?title=MediaWiki:Gadget-{name}.js&action=raw`

**Results:** 
- ❌ **403 Forbidden** for `ext.gadget.switch-infobox`
- ❌ **404 Not Found** for most gadgets tested (`ext.gadget.calc`, etc.)

**Analysis:** OSRS Wiki appears to have restricted access to gadget source code pages, possibly due to:
- Security policies preventing direct JavaScript source exposure
- Different naming conventions than expected
- Gadgets may be defined but not actually implemented as separate files

### 2. ResourceLoader API Method
**Target URL Pattern:** `https://oldschool.runescape.wiki/w/load.php?modules={name}&only=scripts&debug=1`

**Results:**
- ❌ **404 Not Found** for all tested modules:
  - `ext.Tabber` 
  - `ext.cite.ux-enhancements`
  - `ext.gadget.switch-infobox`

**Analysis:** Indicates modules either:
- Don't exist on OSRS Wiki (despite being detected by scanner)
- Use different naming conventions
- Are part of bundled modules not accessible individually
- Are CSS-only modules without JavaScript components

### 3. Extension Check Method  
**Target URL Pattern:** `https://oldschool.runescape.wiki/w/load.php?modules={name}&only=scripts&debug=0`

**Results:** 
- Still returning 404s, confirming modules are not accessible via ResourceLoader

## Scanner vs. Reality Discrepancy

### What the Scanner Detected:
From our enhanced widget scanner on 25 pages:
- `ext.cite.ux-enhancements` (11 occurrences)  
- `ext.Tabber` (1 occurrence)
- `ext.tmh.player` (9 occurrences)
- `ext.scribunto.logs` (8 occurrences)

### Verified Available Gadgets:
From `MediaWiki:Gadgets-definition`:
- `GECharts` (Grand Exchange Charts)
- `calc` (Calculator utilities)
- `switch-infobox` (Infobox switcher)
- `equipment` (Equipment viewer)
- And others...

**Critical Gap:** The scanner detects modules that appear not to exist as standalone JavaScript files.

## Root Cause Hypotheses

### Hypothesis 1: Module Scanner False Positives
The MediaWiki API `action=parse&prop=modules` may return:
- Modules that are requested but not actually loaded
- Module names that exist in MediaWiki core but have no custom JavaScript
- Placeholder modules that register but contain no implementation

### Hypothesis 2: OSRS Wiki Architecture Differences  
OSRS Wiki may use a different architecture than standard MediaWiki:
- Custom ResourceLoader configuration
- Bundled modules instead of individual files
- Different access control policies
- Non-standard gadget implementation

### Hypothesis 3: Module Naming/Location Issues
Actual module locations may differ from standard patterns:
- Gadgets stored in different namespaces
- Alternative file naming conventions  
- Modules embedded in common CSS/JS pages
- Extension-specific storage locations

### Hypothesis 4: Access Control Restrictions
OSRS Wiki may have implemented security measures:
- IP-based access restrictions for module source
- Authentication requirements for ResourceLoader debug mode
- Bot detection preventing automated access
- Rate limiting causing request failures

## Research Questions for Expert Investigation

### 1. MediaWiki ResourceLoader Architecture
**For MediaWiki experts:**
- How does MediaWiki's `action=parse&prop=modules` determine which modules to report?
- Can modules be reported by the API but not actually contain JavaScript code?
- What are the standard methods to programmatically access ResourceLoader module source code?
- Are there alternative APIs or endpoints for module content retrieval?

### 2. OSRS Wiki-Specific Configuration  
**For OSRS Wiki administrators/developers:**
- What is the exact ResourceLoader configuration on oldschool.runescape.wiki?
- Are there access restrictions on the load.php endpoint for external tools?
- How are gadgets actually implemented and stored on this wiki?
- Are there alternative methods to access gadget source code?
- What security policies might be blocking programmatic access?

### 3. Module Detection and Validation
**For web scraping/API experts:**
- How can we differentiate between actual JavaScript modules vs. placeholder/CSS-only modules?
- What request headers or authentication might be required for ResourceLoader access?
- Are there rate limiting or bot detection measures that could be causing failures?
- How can we verify if a module truly exists before attempting extraction?

### 4. Alternative Extraction Strategies
**For MediaWiki/web automation experts:**
- Could browser automation tools (Selenium/Playwright) access module sources that API calls cannot?
- Are there alternative MediaWiki endpoints that expose module content?
- Could examining actual page source reveal embedded module code?
- Are there MediaWiki extensions that provide module introspection APIs?

### 5. OSRS Wiki Specific Investigation
**For OSRS community/technical contributors:**
- Which gadgets are actually active and functional on the OSRS Wiki?
- How do existing wiki editors access and modify gadget code?
- Are there community-maintained repositories of OSRS Wiki gadgets?
- What tools do wiki administrators use for gadget development?

## Technical Implementation Questions

### 6. Module Dependency Resolution
**For JavaScript/build tool experts:**
- How can we map the dependency relationships between MediaWiki modules?
- What MediaWiki core APIs are most commonly used by gadgets?
- How should we prioritize polyfill development for maximum compatibility?
- Are there existing MediaWiki-to-standalone conversion tools?

### 7. WebView Integration
**For Android/WebView experts:**
- What are the performance implications of loading large MediaWiki compatibility layers?
- How should we handle module loading order and dependencies in a WebView?
- Are there WebView-specific APIs we should expose to extracted modules?
- What's the optimal architecture for hot-reloading modules during development?

## Recommended Next Steps

### Immediate Research Actions:
1. **Manual verification:** Browse to actual OSRS Wiki pages and inspect network traffic to see what modules are actually loaded
2. **Alternative tools:** Try existing MediaWiki analysis tools (WikiMedia tools, MediaWiki-Utilities)  
3. **Community outreach:** Contact OSRS Wiki administrators for technical guidance
4. **Browser inspection:** Use developer tools to examine live module loading behavior

### Technical Validation:
1. **Test with other MediaWiki sites:** Verify extraction logic works with other wikis (Wikipedia, etc.)
2. **Header analysis:** Experiment with different request headers, user agents, authentication
3. **Rate limiting:** Implement delays and retry logic to avoid triggering protection mechanisms
4. **Module existence verification:** Create endpoint testing tool to validate module availability

### Alternative Approaches:
1. **Reverse engineering:** Extract module code from live page inspection
2. **Community collaboration:** Work with OSRS Wiki community to identify truly needed modules
3. **Selective implementation:** Focus on a few high-value, manually-verified modules
4. **Upstream contribution:** Contribute back extraction improvements to broader MediaWiki tooling

## Assessment

**Current Status:** ❌ **Extraction automation is not viable** with current approach

**Confidence Level:** High - Multiple extraction methods failed consistently across different module types

**Business Impact:** Moderate - Manual module implementation remains necessary but creates maintenance burden  

**Recommended Action:** Pause extraction automation development pending expert research feedback and alternative strategy development

---

*Report generated: 2025-08-11*  
*Status: Awaiting expert analysis and technical guidance*