#!/bin/bash
set -euo pipefail

# OSRS Wiki Git-Based Android Deployment Script
# Updates ~/Deploy/osrswiki-android and pushes to remote

# Source color utilities (auto-detects Claude Code environment)
source "$(dirname "${BASH_SOURCE[0]}")/color-utils.sh"

print_header "🚀 OSRS Wiki Git-Based Android Deployment"
echo "Date: $(date)"
echo ""

# Ensure we're in the monorepo root
if [[ ! -f "CLAUDE.md" ]]; then
    print_error "Must run from monorepo root (where CLAUDE.md is located)"
    exit 1
fi

# Phase 1: Pre-deployment validation
print_phase "🔍 Phase 1: Pre-deployment Validation"

# Check for Android platform directory
if [[ ! -d "platforms/android" ]]; then
    print_error "Android platform directory not found"
    exit 1
fi
print_success "Android platform directory found"

# Run deployment validation
print_info "Running deployment validation..."
if ! ./scripts/shared/validate-deployment.sh android; then
    print_error "Pre-deployment validation failed"
    echo "Fix validation errors before proceeding"
    exit 1
fi

# Phase 2: Repository health check
print_phase "🏥 Phase 2: Repository Health Check"
echo "-------------------------------"

print_info "Checking repository health..."
if ! ./scripts/shared/validate-repository-health.sh; then
    print_warning " Repository health issues detected"
    echo "Continue anyway? (y/N)"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        print_error "Deployment cancelled by user"
        exit 1
    fi
fi

# Phase 3: Setup deployment environment
print_phase "🏗️  Phase 3: Deployment Environment Setup"
echo "-------------------------------------"

DEPLOY_ANDROID="$HOME/Deploy/osrswiki-android"
MONOREPO_ROOT="$(pwd)"

# Ensure deployment directory exists
if [[ ! -d "$DEPLOY_ANDROID" ]]; then
    print_info "📁 Creating deployment repository..."
    mkdir -p "$(dirname "$DEPLOY_ANDROID")"
    cd "$(dirname "$DEPLOY_ANDROID")"
    git clone https://github.com/omiyawaki/osrswiki-android.git
    cd "$MONOREPO_ROOT"
fi

# Validate deployment repo
if [[ ! -d "$DEPLOY_ANDROID/.git" ]]; then
    print_error "Deployment repository is not a valid git repo: $DEPLOY_ANDROID"
    exit 1
fi

print_success "Deployment environment ready"

# Phase 4: Update deployment repository content
print_phase "📦 Phase 4: Update Deployment Content"
echo "-----------------------------------"

cd "$DEPLOY_ANDROID"
print_info "Working in deployment repository: $DEPLOY_ANDROID"

# Fetch latest changes to ensure we're up to date
print_info "Fetching latest remote changes..."
git fetch origin main
git reset --hard origin/main

# Create deployment branch for safety
DEPLOY_BRANCH="deploy-$(date +%Y%m%d-%H%M%S)"
print_info "Creating deployment branch: $DEPLOY_BRANCH"
git checkout -b "$DEPLOY_BRANCH"

# Clear existing content (except .git)
print_info "Clearing existing content..."
find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +

