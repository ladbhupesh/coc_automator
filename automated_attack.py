#!/usr/bin/env python3
"""
Automated Clash of Clans attack script.
Places spells, heroes, siege machine, and goblins in sequence.
"""

import time
import random
import re
import io
import os
from datetime import datetime
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter
import pytesseract
try:
    import easyocr
    EASYOCR_AVAILABLE = True
    EASYOCR_READER = None  # Lazy initialization on first use
except ImportError:
    EASYOCR_AVAILABLE = False
    EASYOCR_READER = None
    print("Note: EasyOCR not installed. Using pytesseract. For better accuracy, install: pip install easyocr")
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.actions import interaction
from selenium.webdriver.common.actions.action_builder import ActionBuilder
from selenium.webdriver.common.actions.pointer_input import PointerInput

# Directory for storing captured images
CAPTURED_IMAGES_DIR = "captured_images"

# Create directory if it doesn't exist
os.makedirs(CAPTURED_IMAGES_DIR, exist_ok=True)


def check_session_alive(driver):
    """Check if the Appium session is still alive."""
    try:
        driver.current_package
        return True
    except:
        return False


def tap_at_location(driver, x, y, duration=0.1, max_retries=3):
    """Helper function to tap at a specific location with retry logic."""
    for attempt in range(max_retries):
        try:
            # Check if session is alive
            if not check_session_alive(driver):
                raise Exception("Session is not alive")
            
            actions = ActionChains(driver)
            actions.w3c_actions = ActionBuilder(driver, mouse=PointerInput(interaction.POINTER_TOUCH, "touch"))
            actions.w3c_actions.pointer_action.move_to_location(x, y)
            actions.w3c_actions.pointer_action.pointer_down()
            actions.w3c_actions.pointer_action.pause(duration)
            actions.w3c_actions.pointer_action.release()
            actions.perform()
            return True
        except Exception as e:
            if attempt < max_retries - 1:
                print(f"  Retry {attempt + 1}/{max_retries} for tap at ({x}, {y})...")
                time.sleep(1)
            else:
                print(f"  Failed to tap at ({x}, {y}) after {max_retries} attempts: {e}")
                raise
    return False


def tap_with_deviation(driver, base_x, base_y, deviation=2, duration=0.1, max_retries=3):
    """Tap at location with random deviation."""
    x = base_x + random.randint(-deviation, deviation)
    y = base_y + random.randint(-deviation, deviation)
    tap_at_location(driver, x, y, duration, max_retries=max_retries)


def preprocess_image_for_ocr(img):
    """
    Enhance image for better OCR accuracy.
    Applies: grayscale, contrast enhancement, sharpening, and thresholding.
    """
    # Convert to grayscale for better OCR
    if img.mode != 'L':
        img = img.convert('L')
    
    # Enhance contrast
    enhancer = ImageEnhance.Contrast(img)
    img = enhancer.enhance(2.0)  # Increase contrast by 2x
    
    # Enhance sharpness
    enhancer = ImageEnhance.Sharpness(img)
    img = enhancer.enhance(2.0)  # Increase sharpness by 2x
    
    # Apply threshold to make text more distinct (convert to binary)
    # Convert PIL Image to numpy array for thresholding
    img_array = np.array(img)
    # Use adaptive thresholding - better for varying lighting
    threshold_value = np.mean(img_array)
    img_array = np.where(img_array > threshold_value, 255, 0).astype(np.uint8)
    img = Image.fromarray(img_array)
    
    # Apply slight blur to reduce noise
    img = img.filter(ImageFilter.MedianFilter(size=3))
    
    return img


def extract_with_easyocr(cropped_img):
    """Extract text using EasyOCR (more accurate)."""
    global EASYOCR_READER
    
    if not EASYOCR_AVAILABLE:
        return None
    
    try:
        # Lazy initialization - initialize reader on first use (it's expensive)
        if EASYOCR_READER is None:
            print("Initializing EasyOCR reader (first time, may take a moment)...")
            import easyocr
            EASYOCR_READER = easyocr.Reader(['en'], gpu=False)
            print("EasyOCR initialized successfully!")
        
        # Convert PIL image to numpy array
        img_array = np.array(cropped_img)
        
        # Perform OCR with allowlist (numbers, comma, and space)
        results = EASYOCR_READER.readtext(img_array, allowlist='0123456789, ')
        
        # Extract text from results (result format: (bbox, text, confidence))
        ocr_text = ' '.join([result[1] for result in results])
        return ocr_text
    except Exception as e:
        print(f"EasyOCR error: {e}")
        return None


