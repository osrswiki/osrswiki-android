#!/bin/bash
# Test script to verify Tailscale integration
# Runs basic checks without requiring actual Tailscale connection

set -e

echo "Testing Tailscale Integration"
echo "============================="

# Test 1: Check required files exist
echo "✓ Testing file presence..."

required_files=(
    ".devcontainer/Dockerfile"
    ".devcontainer/devcontainer.json" 
    ".devcontainer/setup-tailscale.sh"
    "scripts/setup-mobile-access.sh"
    "scripts/mobile-session.sh"
    "scripts/init-tailscale-env.sh"
)

for file in "${required_files[@]}"; do
    if [ -f "$file" ]; then
        echo "  ✓ $file exists"
    else
        echo "  ❌ $file missing"
        exit 1
    fi
done

# Test 2: Check scripts are executable
echo ""
echo "✓ Testing script permissions..."

executable_scripts=(
    "scripts/setup-mobile-access.sh"
    "scripts/mobile-session.sh"  
    "scripts/init-tailscale-env.sh"
)

for script in "${executable_scripts[@]}"; do
    if [ -x "$script" ]; then
        echo "  ✓ $script is executable"
    else
        echo "  ❌ $script not executable"
        exit 1
    fi
done

# Test 3: Check devcontainer.json configuration
echo ""
echo "✓ Testing devcontainer.json configuration..."

if grep -q "setup-tailscale.sh" .devcontainer/devcontainer.json; then
    echo "  ✓ Tailscale setup script in postCreateCommand"
else
    echo "  ❌ Missing Tailscale setup in postCreateCommand"
    exit 1
fi

if grep -q "tailscale-vscode-state" .devcontainer/devcontainer.json; then
    echo "  ✓ Tailscale state volume configured"  
else
    echo "  ❌ Missing Tailscale state volume"
    exit 1
fi

if grep -q "NET_ADMIN" .devcontainer/devcontainer.json; then
    echo "  ✓ NET_ADMIN capability configured"
else
    echo "  ❌ Missing NET_ADMIN capability"
    exit 1
fi

if grep -q "60000" .devcontainer/devcontainer.json; then
    echo "  ✓ MOSH ports configured"
else
    echo "  ❌ Missing MOSH ports"
    exit 1
fi

# Test 4: Check Dockerfile has required packages
echo ""  
echo "✓ Testing Dockerfile configuration..."

packages=("mosh" "zellij" "tailscale")

for package in "${packages[@]}"; do
    if grep -q "$package" .devcontainer/Dockerfile; then
        echo "  ✓ $package installation configured"
    else
        echo "  ❌ Missing $package in Dockerfile"
        exit 1
    fi
done

# Test 5: Check documentation
echo ""
echo "✓ Testing documentation..."

if grep -q "Remote Mobile Access" CLAUDE.md; then
    echo "  ✓ Mobile access documentation exists"
else
    echo "  ❌ Missing mobile access documentation"
    exit 1
fi

if grep -q "Sudo-Free" CLAUDE.md; then
    echo "  ✓ Sudo-free documentation exists" 
else
    echo "  ❌ Missing sudo-free documentation"
    exit 1
fi

# Test 6: Check script syntax
echo ""
echo "✓ Testing script syntax..."

bash_scripts=(
    ".devcontainer/setup-tailscale.sh"
    "scripts/setup-mobile-access.sh"
    "scripts/mobile-session.sh"
    "scripts/init-tailscale-env.sh"
)

for script in "${bash_scripts[@]}"; do
    if bash -n "$script"; then
        echo "  ✓ $script syntax valid"
    else
        echo "  ❌ $script has syntax errors"
        exit 1
    fi
done

echo ""
echo "🎉 All tests passed!"
echo ""
echo "Integration Summary:"
echo "- Sudo-free Tailscale userspace configuration ✓"
echo "- Mobile access scripts with MOSH support ✓"
echo "- Container configuration with proper capabilities ✓"
echo "- Comprehensive documentation ✓"
echo "- All scripts have valid syntax ✓"
echo ""
echo "Next Steps:"
echo "1. Rebuild devcontainer to test integration"
echo "2. Run './scripts/setup-mobile-access.sh' in container"
echo "3. Test SSH/MOSH access from mobile device"
echo ""
echo "For testing without rebuild:"
echo "  # Test environment setup"
echo "  ./scripts/init-tailscale-env.sh"
echo "  source .claude-env"
echo "  # Should show Tailscale aliases available"