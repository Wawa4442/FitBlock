package com.fitblock.app

data class PoseProgress(val count: Int, val instruction: String)

/** Uses frame times so a delayed inference cannot credit unobserved motion. */
class PoseSession(private val exercise: Exercise, private val challenges: List<Int>) {
    private val reps = RepGate(exercise.minimumUnitMillis)
    private var challengeIndex = 0
    private var challengeSince = -1L
    private var loweringArms = false
    private var lastFrameAt = -1L
    private var lastHoldAt = -1L
    private var holdMillis = 0L
    var count = 0
        private set

    fun resetTracking() {
        reps.reset()
        lastFrameAt = -1
        lastHoldAt = -1
        challengeSince = -1
        loweringArms = false
    }

    fun frame(pose: PoseSample, now: Long): PoseProgress {
        if (lastFrameAt >= 0 && now <= lastFrameAt) return PoseProgress(count, "Mantén tu posición.")
        if (lastFrameAt >= 0 && now - lastFrameAt > RepGate.MAX_GAP) resetTracking()
        lastFrameAt = now
        if (challengeIndex < challenges.size) return verifyMovement(pose, now)

        val form = PoseValidator.check(exercise, pose)
        if (exercise == Exercise.STRETCH) {
            if (form.active) {
                if (lastHoldAt >= 0) holdMillis += now - lastHoldAt
                lastHoldAt = now
                count = (holdMillis / 1000).toInt()
            } else {
                lastHoldAt = -1
            }
            val message = if (form.active) "Postura detectada. Sostén los brazos." else form.message
            return PoseProgress(count, message)
        }

        if (reps.update(now, form.neutral, form.active, form.valid)) count++
        val instruction = when {
            !form.valid -> form.message
            reps.awaitingReturn && exercise == Exercise.JACK -> "Cierra los pies y baja las manos."
            reps.awaitingReturn && exercise == Exercise.SQUAT -> "Vuelve a ponerte de pie."
            reps.awaitingReturn -> "Extiende los brazos."
            !reps.hasStart && exercise == Exercise.JACK -> "Empieza con pies juntos y brazos abajo."
            !reps.hasStart && exercise == Exercise.SQUAT -> "Empieza de pie, de perfil."
            !reps.hasStart -> "Empieza de perfil con los brazos extendidos."
            else -> form.message
        }
        return PoseProgress(count, instruction)
    }

    private fun verifyMovement(pose: PoseSample, now: Long): PoseProgress {
        val visible = pose.has(PoseValidator.LS, PoseValidator.RS, PoseValidator.LH, PoseValidator.RH,
            PoseValidator.LW, PoseValidator.RW, PoseValidator.LA, PoseValidator.RA)
        if (!visible) {
            challengeSince = -1
            return PoseProgress(count, "Colócate de frente con manos y pies a la vista.")
        }
        val correct = if (loweringArms) PoseValidator.armsDown(pose) else PoseValidator.challenge(pose, challenges[challengeIndex])
        if (!correct) {
            challengeSince = -1
        } else {
            if (challengeSince < 0) challengeSince = now
            val hold = if (loweringArms) 240 else 400
            if (now - challengeSince >= hold) {
                if (loweringArms) challengeIndex++
                loweringArms = !loweringArms
                challengeSince = -1
                if (challengeIndex == challenges.size) return PoseProgress(count, "Verificación lista. Ponte en posición para empezar.")
            }
        }
        if (loweringArms) return PoseProgress(count, "Baja ambos brazos.")
        val movement = when (challenges[challengeIndex]) {
            0 -> "levanta tu brazo izquierdo"
            1 -> "levanta tu brazo derecho"
            else -> "levanta ambos brazos"
        }
        return PoseProgress(count, "Verificación ${challengeIndex + 1}/${challenges.size} · $movement.")
    }
}
