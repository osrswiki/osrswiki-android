#!/bin/bash
# Setup mobile access without sudo requirements
# This script prepares Tailscale for remote SSH access

set -e

# Load session environment if available
source .claude-env 2>/dev/null || true

echo "Setting up mobile access (sudo-free)..."
echo "======================================="

# Check if we're in a container
if [ "$IS_CONTAINER" = "true" ]; then
    echo "✓ Running in container environment"
else
    echo "⚠ Not in container - some features may not work"
fi

# Check if Tailscale is installed
if ! command -v tailscale &> /dev/null; then
    echo "❌ Error: Tailscale not installed"
    echo "Make sure you're running in the devcontainer with Tailscale installed"
    exit 1
fi

# Check required directories
if [ ! -d ~/.tailscale ]; then
    echo "Creating Tailscale state directory..."
    mkdir -p ~/.tailscale
fi

# Start Tailscale if not running
if ! pgrep tailscaled > /dev/null; then
    echo "Starting Tailscale daemon..."
    ~/start-tailscale.sh
    
    # Wait a bit for the daemon to fully start
    sleep 3
fi

# Check if we need authentication
export TS_SOCKET=/home/vscode/.tailscale/tailscaled.sock

# Source environment to get aliases
if [ -f ~/.bashrc ]; then
    source ~/.bashrc
fi

# Check tailscale status with explicit socket path
echo "🔍 Checking Tailscale status..."
echo "Using socket: $TS_SOCKET"
set +e  # Don't exit on error
STATUS_OUTPUT=$(timeout 10s /usr/bin/tailscale --socket="$TS_SOCKET" status 2>&1)
STATUS_EXIT_CODE=$?
# Also try to get IP directly
TAILSCALE_IP=$(/usr/bin/tailscale --socket="$TS_SOCKET" ip -4 2>/dev/null | head -n1)
set -e  # Re-enable exit on error

if [ $STATUS_EXIT_CODE -eq 124 ]; then
    echo "⏰ Status check timed out after 10 seconds."
    echo "Let's continue with authentication setup..."
    STATUS_EXIT_CODE=1  # Force authentication path
    STATUS_OUTPUT="timeout"
fi

echo "Status output: $STATUS_OUTPUT"
echo "Exit code: $STATUS_EXIT_CODE"
echo ""

# Check if Tailscale is connected - use IP address as primary indicator
if [ -n "$TAILSCALE_IP" ]; then
    echo "✅ Tailscale is authenticated and connected!"
    echo "Your device: osrs-wiki-dev"
    echo "Tailscale IP: $TAILSCALE_IP"
else
    echo ""
    echo "📱 Tailscale Authentication Required"
    echo "====================================="
    
    # Check if we have an auth key
    if [ -n "$TS_AUTHKEY" ] && [ "$TS_AUTHKEY" != "" ]; then
        echo "Using provided auth key for automatic setup..."
        /usr/bin/tailscale --socket="$TS_SOCKET" up --authkey="$TS_AUTHKEY" --ssh --hostname="osrs-wiki-dev"
        echo "✅ Authentication complete!"
    else
        echo "Running authentication command automatically..."
        echo ""
        
        # Run the command with timeout and proper error handling
        echo "🔍 Running: /usr/bin/tailscale --socket=\"$TS_SOCKET\" up --ssh --hostname=\"osrs-wiki-dev\""
        
        # Use timeout to prevent hanging and run with explicit socket
        set +e  # Don't exit on error
        AUTH_OUTPUT=$(timeout 30s /usr/bin/tailscale --socket="$TS_SOCKET" up --ssh --hostname="osrs-wiki-dev" 2>&1)
        AUTH_EXIT_CODE=$?
        set -e  # Re-enable exit on error
        
        echo "📄 Command exit code: $AUTH_EXIT_CODE"
        echo "📄 Output:"
        echo "$AUTH_OUTPUT"
        echo ""
        
        # Check if command timed out
        if [ $AUTH_EXIT_CODE -eq 124 ]; then
            echo "⏰ Command timed out after 30 seconds."
            echo "This might mean authentication is needed. Please run manually:"
            echo "tailscale up --ssh --hostname=\"osrs-wiki-dev\""
            exit 1
        fi
        
        # Extract URL from output
        AUTH_URL=$(echo "$AUTH_OUTPUT" | grep -o 'https://login\.tailscale\.com/a/[a-zA-Z0-9]*' | head -1)
        
        if [ -n "$AUTH_URL" ]; then
            echo "🔗 AUTHENTICATION URL: $AUTH_URL"
            echo ""
            echo "✋ Please open this URL in your browser, sign in and approve the device."
            echo "Then run 'm' again to see your connection info."
        else
            # Check if already authenticated
            if echo "$AUTH_OUTPUT" | grep -q "Success"; then
                echo "✅ Authentication appears to be successful!"
                echo "Running connection info..."
                # Continue to show connection info instead of exiting
            else
                echo "⚠️  Could not find authentication URL in output."
                echo "Please check the output above for instructions."
                exit 0
            fi
        fi
    fi
    
    echo ""
    echo "⏳ Waiting for Tailscale to fully connect..."
    sleep 3
fi

# Setup SSH on port 2222 if not already running
if ! pgrep -f "sshd.*port2222" > /dev/null; then
    echo ""
    echo "🔧 Setting up SSH on port 2222 (Tailscale compatible)..."
    ./scripts/android/setup-ssh-port-2222.sh
fi

# Display connection information
echo ""
~/tailscale-info.sh

echo ""
echo "📱 Mobile Setup Instructions"
echo "============================"
echo "1. Install Tailscale app on your mobile device"
echo "2. Sign in with the same account"
echo "3. Your device will appear in your tailnet automatically"
echo ""

# Get connection details
TAILSCALE_IP=$(tailscale ip -4 2>/dev/null | head -n1)
HOSTNAME=$(hostname)

if [ -n "$TAILSCALE_IP" ]; then
    echo "4. Use any SSH client to connect (port 2222):"
    echo "   • Termux (Android): ssh -p 2222 vscode@$TAILSCALE_IP"
    echo "   • Secure ShellFish (iOS): Host: $TAILSCALE_IP, Port: 2222, User: vscode"
    echo "   • Or hostname: ssh -p 2222 vscode@$HOSTNAME"
    echo ""
    echo "5. For unstable connections, use MOSH:"
    echo "   mosh --port=60000 vscode@$TAILSCALE_IP"
    echo ""
    echo "🔧 Once Connected"
    echo "================"
    echo "• Start mobile session: ./scripts/shared/mobile-session.sh"
    echo "• Run Claude Code: claude"
    echo "• Access Android emulator: adb devices"
    echo "• Session environment: source .claude-env"
    
    # Show Android setup if available
    if [ -n "$ANDROID_SERIAL" ]; then
        echo ""
        echo "📱 Android Development Ready"
        echo "==========================="
        echo "• Device: $ANDROID_SERIAL"
        echo "• Test with: adb -s \"$ANDROID_SERIAL\" shell echo 'Connected!'"
    fi
else
    echo "⚠ No Tailscale IP found - authentication may be incomplete"
fi

echo ""
echo "✅ Mobile access setup complete!"
echo ""
echo "💡 Tips:"
echo "• Keep this terminal open for connection info"
echo "• Use 'ts-status' command to check connection anytime"
echo "• Sessions persist across network changes with MOSH"
echo "• Multiple mobile devices can connect simultaneously"