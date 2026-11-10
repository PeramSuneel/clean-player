package com.peramsuneel.cleanplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.widget.ImageView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.pm.ActivityInfo
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import com.peramsuneel.cleanplayer.online.OnlineSearchRepository
import com.peramsuneel.cleanplayer.online.OnlineSearchResult

private data class MediaFile(val id: Long, val name: String, val uri: Uri, val folder: String, val isAudio: Boolean, val duration: Long)

class MainActivity : AppCompatActivity() {
    private var isDark = true
    private var isGrid = true
    private var bg = 0xFF101820.toInt()
    private var panel = 0xFF1B2832.toInt()
    private var accent = 0xFFB7D5A8.toInt()
    private var text = 0xFFF3F5F2.toInt()
    private var muted = 0xFF9BA69D.toInt()
    private var currentFolder: String? = null

    private fun applyPalette() {
        if (isDark) {
            bg = 0xFF101820.toInt(); panel = 0xFF1B2832.toInt()
            accent = 0xFFB7D5A8.toInt(); text = 0xFFF3F5F2.toInt(); muted = 0xFF9BA69D.toInt()
        } else {
            bg = 0xFFF4F6F3.toInt(); panel = 0xFFFFFFFF.toInt()
            accent = 0xFF315D48.toInt(); text = 0xFF1B2922.toInt(); muted = 0xFF65736A.toInt()
        }
    }
    private val files = mutableListOf<MediaFile>()
    private val queue = mutableListOf<MediaFile>()
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var currentIndex = 0
    private var showingPlayer = false
    private lateinit var root: LinearLayout
    private lateinit var search: EditText
    private var selectedTab = "Videos"
    private val onlineHandler = Handler(Looper.getMainLooper())
    private val onlineExecutor = Executors.newSingleThreadExecutor()
    private val thumbnailExecutor = Executors.newFixedThreadPool(2)
    private var onlineSearchTask: Future<*>? = null
    private var onlineResolveTask: Future<*>? = null
    private var onlineDebounceRunnable: Runnable? = null
    private var onlineTimeoutRunnable: Runnable? = null
    private var onlineMode = false
    private var onlineLoading = false
    private var onlineError: String? = null
    private var onlineResults: List<OnlineSearchResult> = emptyList()
    private var homeContent: LinearLayout? = null
    private var onlineSection: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReportManager.install(applicationContext)
        val prefs = getSharedPreferences("clean_player_preferences", MODE_PRIVATE)
        isDark = prefs.getBoolean("dark_theme", true)
        isGrid = prefs.getBoolean("grid_view", true)
        applyPalette()
        window.statusBarColor = bg
        window.navigationBarColor = bg
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        setContentView(root)
        if (hasMediaPermission()) loadLibrary() else requestMediaPermission()
        showHome()
        root.post { CrashReportManager.showIfPresent(this) }
    }

    private fun hasMediaPermission(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestMediaPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        ActivityCompat.requestPermissions(this, permissions, 41)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                loadLibrary()
                showHome()
            } else {
                Toast.makeText(this, "Allow media access to show your folders and files.", Toast.LENGTH_LONG).show()
                showHome()
            }
        }
    }

    private fun loadLibrary() {
        files.clear()
        if (Build.VERSION.SDK_INT >= 33) {
            query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, false)
            query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
        } else {
            query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, false)
            query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
        }
    }

    private fun query(collection: Uri, audio: Boolean) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.RELATIVE_PATH)
        val sort = MediaStore.MediaColumns.DISPLAY_NAME + " COLLATE NOCASE ASC"
        try {
            contentResolver.query(collection, projection, null, null, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val durCol = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val pathCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: "Media"
                    val path = if (pathCol >= 0) c.getString(pathCol).orEmpty().trimEnd('/') else ""
                    val pathSegments = path.split('/').filter { it.isNotBlank() }
                    val isDriveContent = path.contains("com.google.android.apps.docs", ignoreCase = true) ||
                        pathSegments.any { it.equals("Drive", ignoreCase = true) || it.equals("Google Drive", ignoreCase = true) || it.equals("GoogleDrive", ignoreCase = true) }
                    if (isDriveContent) continue
                    val folder = path.substringAfterLast('/', path.ifBlank { "Internal storage" }).ifBlank { "Internal storage" }
                    val uri = ContentUris.withAppendedId(collection, id)
                    files.add(MediaFile(id, name, uri, folder, audio, if (durCol >= 0) c.getLong(durCol) else 0L))
                }
            }
        } catch (_: Exception) { }
    }

    private fun showHome(filter: String = "") {
        cancelOnlineWork()
        showingPlayer = false
        currentFolder = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        window.statusBarColor = bg
        window.navigationBarColor = bg
        root.removeAllViews()
        root.setBackgroundColor(bg)
        root.orientation = LinearLayout.VERTICAL
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(8)) }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(label("Clean Player", 24, text, true), LinearLayout.LayoutParams(0, -2, 1f))
        val viewButton = button(if (isGrid) "▦" else "☷", false)
        viewButton.contentDescription = "Switch grid or list view"
        toolbar.addView(viewButton, LinearLayout.LayoutParams(dp(46), dp(42)).apply { marginEnd = dp(7) })
        viewButton.setOnClickListener { isGrid = !isGrid; getSharedPreferences("clean_player_preferences", MODE_PRIVATE).edit().putBoolean("grid_view", isGrid).apply(); showHome(search.text.toString()) }
        val themeButton = button(if (isDark) "☀" else "☾", false)
        themeButton.contentDescription = "Switch light or dark theme"
        toolbar.addView(themeButton, LinearLayout.LayoutParams(dp(46), dp(42)))
        themeButton.setOnClickListener { isDark = !isDark; getSharedPreferences("clean_player_preferences", MODE_PRIVATE).edit().putBoolean("dark_theme", isDark).apply(); applyPalette(); showHome(search.text.toString()) }
        header.addView(toolbar)
        search = EditText(this).apply {
            hint = "Search videos, songs or folders"
            setSingleLine(true); textSize = 15f; setTextColor(this@MainActivity.text); setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0); setBackgroundColor(panel)
            setText(filter)
        }
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(8) })
        val onlineButton = button(if (onlineMode) "Online ✓" else "Online", onlineMode)
        onlineButton.isEnabled = OnlineSearchRepository.ENABLE_ONLINE_SEARCH
        onlineButton.contentDescription = "Toggle online search"
        searchRow.addView(onlineButton, LinearLayout.LayoutParams(dp(88), dp(50)))
        onlineButton.setOnClickListener {
            onlineMode = !onlineMode
            onlineButton.text = if (onlineMode) "Online ✓" else "Online"
            onlineButton.setBackgroundColor(if (onlineMode) accent else panel)
            onlineError = null
            onlineResults = emptyList()
            if (onlineMode) {
                val query = search.text.toString().trim()
                if (query.isBlank()) onlineError = "Type a name, then search online."
                else startOnlineSearch(query)
            } else {
                cancelOnlineSearchOnly()
                onlineLoading = false
            }
            homeContent?.let { renderOnlineResults(it) }
        }
        header.addView(searchRow, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(15) })
        root.addView(header)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(18), dp(5), dp(18), dp(10)) }
        listOf("Videos", "Audio").forEach { tab ->
            val b = button(tab, tab == selectedTab)
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
            b.setOnClickListener { selectedTab = tab; showHome(search.text.toString()) }
        }
        root.addView(tabs)
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(5), dp(18), dp(30)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        homeContent = content
        onlineSection = null
        populateHomeContent(content, filter)
        if (onlineMode) {
            renderOnlineResults(content)
            if (filter.isNotBlank()) scheduleOnlineSearch(filter)
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!showingPlayer) {
                    populateHomeContent(content, s.toString())
                    if (onlineMode) scheduleOnlineSearch(s.toString()) else renderOnlineResults(content)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun populateHomeContent(content: LinearLayout, filter: String) {
        content.removeAllViews()
        val q = filter.trim().lowercase(Locale.getDefault())
        val visible = files.filter { it.isAudio == (selectedTab == "Audio") && (q.isBlank() || it.name.lowercase(Locale.getDefault()).contains(q) || it.folder.lowercase(Locale.getDefault()).contains(q)) }
        val groups = visible.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        content.addView(label(if (selectedTab == "Videos") "FOLDERS" else "AUDIO FOLDERS", 12, muted, true))
        if (groups.isEmpty()) {
            content.addView(label(if (files.isEmpty()) "No media found yet. Allow access to your media library." else "No matching media found.", 15, muted, false).apply { setPadding(0, dp(25), 0, 0) })
        } else {
            if (isGrid) {
                val grid = GridLayout(this).apply { columnCount = 2; useDefaultMargins = false }
                groups.forEach { (folder, items) ->
                    val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(9), dp(12), dp(9), dp(12)); setBackgroundColor(panel) }
                    val preview = folderThumbnail(items)
                    if (preview != null) card.addView(preview, LinearLayout.LayoutParams(-1, dp(94)))
                    else card.addView(label(if (selectedTab == "Videos") "▰" else "♫", 34, accent, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(8)) })
                    card.addView(label(folder, 15, text, true).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    card.addView(label("${items.size} ${if (selectedTab == "Videos") "videos" else "audio files"}", 12, muted, false).apply { gravity = Gravity.CENTER })
                    card.setOnClickListener { showFolder(folder, items) }
                    val params = GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) }
                    grid.addView(card, params)
                }
                content.addView(grid)
            } else {
                groups.forEach { (folder, items) ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9)); setBackgroundColor(panel) }
                    val preview = folderThumbnail(items)
                    if (preview != null) row.addView(preview, LinearLayout.LayoutParams(dp(88), dp(66)).apply { marginEnd = dp(12) })
                    else row.addView(label(if (selectedTab == "Videos") "▰" else "♫", 27, accent, true), LinearLayout.LayoutParams(dp(88), dp(66)).apply { gravity = Gravity.CENTER_VERTICAL })
                    val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    details.addView(label(folder, 15, text, true))
                    details.addView(label("${items.size} ${if (selectedTab == "Videos") "videos" else "audio files"}", 12, muted, false))
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    row.setOnClickListener { showFolder(folder, items) }
                    content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
                }
            }
        }
    }

    private fun showFolder(folder: String, items: List<MediaFile>) {
        cancelOnlineWork()
        onlineMode = false
        currentFolder = folder
        root.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(10)) }
        val back = button("‹", false); head.addView(back, LinearLayout.LayoutParams(dp(48), dp(44))); back.setOnClickListener { showHome() }
        head.addView(label(folder, 21, text, true))
        root.addView(head)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(5), dp(16), dp(20)) }
        scroll.addView(list); root.addView(scroll)
        items.forEachIndexed { index, file ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9)); setBackgroundColor(panel) }
            val preview = mediaThumbnail(file)
            if (preview != null) row.addView(preview, LinearLayout.LayoutParams(dp(104), dp(68)).apply { marginEnd = dp(12) })
            else row.addView(label(if (file.isAudio) "♫" else "▶", 27, accent, true), LinearLayout.LayoutParams(dp(104), dp(68)).apply { gravity = Gravity.CENTER_VERTICAL })
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(label(file.name, 15, text, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            details.addView(label(formatDuration(file.duration), 12, muted, false))
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
            list.addView(row, lp)
            row.setOnClickListener { playItems(items, index) }
        }
    }

    private fun folderThumbnail(items: List<MediaFile>): ImageView? {
        val firstVideo = items.firstOrNull { !it.isAudio } ?: return null
        return mediaThumbnail(firstVideo)
    }

    private fun mediaThumbnail(file: MediaFile): ImageView? {
        if (Build.VERSION.SDK_INT < 29 || file.isAudio) return null
        return try {
            val bitmap: Bitmap = contentResolver.loadThumbnail(file.uri, Size(dp(220), dp(140)), null)
            ImageView(this).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel) }
        } catch (_: Exception) { null }
    }

    private fun playItems(items: List<MediaFile>, index: Int) {
        cancelOnlineWork()
        onlineMode = false
        queue.clear(); queue.addAll(items); currentIndex = index
        val exo = ExoPlayer.Builder(this).build()
        player?.release(); player = exo
        exo.setMediaItems(queue.map { MediaItem.fromUri(it.uri) }, index, 0L)
        exo.prepare(); exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { currentIndex = exo.currentMediaItemIndex; updatePlayerTitle() }
        })
        showPlayer()
    }

    private fun showPlayer() {
        showingPlayer = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        root.removeAllViews()
        root.setPadding(0, 0, 0, 0)

        // Keep the video surface full-screen; controls are transparent overlays inside PlayerView.
        val pv = PlayerView(this).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            player = this@MainActivity.player
            setBackgroundColor(0xFF000000.toInt())
            keepScreenOn = true
        }
        playerView = pv
        root.addView(pv, LinearLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0x55000000)
        }
        val close = playerOverlayButton("‹", 26)
        top.addView(close, LinearLayout.LayoutParams(dp(48), dp(44)))
        close.setOnClickListener { player?.pause(); showHome() }
        val now = label(queue.getOrNull(currentIndex)?.name ?: "Now playing", 15, text, true)
        top.addView(now, LinearLayout.LayoutParams(0, -2, 1f))
        pv.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(18))
            setBackgroundColor(0x55000000)
        }
        val prev = playerOverlayButton("|◀", 17)
        val back = playerOverlayButton("−10", 17)
        val toggle = playerOverlayButton("Ⅱ", 24)
        val forward = playerOverlayButton("+10", 17)
        val next = playerOverlayButton("▶|", 17)
        listOf(prev, back, toggle, forward, next).forEachIndexed { i, b ->
            controls.addView(b, LinearLayout.LayoutParams(0, dp(if (i == 2) 58 else 48), if (i == 2) 1.25f else 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        prev.setOnClickListener { player?.seekToPreviousMediaItem() }
        back.setOnClickListener { player?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) } }
        toggle.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            toggle.text = if (player?.isPlaying == true) "Ⅱ" else "▶"
        }
        forward.setOnClickListener { player?.let { it.seekTo((it.currentPosition + 10_000).coerceAtMost(it.duration.coerceAtLeast(0))) } }
        next.setOnClickListener { player?.seekToNextMediaItem() }
        pv.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        updatePlayerTitle()
    }

    private fun cancelOnlineSearchOnly() {
        onlineDebounceRunnable?.let { onlineHandler.removeCallbacks(it) }
        onlineDebounceRunnable = null
        onlineTimeoutRunnable?.let { onlineHandler.removeCallbacks(it) }
        onlineTimeoutRunnable = null
        onlineSearchTask?.cancel(true)
        onlineSearchTask = null
    }

    private fun cancelOnlineWork() {
        cancelOnlineSearchOnly()
        onlineResolveTask?.cancel(true)
        onlineResolveTask = null
        onlineLoading = false
    }

    private fun scheduleOnlineSearch(query: String) {
        cancelOnlineSearchOnly()
        onlineError = null
        onlineResults = emptyList()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            onlineLoading = false
            renderOnlineResults(homeContent ?: return)
            return
        }
        onlineLoading = true
        homeContent?.let { renderOnlineResults(it) }
        val runnable = Runnable { startOnlineSearch(trimmed) }
        onlineDebounceRunnable = runnable
        onlineHandler.postDelayed(runnable, 450L)
    }

    private fun hasInternetConnection(): Boolean {
        return try {
            val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun startOnlineSearch(query: String) {
        if (!OnlineSearchRepository.ENABLE_ONLINE_SEARCH) {
            onlineLoading = false
            onlineError = "Online search is disabled."
            homeContent?.let { renderOnlineResults(it) }
            return
        }
        if (!hasInternetConnection()) {
            onlineLoading = false
            onlineError = "No network connection. Connect to the internet and try again."
            homeContent?.let { renderOnlineResults(it) }
            return
        }
        cancelOnlineSearchOnly()
        onlineLoading = true
        onlineError = null
        homeContent?.let { renderOnlineResults(it) }
        val task = onlineExecutor.submit {
            try {
                val found = OnlineSearchRepository.search(query)
                onlineHandler.post {
                    clearOnlineTimeout()
                    if (!isFinishing && onlineMode && !showingPlayer && search.text.toString().trim() == query) {
                        onlineLoading = false
                        onlineResults = found
                        onlineError = if (found.isEmpty()) "No results found." else null
                        homeContent?.let { renderOnlineResults(it) }
                    }
                }
            } catch (error: Exception) {
                onlineHandler.post {
                    clearOnlineTimeout()
                    if (!isFinishing && onlineMode && !showingPlayer) {
                        onlineLoading = false
                        onlineError = if (error is InterruptedException) "Search cancelled." else "Could not load online results. Please try again."
                        homeContent?.let { renderOnlineResults(it) }
                    }
                }
            }
        }
        onlineSearchTask = task
        scheduleOnlineTimeout(task, "Online search timed out. Please try again.")
    }

    private fun resolveOnlineResult(result: OnlineSearchResult) {
        cancelOnlineSearchOnly()
        onlineResolveTask?.cancel(true)
        if (!hasInternetConnection()) {
            onlineLoading = false
            onlineError = "No network connection. Connect to the internet and try again."
            homeContent?.let { renderOnlineResults(it) }
            return
        }
        onlineLoading = true
        onlineError = "Preparing ${result.title}…"
        homeContent?.let { renderOnlineResults(it) }
        val task = onlineExecutor.submit {
            try {
                val playableUrl = OnlineSearchRepository.resolve(result)
                onlineHandler.post {
                    clearOnlineTimeout()
                    if (!isFinishing && onlineMode && !showingPlayer) {
                        onlineLoading = false
                        playExternalUrl(playableUrl, result.title)
                    }
                }
            } catch (_: Exception) {
                onlineHandler.post {
                    clearOnlineTimeout()
                    if (!isFinishing && onlineMode && !showingPlayer) {
                        onlineLoading = false
                        onlineError = "Could not open this result. Choose another result or try again."
                        homeContent?.let { renderOnlineResults(it) }
                    }
                }
            }
        }
        onlineResolveTask = task
        scheduleOnlineTimeout(task, "Opening this result timed out. Please try again.")
    }

    private fun scheduleOnlineTimeout(task: Future<*>, message: String) {
        clearOnlineTimeout()
        val timeout = Runnable {
            if (!task.isDone) {
                task.cancel(true)
                onlineLoading = false
                onlineError = message
                homeContent?.let { renderOnlineResults(it) }
            }
        }
        onlineTimeoutRunnable = timeout
        onlineHandler.postDelayed(timeout, 10_000L)
    }

    private fun clearOnlineTimeout() {
        onlineTimeoutRunnable?.let { onlineHandler.removeCallbacks(it) }
        onlineTimeoutRunnable = null
    }

    private fun renderOnlineResults(content: LinearLayout) {
        onlineSection?.let { content.removeView(it) }
        onlineSection = null
        if (!onlineMode) return
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, dp(8))
        }
        onlineSection = section
        section.addView(label("ONLINE RESULTS", 12, muted, true))
        if (onlineLoading) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            row.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
            row.addView(label(onlineError ?: "Searching supported platforms…", 14, muted, false))
            section.addView(row)
        } else if (onlineError != null) {
            section.addView(label(onlineError!!, 14, muted, false).apply { setPadding(0, dp(12), 0, dp(12)) })
        } else if (onlineResults.isEmpty()) {
            section.addView(label("Tap Online to search YouTube and supported platforms.", 14, muted, false).apply { setPadding(0, dp(12), 0, dp(12)) })
        } else {
            onlineResults.forEach { result ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setBackgroundColor(panel)
                }
                val thumbnail = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFF263238.toInt())
                    contentDescription = "Thumbnail for ${result.title}"
                }
                row.addView(thumbnail, LinearLayout.LayoutParams(dp(112), dp(72)).apply { marginEnd = dp(12) })
                loadOnlineThumbnail(result.thumbnailUrl, thumbnail)
                val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                details.addView(label(result.title, 14, text, true).apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                val durationText = if (result.durationSeconds > 0) formatDuration(result.durationSeconds * 1000) else "Live / unknown duration"
                details.addView(label("${result.serviceName}  •  $durationText", 12, muted, false))
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                row.setOnClickListener { resolveOnlineResult(result) }
                section.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
        content.addView(section)
    }

    private fun loadOnlineThumbnail(url: String?, image: ImageView) {
        if (url.isNullOrBlank()) return
        image.tag = url
        thumbnailExecutor.execute {
            val bitmap = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = true
                connection.inputStream.use { BitmapFactory.decodeStream(it) }.also { connection.disconnect() }
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) image.post {
                if (image.tag == url) image.setImageBitmap(bitmap)
            }
        }
    }

    private fun playExternalUrl(url: String, title: String) {
        cancelOnlineWork()
        onlineMode = false
        queue.clear()
        currentIndex = 0
        val exo = ExoPlayer.Builder(this).build()
        player?.release()
        player = exo
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        showPlayer()
    }

    private fun playerOverlayButton(caption: String, size: Int): Button = Button(this).apply {
        text = caption
        textSize = size.toFloat()
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0x33000000)
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        stateListAnimator = null
        includeFontPadding = false
    }

    private fun updatePlayerTitle() {
        // Keep the playback screen title concise; the playlist remains available via Previous/Next.
    }

    private fun button(caption: String, primary: Boolean): Button = Button(this).apply {
        text = caption; textSize = if (primary) 18f else 13f; isAllCaps = false
        setTextColor(if (primary) 0xFF182116.toInt() else this@MainActivity.text)
        setBackgroundColor(if (primary) accent else panel)
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        stateListAnimator = null
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        cancelOnlineWork()
        onlineExecutor.shutdownNow()
        thumbnailExecutor.shutdownNow()
        player?.release(); player = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showingPlayer) { player?.pause(); showHome() } else if (currentFolder != null) { showHome() } else super.onBackPressed()
    }
}
