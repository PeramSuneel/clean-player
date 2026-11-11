package com.peramsuneel.cleanplayer

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.view.PixelCopy
import android.view.SurfaceView
import android.os.Environment
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.LayoutInflater
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.graphics.BitmapFactory
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.widget.ImageView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.pm.ActivityInfo
import android.widget.FrameLayout
import android.graphics.Color
import android.widget.PopupWindow
import android.graphics.Typeface
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import com.peramsuneel.cleanplayer.online.OnlineSearchRepository
import com.peramsuneel.cleanplayer.online.OnlineSearchResult

private const val HOME_BRAND_LOGO_ENABLED = true
private const val VOLUME_PERCENT_INDICATOR_ENABLED = true
private const val MUTE_BUTTON_ENABLED = true
private const val REPLAY_AT_END_ENABLED = true
private const val PLAYER_MENU_FIX_ENABLED = true
private const val SEEK_PREVIEW_ENABLED = true
private const val PLAYER_VECTOR_ICONS_ENABLED = true
private const val PLAYER_PALETTE_ENABLED = true
private const val PLAYER_SMOOTH_UI_ENABLED = true
private const val SUBTITLE_SIZE_ENABLED = true
private const val SUBTITLE_FILE_ENABLED = true
private const val LOCAL_SUBTITLES_ENABLED = false
private const val PLAYER_TOP_LEFT_ENABLED = true
private const val AUDIO_TRACK_SESSION_MEMORY_ENABLED = true
private const val SPEED_LABEL_LIVE_ENABLED = true
private const val SCREENSHOT_ENABLED = false
private const val REPEAT_ONE_ENABLED = false
private const val AUDIO_ONLY_ENABLED = true
private const val LIVE_SCRUBBING_ENABLED = true
private const val OPTIONS_ROW_MEDIA_ACTIONS_ENABLED = false
private const val PLAYER_TOP_OPTIONS_LAYOUT_ENABLED = true
private const val PLAYER_INLINE_OPTIONS_ENABLED = true
private const val PLAYER_FULLSCREEN_OVERLAY_ENABLED = true
private const val BOTTOM_ROTATION_BUTTON_ENABLED = true
private const val LANDSCAPE_COMPACT_CONTROLS_ENABLED = true
private const val PLAYER_ICON_REFRESH_ENABLED = true
private const val BOTTOM_CONTROLS_CENTERED_ENABLED = true
private const val PINCH_ZOOM_ENABLED = true
private const val NEW_PLAYER_UI_ENABLED = true
private const val PLAYER_NO_YELLOW_ENABLED = true
internal const val NEW_PLAYER_PIP_ENABLED = true

private data class MediaFile(val id: Long, val name: String, val uri: Uri, val folder: String, val isAudio: Boolean, val duration: Long)

