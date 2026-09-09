package com.travelpins.test.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

private val numberedIconCache = mutableMapOf<Int, BitmapDescriptor>()

/**
 * Pin VERDE circolare con il numero bianco al centro.
 * Condiviso tra Builder e Result. Disegnato con Canvas, nessuna libreria.
 */
fun numberedGreenIcon(number: Int): BitmapDescriptor {
    numberedIconCache[number]?.let { return it }

    val size = 110
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fillPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#2EBD95")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, fillPaint)

    val borderPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 8f, borderPaint)

    val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val text = number.toString()
    val bounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, bounds)
    canvas.drawText(text, size / 2f, size / 2f + bounds.height() / 2f, textPaint)

    val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
    numberedIconCache[number] = descriptor
    return descriptor
}
