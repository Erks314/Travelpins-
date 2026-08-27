package com.travelpins.test.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.travelpins.test.data.Category
import com.travelpins.test.data.Place

@Composable
fun PlaceMapScreen(
    place: Place,
    category: Category?,
    onBack: () -> Unit
) {
    val position = LatLng(place.latitude, place.longitude)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(position, 15f)
    }

    val hue = if (category != null) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(category.colorArgb, hsv)
        hsv[0]
    } else {
        BitmapDescriptorFactory.HUE_AZURE
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            Marker(
                position = position,
                title = place.name,
                snippet = place.address,
                icon = BitmapDescriptorFactory.defaultMarker(hue)
            )
        }

        Box(Modifier.padding(16.dp)) {
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
        }
    }
}
