#!/usr/bin/env python3
"""
Example script showing how to inspect and interact with UI elements
after connecting via Appium Inspector.
"""

from appium import webdriver
from appium.options.android import UiAutomator2Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import time

# Example: After connecting via Inspector, you can use these methods:

def example_inspect_elements(driver):
    """Example of inspecting elements programmatically."""
    
    # 1. Get the entire page source (XML hierarchy)
    print("Getting page source...")
    page_source = driver.page_source
    # Save to file for inspection
    with open("page_source.xml", "w", encoding="utf-8") as f:
        f.write(page_source)
    print("✓ Saved page source to page_source.xml")
    
    # 2. Take a screenshot
    print("Taking screenshot...")
    driver.save_screenshot("screenshot.png")
    print("✓ Saved screenshot to screenshot.png")
    
    # 3. Find elements by different locators
    
    # By resource-id
    try:
        # Example: Find a button by resource-id
        # Replace with actual resource-id from Inspector
        element = driver.find_element(By.ID, "com.supercell.clashofclans:id/button_id")
        print(f"Found element by ID: {element.text}")
    except:
        print("Element not found by ID")
    
    # By text
    try:
        # Find element containing specific text
        element = driver.find_element(By.XPATH, "//*[@text='Attack']")
        print(f"Found element by text: {element.get_attribute('text')}")
    except:
        print("Element not found by text")
    
    # By XPath (from Inspector)
    try:
        # Use XPath copied from Inspector
        element = driver.find_element(By.XPATH, "//android.widget.Button[@resource-id='...']")
        print(f"Found element by XPath")
    except:
        print("Element not found by XPath")
    
    # 4. Get element properties
    try:
        element = driver.find_element(By.ID, "some_id")
        print("\nElement Properties:")
        print(f"  Text: {element.text}")
        print(f"  Resource ID: {element.get_attribute('resource-id')}")
        print(f"  Content Desc: {element.get_attribute('content-desc')}")
        print(f"  Class: {element.get_attribute('class')}")
        print(f"  Bounds: {element.get_attribute('bounds')}")
        print(f"  Enabled: {element.is_enabled()}")
        print(f"  Displayed: {element.is_displayed()}")
    except:
        print("Could not get element properties")
    
    # 5. Find all elements of a type
    buttons = driver.find_elements(By.CLASS_NAME, "android.widget.Button")
    print(f"\nFound {len(buttons)} buttons on screen")
    for i, btn in enumerate(buttons[:5]):  # Show first 5
        try:
            print(f"  Button {i+1}: {btn.text or btn.get_attribute('content-desc')}")
        except:
            pass

def example_interact_with_elements(driver):
    """Example of interacting with elements."""
    
    # Wait for element to be present
    wait = WebDriverWait(driver, 10)
    
    # Click an element
    try:
        element = wait.until(EC.presence_of_element_located(
            (By.ID, "com.supercell.clashofclans:id/some_button")
        ))
        element.click()
        print("✓ Clicked element")
    except:
        print("Could not click element")
    
    # Send text to an input field
    try:
        element = driver.find_element(By.ID, "com.supercell.clashofclans:id/input_field")
        element.clear()
        element.send_keys("Hello World")
        print("✓ Sent text to input field")
    except:
        print("Could not send text")
    
    # Swipe gesture
    try:
        # Swipe from (x1, y1) to (x2, y2)
        driver.swipe(500, 1000, 500, 500, duration=500)
        print("✓ Performed swipe")
    except:
        print("Could not perform swipe")
    
    # Tap at coordinates
    try:
        driver.tap([(500, 1000)], 100)  # Tap at (500, 1000) for 100ms
        print("✓ Performed tap")
    except:
        print("Could not perform tap")

def example_navigate_app(driver):
    """Example of navigating the app."""
    
    # Go back
    driver.back()
    print("✓ Pressed back button")
    
    # Go to home screen
    driver.press_keycode(3)  # Home key
    print("✓ Pressed home button")
    
    # Open app again
    driver.activate_app("com.supercell.clashofclans")
    print("✓ Activated app")

if __name__ == "__main__":
    print("="*60)
    print("Appium UI Inspection Examples")
    print("="*60)
    print("\nThis script shows examples of inspecting UI elements.")
    print("To use these examples:")
    print("1. Connect via Appium Inspector first")
    print("2. Get element properties from Inspector")
    print("3. Use those properties in your code")
    print("\nSee INSPECT_UI.md for detailed instructions.")
    print("\nNote: This is example code. Modify element IDs/text")
    print("based on what you find in Appium Inspector.")
