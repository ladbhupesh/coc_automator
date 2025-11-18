# Clash of Clans Appium Inspector

This script automatically detects ADB-connected Android devices, launches Clash of Clans, and provides connection information for Appium Inspector to inspect the app.

## Prerequisites

1. **Android SDK Platform Tools (ADB)**
   - Install Android SDK Platform Tools
   - Ensure `adb` is in your PATH
   - Verify with: `adb devices`

2. **Appium Server**
   - Install Appium globally: `npm install -g appium`
   - Or download Appium Desktop from: https://github.com/appium/appium-desktop
   - Start Appium server before running the script

3. **Python 3.7+**
   - Python 3.7 or higher required

4. **Clash of Clans App**
   - Install Clash of Clans on your Android device

5. **USB Debugging**
   - Enable USB debugging on your Android device
   - Connect device via USB or ensure ADB over network is configured

## Installation

1. Install Python dependencies:
```bash
pip install -r requirements.txt
```

2. Make the script executable (Linux/Mac):
```bash
chmod +x appium_inspector.py
```

## Usage

1. **Start Appium Server** (in a separate terminal):
```bash
# Use the provided script (recommended - sets Android SDK environment):
./start_appium.sh

# Or manually:
export ANDROID_HOME=/usr/lib/android-sdk
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
appium
```
   Or use Appium Desktop GUI (make sure to set ANDROID_HOME in its environment).

2. **Connect your Android device** via USB and enable USB debugging.

3. **Verify device connection**:
```bash
adb devices
```

4. **Run the script**:
```bash
python appium_inspector.py
```

5. **Connect Appium Inspector**:
   - Open Appium Inspector (Appium Desktop)
   - The script will display connection details including:
     - Remote Host: `127.0.0.1`
     - Remote Port: `4723`
     - Session capabilities (JSON format)
   - Use these details to connect Inspector to the active session

## How It Works

1. **Device Detection**: Scans for ADB-connected devices
2. **Device Info**: Retrieves Android version, model, and manufacturer
3. **App Launch**: Creates Appium WebDriver session and launches Clash of Clans
4. **Inspector Ready**: Provides connection details for Appium Inspector
5. **Session Management**: Keeps the session alive until you stop the script (Ctrl+C)

## Troubleshooting

### No devices found
- Ensure USB debugging is enabled
- Check `adb devices` shows your device
- Try `adb kill-server && adb start-server`

### ANDROID_HOME / ANDROID_SDK_ROOT error
**⚠️ Most Common Issue**: If you see "Neither ANDROID_HOME nor ANDROID_SDK_ROOT environment variable was exported":
- **Stop** the current Appium server (Ctrl+C)
- **Restart** Appium using `./start_appium.sh` (this sets the environment automatically)
- Or manually: `export ANDROID_HOME=/usr/lib/android-sdk && appium`
- See `QUICKSTART.md` for detailed troubleshooting

### Appium server connection failed
- Make sure Appium server is running on `http://127.0.0.1:4723`
- Check firewall settings
- Verify Appium installation: `appium --version`
- **Important**: Appium server must be started with ANDROID_HOME set (use `./start_appium.sh`)

### App not launching
- Verify Clash of Clans is installed: `adb shell pm list packages | grep clashofclans`
- Check if the package name is correct (may vary by region)
- Ensure the device has sufficient storage and memory

### Inspector connection issues
- Make sure you're using the same Appium server instance
- Check that the session ID matches
- Verify capabilities match between script and Inspector

## Notes

- The script keeps the session alive until you press Ctrl+C
- You can interact with the app on your device while Inspector is connected
- The app will remain open after the script ends (session closed)
- For production automation, remove the inspector-related code

## Customization

To modify the app package or activity, edit these variables in `appium_inspector.py`:
- `get_clash_of_clans_package()`: Change package name
- `options.app_activity`: Change main activity name

## License

This script is provided as-is for educational and testing purposes.


cursor-agent --resume=2bd86914-9ce1-4ad3-b696-66aee57b8daf