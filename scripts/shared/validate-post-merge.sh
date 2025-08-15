#!/bin/bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔍 Validating post-merge state...${NC}"

# Function to check if last operation was actually a merge
check_last_operation() {
    local last_commit
    last_commit=$(git log --format="%H %s" -1)
    
    # Check if it's a merge commit (has 2 parents)
    local parent_count
    parent_count=$(git cat-file -p HEAD | grep "^parent" | wc -l)
    
    if [[ "$parent_count" -lt 2 ]]; then
        echo -e "${RED}❌ Last commit is not a merge commit${NC}"
        echo "Expected: merge commit with 2+ parents"
        echo "Actual: regular commit with $parent_count parent(s)"
        return 1
    fi
    
    echo -e "${GREEN}✅ Last commit is a valid merge${NC}"
    return 0
}

# Function to verify we're still on main branch
check_branch() {
    local current_branch
    current_branch=$(git branch --show-current)
    
    if [[ "$current_branch" != "main" ]]; then
        echo -e "${RED}❌ Not on main branch after merge (on: $current_branch)${NC}"
        echo "This indicates the merge operation failed"
        return 1
    fi
    
    echo -e "${GREEN}✅ Still on main branch${NC}"
    return 0
}

# Function to check that working directory is still clean
check_working_directory() {
    local status_output
    status_output=$(git status --porcelain)
    
    if [[ -n "$status_output" ]]; then
        echo -e "${YELLOW}⚠️  Working directory has changes after merge${NC}"
        echo "This may be normal if merge created conflicts that were resolved"
        git status --short
        return 0
    fi
    
    echo -e "${GREEN}✅ Working directory clean${NC}"
    return 0
}

# Function to verify no accidental resets occurred
check_no_reset() {
    local reflog_entry
    reflog_entry=$(git reflog -1 --format="%gd %gs")
    
    if echo "$reflog_entry" | grep -q "reset:"; then
        echo -e "${RED}❌ Last operation was a reset, not a merge${NC}"
        echo "This indicates the merge was immediately undone"
        echo "Last reflog entry: $reflog_entry"
        return 1
    fi
    
    echo -e "${GREEN}✅ No reset detected after merge${NC}"
    return 0
}

# Function to check for agent files in the merge
check_merge_content() {
    local merge_files
    merge_files=$(git diff --name-only HEAD~1 HEAD 2>/dev/null || echo "")
    
    if echo "$merge_files" | grep -q "\.claude/agents/.*\.md$\|ANDROID_UI_TESTER_USAGE\.md$"; then
        echo -e "${RED}❌ Agent files included in merge${NC}"
        echo "These files should not be part of feature commits:"
        echo "$merge_files" | grep "\.claude/agents/.*\.md$\|ANDROID_UI_TESTER_USAGE\.md$" || true
        echo -e "${YELLOW}Consider amending the commit to remove these files${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✅ No agent files in merge${NC}"
    return 0
}

# Function to show merge summary
show_merge_summary() {
    echo ""
    echo -e "${BLUE}📋 Merge Summary:${NC}"
    
    local merge_commit
    merge_commit=$(git log --format="%H" -1)
    
    echo -e "${BLUE}Commit: ${merge_commit:0:8}${NC}"
    echo -e "${BLUE}Message:${NC}"
    git log --format="   %s" -1
    echo ""
    
    echo -e "${BLUE}Files changed:${NC}"
    git diff --name-only HEAD~1 HEAD | sed 's/^/   /'
    echo ""
    
    echo -e "${BLUE}Commit stats:${NC}"
    git diff --stat HEAD~1 HEAD | sed 's/^/   /'
}

# Main validation function
main() {
    echo -e "${BLUE}Validating last git operation...${NC}"
    echo ""
    
    # Run all validations
    local validation_failed=0
    
    check_branch || validation_failed=1
    check_last_operation || validation_failed=1
    check_no_reset || validation_failed=1
    check_working_directory || validation_failed=1
    check_merge_content || validation_failed=1
    
    if [[ "$validation_failed" -eq 0 ]]; then
        echo ""
        echo -e "${GREEN}🎉 Post-merge validation passed!${NC}"
        show_merge_summary
    else
        echo ""
        echo -e "${RED}❌ Post-merge validation failed!${NC}"
        echo -e "${YELLOW}Review the issues above before proceeding${NC}"
        exit 1
    fi
}

main "$@"