# CSS Tools

This directory contains tools for managing the CSS architecture of the OSRS Wiki Android app. The tools enable modular CSS development, duplicate detection, automated build processes, and **perfect CSS parity** with the reference wiki CSS.

## 🚀 CSS Perfect Sync System

### **Dynamic Perfect CSS Sync (`css-perfect-sync.py`)**
**The flagship tool for achieving perfect CSS parity with any reference CSS size.**

**Key Features:**
- **Dynamic Reference Discovery**: Automatically adapts to any reference CSS size (no hardcoded values)
- **Perfect Parity Achievement**: Achieves exactly 100% coverage with 0 missing rules, 0 extra rules
- **One-Shot Operation**: Single command achieves perfect parity automatically
- **Future-Proof**: Handles reference CSS evolution without manual updates
- **Intelligent Rule Management**: Smart generation, pruning, and validation

**Usage:**
```bash
# Achieve perfect CSS parity in one command
python3 tools/css/css-perfect-sync.py

# Example output:
# 🎉 Perfect parity achieved! 1035 rules, 100% coverage
```

**What it does:**
1. **Discovers** current reference CSS size dynamically (e.g., 1035 rules)
2. **Analyzes** current coverage (e.g., 78.7% with 220 missing, 587 extra)  
3. **Generates** missing rules using intelligent property extraction
4. **Removes** extra rules while preserving essential theming
5. **Builds** perfect CSS with exact reference parity
6. **Validates** 100% coverage with comprehensive checks

**Configuration:** `css-perfect-sync.yml` (auto-generated with defaults)

---

## 📊 Legacy Analysis Tools

### 1. CSS Rule Analyzer (`css-rule-analyzer.py`)
Enhanced CSS analyzer for comprehensive rule-level parity checking.
- Compares CSS rules between reference and local CSS
- Provides detailed coverage analysis and rule mapping
- Generates comprehensive analysis reports

```bash
python3 tools/css/css-rule-analyzer.py
```

### 2. CSS Sync Workflow (`css-sync-workflow.py`)
Automated workflow for maintaining CSS parity with the wiki.
- Fetches latest CSS from oldschool.runescape.wiki  
- Runs gap analysis with rule-level precision
- Supports auto-generation of missing CSS

```bash
python3 tools/css/css-sync-workflow.py
```

### 3. CSS Generator (`css-generator.py`)
Generates properly formatted CSS from analysis results.
- Creates CSS for missing or incomplete selectors
- Enhanced property extraction from reference CSS
- Context-aware rule generation

```bash
python3 tools/css/css-generator.py
```

## 🛠️ Development Tools

### 4. CSS Duplicate Detector (`css-duplicate-detector.py`)
Detects duplicate CSS selectors across multiple files.
- Scans all CSS files in the styles directory
- Reports duplication statistics and conflicts
- Helps maintain clean CSS architecture

```bash
python3 tools/css/css-duplicate-detector.py
```

### 5. CSS Rule Consolidator (`css-rule-consolidator.py`)
Consolidates CSS rules with identical properties.
- Combines rules with same properties for efficiency
- Reduces CSS bloat and improves maintainability
- Preserves functionality while optimizing size

```bash
python3 tools/css/css-rule-consolidator.py
```

### 6. CSS Deduplicator (`css-deduplicator.py`)
Removes duplicate rules from CSS modules.
- Eliminates exact duplicate selectors within files
- Preserves rule order and functionality
- Generates detailed deduplication reports

```bash
python3 tools/css/css-deduplicator.py
```

### 7. CSS Extractor (`css-extractor.py`)
Extracts CSS from monolithic files into functional modules.
- Categorizes selectors by function (tables, forms, media, etc.)
- Creates modular CSS files for better maintainability
- Maintains build order recommendations

```bash
python3 tools/css/css-extractor.py input.css --output-dir modules/
```

### 8. CSS Build System (`css-build.py`)
Concatenates modular CSS files in correct order.
- Builds final CSS from modules
- Validates syntax and structure
- Generates comprehensive build manifests

```bash
python3 tools/css/css-build.py
```

