package com.travelpins.test.scraper

object GoogleMapsScraperScript {

    val NETWORK_HOOK_SCRIPT = """
        (function() {
            if (window.__tpHook) return;
            window.__tpHook = true;
            
            const originalFetch = window.fetch;
            window.fetch = function(...args) {
                const url = typeof args[0] === 'string' ? args[0] : (args[0] ? args[0].url : '');
                if (url && url.includes('/maps/preview/place')) {
                    return originalFetch.apply(this, args).then(response => {
                        const clone = response.clone();
                        clone.text().then(text => {
                            try {
                                window.TravelPinsBridge.onPlaceDetailsExtracted(text);
                            } catch(e) {}
                        });
                        return response;
                    });
                }
                return originalFetch.apply(this, args);
            };

            const originalOpen = XMLHttpRequest.prototype.open;
            const originalSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
                this._tpUrl = url;
                return originalOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function() {
                if (this._tpUrl && this._tpUrl.includes('/maps/preview/place')) {
                    this.addEventListener('load', function() {
                        try {
                            window.TravelPinsBridge.onPlaceDetailsExtracted(this.responseText);
                        } catch(e) {}
                    });
                }
                return originalSend.apply(this, arguments);
            };
        })();
    """.trimIndent()

    val ACCEPT_CONSENT_SCRIPT = """
        (function() {
            var buttons = document.querySelectorAll('button');
            for (var i = 0; i < buttons.length; i++) {
                var text = buttons[i].textContent.toLowerCase();
                if (text.includes('rifiuta') || text.includes('reject') || text.includes('accetta') || text.includes('accept')) {
                    buttons[i].click();
                    return 'CLICK_OK';
                }
            }
            return 'NO_BUTTON';
        })();
    """.trimIndent()

