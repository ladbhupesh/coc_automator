"""
CoC Automator — agent-style TUI (Claude Code / Codex–inspired shell).

Run: python -m coc_tui   or   python run_automated_attacks.py --tui
"""

from __future__ import annotations

import queue
import sys
import threading
import time
from pathlib import Path
from typing import Any

from rich.text import Text
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Container, Horizontal, Vertical, VerticalScroll
from textual.screen import Screen
from textual.widgets import Button, Input, Label, RichLog, Select, Static, Switch

from automated_attack import (
    clear_automation_shutdown,
    request_automation_shutdown,
    run_automated_attacks,
)
from appium_inspector import get_adb_devices, get_device_info, setup_android_sdk_env
from run_automated_attacks import create_driver


def _normalize_adb_device_ids(devices: Any) -> list[str]:
    """
    Build a list of device id strings for the Device Select.

    Never pass a bare str into Select.from_values(*x): Python unpacks str as
    characters, so each digit/dot becomes a fake "device" (seen as 1, 9, 2, …).
    """
    if devices is None:
        return []
    if isinstance(devices, (str, bytes)):
        text = devices.decode() if isinstance(devices, bytes) else devices
        text = text.strip()
        return [text] if text else []
    out: list[str] = []
    for item in devices:
        s = str(item).strip()
        if s:
            out.append(s)
    # Recover one device id that was split into single-character "options"
    # (e.g. 192.168.x.x → ['1','9','2',...]).
    if len(out) > 5 and all(len(x) == 1 for x in out):
        return ["".join(out)]
    return out


def _device_select(device_ids: list[str]) -> Select:
    """Explicit options — avoids Textual from_values + splat footguns."""
    options = [(d, d) for d in device_ids]
    return Select(
        options,
        id="device_sel",
        prompt="Device",
        allow_blank=False,
        value=device_ids[0],
    )


def _hint_bar(*pairs: tuple[str, str]) -> Text:
    """Bottom bar: highlighted keys like Codex (^q Quit)."""
    t = Text()
    sep = "   "
    for i, (key, desc) in enumerate(pairs):
        if i:
            t.append(sep, style="#3d3d3d")
        t.append(key, style="bold #c9a87c")
        t.append(f" {desc}", style="#5c5c5c")
    return t


class QueueWriter:
    """Line-buffered writer that enqueues whole lines for the TUI log."""

    __slots__ = ("_q", "_buf")

    def __init__(self, q: queue.Queue) -> None:
        self._q = q
        self._buf = ""

    def write(self, s: str) -> int:
        if not s:
            return 0
        self._buf += s
        while "\n" in self._buf:
            line, self._buf = self._buf.split("\n", 1)
            self._q.put(line)
        return len(s)

    def flush(self) -> None:
        if self._buf:
            self._q.put(self._buf.rstrip("\r\n"))
            self._buf = ""


class AgentShellScreen(Screen):
    """Shared layout: top chrome · left rail · workspace · bottom hints."""

    def top_subtitle(self) -> str:
        return "  ·  session"

    def compose_sidebar(self) -> ComposeResult:
        yield Static("")

    def compose_workspace(self) -> ComposeResult:
        yield Static("")

    def bottom_hints(self) -> Text | str:
        return _hint_bar(("^Q", "Quit"))

    def compose(self) -> ComposeResult:
        with Horizontal(classes="chrome-top"):
            yield Static("coc-automator", classes="accent")
            yield Static(self.top_subtitle())
        with Horizontal(id="shell-row"):
            with Vertical(id="sidebar-rail"):
                yield Static("coc-automator", classes="brand")
                yield Static("automation", classes="rail-line")
                yield Static("")
                yield from self.compose_sidebar()
            with Vertical(id="main-workspace"):
                yield from self.compose_workspace()
        hints = self.bottom_hints()
        yield Static(hints, classes="chrome-bottom")