private class PlayerSeekBar(
    context: Context,
    private val formatTime: (Long) -> String,
    private val onSeek: (Long) -> Unit,
    private val onInteraction: (Boolean) -> Unit,
    private val onPreview: (Long, Float, Boolean) -> Unit,
    private val onLiveSeek: (Long) -> Unit,
    private val leftInsetRatio: Float? = null,
    private val rightInsetRatio: Float? = null
) : View(context) {
    private val density = resources.displayMetrics.density
    private val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.player_divider)
        strokeWidth = 3f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val bufferedPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.player_buffered)
        strokeWidth = 3f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val progressPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.player_accent)
        strokeWidth = 3f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val thumbPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.player_primary)
        style = android.graphics.Paint.Style.FILL
    }
    private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.player_primary)
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val bubblePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.player_overlay)
        style = android.graphics.Paint.Style.FILL
    }
    private var durationMs = 0L
    private var positionMs = 0L
    private var bufferedMs = 0L
    private var dragging = false
    private var targetMs = 0L
    private val leftInset get() = leftInsetRatio?.let { width * it } ?: 58f * density
    private val rightInset get() = rightInsetRatio?.let { width * it } ?: 58f * density
    private val trackY get() = height / 2f
    private fun trackWidth() = (width - leftInset - rightInset).coerceAtLeast(1f)
    private fun fraction(value: Long) = (value.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    private fun positionFor(value: Long) = leftInset + trackWidth() * fraction(value)

    init {
        isClickable = true
        contentDescription = "Video progress"
        minimumHeight = (48f * density).toInt()
    }

    fun update(position: Long, duration: Long, buffered: Long) {
        durationMs = duration.takeIf { it > 0L } ?: 0L
        positionMs = position.coerceAtLeast(0L)
        bufferedMs = buffered.coerceAtLeast(0L)
        if (!dragging) targetMs = positionMs
        isEnabled = durationMs > 0L
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val left = leftInset
        val right = width - rightInset
        val y = trackY
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText(formatTime(positionMs), 0f, y + 4f * density, textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas.drawText(formatTime(durationMs), width.toFloat(), y + 4f * density, textPaint)
        if (durationMs > 0L) {
            canvas.drawLine(left, y, right, y, trackPaint)
            if (bufferedMs > 0L) canvas.drawLine(left, y, positionFor(bufferedMs.coerceAtMost(durationMs)), y, bufferedPaint)
            val thumbX = positionFor(if (dragging) targetMs else positionMs)
            canvas.drawLine(left, y, thumbX, y, progressPaint)
            canvas.drawCircle(thumbX, y, (if (dragging) 7f else 5f) * density, thumbPaint)
            if (dragging && !SEEK_PREVIEW_ENABLED) {
                val value = formatTime(targetMs)
                val bubbleWidth = textPaint.measureText(value) + 20f * density
                val bubbleHeight = 25f * density
                val bubbleLeft = (thumbX - bubbleWidth / 2f).coerceIn(0f, width - bubbleWidth)
                val bubbleTop = (height - bubbleHeight) / 2f
                canvas.drawRoundRect(bubbleLeft, bubbleTop, bubbleLeft + bubbleWidth, bubbleTop + bubbleHeight, 8f * density, 8f * density, bubblePaint)
                textPaint.textAlign = android.graphics.Paint.Align.CENTER
                canvas.drawText(value, bubbleLeft + bubbleWidth / 2f, bubbleTop + 17f * density, textPaint)
            }
        }
    }

    private fun updateTarget(x: Float) {
        if (durationMs <= 0L) return
        val ratio = ((x - leftInset) / trackWidth()).coerceIn(0f, 1f)
        targetMs = (durationMs * ratio).toLong()
        invalidate()
        if (SEEK_PREVIEW_ENABLED) {
            try { onPreview(targetMs, positionFor(targetMs), dragging) } catch (_: Exception) { }
        }
        if (LIVE_SCRUBBING_ENABLED && dragging) {
            try { onLiveSeek(targetMs) } catch (_: Exception) { }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                onInteraction(true)
                updateTarget(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) updateTarget(event.x)
                return dragging
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    updateTarget(event.x)
                    onSeek(targetMs)
                    dragging = false
                    invalidate()
                    if (SEEK_PREVIEW_ENABLED) {
                        try { onPreview(targetMs, positionFor(targetMs), false) } catch (_: Exception) { }
                    }
                    performClick()
                    onInteraction(false)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    invalidate()
                    if (SEEK_PREVIEW_ENABLED) {
                        try { onPreview(targetMs, positionFor(targetMs), false) } catch (_: Exception) { }
                    }
                    onInteraction(false)
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

class MainActivity : AppCompatActivity() {
    private var isDark = true
    private var isGrid = true
    private var bg = 0xFF000000.toInt()
    private var panel = 0xFF121212.toInt()
    private var accent = 0xFF2F80ED.toInt()
    private var text = 0xFFFFFFFF.toInt()
    private var muted = 0xFF8A8F98.toInt()
    private var currentFolder: String? = null

    private fun applyPalette() {
        if (!PLAYER_PALETTE_ENABLED) return
        try {
            bg = ContextCompat.getColor(this, R.color.player_background)
            panel = ContextCompat.getColor(this, R.color.player_surface)
            accent = ContextCompat.getColor(this, R.color.player_accent)
            text = ContextCompat.getColor(this, R.color.player_primary)
            muted = ContextCompat.getColor(this, R.color.player_secondary)
        } catch (_: Exception) {
            bg = 0xFF000000.toInt(); panel = 0xFF121212.toInt()
            accent = 0xFF2F80ED.toInt(); text = 0xFFFFFFFF.toInt(); muted = 0xFF8A8F98.toInt()
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
    private var playerControls: View? = null
    private var playerSeekBar: PlayerSeekBar? = null
    private var seekBarUpdateRunnable: Runnable? = null
    private var playerTopBar: View? = null
    private var playerSeekIndicator: TextView? = null
    private var seekPreviewHideRunnable: Runnable? = null
    private var playerTitleView: TextView? = null
    private var playPauseButton: ImageButton? = null
    private var controlsHideRunnable: Runnable? = null
    private var gestureMode = 0
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartPosition = 0L
    private var dragTargetPosition = 0L
    private var initialBrightness = 0.5f
    private var initialVolume = 0
    private var lastAudibleVolume = 0
    private var preferredAudioLanguage: String? = null
    private var playerMuteButton: ImageButton? = null
    private var repeatButton: ImageButton? = null
    private var audioOnlyEnabled = false
    private var audioOnlyOverlay: TextView? = null
    private var seekWasPlaying = false
    private var lastLiveSeekAt = 0L
    private var videoZoomMatrix = Matrix()
    private var videoZoomScale = 1f
    private var videoPinching = false
    private var playerControlsLocked = false
    private var playerVideoFlipped = false
    private var videoTransformView: TextureView? = null
    private var zoomLabel: TextView? = null
    private var zoomLabelHideRunnable: Runnable? = null
    private var panLastX = 0f
    private var panLastY = 0f
    private var playerSpeedLabel: TextView? = null
    private var currentPlayerTitle = "Now playing"
    private var playerOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private var rotationManuallySelected = false
    private var rotationButton: ImageButton? = null
    private var playerExpandedOptionsRow: View? = null
    private var playerOptionsScroll: HorizontalScrollView? = null
    private var playerToolbar: LinearLayout? = null
    private var playerControlFrame: FrameLayout? = null
    private var playerCenterControls: LinearLayout? = null
    private var playerExpandButton: ImageButton? = null
    private var playerOptionsPopup: PopupWindow? = null
    private var playerOptionsOpen = false
    private var replacingPlayerPopup = false
    private var suppressPlayerAnimations = false
    private val subtitleFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && SUBTITLE_FILE_ENABLED) applySubtitleFile(uri)
    }

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
        controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
        controlsHideRunnable = null
        cancelOnlineWork()
        showingPlayer = false
        currentFolder = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        window.statusBarColor = bg
        window.navigationBarColor = bg
        root.removeAllViews()
        root.setPadding(0, 0, 0, 0)
        root.setBackgroundColor(bg)
        root.orientation = LinearLayout.VERTICAL
        applyScreenInsets(root)
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(8)) }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (HOME_BRAND_LOGO_ENABLED) {
            try {
                val brandMark = ImageView(this).apply {
                    setImageResource(R.drawable.ic_brand_mark)
                    contentDescription = "Clean Player logo"
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                toolbar.addView(brandMark, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(9) })
            } catch (_: Exception) {
                // Branding is optional; keep the home screen usable if the asset cannot load.
            }
        }
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
        audioOnlyEnabled = false
        currentPlayerTitle = queue.getOrNull(index)?.name ?: "Now playing"
        rotationManuallySelected = false
        playerOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val exo = buildPlayerSafely()
        player?.release(); player = exo
        try { if (SPEED_LABEL_LIVE_ENABLED) playerSpeedLabel?.text = "1X"; exo.setPlaybackParameters(androidx.media3.common.PlaybackParameters(1f)) } catch (_: Exception) { }
        exo.setMediaItems(queue.map { MediaItem.fromUri(it.uri) }, index, 0L)
        exo.prepare(); exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exo.currentMediaItemIndex
                currentPlayerTitle = queue.getOrNull(currentIndex)?.name ?: currentPlayerTitle
                playerExpandedOptionsRow?.visibility = View.GONE
                playerExpandButton?.rotation = 0f
                playerExpandButton?.contentDescription = "Expand playback options"
                playerOptionsScroll?.smoothScrollTo(0, 0)
                resetVideoZoom()
                applySavedAspectRatio()
                updatePlayerTitle()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { updatePlayerTitle() }
            override fun onTracksChanged(tracks: Tracks) {
                if (SUBTITLE_FILE_ENABLED) selectExternalSubtitleTrack(exo)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (REPLAY_AT_END_ENABLED && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    handlePlaybackEnded(exo)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (REPLAY_AT_END_ENABLED && playbackState == Player.STATE_ENDED) handlePlaybackEnded(exo)
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                applyVideoDefaultOrientation(videoSize)
            }
        })
        showPlayer()
    }

    private fun showPlayer() {
        showingPlayer = true
        applyPlayerOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        applyImmersiveMode()
        root.removeAllViews()
        root.setPadding(0, 0, 0, 0)
        root.setBackgroundColor(bg)

        val pv = (if (PINCH_ZOOM_ENABLED) {
            LayoutInflater.from(this).inflate(R.layout.player_view_texture, root, false) as PlayerView
        } else PlayerView(this)).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            player = this@MainActivity.player
            setBackgroundColor(bg)
            keepScreenOn = true
        }
        playerView = pv
        videoTransformView = if (PINCH_ZOOM_ENABLED) pv.videoSurfaceView as? TextureView else null
        pv.clipChildren = false
        pv.clipToPadding = false
        resetVideoZoom()
        applySavedAspectRatio()
        if (SUBTITLE_SIZE_ENABLED) {
            try {
                val savedSize = getSharedPreferences("clean_player_subtitles", MODE_PRIVATE).getString("text_size", "Medium") ?: "Medium"
                applySubtitleTextSize(savedSize, false)
            } catch (_: Exception) { }
        }
        root.addView(pv, LinearLayout.LayoutParams(-1, -1))
        audioOnlyOverlay = TextView(this).apply {
            text = "AUDIO ONLY"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_primary))
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            visibility = View.GONE
            setBackgroundColor(bg)
        }
        pv.addView(audioOnlyOverlay, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                togglePlayerControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val direction = if (e.x < pv.width / 2f) -10_000L else 10_000L
                seekBy(direction)
                showSeekFeedback(if (direction < 0) "−10 sec" else "+10 sec")
                return true
            }
        })
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!PINCH_ZOOM_ENABLED || playerOptionsOpen) return false
                videoPinching = true
                gestureMode = 3
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!PINCH_ZOOM_ENABLED || playerOptionsOpen) return false
                try {
                    val texture = videoTransformView ?: return false
                    val nextScale = (videoZoomScale * detector.scaleFactor).coerceIn(1f, 4f)
                    val factor = nextScale / videoZoomScale
                    videoZoomMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                    videoZoomScale = nextScale
                    applyVideoZoomTransform()
                    showVideoZoomLabel()
                    return true
                } catch (_: Exception) { return false }
            }
        })
        val gestureSurface = View(this).apply {
            isClickable = true
            setOnTouchListener { view, event ->
                try {
                try {
                    if (PINCH_ZOOM_ENABLED) scaleDetector.onTouchEvent(event)
                } catch (_: Exception) { }
                if (playerControlsLocked || playerOptionsOpen) return@setOnTouchListener true
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.pointerCount >= 2) {
                    videoPinching = true
                    gestureMode = 3
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    return@setOnTouchListener true
                }
                if (videoPinching) {
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        videoPinching = false
                        gestureMode = 0
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        scheduleZoomLabelHide()
                        showPlayerControls()
                        scheduleControlsHide()
                    }
                    return@setOnTouchListener true
                }
                if (!(videoZoomScale > 1f && gestureMode == 4)) gestureDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = event.x
                        dragStartY = event.y
                        panLastX = event.x
                        panLastY = event.y
                        dragStartPosition = player?.currentPosition ?: 0L
                        dragTargetPosition = dragStartPosition
                        gestureMode = 0
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - dragStartX
                        val dy = event.y - dragStartY
                        if (gestureMode == 0 && videoZoomScale > 1f &&
                            (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6))) {
                            gestureMode = 4
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        if (gestureMode == 0 && (kotlin.math.abs(dx) > dp(12) || kotlin.math.abs(dy) > dp(12))) {
                            gestureMode = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1 else 2
                            if (gestureMode == 1) {
                                dragStartPosition = player?.currentPosition ?: 0L
                            } else {
                                val attrs = window.attributes
                                initialBrightness = if (attrs.screenBrightness in 0.01f..1f) attrs.screenBrightness else 0.5f
                                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                initialVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            }
                        }
                        if (gestureMode == 4) {
                            videoZoomMatrix.postTranslate(event.x - panLastX, event.y - panLastY)
                            panLastX = event.x
                            panLastY = event.y
                            applyVideoZoomTransform()
                            showVideoZoomLabel()
                        } else if (gestureMode == 1) {
                            val delta = (dx / pv.width.coerceAtLeast(1).toFloat() * 60_000L).toLong()
                            val duration = player?.duration?.takeIf { it > 0 } ?: Long.MAX_VALUE
                            dragTargetPosition = (dragStartPosition + delta).coerceIn(0L, duration)
                            val seconds = delta / 1000L
                            if (SEEK_PREVIEW_ENABLED) {
                                playerSeekIndicator?.text = formatPlayerTime(dragTargetPosition)
                                playerSeekIndicator?.textSize = 28f
                            } else {
                                playerSeekIndicator?.text = "${formatDuration(dragTargetPosition)}   ${if (seconds >= 0) "+" else ""}${seconds}s"
                                playerSeekIndicator?.textSize = 14f
                            }
                            playerSeekIndicator?.visibility = View.VISIBLE
                        } else if (gestureMode == 2) {
                            val fraction = (-dy / pv.height.coerceAtLeast(1).toFloat()).coerceIn(-1f, 1f)
                            if (dragStartX < pv.width / 2f) {
                                val brightness = (initialBrightness + fraction).coerceIn(0.05f, 1f)
                                window.attributes = window.attributes.apply { screenBrightness = brightness }
                                playerSeekIndicator?.text = "Brightness ${(brightness * 100).toInt()}%"
                            } else {
                                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val volume = (initialVolume + (fraction * maxVolume).toInt()).coerceIn(0, maxVolume)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                if (volume > 0) lastAudibleVolume = volume
                                playerMuteButton?.setImageResource(if (NEW_PLAYER_UI_ENABLED) { if (volume == 0) R.drawable.ic_v3_volume_off else R.drawable.ic_v3_volume_up } else { if (volume == 0) R.drawable.ic_player_mute else R.drawable.ic_player_volume })
                                playerMuteButton?.contentDescription = if (volume == 0) "Unmute audio" else "Mute audio"
                                if (VOLUME_PERCENT_INDICATOR_ENABLED) {
                                    val volumePercent = if (maxVolume > 0) (volume * 100f / maxVolume).toInt() else 0
                                    playerSeekIndicator?.text = "Volume $volumePercent%"
                                } else {
                                    playerSeekIndicator?.text = "Volume $volume / $maxVolume"
                                }
                            }
                            playerSeekIndicator?.visibility = View.VISIBLE
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (gestureMode == 1 && event.actionMasked == MotionEvent.ACTION_UP) {
                            player?.seekTo(dragTargetPosition)
                            showSeekFeedback(formatPlayerTime(dragTargetPosition), 300L)
                        } else if (gestureMode == 2) {
                            hideSeekFeedback()
                        } else if (gestureMode == 4) {
                            scheduleZoomLabelHide()
                        }
                        if (gestureMode != 1) playerSeekIndicator?.textSize = 14f
                        if (gestureMode != 0) {
                            showPlayerControls()
                            scheduleControlsHide()
                        }
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        gestureMode = 0
                    }
                }
                true
                } catch (_: Exception) {
                    gestureMode = 0
                    videoPinching = false
                    true
                }
            }
        }
        pv.addView(gestureSurface, FrameLayout.LayoutParams(-1, -1))

        if (NEW_PLAYER_UI_ENABLED) {
            try {
                buildNewPlayerUi(pv)
                return
            } catch (_: Exception) {
                // The isolated v3 overlay must never interrupt playback; remove partial views and fall back.
                playerTopBar?.let { pv.removeView(it) }
                playerControls?.let { pv.removeView(it) }
                playerTopBar = null
                playerControls = null
                playerSeekBar = null
            }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            background = if (PLAYER_FULLSCREEN_OVERLAY_ENABLED) {
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xE6000000.toInt(), 0x00000000)
                )
            } else {
                android.graphics.drawable.ColorDrawable(ContextCompat.getColor(this@MainActivity, R.color.player_overlay))
            }
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(8), dp(6), dp(8), dp(2))
        }
        val close = playerOverlayButton(R.drawable.ic_player_back)
        toolbar.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        close.setOnClickListener { player?.pause(); showHome() }
        val now = label(currentPlayerTitle, 15, text, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        playerTitleView = now
        toolbar.addView(now, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        playerToolbar = toolbar

        val quickOptions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(8), 0, dp(8), 0)
            clipChildren = false
            clipToPadding = false
        }
        val audioTrack = playerOverlayButton(R.drawable.ic_player_audio).apply {
            contentDescription = "Select audio track"
            setOnClickListener { showAudioTrackMenu(this); showPlayerControls() }
        }
        val subtitleTrack = playerOverlayButton(R.drawable.ic_player_subtitles).apply {
            contentDescription = "Select subtitles"
            setOnClickListener { showSubtitleTrackMenu(this); showPlayerControls() }
        }
        val aspect = playerOverlayButton(R.drawable.ic_player_aspect).apply {
            contentDescription = "Aspect ratio"
            setOnClickListener { showAspectRatioMenu(this); showPlayerControls() }
        }
        val mute = if (MUTE_BUTTON_ENABLED) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVolume > 0) lastAudibleVolume = currentVolume
            playerOverlayButton(if (currentVolume == 0) R.drawable.ic_player_mute else R.drawable.ic_player_volume).apply {
                contentDescription = if (currentVolume == 0) "Unmute audio" else "Mute audio"
                setOnClickListener {
                    try {
                        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        if (current > 0) {
                            lastAudibleVolume = current
                            manager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        } else {
                            val restore = lastAudibleVolume.takeIf { it > 0 } ?: (maximum / 2).coerceAtLeast(1)
                            manager.setStreamVolume(AudioManager.STREAM_MUSIC, restore.coerceAtMost(maximum), 0)
                        }
                        val muted = manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                        setImageResource(if (muted) R.drawable.ic_player_mute else R.drawable.ic_player_volume)
                        contentDescription = if (muted) "Unmute audio" else "Mute audio"
                        showPlayerControls()
                        scheduleControlsHide()
                    } catch (_: Exception) { }
                }
            }
        } else null
        playerMuteButton = mute
        val rotate = playerOverlayButton(R.drawable.ic_player_rotate).apply {
            contentDescription = "Rotate screen"
            setOnClickListener {
                try {
                    rotationManuallySelected = true
                    applyPlayerOrientation(if (playerOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    showPlayerControls()
                    scheduleControlsHide()
                } catch (_: Exception) { }
            }
        }
        rotationButton = rotate

        val expandedOptions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            visibility = View.GONE
            alpha = 1f
        }
        val speed = label("1X", 14, text, true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(48)
            contentDescription = "Playback speed"
            isClickable = true
            isFocusable = true
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { showPlaybackSpeedMenu(this) }
        }
        playerSpeedLabel = speed
        expandedOptions.addView(speed, LinearLayout.LayoutParams(dp(54), dp(48)))
        val audioOnly = playerOverlayButton(R.drawable.ic_player_audio_only).apply {
            contentDescription = "Toggle audio-only mode"
            setOnClickListener { toggleAudioOnly(); showPlayerControls(); scheduleControlsHide() }
        }
        expandedOptions.addView(audioOnly, LinearLayout.LayoutParams(dp(48), dp(48)))

        val optionsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, 0)
        }
        val expand = playerOverlayButton(R.drawable.ic_player_chevron).apply {
            contentDescription = "Expand playback options"
            setOnClickListener {
                try {
                    if (!PLAYER_TOP_OPTIONS_LAYOUT_ENABLED || !PLAYER_INLINE_OPTIONS_ENABLED) return@setOnClickListener
                    val expanded = expandedOptions.visibility != View.VISIBLE
                    if (expanded) {
                        expandedOptions.alpha = 0f
                        expandedOptions.visibility = View.VISIBLE
                        if (PLAYER_SMOOTH_UI_ENABLED && !suppressPlayerAnimations) {
                            expandedOptions.animate().alpha(1f).setDuration(150L).start()
                        } else expandedOptions.alpha = 1f
                    } else {
                        expandedOptions.visibility = View.GONE
                    }
                    rotation = if (expanded) 180f else 0f
                    contentDescription = if (expanded) "Collapse playback options" else "Expand playback options"
                    optionsScroll.post {
                        if (expanded) optionsScroll.fullScroll(View.FOCUS_RIGHT)
                        else optionsScroll.smoothScrollTo(0, 0)
                    }
                    showPlayerControls()
                    scheduleControlsHide()
                } catch (_: Exception) { }
            }
        }
        listOf(audioTrack, subtitleTrack, aspect, mute).forEach { button ->
            if (button != null) quickOptions.addView(button, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        if (PLAYER_TOP_OPTIONS_LAYOUT_ENABLED && PLAYER_INLINE_OPTIONS_ENABLED) {
            quickOptions.addView(expandedOptions, LinearLayout.LayoutParams(-2, dp(48)))
        }
        quickOptions.addView(expand, LinearLayout.LayoutParams(dp(48), dp(48)))
        optionsScroll.addView(quickOptions, ViewGroup.LayoutParams(-2, dp(48)))
        playerOptionsScroll = optionsScroll
        top.addView(optionsScroll, LinearLayout.LayoutParams(-1, dp(48)))
        playerExpandedOptionsRow = expandedOptions
        playerExpandButton = expand
        playerTopBar = top
        pv.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0xBF000000.toInt())
            )
        }
        val seekBar = PlayerSeekBar(
            this,
            { millis -> formatPlayerTime(millis) },
            { target ->
                try {
                    val exo = player ?: return@PlayerSeekBar
                    exo.setSeekParameters(SeekParameters.EXACT)
                    exo.seekTo(target)
                } catch (_: Exception) { }
            },
            { interacting ->
                try {
                    val exo = player
                    if (interacting) {
                        controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
                        seekWasPlaying = exo?.playWhenReady == true
                        exo?.pause()
                        if (LIVE_SCRUBBING_ENABLED) exo?.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        showPlayerControls()
                    } else {
                        if (LIVE_SCRUBBING_ENABLED) exo?.setSeekParameters(SeekParameters.EXACT)
                        if (seekWasPlaying) exo?.play()
                        seekWasPlaying = false
                        scheduleControlsHide()
                    }
                } catch (_: Exception) { }
            },
            { targetMs, thumbX, active -> updateSeekPreview(targetMs, pv.width * 0.03f + thumbX, active) },
            { target ->
                try {
                    val exo = player ?: return@PlayerSeekBar
                    val uri = exo.currentMediaItem?.localConfiguration?.uri
                    val online = uri?.scheme.equals("http", true) || uri?.scheme.equals("https", true)
                    val now = SystemClock.elapsedRealtime()
                    if (!online && now - lastLiveSeekAt >= 200L) {
                        lastLiveSeekAt = now
                        exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        exo.seekTo(target)
                    }
                } catch (_: Exception) { }
            }
        )
        playerSeekBar = seekBar
        bottomPanel.addView(seekBar, LinearLayout.LayoutParams(-1, dp(52)))

        val controls = FrameLayout(this).apply {
            setPadding(dp(8), dp(7), dp(8), dp(12))
        }
        val prev = playerOverlayButton(R.drawable.ic_player_previous)
        val back = playerOverlayButton(R.drawable.ic_player_rewind)
        val toggle = playerOverlayButton(if (player?.isPlaying == true) R.drawable.ic_player_pause else R.drawable.ic_player_play)
        val forward = playerOverlayButton(R.drawable.ic_player_forward)
        val next = playerOverlayButton(R.drawable.ic_player_next)
        playPauseButton = toggle
        val centerControls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        listOf(prev, back, toggle, forward, next).forEach { control ->
            val mainButton = control === toggle
            centerControls.addView(control, LinearLayout.LayoutParams(dp(if (mainButton) 56 else 48), dp(if (mainButton) 56 else 48)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        if (BOTTOM_ROTATION_BUTTON_ENABLED) {
            controls.addView(
                rotate,
                FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                    leftMargin = dp(0)
                }
            )
        }
        playerControlFrame = controls
        playerCenterControls = centerControls
        try { controls.addView(centerControls, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) } catch (_: Exception) { }
        prev.setOnClickListener { player?.seekToPreviousMediaItem(); scheduleControlsHide() }
        back.setOnClickListener { seekBy(-10_000L); showSeekFeedback("−10 sec"); scheduleControlsHide() }
        toggle.setOnClickListener {
            player?.let {
                if (it.isPlaying) it.pause() else {
                    if (REPLAY_AT_END_ENABLED && playbackIsAtEnd(it)) it.seekTo(0L)
                    it.play()
                }
            }
            updatePlayerTitle()
            scheduleControlsHide()
        }
        forward.setOnClickListener { seekBy(10_000L); showSeekFeedback("+10 sec"); scheduleControlsHide() }
        next.setOnClickListener { player?.seekToNextMediaItem(); scheduleControlsHide() }
        bottomPanel.addView(controls, LinearLayout.LayoutParams(-1, -2))
        playerControls = bottomPanel
        pv.addView(bottomPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        val seekPreview = label("", 13, ContextCompat.getColor(this, R.color.player_primary), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@MainActivity, R.color.player_overlay))
                cornerRadius = dp(8).toFloat()
            }
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        pv.addView(seekPreview, FrameLayout.LayoutParams(-2, dp(30)))
        seekPreview.tag = "seek-preview"

        val indicator = label("", 14, ContextCompat.getColor(this, R.color.player_primary), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(0xBF000000.toInt())
                cornerRadius = dp(24).toFloat()
            }
            visibility = View.GONE
        }
        playerSeekIndicator = indicator
        pv.addView(indicator, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val zoomIndicator = label("100%", 12, ContextCompat.getColor(this, R.color.player_primary), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = GradientDrawable().apply {
                setColor(0xBF000000.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), ContextCompat.getColor(this@MainActivity, R.color.player_divider))
            }
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        zoomLabel = zoomIndicator
        pv.addView(zoomIndicator, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))


        ViewCompat.setOnApplyWindowInsetsListener(pv) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val gestureBottom = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            top.setPadding(maxOf(cutout.left, bars.left), maxOf(cutout.top, bars.top), maxOf(cutout.right, bars.right), 0)
            toolbar.setPadding(dp(8), dp(6), dp(8), dp(2))
            quickOptions.setPadding(dp(12), 0, dp(12), 0)
            controls.setPadding(dp(8), dp(7), dp(8), dp(12))
            bottomPanel.setPadding(0, 0, 0, maxOf(gestureBottom, bars.bottom, cutout.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(pv)
        applyPlayerLayoutMetrics(resources.configuration.orientation)
        updatePlayerTitle()
        showPlayerControls()
        scheduleControlsHide()
    }

    /**
     * Main, search and folder surfaces draw edge-to-edge, but keep interactive content
     * inside the status bar, navigation bar and display cutout safe areas.
     */
    private fun savePlayerScreenshot(source: View) {
        if (Build.VERSION.SDK_INT < 29 || source.width <= 0 || source.height <= 0) {
            Toast.makeText(this, "Screenshot not available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val location = IntArray(2)
            source.getLocationInWindow(location)
            val rect = android.graphics.Rect(location[0], location[1], location[0] + source.width, location[1] + source.height)
            PixelCopy.request(window, rect, bitmap, { result ->
                if (result != PixelCopy.SUCCESS) {
                    bitmap.recycle()
                    Toast.makeText(this, "Screenshot not available", Toast.LENGTH_SHORT).show()
                    return@request
                }
                var uri: Uri? = null
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "CleanPlayer_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CleanPlayer")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    val output = uri?.let { contentResolver.openOutputStream(it) }
                    if (output == null) throw IllegalStateException("MediaStore output unavailable")
                    output.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    contentResolver.update(uri!!, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    uri?.let { contentResolver.delete(it, null, null) }
                    Toast.makeText(this, "Screenshot not available", Toast.LENGTH_SHORT).show()
                } finally {
                    bitmap.recycle()
                }
            }, onlineHandler)
        } catch (_: Exception) {
            Toast.makeText(this, "Screenshot not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enterPlayerPictureInPicture(pv: PlayerView) {
        if (!NEW_PLAYER_PIP_ENABLED || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            if (player == null || isFinishing) return
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            if (!enterPictureInPictureMode(params)) {
                Toast.makeText(this, "Picture-in-picture is unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Picture-in-picture is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        try {
            if (isInPictureInPictureMode) {
                playerTopBar?.visibility = View.GONE
                playerControls?.visibility = View.GONE
                controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
            } else if (showingPlayer) {
                playerTopBar?.visibility = View.VISIBLE
                playerControls?.visibility = View.VISIBLE
                showPlayerControls()
                scheduleControlsHide()
            }
        } catch (_: Exception) { }
    }

    /**
     * V3 controls are isolated from the legacy controls and reuse the existing ExoPlayer,
     * track menus, seeking callbacks, rotation and playback state.
     */
    private fun buildNewPlayerUi(pv: PlayerView) {
        val seek = PlayerSeekBar(
            this,
            { millis -> formatPlayerTime(millis) },
            { target ->
                try {
                    val exo = player ?: return@PlayerSeekBar
                    exo.setSeekParameters(SeekParameters.EXACT)
                    exo.seekTo(target)
                } catch (_: Exception) { }
            },
            { interacting ->
                try {
                    val exo = player
                    if (interacting) {
                        controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
                        seekWasPlaying = exo?.playWhenReady == true
                        exo?.pause()
                        if (LIVE_SCRUBBING_ENABLED) exo?.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        showPlayerControls()
                    } else {
                        if (LIVE_SCRUBBING_ENABLED) exo?.setSeekParameters(SeekParameters.EXACT)
                        if (seekWasPlaying) exo?.play()
                        seekWasPlaying = false
                        scheduleControlsHide()
                    }
                } catch (_: Exception) { }
            },
            { targetMs, thumbX, active -> updateSeekPreview(targetMs, thumbX, active) },
            { target ->
                try {
                    val exo = player ?: return@PlayerSeekBar
                    val uri = exo.currentMediaItem?.localConfiguration?.uri
                    val online = uri?.scheme.equals("http", true) || uri?.scheme.equals("https", true)
                    val now = SystemClock.elapsedRealtime()
                    if (!online && now - lastLiveSeekAt >= 200L) {
                        lastLiveSeekAt = now
                        exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        exo.seekTo(target)
                    }
                } catch (_: Exception) { }
            },
            leftInsetRatio = 0.095f,
            rightInsetRatio = 0.064f
        )
        playerSeekBar = seek

        var overlayResult: NewPlayerOverlay.Result? = null
        val ui = NewPlayerOverlay(
            this,
            currentPlayerTitle,
            seek,
            onBack = { player?.pause(); showHome() },
            onScreenshot = { savePlayerScreenshot(pv) },
            onMute = { button ->
                try {
                    val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    if (current > 0) {
                        lastAudibleVolume = current
                        manager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    } else {
                        val restore = lastAudibleVolume.takeIf { it > 0 } ?: (maximum / 2).coerceAtLeast(1)
                        manager.setStreamVolume(AudioManager.STREAM_MUSIC, restore.coerceAtMost(maximum), 0)
                    }
                    val muted = manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                    button.setImageResource(if (muted) R.drawable.ic_v3_volume_off else R.drawable.ic_v3_volume_up)
                    button.contentDescription = if (muted) "Unmute audio" else "Mute audio"
                    showPlayerControls(); scheduleControlsHide()
                } catch (_: Exception) { }
            },
            onRotate = { button ->
                try {
                    rotationManuallySelected = true
                    applyPlayerOrientation(if (playerOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    showPlayerControls(); scheduleControlsHide()
                } catch (_: Exception) { }
            },
            onAudio = { showAudioTrackMenu(it); showPlayerControls() },
            onSubtitles = { showSubtitleTrackMenu(it); showPlayerControls() },
            onSpeed = { showPlaybackSpeedMenu(it); showPlayerControls() },
            onPictureInPicture = { enterPlayerPictureInPicture(pv) },
            onPlaylist = { anchor ->
                try {
                    if (queue.isEmpty()) {
                        Toast.makeText(this, "No folder playlist available", Toast.LENGTH_SHORT).show()
                    } else {
                        val labels = queue.mapIndexed { index, file ->
                            (if (index == currentIndex) "▶ " else "") + file.name
                        }
                        val actions = queue.mapIndexed { index, _ ->
                            { player?.seekTo(index, 0L); player?.play(); currentIndex = index; updatePlayerTitle() }
                        }
                        showPlayerPopup(anchor, labels, actions) { }
                    }
                } catch (_: Exception) { }
            },
            onFlip = {
                try {
                    playerVideoFlipped = !playerVideoFlipped
                    videoTransformView?.scaleX = if (playerVideoFlipped) -1f else 1f
                    showPlayerControls(); scheduleControlsHide()
                } catch (_: Exception) { }
            },
            onRepeat = { button ->
                try {
                    val exo = player
                    if (exo != null) {
                        exo.repeatMode = if (exo.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                        button.setImageResource(if (exo.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_v3_repeat_one else R.drawable.ic_v3_repeat)
                        button.imageTintList = android.content.res.ColorStateList.valueOf(if (exo.repeatMode == Player.REPEAT_MODE_ONE) 0xFF2F80ED.toInt() else text)
                        showPlayerControls(); scheduleControlsHide()
                    }
                } catch (_: Exception) { }
            },
            onLock = { button ->
                try {
                    overlayResult?.let { result ->
                        playerControlsLocked = !playerControlsLocked
                        button.setImageResource(if (playerControlsLocked) R.drawable.ic_v3_lock_open else R.drawable.ic_v3_lock)
                        if (playerControlsLocked) {
                            controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
                            result.topLayer.visibility = View.GONE
                            for (index in 0 until result.bottomLayer.childCount) {
                                val child = result.bottomLayer.getChildAt(index)
                                if (child !== button && child !is androidx.constraintlayout.widget.Guideline) child.visibility = View.GONE
                            }
                        } else {
                            result.topLayer.visibility = View.VISIBLE
                            for (index in 0 until result.bottomLayer.childCount) result.bottomLayer.getChildAt(index).visibility = View.VISIBLE
                            showPlayerControls(); scheduleControlsHide()
                        }
                    }
                } catch (_: Exception) { }
            },
            onPrevious = { player?.seekToPreviousMediaItem(); scheduleControlsHide() },
            onBack10 = { seekBy(-10_000L); showSeekFeedback("−10 sec"); scheduleControlsHide() },
            onPlayPause = { button ->
                try {
                    player?.let {
                        if (it.isPlaying) it.pause() else {
                            if (REPLAY_AT_END_ENABLED && playbackIsAtEnd(it)) it.seekTo(0L)
                            it.play()
                        }
                    }
                    updatePlayerTitle(); scheduleControlsHide()
                } catch (_: Exception) { }
            },
            onForward10 = { seekBy(10_000L); showSeekFeedback("+10 sec"); scheduleControlsHide() },
            onNext = { player?.seekToNextMediaItem(); scheduleControlsHide() },
            onAspect = { showAspectRatioMenu(it); showPlayerControls() }
        ).build()

        overlayResult = ui
        playerTitleView = ui.titleView
        playerTopBar = ui.topLayer
        playerControls = ui.bottomLayer
        playPauseButton = ui.playButton
        playerMuteButton = ui.muteButton
        try {
            val initialVolume = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamVolume(AudioManager.STREAM_MUSIC)
            ui.muteButton.setImageResource(if (initialVolume == 0) R.drawable.ic_v3_volume_off else R.drawable.ic_v3_volume_up)
        } catch (_: Exception) { }
        rotationButton = ui.rotationButton
        repeatButton = ui.repeatButton
        pv.addView(ui.topLayer, FrameLayout.LayoutParams(-1, -1))
        pv.addView(ui.bottomLayer, FrameLayout.LayoutParams(-1, -1))

        val preview = label("", 13, ContextCompat.getColor(this, R.color.player_primary), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@MainActivity, R.color.player_overlay))
                cornerRadius = dp(8).toFloat()
            }
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        pv.addView(preview, FrameLayout.LayoutParams(-2, dp(30)))
        preview.tag = "seek-preview"

        val indicator = label("", 14, ContextCompat.getColor(this, R.color.player_primary), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(0xBF000000.toInt())
                cornerRadius = dp(24).toFloat()
            }
            visibility = View.GONE
        }
        playerSeekIndicator = indicator
        pv.addView(indicator, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        val zoomIndicator = label("100%", 12, ContextCompat.getColor(this, R.color.player_primary), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = GradientDrawable().apply {
                setColor(0xBF000000.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), ContextCompat.getColor(this@MainActivity, R.color.player_divider))
            }
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        zoomLabel = zoomIndicator
        pv.addView(zoomIndicator, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        ViewCompat.setOnApplyWindowInsetsListener(pv) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val gestureBottom = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            val safeLeft = maxOf(cutout.left, bars.left)
            val safeTop = maxOf(cutout.top, bars.top)
            val safeRight = maxOf(cutout.right, bars.right)
            val safeBottom = maxOf(gestureBottom, bars.bottom, cutout.bottom)
            ui.topLayer.setPadding(safeLeft, safeTop, safeRight, 0)
            ui.bottomLayer.setPadding(safeLeft, 0, safeRight, safeBottom)
            insets
        }
        ViewCompat.requestApplyInsets(pv)
        updatePlayerTitle()
        showPlayerControls()
        scheduleControlsHide()
    }

    private fun showAudioTrackMenu(anchor: View) {
        val exo = player ?: return
        val options = mutableListOf<Pair<String, (() -> Unit)?>>()
        try {
            val groups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            val count = groups.sumOf { it.length }
            if (count == 0) options.add("No audio" to null)
            var ordinal = 0
            groups.forEach { group ->
                for (i in 0 until group.length) {
                    ordinal++
                    val format = group.getTrackFormat(i)
                    val languageCode = format.language?.takeIf { it.isNotBlank() }
                    val language = languageCode?.let {
                        try { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).ifBlank { it } }
                        catch (_: Exception) { it }
                    } ?: ("Track " + ordinal)
                    val details = listOfNotNull(
                        format.label?.takeIf { it.isNotBlank() },
                        format.sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT),
                        when (format.channelCount) {
                            1 -> "Mono"
                            2 -> "Stereo"
                            6 -> "5.1"
                            8 -> "7.1"
                            else -> if (format.channelCount > 0) format.channelCount.toString() + "ch" else null
                        }
                    ).distinct().joinToString(" ")
                    val selected = group.isTrackSelected(i)
                    val title = (if (selected) "✓ " else "") + language +
                        (if (details.isNotBlank()) " - " + details else "")
                    options.add(title to {
                        try {
                            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                                .build()
                            if (AUDIO_TRACK_SESSION_MEMORY_ENABLED) preferredAudioLanguage = languageCode
                        } catch (_: Exception) {
                            Toast.makeText(this, "Unable to switch audio track", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        } catch (_: Exception) {
            options.clear()
            options.add("Audio tracks unavailable" to null)
        }
        showPlayerPopup(anchor, options.map { it.first }, options.map { it.second }) { }
    }

    private fun showSubtitleTrackMenu(anchor: View) {
        val exo = player ?: return
        val options = mutableListOf<Pair<String, (() -> Unit)?>>()
        options.add("Off" to {
            try {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
            } catch (_: Exception) { }
        })
        try {
            exo.currentTracks.groups.forEach { group ->
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val language = format.language?.takeIf { it.isNotBlank() } ?: "Unknown language"
                        val codec = format.sampleMimeType?.substringAfter('/') ?: "Subtitle"
                        val selected = group.isTrackSelected(i)
                        val title = "${if (selected) "✓ " else ""}$language · $codec"
                        options.add(title to {
                            try {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                                    .build()
                            } catch (_: Exception) { }
                        })
                    }
                }
            }
        } catch (_: Exception) { }
        if (SUBTITLE_SIZE_ENABLED) {
            try {
                val savedSize = getSharedPreferences("clean_player_subtitles", MODE_PRIVATE).getString("text_size", "Medium") ?: "Medium"
                listOf("Small", "Medium", "Large").forEach { size ->
                    options.add("${if (savedSize == size) "✓ " else ""}$size subtitle text" to {
                        applySubtitleTextSize(size)
                    })
                }
            } catch (_: Exception) { }
        }
        options.add("Load from storage" to {
            if (SUBTITLE_FILE_ENABLED) {
                try {
                    subtitleFilePicker.launch(arrayOf("*/*"))
                } catch (_: Exception) {
                    Toast.makeText(this, "Couldn't open subtitle files", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Subtitle file loading is unavailable", Toast.LENGTH_SHORT).show()
            }
        })
        showPlayerPopup(anchor, options.map { it.first }, options.map { it.second }) { }
    }

    private fun applySubtitleFile(uri: Uri) {
        try {
            val exo = player ?: throw IllegalStateException("Player unavailable")
            val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
            val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mimeType = when (extension) {
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "vtt" -> MimeTypes.TEXT_VTT
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                else -> {
                    Toast.makeText(this, "Choose an SRT, VTT, ASS or SSA file", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Some document providers grant temporary access only; continue with the granted URI.
            }

            val itemIndex = exo.currentMediaItemIndex
            val currentItem = exo.currentMediaItem ?: throw IllegalStateException("No active video")
            val position = exo.currentPosition
            val shouldPlay = exo.playWhenReady
            val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
                .setId("clean-player-external-subtitle")
                .setMimeType(mimeType)
                .setLanguage("und")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val updatedItem = currentItem.buildUpon()
                .setSubtitleConfigurations(listOf(subtitle))
                .build()

            exo.replaceMediaItem(itemIndex, updatedItem)
            exo.seekTo(itemIndex, position)
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            exo.playWhenReady = shouldPlay
            Toast.makeText(this, "Subtitle loaded", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't load subtitle; video continues", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAudioOnly() {
        if (!AUDIO_ONLY_ENABLED) return
        try {
            val surface = playerView?.videoSurfaceView ?: throw IllegalStateException("Video surface unavailable")
            audioOnlyEnabled = !audioOnlyEnabled
            surface.visibility = if (audioOnlyEnabled) View.INVISIBLE else View.VISIBLE
            audioOnlyOverlay?.visibility = if (audioOnlyEnabled) View.VISIBLE else View.GONE
            Toast.makeText(this, if (audioOnlyEnabled) "Audio-only mode on" else "Video mode on", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            audioOnlyEnabled = false
            Toast.makeText(this, "Unable to change audio-only mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRepeatOne() {
        if (!REPEAT_ONE_ENABLED) return
        try {
            val exo = player ?: return
            val repeatOne = exo.repeatMode != Player.REPEAT_MODE_ONE
            exo.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            repeatButton?.contentDescription = if (repeatOne) "Turn repeat one off" else "Repeat current video"
            repeatButton?.imageTintList = android.content.res.ColorStateList.valueOf(
                if (repeatOne) accent else ContextCompat.getColor(this, R.color.player_primary)
            )
            Toast.makeText(this, if (repeatOne) "Repeat one on" else "Repeat off", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to change repeat mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun capturePlayerScreenshot() {
        if (!SCREENSHOT_ENABLED) return
        try {
            if (Build.VERSION.SDK_INT < 29) {
                Toast.makeText(this, "Screenshot saving requires Android 10 or later", Toast.LENGTH_SHORT).show()
                return
            }
            val surface = playerView?.videoSurfaceView as? SurfaceView
                ?: throw IllegalStateException("Video surface unavailable")
            if (surface.width <= 0 || surface.height <= 0) throw IllegalStateException("Video frame unavailable")
            val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(surface, bitmap, { result ->
                try {
                    if (result != PixelCopy.SUCCESS) {
                        bitmap.recycle()
                        Toast.makeText(this, "Screenshot capture failed", Toast.LENGTH_SHORT).show()
                        return@request
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "CleanPlayer_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CleanPlayer")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Could not create image")
                    contentResolver.openOutputStream(uri)?.use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw IllegalStateException("Image encoding failed")
                    } ?: throw IllegalStateException("Could not write image")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    bitmap.recycle()
                    Toast.makeText(this, "Screenshot saved to Pictures/CleanPlayer", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    try { bitmap.recycle() } catch (_: Exception) { }
                    Toast.makeText(this, "Couldn't save screenshot", Toast.LENGTH_SHORT).show()
                }
            }, onlineHandler)
        } catch (_: Exception) {
            Toast.makeText(this, "Screenshot unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyVideoZoomTransform() {
        try {
            val texture = videoTransformView ?: return
            if (texture.width <= 0 || texture.height <= 0) return
            if (videoZoomScale <= 1.001f) {
                videoZoomScale = 1f
                videoZoomMatrix.reset()
            } else {
                val bounds = RectF(0f, 0f, texture.width.toFloat(), texture.height.toFloat())
                videoZoomMatrix.mapRect(bounds)
                val dx = when {
                    bounds.left > 0f -> -bounds.left
                    bounds.right < texture.width -> texture.width - bounds.right
                    else -> 0f
                }
                val dy = when {
                    bounds.top > 0f -> -bounds.top
                    bounds.bottom < texture.height -> texture.height - bounds.bottom
                    else -> 0f
                }
                videoZoomMatrix.postTranslate(dx, dy)
            }
            texture.setTransform(videoZoomMatrix)
            texture.invalidate()
        } catch (_: Exception) { }
    }

    private fun showVideoZoomLabel() {
        try {
            val view = zoomLabel ?: return
            zoomLabelHideRunnable?.let { onlineHandler.removeCallbacks(it) }
            view.text = "${(videoZoomScale * 100f).toInt()}%"
            view.visibility = View.VISIBLE
            view.alpha = 1f
        } catch (_: Exception) { }
    }

    private fun scheduleZoomLabelHide() {
        try {
            zoomLabelHideRunnable?.let { onlineHandler.removeCallbacks(it) }
            val hide = Runnable {
                try { zoomLabel?.animate()?.alpha(0f)?.setDuration(180L)?.withEndAction { zoomLabel?.visibility = View.GONE }?.start() }
                catch (_: Exception) { zoomLabel?.visibility = View.GONE }
            }
            zoomLabelHideRunnable = hide
            onlineHandler.postDelayed(hide, 900L)
        } catch (_: Exception) { }
    }

    private fun resetVideoZoom() {
        try {
            videoZoomScale = 1f
            playerVideoFlipped = false
            videoTransformView?.scaleX = 1f
            videoZoomMatrix.reset()
            videoPinching = false
            gestureMode = 0
            videoTransformView?.setTransform(videoZoomMatrix)
            zoomLabel?.visibility = View.GONE
            zoomLabel?.alpha = 1f
            zoomLabelHideRunnable?.let { onlineHandler.removeCallbacks(it) }
            zoomLabelHideRunnable = null
        } catch (_: Exception) { }
    }

    private fun applySubtitleTextSize(size: String, persist: Boolean = true) {
        if (!SUBTITLE_SIZE_ENABLED) return
        try {
            val subtitleView = playerView?.subtitleView ?: return
            val sizeSp = when (size) {
                "Small" -> 14f
                "Large" -> 22f
                else -> 18f
            }
            subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (persist) {
                getSharedPreferences("clean_player_subtitles", MODE_PRIVATE)
                    .edit().putString("text_size", size).apply()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't change subtitle size", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectExternalSubtitleTrack(exo: ExoPlayer) {
        try {
            exo.currentTracks.groups.forEach { group ->
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        if (group.getTrackFormat(i).id == "clean-player-external-subtitle" && !group.isTrackSelected(i)) {
                            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                                .build()
                            return
                        }
                    }
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't activate subtitle; video continues", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaybackSpeedMenu(anchor: View) {
        val exo = player ?: return
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val current = exo.playbackParameters.speed
        val labels = speeds.map { speed ->
            val name = if (speed == speed.toInt().toFloat()) "${speed.toInt()}X" else "${speed}X"
            "${if (kotlin.math.abs(current - speed) < 0.01f) "✓ " else ""}$name"
        }
        val actions = speeds.map { speed ->
            {
                try {
                    exo.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
                    if (SPEED_LABEL_LIVE_ENABLED) playerSpeedLabel?.text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() + "X" else speed.toString() + "X"
                } catch (_: Exception) {
                    Toast.makeText(this, "Unable to change playback speed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        showPlayerPopup(anchor, labels, actions) { }
    }

    private fun applyAspectRatioSelection(label: String) {
        try {
            val view = playerView ?: return
            val mode = when (label) {
                "Fill" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                "Zoom" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            view.resizeMode = mode
            val contentFrame = view.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            if (label == "16:9" || label == "4:3") {
                contentFrame?.setAspectRatio(if (label == "16:9") 16f / 9f else 4f / 3f)
            } else {
                contentFrame?.setAspectRatio(0f)
            }
            resetVideoZoom()
        } catch (_: Exception) { }
    }

    private fun applySavedAspectRatio() {
        try {
            val uri = queue.getOrNull(currentIndex)?.uri ?: player?.currentMediaItem?.localConfiguration?.uri
            val key = uri?.toString() ?: "default"
            val selected = getSharedPreferences("clean_player_aspect_ratio", MODE_PRIVATE).getString(key, "Fit") ?: "Fit"
            applyAspectRatioSelection(selected)
        } catch (_: Exception) { }
    }

    private fun showAspectRatioMenu(anchor: View) {
        val choices = listOf("Fit", "Fill", "Zoom", "16:9", "4:3")
        val prefs = getSharedPreferences("clean_player_aspect_ratio", MODE_PRIVATE)
        val uri = queue.getOrNull(currentIndex)?.uri ?: player?.currentMediaItem?.localConfiguration?.uri
        val key = uri?.toString() ?: "default"
        val current = prefs.getString(key, "Fit") ?: "Fit"
        val rows = choices.map { choice ->
            "${if (choice == current) "✓ " else ""}$choice" to {
                try {
                    applyAspectRatioSelection(choice)
                    prefs.edit().putString(key, choice).apply()
                } catch (_: Exception) { }
            }
        }
        showPlayerPopup(anchor, rows.map { it.first }, rows.map { it.second }) { }
    }

    private fun showPlayerPopup(
        anchor: View,
        labels: List<String>,
        actions: List<(() -> Unit)?>?,
        onSelect: (String) -> Unit
    ) {
        try {
            controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
            controlsHideRunnable = null
            val previousPopup = playerOptionsPopup
            replacingPlayerPopup = true
            if (previousPopup?.isShowing == true) previousPopup.dismiss()
            replacingPlayerPopup = false
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply {
                    setColor(0xBF000000.toInt())
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), ContextCompat.getColor(this@MainActivity, R.color.player_divider))
                }
            }
            labels.forEachIndexed { index, labelText ->
                val item = TextView(this).apply {
                    val selected = labelText.startsWith("✓ ")
                    val displayText = labelText.removePrefix("✓ ")
                    text = if (selected) android.text.SpannableString("✓  $displayText").apply {
                        setSpan(android.text.style.ForegroundColorSpan(ContextCompat.getColor(this@MainActivity, R.color.player_accent)), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else displayText
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    val icon = when (displayText) {
                        "Audio" -> R.drawable.ic_player_audio
                        "Subtitles", "Off", "Load from storage" -> R.drawable.ic_player_subtitles
                        "Aspect ratio", "Fit", "Fill", "Zoom", "16:9", "4:3" -> R.drawable.ic_player_aspect
                        else -> 0
                    }
                    if (icon != 0) {
                        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
                        compoundDrawablePadding = dp(12)
                    }
                    setPadding(dp(14), dp(10), dp(18), dp(10))
                    minHeight = dp(48)
                    isClickable = true
                    isFocusable = true
                    if (PLAYER_NO_YELLOW_ENABLED) {
            try {
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    null,
                    android.graphics.drawable.ColorDrawable(0xFFFFFFFF.toInt())
                )
            } catch (_: Exception) { }
        }
                    setOnClickListener {
                        val clickedPopup = playerOptionsPopup
                        try {
                            if (PLAYER_MENU_FIX_ENABLED) {
                                try { actions?.getOrNull(index)?.invoke() } catch (_: Exception) {
                                    Toast.makeText(this@MainActivity, "Unable to apply this option", Toast.LENGTH_SHORT).show()
                                }
                                onSelect(labelText)
                            } else {
                                actions?.getOrNull(index)?.invoke()
                                onSelect(labelText)
                            }
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "Unable to apply this option", Toast.LENGTH_SHORT).show()
                        }
                        if (playerOptionsPopup === clickedPopup) clickedPopup?.dismiss()
                    }
                }
                content.addView(item, LinearLayout.LayoutParams(-1, -2))
            }
            content.measure(
                View.MeasureSpec.makeMeasureSpec(dp(280), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = minOf(content.measuredHeight, resources.displayMetrics.heightPixels - dp(48)).coerceAtLeast(dp(80))
            val popupScroll = ScrollView(this).apply {
                isFillViewport = false
                addView(content, ViewGroup.LayoutParams(-1, -2))
            }
            val popup = PopupWindow(popupScroll, dp(260), popupHeight, true).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                elevation = dp(12).toFloat()
                setOnDismissListener {
                    if (!replacingPlayerPopup) {
                        playerOptionsOpen = false
                        playerOptionsPopup = null
                        scheduleControlsHide()
                    }
                }
            }
            playerOptionsPopup = popup
            playerOptionsOpen = true
            content.alpha = if (PLAYER_SMOOTH_UI_ENABLED && !suppressPlayerAnimations) 0f else 1f
            content.scaleX = if (PLAYER_SMOOTH_UI_ENABLED && !suppressPlayerAnimations) 0.96f else 1f
            content.scaleY = if (PLAYER_SMOOTH_UI_ENABLED && !suppressPlayerAnimations) 0.96f else 1f
            val popupWidth = minOf(dp(280), resources.displayMetrics.widthPixels - dp(24))
            popup.width = popupWidth
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            val minOffset = dp(12) - location[0]
            val maxOffset = resources.displayMetrics.widthPixels - dp(12) - popupWidth - location[0]
            popup.showAsDropDown(anchor, minOf(maxOffset, maxOf(minOffset, 0)), dp(4), Gravity.START)
            if (PLAYER_SMOOTH_UI_ENABLED && !suppressPlayerAnimations) {
                content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150L).start()
            }
        } catch (_: Exception) {
            playerOptionsOpen = false
            playerOptionsPopup = null
        }
    }

    private fun applyScreenInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            target.setPadding(
                0,
                maxOf(bars.top, cutout.top),
                0,
                maxOf(bars.bottom, cutout.bottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun applyImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun seekBy(deltaMs: Long) {
        player?.let { exo ->
            val duration = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0L, duration))
        }
    }

    private fun updateSeekPreview(targetMs: Long, thumbX: Float, active: Boolean) {
        try {
            val pv = playerView ?: return
            val preview = (0 until pv.childCount).mapNotNull { pv.getChildAt(it) as? TextView }
                .firstOrNull { it.tag == "seek-preview" } ?: return
            if (active) {
                seekPreviewHideRunnable?.let { onlineHandler.removeCallbacks(it) }
                seekPreviewHideRunnable = null
                preview.animate().cancel()
                preview.text = formatPlayerTime(targetMs)
                preview.alpha = 1f
                preview.measure(
                    View.MeasureSpec.makeMeasureSpec(pv.width, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(dp(30), View.MeasureSpec.AT_MOST)
                )
                val seek = playerSeekBar ?: return
                val x = seek.x + thumbX - preview.measuredWidth / 2f
                preview.x = x.coerceIn(0f, (pv.width - preview.measuredWidth).coerceAtLeast(0).toFloat())
                val seekTop = (playerControls?.y ?: 0f) + seek.y
                preview.y = (seekTop - dp(45)).coerceAtLeast(dp(4).toFloat())
                preview.visibility = View.VISIBLE
            } else if (preview.visibility == View.VISIBLE) {
                seekPreviewHideRunnable?.let { onlineHandler.removeCallbacks(it) }
                val hide = Runnable {
                    try {
                        preview.animate().cancel()
                        preview.animate().alpha(0f).setDuration(150L).withEndAction {
                            preview.visibility = View.GONE
                            preview.alpha = 1f
                        }.start()
                    } catch (_: Exception) {
                        preview.visibility = View.GONE
                        preview.alpha = 1f
                    }
                    seekPreviewHideRunnable = null
                }
                seekPreviewHideRunnable = hide
                onlineHandler.postDelayed(hide, 300L)
            }
        } catch (_: Exception) {
            // Preview rendering must never affect seeking or playback.
        }
    }

    private fun showSeekFeedback(message: String, fadeAfterMs: Long = 850L) {
        if (message.isBlank()) return
        playerSeekIndicator?.text = message
        playerSeekIndicator?.visibility = View.VISIBLE
        onlineHandler.postDelayed({ hideSeekFeedback() }, fadeAfterMs)
    }

    private fun hideSeekFeedback() {
        playerSeekIndicator?.visibility = View.GONE
        playerSeekIndicator?.textSize = 14f
    }

    private fun togglePlayerControls() {
        if (playerControls?.visibility == View.VISIBLE) {
            hidePlayerControls()
            controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
        } else {
            showPlayerControls()
            scheduleControlsHide()
        }
    }

    private fun animatePlayerControl(view: View?, visible: Boolean, onHidden: (() -> Unit)? = null) {
        if (view == null) {
            onHidden?.invoke()
            return
        }
        try {
            view.animate().cancel()
            if (PLAYER_SMOOTH_UI_ENABLED && !suppressPlayerAnimations) {
                if (visible) {
                    if (view.visibility != View.VISIBLE) view.alpha = 0f
                    view.visibility = View.VISIBLE
                    view.animate().alpha(1f).setDuration(200L).start()
                } else if (view.visibility == View.VISIBLE) {
                    view.animate().alpha(0f).setDuration(200L).withEndAction {
                        view.visibility = View.GONE
                        view.alpha = 1f
                        onHidden?.invoke()
                    }.start()
                } else {
                    onHidden?.invoke()
                }
            } else {
                view.alpha = 1f
                view.visibility = if (visible) View.VISIBLE else View.GONE
                onHidden?.invoke()
            }
        } catch (_: Exception) {
            view.alpha = 1f
            view.visibility = if (visible) View.VISIBLE else View.GONE
            onHidden?.invoke()
        }
    }

    private fun showPlayerControls() {
        if (playerControlsLocked) return
        animatePlayerControl(playerControls, true)
        animatePlayerControl(playerTopBar, true)
        startSeekBarUpdates()
    }

    private fun hidePlayerControls() {
        animatePlayerControl(playerControls, false) { stopSeekBarUpdates() }
        animatePlayerControl(playerTopBar, false)
    }

    private fun startSeekBarUpdates() {
        stopSeekBarUpdates()
        val update = object : Runnable {
            override fun run() {
                if (!showingPlayer || playerControls?.visibility != View.VISIBLE) {
                    seekBarUpdateRunnable = null
                    return
                }
                val exo = player
                val duration = exo?.duration?.takeIf { it > 0L } ?: 0L
                val uri = exo?.currentMediaItem?.localConfiguration?.uri
                val isOnline = uri?.scheme.equals("http", true) || uri?.scheme.equals("https", true)
                playerSeekBar?.update(
                    exo?.currentPosition ?: 0L,
                    duration,
                    if (isOnline) exo?.bufferedPosition ?: 0L else 0L
                )
                onlineHandler.postDelayed(this, 250L)
            }
        }
        seekBarUpdateRunnable = update
        onlineHandler.post(update)
    }

    private fun stopSeekBarUpdates() {
        seekBarUpdateRunnable?.let { onlineHandler.removeCallbacks(it) }
        seekBarUpdateRunnable = null
    }

    private fun scheduleControlsHide() {
        if (playerOptionsOpen) return
        controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
        if (REPLAY_AT_END_ENABLED && player?.let { playbackIsAtEnd(it) } == true) {
            showPlayerControls()
            return
        }
        val hide = Runnable { hidePlayerControls() }
        controlsHideRunnable = hide
        onlineHandler.postDelayed(hide, 3_000L)
    }

    private fun applyPlayerLayoutMetrics(orientation: Int) {
        if (NEW_PLAYER_UI_ENABLED) { playerTopBar?.requestLayout(); playerControls?.requestLayout(); return }
        if (!LANDSCAPE_COMPACT_CONTROLS_ENABLED) return
        val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val rowHeight = dp(if (landscape) 40 else 48)

        playerToolbar?.let { toolbar ->
            toolbar.layoutParams = (toolbar.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(-1, -2)).apply { height = if (landscape) rowHeight else -2 }
            toolbar.getChildAt(0)?.let { close ->
                close.layoutParams = (close.layoutParams as? LinearLayout.LayoutParams
                    ?: LinearLayout.LayoutParams(dp(48), dp(48))).apply {
                    width = dp(48)
                    height = rowHeight
                }
            }
        }

        playerOptionsScroll?.let { scroll ->
            scroll.layoutParams = (scroll.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(-1, rowHeight)).apply { height = rowHeight }
            val row = scroll.getChildAt(0) as? LinearLayout
            row?.let { options ->
                options.layoutParams = FrameLayout.LayoutParams(-2, rowHeight)
                for (index in 0 until options.childCount) {
                    val child = options.getChildAt(index)
                    val width = if (child === playerExpandedOptionsRow) -2 else dp(48)
                    child.layoutParams = LinearLayout.LayoutParams(width, rowHeight)
                    if (child is ImageButton) {
                        val inset = if (landscape) dp(8) else dp(10)
                        child.setPadding(inset, inset, inset, inset)
                    }
                    if (child === playerExpandedOptionsRow && child is LinearLayout) {
                        for (extraIndex in 0 until child.childCount) {
                            val extra = child.getChildAt(extraIndex)
                            val extraWidth = if (extra is TextView && extra !is ImageButton) dp(54) else dp(48)
                            extra.layoutParams = LinearLayout.LayoutParams(extraWidth, rowHeight)
                            if (extra is ImageButton) {
                                val inset = if (landscape) dp(8) else dp(10)
                                extra.setPadding(inset, inset, inset, inset)
                            }
                        }
                    }
                }
            }
        }

        playerSeekBar?.let { seek ->
            seek.layoutParams = (seek.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(-1, dp(52))).apply {
                height = dp(if (landscape) 32 else 52)
            }
        }

        playerControlFrame?.let { frame ->
            frame.layoutParams = (frame.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(-1, -2)).apply {
                height = if (landscape) dp(48) else -2
            }
            val vertical = if (landscape) 0 else dp(7)
            val bottom = if (landscape) 0 else dp(12)
            frame.setPadding(dp(8), vertical, dp(8), bottom)
        }

        playerCenterControls?.let { group ->
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                val isPlayPause = child === playPauseButton
                val size = if (landscape) 48 else if (isPlayPause) 56 else 48
                child.layoutParams = (child.layoutParams as? LinearLayout.LayoutParams
                    ?: LinearLayout.LayoutParams(dp(size), dp(size))).apply {
                    width = dp(size)
                    height = dp(size)
                    val margin = dp(if (landscape) 1 else 3)
                    setMargins(margin, 0, margin, 0)
                }
                if (isPlayPause) {
                    val inset = dp(if (landscape) 8 else 10)
                    child.setPadding(inset, inset, inset, inset)
                }
            }
        }
        rotationButton?.let { rotate ->
            rotate.layoutParams = (rotate.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL)).apply {
                width = dp(48)
                height = dp(48)
            }
        }
        playerTopBar?.requestLayout()
        playerControls?.requestLayout()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        suppressPlayerAnimations = true
        if (showingPlayer) {
            resetVideoZoom()
            applySavedAspectRatio()
            applyImmersiveMode()
            playerView?.let {
                ViewCompat.requestApplyInsets(it)
                it.requestLayout()
            }
            applyPlayerLayoutMetrics(newConfig.orientation)
            playerSeekBar?.invalidate()
            playerControls?.requestLayout()
        }
        playerView?.post { suppressPlayerAnimations = false }
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
                    setBackgroundColor(panel)
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
        currentPlayerTitle = title
        queue.clear()
        audioOnlyEnabled = false
        currentIndex = 0
        rotationManuallySelected = false
        playerOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val exo = buildPlayerSafely()
        player?.release()
        player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (REPLAY_AT_END_ENABLED && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    handlePlaybackEnded(exo)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (REPLAY_AT_END_ENABLED && playbackState == Player.STATE_ENDED) handlePlaybackEnded(exo)
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                applyVideoDefaultOrientation(videoSize)
            }
        })
        try { if (SPEED_LABEL_LIVE_ENABLED) playerSpeedLabel?.text = "1X"; exo.setPlaybackParameters(androidx.media3.common.PlaybackParameters(1f)) } catch (_: Exception) { }
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        showPlayer()
    }

    private fun applyPlayerOrientation(orientation: Int) {
        if (!showingPlayer && orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) return
        playerOrientation = orientation
        requestedOrientation = orientation
        rotationButton?.contentDescription = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            "Rotate to landscape" else "Rotate to portrait"
        playerView?.let {
            it.requestLayout()
            ViewCompat.requestApplyInsets(it)
        }
        playerSeekBar?.invalidate()
        playerControls?.requestLayout()
    }

    private fun applyVideoDefaultOrientation(videoSize: androidx.media3.common.VideoSize) {
        if (!showingPlayer || rotationManuallySelected || videoSize.width <= 0 || videoSize.height <= 0) return
        val aspectRatio = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height.toFloat()
        val orientation = if (aspectRatio > 1.05f) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        applyPlayerOrientation(orientation)
    }

    private fun playerOverlayButton(icon: Int): ImageButton = ImageButton(this).apply {
        try {
            if (PLAYER_VECTOR_ICONS_ENABLED && PLAYER_ICON_REFRESH_ENABLED) {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.player_primary))
            }
        } catch (_: Exception) { }
        contentDescription = "Player control"
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setBackgroundResource(android.R.drawable.list_selector_background)
        minimumHeight = 0
        minimumWidth = 0
        stateListAnimator = null
    }

    private fun updatePlayerTitle() {
        playerTitleView?.text = currentPlayerTitle
        val activePlayer = player
        try {
            playPauseButton?.setImageResource(when {
                activePlayer?.isPlaying == true -> if (NEW_PLAYER_UI_ENABLED) R.drawable.ic_v3_pause else R.drawable.ic_player_pause
                REPLAY_AT_END_ENABLED && activePlayer != null && playbackIsAtEnd(activePlayer) -> if (NEW_PLAYER_UI_ENABLED) R.drawable.ic_v3_replay else R.drawable.ic_player_replay
                else -> if (NEW_PLAYER_UI_ENABLED) R.drawable.ic_v3_play_arrow else R.drawable.ic_player_play
            })
        } catch (_: Exception) { }
    }

    private fun buildPlayerSafely(): ExoPlayer = try {
        if (REPLAY_AT_END_ENABLED) ExoPlayer.Builder(this).setPauseAtEndOfMediaItems(true).build()
        else ExoPlayer.Builder(this).build()
    } catch (_: Exception) {
        ExoPlayer.Builder(this).build()
    }

    private fun playbackIsAtEnd(exo: ExoPlayer): Boolean = try {
        val duration = exo.duration
        duration > 0L && exo.currentPosition >= duration - 250L && !exo.isPlaying
    } catch (_: Exception) { false }

    private fun handlePlaybackEnded(exo: ExoPlayer) {
        try {
            if (player !== exo || !REPLAY_AT_END_ENABLED) return
            showPlayerControls()
            updatePlayerTitle()
            controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
            controlsHideRunnable = null
        } catch (_: Exception) {
            // Playback remains available if end-of-item UI updates fail.
        }
    }

    private fun button(caption: String, primary: Boolean): Button = Button(this).apply {
        text = caption; textSize = if (primary) 18f else 13f; isAllCaps = false
        setTextColor(this@MainActivity.text)
        setBackgroundColor(if (primary) accent else panel)
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        stateListAnimator = null
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun formatPlayerTime(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000L
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainder = seconds % 60L
        return if (hours > 0L) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainder)
        else String.format(Locale.getDefault(), "%02d:%02d", minutes, remainder)
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        controlsHideRunnable?.let { onlineHandler.removeCallbacks(it) }
        stopSeekBarUpdates()
        cancelOnlineWork()
        onlineExecutor.shutdownNow()
        thumbnailExecutor.shutdownNow()
        player?.release(); player = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showingPlayer && playerControlsLocked) { Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show() }
        else if (showingPlayer) { player?.pause(); showHome() } else if (currentFolder != null) { showHome() } else super.onBackPressed()
    }
}
