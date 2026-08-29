# 🟢 PUNTO STABILE - NON MODIFICARE SENZA BACKUP

**Data**: 29 Agosto 2026
**Stato**: FUNZIONANTE

## ✅ Cosa funziona in questa versione

### Importazione elenco
- Condivisione link da Google Maps → importazione 37 luoghi ✓
- Salvataggio in database Room ✓
- Visualizzazione elenco nella Home ✓

### Arricchimento dettagli luogo
- Hook XHR su `/maps/preview/place` ✓
- Parser estrae correttamente:
  - Nome luogo ✓
  - Rating (es. 4.8) ✓
  - Numero recensioni (es. 4300) ✓
  - Sito web ✓
  - Tipi (es. "Attrazione turistica") ✓
  - Descrizione ✓
  - Foto (10 foto ad alta risoluzione) ✓
- Le recensioni singole NON vengono estratte (per scelta)

### Prefetch automatico in background
- `EnrichmentManager` processa tutti i luoghi dopo l'importazione ✓
- Quando l'utente apre un luogo già arricchito → 0 secondi di attesa ✓

## 📁 File chiave (NON modificare senza capire l'impatto)

| File | Ruolo |
|------|-------|
| `PlaceDetailsParser.kt` | Parser specifico per JSON di Google Maps |
| `TravelPinsJsBridge.kt` | Bridge JS↔Kotlin, rimuove prefisso `)]}'` |
| `GoogleMapsScraperScript.kt` | Hook XHR + script importazione lista |
| `EnrichmentManager.kt` | Prefetch automatico in background |
| `PlaceDetailActivity.kt` | Schermata dettaglio luogo + arricchimento on-demand |
| `MainActivity.kt` | Home, importazione elenco, visualizzazione liste |

## 🔑 Dettagli tecnici importanti

### Struttura JSON di Google Maps
- Inizia con prefisso anti-XSSI `)]}'`
- È un JSONArray, non un JSONObject
- I dati del luogo sono in un sotto-array (tipicamente indice 6)
- Le foto hanno URL `lh3.googleusercontent.com` con parametri `\u003d` (= `=`)
- I token iniziano con `0ahUKE`, `CIH`, o sono stringhe alfanumeriche >20 char

### Comportamento Google Maps
- **Primo caricamento pagina**: i dati sono nell'HTML (SSR), nessuna richiesta XHR
- **Secondo caricamento**: Google fa richiesta XHR a `/maps/preview/place`
- Per questo il prefetch funziona meglio del caricamento on-demand

### URL foto
- URL base: `https://lh3.googleusercontent.com/gps-cs-s/ABC...`
- La UI aggiunge i parametri tramite `sizedUrl()`: `=w1200-h800-k-no`
- NON aggiungere parametri nel parser (causa URL duplicati)

## ⚠️ Problemi noti
- La prima apertura di un luogo può andare in timeout (30s)
- Il prefetch richiede ~2-3 minuti per processare tutti i luoghi
- I tipi a volte possono essere sbagliati (raro)

## 🔄 Come ripristinare questo stato
Se qualcosa si rompe:
1. Verifica che i file nella tabella sopra corrispondano a questa versione
2. Il parser deve essere quello "posizionale" (cerca dati in posizioni specifiche)
3. Il bridge deve rimuovere il prefisso `)]}'` prima di parsare
4. L'hook XHR deve essere installato IMMEDIATAMENTE in `onPageFinished`
