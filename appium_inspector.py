#!/usr/bin/env python3
"""
Appium script to launch Clash of Clans and open inspector for app inspection.
"""

import subprocess
import json
import time
import sys
import os
from appium import webdriver
from appium.options.android import UiAutomator2Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


def setup_android_sdk_env():
    """Set up Android SDK environment variables."""
    # Possible SDK locations
    sdk_paths = [
        os.path.expanduser('~/Android/Sdk'),
        '/usr/lib/android-sdk',
        '/opt/android-sdk',
        os.environ.get('ANDROID_HOME', ''),
        os.environ.get('ANDROID_SDK_ROOT', ''),
    ]
    
    # Check which SDK path exists and has platform-tools
    for sdk_path in sdk_paths:
        if sdk_path and os.path.exists(sdk_path):
            platform_tools = os.path.join(sdk_path, 'platform-tools')
            if os.path.exists(platform_tools):
                # Set environment variables
                os.environ['ANDROID_HOME'] = sdk_path
                os.environ['ANDROID_SDK_ROOT'] = sdk_path
                # Add platform-tools to PATH if not already there
                path = os.environ.get('PATH', '')
                if platform_tools not in path:
                    os.environ['PATH'] = f"{platform_tools}:{path}"
                print(f"✓ Android SDK found at: {sdk_path}")
                return sdk_path
    
    print("Warning: Android SDK not found. Please set ANDROID_HOME or ANDROID_SDK_ROOT")
    print("Common locations:")
    print("  - ~/Android/Sdk")
    print("  - /usr/lib/android-sdk")
    return None


def get_adb_devices():
    """Get list of connected ADB device id strings (never a single concatenated str)."""
    try:
        result = subprocess.run(
            ['adb', 'devices'],
            capture_output=True,
            text=True,
            check=True
        )
        lines = result.stdout.strip().split('\n')[1:]  # Skip header
        devices: list[str] = []
        for line in lines:
            if line.strip() and '\tdevice' in line:
                device_id = line.split('\t')[0].strip()
                if device_id:
                    devices.append(device_id)
        return devices
    except subprocess.CalledProcessError as e:
        print(f"Error running adb devices: {e}")
        return []
    except FileNotFoundError:
        print("ADB not found. Please ensure Android SDK platform-tools are in PATH.")
        return []


def get_device_info(device_id):
    """Get device information using ADB."""
    info = {}
    try:
        # Get Android version
        android_version = subprocess.run(
            ['adb', '-s', device_id, 'shell', 'getprop', 'ro.build.version.release'],
            capture_output=True,
            text=True,
            check=True
        ).stdout.strip()
        info['platformVersion'] = android_version

        # Get device manufacturer
        manufacturer = subprocess.run(
            ['adb', '-s', device_id, 'shell', 'getprop', 'ro.product.manufacturer'],
            capture_output=True,
            text=True,
            check=True
        ).stdout.strip()
        info['manufacturer'] = manufacturer

        # Get device model
        model = subprocess.run(
            ['adb', '-s', device_id, 'shell', 'getprop', 'ro.product.model'],
            capture_output=True,
            text=True,
            check=True
        ).stdout.strip()
        info['model'] = model

        return info
    except Exception as e:
        print(f"Error getting device info: {e}")
        return {}


def get_clash_of_clans_package():
    """Get Clash of Clans package name."""
    # Common package names for Clash of Clans
    package_names = [
        'com.supercell.clashofclans',  # Standard package name
    ]
    return package_names[0]


def get_main_activity(device_id, package_name):
    """Get the main activity for a package."""
    try:
        # Use dumpsys to find the main launcher activity
        result = subprocess.run(
            ['adb', '-s', device_id, 'shell', 'dumpsys', 'package', package_name],
            capture_output=True,
            text=True,
            check=True
        )
        
        # Look for the MAIN action with LAUNCHER category
        # Format from dumpsys: "package_name/activity_name filter ..."
        # Example: "com.supercell.clashofclans/com.supercell.titan.GameApp filter 5f2044a"
        lines = result.stdout.split('\n')
        for i, line in enumerate(lines):
            if 'android.intent.action.MAIN' in line:
                # Check current line and next few lines for the activity
                search_range = lines[max(0, i-2):i+5]
                for search_line in search_range:
                    # Look for pattern: package_name/activity_name
                    if package_name in search_line and '/' in search_line:
                        # Find the part that contains package_name/activity
                        words = search_line.split()
                        for word in words:
                            if package_name in word and '/' in word:
                                # Extract activity: split by / and take the last part
                                # Handle: "com.supercell.clashofclans/com.supercell.titan.GameApp"
                                if word.startswith(package_name):
                                    activity = word.split('/')[-1]
                                    # Clean up any trailing characters
                                    activity = activity.split()[0] if ' ' in activity else activity
                                    activity = activity.rstrip('.,;:')
                                    if activity and activity != package_name and len(activity) > 1:
                                        return activity
        return None
    except Exception as e:
        print(f"Error detecting main activity: {e}")
        return None