class SetupScreen(AgentShellScreen):
    BINDINGS = [
        Binding("ctrl+r", "do_refresh", "Refresh devices", show=False),
        Binding("ctrl+d", "do_connect", "Connect Appium", show=False),
        Binding("ctrl+g", "do_start", "Start attacks", show=False),
        Binding("ctrl+q", "quit_app", "Quit", show=False),
    ]

    def top_subtitle(self) -> str:
        return "  ·  configure session"

    def compose_sidebar(self) -> ComposeResult:
        yield Static("Workspace", classes="rail-title")
        yield Static("─────────────", classes="rail-line")
        yield Static("Session setup", classes="session-idle", id="session-pill")
        yield Static("")
        yield Static("Shortcuts", classes="rail-title")
        yield Static("─────────────", classes="rail-line")
        yield Static("^R  scan ADB", classes="rail-line")
        yield Static("^D  connect", classes="rail-line")
        yield Static("^G  start run", classes="rail-line")
        yield Static("^Q  quit", classes="rail-line")
        yield Static("")
        yield Static("Tip", classes="rail-title")
        yield Static("─────────────", classes="rail-line")
        yield Static("Appium must be\nlistening (e.g.\n:4723).", classes="rail-line")

    def compose_workspace(self) -> ComposeResult:
        with VerticalScroll(id="setup-scroll"):
            yield Static("Session", classes="panel-title")
            yield Static(
                "Pick device, connect WebDriver, then start the attack loop. "
                "Output streams in the next view.",
                classes="panel-desc",
            )
            yield Label("Device", classes="section-label")
            yield Container(id="device_slot")
            yield Label("Heroes", classes="section-label")
            yield Select(
                [
                    ("1 — King", 1),
                    ("2 — King + Queen", 2),
                    ("3 — King + Queen + Warden", 3),
                    ("4 — All heroes", 4),
                ],
                id="heroes_sel",
                prompt="Heroes",
                allow_blank=False,
                value=4,
            )
            yield Label("Attack count (empty = ∞)", classes="section-label")
            yield Input(placeholder="e.g. 10", id="attacks_in")
            yield Horizontal(
                Label("Add reinforcements"),
                Switch(value=False, id="reinf_sw"),
                classes="section-label",
            )
            yield Horizontal(
                Label("Allow session if CoC missing"),
                Switch(value=False, id="allow_miss_sw"),
                classes="section-label",
            )
            yield Static("", id="status-line")
            with Horizontal(id="action-row"):
                yield Button("Refresh", id="btn_refresh", variant="default")
                yield Button("Connect", id="btn_connect", variant="primary")
                yield Button("Start", id="btn_start", variant="success", disabled=True)
                yield Button("Quit", id="btn_quit", variant="error")

    def bottom_hints(self) -> Text:
        return _hint_bar(
            ("^R", "Refresh"),
            ("^D", "Connect"),
            ("^G", "Start"),
            ("^Q", "Quit"),
        )

    def on_mount(self) -> None:
        self._set_status("Scanning ADB…")
        threading.Thread(target=self._adb_scan_thread, daemon=True, name="adb-scan").start()

    def action_quit_app(self) -> None:
        self.app.action_quit()

    def action_do_refresh(self) -> None:
        self._set_status("Scanning ADB…")
        threading.Thread(target=self._adb_scan_thread, daemon=True, name="adb-scan").start()

    def action_do_connect(self) -> None:
        self._connect_pressed()

    def action_do_start(self) -> None:
        self._start_pressed()

    def _set_status(self, text: str) -> None:
        self.query_one("#status-line", Static).update(text)

    def _adb_scan_thread(self) -> None:
        try:
            setup_android_sdk_env()
            devices = get_adb_devices()
        except Exception as e:
            self.app.call_from_thread(self._apply_devices, None, str(e))
            return
        self.app.call_from_thread(self._apply_devices, devices, None)

    def _apply_devices(self, devices: list[str] | None, err: str | None) -> None:
        slot = self.query_one("#device_slot", Container)
        slot.remove_children()
        if err is not None:
            slot.mount(Static(f"ADB error: {err}", id="device_err"))
            self._set_status(f"Device scan failed: {err}")
            return
        assert devices is not None
        device_ids = _normalize_adb_device_ids(devices)
        if not device_ids:
            slot.mount(Static("No ADB devices. USB debugging?", id="no_dev"))
            self._set_status("No devices.")
            return
        slot.mount(_device_select(device_ids))
        self._set_status(f"{len(device_ids)} device(s). ^D to connect Appium.")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        bid = event.button.id or ""
        if bid == "btn_quit":
            self.app.action_quit()
        elif bid == "btn_refresh":
            self.action_do_refresh()
        elif bid == "btn_connect":
            self._connect_pressed()
        elif bid == "btn_start":
            self._start_pressed()

    def _selected_device(self) -> str | None:
        try:
            sel = self.query_one("#device_sel", Select)
        except Exception:
            self.app.notify("Select a device (refresh if empty).", severity="error")
            return None
        v = sel.value
        if v is Select.NULL:
            self.app.notify("Choose a device.", severity="error")
            return None
        return str(v)

    def _connect_pressed(self) -> None:
        device_id = self._selected_device()
        if not device_id:
            return
        allow_miss = self.query_one("#allow_miss_sw", Switch).value
        self._set_status(f"Connecting {device_id}…")
        self.query_one("#btn_connect", Button).disabled = True
        threading.Thread(
            target=self._connect_thread,
            args=(device_id, allow_miss),
            daemon=True,
            name="appium-connect",
        ).start()

    def _connect_thread(self, device_id: str, allow_miss: bool) -> None:
        app = self.app
        try:
            info = get_device_info(device_id) or {"platformVersion": "11.0", "model": "Android Device"}
            drv = create_driver(device_id, info, allow_missing_app=allow_miss)
        except Exception as e:
            app.call_from_thread(self._on_connect_failed, str(e))
            return
        app.call_from_thread(self._on_connect_ok, drv)

    def _on_connect_failed(self, msg: str) -> None:
        self.query_one("#btn_connect", Button).disabled = False
        self._set_status(f"Failed: {msg}")
        try:
            self.query_one("#session-pill", Static).update("○ Connect failed")
            self.query_one("#session-pill", Static).set_classes("session-warn")
        except Exception:
            pass
        self.app.notify(msg, severity="error", timeout=8)

    def _on_connect_ok(self, driver: Any) -> None:
        self.app.driver = driver
        self.query_one("#btn_connect", Button).disabled = False
        self.query_one("#btn_start", Button).disabled = False
        self._set_status("WebDriver ready. Village home → ^G Start.")
        pill = self.query_one("#session-pill", Static)
        pill.update("● Appium ready")
        pill.set_classes("session-ok")
        self.app.notify("Session online.", timeout=3)

    def _start_pressed(self) -> None:
        if getattr(self.app, "driver", None) is None:
            self.app.notify("Connect first (^D).", severity="error")
            return
        attacks_raw = self.query_one("#attacks_in", Input).value.strip()
        num_attacks: int | None
        if not attacks_raw:
            num_attacks = None
        else:
            try:
                num_attacks = int(attacks_raw)
                if num_attacks < 1:
                    raise ValueError("positive")
            except ValueError:
                self.app.notify("Attack count: empty or positive int.", severity="error")
                return
        heroes = self.query_one("#heroes_sel", Select).value
        if heroes is Select.NULL:
            self.app.notify("Select heroes.", severity="error")
            return
        self.app.attack_options = {
            "num_attacks": num_attacks,
            "hero_count": int(heroes),
            "add_reinforcements": self.query_one("#reinf_sw", Switch).value,
        }
        self.app.push_screen(RunScreen())


