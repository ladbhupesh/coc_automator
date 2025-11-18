#!/usr/bin/env python3
"""
Automated Clash of Clans attack script.
Places spells, heroes, siege machine, and goblins in sequence.
"""

import time
import random
import re
import io
from PIL import Image
import pytesseract
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.actions import interaction
from selenium.webdriver.common.actions.action_builder import ActionBuilder
from selenium.webdriver.common.actions.pointer_input import PointerInput


def tap_at_location(driver, x, y, duration=0.1):
    """Helper function to tap at a specific location."""
    actions = ActionChains(driver)
    actions.w3c_actions = ActionBuilder(driver, mouse=PointerInput(interaction.POINTER_TOUCH, "touch"))
    actions.w3c_actions.pointer_action.move_to_location(x, y)
    actions.w3c_actions.pointer_action.pointer_down()
    actions.w3c_actions.pointer_action.pause(duration)
    actions.w3c_actions.pointer_action.release()
    actions.perform()


def tap_with_deviation(driver, base_x, base_y, deviation=2, duration=0.1):
    """Tap at location with random deviation."""
    x = base_x + random.randint(-deviation, deviation)
    y = base_y + random.randint(-deviation, deviation)
    tap_at_location(driver, x, y, duration)


def extract_resources_from_screenshot(driver):
    """
    Take screenshot of resource region and extract gold, elixir, dark elixir values.
    Region: x 100-400, y 140-400
    Returns: (gold, elixir, dark_elixir) or (None, None, None) if failed
    """
    try:
        print("Taking screenshot of resource region...")
        # Take full screenshot
        screenshot_path = driver.get_screenshot_as_png()
        
        # Open image with PIL
        img = Image.open(io.BytesIO(screenshot_path))
        
        # Crop to resource region: x 100-400, y 140-400
        # PIL uses (left, top, right, bottom) format
        cropped = img.crop((100, 140, 400, 400))
        
        # Save cropped image for debugging (optional)
        # cropped.save("resource_region.png")
        
        # Perform OCR
        print("Performing OCR on resource region...")
        ocr_text = pytesseract.image_to_string(cropped, config='--psm 6 -c tessedit_char_whitelist=0123456789,')
        
        # Clean OCR text
        ocr_text = ocr_text.strip()
        print(f"OCR Raw Text: {ocr_text}")
        
        # Extract numbers - look for comma-separated numbers
        # Pattern: numbers with commas (e.g., "1,234,567")
        numbers = re.findall(r'[\d,]+', ocr_text)
        
        # Filter out very short numbers (likely noise)
        numbers = [n for n in numbers if len(n.replace(',', '')) >= 3]
        
        print(f"Extracted numbers: {numbers}")
        
        if len(numbers) >= 3:
            # First is gold, second is elixir, last is dark elixir
            gold = numbers[0].replace(',', '')
            elixir = numbers[1].replace(',', '')
            dark_elixir = numbers[-1].replace(',', '')  # Last one
            
            return (gold, elixir, dark_elixir)
        elif len(numbers) == 2:
            # Only gold and elixir found
            gold = numbers[0].replace(',', '')
            elixir = numbers[1].replace(',', '')
            dark_elixir = "0"
            return (gold, elixir, dark_elixir)
        elif len(numbers) == 1:
            # Only one value found
            gold = numbers[0].replace(',', '')
            elixir = "0"
            dark_elixir = "0"
            return (gold, elixir, dark_elixir)
        else:
            print("Warning: Could not extract resource values from OCR")
            return (None, None, None)
            
    except Exception as e:
        print(f"Error extracting resources: {e}")
        import traceback
        traceback.print_exc()
        return (None, None, None)


def click_attack(driver):
    """Click Attack button."""
    print("Clicking Attack...")
    tap_at_location(driver, 228, 944)
    time.sleep(0.5)  # Reduced from 2s


def click_find_match(driver):
    """Click Find Match button."""
    print("Clicking Find Match...")
    tap_at_location(driver, 431, 809)
    time.sleep(0.5)  # Reduced from 2s


