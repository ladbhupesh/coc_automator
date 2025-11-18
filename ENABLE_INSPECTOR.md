# Enable Appium Inspector Plugin

## Your Appium Has Inspector Plugin!

Your Appium server shows:
```
Available plugins:
  - inspector@2025.11.1
```

But it says:
```
No plugins activated. Use the --use-plugins flag with names of plugins to activate
```

## Solution: Restart Appium with Inspector Plugin

### Step 1: Stop Current Appium Server

Press `Ctrl+C` in the terminal where Appium is running.

### Step 2: Restart with Inspector Plugin

The `start_appium.sh` script has been updated to include the inspector plugin.

Simply restart Appium:
```bash
./start_appium.sh
```

Or manually:
```bash
appium --relaxed-security --use-plugins inspector
```

### Step 3: Verify Inspector is Active

After restarting, you should see the inspector plugin listed as active, or you can test:
```bash
curl http://127.0.0.1:4723/inspector
```

If it works, you'll see the inspector interface instead of an error.

## Access Inspector

Once the plugin is activated:

1. **Browser Inspector** (Easiest):
   - Open: `http://127.0.0.1:4723/inspector`
   - Enter Session ID from your Python script
   - Click "Attach to Session"

2. **Appium Desktop**:
   - Download: https://github.com/appium/appium-desktop/releases
   - Attach to session using Session ID

## Updated Script

The `start_appium.sh` script now includes:
```bash
appium --relaxed-security --use-plugins inspector
```

This activates:
- `--relaxed-security` - Helps with permission issues
- `--use-plugins inspector` - Enables the inspector plugin

## Quick Test

1. Restart Appium: `./start_appium.sh`
2. Run your script: `python appium_inspector.py`
3. Open browser: `http://127.0.0.1:4723/inspector`
4. Enter Session ID and attach!

The inspector should now work! 🎉
