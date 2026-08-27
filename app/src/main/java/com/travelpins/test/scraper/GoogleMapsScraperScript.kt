package com.travelpins.test.scraper

object GoogleMapsScraperScript {

    fun isGoogleListUrl(url: String): Boolean {
        return url.contains("/local/userlists/list/") ||
            url.contains("/maps/@/data=") ||
            url.contains("!11m2!2s")
    }

    fun detailsFetchScript(query: String, ref: String, lat: Double, lng: Double): String {
        return """
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
                } catch(e) {
                    return 'ERRORE BRIDGE: ' + e.message;
                }
            } catch(e) {
                return 'ERRORE FETCH: ' + e.message;
            }
        })();
        """.trimIndent()
    }

    val NETWORK_HOOK_SCRIPT = """
    (function() {
        if (window.__travelpins_hooked) {
            return 'ALREADY_INSTALLED';
        }
        window.__travelpins_hooked = true;

        function isNoiseUrl(url) {
            return url.indexOf('/maps/vt') !== -1 || url.indexOf('/maps/preview/log204') !== -1;
        }

        function isPlaceDetailUrl(url) {
            return url.indexOf('/maps/preview/place') !== -1;
        }

        function logPlaceResponse(url, text) {
            try {
                var preview = text.substring(0, 20000);
                TravelPins.log(
                    '===== RISPOSTA /maps/preview/place =====\n' +
                    'URL: ' + url + '\n' +
                    'LUNGHEZZA TOTALE: ' + text.length + '\n' +
                    'CONTENUTO (troncato a 20000 caratteri):\n' +
                    preview
                );
                
                try {
                    TravelPinsBridge.onPlaceDetailsExtracted(text);
                } catch(e) {
                    TravelPins.log('ERRORE INVIO DETTAGLI AL BRIDGE: ' + e.message);
                }
            } catch(e) {
                try { TravelPins.log('ERRORE LOG RISPOSTA PLACE: ' + e.message); } catch(e2) {}
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
                try { TravelPins.network('FETCH_REQUEST', method, url, body); } catch(e) {}
            }

            var response = await originalFetch.apply(this, arguments);

            if (isPlaceDetailUrl(url)) {
                try {
                    response.clone().text().then(function(text) {
                        logPlaceResponse(url, text);
                    }).catch(function(e) {
                        try { TravelPins.log('ERRORE LETTURA RISPOSTA PLACE (fetch): ' + e.message); } catch(e2) {}
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
                try { TravelPins.network('XHR_REQUEST', method, url, body || ''); } catch(e) {}
            }

            if (isPlaceDetailUrl(url)) {
                xhr.addEventListener('load', function() {
                    try {
                        logPlaceResponse(url, xhr.responseText || '');
                    } catch(e) {
                        try { TravelPins.log('ERRORE LETTURA RISPOSTA PLACE (xhr): ' + e.message); } catch(e2) {}
                    }
                });
            }

            return originalSend.apply(this, arguments);
        };

        try {
            TravelPins.log('NETWORK HOOK INSTALLATO (v3: filtro rumore + cattura e parsing risposta /maps/preview/place)');
        } catch(e) {}

        return 'HOOK_INSTALLED';
    })();
    """.trimIndent()