## 🏗️ CSS Architecture

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
├── fixes.css             # Final overrides
└── wiki-integration.css  # Built final CSS (generated)
```

## 🚀 Development Workflow

### Perfect CSS Parity (Recommended)

**For maintaining perfect parity with reference CSS:**

1. **Run Perfect Sync** (one-shot solution):
   ```bash
   python3 tools/css/css-perfect-sync.py
   ```
   
2. **Verify results** - system will report:
   - ✅ Perfect parity achieved: X rules, 100% coverage
   - 💾 Perfect CSS saved to: app/src/main/assets/styles/wiki-integration-perfect.css

3. **Replace current CSS** with perfect version if needed

### Manual CSS Development

**For custom CSS additions or modifications:**

1. **Identify the appropriate module** based on functionality
2. **Edit the module file** directly (e.g., `modules/tables.css` for table styles)
3. **Run duplicate detection** to ensure no conflicts:
   ```bash
   python3 tools/css/css-duplicate-detector.py
   ```
4. **Rebuild the CSS**:
   ```bash
   python3 tools/css/css-build.py
   ```
5. **Test the changes** in the app

### CSS Analysis and Maintenance

1. **Check current coverage**:
   ```bash
   python3 tools/css/css-rule-analyzer.py
   ```

2. **Remove duplicates if needed**:
   ```bash
   python3 tools/css/css-deduplicator.py
   ```

3. **Consolidate similar rules**:
   ```bash
   python3 tools/css/css-rule-consolidator.py
   ```

## 📁 Output Files

All tool outputs are saved to `tools/css/output/`:
- `perfect_sync_analysis.json` - Perfect sync analysis and results
- `css_rule_analysis.json` - Detailed rule-level analysis
- `reference_profile_cache.json` - Cached reference CSS profile
- `deduplication_report.json` - Duplicate removal results
- `consolidation_report.json` - Rule consolidation results
- `build_manifest.json` - Build system output
- `perfect_css_draft.css` - Generated perfect CSS (if validation fails)

## ⚙️ Configuration

### Perfect Sync Configuration
The system uses `css-perfect-sync.yml` with these key settings:

```yaml
sync_policy:
  target_coverage: 100.0          # Always target perfect coverage
  allow_extra_rules: false        # Never allow extra rules
  
preservation_rules:
  css_variables: preserve_all     # Keep CSS variables for theming
  theming_overrides: preserve_essential
  local_enhancements: remove_all  # Remove non-reference rules

reference_source:
  url: "https://oldschool.runescape.wiki/load.php?lang=en&modules=site.styles&only=styles"
  cache_duration: "1h"           # Cache reference for performance
```

## 🎯 Best Practices

1. **Use Perfect Sync for parity** - Run `css-perfect-sync.py` regularly to maintain perfect parity
2. **Always run duplicate detection** before committing changes
3. **Use the build system** rather than manually editing `wiki-integration.css`
4. **Keep modules focused** on their specific functionality
5. **Test thoroughly** after CSS changes
6. **Monitor reference evolution** - Perfect Sync adapts automatically

## 🔧 Troubleshooting

### Perfect Sync Issues
- **Cache problems**: Delete `tools/css/output/reference_profile_cache.json` to refresh
- **Network issues**: Check connectivity to oldschool.runescape.wiki
- **Validation failures**: Review draft CSS and validation errors in analysis JSON

### Build Failures
- Check syntax with `python3 tools/css/css-build.py --validate`
- Review build manifest for detailed error information
- Ensure all required modules exist

### Coverage Issues  
- Run perfect sync to automatically resolve coverage gaps
- Check analysis files for detailed coverage breakdowns
- Verify reference CSS is accessible and current

## 🌟 Key Features Summary

- **🎯 Perfect Parity**: Achieve exactly 100% CSS coverage with 0 missing, 0 extra rules
- **🔄 Dynamic Adaptation**: Automatically adapts to any reference CSS size changes
- **🚀 One-Shot Operation**: Single command achieves perfect results
- **🛡️ Future-Proof**: Works with any reference evolution (1035 rules today, 1200+ tomorrow)
- **📊 Comprehensive Analysis**: Detailed coverage analysis and validation
- **🏗️ Modular Architecture**: Clean, maintainable CSS module system
- **🔧 Developer-Friendly**: Rich tooling for CSS development and maintenance

## 🤝 Contributing

When adding new tools or modifying existing ones:
1. Follow the existing code structure and documentation standards
2. Add appropriate error handling and validation
3. Update this README with new functionality
4. Test thoroughly with various CSS inputs
5. Maintain dynamic/generalizable approaches (no hardcoded values)
6. Ensure tools work with any reference CSS size