package pt.up.fe.asma.sueca.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Takes a single full quality still, for the cloud reader.
 *
 * Separate from the analysis stream on purpose: the live pipeline wants small frames it can chew
 * through thirty times a second, and this wants one good photograph. The still is rotated
 * upright and scaled down before it leaves the device — a phone's full sized photo is far more
 * detail than the task needs, and image tokens are what the request costs.
 */
class CameraCapture {

    val useCase: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    /**
     * @param maxEdge longest edge of the returned image. 1800 keeps a corner index legible while
     *   costing roughly a couple of thousand image tokens.
     */
    suspend fun takeJpeg(executor: Executor, maxEdge: Int = 1800, quality: Int = 85): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            useCase.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = runCatching { image.toUprightJpeg(maxEdge, quality) }.getOrNull()
                        image.close()
                        continuation.resume(bytes)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(null)
                    }
                },
            )
        }
}

private fun ImageProxy.toUprightJpeg(maxEdge: Int, quality: Int): ByteArray? {
    val buffer = planes.firstOrNull()?.buffer ?: return null
    val encoded = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size) ?: return null

    val scale = maxEdge.toFloat() / max(decoded.width, decoded.height)
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        if (imageInfo.rotationDegrees != 0) postRotate(imageInfo.rotationDegrees.toFloat())
    }

    val upright = if (matrix.isIdentity) {
        decoded
    } else {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    return ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }
}