def extract_with_pytesseract(cropped_img):
    """Extract text using pytesseract with improved config."""
    try:
        # Try multiple PSM modes for better accuracy (allow numbers, comma, and space)
        configs = [
            '--psm 6 -c tessedit_char_whitelist=0123456789, ',  # Uniform block of text
            '--psm 7 -c tessedit_char_whitelist=0123456789, ',  # Single text line
            '--psm 8 -c tessedit_char_whitelist=0123456789, ',  # Single word
            '--psm 11 -c tessedit_char_whitelist=0123456789, ', # Sparse text
        ]
        
        best_text = ""
        best_confidence = 0
        
        for config in configs:
            try:
                # Get detailed data with confidence scores
                data = pytesseract.image_to_data(cropped_img, config=config, output_type=pytesseract.Output.DICT)
                
                # Calculate average confidence
                confidences = [int(conf) for conf in data['conf'] if int(conf) > 0]
                if confidences:
                    avg_confidence = sum(confidences) / len(confidences)
                    if avg_confidence > best_confidence:
                        best_confidence = avg_confidence
                        # Extract text
                        text_parts = [text for text in data['text'] if text.strip()]
                        best_text = ' '.join(text_parts)
            except:
                continue
        
        # Fallback to simple method if detailed data fails
        if not best_text:
            best_text = pytesseract.image_to_string(cropped_img, config='--psm 6 -c tessedit_char_whitelist=0123456789, ')
        
        return best_text.strip()
    except Exception as e:
        print(f"pytesseract error: {e}")
        return None


def extract_number_from_region(img, region_name, left, top, right, bottom):
    """
    Extract a number from a specific region of the image.
    Handles numbers with spaces and commas (e.g., "1,234,567" or "1 234 567" or "1,234 567").
    
    Args:
        img: PIL Image object
        region_name: Name of the region (for logging)
        left, top, right, bottom: Region coordinates
    
    Returns:
        Extracted number string (with spaces and commas removed) or None
    """
    # Crop to specific region
    cropped = img.crop((left, top, right, bottom))
    
    # Preprocess image for better OCR accuracy
    processed_img = preprocess_image_for_ocr(cropped)
    
    # Try EasyOCR first (more accurate), fallback to pytesseract
    ocr_text = None
    
    if EASYOCR_AVAILABLE:
        ocr_text = extract_with_easyocr(processed_img)
    
    # Fallback to pytesseract if EasyOCR failed or not available
    if not ocr_text:
        ocr_text = extract_with_pytesseract(processed_img)
    
    if not ocr_text:
        print(f"  Warning: OCR returned no text for {region_name}")
        return None
    
    # Clean OCR text
    ocr_text = ocr_text.strip()
    print(f"  {region_name} OCR Raw Text: {ocr_text}")
    
    # Extract numbers - look for numbers that may contain spaces and/or commas
    # Pattern matches: digits, spaces, and commas together (e.g., "1,234,567", "1 234 567", "1,234 567")
    # This regex finds sequences of digits with optional spaces and commas between them
    numbers = re.findall(r'[\d\s,]+', ocr_text)
    
    # Clean each number: remove spaces and commas, keep only digits
    cleaned_numbers = []
    for num_str in numbers:
        # Remove all spaces and commas, keep only digits
        cleaned = re.sub(r'[\s,]+', '', num_str)
        # Require at least 4 digits to filter out noise
        if len(cleaned) >= 4:
            cleaned_numbers.append(cleaned)
    
    if cleaned_numbers:
        # Return the first/largest number found (already cleaned of spaces and commas)
        first_num = cleaned_numbers[0]
        # If OCR produced a leading zero (e.g., '01234'), prepend '1' to correct
        # common OCR misreads where leading digit is dropped or replaced by 0.
        if first_num.startswith('0'):
            corrected = '1' + first_num
            print(f"  Note: {region_name} detected leading zero, corrected to: {corrected}")
            return corrected
        return first_num
    
    return None


