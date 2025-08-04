package com.example.documentscandemo

import android.graphics.*
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class DocumentAnalyzer(
    private val onDocumentDetected: (Array<PointF>?) -> Unit
) : ImageAnalysis.Analyzer {

    private val documentDetector = DocumentDetector()
    private var frameSkipCounter = 0
    private val FRAME_SKIP_RATE = 2 // Her 2 frame'de bir analiz et (daha hızlı tespit)

    override fun analyze(image: ImageProxy) {
        // Performans için frame atlama
        frameSkipCounter++
        if (frameSkipCounter % FRAME_SKIP_RATE != 0) {
            image.close()
            return
        }

        try {
            // ImageProxy'yi Bitmap'e çevir
            val bitmap = imageProxyToBitmap(image)
            
            // Belge tespiti yap
            val corners = documentDetector.detectDocument(bitmap)
            
            // Sonucu callback ile gönder
            onDocumentDetected(corners)
            
        } catch (e: Exception) {
            e.printStackTrace()
            onDocumentDetected(null)
        } finally {
            image.close()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        // YUV_420_888 formatını RGB'ye çevir
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotasyon düzeltmesi
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}
