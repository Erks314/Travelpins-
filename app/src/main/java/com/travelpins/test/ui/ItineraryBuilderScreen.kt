package com.travelpins.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.travelpins.test.data.Category
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.itinerary.ItineraryPlace
import com.travelpins.test.itinerary.ItineraryState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
fun ItineraryBuilderScreen(
    repository: TravelPinsRepository,
    listId: String?,
    listName: String?,
    onBack: () -> Unit,
    onViewItinerary: () -> Unit
) {
    val allPlaces by repository.places.collectAsState(initial = emptyList())
    val categories by repository.categories.collectAsState(initial = emptyList())
    val itineraryPlaces by ItineraryState.places.collectAsState()

    val listPlaces = remember(allPlaces, listId) { allPlaces.filter { it.sourceListId == listId } }

    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedPhotoUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedPlace?.id) {
        val placeId = selectedPlace?.id
        if (placeId != null) {
            val photos = repository.observePhotosByPlace(placeId).first()
            selectedPhotoUrl = photos.firstOrNull()?.sizedUrl(400, 400)
        } else {
            selectedPhotoUrl = null
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.9, 12.5), 5f)
    }

    LaunchedEffect(listPlaces.size) {
        if (listPlaces.isEmpty()) return@LaunchedEffect

        try {
            snapshotFlow { cameraPositionState.projection }
                .filter { it != null }
                .first()

            if (listPlaces.size == 1) {
                val p = listPlaces.first()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 12f),
                    durationMs = 600
                )
            } else {
                val bounds = LatLngBounds.Builder()
                listPlaces.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
                val b = bounds.build()
                try {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(b, 140),
                        durationMs = 800
                    )
                } catch (_: Exception) {
                    val center = LatLng(
                        (b.southwest.latitude + b.northeast.latitude) / 2,
                        (b.southwest.longitude + b.northeast.longitude) / 2
                    )
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(center, 10f),
                        durationMs = 800
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    val bottomBarVisible = itineraryPlaces.isNotEmpty()

    Box(Modifier.fillMaxSize().background(TPColors.Bg)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { selectedPlace = null }
        ) {
            listPlaces.forEach { place ->
                val markerState = rememberMarkerState(position = LatLng(place.latitude, place.longitude))
                val positionInItinerary = itineraryPlaces.indexOfFirst { it.placeId == place.id }
                val isInItinerary = positionInItinerary >= 0

                Marker(
                    state = markerState,
                    title = place.name,
                    icon = if (isInItinerary) {
                        numberedGreenIcon(positionInItinerary + 1)
                    } else {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    },
                    onClick = {
                        selectedPlace = place
                        selectedCategory = categories.firstOrNull { it.id == place.categoryId }
                        true
                    }
                )
            }
        }

        Box(
            Modifier.padding(16.dp).size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        selectedPlace?.let { place ->
            val isInItinerary = itineraryPlaces.any { it.placeId == place.id }

            Box(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (bottomBarVisible) 92.dp else 16.dp
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(TPColors.Surface)
                    .padding(16.dp)
            ) {
                Column {
                    if (!isInItinerary && selectedPhotoUrl != null) {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TPColors.SurfaceAlt)
                        ) {
                            AsyncImage(
                                model = selectedPhotoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(place.name, color = TPColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    if (selectedCategory != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            CategoryIcon(
                                iconKey = selectedCategory!!.iconKey,
                                tint = Color(selectedCategory!!.colorArgb),
                                modifier = Modifier.size(16.dp),
                                emojiFontSize = 13.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(selectedCategory!!.name, color = Color(selectedCategory!!.colorArgb), fontSize = 13.sp)
                        }
                    }

                    if (!place.address.isNullOrBlank()) {
                        Text(place.address!!, color = TPColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isInItinerary) TPColors.SurfaceAlt else TPColors.Accent)
                            .clickable {
                                if (isInItinerary) {
                                    ItineraryState.remove(place.id)
                                } else {
                                    val category = categories.firstOrNull { it.id == place.categoryId }
                                    ItineraryState.add(ItineraryPlace.fromPlace(place, category, selectedPhotoUrl))
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isInItinerary) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                tint = if (isInItinerary) TPColors.TextMuted else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isInItinerary) "RIMUOVI DALL'ITINERARIO" else "AGGIUNGI ALL'ITINERARIO",
                                color = if (isInItinerary) TPColors.TextMuted else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (bottomBarVisible) {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(TPColors.Surface)
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${itineraryPlaces.size} ${if (itineraryPlaces.size == 1) "luogo selezionato" else "luoghi selezionati"}",
                        color = TPColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(TPColors.Accent)
                            .clickable { onViewItinerary() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("VEDI ITINERARIO", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
