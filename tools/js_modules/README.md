# JS Modules Automation Tools

Tools for automatically extracting and adapting MediaWiki ResourceLoader modules for standalone use in the OSRS Wiki Android app.

## Components

### 1. Module Extractor (`extract_modules.py`)
Automatically extracts JavaScript modules from the OSRS Wiki and adapts them for app use.

**Usage:**
```bash
# Extract specific modules
./extract_modules.sh --modules ext.gadget.calc ext.gadget.switch-infobox

# Auto-extract from widget scan results  
./extract_modules.sh --auto-from-scan ../wiki_widgets/out/report.json

# Extract only priority modules
./extract_modules.sh --priority-only
```

**Features:**
- Multiple extraction methods (raw action, ResourceLoader API)
- Dependency analysis and resolution
- Automatic MediaWiki API compatibility injection
- Module categorization and registry tracking

### 2. MediaWiki Compatibility Layer (`mw_compatibility.js`)
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
   ../wiki_widgets/scan_widgets.sh --limit 100 --out ../wiki_widgets/out
   ```

2. **Extract priority modules:**
   ```bash
   ./extract_modules.sh --auto-from-scan ../wiki_widgets/out/report.json --priority-only
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
├── extract_modules.py          # Main extraction tool
├── extract_modules.sh          # Wrapper script with environment
├── mw_compatibility.js         # MediaWiki API polyfills
├── README.md                   # This documentation
└── out/                        # Extracted modules output
    ├── module_registry.json    # Metadata and dependencies
    ├── ext_gadget_calc.js      # Adapted calculator gadget
    └── ext_Tabber.js           # Adapted tabbed interface
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