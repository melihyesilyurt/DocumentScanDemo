package com.example.documentscandemo

import android.graphics.*
import kotlin.math.*

class DocumentDetector {

    fun detectDocument(bitmap: Bitmap): Array<PointF>? {
        try {
            android.util.Log.d("DocumentDetector", "Belge tespiti başlıyor - Boyut: ${bitmap.width}x${bitmap.height}")

            // Görüntüyü küçült (performans için)
            val scaledBitmap = scaleBitmap(bitmap, 800)
            val scaleFactorX = bitmap.width.toFloat() / scaledBitmap.width.toFloat()
            val scaleFactorY = bitmap.height.toFloat() / scaledBitmap.height.toFloat()

            android.util.Log.d("DocumentDetector", "Ölçeklenmiş boyut: ${scaledBitmap.width}x${scaledBitmap.height}")

            // Edge detection uygula
            val edges = detectEdges(scaledBitmap)

            // Dörtgen köşeleri bul
            val corners = findDocumentCorners(edges)

            if (corners != null) {
                android.util.Log.d("DocumentDetector", "Belge köşeleri bulundu: ${corners.size} köşe")
                // Köşeleri orijinal boyuta ölçekle
                return corners.map { corner ->
                    PointF(corner.x * scaleFactorX, corner.y * scaleFactorY)
                }.toTypedArray()
            } else {
                android.util.Log.d("DocumentDetector", "Belge köşeleri bulunamadı")
                return null
            }

        } catch (e: Exception) {
            android.util.Log.e("DocumentDetector", "Belge tespiti hatası", e)
            e.printStackTrace()
            return null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun detectEdges(bitmap: Bitmap): Bitmap {
        // Gri tonlamaya çevir ve blur uygula
        val grayBitmap = toGrayscale(bitmap)
        val blurredBitmap = applyGaussianBlur(grayBitmap)

        // Gelişmiş edge detection
        val width = blurredBitmap.width
        val height = blurredBitmap.height
        val pixels = IntArray(width * height)
        blurredBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val edgePixels = IntArray(width * height)

        for (y in 2 until height - 2) {
            for (x in 2 until width - 2) {
                val idx = y * width + x

                // Gelişmiş Sobel operatörü (3x3 kernel)
                val gx = (-1 * getGrayValue(pixels[(y-1)*width + (x-1)]) +
                         0 * getGrayValue(pixels[(y-1)*width + x]) +
                         1 * getGrayValue(pixels[(y-1)*width + (x+1)]) +
                         -2 * getGrayValue(pixels[y*width + (x-1)]) +
                         0 * getGrayValue(pixels[y*width + x]) +
                         2 * getGrayValue(pixels[y*width + (x+1)]) +
                         -1 * getGrayValue(pixels[(y+1)*width + (x-1)]) +
                         0 * getGrayValue(pixels[(y+1)*width + x]) +
                         1 * getGrayValue(pixels[(y+1)*width + (x+1)]))

                val gy = (-1 * getGrayValue(pixels[(y-1)*width + (x-1)]) +
                         -2 * getGrayValue(pixels[(y-1)*width + x]) +
                         -1 * getGrayValue(pixels[(y-1)*width + (x+1)]) +
                         0 * getGrayValue(pixels[y*width + (x-1)]) +
                         0 * getGrayValue(pixels[y*width + x]) +
                         0 * getGrayValue(pixels[y*width + (x+1)]) +
                         1 * getGrayValue(pixels[(y+1)*width + (x-1)]) +
                         2 * getGrayValue(pixels[(y+1)*width + x]) +
                         1 * getGrayValue(pixels[(y+1)*width + (x+1)]))

                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toInt()

                // Adaptive threshold - daha hassas kenar tespiti
                val threshold = calculateAdaptiveThreshold(pixels, x, y, width, height)
                val edgeValue = if (magnitude > threshold) 255 else 0

                edgePixels[idx] = Color.rgb(edgeValue, edgeValue, edgeValue)
            }
        }

        return Bitmap.createBitmap(edgePixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun applyGaussianBlur(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val blurredPixels = IntArray(width * height)

        // 3x3 Gaussian kernel
        val kernel = arrayOf(
            intArrayOf(1, 2, 1),
            intArrayOf(2, 4, 2),
            intArrayOf(1, 2, 1)
        )
        val kernelSum = 16

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixelValue = getGrayValue(pixels[(y + ky) * width + (x + kx)])
                        sum += pixelValue * kernel[ky + 1][kx + 1]
                    }
                }

                val blurredValue = sum / kernelSum
                blurredPixels[y * width + x] = Color.rgb(blurredValue, blurredValue, blurredValue)
            }
        }

        return Bitmap.createBitmap(blurredPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun calculateAdaptiveThreshold(pixels: IntArray, x: Int, y: Int, width: Int, height: Int): Int {
        // Çevredeki piksellerin ortalamasını hesapla
        var sum = 0
        var count = 0
        val radius = 5

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = x + dx
                val ny = y + dy

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    sum += getGrayValue(pixels[ny * width + nx])
                    count++
                }
            }
        }

