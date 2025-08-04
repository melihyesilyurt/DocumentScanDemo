package com.example.documentscandemo

import android.graphics.*
import kotlin.math.*

class SimpleDocumentDetector {

    fun detectDocument(bitmap: Bitmap): Array<PointF>? {
        try {
            android.util.Log.d("SimpleDocumentDetector", "Belge tespiti başlıyor - Boyut: ${bitmap.width}x${bitmap.height}")

            // Görüntüyü küçült (performans için)
            val scaledBitmap = scaleBitmap(bitmap, 800)
            val scaleX = bitmap.width.toFloat() / scaledBitmap.width.toFloat()
            val scaleY = bitmap.height.toFloat() / scaledBitmap.height.toFloat()

            android.util.Log.d("SimpleDocumentDetector", "Ölçeklenmiş boyut: ${scaledBitmap.width}x${scaledBitmap.height}")

            // Birden fazla yöntem dene
            var corners = tryContourDetection(scaledBitmap)

            if (corners == null) {
                android.util.Log.d("SimpleDocumentDetector", "Contour detection başarısız, edge detection deneniyor")
                corners = tryEdgeDetection(scaledBitmap)
            }

            if (corners == null) {
                android.util.Log.d("SimpleDocumentDetector", "Edge detection başarısız, corner detection deneniyor")
                corners = tryCornerDetection(scaledBitmap)
            }

            if (corners == null) {
                android.util.Log.d("SimpleDocumentDetector", "Tüm yöntemler başarısız, varsayılan dörtgen döndürülüyor")
                corners = getDefaultRectangle(scaledBitmap)
            }

            return corners?.let { detectedCorners ->
                // Köşeleri orijinal boyuta ölçekle
                android.util.Log.d("SimpleDocumentDetector", "Belge tespit edildi, ölçeklendiriliyor")
                detectedCorners.map { corner ->
                    PointF(corner.x * scaleX, corner.y * scaleY)
                }.toTypedArray()
            }

        } catch (e: Exception) {
            android.util.Log.e("SimpleDocumentDetector", "Hata", e)
            return null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val scale = kotlin.math.min(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(grayBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return grayBitmap
    }

    private fun increaseContrast(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val contrastBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(contrastBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        
        // Kontrast artır
        colorMatrix.set(floatArrayOf(
            2f, 0f, 0f, 0f, -50f,
            0f, 2f, 0f, 0f, -50f,
            0f, 0f, 2f, 0f, -50f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return contrastBitmap
    }

    private fun simpleEdgeDetection(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val edgePixels = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                // Basit gradient hesaplama
                val current = getGrayValue(pixels[idx])
                val right = getGrayValue(pixels[idx + 1])
                val bottom = getGrayValue(pixels[(y + 1) * width + x])
                
                val gradientX = abs(current - right)
                val gradientY = abs(current - bottom)
                val gradient = gradientX + gradientY
                
                val edgeValue = if (gradient > 30) 255 else 0
                edgePixels[idx] = Color.rgb(edgeValue, edgeValue, edgeValue)
            }
        }
        
        return Bitmap.createBitmap(edgePixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun getGrayValue(pixel: Int): Int {
        return Color.red(pixel) // Gri tonlamada R=G=B
    }

    private fun tryContourDetection(bitmap: Bitmap): Array<PointF>? {
        try {
            // Gri tonlama ve kontrast artırma
            val grayBitmap = toGrayscale(bitmap)
            val contrastBitmap = increaseContrast(grayBitmap)

            // Threshold uygula
            val binaryBitmap = applyThreshold(contrastBitmap)

            // Konturları bul
            val contours = findContours(binaryBitmap)
            android.util.Log.d("SimpleDocumentDetector", "Bulunan kontur sayısı: ${contours.size}")

            // En büyük dörtgen konturu bul
            return findBestRectangleFromContours(contours, bitmap.width, bitmap.height)

        } catch (e: Exception) {
            android.util.Log.e("SimpleDocumentDetector", "Contour detection hatası", e)
            return null
        }
    }

    private fun tryEdgeDetection(bitmap: Bitmap): Array<PointF>? {
        try {
            val grayBitmap = toGrayscale(bitmap)
            val contrastBitmap = increaseContrast(grayBitmap)
            val edges = simpleEdgeDetection(contrastBitmap)
            return findLargestRectangle(edges)
        } catch (e: Exception) {
            android.util.Log.e("SimpleDocumentDetector", "Edge detection hatası", e)
            return null
        }
    }

    private fun tryCornerDetection(bitmap: Bitmap): Array<PointF>? {
        try {
            // Harris corner detection benzeri basit yaklaşım
            val grayBitmap = toGrayscale(bitmap)
            val corners = findCornerPoints(grayBitmap)

            return if (corners.size >= 4) {
                selectBestFourCorners(corners, bitmap.width, bitmap.height)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleDocumentDetector", "Corner detection hatası", e)
            return null
        }
    }

    private fun getDefaultRectangle(bitmap: Bitmap): Array<PointF> {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val margin = kotlin.math.min(width, height) * 0.15f

        return arrayOf(
            PointF(margin, margin),
            PointF(width - margin, margin),
            PointF(width - margin, height - margin),
            PointF(margin, height - margin)
        )
    }

    private fun findCornerPoints(bitmap: Bitmap): List<PointF> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val corners = mutableListOf<PointF>()

        // Grid tabanlı köşe arama
        val stepX = width / 20
        val stepY = height / 20

        for (y in stepY until height - stepY step stepY) {
            for (x in stepX until width - stepX step stepX) {
                if (isCornerPoint(pixels, x, y, width, height)) {
                    corners.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }

        return corners
    }

    private fun isCornerPoint(pixels: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        val windowSize = 3
        var gradientX = 0
        var gradientY = 0

        for (dy in -windowSize..windowSize) {
            for (dx in -windowSize..windowSize) {
                val nx = x + dx
                val ny = y + dy

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    val pixel = Color.red(pixels[ny * width + nx])
                    gradientX += dx * pixel
                    gradientY += dy * pixel
                }
            }
        }

        val magnitude = kotlin.math.sqrt((gradientX * gradientX + gradientY * gradientY).toDouble())
        return magnitude > 1000 // Threshold for corner detection
    }

    private fun selectBestFourCorners(corners: List<PointF>, width: Int, height: Int): Array<PointF> {
        if (corners.size <= 4) {
            return corners.toTypedArray()
        }

        // En uç noktaları seç
        val topLeft = corners.minByOrNull { it.x + it.y }!!
        val topRight = corners.maxByOrNull { it.x - it.y }!!
        val bottomRight = corners.maxByOrNull { it.x + it.y }!!
        val bottomLeft = corners.minByOrNull { it.x - it.y }!!

        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun applyThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val binaryPixels = IntArray(width * height)

        for (i in pixels.indices) {
            val gray = Color.red(pixels[i]) // Gri tonlamada R=G=B
            val binary = if (gray > 128) 255 else 0
            binaryPixels[i] = Color.rgb(binary, binary, binary)
        }

        return Bitmap.createBitmap(binaryPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun findContours(bitmap: Bitmap): List<List<PointF>> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val contours = mutableListOf<List<PointF>>()
        val visited = BooleanArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                if (!visited[idx] && isEdgePixel(pixels, x, y, width)) {
                    val contour = traceContour(pixels, x, y, width, height, visited)
                    if (contour.size > 50) { // Minimum contour size
                        contours.add(contour)
                    }
                }
            }
        }

        return contours
    }

    private fun isEdgePixel(pixels: IntArray, x: Int, y: Int, width: Int): Boolean {
        val idx = y * width + x
        val current = Color.red(pixels[idx])

        // Kenar tespiti - komşu piksellerle fark kontrolü
        val neighbors = listOf(
            if (x > 0) Color.red(pixels[idx - 1]) else current,
            if (x < width - 1) Color.red(pixels[idx + 1]) else current,
            if (y > 0) Color.red(pixels[idx - width]) else current,
            if (y < width - 1) Color.red(pixels[idx + width]) else current
        )

        return neighbors.any { kotlin.math.abs(it - current) > 50 }
    }

    private fun traceContour(
        pixels: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        visited: BooleanArray
    ): List<PointF> {
        val contour = mutableListOf<PointF>()
        val stack = mutableListOf(Pair(startX, startY))

        while (stack.isNotEmpty() && contour.size < 500) {
            val (x, y) = stack.removeAt(stack.size - 1)
            val idx = y * width + x

            if (x < 0 || x >= width || y < 0 || y >= height || visited[idx]) {
                continue
            }

            if (isEdgePixel(pixels, x, y, width)) {
                visited[idx] = true
                contour.add(PointF(x.toFloat(), y.toFloat()))

                // 4-connected neighbors
                stack.add(Pair(x + 1, y))
                stack.add(Pair(x - 1, y))
                stack.add(Pair(x, y + 1))
                stack.add(Pair(x, y - 1))
            }
        }

        return contour
    }

    private fun findBestRectangleFromContours(
        contours: List<List<PointF>>,
        width: Int,
        height: Int
    ): Array<PointF>? {
        var bestRectangle: Array<PointF>? = null
        var maxScore = 0.0

        for (contour in contours) {
            if (contour.size < 4) continue

            val rectangle = approximateToRectangle(contour)
            if (rectangle != null) {
                val score = evaluateRectangle(rectangle, width, height)
                if (score > maxScore) {
                    maxScore = score
                    bestRectangle = rectangle
                }
            }
        }

        android.util.Log.d("SimpleDocumentDetector", "En iyi skor: $maxScore")
        return if (maxScore > 0.3) bestRectangle else null
    }

    private fun approximateToRectangle(contour: List<PointF>): Array<PointF>? {
        if (contour.size < 4) return null

        // En uç noktaları bul
        val minX = contour.minByOrNull { it.x }!!
        val maxX = contour.maxByOrNull { it.x }!!
        val minY = contour.minByOrNull { it.y }!!
        val maxY = contour.maxByOrNull { it.y }!!

        // 4 köşeyi oluştur
        val corners = mutableSetOf<PointF>()
        corners.add(minX)
        corners.add(maxX)
        corners.add(minY)
        corners.add(maxY)

        // 4 köşe yoksa, merkeze en uzak noktaları ekle
        if (corners.size < 4) {
            val centerX = contour.map { it.x }.average().toFloat()
            val centerY = contour.map { it.y }.average().toFloat()
            val center = PointF(centerX, centerY)

            val remaining = contour.filter { it !in corners }
                .sortedByDescending {
                    kotlin.math.sqrt((it.x - center.x) * (it.x - center.x) + (it.y - center.y) * (it.y - center.y))
                }

            corners.addAll(remaining.take(4 - corners.size))
        }

        return if (corners.size >= 4) {
            orderPoints(corners.take(4).toTypedArray())
        } else null
    }

    private fun evaluateRectangle(rectangle: Array<PointF>, width: Int, height: Int): Double {
        // Dikdörtgenin kalitesini değerlendir
        val area = calculateArea(rectangle)
        val imageArea = width * height
        val areaRatio = area / imageArea

        // Alan oranı skoru (0.1-0.9 arası ideal)
        val areaScore = when {
            areaRatio < 0.1 -> 0.0
            areaRatio > 0.9 -> 0.0
            areaRatio in 0.3..0.8 -> 1.0
            else -> 0.5
        }

        // Köşelerin görüntü sınırları içinde olma skoru
        val boundsScore = if (rectangle.all {
            it.x >= 0 && it.x <= width && it.y >= 0 && it.y <= height
        }) 1.0 else 0.0

        return areaScore * boundsScore
    }

    private fun calculateArea(corners: Array<PointF>): Float {
        if (corners.size != 4) return 0f

        var area = 0f
        for (i in corners.indices) {
            val j = (i + 1) % corners.size
            area += corners[i].x * corners[j].y
            area -= corners[j].x * corners[i].y
        }
        return kotlin.math.abs(area) / 2f
    }

    private fun orderPoints(points: Array<PointF>): Array<PointF> {
        if (points.size != 4) return points

        val ordered = Array(4) { PointF() }

        val sums = points.map { it.x + it.y }
        val diffs = points.map { it.x - it.y }

        val topLeftIndex = sums.indexOf(sums.minOrNull())
        ordered[0] = points[topLeftIndex]

        val bottomRightIndex = sums.indexOf(sums.maxOrNull())
        ordered[2] = points[bottomRightIndex]

        val topRightIndex = diffs.indexOf(diffs.maxOrNull())
        ordered[1] = points[topRightIndex]

        val bottomLeftIndex = diffs.indexOf(diffs.minOrNull())
        ordered[3] = points[bottomLeftIndex]

        return ordered
    }

    private fun findLargestRectangle(edgeBitmap: Bitmap): Array<PointF>? {
        val width = edgeBitmap.width
        val height = edgeBitmap.height
        val pixels = IntArray(width * height)
        edgeBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Yatay ve dikey çizgileri bul
        val horizontalLines = findHorizontalLines(pixels, width, height)
        val verticalLines = findVerticalLines(pixels, width, height)
        
        android.util.Log.d("SimpleDocumentDetector", "Bulunan yatay çizgiler: ${horizontalLines.size}")
        android.util.Log.d("SimpleDocumentDetector", "Bulunan dikey çizgiler: ${verticalLines.size}")
        
        if (horizontalLines.size >= 2 && verticalLines.size >= 2) {
            // En üst, en alt, en sol, en sağ çizgileri al
            val topLine = horizontalLines.minByOrNull { it }!!
            val bottomLine = horizontalLines.maxByOrNull { it }!!
            val leftLine = verticalLines.minByOrNull { it }!!
            val rightLine = verticalLines.maxByOrNull { it }!!
            
            // Köşeleri oluştur
            val corners = arrayOf(
                PointF(leftLine.toFloat(), topLine.toFloat()),     // Sol-üst
                PointF(rightLine.toFloat(), topLine.toFloat()),    // Sağ-üst
                PointF(rightLine.toFloat(), bottomLine.toFloat()), // Sağ-alt
                PointF(leftLine.toFloat(), bottomLine.toFloat())   // Sol-alt
            )
            
            // Köşelerin geçerli olup olmadığını kontrol et
            if (isValidRectangle(corners, width, height)) {
                android.util.Log.d("SimpleDocumentDetector", "Geçerli dikdörtgen bulundu")
                return corners
            }
        }
        
        android.util.Log.d("SimpleDocumentDetector", "Dikdörtgen bulunamadı, varsayılan döndürülüyor")
        return null
    }

    private fun findHorizontalLines(pixels: IntArray, width: Int, height: Int): List<Int> {
        val lines = mutableListOf<Int>()
        
        for (y in height / 4 until height * 3 / 4) { // Ortadaki %50'lik alanda ara
            var edgeCount = 0
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                if (Color.red(pixel) > 200) { // Beyaz kenar
                    edgeCount++
                }
            }
            
            // Çizginin en az %30'u kenar olmalı
            if (edgeCount > width * 0.3) {
                lines.add(y)
            }
        }
        
        return lines
    }

    private fun findVerticalLines(pixels: IntArray, width: Int, height: Int): List<Int> {
        val lines = mutableListOf<Int>()
        
        for (x in width / 4 until width * 3 / 4) { // Ortadaki %50'lik alanda ara
            var edgeCount = 0
            for (y in 0 until height) {
                val pixel = pixels[y * width + x]
                if (Color.red(pixel) > 200) { // Beyaz kenar
                    edgeCount++
                }
            }
            
            // Çizginin en az %30'u kenar olmalı
            if (edgeCount > height * 0.3) {
                lines.add(x)
            }
        }
        
        return lines
    }

    private fun isValidRectangle(corners: Array<PointF>, width: Int, height: Int): Boolean {
        // Minimum alan kontrolü
        val rectWidth = abs(corners[1].x - corners[0].x)
        val rectHeight = abs(corners[3].y - corners[0].y)
        val area = rectWidth * rectHeight
        val minArea = (width * height) * 0.1f // En az %10 alan
        
        return area > minArea && rectWidth > 50 && rectHeight > 50
    }
}
