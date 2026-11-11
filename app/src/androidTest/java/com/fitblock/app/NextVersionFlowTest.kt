package com.fitblock.app

import android.app.UiAutomation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real UI actions; observes live accessibility nodes without waiting for an idle stopwatch. */
@RunWith(AndroidJUnit4::class)
class NextVersionFlowTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val automation = inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun shell(command: String) {
        automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
    private fun find(root: AccessibilityNodeInfo? = automation.rootInActiveWindow,
                     predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isVisibleToUser && predicate(root)) return root
        for (index in 0 until root.childCount) find(root.getChild(index), predicate)?.let { return it }
        return null
    }
    private fun node(text: String, exact: Boolean = true): AccessibilityNodeInfo? = find {
        val value = it.text?.toString().orEmpty()
        if (exact) value.equals(text, ignoreCase = true) else value.contains(text, ignoreCase = true)
    }
    private fun waitFor(text: String, exact: Boolean = true): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 12_000
        while (SystemClock.elapsedRealtime() < deadline) {
            node(text, exact)?.let { return it }
            Thread.sleep(150)
        }
        throw AssertionError("Missing UI text: $text")
    }
    private fun click(text: String) {
        assertTrue(waitFor(text).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(400)
    }
    private fun description(text: String) {
        val target = find { it.contentDescription?.toString() == text }
        assertNotNull("Missing description: $text", target)
        assertTrue(target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(400)
    }
    private fun input(text: String, value: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        assertTrue(waitFor(text).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
    }
    private fun seek(text: String) {
        for (attempt in 0..8) {
            if (node(text) != null) return
            find { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(250)
        }
        fail("Missing scrollable UI text: $text")
    }
    private fun top() {
        repeat(6) {
            find { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            Thread.sleep(100)
        }
    }
    private fun back() { shell("input keyevent KEYCODE_BACK"); Thread.sleep(500) }
    private fun screenshot(name: String) {
        // A new accessibility tree can arrive before the activity transition is drawn.
        Thread.sleep(600)
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FitBlockQA03")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use {
            assertTrue(automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun accountSportsTimerStreakTeamPreviewAwardsAndHistory() {
        val store = Store.get(context)
        store.clear()
        store.consent = true
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            click("Mi cuenta")
            screenshot("v03-01-crear-cuenta.png")
            input("Nombre de usuario", "Persona QA")
            input("Correo electrónico", "fitblock.qa@example.test")
            input("Contraseña", "Example-1234")
            input("Repite la contraseña", "Example-1234")
            seek("Crear cuenta"); click("Crear cuenta")
            waitFor("Cuenta local en este teléfono")
            screenshot("v03-02-cuenta.png")
            back()
            description("Moverme")
            seek("Buscar deporte o actividad")
            input("Buscar deporte o actividad", "Yoga")
            waitFor("1 actividad")
            seek("Yoga")
            description("Favorito: Yoga")
            assertEquals(setOf("yoga"), store.favoriteSports())
            screenshot("v03-03-deportes.png")
            description("Abrir Yoga")
            click("Cambiar duración")
            input("10", "1")
            click("Guardar")
            waitFor("Meta de 1 minuto")
            click("Iniciar")
            click("Pausar")
            waitFor("Sesión pausada")
            val paused = SportTimer.get(context).current()!!
            Thread.sleep(1200)
            assertEquals(paused.accumulated, SportTimer.get(context).current()!!.elapsed(SystemClock.elapsedRealtime()))
            click("Reanudar")
            shell("input keyevent KEYCODE_SLEEP")
            Thread.sleep(61_000)
            assertTrue(SportTimer.get(context).current()!!.elapsed(SystemClock.elapsedRealtime()) >= 60_000)
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            waitFor("Meta alcanzada", false)
            screenshot("v03-04-sesion.png")
            click("Guardar actividad")
            description("Progreso")
            click("Rachas y premios")
            waitFor("1 día")
            screenshot("v03-05-rachas.png")
            seek("Preparar mi reto"); click("Preparar mi reto")
            input("Mi equipo", "Equipo QA")
            click("Guardar plan")
            seek("Muy pronto")
            assertTrue(store.profilePreferences().getString("groupDraft", "")!!.contains("Equipo QA"))
            screenshot("v03-06-equipo.png")
            top(); click("Premios")
            seek("Añadir a mi colección"); click("Añadir a mi colección")
            waitFor("✓ En tu colección")
            assertEquals(setOf("first"), AwardCatalog.claimed(store))
            screenshot("v03-07-premios.png")
            top(); click("Historial")
            waitFor("Yoga")
            waitFor("Meta de duración completada")
            assertEquals(0L, store.balance())
            assertEquals(1, store.records().length())
            screenshot("v03-08-historial.png")
            back(); click("Mi cuenta")
            seek("Cerrar sesión"); click("Cerrar sesión")
            assertNull(Accounts.get(context).current())
            click("Mi cuenta")
            seek("Ya tengo cuenta"); click("Ya tengo cuenta")
            input("Correo electrónico", "fitblock.qa@example.test")
            input("Contraseña", "Example-1234")
            click("Entrar")
            waitFor("1 actividad completada")
            click("Ver mi historial")
            waitFor("Yoga")
            assertEquals(1, store.totalActivities())
            assertEquals(setOf("yoga"), store.favoriteSports())
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("input keyevent KEYCODE_HOME")
            store.clear()
        }
    }
}
