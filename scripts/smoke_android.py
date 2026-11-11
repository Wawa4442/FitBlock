"""UI smoke checks on the disposable FitBlock emulator, never on a physical phone."""
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
import re

ROOT = Path(__file__).resolve().parents[1]
ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL = "emulator-5554"
QA = ROOT / "docs/qa"
QA.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.run([str(ADB), "-s", SERIAL, *args], check=True, capture_output=True, text=True, encoding="utf-8", errors="replace").stdout

def ui():
    adb("shell", "uiautomator", "dump", "/sdcard/fitblock-smoke.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/fitblock-smoke.xml"))

def find(text=None, desc=None, cls=None):
    for node in ui().iter("node"):
        if (text is None or text.lower() in node.get("text", "").lower()) and (desc is None or desc in node.get("content-desc", "")) and (cls is None or node.get("class") == cls):
            return node
    return None

def click(node):
    assert node is not None, "Expected UI node is absent"
    x1,y1,x2,y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.7)

def seek(text):
    for _ in range(7):
        node = find(text=text)
        if node is not None:
            return node
        adb("shell", "input", "swipe", "540", "1850", "540", "650", "350")
    raise AssertionError(f"Cannot find {text}")

def capture(name):
    adb("shell", "screencap", "-p", "/sdcard/fitblock-smoke.png")
    adb("pull", "/sdcard/fitblock-smoke.png", str(QA / name))

if __name__ == "__main__":
    assert SERIAL.startswith("emulator-"), "This test may only run on an emulator"
    adb("install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
    adb("shell", "pm", "clear", "com.fitblock.app")
    adb("shell", "am", "start", "-n", "com.fitblock.app/.MainActivity")
    time.sleep(2)
    capture("01-bienvenida.png")
    click(seek("He leído y acepto"))
    click(seek("Comenzar"))
    assert find(text="Tú pones el límite.") is not None
    click(seek("Chrome"))
    capture("02-aplicaciones.png")
    # The test emulator is disposable; enabling a service here is not a product bypass.
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", "com.fitblock.app/.BlockService")
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
    adb("shell", "appops", "set", "com.fitblock.app", "GET_USAGE_STATS", "allow")
    click(find(cls="android.widget.Switch"))
    time.sleep(2)
    if find(text="Ahora no") is not None:
        click(find(text="Ahora no"))
    adb("shell", "am", "start", "-n", "com.fitblock.app/.MainActivity")
    click(find(desc="Inicio"))
    capture("03-inicio.png")
    click(find(desc="Moverme"))
    assert find(text="Actívate. Desbloquea.") is not None, "Workout chooser must open from overlay"
    capture("05-actividades.png")
    click(find(desc="Progreso"))
    assert find(text="Cada pausa cuenta.") is not None
    capture("06-progreso.png")
    (QA / "smoke-results.txt").write_text("PASS: onboarding, app selection, navigation and statistics. Blocking/emergency are tested by BlockingFlowTest because shell uiautomator dump suspends accessibility services.\n", encoding="utf-8")
    print("PASS: onboarding, app selection, navigation, statistics")
