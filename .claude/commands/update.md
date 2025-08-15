# Update Command

Update OSRS Wiki app assets including maps, CSS, and JavaScript discovery data.

## Usage
```bash
/update [options]
```

## Options

### Update Targets
- `--all` - Update all assets (maps, CSS, and JS discovery) [default if no target specified]
- `--maps` - Update only map assets
- `--css` - Update only CSS assets  
- `--js-discovery` - Update only JavaScript module discovery

### Advanced Options
- `--force` - Force regeneration even if assets are up to date (maps only)
- `--dry-run` - Preview what would be updated without executing (maps only)
- `--verify` - Verify that assets exist and are accessible (maps only)
- `--check-freshness` - Check if local assets are up to date (maps only)

## Examples

```bash
# Update all assets
/update
/update --all

# Update specific asset types
/update --maps
/update --css
/update --js-discovery

# Force update with advanced options
/update --all --force
/update --maps --dry-run
/update --css --verify
```

## How it works

Claude will execute the unified asset updater tool that coordinates updates for:

1. **Map Assets** - Game world map tiles and data
2. **CSS Assets** - Synchronized stylesheets from upstream sources  
3. **JavaScript Discovery** - Module and API discovery from game client

## Asset Update Process

### 1. Environment Validation
- Verify required tools exist and are executable
- Check micromamba environment for map/CSS updates
- Validate Python dependencies

### 2. Freshness Checking
- **Maps**: Check against OpenRS2 Archive API for cache version changes
- **CSS**: Compare with upstream stylesheet sources
- **JS Discovery**: Scan for new/changed game modules

### 3. Asset Generation
- **Maps**: 4-step workflow (cache → dump → slice → convert to MBTiles)
- **CSS**: Perfect sync with upstream stylesheets
- **JS Discovery**: Update module masterlists and generate reports

### 4. Progress Reporting
- Real-time progress updates with timestamps
- Success/failure status for each asset type
- Comprehensive summary of all operations

## Map Asset Update Workflow

When updating maps, the tool performs a complete 4-step process:

### Step 1: Cache Setup
```bash
python3 setup_cache.py
```
Downloads latest OSRS cache from OpenRS2 Archive

### Step 2: Map Dumping  
```bash
./run_dumper.sh
```
Extracts map images from game cache using Java dumper

### Step 3: Tile Slicing
```bash
python3 slice_tiles.py
```
Converts dumped images into tile pyramid structure

### Step 4: MBTiles Generation
```bash
python3 map-asset-generator.py --convert-only
```
Packages tiles into MBTiles format for mobile apps

## CSS Asset Update Process

CSS updates use the perfect sync tool to:

1. **Fetch upstream stylesheets** from wiki sources
2. **Analyze differences** between local and remote versions
3. **Sync changes** while preserving local customizations
4. **Generate optimized output** for mobile consumption

## JavaScript Discovery Process

JS discovery scans the game client to:

1. **Detect new modules** and APIs
2. **Update masterlist files** with discovered modules
3. **Generate analysis reports** of implementation coverage
4. **Track changes** in game client architecture

## Environment Requirements

### Dependencies
- **Python 3.8+** with required packages
- **Micromamba environment** (`osrs-tools`) for maps and CSS
- **Java 17+** for map dumping (automatically handled)
- **Internet connection** for freshness checking and downloads

### Directory Structure
```
tools/
├── asset-updater.py          # Main updater script
├── map/
│   ├── map-asset-generator.py
│   ├── setup_cache.py
│   ├── slice_tiles.py
│   └── run_dumper.sh
├── css/
│   └── css-perfect-sync.py
└── js/
    └── update_discovery.py
```

## Output Locations

### Map Assets
```
shared/assets/
├── map_floor_0.mbtiles       # Ground floor
├── map_floor_1.mbtiles       # Floor 1
├── map_floor_2.mbtiles       # Floor 2
└── map_floor_3.mbtiles       # Floor 3
```

### CSS Assets
```
shared/assets/stylesheets/
└── [updated CSS files]
```

### JS Discovery Data
```
tools/js/masterlists/
├── discovered_modules.json
├── implemented_modules.json
└── unimplemented_modules.json
```

## Error Handling

### Common Issues

**Environment not ready:**
- Solution: Ensure micromamba environment exists and is activated
- Command: Check with `micromamba env list`

**Network connectivity:**
- Solution: Verify internet connection for freshness checking
- Fallback: Use `--force` to skip freshness checks

**Insufficient disk space:**
- Solution: Free up space for cache download and processing
- Requirement: ~2GB free space recommended

**Java not found (maps only):**
- Solution: Ensure Java 17+ is installed and in PATH
- Check: `java -version`

### Recovery Options

**Partial failure:**
- Re-run with specific asset type (`--maps`, `--css`, or `--js-discovery`)
- Use `--force` to regenerate failed assets

**Corrupted assets:**
- Use `--verify` to check asset integrity
- Delete corrupted files and re-run update

**Environment corruption:**
- Recreate micromamba environment
- Re-run with fresh environment setup

## Performance Notes

### Update Times (Approximate)
- **JS Discovery**: 30 seconds - 2 minutes
- **CSS Sync**: 1-3 minutes  
- **Map Assets**: 5-15 minutes (full regeneration)
- **Map Assets**: 30 seconds (no changes detected)

### Resource Usage
- **Disk**: 1-2GB temporary space during map generation
- **Memory**: 1-2GB peak during tile processing
- **Network**: 100-500MB for cache downloads

## Integration with Development Workflow

### Daily Development
```bash
# Quick check for updates
/update --check-freshness

# Update only changed assets  
/update --all
```

### Major Game Updates
```bash
# Force complete regeneration
/update --all --force
```

### Pre-Release Testing  
```bash
# Verify all assets are current
/update --all --verify
```

## Success Indicators

### Complete Success
- ✅ All requested asset types updated successfully
- ✅ No errors in generation or validation
- ✅ Assets available in expected output locations
- ✅ Freshness checks passed (or forced update completed)

### Partial Success
- ⚠️ Some asset types updated, others failed
- ⚠️ Clear indication of what succeeded/failed
- ⚠️ Recovery suggestions for failed components

### Update Summary
After completion, Claude provides:
- **Operation counts**: X/Y updates successful
- **Execution times**: Duration for each asset type
- **Asset locations**: Where updated files are stored
- **Next steps**: Recommendations for testing or deployment

## Related Commands

- `/clean` - Clean up session and temporary files
- `/deploy` - Deploy updated assets to target platforms
- `/test` - Run tests against updated assets
- `/start` - Begin new development session

## Technical Implementation

Claude will execute the asset updater by:

1. **Changing to tools directory**
```bash
cd tools/
```

2. **Running the unified updater**
```bash
python3 asset-updater.py [options]
```

3. **Monitoring output** and providing real-time progress updates

4. **Validating results** and reporting success/failure status

The asset updater handles all environment setup, dependency validation, and coordination between the individual update tools.

## 🔄 Assets Updated Successfully!

After Claude completes the update, your app will have:

### ✅ Current Assets
- **Map data** synchronized with latest game world
- **Stylesheets** updated from upstream sources
- **JS modules** reflecting current game client APIs
- **MBTiles** optimized for mobile map rendering

### ✅ Verified Integrity  
- **Freshness validated** against authoritative sources
- **File completeness** verified for all asset types
- **Format correctness** ensured for consumption by mobile apps
- **Performance optimized** with proper compression and tiling

**🎯 Your app assets are now current and ready for development or deployment!**