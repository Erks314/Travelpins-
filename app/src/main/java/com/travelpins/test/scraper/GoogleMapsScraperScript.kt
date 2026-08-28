package com.travelpins.test.scraper

object GoogleMapsScraperScript {

    fun isGoogleListUrl(url: String): Boolean =
        url.contains("/local/userlists/list/") || url.contains("/maps/@/data=") || url.contains("!11m2!2s")

    // NUOVO: Estrae dati dal DOM della pagina luogo
    val EXTRACT_FROM_DOM_SCRIPT = """
    (function() {
        try {
            var photos = [];
            var reviews = [];
            var rating = null;
            var reviewCount = null;
            var description = null;
            var websiteUrl = null;
            
            // 1. Cerca immagini (foto del luogo)
            var imgs = document.querySelectorAll('img');
            for (var i = 0; i < imgs.length; i++) {
                var src = imgs[i].src || imgs[i].getAttribute('data-src') || '';
                if (src.indexOf('lh3.googleusercontent.com') !== -1) {
                    if (photos.indexOf(src) === -1) {
                        photos.push(src);
                    }
                }
            }
            
            // 2. Cerca rating (numero tra 1.0 e 5.0 con stella)
            var starElements = document.querySelectorAll('[role="img"], [aria-label*="stella"], [aria-label*="star"]');
            for (var j = 0; j < starElements.length; j++) {
                var label = starElements[j].getAttribute('aria-label') || '';
                var match = label.match(/(\d+[.,]\d+)/);
                if (match && !rating) {
                    rating = parseFloat(match[1].replace(',', '.'));
                    if (rating < 1.0 || rating > 5.0) rating = null;
                }
            }
            
            // 3. Cerca conteggio recensioni
            var allText = document.body.innerText;
            var countMatch = allText.match(/(\d+)\s*(?:recensioni|reviews)/i);
            if (countMatch) {
                reviewCount = parseInt(countMatch[1]);
            }
            
            // 4. Cerca descrizione (testo lungo non ripetuto)
            var paragraphs = document.querySelectorAll('p, div[data-section-id*="about"] p, span[dir="auto"]');
            for (var k = 0; k < paragraphs.length; k++) {
                var text = (paragraphs[k].innerText || '').trim();
                if (text.length > 50 && text.length < 500 && !description) {
                    description = text;
                    break;
                }
            }
            
            // 5. Cerca sito web
            var links = document.querySelectorAll('a[href*="http"]');
            for (var l = 0; l < links.length; l++) {
                var href = links[l].href;
                if (href && href.indexOf('google.com') === -1 && href.indexOf('maps.') === -1 && 
                    (href.startsWith('http://') || href.startsWith('https://')) && !websiteUrl) {
                    websiteUrl = href;
                    break;
                }
            }
            
            var result = {
                photos: photos.slice(0, 10),
                rating: rating,
                reviewCount: reviewCount,
                description: description,
                websiteUrl: websiteUrl,
                reviews: []
            };
            
            try {
                TravelPinsBridge.onPlaceDetailsExtracted(JSON.stringify(result));
                return 'OK';
            } catch(e) {
                return 'BRIDGE_ERROR: ' + e.message;
            }
        } catch(e) {
            return 'PARSE_ERROR: ' + e.message;
        }
    })();
    """.trimIndent()

    val NETWORK_HOOK_SCRIPT = """
    (function() {
        if (window.__travelpins_hooked) return 'ALREADY_INSTALLED';
        window.__travelpins_hooked = true;
        function isNoiseUrl(url) { return url.indexOf('/maps/vt') !== -1 || url.indexOf('/maps/preview/log204') !== -1; }
        function isPlaceDetailUrl(url) { return url.indexOf('/maps/preview/place') !== -1; }
        function logPlaceResponse(url, text) {
            try {
                TravelPins.log('===== RISPOSTA /maps/preview/place =====\nURL: ' + url + '\nLEN: ' + text.length);
                try { TravelPinsBridge.onPlaceDetailsExtracted(text); } catch(e) { TravelPins.log('ERRORE BRIDGE: ' + e.message); }
            } catch(e) {}
        }
        var originalFetch = window.fetch;
        window.fetch = async function() {
            var input = arguments[0], options = arguments[1] || {};
            var url = typeof input === 'string' ? input : input.url;
            var method = options.method || (typeof input !== 'string' ? input.method : 'GET') || 'GET';
            if (!isNoiseUrl(url)) { try { TravelPins.network('FETCH_REQUEST', method, url, options.body || ''); } catch(e) {} }
            var response = await originalFetch.apply(this, arguments);
            if (isPlaceDetailUrl(url)) {
                try { response.clone().text().then(function(text) { logPlaceResponse(url, text); }); } catch(e) {}
            }
            return response;
        };
        var originalOpen = XMLHttpRequest.prototype.open;
        var originalSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function(method, url) { this.__tp_method = method; this.__tp_url = url; return originalOpen.apply(this, arguments); };
        XMLHttpRequest.prototype.send = function(body) {
            var xhr = this, url = xhr.__tp_url || '', method = xhr.__tp_method || 'GET';
            if (!isNoiseUrl(url)) { try { TravelPins.network('XHR_REQUEST', method, url, body || ''); } catch(e) {} }
            if (isPlaceDetailUrl(url)) {
                xhr.addEventListener('load', function() { try { logPlaceResponse(url, xhr.responseText || ''); } catch(e) {} });
            }
            return originalSend.apply(this, arguments);
        };
        try { TravelPins.log('NETWORK HOOK v3 INSTALLATO'); } catch(e) {}
        return 'HOOK_INSTALLED';
    })();
    """.trimIndent()

