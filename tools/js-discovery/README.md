# JS Module Discovery Tools

Tools for discovering and extracting JavaScript modules from the OSRS Wiki to add to the app's curated JS collection.

**Purpose**: These tools help identify which JS modules are used on wiki pages and extract clean copies for manual integration into the app's reverse-engineered JavaScript solution.

## Directory Structure

```
tools/js-discovery/
├── scanner/           # Discover which modules are used on pages
├── extractors/        # Extract clean copies of modules
├── analysis/          # Analyze module dependencies and loading
└── extracted-samples/ # Reference implementations of gadgets
```

## Workflow: Discovering New Modules

### 1. Scanner Tools - Find Missing Modules

Scan wiki pages to see which JS modules they use:

```bash
# Scan popular pages to see what modules they use
./scanner/scan_widgets.sh --pages "Grand_Exchange" "Magic" "The_Gauntlet" --out ../js-discovery-output

# Scan first 50 mainspace pages 
./scanner/scan_widgets.sh --limit 50 --out ../js-discovery-output
```

This creates:
- `report.json` - Detailed module usage per page
- `summary.txt` - Summary of missing modules

### 2. Extractor Tools - Get Module Code

Extract clean copies of interesting modules:

```bash
# Extract a specific gadget
python3 extractors/extract_gadgets.py --name "Tabber"

# Extract page-specific modules for a page
python3 extractors/page_modules_extractor.py --page "Grand_Exchange"

# Extract core MediaWiki modules
python3 extractors/extract_core_modules.py --modules "mediawiki.page.gallery"
```

### 3. Analysis Tools - Understand Dependencies

Analyze module dependencies before integration:

```bash
# Understand what a module depends on
python3 analysis/module_resolver.py --module "ext.Tabber"

# Analyze loading sequence for a page
python3 analysis/loading_sequence_inspector.py --page "Magic"
```

### 4. Manual Integration

1. **Review extracted module code** - Check if it's suitable for the app
2. **Add to app assets** - Place in `app/src/main/assets/web/` 
3. **Test integration** - Verify it works with existing JS solution
4. **Update curated collection** - Add to the working reverse-engineered setup

## Key Files

### Scanner
- `scanner/scan_widgets.py` - Main scanner that discovers module usage
- `scanner/scan_widgets.sh` - Wrapper script with environment setup

### Extractors  
- `extractors/extract_gadgets.py` - Extract MediaWiki gadgets
- `extractors/page_modules_extractor.py` - Extract page-specific modules using browser automation
- `extractors/extract_core_modules.py` - Extract core MediaWiki modules
- `extractors/extract_modules.sh` - Shell wrapper for extraction workflow
- `extractors/mw_compatibility.js` - Reference MediaWiki compatibility layer

### Analysis
- `analysis/module_resolver.py` - Analyze module dependencies
- `analysis/loading_sequence_inspector.py` - Inspect module loading order

### Reference Samples
- `extracted-samples/gadgets/` - Previously extracted gadgets for reference
  - Charts, GECharts, rsw-util implementations
  - `gadget_definitions.json` - Gadget metadata

## Environment Setup

Most tools require Python with requests library:

```bash
# Install dependencies
pip install requests playwright beautifulsoup4

# For browser automation tools
playwright install chromium
```

Some tools can use conda/micromamba environments if available.

## Integration Notes

**These tools do NOT automatically integrate modules into the app.** They are for discovery and extraction only. Manual review and integration is still required because:

1. **Security** - All extracted code must be reviewed before adding to app
2. **Compatibility** - Modules may need modification to work with the app's WebView
3. **Dependencies** - Complex modules may require additional MediaWiki APIs
4. **Performance** - Some modules may be too heavy for mobile use

The goal is to maintain the current working reverse-engineered JS approach while using these tools to discover and extract new modules to add to the curated collection.