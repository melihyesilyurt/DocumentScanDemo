package com.example.documentscandemo

import android.graphics.*
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

class BalancedDocumentDetector {
    
    companion object {
        private const val TAG = "BalancedDocumentDetector"
        private const val OPTIMAL_SIZE = 1200 // Daha büyük boyut - doğruluk için
    }
    
    suspend fun detectDocumentBalanced(bitmap: Bitmap): Array<PointF>? = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "⚖️ Dengeli belge tespiti başlatılıyor...")
            val startTime = System.currentTimeMillis()
            
            // 1. Optimal boyuta ölçekle (çok küçük değil, çok büyük değil)
            val scaledBitmap = scaleToOptimalSize(bitmap)
            val scaleFactor = bitmap.width.toFloat() / scaledBitmap.width.toFloat()
            
            Log.d(TAG, "Görüntü ${bitmap.width}x${bitmap.height} -> ${scaledBitmap.width}x${scaledBitmap.height}")
            
            // 2. Kaliteli ön işleme
            val preprocessed = qualityPreprocess(scaledBitmap)
            
            // 3. Dengeli algoritma sırası (doğruluk + hız)
            val detectionMethods = listOf(
                { detectByAdvancedContours(preprocessed) },
                { detectByEdgeAnalysis(preprocessed) },
                { detectByCornerDetection(preprocessed) },
                { detectByColorSegmentation(preprocessed) },
                { getIntelligentDefault(preprocessed) }
            )
            
            for ((index, method) in detectionMethods.withIndex()) {
                try {
                    val result = method()
                    if (result != null && isQualityDetection(result, scaledBitmap)) {
                        // Sonucu orijinal boyuta ölçekle
                        val scaledResult = scaleUpCorners(result, scaleFactor)
                        val optimized = intelligentOptimize(scaledResult, bitmap)
                        
                        val endTime = System.currentTimeMillis()
                        Log.d(TAG, "✅ Yöntem ${index + 1} başarılı! Süre: ${endTime - startTime}ms")
                        
                        return@withContext optimized
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Yöntem ${index + 1} hatası: ${e.message}")
                }
            }
            
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "❌ Hiçbir yöntem başarılı olmadı. Süre: ${endTime - startTime}ms")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Dengeli tespit genel hatası", e)
            return@withContext null
        }
    }
    
    private fun scaleToOptimalSize(bitmap: Bitmap): Bitmap {
        val maxDimension = max(bitmap.width, bitmap.height)
        
        return if (maxDimension > OPTIMAL_SIZE) {
            val scale = OPTIMAL_SIZE.toFloat() / maxDimension
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else if (maxDimension < 600) {
            // Çok küçük görüntüleri büyüt
            val scale = 800.toFloat() / maxDimension
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
    }
    
    private fun qualityPreprocess(bitmap: Bitmap): Bitmap {
        // Kaliteli ön işleme - doğruluk odaklı
        val enhanced = enhanceContrast(bitmap, 1.3f)
        val denoised = reduceNoise(enhanced)
        val sharpened = sharpenImage(denoised)
        return sharpened
    }
    
    private fun enhanceContrast(bitmap: Bitmap, factor: Float): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        
        // Dengeli kontrast artırma
        colorMatrix.set(floatArrayOf(
            factor, 0f, 0f, 0f, 0f,
            0f, factor, 0f, 0f, 0f,
            0f, 0f, factor, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return enhanced
    }
    
    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        // Gaussian blur ile gürültü azaltma
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val filtered = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0
                var g = 0
                var b = 0
                
                // 3x3 Gaussian kernel
                val weights = arrayOf(
                    intArrayOf(1, 2, 1),
                    intArrayOf(2, 4, 2),
                    intArrayOf(1, 2, 1)
                )
                var totalWeight = 0
                
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = pixels[(y + dy) * width + (x + dx)]
                        val weight = weights[dy + 1][dx + 1]
                        
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                        totalWeight += weight
                    }
                }
                
                filtered[y * width + x] = Color.rgb(
                    (r / totalWeight).coerceIn(0, 255),
                    (g / totalWeight).coerceIn(0, 255),
                    (b / totalWeight).coerceIn(0, 255)
                )
            }
        }
        
        return Bitmap.createBitmap(filtered, width, height, bitmap.config!!)
    }
    
    private fun sharpenImage(bitmap: Bitmap): Bitmap {
        // Unsharp mask ile keskinleştirme
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val sharpened = IntArray(width * height)
        
        // Sharpening kernel
        val kernel = arrayOf(
            intArrayOf(0, -1, 0),
            intArrayOf(-1, 5, -1),
            intArrayOf(0, -1, 0)
        )
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0
                var g = 0
                var b = 0
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = pixels[(y + ky) * width + (x + kx)]
                        val weight = kernel[ky + 1][kx + 1]
                        
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }
                
                sharpened[y * width + x] = Color.rgb(
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255)
                )
            }
        }
        
        return Bitmap.createBitmap(sharpened, width, height, bitmap.config!!)
    }
    
    private fun detectByAdvancedContours(bitmap: Bitmap): Array<PointF>? {
        // Gelişmiş contour detection - çoklu threshold
        val thresholds = listOf(120, 140, 160, 180, 200)
        
        for (threshold in thresholds) {
            val binary = applyAdaptiveThreshold(bitmap, threshold)
            val contours = findAdvancedContours(binary)
            
            for (contour in contours.sortedByDescending { calculateContourArea(it) }) {
                val approximated = approximateContour(contour, 0.02)
                if (approximated.size == 4) {
                    val corners = approximated.toTypedArray()
                    if (isValidDocumentShape(corners, bitmap.width, bitmap.height)) {
                        return orderPointsClockwise(corners)
                    }
                }
            }
        }
        
        return null
    }
    
    private fun detectByEdgeAnalysis(bitmap: Bitmap): Array<PointF>? {
        // Gelişmiş edge detection
        val edges = applySobelEdgeDetection(bitmap)
        val lines = detectHoughLines(edges)
        return findRectangleFromLines(lines, bitmap.width, bitmap.height)
    }
    
    private fun detectByCornerDetection(bitmap: Bitmap): Array<PointF>? {
        // Harris corner detection
        val corners = detectHarrisCorners(bitmap)
        return findBestRectangleFromCorners(corners, bitmap.width, bitmap.height)
    }
    
    private fun detectByColorSegmentation(bitmap: Bitmap): Array<PointF>? {
        // Renk segmentasyonu ile belge tespiti
        val segmented = segmentDocumentByColor(bitmap)
        val contours = findAdvancedContours(segmented)
        
        for (contour in contours.sortedByDescending { calculateContourArea(it) }) {
            val approximated = approximateContour(contour, 0.015)
            if (approximated.size == 4) {
                val corners = approximated.toTypedArray()
                if (isValidDocumentShape(corners, bitmap.width, bitmap.height)) {
                    return orderPointsClockwise(corners)
                }
            }
        }
        
        return null
    }
    
    private fun getIntelligentDefault(bitmap: Bitmap): Array<PointF> {
        // Akıllı varsayılan - görüntü analizi ile
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        
        // Kenar analizi
        val margin = analyzeImageEdges(bitmap)
        
        // Aspect ratio analizi
        val imageRatio = width / height
        val adjustedMargin = when {
            imageRatio > 2.0f -> margin * 0.8f // Panoramik görüntü
            imageRatio < 0.7f -> margin * 1.2f // Dikey görüntü
            else -> margin
        }
        
        return arrayOf(
            PointF(width * adjustedMargin, height * adjustedMargin),
            PointF(width * (1 - adjustedMargin), height * adjustedMargin),
            PointF(width * (1 - adjustedMargin), height * (1 - adjustedMargin)),
            PointF(width * adjustedMargin, height * (1 - adjustedMargin))
        )
    }
    
    private fun isQualityDetection(corners: Array<PointF>, bitmap: Bitmap): Boolean {
        // Kaliteli tespit kontrolü
        if (corners.size != 4) return false
        
        // 1. Alan kontrolü
        val area = calculatePolygonArea(corners)
        val imageArea = bitmap.width * bitmap.height
        val areaRatio = area / imageArea
        if (areaRatio < 0.1f || areaRatio > 0.9f) return false
        
        // 2. Aspect ratio kontrolü
        val aspectRatio = calculateAspectRatio(corners)
        if (aspectRatio < 0.3f || aspectRatio > 4.0f) return false
        
        // 3. Köşe açıları kontrolü
        val angles = calculateCornerAngles(corners)
        val validAngles = angles.count { it in 45f..135f }
        if (validAngles < 3) return false
        
        return true
    }
    
    private fun scaleUpCorners(corners: Array<PointF>, scaleFactor: Float): Array<PointF> {
        return corners.map { corner ->
            PointF(corner.x * scaleFactor, corner.y * scaleFactor)
        }.toTypedArray()
    }
    
    private fun intelligentOptimize(corners: Array<PointF>, bitmap: Bitmap): Array<PointF> {
        // Akıllı optimizasyon
        var optimized = corners.copyOf()
        
        // 1. Sınır kontrolü
        for (i in optimized.indices) {
            optimized[i].x = optimized[i].x.coerceIn(0f, bitmap.width.toFloat())
            optimized[i].y = optimized[i].y.coerceIn(0f, bitmap.height.toFloat())
        }
        
        // 2. Köşe düzeltmesi
        optimized = refineCornersByEdges(optimized, bitmap)
        
        // 3. Sıralama
        return orderPointsClockwise(optimized)
    }

    // Temel yardımcı fonksiyonlar - SimpleDocumentDetector'dan uyarlanmış
    private fun calculatePolygonArea(corners: Array<PointF>): Float {
        if (corners.size < 3) return 0f

        var area = 0f
        for (i in corners.indices) {
            val j = (i + 1) % corners.size
            area += corners[i].x * corners[j].y
            area -= corners[j].x * corners[i].y
        }
        return abs(area) / 2f
    }

    private fun calculateAspectRatio(corners: Array<PointF>): Float {
        if (corners.size != 4) return 0f

        val width1 = calculateDistance(corners[0], corners[1])
        val width2 = calculateDistance(corners[2], corners[3])
        val height1 = calculateDistance(corners[1], corners[2])
        val height2 = calculateDistance(corners[3], corners[0])

        val avgWidth = (width1 + width2) / 2f
        val avgHeight = (height1 + height2) / 2f

        return if (avgHeight > 0) avgWidth / avgHeight else 0f
    }

    private fun calculateDistance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    private fun calculateCornerAngles(corners: Array<PointF>): List<Float> {
        if (corners.size != 4) return emptyList()

        val angles = mutableListOf<Float>()

        for (i in corners.indices) {
            val prev = corners[(i - 1 + corners.size) % corners.size]
            val curr = corners[i]
            val next = corners[(i + 1) % corners.size]

            val v1x = prev.x - curr.x
            val v1y = prev.y - curr.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y

            val dot = v1x * v2x + v1y * v2y
            val mag1 = sqrt(v1x * v1x + v1y * v1y)
            val mag2 = sqrt(v2x * v2x + v2y * v2y)

            if (mag1 > 0 && mag2 > 0) {
                val cosAngle = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
                val angle = acos(cosAngle) * 180f / PI.toFloat()
                angles.add(angle)
            }
        }

        return angles
    }

    private fun orderPointsClockwise(corners: Array<PointF>): Array<PointF> {
        if (corners.size != 4) return corners

        val topLeft = corners.minByOrNull { it.x + it.y }!!
        val topRight = corners.minByOrNull { -it.x + it.y }!!
        val bottomRight = corners.maxByOrNull { it.x + it.y }!!
        val bottomLeft = corners.maxByOrNull { -it.x + it.y }!!

        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    // Placeholder fonksiyonlar (SimpleDocumentDetector'dan alınacak)
    private fun applyAdaptiveThreshold(bitmap: Bitmap, threshold: Int): Bitmap {
        // SimpleDocumentDetector'daki implementasyonu kullan
        val detector = SimpleDocumentDetector()
        return detector.toGrayscale(bitmap) // Geçici çözüm
    }

    private fun findAdvancedContours(bitmap: Bitmap): List<List<PointF>> = emptyList()
    private fun traceContour(pixels: IntArray, visited: BooleanArray, startX: Int, startY: Int, width: Int, height: Int): List<PointF> = emptyList()
    private fun calculateContourArea(contour: List<PointF>): Float = 0f
    private fun approximateContour(contour: List<PointF>, epsilon: Double): List<PointF> = emptyList()
    private fun calculateContourPerimeter(contour: List<PointF>): Double = 0.0
    private fun douglasPeucker(points: List<PointF>, epsilon: Double): List<PointF> = emptyList()
    private fun pointToLineDistance(point: PointF, lineStart: PointF, lineEnd: PointF): Double = 0.0
    private fun isValidDocumentShape(corners: Array<PointF>, width: Int, height: Int): Boolean = true
    private fun applySobelEdgeDetection(bitmap: Bitmap): Bitmap = bitmap
    private fun detectHoughLines(bitmap: Bitmap): List<Line> = emptyList()
    private fun findRectangleFromLines(lines: List<Line>, width: Int, height: Int): Array<PointF>? = null
    private fun detectHarrisCorners(bitmap: Bitmap): List<PointF> = emptyList()
    private fun findBestRectangleFromCorners(corners: List<PointF>, width: Int, height: Int): Array<PointF>? = null
    private fun segmentDocumentByColor(bitmap: Bitmap): Bitmap = bitmap
    private fun analyzeImageEdges(bitmap: Bitmap): Float = 0.05f
    private fun refineCornersByEdges(corners: Array<PointF>, bitmap: Bitmap): Array<PointF> = corners

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
}
