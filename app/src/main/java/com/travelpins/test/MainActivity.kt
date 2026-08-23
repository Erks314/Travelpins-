package com.travelpins.test

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var output: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var scanButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val log = ConcurrentLinkedQueue<String>()

    private val places = ArrayList<Place>()
    private val categories = ArrayList<Category>()

    private var pageReady = false
    private var scanning = false

    // ============================================================
    // MODELLI
    // ============================================================

    data class Place(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        var categoryId: String = ""
    )

    data class Category(
        val id: String,
        var name: String,
        var color: Int,
        var icon: String
    )

    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadCategories()
        loadPlaces()

        createInterface()
        createWebView()

        handleIntent(intent)
    }

    // ============================================================
    // INTERFACCIA
    // ============================================================

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // --------------------------------------------------------
        // TOOLBAR
        // --------------------------------------------------------

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(35, 35, 35))
        }

        val title = TextView(this).apply {
            text = "📍 TravelPins"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }

        toolbar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val categoriesButton = Button(this).apply {
            text = "Categorie"

            setOnClickListener {
                showCategories()
            }
        }

        toolbar.addView(categoriesButton)

        root.addView(toolbar)

        // --------------------------------------------------------
        // PULSANTE SCANSIONA
        // --------------------------------------------------------

        scanButton = Button(this).apply {

            text = "🔎 SCANSIONA LISTA"

            textSize = 17f

            setOnClickListener {

                if (webView.url.isNullOrBlank()) {

                    Toast.makeText(
                        this@MainActivity,
                        "Prima apri una lista Google Maps.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setOnClickListener
                }

                scanGoogleMapsPage()
            }
        }

        root.addView(
            scanButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // --------------------------------------------------------
        // PROGRESS
        // --------------------------------------------------------

        progress = ProgressBar(this).apply {
            visibility = ProgressBar.GONE
        }

        root.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // --------------------------------------------------------
        // STATUS
        // --------------------------------------------------------

        statusText = TextView(this).apply {

            text = ""

            textSize = 15f

            setTextColor(Color.DKGRAY)

            gravity = Gravity.CENTER

            setPadding(
                20,
                12,
                20,
                12
            )

            visibility = TextView.GONE
        }

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // --------------------------------------------------------
        // WEBVIEW
        // --------------------------------------------------------

        webView = WebView(this)

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // --------------------------------------------------------
        // OUTPUT
        // --------------------------------------------------------

        val scroll = ScrollView(this)

        output = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                20,
                20,
                20,
                40
            )
        }

        scroll.addView(output)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        showPlaces()
    }

    // ============================================================
    // STATUS
    // ============================================================

    private fun showStatus(text: String) {

        statusText.text = text

        statusText.visibility =
            if (text.isBlank()) {
                TextView.GONE
            } else {
                TextView.VISIBLE
            }
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    private fun createWebView() {

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            databaseEnabled = true

            loadsImagesAutomatically = true

            javaScriptCanOpenWindowsAutomatically = true

            setSupportMultipleWindows(false)

            userAgentString =
                "Mozilla/5.0 (Linux; Android 10) " +
                        "AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) " +
                        "Chrome/131.0.0.0 " +
                        "Mobile Safari/537.36"
        }

        CookieManager
            .getInstance()
            .setAcceptCookie(true)

        CookieManager
            .getInstance()
            .setAcceptThirdPartyCookies(
                webView,
                true
            )

        webView.addJavascriptInterface(
            TravelPinsBridge(),
            "TravelPins"
        )

        webView.webChromeClient =
            WebChromeClient()

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url =
                        request.url.toString()

                    addLog(
                        "[NAVIGAZIONE]\n$url"
                    )

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

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?
                ) {

                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    pageReady = false

                    showStatus(
                        "Caricamento Google Maps…"
                    )

                    addLog(
                        "[PAGINA IN CARICAMENTO]\n$url"
                    )
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    pageReady = true

                    showStatus(
                        "Lista caricata. Premi SCANSIONA LISTA."
                    )

                    addLog(
                        "[PAGINA CARICATA]\n$url"
                    )

                    // Aspettiamo che Google Maps completi
                    // il rendering dinamico.

                    handler.postDelayed(
                        {

                            if (
                                pageReady &&
                                !scanning
                            ) {

                                showStatus(
                                    "Lista pronta. Premi SCANSIONA LISTA."
                                )
                            }

                        },
                        1500
                    )
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {

                    if (
                        request.isForMainFrame
                    ) {

                        addLog(
                            "[WEBVIEW ERROR]\n" +
                                    error.description
                        )

                        showStatus(
                            "Errore nel caricamento di Google Maps."
                        )
                    }

                    super.onReceivedError(
                        view,
                        request,
                        error
                    )
                }
            }
    }

    // ============================================================
    // JAVASCRIPT BRIDGE
    // ============================================================

    inner class TravelPinsBridge {

        @JavascriptInterface
        fun log(message: String?) {

            if (
                !message.isNullOrBlank()
            ) {

                addLog(message)
            }
        }

        @JavascriptInterface
        fun importPlaces(
            json: String
        ) {

            runOnUiThread {

                try {

                    val array =
                        JSONArray(json)

                    if (
                        array.length() == 0
                    ) {

                        scanning = false

                        progress.visibility =
                            ProgressBar.GONE

                        scanButton.isEnabled =
                            true

                        showStatus(
                            "Nessun luogo trovato."
                        )

                        return@runOnUiThread
                    }

                    places.clear()

                    for (
                        i in 0 until array.length()
                    ) {

                        val item =
                            array.getJSONObject(i)

                        val name =
                            item.optString(
                                "name"
                            )

                        val address =
                            item.optString(
                                "address"
                            )

                        val lat =
                            item.optDouble(
                                "lat",
                                Double.NaN
                            )

                        val lng =
                            item.optDouble(
                                "lng",
                                Double.NaN
                            )

                        if (
                            name.isNotBlank() &&
                            !lat.isNaN() &&
                            !lng.isNaN()
                        ) {

                            places.add(
                                Place(
                                    name = name,
                                    address = address,
                                    lat = lat,
                                    lng = lng
                                )
                            )
                        }
                    }

                    savePlaces()

                    scanning = false

                    progress.visibility =
                        ProgressBar.GONE

                    scanButton.isEnabled =
                        true

                    showStatus(
                        "${places.size} luoghi importati."
                    )

                    showPlaces()

                    Toast.makeText(
                        this@MainActivity,
                        "${places.size} luoghi importati",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {

                    scanning = false

                    progress.visibility =
                        ProgressBar.GONE

                    scanButton.isEnabled =
                        true

                    showStatus(
                        "Errore importazione: ${e.message}"
                    )

                    addLog(
                        "[IMPORT ERROR]\n" +
                                e.stackTraceToString()
                    )
                }
            }
        }

        @JavascriptInterface
        fun importError(
            message: String
        ) {

            runOnUiThread {

                scanning = false

                progress.visibility =
                    ProgressBar.GONE

                scanButton.isEnabled =
                    true

                showStatus(
                    message
                )

                addLog(
                    "[IMPORT ERROR]\n$message"
                )
            }
        }
    }

    // ============================================================
    // SCANSIONE GOOGLE MAPS
    // ============================================================

    private fun scanGoogleMapsPage() {

        if (scanning) {
            return
        }

        scanning = true

        progress.visibility =
            ProgressBar.VISIBLE

        scanButton.isEnabled =
            false

        showStatus(
            "Scansione della pagina Google Maps…"
        )

        addLog(
            """
            ==============================
            SCANSIONE MANUALE

            URL:
            ${webView.url}

            ==============================
            """.trimIndent()
        )

        val javascript = """
            (function() {

                try {

                    TravelPins.log(
                        "[SCAN] Avvio analisi DOM Google Maps"
                    );

                    var places = [];

                    // =================================================
                    // NORMALIZZA TESTO
                    // =================================================

                    function clean(value) {

                        if (
                            value === null ||
                            value === undefined
                        ) {
                            return "";
                        }

                        return String(value)
                            .replace(/\s+/g, " ")
                            .trim();
                    }

                    // =================================================
                    // NUMERO
                    // =================================================

                    function validNumber(value) {

                        if (
                            value === null ||
                            value === undefined
                        ) {
                            return false;
                        }

                        var n =
                            Number(value);

                        return (
                            isFinite(n)
                        );
                    }

                    // =================================================
                    // COORDINATE DA URL
                    // =================================================

                    function coordinatesFromUrl(
                        url
                    ) {

                        if (!url) {
                            return null;
                        }

                        var m =
                            url.match(
                                /@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)/i
                            );

                        if (m) {

                            return {
                                lat:
                                    Number(m[1]),

                                lng:
                                    Number(m[2])
                            };
                        }

                        return null;
                    }

                    // =================================================
                    // AGGIUNGI LUOGO
                    // =================================================

                    function addPlace(
                        name,
                        address,
                        lat,
                        lng
                    ) {

                        name =
                            clean(name);

                        address =
                            clean(address);

                        lat =
                            Number(lat);

                        lng =
                            Number(lng);

                        if (
                            !name ||
                            name.length < 2
                        ) {
                            return;
                        }

                        if (
                            !validNumber(lat) ||
                            !validNumber(lng)
                        ) {
                            return;
                        }

                        if (
                            Math.abs(lat) > 90 ||
                            Math.abs(lng) > 180
                        ) {
                            return;
                        }

                        // Evita elementi che sono chiaramente
                        // pulsanti/menu di Google Maps.

                        var bad = [
                            "condividi",
                            "indicazioni",
                            "salva",
                            "modifica",
                            "aggiungi luogo",
                            "cerca qui",
                            "google maps"
                        ];

                        var lower =
                            name.toLowerCase();

                        for (
                            var b = 0;
                            b < bad.length;
                            b++
                        ) {

                            if (
                                lower === bad[b]
                            ) {
                                return;
                            }
                        }

                        places.push({

                            name:
                                name,

                            address:
                                address,

                            lat:
                                lat,

                            lng:
                                lng
                        });
                    }

                    // =================================================
                    // ESTRAZIONE DA LINK GOOGLE MAPS
                    // =================================================

                    var links =
                        document.querySelectorAll(
                            'a[href]'
                        );

                    TravelPins.log(
                        "[SCAN] Link trovati: " +
                        links.length
                    );

                    for (
                        var i = 0;
                        i < links.length;
                        i++
                    ) {

                        try {

                            var link =
                                links[i];

                            var href =
                                link.href || "";

                            if (
                                href.indexOf(
                                    "google.com/maps"
                                ) === -1 &&
                                href.indexOf(
                                    "maps.google"
                                ) === -1
                            ) {
                                continue;
                            }

                            var coord =
                                coordinatesFromUrl(
                                    href
                                );

                            if (!coord) {
                                continue;
                            }

                            var text =
                                clean(
                                    link.innerText ||
                                    link.textContent
                                );

                            if (
                                !text
                            ) {
                                continue;
                            }

                            var lines =
                                text
                                    .split(
                                        "\n"
                                    )
                                    .map(
                                        clean
                                    )
                                    .filter(
                                        function(x) {
                                            return x;
                                        }
                                    );

                            var name =
                                lines.length > 0
                                    ? lines[0]
                                    : text;

                            var address =
                                lines.length > 1
                                    ? lines
                                        .slice(1)
                                        .join(", ")
                                    : "";

                            addPlace(
                                name,
                                address,
                                coord.lat,
                                coord.lng
                            );

                        } catch(e) {

                            TravelPins.log(
                                "[SCAN LINK ERROR] " +
                                e.message
                            );
                        }
                    }

                    // =================================================
                    // ELEMENTI CON DATA ATTRIBUTES
                    // =================================================

                    var all =
                        document.querySelectorAll(
                            "*"
                        );

                    TravelPins.log(
                        "[SCAN] Elementi DOM: " +
                        all.length
                    );

                    for (
                        var j = 0;
                        j < all.length;
                        j++
                    ) {

                        try {

                            var el =
                                all[j];

                            var aria =
                                clean(
                                    el.getAttribute(
                                        "aria-label"
                                    )
                                );

                            var dataLat =
                                el.getAttribute(
                                    "data-lat"
                                );

                            var dataLng =
                                el.getAttribute(
                                    "data-lng"
                                );

                            if (
                                aria &&
                                validNumber(
                                    dataLat
                                ) &&
                                validNumber(
                                    dataLng
                                )
                            ) {

                                addPlace(
                                    aria,
                                    "",
                                    Number(dataLat),
                                    Number(dataLng)
                                );
                            }

                        } catch(e) {}
                    }

                    // =================================================
                    // JSON PRESENTE NELLA PAGINA
                    // =================================================

                    var scripts =
                        document.querySelectorAll(
                            "script"
                        );

                    TravelPins.log(
                        "[SCAN] Script trovati: " +
                        scripts.length
                    );

                    function scanString(
                        text
                    ) {

                        if (!text) {
                            return;
                        }

                        // Cerca sequenze del tipo
                        // latitude,longitude

                        var regex =
                            /(-?\d{1,3}\.\d{4,})\s*,\s*(-?\d{1,3}\.\d{4,})/g;

                        var match;

                        while (
                            (match =
                                regex.exec(text)) !==
                            null
                        ) {

                            var lat =
                                Number(match[1]);

                            var lng =
                                Number(match[2]);

                            if (
                                Math.abs(lat) <= 90 &&
                                Math.abs(lng) <= 180
                            ) {

                                // Cerca il testo immediatamente
                                // precedente alle coordinate.

                                var start =
                                    Math.max(
                                        0,
                                        match.index - 500
                                    );

                                var before =
                                    text.substring(
                                        start,
                                        match.index
                                    );

                                var strings =
                                    before.match(
                                        /"([^"]{2,150})"/g
                                    );

                                var name = "";

                                if (
                                    strings &&
                                    strings.length
                                ) {

                                    name =
                                        strings[
                                            strings.length - 1
                                        ]
                                            .replace(
                                                /^"/,
                                                ""
                                            )
                                            .replace(
                                                /"$/,
                                                ""
                                            );
                                }

                                addPlace(
                                    name,
                                    "",
                                    lat,
                                    lng
                                );
                            }
                        }
                    }

                    for (
                        var s = 0;
                        s < scripts.length;
                        s++
                    ) {

                        try {

                            scanString(
                                scripts[s].textContent
                            );

                        } catch(e) {}
                    }

                    // =================================================
                    // DEDUPLICAZIONE
                    // =================================================

                    var unique = [];

                    var seen = {};

                    for (
                        var p = 0;
                        p < places.length;
                        p++
                    ) {

                        var item =
                            places[p];

                        var key =
                            item.name.toLowerCase() +
                            "|" +
                            item.lat.toFixed(6) +
                            "|" +
                            item.lng.toFixed(6);

                        if (
                            !seen[key]
                        ) {

                            seen[key] = true;

                            unique.push(
                                item
                            );
                        }
                    }

                    places =
                        unique;

                    TravelPins.log(
                        "[SCAN] Luoghi trovati: " +
                        places.length
                    );

                    // =================================================
                    // RISULTATO
                    // =================================================

                    if (
                        places.length === 0
                    ) {

                        TravelPins.importError(
                            "Nessun luogo trovato nella pagina. " +
                            "Assicurati che la lista Google Maps sia completamente caricata e riprova."
                        );

                        return;
                    }

                    TravelPins.log(
                        "[SCAN] Invio dati a TravelPins"
                    );

                    TravelPins.importPlaces(
                        JSON.stringify(
                            places
                        )
                    );

                } catch(e) {

                    TravelPins.importError(
                        "Errore durante la scansione: " +
                        e.message
                    );

                    TravelPins.log(
                        "[SCAN GENERAL ERROR] " +
                        e.stack
                    );
                }

            })();
        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            addLog(
                "[JAVASCRIPT CALLBACK]\n$result"
            )
        }

        // --------------------------------------------------------
        // TIMEOUT SOLO DELLA SCANSIONE
        // --------------------------------------------------------

        handler.postDelayed(
            {

                if (scanning) {

                    scanning = false

                    progress.visibility =
                        ProgressBar.GONE

                    scanButton.isEnabled =
                        true

                    showStatus(
                        "Scansione terminata senza risposta. Riprova dopo aver atteso il caricamento completo della lista."
                    )

                    addLog(
                        "[SCAN TIMEOUT]"
                    )
                }

            },
            30000
        )
    }

    // ============================================================
    // VISUALIZZAZIONE LUOGHI
    // ============================================================

    private fun showPlaces() {

        output.removeAllViews()

        val title =
            TextView(this).apply {

                text =
                    if (places.isEmpty()) {
                        "📍 TravelPins"
                    } else {
                        "📍 ${places.size} luoghi"
                    }

                textSize = 27f

                setTextColor(
                    Color.BLACK
                )

                setPadding(
                    0,
                    5,
                    0,
                    20
                )
            }

        output.addView(title)

        if (places.isEmpty()) {

            val empty =
                TextView(this).apply {

                    text =
                        "Condividi una lista di Google Maps con TravelPins, attendi che venga caricata e premi SCANSIONA LISTA."

                    textSize = 17f

                    setTextColor(
                        Color.GRAY
                    )

                    setPadding(
                        0,
                        10,
                        0,
                        20
                    )
                }

            output.addView(empty)

            return
        }

        places.forEachIndexed {
                index,
                place ->

            addPlaceCard(
                index,
                place
            )
        }
    }

    private fun addPlaceCard(
        index: Int,
        place: Place
    ) {

        val category =
            categories.find {
                it.id == place.categoryId
            }

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    18,
                    16,
                    18,
                    16
                )

                setBackgroundColor(
                    Color.rgb(
                        245,
                        245,
                        245
                    )
                )
            }

        val title =
            TextView(this).apply {

                text =
                    "${index + 1}. ${place.name}"

                textSize = 18f

                setTextColor(
                    Color.BLACK
                )
            }

        card.addView(title)

        if (
            place.address.isNotBlank()
        ) {

            val address =
                TextView(this).apply {

                    text =
                        place.address

                    textSize = 14f

                    setTextColor(
                        Color.DKGRAY
                    )

                    setPadding(
                        0,
                        7,
                        0,
                        7
                    )
                }

            card.addView(address)
        }

        val coordinates =
            TextView(this).apply {

                text =
                    "📌 ${place.lat}, ${place.lng}"

                textSize = 12f

                setTextColor(
                    Color.GRAY
                )

                setPadding(
                    0,
                    4,
                    0,
                    4
                )
            }

        card.addView(
            coordinates
        )

        val categoryText =
            TextView(this).apply {

                text =
                    if (category == null) {
                        "⚪ Nessuna categoria"
                    } else {
                        "${category.icon} ${category.name}"
                    }

                textSize = 15f

                setTextColor(
                    category?.color
                        ?: Color.GRAY
                )

                setPadding(
                    0,
                    5,
                    0,
                    5
                )

                setOnClickListener {
                    chooseCategory(place)
                }
            }

        card.addView(
            categoryText
        )

        card.setOnClickListener {
            chooseCategory(place)
        }

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            0,
            0,
            14
        )

        output.addView(
            card,
            params
        )
    }

    // ============================================================
    // CATEGORIE
    // ============================================================

    private fun chooseCategory(
        place: Place
    ) {

        if (categories.isEmpty()) {

            createCategory()

            return
        }

        val names =
            categories.map {
                category ->
                "${category.icon} ${category.name}"
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Categoria")
            .setItems(names) {
                    _: android.content.DialogInterface,
                    which: Int ->

                place.categoryId =
                    categories[which].id

                savePlaces()
                showPlaces()
            }
            .setNegativeButton(
                "Nessuna categoria"
            ) {
                    _: android.content.DialogInterface,
                    _: Int ->

                place.categoryId = ""

                savePlaces()
                showPlaces()
            }
            .show()
    }

    // ============================================================
    // MOSTRA CATEGORIE
    // ============================================================

    private fun showCategories() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    30,
                    10,
                    30,
                    10
                )
            }

        categories.forEach {
                category ->

            val row =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    setPadding(
                        0,
                        12,
                        0,
                        12
                    )
                }

            val icon =
                TextView(this).apply {

                    text =
                        category.icon

                    textSize = 25f
                }

            val name =
                TextView(this).apply {

                    text =
                        category.name

                    textSize = 18f

                    setTextColor(
                        category.color
                    )

                    setPadding(
                        18,
                        0,
                        0,
                        0
                    )
                }

            row.addView(icon)

            row.addView(
                name,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            layout.addView(row)
        }

        val addButton =
            Button(this).apply {

                text =
                    "＋ Nuova categoria"

                setOnClickListener {
                    createCategory()
                }
            }

        layout.addView(addButton)

        AlertDialog.Builder(this)
            .setTitle("Categorie")
            .setView(layout)
            .setPositiveButton("Chiudi") {
                    _: android.content.DialogInterface,
                    _: Int ->
            }
            .show()
    }

    // ============================================================
    // CREAZIONE CATEGORIA
    // ============================================================

    private fun createCategory() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    30,
                    10,
                    30,
                    10
                )
            }

        val name =
            EditText(this).apply {

                hint =
                    "Nome categoria"

                setSingleLine(true)
            }

        val icons =
            arrayOf(
                "📍",
                "🍴",
                "🏰",
                "🌊",
                "🏔️",
                "🏖️",
                "☕",
                "🍺",
                "📸",
                "🚗",
                "🏨",
                "⭐"
            )

        val iconSpinner =
            Spinner(this)

        iconSpinner.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                icons
            )

        layout.addView(name)

        layout.addView(
            iconSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(this)
            .setTitle("Nuova categoria")
            .setView(layout)
            .setPositiveButton("Crea") {
                    _: android.content.DialogInterface,
                    _: Int ->

                val categoryName =
                    name.text
                        .toString()
                        .trim()

                if (
                    categoryName.isBlank()
                ) {
                    return@setPositiveButton
                }

                val icon =
                    icons[
                        iconSpinner.selectedItemPosition
                    ]

                val category =
                    Category(
                        id =
                            System.currentTimeMillis()
                                .toString(),

                        name =
                            categoryName,

                        color =
                            Color.rgb(
                                30,
                                100,
                                200
                            ),

                        icon =
                            icon
                    )

                categories.add(category)

                saveCategories()

                Toast.makeText(
                    this,
                    "Categoria creata",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Annulla") {
                    _: android.content.DialogInterface,
                    _: Int ->
            }
            .show()
    }

    // ============================================================
    // SALVATAGGIO CATEGORIE
    // ============================================================

    private fun saveCategories() {

        val array =
            JSONArray()

        categories.forEach {
                category ->

            val obj =
                JSONObject()

            obj.put(
                "id",
                category.id
            )

            obj.put(
                "name",
                category.name
            )

            obj.put(
                "color",
                category.color
            )

            obj.put(
                "icon",
                category.icon
            )

            array.put(obj)
        }

        getPreferences(
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                "categories",
                array.toString()
            )
            .apply()
    }

    // ============================================================
    // CARICAMENTO CATEGORIE
    // ============================================================

    private fun loadCategories() {

        categories.clear()

        val raw =
            getPreferences(
                Context.MODE_PRIVATE
            )
                .getString(
                    "categories",
                    null
                )

        if (raw.isNullOrBlank()) {

            categories.add(
                Category(
                    id = "default_1",
                    name = "Da vedere",
                    color =
                        Color.rgb(
                            30,
                            100,
                            200
                        ),
                    icon = "📍"
                )
            )

            categories.add(
                Category(
                    id = "default_2",
                    name = "Ristoranti",
                    color =
                        Color.rgb(
                            220,
                            80,
                            50
                        ),
                    icon = "🍴"
                )
            )

            categories.add(
                Category(
                    id = "default_3",
                    name = "Natura",
                    color =
                        Color.rgb(
                            40,
                            150,
                            70
                        ),
                    icon = "🌿"
                )
            )

            saveCategories()

            return
        }

        try {

            val array =
                JSONArray(raw)

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(i)

                categories.add(
                    Category(
                        id =
                            obj.getString("id"),

                        name =
                            obj.getString("name"),

                        color =
                            obj.getInt("color"),

                        icon =
                            obj.getString("icon")
                    )
                )
            }

        } catch (
            _: Exception
        ) {

            categories.clear()
        }
    }

    // ============================================================
    // SALVATAGGIO LUOGHI
    // ============================================================

    private fun savePlaces() {

        val array =
            JSONArray()

        places.forEach {
                place ->

            val obj =
                JSONObject()

            obj.put(
                "name",
                place.name
            )

            obj.put(
                "address",
                place.address
            )

            obj.put(
                "lat",
                place.lat
            )

            obj.put(
                "lng",
                place.lng
            )

            obj.put(
                "category",
                place.categoryId
            )

            array.put(obj)
        }

        getPreferences(
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                "places",
                array.toString()
            )
            .apply()
    }

    // ============================================================
    // CARICAMENTO LUOGHI
    // ============================================================

    private fun loadPlaces() {

        places.clear()

        val raw =
            getPreferences(
                Context.MODE_PRIVATE
            )
                .getString(
                    "places",
                    null
                )

        if (raw.isNullOrBlank()) {
            return
        }

        try {

            val array =
                JSONArray(raw)

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(i)

                places.add(
                    Place(
                        name =
                            obj.optString(
                                "name"
                            ),

                        address =
                            obj.optString(
                                "address"
                            ),

                        lat =
                            obj.optDouble(
                                "lat"
                            ),

                        lng =
                            obj.optDouble(
                                "lng"
                            ),

                        categoryId =
                            obj.optString(
                                "category"
                            )
                    )
                )
            }

        } catch (
            _: Exception
        ) {

            places.clear()
        }
    }

    // ============================================================
    // GOOGLE INTENT
    // ============================================================

    private fun handleGoogleIntent(
        intentUrl: String
    ) {

        try {

            val uri =
                Uri.parse(intentUrl)

            val fallback =
                uri.getQueryParameter(
                    "S.browser_fallback_url"
                )

            if (
                !fallback.isNullOrBlank()
            ) {

                webView.loadUrl(fallback)

                return
            }

            val marker =
                "S.browser_fallback_url="

            val index =
                intentUrl.indexOf(marker)

            if (index >= 0) {

                var fallbackText =
                    intentUrl.substring(
                        index +
                                marker.length
                    )

                val end =
                    fallbackText.indexOf(
                        "#Intent"
                    )

                if (end >= 0) {

                    fallbackText =
                        fallbackText.substring(
                            0,
                            end
                        )
                }

                fallbackText =
                    Uri.decode(
                        fallbackText
                    )

                webView.loadUrl(
                    fallbackText
                )
            }

        } catch (
            _: Exception
        ) {

            addLog(
                "[INTENT ERROR]"
            )
        }
    }

    // ============================================================
    // CONDIVISIONE DA GOOGLE MAPS
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

        val sharedText =
            intent.getStringExtra(
                Intent.EXTRA_TEXT
            )

        if (
            sharedText.isNullOrBlank()
        ) {
            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(sharedText)

        if (match == null) {

            Toast.makeText(
                this,
                "Nessun link Google Maps trovato",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        var url =
            match.value

        url =
            url.trimEnd(
                '.',
                ',',
                ';',
                ')',
                ']'
            )

        addLog(
            """
            ==============================
            LINK RICEVUTO

            $url

            ==============================
            AVVIO GOOGLE MAPS...
            """.trimIndent()
        )

        pageReady = false
        scanning = false

        progress.visibility =
            ProgressBar.VISIBLE

        showStatus(
            "Apertura della lista Google Maps…"
        )

        webView.loadUrl(url)
    }

    // ============================================================
    // NUOVO INTENT
    // ============================================================

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(intent)

        setIntent(intent)

        handleIntent(intent)
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun addLog(
        message: String
    ) {

        log.add(
            message.take(15000)
        )

        while (
            log.size > 50
        ) {

            log.poll()
        }
    }

    // ============================================================
    // BACK
    // ============================================================

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        if (webView.canGoBack()) {

            webView.goBack()

        } else {

            super.onBackPressed()
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(
            null
        )

        webView.stopLoading()

        webView.removeJavascriptInterface(
            "TravelPins"
        )

        webView.destroy()

        super.onDestroy()
    }
}
