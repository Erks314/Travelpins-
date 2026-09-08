package com.travelpins.test.ui

import android.view.Window
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.travelpins.test.R

// =====================================================================
// RESOLVER ICONE CATEGORIA
// ---------------------------------------------------------------------
// Il database salva Category.iconKey come STRINGA.
// - Categorie VECCHIE: iconKey è un'emoji -> renderizzata come testo (compatibilità totale).
// - Categorie NUOVE: iconKey è una chiave semantica ("cat_monumenti", ...) -> icona vettoriale Material.
// Nessun migrate sul database, nessuna rottura delle categorie esistenti.
// =====================================================================

data class CategoryIconDef(
    val key: String,
    val label: String,
    val emoji: String,
    val icon: ImageVector
)

object CategoryIcons {
    val ALL: List<CategoryIconDef> = listOf(
        CategoryIconDef("cat_monumenti", "Monumenti", "🏛️", Icons.Filled.Museum),
        CategoryIconDef("cat_ristoranti", "Ristoranti", "🍴", Icons.Filled.Restaurant),
        CategoryIconDef("cat_hotel", "Hotel", "🏨", Icons.Filled.Hotel),
        CategoryIconDef("cat_fotografia", "Fotografia", "📷", Icons.Filled.PhotoCamera),
        CategoryIconDef("cat_montagna", "Montagna", "🏔️", Icons.Filled.Terrain),
        CategoryIconDef("cat_spiagge", "Spiagge", "🏖️", Icons.Filled.BeachAccess),
        CategoryIconDef("cat_mare", "Mare", "🌊", Icons.Filled.Waves),
        CategoryIconDef("cat_nightlife", "Nightlife", "🍸", Icons.Filled.LocalBar),
        CategoryIconDef("cat_shopping", "Shopping", "🛍️", Icons.Filled.ShoppingBag),
        CategoryIconDef("cat_preferiti", "Preferiti", "⭐", Icons.Filled.Star),
        CategoryIconDef("cat_parchi", "Parchi", "🌳", Icons.Filled.Park),
        CategoryIconDef("cat_chiese", "Chiese", "⛪", Icons.Filled.Church),
        CategoryIconDef("cat_escursioni", "Escursioni", "🥾", Icons.Filled.Hiking),
        CategoryIconDef("cat_animali", "Animali", "🐾", Icons.Filled.Pets),
        CategoryIconDef("cat_cultura", "Cultura", "🎭", Icons.Filled.Theaters),
        CategoryIconDef("cat_panorami", "Panorami", "🌄", Icons.Filled.Landscape)
    )

    fun defFor(key: String): CategoryIconDef? = ALL.firstOrNull { it.key == key }

    fun isVectorKey(key: String): Boolean = defFor(key) != null

    /** Testo sicuro per i contesti solo-testo (snippet marker Google Maps, dialog Views). */
    fun textFor(key: String): String = defFor(key)?.emoji ?: key
}

/** Palette colori canonica (stessi 20 valori ARGB già usati/persistiti dall'app). */
val CATEGORY_COLORS: List<Int> = listOf(
    0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF84CC16,
    0xFF22C55E, 0xFF10B981, 0xFF14B8A6, 0xFF06B6D4, 0xFF0EA5E9,
    0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFA855F7, 0xFFD946EF,
    0xFFEC4899, 0xFFF43F5E, 0xFF64748B, 0xFF6B7280, 0xFF78716C
).map { it.toInt() }

/** Renderizza un'icona categoria: vettoriale se la chiave è mappata, emoji/testo altrimenti. */
@Composable
fun CategoryIcon(
    iconKey: String,
    tint: Color,
    modifier: Modifier = Modifier,
    emojiFontSize: TextUnit = 16.sp
) {
    val def = CategoryIcons.defFor(iconKey)
    if (def != null) {
        Icon(imageVector = def.icon, contentDescription = def.label, tint = tint, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(iconKey, color = tint, fontSize = emojiFontSize)
        }
    }
}

