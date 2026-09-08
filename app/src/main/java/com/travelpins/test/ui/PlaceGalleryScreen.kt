package com.travelpins.test.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.travelpins.test.data.Place
import com.travelpins.test.data.PlacePhoto
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    photos: List<PlacePhoto>,
    startIndex: Int,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TravelPinsRepository(context) }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, maxOf(0, photos.size - 1)),
        pageCount = { photos.size }
    )

    val placeId = photos.firstOrNull()?.placeId
    var place by remember { mutableStateOf<Place?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(placeId) {
        if (placeId != null) place = repository.getPlaceById(placeId)
    }

    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val photo = photos[page]
            ZoomableImage(
                photo = photo,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleIconButton(
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White) },
                onClick = onBack
            )

            Text(
                "${title}  •  ${pagerState.currentPage + 1}/${photos.size}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )

            Box {
                CircleIconButton(
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "Menu", tint = Color.White) },
                    onClick = { menuOpen = true }
                )

                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Imposta come copertina del luogo") },
                        onClick = {
                            menuOpen = false
                            val photo = currentPhoto ?: return@DropdownMenuItem
                            val pid = placeId ?: return@DropdownMenuItem
                            scope.launch {
                                repository.setPlaceCoverPhoto(pid, photo.photoKey)
                                Toast.makeText(context, "Copertina luogo aggiornata", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    if (place?.sourceListId != null) {
                        DropdownMenuItem(
                            text = { Text("Imposta come copertina dell'elenco") },
                            onClick = {
                                menuOpen = false
                                val photo = currentPhoto ?: return@DropdownMenuItem
                                repository.setListCover(place?.sourceListId, photo.imageUrl)
                                Toast.makeText(context, "Copertina elenco aggiornata", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomableImage(
    photo: PlacePhoto,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imgSize by remember { mutableStateOf(IntSize.Zero) }

    // Dimensioni con cui l'immagine viene disegnata a zoom 1x (ContentScale.Fit)
    val drawnW: Float
    val drawnH: Float
    if (boxSize.width > 0 && boxSize.height > 0 && imgSize.width > 0 && imgSize.height > 0) {
        val fit = min(
            boxSize.width.toFloat() / imgSize.width.toFloat(),
            boxSize.height.toFloat() / imgSize.height.toFloat()
        )
        drawnW = imgSize.width.toFloat() * fit
        drawnH = imgSize.height.toFloat() * fit
    } else {
        drawnW = 0f
        drawnH = 0f
    }

    // Limita il pan così l'immagine zoomata non esce MAI dal box (niente sfondo nero)
    fun clamp(off: Offset, sc: Float): Offset {
        if (drawnW <= 0f || drawnH <= 0f) return Offset.Zero
        val maxX = max(0f, (drawnW * sc - boxSize.width) / 2f)
        val maxY = max(0f, (drawnH * sc - boxSize.height) / 2f)
        return Offset(
            off.x.coerceIn(-maxX, maxX),
            off.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            // Gesture custom: pinch + pan condizionale (lascia passare lo swipe al pager)
            .pointerInput(photo.photoKey) {
                forEachGesture {
                    awaitPointerEventScope {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointers = mutableMapOf<PointerId, Offset>(down.id to down.position)
                        var prevCentroid = down.position
                        var prevDistance = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            var anyPressed = false

                            event.changes.forEach { ch ->
                                if (ch.pressed) {
                                    anyPressed = true
                                    pointers[ch.id] = ch.position
                                } else {
                                    pointers.remove(ch.id)
                                }
                            }

                            if (!anyPressed || pointers.isEmpty()) break

                            if (pointers.size >= 2) {
                                // PINCH: zoom + pan con due dita (evento consumato)
                                val pts = pointers.values.toList()
                                val centroid = Offset(
                                    pts.sumOf { it.x.toDouble() }.toFloat() / pts.size,
                                    pts.sumOf { it.y.toDouble() }.toFloat() / pts.size
                                )
                                val dx = pts[0].x - pts[1].x
                                val dy = pts[0].y - pts[1].y
                                val distance = sqrt(dx * dx + dy * dy)

                                if (prevDistance > 0f && distance > 0f) {
                                    val newScale = (scale * (distance / prevDistance)).coerceIn(1f, 5f)
                                    val pan = centroid - prevCentroid
                                    scale = newScale
                                    offset = clamp(offset + pan, newScale)
                                }
                                prevDistance = distance
                                prevCentroid = centroid
                                event.changes.forEach { it.consume() }
                            } else if (pointers.size == 1) {
                                if (scale > 1f) {
                                    // PAN: un dito ma immagine zoomata (evento consumato)
                                    val ch = event.changes.firstOrNull { it.pressed }
                                    if (ch != null) {
                                        offset = clamp(offset + ch.positionChange(), scale)
                                        ch.consume()
                                    }
                                }
                                // Se scale == 1f NON consumo: lo swipe arriva al pager
                            }
                        }
                        prevDistance = 0f
                    }
                }
            }
            // Doppio tap: toggle zoom
            .pointerInput(photo.photoKey) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = photo.sizedUrl(1600),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val intr = state.painter.intrinsicSize
                if (intr.width > 0f && intr.height > 0f) {
                    imgSize = IntSize(intr.width.roundToInt(), intr.height.roundToInt())
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
