package com.travelpins.test.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.travelpins.test.data.Category
import com.travelpins.test.data.Place
import com.travelpins.test.data.PlacePhoto
import com.travelpins.test.data.PlaceReview
import com.travelpins.test.data.TravelPinsRepository
import java.text.NumberFormat
import java.util.Locale

// ============================================================
// COLORI (coerenti con MainActivity)
// ============================================================

object TPColors {
    val Bg = Color(0xFF12121A)
    val Surface = Color(0xFF1C1C28)
    val SurfaceAlt = Color(0xFF242432)
    val Accent = Color(0xFF6C5CE7)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFF9A9AB0)
    val TextMuted = Color(0xFF6E6E85)
    val Star = Color(0xFFFFB300)
}

@Composable
fun TravelPinsDarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TPColors.Accent,
            background = TPColors.Bg,
            surface = TPColors.Surface,
            surfaceVariant = TPColors.SurfaceAlt,
            onBackground = TPColors.TextPrimary,
            onSurface = TPColors.TextPrimary,
            onSurfaceVariant = TPColors.TextSecondary
        ),
        content = content
    )
}

// ============================================================
// HELPERS
// ============================================================

fun PlacePhoto.sizedUrl(width: Int, height: Int? = null): String =
    imageUrl + "=w" + width + (height?.let { "-h$it" } ?: "") + "-k-no"

fun formatRating(rating: Double): String =
    rating.toString().replace('.', ',')

fun formatCount(count: Int): String =
    NumberFormat.getInstance(Locale.ITALY).format(count.toLong())

// ============================================================
// ROOT (navigazione interna: dettaglio / galleria / recensioni / mappa)
// ============================================================

private enum class DetailScreen { Detail, Gallery, Reviews, Map }

@Composable
fun PlaceDetailRoot(
    repository: TravelPinsRepository,
    placeId: Long,
    webViewState: MutableState<WebView?>,
    enrichmentState: PlaceDetailActivity.EnrichmentState,
    onBack: () -> Unit,
    onStartEnrichmentIfNeeded: (Place) -> Unit,
    onForceRefresh: (Place) -> Unit,
    onShare: (Place) -> Unit,
    onOpenGoogleMaps: (Place) -> Unit,
    onDelete: (Place) -> Unit,
    onAssignCategory: (Long, Long?) -> Unit,
    onCreateCategory: (String, Int, String) -> Unit
) {
    val place by repository.observePlaceById(placeId)
        .collectAsState(initial = null)
    val photos by repository.observePhotosByPlace(placeId)
        .collectAsState(initial = emptyList())
    val reviews by repository.observeReviewsByPlace(placeId)
        .collectAsState(initial = emptyList())
    val categories by repository.categories
        .collectAsState(initial = emptyList())

    var screen by remember { mutableStateOf(DetailScreen.Detail) }

    LaunchedEffect(place?.id, place?.detailsFetchedAt) {
        val p = place
        if (p != null && p.detailsFetchedAt == null) {
            onStartEnrichmentIfNeeded(p)
        }
    }

    BackHandler(enabled = screen != DetailScreen.Detail) {
        screen = DetailScreen.Detail
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TPColors.Bg)
    ) {
        when (screen) {
            DetailScreen.Detail -> PlaceDetailScreen(
                place = place,
                photos = photos,
                reviews = reviews,
                categories = categories,
                enrichmentState = enrichmentState,
                onBack = onBack,
                onChangeCategory = { p, categoryId ->
                    onAssignCategory(p.id, categoryId)
                },
                onCreateCategory = onCreateCategory,
                onOpenMapInternal = { screen = DetailScreen.Map },
                onOpenGoogleMaps = onOpenGoogleMaps,
                onShare = onShare,
                onOpenGallery = { screen = DetailScreen.Gallery },
                onOpenReviews = { screen = DetailScreen.Reviews },
                onForceRefresh = onForceRefresh,
                onDelete = onDelete
            )

            DetailScreen.Gallery -> GalleryScreen(
                photos = photos,
                title = place?.name ?: "",
                onBack = { screen = DetailScreen.Detail }
            )

            DetailScreen.Reviews -> ReviewsScreen(
                reviews = reviews,
                title = place?.name ?: "",
                onBack = { screen = DetailScreen.Detail }
            )

            DetailScreen.Map -> {
                val p = place
                if (p != null) {
                    PlaceMapScreen(
                        place = p,
                        category = categories.firstOrNull { it.id == p.categoryId },
                        onBack = { screen = DetailScreen.Detail }
                    )
                }
            }
        }

        webViewState.value?.let { wv ->
            AndroidView(
                factory = { wv },
                modifier = Modifier
                    .size(1.dp)
                    .align(Alignment.BottomStart)
            )
        }
    }
}

