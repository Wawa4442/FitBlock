package com.fitblock.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity

class RouteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store.get(this)
        if (!store.consent) { finish(); return }
        val records = store.records()
        val record = (0 until records.length()).map { records.getJSONObject(it) }
            .firstOrNull { it.optString("id") == intent.getStringExtra("record") }
        if (record == null) { finish(); return }
        val body = column(22)
        body.button("‹ Volver", false) { finish() }
        body.text("Tu recorrido", 28f, bold = true)
        body.gap(8)
        body.text(record.getString("date"), 14f, C.muted)
        body.gap(16)
        body.card {
            text("${record.optInt("amount")} pasos · ${distanceText(record.optDouble("distanceMeters", 0.0))}", 20f, C.green, true)
            gap(8)
            text("Tiempo activo: ${activityTime(record.optLong("durationMs"))}", 14f, C.muted)
        }
        val points = WalkPersistence.routeFrom(record.optJSONArray("route") ?: org.json.JSONArray())
        body.addRouteMap(points)
        body.gap(14)
        body.text("Verde: inicio · Azul: final. Los cortes en la línea representan pausas o pérdida de señal. La distancia es una estimación GPS.", 13f, C.muted)
        body.button("Eliminar ruta GPS", false) {
            android.app.AlertDialog.Builder(this).setTitle("¿Eliminar esta ruta?")
                .setMessage("Se borrarán las coordenadas de este recorrido. Los pasos, distancia y estadísticas se conservarán.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Eliminar ruta") { _, _ ->
                    store.deleteRoute(record.getString("id")); finish()
                }.show()
        }
        setFitContent(body)
    }
}

fun LinearLayout.addRouteMap(points: List<RoutePoint>): RouteMapView {
    val map = RouteMapView(context)
    addView(map, LinearLayout.LayoutParams(-1, context.dp(290)))
    map.update(points)
    gap(8)
    val controls = context.row()
    for ((title, action) in listOf<Pair<String, () -> Unit>>("−" to { map.zoomBy(-1) },
        "+" to { map.zoomBy(1) }, "Encuadrar" to { map.fit() })) {
        controls.addView(context.action(title, false, action), LinearLayout.LayoutParams(0, -2, 1f))
    }
    addView(controls)
    button("© OpenStreetMap contributors", false) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")))
    }
    return map
}
