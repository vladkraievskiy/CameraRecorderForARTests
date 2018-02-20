package com.kaa_solutions.camerarecorderforartests

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import java.lang.ref.WeakReference

class Camera2Manager(context: Context) {

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var cameraManager: CameraManager? = null
    private var cameraSession: CameraCaptureSession? = null

    private var cameraId: String = ""
    private val context = WeakReference(context)

    @SuppressLint("MissingPermission")
    fun startCameraPreview(textureView: TextureView) {
        initialize()

        cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {

            override fun onOpened(camera: CameraDevice?) {
                cameraDevice = camera

                startPreview(textureView)
            }

            override fun onDisconnected(camera: CameraDevice?) {
                camera?.close()
            }

            override fun onError(camera: CameraDevice?, error: Int) {
                Toast.makeText(context.get(), "Some error - $error. Camera - '${camera?.id}.", Toast.LENGTH_LONG).show()
            }
        }, cameraHandler)
    }

    fun startPreview(textureView: TextureView) {
        if (!textureView.isAvailable) {
            return
        }

        val surfaceTarget = Surface(textureView.surfaceTexture)
        val captureRequestBuilder = setCaptureRequestData(cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD))?.apply {
            addTarget(surfaceTarget)
        }

        cameraDevice?.createCaptureSession(arrayListOf(surfaceTarget), object : CameraCaptureSession.StateCallback() {

            override fun onConfigureFailed(session: CameraCaptureSession?) {
                Log.e("Camera", "Error configuring camera session")
            }

            override fun onConfigured(session: CameraCaptureSession?) {
                cameraSession = session?.apply {
                    setRepeatingRequest(captureRequestBuilder?.build(), null, cameraHandler)
                }
            }
        }, null)
    }

    fun startMediaRecordingSession(backgroundTextureView: TextureView, mediaRecorder: MediaRecorder?, onStartedRecording: () -> Unit) {
        if (!backgroundTextureView.isAvailable) {
            return
        }

        val surfaceTarget = Surface(backgroundTextureView.surfaceTexture)
        val captureRequestBuilder = setCaptureRequestData(cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD))?.apply {
            addTarget(surfaceTarget)
            addTarget(mediaRecorder?.surface)
        }

        cameraDevice?.createCaptureSession(arrayListOf(surfaceTarget, mediaRecorder?.surface), object : CameraCaptureSession.StateCallback() {

            override fun onConfigureFailed(session: CameraCaptureSession?) {
                Log.e("Camera", "Error configuring camera session")
            }

            override fun onConfigured(session: CameraCaptureSession?) {
                cameraSession = session?.apply {
                    setRepeatingRequest(captureRequestBuilder?.build(), null, cameraHandler)
                }

                onStartedRecording()
            }
        }, null)
    }

    fun startFrameByFrameRecordingSession(backgroundTextureView: TextureView, imageReader: ImageReader?, onStartedRecording: () -> Unit) {
        if (!backgroundTextureView.isAvailable) {
            return
        }

        val surfaceTarget = Surface(backgroundTextureView.surfaceTexture)
        val captureRequestBuilder = setCaptureRequestData(cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW))?.apply {
            addTarget(surfaceTarget)
            addTarget(imageReader?.surface)
        }

        cameraDevice?.createCaptureSession(arrayListOf(surfaceTarget, imageReader?.surface), object : CameraCaptureSession.StateCallback() {

            override fun onConfigureFailed(session: CameraCaptureSession?) {
                Log.e("Camera", "Error configuring camera session")
            }

            override fun onConfigured(session: CameraCaptureSession?) {
                cameraSession = session?.apply {
                    setRepeatingRequest(captureRequestBuilder?.build(), null, cameraHandler)
                }

                onStartedRecording()
            }
        }, null)
    }

    private fun setCaptureRequestData(requestBuilder: CaptureRequest.Builder?) =
            requestBuilder?.apply {
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
                set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_HIGH_QUALITY)
                set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_HIGH_QUALITY)
                set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
                set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
                set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
                set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }

    fun releaseCameraSession() {
        cameraDevice?.close()
        cameraThread?.quitSafely()
        cameraSession?.close()

        cameraSession = null

        try {
            cameraThread?.join()
        } finally {
            cameraThread = null
            cameraHandler = null
        }
    }


    private fun initialize() {
        cameraManager = context.get()?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager?.cameraIdList?.get(0) ?: ""

        if (cameraId.isEmpty()) {
            Toast.makeText(context.get(), "Unable to open camera because CameraId wasn't found.", Toast.LENGTH_LONG).show()
            return
        }

        cameraThread = HandlerThread("Camera thread").apply {
            start()
            cameraHandler = Handler(looper)
        }
    }
}