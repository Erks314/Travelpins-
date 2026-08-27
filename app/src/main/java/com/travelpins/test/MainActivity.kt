    private fun showImporter() {
        consentAttempted = false
        scanStarted = false
        importStarted = false
        currentListId = null
        currentListName = null

        // PULIZIA TOTALE PREVENTIVA per evitare conflitti di unicità
        lifecycleScope.launch {
            repository.clearAllPlaces()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(COLOR_BG)
            setPadding(28, 80, 28, 40)
        }

        val title = TextView(this).apply {
            text = "TRAVELPINS"
            textSize = 30f
            setTextColor(COLOR_TEXT_PRIMARY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }

        root.addView(title)

        val importTitle = TextView(this).apply {
            text = "Importazione in corso"
            textSize = 22f
            setTextColor(COLOR_TEXT_PRIMARY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        root.addView(importTitle)

        val status = TextView(this).apply {
            tag = "import_status"
            text = "Sto leggendo la lista di Google Maps…"
            textSize = 15f
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        root.addView(status)

        val progress = ProgressBar(this).apply {
            isIndeterminate = true
            tag = "import_progress"
        }

        root.addView(
            progress,
            LinearLayout.LayoutParams(60, 60).apply { bottomMargin = 24 }
        )

        val info = TextView(this).apply {
            text = "Non chiudere TravelPins.\nL'importazione potrebbe richiedere alcuni secondi."
            textSize = 14f
            setTextColor(COLOR_TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(10, 0, 10, 30)
        }

        root.addView(info)

        val cancelButton = Button(this).apply {
            text = "ANNULLA"
            textSize = 13f
            setTextColor(COLOR_TEXT_PRIMARY)
            background = roundedBackground(COLOR_SURFACE, 14f)
            setOnClickListener {
                webView.stopLoading()
                showAppShell(NavTab.HOME)
            }
        }

        root.addView(
            cancelButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52)
        )

        webView.alpha = 0f

        root.addView(webView, LinearLayout.LayoutParams(1, 1))

        outputView = TextView(this).apply {
            text = "TRAVELPINS NETWORK MONITOR"
            visibility = View.GONE
        }

        root.addView(outputView, LinearLayout.LayoutParams(1, 1))

        setContentView(root)
    }