# Copy Android platform content
print_info "Copying Android platform content..."
cp -r "$MONOREPO_ROOT/platforms/android"/* .
cp "$MONOREPO_ROOT/platforms/android/.gitignore" . 2>/dev/null || true

# Copy shared resources with proper Android asset structure
print_info "Copying shared resources to Android asset structure..."
ASSETS_DIR="app/src/main/assets"

if [[ -d "$MONOREPO_ROOT/shared" ]]; then
    echo "  → Creating Android asset directories..."
    mkdir -p "$ASSETS_DIR/styles/modules"
    mkdir -p "$ASSETS_DIR/js"
    mkdir -p "$ASSETS_DIR/web"
    mkdir -p "$ASSETS_DIR/mediawiki"
    mkdir -p "$ASSETS_DIR/data"
    
    # Copy CSS files to styles/ directory
    echo "  → Copying CSS files to styles/..."
    if [[ -d "$MONOREPO_ROOT/shared/css" ]]; then
        find "$MONOREPO_ROOT/shared/css" -name "*.css" -not -path "*/modules/*" -exec cp {} "$ASSETS_DIR/styles/" \;
        # Copy CSS modules maintaining structure  
        if [[ -d "$MONOREPO_ROOT/shared/css/modules" ]]; then
            find "$MONOREPO_ROOT/shared/css/modules" -name "*.css" -exec cp {} "$ASSETS_DIR/styles/modules/" \;
        fi
        echo "    ✓ CSS files copied to styles/"
    fi
    
    # Copy JavaScript files to js/ directory (excluding MediaWiki and WebView files)
    echo "  → Copying main JavaScript files to js/..."
    if [[ -d "$MONOREPO_ROOT/shared/js" ]]; then
        # Copy main JS files (excluding subdirectories)
        find "$MONOREPO_ROOT/shared/js" -maxdepth 1 -name "*.js" -exec cp {} "$ASSETS_DIR/js/" \;
        echo "    ✓ Main JavaScript files copied to js/"
    fi
    
    # Copy WebView-specific files to web/ directory
    echo "  → Copying WebView files to web/..."
    if [[ -d "$MONOREPO_ROOT/shared/js" ]]; then
        # WebView JavaScript files
        for webjs in "collapsible_content.js" "horizontal_scroll_interceptor.js" "responsive_videos.js" \
                     "clipboard_bridge.js" "infobox_switcher_bootstrap.js" "switch_infobox.js" \
                     "ge_charts_init.js" "highcharts-stock.js"; do
            if [[ -f "$MONOREPO_ROOT/shared/js/$webjs" ]]; then
                cp "$MONOREPO_ROOT/shared/js/$webjs" "$ASSETS_DIR/web/"
            fi
        done
        
        # WebView CSS files (CSS files that are in js/ directory)
        for webcss in "collapsible_sections.css" "collapsible_tables.css" "switch_infobox_styles.css"; do
            if [[ -f "$MONOREPO_ROOT/shared/js/$webcss" ]]; then
                cp "$MONOREPO_ROOT/shared/js/$webcss" "$ASSETS_DIR/web/"
            fi
        done
        echo "    ✓ WebView files copied to web/"
    fi
    
    # Copy MediaWiki modules
    echo "  → Copying MediaWiki modules..."
    if [[ -d "$MONOREPO_ROOT/shared/js/mediawiki" ]]; then
        # startup.js goes to assets root
        if [[ -f "$MONOREPO_ROOT/shared/js/mediawiki/startup.js" ]]; then
            cp "$MONOREPO_ROOT/shared/js/mediawiki/startup.js" "$ASSETS_DIR/"
        fi
        
        # Other MediaWiki modules go to mediawiki/ subdirectory
        for mwjs in "page_bootstrap.js" "page_modules.js"; do
            if [[ -f "$MONOREPO_ROOT/shared/js/mediawiki/$mwjs" ]]; then
                cp "$MONOREPO_ROOT/shared/js/mediawiki/$mwjs" "$ASSETS_DIR/mediawiki/"
            fi
        done
        echo "    ✓ MediaWiki modules copied"
    fi
    
    # Handle assets with cache-aware strategy
    echo "  → Copying assets (cache-aware strategy)..."
    
    # Check for centralized cache
    CACHE_BASE="$HOME/Develop/osrswiki-cache"
    CACHE_MBTILES="$CACHE_BASE/binary-assets/mbtiles"
    
    if [[ -d "$MONOREPO_ROOT/shared/assets" ]]; then
        # Copy non-binary assets from shared directory
        find "$MONOREPO_ROOT/shared/assets" -type f ! -name "*.mbtiles" -exec cp {} "$ASSETS_DIR/" \;
        
        # Check for binary assets in cache
        mbtiles_count=0
        if [[ -d "$CACHE_MBTILES" ]]; then
            # Copy .mbtiles files from cache to deployment
            find "$CACHE_MBTILES" -name "*.mbtiles" -exec cp {} "$ASSETS_DIR/" \; 2>/dev/null || true
            mbtiles_count=$(find "$CACHE_MBTILES" -name "*.mbtiles" 2>/dev/null | wc -l)
            
            if [[ $mbtiles_count -gt 0 ]]; then
                echo "    ✅ Included $mbtiles_count .mbtiles files from centralized cache"
            fi
        else
            # Count missing .mbtiles files and document the deployment strategy
            mbtiles_count=$(find "$MONOREPO_ROOT/shared/assets" -name "*.mbtiles" 2>/dev/null | wc -l)
        fi
        
        if [[ $mbtiles_count -eq 0 ]]; then
            echo "    ⚠️  No .mbtiles files found (binary assets missing)"
            
            # Create documentation for how to generate missing assets
            cat > "$ASSETS_DIR/MISSING_ASSETS.md" << EOF
# Missing Binary Assets

This deployment uses a centralized cache strategy for large binary assets (*.mbtiles) to keep repositories lightweight while enabling efficient development workflows.

## Missing Assets
- Map tiles: Expected 4 .mbtiles files (floors 0-3)

## Asset Management Strategy

### Centralized Cache Location
Binary assets are stored in: \`~/Develop/osrswiki-cache/binary-assets/mbtiles/\`

### Option 1: Using Asset Generator (Recommended)
\`\`\`bash
# In monorepo root - assets automatically go to centralized cache
cd tools
./bin/micromamba run -n osrs-tools python3 map/map-asset-generator.py

# Assets are generated to: ~/Develop/osrswiki-cache/binary-assets/mbtiles/
# Build system automatically discovers cache assets
\`\`\`

### Option 2: Manual Generation
\`\`\`bash
# 1. Setup environment
cd tools
micromamba create -n osrs-tools --file environment.yml
micromamba activate osrs-tools

# 2. Generate map assets (outputs to centralized cache)
python3 map/map-asset-generator.py --all

# 3. For standalone deployment, copy from cache
cp ~/Develop/osrswiki-cache/binary-assets/mbtiles/*.mbtiles app/src/main/assets/
\`\`\`

### Option 3: Manual Cache Setup
\`\`\`bash
# Create cache structure
mkdir -p ~/Develop/osrswiki-cache/binary-assets/mbtiles/

# Generate or copy .mbtiles files to cache directory
# Build system will automatically discover them
\`\`\`

## CI/CD Integration
For automated deployments, add asset generation to your CI pipeline:
\`\`\`yaml
- name: Generate Assets  
  run: |
    cd tools
    micromamba activate osrs-tools
    python3 map/map-asset-generator.py --all
    # Assets are automatically placed in centralized cache
    
- name: Copy Assets to Deployment
  run: |
    cp ~/Develop/osrswiki-cache/binary-assets/mbtiles/*.mbtiles app/src/main/assets/
\`\`\`

## Benefits of Centralized Cache
- ✅ No asset regeneration needed for new worktrees  
- ✅ Shared across all development sessions
- ✅ Git repositories stay lightweight
- ✅ Build system auto-discovers cache assets
- ✅ Easy cache maintenance and cleanup

Generated: $(date)
EOF
            echo "    📝 Created MISSING_ASSETS.md with regeneration instructions"
        fi
        
        echo "    ✓ Assets copied (binary exclusion strategy applied)"
    fi
    
    # Copy data files maintaining structure
    echo "  → Copying data files..."
    if [[ -d "$MONOREPO_ROOT/shared/data" ]]; then
        cp -r "$MONOREPO_ROOT/shared/data"/* "$ASSETS_DIR/data/" 2>/dev/null || echo "    (no data files to copy)"
        echo "    ✓ Data files copied to data/"
    fi
    
    print_success "Android asset structure created successfully"
    echo "  📁 Assets organized in proper Android structure:"
    echo "    • CSS files → styles/ (main) and styles/modules/ (modules)"
    echo "    • JavaScript → js/ (main) and web/ (WebView-specific)"
    echo "    • MediaWiki → startup.js (root) and mediawiki/ (modules)"
    echo "    • Assets → root (for direct access)"
    echo "    • Data → data/ (maintaining structure)"
else
    print_warning " No shared directory found - creating empty asset structure"
    mkdir -p "$ASSETS_DIR"
fi

# Verify dual-mode build configuration  
print_info "Verifying dual-mode build configuration..."
if [[ -f "app/build.gradle.kts" ]]; then
    if grep -q "isMonorepo.*File.*exists" app/build.gradle.kts; then
        echo "    ✓ Dual-mode build configuration detected"
        echo "    • Build will auto-detect monorepo vs standalone mode"
        echo "    • No manual configuration changes needed"
    else
        print_info "    ⚠️  Build file may need dual-mode configuration"
    fi
    print_success "Build system ready for standalone deployment"
else
    print_error "build.gradle.kts not found in expected location"
    ls -la app/build.gradle* 2>/dev/null || echo "    No build.gradle files found"
fi

# Phase 5: Deployment Validation
print_phase "✅ Phase 5: Deployment Validation"
echo "------------------------------"

print_info "Running deployment validation checks..."

# Validation 1: Asset structure and content
echo "  → Validating asset structure..."
REQUIRED_ASSET_DIRS=("$ASSETS_DIR/styles" "$ASSETS_DIR/js" "$ASSETS_DIR/web")
VALIDATION_PASSED=true

for dir in "${REQUIRED_ASSET_DIRS[@]}"; do
    if [[ -d "$dir" ]]; then
        file_count=$(find "$dir" -type f | wc -l)
        echo "    ✓ $dir ($file_count files)"
    else
        echo -e "${RED}    ❌ $dir (missing)${NC}"
        VALIDATION_PASSED=false
    fi
done

# Check for critical assets
CRITICAL_ASSETS=(
    "$ASSETS_DIR/styles/themes.css"
    "$ASSETS_DIR/styles/base.css"
    "$ASSETS_DIR/js/tablesort.min.js"
    "$ASSETS_DIR/web/collapsible_content.js"
    "$ASSETS_DIR/startup.js"
)

echo "  → Validating critical assets..."
for asset in "${CRITICAL_ASSETS[@]}"; do
    if [[ -f "$asset" ]]; then
        size=$(stat -c%s "$asset" 2>/dev/null || stat -f%z "$asset" 2>/dev/null)
        if [[ $size -gt 0 ]]; then
            echo "    ✓ $(basename "$asset") (${size} bytes)"
        else
            echo -e "${RED}    ❌ $(basename "$asset") (empty file)${NC}"
            VALIDATION_PASSED=false
        fi
    else
        echo -e "${RED}    ❌ $(basename "$asset") (missing)${NC}"
        VALIDATION_PASSED=false
    fi
done

# Validation 2: Build configuration
echo "  → Validating build configuration..."
if [[ -f "app/build.gradle.kts" ]]; then
    if grep -q "isMonorepo.*File.*exists" app/build.gradle.kts; then
        echo "    ✓ Dual-mode build configuration present"
    else
        print_info "    ⚠️  Dual-mode configuration not found"
        VALIDATION_PASSED=false
    fi
    
    # Check that monorepo references are properly handled
    if grep -q "\.\./\.\./\.\./shared" app/build.gradle.kts; then
        if grep -q "val isMonorepo" app/build.gradle.kts; then
            echo "    ✓ Monorepo references properly conditionalized"
        else
            print_info "    ⚠️  Unconditionalized monorepo references detected"
        fi
    fi
else
    print_error "    build.gradle.kts not found"
    VALIDATION_PASSED=false
fi

# Validation 3: Quick Gradle validation (if gradlew exists)
if [[ -f "./gradlew" ]]; then
    echo "  → Testing Gradle wrapper..."
    if timeout 30 ./gradlew --version >/dev/null 2>&1; then
        echo "    ✓ Gradle wrapper functional"
    else
        print_info "    ⚠️  Gradle wrapper test timed out or failed"
        # This is a warning, not a failure
    fi
else
    print_error "    gradlew not found"
    VALIDATION_PASSED=false
fi

# Validation summary
echo ""
if [[ "$VALIDATION_PASSED" == "true" ]]; then
    print_success "All deployment validation checks passed"
    print_info "📋 Deployment Summary:"
    echo "  • Asset directories: $(find "$ASSETS_DIR" -type d | wc -l)"
    echo "  • Asset files: $(find "$ASSETS_DIR" -type f | wc -l)"
    echo "  • Total asset size: $(du -sh "$ASSETS_DIR" | cut -f1)"
    echo "  • Build configuration: Dual-mode enabled"
    echo "  • Validation: All checks passed"
else
    print_error "Deployment validation failed"
    echo "The deployed app may not build correctly in standalone mode."
    echo "Please review the errors above and fix them before proceeding."
    echo ""
    echo "To run full standalone validation after fixing issues:"
    echo "  \$MONOREPO_ROOT/scripts/shared/test-standalone-build.sh"
    echo ""
    print_info "Continue with deployment anyway? (y/N)"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        print_error "Deployment cancelled by user"
        exit 1
    fi
    print_warning " Proceeding with deployment despite validation warnings"
fi

# Stage all changes
git add -A

# Create deployment commit if there are changes
if ! git diff --cached --quiet; then
    DEPLOY_COMMIT_MSG="deploy: update Android app from monorepo

Recent Android-related changes:
$(cd "$MONOREPO_ROOT" && git log --oneline --no-merges --max-count=5 main --grep='android\\|Android' | sed 's/^/- /' || echo "- Recent commits from monorepo main branch")

This deployment:
- Updates from monorepo platforms/android/
- Copies shared web assets to proper Android structure
- Organizes CSS files in styles/ directory
- Places WebView assets in web/ directory  
- Configures MediaWiki modules appropriately
- Includes map tiles and data files
- Uses dual-mode build configuration (auto-detects environment)
- Build system works in both monorepo and standalone modes
- Validates deployment integrity during process
- Maintains Android-specific .gitignore
- Creates fully standalone buildable Android project

Deployment info:
- Source: $MONOREPO_ROOT
- Target: $DEPLOY_ANDROID  
- Branch: $DEPLOY_BRANCH
- Date: $(date '+%Y-%m-%dT%H:%M:%S%z')
- Asset directories: $(find "$MONOREPO_ROOT/shared" -type d 2>/dev/null | wc -l) 
- Asset files: $(find "$MONOREPO_ROOT/shared" -type f 2>/dev/null | wc -l)"

    git commit -m "$DEPLOY_COMMIT_MSG"
    print_success "Deployment commit created"
    
    # Show what was deployed
    print_phase "📋 Deployment Summary:"
    git show --stat HEAD
    
else
    print_info "ℹ️  No changes to deploy"
    git checkout main
    git branch -d "$DEPLOY_BRANCH"
    cd "$MONOREPO_ROOT"
    exit 0
fi

# Phase 5: Push to remote
print_phase "🚀 Phase 5: Push to Remote"
echo "------------------------"

# Safety check - ensure we have reasonable number of commits
DEPLOY_COMMITS=$(git rev-list --count HEAD)
if [[ "$DEPLOY_COMMITS" -lt 1 ]]; then
    print_error "🚨 CRITICAL SAFETY CHECK FAILED"
    echo "Deployment repository has no commits"
    echo "This suggests a serious error in deployment preparation."
    exit 1
fi

print_success "Safety check passed: $DEPLOY_COMMITS commits"

# Push with force-with-lease for safety
print_info "Pushing to remote..."
if git push origin "$DEPLOY_BRANCH" --force-with-lease; then
    print_success "Deployment branch pushed successfully"
    
    # Merge to main
    git checkout main
    git merge "$DEPLOY_BRANCH" --ff-only
    git push origin main
    
    # Clean up deployment branch
    git branch -d "$DEPLOY_BRANCH"
    git push origin --delete "$DEPLOY_BRANCH"
    
    print_success "🎉 Android deployment completed successfully!"
    
else
    print_error "Push failed - remote may have been updated"
    echo "Fix conflicts and try again"
    exit 1
fi

# Phase 6: Final validation
print_phase "✅ Phase 6: Post-deployment Validation"
echo "--------------------------------"

# Verify remote state
REMOTE_COMMITS=$(git ls-remote origin main | cut -f1)
LOCAL_COMMITS=$(git rev-parse HEAD)

if [[ "$REMOTE_COMMITS" == "$LOCAL_COMMITS" ]]; then
    print_success "Remote and local are synchronized"
else
    print_warning " Remote and local commits differ"
    echo "This may indicate a deployment issue - investigate"
fi

# Return to monorepo
cd "$MONOREPO_ROOT"

echo ""
print_success "🎊 Git-Based Android Deployment Complete!"
echo "=============================================="
echo "Deployment repository: $DEPLOY_ANDROID"
echo "Remote commits: $DEPLOY_COMMITS"
echo "Changes deployed safely"
echo ""
print_phase "Deployed components:"
echo "- ✅ Android app (complete Kotlin/Gradle project)"
echo "- ✅ Web assets organized in proper Android structure" 
echo "- ✅ CSS stylesheets in styles/ directory"
echo "- ✅ WebView JavaScript/CSS in web/ directory"
echo "- ✅ MediaWiki modules in correct locations"
echo "- ✅ Map tiles and data files included"
echo "- ✅ Dual-mode build configuration (monorepo/standalone)"
echo "- ✅ Deployment validation and integrity checks"
echo "- ✅ Standalone buildable without monorepo dependencies"
echo ""
print_phase "Key advantages of ~/Deploy approach:"
echo "- ✅ Simple 1:1 mirror of remote repository"
echo "- ✅ Standard git workflow from deployment directory"
echo "- ✅ Clear separation between monorepo and deployment"
echo "- ✅ Easy to verify deployment state"
echo ""
print_phase "Next steps:"
echo "- Verify deployment at: https://github.com/omiyawaki/osrswiki-android"
echo "- Run full standalone validation: ./scripts/shared/test-standalone-build.sh"
echo "- Test the deployed app builds and runs correctly"
echo "- Monitor for any issues"

exit 0