def extract_resources_from_screenshot(driver):
    """
    Take screenshot and extract gold, elixir, dark elixir values from specific regions.
    
    Regions:
    - Gold: x 165-380, y 145-200
    - Elixir: x 165-380, y 200-250
    
    Uses improved OCR with image preprocessing and EasyOCR as primary method.
    Returns: (gold, elixir, dark_elixir) or (None, None, None) if failed
    """
    try:
        print("Taking screenshot of resource regions...")
        # Take full screenshot
        screenshot_path = driver.get_screenshot_as_png()
        
        # Open image with PIL
        img = Image.open(io.BytesIO(screenshot_path))
        
        # Save timestamp for image files
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3]  # Include milliseconds
        
        # Extract Gold from specific region: x 165-380, y 145-200
        print("\nExtracting Gold from region (x: 165-380, y: 145-200)...")
        gold_cropped = img.crop((165, 145, 380, 200))
        gold_processed = preprocess_image_for_ocr(gold_cropped)
        
        # Save gold images
        gold_original_path = os.path.join(CAPTURED_IMAGES_DIR, f"gold_original_{timestamp}.png")
        gold_processed_path = os.path.join(CAPTURED_IMAGES_DIR, f"gold_processed_{timestamp}.png")
        gold_cropped.save(gold_original_path)
        gold_processed.save(gold_processed_path)
        print(f"  Saved gold images: {gold_original_path}, {gold_processed_path}")
        
        gold = extract_number_from_region(img, "Gold", 165, 145, 380, 200)
        
        # Extract Elixir from specific region: x 165-380, y 200-250
        print("\nExtracting Elixir from region (x: 165-380, y: 200-250)...")
        elixir_cropped = img.crop((165, 200, 380, 250))
        elixir_processed = preprocess_image_for_ocr(elixir_cropped)
        
        # Save elixir images
        elixir_original_path = os.path.join(CAPTURED_IMAGES_DIR, f"elixir_original_{timestamp}.png")
        elixir_processed_path = os.path.join(CAPTURED_IMAGES_DIR, f"elixir_processed_{timestamp}.png")
        elixir_cropped.save(elixir_original_path)
        elixir_processed.save(elixir_processed_path)
        print(f"  Saved elixir images: {elixir_original_path}, {elixir_processed_path}")
        
        elixir = extract_number_from_region(img, "Elixir", 165, 200, 380, 250)
        
        # Dark Elixir extraction (if needed in future, can be added here)
        dark_elixir = None
        
        print(f"\nExtracted values:")
        print(f"  Gold: {gold if gold else 'Not found'}")
        print(f"  Elixir: {elixir if elixir else 'Not found'}")
        
        return (gold, elixir, dark_elixir)
            
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

def click_add_reinforcements(driver):
    """Click Add Reinforcements button."""
    print("Clicking Add Reinforcements...")
    tap_at_location(driver, 1870, 875)  # Example coordinates, adjust as needed
    time.sleep(0.5)  # Reduced from 2s

def click_confirm_reinforcements(driver):
    """Click Confirm Reinforcements button."""
    print("Clicking Confirm Reinforcements...")
    tap_at_location(driver, 1440, 800)  # Example coordinates, adjust as needed
    time.sleep(0.5)  # Reduced from 2s

def click_start_attack(driver):
    """Click Start Attack button."""
    print("Clicking Start Attack...")
    tap_at_location(driver, 2006, 966)
    time.sleep(0.5)  # Reduced from 2s


