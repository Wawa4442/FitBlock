package com.fitblock.app

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

class WorkoutActivity : ComponentActivity(), SensorEventListener {
    private lateinit var store: Store
    private lateinit var exercise: Exercise
    private lateinit var session: PoseSession
    private var ticket: WorkoutTicket? = null
    private lateinit var goal: TextView
    private lateinit var edit: Button
    private lateinit var status: TextView
    private lateinit var counter: TextView
    private lateinit var progress: ProgressBar
    private lateinit var start: Button
    private lateinit var preview: PreviewView
    private lateinit var skeleton: SkeletonView
    private lateinit var sensors: SensorManager
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var detector: PoseDetector? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    @Volatile private var cameraGeneration = 0
    private var finished = false
    private var lastResultAt = 0L
    private var movingCameraAt = -10_000L
    private val permission = PermissionPrompt(this) { _, granted ->
        if (granted) begin()
        else status.text = "Autoriza Cámara para iniciar. Vuelve a pulsar Comenzar para revisar el permiso."
    }
    private val watchdog = object : Runnable {
        override fun run() {
            if (!active) return
            if (SystemClock.elapsedRealtime() - lastResultAt > 1800) {
                session.resetTracking()
                status.text = "Buscando el cuerpo completo. Revisa la luz y el encuadre."
                skeleton.sample = null
                skeleton.invalidate()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store.get(this)
        if (!store.consent) {
            finish()
            return
        }
        exercise = runCatching { Exercise.valueOf(intent.getStringExtra("exercise").orEmpty()) }.getOrDefault(Exercise.SQUAT)
        if (!exercise.camera || WalkService.unfinished(this) != null) {
            startActivity(Intent(this, WalkingActivity::class.java))
            finish()
            return
        }
        session = PoseSession(exercise, listOf(0, 1, 2).shuffled().take(2))
        sensors = getSystemService(SensorManager::class.java)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = exitDialog()
        })
    }

