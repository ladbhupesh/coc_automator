#!/bin/bash
# Start Appium server with Android SDK environment variables set

# Source the environment setup
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/setup_env.sh"

# Check if Appium is installed
if ! command -v appium &> /dev/null; then
    echo "Error: Appium is not installed."
    echo "Install it with: npm install -g appium"
    exit 1
fi

# Check if Android SDK is set
if [ -z "$ANDROID_HOME" ]; then
    echo "Error: ANDROID_HOME is not set."
    echo "Please install Android SDK and set ANDROID_HOME environment variable."
    exit 1
fi

# Ensure platform-tools is in PATH
PLATFORM_TOOLS="$ANDROID_HOME/platform-tools"
if [ -d "$PLATFORM_TOOLS" ]; then
    export PATH="$PLATFORM_TOOLS:$PATH"
    echo "✓ Added platform-tools to PATH: $PLATFORM_TOOLS"
else
    echo "Warning: platform-tools directory not found at $PLATFORM_TOOLS"
fi

# Verify adb is accessible
if command -v adb &> /dev/null; then
    ADB_PATH=$(command -v adb)
    echo "✓ ADB found at: $ADB_PATH"
    adb version | head -1
else
    echo "Error: adb not found in PATH"
    echo "Please ensure platform-tools is installed in $ANDROID_HOME"
    exit 1
fi

echo ""
echo "Starting Appium server..."
echo "ANDROID_HOME: $ANDROID_HOME"
echo "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
echo "PATH includes: $PLATFORM_TOOLS"
echo ""
echo "Appium will be available at: http://127.0.0.1:4723"
echo "Press Ctrl+C to stop the server."
echo ""

# Start Appium server with environment variables, relaxed security, and inspector plugin
# --relaxed-security allows some operations that might otherwise fail
# --use-plugins inspector activates the built-in inspector plugin
# This helps with hidden API policy errors on Android 10+ devices
exec appium --relaxed-security --use-plugins inspector,execute-driver,universal-xml,images,relaxed-caps,storage
