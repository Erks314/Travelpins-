package com.travelpins.test.scraper

object GoogleMapsScraperScript {

    fun isGoogleListUrl(url: String): Boolean {
        return url.contains("/local/userlists/list/") ||
            url.contains("/maps/@/data=") ||
            url.contains("!11m2!2s")
    }

    val NETWORK_HOOK_SCRIPT = """

(function() {
if (window.__travelpins_hooked) {
return 'ALREADY_INSTALLED';
}
window.__travelpins_hooked = true;

/*
 * ============================================================
 * FILTRO RUMORE
 * ============================================================
 */
function isNoiseUrl(url) {
    return url.indexOf('/maps/vt') !== -1 ||
        url.indexOf('/maps/preview/log204') !== -1;
}

/*
 * ============================================================
 * URL DI INTERESSE: /maps/preview/place
 * ============================================================
 *
 * Questo endpoint porta i dettagli del singolo luogo:
 * foto, rating, recensioni, descrizione, sito web, tipi.
 * La risposta viene parsata e inoltrata al bridge Kotlin.
 */
function isPlaceDetailUrl(url) {
    return url.indexOf('/maps/preview/place') !== -1;
}

/*
 * ============================================================
 * PARSER RISPOSTA /maps/preview/place
 * ============================================================
 *
 * Estrae dalla risposta (formato array annidati di Google):
 * - nome, tipi, sito web, descrizione
 * - rating e numero recensioni
 * - foto (URL reali lh3.googleusercontent.com)
 * - recensioni (se presenti nel payload)
 */
function parsePlaceDetails(text) {

    var cleaned = text;

    if (cleaned.indexOf(")]}'") === 0) {
        cleaned = cleaned.substring(4);
        if (cleaned.charAt(0) === '\n') {
            cleaned = cleaned.substring(1);
        }
    }

    var data;
    try {
        data = JSON.parse(cleaned);
    } catch(e) {
        return null;
    }

    if (!Array.isArray(data)) {
        return null;
    }

    var out = {
        name: '',
        rating: null,
        reviewCount: null,
        description: '',
        websiteUrl: '',
        types: [],
        photos: [],
        reviews: []
    };

    /* ---------- BLOCCO PRINCIPALE ----------
     * E' l'array che contiene il cid hex
     * "0x4843358a982620cb:0xb50e4529f57483f3"
     */
    var main = null;
    var cidIndex = -1;

    function findMain(node, depth) {
        if (main || !Array.isArray(node) || depth > 6) {
            return;
        }
        for (var i = 0; i < node.length; i++) {
            var v = node[i];
            if (typeof v === 'string' &&
                /^0x[0-9a-f]+:0x[0-9a-f]+$/i.test(v)) {
                main = node;
                cidIndex = i;
                return;
            }
        }
        for (var j = 0; j < node.length; j++) {
            findMain(node[j], depth + 1);
            if (main) return;
        }
    }

    findMain(data, 0);

    if (main) {

        /* nome: subito dopo il cid hex */
        if (typeof main[cidIndex + 1] === 'string') {
            out.name = main[cidIndex + 1];
        }

        /* tipi: primo array di stringhe brevi dopo il nome */
        for (var t = cidIndex + 2; t < Math.min(main.length, cidIndex + 6); t++) {
            var cand = main[t];
            if (Array.isArray(cand) && cand.length > 0) {
                var allStr = true;
                for (var k = 0; k < cand.length; k++) {
                    if (typeof cand[k] !== 'string' ||
                        cand[k].length > 60) {
                        allStr = false;
                    }
                }
                if (allStr) {
                    out.types = cand;
                    break;
                }
            }
        }

        /* sito web: primo array il cui [0] e' un URL esterno */
        for (var w = 0; w < main.length; w++) {
            var wb = main[w];
            if (Array.isArray(wb) &&
                typeof wb[0] === 'string' &&
                wb[0].indexOf('http') === 0 &&
                wb[0].indexOf('googleusercontent') === -1 &&
                wb[0].indexOf('google.com') === -1) {
                out.websiteUrl = wb[0];
                break;
            }
        }

        /* rating + conteggio recensioni:
         * array che contiene una stringa "recensioni/reviews"
         */
        function containsReviewText(arr) {
            for (var i = 0; i < arr.length; i++) {
                var v = arr[i];
                if (typeof v === 'string' &&
                    /recension|review/i.test(v)) {
                    return true;
                }
                if (Array.isArray(v) && v.length <= 6) {
                    for (var j = 0; j < v.length; j++) {
                        if (typeof v[j] === 'string' &&
                            /recension|review/i.test(v[j])) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        for (var r = 0; r < main.length; r++) {
            var blk = main[r];
            if (Array.isArray(blk) && containsReviewText(blk)) {
                for (var n = 0; n < blk.length; n++) {
                    var num = blk[n];
                    if (typeof num === 'number') {
                        if (num > 0 && num <= 5 && out.rating === null) {
                            out.rating = num;
                        } else if (num > 5 && out.reviewCount === null) {
                            out.reviewCount = Math.round(num);
                        }
                    }
                }
                if (out.reviewCount === null) {
                    var mTxt = JSON.stringify(blk)
                        .match(/([\d.,]+)\s*recension/i);
                    if (mTxt) {
                        out.reviewCount = parseInt(
                            mTxt[1].replace(/\./g, '').replace(/,/g, ''),
                            10
                        );
                    }
                }
                break;
            }
        }

        /* descrizione: coppia [breve, estesa] */
        function findDescription(node, depth) {
            if (out.description || !Array.isArray(node) || depth > 5) {
                return;
            }
            if (Array.isArray(node[0]) && Array.isArray(node[1])) {
                var s0 = node[0][1];
                var s1 = node[1][1];
                if (typeof s1 === 'string' && s1.length > 30) {
                    out.description = s1;
                    return;
                }
                if (typeof s0 === 'string' && s0.length > 30) {
                    out.description = s0;
                    return;
                }
            }
            for (var i = 0; i < node.length; i++) {
                findDescription(node[i], depth + 1);
                if (out.description) return;
            }
        }
        findDescription(main, 0);
    }

    /* ---------- FOTO ----------
     * Nodo foto: [0] = key, [6] = [url, caption, [w,h], ...]
     */
    var photoSeen = {};
    var realPhotos = [];
    var svPhotos = [];

    function walkPhotos(node) {
        if (!node || !Array.isArray(node)) {
            return;
        }

        if (typeof node[0] === 'string' &&
            node[0].length > 8 &&
            Array.isArray(node[6])) {

            var meta = node[6];

            if (typeof meta[0] === 'string' &&
                meta[0].indexOf('https://lh3.googleusercontent.com/') === 0) {

                var key = node[0];

                if (!photoSeen[key]) {
                    photoSeen[key] = true;

                    var pw = null;
                    var ph = null;
                    if (Array.isArray(meta[2]) &&
                        typeof meta[2][0] === 'number' &&
                        typeof meta[2][1] === 'number') {
                        pw = meta[2][0];
                        ph = meta[2][1];
                    }

                    var isSv = false;
                    for (var s = 0; s < node.length; s++) {
                        if (node[s] === 'Street View') {
                            isSv = true;
                        }
                    }

                    var base = meta[0];
                    var cut = base.indexOf('=w');
                    if (cut === -1) {
                        cut = base.indexOf('=s');
                    }
                    if (cut > 0) {
                        base = base.substring(0, cut);
                    }

                    var entry = {
                        key: key,
                        url: base,
                        w: pw,
                        h: ph
                    };

                    if (isSv) {
                        svPhotos.push(entry);
                    } else {
                        realPhotos.push(entry);
                    }
                }
            }
        }

        for (var i = 0; i < node.length; i++) {
            walkPhotos(node[i]);
        }
    }

    walkPhotos(data);

    out.photos = realPhotos
        .concat(svPhotos)
        .slice(0, 20);

    /* ---------- RECENSIONI ----------
     * Euristica: array che contiene insieme
     * - testo lungo (>= 60 caratteri)
     * - stelle intere 1-5
     * - eventuale autore / foto profilo / data
     */
    var reviewSeen = {};

    function walkReviews(node) {
        if (!node || !Array.isArray(node)) {
            return;
        }

        var text = null;
        var rating = null;
        var author = null;
        var photo = null;
        var time = null;

        for (var i = 0; i < node.length; i++) {
            var v = node[i];

            if (typeof v === 'string') {

                if (v.indexOf('https://lh3.googleusercontent.com/a/') === 0) {
                    if (!photo) photo = v;
                }
                else if (!text &&
                    v.length >= 60 &&
                    v.indexOf('http') !== 0 &&
                    v.indexOf('recensioni') === -1 &&
                    /\s/.test(v)) {
                    text = v;
                }
                else if (!time && v.length < 40 &&
                    (/(fa|ago|hour|day|week|month|year)/i.test(v)) &&
                    (/(settiman|mese|mesi|anno|anni|giorn|ora|ore)/i.test(v) ||
                     / (fa|ago)$/i.test(v))) {
                    time = v;
                }
                else if (!author &&
                    v.length >= 3 && v.length <= 40 &&
                    /^[A-ZÀ-Ù][a-zà-ù]+([ -][A-ZÀ-Ùa-zà-ù.]+){1,3}$/.test(v)) {
                    author = v;
                }

            } else if (typeof v === 'number' &&
                v >= 1 && v <= 5 && v % 1 === 0 &&
                rating === null) {
                rating = v;
            }
        }

        if (text && rating !== null && !reviewSeen[text]) {
            reviewSeen[text] = true;
            out.reviews.push({
                author: author || '',
                photo: photo || '',
                rating: rating,
                time: time || '',
                text: text
            });
        }

        for (var j = 0; j < node.length; j++) {
            walkReviews(node[j]);
        }
    }

    walkReviews(data);

    out.reviews = out.reviews.slice(0, 30);

    return out;
}

/*
 * ============================================================
 * INOLTRO AL BRIDGE
 * ============================================================
 */
function handlePlaceResponse(url, text) {
    try {
        if (window.__travelpins_details_sent) {
            return;
        }

        var parsed = parsePlaceDetails(text);

        if (!parsed) {
            try {
                TravelPins.log('DETAILS: parse fallito ' + url);
            } catch(e) {}
            return;
        }

        window.__travelpins_details_sent = true;

        try {
            TravelPins.log(
                'DETAILS ESTRATTI: nome=' + parsed.name +
                ' foto=' + parsed.photos.length +
                ' recensioni=' + parsed.reviews.length +
                ' rating=' + parsed.rating
            );
        } catch(e) {}

        try {
            TravelPinsBridge.onPlaceDetailsExtracted(
                JSON.stringify(parsed)
            );
        } catch(e) {
            try {
                TravelPins.log('ERRORE INVIO DETAILS: ' + e.message);
            } catch(e2) {}
        }

    } catch(e) {
        try {
            TravelPins.log('ERRORE GESTIONE RISPOSTA PLACE: ' + e.message);
        } catch(e2) {}
    }
}

var originalFetch = window.fetch;
window.fetch = async function() {
    var input = arguments[0];
    var options = arguments[1] || {};
    var url = typeof input === 'string' ? input : input.url;
    var method = options.method || (typeof input !== 'string' ? input.method : 'GET') || 'GET';
    var body = options.body || '';

    if (!isNoiseUrl(url)) {
        try {
            TravelPins.network('FETCH_REQUEST', method, url, body);
        } catch(e) {}
    }

    var response = await originalFetch.apply(this, arguments);

    if (isPlaceDetailUrl(url)) {
        try {
            response
                .clone()
                .text()
                .then(function(text) {
                    handlePlaceResponse(url, text);
                })
                .catch(function(e) {
                    try {
                        TravelPins.log(
                            'ERRORE LETTURA RISPOSTA PLACE (fetch): ' + e.message
                        );
                    } catch(e2) {}
                });
        } catch(e) {}
    }

    return response;
};

var originalOpen = XMLHttpRequest.prototype.open;
var originalSend = XMLHttpRequest.prototype.send;

XMLHttpRequest.prototype.open = function(method, url) {
    this.__tp_method = method;
    this.__tp_url = url;
    return originalOpen.apply(this, arguments);
};

XMLHttpRequest.prototype.send = function(body) {
    var xhr = this;
    var url = xhr.__tp_url || '';
    var method = xhr.__tp_method || 'GET';

    if (!isNoiseUrl(url)) {
        try {
            TravelPins.network(
                'XHR_REQUEST',
                method,
                url,
                body || ''
            );
        } catch(e) {}
    }

    if (isPlaceDetailUrl(url)) {
        xhr.addEventListener('load', function() {
            try {
                handlePlaceResponse(url, xhr.responseText || '');
            } catch(e) {
                try {
                    TravelPins.log(
                        'ERRORE LETTURA RISPOSTA PLACE (xhr): ' + e.message
                    );
                } catch(e2) {}
            }
        });
    }

    return originalSend.apply(this, arguments);
};

try {
    TravelPins.log('NETWORK HOOK INSTALLATO (v3: parsing /maps/preview/place + inoltro details al bridge)');
} catch(e) {}

return 'HOOK_INSTALLED';

})();
""".trimIndent()

