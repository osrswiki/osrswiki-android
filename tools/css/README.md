# CSS Tools

This directory contains tools for managing the CSS architecture of the OSRS Wiki Android app. The tools enable modular CSS development, duplicate detection, and automated build processes.

## Tools Overview

### 1. CSS Analyzer (`css-analyzer.py`)
Advanced CSS analyzer for comprehensive wiki-app CSS parity checking.
- Compares CSS selectors between wiki CSS and app CSS
- Categorizes selectors by priority (content-high, content-medium, etc.)
- Generates detailed gap analysis reports

```bash
python tools/css/css-analyzer.py --help
```

### 2. CSS Sync Workflow (`css-sync-workflow.py`)
Automated workflow for maintaining CSS parity with the wiki.
- Fetches latest CSS from oldschool.runescape.wiki
- Runs gap analysis
- Generates recommendations for next steps

```bash
python tools/css/css-sync-workflow.py
```

### 3. CSS Generator (`css-generator.py`)
Generates properly formatted CSS from gap analysis results.
- Creates CSS for missing or incomplete selectors
- Supports bulk generation by category
- Mobile-friendly filtering

```bash
python tools/css/css-generator.py --help
```

### 4. CSS Duplicate Detector (`css-duplicate-detector.py`)
Detects duplicate CSS selectors across multiple files.
- Scans all CSS files in the styles directory
- Reports duplication statistics
- Helps prevent CSS conflicts

```bash
python tools/css/css-duplicate-detector.py
```

### 5. CSS Extractor (`css-extractor.py`)
Extracts CSS from monolithic files into functional modules.
- Categorizes selectors by function (tables, forms, media, etc.)
- Creates modular CSS files
- Maintains build order recommendations

```bash
python tools/css/css-extractor.py input.css --output-dir modules/
```

### 6. CSS Build System (`css-build.py`)
Concatenates modular CSS files in correct order.
- Builds final CSS from modules
- Validates syntax
- Generates build manifests

```bash
python tools/css/css-build.py
```

## CSS Architecture

The CSS system is organized into functional modules:

```
app/src/main/assets/styles/
├── modules/
│   ├── base.css          # Base styles, resets, global rules
│   ├── typography.css    # Headings and text styling  
│   ├── layout.css        # Page layout and structure
│   ├── tables.css        # Table styling including wikitables
│   ├── forms.css         # Form controls and inputs
│   ├── media.css         # Images, galleries, media handling
│   ├── navigation.css    # Navigation elements and menus
│   ├── messagebox.css    # Message boxes and notifications
│   ├── gaming.css        # OSRS gaming-specific UI elements
│   ├── mediawiki.css     # MediaWiki and SMW components
│   ├── interactive.css   # Interactive states and OOUI
│   └── other.css         # Uncategorized selectors
├── themes.css            # CSS variables and theming
├── fonts.css             # Font definitions
└── fixes.css             # Final overrides
```

## Development Workflow

### Adding New CSS

1. **Identify the appropriate module** based on functionality
2. **Edit the module file** directly (e.g., `modules/tables.css` for table styles)
3. **Run duplicate detection** to ensure no conflicts:
   ```bash
   python tools/css/css-duplicate-detector.py
   ```
4. **Rebuild the CSS**:
   ```bash
   python tools/css/css-build.py
   ```
5. **Test the changes** in the app

### Bulk CSS Addition

1. **Run CSS sync workflow** to identify missing selectors:
   ```bash
   python tools/css/css-sync-workflow.py
   ```
2. **Generate CSS for specific modules**:
   ```bash
   python tools/css/css-generator.py --module tables
   ```
3. **Integrate generated CSS** into appropriate modules
4. **Rebuild and test**

### Maintaining CSS Parity

1. **Regular sync checks** with the wiki CSS
2. **Monitor gap analysis reports** in `output/`
3. **Update modules** based on priority recommendations
4. **Keep build system up to date**

## Output Files

All tool outputs are saved to `tools/css/output/`:
- `css_gap_analysis.json` - Detailed gap analysis
- `css_sync_report_*.txt` - Sync workflow reports  
- `duplicate_report.json` - Duplicate detection results
- `build_manifest.json` - Build system output
- `extraction_report.json` - Module extraction results

## Configuration

### Build Configuration
Create a custom build config with:
```bash
python tools/css/css-build.py --create-config
```

Edit `tools/css/css-build-config.json` to customize:
- Module build order
- Output filename
- Validation settings
- Header comments

## Best Practices

1. **Always run duplicate detection** before committing changes
2. **Use the build system** rather than manually editing `wiki-integration.css`
3. **Keep modules focused** on their specific functionality
4. **Document significant changes** in module comments
5. **Test thoroughly** after CSS changes
6. **Monitor CSS parity** regularly with sync workflow

## Troubleshooting

### Build Failures
- Check syntax with `python tools/css/css-build.py --validate`
- Review build manifest for detailed error information
- Ensure all required modules exist

### Duplicate Issues  
- Run duplicate detector to identify conflicts
- Use the build system to resolve ordering issues
- Consider CSS specificity when resolving conflicts

### Gap Analysis Issues
- Verify network connectivity for wiki CSS fetching
- Check that analysis files are up to date
- Review categorization logic if selectors seem miscategorized

## Contributing

When adding new tools or modifying existing ones:
1. Follow the existing code structure and documentation standards
2. Add appropriate error handling and validation
3. Update this README with new functionality
4. Test thoroughly with various CSS inputs
5. Maintain backward compatibility where possible