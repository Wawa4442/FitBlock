package com.fitblock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/** Offer preview. A plan is never activated without a verified purchase. */
class PremiumActivity : ComponentActivity() {
    private var reviewing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Store.get(this).consent) { finish(); return }
        reviewing = savedInstanceState?.getBoolean("reviewing") ?: false
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("reviewing", reviewing)
        super.onSaveInstanceState(outState)
    }

    private fun goBack() {
        if (reviewing) { reviewing = false; render() } else finish()
    }

    private fun render() {
        val body = column(22)
        body.button(if (reviewing) "‹ Volver a Premium" else "‹ Volver", false) { goBack() }
        body.gap(14)
        body.text("FITBLOCK PREMIUM", 12f, C.cyan, true)
        body.gap(8)
        body.text(if (reviewing) "Tu siguiente nivel" else "Todo FitBlock para ti.", 28f, bold = true)
        body.gap(10)
        body.text("Mejora tu plan a Premium y desbloquea todas las funciones de la aplicación.", 16f, C.muted)
        body.gap(22)
        body.card {
            text(if (reviewing) "Resumen del plan" else "Un plan. Todas las funciones.", 18f, bold = true)
            gap(14)
            text("50 MXN", 38f, C.green, true)
            text("Precio del plan Premium · pesos mexicanos", 13f, C.muted)
            gap(14)
            text("Acceso completo a FitBlock", 16f, bold = true)
            gap(6)
            text("Ejercicios, deportes, protección de apps, personalización, progreso, rachas y premios.", 14f, C.muted)
        }
        if (reviewing) {
            body.card {
                text("Pagos disponibles próximamente", 18f, C.amber, true)
                gap(8)
                text("Todavía no puedes comprar ni activar Premium desde esta versión. No se realizará ningún cobro.", 14f, C.muted)
                gap(10)
                text("La modalidad de pago y su vigencia se mostrarán antes de habilitar la compra.", 13f, C.muted)
            }
            body.button("Volver a FitBlock") { finish() }
            body.button("Revisar beneficios", false) { reviewing = false; render() }
        } else {
            body.text("Lo que incluye Premium", 20f, bold = true)
            body.gap(12)
            listOf(
                "Muévete a tu manera" to "Todos los deportes y actividades del catálogo, favoritos y ejercicios con cámara o sensores.",
                "Cuida tu tiempo" to "Protección de aplicaciones, horarios y límites de uso para tu espacio de enfoque.",
                "Hazlo a tu medida" to "Metas de ejercicio, recompensas de minutos y dificultad progresiva configurables.",
                "Ve cada avance" to "Historial de actividades, estadísticas, rachas personales e insignias para tu colección."
            ).forEach { (title, description) ->
                body.card {
                    text(title, 17f, C.green, true)
                    gap(6)
                    text(description, 14f, C.muted)
                }
            }
            body.text("Las funciones en desarrollo, como equipos en línea y sincronización, estarán disponibles cuando se lancen. Premium no las activa anticipadamente.", 12f, C.muted)
            body.gap(16)
            body.button("Mejorar a Premium · 50 MXN") { reviewing = true; render() }
            body.button("Seguir con mi plan actual", false) { finish() }
            body.text("Vista previa del plan. Compras disponibles próximamente.", 12f, C.amber)
        }
        body.gap(16)
        setFitContent(body)
    }
}
