#!/bin/bash
set -euo pipefail

# OSRS Wiki Standalone Build Test Script
# Tests that deployed Android app builds independently without monorepo dependencies

# Colors for output  
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 OSRS Wiki Standalone Build Test${NC}"
echo "======================================="
echo "Date: $(date)"
echo ""

# Configuration
TEST_DIR="$HOME/tmp/osrswiki-standalone-test-$(date +%Y%m%d-%H%M%S)"
ANDROID_REPO_URL="https://github.com/omiyawaki/osrswiki-android.git"
CLEANUP_ON_SUCCESS=${CLEANUP_ON_SUCCESS:-true}
CLEANUP_ON_FAILURE=${CLEANUP_ON_FAILURE:-false}

# Validation mode (can be overridden)
VALIDATION_MODE=${1:-"full"}  # Options: quick, full, assets-only

echo -e "${CYAN}Test Configuration:${NC}"
echo "• Test directory: $TEST_DIR"
echo "• Android repo: $ANDROID_REPO_URL"
echo "• Validation mode: $VALIDATION_MODE"
echo "• Cleanup on success: $CLEANUP_ON_SUCCESS"
echo "• Cleanup on failure: $CLEANUP_ON_FAILURE"
echo ""

# Test results tracking
declare -A TEST_RESULTS
TEST_RESULTS[clone]="pending"
TEST_RESULTS[asset_structure]="pending"
TEST_RESULTS[asset_content]="pending"
TEST_RESULTS[build_config]="pending"
TEST_RESULTS[gradle_sync]="pending"
TEST_RESULTS[compile]="pending"
TEST_RESULTS[unit_tests]="pending"

print_test_summary() {
    echo ""
    echo -e "${BLUE}📊 Test Results Summary${NC}"
    echo "======================="
    local passed=0
    local failed=0
    local total=0
    
    for test_name in "${!TEST_RESULTS[@]}"; do
        local status="${TEST_RESULTS[$test_name]}"
        total=$((total + 1))
        case "$status" in
            "passed") 
                echo -e "  ✅ $test_name: ${GREEN}PASSED${NC}"
                passed=$((passed + 1))
                ;;
            "failed")
                echo -e "  ❌ $test_name: ${RED}FAILED${NC}"
                failed=$((failed + 1))
                ;;
            "skipped")
                echo -e "  ⏭️  $test_name: ${YELLOW}SKIPPED${NC}"
                ;;
            "pending")
                echo -e "  ⏸️  $test_name: ${YELLOW}PENDING${NC}"
                ;;
        esac
    done
    
    echo ""
    echo -e "${CYAN}Overall: $passed passed, $failed failed, $total total${NC}"
    
    if [[ $failed -eq 0 ]]; then
        echo -e "${GREEN}🎉 All tests passed! Standalone build is working correctly.${NC}"
        return 0
    else
        echo -e "${RED}💥 Some tests failed. Standalone build needs attention.${NC}"
        return 1
    fi
}

cleanup() {
    local exit_code=$?
    echo ""
    echo -e "${YELLOW}🧹 Cleanup Phase${NC}"
    
    if [[ -d "$TEST_DIR" ]]; then
        local should_cleanup=false
        
        if [[ $exit_code -eq 0 && "$CLEANUP_ON_SUCCESS" == "true" ]]; then
            should_cleanup=true
            echo "  → Cleaning up (successful test, cleanup enabled)"
        elif [[ $exit_code -ne 0 && "$CLEANUP_ON_FAILURE" == "true" ]]; then
            should_cleanup=true
            echo "  → Cleaning up (failed test, cleanup enabled)"
        else
            echo "  → Preserving test directory for inspection"
            echo "    Directory: $TEST_DIR"
        fi
        
        if [[ "$should_cleanup" == "true" ]]; then
            rm -rf "$TEST_DIR"
            echo "    ✓ Test directory removed"
        fi
    fi
    
    print_test_summary
    exit $exit_code
}

trap cleanup EXIT

# Phase 1: Clone standalone repository
echo -e "${BLUE}📥 Phase 1: Clone Standalone Repository${NC}"
echo "--------------------------------------"

echo "Creating test directory: $TEST_DIR"
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

