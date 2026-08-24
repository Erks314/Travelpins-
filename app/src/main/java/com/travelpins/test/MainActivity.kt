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

    private lateinit var webView: WebView
    private lateinit var outputView: TextView
    private lateinit var repository: TravelPinsRepository

    // ============================================================
    // GOOGLE MAP
    // ============================================================

    private var mapView: MapView? = null
    private var googleMap: GoogleMap? = null

    // ============================================================
    // IMPORT
    // ============================================================

    private var currentListId: String? = null

    private var consentAttempted = false
    private var scanStarted = false
    private var importStarted = false

    // ============================================================
    // DATA
    // ============================================================

    private var currentPlaces: List<Place> = emptyList()
    private var currentCategories: List<Category> = emptyList()

    private var selectedCategoryId: Long? = null

    // ============================================================
    // ACTIVITY
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = TravelPinsRepository(applicationContext)

        createWebView()

        showHome()

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

        handleIntent(intent)
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
    // HOME
    // ============================================================

    private fun showHome() {

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setBackgroundColor(
                Color.rgb(248, 249, 250)
            )

            setPadding(
                20,
                28,
                20,
                20
            )
        }

        // --------------------------------------------------------
        // HEADER
        // --------------------------------------------------------

        val title = TextView(this).apply {

            text = "TRAVELPINS"

            textSize = 30f

            setTextColor(
                Color.rgb(25, 25, 25)
            )

            setPadding(
                0,
                0,
                0,
                2
            )
        }

        val subtitle = TextView(this).apply {

            text = "I miei luoghi"

            textSize = 18f

            setTextColor(
                Color.rgb(90, 90, 90)
            )

            setPadding(
                0,
                0,
                0,
                18
            )
        }

        root.addView(title)
        root.addView(subtitle)

        // --------------------------------------------------------
        // CONTATORE
        // --------------------------------------------------------

        val countCard = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                20,
                18,
                20,
                18
            )

            background = roundedBackground(
                Color.WHITE,
                18f
            )
        }

        val countTitle = TextView(this).apply {

            text = "LUOGHI SALVATI"

            textSize = 12f

            setTextColor(
                Color.rgb(110, 110, 110)
            )
        }

        val countView = TextView(this).apply {

            tag = "place_count"

            text = "0"

            textSize = 30f

            setTextColor(
                Color.rgb(30, 30, 30)
            )

            setPadding(
                0,
                4,
                0,
                0
            )
        }

        countCard.addView(countTitle)
        countCard.addView(countView)

        root.addView(
            countCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        )

        // --------------------------------------------------------
        // MAPPA
        // --------------------------------------------------------

        val mapTitle = TextView(this).apply {

            text = "MAPPA"

            textSize = 13f

            setTextColor(
                Color.rgb(100, 100, 100)
            )

            setPadding(
                2,
                0,
                0,
                7
            )
        }

        root.addView(mapTitle)

        val mapContainer = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            background = roundedBackground(
                Color.WHITE,
                18f
            )

            clipChildren = true
        }

        prepareMapView()

        mapView?.let { map ->

            mapContainer.addView(
                map,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(280)
                )
            )
        }

        root.addView(
            mapContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(280)
            ).apply {
                bottomMargin = 14
            }
        )

        // --------------------------------------------------------
        // IMPORTA
        // --------------------------------------------------------

        val importButton = Button(this).apply {

            text = "＋  IMPORTA DA GOOGLE MAPS"

            textSize = 15f

            setTextColor(Color.WHITE)

            background = roundedBackground(
                Color.rgb(45, 105, 225),
                16f
            )

            setPadding(
                12,
                8,
                12,
                8
            )

            setOnClickListener {
                showImporter()
            }
        }

        root.addView(
            importButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                58
            ).apply {
                bottomMargin = 10
            }
        )

        // --------------------------------------------------------
        // CATEGORIE
        // --------------------------------------------------------

        val categoriesButton = Button(this).apply {

            text = "📁  CATEGORIE"

            textSize = 14f

            setTextColor(
                Color.rgb(45, 45, 45)
            )

            background = roundedBackground(
                Color.WHITE,
                16f
            )

            setOnClickListener {
                showCategoriesDialog()
            }
        }

        root.addView(
            categoriesButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                52
            ).apply {
                bottomMargin = 14
            }
        )

        // --------------------------------------------------------
        // FILTRI
        // --------------------------------------------------------

        val filterScroll = ScrollView(this).apply {

            isHorizontalScrollBarEnabled = false
        }

        val filterContainer = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            tag = "filter_container"
        }

        filterScroll.addView(filterContainer)

        root.addView(
            filterScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48
            ).apply {
                bottomMargin = 10
            }
        )

        // --------------------------------------------------------
        // TITOLO LUOGHI
        // --------------------------------------------------------

        val placesTitle = TextView(this).apply {

            text = "LUOGHI"

            textSize = 13f

            setTextColor(
                Color.rgb(100, 100, 100)
            )

            setPadding(
                2,
                0,
                0,
                8
            )
        }

        root.addView(placesTitle)

        // --------------------------------------------------------
        // LISTA LUOGHI
        // --------------------------------------------------------

        val placesScroll = ScrollView(this)

        val placesContainer = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            tag = "places_container"

            setPadding(
                0,
                0,
                0,
                20
            )
        }

        placesScroll.addView(placesContainer)

        root.addView(
            placesScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        refreshHome()
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

                // ------------------------------------------------
                // GESTURE
                // ------------------------------------------------

                map.uiSettings.apply {

                    isZoomControlsEnabled = true
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isRotateGesturesEnabled = true
                    isTiltGesturesEnabled = true

                    isMapToolbarEnabled = true
                    isCompassEnabled = true
                }

                // ------------------------------------------------
                // MAPPA PRONTA
                // ------------------------------------------------

                map.setOnMapLoadedCallback {

                    updateMapMarkers()
                }

                updateMapMarkers()
            }

        } else {

            val parent = mapView?.parent

            if (parent is ViewGroup) {
                parent.removeView(mapView)
            }
        }
    }

    // ============================================================
    // MAP MARKERS
    // ============================================================

    private fun updateMapMarkers() {

        val map = googleMap ?: return

        // Evitiamo di aggiornare una mappa non ancora pronta.
        mapView?.post {

            map.clear()

            val placesToShow = when {

                selectedCategoryId == null ->
                    currentPlaces

                selectedCategoryId == -1L ->
                    currentPlaces.filter {
                        it.categoryId == null
                    }

                else ->
                    currentPlaces.filter {
                        it.categoryId == selectedCategoryId
                    }
            }

            if (placesToShow.isEmpty()) {
                return@post
            }

            val boundsBuilder =
                LatLngBounds.Builder()

            placesToShow.forEach { place ->

                val position = LatLng(
                    place.latitude,
                    place.longitude
                )

                boundsBuilder.include(position)

                val category =
                    currentCategories.firstOrNull {
                        it.id == place.categoryId
                    }

                val hue =
                    if (category != null) {
                        colorToMarkerHue(
                            category.colorArgb
                        )
                    } else {
                        BitmapDescriptorFactory.HUE_AZURE
                    }

                map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(place.name)
                        .snippet(
                            buildMarkerSnippet(place)
                        )
                        .icon(
                            BitmapDescriptorFactory
                                .defaultMarker(hue)
                        )
                )
            }

            // ----------------------------------------------------
            // CLICK SUL PIN
            // ----------------------------------------------------

            map.setOnMarkerClickListener { marker ->

                // Restituiamo false:
                // Google Maps apre normalmente la info window.
                false
            }

            // ----------------------------------------------------
            // CLICK SULLA FINESTRA DEL PIN
            // ----------------------------------------------------

            map.setOnInfoWindowClickListener { marker ->

                val place =
                    findPlaceForMarker(marker)

                if (place != null) {
                    openInGoogleMaps(place)
                }
            }

            // ----------------------------------------------------
            // CAMERA
            // ----------------------------------------------------

            mapView?.postDelayed({

                try {

                    if (placesToShow.size == 1) {

                        val place =
                            placesToShow.first()

                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    place.latitude,
                                    place.longitude
                                ),
                                14f
                            )
                        )

                    } else {

                        val bounds =
                            boundsBuilder.build()

                        map.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(
                                bounds,
                                dp(55)
                            )
                        )
                    }

                } catch (_: Exception) {

                    // Se la MapView non è ancora completamente
                    // misurata, lasciamo semplicemente la mappa
                    // alla posizione corrente.
                }

            }, 150)
        }
    }

    // ============================================================
    // MARKER HELPERS
    // ============================================================

    private fun buildMarkerSnippet(
        place: Place
    ): String {

        val category =
            currentCategories.firstOrNull {
                it.id == place.categoryId
            }

        return if (category != null) {

            if (!place.address.isNullOrBlank()) {
                "${category.iconKey} ${category.name}\n${place.address}"
            } else {
                "${category.iconKey} ${category.name}"
            }

        } else {

            place.address ?: "Senza indirizzo"
        }
    }

    private fun findPlaceForMarker(
        marker: Marker
    ): Place? {

        return currentPlaces.firstOrNull { place ->

            val samePosition =
                kotlin.math.abs(
                    place.latitude -
                            marker.position.latitude
                ) < 0.000001 &&
                        kotlin.math.abs(
                            place.longitude -
                                    marker.position.longitude
                        ) < 0.000001

            samePosition &&
                    place.name == marker.title
        }
    }

    // ============================================================
    // MAP COLOR
    // ============================================================

    private fun colorToMarkerHue(
        color: Int
    ): Float {

        val hsv = FloatArray(3)

        Color.colorToHSV(
            color,
            hsv
        )

        return hsv[0]
    }

    // ============================================================
    // DATABASE
    // ============================================================

    private fun observeData() {

        lifecycleScope.launch {

            repository.places.collect { places ->

                currentPlaces = places

                runOnUiThread {
                    refreshHome()
                }
            }
        }

        lifecycleScope.launch {

            repository.categories.collect { categories ->

                currentCategories = categories

                runOnUiThread {
                    refreshHome()
                }
            }
        }
    }

    // ============================================================
    // REFRESH HOME
    // ============================================================

    private fun refreshHome() {

        if (!::repository.isInitialized) {
            return
        }

        val content =
            findViewById<View>(
                android.R.id.content
            ) ?: return

        val countView =
            content.findViewWithTag<TextView>(
                "place_count"
            )

        countView?.text =
            currentPlaces.size.toString()

        refreshFilters(content)

        refreshPlaces(content)

        updateMapMarkers()
    }

    // ============================================================
    // FILTERS
    // ============================================================

    private fun refreshFilters(
        content: View
    ) {

        val container =
            content.findViewWithTag<LinearLayout>(
                "filter_container"
            ) ?: return

        container.removeAllViews()

        addFilterButton(
            container,
            "Tutti",
            null
        )

        addFilterButton(
            container,
            "Senza categoria",
            -1L
        )

        currentCategories.forEach { category ->

            addFilterButton(
                container,
                "${category.iconKey}  ${category.name}",
                category.id
            )
        }
    }

    private fun addFilterButton(
        container: LinearLayout,
        text: String,
        categoryId: Long?
    ) {

        val selected =
            selectedCategoryId == categoryId

        val button = Button(this).apply {

            this.text = text

            textSize = 12f

            setTextColor(
                if (selected) {
                    Color.WHITE
                } else {
                    Color.rgb(
                        55,
                        55,
                        55
                    )
                }
            )

            background =
                roundedBackground(
                    if (selected) {
                        Color.rgb(
                            45,
                            105,
                            225
                        )
                    } else {
                        Color.WHITE
                    },
                    14f
                )

            setPadding(
                16,
                0,
                16,
                0
            )

            setOnClickListener {

                selectedCategoryId =
                    categoryId

                refreshHome()
            }
        }

        container.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                44
            ).apply {
                rightMargin = 8
            }
        )
    }

    // ============================================================
    // PLACES
    // ============================================================

    private fun refreshPlaces(
        content: View
    ) {

        val container =
            content.findViewWithTag<LinearLayout>(
                "places_container"
            ) ?: return

        container.removeAllViews()

        val placesToShow = when {

            selectedCategoryId == null ->
                currentPlaces

            selectedCategoryId == -1L ->
                currentPlaces.filter {
                    it.categoryId == null
                }

            else ->
                currentPlaces.filter {
                    it.categoryId == selectedCategoryId
                }
        }

        if (placesToShow.isEmpty()) {

            val emptyCard =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        24,
                        32,
                        24,
                        32
                    )

                    background =
                        roundedBackground(
                            Color.WHITE,
                            18f
                        )
                }

            val emptyTitle =
                TextView(this).apply {

                    text =
                        if (currentPlaces.isEmpty()) {
                            "Nessun luogo ancora salvato"
                        } else {
                            "Nessun luogo in questa categoria"
                        }

                    textSize = 17f

                    setTextColor(
                        Color.rgb(
                            50,
                            50,
                            50
                        )
                    )

                    gravity =
                        Gravity.CENTER
                }

            val emptyText =
                TextView(this).apply {

                    text =
                        if (currentPlaces.isEmpty()) {
                            "Importa una lista da Google Maps\n" +
                                    "per iniziare a organizzare i tuoi luoghi."
                        } else {
                            "Prova a selezionare un'altra categoria."
                        }

                    textSize = 14f

                    setTextColor(
                        Color.rgb(
                            110,
                            110,
                            110
                        )
                    )

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        0,
                        8,
                        0,
                        0
                    )
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

        placesToShow.forEach { place ->

            container.addView(
                createPlaceView(place),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
            )
        }
    }

    // ============================================================
    // PLACE CARD
    // ============================================================

    private fun createPlaceView(
        place: Place
    ): View {

        val category =
            currentCategories.firstOrNull {
                it.id == place.categoryId
            }

        val box =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    18,
                    16,
                    18,
                    16
                )

                background =
                    roundedBackground(
                        Color.WHITE,
                        18f
                    )

                setOnClickListener {
                    showPlaceMenu(place)
                }
            }

        val name =
            TextView(this).apply {

                text =
                    if (category != null) {
                        "${category.iconKey}  ${place.name}"
                    } else {
                        "📍  ${place.name}"
                    }

                textSize = 17f

                setTextColor(
                    Color.rgb(
                        35,
                        35,
                        35
                    )
                )

                setPadding(
                    0,
                    0,
                    0,
                    7
                )
            }

        box.addView(name)

        if (category != null) {

            val categoryView =
                TextView(this).apply {

                    text = category.name

                    textSize = 12f

                    setTextColor(
                        category.colorArgb
                    )

                    setPadding(
                        0,
                        0,
                        0,
                        7
                    )
                }

            box.addView(categoryView)

        } else {

            val uncategorized =
                TextView(this).apply {

                    text = "Senza categoria"

                    textSize = 12f

                    setTextColor(
                        Color.rgb(
                            150,
                            150,
                            150
                        )
                    )

                    setPadding(
                        0,
                        0,
                        0,
                        7
                    )
                }

            box.addView(uncategorized)
        }

        if (!place.address.isNullOrBlank()) {

            val address =
                TextView(this).apply {

                    text = place.address

                    textSize = 14f

                    setTextColor(
                        Color.rgb(
                            95,
                            95,
                            95
                        )
                    )

                    setPadding(
                        0,
                        0,
                        0,
                        7
                    )
                }

            box.addView(address)
        }

        val coordinates =
            TextView(this).apply {

                text =
                    "📍 ${place.latitude}, ${place.longitude}"

                textSize = 11f

                setTextColor(
                    Color.rgb(
                        130,
                        130,
                        130
                    )
                )
            }

        box.addView(coordinates)

        val actions =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.END

                setPadding(
                    0,
                    10,
                    0,
                    0
                )
            }

        val mapsButton =
            Button(this).apply {

                text = "MAPS"

                textSize = 11f

                setOnClickListener {

                    openInGoogleMaps(place)
                }
            }

        val categoryButton =
            Button(this).apply {

                text =
                    if (category == null) {
                        "CATEGORIZZA"
                    } else {
                        "CAMBIA CATEGORIA"
                    }

                textSize = 11f

                setOnClickListener {

                    showCategoryPicker(place)
                }
            }

        actions.addView(
            categoryButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                42
            ).apply {
                rightMargin = 6
            }
        )

        actions.addView(
            mapsButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                42
            )
        )

        box.addView(actions)

        return box
    }

    // ============================================================
    // PLACE MENU
    // ============================================================

    private fun showPlaceMenu(
        place: Place
    ) {

        val options =
            arrayOf(
                "Cambia categoria",
                "Apri in Google Maps"
            )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(place.name)
            .setItems(options) { _, which ->

                when (which) {

                    0 ->
                        showCategoryPicker(place)

                    1 ->
                        openInGoogleMaps(place)
                }
            }
            .setNegativeButton(
                "Annulla",
                null
            )
            .show()
    }

    // ============================================================
    // CATEGORY PICKER
    // ============================================================

    private fun showCategoryPicker(
        place: Place
    ) {

        if (currentCategories.isEmpty()) {

            Toast.makeText(
                this,
                "Prima crea almeno una categoria.",
                Toast.LENGTH_LONG
            ).show()

            showCreateCategoryDialog()

            return
        }

        val items =
            mutableListOf<String>()

        items.add("⚪  Senza categoria")

        currentCategories.forEach { category ->

            items.add(
                "${category.iconKey}  ${category.name}"
            )
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(
                "Categoria di\n${place.name}"
            )
            .setItems(
                items.toTypedArray()
            ) { _, which ->

                val categoryId =
                    if (which == 0) {
                        null
                    } else {
                        currentCategories[
                            which - 1
                        ].id
                    }

                lifecycleScope.launch {

                    repository.assignPlaceToCategory(
                        place.id,
                        categoryId
                    )

                    runOnUiThread {

                        Toast.makeText(
                            this@MainActivity,
                            "Categoria aggiornata",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNeutralButton(
                "＋ Nuova categoria"
            ) { _, _ ->

                showCreateCategoryDialog()
            }
            .setNegativeButton(
                "Annulla",
                null
            )
            .show()
    }

    // ============================================================
    // CATEGORIES DIALOG
    // ============================================================

    private fun showCategoriesDialog() {

        val items =
            mutableListOf<String>()

        currentCategories.forEach { category ->

            val count =
                currentPlaces.count {
                    it.categoryId == category.id
                }

            items.add(
                "${category.iconKey}  ${category.name}  ($count)"
            )
        }

        items.add(
            "＋  Crea nuova categoria"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Le mie categorie")
            .setItems(
                items.toTypedArray()
            ) { _, which ->

                if (which == currentCategories.size) {

                    showCreateCategoryDialog()

                } else {

                    showCategoryOptions(
                        currentCategories[which]
                    )
                }
            }
            .setNegativeButton(
                "Chiudi",
                null
            )
            .show()
    }

    // ============================================================
    // CATEGORY OPTIONS
    // ============================================================

    private fun showCategoryOptions(
        category: Category
    ) {

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(
                "${category.iconKey}  ${category.name}"
            )
            .setItems(
                arrayOf(
                    "Visualizza luoghi",
                    "Elimina categoria"
                )
            ) { _, which ->

                when (which) {

                    0 -> {

                        selectedCategoryId =
                            category.id

                        refreshHome()
                    }

                    1 -> {

                        confirmDeleteCategory(
                            category
                        )
                    }
                }
            }
            .setNegativeButton(
                "Annulla",
                null
            )
            .show()
    }

    // ============================================================
    // CREATE CATEGORY
    // ============================================================

    private fun showCreateCategoryDialog() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    24,
                    8,
                    24,
                    0
                )
            }

        val nameInput =
            EditText(this).apply {

                hint = "Nome categoria"

                setSingleLine(true)
            }

        layout.addView(
            nameInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val iconTitle =
            TextView(this).apply {

                text = "Scegli icona"

                textSize = 14f

                setTextColor(
                    Color.rgb(
                        80,
                        80,
                        80
                    )
                )

                setPadding(
                    0,
                    18,
                    0,
                    8
                )
            }

        layout.addView(iconTitle)

        var selectedIcon = "📍"

        val iconContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        val icons =
            listOf(
                "📍",
                "🍴",
                "🏨",
                "🏖️",
                "🏛️",
                "🌄",
                "🎯",
                "🛍️",
                "☕",
                "🍺"
            )

        icons.forEach { icon ->

            val button =
                Button(this).apply {

                    text = icon

                    textSize = 20f

                    setPadding(
                        2,
                        0,
                        2,
                        0
                    )

                    setOnClickListener {

                        selectedIcon = icon

                        Toast.makeText(
                            this@MainActivity,
                            "Icona: $icon",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            iconContainer.addView(
                button,
                LinearLayout.LayoutParams(
                    52,
                    52
                ).apply {
                    rightMargin = 4
                }
            )
        }

        val iconScroll =
            ScrollView(this).apply {

                isHorizontalScrollBarEnabled =
                    false

                addView(iconContainer)
            }

        layout.addView(
            iconScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                62
            )
        )

        val colorTitle =
            TextView(this).apply {

                text = "Scegli colore"

                textSize = 14f

                setTextColor(
                    Color.rgb(
                        80,
                        80,
                        80
                    )
                )

                setPadding(
                    0,
                    14,
                    0,
                    8
                )
            }

        layout.addView(colorTitle)

        var selectedColor =
            Color.rgb(
                45,
                105,
                225
            )

        val colorContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        val colors =
            listOf(
                Color.rgb(45, 105, 225),
                Color.rgb(220, 70, 70),
                Color.rgb(45, 160, 90),
                Color.rgb(230, 150, 30),
                Color.rgb(140, 80, 190),
                Color.rgb(0, 150, 170),
                Color.rgb(220, 70, 130),
                Color.rgb(90, 90, 90)
            )

        colors.forEach { color ->

            val colorButton =
                View(this).apply {

                    background =
                        roundedBackground(
                            color,
                            30f
                        )

                    setOnClickListener {

                        selectedColor = color
                    }
                }

            colorContainer.addView(
                colorButton,
                LinearLayout.LayoutParams(
                    42,
                    42
                ).apply {
                    rightMargin = 8
                }
            )
        }

        layout.addView(colorContainer)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nuova categoria")
            .setView(layout)
            .setPositiveButton("CREA") { _, _ ->

                val name =
                    nameInput.text
                        .toString()
                        .trim()

                if (name.isBlank()) {

                    Toast.makeText(
                        this,
                        "Inserisci un nome.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                lifecycleScope.launch {

                    repository.createCategory(
                        name = name,
                        colorArgb = selectedColor,
                        iconKey = selectedIcon
                    )

                    runOnUiThread {

                        Toast.makeText(
                            this@MainActivity,
                            "Categoria creata",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(
                "ANNULLA",
                null
            )
            .show()
    }

    // ============================================================
    // DELETE CATEGORY
    // ============================================================

    private fun confirmDeleteCategory(
        category: Category
    ) {

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Eliminare categoria?")
            .setMessage(
                "La categoria \"${category.name}\" verrà eliminata.\n\n" +
                        "I luoghi resteranno salvati, ma torneranno senza categoria."
            )
            .setPositiveButton("ELIMINA") { _, _ ->

                lifecycleScope.launch {

                    repository.deleteCategory(category)

                    runOnUiThread {

                        if (
                            selectedCategoryId ==
                            category.id
                        ) {

                            selectedCategoryId = null
                        }

                        Toast.makeText(
                            this@MainActivity,
                            "Categoria eliminata",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(
                "ANNULLA",
                null
            )
            .show()
    }

    // ============================================================
    // OPEN GOOGLE MAPS
    // ============================================================

    private fun openInGoogleMaps(
        place: Place
    ) {

        val uri =
            Uri.parse(
                "https://www.google.com/maps/search/?api=1" +
                        "&query=${place.latitude},${place.longitude}"
            )

        try {

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Impossibile aprire Google Maps.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // BACKGROUND
    // ============================================================

    private fun roundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                radius *
                        resources.displayMetrics.density
        }
    }

    // ============================================================
    // DP
    // ============================================================

    private fun dp(
        value: Int
    ): Int {

        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }

    // ============================================================
    // IMPORTER
    // ============================================================

    private fun showImporter() {

        consentAttempted = false
        scanStarted = false
        importStarted = false
        currentListId = null

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                setBackgroundColor(
                    Color.rgb(
                        248,
                        249,
                        250
                    )
                )

                setPadding(
                    28,
                    80,
                    28,
                    40
                )
            }

        val title =
            TextView(this).apply {

                text = "TRAVELPINS"

                textSize = 30f

                setTextColor(
                    Color.rgb(
                        25,
                        25,
                        25
                    )
                )

                gravity = Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    12
                )
            }

        root.addView(title)

        val importTitle =
            TextView(this).apply {

                text = "Importazione in corso"

                textSize = 22f

                setTextColor(
                    Color.rgb(
                        40,
                        40,
                        40
                    )
                )

                gravity = Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    10
                )
            }

        root.addView(importTitle)

        val status =
            TextView(this).apply {

                tag = "import_status"

                text =
                    "Sto leggendo la lista di Google Maps…"

                textSize = 15f

                setTextColor(
                    Color.rgb(
                        100,
                        100,
                        100
                    )
                )

                gravity = Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    24
                )
            }

        root.addView(status)

        val progress =
            ProgressBar(this).apply {

                isIndeterminate = true

                tag = "import_progress"
            }

        root.addView(
            progress,
            LinearLayout.LayoutParams(
                60,
                60
            ).apply {
                bottomMargin = 24
            }
        )

        val info =
            TextView(this).apply {

                text =
                    "Non chiudere TravelPins.\n" +
                            "L'importazione potrebbe richiedere alcuni secondi."

                textSize = 14f

                setTextColor(
                    Color.rgb(
                        120,
                        120,
                        120
                    )
                )

                gravity = Gravity.CENTER

                setPadding(
                    10,
                    0,
                    10,
                    30
                )
            }

        root.addView(info)

        val cancelButton =
            Button(this).apply {

                text = "ANNULLA"

                textSize = 13f

                setTextColor(
                    Color.rgb(
                        70,
                        70,
                        70
                    )
                )

                background =
                    roundedBackground(
                        Color.WHITE,
                        14f
                    )

                setOnClickListener {

                    webView.stopLoading()

                    showHome()
                }
            }

        root.addView(
            cancelButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                52
            )
        )

        webView.alpha = 0f

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                1,
                1
            )
        )

        outputView =
            TextView(this).apply {

                text =
                    "TRAVELPINS NETWORK MONITOR"

                visibility = View.GONE
            }

        root.addView(
            outputView,
            LinearLayout.LayoutParams(
                1,
                1
            )
        )

        setContentView(root)
    }

    // ============================================================
    // IMPORT STATUS
    // ============================================================

    private fun updateImportStatus(
        message: String
    ) {

        runOnUiThread {

            val content =
                findViewById<View>(
                    android.R.id.content
                )

            val status =
                content.findViewWithTag<TextView>(
                    "import_status"
                )

            status?.text = message
        }
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {

        webView =
            WebView(this).apply {

                settings.javaScriptEnabled = true

                settings.domStorageEnabled = true

                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 10) " +
                            "AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                alpha = 0f
            }

        val bridge =
            TravelPinsJsBridge(

                repository = repository,

                scope = lifecycleScope,

                getCurrentSourceListId = {
                    currentListId
                },

                getCurrentSourceListName = {
                    null
                },

                onImportFinished = { savedCount ->

                    runOnUiThread {

                        updateImportStatus(
                            "Importazione completata.\n" +
                                    "$savedCount luoghi salvati."
                        )

                        Toast.makeText(
                            this,
                            "$savedCount luoghi importati",
                            Toast.LENGTH_SHORT
                        ).show()

                        webView.stopLoading()

                        webView.postDelayed({

                            showHome()

                        }, 700)
                    }
                },

                onImportError = { error ->

                    runOnUiThread {

                        updateImportStatus(
                            "Si è verificato un errore.\n\n" +
                                    "${error.message}"
                        )

                        Toast.makeText(
                            this,
                            "Errore durante l'importazione",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },

                onLogMessage = { message ->

                    if (::outputView.isInitialized) {
                        appendOutput(message)
                    }
                }
            )

        webView.addJavascriptInterface(
            bridge,
            TravelPinsJsBridge.NAME
        )

        webView.addJavascriptInterface(
            bridge,
            TravelPinsJsBridge.BRIDGE_NAME
        )

        webView.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    appendOutput(
                        "PAGINA CARICATA: $url"
                    )

                    view.evaluateJavascript(
                        GoogleMapsScraperScript
                            .NETWORK_HOOK_SCRIPT,
                        null
                    )

                    if (
                        url.contains(
                            "consent.google.com"
                        )
                    ) {

                        if (!consentAttempted) {

                            consentAttempted = true

                            updateImportStatus(
                                "Autorizzazione Google in corso…"
                            )

                            view.postDelayed({

                                acceptGoogleConsent()

                            }, 700)
                        }

                        return
                    }

                    if (
                        GoogleMapsScraperScript
                            .isGoogleListUrl(url)
                    ) {

                        currentListId =
                            extractListId(url)

                        appendOutput(
                            "LISTA GOOGLE MAPS RILEVATA\n$url"
                        )

                        updateImportStatus(
                            "Lista trovata.\n" +
                                    "Lettura dei luoghi in corso…"
                        )

                        if (
                            currentListId != null &&
                            !scanStarted
                        ) {

                            scanStarted = true

                            view.postDelayed({

                                scanGoogleList()

                            }, 500)
                        }
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {

                    appendOutput(
                        "WEBVIEW RENDERER TERMINATO\n" +
                                "CRASH: $detail"
                    )

                    updateImportStatus(
                        "Google Maps ha interrotto l'importazione."
                    )

                    return true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url =
                        request.url.toString()

                    if (
                        url.startsWith(
                            "intent://"
                        )
                    ) {

                        handleGoogleIntent(url)

                        return true
                    }

                    return false
                }
            }
    }

    // ============================================================
    // GOOGLE LIST ID
    // ============================================================

    private fun extractListId(
        url: String
    ): String? {

        Regex(
            "!11m2!2s([^!&]+)",
            RegexOption.IGNORE_CASE
        ).find(url)?.let {

            return it.groupValues[1]
        }

        Regex(
            """/local/userlists/list/([^?/]+)""",
            RegexOption.IGNORE_CASE
        ).find(url)?.let {

            return it.groupValues[1]
        }

        Regex(
            "2s([A-Za-z0-9_-]{20,})"
        ).find(url)?.let {

            return it.groupValues[1]
        }

        return null
    }

    // ============================================================
    // GOOGLE CONSENT
    // ============================================================

    private fun acceptGoogleConsent() {

        if (!::webView.isInitialized) {
            return
        }

        updateImportStatus(
            "Autorizzazione Google…"
        )

        webView.evaluateJavascript(
            GoogleMapsScraperScript
                .ACCEPT_CONSENT_SCRIPT
        ) { result ->

            appendOutput(
                "CONSENSO RISULTATO\n$result"
            )
        }
    }

    // ============================================================
    // GOOGLE SCAN
    // ============================================================

    private fun scanGoogleList() {

        if (importStarted) {
            return
        }

        importStarted = true

        updateImportStatus(
            "Lettura dei luoghi in corso…"
        )

        appendOutput(
            "SCANSIONE LISTA AVVIATA\n" +
                    "Metodo: entitylist/getlist"
        )

        webView.evaluateJavascript(
            GoogleMapsScraperScript
                .GETLIST_SCRIPT
        ) { result ->

            appendOutput(
                "CALLBACK GETLIST\n$result"
            )
        }
    }

    // ============================================================
    // GOOGLE INTENT
    // ============================================================

    private fun handleGoogleIntent(
        intentUrl: String
    ) {

        try {

            val marker =
                "S.browser_fallback_url="

            val start =
                intentUrl.indexOf(marker)

            if (start == -1) {

                updateImportStatus(
                    "Impossibile aprire la lista Google Maps."
                )

                appendOutput(
                    "FALLBACK URL NON TROVATO"
                )

                return
            }

            var value =
                intentUrl.substring(
                    start + marker.length
                )

            val end =
                value.indexOf("#Intent")

            if (end != -1) {

                value =
                    value.substring(
                        0,
                        end
                    )
            }

            val decoded =
                URLDecoder.decode(
                    value,
                    "UTF-8"
                )

            appendOutput(
                "GOOGLE INTENT INTERCETTATO\n" +
                        "FALLBACK WEB:\n$decoded"
            )

            webView.loadUrl(decoded)

        } catch (e: Exception) {

            appendOutput(
                "ERRORE PARSING INTENT:\n$e"
            )

            updateImportStatus(
                "Errore durante l'apertura della lista."
            )
        }
    }

    // ============================================================
    // SHARE INTENT
    // ============================================================

    private fun handleIntent(
        intent: Intent?
    ) {

        if (
            intent?.action !=
            Intent.ACTION_SEND
        ) {
            return
        }

        val text =
            intent.getStringExtra(
                Intent.EXTRA_TEXT
            )

        if (text.isNullOrBlank()) {

            Toast.makeText(
                this,
                "Nessun testo ricevuto",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(text)

        if (match == null) {

            Toast.makeText(
                this,
                "Nessun URL trovato",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val url = match.value

        consentAttempted = false
        scanStarted = false
        importStarted = false
        currentListId = null

        showImporter()

        updateImportStatus(
            "Apertura della lista Google Maps…"
        )

        appendOutput(
            "LINK RICEVUTO\n$url"
        )

        webView.loadUrl(url)
    }

    // ============================================================
    // LOG INTERNO
    // ============================================================

    private fun appendOutput(
        section: String
    ) {

        if (!::outputView.isInitialized) {
            return
        }

        outputView.append(
            "\n$section\n"
        )
    }

    // ============================================================
    // COPIA LOG
    // ============================================================

    private fun copyOutputToClipboard() {

        if (!::outputView.isInitialized) {
            return
        }

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as? ClipboardManager
                ?: return

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "TravelPins",
                outputView.text
            )
        )

        Toast.makeText(
            this,
            "Copiato!",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ============================================================
    // PULISCI LOG
    // ============================================================

    private fun clearOutput() {

        if (!::outputView.isInitialized) {
            return
        }

        outputView.text =
            "TRAVELPINS NETWORK MONITOR\n\n" +
                    "Monitor pulito."
    }
}
