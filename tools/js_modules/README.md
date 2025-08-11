# JS Modules Automation Tools

Unified toolkit for scanning, extracting, adapting, and deploying MediaWiki ResourceLoader modules for standalone use in the OSRS Wiki Android app.

## Components

### 1. Widget Scanner (`scanner/`)
Discovers pages' JS modules and widget-like HTML markers, and compares with local assets.

Usage:
```
# Basic scan (first 200 mainspace pages)
./tools/js_modules/scanner/scan_widgets.sh --limit 200 --out tools/js_modules/out

# Scan specific pages
./tools/js_modules/scanner/scan_widgets.sh --pages "Grand_Exchange" "Magic" "The_Gauntlet"
```

Outputs:
- `tools/js_modules/out/report.json`
- `tools/js_modules/out/summary.txt`

### 2. Module Extractor (`extractor/`)
Automatically extracts JavaScript modules from the OSRS Wiki and adapts them for app use.

**Usage:**
```bash
# Extract specific modules
./extractor/extract_modules.sh --modules ext.gadget.calc ext.gadget.switch-infobox

# Auto-extract from widget scan results  
./extractor/extract_modules.sh --auto-from-scan tools/js_modules/out/report.json

# Extract only priority modules
./extract_modules.sh --priority-only
```

**Features:**
- Multiple extraction methods (raw action, ResourceLoader API)
- Dependency analysis and resolution
- Automatic MediaWiki API compatibility injection
- Module categorization and registry tracking

### MediaWiki Compatibility Layer (`extractor/mw_compatibility.js`)
Standalone JavaScript library that provides essential MediaWiki APIs for extracted modules.

**Features:**
- Core MediaWiki APIs (`mw.config`, `mw.loader`, `mw.util`, `mw.message`, etc.)
- Basic jQuery compatibility for simple DOM operations
- Cookie and user preference management
- App-bridge integration for OSRS Wiki Android app
- Configurable fallbacks for missing functionality

**Usage in App:**
```html
<!-- Include before any adapted MediaWiki modules -->
<script src="mw_compatibility.js"></script>
<script src="adapted_module.js"></script>
```

**Configuration:**
```javascript
// Set app-specific config values
mw.config.set('osmw-app-mode', true);
mw.config.set('wgPageName', 'Current_Page_Title');
```

### 3. Integration Workflow

1. **Scan for missing modules:**
   ```bash
   ./tools/js_modules/scanner/scan_widgets.sh --limit 100 --out tools/js_modules/out
   ```

2. **Extract priority modules:**
   ```bash
   ./tools/js_modules/extractor/extract_modules.sh --auto-from-scan tools/js_modules/out/report.json --priority-only
   ```

3. **Include in app assets:**
   - Copy `mw_compatibility.js` to `app/src/main/assets/js/`
   - Copy extracted modules to `app/src/main/assets/js/modules/`
   - Load compatibility layer first, then modules

4. **Test and integrate:**
   - Verify modules work with app WebView
   - Add app-specific styling and behavior
   - Update module registry for maintenance

## Module Categories

**Priority Modules** (simple, high-impact):
- `ext.Tabber` - Tabbed interfaces
- `ext.cite.ux-enhancements` - Citation improvements  
- `mediawiki.page.gallery` - Image galleries

**Complex Modules** (require manual review):
- `ext.gadget.GECharts` - Already manually implemented
- `oojs-ui-core` - Complex UI framework
- `mediawiki.widgets` - Advanced form widgets

## Architecture

```
tools/js_modules/
├── scanner/                    # Widget scanner
│   ├── scan_widgets.py         # Scans wiki pages and local assets
│   └── scan_widgets.sh         # Wrapper script with environment
├── extractor/                  # Module extraction
│   ├── extract_modules.py      # Main extraction tool
│   ├── extract_modules.sh      # Wrapper script with environment
│   └── mw_compatibility.js     # MediaWiki API polyfills
├── deploy/                     # Deployment and tracing
│   ├── deploy_modules.py       # Copy to app assets + report
│   ├── network_tracer.py       # Optional: trace load.php bundles
│   └── network_tracer.sh       # Wrapper script
├── README.md                   # This documentation
└── out/                        # Outputs
    ├── module_registry.json    # Extraction metadata
    ├── report.json             # Scanner report
    ├── summary.txt             # Scanner summary
    └── *.js                    # Extracted modules
```

## Future Enhancements

- **Dependency graph resolution** - Automatically extract and order dependencies
- **Bundle optimization** - Combine related modules and tree-shake unused code
- **Hot module reloading** - Update modules without app restart during development  
- **Performance monitoring** - Track module impact on app performance
- **Automated testing** - Verify extracted modules work in WebView environment
- **CI/CD integration** - Automatically update modules when wiki changes

## Troubleshooting

**Module extraction fails with 404:**
- Module may not exist or have different naming
- Check `MediaWiki:Gadgets-definition` for actual available gadgets
- Try alternative extraction methods

**Extracted module doesn't work:**
- Check browser console for JavaScript errors
- Verify MediaWiki compatibility layer is loaded first
- Add missing API polyfills to compatibility layer

**Performance issues:**
- Use module bundling to reduce HTTP requests
- Implement lazy loading for non-critical modules
- Monitor WebView memory usage with large modules
