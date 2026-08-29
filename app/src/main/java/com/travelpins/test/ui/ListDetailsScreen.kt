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
    onChange