def click_start_attack(driver):
    """Click Start Attack button."""
    print("Clicking Start Attack...")
    tap_at_location(driver, 2006, 966)
    time.sleep(0.5)  # Reduced from 2s


def place_spells(driver):
    """Place spells first."""
    print("Placing spells...")
    
    # Select and place jump spell
    print("  Placing jump spell...")
    tap_at_location(driver, 1500, 978)  # Select jump spell
    time.sleep(0.1)  # Reduced delay
    
    # Place jump spell at 3 locations with 2-3 point deviation
    jump_locations = [
        (466, 372),
        (1681, 159),
        (1572, 706)
    ]
    for loc in jump_locations:
        tap_with_deviation(driver, loc[0], loc[1], deviation=random.randint(2, 3))
        time.sleep(0.05)  # Reduced delay
    
    # Select and place 5 earthquake spells at same location
    print("  Placing 5 earthquake spells...")
    tap_at_location(driver, 1644, 997)  # Select earthquake spell
    time.sleep(0.1)  # Reduced delay
    
    # Place 5 earthquake spells at the same location (1353, 444)
    earthquake_location = (1353, 444)
    for i in range(5):
        tap_with_deviation(driver, earthquake_location[0], earthquake_location[1], deviation=random.randint(2, 3))
        time.sleep(0.05)  # Very fast placement


def place_heroes_and_siege(driver):
    """Place heroes and siege machine quickly."""
    print("Placing heroes and siege machine...")
    
    # Hero locations with base coordinates
    heroes = [
        {"name": "King", "select": (900, 969), "place": (2263, 469)},
        {"name": "Queen", "select": (1053, 972), "place": (2281, 466)},
        {"name": "Grand Warden", "select": (1219, 953), "place": (2256, 456)},
        {"name": "Champion", "select": (1363, 997), "place": (2284, 459)},
        {"name": "Siege Machine", "select": (734, 978), "place": (2272, 491)}
    ]
    
    for hero in heroes:
        print(f"  Placing {hero['name']}...")
        # Select hero
        tap_at_location(driver, hero["select"][0], hero["select"][1])
        time.sleep(0.05)  # Reduced delay - very fast
        
        # Place hero quickly with deviation
        base_x, base_y = hero["place"]
        # Place at 2-3 different locations with deviation (faster)
        num_placements = random.randint(2, 3)
        for i in range(num_placements):
            # Vary location slightly for each placement
            offset_x = random.randint(-3, 3) * (i + 1)
            offset_y = random.randint(-3, 3) * (i + 1)
            tap_with_deviation(driver, base_x + offset_x, base_y + offset_y, deviation=random.randint(2, 3))
            time.sleep(0.03)  # Reduced delay - very fast placement


def place_goblins(driver, count=106):
    """Place goblins at various locations quickly with random deviation."""
    print(f"Placing {count} goblins...")
    
    # Select Goblins
    tap_at_location(driver, 447, 966)  # Select Goblins button
    time.sleep(0.05)  # Reduced delay - very fast
    
    # Goblin placement locations from recording (29 locations)
    goblin_locations = [
        (509, 738), (438, 684), (372, 619), (313, 578), (216, 469),
        (375, 347), (481, 256), (538, 216), (619, 166), (691, 125),
        (803, 63), (1706, 844), (1766, 816), (1847, 794), (1906, 753),
        (1963, 713), (2022, 669), (2075, 638), (2141, 575), (2197, 531),
        (2259, 466), (2200, 413), (2169, 384), (2100, 334), (2003, 275),
        (1866, 194), (1806, 153), (1681, 75), (1641, 50)
    ]
    
    # Distribute goblins across locations
    goblins_per_location = count // len(goblin_locations)
    remaining_goblins = count % len(goblin_locations)
    
    for i, location in enumerate(goblin_locations):
        # Add extra goblin to first few locations if there's a remainder
        num_goblins = goblins_per_location + (1 if i < remaining_goblins else 0)
        
        for _ in range(num_goblins):
            # Place with 2-2 point deviation (as specified) - very fast
            tap_with_deviation(driver, location[0], location[1], deviation=2)
            time.sleep(0.001)  # Very fast placement - almost instant
    
    print(f"  Placed {count} goblins across {len(goblin_locations)} locations")


