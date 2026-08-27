package com.travelpins.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.travelpins.test.data.PlacePhoto

@Composable
fun GalleryScreen(
    photos: List<PlacePhoto>,
    startIndex: Int = 0,
    title: String,
    onBack: () -> Unit
) {
    if (photos.isEmpty()) {
        onBack()
        return
    }

    val initialPage = startIndex.coerceIn(0, photos.size - 1)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { photos.size }
    )
    val current = pagerState.currentPage

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        tint = Color.White
                    )
                },
                onClick = onBack
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Text(
                    "${current + 1} / ${photos.size}",
                    color = TPColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                model = photos[page].sizedUrl(1600)
            )
        }
    }
}

/**
 * Immagine con zoom.
 *
 * Regole di consumo dei tocchi (fondamentali per lo swipe):
 * - 1 dito a scala 1x  -> NON consumo: lo swipe passa al HorizontalPager
 * - 2 dita (pinch)     -> consumo e applico zoom
 * - 1 dito a scala >1x -> consumo e applico pan sull'immagine zoomata
 * - doppio tap         -> toggle zoom 1x / 2.5x
 */
@Composable
fun ZoomableImage(model: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val changes = event.changes

                        if (changes.size >= 2) {
                            // PINCH: zoom
                            val zoomChange = event.calculateZoom()
                            scale = (scale * zoomChange).coerceIn(1f, 4f)
                            if (scale <= 1.01f) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            changes.forEach { it.consume() }
                        } else if (scale > 1f && changes.size == 1) {
                            // PAN su immagine zoomata
                            val c = changes[0]
                            offsetX += c.position.x - c.previousPosition.x
                            offsetY += c.position.y - c.previousPosition.y
                            c.consume()
                        }
                        // altrimenti: nessun consume -> il pager scorre
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}
