#!/bin/bash
# Verify Android SDK and Appium setup

echo "=== Android SDK Setup Verification ==="
echo ""

# Source environment
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/setup_env.sh"

echo ""
echo "=== Environment Variables ==="
echo "ANDROID_HOME: ${ANDROID_HOME:-NOT SET}"
echo "ANDROID_SDK_ROOT: ${ANDROID_SDK_ROOT:-NOT SET}"
echo ""

echo "=== ADB Check ==="
if command -v adb &> /dev/null; then
    ADB_PATH=$(command -v adb)
    echo "✓ ADB found at: $ADB_PATH"
    adb version | head -1
else
    echo "✗ ADB not found in PATH"
fi

echo ""
echo "=== Platform Tools Check ==="
if [ -n "$ANDROID_HOME" ]; then
    PLATFORM_TOOLS="$ANDROID_HOME/platform-tools"
    if [ -d "$PLATFORM_TOOLS" ]; then
        echo "✓ Platform-tools directory exists: $PLATFORM_TOOLS"
        if [ -f "$PLATFORM_TOOLS/adb" ]; then
            echo "✓ adb executable found in platform-tools"
        else
            echo "✗ adb executable NOT found in platform-tools"
        fi
    else
        echo "✗ Platform-tools directory NOT found at: $PLATFORM_TOOLS"
    fi
else
    echo "✗ ANDROID_HOME not set, cannot check platform-tools"
fi

echo ""
echo "=== Connected Devices ==="
if command -v adb &> /dev/null; then
    adb devices
else
    echo "Cannot check devices - adb not found"
fi

echo ""
echo "=== Appium Server Check ==="
if curl -s http://127.0.0.1:4723/wd/hub/status &> /dev/null; then
    echo "✓ Appium server is running"
    echo "Note: If you're getting 'adb not found' errors, restart Appium with: ./start_appium.sh"
else
    echo "✗ Appium server is not running"
    echo "Start it with: ./start_appium.sh"
fi

echo ""
echo "=== Summary ==="
if [ -n "$ANDROID_HOME" ] && command -v adb &> /dev/null; then
    echo "✓ Setup looks good!"
    echo ""
    echo "To start Appium: ./start_appium.sh"
    echo "To run inspector: source ../venv/bin/activate && python appium_inspector.py"
else
    echo "✗ Setup incomplete. Please check the errors above."
fi
