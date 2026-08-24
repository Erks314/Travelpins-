package com.travelpins.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
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
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
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
                        Text(category.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun CreateCategoryDialog(
    onCreate: (name: String, colorArgb: Int, iconKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val palette = listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFD54F, 0xFFBA68C8).map { it.toInt() }
    var selectedColor by remember { mutableStateOf(palette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova categoria") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (c == selectedColor)
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, selectedColor, "place") },
                enabled = name.isNotBlank()
            ) { Text("Crea") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
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
