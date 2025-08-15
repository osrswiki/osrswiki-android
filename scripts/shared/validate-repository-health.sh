#!/bin/bash
set -euo pipefail

# OSRS Wiki Repository Health Validation Script
# Ensures repositories are not contaminated and are in good state

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🏥 OSRS Wiki Repository Health Check${NC}"
echo "========================================"
echo "Date: $(date)"
echo ""

HEALTH_ISSUES=0
WARNINGS=0

# Function to report health issue
health_issue() {
    echo -e "${RED}🔴 HEALTH ISSUE: $1${NC}"
    ((HEALTH_ISSUES++))
}

# Function to report health warning
health_warning() {
    echo -e "${YELLOW}🟡 WARNING: $1${NC}"
    ((WARNINGS++))
}

# Function to report health success
health_success() {
    echo -e "${GREEN}🟢 $1${NC}"
}

# Check if we're in the right place
if [[ ! -f "CLAUDE.md" ]]; then
    health_issue "Not in monorepo root - must run from directory containing CLAUDE.md"
    exit 1
fi

echo -e "${YELLOW}📋 Phase 1: Main Repository Structure Health${NC}"
echo "------------------------------------------"

# Check for proper git repository
if [[ ! -d ".git" ]]; then
    health_issue "Not a git repository"
    exit 1
else
    health_success "Valid git repository"
fi

# Check for contamination: worktrees inside main repo
INTERNAL_WORKTREES=$(find . -maxdepth 1 -type d -name "claude-*" ! -path "./.git/*" || true)
if [[ -n "$INTERNAL_WORKTREES" ]]; then
    health_issue "Worktrees found inside main repository:"
    echo "$INTERNAL_WORKTREES" | sed 's/^/  /'
    echo "  → These should be in ~/Develop/osrswiki-sessions/"
else
    health_success "No internal worktrees found"
fi

# Check for proper platform structure
for platform in android ios; do
    if [[ -d "platforms/$platform" ]]; then
        health_success "Platform $platform directory exists"
        
        # Check that platform dir is not empty
        if [[ -z "$(ls -A "platforms/$platform" 2>/dev/null)" ]]; then
            health_warning "Platform $platform directory is empty"
        fi
    else
        health_warning "Platform $platform directory missing"
    fi
done

# Check for session contamination in git history
echo -e "${YELLOW}📋 Phase 2: Git History Contamination Check${NC}"
echo "-----------------------------------------"

# Check for session files in git index
SESSION_FILES=$(git ls-files | grep -E "\\.claude-|claude-[0-9]{8}" || true)
if [[ -n "$SESSION_FILES" ]]; then
    health_warning "Session files found in git history:"
    echo "$SESSION_FILES" | sed 's/^/  /'
fi

# Check for temporary files that shouldn't be committed
TEMP_FILES=$(git ls-files | grep -E "\\.tmp$|\\.log$|emulator\\.(err|out)$" || true)
if [[ -n "$TEMP_FILES" ]]; then
    health_warning "Temporary files found in git history:"
    echo "$TEMP_FILES" | sed 's/^/  /'
fi

# Check working directory status
echo -e "${YELLOW}📋 Phase 3: Working Directory Health${NC}"
echo "--------------------------------"

# Check for uncommitted changes
if ! git diff --quiet || ! git diff --cached --quiet; then
    health_warning "Uncommitted changes in working directory"
    echo "  → Run 'git status' to see details"
else
    health_success "Working directory is clean"
fi

# Check for untracked files that might be problematic
UNTRACKED_FILES=$(git ls-files --others --exclude-standard | grep -E "\\.apk$|\\.aab$|\\.log$" || true)
if [[ -n "$UNTRACKED_FILES" ]]; then
    health_warning "Potentially problematic untracked files:"
    echo "$UNTRACKED_FILES" | sed 's/^/  /'
fi