def check_app_installed(device_id, package_name):
    """Check if app is installed on device."""
    try:
        result = subprocess.run(
            ['adb', '-s', device_id, 'shell', 'pm', 'list', 'packages', package_name],
            capture_output=True,
            text=True,
            check=True
        )
        return package_name in result.stdout
    except Exception as e:
        print(f"Error checking app installation: {e}")
        return False


def start_appium_server():
    """Start Appium server if not already running."""
    try:
        # Check if Appium server is already running
        result = subprocess.run(
            ['curl', '-s', 'http://127.0.0.1:4723/wd/hub/status'],
            capture_output=True,
            timeout=2
        )
        if result.returncode == 0:
            print("Appium server is already running.")
            # Check if it has ANDROID_HOME set by trying to get status
            # Note: We can't directly check, but we'll get an error later if not set
            return True
    except:
        pass

    print("Appium server is not running.")
    print("\nTo start Appium server with Android SDK environment:")
    print("  1. Use the provided script: ./start_appium.sh")
    print("  2. Or manually set ANDROID_HOME and start:")
    print("     export ANDROID_HOME=/usr/lib/android-sdk")
    print("     export ANDROID_SDK_ROOT=/usr/lib/android-sdk")
    print("     appium")
    print("\nOr install Appium Desktop from https://github.com/appium/appium-desktop")
    return False


