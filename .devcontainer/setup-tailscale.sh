#!/bin/bash
# Setup Tailscale userspace configuration (no sudo required)
# Runs as vscode user during container creation

set -e

echo "Setting up Tailscale userspace configuration..."

# Create state directory (Docker volume, can't chmod)
mkdir -p ~/.tailscale

# Create start script
cat > ~/start-tailscale.sh << 'EOF'
#!/bin/bash
# Start Tailscale in userspace mode (no sudo needed)

set -e

# Check if already running
if pgrep tailscaled > /dev/null; then
    echo "Tailscale daemon already running"
    tailscale --socket=/home/vscode/.tailscale/tailscaled.sock status
    exit 0
fi

echo "Starting Tailscale in userspace mode..."

# Create socket directory if it doesn't exist
mkdir -p ~/.tailscale

# Debug: check permissions
echo "Directory permissions: $(ls -ld ~/.tailscale)"
echo "Can write test: $(touch ~/.tailscale/test.txt 2>&1 && echo "OK" || echo "FAILED")"
rm -f ~/.tailscale/test.txt

# Start daemon in background with userspace networking
echo "Starting tailscaled daemon..."
tailscaled \
    --tun=userspace-networking \
    --socks5-server=localhost:1055 \
    --state=/home/vscode/.tailscale/tailscaled.state \
    --socket=/home/vscode/.tailscale/tailscaled.sock \
    > /home/vscode/.tailscale/tailscaled.log 2>&1 &

# Wait for daemon to start
echo "Waiting for Tailscale daemon to start..."
for i in {1..10}; do
    if [ -S ~/.tailscale/tailscaled.sock ]; then
        echo "Tailscale daemon started successfully"
        break
    fi
    if [ $i -eq 10 ]; then
        echo "Error: Tailscale daemon failed to start"
        echo "Debug info:"
        ls -la ~/.tailscale/
        if [ -f ~/.tailscale/tailscaled.log ]; then
            echo "Log contents:"
            tail -20 ~/.tailscale/tailscaled.log
        else
            echo "No log file found"
        fi
        exit 1
    fi
    sleep 1
done

# Configure Tailscale with SSH
export TS_SOCKET=/home/vscode/.tailscale/tailscaled.sock

if [ -n "$TS_AUTHKEY" ] && [ "$TS_AUTHKEY" != "" ]; then
    echo "Configuring Tailscale with provided auth key..."
    tailscale up \
        --authkey="$TS_AUTHKEY" \
        --ssh \
        --hostname="osrs-wiki-dev"
    echo "✅ Tailscale configured successfully!"
else
    echo "⚠️ No auth key provided - authentication will be required."
    echo "Run ./scripts/setup-mobile-access.sh after container setup to authenticate."
fi
EOF

chmod +x ~/start-tailscale.sh

# Create status/info script
cat > ~/tailscale-info.sh << 'EOF'
#!/bin/bash
# Get Tailscale connection info (no sudo needed)

export TS_SOCKET=/home/vscode/.tailscale/tailscaled.sock

if ! pgrep tailscaled > /dev/null; then
    echo "Tailscale daemon not running"
    echo "Start with: ~/start-tailscale.sh"
    exit 1
fi

if ! [ -S "$TS_SOCKET" ]; then
    echo "Tailscale socket not found at $TS_SOCKET"
    exit 1
fi

echo "========================================="
echo "Tailscale Status"
echo "========================================="
/usr/bin/tailscale --socket="$TS_SOCKET" status
echo ""
echo "Tailscale IP addresses:"
/usr/bin/tailscale --socket="$TS_SOCKET" ip
echo ""

# Get the main IPv4 address
TAILSCALE_IP=$(/usr/bin/tailscale --socket="$TS_SOCKET" ip -4 2>/dev/null | head -n1)
HOSTNAME=$(hostname)

if [ -n "$TAILSCALE_IP" ]; then
    echo "Mobile SSH Connection Commands:"
    echo "  Direct IP:  ssh vscode@$TAILSCALE_IP"
    echo "  Hostname:   ssh vscode@$HOSTNAME"
    echo ""
    echo "For stable mobile connections (MOSH):"
    echo "  mosh --port=60000 vscode@$TAILSCALE_IP"
    echo ""
    echo "Container Details:"
    echo "  Android Serial: ${ANDROID_SERIAL:-<not set>}"
    echo "  SSH via Tailscale: Available"
else
    echo "Tailscale not authenticated yet"
    echo "Run: tailscale up --ssh --hostname=osrs-wiki-dev"
fi
echo "========================================="
EOF

chmod +x ~/tailscale-info.sh

# Create stop script
cat > ~/stop-tailscale.sh << 'EOF'
#!/bin/bash
# Stop Tailscale daemon

if pgrep tailscaled > /dev/null; then
    echo "Stopping Tailscale daemon..."
    pkill tailscaled
    echo "Tailscale daemon stopped"
else
    echo "Tailscale daemon not running"
fi
EOF

chmod +x ~/stop-tailscale.sh

# Add Tailscale environment to shell profile
echo '' >> ~/.bashrc
echo '# Tailscale configuration (sudo-free)' >> ~/.bashrc
echo 'export TS_SOCKET=/home/vscode/.tailscale/tailscaled.sock' >> ~/.bashrc
echo 'alias tailscale="tailscale --socket=$TS_SOCKET"' >> ~/.bashrc
echo 'alias ts-status="~/tailscale-info.sh"' >> ~/.bashrc
echo 'alias ts-start="~/start-tailscale.sh"' >> ~/.bashrc
echo 'alias ts-stop="~/stop-tailscale.sh"' >> ~/.bashrc
echo 'alias m="./scripts/setup-mobile-access.sh"' >> ~/.bashrc

# Also add to .claude-env if it exists
if [ -f /workspace/.claude-env ]; then
    echo '' >> /workspace/.claude-env
    echo '# Tailscale configuration (sudo-free)' >> /workspace/.claude-env
    echo 'export TS_SOCKET=/home/vscode/.tailscale/tailscaled.sock' >> /workspace/.claude-env
    echo 'alias tailscale="tailscale --socket=$TS_SOCKET"' >> /workspace/.claude-env
    echo 'alias ts-status="~/tailscale-info.sh"' >> /workspace/.claude-env
    echo 'alias ts-start="~/start-tailscale.sh"' >> /workspace/.claude-env
    echo 'alias ts-stop="~/stop-tailscale.sh"' >> /workspace/.claude-env
    echo 'alias m="./scripts/setup-mobile-access.sh"' >> /workspace/.claude-env
fi

echo "✓ Tailscale userspace setup complete!"
echo ""
echo "Quick start commands:"
echo "  ~/start-tailscale.sh   - Start Tailscale daemon"  
echo "  ~/tailscale-info.sh    - Show connection info"
echo "  ts-start               - Alias for start script"
echo "  ts-status              - Alias for info script"
echo ""
echo "To enable remote access after container starts:"
echo "  1. Run: ~/start-tailscale.sh"
echo "  2. Authenticate via the provided URL"
echo "  3. Connect from mobile using SSH or MOSH"