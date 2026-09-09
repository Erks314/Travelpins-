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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.travelpins.test.itinerary.ItineraryRoute
import com.travelpins.test.itinerary.ItineraryState
import com.travelpins.test.itinerary.PolylineDecoder
import com.travelpins.test.itinerary.RouteCalculator
import java.text.DecimalFormat

// Blu vivido e ben visibile sulla mappa
private val ROUTE_BLUE = Color(0xFF1E88E5)

@Composable
fun ItineraryResultScreen(
    onBack: () -> Unit,
    onEditOrder: () -> Unit
) {
    val itineraryPlaces by ItineraryState.places.collectAsState()
    var route by remember { mutableStateOf<ItineraryRoute?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        when (val result = RouteCalculator.compute(itineraryPlaces)) {
            is RouteCalculator.Result.Success -> {
                route = result.route
                isLoading = false
            }
            is RouteCalculator.Result.Error -> {
                errorMessage = result.message
                isLoading = false
            }
        }
    }

    val polylinePoints = remember(route?.polylineEncoded) {
        route?.polylineEncoded?.let { PolylineDecoder.decode(it) } ?: emptyList()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.9, 12.5), 5f)
    }

    // Inquadra il percorso / le tappe appena pronti
    LaunchedEffect(polylinePoints.size, itineraryPlaces.size) {
        val points = if (polylinePoints.isNotEmpty()) polylinePoints
        else itineraryPlaces.map { LatLng(it.latitude, it.longitude) }
        if (points.isEmpty()) return@LaunchedEffect
        try {
            if (points.size == 1) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(points.first(), 12f),
                    durationMs = 600
                )
            } else {
                val bounds = LatLngBounds.Builder()
                points.forEach { bounds.include(it) }
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), 100),
                    durationMs = 800
                )
            }
        } catch (_: Exception) {
        }
    }

    Column(Modifier.fillMaxSize().background(TPColors.Bg)) {
        // Header fisso
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
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
                Text("IL TUO ITINERARIO", color = TPColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(TPColors.Surface)
                    .clickable { onEditOrder() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = TPColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("MODIFICA", color = TPColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TPColors.Accent)
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Errore", color = TPColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage!!, color = TPColors.TextSecondary, fontSize = 14.sp)
                }
            }
        } else if (route != null) {
            // MAPPA FISSA (non dentro lo scroll): i gesti pan/zoom funzionano
            Box(
                Modifier.fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TPColors.SurfaceAlt)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    itineraryPlaces.forEachIndexed { index, place ->
                        val markerState = rememberMarkerState(position = LatLng(place.latitude, place.longitude))
                        Marker(
                            state = markerState,
                            title = "${index + 1}. ${place.name}",
                            icon = numberedGreenIcon(index + 1)
                        )
                    }

                    if (polylinePoints.isNotEmpty()) {
                        Polyline(
                            points = polylinePoints,
                            color = ROUTE_BLUE,
                            width = 12f
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Contenuto scrollabile sotto la mappa
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                // Sintesi
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TPColors.Surface)
                        .padding(16.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                DecimalFormat("#.0").format(route!!.totalDistanceKm),
                                color = TPColors.Accent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("km", color = TPColors.TextSecondary, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${route!!.totalDurationMinutes}",
                                color = TPColors.Accent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("min", color = TPColors.TextSecondary, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${itineraryPlaces.size}",
                                color = TPColors.Accent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("tappe", color = TPColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Lista tappe con distanze
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (index in itineraryPlaces.indices) {
                        val place = itineraryPlaces[index]

                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TPColors.Surface)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(28.dp)
                                    .clip(CircleShape)
                                    .background(TPColors.Accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(place.name, color = TPColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (index < route!!.legs.size) {
                            val leg = route!!.legs[index]
                            Row(
                                Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("↓", color = TPColors.TextMuted, fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${DecimalFormat("#.0").format(leg.distanceKm)} km · ${leg.durationMinutes} min",
                                    color = TPColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Totale
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TPColors.Accent.copy(alpha = 0.15f))
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTALE", color = TPColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${DecimalFormat("#.0").format(route!!.totalDistanceKm)} km · ${route!!.totalDurationMinutes} min",
                                color = TPColors.Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