def create_driver(device_id, device_info):
    """Create Appium WebDriver with inspector capabilities."""
    package_name = get_clash_of_clans_package()
    
    # Check if app is installed
    if not check_app_installed(device_id, package_name):
        print(f"Warning: {package_name} not found on device.")
        print("Please install Clash of Clans on your device first.")
        response = input("Continue anyway? (y/n): ")
        if response.lower() != 'y':
            sys.exit(1)

    # Detect main activity automatically
    print(f"Detecting main activity for {package_name}...")
    main_activity = get_main_activity(device_id, package_name)
    
    if main_activity:
        app_activity = main_activity
        print(f"✓ Found main activity: {app_activity}")
    else:
        # Fallback to common activity names
        common_activities = [
            "com.supercell.titan.GameApp",  # Most common (found on your device)
            "com.supercell.clashofclans.GameApp",  # Alternative
            ".GameApp",  # Short form
        ]
        app_activity = common_activities[0]
        print(f"⚠️  Could not auto-detect activity, using: {app_activity}")
        print("   If this fails, you may need to specify the correct activity manually.")

    # Appium server URL
    appium_server_url = "http://127.0.0.1:4723"

    # Configure capabilities for Android
    options = UiAutomator2Options()
    options.platform_name = "Android"
    options.device_name = device_info.get('model', 'Android Device')
    options.udid = device_id
    options.platform_version = device_info.get('platformVersion', '11.0')
    options.app_package = package_name
    options.app_activity = app_activity
    options.automation_name = "UiAutomator2"
    options.no_reset = True  # Don't reset app state
    options.full_reset = False
    
    # Enable inspector capabilities
    options.new_command_timeout = 300  # 5 minutes timeout
    options.uiautomator2_server_launch_timeout = 30000
    
    # Workaround for hidden API policy errors (common on Android 10+ and wireless ADB)
    # These options help skip problematic operations that may fail due to permissions
    options.skip_unlock = True  # Skip unlock screen
    options.auto_grant_permissions = True  # Auto-grant permissions
    
    # Additional capabilities to handle permission issues
    # Note: The actual fix requires Appium server to be started with --relaxed-security
    # which is done in start_appium.sh
    
    print(f"\nConnecting to Appium server at {appium_server_url}")
    print(f"Device: {device_info.get('model', 'Unknown')} ({device_id})")
    print(f"Android Version: {device_info.get('platformVersion', 'Unknown')}")
    print(f"Package: {package_name}")
    print("\nCreating WebDriver session...")
    print("Note: If you see 'hidden_api_policy' errors, the session may still work.")
    print("This is common with Android 10+ and wireless ADB connections.\n")

    try:
        driver = webdriver.Remote(
            command_executor=appium_server_url,
            options=options
        )
        print("✓ WebDriver session created successfully!")
        return driver
    except Exception as e:
        error_msg = str(e)
        print(f"Error creating WebDriver: {e}")
        print("\nTroubleshooting:")
        print("1. Make sure Appium server is running (http://127.0.0.1:4723)")
        if "ANDROID_HOME" in error_msg or "ANDROID_SDK_ROOT" in error_msg:
            print("2. ⚠️  IMPORTANT: Appium server needs ANDROID_HOME environment variable!")
            print("   - Stop the current Appium server (Ctrl+C in its terminal)")
            print("   - Restart it with: ./start_appium.sh")
            print("   - Or set ANDROID_HOME before starting: export ANDROID_HOME=/usr/lib/android-sdk && appium")
        elif "hidden_api_policy" in error_msg.lower() or "exited with code 255" in error_msg:
            print("2. ⚠️  Hidden API Policy Error (Common on Android 10+ and Wireless ADB)")
            print("   This error occurs when Appium tries to modify system settings.")
            print("   Solutions:")
            print("   a) Try connecting via USB instead of wireless ADB (RECOMMENDED)")
            print("   b) Enable 'Disable permission monitoring' in Developer Options")
            print("   c) Check if your device allows ADB shell commands:")
            print("      adb -s {} shell settings list global | grep hidden_api".format(device_id))
            print("   d) See TROUBLESHOOTING.md for detailed solutions")
            print("\n   Note: This error often occurs with wireless ADB connections.")
            print("   USB connections typically have more permissions.")
        elif "does not exist" in error_msg.lower() or "activity" in error_msg.lower():
            print("2. ⚠️  Activity Not Found Error")
            print("   The app activity name is incorrect.")
            print("   Solutions:")
            print("   a) The script tries to auto-detect the activity, but you can find it manually:")
            print("      adb -s {} shell dumpsys package {} | grep -A 5 MAIN".format(device_id, package_name))
            print("   b) Or try launching the app manually to see the correct activity:")
            print("      adb -s {} shell monkey -p {} -c android.intent.category.LAUNCHER 1".format(device_id, package_name))
            print("   c) Common activity names for Clash of Clans:")
            print("      - com.supercell.titan.GameApp (most common)")
            print("      - com.supercell.clashofclans.GameApp")
            print("      - .GameApp")
        print("3. Check that device is connected: adb devices")
        print("4. Verify Clash of Clans is installed on device")
        raise


