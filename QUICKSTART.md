# Quick Start Guide

## Common Problems

### Problem 1: ANDROID_HOME / ANDROID_SDK_ROOT error
If you see this error:
```
Neither ANDROID_HOME nor ANDROID_SDK_ROOT environment variable was exported
```

### Problem 2: Could not find 'adb' error
If you see this error:
```
Could not find 'adb'
```

**Both problems** mean **Appium server** needs the Android SDK environment variables and PATH set correctly, but they weren't set when Appium started.

## Solution

### Step 1: Stop Current Appium Server
If Appium is already running, stop it (Ctrl+C in the terminal where it's running).

### Step 2: Start Appium with Android SDK Environment

**Option A: Use the provided script (Easiest)**
```bash
./start_appium.sh
```

**Option B: Manual setup**
```bash
export ANDROID_HOME=/usr/lib/android-sdk
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
appium
```

### Step 3: Run the Inspector Script
In a **different terminal**:
```bash
cd /media/bhupesh-lad/4ab4e02c-4256-44ef-886b-f3bdea9e4880/coc_automator
source ../venv/bin/activate
python appium_inspector.py
```

## Why This Happens

- The Python script can set environment variables for itself
- But Appium server is a **separate process** that needs environment variables set **when it starts**
- Setting variables in the Python script doesn't affect the already-running Appium server

## Verify Setup

Run the verification script:
```bash
./verify_setup.sh
```

This will check:
- Android SDK environment variables
- ADB availability
- Platform-tools directory
- Connected devices
- Appium server status

Or check manually:
```bash
# Check Android SDK
echo $ANDROID_HOME
# Should show: /usr/lib/android-sdk

# Check if adb is accessible
which adb
# Should show: /usr/lib/android-sdk/platform-tools/adb or /usr/bin/adb

# Check Appium server
curl http://127.0.0.1:4723/wd/hub/status
# Should return JSON status
```

## Permanent Fix (Optional)

Add to your `~/.bashrc` or `~/.zshrc`:
```bash
export ANDROID_HOME=/usr/lib/android-sdk
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH
```

Then restart your terminal or run `source ~/.bashrc`.