def click_next_button(driver):
    """Click Next button to find new match."""
    print("Clicking Next button to find new match...")
    # Next button is at x: 2100-2200, y: 700-800
    # Use center of the range with deviation
    center_x = 2150  # (2100 + 2200) / 2
    center_y = 750   # (700 + 800) / 2
    tap_with_deviation(driver, center_x, center_y, deviation=50)  # Allow deviation within range
    time.sleep(2)  # Wait for new match to load


def click_end_battle(driver):
    """Click End Battle button."""
    print("Clicking End Battle...")
    # End Battle button is at x: 125-250, y: 780-820
    # Use center of the range with deviation
    center_x = 187  # (125 + 250) / 2
    center_y = 800  # (780 + 820) / 2
    tap_with_deviation(driver, center_x, center_y, deviation=50)  # Allow deviation within range
    time.sleep(2)  # Wait for battle to end


def click_return_home(driver):
    """Click Return Home button."""
    print("Clicking Return Home...")
    tap_at_location(driver, 1253, 916)
    time.sleep(1)  # Reduced from 2s


def execute_attack_sequence(driver, max_search_attempts=10):
    """
    Execute one complete attack sequence.
    Will search for matches until resources meet threshold or max attempts reached.
    
    Args:
        driver: Appium WebDriver instance
        max_search_attempts: Maximum number of times to search for a good match
    
    Returns:
        True if attack was completed, False if skipped
    """
    print("\n" + "="*60)
    print("Starting Attack Sequence")
    print("="*60)
    
    search_attempts = 0
    
    # Keep searching until we find a good match or reach max attempts
    while search_attempts < max_search_attempts:
        search_attempts += 1
        print(f"\nSearch Attempt #{search_attempts}")
    
        # Step 1: Click Attack -> Find Match -> Start Attack (only on first attempt)
        if search_attempts == 1:
            click_attack(driver)
            click_find_match(driver)
            click_start_attack(driver)
        
        # Step 2: Wait 20 seconds (or less if searching)
        if search_attempts == 1:
            print("Waiting 20 seconds before checking resources...")
            time.sleep(20)
        else:
            print("Waiting 5 seconds for match to load...")
            time.sleep(5)
        
        # Step 2.5: Extract and check resource values before placing troops
        print("\n" + "-"*60)
        print("EXTRACTING RESOURCE VALUES")
        print("-"*60)
        gold, elixir, dark_elixir = extract_resources_from_screenshot(driver)
        
        # Resource threshold: 5L = 500,000 (attack if ANY one crosses 5L)
        RESOURCE_THRESHOLD = 500000
        
        if gold and elixir:
            try:
                gold_value = int(gold)
                elixir_value = int(elixir)
                
                print(f"Gold: {gold_value:,}")
                print(f"Elixir: {elixir_value:,}")
                if dark_elixir:
                    print(f"Dark Elixir: {dark_elixir}")
                
                print(f"\nThreshold Check: Need Gold >= {RESOURCE_THRESHOLD:,} OR Elixir >= {RESOURCE_THRESHOLD:,}")
                
                # Check if ANY resource meets threshold (OR condition)
                if gold_value >= RESOURCE_THRESHOLD or elixir_value >= RESOURCE_THRESHOLD:
                    print(f"✓ Resources meet threshold! Proceeding with attack...")
                    if gold_value >= RESOURCE_THRESHOLD:
                        print(f"  Gold: {gold_value:,} >= {RESOURCE_THRESHOLD:,} ✓")
                    if elixir_value >= RESOURCE_THRESHOLD:
                        print(f"  Elixir: {elixir_value:,} >= {RESOURCE_THRESHOLD:,} ✓")
                    print("-"*60 + "\n")
                    should_attack = True
                else:
                    print(f"✗ Resources below threshold!")
                    print(f"  Gold: {gold_value:,} < {RESOURCE_THRESHOLD:,}")
                    print(f"  Elixir: {elixir_value:,} < {RESOURCE_THRESHOLD:,}")
                    print("  Clicking Next to find better match...")
                    print("-"*60 + "\n")
                    should_attack = False
                    
            except ValueError as e:
                print(f"Error parsing resource values: {e}")
                print("Proceeding with attack anyway...")
                should_attack = True
        else:
            print("Warning: Could not extract resource values")
            if gold:
                print(f"Gold: {gold}")
            if elixir:
                print(f"Elixir: {elixir}")
            if dark_elixir:
                print(f"Dark Elixir: {dark_elixir}")
            print("Proceeding with attack anyway...")
            print("-"*60 + "\n")
            should_attack = True
        
        # If resources don't meet threshold, click Next and continue loop
        if not should_attack:
            if search_attempts < max_search_attempts:
                click_next_button(driver)
                print("Waiting for new match to load...")
                time.sleep(3)  # Wait for new match
                continue  # Try again with new match
            else:
                print(f"Reached max search attempts ({max_search_attempts}). Skipping attack.")
                return False
        
        # Resources meet threshold - proceed with attack
        # Step 3: Place spells first (faster)
        place_spells(driver)
        time.sleep(0.1)  # Reduced delay
        
        # Step 4: Place heroes and siege machine quickly
        place_heroes_and_siege(driver)
        time.sleep(0.1)  # Reduced delay
        
        # Step 5: Place 106 goblins quickly
        place_goblins(driver, count=106)
        
        # Step 6: Wait 2 minutes then click End Battle
        print("Waiting 2 minutes before ending battle...")
        time.sleep(120)  # 2 minutes = 120 seconds
        
        # Step 7: Click End Battle
        click_end_battle(driver)
        
        # Step 8: Click Return Home (after battle ends)
        time.sleep(2)  # Wait for battle end screen
        click_return_home(driver)
        
        print("Attack sequence completed!")
        print("="*60 + "\n")
        return True  # Indicate attack was completed
    
    # Should not reach here, but just in case
    return False


