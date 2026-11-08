#!/usr/bin/env python3
"""
Push2find device simulator.

Turns this computer into a Push2find device over Bluetooth Low Energy, using
the protocol in PROTOCOL.md. The phone app pairs with it exactly as it would with
the real device.

    pip install -r requirements.txt && pip install --no-deps bless==0.2.6
    python wristband_sim.py          # window with a big button
    python wristband_sim.py --cli    # terminal only

Works on Windows 10/11, macOS and Linux (BlueZ). The computer's Bluetooth
adapter must support the LE peripheral role (most built-in adapters do).
"""
import argparse
import asyncio
import queue
import random
import sys
import threading
import time

import traceback

FROZEN = getattr(sys, "frozen", False)  # running as the packaged .exe / .app


def fatal(message: str):
    """Report a startup error. A packaged windowed app has no console, so show a dialog."""
    if sys.stderr:
        print(message, file=sys.stderr)
    try:
        import tkinter as tk
        from tkinter import messagebox
        root = tk.Tk()
        root.withdraw()
        messagebox.showerror("Push2find simulator", message)
        root.destroy()
    except Exception:
        pass
    sys.exit(1)


def selftest_path():
    """`--selftest FILE`: used by CI to check the packaged app starts and imports its BLE stack."""
    if "--selftest" in sys.argv:
        i = sys.argv.index("--selftest")
        return sys.argv[i + 1] if i + 1 < len(sys.argv) else "selftest.txt"
    return None


try:
    from bless import (
        BlessServer,
        GATTAttributePermissions,
        GATTCharacteristicProperties,
    )
except Exception:
    err = traceback.format_exc()
    if selftest_path():
        open(selftest_path(), "w").write("FAIL\n" + err)
        sys.exit(1)
    fatal("Could not load the Bluetooth library.\n\n" + err[-1500:]
          + ("" if FROZEN else "\nSee simulator/README.md for install instructions."))

if selftest_path():
    open(selftest_path(), "w").write("OK\n")
    sys.exit(0)

# ---- protocol (keep in sync with PROTOCOL.md / P2FProtocol.kt / P2FProtocol.swift)
SERVICE = "7a1e0001-5c3d-4b8e-9f2a-1d6c0b7e9a10"
BUTTON_EVENT = "7a1e0002-5c3d-4b8e-9f2a-1d6c0b7e9a10"
CONTROL = "7a1e0003-5c3d-4b8e-9f2a-1d6c0b7e9a10"
BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb"
BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"

EVENT_SINGLE, EVENT_DOUBLE, EVENT_LONG = 0x01, 0x02, 0x03
EVENT_NAMES = {EVENT_SINGLE: "single press", EVENT_DOUBLE: "double press", EVENT_LONG: "long press"}
CMD_IDENTIFY = 0x01

DEVICE_NAME = "P2F-SIM"


