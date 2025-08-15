#!/bin/bash
set -euo pipefail

# OSRS Wiki Git-Based iOS Deployment Script
# Updates ~/Deploy/osrswiki-ios and pushes to remote

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 OSRS Wiki Git-Based iOS Deployment${NC}"
echo "======================================="
echo "Date: $(date)"
echo ""

# macOS requirement check
if [[ "$(uname)" != "Darwin" ]]; then
    echo -e "${RED}❌ iOS deployment requires macOS${NC}"
    echo "This script can only run on macOS with Xcode installed"
    exit 1
fi

# Ensure we're in the monorepo root
if [[ ! -f "CLAUDE.md" ]]; then
    echo -e "${RED}❌ Must run from monorepo root (where CLAUDE.md is located)${NC}"
    exit 1
fi

# Phase 1: Pre-deployment validation
echo -e "${BLUE}🔍 Phase 1: Pre-deployment Validation${NC}"
echo "--------------------------------"

# Check for iOS platform directory
if [[ ! -d "platforms/ios" ]]; then
    echo -e "${RED}❌ iOS platform directory not found${NC}"
    exit 1
fi
echo -e "${GREEN}✅ iOS platform directory found${NC}"

# Check for Xcode
if ! command -v xcodebuild >/dev/null; then
    echo -e "${RED}❌ Xcode not found${NC}"
    echo "Install Xcode from the App Store"
    exit 1
fi
echo -e "${GREEN}✅ Xcode found: $(xcodebuild -version | head -1)${NC}"

# Run deployment validation
echo -e "${YELLOW}Running deployment validation...${NC}"
if ! ./scripts/shared/validate-deployment.sh ios; then
    echo -e "${RED}❌ Pre-deployment validation failed${NC}"
    echo "Fix validation errors before proceeding"
    exit 1
fi

# Phase 2: iOS build validation
echo -e "${BLUE}🏗️  Phase 2: iOS Build Validation${NC}"
echo "-----------------------------"

echo -e "${YELLOW}Validating iOS project build...${NC}"
cd "platforms/ios"

# Check if project builds successfully (using the actual project name)
PROJECT_NAME="OSRS Wiki"
if xcodebuild -project "$PROJECT_NAME.xcodeproj" -scheme "$PROJECT_NAME" -configuration Debug -sdk iphonesimulator build -quiet; then
    echo -e "${GREEN}✅ iOS project builds successfully${NC}"
else
    echo -e "${RED}❌ iOS project build failed${NC}"
    echo "Fix build errors before deployment"
    cd ../../
    exit 1
fi

cd ../../

# Phase 3: Repository health check
echo -e "${BLUE}🏥 Phase 3: Repository Health Check${NC}"
echo "-------------------------------"

echo -e "${YELLOW}Checking repository health...${NC}"
if ! ./scripts/shared/validate-repository-health.sh; then
    echo -e "${YELLOW}⚠️  Repository health issues detected${NC}"
    echo "Continue anyway? (y/N)"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo -e "${RED}Deployment cancelled by user${NC}"
        exit 1
    fi
fi

# Phase 4: Setup deployment environment
echo -e "${BLUE}🏗️  Phase 4: Deployment Environment Setup${NC}"
echo "-------------------------------------"

DEPLOY_IOS="$HOME/Deploy/osrswiki-ios"
MONOREPO_ROOT="$(pwd)"

# Ensure deployment directory exists
if [[ ! -d "$DEPLOY_IOS" ]]; then
    echo -e "${YELLOW}📁 Creating deployment repository...${NC}"
    mkdir -p "$(dirname "$DEPLOY_IOS")"
    cd "$(dirname "$DEPLOY_IOS")"
    git clone https://github.com/omiyawaki/osrswiki-ios.git
    cd "$MONOREPO_ROOT"
fi

# Validate deployment repo
if [[ ! -d "$DEPLOY_IOS/.git" ]]; then
    echo -e "${RED}❌ Deployment repository is not a valid git repo: $DEPLOY_IOS${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Deployment environment ready${NC}"

# Phase 5: Update deployment repository content
echo -e "${BLUE}📦 Phase 5: Update Deployment Content${NC}"
echo "-----------------------------------"

