package com.fitblock.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

enum class WalkPhase { STARTING, RUNNING, PAUSED, COMPLETE }

data class WalkProgress(
    val ticket: WorkoutTicket,
    val steps: Int,
    val phase: WalkPhase,
    val message: String
) {
    val active get() = phase == WalkPhase.STARTING || phase == WalkPhase.RUNNING
}

class WalkService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sensors: SensorManager
    private lateinit var notifications: NotificationManager
    private lateinit var store: Store
    private var tracker: WalkingTracker? = null
    private var walkId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAt = 0L
    private var wakeLeaseAt = 0L
    private var previousSteps = 0
    private val refresh = object : Runnable {
        override fun run() {
            val session = progress
            val counting = tracker
            if (session == null || session.ticket.id != walkId || counting == null ||
                !store.isWorkoutCurrent(session.ticket.id) || !store.consent
            ) {
                stopSelf()
                return
            }
            val now = SystemClock.elapsedRealtime()
            counting.settle(now)
            val steps = (previousSteps + counting.steps).coerceAtMost(session.ticket.goal)
            if (steps == session.ticket.goal && store.completeWorkout(session.ticket.id, steps)) {
                progress = session.copy(steps = steps, phase = WalkPhase.COMPLETE,
                    message = "Meta completada. Ganaste ${session.ticket.rewardMinutes} minutos.")
                endTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (notifications.areNotificationsEnabled()) notifications.notify(COMPLETE_ID, notification(progress!!))
                stopSelf()
                return
            }
            if (now - wakeLeaseAt >= WAKE_LEASE_MILLIS / 2) {
                wakeLock?.acquire(WAKE_LEASE_MILLIS)
                wakeLeaseAt = now
            }
            if (steps != session.steps) {
                progress = session.copy(steps = steps)
                notifications.notify(NOTIFICATION_ID, notification(progress!!))
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = Store.get(this)
        sensors = getSystemService(SensorManager::class.java)
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Caminatas", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = progress
        if (session == null || session.ticket.id != intent?.getStringExtra(EXTRA_TICKET)) {
            if (tracker == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent.action) {
            START -> if (tracker == null && session.phase == WalkPhase.STARTING) begin(session)
            PAUSE -> if (session.active) pause("Caminata pausada. Tus pasos se conservan.")
        }
        return START_NOT_STICKY
    }

    private fun begin(session: WalkProgress) {
        walkId = session.ticket.id
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(session), type)
            notifications.cancel(COMPLETE_ID)
            if (!store.isWorkoutCurrent(session.ticket.id) || !store.consent) {
                stopSelf()
                return
            }
            startedAt = SystemClock.elapsedRealtime()
            previousSteps = session.steps
            val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer == null || !sensors.registerListener(this, accelerometer, 30_000)) {
                pause("Este teléfono no tiene un acelerómetro disponible.")
                return
            }
            val source = when {
                register(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL) -> StepSource.COUNTER
                register(Sensor.TYPE_STEP_DETECTOR, SensorManager.SENSOR_DELAY_NORMAL) -> StepSource.DETECTOR
                else -> StepSource.ACCELEROMETER
            }
            register(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_UI)
            tracker = WalkingTracker(source, startedAt)
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FitBlock:Walking").apply {
                setReferenceCounted(false)
                acquire(WAKE_LEASE_MILLIS)
            }
            wakeLeaseAt = startedAt
            progress = session.copy(phase = WalkPhase.RUNNING,
                message = if (source == StepSource.COUNTER) "Caminata activa. El podómetro puede actualizar los pasos en grupos."
                else "Caminata activa. Puedes apagar la pantalla.")
            notifications.notify(NOTIFICATION_ID, notification(progress!!))
            handler.post(refresh)
        } catch (_: SecurityException) {
            pause("Activa el permiso de actividad física y vuelve a iniciar.")
        } catch (_: IllegalStateException) {
            pause("Abre FitBlock y vuelve a iniciar la caminata.")
        }
    }

    private fun register(type: Int, period: Int): Boolean {
        val sensor = sensors.getDefaultSensor(type) ?: return false
        return sensors.registerListener(this, sensor, period)
    }

    private fun pause(message: String) {
        progress?.takeIf { it.ticket.id == walkId || walkId == null }?.let {
            tracker?.settle(SystemClock.elapsedRealtime())
            val steps = tracker?.let { tracker -> (previousSteps + tracker.steps).coerceAtMost(it.ticket.goal) } ?: it.steps
            progress = it.copy(steps = steps, phase = WalkPhase.PAUSED, message = message)
        }
        endTracking()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun endTracking() {
        handler.removeCallbacks(refresh)
        sensors.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        tracker = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val counting = tracker ?: return
        val at = event.timestamp / 1_000_000
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> counting.acceleration(at, event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_GYROSCOPE -> counting.rotation(at, event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_STEP_COUNTER -> counting.counter(at, event.values[0].toInt())
            Sensor.TYPE_STEP_DETECTOR -> counting.detected(at)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        progress?.takeIf { it.ticket.id == walkId && it.phase == WalkPhase.RUNNING }?.let {
            progress = it.copy(phase = WalkPhase.PAUSED, message = "Android pausó la caminata. Puedes reanudarla.")
        }
        endTracking()
        super.onDestroy()
    }

    private fun notification(session: WalkProgress): Notification {
        val open = PendingIntent.getActivity(this, 30, Intent(this, WalkingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(if (session.phase == WalkPhase.COMPLETE) "Caminata completada" else "Caminata de FitBlock")
            .setContentText(if (session.phase == WalkPhase.COMPLETE) session.message else "${session.steps} de ${session.ticket.goal} pasos")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(session.active)
            .setAutoCancel(!session.active)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (session.active) {
            val pause = PendingIntent.getService(this, 31, command(this, PAUSE, session.ticket.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setProgress(session.ticket.goal, session.steps, false)
            builder.addAction(Notification.Action.Builder(null, "Pausar", pause).build())
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL = "walking"
        private const val NOTIFICATION_ID = 101
        private const val COMPLETE_ID = 102
        private const val START = "com.fitblock.app.walk.START"
        private const val PAUSE = "com.fitblock.app.walk.PAUSE"
        private const val EXTRA_TICKET = "ticket"
        private const val WAKE_LEASE_MILLIS = 60 * 60 * 1000L

        var progress: WalkProgress? = null
            private set

        fun unfinished(context: Context): WalkProgress? = progress?.takeIf {
            it.phase != WalkPhase.COMPLETE && Store.get(context).isWorkoutCurrent(it.ticket.id)
        }

        fun start(context: Context) {
            if (SportTimer.get(context).current() != null) return
            if (Build.VERSION.SDK_INT >= 29 &&
                context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
            ) return
            val existing = unfinished(context)
            if (existing?.active == true) return
            val ticket = existing?.ticket ?: Store.get(context).startWorkout(Exercise.WALK)
            val starting = WalkProgress(ticket, existing?.steps ?: 0, WalkPhase.STARTING, "Preparando sensores…")
            progress = starting
            try {
                ContextCompat.startForegroundService(context, command(context, START, ticket.id))
            } catch (_: RuntimeException) {
                progress = starting.copy(phase = WalkPhase.PAUSED, message = "Vuelve a iniciar la caminata con FitBlock abierto.")
            }
        }

        fun pause(context: Context) {
            val session = unfinished(context)?.takeIf { it.active } ?: return
            context.startService(command(context, PAUSE, session.ticket.id))
        }

        fun discard(context: Context) {
            progress?.let { Store.get(context).cancelWorkout(it.ticket.id) }
            progress = null
            context.stopService(Intent(context, WalkService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel(COMPLETE_ID)
        }

        private fun command(context: Context, action: String, id: String) =
            Intent(context, WalkService::class.java).setAction(action).putExtra(EXTRA_TICKET, id)
    }
}
