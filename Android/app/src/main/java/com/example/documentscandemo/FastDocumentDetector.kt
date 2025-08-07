package com.example.documentscandemo

import android.graphics.*
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

class FastDocumentDetector {
    
    companion object {
        private const val TAG = "FastDocumentDetector"
        private const val MAX_IMAGE_SIZE = 800 // Maksimum işleme boyutu
    }
    
    suspend fun detectDocumentFast(bitmap: Bitmap): Array<PointF>? = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "⚡ Hızlı belge tespiti başlatılıyor...")
            val startTime = System.currentTimeMillis()
            
            // 1. Görüntüyü küçült (hız için)
            val scaledBitmap = scaleDownImage(bitmap)
            val scaleFactor = bitmap.width.toFloat() / scaledBitmap.width.toFloat()
            
            Log.d(TAG, "Görüntü ${bitmap.width}x${bitmap.height} -> ${scaledBitmap.width}x${scaledBitmap.height}")
            
            // 2. Hızlı ön işleme
            val preprocessed = fastPreprocess(scaledBitmap)
            
            // 3. Hızlı algoritmaları sırayla dene (ilk başarılıda dur)
            val algorithms = listOf(
                { fastContourDetection(preprocessed) },
                { fastEdgeDetection(preprocessed) },
                { fastCornerDetection(preprocessed) },
                { getSmartDefault(preprocessed) }
            )
            
            for ((index, algorithm) in algorithms.withIndex()) {
                try {
                    val result = algorithm()
                    if (result != null) {
                        // Sonucu orijinal boyuta ölçekle
                        val scaledResult = scaleUpCorners(result, scaleFactor)
                        val optimized = quickOptimize(scaledResult, bitmap)
                        
                        val endTime = System.currentTimeMillis()
                        Log.d(TAG, "✅ Algoritma ${index + 1} başarılı! Süre: ${endTime - startTime}ms")
                        
                        return@withContext optimized
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Algoritma ${index + 1} hatası: ${e.message}")
                }
            }
            
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "❌ Hiçbir algoritma başarılı olmadı. Süre: ${endTime - startTime}ms")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Hızlı tespit genel hatası", e)
            return@withContext null
        }
    }
    
    private fun scaleDownImage(bitmap: Bitmap): Bitmap {
        val maxDimension = max(bitmap.width, bitmap.height)
        
        return if (maxDimension > MAX_IMAGE_SIZE) {
            val scale = MAX_IMAGE_SIZE.toFloat() / maxDimension
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
    }
    
    private fun fastPreprocess(bitmap: Bitmap): Bitmap {
        // Minimal ön işleme - sadece gri tonlama ve hafif kontrast
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val processed = IntArray(width * height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
            
            // Hafif kontrast artırma
            val enhanced = ((gray - 128) * 1.2 + 128).toInt().coerceIn(0, 255)
            
            processed[i] = Color.rgb(enhanced, enhanced, enhanced)
        }
        
        return Bitmap.createBitmap(processed, width, height, bitmap.config!!)
    }
    
    private fun fastContourDetection(bitmap: Bitmap): Array<PointF>? {
        // Basit threshold + contour bulma
        val binary = simpleBinarize(bitmap, 128)
        val contours = findSimpleContours(binary)
        
        // En büyük dörtgen contour'u bul
        for (contour in contours.sortedByDescending { calculateArea(it) }) {
            val approx = simpleApproximate(contour)
            if (approx.size == 4) {
                return approx.toTypedArray()
            }
        }
        
        return null
    }
    
    private fun fastEdgeDetection(bitmap: Bitmap): Array<PointF>? {
        // Basit Sobel edge detection
        val edges = applySobelFast(bitmap)
        val binary = simpleBinarize(edges, 100)
        
        // Kenarlardan dörtgen bul
        return findRectangleFromEdges(binary)
    }
    
    private fun fastCornerDetection(bitmap: Bitmap): Array<PointF>? {
        // Basit corner detection
        val corners = detectSimpleCorners(bitmap)
        
        if (corners.size >= 4) {
            // En iyi 4 köşeyi seç
            val sorted = corners.sortedByDescending { getCornerStrength(bitmap, it) }
            return findBestQuadrilateral(sorted.take(4))
        }
        
        return null
    }
    
    private fun getSmartDefault(bitmap: Bitmap): Array<PointF> {
        // Akıllı varsayılan dörtgen
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        
        // Kenar analizi ile optimal margin
        val margin = analyzeEdgesForMargin(bitmap)
        
        return arrayOf(
            PointF(width * margin, height * margin),
            PointF(width * (1 - margin), height * margin),
            PointF(width * (1 - margin), height * (1 - margin)),
            PointF(width * margin, height * (1 - margin))
        )
    }
    
    private fun scaleUpCorners(corners: Array<PointF>, scaleFactor: Float): Array<PointF> {
        return corners.map { corner ->
            PointF(corner.x * scaleFactor, corner.y * scaleFactor)
        }.toTypedArray()
    }
    
    private fun quickOptimize(corners: Array<PointF>, bitmap: Bitmap): Array<PointF> {
        // Hızlı optimizasyon - sadece sınır kontrolü ve sıralama
        val optimized = corners.map { corner ->
            PointF(
                corner.x.coerceIn(0f, bitmap.width.toFloat()),
                corner.y.coerceIn(0f, bitmap.height.toFloat())
            )
        }.toTypedArray()
        
        return orderPointsClockwise(optimized)
    }
    
    // Yardımcı fonksiyonlar
    private fun simpleBinarize(bitmap: Bitmap, threshold: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val binary = IntArray(width * height)
        
        for (i in pixels.indices) {
            val gray = Color.red(pixels[i])
            val value = if (gray > threshold) 255 else 0
            binary[i] = Color.rgb(value, value, value)
        }
        
        return Bitmap.createBitmap(binary, width, height, bitmap.config!!)
    }
    
    private fun findSimpleContours(bitmap: Bitmap): List<List<PointF>> {
        // Basit contour bulma - sadece dış kenarlar
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val contours = mutableListOf<List<PointF>>()
        val visited = BooleanArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (!visited[index] && Color.red(pixels[index]) == 255) {
                    val contour = traceSimpleContour(pixels, visited, x, y, width, height)
                    if (contour.size >= 4) {
                        contours.add(contour)
                    }
                }
            }
        }
        
        return contours
    }
    
    private fun traceSimpleContour(pixels: IntArray, visited: BooleanArray, startX: Int, startY: Int, width: Int, height: Int): List<PointF> {
        val contour = mutableListOf<PointF>()
        val stack = mutableListOf(Pair(startX, startY))
        
        while (stack.isNotEmpty() && contour.size < 1000) {
            val (x, y) = stack.removeAt(stack.size - 1)
            val index = y * width + x
            
            if (x < 0 || x >= width || y < 0 || y >= height || visited[index] || Color.red(pixels[index]) != 255) {
                continue
            }
            
            visited[index] = true
            contour.add(PointF(x.toFloat(), y.toFloat()))
            
            // 4-connected neighbors (daha hızlı)
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
        }
        
        return contour
    }
    
    private fun simpleApproximate(contour: List<PointF>): List<PointF> {
        if (contour.size <= 4) return contour
        
        // Basit Douglas-Peucker
        val epsilon = (calculatePerimeter(contour) * 0.02).toFloat()
        return douglasPeuckerSimple(contour, epsilon)
    }
    
    private fun douglasPeuckerSimple(points: List<PointF>, epsilon: Float): List<PointF> {
        if (points.size <= 2) return points
        
        var maxDist: Float = 0.0F
        var maxIndex = 0
        
        val start = points.first()
        val end = points.last()
        
        for (i in 1 until points.size - 1) {
            val dist = pointLineDistance(points[i], start, end)
            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }
        
        return if (maxDist > epsilon) {
            val left = douglasPeuckerSimple(points.subList(0, maxIndex + 1), epsilon)
            val right = douglasPeuckerSimple(points.subList(maxIndex, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }
    
    private fun calculateArea(contour: List<PointF>): Float {
        if (contour.size < 3) return 0f
        
        var area = 0f
        for (i in contour.indices) {
            val j = (i + 1) % contour.size
            area += contour[i].x * contour[j].y - contour[j].x * contour[i].y
        }
        return abs(area) / 2f
    }
    
    private fun calculatePerimeter(contour: List<PointF>): Float {
        if (contour.size < 2) return 0f
        
        var perimeter = 0f
        for (i in contour.indices) {
            val j = (i + 1) % contour.size
            val dx = contour[j].x - contour[i].x
            val dy = contour[j].y - contour[i].y
            perimeter += sqrt(dx * dx + dy * dy)
        }
        return perimeter
    }
    
    private fun pointLineDistance(point: PointF, lineStart: PointF, lineEnd: PointF): Float {
        val A = lineEnd.y - lineStart.y
        val B = lineStart.x - lineEnd.x
        val C = lineEnd.x * lineStart.y - lineStart.x * lineEnd.y
        
        return (abs(A * point.x + B * point.y + C) / sqrt(A * A + B * B)).toFloat()
    }

    private fun applySobelFast(bitmap: Bitmap): Bitmap {
        // Hızlı Sobel implementasyonu
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val edges = IntArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val gx = Color.red(pixels[(y-1)*width + x+1]) - Color.red(pixels[(y-1)*width + x-1]) +
                        2*(Color.red(pixels[y*width + x+1]) - Color.red(pixels[y*width + x-1])) +
                        Color.red(pixels[(y+1)*width + x+1]) - Color.red(pixels[(y+1)*width + x-1])

                val gy = Color.red(pixels[(y-1)*width + x-1]) - Color.red(pixels[(y+1)*width + x-1]) +
                        2*(Color.red(pixels[(y-1)*width + x]) - Color.red(pixels[(y+1)*width + x])) +
                        Color.red(pixels[(y-1)*width + x+1]) - Color.red(pixels[(y+1)*width + x+1])

                val magnitude = sqrt((gx*gx + gy*gy).toFloat()).toInt().coerceIn(0, 255)
                edges[y*width + x] = Color.rgb(magnitude, magnitude, magnitude)
            }
        }

        return Bitmap.createBitmap(edges, width, height, bitmap.config!!)
    }

    private fun findRectangleFromEdges(bitmap: Bitmap): Array<PointF>? {
        // Kenarlardan basit dörtgen bulma
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Yatay ve dikey çizgileri bul
        val horizontalLines = mutableListOf<Pair<Int, Int>>()
        val verticalLines = mutableListOf<Pair<Int, Int>>()

        // Yatay çizgiler
        for (y in 0 until height step 5) {
            var edgeCount = 0
            for (x in 0 until width) {
                if (Color.red(pixels[y * width + x]) > 128) edgeCount++
            }
            if (edgeCount > width / 4) {
                horizontalLines.add(Pair(y, edgeCount))
            }
        }

        // Dikey çizgiler
        for (x in 0 until width step 5) {
            var edgeCount = 0
            for (y in 0 until height) {
                if (Color.red(pixels[y * width + x]) > 128) edgeCount++
            }
            if (edgeCount > height / 4) {
                verticalLines.add(Pair(x, edgeCount))
            }
        }

        if (horizontalLines.size >= 2 && verticalLines.size >= 2) {
            val topLine = horizontalLines.minByOrNull { it.first }?.first ?: 0
            val bottomLine = horizontalLines.maxByOrNull { it.first }?.first ?: height
            val leftLine = verticalLines.minByOrNull { it.first }?.first ?: 0
            val rightLine = verticalLines.maxByOrNull { it.first }?.first ?: width

            return arrayOf(
                PointF(leftLine.toFloat(), topLine.toFloat()),
                PointF(rightLine.toFloat(), topLine.toFloat()),
                PointF(rightLine.toFloat(), bottomLine.toFloat()),
                PointF(leftLine.toFloat(), bottomLine.toFloat())
            )
        }

        return null
    }

    private fun detectSimpleCorners(bitmap: Bitmap): List<PointF> {
        // Basit Harris corner detection
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val corners = mutableListOf<PointF>()

        for (y in 2 until height - 2 step 3) {
            for (x in 2 until width - 2 step 3) {
                val cornerResponse = calculateCornerResponse(pixels, x, y, width)
                if (cornerResponse > 0.1f) {
                    corners.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }

        return corners
    }

    private fun calculateCornerResponse(pixels: IntArray, x: Int, y: Int, width: Int): Float {
        // Basit corner response hesaplama
        var Ixx = 0f
        var Iyy = 0f
        var Ixy = 0f

        for (dy in -1..1) {
            for (dx in -1..1) {
                val px = x + dx
                val py = y + dy
                val index = py * width + px

                val Ix = if (dx != 0) Color.red(pixels[index + 1]) - Color.red(pixels[index - 1]) else 0
                val Iy = if (dy != 0) Color.red(pixels[index + width]) - Color.red(pixels[index - width]) else 0

                Ixx += Ix * Ix
                Iyy += Iy * Iy
                Ixy += Ix * Iy
            }
        }

        val det = Ixx * Iyy - Ixy * Ixy
        val trace = Ixx + Iyy

        return if (trace > 0) det / trace else 0f
    }

    private fun getCornerStrength(bitmap: Bitmap, point: PointF): Float {
        val x = point.x.toInt()
        val y = point.y.toInt()

        if (x < 2 || x >= bitmap.width - 2 || y < 2 || y >= bitmap.height - 2) return 0f

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        return calculateCornerResponse(pixels, x, y, bitmap.width)
    }

    private fun findBestQuadrilateral(corners: List<PointF>): Array<PointF>? {
        if (corners.size < 4) return null

        // En uzak 4 köşeyi bul
        val center = PointF(
            corners.map { it.x }.average().toFloat(),
            corners.map { it.y }.average().toFloat()
        )

        val sortedByAngle = corners.sortedBy { corner ->
            atan2(corner.y - center.y, corner.x - center.x)
        }

        // 4 köşeyi eşit açılarla seç
        val step = sortedByAngle.size / 4
        return arrayOf(
            sortedByAngle[0],
            sortedByAngle[step],
            sortedByAngle[step * 2],
            sortedByAngle[step * 3]
        )
    }

    private fun analyzeEdgesForMargin(bitmap: Bitmap): Float {
        // Kenar analizi ile optimal margin hesapla
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var edgeBrightness = 0f
        var edgePixelCount = 0

        val marginSize = min(width, height) / 20

        // Kenar piksellerini analiz et
        for (i in 0 until marginSize) {
            for (j in 0 until width) {
                edgeBrightness += Color.red(pixels[i * width + j])
                edgeBrightness += Color.red(pixels[(height - 1 - i) * width + j])
                edgePixelCount += 2
            }
            for (j in marginSize until height - marginSize) {
                edgeBrightness += Color.red(pixels[j * width + i])
                edgeBrightness += Color.red(pixels[j * width + (width - 1 - i)])
                edgePixelCount += 2
            }
        }

        val avgBrightness = edgeBrightness / edgePixelCount

        return when {
            avgBrightness > 200 -> 0.02f
            avgBrightness > 100 -> 0.05f
            else -> 0.08f
        }
    }

    private fun orderPointsClockwise(corners: Array<PointF>): Array<PointF> {
        if (corners.size != 4) return corners

        // Merkez hesapla
        val centerX = corners.map { it.x }.average().toFloat()
        val centerY = corners.map { it.y }.average().toFloat()

        // Sol-üst, sağ-üst, sağ-alt, sol-alt
        val topLeft = corners.minByOrNull { it.x + it.y }!!
        val topRight = corners.minByOrNull { -it.x + it.y }!!
        val bottomRight = corners.maxByOrNull { it.x + it.y }!!
        val bottomLeft = corners.maxByOrNull { -it.x + it.y }!!

        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }
}
