package com.travelpins.test.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.travelpins.test.data.Category
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.flow.first
import java.util.Locale

private class ListCurvedShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val curve = 26f * density.density
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - curve)
            quadraticTo(size.width / 2f, size.height + curve, 0f, size.height - curve)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun rememberListCover(repository: TravelPinsRepository, placeIds: List<Long>, width: Int): String? {
    var url by remember(placeIds) { mutableStateOf<String?>(null) }
    LaunchedEffect(placeIds) {
        for (id in placeIds) {
            val photos = repository.observePhotosByPlace(id).first()
            if (photos.isNotEmpty()) {
                url = photos.first().sizedUrl(width)
                return@LaunchedEffect
            }
        }
        url = null
    }
    return url
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TravelPinsListDetailScreen(
    repository: TravelPinsRepository,
    listId: String?,
    listName: String?,
    onBack: () -> Unit,
    onOpenMap: (Long?) -> Unit,
    onOpenPlace: (Long) -> Unit,
    onChangeCategory: (Place) -> Unit,
    onCreateCategory: () -> Unit,
    onManageCategories: () -> Unit
) {
    val allPlaces by repository.places.collectAsState(initial = emptyList())
    val categories by repository.categories.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var filterId by remember { mutableStateOf<Long?>(null) }

    val listPlaces = remember(allPlaces, listId) { allPlaces.filter { it.sourceListId == listId } }

    val visiblePlaces = remember(listPlaces, filterId, query) {
        var result = when (filterId) {
            null -> listPlaces
            -1L -> listPlaces.filter { it.categoryId == null }
            else -> listPlaces.filter { it.categoryId == filterId }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase(Locale.ITALY)
            result = result.filter {
                it.name.lowercase(Locale.ITALY).contains(q) ||
                    it.address?.lowercase(Locale.ITALY)?.contains(q) == true
            }
        }
        result
    }

    Box(Modifier.fillMaxSize().background(TPColors.Bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ListHeader(repository, listPlaces, listName, onBack, onManageCategories)
            ControlsRow(query, { query = it }, filterId, { filterId = it }, categories, { onOpenMap(filterId) })

            Text(
                "I LUOGHI IN QUESTO ELENCO",
                color = TPColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            if (listPlaces.isEmpty()) {
                Text("Nessun luogo in questo elenco.", color = TPColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
            } else if (visiblePlaces.isEmpty()) {
                Text("Nessun luogo corrisponde ai filtri attivi.", color = TPColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
            } else {
                visiblePlaces.forEach { place ->
                    PlaceCard(repository, place, categories, onOpenPlace, onChangeCategory)
                }
            }
            Spacer(Modifier.height(90.dp))
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp)
                .clip(CircleShape).background(TPColors.Accent)
                .combinedClickable(onClick = onCreateCategory, onLongClick = onManageCategories),
            contentAlignment = Alignment.Center
        ) {
            Text("＋", color = Color.White, fontSize = 24.sp)
        }
    }
}

@Composable
private fun ListHeader(
    repository: TravelPinsRepository,
    listPlaces: List<Place>,
    listName: String?,
    onBack: () -> Unit,
    onManageCategories: () -> Unit
) {
    val candidates = remember(listPlaces) { listPlaces.take(6).map { it.id } }
    val url = rememberListCover(repository, candidates, 1200)
    var menuOpen by remember { mutableStateOf(false) }
    val displayName = listName?.takeIf { it.isNotBlank() } ?: "Elenco senza titolo"

    Box(Modifier.fillMaxWidth().height(260.dp).clip(ListCurvedShape())) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TPColors.SurfaceAlt, TPColors.Bg))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.7f)))))

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White) }, onClick = onBack)
            Box {
                CircleIconButton(icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "Menu", tint = Color.White) }, onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Gestisci categorie") }, onClick = { menuOpen = false; onManageCategories() })
                }
            }
        }

        Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(TPColors.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(displayName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${listPlaces.size} luoghi", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ControlsRow(
    query: String,
    onQuery: (String) -> Unit,
    filterId: Long?,
    onFilter: (Long?) -> Unit,
    categories: List<Category>,
    onOpenMap: () -> Unit
) {
    var filterOpen by remember { mutableStateOf(false) }
    val filterActive = filterId != null

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(TPColors.Surface)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = TPColors.TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.fillMaxWidth()) {
                if (query.isEmpty()) Text("Cerca", color = TPColors.TextMuted, fontSize = 14.sp)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = TPColors.TextPrimary, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(24.dp))
                        .background(if (filterActive) TPColors.Accent else TPColors.Surface)
                        .clickable { filterOpen = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FilterAlt, contentDescription = null, tint = if (filterActive) Color.White else TPColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Filtri", color = if (filterActive) Color.White else TPColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                    DropdownMenuItem(text = { Text("Tutti") }, onClick = { onFilter(null); filterOpen = false })
                    DropdownMenuItem(text = { Text("Senza categoria") }, onClick = { onFilter(-1L); filterOpen = false })
                    categories.forEach { category ->
                        DropdownMenuItem(text = { Text("${category.iconKey}  ${category.name}") }, onClick = { onFilter(category.id); filterOpen = false })
                    }
                }
            }

            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                    .background(TPColors.Accent)
                    .clickable { onOpenMap() }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("VEDI SU MAPPA", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceCard(
    repository: TravelPinsRepository,
    place: Place,
    categories: List<Category>,
    onOpenPlace: (Long) -> Unit,
    onChangeCategory: (Place) -> Unit
) {
    val photos by repository.observePhotosByPlace(place.id).collectAsState(initial = emptyList())
    val photoUrl = photos.firstOrNull()?.sizedUrl(400, 400)
    val category = categories.firstOrNull { it.id == place.categoryId }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(TPColors.Surface)
            .combinedClickable(onClick = { onOpenPlace(place.id) }, onLongClick = { onChangeCategory(place) })
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(90.dp).clip(RoundedCornerShape(14.dp)).background(TPColors.SurfaceAlt), contentAlignment = Alignment.Center) {
            if (photoUrl != null) {
                AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("📍", fontSize = 26.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, color = TPColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            if (category != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color(category.colorArgb)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${category.iconKey}  ${category.name}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("Senza categoria", color = TPColors.TextMuted, fontSize = 11.sp)
            }
        }
        Text("›", color = TPColors.Accent, fontSize = 22.sp)
    }
}
