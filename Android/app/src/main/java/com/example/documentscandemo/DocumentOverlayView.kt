package com.example.documentscandemo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f) // Kesikli çizgi
    }

    private val fillPaint = Paint().apply {
        color = Color.argb(30, 0, 255, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var documentCorners: Array<PointF>? = null
    private var isDocumentDetected = false

    fun updateDocumentCorners(corners: Array<PointF>?) {
        documentCorners = corners
        isDocumentDetected = corners != null
        
        // Renk güncelle
        if (isDocumentDetected) {
            paint.color = Color.GREEN
            fillPaint.color = Color.argb(30, 0, 255, 0)
            cornerPaint.color = Color.GREEN
        } else {
            paint.color = Color.RED
            fillPaint.color = Color.argb(30, 255, 0, 0)
            cornerPaint.color = Color.RED
        }
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        documentCorners?.let { corners ->
            if (corners.size == 4) {
                // Belge alanını doldur
                val path = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    lineTo(corners[1].x, corners[1].y)
                    lineTo(corners[2].x, corners[2].y)
                    lineTo(corners[3].x, corners[3].y)
                    close()
                }
                canvas.drawPath(path, fillPaint)
                
                // Köşeleri çiz
                for (i in corners.indices) {
                    val start = corners[i]
                    val end = corners[(i + 1) % corners.size]
                    canvas.drawLine(start.x, start.y, end.x, end.y, paint)
                }
                
                // Köşe noktalarını büyük daireler olarak çiz
                corners.forEach { corner ->
                    canvas.drawCircle(corner.x, corner.y, 20f, cornerPaint)
                    canvas.drawCircle(corner.x, corner.y, 15f, Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    })
                    canvas.drawCircle(corner.x, corner.y, 8f, cornerPaint)
                }
            }
        }
    }
}
