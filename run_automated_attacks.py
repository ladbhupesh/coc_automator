#!/usr/bin/env python3
"""
Main script to run automated Clash of Clans attacks.
Combines the inspector launcher with automated attack sequence.
"""

import sys
import os
import time
from appium import webdriver
from appium.options.android import UiAutomator2Options
from automated_attack import run_automated_attacks
from appium_inspector import (
    setup_android_sdk_env,
    get_adb_devices,
    get_device_info,
    get_clash_of_clans_package,
    check_app_installed,
    get_main_activity
)


def create_driver(device_id, device_info):
    """Create Appium WebDriver."""
    package_name = get_clash_of_clans_package()
    
    # Check if app is installed
    if not check_app_installed(device_id, package_name):
        print(f"Warning: {package_name} not found on device.")
        print("Please install Clash of Clans on your device first.")
        response = input("Continue anyway? (y/n): ")
        if response.lower() != 'y':
            sys.exit(1)

    # Detect main activity
    print(f"Detecting main activity for {package_name}...")
    main_activity = get_main_activity(device_id, package_name)
    
    if main_activity:
        app_activity = main_activity
        print(f"✓ Found main activity: {app_activity}")
    else:
        app_activity = "com.supercell.titan.GameApp"
        print(f"⚠️  Using default activity: {app_activity}")

    # Appium server URL
    appium_server_url = "http://127.0.0.1:4723"

    # Configure capabilities
    options = UiAutomator2Options()
    options.platform_name = "Android"
    options.device_name = device_info.get('model', 'Android Device')
    options.udid = device_id
    options.platform_version = device_info.get('platformVersion', '11.0')
    options.app_package = package_name
    options.app_activity = app_activity
    options.automation_name = "UiAutomator2"
    options.no_reset = True
    options.full_reset = False
    options.new_command_timeout = 600  # 10 minutes timeout
    options.uiautomator2_server_launch_timeout = 30000
    options.skip_unlock = True
    options.auto_grant_permissions = True

    print(f"\nConnecting to Appium server at {appium_server_url}")
    print(f"Device: {device_info.get('model', 'Unknown')} ({device_id})")
    print(f"Android Version: {device_info.get('platformVersion', 'Unknown')}")
    print(f"Package: {package_name}")
    print("\nCreating WebDriver session...")

    try:
        driver = webdriver.Remote(
            command_executor=appium_server_url,
            options=options
        )
        print("✓ WebDriver session created successfully!")
        return driver
    except Exception as e:
        print(f"Error creating WebDriver: {e}")
        print("\nTroubleshooting:")
        print("1. Make sure Appium server is running: ./start_appium.sh")
        print("2. Check that device is connected: adb devices")
        print("3. Verify Clash of Clans is installed on device")
        raise


def main():
    """Main function."""
    print("="*60)
    print("Clash of Clans Automated Attack Runner")
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
    
    # Ask for number of attacks
    print("\nAttack Configuration:")
    num_attacks_input = input("Number of attacks (Enter for infinite loop, or number): ").strip()
    if num_attacks_input:
        try:
            num_attacks = int(num_attacks_input)
            print(f"Will perform {num_attacks} attacks.")
        except ValueError:
            print("Invalid input. Using infinite loop.")
            num_attacks = None
    else:
        num_attacks = None
        print("Will run infinite loop (Ctrl+C to stop).")
    
    # Create driver
    driver = None
    try:
        driver = create_driver(device_id, device_info)
        
        print("\n✓ Clash of Clans should now be launching on your device...")
        time.sleep(5)  # Wait for app to launch
        
        print("\n" + "="*60)
        print("READY TO START AUTOMATED ATTACKS")
        print("="*60)
        print("\nMake sure you're on the home screen.")
        input("Press Enter when ready to start attacks...")
        
        # Run automated attacks
        run_automated_attacks(driver, num_attacks=num_attacks)
    
    except KeyboardInterrupt:
        print("\n\nInterrupted by user.")
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if driver:
            try:
                print("\nClosing WebDriver session...")
                driver.quit()
                print("✓ Session closed.")
            except:
                pass


if __name__ == "__main__":
    main()
