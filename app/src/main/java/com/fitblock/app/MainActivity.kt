package com.fitblock.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.ComponentActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var tab = "home"
    private var ring: ProgressRing? = null
    private var timeCaption: TextView? = null
    private var protectionLabel: TextView? = null
    private var walkLabel: TextView? = null
    private var sportQuery = ""
    private var sportFilter = 0
    private val permissions = PermissionPrompt(this) { _, _ -> render() }
    private val handler = Handler(Looper.getMainLooper())
    private val pulse = object : Runnable {
        override fun run() {
            ring?.apply { minutes = store.balance(); maximum = store.rewardMinutes*60_000L; invalidate() }
            timeCaption?.text = if (store.balance() > 0) "Úsalo en tus apps · vence en ${(store.expiresIn()+59_999)/60_000} min" else "Tu próximo descanso empieza con movimiento"
            protectionLabel?.text = protectionText()
            walkLabel?.text = walkSummary()
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); store = Store.get(this)
        tab = savedInstanceState?.getString("tab") ?: "home"
        handleDestination(); render()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("tab", tab); super.onSaveInstanceState(outState) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleDestination(); render() }
    override fun onResume() { super.onResume(); if (::store.isInitialized) { render(); handler.removeCallbacks(pulse); handler.post(pulse) } }
    override fun onPause() { handler.removeCallbacks(pulse); super.onPause() }
    private fun handleDestination() {
        when (intent.getStringExtra("destination")) { "exercise" -> tab = "exercise"; "emergency" -> { tab = "settings"; handler.post { emergencyDialog() } } }
        intent.removeExtra("destination")
    }
    private fun protectionText() = when {
        !BlockService.connected -> "●  Permiso de protección pendiente"
        !store.enabled -> "●  Protección en pausa"
        store.selected.isEmpty() -> "●  Selecciona tus aplicaciones"
        !store.inSchedule() -> "●  Fuera de tu horario de bloqueo"
        store.emergencyActive() -> "●  Acceso de emergencia activo"
        else -> "●  Protección activa · ${store.selected.size} apps"
    }
    private fun render() {
        ring = null; timeCaption = null; protectionLabel = null; walkLabel = null
        root = column().apply { setBackgroundColor(C.bg) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) { val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()); view.setPadding(b.left,b.top,b.right,b.bottom) }
            else { @Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom) }
            insets
        }
        setContentView(root); root.requestApplyInsets()
        if (!store.consent) { onboarding(); return }
        val header = row().apply { setPadding(dp(22), dp(15), dp(22), dp(12)) }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.fitblock_icon); contentDescription = "FitBlock" }, LinearLayout.LayoutParams(dp(38), dp(38)))
        val brand = label("FitBlock", 22f, C.white, true).apply { setPadding(dp(10),0,0,0) }
        header.addView(brand, LinearLayout.LayoutParams(0,-2,1f))
        header.addView(action("Mi cuenta", false) { openAccount() }.apply {
            isSingleLine = true
            textSize = 12f
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(10), 0, dp(10), 0)
        }, LinearLayout.LayoutParams(dp(100), dp(48)))
        root.addView(header)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content = column(22); scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        when(tab) { "apps" -> apps(); "exercise" -> exercises(); "stats" -> stats(); "settings" -> settings(); "permissions" -> permissionSettings(); else -> home() }
        content.gap(16)
        val nav = row().apply { setPadding(dp(6),dp(8),dp(6),dp(9)); background = surface(C.bg,0f) }
        listOf(Triple("home","◉","Inicio"), Triple("apps","▦","Apps"), Triple("exercise","↗","Moverme"), Triple("stats","▥","Progreso"), Triple("settings","⚙","Ajustes")).forEach { (key, symbol, title) ->
            val b = column(5).apply {
                val selected = tab == key || (tab == "permissions" && key == "settings")
                gravity = Gravity.CENTER; background = if (selected) surface(C.card, dp(14).toFloat(), C.card) else null
                text(symbol,22f,if(tab==key) C.green else C.muted).gravity=Gravity.CENTER
                text(title,10f,if(tab==key) C.green else C.muted,tab==key).gravity=Gravity.CENTER
                contentDescription = title; isClickable = true; isFocusable = true; setOnClickListener { tab=key;render() }
            }
            nav.addView(b, LinearLayout.LayoutParams(0,dp(62),1f))
        }
        root.addView(nav)
    }
    private fun title(eyebrow: String, heading: String, subtitle: String) {
        content.text(eyebrow.uppercase(),11f,C.cyan,true);content.gap(8)
        content.text(heading,27f,bold=true);content.gap(8);content.text(subtitle,14f,C.muted);content.gap(22)
    }
    private fun onboarding() {
        val scroll = ScrollView(this).apply { isFillViewport = true }; val body = column(26)
        body.gap(24)
        body.addView(ImageView(this).apply { setImageResource(R.drawable.fitblock_icon); contentDescription="Logotipo de FitBlock" },LinearLayout.LayoutParams(dp(92),dp(92)))
        body.gap(22);body.text("BIENVENIDO A FITBLOCK",12f,C.cyan,true);body.gap(12)
        body.text("Gana tu pantalla,\nmuévete primero.",32f,bold=true);body.gap(16)
        body.text("Haz espacio para lo que te hace bien. Convierte una pausa activa en tiempo para tus aplicaciones favoritas.",16f,C.muted);body.gap(24)
        listOf("01  Elige tus distracciones" to "Selecciona las apps y el horario que quieres proteger.", "02  Activa tu cuerpo" to "Completa tu meta de ejercicio validada con cámara o sensores.", "03  Disfruta tu tiempo" to "Gana minutos de uso. Al agotarlos, tus apps vuelven a bloquearse.").forEach { (a,b) -> body.card { text(a,16f,bold=true);gap(6);text(b,14f,C.muted) } }
        body.text("Todo sucede en tu teléfono",18f,bold=true);body.gap(8)
        body.text("La cámara se procesa localmente. Los sensores cuentan tus pasos durante una caminata, incluso con la pantalla apagada. El historial se guarda en este dispositivo.",13f,C.muted);body.gap(10)
        body.text("Con tu autorización, Accesibilidad identifica qué app está abierta y muestra una barrera sobre las que elijas. No lee mensajes, contraseñas ni contenido. Puedes revocar el permiso y eliminar tus datos desde Ajustes.",13f,C.muted);body.gap(16)
        val accept = CheckBox(this).apply { text="He leído y acepto este funcionamiento y el procesamiento local de cámara, sensores y estadísticas.";setTextColor(C.white);textSize=13f;buttonTintList=android.content.res.ColorStateList.valueOf(C.green) }
        body.addView(accept);body.gap(12)
        body.button("Comenzar") { if(accept.isChecked) { store.consent=true;tab="apps";render() } else toast("Marca la casilla para continuar.") }
        body.button("Ver privacidad y condiciones",false) { privacy() }
        body.gap(24);scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,-1))
    }
    private fun home() {
        val greet=if(store.name.isBlank()) "Hoy eliges tú." else "Hola, ${store.name}."
        title("Tu bienestar digital",greet,"Una pausa activa. Una decisión más consciente.")
        walkCard()
        timerCard()
        content.card {
            protectionLabel=text(protectionText(),12f,if(BlockService.connected&&store.enabled) C.green else C.amber,true)
            gap(16)
            ring=ProgressRing(this@MainActivity,store.balance(),store.rewardMinutes*60_000L)
            addView(ring,LinearLayout.LayoutParams(-1,dp(212)))
            timeCaption=text(if(store.balance()>0) "Tu pase está listo para usar" else "Tu próximo descanso empieza con movimiento",12f,C.muted).apply { gravity=Gravity.CENTER }
            gap(22);button("↗  Ganar ${store.rewardMinutes} minutos") { tab="exercise";render() }
            text("El tiempo se descuenta solo al usar apps protegidas.",11f,C.muted).gravity=Gravity.CENTER
        }
        if(!BlockService.connected) content.card {
            text("Activa la protección",16f,bold=true);gap(6);text("Falta autorizar el servicio de accesibilidad para que FitBlock pueda bloquear tus apps.",13f,C.muted);gap(12)
            button("Configurar permisos",false) { showPermissions() }
        }
        val d=store.dayStats();val r=row()
        fun metric(value:String,caption:String) = column(15).apply { background=surface(radius=dp(18).toFloat());text(value,26f,C.green,true);gap(4);text(caption,11f,C.muted) }
        r.addView(metric("${d.optInt("sessions") + d.optInt("manualSessions")}","actividades hoy"),LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(10) })
        r.addView(metric("${store.streak()}","días de racha"),LinearLayout.LayoutParams(0,-2,1f));content.addView(r);content.gap(20)
        content.button("Rachas y premios", false) { openCommunity() }
        content.text("Tu siguiente paso",18f,bold=true);content.gap(12)
        content.card { text("${store.requiredGoal(Exercise.SQUAT)} sentadillas  →  ${store.rewardMinutes} min",16f,bold=true);gap(8);text("Cámara · cuerpo completo · sin guardar video",12f,C.muted);gap(12);button("Iniciar actividad",false) { startWorkout(Exercise.SQUAT) } }
        content.card { text("${store.selected.size} apps en tu espacio de enfoque",16f,bold=true);gap(8);text("Horario: ${scheduleText()}\nLímite por defecto: ${store.dailyLimit} min por app al día",13f,C.muted);gap(12);button("Administrar mis aplicaciones",false) { tab="apps";render() } }
    }
    private fun apps() {
        title("Espacio de enfoque","Tú pones el límite.","Elige qué aplicaciones necesitan una pausa antes de abrirlas.")
        content.card {
            toggle("Protección de FitBlock","${store.selected.size} apps elegidas · ${scheduleText()}",store.enabled) { enabled ->
                if(enabled) {
                    store.enabled = true
                    if (!BlockService.connected) showPermissions() else render()
                }
                else pauseDialog()
            }
            button("Configurar horarios y límites",false) { rulesDialog() }
        }
        content.text("Tus aplicaciones",18f,bold=true);content.gap(5);content.text("Mantén presionada una app para cambiar su límite diario.",12f,C.muted);content.gap(12)
        val search=input("Buscar una aplicación");content.addView(search);content.gap(12)
        val list=column();content.addView(list)
        val installed=AppCatalog.list(this)
        fun show(filter:String) {
            list.removeAllViews()
            val results=installed.filter { it.title.contains(filter,true)||it.pkg.contains(filter,true) }
            if(results.isEmpty()) list.text("No se encontraron aplicaciones. Las apps esenciales se excluyen de la lista.",14f,C.muted)
            results.forEach { app ->
                val item=row().apply { setPadding(dp(12),dp(12),dp(10),dp(12));background=surface(radius=dp(16).toFloat()) }
                val icon=ImageView(this).apply { setImageDrawable(runCatching { packageManager.getApplicationIcon(app.pkg) }.getOrNull());contentDescription=app.title }
                item.addView(icon,LinearLayout.LayoutParams(dp(40),dp(40)).apply { marginEnd=dp(12) })
                val txt=column();txt.text(app.title,14f,bold=true);txt.text("${store.appLimit(app.pkg)} min/día · ${(store.used(app.pkg)/60_000)} min usados",11f,C.muted)
                item.addView(txt,LinearLayout.LayoutParams(0,-2,1f))
                val check=CheckBox(this).apply { isChecked=app.pkg in store.selected;contentDescription="Proteger ${app.title}";buttonTintList=android.content.res.ColorStateList.valueOf(C.green) }
                check.setOnCheckedChangeListener { _,v -> store.selected=if(v) store.selected+app.pkg else store.selected-app.pkg }
                item.addView(check);item.setOnClickListener { check.isChecked=!check.isChecked }
                item.setOnLongClickListener { numberDialog("Límite diario de ${app.title}",store.appLimit(app.pkg),1..240,"minutos") { store.setAppLimit(app.pkg,it);render() };true }
                list.addView(item);list.gap(8)
            }
        }
        show("");search.addTextChangedListener(object:TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){show(s.toString())};override fun afterTextChanged(s:Editable?){} })
        content.gap(12);content.text("Siempre disponibles: teléfono, SMS, inicio, teclado, ajustes y las aplicaciones de emergencia identificadas por Android.",12f,C.muted)
    }
    private fun exercises() {
        title("Deportes y actividad","Actívate. Desbloquea.","Explora ${SportCatalog.all.size} actividades y elige cómo registrar tu movimiento.")
        walkCard()
        timerCard()
        content.card {
            text("Cámara, pasos o cronómetro", 16f, bold = true)
            gap(8)
            text("Las actividades verificadas por cámara o pasos ganan minutos para tus apps. Los otros deportes registran su duración y las metas completadas suman a tu racha.", 13f, C.muted)
            gap(10)
            button("Personalizar mis metas", false) { goalsDialog() }
        }
        SportsPanel(this, content, sportQuery, sportFilter, { query, filter ->
            sportQuery = query
            sportFilter = filter
        }) { sport ->
            if (sport.exercise != null) startWorkout(sport.exercise)
            else startActivity(Intent(this, SportSessionActivity::class.java).putExtra("sport", sport.id))
        }
    }
    private fun timerCard() {
        val session = SportTimer.get(this).current() ?: return
        val sport = SportCatalog.get(session.sportId) ?: return
        content.card {
            text("${sport.title} · ${if (session.running) "En curso" else "Pausada"}", 17f, bold = true)
            gap(8)
            button("Continuar actividad", false) {
                startActivity(Intent(this@MainActivity, SportSessionActivity::class.java).putExtra("sport", sport.id))
            }
        }
    }
    private fun openAccount() { startActivity(Intent(this, AccountActivity::class.java)) }
    private fun openCommunity(tab: String = "streaks") {
        startActivity(Intent(this, CommunityActivity::class.java).putExtra("tab", tab))
    }
    private fun startWorkout(exercise: Exercise) {
        val timer = SportTimer.get(this).current()
        if (timer != null) {
            startActivity(Intent(this, SportSessionActivity::class.java).putExtra("sport", timer.sportId))
            return
        }
        if (exercise == Exercise.WALK) {
            startActivity(Intent(this, WalkingActivity::class.java))
            return
        }
        if (WalkService.unfinished(this) != null) {
            AlertDialog.Builder(this).setTitle("Tienes una caminata pendiente")
                .setMessage("Completa o termina tu caminata para iniciar otro ejercicio.")
                .setPositiveButton("Ir a la caminata") { _, _ -> startWorkout(Exercise.WALK) }
                .setNegativeButton("Volver", null).show()
            return
        }
        startActivity(Intent(this, WorkoutActivity::class.java).putExtra("exercise", exercise.name))
    }

    private fun walkSummary(): String {
        val walk = WalkService.unfinished(this) ?: return "Consulta tu progreso en Caminata."
        return "${walk.steps} de ${walk.ticket.goal} pasos · ${if (walk.active) "En curso" else "Pausada"}"
    }

    private fun walkCard() {
        if (WalkService.unfinished(this) == null) return
        content.card {
            text("Tu caminata", 17f, bold = true)
            gap(6)
            walkLabel = text(walkSummary(), 14f, C.muted)
            gap(8)
            button("Ver caminata", false) { startWorkout(Exercise.WALK) }
        }
    }
    private fun stats() {
        title("Tu progreso","Cada pausa cuenta.","Actividad real registrada en este dispositivo.")
        content.button("Historial de actividades", false) { openCommunity("history") }
        content.button("Rachas y premios", false) { openCommunity() }
        val d=store.dayStats()
        content.card {
            text("HOY",11f,C.cyan,true);gap(12)
            text("${d.optInt("earned")} min ganados",25f,C.green,true);gap(8)
            text("${d.optLong("used")/60_000} min de apps protegidas usados",14f,C.muted);gap(12)
            text("${d.optInt("reps")} repeticiones  ·  ${d.optInt("steps")} pasos",14f)
            text("${d.optInt("stretchSeconds")} s de estiramiento  ·  ${d.optLong("exerciseMs")/60_000} min de actividad",13f,C.muted)
            text("${d.optInt("recordedSessions")} deportes registrados · ${d.optLong("sportMs") / 60_000} min por cronómetro",13f,C.muted)
            gap(10);text("${d.optInt("blocked")} accesos interrumpidos · ${d.optInt("emergencies")} emergencias",12f,C.muted)
        }
        content.card {
            text("Minutos ganados esta semana",17f,bold=true);gap(20)
            val days=(6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
            addView(WeekChart(this@MainActivity,days.map { store.dayStats(it.toString()).optInt("earned") },days.map { it.format(DateTimeFormatter.ofPattern("EE",Locale.forLanguageTag("es"))).take(2) }),LinearLayout.LayoutParams(-1,dp(155)))
        }
        val total=store.totalActivities();val level=total/5+1
        content.card {
            text("Nivel $level · ${store.streak()} días de racha",20f,bold=true);gap(8);text("$total actividades completadas. Próximo nivel en ${5-total%5} actividades.",13f,C.muted)
            gap(12);val progress=ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply { max=5;progress=total%5;progressTintList=android.content.res.ColorStateList.valueOf(C.green) };addView(progress)
            gap(16)
            val unlocked = AwardCatalog.refresh(store)
            text("${unlocked.size} de 8 insignias desbloqueadas",13f,C.green)
            gap(10)
            button("Ver mis premios",false) { openCommunity("awards") }
        }
        content.text("Historial reciente",18f,bold=true);content.gap(12)
        val events=store.events()
        if(events.length()==0) content.text("Tu historial aparecerá cuando actives la protección o completes una actividad.",14f,C.muted)
        for(i in 0 until minOf(12,events.length())) {
            val ev=events.getJSONObject(i)
            content.card {
                text(ev.getString("title"),14f,bold=true);gap(4);text(ev.getString("detail"),12f,C.muted);gap(4)
                text(java.text.SimpleDateFormat("dd MMM · HH:mm",Locale.forLanguageTag("es")).format(java.util.Date(ev.getLong("time"))),10f,C.cyan)
            }
        }
        content.text("El consumo muestra únicamente las apps seleccionadas durante tu horario protegido con FitBlock activo. No es el tiempo de pantalla total del teléfono.",12f,C.muted)
    }
    private fun settings() {
        title("A tu medida","Tu espacio. Tus reglas.","Ajusta FitBlock para acompañar tu día.")
        content.card {
            text("Mi cuenta",17f,bold=true);gap(8);text(if(store.name.isBlank()) "Invitado" else store.name,14f,C.muted);gap(10)
            button(if (Accounts.get(this@MainActivity).current() == null) "Crear cuenta o entrar" else "Ver mi cuenta",false) { openAccount() }
            button("Cambiar nombre",false) { val edit=input("Tu nombre",store.name);AlertDialog.Builder(this@MainActivity).setTitle("¿Cómo te llamas?").setView(column(20).apply { addView(edit) }).setPositiveButton("Guardar") { _,_-> val name = edit.text.toString().trim(); if (name.length in 1..40) store.name=name else toast("Escribe un nombre de hasta 40 caracteres."); render() }.setNegativeButton("Cancelar",null).show() }
        }
        content.card {
            text("Estado de protección",17f,bold=true);gap(10);text(protectionText(),13f,C.amber);gap(12)
            button("Configurar permisos",false) { showPermissions() }
        }
        content.card {
            text("Reglas de desbloqueo",17f,bold=true);gap(10)
            text("${store.rewardMinutes} min por actividad · pase válido ${store.validityMinutes} min\n${store.dailyLimit} min diarios por app (por defecto)\nHorario: ${scheduleText()}",13f,C.muted);gap(12)
            button("Editar tiempos y horarios",false) { rulesDialog() };button("Metas de ejercicio",false) { goalsDialog() }
            toggle("Dificultad progresiva","+10 % por actividad del día; máximo +40 %.",store.progressive) { store.progressive=it }
        }
        content.card {
            text("Acceso de emergencia",17f,bold=true);gap(8);text("Una vez al día: 2 minutos sin bloqueo ni consumo de saldo. Tu siguiente meta aumenta un 50 %. El acceso esencial del teléfono nunca depende de hacer ejercicio.",13f,C.muted);gap(12)
            button("Solicitar acceso de emergencia",false) { emergencyDialog() }
        }
        content.card {
            text("Tus datos te pertenecen",17f,bold=true);gap(10);text("Tus cuentas e historiales se conservan en este teléfono. Puedes exportar el historial de tu sesión o borrar todos los datos locales.",13f,C.muted);gap(12)
            button("Privacidad y condiciones",false) { privacy() };button("Exportar mi historial",false) { exportData() }
            button("Eliminar mis datos",false) { AlertDialog.Builder(this@MainActivity).setTitle("Eliminar los datos de FitBlock").setMessage("Se eliminarán todas las cuentas, sesiones, metas, aplicaciones, pases e historiales de este teléfono. Se desactivará la protección. Esta acción no puede deshacerse.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar") { _,_->WalkService.discard(this@MainActivity);SportTimer.get(this@MainActivity).discard();store.clear();render() }.show() }
        }
        content.text("FitBlock 0.3.0",12f,C.muted)
    }

    private fun showPermissions() {
        tab = "permissions"
        render()
    }

    private fun permissionSettings() {
        content.button("‹ Volver a ajustes", false) { tab = "settings"; render() }
        content.gap(12)
        content.text("Permisos de FitBlock", 26f, bold = true)
        content.gap(16)
        permissionCard("Bloqueo de apps", BlockService.connected,
            "En Accesibilidad, abre Aplicaciones instaladas o descargadas, elige FitBlock y activa el servicio.",
            "Abrir Accesibilidad") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        if (!BlockService.connected) {
            content.card {
                text("Si Android muestra «Ajuste restringido»", 15f, bold = true)
                gap(6)
                text("Abre la información de FitBlock. En el menú ⋮, pulsa Permitir ajustes restringidos y vuelve a Accesibilidad.", 13f, C.muted)
                gap(8)
                button("Abrir información de FitBlock", false) { openAppSettings() }
            }
        }
        permissionCard("Cámara", hasPermission(Manifest.permission.CAMERA),
            "Para contar repeticiones y segundos de postura.", "Permitir cámara") {
            permissions.request(Manifest.permission.CAMERA)
        }
        permissionCard("Actividad física", Build.VERSION.SDK_INT < 29 || hasPermission(Manifest.permission.ACTIVITY_RECOGNITION),
            "Para contar pasos con el teléfono en el bolsillo.", "Permitir actividad física") {
            permissions.request(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissionCard("Notificaciones · opcional", hasPermission(Manifest.permission.POST_NOTIFICATIONS),
                "Muestra tus pasos y te permite pausar desde la notificación.", "Permitir notificaciones") {
                permissions.request(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionCard("Acceso de uso · opcional", UsageAccess.allowed(this),
            "Ayuda a restaurar el bloqueo tras reconectar el servicio.", "Abrir Acceso de uso") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun hasPermission(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionCard(title: String, granted: Boolean, description: String, action: String, request: () -> Unit) {
        content.card {
            text(title, 17f, bold = true)
            gap(5)
            text(if (granted) "Activo" else "Pendiente", 13f, if (granted) C.green else C.amber, true)
            gap(5)
            text(description, 13f, C.muted)
            if (!granted) {
                gap(10)
                button(action, false, request)
            }
        }
    }
    private fun scheduleText():String {
        val s=store.schedule
        val time=if(s.start==s.end) "Todo el día" else "${time(s.start)}–${time(s.end)}"
        val names=listOf("L","M","X","J","V","S","D")
        return "$time · ${s.days.sorted().joinToString(" ") { names[it-1] }}"
    }
    private fun time(m:Int)="${(m/60).toString().padStart(2,'0')}:${(m%60).toString().padStart(2,'0')}"
    private fun rulesDialog() {
        val box=column(20);val scroll=ScrollView(this).apply { addView(box) }
        fun field(title:String,value:Int):EditText { box.text(title,13f,C.muted);box.gap(6);val e=input(title,"$value",true);box.addView(e);box.gap(14);return e }
        val reward=field("Minutos ganados por actividad (1–60)",store.rewardMinutes)
        val valid=field("Vigencia del pase en minutos (5–240)",store.validityMinutes)
        val limit=field("Límite diario por defecto, por app (1–240)",store.dailyLimit)
        box.text("Solo se bloquea dentro de este horario. Si inicio y fin son iguales, se protege todo el día.",12f,C.muted);box.gap(12)
        var start=store.schedule.start;var end=store.schedule.end
        lateinit var startButton:Button;lateinit var endButton:Button
        startButton=box.button("Inicio: ${time(start)}",false) { TimePickerDialog(this,{_,h,m->start=h*60+m;startButton.text="Inicio: ${time(start)}"},start/60,start%60,true).show() }
        endButton=box.button("Fin: ${time(end)}",false) { TimePickerDialog(this,{_,h,m->end=h*60+m;endButton.text="Fin: ${time(end)}"},end/60,end%60,true).show() }
        val days=store.schedule.days.toMutableSet();listOf("Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo").forEachIndexed { i,n -> box.addView(CheckBox(this).apply { text=n;setTextColor(C.white);isChecked=i+1 in days;setOnCheckedChangeListener { _,v->if(v)days.add(i+1)else days.remove(i+1) } }) }
        val dialog=AlertDialog.Builder(this).setTitle("Tiempos y horario").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val r=reward.text.toString().toIntOrNull();val v=valid.text.toString().toIntOrNull();val l=limit.text.toString().toIntOrNull()
            if(r==null||r !in 1..60||v==null||v !in 5..240||l==null||l !in 1..240||days.isEmpty()) { toast("Revisa los rangos y elige al menos un día.");return@setOnClickListener }
            store.rewardMinutes=r;store.validityMinutes=v;store.dailyLimit=l;store.schedule=Schedule(start,end,days);dialog.dismiss();render()
        } };dialog.show()
    }
    private fun goalsDialog() {
        val box=column(20);val fields=Exercise.entries.associateWith { e->box.text("${e.title} · ${e.base}–${e.goalRange.last} ${e.unit}",13f,C.muted);box.gap(6);input("Meta","${store.baseGoal(e)}",true).also { box.addView(it);box.gap(12) } }
        box.text("Estos valores se aplican al iniciar una actividad nueva.",12f,C.muted)
        val dialog=AlertDialog.Builder(this).setTitle("Mis metas de movimiento").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val values=fields.mapValues { it.value.text.toString().toIntOrNull() }
            if(values.any { (e,v)->v==null||v !in e.goalRange }) { toast("Introduce metas dentro de los rangos indicados.");return@setOnClickListener }
            values.forEach { (e,v)->store.setGoal(e,v!!) };dialog.dismiss();render()
        } };dialog.show()
    }
    private fun numberDialog(title:String,value:Int,range:IntRange,unit:String,save:(Int)->Unit) {
        val edit=input("${range.first}–${range.last} $unit","$value",true)
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(column(20).apply { addView(edit) }).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val n=edit.text.toString().toIntOrNull();if(n==null||n !in range)toast("Elige de ${range.first} a ${range.last}.")else { save(n);dialog.dismiss() } } };dialog.show()
    }
    private fun pauseDialog() {
        val dialog=AlertDialog.Builder(this).setTitle("Pausar la protección").setMessage("Las aplicaciones seleccionadas volverán a estar disponibles. Tómate unos segundos para decidir.").setNegativeButton("Seguir protegido") { _,_->render() }.setPositiveButton("Pausar en 10 s",null).create()
        dialog.setOnShowListener {
            val button=dialog.getButton(AlertDialog.BUTTON_POSITIVE);button.isEnabled=false
            object:CountDownTimer(10_000,1000) { override fun onTick(ms:Long) { button.text="Pausar en ${(ms+999)/1000} s" };override fun onFinish(){ if(!dialog.isShowing)return;button.text="Pausar protección";button.isEnabled=true;button.setOnClickListener { store.enabled=false;store.audit("Protección pausada","Pausa voluntaria desde FitBlock.");dialog.dismiss();render() } } }.start()
        };dialog.setOnCancelListener { render() };dialog.show()
    }
    private fun emergencyDialog() {
        AlertDialog.Builder(this).setTitle("Acceso de emergencia").setMessage("Tendrás 2 minutos de acceso inmediato a tus apps, incluso si alcanzaste el límite diario. Disponible una vez al día. La siguiente meta de ejercicio aumentará un 50 %. ¿Activar ahora?").setNegativeButton("Cancelar",null).setPositiveButton("Activar 2 minutos") { _,_->if(store.emergency()) { toast("Acceso de emergencia activado por 2 minutos.");render() }else toast("Ya utilizaste tu acceso de emergencia de hoy.") }.show()
    }
    private fun privacy() {
        AlertDialog.Builder(this).setTitle("Privacidad y condiciones")
            .setMessage("FitBlock procesa tus datos en el teléfono.\n\nLas cuentas guardan nombre y correo. La contraseña se transforma con PBKDF2 y una sal aleatoria. Cada cuenta tiene su historial.\n\nLa cámara estima articulaciones en memoria. No se guardan fotos, videos ni recorridos. La caminata usa sensores durante la sesión, incluso con la pantalla apagada. Los otros deportes registran tiempo.\n\nAccesibilidad identifica la app abierta para aplicar el bloqueo que elegiste.\n\nPuedes exportar el historial de tu cuenta o borrar todos los datos locales de FitBlock desde Ajustes de la app.")
            .setPositiveButton("Entendido",null).show()
    }
    private fun exportData() { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"FitBlock-${LocalDate.now()}.json"),101) }
    @Deprecated("Legacy activity result used for document picker")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==101&&resultCode==RESULT_OK) data?.data?.let { uri->runCatching { contentResolver.openOutputStream(uri)?.use { it.write(store.export().toByteArray(Charsets.UTF_8)) } }.onSuccess { toast("Historial exportado.") }.onFailure { toast("No se pudo guardar el archivo.") } }
    }
    private fun toast(message:String) { Toast.makeText(this,message,Toast.LENGTH_LONG).show() }
}
