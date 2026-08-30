package com.travelpins.test.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.travelpins.test.R
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlin.math.abs

private enum class HomeTab { HOME, ELENCHI, MAPPA, PROFILO }

private data class ListGroup(
    val listId: String?,
    val listName: String?,
    val places: List<Place>
) {
    val displayName: String get() = listName?.takeIf { it.isNotBlank() } ?: "Elenco senza titolo"
}

private fun groupLists(places: List<Place>): List<ListGroup> =
    places.groupBy { it.sourceListId to it.sourceListName }
        .map { (key, pls) -> ListGroup(key.first, key.second, pls) }
        .sortedByDescending { it.places.maxOf { p -> p.importedAt } }

private val badgePalette = listOf(
    "📍" to Color(0xFF2EBD95),
    "⭐" to Color(0xFF8B5CF6),
    "🏛️" to Color(0xFF3B82F6),
    "⛩️" to Color(0xFFEF4444),
    "🏖️" to Color(0xFFF59E0B),
    "🗺️" to Color(0xFF14B8A6),
    "🎭" to Color(0xFFEC4899),
    "🏔️" to Color(0xFF64748B)
)

private fun badgeAt(index: Int): Pair<String, Color> = badgePalette[index % badgePalette.size]

private class CurvedBottomShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val curve = 28f * density.density
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
private fun rememberCoverUrl(repository: TravelPinsRepository, placeIds: List<Long>, width: Int, manualUrl: String? = null): String? {
    var url by remember(placeIds, manualUrl) { mutableStateOf<String?>(null) }
    
    LaunchedEffect(placeIds, manualUrl) {
        if (manualUrl != null) {
            url = manualUrl + "=w" + width + "-k-no"
            return@LaunchedEffect
        }
        
        if (placeIds.isEmpty()) {
            url = null
            return@LaunchedEffect
        }
        
        // MODIFICA CHIAVE: Usa merge invece di combine per reagire immediatamente
        // quando QUALSIASI luogo ottiene una foto
        val photoFlows = placeIds.map { id -> 
            repository.observePhotosByPlace(id).mapNotNull { photos ->
                if (photos.isNotEmpty()) photos.first().sizedUrl(width) else null
            }
        }
        
        // merge emette valori da tutti i flussi, reagendo immediatamente
        merge(*photoFlows.toTypedArray())
            .filterNotNull()
            .distinctUntilChanged()
            .collect { photoUrl ->
                url = photoUrl
            }
    }
    return url
}

@Composable
fun TravelPinsHomeShell(
    repository: TravelPinsRepository,
    onOpenList: (String?, String?) -> Unit,
    onImport: () -> Unit,
    onOpenGoogleLists: () -> Unit,
    onShowDebugLog: () -> Unit
) {
    var currentTab by remember { mutableStateOf(HomeTab.HOME) }

    Column(Modifier.fillMaxSize().background(TPColors.Bg)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (currentTab) {
                HomeTab.HOME -> HomeTabContent(
                    repository = repository,
                    onOpenList = onOpenList,
                    onSeeAll = { currentTab = HomeTab.ELENCHI },
                    onImport = onImport
                )
                HomeTab.ELENCHI -> ElenchiTabContent(
                    repository = repository,
                    onOpenList = onOpenList,
                    onImport = onImport
                )
                HomeTab.MAPPA -> PlaceholderTab("🗺️", "Mappa generale", "Presto disponibile: tutti i tuoi luoghi su un'unica mappa.")
                HomeTab.PROFILO -> ProfiloTabContent(onShowDebugLog)
            }
        }
        BottomNav(currentTab) { currentTab = it }
    }
}

