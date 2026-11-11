package com.fitblock.app

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*

class SportsPanel(
    private val activity: Activity,
    private val host: LinearLayout,
    initialQuery: String,
    initialFilter: Int,
    private val remember: (String, Int) -> Unit,
    private val open: (Sport) -> Unit
) {
    private val store = Store.get(activity)
    private val results = activity.column()
    private val count = activity.label("", 12f, C.muted)
    private var query = initialQuery
    private var filter = initialFilter

    init {
        val search = activity.input("Buscar deporte o actividad", initialQuery)
        host.addView(search)
        host.gap(10)
        val options = listOf("Todos los deportes", "Mis favoritos") + SportCategory.entries.map { it.title }
        val category = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(initialFilter.coerceIn(options.indices))
            contentDescription = "Categoría deportiva"
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    filter = position
                    update()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        host.addView(category, LinearLayout.LayoutParams(-1, activity.dp(48)))
        host.gap(8)
        host.addView(count)
        host.gap(12)
        host.addView(results)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(text: Editable?) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                query = text.toString()
                update()
            }
        })
        update()
    }

    private fun update() {
        remember(query, filter)
        results.removeAllViews()
        val category = SportCategory.entries.getOrNull(filter - 2)
        val favorites = store.favoriteSports()
        val sports = SportCatalog.search(query, category).filter { filter != 1 || it.id in favorites }
        count.text = quantity(sports.size, "actividad", "actividades")
        if (sports.isEmpty()) {
            results.text(if (filter == 1) "Marca una estrella para guardar tus deportes favoritos." else "Prueba con otro deporte o categoría.", 14f, C.muted)
        }
        for (sport in sports) {
            results.card {
                val heading = activity.row()
                val labels = activity.column()
                labels.text(sport.title, if (sport.exercise != null) 21f else 17f, bold = true)
                labels.text("${sport.category.title} · ${sport.tracking}", 12f, C.muted)
                heading.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                val favorite = activity.action(if (sport.id in favorites) "★" else "☆", false) {
                    store.setFavoriteSport(sport.id, sport.id !in store.favoriteSports())
                    update()
                }.apply { contentDescription = "Favorito: ${sport.title}" }
                heading.addView(favorite, LinearLayout.LayoutParams(activity.dp(52), activity.dp(48)))
                addView(heading)
                val exercise = sport.exercise
                if (exercise != null) {
                    gap(8)
                    text("${store.requiredGoal(exercise)} ${exercise.unit} → ${store.rewardMinutes} min", 14f, C.green, true)
                    gap(8)
                    text(when (exercise) {
                        Exercise.SQUAT -> "De perfil, baja la cadera y vuelve a ponerte de pie."
                        Exercise.PUSHUP -> "De perfil, mantén el cuerpo alineado y completa el recorrido de los brazos."
                        Exercise.JACK -> "De frente, abre los pies, levanta ambas manos y vuelve al centro."
                        Exercise.STRETCH -> "Mantén ambos brazos arriba. El tiempo se pausa si pierdes la postura."
                        Exercise.WALK -> "Lleva el teléfono en el bolsillo. El conteo sigue con la pantalla apagada."
                    }, 13f, C.muted)
                    gap(12)
                    button("Iniciar ${sport.title.lowercase()}") { open(sport) }
                } else {
                    gap(8)
                    text("Meta de ${quantity(store.sportGoal(sport.id), "minuto", "minutos")}", 13f, C.cyan)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Abrir ${sport.title}"
                    setOnClickListener { open(sport) }
                }
            }
        }
    }
}
