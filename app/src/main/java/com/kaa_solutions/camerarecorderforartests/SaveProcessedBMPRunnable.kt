package com.kaa_solutions.camerarecorderforartests

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix


class SaveProcessedBMPRunnable(
        private val byteArray: ByteArray,
        private val width: Int,
        private val height: Int,
        private val filePath: String,
        private val number: Int) : Runnable {

    override fun run() {
        val bitmapCache = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (i in 0 until height) {
            for (j in 0 until width) {
                val colorValue = (byteArray[width * i + j].toInt() and 0xFF)
                bitmapCache.setPixel(j, i, Color.rgb(colorValue, colorValue, colorValue))
            }
        }

        val matrix = Matrix()
        matrix.postRotate(270f)
        val rotatedBitmap = Bitmap.createBitmap(bitmapCache, 0, 0, bitmapCache.width, bitmapCache.height, matrix, true)

        AndroidBmpUtil.save(rotatedBitmap, "$filePath/$number.bmp")

        bitmapCache.recycle()
        rotatedBitmap.recycle()
    }
}