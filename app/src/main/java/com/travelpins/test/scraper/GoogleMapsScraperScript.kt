package com.travelpins.test.scraper

object GoogleMapsScraperScript {

    fun isGoogleListUrl(url: String): Boolean =
        url.contains("/local/userlists/list/") || url.contains("/maps/@/data=") || url.contains("!11m2!2s")

    val NETWORK_HOOK_SCRIPT = """
    (function() {
        if (window.__travelpins_hooked) return 'ALREADY_INSTALLED';
        window.__travelpins_hooked = true;
        
        function isNoiseUrl(url) { 
            return url.indexOf('/maps/vt') !== -1 || 
                   url.indexOf('/maps/preview/log204') !== -1; 
        }
        
        function isPlaceDetailUrl(url) { 
            return url.indexOf('/maps/preview/place') !== -1; 
        }
        
        function logPlaceResponse(url, text) {
            try {
                TravelPins.log('===== RISPOSTA /maps/preview/place =====');
                TravelPins.log('URL: ' + url);
                TravelPins.log('LUNGHEZZA: ' + text.length);
                TravelPins.log('PRIMI 200 CHAR: ' + text.substring(0, 200));
                
                try { 
                    TravelPinsBridge.onPlaceDetailsExtracted(text); 
                    TravelPins.log('DATI INVIATI AL BRIDGE');
                } catch(e) { 
                    TravelPins.log('ERRORE INVIO BRIDGE: ' + e.message); 
                }
            } catch(e) {
                TravelPins.log('ERRORE LOG RISPOSTA: ' + e.message);
            }
        }
        
        // Hook fetch
        var originalFetch = window.fetch;
        window.fetch = async function() {
            var input = arguments[0], options = arguments[1] || {};
            var url = typeof input === 'string' ? input : input.url;
            var method = options.method || (typeof input !== 'string' ? input.method : 'GET') || 'GET';
            
            if (!isNoiseUrl(url)) { 
                try { TravelPins.network('FETCH_REQUEST', method, url, options.body || ''); } catch(e) {} 
            }
            
            var response = await originalFetch.apply(this, arguments);
            
            if (isPlaceDetailUrl(url)) {
                try { 
                    response.clone().text().then(function(text) { 
                        logPlaceResponse(url, text); 
                    }); 
                } catch(e) {}
            }
            
            return response;
        };
        
        // Hook XMLHttpRequest
        var originalOpen = XMLHttpRequest.prototype.open;
        var originalSend = XMLHttpRequest.prototype.send;
        
        XMLHttpRequest.prototype.open = function(method, url) { 
            this.__tp_method = method; 
            this.__tp_url = url; 
            return originalOpen.apply(this, arguments); 
        };
        
        XMLHttpRequest.prototype.send = function(body) {
            var xhr = this, url = xhr.__tp_url || '', method = xhr.__tp_method || 'GET';
            
            if (!isNoiseUrl(url)) { 
                try { TravelPins.network('XHR_REQUEST', method, url, body || ''); } catch(e) {} 
            }
            
            if (isPlaceDetailUrl(url)) {
                xhr.addEventListener('load', function() { 
                    try { logPlaceResponse(url, xhr.responseText || ''); } catch(e) {} 
                });
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
            var match = currentUrl.match(/!11m2!2s([^!&]+)/i) || 
                       currentUrl.match(/\/local\/userlists\/list\/([^?\/]+)/i) || 
                       currentUrl.match(/2s([A-Za-z0-9_-]{20,})/);
            if (match) listId = match[1] || match[2] || match[3];
            if (!listId) { TravelPins.log('ERRORE: LIST ID NON TROVATO'); return; }
            
            var pb = '!1m4!1s' + encodeURIComponent(listId) + '!2e1!3m1!1e1!2e2!3e3!4i500!8i3!16b1';
            var endpoint = '/maps/preview/entitylist/getlist?authuser=0&hl=it&gl=it&pb=' + pb;
            var response = await fetch(endpoint, { method: 'GET', credentials: 'include', cache: 'no-store' });
            var raw = await response.text();
            if (!raw || raw.length < 10) return;
            
            var cleaned = raw;
            if (cleaned.indexOf(")]}'") === 0) { 
                cleaned = cleaned.substring(4); 
                if (cleaned.charAt(0) === '\n') cleaned = cleaned.substring(1); 
            }
            var data = JSON.parse(cleaned);
            
            var sourceListName = '';
            if (Array.isArray(data) && Array.isArray(data[0]) && typeof data[0][4] === 'string') {
                sourceListName = data[0][4].trim();
            }
            if (!sourceListName) {
                sourceListName = (document.title || '').replace(/\s*-\s*Google Maps\s*$/i, '').trim() || 'Lista Google Maps';
            }
            try { TravelPinsBridge.onListTitleExtracted(sourceListName); } catch(e) {}
            
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
            
            var unique = [], seen = {};
            for (var i = 0; i < places.length; i++) {
                var p = places[i], key = p.name + '|' + p.lat + '|' + p.lng;
                if (!seen[key]) { seen[key] = true; unique.push(p); }
            }
            try { TravelPinsBridge.onPlacesExtracted(JSON.stringify(unique)); } catch(e) {}
        } catch(e) { TravelPins.log('ERRORE GETLIST: ' + e.message); }
    })();
    """.trimIndent()
}
