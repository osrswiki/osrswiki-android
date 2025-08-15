#!/bin/bash
# Post-creation setup for Android development container

set -e

echo "🚀 Setting up OSRS Wiki Android development environment..."

# Setup Android environment variables
export ANDROID_SDK_ROOT=/opt/android-sdk
export ANDROID_HOME=/opt/android-sdk
export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

# Add to bashrc for persistence
echo "export ANDROID_SDK_ROOT=/opt/android-sdk" >> ~/.bashrc
echo "export ANDROID_HOME=/opt/android-sdk" >> ~/.bashrc
echo "export PATH=\$PATH:\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools" >> ~/.bashrc

# Add Claude Code alias for container use
echo "# Claude Code alias for safe container usage" >> ~/.bashrc
echo "alias c='claude --dangerously-skip-permissions'" >> ~/.bashrc

# Set password for vscode user (needed for SSH access)
echo "🔐 Setting up vscode user password for SSH..."
echo "vscode:vscode" | sudo chpasswd

# Configure npm for global packages without sudo
echo "📦 Configuring npm..."
mkdir -p ~/.npm-global
npm config set prefix '~/.npm-global'
export PATH=~/.npm-global/bin:$PATH
echo 'export PATH=~/.npm-global/bin:$PATH' >> ~/.bashrc

# Install Claude Code CLI
echo "📦 Installing Claude Code CLI..."
npm install -g @anthropic-ai/claude-code@latest

# Setup Claude settings for container environment
echo "⚙️  Setting up Claude configuration..."
mkdir -p ~/.claude

# Use container-specific settings to avoid host dependency issues
if [ -f /workspace/.devcontainer/claude-settings.json ]; then
    cp /workspace/.devcontainer/claude-settings.json ~/.claude/settings.json
    echo "✅ Container-optimized settings applied"
else
    echo "⚠️  Container settings file not found, using minimal config"
    # Create minimal settings as fallback
    cat > ~/.claude/settings.json << 'EOF'
{
  "includeCoAuthoredBy": false,
  "permissions": {
    "allow": [
      "Read", "List", "Write", "Edit", "WebSearch", "WebFetch", "Bash(find:*)", 
      "Bash(ls:*)", "Bash(grep:*)", "Bash(cat:*)", "Bash(which:*)", "Bash(echo:*)", 
      "Bash(pwd:*)", "Bash(cd:*)", "Bash(export:*)", "Bash(git add:*)", 
      "Bash(git commit:*)", "Bash(git push:*)", "Bash(git fetch:*)", 
      "Bash(git checkout:*)", "Bash(git status:*)", "Bash(git log:*)", 
      "Bash(git diff:*)", "Bash(./gradlew:*)", "Bash(adb:*)", "Bash(avdmanager:*)", 
      "Bash(emulator:*)"
    ]
  },
  "model": "opusplan"
}
EOF
fi

# Copy other Claude config files if available (authentication, etc.)
if [ -d /workspace/.devcontainer/claude-config/.claude ]; then
    # Preserve authentication and other non-settings files
    for file in $(ls /workspace/.devcontainer/claude-config/.claude/); do
        if [ "$file" != "settings.json" ]; then
            cp -r "/workspace/.devcontainer/claude-config/.claude/$file" ~/.claude/
        fi
    done
    echo "   • Authentication and other config preserved"
fi

echo "🔐 Claude authentication:"
echo "   First time: run 'claude' and complete login flow"
echo "   After login: use 'c' alias for fast access"

# Make scripts executable
echo "🔧 Making scripts executable..."
find /workspace/scripts -name "*.sh" -exec chmod +x {} \; 2>/dev/null || true
chmod +x /workspace/main/gradlew 2>/dev/null || true

# Skip Android system images for simple test

# Create directory structure if needed
mkdir -p ~/.android
mkdir -p /tmp/android-tmp

# Test gradle wrapper
echo "🏗️  Testing Gradle wrapper..."
if [ -f "/workspace/main/gradlew" ]; then
    cd /workspace/main && ./gradlew --version
    echo "✅ Gradle wrapper working"
else
    echo "⚠️  Gradle wrapper not found at /workspace/main/gradlew"
fi

echo ""
echo "🎯 Android development environment setup complete!"
echo "📁 Workspace: /workspace"
echo "🔧 Android SDK: $ANDROID_SDK_ROOT" 
echo "🛠️  Available commands: gradlew, adb, avdmanager, emulator"
echo ""
echo "💡 Quick start:"
echo "   claude       # First time: complete login"
echo "   c            # After login: fast Claude access"
echo "   /start       # Creates worktree session"
echo ""