private class CategoryHeroCurve : Shape {
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
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TPColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = TPColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

// =====================================================================
// SCHERMATA "CREA NUOVA CATEGORIA" (nuovo design)
// =====================================================================

@Composable
fun CreateCategoryContent(
    onCreate: (name: String, colorArgb: Int, iconKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(CategoryIcons.ALL.first().key) }
    var selectedColor by remember { mutableStateOf(CATEGORY_COLORS.first()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(TPColors.Bg)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        // ---------------- HERO FOTOGRAFICO ----------------
        // FIX: home_hero contiene il branding TravelPins stampato nell'immagine.
        // Zoomiamo 2x ancorati in basso (TransformOrigin 0.5/0.95) per mostrare SOLO la
        // fascia pulita (acqua/bosco) ed escludere logo, wordmark e tagline.
        // Se vuoi ritoccare il ritaglio: modifica scaleX/scaleY e TransformOrigin.
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            Box(Modifier.fillMaxSize().clip(CategoryHeroCurve())) {
                AsyncImage(
                    model = R.drawable.home_hero,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 2f
                            scaleY = 2f
                            transformOrigin = TransformOrigin(0.5f, 0.95f)
                        }
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.55f to Color.Transparent,
                            1f to TPColors.Bg
                        )
                    )
                )
            }

            // Pulsante BACK / ANNULLA
            Box(
                Modifier.statusBarsPadding().padding(16.dp).size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Annulla", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // Logo + titolo + sottotitolo
            Column(
                Modifier.align(Alignment.Center).padding(top = 56.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Box(
                        Modifier.size(88.dp).clip(CircleShape).background(TPColors.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(TPColors.Accent)
                            .border(2.dp, TPColors.Bg, CircleShape)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = TPColors.Bg, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White)) { append("Crea nuova ") }
                        withStyle(SpanStyle(color = TPColors.Accent)) { append("categoria") }
                    },
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Organizza i tuoi luoghi, rendi unico il tuo viaggio.",
                    color = TPColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // ---------------- NOME CATEGORIA + PREVIEW ----------------
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TPColors.Surface)
                .padding(18.dp)
        ) {
            Text("NOME CATEGORIA", color = TPColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TPColors.SurfaceAlt)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TPColors.TextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(TPColors.Accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (name.isEmpty()) {
                                Text("Es. Musei, Ristoranti, Spiagge...", color = TPColors.TextMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Edit, contentDescription = null, tint = TPColors.Accent, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(12.dp))

            // Preview dinamica [icona] nome
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TPColors.Bg)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(selectedColor)),
                    contentAlignment = Alignment.Center
                ) {
                    CategoryIcon(selectedIcon, tint = Color.White, modifier = Modifier.size(18.dp), emojiFontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    name.ifBlank { "Anteprima categoria" },
                    color = if (name.isBlank()) TPColors.TextMuted else TPColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        // ---------------- SCEGLI UN'ICONA ----------------
        SectionHeader(icon = Icons.Filled.Layers, title = "SCEGLI UN'ICONA")
        Column(Modifier.padding(horizontal = 20.dp)) {
            CategoryIcons.ALL.chunked(5).forEach { rowDefs ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowDefs.forEach { def ->
                        val selected = def.key == selectedIcon
                        val scale by animateFloatAsState(if (selected) 1.06f else 1f)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) TPColors.Accent.copy(alpha = 0.18f) else TPColors.SurfaceAlt)
                                .then(if (selected) Modifier.border(2.dp, TPColors.Accent, RoundedCornerShape(16.dp)) else Modifier)
                                .clickable { selectedIcon = def.key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                def.icon,
                                contentDescription = def.label,
                                tint = if (selected) Color.White else TPColors.Accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    repeat(5 - rowDefs.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // ---------------- SCEGLI UN COLORE ----------------
        SectionHeader(icon = Icons.Filled.Palette, title = "SCEGLI UN COLORE")
        Column(Modifier.padding(horizontal = 20.dp)) {
            CATEGORY_COLORS.chunked(5).forEach { rowColors ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowColors.forEach { c ->
                        val selected = c == selectedColor
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(if (selected) 46.dp else 40.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { selectedColor = c }
                            )
                        }
                    }
                    repeat(5 - rowColors.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // ---------------- CTA CREA ----------------
        Spacer(Modifier.height(26.dp))
        val enabled = name.isNotBlank()
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.horizontalGradient(listOf(TPColors.Accent, Color(0xFF3AD6AE))))
                .clickable(enabled = enabled) { onCreate(name.trim(), selectedColor, selectedIcon) }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CREA CATEGORIA", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // ---------------- ANNULLA DISCRETO ----------------
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 28.dp)
                .clickable { onDismiss() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Annulla", color = TPColors.TextMuted, fontSize = 14.sp)
        }
    }
}

/** Wrapper dialog full-screen (Opzione A approvata): resta un modal, nessuna nuova Activity. */
@Composable
fun CreateCategoryFullscreenDialog(
    onCreate: (name: String, colorArgb: Int, iconKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { dialogWindow: Window ->
            SideEffect {
                dialogWindow.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }
        CreateCategoryContent(onCreate = onCreate, onDismiss = onDismiss)
    }
}
