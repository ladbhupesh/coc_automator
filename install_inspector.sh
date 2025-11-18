#!/bin/bash
# Install Appium Inspector

echo "Installing Appium Inspector..."
echo ""

# Check if npm is available
if ! command -v npm &> /dev/null; then
    echo "Error: npm is not installed."
    echo "Please install Node.js and npm first."
    exit 1
fi

# Install Appium Inspector globally
echo "Installing @appium/inspector..."
npm install -g @appium/inspector

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Appium Inspector installed successfully!"
    echo ""
    echo "To start Inspector, run:"
    echo "  appium-inspector"
    echo ""
    echo "Or use Appium Desktop (recommended):"
    echo "  Download from: https://github.com/appium/appium-desktop/releases"
else
    echo ""
    echo "✗ Installation failed. Try:"
    echo "  sudo npm install -g @appium/inspector"
fi
