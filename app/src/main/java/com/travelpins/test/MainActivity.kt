package com.travelpins.test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var output: LinearLayout
    private lateinit var progress: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private val log = ConcurrentLinkedQueue<String>()

    private val places = ArrayList<Place>()
    private val categories = ArrayList<Category>()

    private var importing = false

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
    // AVVIO
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadCategories()
        createInterface()
        createWebView()

        handleIntent(intent)
    }

    // ============================================================
    // INTERFACCIA PRINCIPALE
    // ============================================================

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.rgb(35, 35, 35))
        }

        val title = TextView(this).apply {
            text = "📍 TravelPins"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER_VERTICAL
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

        progress = ProgressBar(this).apply {
            visibility = ProgressBar.GONE
        }

        root.addView(toolbar)
        root.addView(progress)

        val scroll = ScrollView(this)

        output = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 40)
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

        showWelcome()
    }

    // ============================================================
    // SCHERMATA INIZIALE
    // ============================================================

    private fun showWelcome() {

        output.removeAllViews()

        val title = TextView(this).apply {
            text = "Le tue destinazioni"
            textSize = 28f
            setTextColor(Color.BLACK)
            setPadding(0, 10, 0, 20)
        }

        output.addView(title)

        val info = TextView(this).apply {
            text =
                "Condividi una lista di Google Maps con TravelPins.\n\n" +
                "I luoghi verranno importati automaticamente e potrai " +
                "organizzarli nelle tue categorie."
            textSize = 17f
            setTextColor(Color.DKGRAY)
        }

        output.addView(info)

        if (places.isEmpty()) {

            val empty = TextView(this).apply {
                text = "\n\n📍 Nessun luogo importato."
                textSize = 18f
                setTextColor(Color.GRAY)
            }

            output.addView(empty)

        } else {

            showPlaces()
        }
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    private fun createWebView() {

        webView = WebView(this)

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

        CookieManager.getInstance().setAcceptCookie(true)

        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(
            TravelPinsBridge(),
            "TravelPins"
        )

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url = request.url.toString()

                    if (url.startsWith("intent://")) {

                        handleGoogleIntent(url)

                        return true
                    }

                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {

                    return null
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    injectNetworkHook()

                    if (isGoogleListUrl(url)) {

                        if (!importing) {

                            importing = true

                            handler.postDelayed({

                                scanGoogleList()

                            }, 1200)
                        }
                    }
                }
            }
    }

    // ============================================================
    // JAVASCRIPT BRIDGE
    // ============================================================

    inner class TravelPinsBridge {

        @JavascriptInterface
        fun log(message: String?) {

            if (!message.isNullOrBlank()) {
                addLog(message)
            }
        }

        @JavascriptInterface
        fun importPlaces(json: String) {

            runOnUiThread {

                try {

                    val array = JSONArray(json)

                    places.clear()

                    for (i in 0 until array.length()) {

                        val item = array.getJSONObject(i)

                        places.add(
                            Place(
                                name =
                                    item.optString("name"),
                                address =
                                    item.optString("address"),
                                lat =
                                    item.optDouble("lat"),
                                lng =
                                    item.optDouble("lng")
                            )
                        )
                    }

                    progress.visibility =
                        ProgressBar.GONE

                    webView.visibility =
                        WebView.GONE

                    importing = false

                    showPlaces()

                } catch (e: Exception) {

                    importing = false

                    progress.visibility =
                        ProgressBar.GONE

                    Toast.makeText(
                        this@MainActivity,
                        "Errore importazione: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ============================================================
    // RICONOSCIMENTO LISTA
    // ============================================================

    private fun isGoogleListUrl(
        url: String
    ): Boolean {

        return (
            url.contains(
                "/local/userlists/list/",
                true
            )
        ) ||
        (
            url.contains(
                "/maps/@/data=",
                true
            ) &&
            url.contains(
                "!11m2!2s",
                true
            )
        )
    }

    // ============================================================
    // IMPORTAZIONE
    // ============================================================

    private fun scanGoogleList() {

        runOnUiThread {

            progress.visibility =
                ProgressBar.VISIBLE
        }

        val javascript = """

            (async function() {

                try {

                    var currentUrl =
                        window.location.href;

                    var listId = '';

                    var match =
                        currentUrl.match(
                            /!11m2!2s([^!&]+)/i
                        );

                    if (match) {
                        listId = match[1];
                    }

                    if (!listId) {

                        match =
                            currentUrl.match(
                                /\/local\/userlists\/list\/([^?\/]+)/i
                            );

                        if (match) {
                            listId = match[1];
                        }
                    }

                    if (!listId) {

                        TravelPins.log(
                            'LIST ID NON TROVATO'
                        );

                        return;
                    }

                    var pb =
                        '!1m4' +
                        '!1s' +
                        encodeURIComponent(listId) +
                        '!2e1' +
                        '!3m1!1e1' +
                        '!2e2' +
                        '!3e3' +
                        '!4i500' +
                        '!8i3' +
                        '!16b1';

                    var endpoint =
                        '/maps/preview/entitylist/getlist' +
                        '?authuser=0' +
                        '&hl=it' +
                        '&gl=it' +
                        '&pb=' +
                        pb;

                    var response =
                        await fetch(
                            endpoint,
                            {
                                method: 'GET',
                                credentials: 'include',
                                cache: 'no-store'
                            }
                        );

                    var raw =
                        await response.text();

                    if (!raw) {
                        return;
                    }

                    if (
                        raw.indexOf(")]}'") === 0
                    ) {

                        raw = raw.substring(4);

                        if (
                            raw.charAt(0) === '\\n'
                        ) {
                            raw = raw.substring(1);
                        }
                    }

                    var data =
                        JSON.parse(raw);

                    var places = [];

                    function number(v) {
                        return typeof v === 'number' &&
                               isFinite(v);
                    }

                    function coords(a,b) {

                        return number(a) &&
                               number(b) &&
                               Math.abs(a) <= 90 &&
                               Math.abs(b) <= 180;
                    }

                    function clean(v) {

                        if (
                            typeof v !== 'string'
                        ) {
                            return '';
                        }

                        return v
                            .replace(/\\s+/g,' ')
                            .trim();
                    }

                    function useful(v) {

                        v = clean(v);

                        return (
                            v.length >= 2 &&
                            v.length <= 250 &&
                            v.indexOf('http://') !== 0 &&
                            v.indexOf('https://') !== 0
                        );
                    }

                    function parsePlace(x) {

                        try {

                            if (
                                !Array.isArray(x) ||
                                x.length < 3
                            ) {
                                return;
                            }

                            var name =
                                clean(x[2]);

                            if (!useful(name)) {
                                return;
                            }

                            var envelope =
                                x[1];

                            if (
                                !Array.isArray(envelope)
                            ) {
                                return;
                            }

                            var block =
                                envelope[5];

                            if (
                                !Array.isArray(block)
                            ) {
                                return;
                            }

                            var lat =
                                block[2];

                            var lng =
                                block[3];

                            if (!coords(lat,lng)) {
                                return;
                            }

                            var address = '';

                            if (
                                typeof x[3] === 'string'
                            ) {
                                address =
                                    clean(x[3]);
                            }

                            places.push({
                                name:name,
                                address:address,
                                lat:lat,
                                lng:lng
                            });

                        } catch(e) {}
                    }

                    function walk(node) {

                        if (!node) {
                            return;
                        }

                        if (
                            Array.isArray(node)
                        ) {

                            parsePlace(node);

                            for (
                                var i=0;
                                i<node.length;
                                i++
                            ) {
                                walk(node[i]);
                            }

                        } else if (
                            typeof node === 'object'
                        ) {

                            for (
                                var key in node
                            ) {

                                walk(node[key]);
                            }
                        }
                    }

                    walk(data);

                    var unique = [];
                    var seen = {};

                    for (
                        var i=0;
                        i<places.length;
                        i++
                    ) {

                        var p =
                            places[i];

                        var key =
                            p.name +
                            '|' +
                            p.lat +
                            '|' +
                            p.lng;

                        if (!seen[key]) {

                            seen[key] = true;

                            unique.push(p);
                        }
                    }

                    TravelPins.log(
                        'LUOGHI IMPORTATI: ' +
                        unique.length
                    );

                    TravelPins.importPlaces(
                        JSON.stringify(unique)
                    );

                } catch(e) {

                    TravelPins.log(
                        'ERRORE: ' +
                        e.message
                    );
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript,
            null
        )
    }

    // ============================================================
    // HOOK
    // ============================================================

    private fun injectNetworkHook() {

        webView.evaluateJavascript(
            """
            (function(){

                if(window.__tp_hooked)
                    return;

                window.__tp_hooked=true;

                var f=window.fetch;

                window.fetch=function(){

                    return f.apply(
                        this,
                        arguments
                    );
                };

            })();
            """.trimIndent(),
            null
        )
    }

    // ============================================================
    // VISUALIZZAZIONE LUOGHI
    // ============================================================

    private fun showPlaces() {

        output.removeAllViews()

        val title = TextView(this).apply {

            text =
                "📍 Luoghi importati: ${places.size}"

            textSize = 25f
            setTextColor(Color.BLACK)
            setPadding(0,10,0,20)
        }

        output.addView(title)

        places.forEachIndexed { index, place ->

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
                    20,
                    18,
                    20,
                    18
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
                setTextColor(Color.BLACK)
            }

        card.addView(title)

        if (place.address.isNotBlank()) {

            val address =
                TextView(this).apply {

                    text =
                        place.address

                    textSize = 14f
                    setTextColor(Color.DKGRAY)
                    setPadding(0,8,0,8)
                }

            card.addView(address)
        }

        val categoryText =
            TextView(this).apply {

                text =
                    if (category == null)
                        "⚪ Nessuna categoria"
                    else
                        "${category.icon} ${category.name}"

                textSize = 15f

                if (category != null) {
                    setTextColor(category.color)
                } else {
                    setTextColor(Color.GRAY)
                }

                setPadding(0,6,0,6)

                setOnClickListener {

                    chooseCategory(place)
                }
            }

        card.addView(categoryText)

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
            15
        )

        output.addView(
            card,
            params
        )
    }

    // ============================================================
    // CATEGORIE
    // ============================================================

    private fun showCategories() {

        val dialog =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    40,
                    20,
                    40,
                    10
                )
            }

        categories.forEach { category ->

            val row =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    setPadding(
                        0,
                        15,
                        0,
                        15
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
                        20,
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

            dialog.addView(row)
        }

        val add =
            Button(this).apply {

                text =
                    "＋ Nuova categoria"

                setOnClickListener {

                    createCategory()

                }
            }

        dialog.addView(add)

        AlertDialogBuilder(
            "Categorie",
            dialog
        )
    }

    private fun chooseCategory(
        place: Place
    ) {

        if (categories.isEmpty()) {

            createCategory()

            return
        }

        val names =
            categories.map {
                "${it.icon} ${it.name}"
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(
                "Categoria per ${place.name}"
            )
            .setItems(names) { _, which ->

                place.categoryId =
                    categories[which].id

                savePlaces()

                showPlaces()
            }
            .setNegativeButton(
                "Nessuna",
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
                    40,
                    10,
                    40,
                    10
                )
            }

        val name =
            EditText(this).apply {

                hint =
                    "Nome categoria"
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
        layout.addView(iconSpinner)

        AlertDialog.Builder(this)
            .setTitle(
                "Nuova categoria"
            )
            .setView(layout)
            .setPositiveButton(
                "Crea"
            ) { _, _ ->

                val categoryName =
                    name.text.toString().trim()

                if (categoryName.isBlank()) {
                    return@setPositiveButton
                }

                val newCategory =
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
                            icons[
                                iconSpinner
                                    .selectedItemPosition
                            ]
                    )

                categories.add(
                    newCategory
                )

                saveCategories()

                Toast.makeText(
                    this,
                    "Categoria creata",
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
    // DIALOG CATEGORIE
    // ============================================================

    private fun AlertDialogBuilder(
        title: String,
        view: LinearLayout
    ) {

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(
                "Chiudi",
                null
            )
            .show()
    }

    // ============================================================
    // SALVATAGGIO CATEGORIE
    // ============================================================

    private fun saveCategories() {

        val array = JSONArray()

        categories.forEach {

            val obj =
                JSONObject()

            obj.put(
                "id",
                it.id
            )

            obj.put(
                "name",
                it.name
            )

            obj.put(
                "color",
                it.color
            )

            obj.put(
                "icon",
                it.icon
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
                    "1",
                    "Da vedere",
                    Color.rgb(30,100,200),
                    "📍"
                )
            )

            categories.add(
                Category(
                    "2",
                    "Ristoranti",
                    Color.rgb(220,80,50),
                    "🍴"
                )
            )

            categories.add(
                Category(
                    "3",
                    "Natura",
                    Color.rgb(40,150,70),
                    "🌿"
                )
            )

            saveCategories()

            return
        }

        try {

            val array =
                JSONArray(raw)

            for (i in 0 until array.length()) {

                val obj =
                    array.getJSONObject(i)

                categories.add(
                    Category(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.getInt("color"),
                        obj.getString("icon")
                    )
                )
            }

        } catch (e: Exception) {}
    }

    // ============================================================
    // SALVATAGGIO LUOGHI
    // ============================================================

    private fun savePlaces() {

        val array =
            JSONArray()

        places.forEach {

            val obj =
                JSONObject()

            obj.put(
                "name",
                it.name
            )

            obj.put(
                "address",
                it.address
            )

            obj.put(
                "lat",
                it.lat
            )

            obj.put(
                "lng",
                it.lng
            )

            obj.put(
                "category",
                it.categoryId
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

            if (!fallback.isNullOrBlank()) {

                webView.loadUrl(
                    fallback
                )
            }

        } catch (e: Exception) {}
    }

    // ============================================================
    // CONDIVIDI
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

        if (sharedText.isNullOrBlank()) {
            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(sharedText)

        if (match == null) {
            return
        }

        val url =
            match.value

        webView.visibility =
            WebView.VISIBLE

        webView.loadUrl(url)
    }

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

        log.add(message)

        while (log.size > 50) {
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

        handler.removeCallbacksAndMessages(null)

        webView.stopLoading()

        webView.removeJavascriptInterface(
            "TravelPins"
        )

        webView.destroy()

        super.onDestroy()
    }
}