class SimulatedBand:
    """BLE GATT server. All BLE work runs on a private asyncio loop in a background thread."""

    def __init__(self, events: "queue.Queue[tuple[str, str]]"):
        self.events = events  # (kind, message) → UI thread
        self.loop = asyncio.new_event_loop()
        self.thread = threading.Thread(target=self.loop.run_forever, daemon=True)
        self.server = None
        self.seq = random.randint(0, 255)
        self.battery = 87
        self.has_battery = False

    # ------------------------------------------------------------ lifecycle

    def start(self):
        self.thread.start()
        return asyncio.run_coroutine_threadsafe(self._start(), self.loop)

    def stop(self):
        if self.server is not None:
            fut = asyncio.run_coroutine_threadsafe(self.server.stop(), self.loop)
            try:
                fut.result(timeout=5)
            except Exception:
                pass
        self.loop.call_soon_threadsafe(self.loop.stop)

    async def _start(self):
        self.server = BlessServer(name=DEVICE_NAME, loop=self.loop)
        self.server.read_request_func = self._on_read
        self.server.write_request_func = self._on_write

        P, A = GATTCharacteristicProperties, GATTAttributePermissions
        await self.server.add_new_service(SERVICE)
        await self.server.add_new_characteristic(
            SERVICE, BUTTON_EVENT, P.read | P.notify, bytearray(b"\x00\x00"), A.readable)
        await self.server.add_new_characteristic(
            SERVICE, CONTROL, P.write | P.write_without_response, bytearray(b"\x00"), A.writeable)

        try:
            await self.server.add_new_service(BATTERY_SERVICE)
            await self.server.add_new_characteristic(
                BATTERY_SERVICE, BATTERY_LEVEL, P.read | P.notify, bytearray([self.battery]), A.readable)
            self.has_battery = True
        except Exception as e:  # some OSes reserve standard services
            self._emit("warn", f"Battery service not available on this OS ({e}); battery will show '—' in the app.")

        await self.server.start()
        self._emit("ok", f"Advertising as {DEVICE_NAME}. In the app: Pair device → tap it.")

    # ------------------------------------------------------------ actions (any thread)

    def press(self, event: int = EVENT_SINGLE):
        self.loop.call_soon_threadsafe(self._press, event)

    def set_battery(self, level: int):
        self.loop.call_soon_threadsafe(self._set_battery, max(0, min(100, int(level))))

    # ------------------------------------------------------------ loop thread

    def _press(self, event: int):
        if self.server is None:
            self._emit("warn", "Not advertising yet.")
            return
        self.seq = (self.seq + 1) & 0xFF
        self._notify(SERVICE, BUTTON_EVENT, bytes([event, self.seq]))
        self._emit("press", f"Sent {EVENT_NAMES[event]}  [{event:02X} {self.seq:02X}]")

    def _set_battery(self, level: int):
        self.battery = level
        if self.server is not None and self.has_battery:
            self._notify(BATTERY_SERVICE, BATTERY_LEVEL, bytes([level]))
            self._emit("info", f"Battery → {level}%")

    def _notify(self, service: str, char: str, value: bytes):
        c = self.server.get_characteristic(char)
        if c is None:
            return
        c.value = bytearray(value)
        try:
            self.server.update_value(service, char)
        except Exception as e:
            self._emit("warn", f"Notify failed: {e}")

    # ------------------------------------------------------------ GATT callbacks

    def _on_read(self, characteristic, **kwargs):
        if str(characteristic.uuid).lower() == BATTERY_LEVEL:
            self._emit("info", f"Phone read battery ({self.battery}%)")
            return bytearray([self.battery])
        return characteristic.value

    def _on_write(self, characteristic, value, **kwargs):
        characteristic.value = value
        if str(characteristic.uuid).lower() == CONTROL and value and value[0] == CMD_IDENTIFY:
            self._emit("identify", "Phone sent IDENTIFY – the device would blink now")
        else:
            self._emit("info", f"Write to {characteristic.uuid}: {bytes(value).hex(' ')}")

    def _emit(self, kind: str, msg: str):
        self.events.put((kind, f"{time.strftime('%H:%M:%S')}  {msg}"))


# ====================================================================== GUI