echo "Cloning Android repository..."
if git clone "$ANDROID_REPO_URL" android-app; then
    TEST_RESULTS[clone]="passed"
    echo -e "${GREEN}✅ Repository cloned successfully${NC}"
    cd android-app
else
    TEST_RESULTS[clone]="failed"
    echo -e "${RED}❌ Failed to clone repository${NC}"
    exit 1
fi

# Phase 2: Validate Asset Structure
echo ""
echo -e "${BLUE}📁 Phase 2: Validate Asset Structure${NC}"
echo "-----------------------------------"

ASSETS_DIR="app/src/main/assets"
REQUIRED_DIRS=("styles" "js" "web" "mediawiki")

echo "Checking asset directory structure..."
if [[ ! -d "$ASSETS_DIR" ]]; then
    TEST_RESULTS[asset_structure]="failed"
    echo -e "${RED}❌ Assets directory not found: $ASSETS_DIR${NC}"
else
    local missing_dirs=0
    for dir in "${REQUIRED_DIRS[@]}"; do
        if [[ -d "$ASSETS_DIR/$dir" ]]; then
            echo "  ✓ $ASSETS_DIR/$dir"
        else
            echo -e "${YELLOW}  ⚠️  $ASSETS_DIR/$dir (missing)${NC}"
            missing_dirs=$((missing_dirs + 1))
        fi
    done
    
    if [[ $missing_dirs -eq 0 ]]; then
        TEST_RESULTS[asset_structure]="passed"
        echo -e "${GREEN}✅ Asset structure is correct${NC}"
    else
        TEST_RESULTS[asset_structure]="failed" 
        echo -e "${YELLOW}⚠️  Some asset directories are missing${NC}"
    fi
fi

# Phase 3: Validate Asset Content
echo ""
echo -e "${BLUE}📋 Phase 3: Validate Asset Content${NC}"
echo "----------------------------------"

# Load expected assets from our mapping configuration
EXPECTED_ASSETS=(
    "styles/themes.css"
    "styles/base.css"
    "styles/fonts.css"
    "js/tablesort.min.js"
    "web/collapsible_content.js"
    "web/collapsible_sections.css"
    "startup.js"
    # Note: *.mbtiles files are excluded from deployment (large binaries)
)

echo "Checking required asset files..."
local missing_assets=0
for asset in "${EXPECTED_ASSETS[@]}"; do
    if [[ -f "$ASSETS_DIR/$asset" ]]; then
        # Get file size for verification
        local size=$(stat -c%s "$ASSETS_DIR/$asset" 2>/dev/null || stat -f%z "$ASSETS_DIR/$asset" 2>/dev/null || echo "?")
        echo "  ✓ $asset ($size bytes)"
    else
        echo -e "${RED}  ❌ $asset (missing)${NC}"
        missing_assets=$((missing_assets + 1))
    fi
done

if [[ $missing_assets -eq 0 ]]; then
    TEST_RESULTS[asset_content]="passed"
    echo -e "${GREEN}✅ All required assets are present${NC}"
else
    TEST_RESULTS[asset_content]="failed"
    echo -e "${RED}❌ $missing_assets required assets are missing${NC}"
fi

# Phase 4: Validate Build Configuration
echo ""
echo -e "${BLUE}⚙️  Phase 4: Validate Build Configuration${NC}"
echo "----------------------------------------"

echo "Checking build.gradle.kts configuration..."
if [[ -f "app/build.gradle.kts" ]]; then
    if grep -q "isMonorepo.*File.*exists" app/build.gradle.kts; then
        echo "  ✓ Dual-mode configuration detected"
        
        # Check for monorepo references that shouldn't be there
        if grep -q "\.\./\.\./\.\./shared" app/build.gradle.kts; then
            echo "  ✓ Monorepo paths present (expected for dual-mode)"
        fi
        
        TEST_RESULTS[build_config]="passed"
        echo -e "${GREEN}✅ Build configuration is correct${NC}"
    else
        echo -e "${YELLOW}  ⚠️  Dual-mode configuration not detected${NC}"
        echo "  → This may indicate an older build configuration"
        TEST_RESULTS[build_config]="failed"
    fi
else
    echo -e "${RED}  ❌ app/build.gradle.kts not found${NC}"
    TEST_RESULTS[build_config]="failed"
fi

