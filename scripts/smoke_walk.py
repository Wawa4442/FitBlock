"""Exercise the real walking screen and Android sensor callbacks on the disposable AVD."""
import math
import time

from smoke_android import QA, adb, capture, click, find, seek, ui


def replace_goal(value):
    field = find(cls="android.widget.EditText")
    click(field)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * len(field.get("text"))))
    adb("shell", "input", "text", str(value))
    click(find(text="Guardar"))


def simulate_steps(count):
    started = time.monotonic()
    sample_period = 0.065
    samples_per_step = 8
    for sample in range(count * samples_per_step):
        phase = sample * 2 * math.pi / samples_per_step
        acceleration = 9.81 + 2.0 * math.sin(phase)
        assert "OK" in adb("emu", "sensor", "set", "acceleration", f"0:{acceleration:.3f}:0")
        time.sleep(max(0, started + (sample + 1) * sample_period - time.monotonic()))
    adb("emu", "sensor", "set", "acceleration", "0:9.81:0")


def enable_activity_permission():
    for node in ui().iter("node"):
        if node.get("resource-id", "").endswith("permission_allow_button"):
            click(node)
            return
    raise AssertionError("Activity permission prompt did not offer the Android Allow button")


if __name__ == "__main__":
    assert adb("emu", "avd", "name").splitlines()[0] == "FitBlock_Test_API35"
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "pm", "clear", "com.fitblock.app")
    adb("shell", "am", "start", "-n", "com.fitblock.app/.MainActivity")
    click(seek("He leído y acepto"))
    click(seek("Comenzar"))
    click(find(desc="Ajustes"))
    click(seek("Configurar permisos"))
    assert find(text="Permisos de FitBlock") is not None
    capture("08-permisos.png")
    print("PASS: permission setup screen", flush=True)
    click(find(desc="Moverme"))
    click(seek("Iniciar caminata"))
    assert find(text="0 / 100") is not None
    print("PASS: configurable goal and enforced minimum", flush=True)
    click(find(text="Cambiar meta"))
    replace_goal(120)
    assert find(text="0 / 120") is not None
    click(find(text="Cambiar meta"))
    replace_goal(99)
    assert find(text="Guardar") is not None, "99 steps must be rejected"
    click(find(text="Usar mínimo de 100"))
    click(find(text="Guardar"))
    assert find(text="0 / 100") is not None
    click(find(text="Iniciar caminata"))
    enable_activity_permission()
    assert find(text="Pausar caminata") is not None
    adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
    time.sleep(1)
    assert "mWakefulness=Asleep" in adb("shell", "dumpsys", "power")
    assert "isForeground=true" in adb("shell", "dumpsys", "activity", "services", "com.fitblock.app/.WalkService")
    try:
        simulate_steps(14)
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "wm", "dismiss-keyguard")
        click(find(text="Pausar caminata"))
        paused = next(node.get("text") for node in ui().iter("node") if node.get("text", "").endswith(" / 100"))
        steps = int(paused.split("/")[0])
        assert 10 <= steps <= 14, paused
        assert find(text="Reanudar caminata") is not None
        capture("09-caminata-pausada.png")
        print(f"PASS: {steps} steps recorded with screen off and pause", flush=True)
        click(find(text="Reanudar caminata"))
        adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
        simulate_steps(104 - steps)
        assert "mWakefulness=Asleep" in adb("shell", "dumpsys", "power")
    finally:
        adb("emu", "sensor", "set", "acceleration", "0:9.81:0")
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "wm", "dismiss-keyguard")
    assert find(text="100 / 100") is not None
    assert find(text="Ganaste 10 minutos") is not None
    assert "isForeground=true" not in adb("shell", "dumpsys", "activity", "services", "com.fitblock.app/.WalkService")
    capture("10-caminata-completada.png")
    result = f"PASS: runtime permission, goal min/max UI, {steps} steps recorded with screen off, pause and resume, completion and 10-minute award while screen off."
    (QA / "walk-smoke-results.txt").write_text(result + "\n", encoding="utf-8")
    print(result)