    val ACCEPT_CONSENT_SCRIPT = """
    (function() {
        try {
            var result = [];
            var elements = document.querySelectorAll('button, div[role="button"], input');
            for (var i = 0; i < elements.length; i++) {
                var e = elements[i];
                var text = (e.innerText || e.value || e.getAttribute('aria-label') || '').trim().toLowerCase();
                if (text === 'accetta tutto' || text === 'accetta' || text === 'accept all' || text === 'accept') {
                    result.push('BUTTON_FOUND: ' + text);
                    try {
                        e.click();
                        result.push('CLICK_OK');
                    } catch(err) {
                        result.push('CLICK_ERROR: ' + err.message);
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
            TravelPins.log('URL ANALIZZATO: ' + currentUrl);

            var listId = '';
            var match = currentUrl.match(/!11m2!2s([^!&]+)/i);
            if (match) listId = match[1];
            if (!listId) {
                match = currentUrl.match(/\/local\/userlists\/list\/([^?\/]+)/i);
                if (match) listId = match[1];
            }
            if (!listId) {
                match = currentUrl.match(/2s([A-Za-z0-9_-]{20,})/);
                if (match) listId = match[1];
            }

            TravelPins.log('LIST ID: ' + (listId || 'NON TROVATO'));
            if (!listId) {
                TravelPins.log('ERRORE: LIST ID NON TROVATO');
                return;
            }

            var pb = '!1m4!1s' + encodeURIComponent(listId) + '!2e1!3m1!1e1!2e2!3e3!4i500!8i3!16b1';
            var endpoint = '/maps/preview/entitylist/getlist?authuser=0&hl=it&gl=it&pb=' + pb;
            TravelPins.log('GETLIST URL: ' + endpoint);

            var response = await fetch(endpoint, { method: 'GET', credentials: 'include', cache: 'no-store' });
            TravelPins.log('GETLIST HTTP: ' + response.status);
            var raw = await response.text();
            TravelPins.log('GETLIST LENGTH: ' + raw.length);

            if (!raw || raw.length < 10) {
                TravelPins.log('RISPOSTA GETLIST VUOTA');
                return;
            }

            var cleaned = raw;
            if (cleaned.indexOf(")]}'") === 0) {
                cleaned = cleaned.substring(4);
                if (cleaned.charAt(0) === '\n') cleaned = cleaned.substring(1);
            }

            var data;
            try {
                data = JSON.parse(cleaned);
                TravelPins.log('JSON PARSATO CORRETTAMENTE');
            } catch(e) {
                TravelPins.log('JSON PARSE FALLITO: ' + e.message);
                return;
            }

            var sourceListName = '';
            try {
                if (Array.isArray(data) && Array.isArray(data[0]) && typeof data[0][4] === 'string' && data[0][4].trim() !== '') {
                    sourceListName = data[0][4].trim();
                }
            } catch(e) {}

            if (!sourceListName) {
                var fallbackTitle = (document.title || '').trim().replace(/\s*-\s*Google Maps\s*$/i, '').trim();
                sourceListName = fallbackTitle || 'Lista Google Maps';
            }

            TravelPins.log('NOME LISTA: ' + sourceListName);
            try { TravelPinsBridge.onListTitleExtracted(sourceListName); } catch(e) { TravelPins.log('ERRORE INVIO TITOLO LISTA: ' + e.message); }

            var places = [];
            function isNumber(v) { return typeof v === 'number' && isFinite(v); }
            function looksLikeLatLng(a, b) { return isNumber(a) && isNumber(b) && Math.abs(a) <= 90 && Math.abs(b) <= 180; }
            function cleanString(value) { return typeof value !== 'string' ? '' : value.replace(/\s+/g, ' ').trim(); }
            function isUsefulName(value) {
                var s = cleanString(value);
                if (!s || s.length < 2 || s.length > 250) return false;
                if (s.indexOf('http://') === 0 || s.indexOf('https://') === 0) return false;
                return true;
            }

            function tryKnownPlace(x) {
                try {
                    if (!Array.isArray(x) || x.length < 3) return;
                    var name = cleanString(x[2]);
                    if (!isUsefulName(name)) return;
                    var envelope = x[1];
                    if (!Array.isArray(envelope)) return;
                    var coordBlock = envelope[5];
                    if (!Array.isArray(coordBlock)) return;
                    var lat = coordBlock[2];
                    var lng = coordBlock[3];
                    if (!looksLikeLatLng(lat, lng)) return;

                    var address = typeof x[3] === 'string' ? cleanString(x[3]) : '';
                    var placeId = '';

                    function findId(node) {
                        if (placeId || !node) return;
                        if (typeof node === 'string') {
                            if (node.length > 15 && node.length < 200 && (node.indexOf('ChIJ') === 0 || node.indexOf('0x') === 0)) {
                                placeId = node;
                            }
                            return;
                        }
                        if (Array.isArray(node)) {
                            for (var i = 0; i < node.length; i++) {
                                findId(node[i]);
                                if (placeId) return;
                            }
                        }
                    }
                    findId(envelope);

                    var mapsUrl = '';
                    try {
                        var featurePair = envelope[6];
                        if (Array.isArray(featurePair) && featurePair.length >= 2 && typeof featurePair[1] !== 'undefined') {
                            var rawCid = BigInt(featurePair[1]);
                            if (rawCid < 0n) rawCid = rawCid + (1n << 64n);
                            mapsUrl = 'https://www.google.com/maps?cid=' + rawCid.toString();
                        }
                    } catch(e) { mapsUrl = ''; }

                    places.push({ name: name, address: address, lat: lat, lng: lng, placeId: placeId, mapsUrl: mapsUrl });
                } catch(e) {}
            }

            function walk(node) {
                if (!node) return;
                if (Array.isArray(node)) {
                    tryKnownPlace(node);
                    for (var i = 0; i < node.length; i++) walk(node[i]);
                } else if (typeof node === 'object') {
                    for (var key in node) {
                        try { walk(node[key]); } catch(e) {}
                    }
                }
            }
            walk(data);

            var unique = [];
            var seen = {};
            for (var i = 0; i < places.length; i++) {
                var p = places[i];
                var key = p.name + '|' + p.lat + '|' + p.lng;
                if (!seen[key]) {
                    seen[key] = true;
                    unique.push(p);
                }
            }
            places = unique;
            TravelPins.log('PLACE TROVATI: ' + places.length);

            if (places.length === 0) {
                TravelPins.log('NESSUN PLACE TROVATO CON IL PARSER PRINCIPALE.');
                return;
            }

            window.__travelpins_places = places;
            try { TravelPinsBridge.onPlacesExtracted(JSON.stringify(places)); } catch(e) { TravelPins.log('ERRORE INVIO A BRIDGE: ' + e.message); }

        } catch(e) {
            TravelPins.log('ERRORE GENERALE GETLIST: ' + e.message);
        }
    })();
    """.trimIndent()
}
