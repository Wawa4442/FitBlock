package com.fitblock.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity

/** Read-only showcase: sample data never enters Store or activity rewards. */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = column(22)
        body.button("‹ Volver", false) { finish() }
        body.text("Conoce FitBlock", 28f, bold = true)
        body.gap(10)
        body.text("Gana tu pantalla, muévete primero.", 18f, C.green, true)
        body.gap(12)
        body.text("Una pausa activa puede cambiar tu relación con el teléfono. Elige tus distracciones, completa una meta y gana tiempo para usarlas.", 15f, C.muted)
        body.gap(18)
        body.card {
            text("01 · Elige tus apps", 18f, bold = true)
            gap(8)
            text("Decide qué proteger y en qué horarios. Configura límites de uso; las funciones esenciales del teléfono siguen disponibles.", 14f, C.muted)
        }
        body.card {
            text("02 · Actívate", 18f, bold = true)
            gap(8)
            text("Camina con el teléfono en el bolsillo o completa sentadillas, flexiones, saltos o estiramientos con cámara. También puedes registrar otros deportes por tiempo.", 14f, C.muted)
        }
        body.card {
            text("03 · Ve tu avance", 18f, bold = true)
            gap(8)
            text("Consulta pasos y actividad por día, semana y mes. Guarda tus recorridos, construye rachas y reúne insignias.", 14f, C.muted)
        }
        body.text("Así se ve una ruta", 23f, bold = true)
        body.gap(8)
        body.text("DATOS DE EJEMPLO · recorrido ilustrativo", 12f, C.amber, true)
        body.text("Esta vista no registra actividad, no cuenta pasos ni concede minutos.", 13f, C.muted)
        body.gap(12)
        val coordinates = listOf(19.4339 to -99.1351, 19.4341 to -99.1344, 19.4340 to -99.1337,
            19.4337 to -99.1330, 19.4331 to -99.1330, 19.4325 to -99.1330, 19.4321 to -99.1335,
            19.4321 to -99.1343, 19.4322 to -99.1350, 19.4328 to -99.1352, 19.4334 to -99.1352,
            19.4339 to -99.1351)
        body.addRouteMap(coordinates.mapIndexed { index, (lat, lon) -> RoutePoint(lat, lon, index * 5000L, 5f) })
        body.card {
            text("Tu semana, de un vistazo", 19f, bold = true)
            text("Gráfica ilustrativa · datos de ejemplo", 12f, C.amber)
            gap(16)
            addView(WeekChart(this@DemoActivity, listOf(620, 840, 430, 1200, 970, 1500, 1100),
                listOf("L", "M", "X", "J", "V", "S", "D")), LinearLayout.LayoutParams(-1, dp(150)))
        }
        body.button("Probar con mis datos") {
            startActivity(Intent(this, MainActivity::class.java).putExtra("destination", "exercise")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        body.button("Conocer Premium · 50 MXN", false) { startActivity(Intent(this, PremiumActivity::class.java)) }
        body.text("Demo Comercial · compras y funciones en línea en preparación. Tus actividades reales se guardan únicamente cuando las inicias desde FitBlock.", 12f, C.muted)
        setFitContent(body)
    }
}
