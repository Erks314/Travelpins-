# TravelPins — MainActivity ricostruita dal bytecode dell'APK funzionante

## Come è stata fatta questa volta

La volta scorsa avevo scritto codice "plausibile" basandomi solo sui nomi
di classe visibili. Questa volta ho scritto un piccolo disassemblatore Dex
in Python (niente `apktool`/`jadx` disponibili offline) ed ho estratto **le
istruzioni `const-string` in ordine** da ogni metodo di `MainActivity` nel
tuo `app-debug.apk`. Questo mi ha dato il testo esatto di tutti gli script
JS e la logica reale, non un'ipotesi.

## Cosa fa davvero l'app (confermato dal bytecode)

1. Riceve il link via `ACTION_SEND` (`handleIntent`), estrae l'URL con
   regex `https?://\S+`, lo carica nella WebView
2. `onPageFinished` rileva se la pagina è `consent.google.com` (mostra
   pulsante ACCETTA GOOGLE) oppure una pagina di lista (`isGoogleListUrl`:
   contiene `/local/userlists/list/`, `/maps/@/data=`, o `!11m2!2s`)
3. Al tap su SCANSIONA, esegue lo script `GETLIST_SCRIPT`: **non legge il
   DOM**, chiama direttamente `/maps/preview/entitylist/getlist` con un
   parametro `pb` costruito dal `listId` estratto dall'URL, e `credentials:
   'include'` — per questo funziona senza login su liste pubbliche
4. Rimuove il prefisso anti-XSSI `)]}'`, fa `JSON.parse`, poi cammina
   ricorsivamente la struttura cercando array compatibili con un "place"
   (nome in `x[2]`, lat/lng in `x[1][5][2]`/`x[1][5][3]`)
5. Deduplica e salva tutto in `window.__travelpins_places`

Il punto 5 è dove l'app si fermava: i dati restavano lì, mai letti da
Kotlin in forma strutturata (solo un riassunto testuale via `TravelPins.log`).

## L'unica modifica fatta

In `GoogleMapsScraperScript.kt`, subito dopo `window.__travelpins_places =
places;`, ho aggiunto:

```javascript
TravelPinsBridge.onPlacesExtracted(JSON.stringify(places));
```

Questo manda l'array al bridge Kotlin (`TravelPinsJsBridge.onPlacesExtracted`)
che lo parsa con `PlaceJsonParser` (i nomi campo `name`/`address`/`lat`/`lng`/`placeId`
sono quelli esatti prodotti dallo script — confermati dal bytecode, non ipotizzati)
e lo salva in Room.

## File

- **`scraper/GoogleMapsScraperScript.kt`** — i tre script JS reali
  (hook di rete, accetta consenso, getlist) + `isGoogleListUrl`
- **`MainActivity.kt`** — ricostruzione completa: UI nativa (TextView di
  log + pulsanti COPIA TUTTO / PULISCI / ACCETTA GOOGLE / SCANSIONA, più
  il pulsante 🔐 Accedi con Google richiesto dalla spec — questo NON era
  nell'APK, quindi è un placeholder, non una ricostruzione), gestione
  WebViewClient, gestione `intent://` con fallback
- **`importer/TravelPinsJsBridge.kt`** — bridge con le firme reali
  (`log(String)`, `network(String,String,String,String)`) + il nuovo
  `onPlacesExtracted`
- **`importer/PlaceJsonParser.kt`** — parser con i nomi campo esatti
- **`data/`** — Room (Place con campo `placeId`, Category, DAO, repository)
- **`ui/PlacesListScreen.kt`** — schermata Compose separata per sfogliare
  i luoghi salvati con categorie colorate (non tocca la MainActivity di
  scraping)

## Passo 1 — Sostituisci il file

Sostituisci il tuo attuale `app/src/main/java/com/travelpins/test/MainActivity.kt`
con quello qui incluso, e aggiungi le altre cartelle (`data/`, `importer/`,
`scraper/`, `ui/`) dentro `app/src/main/java/com/travelpins/test/`.

## Passo 2 — Dipendenze Gradle

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" // allinea alla tua versione Kotlin
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

Nel Manifest, verifica di avere ancora l'intent-filter per `ACTION_SEND` /
`text/plain` (era già presente nell'APK).

## Passo 3 — Testa lo scraping PRIMA di collegare la UI Compose

Consiglio: builda e installa solo con `MainActivity.kt` + `scraper/` +
`data/` + `importer/` (senza toccare la UI Compose), verifica che il Toast
"Salvati N luoghi nel database" compaia dopo SCANSIONA. Solo dopo aggiungi
una seconda schermata/Activity che mostra `PlacesListScreen` leggendo da
`TravelPinsRepository`.

## Cosa NON è ricostruito (perché non esisteva nell'APK)

- Login Google reale (il pulsante è un placeholder — l'endpoint funziona
  già senza, per liste pubbliche; serve solo se testerai liste private)
- UI di categorizzazione (è la parte nuova che aggiungo io in `ui/`)
- Import incrementale senza duplicati tra scan ripetuti della stessa lista
  (la base c'è con `sourceListId`, manca solo un `UNIQUE index` se lo vuoi)

## Se Google cambia il formato della risposta

Il parser JS (`tryKnownPlace`) è euristico per natura (Google non pubblica
questo endpoint). Se in futuro smette di trovare luoghi, il primo posto da
controllare è `TravelPins.log('NESSUN PLACE TROVATO...')` seguito dai
`TOP[z]` di diagnostica che lo script stesso stampa — mostrano la struttura
reale ricevuta, utile per aggiustare gli indici `x[1][5][2]` ecc.
