package com.kaa_solutions.camerarecorderforartests

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaRecorder
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.support.v4.content.PermissionChecker
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.webkit.MimeTypeMap
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private lateinit var cameraManager: Camera2Manager
    private val previewSize = Size(640, 480)
    private var isRecordingVideo = false
    private var recordingVideoTime = 0
    private var timer: Timer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var videoFilePath: String = ""

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            surface?.setDefaultBufferSize(previewSize.width, previewSize.height)
            configureTransform(width, height)
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraManager = Camera2Manager(this)

        startStopRecodingButton.setOnClickListener {
            if (isRecordingVideo) {
                stopRecordingVideo()
                cameraManager.startPreview(cameraTexture)
            } else {
                startRecodingVideo()
            }
        }
    }

    private fun startRecodingVideo() {
        if (isRecordingVideo) {
            return
        }

        isRecordingVideo = true
        recordingVideoTime = 0
        startStopRecodingButton.text = "Stop recording"
        infoText.visibility = View.VISIBLE

        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        infoText.text = "Recording video, time: ${recordingVideoTime++}"
                    }
                }
            }, 0L, 1000L)
        }

        videoFilePath = getVideoFilePath()
        mediaRecorder = createMediaRecorder()

        cameraManager.startMediaRecordingSession(cameraTexture, mediaRecorder) {
            mediaRecorder?.start()
        }
    }

    private fun createMediaRecorder() = MediaRecorder().apply {
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
        setVideoEncodingBitRate(10000000)
        setOrientationHint(270)
        setVideoFrameRate(30)
        setOutputFile(videoFilePath)
        setVideoSize(previewSize.width, previewSize.height)
        setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
        prepare()
    }

    private fun getVideoFilePath() = File(
            getExternalFilesDir(""),
            "video_${
            SimpleDateFormat("ddMMyyyy_hhmmss", Locale.CANADA).format(Date(System.currentTimeMillis()))
            }.mp4"
    ).absolutePath

    private fun stopRecordingVideo() {
        if (!isRecordingVideo) {
            return
        }

        isRecordingVideo = false
        timer?.cancel()

        infoText.visibility = View.GONE
        startStopRecodingButton.text = "Start recording"

        mediaRecorder?.stop()
        mediaRecorder = null

        showRecordedVideoResultDialog()
    }

    private fun showRecordedVideoResultDialog() {
        val videoFile = File(videoFilePath)

        val dialog = AlertDialog.Builder(this)
                .setTitle("Video recorded successfully.")
                .setMessage("Video duration: ${recordingVideoTime}s.; size: ${videoFile.length() / 1024f / 1024f}mb.")
                .create()

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Share") { dialogInterface, _ ->
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.putExtra(
                    Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(
                            this@MainActivity,
                            "$packageName.provider",
                            videoFile
                    )
            )
            shareIntent.type = getMimeTypeFromFile(videoFile) ?: "*/*"
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(shareIntent, "Share video with:"))

            dialogInterface.dismiss()
        }

        dialog.show()
    }

    private fun getMimeTypeFromFile(file: File): String? {
        var type: String? = null
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath)

        if (fileExtension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
        }

        return type
    }

    override fun onResume() {
        super.onResume()

        if (cameraTexture.isAvailable) {
            startCamera()
        } else {
            cameraTexture.surfaceTextureListener = surfaceTextureListener
        }
    }

    private fun startCamera() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_DENIED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            return
        }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            cameraTexture.setAspectRatio(previewSize.width, previewSize.height)
        } else {
            cameraTexture.setAspectRatio(previewSize.height, previewSize.width)
        }

        cameraManager.startCameraPreview(cameraTexture)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != 100) {
            return
        }

        if (grantResults[permissions.indexOf(Manifest.permission.CAMERA)] != PermissionChecker.PERMISSION_GRANTED) {
            return
        }

        cameraManager.startCameraPreview(cameraTexture)
    }


    override fun onPause() {
        stopRecordingVideo()
        super.onPause()

        cameraManager.releaseCameraSession()
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(viewHeight.toFloat() / previewSize.height, viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        }
        cameraTexture.setTransform(matrix)
    }

}
