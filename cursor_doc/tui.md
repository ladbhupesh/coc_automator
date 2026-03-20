# CoC Automator — TUI

The interface follows an **agent-style shell** (similar to Claude Code / Codex): **top status line**, **left rail** (context + shortcuts), **main workspace**, and a **bottom hint bar** with `^` key chords. **Command palette** is enabled (Textual default binding, often `Ctrl+\\` — see footer when the app runs).

## Run

From the project root (with dependencies installed):

```bash
pip install -r requirements.txt
python -m coc_tui
```

Or:

```bash
python run_automated_attacks.py --tui
```

## Flow

1. **Refresh devices** — ADB scan (USB debugging, `adb devices`).
2. **Connect Appium** — Creates the WebDriver session (Appium must be running, e.g. `./start_appium.sh`).
3. **Start attacks** — Opens the **log screen** and runs the same loop as the CLI (5s delay for CoC to foreground, then `run_automated_attacks`).

Toggles:

- **Allow session if CoC package not detected** — same as answering “y” to the CLI warning when the game is not installed.

## Controls

### Setup view
- **^R** (`Ctrl+R`) — Refresh ADB device list  
- **^D** — Connect Appium / create WebDriver  
- **^G** — Start attacks (opens output stream)  
- **^Q** — Quit (closes WebDriver)

### Output stream view
- **^S** or **Esc** — Request graceful stop (`AutomationShutdown` at next safe point)  
- **^Q** — Stop + quit app  
- **Stop run** / **Close view** buttons — same semantics as above / pop screen  

Terminal **hotkeys** (pause / resume / restart) and **pynput** still apply when supported.

## Implementation notes

- Log output is captured by replacing `stdout`/`stderr` with a line-buffered `QueueWriter` bound to a `RichLog`.
- `create_driver(..., allow_missing_app=...)` avoids blocking `input()` in the TUI.
- `AutomationShutdown` is raised from the attack loop when stop is requested.
