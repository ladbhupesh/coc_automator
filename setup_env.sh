#!/bin/bash
# Setup script for Android SDK environment variables

# Detect Android SDK location (prioritize locations with platform-tools)
if [ -d "/usr/lib/android-sdk" ] && [ -d "/usr/lib/android-sdk/platform-tools" ]; then
    export ANDROID_HOME="/usr/lib/android-sdk"
    export ANDROID_SDK_ROOT="/usr/lib/android-sdk"
elif [ -d "$HOME/Android/Sdk" ] && [ -d "$HOME/Android/Sdk/platform-tools" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
elif [ -d "/opt/android-sdk" ] && [ -d "/opt/android-sdk/platform-tools" ]; then
    export ANDROID_HOME="/opt/android-sdk"
    export ANDROID_SDK_ROOT="/opt/android-sdk"
elif [ -d "/usr/lib/android-sdk" ]; then
    export ANDROID_HOME="/usr/lib/android-sdk"
    export ANDROID_SDK_ROOT="/usr/lib/android-sdk"
elif [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
elif [ -d "/opt/android-sdk" ]; then
    export ANDROID_HOME="/opt/android-sdk"
    export ANDROID_SDK_ROOT="/opt/android-sdk"
fi

# Add platform-tools to PATH if ANDROID_HOME is set
if [ -n "$ANDROID_HOME" ]; then
    PLATFORM_TOOLS="$ANDROID_HOME/platform-tools"
    if [ -d "$PLATFORM_TOOLS" ]; then
        # Add to PATH if not already there
        case ":$PATH:" in
            *:"$PLATFORM_TOOLS":*)
                ;;
            *)
                export PATH="$PLATFORM_TOOLS:$PATH"
                ;;
        esac
        echo "✓ Android SDK set to: $ANDROID_HOME"
        echo "✓ Platform-tools added to PATH: $PLATFORM_TOOLS"
    else
        echo "Warning: platform-tools not found at $PLATFORM_TOOLS"
        echo "✓ Android SDK set to: $ANDROID_HOME"
    fi
else
    echo "Warning: Android SDK not found. Please install Android SDK."
fi