    val GETLIST_SCRIPT = """
        (async function() {
            try {
                TravelPins.log('GETLIST: Inizio scansione...');
                
                var currentUrl = window.location.href;
                TravelPins.log('GETLIST: URL corrente: ' + currentUrl);
                
                var listId = '';
                var match = currentUrl.match(/!11m2!2s([^!&]+)/i) ||
                            currentUrl.match(/\/local\/userlists\/list\/([^?\/]+)/i) ||
                            currentUrl.match(/2s([A-Za-z0-9_-]{20,})/);
                
                if (match) listId = match[1] || match[2] || match[3];
                
                if (!listId) {
                    TravelPins.log('ERRORE: LIST ID NON TROVATO nell\'URL');
                    TravelPinsBridge.onListScanError('LIST_ID_NOT_FOUND');
                    return;
                }
                
                TravelPins.log('GETLIST: List ID trovato: ' + listId);
                
                // Controlla se la pagina mostra "Elenco non trovato"
                var pageTitle = document.title || '';
                var pageText = document.body ? document.body.innerText : '';
                
                if (pageTitle.includes('non trovato') || pageTitle.includes('not found') ||
                    pageText.includes('Elenco non trovato') || pageText.includes('List not found')) {
                    TravelPins.log('ERRORE: La pagina mostra "Elenco non trovato"');
                    TravelPinsBridge.onListScanError('LIST_NOT_FOUND');
                    return;
                }
                
                var pb = '!1m4!1s' + encodeURIComponent(listId) + '!2e1!3m1!1e1!2e2!3e3!4i500!8i3!16b1';
                var endpoint = '/maps/preview/entitylist/getlist?authuser=0&hl=it&gl=it&pb=' + pb;
                
                TravelPins.log('GETLIST: Chiamata endpoint: ' + endpoint);
                
                var response = await fetch(endpoint, { 
                    method: 'GET', 
                    credentials: 'include', 
                    cache: 'no-store' 
                });
                
                TravelPins.log('GETLIST: Status risposta: ' + response.status);
                
                if (!response.ok) {
                    TravelPins.log('ERRORE: Response non OK, status: ' + response.status);
                    TravelPinsBridge.onListScanError('FETCH_FAILED_' + response.status);
                    return;
                }
                
                var raw = await response.text();
                TravelPins.log('GETLIST: Lunghezza risposta raw: ' + raw.length);
                
                if (!raw || raw.length < 10) {
                    TravelPins.log('ERRORE: Risposta troppo corta o vuota');
                    TravelPinsBridge.onListScanError('EMPTY_RESPONSE');
                    return;
                }
                
                var cleaned = raw;
                if (cleaned.indexOf(")]}'") === 0) {
                    cleaned = cleaned.substring(4);
                    if (cleaned.charAt(0) === 'n') cleaned = cleaned.substring(1);
                }
                
                var data;
                try {
                    data = JSON.parse(cleaned);
                } catch(e) {
                    TravelPins.log('ERRORE: Parsing JSON fallito: ' + e.message);
                    TravelPinsBridge.onListScanError('JSON_PARSE_ERROR');
                    return;
                }
                
                TravelPins.log('GETLIST: JSON parsato con successo');
                
                var sourceListName = '';
                if (Array.isArray(data) && Array.isArray(data[0]) && typeof data[0][4] === 'string') {
                    sourceListName = data[0][4].trim();
                }
                
                if (!sourceListName) {
                    sourceListName = pageTitle.replace(/\s*-\s*Google Maps\s*$/i, '').trim() || 'Lista Google Maps';
                }
                
                TravelPins.log('GETLIST: Nome lista: ' + sourceListName);
                
                try { 
                    TravelPinsBridge.onListTitleExtracted(sourceListName); 
                } catch(e) {
                    TravelPins.log('ERRORE onListTitleExtracted: ' + e.message);
                }
                
                var places = [];
                
                function walk(node) {
                    if (!node) return;
                    if (Array.isArray(node)) {
                        if (node.length >= 3 && typeof node[2] === 'string' &&
                            node[2].length > 2 && node[2].length < 250 && !node[2].startsWith('http')) {
                            var env = node[1];
                            if (Array.isArray(env) && Array.isArray(env[5]) &&
                                typeof env[5][2] === 'number' && typeof env[5][3] === 'number') {
                                var lat = env[5][2], lng = env[5][3];
                                if (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
                                    var address = typeof node[3] === 'string' ? node[3] : '';
                                    var placeId = '', mapsUrl = '';
                                    
                                    function findId(n) {
                                        if (placeId || !n) return;
                                        if (typeof n === 'string' && n.length > 15 && n.length < 200 &&
                                            (n.indexOf('ChIJ') === 0 || n.indexOf('0x') === 0)) {
                                            placeId = n;
                                            return;
                                        }
                                        if (Array.isArray(n)) {
                                            for (var i = 0; i < n.length; i++) {
                                                findId(n[i]);
                                                if (placeId) return;
                                            }
                                        }
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
                                    
                                    places.push({
                                        name: node[2],
                                        address: address,
                                        lat: lat,
                                        lng: lng,
                                        placeId: placeId,
                                        mapsUrl: mapsUrl
                                    });
                                }
                            }
                        }
                        for (var i = 0; i < node.length; i++) walk(node[i]);
                    } else if (typeof node === 'object') {
                        for (var key in node) {
                            try { walk(node[key]); } catch(e) {}
                        }
                    }
                }
                
                walk(data);
                
                TravelPins.log('GETLIST: Trovati ' + places.length + ' luoghi grezzi');
                
                var unique = [], seen = {};
                for (var i = 0; i < places.length; i++) {
                    var p = places[i], key = p.name + '|' + p.lat + '|' + p.lng;
                    if (!seen[key]) { seen[key] = true; unique.push(p); }
                }
                
                TravelPins.log('GETLIST: ' + unique.length + ' luoghi unici dopo deduplica');
                
                if (unique.length === 0) {
                    TravelPins.log('ERRORE: Nessun luogo trovato nella risposta');
                    TravelPinsBridge.onListScanError('NO_PLACES_FOUND');
                    return;
                }
                
                try { 
                    TravelPinsBridge.onPlacesExtracted(JSON.stringify(unique)); 
                } catch(e) {
                    TravelPins.log('ERRORE onPlacesExtracted: ' + e.message);
                    TravelPinsBridge.onListScanError('BRIDGE_CALL_ERROR');
                }
                
            } catch(e) { 
                TravelPins.log('ERRORE GETLIST: ' + e.message); 
                TravelPinsBridge.onListScanError('EXCEPTION: ' + e.message);
            }
        })();
    """.trimIndent()

    fun isGoogleListUrl(url: String): Boolean {
        return url.contains("/local/userlists/list/") || 
               url.contains("!11m2!2s") ||
               (url.contains("google.com/maps") && url.contains("@/data"))
    }
}
