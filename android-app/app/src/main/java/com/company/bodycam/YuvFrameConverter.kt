package com.company.bodycam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object YuvFrameConverter {

    fun toBitmap(imageProxy: ImageProxy): Bitmap? {
        if (imageProxy.format != ImageFormat.YUV_420_888) {
            return null
        }
        val width = imageProxy.width
        val height = imageProxy.height
        val nv21 = yuv420888ToNv21(imageProxy)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val jpegBuffer = ByteArrayOutputStream()
        val compressed = yuvImage.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, jpegBuffer)
        if (!compressed) {
            return null
        }
        val jpegBytes = jpegBuffer.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        val ySize = width * height
        val output = ByteArray(ySize + (width * height / 2))
        copyPlane(
            plane = imageProxy.planes[0],
            width = width,
            height = height,
            output = output,
            offset = 0,
            outputPixelStride = 1
        )
        copyPlane(
            plane = imageProxy.planes[2],
            width = width / 2,
            height = height / 2,
            output = output,
            offset = ySize,
            outputPixelStride = 2
        )
        copyPlane(
            plane = imageProxy.planes[1],
            width = width / 2,
            height = height / 2,
            output = output,
            offset = ySize + 1,
            outputPixelStride = 2
        )
        return output
    }

    private fun copyPlane(
        plane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        output: ByteArray,
        offset: Int,
        outputPixelStride: Int
    ) {
        val sourceBuffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (row in 0 until height) {
            for (column in 0 until width) {
                val sourceIndex = row * rowStride + column * pixelStride
                val targetIndex = offset + (row * width + column) * outputPixelStride
                output[targetIndex] = sourceBuffer.get(sourceIndex)
            }
        }
    }

    private const val JPEG_QUALITY = 75
}
