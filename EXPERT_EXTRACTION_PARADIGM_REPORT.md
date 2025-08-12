# Expert Consultation: Fundamental Extraction Paradigm Question

## Executive Summary

After implementing both Expert 1's dependency normalization solution and Expert 2's minimal architecture approach, we've identified that **we're reverse-engineering server-side behavior instead of extracting the complete server-side resource**. We need expert guidance on what we're fundamentally missing in our extraction approach.

## The Reverse Engineering Trap

**Current Situation:**
- Our OSRS Wiki startup.js contains numeric dependencies: `["mediawiki.base", "", [10]]`
- We're implementing client-side patches to resolve `[10]` → `"jquery"`
- MediaWiki on the server clearly knows how to resolve these dependencies
- We're recreating this resolution logic instead of extracting it

**The Fundamental Question:**
**Why are we patching client-side behavior when the server already has all the dependency resolution information we need?**

## What We've Learned About Server-Side Reality

### 1. OSRS Wiki Server Behavior
- **Startup.js size:** 149KB (significantly larger than typical 50KB)
- **Module count:** 652 modules in registry
- **Dependency format:** Numeric indices requiring resolution
- **Server capability:** Obviously resolves dependencies correctly for browsers

### 2. Missing Extraction Components
Our current extraction captures:
- ✅ Module registry with numeric dependencies
- ✅ Individual module source code  
- ✅ Basic ResourceLoader infrastructure
- ❌ **Dependency resolution logic/data**
- ❌ **Name table for index mapping**
- ❌ **Complete ResourceLoader initialization sequence**

## Critical Extraction Gap Analysis

### Current Extraction Method
```python
# From scan_widgets.py lines 150-292
startup_url = f"{base}/load.php"
startup_params = {
    'debug': 'true',
    'lang': 'en-gb', 
    'modules': 'startup',
    'only': 'scripts',
    'skin': 'vector'
}
startup_response = session.get(startup_url, params=startup_params)
```

**What we get:** 149KB file with module registry + numeric dependencies  
**What we're missing:** The resolution mechanism that browsers use

### Server-Side Questions
1. **Where is the dependency resolution logic stored on the server?**
   - Is it embedded in startup.js but not in the part we extract?
   - Is it in a separate ResourceLoader component?
   - Is it generated dynamically per request?

2. **How does the server build the name table?**
   - Is there a master dependency mapping file?
   - Is the name table passed differently to browsers vs. our extraction?
   - Are we missing URL parameters that provide the name table?

3. **What does a complete ResourceLoader response contain?**
   - Are we only extracting part of what browsers receive?
   - Is there additional initialization code we're missing?
   - Should we be extracting from different endpoints?

## Paradigm Shift Needed

### Instead of: Reverse Engineering Patches
- ❌ Implementing client-side dependency index resolution
- ❌ Creating custom name table mapping logic  
- ❌ Patching MediaWiki's internal behavior
- ❌ Guessing how dependency resolution should work

### We Should: Extract Complete Server Resources
- ✅ Identify what complete ResourceLoader response contains
- ✅ Extract the actual dependency resolution mechanism
- ✅ Capture the name table or mapping data the server uses
- ✅ Understand the complete server-side dependency flow

## Extraction Architecture Questions

### 1. Are We Extracting the Complete Response?
**Question:** Does our `load.php?modules=startup` extraction match exactly what browsers receive?

**Investigation needed:**
- Browser network tab capture vs. our extraction
- Complete HTTP response headers and content
- Any additional data browsers receive that we don't

### 2. Are We Missing Dependency Resolution Resources?
**Question:** Where does MediaWiki store the logic/data to resolve `[10]` → `"jquery"`?

**Possibilities:**
- Name table embedded in startup.js but not extracted
- Separate ResourceLoader endpoint for dependency data
- Additional URL parameters needed for complete extraction
- Dynamic generation based on request context

### 3. Are We Using the Wrong Extraction Strategy?
**Question:** Should we be extracting ResourceLoader differently?

**Alternative approaches:**
- Extract complete page ResourceLoader configuration
- Use MediaWiki API to get dependency metadata
- Extract from different load.php configurations
- Capture the complete browser ResourceLoader initialization sequence

## Expert Guidance Requested

**Please do not provide implementation solutions at this stage.** We need higher-level architectural guidance:

### 1. Extraction Completeness
- What constitutes a "complete" ResourceLoader extraction?
- How can we verify our extraction matches server reality?
- What server-side components are we likely missing?

### 2. Dependency Resolution Source
- Where does MediaWiki actually store dependency resolution logic?
- How should we identify what browsers receive that we don't?
- What's the correct way to extract complete dependency information?

### 3. Extraction Methodology
- Should we be extracting from different endpoints?
- Are there MediaWiki API methods that provide dependency metadata?
- How can we make our extraction more comprehensive and robust?

### 4. Architecture Philosophy  
- When is it appropriate to patch vs. extract more completely?
- How do we avoid the reverse engineering trap in the future?
- What's the correct abstraction level for this type of extraction?

## Current Status

We have successfully:
- ✅ Identified that dependency resolution is the core issue
- ✅ Confirmed our startup.js structure is correct
- ✅ Proven that MediaWiki's dependency resolution isn't working with our extraction
- ✅ Realized we're missing fundamental server-side components

**Next Step:** Understand what complete extraction should look like before implementing more patches.

## Request

**Help us understand what we're missing at the extraction level, not how to fix it at the implementation level.** We want to extract the server's complete dependency resolution capability rather than reverse-engineer it.

---

*Report Date: 2025-08-12*  
*Purpose: Paradigm shift from patching to comprehensive extraction*  
*Status: Requesting extraction architecture guidance, not implementation solutions*