def calculate_card_positions(hero_count=4):
    """
    Calculate card positions based on ACTUAL RECORDED positions from gameplay_recording.py.
    
    Panel sequence:
    Cell 1: Goblin (fixed at x=447)
    Cell 2: [Card - skip]
    Cell 3: Siege Machine (fixed at x=734)
    Cell 4-7: Heroes (King=900, Queen=1053, Warden=1219, Champion=1363)
    Cell 8: Jump Spell (shifts based on hero count, base=1500)
    Cell 9: Quake Spell (shifts based on hero count, base=1644)
    
    When heroes are missing, available heroes stay at their recorded positions.
    Only spells shift left by (4 - hero_count) * (CARD_WIDTH + CARD_GAP).
    
    Returns dict with card positions
    """
    CARD_WIDTH = 134  # Card width
    CARD_GAP = 20     # Gap between cards
    
    # ACTUAL RECORDED POSITIONS (from gameplay_recording.py with 4 heroes)
    RECORDED_BASE_POSITIONS = {
        "goblin": 447,
        "siege": 734,
        "king": 900,
        "queen": 1053,
        "warden": 1219,
        "champion": 1363,
        "jump_spell": 1500,
        "quake_spell": 1644
    }
    
    # Initialize positions with recorded base values
    positions = {
        "goblin": {"cell": 1, "center_x": RECORDED_BASE_POSITIONS["goblin"], "fixed": True},
        "card_skip": {"cell": 2, "center_x": 0, "fixed": True},  # Skip
        "siege": {"cell": 3, "center_x": RECORDED_BASE_POSITIONS["siege"], "fixed": True},
        "king": {"cell": 4, "center_x": RECORDED_BASE_POSITIONS["king"], "fixed": False},
        "queen": {"cell": 5, "center_x": RECORDED_BASE_POSITIONS["queen"], "fixed": False},
        "warden": {"cell": 6, "center_x": RECORDED_BASE_POSITIONS["warden"], "fixed": False},
        "champion": {"cell": 7, "center_x": RECORDED_BASE_POSITIONS["champion"], "fixed": False},
        "jump_spell": {"cell": 8, "center_x": RECORDED_BASE_POSITIONS["jump_spell"], "fixed": False},
        "quake_spell": {"cell": 9, "center_x": RECORDED_BASE_POSITIONS["quake_spell"], "fixed": False},
    }
    
    # Adjust for missing heroes: Only spells shift left, heroes stay at their recorded positions
    missing_heroes = 4 - hero_count
    if missing_heroes > 0:
        shift_amount = missing_heroes * (CARD_WIDTH + CARD_GAP)
        # Shift only spells left
        positions["jump_spell"]["center_x"] -= shift_amount
        positions["quake_spell"]["center_x"] -= shift_amount
    
    return positions


def place_jump_spell(driver, hero_count=4):
    """
    Select and place jump spell 3 times at 3 locations.
    Selects from panel center with 5-6px deviation.
    """
    print("Placing jump spell...")
    
    # Get card positions
    positions = calculate_card_positions(hero_count)
    jump_spell_pos = positions["jump_spell"]
    
    # SELECT jump spell from panel center with 5-6px deviation
    center_x = jump_spell_pos["center_x"]
    deviation = random.randint(5, 6)
    select_x = center_x + random.randint(-deviation, deviation)
    
    print(f"  Selecting jump spell from panel (Cell {jump_spell_pos['cell']}, center x={center_x}, selecting at x={select_x})...")
    tap_at_location(driver, select_x, 978)  # Y position from recording
    time.sleep(0.1)
    
    # PLACE jump spell at 3 locations on board with 2-3 point deviation
    jump_locations = [
        (760, 260),
        (1620, 230),
        (1572, 706)
    ]
    print(f"  Placing jump spell at 3 locations on board...")
    for i, loc in enumerate(jump_locations, 1):
        print(f"    Location {i}: ({loc[0]}, {loc[1]})")
        tap_with_deviation(driver, loc[0], loc[1], deviation=random.randint(2, 3))
        time.sleep(0.05)


def place_quake_spells(driver, hero_count=4):
    """
    Select and place 5 earthquake spells at given location.
    Selects from panel center with 5-6px deviation.
    """
    print("Placing 5 earthquake spells...")
    
    # Get card positions
    positions = calculate_card_positions(hero_count)
    quake_spell_pos = positions["quake_spell"]
    
    # SELECT earthquake spell from panel center with 5-6px deviation
    center_x = quake_spell_pos["center_x"]
    deviation = random.randint(5, 6)
    select_x = center_x + random.randint(-deviation, deviation)
    
    print(f"  Selecting earthquake spell from panel (Cell {quake_spell_pos['cell']}, center x={center_x}, selecting at x={select_x})...")
    tap_at_location(driver, select_x, 997)  # Y position from recording
    time.sleep(0.1)
    
    # PLACE 5 earthquake spells at the same location on board
    earthquake_location = (1353, 444)
    print(f"  Placing 5 earthquake spells at ({earthquake_location[0]}, {earthquake_location[1]})...")
    for i in range(5):
        tap_with_deviation(driver, earthquake_location[0], earthquake_location[1], deviation=random.randint(2, 3))
        time.sleep(0.05)  # Very fast placement


