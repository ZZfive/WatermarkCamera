package com.watermarkcamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var previewView: PreviewView? = null
    private var reusableByteArray: ByteArray? = null

    val isUsingFrontCamera: Boolean
        get() = currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA

    suspend fun startCamera(previewView: PreviewView): Boolean {
        this.previewView = previewView
        return bindCamera(currentCameraSelector)
    }

    suspend fun switchCamera(previewView: PreviewView): Boolean {
        this.previewView = previewView
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        return bindCamera(currentCameraSelector)
    }

    private suspend fun bindCamera(cameraSelector: CameraSelector): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    val targetRotation = previewView?.display?.rotation ?: Surface.ROTATION_0

                    preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .setTargetRotation(targetRotation)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView?.surfaceProvider)
                        }

                    imageCapture = ImageCapture.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .setTargetRotation(targetRotation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(90)
                        .build()

                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    continuation.resume(true)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun updateRotation(previewView: PreviewView? = this.previewView) {
        this.previewView = previewView ?: this.previewView
        val targetRotation = this.previewView?.display?.rotation ?: Surface.ROTATION_0
        preview?.targetRotation = targetRotation
        imageCapture?.targetRotation = targetRotation
    }

    suspend fun takePictureToBitmap(): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            val imageCapture = imageCapture ?: run {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        var bitmap = imageProxyToBitmap(imageProxy)
                        if (isUsingFrontCamera && bitmap != null) {
                            val flipped = flipHorizontally(bitmap)
                            if (flipped !== bitmap) {
                                bitmap.recycle()
                            }
                            bitmap = flipped
                        }
                        imageProxy.close()
                        continuation.resume(bitmap)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(null)
                    }
                }
            )
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val size = buffer.remaining()

        val bytes = if (reusableByteArray?.size ?: 0 >= size) {
            reusableByteArray!!
        } else {
            ByteArray(size.also { reusableByteArray = ByteArray(it) })
        }
        buffer.get(bytes, 0, size)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, size) ?: return null
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            rotated.density = bitmap.density
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            rotated
        } else {
            bitmap
        }
    }

    private fun flipHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            preScale(-1f, 1f)
        }
        val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        flipped.density = bitmap.density
        return flipped
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
    }
}
