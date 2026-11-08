package com.example.tapcounter.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Result of looking at one camera frame. */
data class FaceFrame(
    val faceCount: Int,
    /** Similarity of the largest face to the enrolled owner, null if unknown. */
    val similarity: Float?,
    val eyesOpen: Boolean?,
    /** Single, frontal, big enough: good enough to enrol or to trust. */
    val quality: Boolean,
    val embedding: FloatArray?,
    /** Only filled while enrolling: the same face framed slightly differently. */
    val extraEmbeddings: List<FloatArray> = emptyList(),
) {
    fun toObservation() = FaceObservation(faceCount, similarity, eyesOpen)

    companion object {
        /** Used for every failure: "no face" can never unlock anything. */
        val NONE = FaceFrame(0, null, null, false, null)
    }
}

/** Face detection (ML Kit) + recognition (MobileFaceNet). Created lazily; [ready] is false if the model is missing. */
class FaceEngine(context: Context) : AutoCloseable {
    private val embedder: FaceEmbedder? = try {
        FaceEmbedder(context.applicationContext)
    } catch (e: Throwable) {
        null
    }

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f) // only faces reasonably close to the phone count
            .build(),
    )

    val ready: Boolean get() = embedder != null

    /** Never throws. Call from a background thread (it waits for the detector). */
    fun process(upright: Bitmap, templates: List<FloatArray>, augment: Boolean = false): FaceFrame {
        val e = embedder ?: return FaceFrame.NONE
        return try {
            val faces: List<Face> = Tasks.await(detector.process(InputImage.fromBitmap(upright, 0)))
            val main = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return FaceFrame.NONE
            val embedding = e.embed(cropSquare(upright, main.boundingBox))
            val extras = if (augment) {
                listOf(Triple(1.3f, 0.08f, 0f), Triple(1.3f, 0f, 0.08f), Triple(1.5f, 0f, 0f)).map { (s, dx, dy) ->
                    e.embed(cropSquare(upright, main.boundingBox, s, dx, dy))
                }
            } else {
                emptyList()
            }
            val similarity = if (templates.isEmpty()) null else FaceMath.bestSimilarity(templates, embedding)
            val left = main.leftEyeOpenProbability
            val right = main.rightEyeOpenProbability
            val eyes = if (left == null || right == null) null else (left > 0.5f && right > 0.5f)
            val widthFraction = main.boundingBox.width().toFloat() / upright.width
            val frontal = abs(main.headEulerAngleY) <= 25f && abs(main.headEulerAngleZ) <= 20f
            FaceFrame(
                faceCount = faces.size,
                similarity = similarity,
                eyesOpen = eyes,
                quality = faces.size == 1 && widthFraction >= 0.22f && frontal && eyes != false,
                embedding = embedding,
                extraEmbeddings = extras,
            )
        } catch (t: Throwable) {
            FaceFrame.NONE
        }
    }

    override fun close() {
        detector.close()
        embedder?.close()
    }

    /** Square crop around the face (default 30% margin, as validated); [dx]/[dy] shift it by a fraction of the face size. */
    private fun cropSquare(src: Bitmap, box: Rect, scale: Float = 1.3f, dx: Float = 0f, dy: Float = 0f): Bitmap {
        val side = (max(box.width(), box.height()) * scale).toInt().coerceIn(1, min(src.width, src.height))
        val cx = box.centerX() + (dx * box.width()).toInt()
        val cy = box.centerY() + (dy * box.height()).toInt()
        val left = (cx - side / 2).coerceIn(0, src.width - side)
        val top = (cy - side / 2).coerceIn(0, src.height - side)
        return Bitmap.createBitmap(src, left, top, side, side)
    }
}
