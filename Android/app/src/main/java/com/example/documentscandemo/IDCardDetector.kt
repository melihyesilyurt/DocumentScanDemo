package com.example.documentscandemo

import android.graphics.*
import android.util.Log
import kotlin.math.*

class IDCardDetector {
    
    companion object {
        private const val TAG = "IDCardDetector"
        
        // TC Kimlik Kartı standart boyutları (ISO/IEC 7810 ID-1)
        private const val ID_CARD_WIDTH_MM = 85.6f
        private const val ID_CARD_HEIGHT_MM = 53.98f
        private const val ID_CARD_ASPECT_RATIO = ID_CARD_WIDTH_MM / ID_CARD_HEIGHT_MM // ~1.585
        
        // Tolerans değerleri
        private const val ASPECT_RATIO_TOLERANCE = 0.3f
        private const val MIN_AREA_RATIO = 0.15f // Görüntünün en az %15'i
        private const val MAX_AREA_RATIO = 0.85f // Görüntünün en fazla %85'i
    }
    
    fun detectIDCard(bitmap: Bitmap): Array<PointF>? {
        try {
            Log.d(TAG, "🆔 TC Kimlik kartı tespiti başlatılıyor...")
            
            // Görüntüyü optimize et
            val optimizedBitmap = preprocessForIDCard(bitmap)
            
            // Çoklu tespit yöntemi
            val detectionResults = mutableListOf<DetectionResult>()
            
            // 1. Kenar tabanlı tespit (kimlik kartı kenarları için optimize)
            detectByEdges(optimizedBitmap)?.let { corners ->
                val confidence = calculateIDCardConfidence(corners, bitmap)
                detectionResults.add(DetectionResult(corners, confidence, "EdgeDetection"))
            }
            
            // 2. Renk segmentasyonu (kimlik kartı arka planı)
            detectByColorSegmentation(optimizedBitmap)?.let { corners ->
                val confidence = calculateIDCardConfidence(corners, bitmap)
                detectionResults.add(DetectionResult(corners, confidence, "ColorSegmentation"))
            }
            
            // 3. Template matching (kimlik kartı şablonu)
            detectByTemplate(optimizedBitmap)?.let { corners ->
                val confidence = calculateIDCardConfidence(corners, bitmap)
                detectionResults.add(DetectionResult(corners, confidence, "TemplateMatching"))
            }
            
            // 4. Contour detection (kimlik kartı şekli için optimize)
            detectByContours(optimizedBitmap)?.let { corners ->
                val confidence = calculateIDCardConfidence(corners, bitmap)
                detectionResults.add(DetectionResult(corners, confidence, "ContourDetection"))
            }
            
            // En iyi sonucu seç
            val bestResult = detectionResults.maxByOrNull { it.confidence }
            
            if (bestResult != null && bestResult.confidence > 0.6f) {
                Log.d(TAG, "✅ Kimlik kartı tespit edildi! Yöntem: ${bestResult.method}, Güven: ${bestResult.confidence}")
                return optimizeIDCardCorners(bestResult.corners, bitmap)
            }
            
            Log.d(TAG, "❌ Kimlik kartı tespit edilemedi, varsayılan dörtgen döndürülüyor")
            return getIDCardDefaultRectangle(bitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "Kimlik kartı tespit hatası", e)
            return getIDCardDefaultRectangle(bitmap)
        }
    }
    
    private fun preprocessForIDCard(bitmap: Bitmap): Bitmap {
        // Kimlik kartı tespiti için özel ön işleme
        val enhanced = enhanceForIDCard(bitmap)
        val denoised = reduceNoise(enhanced)
        return sharpenEdges(denoised)
    }
    
