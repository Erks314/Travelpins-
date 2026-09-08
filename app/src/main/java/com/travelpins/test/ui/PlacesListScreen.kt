package com.travelpins.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.travelpins.test.data.Category
import com.travelpins.test.data.Place

/**
 * Schermata separata (Activity/Composable a parte) per sfogliare i luoghi
 * salvati e categorizzarli. Non tocca la MainActivity di scraping: puoi
 * lanciarla da un pulsante "Vedi luoghi salvati" o da una seconda Activity
 * (es. PlacesActivity) che usa lo stesso TravelPinsRepository.
 */
@Composable
fun PlacesListScreen(
    places: List<Place>,
    categories: List<Category>,
    onAssignCategory: (placeId: Long, categoryId: Long?) -> Unit,
    onCreateCategory: (name: String, colorArgb: Int, iconKey: String) -> Unit,
    onDeletePlace: (Place) -> Unit
) {
    var placeForCategoryPicker by remember { mutableStateOf<Place?>(null) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreateCategoryDialog = true }) {
                Text("Nuova categoria")
            }
        }
    ) { padding ->
        if (places.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(places, key = { it.id }) { place ->
                    val category = categories.find { it.id == place.categoryId }
                    PlaceRow(
                        place = place,
                        category = category,
                        onChipClick = { placeForCategoryPicker = place },
                        onDelete = { onDeletePlace(place) }
                    )
                }
            }
        }
    }

    placeForCategoryPicker?.let { place ->
        CategoryPickerDialog(
            categories = categories,
            onPick = { categoryId ->
                onAssignCategory(place.id, categoryId)
                placeForCategoryPicker = null
            },
            onDismiss = { placeForCategoryPicker = null }
        )
    }

    // NUOVO: dialog full-screen condiviso (stessa UI delle altre entry point)
    if (showCreateCategoryDialog) {
        CreateCategoryFullscreenDialog(
            onCreate = { name, color, icon ->
                onCreateCategory(name, color, icon)
                showCreateCategoryDialog = false
            },
            onDismiss = { showCreateCategoryDialog = false }
        )
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    category: Category?,
    onChipClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                place.address?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            CategoryChip(category = category, onClick = onChipClick)

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Elimina")
            }
        }
    }
}

@Composable
fun CategoryChip(category: Category?, onClick: () -> Unit) {
    val color = category?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.surfaceVariant
    val label = category?.name ?: "Non categorizzato"

    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            if (category != null) {
                CategoryIcon(
                    iconKey = category.iconKey,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                    emojiFontSize = 11.sp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    )
}

@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assegna categoria") },
        text = {
            Column {
                TextButton(onClick = { onPick(null) }) { Text("Nessuna categoria") }
                categories.forEach { category ->
                    TextButton(onClick = { onPick(category.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(
                                iconKey = category.iconKey,
                                tint = Color(category.colorArgb),
                                modifier = Modifier.size(16.dp),
                                emojiFontSize = 13.sp
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                            Text(category.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss = onDismiss, onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nessun luogo importato", style = MaterialTheme.typography.titleMedium)
            Text("Condividi una lista da Google Maps per iniziare", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
