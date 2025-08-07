package com.example.documentscandemo

import android.graphics.*
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

class PreciseDocumentDetector {
    
    companion object {
        private const val TAG = "PreciseDocumentDetector"
        
        // Esnek tespit parametreleri
        private const val ID_CARD_ASPECT_RATIO = 1.586f // 85.6mm / 53.98mm
        private const val ASPECT_TOLERANCE = 0.4f // Daha esnek tolerans

        // Alan kontrolü - esnek
        private const val MIN_AREA_RATIO = 0.1f // En az %10
        private const val MAX_AREA_RATIO = 0.85f // En fazla %85
    }
    
    suspend fun detectDocumentPrecise(bitmap: Bitmap): Array<PointF>? = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "🎯 Hassas belge tespiti başlatılıyor...")
            val startTime = System.currentTimeMillis()
            
            // 1. Çoklu boyut + çoklu ön işleme
            val detectionResults = mutableListOf<DetectionResult>()
            
            // Farklı boyutlarda tespit
            val sizes = listOf(2000, 1600, 1200) // Daha yüksek çözünürlük
            
            for (size in sizes) {
                val scaledBitmap = scaleToOptimalSize(bitmap, size)
                val scaleFactor = bitmap.width.toFloat() / scaledBitmap.width.toFloat()
                
                // Farklı ön işleme yöntemleri
                val preprocessMethods = listOf(
                    { enhanceContrast(scaledBitmap, 1.5f) },
                    { enhanceEdges(scaledBitmap) },
                    { reduceNoise(scaledBitmap) },
                    { scaledBitmap } // Orijinal
                )
                
                for ((index, preprocessMethod) in preprocessMethods.withIndex()) {
                    try {
                        val processedBitmap = preprocessMethod()
                        val corners = detectWithMultipleThresholds(processedBitmap)
                        
                        if (corners != null) {
                            val scaledCorners = scaleUpCorners(corners, scaleFactor)
                            val confidence = calculatePreciseConfidence(scaledCorners, bitmap)
                            
                            if (confidence > 0.4f) { // Daha düşük güven eşiği
                                detectionResults.add(DetectionResult(scaledCorners, confidence, "Size${size}_Method${index}"))
                                Log.d(TAG, "Boyut $size, Yöntem $index: güven=$confidence")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Boyut $size, Yöntem $index hatası: ${e.message}")
                    }
                }
            }
            
            // En iyi sonucu seç
            val bestResult = detectionResults.maxByOrNull { it.confidence }
            
            if (bestResult != null) {
                val optimizedCorners = optimizeCornersPrecisely(bestResult.corners, bitmap)
                val endTime = System.currentTimeMillis()
                
                Log.d(TAG, "✅ Hassas tespit başarılı! Yöntem: ${bestResult.method}, Güven: ${bestResult.confidence}, Süre: ${endTime - startTime}ms")
                return@withContext optimizedCorners
            }
            
            Log.d(TAG, "❌ Hassas tespit başarısız")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Hassas tespit genel hatası", e)
            return@withContext null
        }
    }
    
    private fun scaleToOptimalSize(bitmap: Bitmap, targetSize: Int): Bitmap {
        val maxDimension = max(bitmap.width, bitmap.height)
        
        return if (maxDimension != targetSize) {
            val scale = targetSize.toFloat() / maxDimension
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
    }
    
    private fun enhanceContrast(bitmap: Bitmap, factor: Float): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        
        // Yüksek kontrast
        colorMatrix.set(floatArrayOf(
            factor, 0f, 0f, 0f, -30f,
            0f, factor, 0f, 0f, -30f,
            0f, 0f, factor, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return enhanced
    }
    
    private fun enhanceEdges(bitmap: Bitmap): Bitmap {
        // Kenar belirginleştirme
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val enhanced = IntArray(width * height)
        
        // Laplacian kernel - kenar belirginleştirme
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
                
                enhanced[y * width + x] = Color.rgb(
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255)
                )
            }
        }
        
        return Bitmap.createBitmap(enhanced, width, height, bitmap.config!!)
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
                
                // 3x3 Gaussian
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
                    r / totalWeight,
                    g / totalWeight,
                    b / totalWeight
                )
            }
        }
        
        return Bitmap.createBitmap(filtered, width, height, bitmap.config!!)
    }
    
    private fun detectWithMultipleThresholds(bitmap: Bitmap): Array<PointF>? {
        // SimpleDocumentDetector'ı kullan - daha güvenilir
        val detector = SimpleDocumentDetector()
        val corners = detector.detectDocument(bitmap)

        if (corners != null && isValidIDCard(corners, bitmap.width, bitmap.height)) {
            return corners
        }

        return null
    }

    private fun applyThreshold(bitmap: Bitmap, threshold: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val binary = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val gray = (Color.red(pixels[y * width + x]) * 0.299 +
                           Color.green(pixels[y * width + x]) * 0.587 +
                           Color.blue(pixels[y * width + x]) * 0.114).toInt()

                val value = if (gray > threshold) 255 else 0
                binary[y * width + x] = Color.rgb(value, value, value)
            }
        }

        return Bitmap.createBitmap(binary, width, height, bitmap.config!!)
    }
    
    private fun isValidIDCard(corners: Array<PointF>, width: Int, height: Int): Boolean {
        if (corners.size != 4) return false

        // 1. Alan kontrolü - esnek
        val area = calculatePolygonArea(corners)
        val imageArea = width * height
        val areaRatio = area / imageArea

        if (areaRatio < MIN_AREA_RATIO || areaRatio > MAX_AREA_RATIO) {
            Log.d(TAG, "Alan oranı uyumsuz: $areaRatio (aralık: $MIN_AREA_RATIO-$MAX_AREA_RATIO)")
            return false
        }

        // 2. Aspect ratio kontrolü - esnek
        val aspectRatio = calculateAspectRatio(corners)
        if (aspectRatio < 0.5f || aspectRatio > 3.0f) { // Çok geniş aralık
            Log.d(TAG, "Aspect ratio çok uç: $aspectRatio")
            return false
        }

        // 3. Köşe açıları kontrolü - esnek
        val angles = calculateCornerAngles(corners)
        val reasonableAngles = angles.count { it in 45f..135f } // Geniş açı toleransı

        if (reasonableAngles < 3) { // En az 3 köşe makul olsun
            Log.d(TAG, "Açı kontrolü başarısız: $reasonableAngles/4 köşe uygun")
            return false
        }

        Log.d(TAG, "✅ Geçerli belge: aspect=$aspectRatio, alan=$areaRatio, açılar=$reasonableAngles")
        return true
    }
    
    private fun calculatePreciseConfidence(corners: Array<PointF>, bitmap: Bitmap): Float {
        var confidence = 0.5f // Başlangıç güveni yüksek

        // 1. Alan skoru (en önemli)
        val area = calculatePolygonArea(corners)
        val imageArea = bitmap.width * bitmap.height
        val areaRatio = area / imageArea

        confidence += when {
            areaRatio in 0.2f..0.7f -> 0.3f // İdeal alan
            areaRatio in 0.1f..0.85f -> 0.2f // Kabul edilebilir alan
            else -> 0f
        }

        // 2. Aspect ratio skoru
        val aspectRatio = calculateAspectRatio(corners)
        confidence += when {
            aspectRatio in 1.2f..2.0f -> 0.2f // Belge benzeri
            aspectRatio in 0.7f..2.5f -> 0.1f // Geniş aralık
            else -> 0f
        }

        return confidence.coerceIn(0f, 1f)
    }
    
    private fun optimizeCornersPrecisely(corners: Array<PointF>, bitmap: Bitmap): Array<PointF> {
        // Hassas köşe optimizasyonu
        var optimized = corners.copyOf()
        
        // 1. Sınır kontrolü
        for (i in optimized.indices) {
            optimized[i].x = optimized[i].x.coerceIn(0f, bitmap.width.toFloat())
            optimized[i].y = optimized[i].y.coerceIn(0f, bitmap.height.toFloat())
        }
        
        // 2. Aspect ratio düzeltmesi
        optimized = correctAspectRatioPrecisely(optimized)
        
        // 3. Köşe sıralaması
        return orderPointsClockwise(optimized)
    }
    
    private fun correctAspectRatioPrecisely(corners: Array<PointF>): Array<PointF> {
        val currentRatio = calculateAspectRatio(corners)
        
        if (abs(currentRatio - ID_CARD_ASPECT_RATIO) > 0.05f) {
            // Merkez hesapla
            val centerX = corners.map { it.x }.average().toFloat()
            val centerY = corners.map { it.y }.average().toFloat()
            
            // Mevcut boyutları hesapla
            val currentWidth = (calculateDistance(corners[0], corners[1]) + calculateDistance(corners[2], corners[3])) / 2f
            val currentHeight = (calculateDistance(corners[1], corners[2]) + calculateDistance(corners[3], corners[0])) / 2f
            
            // Doğru boyutları hesapla
            val correctWidth: Float
            val correctHeight: Float
            
            if (currentRatio > ID_CARD_ASPECT_RATIO) {
                // Çok geniş - yüksekliği artır
                correctWidth = currentWidth
                correctHeight = correctWidth / ID_CARD_ASPECT_RATIO
            } else {
                // Çok dar - genişliği artır
                correctHeight = currentHeight
                correctWidth = correctHeight * ID_CARD_ASPECT_RATIO
            }
            
            // Yeni köşeleri hesapla
            return arrayOf(
                PointF(centerX - correctWidth/2, centerY - correctHeight/2),
                PointF(centerX + correctWidth/2, centerY - correctHeight/2),
                PointF(centerX + correctWidth/2, centerY + correctHeight/2),
                PointF(centerX - correctWidth/2, centerY + correctHeight/2)
            )
        }
        
        return corners
    }

    // Yardımcı fonksiyonlar
    private fun scaleUpCorners(corners: Array<PointF>, scaleFactor: Float): Array<PointF> {
        return corners.map { corner ->
            PointF(corner.x * scaleFactor, corner.y * scaleFactor)
        }.toTypedArray()
    }

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

    private fun calculateEdgeLengths(corners: Array<PointF>): List<Float> {
        if (corners.size != 4) return emptyList()

        return listOf(
            calculateDistance(corners[0], corners[1]),
            calculateDistance(corners[1], corners[2]),
            calculateDistance(corners[2], corners[3]),
            calculateDistance(corners[3], corners[0])
        )
    }

    private fun orderPointsClockwise(corners: Array<PointF>): Array<PointF> {
        if (corners.size != 4) return corners

        val topLeft = corners.minByOrNull { it.x + it.y }!!
        val topRight = corners.minByOrNull { -it.x + it.y }!!
        val bottomRight = corners.maxByOrNull { it.x + it.y }!!
        val bottomLeft = corners.maxByOrNull { -it.x + it.y }!!

        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }



    data class DetectionResult(
        val corners: Array<PointF>,
        val confidence: Float,
        val method: String
    )
}