    private fun enhanceForIDCard(bitmap: Bitmap): Bitmap {
        // Kimlik kartı için özel kontrast artırma
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        
        // Kimlik kartı kenarlarını belirginleştir
        colorMatrix.set(floatArrayOf(
            1.8f, 0f, 0f, 0f, -30f,
            0f, 1.8f, 0f, 0f, -30f,
            0f, 0f, 1.8f, 0f, -30f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return enhanced
    }
    
    private fun detectByEdges(bitmap: Bitmap): Array<PointF>? {
        // Kimlik kartı kenarları için optimize edilmiş edge detection
        val edges = applyIDCardEdgeDetection(bitmap)
        val lines = detectStraightLines(edges)
        
        // Kimlik kartı şeklinde dörtgen oluştur
        return findIDCardRectangleFromLines(lines, bitmap.width, bitmap.height)
    }
    
    private fun detectByColorSegmentation(bitmap: Bitmap): Array<PointF>? {
        // Kimlik kartı arka plan rengini tespit et
        val backgroundMask = segmentIDCardBackground(bitmap)
        val contours = findContoursFromMask(backgroundMask)
        
        // En büyük kimlik kartı şeklindeki contour'u bul
        return findBestIDCardContour(contours, bitmap.width, bitmap.height)
    }
    
    private fun detectByTemplate(bitmap: Bitmap): Array<PointF>? {
        // TC kimlik kartı şablonu ile eşleştirme
        val templates = getIDCardTemplates()
        
        for (template in templates) {
            val matchResult = templateMatchIDCard(bitmap, template)
            if (matchResult.confidence > 0.7f) {
                return matchResult.corners
            }
        }
        
        return null
    }
    
    private fun detectByContours(bitmap: Bitmap): Array<PointF>? {
        // Kimlik kartı şekli için optimize edilmiş contour detection
        val binary = applyIDCardBinarization(bitmap)
        val contours = findIDCardContours(binary)
        
        return findBestIDCardFromContours(contours, bitmap.width, bitmap.height)
    }
    
    private fun calculateIDCardConfidence(corners: Array<PointF>, bitmap: Bitmap): Float {
        var confidence = 0f
        
        // 1. Aspect ratio kontrolü (en önemli)
        val aspectRatio = calculateAspectRatio(corners)
        val aspectRatioScore = if (abs(aspectRatio - ID_CARD_ASPECT_RATIO) < ASPECT_RATIO_TOLERANCE) {
            1f - (abs(aspectRatio - ID_CARD_ASPECT_RATIO) / ASPECT_RATIO_TOLERANCE)
        } else 0f
        confidence += aspectRatioScore * 0.4f
        
        // 2. Alan kontrolü
        val area = calculatePolygonArea(corners)
        val imageArea = bitmap.width * bitmap.height
        val areaRatio = area / imageArea
        val areaScore = if (areaRatio in MIN_AREA_RATIO..MAX_AREA_RATIO) 0.3f else 0f
        confidence += areaScore
        
        // 3. Dörtgen şekli kontrolü
        val rectangleScore = if (isValidRectangle(corners)) 0.2f else 0f
        confidence += rectangleScore
        
        // 4. Kenar parallelliği kontrolü
        val parallelScore = calculateParallelismScore(corners) * 0.1f
        confidence += parallelScore
        
        Log.d(TAG, "Güven skoru: Aspect=${aspectRatioScore}, Area=${areaScore}, Rectangle=${rectangleScore}, Parallel=${parallelScore}, Total=${confidence}")
        
        return confidence.coerceIn(0f, 1f)
    }
    
    private fun calculateDistance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
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
    
    private fun calculateParallelismScore(corners: Array<PointF>): Float {
        if (corners.size != 4) return 0f
        
        // Karşılıklı kenarların parallellik skorunu hesapla
        val edge1 = PointF(corners[1].x - corners[0].x, corners[1].y - corners[0].y)
        val edge3 = PointF(corners[2].x - corners[3].x, corners[2].y - corners[3].y)
        val edge2 = PointF(corners[2].x - corners[1].x, corners[2].y - corners[1].y)
        val edge4 = PointF(corners[3].x - corners[0].x, corners[3].y - corners[0].y)
        
        val parallel1 = calculateVectorSimilarity(edge1, edge3)
        val parallel2 = calculateVectorSimilarity(edge2, edge4)
        
        return (parallel1 + parallel2) / 2f
    }
    
    private fun calculateVectorSimilarity(v1: PointF, v2: PointF): Float {
        val dot = v1.x * v2.x + v1.y * v2.y
        val mag1 = sqrt(v1.x * v1.x + v1.y * v1.y)
        val mag2 = sqrt(v2.x * v2.x + v2.y * v2.y)
        
        return if (mag1 > 0 && mag2 > 0) {
            abs(dot / (mag1 * mag2))
        } else 0f
    }
    
    private fun optimizeIDCardCorners(corners: Array<PointF>, bitmap: Bitmap): Array<PointF> {
        // Kimlik kartı köşelerini optimize et
        var optimized = corners.copyOf()
        
        // 1. Sınır kontrolü
        for (i in optimized.indices) {
            optimized[i].x = optimized[i].x.coerceIn(0f, bitmap.width.toFloat())
            optimized[i].y = optimized[i].y.coerceIn(0f, bitmap.height.toFloat())
        }
        
        // 2. Aspect ratio düzeltmesi
        optimized = correctAspectRatio(optimized)
        
        // 3. Köşe sıralaması
        return orderPointsClockwise(optimized)
    }
    
    private fun correctAspectRatio(corners: Array<PointF>): Array<PointF> {
        // Kimlik kartı aspect ratio'suna göre düzelt
        val currentRatio = calculateAspectRatio(corners)
        
        if (abs(currentRatio - ID_CARD_ASPECT_RATIO) > 0.1f) {
            // Aspect ratio'yu düzelt
            val center = PointF(
                corners.map { it.x }.average().toFloat(),
                corners.map { it.y }.average().toFloat()
            )
            
            val avgWidth = (calculateDistance(corners[0], corners[1]) + calculateDistance(corners[2], corners[3])) / 2f
            val correctedHeight = avgWidth / ID_CARD_ASPECT_RATIO
            
            return arrayOf(
                PointF(center.x - avgWidth/2, center.y - correctedHeight/2),
                PointF(center.x + avgWidth/2, center.y - correctedHeight/2),
                PointF(center.x + avgWidth/2, center.y + correctedHeight/2),
                PointF(center.x - avgWidth/2, center.y + correctedHeight/2)
            )
        }
        
        return corners
    }
    
    private fun getIDCardDefaultRectangle(bitmap: Bitmap): Array<PointF> {
        // Kimlik kartı boyutlarına uygun varsayılan dörtgen
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        
        // Görüntü aspect ratio'suna göre optimal kimlik kartı boyutu hesapla
        val imageRatio = width / height
        
        val cardWidth: Float
        val cardHeight: Float
        
        if (imageRatio > ID_CARD_ASPECT_RATIO) {
            // Görüntü kimlik kartından daha geniş
            cardHeight = height * 0.7f
            cardWidth = cardHeight * ID_CARD_ASPECT_RATIO
        } else {
            // Görüntü kimlik kartından daha dar
            cardWidth = width * 0.8f
            cardHeight = cardWidth / ID_CARD_ASPECT_RATIO
        }
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        return arrayOf(
            PointF(centerX - cardWidth/2, centerY - cardHeight/2),
            PointF(centerX + cardWidth/2, centerY - cardHeight/2),
            PointF(centerX + cardWidth/2, centerY + cardHeight/2),
            PointF(centerX - cardWidth/2, centerY + cardHeight/2)
        )
    }
    
    // Gerçek implementasyonlar
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
                var count = 0

                // 3x3 kernel
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = pixels[(y + dy) * width + (x + dx)]
                        r += Color.red(pixel)
                        g += Color.green(pixel)
                        b += Color.blue(pixel)
                        count++
                    }
                }

