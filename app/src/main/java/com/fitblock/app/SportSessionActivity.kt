package com.fitblock.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class SportSessionActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var timer: SportTimer
    private lateinit var sport: Sport
    private lateinit var elapsed: TextView
    private lateinit var goal: TextView
    private lateinit var state: TextView
    private lateinit var progress: ProgressBar
    private lateinit var start: Button
    private lateinit var finish: Button
    private lateinit var discard: Button
    private lateinit var edit: Button
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store.get(this)
        timer = SportTimer.get(this)
        val selected = timer.current()?.sportId ?: intent.getStringExtra("sport")
        val entry = selected?.let(SportCatalog::get)
        if (!store.consent || entry == null || entry.exercise != null) {
            finish()
            return
        }
        sport = entry
        val body = column(22)
        body.button("‹ Volver", false) { finish() }
        body.gap(14)
        body.text(sport.title, 28f, bold = true)
        body.text(sport.category.title, 13f, C.cyan)
        body.gap(16)
        body.card {
            elapsed = text("00:00", 42f, bold = true).apply { gravity = Gravity.CENTER }
            goal = text("", 14f, C.green).apply { gravity = Gravity.CENTER }
            gap(12)
            progress = ProgressBar(this@SportSessionActivity, null, android.R.attr.progressBarStyleHorizontal)
            addView(progress)
            gap(12)
            state = text("", 14f, C.muted)
        }
        edit = body.button("Cambiar duración", false) { editGoal() }
        start = body.button("Iniciar") {
            try {
                when (val current = timer.current()) {
                    null -> timer.begin(sport, store.sportGoal(sport.id))
                    else -> if (current.running) timer.pause() else timer.resume()
                }
                update()
            } catch (error: IllegalStateException) {
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
        }
        finish = body.button("Guardar actividad", false) { save() }
        discard = body.button("Descartar sesión", false) {
            AlertDialog.Builder(this).setTitle("¿Descartar la sesión?")
                .setNegativeButton("Continuar", null)
                .setPositiveButton("Descartar") { _, _ -> timer.discard(); finish() }.show()
        }
        body.gap(12)
        body.text("Registro por tiempo", 15f, bold = true)
        body.text("El cronómetro continúa al apagar la pantalla. Guarda la sesión cuando termines. Los minutos para desbloquear apps se ganan con las actividades verificadas por cámara o pasos.", 13f, C.muted)
        setFitContent(body)
        update()
    }

    override fun onResume() { super.onResume(); if (::elapsed.isInitialized) handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun update() {
        val current = timer.current()
        val millis = current?.elapsed(SystemClock.elapsedRealtime()) ?: 0
        val seconds = millis / 1000
        val timeText = "%02d:%02d".format(seconds / 60, seconds % 60)
        if (elapsed.text.toString() != timeText) elapsed.text = timeText
        val target = current?.goalMillis ?: store.sportGoal(sport.id) * 60_000L
        val goalText = "Meta de ${quantity((target / 60_000).toInt(), "minuto", "minutos") }"
        if (goal.text.toString() != goalText) goal.text = goalText
        progress.max = (target / 1000).toInt()
        progress.progress = minOf(seconds, progress.max.toLong()).toInt()
        val stateText = when {
            current == null -> "Elige tu meta y comienza cuando estés listo."
            millis >= target -> "Meta alcanzada. Guarda tu actividad para sumarla a tu racha."
            current.running -> "Sesión en curso"
            else -> "Sesión pausada"
        }
        if (state.text.toString() != stateText) state.text = stateText
        val startText = when {
            current == null -> "Iniciar"
            current.running -> "Pausar"
            else -> "Reanudar"
        }
        if (start.text.toString() != startText) start.text = startText
        edit.visibility = if (current == null) View.VISIBLE else View.GONE
        finish.visibility = if (current != null) View.VISIBLE else View.GONE
        finish.isEnabled = millis >= 1000
        discard.visibility = finish.visibility
    }

    private fun editGoal() {
        val input = input("Minutos", store.sportGoal(sport.id).toString(), true)
        val dialog = AlertDialog.Builder(this).setTitle("Duración de la actividad")
            .setView(column(20).apply { text("Elige entre 1 y 300 minutos.", 13f, C.muted); gap(8); addView(input) })
            .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutes = input.text.toString().toIntOrNull()
                if (minutes == null || minutes !in 1..300) input.error = "Elige entre 1 y 300 minutos."
                else { store.setSportGoal(sport.id, minutes); dialog.dismiss(); update() }
            }
        }
        dialog.show()
    }

    private fun save() {
        try {
            if (timer.finish()) {
                Toast.makeText(this, "Actividad guardada en tu historial.", Toast.LENGTH_LONG).show()
                finish()
            } else Toast.makeText(this, "No se pudo guardar la actividad. Intenta de nuevo.", Toast.LENGTH_LONG).show()
        } catch (error: IllegalStateException) {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
        }
    }
}