    val ACCEPT_CONSENT_SCRIPT = """

(function() {
try {
var result = [];
var elements = document.querySelectorAll(
'button, div[role="button"], input'
);

    for (var i = 0; i < elements.length; i++) {
        var e = elements[i];

        var text = (
            e.innerText ||
            e.value ||
            e.getAttribute('aria-label') ||
            ''
        ).trim().toLowerCase();

        if (
            text === 'accetta tutto' ||
            text === 'accetta' ||
            text === 'accept all' ||
            text === 'accept'
        ) {
            result.push('BUTTON_FOUND: ' + text);

            try {
                e.click();
                result.push('CLICK_OK');
            } catch(err) {
                result.push(
                    'CLICK_ERROR: ' + err.message
                );
            }

            break;
        }
    }

    return result.join('|');

} catch(e) {
    return 'ERROR: ' + e.message;
}

})();
""".trimIndent()

    val GETLIST_SCRIPT = """

(async function() {
try {

    var currentUrl = window.location.href;

    TravelPins.log(
        'URL ANALIZZATO: ' + currentUrl
    );

    /*
     * ========================================================
     * LIST ID
     * ========================================================
     */

    var listId = '';

    var match = currentUrl.match(
        /!11m2!2s([^!&]+)/i
    );

    if (match) {
        listId = match[1];
    }

    if (!listId) {

        match = currentUrl.match(
            /\/local\/userlists\/list\/([^?\/]+)/i
        );

        if (match) {
            listId = match[1];
        }
    }

    if (!listId) {

        match = currentUrl.match(
            /2s([A-Za-z0-9_-]{20,})/
        );

        if (match) {
            listId = match[1];
        }
    }

    TravelPins.log(
        'LIST ID: ' +
        (listId || 'NON TROVATO')
    );

    if (!listId) {

        TravelPins.log(
            'ERRORE: LIST ID NON TROVATO'
        );

        return;
    }

    /*
     * ========================================================
     * GETLIST
     * ========================================================
     */

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

    TravelPins.log(
        'GETLIST URL: ' + endpoint
    );

    var response = await fetch(
        endpoint,
        {
            method: 'GET',
            credentials: 'include',
            cache: 'no-store'
        }
    );

    TravelPins.log(
        'GETLIST HTTP: ' +
        response.status
    );

    var raw = await response.text();

    TravelPins.log(
        'GETLIST LENGTH: ' +
        raw.length
    );

    if (!raw || raw.length < 10) {

        TravelPins.log(
            'RISPOSTA GETLIST VUOTA'
        );

        return;
    }

    TravelPins.log(
    '===== GETLIST RAW COMPLETO =====\n' +
    raw
);

    /*
     * ========================================================
     * RIMOZIONE PREFISSO ANTI-XSSI
     * ========================================================
     */

    var cleaned = raw;

    if (cleaned.indexOf(")]}'") === 0) {

        cleaned = cleaned.substring(4);

        if (cleaned.charAt(0) === '\n') {
            cleaned = cleaned.substring(1);
        }
    }

    var data;

    try {

        data = JSON.parse(cleaned);

        TravelPins.log(
            'JSON PARSATO CORRETTAMENTE'
        );

    } catch(e) {

        TravelPins.log(
            'JSON PARSE FALLITO: ' +
            e.message
        );

        return;
    }

    /*
     * ========================================================
     * TITOLO DELLA LISTA GOOGLE MAPS
     * ========================================================
     *
     * Il titolo NON e' nel document.title della pagina (Google
     * Maps non lo aggiorna in questo contesto). E' invece dentro
     * la risposta getlist stessa, in posizione fissa:
     * data[0][4] (es. "Irlanda").
     */

    var sourceListName = '';

    try {
        if (
            Array.isArray(data) &&
            Array.isArray(data[0]) &&
            typeof data[0][4] === 'string' &&
            data[0][4].trim() !== ''
        ) {
            sourceListName = data[0][4].trim();
        }
    } catch(e) {}

    if (!sourceListName) {
        var fallbackTitle = (document.title || '')
            .trim()
            .replace(/\s*-\s*Google Maps\s*$/i, '')
            .trim();

        sourceListName = fallbackTitle || 'Lista Google Maps';
    }

    TravelPins.log(
        'NOME LISTA: ' + sourceListName
    );

    try {
        TravelPinsBridge.onListTitleExtracted(
            sourceListName
        );
    } catch(e) {
        TravelPins.log(
            'ERRORE INVIO TITOLO LISTA: ' +
            e.message
        );
    }

    /*
     * ========================================================
     * ESTRAZIONE PLACES
     * ========================================================
     */

    var places = [];

    function isNumber(v) {
        return typeof v === 'number' &&
            isFinite(v);
    }

    function looksLikeLatLng(a, b) {

        return isNumber(a) &&
            isNumber(b) &&
            Math.abs(a) <= 90 &&
            Math.abs(b) <= 180;
    }

    function cleanString(value) {

        if (typeof value !== 'string') {
            return '';
        }

        return value
            .replace(/\s+/g, ' ')
            .trim();
    }

    function isUsefulName(value) {

        var s = cleanString(value);

        if (
            !s ||
            s.length < 2 ||
            s.length > 250
        ) {
            return false;
        }

        if (
            s.indexOf('http://') === 0 ||
            s.indexOf('https://') === 0
        ) {
            return false;
        }

        return true;
    }

    function tryKnownPlace(x) {

        try {

            if (!Array.isArray(x)) {
                return;
            }

            if (x.length < 3) {
                return;
            }

            var name =
                cleanString(x[2]);

            if (!isUsefulName(name)) {
                return;
            }

            var envelope = x[1];

            if (!Array.isArray(envelope)) {
                return;
            }

            var coordBlock =
                envelope[5];

            if (!Array.isArray(coordBlock)) {
                return;
            }

            var lat = coordBlock[2];
            var lng = coordBlock[3];

            if (!looksLikeLatLng(lat, lng)) {
                return;
            }

            var address = '';

            if (typeof x[3] === 'string') {
                address =
                    cleanString(x[3]);
            }

            var placeId = '';

            function findId(node) {

                if (placeId || !node) {
                    return;
                }

                if (typeof node === 'string') {

                    if (
                        node.length > 15 &&
                        node.length < 200 &&
                        (
                            node.indexOf('ChIJ') === 0 ||
                            node.indexOf('0x') === 0
                        )
                    ) {
                        placeId = node;
                    }

                    return;
                }

                if (Array.isArray(node)) {

                    for (
                        var i = 0;
                        i < node.length;
                        i++
                    ) {

                        findId(node[i]);

                        if (placeId) {
                            return;
                        }
                    }
                }
            }

            findId(envelope);

            /*
             * ====================================================
             * LINK DIRETTO ALLA SCHEDA DEL LUOGO (con foto/recensioni)
             * ====================================================
             *
             * Google include, per ogni luogo, una coppia di ID
             * (es. ["5213690538307142171","-9202193626830073640"]).
             * Il secondo valore, convertito da intero 64bit con
             * segno a intero 64bit SENZA segno, e' il "cid" che
             * Google Maps usa nei link diretti:
             * https://www.google.com/maps?cid=<numero>
             *
             * Se per qualche motivo non riusciamo a calcolarlo,
             * mapsUrl resta vuoto e l'app ripiega su una ricerca
             * per coordinate (comportamento precedente).
             */

            var mapsUrl = '';

            try {

                var featurePair = envelope[6];

                if (
                    Array.isArray(featurePair) &&
                    featurePair.length >= 2 &&
                    typeof featurePair[1] !== 'undefined'
                ) {

                    var rawCid = BigInt(featurePair[1]);

                    if (rawCid < 0n) {
                        rawCid = rawCid + (1n << 64n);
                    }

                    mapsUrl =
                        'https://www.google.com/maps?cid=' +
                        rawCid.toString();
                }

            } catch(e) {
                mapsUrl = '';
            }

            places.push({
                name: name,
                address: address,
                lat: lat,
                lng: lng,
                placeId: placeId,
                mapsUrl: mapsUrl
            });

        } catch(e) {}
    }

    function walk(node) {

        if (!node) {
            return;
        }

        if (Array.isArray(node)) {

            tryKnownPlace(node);

            for (
                var i = 0;
                i < node.length;
                i++
            ) {
                walk(node[i]);
            }

        } else if (
            typeof node === 'object'
        ) {

            for (var key in node) {

                try {
                    walk(node[key]);
                } catch(e) {}
            }
        }
    }

    walk(data);

    /*
     * ========================================================
     * RIMOZIONE DUPLICATI
     * ========================================================
     */

    var unique = [];
    var seen = {};

    for (
        var i = 0;
        i < places.length;
        i++
    ) {

        var p = places[i];

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

    places = unique;

    TravelPins.log(
        'PLACE TROVATI: ' +
        places.length
    );

    if (places.length === 0) {

        TravelPins.log(
            'NESSUN PLACE TROVATO CON IL PARSER PRINCIPALE.'
        );

        if (Array.isArray(data)) {

            TravelPins.log(
                'TOP LEVEL ARRAY LENGTH: ' +
                data.length
            );

            for (
                var z = 0;
                z < Math.min(
                    data.length,
                    15
                );
                z++
            ) {

                var item = data[z];

                var preview = '';

                try {

                    preview =
                        JSON.stringify(item)
                            .substring(0, 1000);

                } catch(e) {}

                TravelPins.log(
                    'TOP[' +
                    z +
                    ']: ' +
                    preview
                );
            }
        }

        return;
    }

    /*
     * ========================================================
     * OUTPUT DIAGNOSTICO
     * ========================================================
     */

    var output = '';

    output +=
        'TITLE: ' +
        sourceListName +
        '\n\n';

    output +=
        'PLACES (' +
        places.length +
        ')\n';

    output +=
        '==============================\n';

    for (
        var j = 0;
        j < places.length;
        j++
    ) {

        var place = places[j];

        output +=
            '\n' +
            (j + 1) +
            '. ' +
            place.name +
            '\n';

        if (place.address) {

            output +=
                '   ' +
                place.address +
                '\n';
        }

        output +=
            '   COORD: ' +
            place.lat +
            ', ' +
            place.lng +
            '\n';

        if (place.placeId) {

            output +=
                '   PLACE ID: ' +
                place.placeId +
                '\n';
        }

        output +=
            '   MAPS: ' +
            'https://www.google.com/maps/search/?api=1&query=' +
            encodeURIComponent(
                place.lat +
                ',' +
                place.lng
            ) +
            '\n';

        output +=
            '------------------------------\n';
    }

    TravelPins.log(
        '===== RISULTATO LISTA =====\n' +
        output.substring(0, 14000)
    );

    window.__travelpins_places =
        places;

    /*
     * ========================================================
     * INVIO DATI AL BRIDGE KOTLIN
     * ========================================================
     */

    try {

        TravelPinsBridge.onPlacesExtracted(
            JSON.stringify(places)
        );

    } catch(e) {

        TravelPins.log(
            'ERRORE INVIO A BRIDGE: ' +
            e.message
        );
    }

} catch(e) {

    TravelPins.log(
        'ERRORE GENERALE GETLIST: ' +
        e.message
    );
}

})();
""".trimIndent()
}
