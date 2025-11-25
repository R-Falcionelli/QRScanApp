package com.example.qrlookup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class Reticleview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var borderColor: Int =
        ContextCompat.getColor(context, android.R.color.white)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = ContextCompat.getColor(context, android.R.color.white)
    }

    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x66000000  // noir semi-transparent autour
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Taille du cadre (70% de la largeur, par ex.)
        val boxSize = width * 0.8f
        val left = (width - boxSize) / 2f
        val top = (height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize
        val boxRect = RectF(left, top, right, bottom)

        // Assombrir autour (facultatif, mais ça fait classe)
        // On dessine 4 rectangles autour du carré central
        canvas.drawRect(0f, 0f, width, top, shadePaint)
        canvas.drawRect(0f, bottom, width, height, shadePaint)
        canvas.drawRect(0f, top, left, bottom, shadePaint)
        canvas.drawRect(right, top, width, bottom, shadePaint)

        // Bord blanc du réticule
        canvas.drawRect(boxRect, borderPaint)
    }

    /** Flash vert pendant 150 ms */
    fun flash() {
        borderColor = ContextCompat.getColor(context, android.R.color.holo_green_light)
        invalidate()

        postDelayed({
            borderColor = ContextCompat.getColor(context, android.R.color.white)
            invalidate()
        }, 150)
    }
}