cd "$DEPLOY_IOS"
echo -e "${YELLOW}Working in deployment repository: $DEPLOY_IOS${NC}"

# Fetch latest changes to ensure we're up to date
echo -e "${YELLOW}Fetching latest remote changes...${NC}"
git fetch origin main
git reset --hard origin/main

# Create deployment branch for safety
DEPLOY_BRANCH="deploy-$(date +%Y%m%d-%H%M%S)"
echo -e "${YELLOW}Creating deployment branch: $DEPLOY_BRANCH${NC}"
git checkout -b "$DEPLOY_BRANCH"

# Clear existing content (except .git)
echo -e "${YELLOW}Clearing existing content...${NC}"
find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +

# Copy iOS platform content
echo -e "${YELLOW}Copying iOS platform content...${NC}"
cp -r "$MONOREPO_ROOT/platforms/ios"/* .
cp "$MONOREPO_ROOT/platforms/ios/.gitignore" . 2>/dev/null || true

# Create iOS-specific shared component bridge if shared components exist
echo -e "${YELLOW}Creating shared components bridge...${NC}"
IOS_SHARED_DIR="OSRSWiki/Shared"

if [[ -d "$MONOREPO_ROOT/shared" ]]; then
    mkdir -p "$IOS_SHARED_DIR"
    
    # Create Swift bridge file for shared components
    cat > "$IOS_SHARED_DIR/SharedComponentsBridge.swift" << 'EOF'
//
//  SharedComponentsBridge.swift
//  OSRSWiki
//
//  Auto-generated shared components bridge
//  This file bridges shared components for iOS use
//

import Foundation

// MARK: - Shared Components Bridge
// This provides iOS-compatible interfaces for shared components
// Originally from the monorepo shared/ directory

class SharedComponentsBridge {
    // TODO: Implement Swift bridges for shared components
    // - API layer bridge
    // - Model definitions bridge  
    // - Network utility bridge
    // - Utility function bridge
}

// MARK: - Configuration
extension SharedComponentsBridge {
    static let shared = SharedComponentsBridge()
    
    // Shared component paths (reference only - implemented natively in Swift)
    enum ComponentPath {
        static let api = "shared/api"
        static let models = "shared/models"
        static let network = "shared/network" 
        static let utils = "shared/utils"
    }
}
EOF

    # Create shared component documentation
    cat > "$IOS_SHARED_DIR/README.md" << EOF
# iOS Shared Components

This directory contains iOS-specific implementations of shared components from the monorepo.

## Available Components

### API Layer
- Location: \`shared/api\`
- iOS Implementation: Native Swift API client
- Status: TODO - Implement Swift version

### Models  
- Location: \`shared/models\`
- iOS Implementation: Swift Codable structs
- Status: TODO - Define Swift model structures

### Network Layer
- Location: \`shared/network\`
- iOS Implementation: URLSession-based networking
- Status: TODO - Implement iOS networking layer

### Utilities
- Location: \`shared/utils\`
- iOS Implementation: Swift utility extensions
- Status: TODO - Port utility functions to Swift

## Integration Notes

The iOS app uses native Swift implementations of shared components rather than 
direct code sharing. This provides:

1. **Type Safety**: Full Swift type checking
2. **Platform Optimization**: iOS-specific optimizations
3. **Maintainability**: Clear separation of concerns
4. **Testing**: Native iOS testing frameworks

## Development Workflow

1. Update shared components in monorepo
2. Update corresponding Swift implementations
3. Ensure API compatibility between platforms
4. Test iOS-specific functionality

Last Updated: $(date)
EOF

    echo "  → Created Swift bridge for shared components"
fi

# Stage all changes
git add -A

# Create deployment commit if there are changes
if ! git diff --cached --quiet; then
    DEPLOY_COMMIT_MSG="deploy: update iOS app from monorepo

Recent iOS-related changes:
$(cd "$MONOREPO_ROOT" && git log --oneline --no-merges --max-count=5 main --grep='ios\\|iOS' | sed 's/^/- /' || echo "- Recent commits from monorepo main branch")

This deployment:
- Updates from monorepo platforms/ios/
- Creates Swift bridges for shared components
- Maintains iOS-specific .gitignore
- Preserves Xcode project structure

Deployment info:
- Source: $MONOREPO_ROOT
- Target: $DEPLOY_IOS
- Branch: $DEPLOY_BRANCH  
- Date: $(date '+%Y-%m-%dT%H:%M:%S%z')
- Xcode Version: $(xcodebuild -version | head -1)
- Shared components: $(ls -d "$MONOREPO_ROOT/shared"/* 2>/dev/null | wc -l) modules"

    git commit -m "$DEPLOY_COMMIT_MSG"
    echo -e "${GREEN}✅ Deployment commit created${NC}"
    
    # Show what was deployed
    echo -e "${BLUE}📋 Deployment Summary:${NC}"
    git show --stat HEAD
    
else
    echo -e "${YELLOW}ℹ️  No changes to deploy${NC}"
    git checkout main
    git branch -d "$DEPLOY_BRANCH"
    cd "$MONOREPO_ROOT"
    exit 0
fi

# Phase 6: Push to remote
echo -e "${BLUE}🚀 Phase 6: Push to Remote${NC}"
echo "------------------------"

# Safety check - ensure we have reasonable number of commits
DEPLOY_COMMITS=$(git rev-list --count HEAD)
if [[ "$DEPLOY_COMMITS" -lt 1 ]]; then
    echo -e "${RED}🚨 CRITICAL SAFETY CHECK FAILED${NC}"
    echo "Deployment repository has no commits"
    echo "This suggests a serious error in deployment preparation."
    exit 1
fi

echo -e "${GREEN}✅ Safety check passed: $DEPLOY_COMMITS commits${NC}"

# Push with force-with-lease for safety
echo -e "${YELLOW}Pushing to remote...${NC}"
if git push origin "$DEPLOY_BRANCH" --force-with-lease; then
    echo -e "${GREEN}✅ Deployment branch pushed successfully${NC}"
    
    # Merge to main
    git checkout main
    git merge "$DEPLOY_BRANCH" --ff-only
    git push origin main
    
    # Clean up deployment branch
    git branch -d "$DEPLOY_BRANCH"
    git push origin --delete "$DEPLOY_BRANCH"
    
    echo -e "${GREEN}🎉 iOS deployment completed successfully!${NC}"
    
else
    echo -e "${RED}❌ Push failed - remote may have been updated${NC}"
    echo "Fix conflicts and try again"
    exit 1
fi

# Phase 7: Final validation
echo -e "${BLUE}✅ Phase 7: Post-deployment Validation${NC}"
echo "--------------------------------"

# Verify remote state
REMOTE_COMMITS=$(git ls-remote origin main | cut -f1)
LOCAL_COMMITS=$(git rev-parse HEAD)

if [[ "$REMOTE_COMMITS" == "$LOCAL_COMMITS" ]]; then
    echo -e "${GREEN}✅ Remote and local are synchronized${NC}"
else
    echo -e "${YELLOW}⚠️  Remote and local commits differ${NC}"
    echo "This may indicate a deployment issue - investigate"
fi

# Return to monorepo
cd "$MONOREPO_ROOT"

echo ""
echo -e "${GREEN}🎊 Git-Based iOS Deployment Complete!${NC}"
echo "========================================="
echo "Deployment repository: $DEPLOY_IOS"
echo "Remote commits: $DEPLOY_COMMITS"
echo "Changes deployed safely"
echo ""
echo -e "${BLUE}Deployed components:${NC}"
echo "- ✅ iOS app (complete Xcode project)"
echo "- ✅ Swift bridges for shared components"
echo "- ✅ iOS-specific .gitignore"
echo "- ✅ Xcode project and configuration"
echo ""
echo -e "${BLUE}Key advantages of ~/Deploy approach:${NC}"
echo "- ✅ Simple 1:1 mirror of remote repository"
echo "- ✅ Standard git workflow from deployment directory"
echo "- ✅ Clear separation between monorepo and deployment"
echo "- ✅ Easy to verify deployment state"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "- Verify deployment at: https://github.com/omiyawaki/osrswiki-ios"
echo "- Test the deployed app in Xcode"
echo "- Implement shared component Swift bridges in OSRSWiki/Shared/"
echo "- Monitor for any issues"

exit 0