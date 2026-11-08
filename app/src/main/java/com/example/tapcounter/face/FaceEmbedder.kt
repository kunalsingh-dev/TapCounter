package com.example.tapcounter.face

import android.content.Context
import android.graphics.Bitmap
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

/**
 * MobileFaceNet (TensorFlow Lite): 112x112 RGB face crop -> 192 numbers that are close together
 * for the same person. The model takes a batch of 2, so the same crop is fed twice and only the
 * first result is used. Preprocessing is (value - 127.5) / 128 in RGB order; this was checked
 * on real photos (same person 0.70 similarity, different people about 0.1).
 */
class FaceEmbedder(context: Context) : AutoCloseable {
    private val interpreter = Interpreter(loadModel(context), Interpreter.Options().setNumThreads(2))
    private val input = ByteBuffer.allocateDirect(BATCH * SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = Array(BATCH) { FloatArray(FaceMath.EMBEDDING_SIZE) }
    private val pixels = IntArray(SIZE * SIZE)

    @Synchronized
    fun embed(face: Bitmap): FloatArray {
        val scaled = if (face.width == SIZE && face.height == SIZE) face else Bitmap.createScaledBitmap(face, SIZE, SIZE, true)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        repeat(BATCH) {
            for (p in pixels) {
                input.putFloat((((p shr 16) and 0xFF) - 127.5f) / 128f) // R
                input.putFloat((((p shr 8) and 0xFF) - 127.5f) / 128f)  // G
                input.putFloat(((p and 0xFF) - 127.5f) / 128f)          // B
            }
        }
        input.rewind()
        interpreter.run(input, output)
        return FaceMath.normalize(output[0])
    }

    override fun close() {
        interpreter.close()
    }

    private fun loadModel(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL_FILE).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }

    private companion object {
        const val MODEL_FILE = "MobileFaceNet.tflite"
        const val SIZE = 112
        const val BATCH = 2
    }
}
