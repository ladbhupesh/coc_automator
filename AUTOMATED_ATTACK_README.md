# Automated Clash of Clans Attack Script

This script automates the attack sequence in Clash of Clans, including placing spells, heroes, siege machine, and goblins.

## Features

- **Automated Attack Flow**: Clicks Attack → Find Match → Start Attack
- **Spell Placement**: Places jump spells and earthquake spells with deviation
- **Hero Deployment**: Places King, Queen, Grand Warden, Champion, and Siege Machine with location variations
- **Goblin Deployment**: Places 106 goblins across 29 locations with random deviation
- **Repeatable**: Can run infinite loop or specified number of attacks
- **Runtime controls** while the script runs:
  - **Pause** — **Ctrl+P**, **Ctrl+^** (often Ctrl+Shift+6), or **Ctrl+L**
  - **Resume** — **Ctrl+S**, **Ctrl+Y**, or **Ctrl+Q** (S/Q are delivered because the script turns off TTY IXON flow control)
  - **Restart** — **Ctrl+R**, **Ctrl+]**, or **Ctrl+O** (same attack number; mid-battle restart may mis-click unless you are on a sensible screen)
  - On **Linux/macOS**, keys are read from **`/dev/tty`** (the real terminal) when possible, so hotkeys still work if Python’s **stdin is piped** (e.g. some IDE run configs). TTY flow control is adjusted so **Ctrl+S** works. **pynput** is used automatically when there is **no** TTY listener (and is optional with `COC_ATTACK_PYNPUT=1` when TTY already works, for keys while the game has focus — X11; often not on Wayland). Set **`COC_ATTACK_PYNPUT=0`** to force-disable pynput.

## Files

- `automated_attack.py` - Core attack automation functions
- `run_automated_attacks.py` - Main script that sets up driver and runs attacks
- `gameplay_recording.py` - Original recorded coordinates (reference)

## Prerequisites

1. Appium server running (`./start_appium.sh`)
2. Android device connected via ADB
3. Clash of Clans installed and ready on device
4. Python dependencies installed (`pip install -r requirements.txt`)

## Usage

### TUI (Textual)

```bash
pip install -r requirements.txt
python -m coc_tui
# or: python run_automated_attacks.py --tui
```

See `cursor_doc/tui.md` for the full flow (device list, Appium connect, live log, stop button).

### Quick Start (CLI)

```bash
cd /media/bhupesh-lad/4ab4e02c-4256-44ef-886b-f3bdea9e4880/coc_automator
source ../venv/bin/activate
python run_automated_attacks.py
```

### Configuration

When you run the script, it will:
1. Detect your connected device
2. Ask for number of attacks (or infinite loop)
3. Launch Clash of Clans
4. Wait for you to press Enter (make sure you're on home screen)
5. Start automated attacks

### Attack Sequence

Each attack follows this sequence:

1. **Click Attack** (2s wait)
2. **Click Find Match** (2s wait)
3. **Click Start Attack** (2s wait)
4. **Wait 20 seconds** (for match to load)
5. **Place Spells**:
   - Jump spell at 3 locations (with 2-3 point deviation)
   - Earthquake spell (with 2-3 point deviation)
6. **Place Heroes & Siege**:
   - King (2-3 placements with 2-3 point deviation)
   - Queen (2-3 placements with 2-3 point deviation)
   - Grand Warden (2-3 placements with 2-3 point deviation)
   - Champion (2-3 placements with 2-3 point deviation)
   - Siege Machine (2-3 placements with 2-3 point deviation)
7. **Place 106 Goblins**:
   - Distributed across 29 locations
   - Each with 2-2 point deviation
8. **Wait 30 seconds**
9. **Click Return Home**
10. **Repeat** (if configured)

## Customization

### Change Number of Goblins

Edit `automated_attack.py`:
```python
place_goblins(driver, count=106)  # Change 106 to your desired count
```

### Adjust Wait Times

Edit the `time.sleep()` values in `automated_attack.py`:
- Between attack clicks: Currently 2 seconds
- Before attack starts: Currently 20 seconds
- After goblins placed: Currently 30 seconds

### Modify Hero Placement Locations

Edit the `heroes` list in `place_heroes_and_siege()` function:
```python
heroes = [
    {"name": "King", "select": (900, 969), "place": (2263, 469)},
    # ... modify coordinates as needed
]
```

### Adjust Deviation

- **Heroes/Siege**: Currently 2-3 points (random)
- **Goblins**: Currently 2-2 points (fixed)
- **Spells**: Currently 2-3 points (random)

Edit the `deviation` parameter in `tap_with_deviation()` calls.

## Coordinate System

All coordinates are in screen pixels:
- X: 0 (left) to screen width (right)
- Y: 0 (top) to screen height (bottom)

**Important**: Coordinates are device-specific. If your device has a different resolution than the recording device, you may need to adjust coordinates.

## Troubleshooting

### Script Clicks Wrong Locations

- Your device may have different screen resolution
- Record new coordinates using Appium Inspector
- Update coordinates in `automated_attack.py`

### Attack Sequence Too Fast/Slow

- Adjust `time.sleep()` values
- Increase delays if actions aren't completing
- Decrease delays to speed up (risky)

### Goblins Not Placing Correctly

- Check if goblin button is selected
- Verify goblin locations are on screen
- Adjust deviation if placements are off

### Heroes Not Placing

- Verify hero selection coordinates
- Check if heroes are available/unlocked
- Adjust placement coordinates

## Safety Features

- **Keyboard Interrupt**: Press Ctrl+C to stop safely
- **Error Handling**: Script catches errors and reports them
- **Session Management**: Properly closes driver on exit

## Example Output

```
============================================================
ATTACK #1
============================================================
Clicking Attack...
Clicking Find Match...
Clicking Start Attack...
Waiting 20 seconds before starting attack...
Placing spells...
  Placing jump spell...
  Placing earthquake spell...
Placing heroes and siege machine...
  Placing King...
  Placing Queen...
  Placing Grand Warden...
  Placing Champion...
  Placing Siege Machine...
Placing 106 goblins...
  Placed 106 goblins across 29 locations
Waiting 30 seconds...
Clicking Return Home...
Attack sequence completed!
```

## Notes

- **Screen Resolution**: Coordinates are based on the recording device's resolution
- **Timing**: Delays may need adjustment based on device performance
- **Game Updates**: If Clash of Clans UI changes, coordinates may need updating
- **Use Responsibly**: Automation may violate game terms of service

## Integration

To use in your own script:

```python
from automated_attack import run_automated_attacks
from appium import webdriver
# ... create driver ...
run_automated_attacks(driver, num_attacks=5)
```

## Support

For issues:
1. Check Appium server is running
2. Verify device connection
3. Ensure Clash of Clans is installed
4. Check coordinates match your device resolution
