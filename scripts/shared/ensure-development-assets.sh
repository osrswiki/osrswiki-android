#!/bin/bash
set -euo pipefail

# OSRS Wiki Development Asset Provisioning Script
# Ensures development/worktree environments have all necessary assets

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧰 Development Asset Provisioning${NC}"
echo "==================================="
echo ""

# Ensure we're in the monorepo root or a worktree
if [[ ! -f "CLAUDE.md" ]]; then
    echo -e "${RED}❌ Must run from monorepo root or worktree (where CLAUDE.md is located)${NC}"
    exit 1
fi

MONOREPO_ROOT="$(pwd)"
CACHE_BASE="$HOME/Develop/osrswiki-cache"
MBTILES_CACHE="$CACHE_BASE/binary-assets/mbtiles"

echo -e "${BLUE}📋 Phase 1: Asset Availability Check${NC}"
echo "--------------------------------"

# Check for centralized cache
if [[ ! -d "$CACHE_BASE" ]]; then
    echo -e "${YELLOW}⚠️  Centralized cache not found, initializing...${NC}"
    ./scripts/shared/manage-asset-cache.sh init
fi

# Check for .mbtiles files in centralized cache
echo "Checking for development binary assets in cache..."
mbtiles_present=$(find "$MBTILES_CACHE" -name "*.mbtiles" 2>/dev/null | wc -l)
mbtiles_expected=4  # floors 0-3

if [[ $mbtiles_present -eq $mbtiles_expected ]]; then
    echo -e "${GREEN}✅ All $mbtiles_expected .mbtiles files present${NC}"
    echo ""
    echo -e "${GREEN}🎉 Development environment fully provisioned!${NC}"
    echo "- Worktree has access to all development assets"
    echo "- Map functionality will work correctly"
    echo "- Testing can proceed with full asset coverage"
    exit 0
elif [[ $mbtiles_present -gt 0 ]]; then
    echo -e "${YELLOW}⚠️  Partial assets: $mbtiles_present/$mbtiles_expected .mbtiles files present${NC}"
else
    echo -e "${YELLOW}⚠️  No .mbtiles files found - development assets missing${NC}"
fi

echo ""
echo -e "${BLUE}🔧 Phase 2: Asset Generation${NC}"
echo "-------------------------"

# Check if tools environment is available
TOOLS_DIR="$MONOREPO_ROOT/tools"
if [[ ! -d "$TOOLS_DIR" ]]; then
    echo -e "${RED}❌ Tools directory not found: $TOOLS_DIR${NC}"
    exit 1
fi

cd "$TOOLS_DIR"

# Check for micromamba environment
if [[ -x "./bin/micromamba" ]]; then
    echo -e "${GREEN}✅ Micromamba environment found${NC}"
    
    # Generate missing assets
    echo -e "${YELLOW}🏗️  Generating missing binary assets...${NC}"
    echo "This may take several minutes for large assets..."
    
    if ./bin/micromamba run -n osrs-tools python3 map/map-asset-generator.py --missing-only; then
        echo -e "${GREEN}✅ Asset generation completed${NC}"
        
        # Verify assets were created in cache
        cd "$MONOREPO_ROOT"
        mbtiles_final=$(find "$MBTILES_CACHE" -name "*.mbtiles" 2>/dev/null | wc -l)
        
        if [[ $mbtiles_final -eq $mbtiles_expected ]]; then
            echo -e "${GREEN}🎉 Development environment fully provisioned!${NC}"
            echo "- Generated $mbtiles_expected .mbtiles files"
            echo "- Worktree ready for full testing"
            echo "- Map functionality will work correctly"
        else
            echo -e "${YELLOW}⚠️  Partial success: $mbtiles_final/$mbtiles_expected assets generated${NC}"
            echo "Some assets may still be missing - check map generation logs"
        fi
    else
        echo -e "${RED}❌ Asset generation failed${NC}"
        echo "Check the tools environment and try manual generation:"
        echo "  cd tools"
        echo "  ./bin/micromamba run -n osrs-tools python3 map/map-asset-generator.py"
        exit 1
    fi
    
else
    echo -e "${YELLOW}⚠️  Micromamba not found - manual setup required${NC}"
    echo ""
    echo -e "${BLUE}Manual Setup Instructions:${NC}"
    echo "1. Set up the tools environment:"
    echo "   cd $TOOLS_DIR"
    echo "   curl -Ls https://micro.mamba.pm/api/micromamba/osx-64/latest | tar -xvj -C bin"
    echo "   ./bin/micromamba create -n osrs-tools --file environment.yml"
    echo ""
    echo "2. Generate development assets:"
    echo "   ./bin/micromamba run -n osrs-tools python3 map/map-asset-generator.py"
    echo ""
    echo "3. Re-run this script to verify:"
    echo "   ./scripts/shared/ensure-development-assets.sh"
fi

echo ""
echo -e "${BLUE}💡 Development Asset Strategy:${NC}"
echo "- ✅ .mbtiles files available in development/worktrees"
echo "- ✅ .mbtiles files excluded from git commits (too large)"
echo "- ✅ Generated on-demand when needed"
echo "- ✅ Deployment repos get documentation instead of binaries"
echo "- ✅ CI/CD can generate assets as needed"

cd "$MONOREPO_ROOT"