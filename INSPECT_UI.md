# How to Inspect UI with Appium Inspector

This guide explains how to inspect UI elements of Clash of Clans using Appium Inspector.

## Prerequisites

1. **Appium Server Running** - Started with `./start_appium.sh`
2. **Python Script Running** - `python appium_inspector.py` (keeps session alive)
3. **Appium Inspector/Desktop Installed**

## Method 1: Using Appium Desktop (Recommended)

### Step 1: Install Appium Desktop

Download from: https://github.com/appium/appium-desktop/releases

Or install via npm:
```bash
npm install -g appium-desktop
```

### Step 2: Start Appium Desktop

```bash
appium-desktop
```

Or launch the Appium Desktop application.

### Step 3: Connect to Existing Session

When you run `python appium_inspector.py`, it will display:

```
============================================================
INSPECTOR CONNECTION INFO
============================================================
Session ID: <session-id>
Appium Server: http://127.0.0.1:4723

Capabilities JSON:
{
  "platformName": "Android",
  "deviceName": "...",
  ...
}
```

**In Appium Desktop:**

1. Click **"Attach to Session"** or **"New Session"**
2. Set **Remote Host**: `127.0.0.1`
3. Set **Remote Port**: `4723`
4. If attaching to existing session:
   - Enter the **Session ID** from the script output
   - Click **"Attach"**
5. If creating new session:
   - Paste the **Capabilities JSON** from script output
   - Click **"Start Session"**

### Step 4: Inspect Elements

Once connected:

1. **View Element Tree** - Left panel shows the UI hierarchy
2. **Select Elements** - Click on elements in the tree or use the "Select Element" button
3. **View Properties** - Right panel shows element details:
   - `resource-id`
   - `text`
   - `content-desc`
   - `class`
   - `bounds`
   - XPath
4. **Take Screenshot** - Click screenshot button to see current UI
5. **Interact** - Click, swipe, or send text to elements

## Method 2: Using Appium Inspector (Standalone)

### Step 1: Install Appium Inspector

**Option A: Using the provided script**
```bash
./install_inspector.sh
```

**Option B: Manual installation**
```bash
npm install -g @appium/inspector
```

### Step 2: Start Inspector

```bash
appium-inspector
```

This will open a browser window automatically.

### Step 3: Connect

1. In the Inspector window, enter:
   - **Remote Host**: `127.0.0.1`
   - **Remote Port**: `4723`
   - **Session ID**: (from script output, if attaching to existing session)
   - **Capabilities**: (paste JSON from script output, if creating new session)
2. Click **"Start Session"** or **"Attach to Session"**

## Method 3: Browser-Based Inspector (Appium 2.x only)

**Note**: Appium 3.x does NOT have built-in browser inspector at `/inspector`.

If you're using Appium 2.x, you can access:
```
http://127.0.0.1:4723/inspector
```

For Appium 3.x, use Method 1 (Appium Desktop) or Method 2 (Standalone Inspector).

## Quick Start Workflow

1. **Terminal 1** - Start Appium server:
   ```bash
   ./start_appium.sh
   ```

2. **Terminal 2** - Run the inspector script:
   ```bash
   source ../venv/bin/activate
   python appium_inspector.py
   ```
   
   Wait for output showing:
   ```
   ✓ WebDriver session created successfully!
   INSPECTOR CONNECTION INFO
   Session ID: <your-session-id>
   ```

3. **Terminal 3** (or Appium Desktop) - Connect Inspector:
   - Use Session ID to attach, OR
   - Use Capabilities JSON to create new session

4. **Keep Terminal 2 running** - The script keeps the session alive

5. **Inspect UI** - Use Inspector to explore elements

## Finding Elements

### Common Element Properties

- **resource-id**: `com.supercell.clashofclans:id/button_name`
- **text**: Visible text on buttons/labels
- **content-desc**: Accessibility description
- **class**: Element type (Button, TextView, etc.)
- **bounds**: `[x1,y1][x2,y2]` - Element position

### Example: Finding a Button

1. Click "Select Element" in Inspector
2. Click on the button in the screenshot
3. View properties:
   ```json
   {
     "resource-id": "com.supercell.clashofclans:id/attack_button",
     "text": "Attack",
     "class": "android.widget.Button"
   }
   ```

### Using XPath

Inspector shows XPath for each element:
```
//android.widget.Button[@resource-id='com.supercell.clashofclans:id/attack_button']
```

## Tips for Clash of Clans

1. **Game UI Changes** - Game UI updates dynamically, refresh screenshots frequently
2. **Overlays** - Some elements may be overlays; check element hierarchy
3. **Animations** - Wait for animations to complete before inspecting
4. **Resource IDs** - Clash of Clans may use generic IDs; use text or XPath instead

## Troubleshooting

### Inspector Can't Connect

- Verify Appium server is running: `curl http://127.0.0.1:4723/wd/hub/status`
- Check session is active: The Python script should still be running
- Try creating new session instead of attaching

### Session Expired

- Restart the Python script: `python appium_inspector.py`
- Get new Session ID and reconnect

### Elements Not Visible

- Take a fresh screenshot
- Scroll or navigate to the screen with the element
- Check if element is in an overlay or different activity

### Inspector Shows Blank Screen

- Verify device is connected: `adb devices`
- Check app is running on device
- Try refreshing screenshot in Inspector

## Advanced: Using Python to Inspect

You can also inspect programmatically:

```python
from appium import webdriver
from appium.options.android import UiAutomator2Options

# ... create driver ...

# Get page source (XML hierarchy)
source = driver.page_source
print(source)

# Find element by ID
element = driver.find_element("id", "com.supercell.clashofclans:id/button")

# Get element properties
print(element.text)
print(element.get_attribute("resource-id"))
print(element.get_attribute("bounds"))

# Take screenshot
driver.save_screenshot("screenshot.png")
```

## Next Steps

After inspecting:
1. Note element properties (resource-id, text, XPath)
2. Use these in your automation scripts
3. Create locator strategies for your tests

For automation examples, see the script code comments in `appium_inspector.py`.