# Check git remotes
echo -e "${YELLOW}📋 Phase 4: Git Remote Configuration Health${NC}"
echo "-------------------------------------------"

EXPECTED_REMOTES=("android" "ios" "tooling")
for remote in "${EXPECTED_REMOTES[@]}"; do
    if git remote | grep -q "^${remote}$"; then
        health_success "Remote '$remote' configured"
        
        # Test remote connectivity
        if git ls-remote "$remote" HEAD &>/dev/null; then
            health_success "Remote '$remote' is accessible"
        else
            health_warning "Remote '$remote' is not accessible"
        fi
    else
        health_warning "Expected remote '$remote' not configured"
    fi
done

# Check directory structure health
echo -e "${YELLOW}📋 Phase 5: Directory Structure Health${NC}"
echo "------------------------------------"

# Check for proper session directory
if [[ -d "$HOME/Develop/osrswiki-sessions" ]]; then
    health_success "Session directory exists at ~/Develop/osrswiki-sessions"
    
    # Check session directory contents
    SESSION_COUNT=$(find "$HOME/Develop/osrswiki-sessions" -maxdepth 1 -type d -name "claude-*" | wc -l)
    echo "  → Contains $SESSION_COUNT session directories"
else
    health_warning "Session directory not found at ~/Develop/osrswiki-sessions"
fi

# Check for deploy directory
if [[ -d "$HOME/Deploy" ]]; then
    health_success "Deploy directory exists at ~/Deploy"
    
    # Check deployment repositories
    for platform in android ios tooling; do
        DEPLOY_REPO="$HOME/Deploy/osrswiki-$platform"
        if [[ -d "$DEPLOY_REPO" ]]; then
            health_success "Deployment repo exists: $DEPLOY_REPO"
            
            # Check if it's a valid git repo
            if [[ -d "$DEPLOY_REPO/.git" ]]; then
                cd "$DEPLOY_REPO"
                COMMIT_COUNT=$(git rev-list --count HEAD 2>/dev/null || echo "0")
                if [[ "$COMMIT_COUNT" -gt 0 ]]; then
                    health_success "$platform deployment repo has $COMMIT_COUNT commits"
                else
                    health_warning "$platform deployment repo appears to be empty"
                fi
                cd - >/dev/null
            else
                health_warning "$DEPLOY_REPO exists but is not a git repository"
            fi
        else
            health_warning "Deployment repo missing: $DEPLOY_REPO"
        fi
    done
else
    health_warning "Deploy directory not found at ~/Deploy"
fi

# Check git hooks
echo -e "${YELLOW}📋 Phase 6: Safety Infrastructure Health${NC}"
echo "-------------------------------------"

# Check pre-push hook
if [[ -f ".git/hooks/pre-push" ]] && [[ -x ".git/hooks/pre-push" ]]; then
    health_success "Pre-push safety hook installed and executable"
else
    health_warning "Pre-push safety hook missing or not executable"
    echo "  → This hook prevents dangerous pushes to deployment repos"
fi

# Final health summary
echo -e "${BLUE}========================================"
echo -e "📊 REPOSITORY HEALTH SUMMARY"
echo -e "=======================================${NC}"

echo "Health Issues: $HEALTH_ISSUES"
echo "Warnings: $WARNINGS"
echo ""

if [[ "$HEALTH_ISSUES" -eq 0 ]]; then
    if [[ "$WARNINGS" -eq 0 ]]; then
        health_success "🎉 Repository is in perfect health!"
    else
        health_success "✅ Repository health is good (with $WARNINGS minor warnings)"
        echo -e "${YELLOW}💡 Consider addressing warnings for optimal health${NC}"
    fi
    echo -e "${GREEN}🚀 Safe to perform development and deployment operations${NC}"
    exit 0
else
    echo -e "${RED}🚨 Repository health issues detected!${NC}"
    echo -e "${RED}🛠️  Address health issues before performing critical operations${NC}"
    exit 1
fi