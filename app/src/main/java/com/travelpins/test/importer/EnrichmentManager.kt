package com.travelpins.test.enrichment // Adatta il tuo package

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.util.Log
import org.json.JSONObject

class EnrichmentWebViewClient(
    private val onLog: (String) -> Unit,
    private val onDataReceived: (String) -> Unit
) : WebViewClient() {

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url == null || view == null) return
        
        onLog("Pagina caricata: $url")
        
        // 🔥 FIX REDIRECT: Inietta SEMPRE l'hook. 
        // Google Maps redirecta da ?cid= a /place/. Se skippi la seconda pagina, 
        // le XHR non vengono intercettate. Il flag JS interno eviterà doppie installazioni.
        view.evaluateJavascript(NETWORK_HOOK_JS, null)
    }

    @JavascriptInterface
    fun onPlaceDataReceived(jsonData: String) {
        try {
            onDataReceived(jsonData)
        } catch (e: Exception) {
            onLog("ERRORE BRIDGE: ${e.message}")
        }
    }

    companion object {
        val NETWORK_HOOK_JS = """
            (function() {
                if (window.__tpHook) return;
                window.__tpHook = true;
                console.log("NETWORK HOOK v3 INSTALLATO");
                
                const originalFetch = window.fetch;
                window.fetch = function(...args) {
                    const url = typeof args[0] === 'string' ? args[0] : (args[0] ? args[0].url : '');
                    if (url && url.includes('/maps/preview/place')) {
                        return originalFetch.apply(this, args).then(response => {
                            const clone = response.clone();
                            clone.text().then(text => processResponse(text, url));
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
                            processResponse(this.responseText, this._tpUrl);
                        });
                    }
                    return originalSend.apply(this, arguments);
                };

                function processResponse(rawText, url) {
                    try {
                        // --- QUI VA IL TUO PARSER ATTUALE PER NOME, RATING, SITO, ECC. ---
                        // Esempio: let parsedData = parseMyData(rawText);
                        let rawPhotos = []; // Popola questo array con gli URL grezzi che trovi nel JSON
                        
                        // 🔥 FIX FOTO: Filtro rigoroso per scartare tile, street view e avatar
                        const validPhotos = [];
                        const seen = new Set();
                        for (let p of rawPhotos) {
                            if (typeof p === 'string' && p.startsWith('https://lh3.googleusercontent.com/')) {
                                // Scarta avatar utenti
                                if (p.includes('/a/') || p.includes('/a-/') || p.includes('ACg8oc')) continue;
                                // Scarta street view e tile mappa
                                if (p.includes('streetview') || p.includes('cb_client') || p.includes('maps_api_')) continue;
                                if (/=w\d+-h\d+/.test(p) || /=s\d+/.test(p)) continue;
                                
                                // Forza alta risoluzione
                                let cleanUrl = p.split('=')[0] + '=w1080-h608-p-k-no';
                                if (!seen.has(cleanUrl)) {
                                    seen.add(cleanUrl);
                                    validPhotos.push(cleanUrl);
                                }
                            }
                        }
                        
                        if (window.AndroidBridge && window.AndroidBridge.onPlaceDataReceived) {
                            window.AndroidBridge.onPlaceDataReceived(JSON.stringify({
                                url: url,
                                photos: validPhotos.slice(0, 10) // Max 10 foto pulite
                                // ... aggiungi qui gli altri dati parsati ...
                            }));
                        }
                    } catch(e) {
                        console.error("TP Hook Error", e);
                    }
                }
            })();
        """.trimIndent()
    }
}