    private fun buildUi() {
        val body = column(22)
        body.button("‹ Volver", false) { exitDialog() }
        body.gap(8)
        body.text(exercise.title, 27f, bold = true)
        body.gap(6)
        goal = body.text("", 14f, C.green, true)
        body.gap(8)
        edit = body.button("Cambiar meta", false) {
            chooseGoal(exercise, store.baseGoal(exercise)) {
                store.setGoal(exercise, it)
                updateGoal()
            }
        }
        counter = body.text("", 32f, bold = true).apply { gravity = Gravity.CENTER }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        body.addView(progress)
        body.gap(6)
        status = body.text("Apoya el teléfono y encuadra todo tu cuerpo. Levanta los brazos según las indicaciones iniciales.", 14f, C.muted)
        status.minLines = 3
        body.gap(6)
        val frame = FrameLayout(this)
        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(Color.BLACK)
        }
        skeleton = SkeletonView(this)
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        frame.addView(skeleton, FrameLayout.LayoutParams(-1, -1))
        val height = (resources.configuration.screenHeightDp * .36f).toInt().coerceIn(180, 300)
        body.addView(frame, LinearLayout.LayoutParams(-1, dp(height)))
        body.gap(10)
        start = body.button("Comenzar verificación") {
            if (active) {
                stopTracking()
                status.text = "Actividad pausada. Puedes reanudarla con el mismo conteo."
            } else {
                permission.request(Manifest.permission.CAMERA)
            }
        }
        body.text("Cámara frontal · vista espejo", 11f, C.muted)
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
        updateGoal()
    }

    private fun updateGoal() {
        val target = ticket?.goal ?: store.requiredGoal(exercise)
        val minutes = ticket?.rewardMinutes ?: store.rewardMinutes
        goal.text = "Meta: $target ${exercise.unit} · +$minutes min"
        progress.max = target
        counter.text = "${session.count} / $target"
    }

    private fun begin() {
        if (active || finished || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        if (WalkService.unfinished(this) != null) {
            status.text = "Termina tu caminata antes de iniciar otro ejercicio."
            return
        }
        if (ticket == null) ticket = store.startWorkout(exercise)
        active = true
        val generation = ++cameraGeneration
        lastResultAt = SystemClock.elapsedRealtime()
        status.text = "Preparando cámara…"
        start.text = "Pausar actividad"
        edit.visibility = View.GONE
        updateGoal()
        for (type in listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)) {
            sensors.getDefaultSensor(type)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        openCamera(generation)
        handler.postDelayed(watchdog, 1000)
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun openCamera(generation: Int) {
        val main = ContextCompat.getMainExecutor(this)
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (generation != cameraGeneration || isFinishing) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    stopTracking()
                    status.text = "No hay cámara frontal disponible."
                    return@addListener
                }
                val client = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
                detector = client
                @Suppress("DEPRECATION")
                val view = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                view.setSurfaceProvider(preview.surfaceProvider)
                @Suppress("DEPRECATION")
                val frames = ImageAnalysis.Builder().setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis = frames
                frames.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || generation != cameraGeneration) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val capturedAt = SystemClock.elapsedRealtime()
                    val rotation = proxy.imageInfo.rotationDegrees
                    val width = if (rotation % 180 == 0) proxy.width else proxy.height
                    val height = if (rotation % 180 == 0) proxy.height else proxy.width
                    val scale = maxOf(width, height).toFloat()
                    try {
                        client.process(InputImage.fromMediaImage(media, rotation))
                            .addOnSuccessListener(main) { pose ->
                                if (generation == cameraGeneration) {
                                    val sample = PoseSample(
                                        pose.allPoseLandmarks.associate { landmark ->
                                            landmark.landmarkType to Joint(landmark.position.x / scale, landmark.position.y / scale,
                                                landmark.inFrameLikelihood)
                                        },
                                        width / scale, height / scale
                                    )
                                    onPose(sample, capturedAt)
                                }
                            }
                            .addOnFailureListener(main) {
                                if (generation == cameraGeneration) status.text = "No se pudo analizar la postura. Mejora la luz y reintenta."
                            }
                            .addOnCompleteListener(main) { proxy.close() }
                    } catch (_: RuntimeException) {
                        proxy.close()
                    }
                }
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, view, frames)
            } catch (_: Exception) {
                stopTracking()
                status.text = "No se pudo abrir la cámara. Revisa el permiso y cierra otras apps que la usen."
            }
        }, main)
    }

    private fun onPose(sample: PoseSample, capturedAt: Long) {
        lastResultAt = SystemClock.elapsedRealtime()
        skeleton.sample = sample
        skeleton.invalidate()
        if (capturedAt - movingCameraAt < 450) {
            session.resetTracking()
            status.text = "Apoya el teléfono para mantener la cámara fija."
            return
        }
        val current = ticket ?: return
        val before = session.count
        val result = session.frame(sample, capturedAt)
        status.text = result.instruction
        counter.text = "${result.count.coerceAtMost(current.goal)} / ${current.goal}"
        progress.progress = result.count
        if (result.count > before && exercise != Exercise.STRETCH) counter.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (result.count < current.goal) return

        if (!store.completeWorkout(current.id, current.goal)) {
            if (!store.isWorkoutCurrent(current.id)) {
                stopTracking()
                start.isEnabled = false
                status.text = "Esta sesión terminó. Vuelve a abrir la actividad para empezar de nuevo."
            }
            return
        }
        finished = true
        stopTracking()
        start.isEnabled = false
        status.text = "Meta completada. Ganaste ${current.rewardMinutes} minutos."
        AlertDialog.Builder(this).setTitle("Meta completada")
            .setMessage("Ganaste ${current.rewardMinutes} minutos para tus apps protegidas.")
            .setPositiveButton("Volver a FitBlock") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        val (x, y, z) = event.values
        val magnitude = sqrt(x * x + y * y + z * z)
        if ((event.sensor.type == Sensor.TYPE_ACCELEROMETER && abs(magnitude - 9.81f) > 3.6f) ||
            (event.sensor.type == Sensor.TYPE_GYROSCOPE && magnitude > .8f)
        ) movingCameraAt = SystemClock.elapsedRealtime()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun stopTracking() {
        active = false
        cameraGeneration++
        handler.removeCallbacks(watchdog)
        sensors.unregisterListener(this)
        analysis?.clearAnalyzer()
        analysis = null
        cameraProvider?.unbindAll()
        detector?.close()
        detector = null
        session.resetTracking()
        skeleton.sample = null
        skeleton.invalidate()
        start.text = "Reanudar actividad"
    }

    private fun exitDialog() {
        if (finished || ticket == null) {
            finish()
            return
        }
        if (active) {
            stopTracking()
            status.text = "Actividad pausada."
        }
        AlertDialog.Builder(this).setTitle("¿Terminar la actividad?")
            .setMessage("La meta incompleta no otorga minutos.")
            .setNegativeButton("Seguir aquí", null)
            .setPositiveButton("Terminar") { _, _ -> finish() }
            .show()
    }

    override fun onPause() {
        if (active) {
            stopTracking()
            status.text = "Cámara pausada. Pulsa Reanudar actividad para continuar."
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (active) stopTracking()
        if (!finished) ticket?.let { store.cancelWorkout(it.id) }
        executor.shutdown()
        super.onDestroy()
    }
}

class SkeletonView(context: Context) : View(context) {
    var sample: PoseSample? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val pose = sample ?: return
        val scale = minOf(width / pose.imageWidth, height / pose.imageHeight)
        val left = (width - pose.imageWidth * scale) / 2
        val top = (height - pose.imageHeight * scale) / 2
        fun x(joint: Joint) = left + (pose.imageWidth - joint.x) * scale
        fun y(joint: Joint) = top + joint.y * scale
        paint.strokeWidth = context.dp(3).toFloat()
        paint.color = C.green
        for ((a, b) in bones) {
            if (pose.has(a, b)) canvas.drawLine(x(pose[a]), y(pose[a]), x(pose[b]), y(pose[b]), paint)
        }
        paint.color = C.cyan
        for ((id, joint) in pose.joints) {
            if (id in bodyJoints && pose.has(id)) canvas.drawCircle(x(joint), y(joint), context.dp(4).toFloat(), paint)
        }
    }

    companion object {
        private val bones = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24,
            23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28)
        private val bodyJoints = bones.flatMap { listOf(it.first, it.second) }.toSet()
    }
}