@Composable
private fun BottomNav(current: HomeTab, onSelect: (HomeTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1A1A24)).height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(Icons.Filled.Home, "Home", HomeTab.HOME, current, onSelect, Modifier.weight(1f))
        NavItem(Icons.Filled.List, "Elenchi", HomeTab.ELENCHI, current, onSelect, Modifier.weight(1f))
        NavItem(Icons.Filled.Map, "Mappa", HomeTab.MAPPA, current, onSelect, Modifier.weight(1f))
        NavItem(Icons.Filled.Person, "Profilo", HomeTab.PROFILO, current, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    tab: HomeTab,
    current: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = current == tab
    val color = if (selected) TPColors.Accent else TPColors.TextMuted

    Column(
        modifier.clickable { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.width(24.dp).height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) TPColors.Accent else Color.Transparent)
        )
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}

@Composable
private fun HomeTabContent(
    repository: TravelPinsRepository,
    onOpenList: (String?, String?) -> Unit,
    onSeeAll: () -> Unit,
    onImport: () -> Unit
) {
    val places by repository.places.collectAsState(initial = emptyList())
    val groups = remember(places) { groupLists(places) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Hero()

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Layers, contentDescription = null, tint = TPColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("I TUOI ELENCHI", color = TPColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Vedi tutti ›", color = TPColors.Accent, fontSize = 14.sp, modifier = Modifier.clickable { onSeeAll() })
        }

        if (groups.isEmpty()) {
            EmptyState(onImport)
        } else {
            groups.forEachIndexed { index, group -> ListCard(repository, group, index, onOpenList) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Hero() {
    Box(Modifier.fillMaxWidth().height(260.dp).clip(CurvedBottomShape())) {
        Image(
            painter = painterResource(id = R.drawable.home_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ListCard(
    repository: TravelPinsRepository,
    group: ListGroup,
    badgeIndex: Int,
    onOpenList: (String?, String?) -> Unit
) {
    val candidates = remember(group) { group.places.take(6).map { it.id } }
    val manualCover = remember(group.listId) { repository.getListCover(group.listId) }
    val coverUrl = rememberCoverUrl(repository, candidates, 900, manualCover)
    val (emoji, badgeColor) = badgeAt(badgeIndex)

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TPColors.SurfaceAlt)
            .clickable { onOpenList(group.listId, group.listName) }
    ) {
        if (coverUrl != null) {
            AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
            )
        )

        Row(
            Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(group.displayName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${group.places.size} luoghi", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }

        Box(
            Modifier.align(Alignment.CenterEnd).padding(16.dp).size(36.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Text("›", color = Color.White, fontSize = 20.sp)
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(20.dp))
            .background(TPColors.Surface).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🌍", fontSize = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text("Nessun elenco importato", color = TPColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Importa una lista da Google Maps per iniziare.", color = TPColors.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.clip(RoundedCornerShape(14.dp)).background(TPColors.Accent)
                .clickable { onImport() }.padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Text("＋ IMPORTA DA GOOGLE MAPS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Apri un elenco in Google Maps, tocca Condividi e scegli TravelPins.",
            color = TPColors.TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ElenchiTabContent(
    repository: TravelPinsRepository,
    onOpenList: (String?, String?) -> Unit,
    onImport: () -> Unit
) {
    val places by repository.places.collectAsState(initial = emptyList())
    val groups = remember(places) { groupLists(places) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Elenchi", color = TPColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp)).background(TPColors.Surface)
                .clickable { onImport() }.padding(16.dp)
        ) {
            Text("＋ Importa da Google Maps", color = TPColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Apri un elenco in Google Maps, tocca Condividi e scegli TravelPins.", color = TPColors.TextMuted, fontSize = 12.sp)
        }

        if (groups.isEmpty()) {
            EmptyState(onImport)
        } else {
            groups.forEachIndexed { index, group -> ListCard(repository, group, index, onOpenList) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfiloTabContent(onShowDebugLog: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Profilo", color = TPColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TPColors.Surface)
                .clickable { onShowDebugLog() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🐞", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Log diagnostica", color = TPColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Apri il log di rete per il supporto", color = TPColors.TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("TravelPins 1.0", color = TPColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun PlaceholderTab(icon: String, title: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(title, color = TPColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = TPColors.TextSecondary, fontSize = 14.sp)
    }
}
