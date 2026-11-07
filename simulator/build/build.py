"""Builds the simulator into a single-click app with PyInstaller (run by CI)."""
import importlib.util, os, subprocess, sys

here = os.path.dirname(os.path.abspath(__file__))
src = os.path.join(here, "..", "wristband_sim.py")
args = [sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--windowed",
        "--name", "Push2find-Simulator", "--distpath", os.path.join(here, "..", "dist"),
        "--workpath", os.path.join(here, "out")]
if sys.platform == "darwin":
    args += ["--osx-bundle-identifier", "com.push2find.simulator"]
else:
    args += ["--onefile"]
for pkg in ("bless", "bleak", "winrt", "bleak_winrt", "dbus_next", "CoreBluetooth", "libdispatch"):
    if importlib.util.find_spec(pkg) is not None:
        args += ["--collect-all", pkg]
args.append(src)
print(" ".join(args), flush=True)
subprocess.check_call(args)