def open_inspector_info(driver):
    """Display information about connecting to inspector."""
    session_id = driver.session_id
    appium_server = "http://127.0.0.1:4723"
    
    print("\n" + "="*60)
    print("INSPECTOR CONNECTION INFO")
    print("="*60)
    print(f"Session ID: {session_id}")
    print(f"Appium Server: {appium_server}")
    print("\n" + "="*60)
    print("HOW TO INSPECT UI:")
    print("="*60)
    print("\n📱 Method 1: Appium Desktop (Recommended)")
    print("   1. Open Appium Desktop application")
    print("   2. Click 'Attach to Session'")
    print("   3. Enter Session ID:", session_id)
    print("   4. Set Remote Host: 127.0.0.1")
    print("   5. Set Remote Port: 4723")
    print("   6. Click 'Attach'")
    print("\n🌐 Method 2: Browser Inspector (if plugin enabled)")
    print("   1. Make sure Appium started with: --use-plugins inspector")
    print("   2. Open browser: http://127.0.0.1:4723/inspector")
    print("   3. Enter Session ID:", session_id)
    print("   4. Click 'Attach to Session'")
    print("\n🌐 Method 3: Standalone Inspector")
    print("   1. Install: npm install -g @appium/inspector")
    print("   2. Run: appium-inspector")
    print("   3. Enter Session ID:", session_id)
    print("   4. Set Remote Host: 127.0.0.1, Port: 4723")
    print("   5. Click 'Attach to Session'")
    print("\n💻 Method 4: Create New Session (Alternative)")
    print("   Use these capabilities in Appium Inspector:")
    print("\n   Capabilities JSON:")
    caps = driver.capabilities
    # Create a clean capabilities dict for inspector
    inspector_caps = {
        "platformName": caps.get("platformName", "Android"),
        "deviceName": caps.get("deviceName", "Android Device"),
        "udid": caps.get("udid", ""),
        "platformVersion": caps.get("platformVersion", ""),
        "appPackage": caps.get("appPackage", ""),
        "appActivity": caps.get("appActivity", ""),
        "automationName": caps.get("automationName", "UiAutomator2"),
        "noReset": True,
    }
    print(json.dumps(inspector_caps, indent=2))
    print("\n" + "="*60)
    print("INSPECTOR FEATURES:")
    print("="*60)
    print("✓ View UI element tree (hierarchy)")
    print("✓ Select elements by clicking")
    print("✓ See element properties (resource-id, text, bounds, etc.)")
    print("✓ Take screenshots")
    print("✓ Interact with elements (click, swipe, send text)")
    print("✓ Get XPath for elements")
    print("\n" + "="*60)
    print("QUICK TIPS:")
    print("="*60)
    print("• Keep this script running to maintain the session")
    print("• Use 'Select Element' button in Inspector to click on UI")
    print("• Refresh screenshot frequently to see current UI state")
    print("• Note element properties for use in automation scripts")
    print("• See INSPECT_UI.md for detailed instructions")
    print("\n" + "="*60)
    print("\nThe app should now be open on your device.")
    print("You can interact with it and use Appium Inspector to inspect elements.")
    print("\nPress Ctrl+C to close the session when done.")


def main():
    """Main function."""
    print("="*60)
    print("Clash of Clans Appium Inspector Launcher")
    print("="*60)
    
    # Set up Android SDK environment
    print("\nSetting up Android SDK environment...")
    setup_android_sdk_env()
    
    # Get connected devices
    print("\nScanning for ADB connected devices...")
    devices = get_adb_devices()
    
    if not devices:
        print("No devices found. Please connect a device via ADB.")
        print("Run 'adb devices' to verify connection.")
        sys.exit(1)
    
    # Select device
    if len(devices) == 1:
        device_id = devices[0]
        print(f"Found 1 device: {device_id}")
    else:
        print(f"Found {len(devices)} devices:")
        for i, device_id in enumerate(devices, 1):
            print(f"  {i}. {device_id}")
        choice = input("\nSelect device number (1-{}): ".format(len(devices)))
        try:
            device_id = devices[int(choice) - 1]
        except (ValueError, IndexError):
            print("Invalid selection.")
            sys.exit(1)
    
    # Get device info
    print(f"\nGetting device information for {device_id}...")
    device_info = get_device_info(device_id)
    if not device_info:
        print("Warning: Could not retrieve device info. Using defaults.")
        device_info = {'platformVersion': '11.0', 'model': 'Android Device'}
    
    # Check Appium server
    print("\nChecking Appium server...")
    start_appium_server()
    
    # Create driver and launch app
    driver = None
    try:
        driver = create_driver(device_id, device_info)
        
        print("\n✓ Clash of Clans should now be launching on your device...")
        time.sleep(3)  # Wait for app to launch
        
        # Display inspector connection info
        open_inspector_info(driver)
        
        # Keep session alive
        print("\nSession is active. Keeping connection alive...")
        print("You can now use Appium Inspector to inspect the app.")
        print("The script will keep running until you press Ctrl+C.\n")
        
        # Keep the session alive
        while True:
            try:
                # Check if session is still alive
                driver.current_package
                time.sleep(5)
            except KeyboardInterrupt:
                print("\n\nShutting down...")
                break
            except Exception as e:
                print(f"\nSession ended: {e}")
                break
    
    except KeyboardInterrupt:
        print("\n\nInterrupted by user.")
    except Exception as e:
        print(f"\nError: {e}")
    finally:
        if driver:
            try:
                print("Closing WebDriver session...")
                driver.quit()
                print("✓ Session closed.")
            except:
                pass


if __name__ == "__main__":
    main()