        val average = if (count > 0) sum / count else 128
        return maxOf(30, average - 20) // Minimum 30, ortalamadan 20 düşük
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

    private fun getGrayValue(pixel: Int): Int {
        return Color.red(pixel) // Gri tonlamada R=G=B
    }

    private fun findDocumentCorners(edgeBitmap: Bitmap): Array<PointF>? {
        val width = edgeBitmap.width
        val height = edgeBitmap.height
        val pixels = IntArray(width * height)
        edgeBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Contour detection benzeri yaklaşım
        val contours = findContours(pixels, width, height)

        // En büyük dörtgen konturu bul
        var bestQuad: Array<PointF>? = null
        var maxArea = 0f

        for (contour in contours) {
            val quad = approximateToQuadrilateral(contour)
            if (quad != null) {
                val area = calculateQuadrilateralArea(quad)
                val minArea = (width * height) * 0.1f // En az %10 alan

                if (area > maxArea && area > minArea) {
                    maxArea = area
                    bestQuad = quad
                }
            }
        }

        return bestQuad ?: getDefaultRectangle(width, height)
    }

    private fun findContours(pixels: IntArray, width: Int, height: Int): List<List<PointF>> {
        val contours = mutableListOf<List<PointF>>()
        val visited = BooleanArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                if (!visited[idx] && isEdgePixel(pixels, x, y, width, height)) {
                    val contour = traceContour(pixels, x, y, width, height, visited)
                    if (contour.size > 20) { // Minimum contour size
                        contours.add(contour)
                    }
                }
            }
        }

        return contours
    }

    private fun isEdgePixel(pixels: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        val pixel = pixels[y * width + x]
        val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
        return brightness > 200 // Beyaz kenar
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

        while (stack.isNotEmpty() && contour.size < 1000) { // Limit contour size
            val (x, y) = stack.removeAt(stack.size - 1)
            val idx = y * width + x

            if (x < 0 || x >= width || y < 0 || y >= height || visited[idx]) {
                continue
            }

            if (isEdgePixel(pixels, x, y, width, height)) {
                visited[idx] = true
                contour.add(PointF(x.toFloat(), y.toFloat()))

                // 8-connected neighbors
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx != 0 || dy != 0) {
                            stack.add(Pair(x + dx, y + dy))
                        }
                    }
                }
            }
        }

        return contour
    }

    private fun approximateToQuadrilateral(contour: List<PointF>): Array<PointF>? {
        if (contour.size < 4) return null

        // Douglas-Peucker benzeri basitleştirme
        val epsilon = 0.02 * calculatePerimeter(contour)
        val simplified = simplifyContour(contour, epsilon)

        if (simplified.size >= 4) {
            // En uzak 4 noktayı seç
            return selectFourCorners(simplified)
        }

        return null
    }

    private fun calculatePerimeter(contour: List<PointF>): Double {
        var perimeter = 0.0
        for (i in contour.indices) {
            val current = contour[i]
            val next = contour[(i + 1) % contour.size]
            perimeter += calculateDistance(current, next).toDouble()
        }
        return perimeter
    }

    private fun simplifyContour(contour: List<PointF>, epsilon: Double): List<PointF> {
        if (contour.size <= 2) return contour

        // Basit decimation - her N'inci noktayı al
        val step = maxOf(1, contour.size / 20)
        return contour.filterIndexed { index, _ -> index % step == 0 }
    }

    private fun selectFourCorners(points: List<PointF>): Array<PointF> {
        if (points.size <= 4) {
            return points.take(4).toTypedArray()
        }

        // Convex hull benzeri yaklaşım - en uç noktaları bul
        val minX = points.minByOrNull { it.x }!!
        val maxX = points.maxByOrNull { it.x }!!
        val minY = points.minByOrNull { it.y }!!
        val maxY = points.maxByOrNull { it.y }!!

        val corners = mutableSetOf<PointF>()
        corners.add(minX)
        corners.add(maxX)
        corners.add(minY)
        corners.add(maxY)

        // 4 köşe yoksa, merkeze en uzak noktaları ekle
        if (corners.size < 4) {
            val centerX = points.map { it.x }.average().toFloat()
            val centerY = points.map { it.y }.average().toFloat()
            val center = PointF(centerX, centerY)

            val remaining = points.filter { it !in corners }
                .sortedByDescending { calculateDistance(it, center) }

            corners.addAll(remaining.take(4 - corners.size))
        }

        return orderPoints(corners.take(4).toTypedArray())
    }

    private fun findLargestQuadrilateral(points: List<PointF>, width: Int, height: Int): Array<PointF>? {
        if (points.size < 4) return null

        // Hough transform benzeri yaklaşım - çizgileri bul
        val lines = findLines(points)

        if (lines.size < 4) {
            // Yeterli çizgi bulunamadı, varsayılan dörtgen döndür
            return getDefaultRectangle(width, height)
        }

        // Çizgilerin kesişim noktalarını bul
        val intersections = mutableListOf<PointF>()

        for (i in lines.indices) {
            for (j in i + 1 until lines.size) {
                val intersection = findLineIntersection(lines[i], lines[j])
                intersection?.let { point ->
                    if (isPointInBounds(point, width, height)) {
                        intersections.add(point)
                    }
                }
            }
        }

        if (intersections.size < 4) {
            return getDefaultRectangle(width, height)
        }

        // En büyük dörtgeni oluşturan 4 noktayı seç
        return selectBestQuadrilateral(intersections, width, height)
    }

    private fun findLines(points: List<PointF>): List<Line> {
        val lines = mutableListOf<Line>()

        // Basit çizgi tespit algoritması
        val groupedPoints = groupPointsByDirection(points)

        groupedPoints.forEach { group ->
            if (group.size >= 3) {
                val line = fitLineToPoints(group)
                line?.let { lines.add(it) }
            }
        }

        return lines
    }

    private fun groupPointsByDirection(points: List<PointF>): List<List<PointF>> {
        // Noktaları yönlerine göre grupla (yatay, dikey, çapraz)
        val horizontal = mutableListOf<PointF>()
        val vertical = mutableListOf<PointF>()
        val diagonal1 = mutableListOf<PointF>()
        val diagonal2 = mutableListOf<PointF>()

        points.forEach { point ->
            // Basit sınıflandırma - gerçek uygulamada daha sofistike olmalı
            when {
                point.y < 100 || point.y > points.maxOf { it.y } - 100 -> horizontal.add(point)
                point.x < 100 || point.x > points.maxOf { it.x } - 100 -> vertical.add(point)
                point.x + point.y < points.maxOf { it.x + it.y } / 2 -> diagonal1.add(point)
                else -> diagonal2.add(point)
            }
        }

        return listOf(horizontal, vertical, diagonal1, diagonal2).filter { it.size >= 3 }
    }

    private fun fitLineToPoints(points: List<PointF>): Line? {
        if (points.size < 2) return null

        // En basit yaklaşım - ilk ve son noktayı birleştir
        val first = points.first()
        val last = points.last()

        return Line(first, last)
    }

    private fun findLineIntersection(line1: Line, line2: Line): PointF? {
        val x1 = line1.start.x
        val y1 = line1.start.y
        val x2 = line1.end.x
        val y2 = line1.end.y

        val x3 = line2.start.x
        val y3 = line2.start.y
        val x4 = line2.end.x
        val y4 = line2.end.y

        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)

        if (abs(denom) < 0.001) return null // Paralel çizgiler

        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom

        val intersectionX = x1 + t * (x2 - x1)
        val intersectionY = y1 + t * (y2 - y1)

        return PointF(intersectionX, intersectionY)
    }

    private fun isPointInBounds(point: PointF, width: Int, height: Int): Boolean {
        return point.x >= 0 && point.x <= width && point.y >= 0 && point.y <= height
    }

    private fun selectBestQuadrilateral(points: List<PointF>, width: Int, height: Int): Array<PointF> {
        if (points.size < 4) {
            return getDefaultRectangle(width, height)
        }

        // En büyük alanı kapsayan 4 noktayı seç
        var maxArea = 0f
        var bestQuad = getDefaultRectangle(width, height)

        // Tüm 4'lü kombinasyonları dene (basit yaklaşım)
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                for (k in j + 1 until points.size) {
                    for (l in k + 1 until points.size) {
                        val quad = arrayOf(points[i], points[j], points[k], points[l])
                        val orderedQuad = orderPoints(quad)
                        val area = calculateQuadrilateralArea(orderedQuad)

                        if (area > maxArea && isValidQuadrilateral(orderedQuad, width, height)) {
                            maxArea = area
                            bestQuad = orderedQuad
                        }
                    }
                }
            }
        }

        return bestQuad
    }

    private fun getDefaultRectangle(width: Int, height: Int): Array<PointF> {
        val margin = minOf(width, height) * 0.15f
        return arrayOf(
            PointF(margin, margin),
            PointF(width - margin, margin),
            PointF(width - margin, height - margin),
            PointF(margin, height - margin)
        )
    }

    private fun calculateQuadrilateralArea(corners: Array<PointF>): Float {
        if (corners.size != 4) return 0f

        // Shoelace formula
        var area = 0f
        for (i in corners.indices) {
            val j = (i + 1) % corners.size
            area += corners[i].x * corners[j].y
            area -= corners[j].x * corners[i].y
        }
        return abs(area) / 2f
    }

    private fun isValidQuadrilateral(corners: Array<PointF>, width: Int, height: Int): Boolean {
        // Minimum alan kontrolü
        val area = calculateQuadrilateralArea(corners)
        val minArea = (width * height) * 0.1f // En az %10 alan

        if (area < minArea) return false

        // Köşelerin görüntü sınırları içinde olması
        return corners.all { corner ->
            corner.x >= 0 && corner.x <= width && corner.y >= 0 && corner.y <= height
        }
    }

    data class Line(val start: PointF, val end: PointF)

    fun calculateDistance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    fun orderPoints(points: Array<PointF>): Array<PointF> {
        if (points.size != 4) return points

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
}
