package com.fitblock.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*

class BlockService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private var current = ""
    private var overlay: View? = null
    private var lastTick = 0L
    private var lastCount = ""
    private var lastRecovery = 0L
    private lateinit var protected: Set<String>
    private var inputMethods: Set<String> = emptySet()
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { current = ""; lastTick = SystemClock.elapsedRealtime(); removeOverlay() }
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val interactive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive && !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
            if (!interactive) { current = ""; removeOverlay() }
            if (interactive && current.isEmpty() && now-lastRecovery>5000 && UsageAccess.allowed(this@BlockService)) {
                lastRecovery=now
                UsageAccess.foreground(this@BlockService)?.let { if(it !in protected)current=it }
            }
            if (current.isNotEmpty() && interactive) {
                if (overlay == null) store.consume(current, now-lastTick)
                if (store.blocked(current)) showOverlay() else removeOverlay()
            } else removeOverlay()
            lastTick = now; handler.postDelayed(this, 1000)
        }
    }
    override fun onServiceConnected() {
        store = Store.get(this); protected = AppCatalog.protected(this)
        inputMethods = (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).inputMethodList.map { it.packageName }.toSet()
        connected = true; lastTick = SystemClock.elapsedRealtime()
        UsageAccess.foreground(this)?.let { if(it !in protected)current=it }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver, filter)
        handler.post(tick)
        store.audit("Protección conectada", "Servicio de accesibilidad disponible.")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::store.isInitialized || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // A keyboard is a transient window over the same app, not a new foreground app.
        if (pkg in inputMethods) return
        // Overlay windows are owned by this package. Do not mistake them for foreground app changes.
        if (pkg == packageName && overlay != null && event.className?.toString()?.contains("MainActivity") != true && event.className?.toString()?.contains("WorkoutActivity") != true) return
        if (pkg != current) {
            if (overlay == null && current.isNotEmpty()) store.consume(current, SystemClock.elapsedRealtime()-lastTick)
            current = if (pkg in protected) "" else pkg
            lastTick = SystemClock.elapsedRealtime(); lastCount = ""
        }
        if (current.isNotEmpty() && store.blocked(current)) showOverlay() else removeOverlay()
    }
    private fun showOverlay() {
        if (overlay != null) return
        if (lastCount != current) { store.blockedAttempt(); lastCount = current }
        val body = column(28).apply {
            setBackgroundColor(C.bg); gravity = Gravity.CENTER
            val icon = ImageView(this@BlockService).apply { setImageResource(com.fitblock.app.R.drawable.fitblock_icon); contentDescription = "FitBlock" }
            addView(icon, LinearLayout.LayoutParams(dp(84), dp(84)).apply { gravity = Gravity.CENTER_HORIZONTAL }); gap(24)
            text("UNA PAUSA PARA TI", 12f, C.cyan, true).gravity = Gravity.CENTER
            gap(12); text("Gana tu pantalla,\nmuévete primero.", 28f, bold = true).gravity = Gravity.CENTER
            gap(12)
            val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(current, 0)).toString() }.getOrDefault("Esta aplicación")
            text(if (store.used(current) >= store.appLimit(current)*60_000L) "$appName alcanzó su límite diario. Estará disponible fuera de tu horario de protección o mañana." else "$appName está en pausa. Completa una actividad para ganar ${store.rewardMinutes} minutos de acceso.", 15f, C.muted).gravity = Gravity.CENTER
            gap(28)
            button("Elegir actividad") { launch("exercise") }
            button("Volver al inicio", false) { performGlobalAction(GLOBAL_ACTION_HOME); current = ""; removeOverlay() }
            gap(16)
            button("Necesito acceso de emergencia", false) { launch("emergency") }
            text("Teléfono, mensajes y ajustes siguen disponibles.", 12f, C.muted).gravity = Gravity.CENTER
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE)
        params.title = "FitBlock · Aplicación bloqueada"
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(scroll, params); overlay = scroll }
        catch (e: Exception) { android.util.Log.e("FitBlockGuard", "Could not show blocking screen", e); performGlobalAction(GLOBAL_ACTION_HOME) }
    }
    private fun launch(destination: String) {
        current = ""; removeOverlay()
        startActivity(Intent(this, MainActivity::class.java).putExtra("destination", destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
    private fun removeOverlay() { overlay?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }; overlay = null }
    override fun onInterrupt() { current = ""; removeOverlay() }
    override fun onDestroy() {
        connected = false; handler.removeCallbacksAndMessages(null); removeOverlay()
        runCatching { unregisterReceiver(screenReceiver) }; super.onDestroy()
    }
    companion object { @Volatile var connected = false; private set }
}
