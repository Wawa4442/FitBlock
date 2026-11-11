package com.fitblock.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class WalkingActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var goal: TextView
    private lateinit var count: TextView
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    private lateinit var start: Button
    private lateinit var edit: Button
    private lateinit var terminate: Button
    private var notificationButton: Button? = null
    private var permissionMessage: String? = null
    private val permissions = PermissionPrompt(this) { permission, granted ->
        if (permission == Manifest.permission.ACTIVITY_RECOGNITION) {
            permissionMessage = if (granted) null else "Autoriza Actividad física para contar pasos. Pulsa Iniciar para intentarlo de nuevo."
            if (granted) WalkService.start(this)
        }
        renderProgress()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            renderProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store.get(this)
        if (!store.consent) {
            finish()
            return
        }
        val body = column(22)
        body.button("‹ Volver", false) { finish() }
        body.gap(14)
        body.text("Caminata", 28f, bold = true)
        body.gap(8)
        goal = body.text("", 14f, C.green, true)
        body.gap(12)
        edit = body.button("Cambiar meta", false) {
            chooseGoal(Exercise.WALK, store.baseGoal(Exercise.WALK)) {
                store.setGoal(Exercise.WALK, it)
                renderProgress()
            }
        }
        body.card {
            count = text("", 38f, bold = true).apply { gravity = Gravity.CENTER }
            bar = ProgressBar(this@WalkingActivity, null, android.R.attr.progressBarStyleHorizontal)
            addView(bar)
            gap(12)
            status = text("", 14f, C.muted)
        }
        start = body.button("Iniciar caminata") { primaryAction() }
        terminate = body.button("Terminar caminata", false) {
            AlertDialog.Builder(this).setTitle("¿Terminar la caminata?")
                .setMessage("Los pasos de esta sesión se perderán si aún no alcanzaste la meta.")
                .setNegativeButton("Continuar", null)
                .setPositiveButton("Terminar") { _, _ -> WalkService.discard(this); finish() }
                .show()
        }
        if (Build.VERSION.SDK_INT >= 33) {
            notificationButton = body.button("Activar notificación de caminata", false) {
                permissions.request(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(C.bg)
            addView(body)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                }
                insets
            }
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
        renderProgress()
    }

    override fun onResume() {
        super.onResume()
        if (::goal.isInitialized) handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun primaryAction() {
        permissionMessage = null
        val session = WalkService.progress
        when (session?.phase) {
            WalkPhase.RUNNING -> WalkService.pause(this)
            WalkPhase.STARTING -> return
            WalkPhase.COMPLETE -> WalkService.discard(this)
            else -> {
                if (Build.VERSION.SDK_INT >= 29) permissions.request(Manifest.permission.ACTIVITY_RECOGNITION)
                else WalkService.start(this)
            }
        }
        renderProgress()
    }

    private fun renderProgress() {
        val session = WalkService.unfinished(this) ?: WalkService.progress?.takeIf { it.phase == WalkPhase.COMPLETE }
        val target = session?.ticket?.goal ?: store.requiredGoal(Exercise.WALK)
        val minutes = session?.ticket?.rewardMinutes ?: store.rewardMinutes
        val steps = session?.steps ?: 0
        goal.text = "Meta: $target pasos · +$minutes min"
        count.text = "$steps / $target"
        bar.max = target
        bar.progress = steps
        status.text = permissionMessage ?: session?.message ?:
            "Lleva el teléfono en el bolsillo. Al iniciar podrás apagar la pantalla y seguir caminando."
        edit.visibility = if (session == null) View.VISIBLE else View.GONE
        terminate.visibility = if (session != null && session.phase != WalkPhase.COMPLETE) View.VISIBLE else View.GONE
        start.isEnabled = session?.phase != WalkPhase.STARTING
        start.text = when (session?.phase) {
            WalkPhase.STARTING -> "Preparando sensores…"
            WalkPhase.RUNNING -> "Pausar caminata"
            WalkPhase.PAUSED -> "Reanudar caminata"
            WalkPhase.COMPLETE -> "Nueva caminata"
            null -> "Iniciar caminata"
        }
        notificationButton?.visibility = if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) View.VISIBLE else View.GONE
    }
}
