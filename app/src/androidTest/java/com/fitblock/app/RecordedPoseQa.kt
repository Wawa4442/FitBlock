package com.fitblock.app

import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RecordedPoseQa {
    @Test fun countsThreeContinuousJacksInRecordedExercise() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val reader = MediaMetadataRetriever()
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
        val session = PoseSession(Exercise.JACK, emptyList())
        try {
            context.assets.openFd("jacks.mp4").use { reader.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            for (ms in 0 until duration step 100) {
                val bitmap = reader.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                val pose = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 10, TimeUnit.SECONDS)
                val norm = maxOf(bitmap.width, bitmap.height).toFloat()
                val sample = PoseSample(pose.allPoseLandmarks.associate { landmark ->
                    landmark.landmarkType to Joint(landmark.position.x / norm, landmark.position.y / norm, landmark.inFrameLikelihood)
                }, bitmap.width / norm, bitmap.height / norm)
                session.frame(sample, ms)
                bitmap.recycle()
            }
            assertEquals(3, session.count)
        } finally {
            detector.close()
            reader.release()
        }
    }
}
