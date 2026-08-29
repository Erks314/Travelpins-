package com.travelpins.test.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.travelpins.test.data.Place
import com.travelpins.test.data.PlacePhoto
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.launch

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
            AsyncImage(
                model = photo.sizedUrl(1600),
                contentDescription = null,
                contentScale = ContentScale.Fit,
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
