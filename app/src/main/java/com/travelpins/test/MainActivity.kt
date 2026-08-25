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
import com.travelpins.test.importer.TravelPinsJsBridge
import com.travelpins.test.scraper.GoogleMapsScraperScript
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    // ============================================================
    // PALETTE — tema scuro stile mockup
    // ============================================================

    companion object {
        private val COLOR_BG = Color.parseColor("#12121A")
        private val COLOR_SURFACE = Color.parseColor("#1C1C28")
        private val COLOR_SURFACE_ALT = Color.parseColor("#242432")
        private val COLOR_ACCENT = Color.parseColor("#6C5CE7")
        private val COLOR_ACCENT_DARK = Color.parseColor("#5847C9")
        private val COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF")
        private val COLOR_TEXT_SECONDARY = Color.parseColor("#9A9AB0")
        private val COLOR_TEXT_MUTED = Color.parseColor("#6E6E85")
        private val COLOR_NAV_BG = Color.parseColor("#1A1A24")
        private val COLOR_FAVORITE = Color.parseColor("#FF6B6B")
    }

    private enum class Screen { HOME, LIST_DETAIL }
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
        if (currentScreen == Screen.LIST_DETAIL) {
            showAppShell(NavTab.HOME)
        } else {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView?.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        mapView?.onDestroy()
        mapView = null
        googleMap = null
        super.onDestroy()
    }

    // ============================================================
    // APP SHELL — contenitore con bottom nav + FAB import
    // ============================================================

    private fun showAppShell(tab: NavTab) {
        currentScreen = Screen.HOME
        currentNavTab = tab
        viewingListId = null
        viewingListName = null
        selectedCategoryId = null

        val root = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
        }

        val contentContainer = FrameLayout(this).apply {
            tag = "content_container"
            setPadding(0, 0, 0, dp(72))
        }

        root.addView(
            contentContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val navBar = buildBottomNav()

        root.addView(
            navBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72),
                Gravity.BOTTOM
            )
        )

        val fab = TextView(this).apply {
            text = "＋"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(COLOR_ACCENT, 28f)
            setOnClickListener { showImporter() }
        }

        root.addView(
            fab,
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(36)
            }
        )

        setContentView(root)
        refreshContent()
    }

    private fun buildBottomNav(): LinearLayout {
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(COLOR_NAV_BG)
            gravity = Gravity.CENTER_VERTICAL
        }

        navBar.addView(buildNavItem("🏠", "Home", NavTab.HOME))
        navBar.addView(buildNavItem("🔖", "Elenchi", NavTab.ELENCHI))
        navBar.addView(buildNavItem("🗺️", "Mappa", NavTab.MAPPA))
        navBar.addView(buildNavItem("👤", "Profilo", NavTab.PROFILO))

        return navBar
    }

    private fun buildNavItem(icon: String, label: String, tab: NavTab): LinearLayout {
        val selected = currentNavTab == tab
        val color = if (selected) COLOR_ACCENT else COLOR_TEXT_MUTED

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener {
                if (currentNavTab != tab) {
                    currentNavTab = tab
                    refreshContent()
                    // Ridisegna la nav per aggiornare l'evidenziazione
                    findViewById<View>(android.R.id.content)?.let { content ->
                        if (content is FrameLayout) {
                            val old = content.getChildAt(1) as? LinearLayout
                            if (old != null) {
                                content.removeViewAt(1)
                                content.addView(
                                    buildBottomNav(),
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        dp(72),
                                        Gravity.BOTTOM
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        val iconView = TextView(this).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, 2, 0, 0)
        }

        item.addView(iconView)
        item.addView(labelView)

        return item
    }

    private fun refreshContent() {
        if (!::repository.isInitialized) return
        val content = findViewById<View>(android.R.id.content) ?: return
        val contentContainer = content.findViewWithTag<FrameLayout>("content_container") ?: return

        when (currentScreen) {
            Screen.HOME -> {
                when (currentNavTab) {
                    NavTab.HOME, NavTab.ELENCHI -> renderListsTab(contentContainer)
                    NavTab.MAPPA -> renderPlaceholderTab(contentContainer, "🗺️", "Mappa generale", "Presto disponibile: tutti i tuoi luoghi su un'unica mappa.")
                    NavTab.PROFILO -> renderPlaceholderTab(contentContainer, "👤", "Profilo", "Presto disponibile: impostazioni e preferenze account.")
                }
            }
            Screen.LIST_DETAIL -> {
                val listPlaces = placesInCurrentList()
                refreshFilters(content)
                refreshPlaces(content, listPlaces)
                updateMapMarkers(listPlaces)
            }
        }
    }

    // ============================================================
    // TAB: HOME / ELENCHI — elenco delle liste importate
    // ============================================================

    private fun renderListsTab(container: FrameLayout) {
        container.removeAllViews()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 12)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "I miei elenchi"
            textSize = 26f
            setTextColor(COLOR_TEXT_PRIMARY)
        }

        headerRow.addView(
            title,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val addButton = TextView(this).apply {
            text = "＋"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(COLOR_ACCENT, 24f)
            setOnClickListener { showImporter() }
        }

        headerRow.addView(addButton, LinearLayout.LayoutParams(dp(44), dp(44)))

        root.addView(
            headerRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 18
            }
        )

        val debugLogButton = TextView(this).apply {
            text = "🐞 Mostra log diagnostica"
            textSize = 12f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(2, 0, 0, 12)
            setOnClickListener { showDebugLogDialog() }
        }

        root.addView(debugLogButton)

        val listsScroll = ScrollView(this)

        val listsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "lists_container"
            setPadding(0, 0, 0, 20)
        }

        listsScroll.addView(listsContainer)

        root.addView(
            listsScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        container.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        refreshListsScreen(root)
    }

    private fun refreshListsScreen(content: View) {
        val container = content.findViewWithTag<LinearLayout>("lists_container") ?: return
        container.removeAllViews()

        if (currentPlaces.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(24, 32, 24, 32)
                background = roundedBackground(COLOR_SURFACE, 18f)
            }

            val emptyTitle = TextView(this).apply {
                text = "Nessun elenco importato"
                textSize = 17f
                setTextColor(COLOR_TEXT_PRIMARY)
                gravity = Gravity.CENTER
            }

            val emptyText = TextView(this).apply {
                text = "Importa una lista da Google Maps per iniziare."
                textSize = 14f
                setTextColor(COLOR_TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 0)
            }

            emptyCard.addView(emptyTitle)
            emptyCard.addView(emptyText)

            container.addView(
                emptyCard,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            return
        }

        val groups = currentPlaces
            .groupBy { it.sourceListId to it.sourceListName }
            .toList()
            .sortedByDescending { (_, places) -> places.maxOf { it.importedAt } }

        groups.forEach { (key, placesInGroup) ->
            val (listId, listName) = key
            val displayName = listName?.takeIf { it.isNotBlank() } ?: "Elenco senza titolo"

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 16, 18, 16)
                background = roundedBackground(COLOR_SURFACE, 18f)
                setOnClickListener { showListDetail(listId, listName) }
            }

            val nameView = TextView(this).apply {
                text = "📁  $displayName"
                textSize = 17f
                setTextColor(COLOR_TEXT_PRIMARY)
                setPadding(0, 0, 0, 6)
            }

            val countView = TextView(this).apply {
                text = "${placesInGroup.size} luoghi"
                textSize = 13f
                setTextColor(COLOR_TEXT_SECONDARY)
            }

            card.addView(nameView)
            card.addView(countView)

            container.addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10 }
            )
        }
    }

    // ============================================================
    // TAB: MAPPA / PROFILO — placeholder
    // ============================================================

    private fun renderPlaceholderTab(container: FrameLayout, icon: String, title: String, message: String) {
        container.removeAllViews()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val iconView = TextView(this).apply {
            text = icon
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(COLOR_TEXT_PRIMARY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val messageView = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.CENTER
        }

        root.addView(iconView)
        root.addView(titleView)
        root.addView(messageView)

        container.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ============================================================
    // DETTAGLIO ELENCO — mappa + luoghi di UN elenco (full-screen, nav nascosta)
    // ============================================================

    private fun showListDetail(listId: String?, listName: String?) {
        currentScreen = Screen.LIST_DETAIL
        viewingListId = listId
        viewingListName = listName
        selectedCategoryId = null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding(20, 28, 20, 20)
        }

        val backButton = TextView(this).apply {
            text = "←"
            textSize = 18f
            setTextColor(COLOR_TEXT_PRIMARY)
            gravity = Gravity.CENTER
            background = roundedBackground(COLOR_SURFACE, 20f)
            setOnClickListener { showAppShell(NavTab.HOME) }
        }

        root.addView(
            backButton,
            LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                bottomMargin = 14
            }
        )

        val title = TextView(this).apply {
            text = listName?.takeIf { it.isNotBlank() } ?: "Elenco senza titolo"
            textSize = 26f
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 0, 0, 18)
        }

        root.addView(title)

        val mapTitle = TextView(this).apply {
            text = "MAPPA"
            textSize = 13f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(2, 0, 0, 7)
        }

        root.addView(mapTitle)

        val mapContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(COLOR_SURFACE, 18f)
            clipChildren = true
        }

        prepareMapView()

        mapView?.let { map ->
            mapContainer.addView(
                map,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
            )
        }

        root.addView(
            mapContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)).apply {
                bottomMargin = 14
            }
        )

        val categoriesButton = Button(this).apply {
            text = "📁  CATEGORIE"
            textSize = 14f
            setTextColor(COLOR_TEXT_PRIMARY)
            background = roundedBackground(COLOR_SURFACE, 16f)
            setOnClickListener { showCategoriesDialog() }
        }

        root.addView(
            categoriesButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52).apply {
                bottomMargin = 14
            }
        )

        val filterScroll = ScrollView(this).apply { isHorizontalScrollBarEnabled = false }

        val filterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "filter_container"
        }

        filterScroll.addView(filterContainer)

        root.addView(
            filterScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48).apply {
                bottomMargin = 10
            }
        )

        val placesTitle = TextView(this).apply {
            text = "LUOGHI"
            textSize = 13f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(2, 0, 0, 8)
        }

        root.addView(placesTitle)

        val placesScroll = ScrollView(this)

        val placesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "places_container"
            setPadding(0, 0, 0, 20)
        }

        placesScroll.addView(placesContainer)

        root.addView(
            placesScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        refreshContent()
    }

    private fun placesInCurrentList(): List<Place> {
        return currentPlaces.filter { it.sourceListId == viewingListId }
    }

    // ============================================================
    // MAP VIEW
    // ============================================================

    private fun prepareMapView() {
        if (mapView == null) {
            mapView = MapView(this)
            mapView?.onCreate(null)
            MapsInitializer.initialize(this)

            mapView?.getMapAsync { map ->
                googleMap = map

                map.uiSettings.apply {
                    isZoomControlsEnabled = true
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isRotateGesturesEnabled = true
                    isTiltGesturesEnabled = true
                    isMapToolbarEnabled = true
                    isCompassEnabled = true
                }

                map.setOnMapLoadedCallback { updateMapMarkers() }
                updateMapMarkers()
            }
        } else {
            val parent = mapView?.parent
            if (parent is ViewGroup) {
                parent.removeView(mapView)
            }
        }
    }

    private fun updateMapMarkers(scopedPlaces: List<Place> = placesInCurrentList()) {
        if (currentScreen != Screen.LIST_DETAIL) return

        val map = googleMap ?: return

        mapView?.post {
            map.clear()

            val placesToShow = when {
                selectedCategoryId == null -> scopedPlaces
                selectedCategoryId == -1L -> scopedPlaces.filter { it.categoryId == null }
                else -> scopedPlaces.filter { it.categoryId == selectedCategoryId }
            }

            if (placesToShow.isEmpty()) {
