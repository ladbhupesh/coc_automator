# Troubleshooting Guide

## Hidden API Policy Error (Exit Code 255)

### Error Message
```
Error executing adbExec. Original error: 'Command '/usr/lib/android-sdk/platform-tools/adb -P 5037 -s DEVICE shell 'settings delete global hidden_api_policy...' exited with code 255'
```

### What This Means
Appium tries to modify Android system settings (hidden API policies) to allow certain automation operations. This command fails with exit code 255 when:
- The device doesn't allow modification of these settings (Android 10+ security)
- Using wireless ADB connection (less permissions than USB)
- Device has strict security policies enabled
- ADB shell doesn't have sufficient permissions

### Solutions (Try in Order)

#### Solution 1: Use USB Connection (Recommended)
Wireless ADB often has fewer permissions than USB:
```bash
# Disconnect wireless ADB
adb disconnect

# Connect via USB and verify
adb devices
# Should show device connected via USB
```

#### Solution 2: Enable Developer Options Settings
On your Android device:
1. Go to **Settings** → **Developer Options**
2. Enable **"Disable permission monitoring"** (if available)
3. Enable **"USB debugging (Security settings)"** (if available)
4. Some devices have **"Disable ADB authorization timeout"**

#### Solution 3: Grant ADB Shell Permissions Manually
Try running the command manually to see the exact error:
```bash
adb -s YOUR_DEVICE_ID shell 'settings delete global hidden_api_policy_pre_p_apps'
```

If this fails, your device likely doesn't allow this operation.

#### Solution 4: Use Appium with Relaxed Security
The `start_appium.sh` script now includes `--relaxed-security` flag which helps, but you may still encounter this error.

#### Solution 5: Check Device Compatibility
Some devices/manufacturers (especially Samsung, Xiaomi) have stricter security:
- Try on a different device if available
- Use a device running Android 9 or earlier
- Use an emulator (often more permissive)

#### Solution 6: Work Around the Error
If the error occurs but the app still launches, you can:
1. Ignore the error (the session might still work)
2. Modify the script to catch and continue on this specific error
3. Use Appium Inspector directly without the script

### Testing the Fix

After trying solutions, test with:
```bash
# Check if you can modify settings
adb shell settings list global | grep hidden_api

# Try to delete (will fail if not allowed, but shows the error)
adb shell 'settings delete global hidden_api_policy_pre_p_apps' 2>&1
```

### Why This Happens

Android 10+ introduced restrictions on accessing hidden APIs. Appium tries to disable these restrictions to allow full automation capabilities. However:
- Some devices don't allow this modification
- Wireless ADB has fewer permissions than USB
- Manufacturer customizations (MIUI, OneUI) add extra restrictions

### Alternative: Use Appium Inspector Directly

If the script fails but you just want to inspect:
1. Start Appium server: `./start_appium.sh`
2. Open Appium Desktop/Inspector
3. Manually enter capabilities:
   ```json
   {
     "platformName": "Android",
     "deviceName": "Your Device",
     "udid": "YOUR_DEVICE_ID",
     "appPackage": "com.supercell.clashofclans",
     "appActivity": "com.supercell.clashofclans.GameApp",
     "automationName": "UiAutomator2"
   }
   ```
4. The inspector might still work even if the settings command fails

### Still Having Issues?

1. Check Appium logs for more details
2. Try with a different Android device/emulator
3. Consider using USB connection instead of wireless
4. Check if your device manufacturer has specific ADB requirements
