package com.travelpins.test

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.travelpins.test.data.Category
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.EnrichmentManager
import com.travelpins.test.importer.TravelPinsJsBridge
import com.travelpins.test.scraper.GoogleMapsScraperScript
import com.travelpins.test.ui.TravelPinsDarkTheme
import com.travelpins.test.ui.TravelPinsHomeShell
import com.travelpins.test.ui.TravelPinsListDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    companion object {
        private val COLOR_BG = Color.parseColor("#12121A")
        private val COLOR_SURFACE = Color.parseColor("#1C1C28")
        private val COLOR_SURFACE_ALT = Color.parseColor("#242432")
        private val COLOR_ACCENT = Color.parseColor("#2EBD95")
        private val COLOR_ACCENT_DARK = Color.parseColor("#249B7B")
        private val COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF")
        private val COLOR_TEXT_SECONDARY = Color.parseColor("#9A9AB0")
        private val COLOR_TEXT_MUTED = Color.parseColor("#6E6E85")
        private val COLOR_NAV_BG = Color.parseColor("#1A1A24")
        private val COLOR_FAVORITE = Color.parseColor("#FF6B6B")
    }

    private enum class Screen { HOME, LIST_DETAIL, LIST_MAP }
    private enum class NavTab { HOME, ELENCHI, MAPPA, PROFILO }

    private lateinit var webView: WebView
    private lateinit var outputView: TextView
    private lateinit var repository: TravelPinsRepository
    private var mapView: MapView? = null
    private var googleMap: GoogleMap? = null
    private var currentScreen: Screen = Screen.HOME
    private var currentNavTab: NavTab = NavTab.HOME
    private var viewingListId: String? = null
    private var viewingListName: String? = null
    private var currentListId: String? = null
    private var currentListName: String? = null
    private var consentAttempted = false
    private var scanStarted = false
    private var importStarted = false
    private var currentPlaces: List<Place> = emptyList()
    private var currentCategories: List<Category> = emptyList()
    private var selectedCategoryId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TravelPinsRepository(applicationContext)
        createWebView()
        EnrichmentManager.attach(this)
        EnrichmentManager.start(applicationContext, repository)
        showAppShell(NavTab.HOME)
        handleIntent(intent)
        observeData()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consentAttempted = false
        scanStarted = false
        importStarted = false
        currentListId = null
        currentListName = null
        handleIntent(intent)
    }

    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        when (currentScreen) {
            Screen.LIST_MAP -> showListDetail(viewingListId, viewingListName)
            Screen.LIST_DETAIL -> showAppShell(NavTab.HOME)
            else -> super.onBackPressed()
        }
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }

    override fun onDestroy() {
        if (::webView.isInitialized) { webView.stopLoading(); webView.destroy() }
        mapView?.onDestroy(); mapView = null; googleMap = null
        super.onDestroy()
    }

    private fun showAppShell(tab: NavTab) {
        currentScreen = Screen.HOME; currentNavTab = tab
        viewingListId = null; viewingListName = null; selectedCategoryId = null
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TravelPinsDarkTheme {
                    TravelPinsHomeShell(
                        repository = repository,
                        onOpenList = { listId, listName -> showListDetail(listId, listName) },
                        onImport = { showImporter() },
                        onOpenGoogleLists = { openGoogleMapsLists() },
                        onShowDebugLog = { showDebugLogDialog() }
                    )
                }
            }
        }
        setContentView(composeView)
    }

    private fun openGoogleMapsLists() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/saved"))) }
        catch (e: Exception) { Toast.makeText(this, "Impossibile aprire Google Maps.", Toast.LENGTH_SHORT).show() }
    }

    private fun refreshContent() {
        if (!::repository.isInitialized) return
        val content = findViewById<View>(android.R.id.content) ?: return
        when (currentScreen) {
            Screen.HOME -> { }
            Screen.LIST_DETAIL -> { }
            Screen.LIST_MAP -> { refreshFilters(content); updateMapMarkers(placesInCurrentList()) }
        }
    }

    private fun showListDetail(listId: String?, listName: String?) {
        currentScreen = Screen.LIST_DETAIL
        viewingListId = listId; viewingListName = listName; selectedCategoryId = null
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TravelPinsDarkTheme {
                    TravelPinsListDetailScreen(
                        repository = repository, listId = listId, listName = listName,
                        onBack = { showAppShell(NavTab.HOME) },
                        onOpenMap = { filter -> selectedCategoryId = filter; showListMap(viewingListId, viewingListName) },
                        onOpenPlace = { placeId -> startActivity(com.travelpins.test.ui.PlaceDetailActivity.newIntent(this@MainActivity, placeId)) },
                        onChangeCategory = { place -> showCategoryPicker(place) },
                        onCreateCategory = { showCreateCategoryDialog() },
                        onManageCategories = { showCategoriesDialog() }
                    )
                }
            }
        }
        setContentView(composeView)
    }

    private fun showListMap(listId: String?, listName: String?) {
        currentScreen = Screen.LIST_MAP
        viewingListId = listId; viewingListName = listName
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(COLOR_BG); setPadding(20, 28, 20, 20) }
        val backButton = TextView(this).apply { text = "←"; textSize = 18f; setTextColor(COLOR_TEXT_PRIMARY); gravity = Gravity.CENTER; background = roundedBackground(COLOR_SURFACE, 20f); setOnClickListener { showListDetail(listId, listName) } }
        root.addView(backButton, LinearLayout.LayoutParams(dp(40), dp(40)).apply { bottomMargin = 14 })
        val title = TextView(this).apply { text = listName?.takeIf { it.isNotBlank() } ?: "Elenco senza titolo"; textSize = 22f; setTextColor(COLOR_TEXT_PRIMARY); setPadding(0, 0, 0, 14) }
        root.addView(title)
        val filterScroll = ScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; tag = "filter_container" }
        filterScroll.addView(filterContainer)
        root.addView(filterScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48).apply { bottomMargin = 14 })
        val mapContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundedBackground(COLOR_SURFACE, 18f); clipChildren = true }
        prepareMapView()
        mapView?.let { mapContainer.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        root.addView(mapContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root); refreshContent()
    }

    private fun placesInCurrentList(): List<Place> = currentPlaces.filter { it.sourceListId == viewingListId }

    private fun prepareMapView() {
        if (mapView == null) {
            mapView = MapView(this); mapView?.onCreate(null); MapsInitializer.initialize(this)
            mapView?.getMapAsync { map -> googleMap = map; map.uiSettings.apply { isZoomControlsEnabled = true; isZoomGesturesEnabled = true; isScrollGesturesEnabled = true; isRotateGesturesEnabled = true; isTiltGesturesEnabled = true; isMapToolbarEnabled = true; isCompassEnabled = true }; map.setOnMapLoadedCallback { updateMapMarkers() }; updateMapMarkers() }
        } else { (mapView?.parent as? ViewGroup)?.removeView(mapView) }
    }

    private fun updateMapMarkers(scopedPlaces: List<Place> = placesInCurrentList()) {
        if (currentScreen != Screen.LIST_MAP) return
        val map = googleMap ?: return
        mapView?.post {
            map.clear()
            val placesToShow = when {
                selectedCategoryId == null -> scopedPlaces
                selectedCategoryId == -1L -> scopedPlaces.filter { it.categoryId == null }
                else -> scopedPlaces.filter { it.categoryId == selectedCategoryId }
            }
            if (placesToShow.isEmpty()) return@post
            val boundsBuilder = LatLngBounds.Builder()
            placesToShow.forEach { place ->
                val position = LatLng(place.latitude, place.longitude); boundsBuilder.include(position)
                val category = currentCategories.firstOrNull { it.id == place.categoryId }
                val hue = if (category != null) colorToMarkerHue(category.colorArgb) else BitmapDescriptorFactory.HUE_AZURE
                map.addMarker(MarkerOptions().position(position).title(place.name).snippet(buildMarkerSnippet(place)).icon(BitmapDescriptorFactory.defaultMarker(hue)))
            }
            map.setOnMarkerClickListener { false }
            map.setOnInfoWindowClickListener { marker -> findPlaceForMarker(marker)?.let { openInGoogleMaps(it) } }
            mapView?.postDelayed({
                try {
                    if (placesToShow.size == 1) { val p = placesToShow.first(); map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 14f)) }
                    else map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), dp(55)))
                } catch (_: Exception) { }
            }, 150)
        }
    }

    private fun buildMarkerSnippet(place: Place): String {
        val category = currentCategories.firstOrNull { it.id == place.categoryId }
        return if (category != null) if (!place.address.isNullOrBlank()) "${category.iconKey} ${category.name}\n${place.address}" else "${category.iconKey} ${category.name}"
        else place.address ?: "Senza indirizzo"
    }

    private fun findPlaceForMarker(marker: Marker): Place? = currentPlaces.firstOrNull { kotlin.math.abs(it.latitude - marker.position.latitude) < 0.000001 && kotlin.math.abs(it.longitude - marker.position.longitude) < 0.000001 && it.name == marker.title }

    private fun colorToMarkerHue(color: Int): Float { val hsv = FloatArray(3); Color.colorToHSV(color, hsv); return hsv[0] }

    private fun observeData() {
        lifecycleScope.launch { repository.places.collect { places -> currentPlaces = places; runOnUiThread { refreshContent() } } }
        lifecycleScope.launch { repository.categories.collect { categories -> currentCategories = categories; runOnUiThread { refreshContent() } } }
    }

    private fun refreshFilters(content: View) {
        val container = content.findViewWithTag<LinearLayout>("filter_container") ?: return
        container.removeAllViews()
        addFilterButton(container, "Tutti", null)
        addFilterButton(container, "Senza categoria", -1L)
        currentCategories.forEach { category -> addFilterButton(container, "${category.iconKey}  ${category.name}", category.id) }
    }

    private fun addFilterButton(container: LinearLayout, text: String, categoryId: Long?) {
        val selected = selectedCategoryId == categoryId
        val button = Button(this).apply { this.text = text; textSize = 12f; setTextColor(if (selected) Color.WHITE else COLOR_TEXT_SECONDARY); background = roundedBackground(if (selected) COLOR_ACCENT else COLOR_SURFACE, 14f); setPadding(16, 0, 16, 0); setOnClickListener { selectedCategoryId = categoryId; refreshContent() } }
        container.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 44).apply { rightMargin = 8 })
    }

    private fun refreshPlaces(content: View, scopedPlaces: List<Place>) {
        val container = content.findViewWithTag<LinearLayout>("places_container") ?: return
        container.removeAllViews()
        val placesToShow = when {
            selectedCategoryId == null -> scopedPlaces
            selectedCategoryId == -1L -> scopedPlaces.filter { it.categoryId == null }
            else -> scopedPlaces.filter { it.categoryId == selectedCategoryId }
        }
        if (placesToShow.isEmpty()) {
            val emptyCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(24, 32, 24, 32); background = roundedBackground(COLOR_SURFACE, 18f) }
            val emptyTitle = TextView(this).apply { text = if (scopedPlaces.isEmpty()) "Nessun luogo in questo elenco" else "Nessun luogo in questa categoria"; textSize = 17f; setTextColor(COLOR_TEXT_PRIMARY); gravity = Gravity.CENTER }
            emptyCard.addView(emptyTitle)
            container.addView(emptyCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        placesToShow.forEach { place -> container.addView(createPlaceView(place), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }) }
    }

    private fun createPlaceView(place: Place): View {
        val category = currentCategories.firstOrNull { it.id == place.categoryId }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16); background = roundedBackground(COLOR_SURFACE, 18f); setOnClickListener { startActivity(com.travelpins.test.ui.PlaceDetailActivity.newIntent(this@MainActivity, place.id)) } }
        val name = TextView(this).apply { text = if (category != null) "${category.iconKey}  ${place.name}" else "📍  ${place.name}"; textSize = 17f; setTextColor(COLOR_TEXT_PRIMARY); setPadding(0, 0, 0, 7) }
        box.addView(name)
        if (category != null) {
            val categoryView = TextView(this).apply { text = category.name; textSize = 12f; setTextColor(category.colorArgb); setPadding(0, 0, 0, 7) }
            box.addView(categoryView)
        } else {
            val uncategorized = TextView(this).apply { text = "Senza categoria"; textSize = 12f; setTextColor(COLOR_TEXT_MUTED); setPadding(0, 0, 0, 7) }
            box.addView(uncategorized)
        }
        if (!place.address.isNullOrBlank()) {
            val address = TextView(this).apply { text = place.address; textSize = 14f; setTextColor(COLOR_TEXT_SECONDARY); setPadding(0, 0, 0, 7) }
            box.addView(address)
        }
        val coordinates = TextView(this).apply { text = "📍 ${place.latitude}, ${place.longitude}"; textSize = 11f; setTextColor(COLOR_TEXT_MUTED) }
        box.addView(coordinates)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, 10, 0, 0) }
        val mapsButton = Button(this).apply { text = "MAPS"; textSize = 11f; setOnClickListener { openInGoogleMaps(place) } }
        val categoryButton = Button(this).apply { text = if (category == null) "CATEGORIZZA" else "CAMBIA CATEGORIA"; textSize = 11f; setOnClickListener { showCategoryPicker(place) } }
        actions.addView(categoryButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 42).apply { rightMargin = 6 })
        actions.addView(mapsButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 42))
        box.addView(actions)
        return box
    }

    private fun showCategoryPicker(place: Place) {
        if (currentCategories.isEmpty()) { Toast.makeText(this, "Prima crea almeno una categoria.", Toast.LENGTH_LONG).show(); showCreateCategoryDialog(); return }
        val items = mutableListOf<String>(); items.add("⚪  Senza categoria")
        currentCategories.forEach { category -> items.add("${category.iconKey}  ${category.name}") }
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TravelPinsTest_DarkDialog)
            .setTitle("Categoria di\n${place.name}")
            .setItems(items.toTypedArray()) { _, which ->
                val categoryId = if (which == 0) null else currentCategories[which - 1].id
                lifecycleScope.launch { repository.assignPlaceToCategory(place.id, categoryId); runOnUiThread { Toast.makeText(this@MainActivity, "Categoria aggiornata", Toast.LENGTH_SHORT).show() } }
            }
            .setNeutralButton("＋ Nuova categoria") { _, _ -> showCreateCategoryDialog() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showCategoriesDialog() {
        val items = mutableListOf<String>()
        currentCategories.forEach { category -> val count = currentPlaces.count { it.categoryId == category.id }; items.add("${category.iconKey}  ${category.name}  ($count)") }
        items.add("＋  Crea nuova categoria")
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TravelPinsTest_DarkDialog)
            .setTitle("Le mie categorie")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == currentCategories.size) showCreateCategoryDialog() else showCategoryOptions(currentCategories[which])
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showCategoryOptions(category: Category) {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TravelPinsTest_DarkDialog)
            .setTitle("${category.iconKey}  ${category.name}")
            .setItems(arrayOf("Filtra per questa categoria", "Elimina categoria")) { _, which ->
                when (which) { 0 -> { selectedCategoryId = category.id; refreshContent() }; 1 -> confirmDeleteCategory(category) }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private val categoryColorPalette = listOf(
        Color.parseColor("#EF4444"), Color.parseColor("#F97316"), Color.parseColor("#F59E0B"),
        Color.parseColor("#EAB308"), Color.parseColor("#84CC16"), Color.parseColor("#22C55E"),
        Color.parseColor("#10B981"), Color.parseColor("#14B8A6"), Color.parseColor("#06B6D4"),
        Color.parseColor("#0EA5E9"), Color.parseColor("#3B82F6"), Color.parseColor("#6366F1"),
        Color.parseColor("#8B5CF6"), Color.parseColor("#A855F7"), Color.parseColor("#D946EF"),
        Color.parseColor("#EC4899"), Color.parseColor("#F43F5E"), Color.parseColor("#64748B"),
        Color.parseColor("#6B7280"), Color.parseColor("#78716C")
    )

    private val categoryIconPalette = listOf("📍", "🍴", "🏨", "🏖️", "🏛️", "🌄", "🎯", "🛍️", "☕", "🍺", "🎭", "")

    private fun showCreateCategoryDialog() {
        var selectedIcon = categoryIconPalette.first()
        var selectedColor = categoryColorPalette.first()
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 10, 30, 0) }
        val previewBadge = TextView(this).apply { text = selectedIcon; textSize = 30f; gravity = Gravity.CENTER; background = roundedBackground(selectedColor, 40f) }
        val previewRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 4, 0, 22) }
        previewRow.addView(previewBadge, LinearLayout.LayoutParams(dp(72), dp(72))); layout.addView(previewRow)
        fun refreshPreview() { previewBadge.text = selectedIcon; previewBadge.background = roundedBackground(selectedColor, 40f) }
        val nameInput = EditText(this).apply { hint = "Nome categoria"; setHintTextColor(COLOR_TEXT_MUTED); setTextColor(COLOR_TEXT_PRIMARY); setSingleLine(true); background = roundedBackground(COLOR_SURFACE_ALT, 12f); setPadding(24, 20, 24, 20) }
        layout.addView(nameInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 })
        val iconTitle = TextView(this).apply { text = "ICONA"; textSize = 12f; setTextColor(COLOR_TEXT_MUTED); setPadding(2, 0, 0, 10) }; layout.addView(iconTitle)
        val iconViews = mutableListOf<Pair<String, TextView>>()
        fun iconBackground(icon: String) = roundedBackground(if (icon == selectedIcon) COLOR_ACCENT else COLOR_SURFACE_ALT, 16f)
        val iconGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        categoryIconPalette.chunked(4).forEach { rowIcons ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowIcons.forEach { icon ->
                val iconView = TextView(this).apply { text = icon; textSize = 26f; gravity = Gravity.CENTER; background = iconBackground(icon); setOnClickListener { selectedIcon = icon; iconViews.forEach { (i, v) -> v.background = iconBackground(i) }; refreshPreview() } }
                iconViews.add(icon to iconView)
                row.addView(iconView, LinearLayout.LayoutParams(dp(60), dp(60)).apply { rightMargin = 10; bottomMargin = 10 })
            }
            iconGrid.addView(row)
        }
        layout.addView(iconGrid)
        val colorTitle = TextView(this).apply { text = "COLORE"; textSize = 12f; setTextColor(COLOR_TEXT_MUTED); setPadding(2, 20, 0, 10) }; layout.addView(colorTitle)
        val colorViews = mutableListOf<Pair<Int, View>>()
        fun colorBackground(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); if (color == selectedColor) setStroke(dp(3), Color.WHITE) }
        val colorGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        categoryColorPalette.chunked(5).forEach { rowColors ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowColors.forEach { color ->
                val colorView = View(this).apply { background = colorBackground(color); setOnClickListener { selectedColor = color; colorViews.forEach { (c, v) -> v.background = colorBackground(c) }; refreshPreview() } }
                colorViews.add(color to colorView)
                row.addView(colorView, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = 12; bottomMargin = 12 })
            }
            colorGrid.addView(row)
        }
        layout.addView(colorGrid); scroll.addView(layout)
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TravelPinsTest_DarkDialog)
            .setTitle("Nuova categoria").setView(scroll)
            .setPositiveButton("CREA") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) { Toast.makeText(this, "Inserisci un nome.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                lifecycleScope.launch { repository.createCategory(name = name, colorArgb = selectedColor, iconKey = selectedIcon); runOnUiThread { Toast.makeText(this@MainActivity, "Categoria creata", Toast.LENGTH_SHORT).show() } }
            }
            .setNegativeButton("ANNULLA", null)
            .show()
    }

    private fun confirmDeleteCategory(category: Category) {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TravelPinsTest_DarkDialog)
            .setTitle("Eliminare categoria?").setMessage("La categoria \"${category.name}\" verrà eliminata.\n\nI luoghi resteranno salvati, ma torneranno senza categoria.")
            .setPositiveButton("ELIMINA") { _, _ -> lifecycleScope.launch { repository.deleteCategory(category); runOnUiThread { if (selectedCategoryId == category.id) selectedCategoryId = null; Toast.makeText(this@MainActivity, "Categoria eliminata", Toast.LENGTH_SHORT).show() } } }
            .setNegativeButton("ANNULLA", null)
            .show()
    }

    private fun openInGoogleMaps(place: Place) {
        val uri = if (!place.mapsUrl.isNullOrBlank()) Uri.parse(place.mapsUrl) else Uri.parse("https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { Toast.makeText(this, "Impossibile aprire Google Maps.", Toast.LENGTH_SHORT).show() }
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius * resources.displayMetrics.density }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showImporter() {
        consentAttempted = false; scanStarted = false; importStarted = false; currentListId = null; currentListName = null
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setBackgroundColor(COLOR_BG); setPadding(28, 80, 28, 40) }
        val title = TextView(this).apply { text = "TRAVELPINS"; textSize = 30f; setTextColor(COLOR_TEXT_PRIMARY); gravity = Gravity.CENTER; setPadding(0, 0, 0, 12) }; root.addView(title)
        val importTitle = TextView(this).apply { text = "Importazione in corso"; textSize = 22f; setTextColor(COLOR_TEXT_PRIMARY); gravity = Gravity.CENTER; setPadding(0, 0, 0, 10) }; root.addView(importTitle)
        val status = TextView(this).apply { tag = "import_status"; text = "Sto leggendo la lista di Google Maps…"; textSize = 15f; setTextColor(COLOR_TEXT_SECONDARY); gravity = Gravity.CENTER; setPadding(0, 0, 0, 24) }; root.addView(status)
        val progress = ProgressBar(this).apply { isIndeterminate = true; tag = "import_progress" }; root.addView(progress, LinearLayout.LayoutParams(60, 60).apply { bottomMargin = 24 })
        val info = TextView(this).apply { text = "Non chiudere TravelPins.\nL'importazione potrebbe richiedere alcuni secondi."; textSize = 14f; setTextColor(COLOR_TEXT_MUTED); gravity = Gravity.CENTER; setPadding(10, 0, 10, 30) }; root.addView(info)
        val cancelButton = Button(this).apply { text = "ANNULLA"; textSize = 13f; setTextColor(COLOR_TEXT_PRIMARY); background = roundedBackground(COLOR_SURFACE, 14f); setOnClickListener { webView.stopLoading(); showAppShell(NavTab.HOME) } }
        root.addView(cancelButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52))
        webView.alpha = 0f; root.addView(webView, LinearLayout.LayoutParams(1, 1))
        outputView = TextView(this).apply { text = "TRAVELPINS NETWORK MONITOR"; visibility = View.GONE }
        root.addView(outputView, LinearLayout.LayoutParams(1, 1)); setContentView(root)
    }

    private fun updateImportStatus(message: String) { runOnUiThread { val content = findViewById<View>(android.R.id.content); val status = content.findViewWithTag<TextView>("import_status"); status?.text = message } }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView = WebView(this).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"; alpha = 0f }
        val bridge = TravelPinsJsBridge(
            repository = repository, scope = lifecycleScope, getCurrentSourceListId = { currentListId }, getCurrentSourceListName = { currentListName },
            onImportFinished = { savedCount ->
                lifecycleScope.launch {
                    val listId = currentListId
                    val first10Places = if (listId != null) {
                        repository.getPlacesByListId(listId).take(10).map { it.id }
                    } else {
                        emptyList()
                    }

                    if (first10Places.isNotEmpty()) {
                        runOnUiThread { updateImportStatus("Importazione completata.\n$savedCount luoghi salvati.\nArricchimento prioritario in corso...") }
                        EnrichmentManager.prioritize(first10Places)
                        
                        var waited = 0L
                        val step = 500L
                        while (waited < 15000L) {
                            val places = repository.getPlacesByListId(listId)
                            val enrichedCount = first10Places.count { id -> 
                                places.firstOrNull { it.id == id }?.detailsFetchedAt != null 
                            }
                            runOnUiThread { 
                                updateImportStatus("Importazione completata.\n$savedCount luoghi salvati.\nArricchiti $enrichedCount/${first10Places.size} luoghi prioritari...") 
                            }
                            if (enrichedCount >= 3) {
                                break
                            }
                            delay(step)
                            waited += step
                        }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "$savedCount luoghi importati", Toast.LENGTH_SHORT).show()
                        webView.stopLoading()
                        webView.postDelayed({ showAppShell(NavTab.HOME) }, 300)
                    }
                }
            },
            onImportError = { error -> runOnUiThread { updateImportStatus("Si è verificato un errore.\n\n${error.message}"); Toast.makeText(this, "Errore durante l'importazione", Toast.LENGTH_LONG).show() } },
            onLogMessage = { message -> if (::outputView.isInitialized) appendOutput(message) }
        )
        
        EnrichmentManager.setLogCallback { message -> if (::outputView.isInitialized) appendOutput(message) }
        
        webView.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME); webView.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url); appendOutput("PAGINA CARICATA: $url")
                view.evaluateJavascript(GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT, null)
                if (url.contains("consent.google.com")) { if (!consentAttempted) { consentAttempted = true; updateImportStatus("Autorizzazione Google in corso…"); view.postDelayed({ acceptGoogleConsent() }, 700) }; return }
                if (GoogleMapsScraperScript.isGoogleListUrl(url)) {
                    currentListId = extractListId(url)
                    currentListName = view.title?.replace(" - Google Maps", "")?.replace("Google Maps", "")?.trim()?.takeIf { it.isNotBlank() }
                    appendOutput("LISTA GOOGLE MAPS RILEVATA\n$url\nTITOLO: $currentListName")
                    updateImportStatus("Lista trovata.\nLettura dei luoghi in corso…")
                    if (currentListId != null && !scanStarted) { scanStarted = true; view.postDelayed({ scanGoogleList() }, 500) }
                }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { appendOutput("WEBVIEW RENDERER TERMINATO\nCRASH: $detail"); updateImportStatus("Google Maps ha interrotto l'importazione."); return true }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean { val url = request.url.toString(); if (url.startsWith("intent://")) { handleGoogleIntent(url); return true }; return false }
        }
    }

    private fun extractListId(url: String): String? {
        Regex("!11m2!2s([^!&]+)", RegexOption.IGNORE_CASE).find(url)?.let { return it.groupValues[1] }
        Regex("""/local/userlists/list/([^?/]+)""", RegexOption.IGNORE_CASE).find(url)?.let { return it.groupValues[1] }
        Regex("2s([A-Za-z0-9_-]{20,})").find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun acceptGoogleConsent() { if (!::webView.isInitialized) return; updateImportStatus("Autorizzazione Google…"); webView.evaluateJavascript(GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT) { result -> appendOutput("CONSENSO RISULTATO\n$result") } }

    private fun scanGoogleList() { if (importStarted) return; importStarted = true; updateImportStatus("Lettura dei luoghi in corso…"); appendOutput("SCANSIONE LISTA AVVIATA\nMetodo: entitylist/getlist"); webView.evaluateJavascript(GoogleMapsScraperScript.GETLIST_SCRIPT) { result -> appendOutput("CALLBACK GETLIST\n$result") } }

    private fun handleGoogleIntent(intentUrl: String) {
        try {
            val marker = "S.browser_fallback_url=";
            val start = intentUrl.indexOf(marker)
            if (start == -1) { updateImportStatus("Impossibile aprire la lista Google Maps."); appendOutput("FALLBACK URL NON TROVATO"); return }
            var value = intentUrl.substring(start + marker.length);
            val end = value.indexOf("#Intent");
            if (end != -1) value = value.substring(0, end)
            val decoded = URLDecoder.decode(value, "UTF-8");
            appendOutput("GOOGLE INTENT INTERCETTATO\nFALLBACK WEB:\n$decoded");
            webView.loadUrl(decoded)
        } catch (e: Exception) { appendOutput("ERRORE PARSING INTENT:\n$e"); updateImportStatus("Errore durante l'apertura della lista.") }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT); if (text.isNullOrBlank()) { Toast.makeText(this, "Nessun testo ricevuto", Toast.LENGTH_SHORT).show(); return }
        val match = Regex("""https?://\S+""").find(text); if (match == null) { Toast.makeText(this, "Nessun URL trovato", Toast.LENGTH_SHORT).show(); return }
        val url = match.value; consentAttempted = false; scanStarted = false; importStarted = false; currentListId = null; currentListName = null
        showImporter(); updateImportStatus("Apertura della lista Google Maps…"); appendOutput("LINK RICEVUTO\n$url"); webView.loadUrl(url)
    }

    private fun appendOutput(section: String) { if (!::outputView.isInitialized) return; outputView.append("\n$section\n") }

    private fun copyOutputToClipboard() { if (!::outputView.isInitialized) return; val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return; clipboard.setPrimaryClip(ClipData.newPlainText("TravelPins", outputView.text)); Toast.makeText(this, "Copiato!", Toast.LENGTH_SHORT).show() }

    private fun showDebugLogDialog() {
        val logText = if (::outputView.isInitialized) outputView.text.toString().takeIf { it.isNotBlank() } ?: "Nessun log disponibile ancora.\nImporta un elenco prima di aprire questo log." else "Nessun log disponibile ancora.\nImporta un elenco prima di aprire questo log."
        val scroll = ScrollView(this); val textView = TextView(this).apply { text = logText; textSize = 11f; setTextColor(COLOR_TEXT_PRIMARY); setPadding(24, 16, 24, 16); setTextIsSelectable(true) }; scroll.addView(textView)
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TravelPinsTest_DarkDialog)
            .setTitle("Log diagnostica").setView(scroll)
            .setPositiveButton("COPIA") { _, _ -> copyOutputToClipboard() }
            .setNegativeButton("CHIUDI", null)
            .show()
    }
}