    val ACCEPT_CONSENT_SCRIPT = """
    (function() {
        try {
            var elements = document.querySelectorAll('button, div[role="button"], input');
            for (var i = 0; i < elements.length; i++) {
                var e = elements[i];
                var text = (e.innerText || e.value || e.getAttribute('aria-label') || '').trim().toLowerCase();
                if (text === 'accetta tutto' || text === 'accetta' || text === 'accept all' || text === 'accept') {
                    try { e.click(); return 'CLICK_OK'; } catch(err) { return 'CLICK_ERROR'; }
                }
            }
            return 'NOT_FOUND';
        } catch(e) { return 'ERROR: ' + e.message; }
    })();
    """.trimIndent()

    val GETLIST_SCRIPT = """
    (async function() {
        try {
            var currentUrl = window.location.href;
            var listId = '';
            var match = currentUrl.match(/!11m2!2s([^!&]+)/i) || currentUrl.match(/\/local\/userlists\/list\/([^?\/]+)/i) || currentUrl.match(/2s([A-Za-z0-9_-]{20,})/);
            if (match) listId = match[1] || match[2] || match[3];
            if (!listId) { TravelPins.log('ERRORE: LIST ID NON TROVATO'); return; }
            
            var pb = '!1m4!1s' + encodeURIComponent(listId) + '!2e1!3m1!1e1!2e2!3e3!4i500!8i3!16b1';
            var endpoint = '/maps/preview/entitylist/getlist?authuser=0&hl=it&gl=it&pb=' + pb;
            var response = await fetch(endpoint, { method: 'GET', credentials: 'include', cache: 'no-store' });
            var raw = await response.text();
            if (!raw || raw.length < 10) return;
            
            var cleaned = raw;
            if (cleaned.indexOf(")]}'") === 0) { cleaned = cleaned.substring(4); if (cleaned.charAt(0) === '\n') cleaned = cleaned.substring(1); }
            var data = JSON.parse(cleaned);
            
            var sourceListName = '';
            if (Array.isArray(data) && Array.isArray(data[0]) && typeof data[0][4] === 'string') sourceListName = data[0][4].trim();
            if (!sourceListName) sourceListName = (document.title || '').replace(/\s*-\s*Google Maps\s*$/i, '').trim() || 'Lista Google Maps';
            try { TravelPinsBridge.onListTitleExtracted(sourceListName); } catch(e) {}
            
            var places = [];
            function walk(node) {
                if (!node) return;
                if (Array.isArray(node)) {
                    if (node.length >= 3 && typeof node[2] === 'string' && node[2].length > 2 && node[2].length < 250 && !node[2].startsWith('http')) {
                        var env = node[1];
                        if (Array.isArray(env) && Array.isArray(env[5]) && typeof env[5][2] === 'number' && typeof env[5][3] === 'number') {
                            var lat = env[5][2], lng = env[5][3];
                            if (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
                                var address = typeof node[3] === 'string' ? node[3] : '';
                                var placeId = '', mapsUrl = '';
                                function findId(n) {
                                    if (placeId || !n) return;
                                    if (typeof n === 'string' && n.length > 15 && n.length < 200 && (n.indexOf('ChIJ') === 0 || n.indexOf('0x') === 0)) { placeId = n; return; }
                                    if (Array.isArray(n)) for (var i = 0; i < n.length; i++) { findId(n[i]); if (placeId) return; }
                                }
                                findId(env);
                                try {
                                    var fp = env[6];
                                    if (Array.isArray(fp) && fp.length >= 2 && typeof fp[1] !== 'undefined') {
                                        var rawCid = BigInt(fp[1]);
                                        if (rawCid < 0n) rawCid = rawCid + (1n << 64n);
                                        mapsUrl = 'https://www.google.com/maps?cid=' + rawCid.toString();
                                    }
                                } catch(e) {}
                                places.push({ name: node[2], address: address, lat: lat, lng: lng, placeId: placeId, mapsUrl: mapsUrl });
                            }
                        }
                    }
                    for (var i = 0; i < node.length; i++) walk(node[i]);
                } else if (typeof node === 'object') {
                    for (var key in node) try { walk(node[key]); } catch(e) {}
                }
            }
            walk(data);
            
            var unique = [], seen = {};
            for (var i = 0; i < places.length; i++) {
                var p = places[i], key = p.name + '|' + p.lat + '|' + p.lng;
                if (!seen[key]) { seen[key] = true; unique.push(p); }
            }
            try { TravelPinsBridge.onPlacesExtracted(JSON.stringify(unique)); } catch(e) {}
        } catch(e) { TravelPins.log('ERRORE GETLIST: ' + e.message); }
    })();
    """.trimIndent()

    fun detailsFetchScript(query: String, ref: String, lat: Double, lng: Double): String = """
        (async function() {
            try {
                var pb = '!1m18!1m12!1m3!1d1!2d1!3d1!2m1!1s' + encodeURIComponent('$ref') + '!3m2!1sit!2sit!4m2!3m1!1s' + encodeURIComponent('$ref');
                var url = '/maps/preview/place?authuser=0&hl=it&gl=it&pb=' + encodeURIComponent(pb);
                var res = await fetch(url, { method: 'GET', credentials: 'include', cache: 'no-store' });
                var text = await res.text();
                if (text.startsWith(")]}'")) {
                    text = text.substring(4);
                    if (text.charAt(0) === '\n') text = text.substring(1);
                }
                try {
                    TravelPinsBridge.onPlaceDetailsExtracted(text);
                    return 'OK foto=1';
                } catch(e) { return 'ERRORE BRIDGE: ' + e.message; }
            } catch(e) { return 'ERRORE FETCH: ' + e.message; }
        })();
    """.trimIndent()
}