                filtered[y * width + x] = Color.rgb(r / count, g / count, b / count)
            }
        }

        return Bitmap.createBitmap(filtered, width, height, bitmap.config!!)
    }

    private fun sharpenEdges(bitmap: Bitmap): Bitmap {
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

    private fun applyIDCardEdgeDetection(bitmap: Bitmap): Bitmap {
        // Kimlik kartı için optimize edilmiş Sobel edge detection
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val edges = IntArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                // Sobel X
                val gx = (-1 * getGrayValue(pixels[(y-1)*width + (x-1)]) +
                         -2 * getGrayValue(pixels[y*width + (x-1)]) +
                         -1 * getGrayValue(pixels[(y+1)*width + (x-1)]) +
                         1 * getGrayValue(pixels[(y-1)*width + (x+1)]) +
                         2 * getGrayValue(pixels[y*width + (x+1)]) +
                         1 * getGrayValue(pixels[(y+1)*width + (x+1)]))

                // Sobel Y
                val gy = (-1 * getGrayValue(pixels[(y-1)*width + (x-1)]) +
                         -2 * getGrayValue(pixels[(y-1)*width + x]) +
                         -1 * getGrayValue(pixels[(y-1)*width + (x+1)]) +
                         1 * getGrayValue(pixels[(y+1)*width + (x-1)]) +
                         2 * getGrayValue(pixels[(y+1)*width + x]) +
                         1 * getGrayValue(pixels[(y+1)*width + (x+1)]))

                val magnitude = sqrt((gx * gx + gy * gy).toFloat()).toInt()
                val edgeValue = if (magnitude > 100) 255 else 0 // Kimlik kartı için threshold

                edges[y * width + x] = Color.rgb(edgeValue, edgeValue, edgeValue)
            }
        }

        return Bitmap.createBitmap(edges, width, height, bitmap.config!!)
    }

    private fun getGrayValue(pixel: Int): Int {
        return (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
    }

    private fun detectStraightLines(bitmap: Bitmap): List<Line> {
        // Hough Transform ile düz çizgi tespiti
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val lines = mutableListOf<Line>()

        // Basit çizgi tespiti - yatay ve dikey çizgiler
        // Yatay çizgiler
        for (y in 0 until height step 5) {
            var edgeCount = 0
            var startX = -1
            var endX = -1

            for (x in 0 until width) {
                if (Color.red(pixels[y * width + x]) > 128) {
                    if (startX == -1) startX = x
                    endX = x
                    edgeCount++
                }
            }

            if (edgeCount > width / 4) { // En az %25 edge pixel
                lines.add(Line(startX.toFloat(), y.toFloat(), endX.toFloat(), y.toFloat()))
            }
        }

        // Dikey çizgiler
        for (x in 0 until width step 5) {
            var edgeCount = 0
            var startY = -1
            var endY = -1

            for (y in 0 until height) {
                if (Color.red(pixels[y * width + x]) > 128) {
                    if (startY == -1) startY = y
                    endY = y
                    edgeCount++
                }
            }

            if (edgeCount > height / 4) { // En az %25 edge pixel
                lines.add(Line(x.toFloat(), startY.toFloat(), x.toFloat(), endY.toFloat()))
            }
        }

        return lines
    }

    private fun findIDCardRectangleFromLines(lines: List<Line>, width: Int, height: Int): Array<PointF>? {
        if (lines.size < 4) return null

        val horizontalLines = lines.filter { abs(it.y1 - it.y2) < 10 }
        val verticalLines = lines.filter { abs(it.x1 - it.x2) < 10 }

        if (horizontalLines.size >= 2 && verticalLines.size >= 2) {
            val topLine = horizontalLines.minByOrNull { it.y1 }
            val bottomLine = horizontalLines.maxByOrNull { it.y1 }
            val leftLine = verticalLines.minByOrNull { it.x1 }
            val rightLine = verticalLines.maxByOrNull { it.x1 }

            if (topLine != null && bottomLine != null && leftLine != null && rightLine != null) {
                return arrayOf(
                    PointF(leftLine.x1, topLine.y1),
                    PointF(rightLine.x1, topLine.y1),
                    PointF(rightLine.x1, bottomLine.y1),
                    PointF(leftLine.x1, bottomLine.y1)
                )
            }
        }

        return null
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

    private fun isValidRectangle(corners: Array<PointF>): Boolean {
        if (corners.size != 4) return false

        // Açı kontrolü - dörtgenin köşe açıları 90 dereceye yakın olmalı
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

        // En az 3 köşe 90±30 derece arasında olmalı
        val validAngles = angles.count { it in 60f..120f }
        return validAngles >= 3
    }

    private fun orderPointsClockwise(corners: Array<PointF>): Array<PointF> {
        if (corners.size != 4) return corners

        // Merkez noktayı hesapla
        val centerX = corners.map { it.x }.average().toFloat()
        val centerY = corners.map { it.y }.average().toFloat()

        // Sol-üst, sağ-üst, sağ-alt, sol-alt sıralaması
        val topLeft = corners.minByOrNull { it.x + it.y }!!
        val topRight = corners.minByOrNull { -it.x + it.y }!!
        val bottomRight = corners.maxByOrNull { it.x + it.y }!!
        val bottomLeft = corners.maxByOrNull { -it.x + it.y }!!

        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    // Basit implementasyonlar (geliştirilecek)
    private fun segmentIDCardBackground(bitmap: Bitmap): Bitmap = applyIDCardBinarization(bitmap)
    private fun findContoursFromMask(bitmap: Bitmap): List<List<PointF>> = findIDCardContours(bitmap)
    private fun findBestIDCardContour(contours: List<List<PointF>>, width: Int, height: Int): Array<PointF>? = findBestIDCardFromContours(contours, width, height)

    private fun getIDCardTemplates(): List<IDCardTemplate> {
        // TC kimlik kartı şablonları
        return listOf(
            IDCardTemplate(emptyArray(), ID_CARD_ASPECT_RATIO)
        )
    }

    private fun templateMatchIDCard(bitmap: Bitmap, template: IDCardTemplate): TemplateMatchResult {
        // Basit template matching
        return TemplateMatchResult(emptyArray(), 0f)
    }

    private fun applyIDCardBinarization(bitmap: Bitmap): Bitmap {
        // Kimlik kartı için optimize edilmiş binarization
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val binary = IntArray(width * height)

        for (i in pixels.indices) {
            val gray = getGrayValue(pixels[i])
            val value = if (gray > 140) 255 else 0 // Kimlik kartı için threshold
            binary[i] = Color.rgb(value, value, value)
        }

        return Bitmap.createBitmap(binary, width, height, bitmap.config!!)
    }

    private fun findIDCardContours(bitmap: Bitmap): List<List<PointF>> {
        // Basit contour detection
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
                    val contour = traceContour(pixels, visited, x, y, width, height)
                    if (contour.size >= 4) {
                        contours.add(contour)
                    }
                }
            }
        }

        return contours.sortedByDescending { calculateContourArea(it) }
    }

    private fun traceContour(pixels: IntArray, visited: BooleanArray, startX: Int, startY: Int, width: Int, height: Int): List<PointF> {
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

            // 4-connected neighbors
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
        }

        return contour
    }

    private fun calculateContourArea(contour: List<PointF>): Float {
        if (contour.size < 3) return 0f

        var area = 0f
        for (i in contour.indices) {
            val j = (i + 1) % contour.size
            area += contour[i].x * contour[j].y - contour[j].x * contour[i].y
        }
        return abs(area) / 2f
    }

    private fun findBestIDCardFromContours(contours: List<List<PointF>>, width: Int, height: Int): Array<PointF>? {
        for (contour in contours) {
            val approximated = approximateContour(contour)
            if (approximated.size == 4) {
                val corners = approximated.toTypedArray()
                val aspectRatio = calculateAspectRatio(corners)

                // Kimlik kartı aspect ratio kontrolü
                if (abs(aspectRatio - ID_CARD_ASPECT_RATIO) < ASPECT_RATIO_TOLERANCE) {
                    return orderPointsClockwise(corners)
                }
            }
        }
        return null
    }

    private fun approximateContour(contour: List<PointF>): List<PointF> {
        if (contour.size <= 4) return contour

        // Douglas-Peucker approximation
        val epsilon = calculateContourPerimeter(contour) * 0.02f
        return douglasPeucker(contour, epsilon)
    }

    private fun calculateContourPerimeter(contour: List<PointF>): Float {
        if (contour.size < 2) return 0f

        var perimeter = 0f
        for (i in contour.indices) {
            val j = (i + 1) % contour.size
            perimeter += calculateDistance(contour[i], contour[j])
        }
        return perimeter
    }

    private fun douglasPeucker(points: List<PointF>, epsilon: Float): List<PointF> {
        if (points.size <= 2) return points

        var maxDist = 0f
        var maxIndex = 0

        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val dist = pointToLineDistance(points[i], start, end)
            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }

        return if (maxDist > epsilon) {
            val left = douglasPeucker(points.subList(0, maxIndex + 1), epsilon)
            val right = douglasPeucker(points.subList(maxIndex, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun pointToLineDistance(point: PointF, lineStart: PointF, lineEnd: PointF): Float {
        val A = lineEnd.y - lineStart.y
        val B = lineStart.x - lineEnd.x
        val C = lineEnd.x * lineStart.y - lineStart.x * lineEnd.y

        return abs(A * point.x + B * point.y + C) / sqrt(A * A + B * B)
    }
    
    data class DetectionResult(val corners: Array<PointF>, val confidence: Float, val method: String)
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    data class IDCardTemplate(val corners: Array<PointF>, val aspectRatio: Float)
    data class TemplateMatchResult(val corners: Array<PointF>, val confidence: Float)
}
