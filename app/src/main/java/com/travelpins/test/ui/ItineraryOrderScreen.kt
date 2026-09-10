package com.travelpins.test.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.travelpins.test.itinerary.ItineraryPlace
import com.travelpins.test.itinerary.ItineraryState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.round

private val CARD_HEIGHT_DP = 88
private val CARD_SPACING_DP = 10

@Composable
fun ItineraryOrderScreen(
    onBack: () -> Unit,
    onAddMore: () -> Unit,
    onCalculate: () -> Unit
) {
    val order by ItineraryState.places.collectAsState()

    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // FIX: al rilascio "congela" le animazioni per un frame, così non si vede lo scatto
    var snapRelease by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val stepPx = with(density) { (CARD_HEIGHT_DP + CARD_SPACING_DP).dp.toPx() }

    val size = order.size
    val from = dragId?.let { id -> order.indexOfFirst { it.placeId == id } } ?: -1
    val target = if (from >= 0 && size > 0) {
        (from + round(dragOffset / stepPx).toInt()).coerceIn(0, size - 1)
    } else {
        -1
    }

    Column(
        Modifier.fillMaxSize().background(TPColors.Bg).padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(TPColors.Surface)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = TPColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("IL TUO ITINERARIO", color = TPColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${order.size} ${if (order.size == 1) "luogo" else "luoghi"} · Trascina la maniglia ☰",
                        color = TPColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            order.forEachIndexed { i, place ->
                val isDragging = place.placeId == dragId

                val staticOffset = when {
                    from < 0 || i == from -> 0f
                    from < target && i > from && i <= target -> -stepPx
                    from > target && i >= target && i < from -> stepPx
                    else -> 0f
                }

                ItineraryPlaceCard(
                    place = place,
                    position = i + 1,
                    isDragging = isDragging,
                    dragOffset = dragOffset,
                    staticOffset = staticOffset,
                    snap = snapRelease,
                    onDragStart = {
                        dragId = place.placeId
                        dragOffset = 0f
                    },
                    onDragMove = { delta -> dragOffset += delta },
                    onDragEnd = { finalOffset ->
                        val currentSize = order.size
                        val currentFrom = dragId?.let { id -> order.indexOfFirst { it.placeId == id } } ?: -1
                        val currentTarget = if (currentFrom >= 0 && currentSize > 0) {
                            (currentFrom + round(finalOffset / stepPx).toInt()).coerceIn(0, currentSize - 1)
                        } else {
                            -1
                        }

                        if (currentFrom in 0 until currentSize && currentTarget in 0 until currentSize && currentTarget != currentFrom) {
                            ItineraryState.reorder(currentFrom, currentTarget)
                        }

                        // Congela le animazioni, resetta, poi riattiva
                        snapRelease = true
                        dragId = null
                        dragOffset = 0f
                        scope.launch {
                            delay(32)
                            snapRelease = false
                        }
                    },
                    onRemove = { ItineraryState.remove(place.placeId) }
                )

                Spacer(Modifier.height(CARD_SPACING_DP.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TPColors.Surface)
                .clickable { onAddMore() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = TPColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("AGGIUNGI ALTRI LUOGHI", color = TPColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TPColors.Accent)
                .clickable(enabled = order.size >= 2) { onCalculate() }
                .padding(vertical = 14.dp)
                .alpha(if (order.size >= 2) 1f else 0.5f),
            contentAlignment = Alignment.Center
        ) {
            Text("CALCOLA ITINERARIO", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ItineraryPlaceCard(
    place: ItineraryPlace,
    position: Int,
    isDragging: Boolean,
    dragOffset: Float,
    staticOffset: Float,
    snap: Boolean,
    onDragStart: () -> Unit,
    onDragMove: (Float) -> Unit,
    onDragEnd: (finalOffset: Float) -> Unit,
    onRemove: () -> Unit
) {
    val animatedStatic by animateFloatAsState(staticOffset)

    // Durante il drag segue il dito; al rilascio (snap) va istantaneo a 0; altrimenti anima
    val translationY = when {
        isDragging -> dragOffset
        snap -> staticOffset
        else -> animatedStatic
    }

    val scale by animateFloatAsState(if (isDragging) 1.03f else 1f)

    var localDragOffset by remember { mutableFloatStateOf(0f) }

    Row(
        Modifier.fillMaxWidth()
            .height(CARD_HEIGHT_DP.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                this.translationY = translationY
                this.scaleX = scale
                this.scaleY = scale
            }
            .then(
                if (isDragging) Modifier.shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDragging) TPColors.SurfaceAlt else TPColors.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp, 70.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Trascina per riordinare",
                tint = if (isDragging) TPColors.Accent else TPColors.TextMuted,
                modifier = Modifier
                    .size(28.dp)
                    .pointerInput(place.placeId) {
                        detectDragGestures(
                            onDragStart = {
                                localDragOffset = 0f
                                onDragStart()
                            },
                            onDragEnd = { onDragEnd(localDragOffset) },
                            onDragCancel = { onDragEnd(localDragOffset) }
                        ) { change, dragAmount ->
                            change.consume()
                            localDragOffset += dragAmount.y
                            onDragMove(dragAmount.y)
                        }
                    }
            )
        }
        Spacer(Modifier.width(4.dp))

        Box(
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(TPColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Text("$position", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))

        Box(
            Modifier.size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TPColors.SurfaceAlt)
        ) {
            if (place.photoUrl != null) {
                AsyncImage(
                    model = place.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(place.name, color = TPColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (!place.address.isNullOrBlank()) {
                Text(place.address!!, color = TPColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }

        Box(
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(TPColors.SurfaceAlt)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Rimuovi", tint = TPColors.TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}
