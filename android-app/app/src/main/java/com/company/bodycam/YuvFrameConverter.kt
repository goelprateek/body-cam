package com.company.bodycam

import androidx.camera.core.ImageProxy
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import java.nio.ByteBuffer

object YuvFrameConverter {

    fun toVideoFrame(imageProxy: ImageProxy): VideoFrame? {
        val image = imageProxy.image ?: return null
        val width = image.width
        val height = image.height
        val buffer = JavaI420Buffer.allocate(width, height)

        copyPlane(
            source = image.planes[0].buffer,
            rowStride = image.planes[0].rowStride,
            pixelStride = image.planes[0].pixelStride,
            width = width,
            height = height,
            target = buffer.dataY,
            targetStride = buffer.strideY
        )
        copyPlane(
            source = image.planes[1].buffer,
            rowStride = image.planes[1].rowStride,
            pixelStride = image.planes[1].pixelStride,
            width = width / 2,
            height = height / 2,
            target = buffer.dataU,
            targetStride = buffer.strideU
        )
        copyPlane(
            source = image.planes[2].buffer,
            rowStride = image.planes[2].rowStride,
            pixelStride = image.planes[2].pixelStride,
            width = width / 2,
            height = height / 2,
            target = buffer.dataV,
            targetStride = buffer.strideV
        )

        return VideoFrame(buffer, imageProxy.imageInfo.rotationDegrees, System.nanoTime())
    }

    private fun copyPlane(
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        target: ByteBuffer,
        targetStride: Int
    ) {
        val sourceBuffer = source.duplicate()
        for (row in 0 until height) {
            for (column in 0 until width) {
                val sourceIndex = row * rowStride + column * pixelStride
                val targetIndex = row * targetStride + column
                target.put(targetIndex, sourceBuffer.get(sourceIndex))
            }
        }
    }
}