def place_heroes_in_sequence(driver, hero_count=4):
    """
    Place heroes in sequence: Hero 1 (King), Hero 2 (Queen), Hero 3 (Warden), Hero 4 (Champion).
    Selects from panel center with 5-6px deviation.
    
    Args:
        driver: Appium WebDriver instance
        hero_count: Number of heroes (1-4). 
                    1 = King only
                    2 = King, Queen
                    3 = King, Queen, Warden
                    4 = King, Queen, Warden, Champion
    """
    print(f"Placing heroes in sequence ({hero_count} heroes available)...")
    
    # Get card positions
    positions = calculate_card_positions(hero_count)
    
    # Hero names and their cells
    hero_configs = [
        {"name": "King", "key": "king", "cell": 4, "place": (2263, 469)},
        {"name": "Queen", "key": "queen", "cell": 5, "place": (2281, 466)},
        {"name": "Grand Warden", "key": "warden", "cell": 6, "place": (2256, 456)},
        {"name": "Champion", "key": "champion", "cell": 7, "place": (2284, 459)}
    ]
    
    # Place heroes in sequence: Hero 1, Hero 2, Hero 3, Hero 4 (only if available)
    for i in range(hero_count):
        hero_config = hero_configs[i]
        hero_pos = positions[hero_config["key"]]
        
        print(f"  Selecting and placing Hero {i+1}: {hero_config['name']} (Cell {hero_config['cell']})...")
        
        # SELECT hero from panel center with 5-6px deviation
        center_x = hero_pos["center_x"]
        deviation = random.randint(5, 6)
        select_x = center_x + random.randint(-deviation, deviation)
        select_y = 969  # Y position from recording (adjust if needed)
        
        print(f"    Selecting from panel (center x={center_x}, selecting at x={select_x} with {deviation}px deviation)...")
        tap_at_location(driver, select_x, select_y)
        time.sleep(0.1)  # Wait for selection to register
        
        # PLACE hero on board with deviation
        base_x, base_y = hero_config["place"]
        print(f"    Placing on board at ({base_x}, {base_y})...")
        # Place at 2-3 different locations with deviation
        num_placements = random.randint(2, 3)
        for j in range(num_placements):
            offset_x = random.randint(-3, 3) * (j + 1)
            offset_y = random.randint(-3, 3) * (j + 1)
            tap_with_deviation(driver, base_x + offset_x, base_y + offset_y, deviation=random.randint(2, 3))
            time.sleep(0.03)
        
        # Deselect hero by tapping on empty area
        time.sleep(0.05)
        tap_at_location(driver, 100, 100)  # Tap empty area to deselect
        time.sleep(0.05)
        print(f"    Hero {i+1} ({hero_config['name']}) placed and deselected.")


def place_siege_machine(driver):
    """
    Place siege machine.
    Selects from panel center (Cell 3) with 5-6px deviation.
    """
    print("Placing Siege Machine...")
    
    # Get card positions
    positions = calculate_card_positions(hero_count=4)  # Use 4 for fixed position calculation
    siege_pos = positions["siege"]
    
    # SELECT siege machine from panel center with 5-6px deviation (Cell 3, fixed)
    center_x = siege_pos["center_x"]
    deviation = random.randint(5, 6)
    select_x = center_x + random.randint(-deviation, deviation)
    select_y = 978  # Y position from recording
    
    print(f"  Selecting Siege Machine from panel (Cell {siege_pos['cell']}, center x={center_x}, selecting at x={select_x} with {deviation}px deviation)...")
    tap_at_location(driver, select_x, select_y)
    time.sleep(0.1)  # Wait for selection to register
    
    # PLACE siege machine on board with deviation (2-3 placements)
    base_x, base_y = 2272, 491  # Placement coordinates from recording
    num_placements = random.randint(2, 3)
    print(f"  Placing Siege Machine {num_placements} time(s) on board...")
    for i in range(num_placements):
        offset_x = random.randint(-3, 3) * (i + 1)
        offset_y = random.randint(-3, 3) * (i + 1)
        tap_with_deviation(driver, base_x + offset_x, base_y + offset_y, deviation=random.randint(2, 3))
        time.sleep(0.03)
    
    # Deselect siege machine by tapping empty area
    time.sleep(0.05)
    tap_at_location(driver, 100, 100)  # Tap empty area to deselect
    time.sleep(0.05)
    print("  Siege Machine placed and deselected.")


