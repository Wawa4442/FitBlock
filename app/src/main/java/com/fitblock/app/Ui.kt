package com.fitblock.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import kotlin.math.*

fun android.app.Activity.chooseGoal(exercise: Exercise, current: Int, save: (Int) -> Unit) {
    val field = input("Meta", current.toString(), true)
    val error = label("", 13f, C.amber).apply {
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    val box = column(20).apply {
        text("Elige entre ${exercise.base} y ${exercise.goalRange.last} ${exercise.unit}.", 14f, C.muted)
        gap(12)
        addView(field)
        gap(8)
        button("Usar mínimo de ${exercise.base}", false) {
            field.setText(exercise.base.toString())
            error.visibility = View.GONE
        }
        addView(error)
    }
    val dialog = android.app.AlertDialog.Builder(this)
        .setTitle("Meta de ${exercise.title.lowercase()}")
        .setView(box)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Guardar", null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = field.text.toString().toIntOrNull()
            if (value == null || value !in exercise.goalRange) {
                error.text = "Elige un valor entre ${exercise.base} y ${exercise.goalRange.last}."
                error.visibility = View.VISIBLE
            } else {
                save(value)
                dialog.dismiss()
            }
        }
    }
    dialog.show()
}

object C {
    val bg = Color.rgb(16, 23, 42)
    val card = Color.rgb(24, 34, 54)
    val line = Color.rgb(43, 57, 78)
    val green = Color.rgb(56, 217, 150)
    val cyan = Color.rgb(34, 184, 207)
    val muted = Color.rgb(156, 174, 196)
    val white = Color.WHITE
    val amber = Color.rgb(255, 201, 120)
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
private var titleFace:Typeface?=null
private var bodyFace:Typeface?=null
fun Context.face(title: Boolean = false): Typeface {
    val cached=if(title)titleFace else bodyFace
    if(cached!=null)return cached
    val font=runCatching { Typeface.Builder(assets,if(title) "montserrat.ttf" else "inter.ttf")
        .setFontVariationSettings(if(title) "'wght' 700" else "'wght' 400")
        .setWeight(if(title)700 else 400).build() }.getOrDefault(Typeface.DEFAULT)
    if(title)titleFace=font else bodyFace=font
    return font
}
fun surface(color: Int = C.card, radius: Float = 22f, border: Int = C.line) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius; setStroke(1, border)
}
fun Context.label(value: String, size: Float = 14f, color: Int = C.white, bold: Boolean = false) = TextView(this).apply {
    text = value; textSize = size; setTextColor(color); typeface = face(bold)
    setLineSpacing(dp(3).toFloat(), 1f)
}
fun Context.column(padding: Int = 0) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding)) }
fun Context.row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
fun LinearLayout.gap(height: Int = 12) { addView(View(context), LinearLayout.LayoutParams(1, context.dp(height))) }
fun LinearLayout.text(value: String, size: Float = 14f, color: Int = C.white, bold: Boolean = false): TextView = context.label(value, size, color, bold).also { addView(it) }
fun LinearLayout.card(block: LinearLayout.() -> Unit): LinearLayout {
    val box = context.column(18).apply { background = surface(radius = context.dp(22).toFloat()) }
    addView(box, LinearLayout.LayoutParams(-1, -2)); box.block(); gap(12); return box
}
fun Context.action(title: String, primary: Boolean = true, onClick: () -> Unit): Button = Button(this).apply {
    text = title; isAllCaps = false; textSize = 14f; typeface = face(true)
    setTextColor(if (primary) C.bg else C.white)
    background = if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(C.green, C.cyan)).apply { cornerRadius = dp(16).toFloat() } else surface(C.card, dp(16).toFloat())
    minHeight = dp(52); minimumHeight = dp(52); setPadding(dp(16), dp(10), dp(16), dp(10))
    setOnClickListener { onClick() }
}
fun LinearLayout.button(title: String, primary: Boolean = true, onClick: () -> Unit): Button = context.action(title, primary, onClick).also {
    addView(it, LinearLayout.LayoutParams(-1, -2)); gap(8)
}
fun Context.input(hintText: String, value: String = "", number: Boolean = false) = EditText(this).apply {
    hint = hintText; setText(value); setTextColor(C.white); setHintTextColor(C.muted); textSize = 16f; typeface = face()
    background = surface(C.bg, dp(12).toFloat()); setPadding(dp(14), dp(12), dp(14), dp(12))
    inputType = if (number) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT
    minHeight = dp(52)
}
fun LinearLayout.toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val r = context.row(); val txt = context.column()
    txt.text(title, 14f, bold = true); txt.text(description, 12f, C.muted)
    r.addView(txt, LinearLayout.LayoutParams(0, -2, 1f))
    val sw = Switch(context).apply { isChecked = checked; contentDescription = title; thumbTintList = android.content.res.ColorStateList.valueOf(C.green); setOnCheckedChangeListener { _, v -> onChange(v) } }
    r.addView(sw); addView(r); gap()
}

class ProgressRing(context: Context, var minutes: Long = 0, var maximum: Long = 600_000) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val radius = min(width, height) / 2f - context.dp(12)
        p.style = Paint.Style.STROKE; p.strokeWidth = context.dp(9).toFloat(); p.strokeCap = Paint.Cap.ROUND; p.color = C.line
        canvas.drawCircle(cx, cy, radius, p)
        p.shader = SweepGradient(cx, cy, intArrayOf(C.cyan, C.green, C.cyan), null)
        canvas.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, -90f, (minutes.toFloat() / maximum.coerceAtLeast(1) * 360).coerceIn(0f, 360f), false, p)
        p.shader = null; p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.color = C.white; p.typeface = context.face(true); p.textSize = context.dp(42).toFloat()
        val seconds = (minutes.coerceAtLeast(0) + 999) / 1000
        canvas.drawText("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", cx, cy + context.dp(5), p)
        p.color = C.muted; p.typeface = context.face(); p.textSize = context.dp(12).toFloat()
        canvas.drawText("TIEMPO DISPONIBLE", cx, cy + context.dp(32), p)
    }
}

class WeekChart(context: Context, val values: List<Int>, val labels: List<String>) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val cell = width / 7f; val chartH = (height - context.dp(28)).toFloat(); val highest = max(10, values.maxOrNull() ?: 10)
        values.forEachIndexed { i, value ->
            val x = cell * i + cell * .23f; val barWidth = cell * .54f
            p.color = C.line; canvas.drawRoundRect(x, 0f, x+barWidth, chartH, 8f, 8f, p)
            p.shader = LinearGradient(0f, 0f, 0f, chartH, C.green, C.cyan, Shader.TileMode.CLAMP)
            val h = (value.toFloat() / highest * chartH).coerceAtLeast(if (value > 0) 5f else 0f)
            canvas.drawRoundRect(x, chartH-h, x+barWidth, chartH, 8f, 8f, p); p.shader = null
            p.color = C.muted; p.textAlign = Paint.Align.CENTER; p.textSize = context.dp(11).toFloat(); p.typeface = context.face()
            canvas.drawText(labels[i], cell*i + cell/2, height - context.dp(6).toFloat(), p)
        }
    }
}