def main_loop(driver, num_attacks=None):
    """Main loop to repeat attack sequence."""
    if num_attacks:
        print(f"Starting automated attacks - {num_attacks} attacks planned")
    else:
        print("Starting automated attacks - Infinite loop (Ctrl+C to stop)")
    
    attack_count = 0
    
    try:
        while True:
            attack_count += 1
            print(f"\n{'='*60}")
            print(f"ATTACK #{attack_count}")
            print(f"{'='*60}")
            
            execute_attack_sequence(driver)
            
            # Wait a bit before next attack
            print("Waiting before next attack...")
            time.sleep(2)  # Reduced from 5s
            
            # Check if we've reached the limit
            if num_attacks and attack_count >= num_attacks:
                print(f"\nCompleted {num_attacks} attacks. Stopping.")
                break
                
    except KeyboardInterrupt:
        print(f"\n\nStopped by user after {attack_count} attacks.")
    except Exception as e:
        print(f"\nError during attack #{attack_count}: {e}")
        raise


# Example usage function that needs to be called with a driver instance
def run_automated_attacks(driver, num_attacks=None):
    """
    Run automated attacks.
    
    Args:
        driver: Appium WebDriver instance
        num_attacks: Number of attacks to perform (None for infinite loop)
    """
    main_loop(driver, num_attacks)


if __name__ == "__main__":
    print("="*60)
    print("Clash of Clans Automated Attack Script")
    print("="*60)
    print("\nThis script automates the attack sequence:")
    print("1. Click Attack -> Find Match -> Start Attack")
    print("2. Place spells (jump, earthquake)")
    print("3. Place heroes and siege machine")
    print("4. Place 106 goblins")
    print("5. Wait and return home")
    print("\nTo use this script, you need to:")
    print("1. Import it in your main script")
    print("2. Create an Appium driver")
    print("3. Call run_automated_attacks(driver)")
    print("\nExample:")
    print("  from automated_attack import run_automated_attacks")
    print("  # ... create driver ...")
    print("  run_automated_attacks(driver, num_attacks=5)")