def place_goblins(driver, count=106):
    """
    Place goblins at various locations quickly with random deviation.
    Selects from panel center (Cell 1) with 5-6px deviation.
    
    Args:
        driver: Appium WebDriver instance
        count: Number of goblins to place
    """
    print(f"Placing {count} goblins...")
    
    # Get card positions
    positions = calculate_card_positions(hero_count=4)  # Use 4 for fixed position calculation
    goblin_pos = positions["goblin"]
    
    # SELECT Goblins from panel center with 5-6px deviation (Cell 1, fixed)
    center_x = goblin_pos["center_x"]
    deviation = random.randint(5, 6)
    select_x = center_x + random.randint(-deviation, deviation)
    select_y = 966  # Y position from recording
    
    print(f"  Selecting Goblins from panel (Cell {goblin_pos['cell']}, center x={center_x}, selecting at x={select_x} with {deviation}px deviation)...")
    tap_at_location(driver, select_x, select_y)
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
            # Place with 2-2 point deviation (as specified) - no delay between placements
            tap_with_deviation(driver, location[0], location[1], deviation=2)
            # No sleep - deploy all 106 goblins as fast as possible
    
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
    """Click End Battle button with error handling."""
    print("Clicking End Battle...")
    try:
        # Check session before action
        if not check_session_alive(driver):
            raise Exception("Session lost before clicking End Battle")
        
        # End Battle button is at x: 125-250, y: 780-820
        # Use center of the range with deviation
        center_x = 187  # (125 + 250) / 2
        center_y = 800  # (780 + 820) / 2
        tap_with_deviation(driver, center_x, center_y, deviation=50, max_retries=5)  # More retries for critical action
        time.sleep(1)  # Wait for confirm dialog
    except Exception as e:
        print(f"Error clicking End Battle: {e}")
        print("Attempting to recover...")
        raise


def click_confirm(driver):
    """Click Confirm button after End Battle with error handling."""
    print("Clicking Confirm...")
    try:
        # Check session before action
        if not check_session_alive(driver):
            raise Exception("Session lost before clicking Confirm")
        
        # Confirm button is at x: 1200-1500, y: 650-750
        # Use center of the range with deviation
        center_x = 1350  # (1200 + 1500) / 2
        center_y = 700   # (650 + 750) / 2
        tap_with_deviation(driver, center_x, center_y, deviation=50, max_retries=5)  # More retries for critical action
        time.sleep(2)  # Wait for battle to end
    except Exception as e:
        print(f"Error clicking Confirm: {e}")
        print("Attempting to recover...")
        raise


def click_return_home(driver):
    """Click Return Home button."""
    print("Clicking Return Home...")
    tap_at_location(driver, 1253, 916)
    time.sleep(1)  # Reduced from 2s


