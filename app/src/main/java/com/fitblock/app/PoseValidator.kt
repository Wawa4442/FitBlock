package com.fitblock.app

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

data class Joint(val x: Float, val y: Float, val confidence: Float = 1f)
data class PoseSample(val joints: Map<Int, Joint>, val imageWidth: Float = 1f, val imageHeight: Float = 1f) {
    fun has(vararg ids: Int) = ids.all { id ->
        joints[id]?.let { point ->
            point.confidence >= .50f && point.x in 0f..imageWidth && point.y in 0f..imageHeight
        } == true
    }
    operator fun get(id: Int): Joint = joints[id] ?: Joint(0f, 0f, 0f)
}

data class Form(val valid: Boolean, val neutral: Boolean = false, val active: Boolean = false, val message: String)

object PoseValidator {
    // ML Kit landmark identifiers. Both axes share the same normalization scale.
    const val LS = 11
    const val RS = 12
    const val LE = 13
    const val RE = 14
    const val LW = 15
    const val RW = 16
    const val LH = 23
    const val RH = 24
    const val LK = 25
    const val RK = 26
    const val LA = 27
    const val RA = 28

    private data class Side(val shoulder: Int, val elbow: Int, val wrist: Int, val hip: Int, val knee: Int, val ankle: Int)
    private val sides = listOf(Side(LS, LE, LW, LH, LK, LA), Side(RS, RE, RW, RH, RK, RA))

    fun angle(a: Joint, b: Joint, c: Joint): Double {
        val ux = (a.x - b.x).toDouble()
        val uy = (a.y - b.y).toDouble()
        val vx = (c.x - b.x).toDouble()
        val vy = (c.y - b.y).toDouble()
        val denominator = hypot(ux, uy) * hypot(vx, vy)
        return if (denominator < .00001) 0.0 else Math.toDegrees(acos(((ux * vx + uy * vy) / denominator).coerceIn(-1.0, 1.0)))
    }

    fun check(exercise: Exercise, pose: PoseSample): Form = when (exercise) {
        Exercise.SQUAT, Exercise.PUSHUP -> sideForm(exercise, pose)
        Exercise.JACK, Exercise.STRETCH -> frontForm(exercise, pose)
        Exercise.WALK -> Form(false, message = "Usa los sensores para la caminata.")
    }

    private fun sideForm(exercise: Exercise, pose: PoseSample): Form {
        fun needed(side: Side) = if (exercise == Exercise.SQUAT) {
            intArrayOf(side.shoulder, side.hip, side.knee, side.ankle)
        } else {
            intArrayOf(side.shoulder, side.elbow, side.wrist, side.hip, side.ankle)
        }
        val side = sides.filter { pose.has(*needed(it)) }
            .maxByOrNull { side -> needed(side).sumOf { pose[it].confidence.toDouble() } }
            ?: return Form(false, message = "Colócate de perfil con el cuerpo completo a la vista.")

        val shoulder = pose[side.shoulder]
        val hip = pose[side.hip]
        val ankle = pose[side.ankle]
        if (exercise == Exercise.SQUAT) {
            val knee = pose[side.knee]
            val legLength = hypot(hip.x - knee.x, hip.y - knee.y) + hypot(knee.x - ankle.x, knee.y - ankle.y)
            if (legLength < .08f || shoulder.y >= hip.y || hip.y >= ankle.y) {
                return Form(false, message = "Mantén hombros, cadera y pies visibles de perfil.")
            }
            val kneeAngle = angle(hip, knee, ankle)
            val down = kneeAngle in 50.0..120.0 && hip.y >= knee.y - .30f * legLength
            return Form(true, kneeAngle >= 153, down, "Baja la cadera y vuelve a extender las piernas.")
        }

        val horizontalSpan = abs(shoulder.x - ankle.x)
        if (horizontalSpan < .12f || horizontalSpan < abs(shoulder.y - ankle.y) * 1.15f ||
            angle(shoulder, hip, ankle) < 143
        ) {
            return Form(false, message = "Mantén hombros, cadera y tobillos alineados de perfil.")
        }
        val elbowAngle = angle(shoulder, pose[side.elbow], pose[side.wrist])
        return Form(true, elbowAngle >= 146, elbowAngle in 40.0..112.0, "Flexiona los brazos y vuelve a extenderlos.")
    }

    private fun frontForm(exercise: Exercise, pose: PoseSample): Form {
        if (!pose.has(LS, RS, LW, RW, LH, RH, LA, RA)) {
            return Form(false, message = "Colócate de frente con manos y pies a la vista.")
        }
        val shoulders = (pose[LS].y + pose[RS].y) / 2
        val torso = (pose[LH].y + pose[RH].y) / 2 - shoulders
        val shoulderWidth = abs(pose[LS].x - pose[RS].x)
        if (torso < .04f || shoulderWidth < torso * .30f) {
            return Form(false, message = "Ponte de frente para que se distingan ambos lados del cuerpo.")
        }
        val armsDown = pose[LW].y > shoulders + torso * .20f && pose[RW].y > shoulders + torso * .20f
        if (exercise == Exercise.JACK) {
            val feetWidth = abs(pose[LA].x - pose[RA].x)
            val armsUp = pose[LW].y < shoulders - torso * .16f && pose[RW].y < shoulders - torso * .16f
            val feetTogether = feetWidth <= shoulderWidth * 1.12f
            val feetApart = feetWidth >= shoulderWidth * 1.40f && feetWidth >= torso * .70f
            return Form(true, armsDown && feetTogether, armsUp && feetApart, "Abre los pies y levanta las manos por encima de los hombros.")
        }

        val armsUp = pose[LW].y < shoulders - torso * .60f && pose[RW].y < shoulders - torso * .60f
        val extended = pose.has(LE, RE) &&
            angle(pose[LS], pose[LE], pose[LW]) >= 138 && angle(pose[RS], pose[RE], pose[RW]) >= 138
        return Form(true, armsDown, armsUp && extended, "Mantén ambos brazos extendidos por encima de la cabeza.")
    }

    fun challenge(pose: PoseSample, arm: Int): Boolean {
        if (!pose.has(LS, RS, LW, RW, LH, RH, LA, RA)) return false
        val left = pose[LW].y < pose[LS].y - .02f
        val right = pose[RW].y < pose[RS].y - .02f
        return when (arm) {
            0 -> left && pose[RW].y > pose[RS].y
            1 -> right && pose[LW].y > pose[LS].y
            else -> left && right
        }
    }

    fun armsDown(pose: PoseSample): Boolean = pose.has(LS, RS, LW, RW) &&
        pose[LW].y > pose[LS].y && pose[RW].y > pose[RS].y
}