// ============================================================
// SCHEDA DETTAGLIO
// ============================================================

@Composable
fun PlaceDetailScreen(
    place: Place?,
    photos: List<PlacePhoto>,
    reviews: List<PlaceReview>,
    categories: List<Category>,
    enrichmentState: PlaceDetailActivity.EnrichmentState,
    onBack: () -> Unit,
    onChangeCategory: (Place, Long?) -> Unit,
    onCreateCategory: (String, Int, String) -> Unit,
    onOpenMapInternal: () -> Unit,
    onOpenGoogleMaps: (Place) -> Unit,
    onShare: (Place) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenReviews: () -> Unit,
    onForceRefresh: (Place) -> Unit,
    onDelete: (Place) -> Unit
) {
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showCreateCategory by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ---------- HERO IMAGE ----------
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            val hero = photos.firstOrNull()
            if (hero != null) {
                AsyncImage(
                    model = hero.sizedUrl(1200),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1f to TPColors.Bg
                            )
                        )
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(TPColors.Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📍", fontSize = 48.sp)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
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

                CircleIconButton(
                    icon = {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                    },
                    onClick = { showMenu = true }
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (place != null) {
                    DropdownMenuItem(
                        text = { Text("Aggiorna dati Google") },
                        onClick = {
                            showMenu = false
                            onForceRefresh(place)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Condividi") },
                        onClick = {
                            showMenu = false
                            onShare(place)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Elimina luogo") },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }

        // ---------- CONTENUTO ----------
        Column(Modifier.padding(horizontal = 20.dp)) {

            if (place == null) {
                Text(
                    "Luogo non trovato",
                    color = TPColors.TextSecondary
                )
                return@Column
            }

            val category = categories.firstOrNull { it.id == place.categoryId }

            // Nome + Cambia categoria
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    place.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TPColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { showCategoryPicker = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TPColors.Accent
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "CAMBIA CATEGORIA",
                        fontSize = 11.sp
                    )
                }
            }

            // Categoria + tipi
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (category != null) {
                    Text(
                        "${category.iconKey}  ${category.name}",
                        color = Color(category.colorArgb),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        "📍  Senza categoria",
                        color = TPColors.TextMuted,
                        fontSize = 14.sp
                    )
                }

                val types = place.types
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                if (types.isNotEmpty()) {
                    Text(
                        "  •  ${types.take(2).joinToString(" • ")}",
                        color = TPColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            // Indirizzo
            if (!place.address.isNullOrBlank()) {
                Text(
                    "📍  ${place.address}",
                    color = TPColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // Rating
            if (place.rating != null) {
                Row(
                    Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = TPColors.Star,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatRating(place.rating!!),
                        color = TPColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (place.reviewCount != null) {
                        Text(
                            "  (${formatCount(place.reviewCount!!)} recensioni)",
                            color = TPColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Descrizione
            if (!place.description.isNullOrBlank()) {
                Text(
                    place.description!!,
                    color = TPColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // Stato arricchimento
            if (enrichmentState == PlaceDetailActivity.EnrichmentState.Loading &&
                photos.isEmpty()
            ) {
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = TPColors.Accent,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Caricamento dettagli da Google Maps…",
                        color = TPColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // ---------- AZIONI ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionCard(
                    icon = {
                        Icon(
                            Icons.Filled.Map,
                            contentDescription = null,
                            tint = TPColors.Accent,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = "VISUALIZZA\nSU MAPPA",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenMapInternal
                )
                ActionCard(
                    icon = {
                        Icon(
                            Icons.Filled.NearMe,
                            contentDescription = null,
                            tint = TPColors.Accent,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = "VISUALIZZA IN\nGOOGLE MAPS",
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenGoogleMaps(place) }
                )
                ActionCard(
                    icon = {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = TPColors.Accent,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = "CONDIVIDI",
                    modifier = Modifier.weight(1f),
                    onClick = { onShare(place) }
                )
            }

            // ---------- GALLERIA ----------
            if (photos.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Galleria",
                        color = TPColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Vedi tutte (${photos.size})",
                        color = TPColors.Accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { onOpenGallery() }
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val visible = photos.take(4)
                    visible.forEachIndexed { index, photo ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TPColors.SurfaceAlt)
                                .clickable { onOpenGallery() }
                        ) {
                            AsyncImage(
                                model = photo.sizedUrl(300, 300),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (index == 3 && photos.size > 4) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "+${photos.size - 4}",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------- RECENSIONI ----------
            if (reviews.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recensioni",
                        color = TPColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Vedi tutte",
                        color = TPColors.Accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { onOpenReviews() }
                    )
                }

                reviews.take(3).forEach { review ->
                    Spacer(Modifier.height(12.dp))
                    ReviewCard(review)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // ---------- DIALOGS ----------
    if (showCategoryPicker && place != null) {
        CategoryPickerDialog(
            categories = categories,
            onPick = { categoryId ->
                onChangeCategory(place, categoryId)
                showCategoryPicker = false
            },
            onCreateNew = {
                showCategoryPicker = false
                showCreateCategory = true
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showCreateCategory) {
        CreateCategoryDialog(
            onCreate = { name, color, icon ->
                onCreateCategory(name, color, icon)
                showCreateCategory = false
            },
            onDismiss = { showCreateCategory = false }
        )
    }

    if (showDeleteConfirm && place != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare luogo?") },
            text = {
                Text("\"${place.name}\" verrà eliminato insieme a foto e recensioni salvate.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(place)
                }) { Text("ELIMINA") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("ANNULLA")
                }
            }
        )
    }
}

// ============================================================
// COMPONENTI
// ============================================================

@Composable
fun CircleIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
fun ActionCard(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TPColors.Surface)
            .clickable { onClick() }
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon()
        Text(
            label,
            color = TPColors.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp
        )
    }
}

@Composable
fun StarsRow(rating: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (i <= rating) TPColors.Star else TPColors.TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun ReviewCard(review: PlaceReview) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TPColors.Surface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (review.authorPhotoUrl != null) {
                AsyncImage(
                    model = review.authorPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TPColors.SurfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (review.authorName?.firstOrNull() ?: '?').toString(),
                        color = TPColors.TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Text(
                review.authorName ?: "Utente Google",
                color = TPColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StarsRow(review.rating ?: 0)
            if (!review.timeText.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    review.timeText!!,
                    color = TPColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            review.reviewText ?: "",
            color = TPColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ============================================================
// DIALOG CATEGORIE (stesso sistema di MainActivity)
// ============================================================

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    onPick: (Long?) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assegna categoria") },
        text = {
            Column {
                TextButton(onClick = { onPick(null) }) {
                    Text("⚪  Senza categoria")
                }
                categories.forEach { category ->
                    TextButton(onClick = { onPick(category.id) }) {
                        Text("${category.iconKey}  ${category.name}")
                    }
                }
                TextButton(onClick = onCreateNew) {
                    Text("＋  Nuova categoria")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

private val categoryColorPalette = listOf(
    0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF84CC16,
    0xFF22C55E, 0xFF10B981, 0xFF14B8A6, 0xFF06B6D4, 0xFF0EA5E9,
    0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFA855F7, 0xFFD946EF,
    0xFFEC4899, 0xFFF43F5E, 0xFF64748B, 0xFF6B7280, 0xFF78716C
).map { it.toInt() }

private val categoryIconPalette = listOf(
    "📍", "", "", "🏖️", "🏛️", "🌄", "🎯", "🛍️", "☕", "", "", "🎭"
)

@Composable
fun CreateCategoryDialog(
    onCreate: (name: String, colorArgb: Int, iconKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(categoryIconPalette.first()) }
    var selectedColor by remember { mutableStateOf(categoryColorPalette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova categoria") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true
                )

                Spacer(Modifier.height(14.dp))
                Text("ICONA", fontSize = 11.sp, color = TPColors.TextMuted)
                Spacer(Modifier.height(6.dp))

                categoryIconPalette.chunked(6).forEach { rowIcons ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        rowIcons.forEach { icon ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (icon == selectedIcon) TPColors.Accent
                                        else TPColors.SurfaceAlt
                                    )
                                    .clickable { selectedIcon = icon },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(icon, fontSize = 20.sp)
                            }
                        }
                    }
                }

                Text("COLORE", fontSize = 11.sp, color = TPColors.TextMuted)
                Spacer(Modifier.height(6.dp))

                categoryColorPalette.chunked(7).forEach { rowColors ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        rowColors.forEach { color ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .then(
                                        if (color == selectedColor)
                                            Modifier.border(2.dp, Color.White, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), selectedColor, selectedIcon) }
            ) { Text("CREA") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ANNULLA") }
        }
    )
}

// ============================================================
// SCHERMATA RECENSIONI (tutte)
// ============================================================

@Composable
fun ReviewsScreen(
    reviews: List<PlaceReview>,
    title: String,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
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
            Text(
                "Recensioni • $title",
                color = TPColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(reviews, key = { it.id }) { review ->
                ReviewCard(review)
            }
        }
    }
}
