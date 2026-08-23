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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {

    // ============================================================
    // UI
    // ============================================================

    private lateinit var webView: WebView
    private lateinit var output: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var scanButton: Button

    // ============================================================
    // DATI
    // ============================================================

    private val places = ArrayList<Place>()
    private val categories = ArrayList<Category>()

    private var currentSharedUrl: String = ""
    private var importing = false

    private val handler =
        Handler(Looper.getMainLooper())

    private val executor =
        Executors.newSingleThreadExecutor()

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
    // CREATE
    // ============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

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

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    Color.WHITE
                )
            }

        // --------------------------------------------------------
        // TOOLBAR
        // --------------------------------------------------------

        val toolbar =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    12,
                    10,
                    12,
                    10
                )

                setBackgroundColor(
                    Color.rgb(
                        35,
                        35,
                        35
                    )
                )
            }

        val title =
            TextView(this).apply {
                text =
                    "📍 TravelPins"

                textSize =
                    21f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        toolbar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        scanButton =
            Button(this).apply {

                text =
                    "🔎 SCANSIONA"

                isAllCaps =
                    false

                setOnClickListener {

                    if (
                        currentSharedUrl.isBlank()
                    ) {

                        Toast.makeText(
                            this@MainActivity,
                            "Prima condividi una lista Google Maps.",
                            Toast.LENGTH_LONG
                        ).show()

                    } else {

                        scanCurrentList()
                    }
                }
            }

        toolbar.addView(
            scanButton
        )

        val categoriesButton =
            Button(this).apply {

                text =
                    "Categorie"

                isAllCaps =
                    false

                setOnClickListener {
                    showCategories()
                }
            }

        toolbar.addView(
            categoriesButton
        )

        root.addView(
            toolbar
        )

        // --------------------------------------------------------
        // PROGRESS
        // --------------------------------------------------------

        progress =
            ProgressBar(this).apply {

                visibility =
                    ProgressBar.GONE
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

        statusText =
            TextView(this).apply {

                text =
                    ""

                textSize =
                    15f

                setTextColor(
                    Color.DKGRAY
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    20,
                    10,
                    20,
                    10
                )

                visibility =
                    TextView.GONE
            }

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // --------------------------------------------------------
        // LISTA
        // --------------------------------------------------------

        val scroll =
            ScrollView(this)

        output =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    20,
                    20,
                    20,
                    40
                )
            }

        scroll.addView(
            output
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(
            root
        )

        showPlaces()
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    private fun createWebView() {

        webView =
            WebView(this)

        webView.settings.apply {

            javaScriptEnabled =
                true

            domStorageEnabled =
                true

            databaseEnabled =
                true

            loadsImagesAutomatically =
                true

            userAgentString =
                "Mozilla/5.0 (Linux; Android 10) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/151.0.0.0 " +
                "Mobile Safari/537.36"
        }

        CookieManager
            .getInstance()
            .setAcceptCookie(
                true
            )

        CookieManager
            .getInstance()
            .setAcceptThirdPartyCookies(
                webView,
                true
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

                    showStatus(
                        "Google Maps caricata."
                    )

                    /*
                     * NON facciamo più partire immediatamente
                     * una richiesta getlist costruita a mano.
                     *
                     * Aspettiamo che la pagina abbia fornito
                     * il suo vero preload URL.
                     */

                    if (
                        currentSharedUrl.isBlank()
                    ) {

                        currentSharedUrl =
                            url
                    }

                    /*
                     * Dopo il caricamento proviamo
                     * automaticamente una volta.
                     */

                    handler.postDelayed(
                        {

                            if (
                                !importing &&
                                currentSharedUrl.isNotBlank()
                            ) {

                                scanCurrentList()
                            }

                        },
                        1200
                    )
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
                ): Boolean {

                    return false
                }
            }
    }

    // ============================================================
    // IMPORT LINK
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
            ).find(
                sharedText
            )

        if (
            match == null
        ) {

            Toast.makeText(
                this,
                "Nessun link Google Maps trovato.",
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
                ']',
                '>'
            )

        currentSharedUrl =
            url

        showStatus(
            "Apertura della lista Google Maps…"
        )

        progress.visibility =
            ProgressBar.VISIBLE

        webView.loadUrl(
            url
        )
    }

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(
            intent
        )

        setIntent(
            intent
        )

        handleIntent(
            intent
        )
    }

    // ============================================================
    // SCANSIONE
    // ============================================================

    private fun scanCurrentList() {

        if (
            importing
        ) {
            return
        }

        if (
            currentSharedUrl.isBlank()
        ) {

            showStatus(
                "Nessun link da scansionare."
            )

            return
        }

        importing =
            true

        progress.visibility =
            ProgressBar.VISIBLE

        scanButton.isEnabled =
            false

        showStatus(
            "🔎 Scansione della lista Google Maps…"
        )

        val url =
            currentSharedUrl

        executor.execute {

            try {

                val result =
                    fetchGoogleList(
                        url
                    )

                runOnUiThread {

                    importing =
                        false

                    scanButton.isEnabled =
                        true

                    progress.visibility =
                        ProgressBar.GONE

                    if (
                        result.isEmpty()
                    ) {

                        showStatus(
                            "Google Maps non ha restituito luoghi."
                        )

                        Toast.makeText(
                            this,
                            "Nessun luogo trovato.",
                            Toast.LENGTH_LONG
                        ).show()

                    } else {

                        places.clear()

                        places.addAll(
                            result
                        )

                        savePlaces()

                        showStatus(
                            "✅ ${places.size} luoghi importati."
                        )

                        showPlaces()

                        Toast.makeText(
                            this,
                            "${places.size} luoghi importati.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (
                e: Exception
            ) {

                runOnUiThread {

                    importing =
                        false

                    scanButton.isEnabled =
                        true

                    progress.visibility =
                        ProgressBar.GONE

                    showStatus(
                        "Errore: ${e.message}"
                    )

                    Toast.makeText(
                        this,
                        "Errore importazione: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ============================================================
    // DOWNLOAD PAGINA GOOGLE
    // ============================================================

    private fun fetchGoogleList(
        sharedUrl: String
    ): ArrayList<Place> {

        /*
         * Primo passaggio:
         *
         * maps.app.goo.gl/...
         *
         * viene seguito fino alla pagina Google Maps.
         *
         * Google normalmente inserisce nella pagina un
         * preload link verso:
         *
         * /maps/preview/entitylist/getlist
         *
         * Questo è il punto importante:
         * NON costruiamo più il pb da soli.
         */

        val html =
            httpGet(
                sharedUrl
            )

        // --------------------------------------------------------
        // CERCA GETLIST NELL'HTML
        // --------------------------------------------------------

        val getListUrl =
            extractGetListUrl(
                html
            )

        if (
            getListUrl.isNullOrBlank()
        ) {

            throw Exception(
                "Non ho trovato il preload entitylist/getlist nella pagina Google Maps."
            )
        }

        // --------------------------------------------------------
        // RICHIESTA REALE
        // --------------------------------------------------------

        val raw =
            httpGet(
                getListUrl
            )

        if (
            raw.isBlank()
        ) {

            throw Exception(
                "Risposta getlist vuota."
            )
        }

        // --------------------------------------------------------
        // PARSING
        // --------------------------------------------------------

        return parseGoogleResponse(
            raw
        )
    }

    // ============================================================
    // HTTP GET
    // ============================================================

    private fun httpGet(
        requestUrl: String
    ): String {

        var connection:
            HttpURLConnection? = null

        try {

            val url =
                URL(
                    requestUrl
                )

            connection =
                url.openConnection()
                        as HttpURLConnection

            connection.connectTimeout =
                15000

            connection.readTimeout =
                20000

            connection.instanceFollowRedirects =
                true

            connection.requestMethod =
                "GET"

            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/151.0.0.0 " +
                    "Mobile Safari/537.36"
            )

            connection.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml," +
                    "application/json;q=0.9,*/*;q=0.8"
            )

            connection.setRequestProperty(
                "Accept-Language",
                "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7"
            )

            val status =
                connection.responseCode

            if (
                status !in 200..399
            ) {

                throw Exception(
                    "HTTP $status"
                )
            }

            val input =
                BufferedReader(
                    InputStreamReader(
                        connection.inputStream,
                        Charsets.UTF_8
                    )
                )

            val builder =
                StringBuilder()

            while (true) {

                val line =
                    input.readLine()
                        ?: break

                builder.append(
                    line
                )

                builder.append(
                    '\n'
                )
            }

            input.close()

            return builder.toString()

        } finally {

            connection?.disconnect()
        }
    }

    // ============================================================
    // ESTRAZIONE URL GETLIST
    // ============================================================

    private fun extractGetListUrl(
        html: String
    ): String? {

        /*
         * Google può rappresentare l'URL:
         *
         * https://www.google.com/maps/preview/
         * entitylist/getlist?... 
         *
         * oppure inserirlo dentro un attributo HTML
         * con escaping.
         */

        val decoded =
            html
                .replace(
                    "\\u003d",
                    "="
                )
                .replace(
                    "\\u0026",
                    "&"
                )
                .replace(
                    "\\u002F",
                    "/"
                )
                .replace(
                    "\\/",
                    "/"
                )
                .replace(
                    "&amp;",
                    "&"
                )

        // --------------------------------------------------------
        // CASO 1 — URL COMPLETO
        // --------------------------------------------------------

        val fullRegex =
            Regex(
                """https?://[^"'<>\\\s]+/maps/preview/entitylist/getlist[^"'<>\\\s]*"""
            )

        val full =
            fullRegex
                .find(
                    decoded
                )
                ?.value

        if (
            !full.isNullOrBlank()
        ) {

            return cleanExtractedUrl(
                full
            )
        }

        // --------------------------------------------------------
        // CASO 2 — URL HTML-ESCAPED
        // --------------------------------------------------------

        val relativeRegex =
            Regex(
                """/maps/preview/entitylist/getlist[^"'<>\\\s]*"""
            )

        val relative =
            relativeRegex
                .find(
                    decoded
                )
                ?.value

        if (
            !relative.isNullOrBlank()
        ) {

            return cleanExtractedUrl(
                "https://www.google.com" +
                    relative
            )
        }

        // --------------------------------------------------------
        // CASO 3 — GOOGLE MAPS DOMAIN
        // --------------------------------------------------------

        val mapsRegex =
            Regex(
                """https?://www\.google\.com/maps/preview/entitylist/getlist\?[^"'<>]+"""
            )

        val maps =
            mapsRegex
                .find(
                    decoded
                )
                ?.value

        if (
            !maps.isNullOrBlank()
        ) {

            return cleanExtractedUrl(
                maps
            )
        }

        return null
    }

    private fun cleanExtractedUrl(
        value: String
    ): String {

        return value
            .replace(
                "\\u003F",
                "?"
            )
            .replace(
                "\\u003f",
                "?"
            )
            .replace(
                "\\u0026",
                "&"
            )
            .replace(
                "\\u003D",
                "="
            )
            .replace(
                "\\u003d",
                "="
            )
            .replace(
                "&amp;",
                "&"
            )
            .trimEnd(
                '"',
                '\'',
                '>',
                '<',
                '\\'
            )
    }

    // ============================================================
    // PARSER GETLIST
    // ============================================================

    private fun parseGoogleResponse(
        rawInput: String
    ): ArrayList<Place> {

        var raw =
            rawInput.trim()

        // --------------------------------------------------------
        // RIMUOVE XSSI
        // --------------------------------------------------------

        if (
            raw.startsWith(
                ")]}'"
            )
        ) {

            raw =
                raw.substring(
                    4
                )

            if (
                raw.startsWith(
                    "\n"
                )
            ) {

                raw =
                    raw.substring(
                        1
                    )
            }
        }

        val root =
            try {

                JSONArray(
                    raw
                )

            } catch (
                e: Exception
            ) {

                throw Exception(
                    "Risposta Google non è JSON valido."
                )
            }

        val result =
            ArrayList<Place>()

        val seen =
            HashSet<String>()

        /*
         * Struttura verificata del getlist:
         *
         * root[0][8]
         *
         * contiene gli elementi della lista.
         *
         * place[1][5][2] = latitude
         * place[1][5][3] = longitude
         * place[2]       = nome
         * place[1][4]    = indirizzo
         *
         * Manteniamo comunque anche un walker di sicurezza.
         */

        parseKnownListStructure(
            root,
            result,
            seen
        )

        /*
         * Se la struttura cambia leggermente,
         * proviamo comunque il parser generico.
         */

        if (
            result.isEmpty()
        ) {

            walkJson(
                root,
                result,
                seen
            )
        }

        return result
    }

    // ============================================================
    // PARSER STRUTTURA CONOSCIUTA
    // ============================================================

    private fun parseKnownListStructure(
        root: JSONArray,
        result: ArrayList<Place>,
        seen: HashSet<String>
    ) {

        try {

            val listBlock =
                root.optJSONArray(
                    0
                )

            if (
                listBlock == null
            ) {
                return
            }

            val entries =
                listBlock.optJSONArray(
                    8
                )

            if (
                entries == null
            ) {
                return
            }

            for (
                i in 0 until entries.length()
            ) {

                val place =
                    entries.optJSONArray(
                        i
                    )
                        ?: continue

                parsePlaceArray(
                    place,
                    result,
                    seen
                )
            }

        } catch (
            _: Exception
        ) {
            // fallback generico
        }
    }

    // ============================================================
    // PARSER PLACE
    // ============================================================

    private fun parsePlaceArray(
        place: JSONArray,
        result: ArrayList<Place>,
        seen: HashSet<String>
    ) {

        try {

            val name =
                place.optString(
                    2,
                    ""
                ).trim()

            if (
                name.isBlank()
            ) {
                return
            }

            val core =
                place.optJSONArray(
                    1
                )
                    ?: return

            val address =
                core.optString(
                    4,
                    ""
                ).trim()

            val coords =
                core.optJSONArray(
                    5
                )
                    ?: return

            val lat =
                coords.optDouble(
                    2,
                    Double.NaN
                )

            val lng =
                coords.optDouble(
                    3,
                    Double.NaN
                )

            if (
                lat.isNaN() ||
                lng.isNaN()
            ) {
                return
            }

            if (
                lat < -90 ||
                lat > 90 ||
                lng < -180 ||
                lng > 180
            ) {
                return
            }

            val key =
                "$name|$lat|$lng"

            if (
                seen.contains(
                    key
                )
            ) {
                return
            }

            seen.add(
                key
            )

            result.add(
                Place(
                    name =
                        name,

                    address =
                        address,

                    lat =
                        lat,

                    lng =
                        lng
                )
            )

        } catch (
            _: Exception
        ) {
            // ignora elemento non valido
        }
    }

    // ============================================================
    // WALKER DI SICUREZZA
    // ============================================================

    private fun walkJson(
        value: Any?,
        result: ArrayList<Place>,
        seen: HashSet<String>
    ) {

        when (value) {

            is JSONArray -> {

                /*
                 * Proviamo a interpretare direttamente
                 * questa array come un place.
                 */

                parsePlaceArray(
                    value,
                    result,
                    seen
                )

                for (
                    i in 0 until value.length()
                ) {

                    walkJson(
                        value.opt(i),
                        result,
                        seen
                    )
                }
            }

            is JSONObject -> {

                val keys =
                    value.keys()

                while (
                    keys.hasNext()
                ) {

                    val key =
                        keys.next()

                    walkJson(
                        value.opt(key),
                        result,
                        seen
                    )
                }
            }
        }
    }

    // ============================================================
    // VISUALIZZAZIONE
    // ============================================================

    private fun showPlaces() {

        output.removeAllViews()

        val title =
            TextView(this).apply {

                text =
                    if (
                        places.isEmpty()
                    ) {

                        "📍 TravelPins"

                    } else {

                        "📍 ${places.size} luoghi"
                    }

                textSize =
                    27f

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

        output.addView(
            title
        )

        if (
            places.isEmpty()
        ) {

            val empty =
                TextView(this).apply {

                    text =
                        "Condividi una lista Google Maps con TravelPins.\n\n" +
                        "Dopo l'apertura puoi anche premere 🔎 SCANSIONA."

                    textSize =
                        17f

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

            output.addView(
                empty
            )

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

    // ============================================================
    // CARD LUOGO
    // ============================================================

    private fun addPlaceCard(
        index: Int,
        place: Place
    ) {

        val category =
            categories.find {
                it.id ==
                    place.categoryId
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

                textSize =
                    18f

                setTextColor(
                    Color.BLACK
                )
            }

        card.addView(
            title
        )

        if (
            place.address.isNotBlank()
        ) {

            val address =
                TextView(this).apply {

                    text =
                        place.address

                    textSize =
                        14f

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

            card.addView(
                address
            )
        }

        val categoryText =
            TextView(this).apply {

                text =
                    if (
                        category == null
                    ) {

                        "⚪ Nessuna categoria"

                    } else {

                        "${category.icon} ${category.name}"
                    }

                textSize =
                    15f

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

                    chooseCategory(
                        place
                    )
                }
            }

        card.addView(
            categoryText
        )

        card.setOnClickListener {

            chooseCategory(
                place
            )
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

        if (
            categories.isEmpty()
        ) {

            createCategory()

            return
        }

        val names =
            categories.map {
                "${it.icon} ${it.name}"
            }.toTypedArray()

        AlertDialog.Builder(this)

            .setTitle(
                "Categoria"
            )

            .setItems(
                names
            ) {
                _,
                which ->

                place.categoryId =
                    categories[
                        which
                    ].id

                savePlaces()

                showPlaces()
            }

            .setNegativeButton(
                "Nessuna categoria"
            ) {
                _,
                _ ->

                place.categoryId =
                    ""

                savePlaces()

                showPlaces()
            }

            .show()
    }

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

                    textSize =
                        25f
                }

            val name =
                TextView(this).apply {

                    text =
                        category.name

                    textSize =
                        18f

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

            row.addView(
                icon
            )

            row.addView(
                name,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            layout.addView(
                row
            )
        }

        val addButton =
            Button(this).apply {

                text =
                    "＋ Nuova categoria"

                setOnClickListener {
                    createCategory()
                }
            }

        layout.addView(
            addButton
        )

        AlertDialog.Builder(this)

            .setTitle(
                "Categorie"
            )

            .setView(
                layout
            )

            .setPositiveButton(
                "Chiudi",
                null
            )

            .show()
    }

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

                setSingleLine(
                    true
                )
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

        layout.addView(
            name
        )

        layout.addView(
            iconSpinner
        )

        AlertDialog.Builder(this)

            .setTitle(
                "Nuova categoria"
            )

            .setView(
                layout
            )

            .setPositiveButton(
                "Crea"
            ) {
                _,
                _ ->

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
                        iconSpinner
                            .selectedItemPosition
                    ]

                categories.add(
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
                )

                saveCategories()

                Toast.makeText(
                    this,
                    "Categoria creata.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            .setNegativeButton(
                "Annulla",
                null
            )

            .show()
    }

    // ============================================================
    // STORAGE CATEGORIE
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

            array.put(
                obj
            )
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

        if (
            raw.isNullOrBlank()
        ) {

            categories.add(
                Category(
                    "default_1",
                    "Da vedere",
                    Color.rgb(
                        30,
                        100,
                        200
                    ),
                    "📍"
                )
            )

            categories.add(
                Category(
                    "default_2",
                    "Ristoranti",
                    Color.rgb(
                        220,
                        80,
                        50
                    ),
                    "🍴"
                )
            )

            categories.add(
                Category(
                    "default_3",
                    "Natura",
                    Color.rgb(
                        40,
                        150,
                        70
                    ),
                    "🌿"
                )
            )

            saveCategories()

            return
        }

        try {

            val array =
                JSONArray(
                    raw
                )

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(
                        i
                    )

                categories.add(
                    Category(
                        obj.getString(
                            "id"
                        ),
                        obj.getString(
                            "name"
                        ),
                        obj.getInt(
                            "color"
                        ),
                        obj.getString(
                            "icon"
                        )
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
    // STORAGE PLACES
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

            array.put(
                obj
            )
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

        if (
            raw.isNullOrBlank()
        ) {
            return
        }

        try {

            val array =
                JSONArray(
                    raw
                )

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(
                        i
                    )

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
    // BACK
    // ============================================================

    @Suppress(
        "DEPRECATION"
    )
    override fun onBackPressed() {

        if (
            webView.canGoBack()
        ) {

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

        executor.shutdownNow()

        webView.stopLoading()
        webView.destroy()

        super.onDestroy()
    }
}