def execute_attack_sequence(driver, max_search_attempts=10, hero_count=4, add_reinforcements=False):
    """
    Execute one complete attack sequence.
    Will search for matches until resources meet threshold or max attempts reached.
    
    Args:
        driver: Appium WebDriver instance
        max_search_attempts: Maximum number of times to search for a good match
        hero_count: Number of heroes available (1-4). Affects spell and hero positions.
    
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
            if add_reinforcements:
                click_add_reinforcements(driver)
                click_confirm_reinforcements(driver)
            click_start_attack(driver)
        
        # Step 2: Wait 10 seconds (or less if searching)
        if search_attempts == 1:
            print("Waiting 10 seconds before checking resources...")
            time.sleep(10)
        else:
            print("Waiting 5 seconds for match to load...")
            time.sleep(5)
        
        # Step 2.5: Extract and check resource values before placing troops
        print("\n" + "-"*60)
        print("EXTRACTING RESOURCE VALUES")
        print("-"*60)
        gold, elixir, dark_elixir = extract_resources_from_screenshot(driver)
        
        # Dynamic resource threshold:
        # - First 4 attempts: 9L (900,000) - higher threshold
        # - From attempt 5 onwards: 5L (500,000) - lower threshold
        # if search_attempts < 5:
        #     RESOURCE_THRESHOLD = 900000  # 9L (9 lakhs) for first 4 attempts
        #     threshold_label = "9L"
        # else:
        #     RESOURCE_THRESHOLD = 500000  # 5L (5 lakhs) from attempt 5 onwards
        #     threshold_label = "5L"
        
        RESOURCE_THRESHOLD = 500000  # 5L (5 lakhs)
        threshold_label = "5L"
        
        if gold and elixir:
            try:
                gold_value = int(gold)
                elixir_value = int(elixir)
                
                print(f"Gold: {gold_value:,}")
                print(f"Elixir: {elixir_value:,}")
                if dark_elixir:
                    print(f"Dark Elixir: {dark_elixir}")
                
                print(f"\nThreshold Check (Attempt {search_attempts}): Need Gold >= {RESOURCE_THRESHOLD:,} ({threshold_label}) AND Elixir >= {RESOURCE_THRESHOLD:,} ({threshold_label})")
                
                # Check if BOTH resources meet threshold (AND condition)
                if gold_value >= RESOURCE_THRESHOLD and elixir_value >= RESOURCE_THRESHOLD:
                    print(f"✓ Resources meet threshold! Proceeding with attack...")
                    print(f"  Gold: {gold_value:,} >= {RESOURCE_THRESHOLD:,} ✓")
                    print(f"  Elixir: {elixir_value:,} >= {RESOURCE_THRESHOLD:,} ✓")
                    print("-"*60 + "\n")
                    should_attack = True
                else:
                    print(f"✗ Resources below threshold!")
                    if gold_value < RESOURCE_THRESHOLD:
                        print(f"  Gold: {gold_value:,} < {RESOURCE_THRESHOLD:,}")
                    if elixir_value < RESOURCE_THRESHOLD:
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
        # Step 3: Place jump spell first (place 3 times at 3 locations)
        place_jump_spell(driver, hero_count=hero_count)
        time.sleep(0.1)
        
        # Step 4: Place 5 earthquake spells
        place_quake_spells(driver, hero_count=hero_count)
        time.sleep(0.1)
        
        # Step 5: Place heroes in sequence (Hero 1, 2, 3, 4 if available)
        place_heroes_in_sequence(driver, hero_count=hero_count)
        # Ensure all heroes are deselected before selecting siege
        time.sleep(0.1)
        tap_at_location(driver, 100, 100)  # Tap empty area to ensure nothing is selected
        time.sleep(0.05)
        
        # Step 6: Place siege machine (select ONCE, then place multiple times)
        place_siege_machine(driver)
        time.sleep(0.1)
        
        # Step 7: Place remaining goblins last
        place_goblins(driver, count=106)
        time.sleep(0.1)
        
        # Step 6: Wait 1 seconds after last placement (goblins)
        print("Waiting 30 seconds after all placements...")
        # Check session periodically during wait
        wait_time = 30  # 30 seconds
        check_interval = 10  # Check every 10 seconds
        elapsed = 0
        while elapsed < wait_time:
            time.sleep(min(check_interval, wait_time - elapsed))
            elapsed += check_interval
            if not check_session_alive(driver):
                print("Warning: Session lost during wait. Attempting to continue...")
                # Try to continue anyway
        
        # Step 7: Click End Battle with error handling and auto-retry
        end_battle_success = False
        for retry in range(3):
            try:
                click_end_battle(driver)
                end_battle_success = True
                break
            except Exception as e:
                if retry < 2:
                    print(f"Failed to click End Battle (attempt {retry + 1}/3): {e}")
                    print("Retrying in 2 seconds...")
                    time.sleep(2)
                    # Try to recover session
                    try:
                        check_session_alive(driver)
                    except:
                        pass
                else:
                    print(f"Failed to click End Battle after 3 attempts: {e}")
                    print("Trying alternative: direct tap...")
                    try:
                        tap_at_location(driver, 187, 800, max_retries=2)
                        time.sleep(1)
                        end_battle_success = True
                    except:
                        print("Warning: Could not click End Battle. Continuing anyway...")
        
        # Step 8: Click Confirm button with error handling and auto-retry
        if end_battle_success:
            confirm_success = False
            for retry in range(3):
                try:
                    click_confirm(driver)
                    confirm_success = True
                    break
                except Exception as e:
                    if retry < 2:
                        print(f"Failed to click Confirm (attempt {retry + 1}/3): {e}")
                        print("Retrying in 2 seconds...")
                        time.sleep(2)
                        # Try to recover session
                        try:
                            check_session_alive(driver)
                        except:
                            pass
                    else:
                        print(f"Failed to click Confirm after 3 attempts: {e}")
                        print("Trying alternative: direct tap...")
                        try:
                            tap_at_location(driver, 1350, 700, max_retries=2)
                            time.sleep(2)
                            confirm_success = True
                        except:
                            print("Warning: Could not click Confirm. Continuing anyway...")
        
        # Step 9: Click Return Home (after battle ends) with auto-retry
        time.sleep(2)  # Wait for battle end screen
        for retry in range(3):
            try:
                click_return_home(driver)
                break
            except Exception as e:
                if retry < 2:
                    print(f"Failed to click Return Home (attempt {retry + 1}/3): {e}")
                    print("Retrying in 2 seconds...")
                    time.sleep(2)
                    # Try to recover session
                    try:
                        check_session_alive(driver)
                    except:
                        pass
                else:
                    print(f"Warning: Could not click Return Home after 3 attempts: {e}")
                    print("Session may have ended. Continuing to next attack...")
        
        print("Attack sequence completed!")
        print("="*60 + "\n")
        return True  # Indicate attack was completed
    
    # Should not reach here, but just in case
    return False


def main_loop(driver, num_attacks=None, hero_count=4, add_reinforcements = False):
    """
    Main loop to repeat attack sequence.
    
    Args:
        driver: Appium WebDriver instance
        num_attacks: Number of attacks to perform (None for infinite loop)
        hero_count: Number of heroes available (1-4)
    """
    if num_attacks:
        print(f"Starting automated attacks - {num_attacks} attacks planned")
    else:
        print("Starting automated attacks - Infinite loop (Ctrl+C to stop)")
    
    print(f"Hero Configuration: {hero_count} hero(s) available")
    hero_names = ["King", "King+Queen", "King+Queen+Warden", "All (King+Queen+Warden+Champion)"]
    print(f"Heroes: {hero_names[hero_count-1]}")
    
    attack_count = 0
    
    try:
        while True:
            attack_count += 1
            print(f"\n{'='*60}")
            print(f"ATTACK #{attack_count}")
            print(f"{'='*60}")
            
            try:
                execute_attack_sequence(driver, hero_count=hero_count, add_reinforcements=add_reinforcements)
                
                # Wait a bit before next attack
                print("Waiting before next attack...")
                time.sleep(2)  # Reduced from 5s
                
            except Exception as attack_error:
                # Handle errors within attack sequence
                print(f"\nError in attack sequence: {attack_error}")
                error_str = str(attack_error).lower()
                is_connection_error = "socket" in error_str or "hang up" in error_str or "connection" in error_str
                
                if is_connection_error:
                    print("⚠️  Connection error in attack sequence. Auto-recovering...")
                    # Try to check session
                    try:
                        if check_session_alive(driver):
                            print("✓ Session recovered. Continuing...")
                        else:
                            print("✗ Session lost. Will try next attack...")
                    except:
                        print("Could not verify session. Continuing anyway...")
                
                print("Waiting 5 seconds before next attack...")
                time.sleep(5)
                # Continue to next attack instead of breaking
            
            # Check if we've reached the limit
            if num_attacks and attack_count >= num_attacks:
                print(f"\nCompleted {num_attacks} attacks. Stopping.")
                break
                
    except KeyboardInterrupt:
        print(f"\n\nStopped by user after {attack_count} attacks.")
    except Exception as e:
        print(f"\nError during attack #{attack_count}: {e}")
        import traceback
        traceback.print_exc()
        
        # Check if it's a connection error
        error_str = str(e).lower()
        is_connection_error = "socket" in error_str or "hang up" in error_str or "connection" in error_str
        
        if is_connection_error:
            print("\n⚠️  Connection error detected! Attempting auto-recovery...")
            print("This usually means:")
            print("  1. Appium server connection was lost")
            print("  2. Device disconnected")
            print("  3. App crashed or became unresponsive")
            
            # Try to recover session
            try:
                if check_session_alive(driver):
                    print("✓ Session is still alive. Continuing to next attack...")
                else:
                    print("✗ Session is dead. Attempting to restart...")
                    # Try to restart by reconnecting
                    try:
                        driver.current_package  # Try to access driver
                        print("✓ Driver still accessible, continuing...")
                    except:
                        print("✗ Driver connection lost. Will attempt to continue anyway...")
            except Exception as recovery_error:
                print(f"Recovery check failed: {recovery_error}")
                print("Continuing to next attack anyway...")
        
        # Auto-continue to next attack
        print(f"\nAttack #{attack_count} failed. Auto-continuing to next attack...")
        print("Waiting 5 seconds before retrying...\n")
        time.sleep(5)
        # Continue loop - don't raise exception


# Example usage function that needs to be called with a driver instance
def run_automated_attacks(driver, num_attacks=None, hero_count=4, add_reinforcements = False):
    """
    Run automated attacks.
    
    Args:
        driver: Appium WebDriver instance
        num_attacks: Number of attacks to perform (None for infinite loop)
        hero_count: Number of heroes available (1-4). Default 4.
                    1 = King only
                    2 = King, Queen
                    3 = King, Queen, Warden
                    4 = King, Queen, Warden, Champion
    """
    main_loop(driver, num_attacks, hero_count=hero_count, add_reinforcements=add_reinforcements)


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
