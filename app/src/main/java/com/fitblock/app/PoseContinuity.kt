package com.fitblock.app

import kotlin.math.hypot

/** A person swap or abrupt framing jump must not complete the preceding repetition. */
class PoseContinuity {
    private var center: Joint? = null
    private var torso = 0f

    fun reset() { center = null; torso = 0f }

    fun accept(pose: PoseSample): Boolean {
        val ids = listOf(PoseValidator.LS, PoseValidator.RS, PoseValidator.LH, PoseValidator.RH)
        if (!pose.has(*ids.toIntArray())) return true
        val shoulder = Joint((pose[ids[0]].x + pose[ids[1]].x) / 2, (pose[ids[0]].y + pose[ids[1]].y) / 2)
        val hip = Joint((pose[ids[2]].x + pose[ids[3]].x) / 2, (pose[ids[2]].y + pose[ids[3]].y) / 2)
        val size = hypot(shoulder.x - hip.x, shoulder.y - hip.y)
        val next = Joint((shoulder.x + hip.x) / 2, (shoulder.y + hip.y) / 2)
        val old = center
        val continuous = old == null || torso < .03f || size < .03f ||
            (size / torso in .45f..2.2f && hypot(next.x - old.x, next.y - old.y) < maxOf(size, torso) * 1.2f)
        center = next; torso = size
        return continuous
    }
}
