package com.kriyanshtech.bodycam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

fun ImageProxy.toBitmapCompatible(): Bitmap {
    val nv21 = yuv420888ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val pixelCount = image.width * image.height
    val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
    val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
    val planes = image.planes

    // Y-plane
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    var offset = 0
    yBuffer.get(outputBuffer, offset, ySize)
    offset += ySize

    // Interleave V and U for NV21
    val vRowStride = planes[2].rowStride
    val vPixelStride = planes[2].pixelStride
    val uRowStride = planes[1].rowStride
    val uPixelStride = planes[1].pixelStride

    for (row in 0 until image.height / 2) {
        for (col in 0 until image.width / 2) {
            val vIndex = row * vRowStride + col * vPixelStride
            val uIndex = row * uRowStride + col * uPixelStride

            outputBuffer[offset++] = vBuffer.get(vIndex)
            outputBuffer[offset++] = uBuffer.get(uIndex)
        }
    }

    return outputBuffer
}