def run_gui(band: SimulatedBand, events: "queue.Queue[tuple[str, str]]"):
    import tkinter as tk

    BG, CARD, INK, MUTED = "#0E0F12", "#17191D", "#F6F5F2", "#8A8F98"
    SIGNAL, SIGNAL_DOWN, GREEN, AMBER = "#FF5B1F", "#C9420F", "#1E9E5A", "#E39B12"

    root = tk.Tk()
    root.title("Push2find – device simulator")
    root.configure(bg=BG)
    root.minsize(380, 600)

    status = tk.Label(root, text="Starting Bluetooth…", bg=BG, fg=AMBER, font=("Helvetica", 12, "bold"))
    status.pack(pady=(18, 4))
    tk.Label(root, text="Click the button or press Space", bg=BG, fg=MUTED, font=("Helvetica", 11)).pack()

    size = 240
    canvas = tk.Canvas(root, width=size, height=size, bg=BG, highlightthickness=0, cursor="hand2")
    canvas.pack(pady=18)
    ring = canvas.create_oval(8, 8, size - 8, size - 8, outline="#2A2D33", width=10)
    btn = canvas.create_oval(34, 34, size - 34, size - 34, fill=SIGNAL, outline="")
    label = canvas.create_text(size / 2, size / 2, text="PRESS", fill="white", font=("Helvetica", 26, "bold"))

    def flash_press():
        canvas.itemconfig(btn, fill=SIGNAL_DOWN)
        canvas.itemconfig(ring, outline=SIGNAL)
        root.after(160, lambda: (canvas.itemconfig(btn, fill=SIGNAL), canvas.itemconfig(ring, outline="#2A2D33")))

    def do_press(event=EVENT_SINGLE):
        band.press(event)
        flash_press()

    for item in (btn, label):
        canvas.tag_bind(item, "<Button-1>", lambda e: do_press())
    root.bind("<space>", lambda e: do_press())

    row = tk.Frame(root, bg=BG)
    row.pack()
    for text, ev in (("Double press", EVENT_DOUBLE), ("Long press", EVENT_LONG)):
        tk.Button(row, text=text, command=lambda ev=ev: do_press(ev), bg=CARD, fg=INK,
                  activebackground="#22252B", activeforeground=INK, relief="flat",
                  padx=14, pady=6, highlightthickness=0).pack(side="left", padx=6)

    bat_frame = tk.Frame(root, bg=BG)
    bat_frame.pack(fill="x", padx=24, pady=(18, 6))
    tk.Label(bat_frame, text="Device battery", bg=BG, fg=INK, font=("Helvetica", 11)).pack(anchor="w")
    battery = tk.Scale(bat_frame, from_=0, to=100, orient="horizontal", bg=BG, fg=INK,
                       troughcolor=CARD, highlightthickness=0, sliderrelief="flat", length=300)
    battery.set(band.battery)
    battery.pack(fill="x")
    battery.bind("<ButtonRelease-1>", lambda e: band.set_battery(battery.get()))

    log = tk.Text(root, height=9, bg=CARD, fg=INK, relief="flat", font=("Menlo", 10),
                  padx=10, pady=8, highlightthickness=0, wrap="word")
    log.pack(fill="both", expand=True, padx=16, pady=(8, 16))
    log.tag_config("warn", foreground=AMBER)
    log.tag_config("ok", foreground=GREEN)
    log.tag_config("press", foreground=SIGNAL)
    log.tag_config("identify", foreground="#7CC4FF")
    log.configure(state="disabled")

    def blink(n=6):
        if n <= 0:
            canvas.itemconfig(ring, outline="#2A2D33")
            return
        canvas.itemconfig(ring, outline="#7CC4FF" if n % 2 else "#2A2D33")
        root.after(220, lambda: blink(n - 1))

    def pump():
        try:
            while True:
                kind, msg = events.get_nowait()
                log.configure(state="normal")
                log.insert("end", msg + "\n", kind)
                log.see("end")
                log.configure(state="disabled")
                if kind == "ok":
                    status.config(text=f"● Advertising as {DEVICE_NAME}", fg=GREEN)
                elif kind == "error":
                    status.config(text="Bluetooth error – see log", fg="#FF5A6E")
                elif kind == "identify":
                    blink()
        except queue.Empty:
            pass
        root.after(100, pump)

    def on_close():
        band.stop()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)
    pump()
    root.mainloop()


# ====================================================================== CLI

def run_cli(band: SimulatedBand, events: "queue.Queue[tuple[str, str]]"):
    def printer():
        while True:
            _, msg = events.get()
            print("\r" + msg)

    threading.Thread(target=printer, daemon=True).start()
    print("Commands:  Enter = press | d = double | l = long | b 40 = battery 40% | q = quit")
    try:
        while True:
            cmd = input().strip().lower()
            if cmd == "":
                band.press(EVENT_SINGLE)
            elif cmd == "d":
                band.press(EVENT_DOUBLE)
            elif cmd == "l":
                band.press(EVENT_LONG)
            elif cmd.startswith("b"):
                try:
                    band.set_battery(int(cmd[1:].strip()))
                except ValueError:
                    print("Usage: b 0-100")
            elif cmd in ("q", "quit", "exit"):
                break
    except (KeyboardInterrupt, EOFError):
        pass
    band.stop()


def main():
    ap = argparse.ArgumentParser(description="Push2find device simulator")
    ap.add_argument("--cli", action="store_true", help="terminal mode, no window")
    args = ap.parse_args()

    events: "queue.Queue[tuple[str, str]]" = queue.Queue()
    band = SimulatedBand(events)
    fut = band.start()
    fut.add_done_callback(
        lambda f: f.exception() and events.put(("error", f"Could not start Bluetooth: {f.exception()!r}")))

    if not args.cli:
        try:
            import tkinter  # noqa: F401
        except ImportError:
            print("tkinter not available – falling back to terminal mode.")
            args.cli = True
    run_cli(band, events) if args.cli else run_gui(band, events)


if __name__ == "__main__":
    main()
