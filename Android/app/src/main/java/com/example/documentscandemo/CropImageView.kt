package com.example.documentscandemo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private var corners = arrayOf(
        PointF(100f, 100f),
        PointF(300f, 100f),
        PointF(300f, 300f),
        PointF(100f, 300f)
    )
    
    private var selectedCornerIndex = -1
    private val touchRadius = 50f
    
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val cornerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 255, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private var imageRect = RectF()
    private var scaleX = 1f
    private var scaleY = 1f

    fun setImageBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        // View boyutları henüz hazır değilse, post ile bekle
        if (width > 0 && height > 0) {
            calculateImageRect()
            invalidate()
        } else {
            post {
                calculateImageRect()
                invalidate()
            }
        }
    }

    fun setCorners(corners: Array<PointF>) {
        this.corners = corners.copyOf()
        // Eğer image rect hesaplanmışsa, köşeleri view koordinatlarına dönüştür
        if (imageRect.width() > 0 && imageRect.height() > 0) {
            for (i in corners.indices) {
                this.corners[i] = imageToViewCoordinates(corners[i])
            }
        } else {
            // Image rect henüz hazır değil, post ile bekle
            post {
                for (i in corners.indices) {
                    this.corners[i] = imageToViewCoordinates(corners[i])
                }
                invalidate()
            }
        }
        invalidate()
    }

    fun getCorners(): Array<PointF> {
        // Köşeleri image koordinatlarına dönüştür
        return corners.map { corner ->
            viewToImageCoordinates(corner)
        }.toTypedArray()
    }

    private fun calculateImageRect() {
        bitmap?.let { bmp ->
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val imageWidth = bmp.width.toFloat()
            val imageHeight = bmp.height.toFloat()

            if (viewWidth <= 0 || viewHeight <= 0) {
                android.util.Log.d("CropImageView", "View boyutları henüz hazır değil")
                return
            }

            // Aspect ratio'yu koru
            val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
            val scaledWidth = imageWidth * scale
            val scaledHeight = imageHeight * scale

            val left = (viewWidth - scaledWidth) / 2
            val top = (viewHeight - scaledHeight) / 2

            imageRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
            scaleX = scaledWidth / imageWidth
            scaleY = scaledHeight / imageHeight

            android.util.Log.d("CropImageView", "Image rect hesaplandı: $imageRect, scale: $scaleX x $scaleY")
        }
    }

    private fun imageToViewCoordinates(imagePoint: PointF): PointF {
        return PointF(
            imageRect.left + imagePoint.x * scaleX,
            imageRect.top + imagePoint.y * scaleY
        )
    }

    private fun viewToImageCoordinates(viewPoint: PointF): PointF {
        return PointF(
            (viewPoint.x - imageRect.left) / scaleX,
            (viewPoint.y - imageRect.top) / scaleY
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Bitmap'i çiz
        bitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, imageRect, imagePaint)
        }
        
        // Kırpma alanını çiz
        if (corners.size == 4) {
            // Dolgu alanı
            val path = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[2].x, corners[2].y)
                lineTo(corners[3].x, corners[3].y)
                close()
            }
            canvas.drawPath(path, fillPaint)
            
            // Çizgiler
            for (i in corners.indices) {
                val start = corners[i]
                val end = corners[(i + 1) % corners.size]
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
            }
            
            // Köşe noktaları
            corners.forEach { corner ->
                canvas.drawCircle(corner.x, corner.y, 20f, cornerPaint)
                canvas.drawCircle(corner.x, corner.y, 15f, Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                })
                canvas.drawCircle(corner.x, corner.y, 8f, cornerPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selectedCornerIndex = findNearestCorner(event.x, event.y)
                return selectedCornerIndex != -1
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (selectedCornerIndex != -1) {
                    // Köşeyi hareket ettir (image sınırları içinde)
                    val newX = event.x.coerceIn(imageRect.left, imageRect.right)
                    val newY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                    
                    corners[selectedCornerIndex] = PointF(newX, newY)
                    invalidate()
                    return true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                selectedCornerIndex = -1
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }

    private fun findNearestCorner(x: Float, y: Float): Int {
        for (i in corners.indices) {
            val corner = corners[i]
            val distance = sqrt((x - corner.x) * (x - corner.x) + (y - corner.y) * (y - corner.y))
            if (distance <= touchRadius) {
                return i
            }
        }
        return -1
    }
}
