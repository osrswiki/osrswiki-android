#!/bin/bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔍 Validating merge operation...${NC}"

# Function to check if we're in main repo (not a worktree)
check_main_repo() {
    if [[ -f ".git" ]] && grep -q "gitdir:" ".git" 2>/dev/null; then
        echo -e "${RED}❌ Cannot perform merge from inside a worktree${NC}"
        echo "Navigate to main repository: /Users/miyawaki/Develop/osrswiki"
        return 1
    fi
    
    if [[ ! -f "CLAUDE.md" ]]; then
        echo -e "${RED}❌ Not in main repository (CLAUDE.md missing)${NC}"
        echo "Navigate to main repository: /Users/miyawaki/Develop/osrswiki"
        return 1
    fi
    
    echo -e "${GREEN}✅ In main repository${NC}"
    return 0
}

# Function to validate git status before merge
check_git_status() {
    local status_output
    status_output=$(git status --porcelain)
    
    if [[ -n "$status_output" ]]; then
        echo -e "${RED}❌ Working directory not clean${NC}"
        echo "Please commit or stash changes before merging:"
        git status --short
        return 1
    fi
    
    echo -e "${GREEN}✅ Working directory clean${NC}"
    return 0
}

# Function to validate branch exists and has commits
validate_branch() {
    local branch="$1"
    
    if ! git rev-parse --verify "$branch" >/dev/null 2>&1; then
        echo -e "${RED}❌ Branch '$branch' does not exist${NC}"
        return 1
    fi
    
    # Check if branch has commits ahead of main
    local ahead_count
    ahead_count=$(git rev-list --count main.."$branch" 2>/dev/null || echo "0")
    
    if [[ "$ahead_count" -eq 0 ]]; then
        echo -e "${YELLOW}⚠️  Branch '$branch' has no commits ahead of main${NC}"
        echo "Nothing to merge"
        return 1
    fi
    
    echo -e "${GREEN}✅ Branch '$branch' exists with $ahead_count commits ahead${NC}"
    return 0
}

# Function to check if merge would be successful
test_merge() {
    local branch="$1"
    
    echo -e "${YELLOW}🧪 Testing merge feasibility...${NC}"
    
    # Test merge without committing
    if git merge --no-commit --no-ff "$branch" >/dev/null 2>&1; then
        # Abort the test merge
        git merge --abort >/dev/null 2>&1
        echo -e "${GREEN}✅ Merge test successful${NC}"
        return 0
    else
        # If merge failed, abort it
        git merge --abort >/dev/null 2>&1 || true
        echo -e "${RED}❌ Merge test failed - conflicts detected${NC}"
        echo "Resolve conflicts manually before merging"
        return 1
    fi
}

# Function to validate that current branch is main
check_current_branch() {
    local current_branch
    current_branch=$(git branch --show-current)
    
    if [[ "$current_branch" != "main" ]]; then
        echo -e "${RED}❌ Not on main branch (currently on: $current_branch)${NC}"
        echo "Switch to main branch before merging"
        return 1
    fi
    
    echo -e "${GREEN}✅ On main branch${NC}"
    return 0
}

# Function to check for agent files in staging area
check_for_agent_files() {
    local staged_files
    staged_files=$(git diff --cached --name-only 2>/dev/null || echo "")
    
    if echo "$staged_files" | grep -q "\.claude/agents/.*\.md$\|ANDROID_UI_TESTER_USAGE\.md$"; then
        echo -e "${RED}❌ Agent files detected in staging area${NC}"
        echo "These files should not be committed:"
        echo "$staged_files" | grep "\.claude/agents/.*\.md$\|ANDROID_UI_TESTER_USAGE\.md$" || true
        echo "Remove these files from staging before committing"
        return 1
    fi
    
    echo -e "${GREEN}✅ No agent files in staging area${NC}"
    return 0
}

# Main validation function
main() {
    local branch="${1:-}"
    
    if [[ -z "$branch" ]]; then
        echo "Usage: $0 <branch-to-merge>"
        echo "Example: $0 claude/20250815-123440-font-fix"
        exit 1
    fi
    
    echo -e "${BLUE}Validating merge of branch: $branch${NC}"
    echo ""
    
    # Run all validations
    check_main_repo || exit 1
    check_current_branch || exit 1
    check_git_status || exit 1
    check_for_agent_files || exit 1
    validate_branch "$branch" || exit 1
    test_merge "$branch" || exit 1
    
    echo ""
    echo -e "${GREEN}🎉 All validations passed!${NC}"
    echo -e "${GREEN}Ready to merge branch: $branch${NC}"
    echo ""
    echo -e "${YELLOW}To perform the merge:${NC}"
    echo "   git merge --no-ff '$branch'"
    echo ""
    echo -e "${YELLOW}After merging, validate with:${NC}"
    echo "   ./scripts/shared/validate-post-merge.sh"
}

main "$@"