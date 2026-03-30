#!/usr/bin/env python3
"""
Record on-screen taps from the device via ADB getevent.

Usage:
  python3 scripts/adb_record_taps.py
  python3 scripts/adb_record_taps.py --device SERIAL

Prereqs: USB debugging, one device connected (or -s SERIAL).

How it works:
  Streams `adb shell getevent -l` and watches ABS_MT_POSITION_X / ABS_MT_POSITION_Y
  (Linux multitouch). On each SYN_REPORT after both X and Y are set, prints one line:

    tap  screen=(928,1592)  base_2340x1080=(906,797)   # if --base 2340 1080

Leave this running, tap the game; copy coordinates into CoCAttackAutomation / Python.

Note: Some OEMs use different event layouts; if you see nothing, run manually:
  adb shell getevent -l
and look for POSITION_X / POSITION_Y names, then extend PATTERNS below.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys


def main() -> None:
    p = argparse.ArgumentParser(description="Record screen taps via adb getevent")
    p.add_argument("--device", "-s", default=None, help="adb device serial")
    p.add_argument(
        "--base",
        nargs=2,
        type=int,
        metavar=("W", "H"),
        default=(2340, 1080),
        help="Reference base size for scaled columns (default 2340 1080)",
    )
    p.add_argument(
        "--screen",
        nargs=2,
        type=int,
        metavar=("W", "H"),
        default=None,
        help="Physical screen size for base conversion (default: ask adb wm size)",
    )
    args = p.parse_args()
    base_w, base_h = args.base[0], args.base[1]

    screen_w, screen_h = args.screen if args.screen else _adb_wm_size(args.device)
    if screen_w is None:
        print(
            "Could not read screen size. Pass --screen WIDTH HEIGHT "
            "(landscape: e.g. 2400 1080).",
            file=sys.stderr,
        )
        screen_w, screen_h = 2400, 1080

    adb_prefix = ["adb"]
    if args.device:
        adb_prefix += ["-s", args.device]

    cmd = adb_prefix + ["shell", "getevent", "-l"]
    print(
        f"Listening for taps… screen={screen_w}x{screen_h} base={base_w}x{base_h}\n"
        f"Command: {' '.join(cmd)}\nCtrl+C to stop.\n",
        file=sys.stderr,
    )

    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    assert proc.stdout is not None

    x_pat = re.compile(r"ABS_MT_POSITION_X\s+([0-9a-fA-F]+)")
    y_pat = re.compile(r"ABS_MT_POSITION_Y\s+([0-9a-fA-F]+)")
    syn_pat = re.compile(r"SYN_REPORT")

    pending_x: int | None = None
    pending_y: int | None = None

    try:
        for line in proc.stdout:
            line = line.strip()
            if not line:
                continue
            m = x_pat.search(line)
            if m:
                pending_x = int(m.group(1), 16)
                continue
            m = y_pat.search(line)
            if m:
                pending_y = int(m.group(1), 16)
                continue
            if syn_pat.search(line) and pending_x is not None and pending_y is not None:
                sx, sy = pending_x, pending_y
                bx = round(sx * base_w / screen_w)
                by = round(sy * base_h / screen_h)
                print(
                    f"tap  screen=({sx},{sy})  "
                    f"base_{base_w}x{base_h}=({bx},{by})  "
                    f"Kotlin: tapAt({bx}, {by})"
                )
                sys.stdout.flush()
                pending_x = pending_y = None
    except KeyboardInterrupt:
        print("\nStopped.", file=sys.stderr)
    finally:
        proc.terminate()
        proc.wait(timeout=2)


def _adb_wm_size(device: str | None) -> tuple[int | None, int | None]:
    cmd = ["adb"]
    if device:
        cmd += ["-s", device]
    cmd += ["shell", "wm", "size"]
    try:
        out = subprocess.check_output(cmd, text=True, timeout=5)
        # Physical size: 2400x1080\n  or Override size: ...
        m = re.search(r"(?:Physical|Override) size:\s*(\d+)x(\d+)", out)
        if m:
            return int(m.group(1)), int(m.group(2))
    except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired):
        pass
    return None, None


if __name__ == "__main__":
    main()
