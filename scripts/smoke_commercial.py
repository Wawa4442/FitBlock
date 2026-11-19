"""Commercial-demo UI and walk recovery QA; disposable AVD only."""
import json
import time
import xml.etree.ElementTree as ET

from smoke_android import ROOT, QA, adb, capture, click, find, ui
from smoke_walk import simulate_steps


def seek(text):
    for _ in range(16):
        node = find(text=text)
        if node is not None:
            return node
        # Scroll outside the interactive map so this moves the page.
        adb("shell", "input", "swipe", "25", "1920", "25", "700", "280")
    raise AssertionError(f"Cannot find {text}")


def preferences():
    raw = adb("shell", "run-as", "com.fitblock.app", "cat", "shared_prefs/fitblock_private.xml")
    return {item.get("name"): item.text or item.get("value") for item in ET.fromstring(raw)}


def count():
    return next(int(node.get("text").split("/")[0]) for node in ui().iter("node")
                if node.get("text", "").endswith(" / 100"))


if __name__ == "__main__":
    assert adb("emu", "avd", "name").splitlines()[0] == "FitBlock_Test_API35"
    adb("install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
    adb("shell", "pm", "clear", "com.fitblock.app")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "am", "start", "-n", "com.fitblock.app/.MainActivity")
    time.sleep(2)
    click(seek("He leído y acepto")); click(seek("Comenzar"))
    click(find(desc="Inicio"))
    assert find(text="Demo Comercial") is not None
    capture("demo-01-inicio.png")
    click(seek("ver demostración"))
    click(seek("Encuadrar"))
    time.sleep(7)
    capture("demo-02-mapa-ejemplo.png")
    assert "records" not in preferences(), "Sample tour must not create real records"
    click(seek("Probar con mis datos"))
    click(seek("Iniciar caminata"))
    capture("demo-03-caminata.png")
    click(seek("Activar ruta GPS")); click(find(text="Ahora no"))
    # Sensor-only session tests permission independence and local checkpoints.
    adb("shell", "pm", "grant", "com.fitblock.app", "android.permission.ACTIVITY_RECOGNITION")
    click(seek("Iniciar caminata"))
    adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
    simulate_steps(20)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    click(seek("Pausar caminata"))
    steps = count()
    assert 16 <= steps <= 20, steps
    checkpoint = json.loads(preferences()["pendingWalk"])
    assert checkpoint["steps"] == steps
    capture("demo-04-pausa.png")
    adb("shell", "am", "force-stop", "com.fitblock.app")
    adb("shell", "am", "start", "-n", "com.fitblock.app/.MainActivity")
    time.sleep(2)
    click(seek("Ver caminata"))
    assert count() == steps
    assert find(text="Caminata recuperada") is not None
    capture("demo-05-recuperada.png")
    click(seek("Guardar caminata parcial"))
    prefs = preferences()
    assert "pendingWalk" not in prefs
    records = json.loads(prefs["records"])
    assert len(records) == 1 and records[0]["amount"] == steps and not records[0]["goalMet"]
    assert int(prefs.get("balance", 0)) == 0
    click(find(desc="Progreso"))
    for period in ("Día", "Semana", "Mes"):
        click(find(text=period))
        assert find(text=f"{steps} pasos") is not None
    capture("demo-06-progreso.png")
    click(seek("Historial de actividades"))
    assert find(text="Registro parcial") is not None
    capture("demo-07-historial.png")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    click(find(desc="Moverme")); click(seek("Iniciar caminata"))
    click(seek("Activar ruta GPS")); click(find(text="Activar ruta"))
    allow = next((node for node in ui().iter("node") if node.get("resource-id", "").endswith("permission_allow_foreground_only_button")), None)
    assert allow is not None, "Expected Android foreground-location permission prompt"
    click(allow)
    click(seek("Iniciar caminata"))
    time.sleep(5)
    services = adb("shell", "dumpsys", "activity", "services", "com.fitblock.app/.WalkService")
    assert "isForeground=true" in services
    assert "Pausar caminata" in [node.get("text") for node in ui().iter("node")]
    click(seek("Pausar caminata"))
    click(seek("Guardar caminata parcial"))
    result = f"PASS: commercial banner, read-only sample tour, map rendering, optional GPS consent, {steps} screen-off steps, pause, process-death recovery, partial save without reward, day/week/month statistics and history."
    (QA / "commercial-smoke-results.txt").write_text(result + "\n", encoding="utf-8")
    print(result, flush=True)
