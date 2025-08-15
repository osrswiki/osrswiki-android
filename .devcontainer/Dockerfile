# Android Development Container with full SDK
FROM openjdk:11-jdk-slim

# Install system dependencies (architecture-aware)
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git \
    curl \
    sudo \
    && ARCH=$(dpkg --print-architecture) \
    && if [ "$ARCH" = "amd64" ]; then \
        apt-get install -y lib32stdc++6 lib32z1; \
    fi \
    && rm -rf /var/lib/apt/lists/*

# Create vscode user
RUN useradd -m -s /bin/bash vscode && \
    echo 'vscode ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

# Install Node.js (for Claude Code)
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs

# Add Tailscale and remote access tools (no sudo configuration needed)
RUN apt-get update && apt-get install -y \
    mosh \
    tmux \
    procps \
    openssh-server \
    && rm -rf /var/lib/apt/lists/*

# Install zellij from binary release (multi-arch support)
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "amd64" ]; then \
        ZELLIJ_ARCH="x86_64-unknown-linux-musl"; \
    elif [ "$ARCH" = "arm64" ]; then \
        ZELLIJ_ARCH="aarch64-unknown-linux-musl"; \
    else \
        echo "Unsupported architecture: $ARCH" && exit 1; \
    fi && \
    wget -q https://github.com/zellij-org/zellij/releases/latest/download/zellij-${ZELLIJ_ARCH}.tar.gz \
    && tar -xzf zellij-${ZELLIJ_ARCH}.tar.gz \
    && chmod +x zellij \
    && mv zellij /usr/local/bin/ \
    && rm zellij-${ZELLIJ_ARCH}.tar.gz

# Install Tailscale binaries
RUN curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/focal.gpg | apt-key add - && \
    echo "deb https://pkgs.tailscale.com/stable/ubuntu focal main" > /etc/apt/sources.list.d/tailscale.list && \
    apt-get update && apt-get install -y tailscale && \
    rm -rf /var/lib/apt/lists/*

# Install Android SDK
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator

RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O /tmp/cmdtools.zip && \
    unzip -q /tmp/cmdtools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm /tmp/cmdtools.zip

# Accept licenses and install basic Android components
RUN yes | $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --licenses && \
    $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-36" \
    "platforms;android-35" \
    "build-tools;36.0.0" \
    "build-tools;35.0.0" \
    "platform-tools"

# Install emulator and system images (architecture-specific)
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "amd64" ]; then \
        $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager \
        "emulator" \
        "system-images;android-35;google_apis;x86_64"; \
    else \
        echo "Emulator not available for $ARCH - build-only container"; \
    fi

# Set permissions
RUN chown -R vscode:vscode $ANDROID_SDK_ROOT

# Set Java environment
ENV JAVA_HOME=/usr/local/openjdk-11
ENV PATH=$PATH:$JAVA_HOME/bin

USER vscode
WORKDIR /workspace