class RunScreen(AgentShellScreen):
    BINDINGS = [
        Binding("ctrl+s", "request_stop", "Stop", show=False),
        Binding("escape", "request_stop", "Stop", show=False),
        Binding("ctrl+q", "quit_app", "Quit", show=False),
    ]

    def top_subtitle(self) -> str:
        return "  ·  output stream"

    def compose_sidebar(self) -> ComposeResult:
        yield Static("Run", classes="rail-title")
        yield Static("─────────────", classes="rail-line")
        yield Static("● Streaming", classes="session-ok", id="run-state")
        yield Static("")
        yield Static("Shortcuts", classes="rail-title")
        yield Static("─────────────", classes="rail-line")
        yield Static("^S  stop run", classes="rail-line")
        yield Static("Esc stop", classes="rail-line")
        yield Static("^Q  quit app", classes="rail-line")
        yield Static("")
        yield Static("Automation", classes="rail-title")
        yield Static("─────────────", classes="rail-line")
        yield Static("Pause / resume\nstill use term\nhotkeys if on.", classes="rail-line")

    def compose_workspace(self) -> ComposeResult:
        yield Static(
            "Live log — same stdout as CLI. Stop waits for a safe slice in the loop.",
            id="run-hint",
        )
        with Container(id="log-panel"):
            yield RichLog(id="run_log", highlight=False, markup=False, wrap=True, max_lines=12000, auto_scroll=True)
        with Horizontal(id="run-actions"):
            yield Button("Stop run", variant="warning", id="btn_stop_run")
            yield Button("Close view", variant="primary", id="btn_close_run")

    def bottom_hints(self) -> Text:
        return _hint_bar(
            ("^S", "Stop"),
            ("Esc", "Stop"),
            ("^Q", "Quit"),
        )

    def on_mount(self) -> None:
        log = self.query_one("#run_log", RichLog)
        log.clear()
        self._log_timer = self.set_interval(0.04, self._drain_log_queue)
        threading.Thread(target=self._automation_worker, daemon=True, name="coc-automation").start()

    def action_quit_app(self) -> None:
        request_automation_shutdown()
        self.app.action_quit()

    def _drain_log_queue(self) -> None:
        log = self.query_one("#run_log", RichLog)
        q = self.app.log_queue
        try:
            while True:
                line = q.get_nowait()
                log.write(line)
        except queue.Empty:
            pass

    def _automation_worker(self) -> None:
        app = self.app
        old_out, old_err = sys.stdout, sys.stderr
        writer = QueueWriter(app.log_queue)
        sys.stdout = writer
        sys.stderr = writer
        opts = getattr(app, "attack_options", {})
        try:
            clear_automation_shutdown()
            print("CoC launch wait (5s)…", flush=True)
            time.sleep(5)
            print("Loop running. ^S or Esc to request stop.", flush=True)
            run_automated_attacks(
                app.driver,
                num_attacks=opts.get("num_attacks"),
                hero_count=int(opts.get("hero_count", 4)),
                add_reinforcements=bool(opts.get("add_reinforcements", False)),
            )
        except Exception as e:
            app.log_queue.put(f"[error] {e!r}")
            import traceback

            for ln in traceback.format_exc().splitlines():
                app.log_queue.put(ln)
        finally:
            writer.flush()
            sys.stdout, sys.stderr = old_out, old_err
            app.call_from_thread(self._after_run)

    def _after_run(self) -> None:
        t = getattr(self, "_log_timer", None)
        if t is not None:
            try:
                t.stop()
            except Exception:
                pass
            self._log_timer = None
        self._drain_log_queue()
        try:
            st = self.query_one("#run-state", Static)
            st.update("○ Finished")
            st.set_classes("session-idle")
        except Exception:
            pass
        self.app.notify("Run finished.", timeout=5)

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn_stop_run":
            self.action_request_stop()
        elif event.button.id == "btn_close_run":
            t = getattr(self, "_log_timer", None)
            if t is not None:
                try:
                    t.stop()
                except Exception:
                    pass
                self._log_timer = None
            self.app.pop_screen()

    def action_request_stop(self) -> None:
        request_automation_shutdown()
        self.app.notify("Stop requested.", timeout=3)


class CocAutomatorApp(App):
    """Root app — command palette + agent chrome."""

    TITLE = "coc-automator"
    CSS_PATH = Path(__file__).with_name("app.tcss")
    ENABLE_COMMAND_PALETTE = True

    BINDINGS = [
        Binding("ctrl+q", "quit", "Quit"),
    ]

    def __init__(self) -> None:
        super().__init__()
        self.driver = None
        self.log_queue: queue.Queue[str] = queue.Queue()
        self.attack_options: dict[str, Any] = {}

    def on_mount(self) -> None:
        self.push_screen(SetupScreen())

    def _release_driver(self) -> None:
        if getattr(self, "driver", None) is None:
            return
        try:
            self.driver.quit()
        except Exception:
            pass
        self.driver = None

    def action_quit(self) -> None:
        self._release_driver()
        self.exit()


def run_tui() -> None:
    app = CocAutomatorApp()
    try:
        app.run()
    finally:
        app._release_driver()


if __name__ == "__main__":
    run_tui()
