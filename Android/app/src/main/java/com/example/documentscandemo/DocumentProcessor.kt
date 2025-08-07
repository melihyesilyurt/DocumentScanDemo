package com.example.documentscandemo

import android.graphics.*
import kotlin.math.*

class DocumentProcessor {

    fun cropAndCorrectPerspective(
        originalBitmap: Bitmap,
        corners: Array<PointF>
    ): Bitmap? {
        try {
            // Köşeleri sırala
            val orderedCorners = orderPoints(corners)

            // Hedef boyutları hesapla
            val (width, height) = calculateTargetDimensions(orderedCorners)

            // Perspektif dönüşümü uygula
            val correctedBitmap = applyPerspectiveTransform(originalBitmap, orderedCorners, width, height)

            // Görüntü iyileştirmeleri uygula
            return enhanceDocument(correctedBitmap)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun applyPerspectiveTransform(
        bitmap: Bitmap,
        corners: Array<PointF>,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        // Basit perspektif dönüşümü - Matrix kullanarak
        val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // Kaynak köşeler
        val src = floatArrayOf(
            corners[0].x, corners[0].y, // Sol-üst
            corners[1].x, corners[1].y, // Sağ-üst
            corners[2].x, corners[2].y, // Sağ-alt
            corners[3].x, corners[3].y  // Sol-alt
        )

        // Hedef köşeler
        val dst = floatArrayOf(
            0f, 0f,                           // Sol-üst
            targetWidth.toFloat(), 0f,        // Sağ-üst
            targetWidth.toFloat(), targetHeight.toFloat(), // Sağ-alt
            0f, targetHeight.toFloat()        // Sol-alt
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return resultBitmap
    }

    private fun orderPoints(points: Array<PointF>): Array<PointF> {
        val ordered = Array(4) { PointF() }
        
        // Toplam ve fark hesapla
        val sums = points.map { it.x + it.y }
        val diffs = points.map { it.x - it.y }
        
        // Sol-üst: minimum toplam
        val topLeftIndex = sums.indexOf(sums.minOrNull())
        ordered[0] = points[topLeftIndex]
        
        // Sağ-alt: maksimum toplam
        val bottomRightIndex = sums.indexOf(sums.maxOrNull())
        ordered[2] = points[bottomRightIndex]
        
        // Sağ-üst: maksimum fark
        val topRightIndex = diffs.indexOf(diffs.maxOrNull())
        ordered[1] = points[topRightIndex]
        
        // Sol-alt: minimum fark
        val bottomLeftIndex = diffs.indexOf(diffs.minOrNull())
        ordered[3] = points[bottomLeftIndex]
        
        return ordered
    }

    private fun calculateTargetDimensions(corners: Array<PointF>): Pair<Int, Int> {
        // Üst ve alt kenar uzunluklarını hesapla
        val topWidth = calculateDistance(corners[0], corners[1])
        val bottomWidth = calculateDistance(corners[3], corners[2])
        val maxWidth = max(topWidth, bottomWidth)

        // Sol ve sağ kenar uzunluklarını hesapla
        val leftHeight = calculateDistance(corners[0], corners[3])
        val rightHeight = calculateDistance(corners[1], corners[2])
        val maxHeight = max(leftHeight, rightHeight)

        return Pair(maxWidth.toInt(), maxHeight.toInt())
    }

    private fun calculateDistance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    fun enhanceDocument(bitmap: Bitmap): Bitmap {
        // Orijinal renkleri %100 koruyarak hiçbir değişiklik yapma
        // Kullanıcı orijinal renkli belgeyi görsün
        return bitmap
    }

    fun saveProcessedImage(bitmap: Bitmap, fileName: String): String? {
        try {
            // Implement save logic here
            // For now, return a dummy path
            return "/storage/emulated/0/Documents/$fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
