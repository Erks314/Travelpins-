package com.travelpins.test.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
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
 * Pin circolare del COLORE della categoria con l'ICONA della categoria al centro.
 * - iconKey vettoriale -> disegna l'ImageVector rasterizzato.
 * - iconKey emoji legacy -> disegna l'emoji come testo.
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

    if (def != null) {
        val vector = def.icon
        val vpath = imageVectorToAndroidPath(vector)
        val vw = vector.viewportWidth
        val vh = vector.viewportHeight
        val iconArea = size * 0.54f
        val scale = iconArea / maxOf(vw, vh)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(size / 2f - vw * scale / 2f, size / 2f - vh * scale / 2f)
        vpath.transform(matrix)

        val iconPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(vpath, iconPaint)
    } else {
        val text = iconKey ?: "?"
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
    }

    val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
    categoryPinCache[cacheKey] = descriptor
    return descriptor
}

/** Converte un ImageVector Compose in un android.graphics.Path (coordinate viewport). */
private fun imageVectorToAndroidPath(vector: ImageVector): android.graphics.Path {
    val path = android.graphics.Path()
    fun handleGroup(group: VectorGroup) {
        group.items.forEach { item ->
            when (item) {
                is VectorGroup -> handleGroup(item)
                is VectorPath -> addPathNodes(path, item.pathData)
            }
        }
    }
    handleGroup(vector.root)
    return path
}

private fun addPathNodes(path: android.graphics.Path, nodes: List<PathNode>) {
    var cx = 0f; var cy = 0f
    var lx = 0f; var ly = 0f

    nodes.forEach { node ->
        when (node) {
            is PathNode.MoveTo -> { path.moveTo(node.x, node.y); cx = node.x; cy = node.y }
            is PathNode.RelativeMoveTo -> { cx += node.dx; cy += node.dy; path.moveTo(cx, cy) }
            is PathNode.LineTo -> { path.lineTo(node.x, node.y); cx = node.x; cy = node.y }
            is PathNode.RelativeLineTo -> { cx += node.dx; cy += node.dy; path.lineTo(cx, cy) }
            is PathNode.HorizontalTo -> { path.lineTo(node.x, cy); cx = node.x }
            is PathNode.RelativeHorizontalTo -> { cx += node.dx; path.lineTo(cx, cy) }
            is PathNode.VerticalTo -> { path.lineTo(cx, node.y); cy = node.y }
            is PathNode.RelativeVerticalTo -> { cy += node.dy; path.lineTo(cx, cy) }
            is PathNode.CurveTo -> {
                path.cubicTo(node.x1, node.y1, node.x2, node.y2, node.x, node.y)
                lx = node.x2; ly = node.y2; cx = node.x; cy = node.y
            }
            is PathNode.RelativeCurveTo -> {
                val x1 = cx + node.dx1; val y1 = cy + node.dy1
                val x2 = cx + node.dx2; val y2 = cy + node.dy2
                val x = cx + node.dx; val y = cy + node.dy
                path.cubicTo(x1, y1, x2, y2, x, y)
                lx = x2; ly = y2; cx = x; cy = y
            }
            is PathNode.ReflectiveCurveTo -> {
                val x1 = 2 * cx - lx; val y1 = 2 * cy - ly
                path.cubicTo(x1, y1, node.x1, node.y1, node.x, node.y)
                lx = node.x1; ly = node.y1; cx = node.x; cy = node.y
            }
            is PathNode.RelativeReflectiveCurveTo -> {
                val x1 = 2 * cx - lx; val y1 = 2 * cy - ly
                val x2 = cx + node.dx1; val y2 = cy + node.dy1
                val x = cx + node.dx; val y = cy + node.dy
                path.cubicTo(x1, y1, x2, y2, x, y)
                lx = x2; ly = y2; cx = x; cy = y
            }
            is PathNode.QuadTo -> {
                path.quadTo(node.x1, node.y1, node.x, node.y)
                lx = node.x1; ly = node.y1; cx = node.x; cy = node.y
            }
            is PathNode.RelativeQuadTo -> {
                val x1 = cx + node.dx1; val y1 = cy + node.dy1
                val x = cx + node.dx; val y = cy + node.dy
                path.quadTo(x1, y1, x, y)
                lx = x1; ly = y1; cx = x; cy = y
            }
            is PathNode.ReflectiveQuadTo -> {
                val x1 = 2 * cx - lx; val y1 = 2 * cy - ly
                path.quadTo(x1, y1, node.x, node.y)
                lx = x1; ly = y1; cx = node.x; cy = node.y
            }
            is PathNode.RelativeReflectiveQuadTo -> {
                val x1 = 2 * cx - lx; val y1 = 2 * cy - ly
                val x = cx + node.dx; val y = cy + node.dy
                path.quadTo(x1, y1, x, y)
                lx = x1; ly = y1; cx = x; cy = y
            }
            is PathNode.ArcTo -> { path.lineTo(node.x, node.y); cx = node.x; cy = node.y }
            is PathNode.RelativeArcTo -> { cx += node.dx; cy += node.dy; path.lineTo(cx, cy) }
            is PathNode.Close -> path.close()
        }
    }
}
