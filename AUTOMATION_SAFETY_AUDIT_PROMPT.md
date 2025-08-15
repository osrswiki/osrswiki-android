# CRITICAL: Automation Safety Audit Request

## Background
On August 15, 2025, a catastrophic git repository corruption occurred that completely destroyed the main monorepo structure. The incident was caused by incorrect deployment operations that mixed remotes and pushed Android deployment repository content TO the main monorepo instead of FROM it, overwriting the entire repository structure.

**Root Cause**: Deployment direction confusion - commands pushed deployment content TO origin/main instead of extracting FROM main TO deployment repos.

## Incident Summary
- **Trigger**: Series of "deploy: update Android app from monorepo" commits overwrote origin/main
- **Impact**: Complete loss of monorepo structure (CLAUDE.md, scripts/, shared/, tools/)
- **Corruption**: Git objects with missing parents, corrupted merge commits
- **Resolution**: Full repository reconstruction with all legitimate work preserved
- **Current State**: Repository fully restored and functional

## Audit Objective
**CRITICAL PRIORITY**: Identify potentially dangerous constructs in automation scripts that may have caused or could cause similar incidents in the future.

## Scope of Investigation
Examine ALL automation and script files in these locations:

### Primary Targets
1. **`.claude/` directory** - Claude agent configurations, commands, settings
2. **`./scripts/` directory** - All development and deployment scripts
3. **`CLAUDE.md`** - Agent directives and workflow instructions

### Specific Files to Audit
- `.claude/commands/*.md` - Command definitions
- `.claude/agents/*.agent.md` - Agent configurations  
- `.claude/settings.json` - Claude configuration
- `scripts/android/*.sh` - Android development scripts
- `scripts/ios/*.sh` - iOS development scripts
- `scripts/shared/*.sh` - Cross-platform scripts
- Any other shell scripts, automation files, or configuration files

## Critical Security Issues to Identify

### 1. Git Remote Operations
**HIGHEST PRIORITY** - Look for commands that could confuse remotes:
- `git push` commands with hardcoded remotes
- Remote switching or manipulation
- Commands that could push TO main repo instead of deployment repos
- Missing remote validation or confirmation

### 2. Deployment Direction Errors
- Scripts that could reverse deployment direction
- Commands pushing FROM deployment repos TO main repo
- Missing safeguards against incorrect push direction
- Automated deployment without human confirmation

### 3. Force Operations
- `git push --force` without adequate safeguards
- `git reset --hard` on important branches
- Repository manipulation without backups
- Commands that could overwrite git history

### 4. Missing Validation
- Commands executed without checking current branch
- Operations without verifying repository state
- Missing pre-push hooks or safety checks
- No validation of target remotes before operations

### 5. Agent/Automation Risks
- Automated processes with dangerous permissions
- Agents capable of executing git operations unsupervised
- Scripts with embedded credentials or sensitive operations
- Commands that could be triggered accidentally

## Specific Patterns to Flag

### Command Patterns (Shell Scripts)
```bash
# DANGEROUS - Flag these patterns:
git push origin main --force  # Force push to main
git push android               # Wrong direction deployment
git reset --hard HEAD~        # Destructive reset
git remote set-url             # Remote manipulation
rm -rf .git                    # Repository destruction
```

### Agent Configuration Risks
- Agents with `git push` capabilities
- Automated deployment workflows
- Commands triggered by keywords or events
- Unrestricted bash/shell access

### Missing Safety Checks
- No `git status` verification before operations
- No branch confirmation before force operations
- No remote URL validation before push
- No backup creation before destructive operations

## Investigation Tasks

### 1. File Discovery and Cataloging
- List ALL automation files in scope
- Identify file types: shell scripts, agent configs, command definitions
- Map dependencies between files
- Document execution triggers and permissions

### 2. Risk Assessment per File
For each file, evaluate:
- **Criticality**: Can it modify git repository state?
- **Scope**: What operations can it perform?
- **Safeguards**: What validation does it include?
- **Triggers**: How/when is it executed?
- **Impact**: What damage could it cause if misused?

### 3. Dangerous Construct Identification
Create detailed inventory of:
- Git commands with potential for damage
- Missing validation or confirmation steps
- Hardcoded remote references
- Force operations without safeguards
- Deployment direction vulnerabilities

### 4. Root Cause Analysis
Specifically investigate:
- Could existing scripts have caused the incident?
- Are there automation workflows that mix remotes?
- Do any agents have deployment capabilities?
- Are there missing referenced scripts (e.g., deploy-android.sh)?

## Deliverables Required

### 1. Risk Assessment Matrix
For each file/script, provide:
- **Risk Level**: CRITICAL/HIGH/MEDIUM/LOW
- **Dangerous Operations**: Specific commands/patterns flagged
- **Missing Safeguards**: What protections are absent
- **Recommendation**: Fix/Remove/Restrict/Monitor

### 2. Specific Findings Report
- **Smoking Guns**: Scripts that could have directly caused incident
- **High-Risk Patterns**: Commands that pose similar risks
- **Missing Safety Infrastructure**: Absent validation/protection
- **Agent Security Issues**: Dangerous automation capabilities

### 3. Immediate Action Items
- **CRITICAL fixes required immediately**
- **Scripts to disable/remove**
- **Safeguards to implement**
- **Monitoring to establish**

### 4. Prevention Recommendations
- **Pre-push hooks to implement**
- **Agent capability restrictions**
- **Script modification guidelines**
- **Safety infrastructure improvements**

## Investigation Methodology

### Phase 1: Discovery (30 minutes)
- Systematically scan all target directories
- Catalog every automation file
- Identify execution contexts and triggers
- Map file relationships and dependencies

### Phase 2: Analysis (60 minutes)
- Examine each file for dangerous patterns
- Trace git operations and remote interactions
- Evaluate safety mechanisms and validation
- Identify potential incident vectors

### Phase 3: Risk Assessment (30 minutes)
- Classify findings by risk level
- Prioritize critical security issues
- Develop immediate action recommendations
- Document prevention strategies

## Success Criteria
The audit is successful when you can definitively answer:
1. **Could existing automation have caused this incident?**
2. **What are the highest risk operations currently present?**
3. **What immediate changes are needed to prevent recurrence?**
4. **How can we implement ongoing safety monitoring?**

## Constraints and Guidelines
- **Read-only analysis**: Do NOT modify any files during audit
- **Complete coverage**: Examine EVERY automation file in scope
- **Evidence-based**: Provide specific line numbers and commands
- **Actionable**: All recommendations must be implementable
- **Prioritized**: Focus on CRITICAL risks first

## Context Files
- Read `INCIDENT_REPORT_20250815.md` for full incident details
- Review `CLAUDE.md` for understanding of intended workflows
- Check `.gitignore` to understand what files should be excluded

---
**URGENCY**: CRITICAL - This audit must be completed immediately to prevent similar incidents and ensure repository safety going forward.

**AGENT SELECTION**: Use an agent with strong security analysis capabilities and git/shell scripting expertise.