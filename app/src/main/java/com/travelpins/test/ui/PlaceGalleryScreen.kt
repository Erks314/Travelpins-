package com.travelpins.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.mutableStateOf
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
    title: String,
    onBack: () -> Unit
) {
    if (photos.isEmpty()) {
        onBack()
        return
    }

    val pagerState = rememberPagerState(pageCount = { photos.size })
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

@Composable
fun ZoomableImage(model: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    if (newScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    scale = newScale
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