# Exit early for assets-only validation
if [[ "$VALIDATION_MODE" == "assets-only" ]]; then
    echo ""
    echo -e "${CYAN}ℹ️  Validation mode: assets-only - stopping here${NC}"
    # Mark remaining tests as skipped
    for test in gradle_sync compile unit_tests; do
        TEST_RESULTS[$test]="skipped"
    done
    exit 0
fi

# Phase 5: Gradle Sync Test
echo ""
echo -e "${BLUE}🔄 Phase 5: Gradle Sync Test${NC}"
echo "----------------------------"

echo "Testing Gradle project sync..."
if [[ "$VALIDATION_MODE" == "quick" ]]; then
    echo -e "${CYAN}  (Quick mode - using --dry-run)${NC}"
    if ./gradlew --dry-run tasks; then
        TEST_RESULTS[gradle_sync]="passed"
        echo -e "${GREEN}✅ Gradle sync successful (dry-run)${NC}"
    else
        TEST_RESULTS[gradle_sync]="failed"
        echo -e "${RED}❌ Gradle sync failed${NC}"
    fi
else
    echo -e "${CYAN}  (Full mode - complete sync)${NC}"
    if timeout 300 ./gradlew tasks --console=plain; then
        TEST_RESULTS[gradle_sync]="passed"
        echo -e "${GREEN}✅ Gradle sync successful${NC}"
    else
        TEST_RESULTS[gradle_sync]="failed"
        echo -e "${RED}❌ Gradle sync failed or timed out${NC}"
    fi
fi

# Phase 6: Compilation Test
echo ""
echo -e "${BLUE}🔨 Phase 6: Compilation Test${NC}"
echo "----------------------------"

if [[ "${TEST_RESULTS[gradle_sync]}" == "passed" ]]; then
    echo "Testing Android app compilation..."
    if [[ "$VALIDATION_MODE" == "quick" ]]; then
        echo -e "${CYAN}  (Quick mode - debug build only)${NC}"
        if timeout 600 ./gradlew assembleDebug --console=plain; then
            TEST_RESULTS[compile]="passed"
            echo -e "${GREEN}✅ Debug build successful${NC}"
            
            # Check if APK was created
            if find app/build/outputs/apk -name "*.apk" | grep -q .; then
                local apk_size=$(find app/build/outputs/apk -name "*.apk" -exec stat -c%s {} \; | head -1)
                echo "    APK created: ${apk_size} bytes"
            fi
        else
            TEST_RESULTS[compile]="failed"
            echo -e "${RED}❌ Debug build failed${NC}"
        fi
    else
        echo -e "${CYAN}  (Full mode - debug and release builds)${NC}"
        if timeout 900 ./gradlew assemble --console=plain; then
            TEST_RESULTS[compile]="passed"
            echo -e "${GREEN}✅ Full build successful${NC}"
            
            # List created APKs
            echo "    Created APKs:"
            find app/build/outputs/apk -name "*.apk" -exec basename {} \; | sed 's/^/      • /'
        else
            TEST_RESULTS[compile]="failed"
            echo -e "${RED}❌ Full build failed${NC}"
        fi
    fi
else
    TEST_RESULTS[compile]="skipped"
    echo -e "${YELLOW}⏭️  Skipping compilation (Gradle sync failed)${NC}"
fi

# Phase 7: Unit Tests
echo ""
echo -e "${BLUE}🧪 Phase 7: Unit Tests${NC}"
echo "----------------------"

if [[ "${TEST_RESULTS[compile]}" == "passed" ]]; then
    echo "Running unit tests..."
    if timeout 300 ./gradlew testDebugUnitTest --console=plain; then
        TEST_RESULTS[unit_tests]="passed"
        echo -e "${GREEN}✅ Unit tests passed${NC}"
        
        # Look for test results
        if [[ -d "app/build/test-results" ]]; then
            local test_count=$(find app/build/test-results -name "*.xml" | wc -l)
            echo "    Test result files: $test_count"
        fi
    else
        TEST_RESULTS[unit_tests]="failed" 
        echo -e "${RED}❌ Unit tests failed${NC}"
    fi
else
    TEST_RESULTS[unit_tests]="skipped"
    echo -e "${YELLOW}⏭️  Skipping unit tests (compilation failed)${NC}"
fi

echo ""
echo -e "${GREEN}🏁 Standalone Build Test Complete${NC}"
echo "=================================="