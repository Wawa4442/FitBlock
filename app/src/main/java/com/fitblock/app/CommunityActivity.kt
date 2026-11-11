package com.fitblock.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CommunityActivity : ComponentActivity() {
    private lateinit var store: Store
    private var tab = "streaks"
    private var historyLimit = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store.get(this)
        if (!store.consent) { finish(); return }
        tab = savedInstanceState?.getString("tab") ?: intent.getStringExtra("tab") ?: "streaks"
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", tab)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() { super.onResume(); if (::store.isInitialized) render() }

    private fun render() {
        val body = column(22)
        body.button("‹ Volver", false) { finish() }
        body.gap(12)
        body.text(when (tab) { "awards" -> "Tus premios"; "history" -> "Mi historial"; else -> "Tus rachas" }, 28f, bold = true)
        body.gap(14)
        val tabs = row()
        for ((id, title) in listOf("streaks" to "Rachas", "awards" to "Premios", "history" to "Historial")) {
            val button = action(title, id == tab) { tab = id; render() }
            tabs.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        }
        body.addView(tabs)
        body.gap(18)
        when (tab) {
            "awards" -> awards(body)
            "history" -> history(body)
            else -> streaks(body)
        }
        body.gap(20)
        setFitContent(body)
    }

    private fun streaks(body: LinearLayout) {
        body.card {
            text("Tu racha personal", 18f, bold = true)
            gap(8)
            text(quantity(store.streak(), "día", "días"), 36f, C.green, true)
            text("Mejor racha de ${quantity(store.bestStreak(), "día", "días")}", 13f, C.muted)
            gap(14)
            val days = store.activeDates()
            val row = row()
            for (offset in 6 downTo 0) {
                val date = LocalDate.now().minusDays(offset.toLong())
                val day = column().apply {
                    gravity = Gravity.CENTER
                    text(if (date in days) "✓" else "○", 24f, if (date in days) C.green else C.muted).gravity = Gravity.CENTER
                    text(date.format(DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("es"))).take(2), 11f, C.muted).gravity = Gravity.CENTER
                }
                row.addView(day, LinearLayout.LayoutParams(0, -2, 1f))
            }
            addView(row)
            gap(12)
            text("Completa al menos una meta al día. Las actividades guardadas antes de alcanzar la meta permanecen en tu historial.", 13f, C.muted)
        }
        body.text("Rachas en equipo", 23f, bold = true)
        body.gap(8)
        body.text("Vista previa", 12f, C.cyan, true)
        body.gap(12)
        val raw = store.profilePreferences().getString("groupDraft", null)
        val draft = raw?.let(::JSONObject)
        body.card {
            text(draft?.getString("name") ?: "Un objetivo compartido", 19f, bold = true)
            gap(8)
            text("Meta de ${draft?.optInt("days") ?: 7} días juntos", 14f, C.green)
            gap(16)
            val people = row()
            for (index in 0..2) {
                val person = column(8).apply {
                    gravity = Gravity.CENTER
                    text(if (index == 0) "Tú" else "+", 22f, if (index == 0) C.green else C.muted, true).gravity = Gravity.CENTER
                    text(if (index == 0) "Tu actividad" else "Por invitar", 11f, C.muted).gravity = Gravity.CENTER
                }
                people.addView(person, LinearLayout.LayoutParams(0, -2, 1f))
            }
            addView(people)
            gap(12)
            text("Cuando todos completen su meta diaria, el equipo sumará un día de racha.", 14f, C.muted)
            gap(12)
            button(if (draft == null) "Preparar mi reto" else "Editar mi reto", false) { editDraft() }
            text("Invitaciones y seguimiento con otras personas", 13f, C.muted)
            text("Muy pronto", 16f, C.cyan, true)
        }
    }

    private fun editDraft() {
        if (Accounts.get(this).current() == null) {
            AlertDialog.Builder(this).setTitle("Guarda tu reto en tu cuenta")
                .setMessage("Crea tu cuenta local para guardar el plan.")
                .setNegativeButton("Volver", null)
                .setPositiveButton("Mi cuenta") { _, _ -> startActivity(Intent(this, AccountActivity::class.java)) }.show()
            return
        }
        val draft = JSONObject(store.profilePreferences().getString("groupDraft", "{}")!!)
        val name = input("Nombre del equipo", draft.optString("name", "Mi equipo"))
        val days = input("Días del reto", draft.optInt("days", 7).toString(), true)
        val box = column(20).apply {
            addView(name)
            gap(10)
            text("Meta de 3 a 90 días", 13f, C.muted)
            gap(6)
            addView(days)
            gap(12)
            text("Guardarás un plan. Las invitaciones estarán disponibles muy pronto.", 13f, C.muted)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Prepara tu racha conjunta").setView(box)
            .setNegativeButton("Cancelar", null).setPositiveButton("Guardar plan", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                val target = days.text.toString().toIntOrNull()
                if (title.length !in 1..40 || target == null || target !in 3..90) {
                    Toast.makeText(this, "Escribe un nombre y una meta entre 3 y 90 días.", Toast.LENGTH_LONG).show()
                } else {
                    store.profilePreferences().edit().putString("groupDraft", JSONObject().put("name", title).put("days", target).toString()).commit()
                    dialog.dismiss()
                    render()
                }
            }
        }
        dialog.show()
    }

    private fun awards(body: LinearLayout) {
        val unlocked = AwardCatalog.refresh(store)
        val claimed = AwardCatalog.claimed(store)
        body.text("Tu colección · ${quantity(claimed.size, "insignia", "insignias")}", 16f, C.green, true)
        body.gap(8)
        body.text("Completa actividades y construye tu constancia para desbloquear insignias.", 14f, C.muted)
        body.gap(16)
        AwardCatalog.progress(store).forEach { award ->
            body.card {
                text(award.title, 18f, bold = true)
                gap(6)
                text(award.description, 13f, C.muted)
                gap(8)
                if (award.id in unlocked) {
                    if (award.id in claimed) text("✓ En tu colección", 14f, C.green, true)
                    else button("Añadir a mi colección") {
                        if (AwardCatalog.claim(store, award.id)) render()
                    }
                } else {
                    text("${award.current.coerceAtMost(award.target)} de ${award.target}", 13f, C.muted)
                    addView(ProgressBar(this@CommunityActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = (100 * award.current / award.target).toInt().coerceIn(0, 100)
                    })
                }
            }
        }
        body.card {
            text("Premios para seguir moviéndote", 20f, bold = true)
            gap(10)
            text("Personalización de FitBlock, retos de temporada y recompensas canjeables.", 14f, C.muted)
            gap(16)
            text("Muy pronto", 22f, C.cyan, true)
        }
    }

    private fun history(body: LinearLayout) {
        val user = Accounts.get(this).current()
        body.text(user?.name ?: "Historial de invitado", 16f, C.green, true)
        body.gap(8)
        if (user == null) body.button("Crear cuenta y asociar mi historial", false) {
            startActivity(Intent(this, AccountActivity::class.java))
        }
        val records = store.records()
        body.text(quantity(records.length(), "registro detallado", "registros detallados"), 13f, C.muted)
        body.gap(16)
        if (records.length() == 0) {
            body.card {
                text("Tu próxima actividad comienza el historial.", 17f, bold = true)
                gap(8)
                text("Las estadísticas de versiones anteriores siguen disponibles en Progreso.", 13f, C.muted)
            }
        }
        for (index in 0 until minOf(historyLimit, records.length())) {
            val record = records.getJSONObject(index)
            body.card {
                text(record.getString("title"), 18f, bold = true)
                gap(5)
                val seconds = record.getLong("durationMs") / 1000
                text("${seconds / 60} min ${seconds % 60} s · ${record.getString("date")}", 13f, C.muted)
                gap(8)
                if (record.optBoolean("verified")) {
                    text("${record.getInt("amount")} ${record.getString("unit")} · Verificada", 13f, C.green)
                    text("+${record.optInt("reward")} min para tus apps", 12f, C.cyan)
                } else {
                    text(if (record.optBoolean("goalMet")) "Meta de duración completada" else "Registro parcial", 13f, C.muted)
                    text("Registrada por tiempo", 12f, C.cyan)
                }
            }
        }
        if (historyLimit < records.length()) body.button("Cargar más", false) { historyLimit += 20; render() }
    }
}
