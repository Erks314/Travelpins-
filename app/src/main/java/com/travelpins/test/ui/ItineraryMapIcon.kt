package com.travelpins.test.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

private val numberedIconCache = mutableMapOf<Int, BitmapDescriptor>()
private val categoryPinCache = mutableMapOf<String, BitmapDescriptor>()

/** Colore usato per i luoghi SENZA categoria. */
const val NO_CATEGORY_COLOR: Int = 0xFF0EA5E9.toInt()

/**
 * Pin VERDE circolare con il numero bianco al centro (itinerario).
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

/**
 * Pin circolare del COLORE della categoria con l'EMOJI della categoria al centro.
 * - iconKey vettoriale -> disegna l'emoji associata (es. "🏛️" per Musei).
 * - iconKey emoji legacy -> disegna l'emoji stessa.
 * - iconKey null (nessuna categoria) -> disegna "?".
 */
fun categoryPinIcon(colorArgb: Int, iconKey: String?): BitmapDescriptor {
    val cacheKey = "$colorArgb|${iconKey ?: "__none__"}"
    categoryPinCache[cacheKey]?.let { return it }

    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fill = Paint().apply {
        isAntiAlias = true
        color = colorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, fill)

    val border = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, border)

    val def = iconKey?.let { CategoryIcons.defFor(it) }
    val text = when {
        def != null -> def.emoji  // Usa l'emoji associata alla categoria vettoriale
        iconKey != null -> iconKey  // Categorie legacy (già emoji)
        else -> "?"  // Nessuna categoria
    }

    val tp = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        textSize = size * 0.52f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val bounds = Rect()
    tp.getTextBounds(text, 0, text.length, bounds)
    canvas.drawText(text, size / 2f, size / 2f + bounds.height() / 2f, tp)

    val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
    categoryPinCache[cacheKey] = descriptor
    return descriptor
}
