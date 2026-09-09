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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.travelpins.test.itinerary.ItineraryState

@Composable
fun ItineraryOrderScreen(
    onBack: () -> Unit,
    onAddMore: () -> Unit,
    onCalculate: () -> Unit
) {
    val itineraryPlaces by ItineraryState.places.collectAsState()

    Column(
        Modifier.fillMaxSize().background(TPColors.Bg).padding(16.dp)
    ) {
        // Header
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
                        "${itineraryPlaces.size} ${if (itineraryPlaces.size == 1) "luogo" else "luoghi"} · Ordina le tappe",
                        color = TPColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Lista drag & drop
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(itineraryPlaces) { index, place ->
                ItineraryPlaceCard(
                    place = place,
                    position = index + 1,
                    onRemove = { ItineraryState.remove(place.placeId) },
                    onDragStart = { /* drag start */ },
                    onDragEnd = { fromIndex, toIndex ->
                        ItineraryState.reorder(fromIndex, toIndex)
                    },
                    currentIndex = index
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Pulsante AGGIUNGI ALTRI LUOGHI
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

        // Pulsante CALCOLA ITINERARIO
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TPColors.Accent)
                .clickable(enabled = itineraryPlaces.size >= 2) { onCalculate() }
                .padding(vertical = 14.dp)
                .alpha(if (itineraryPlaces.size >= 2) 1f else 0.5f),
            contentAlignment = Alignment.Center
        ) {
            Text("CALCOLA ITINERARIO", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ItineraryPlaceCard(
    place: com.travelpins.test.itinerary.ItineraryPlace,
    position: Int,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: (fromIndex: Int, toIndex: Int) -> Unit,
    currentIndex: Int
) {
    var isDragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f)
    val alpha by animateFloatAsState(if (isDragging) 0.7f else 1f)

    Row(
        Modifier.fillMaxWidth()
            .height(80.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(16.dp))
            .background(TPColors.Surface)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true; onDragStart() },
                    onDragEnd = { isDragging = false; onDragEnd(currentIndex, currentIndex) },
                    onDragCancel = { isDragging = false }
                ) { _, _ -> }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Maniglia drag
        Icon(Icons.Filled.DragHandle, contentDescription = "Trascina", tint = TPColors.TextMuted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))

        // Numero
        Box(
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(TPColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Text("$position", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))

        // Foto
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

        // Info
        Column(Modifier.weight(1f)) {
            Text(place.name, color = TPColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (!place.address.isNullOrBlank()) {
                Text(place.address!!, color = TPColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }

        // Pulsante rimuovi
        Box(
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(TPColors.SurfaceAlt)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Rimuovi", tint = TPColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
