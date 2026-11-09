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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale

private data class MediaFile(val id: Long, val name: String, val uri: Uri, val folder: String, val isAudio: Boolean, val duration: Long)

class MainActivity : AppCompatActivity() {
    private val bg = 0xFF101311.toInt()
    private val panel = 0xFF1B211D.toInt()
    private val accent = 0xFFB7D5A8.toInt()
    private val text = 0xFFF3F5F2.toInt()
    private val muted = 0xFF9BA69D.toInt()
    private val files = mutableListOf<MediaFile>()
    private val queue = mutableListOf<MediaFile>()
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var currentIndex = 0
    private var showingPlayer = false
    private lateinit var root: LinearLayout
    private lateinit var search: EditText
    private var selectedTab = "Videos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        setContentView(root)
        if (hasMediaPermission()) loadLibrary() else requestMediaPermission()
        showHome()
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
                    val folder = path.substringAfterLast('/', path.ifBlank { "Internal storage" }).ifBlank { "Internal storage" }
                    val uri = ContentUris.withAppendedId(collection, id)
                    files.add(MediaFile(id, name, uri, folder, audio, if (durCol >= 0) c.getLong(durCol) else 0L))
                }
            }
        } catch (_: Exception) { }
    }

    private fun showHome(filter: String = "") {
        showingPlayer = false
        root.removeAllViews()
        root.orientation = LinearLayout.VERTICAL
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(8)) }
        header.addView(label("Clean Player", 25, text, true))
        search = EditText(this).apply {
            hint = "Search videos, songs or folders"
            setSingleLine(true); textSize = 15f; setTextColor(this@MainActivity.text); setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0); setBackgroundColor(panel)
            setText(filter)
        }
        header.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(15) })
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
        populateHomeContent(content, filter)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!showingPlayer) populateHomeContent(content, s.toString()) }
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
            val grid = GridLayout(this).apply { columnCount = 2; useDefaultMargins = false }
            groups.forEach { (folder, items) ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    setPadding(dp(10), dp(16), dp(10), dp(16)); setBackgroundColor(panel)
                }
                card.addView(label(if (selectedTab == "Videos") "▰" else "♫", 34, accent, true).apply { gravity = Gravity.CENTER })
                card.addView(label(folder, 15, text, true).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                card.addView(label("${items.size} ${if (selectedTab == "Videos") "videos" else "audio files"}", 12, muted, false).apply { gravity = Gravity.CENTER })
                card.setOnClickListener { showFolder(folder, items) }
                val params = GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) }
                grid.addView(card, params)
            }
            content.addView(grid)
        }
    }

    private fun showFolder(folder: String, items: List<MediaFile>) {
        root.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(10)) }
        val back = button("‹", false); head.addView(back, LinearLayout.LayoutParams(dp(48), dp(44))); back.setOnClickListener { showHome() }
        head.addView(label(folder, 21, text, true))
        root.addView(head)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(5), dp(16), dp(20)) }
        scroll.addView(list); root.addView(scroll)
        items.forEachIndexed { index, file ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(panel) }
            row.addView(label(if (file.isAudio) "♫  ${file.name}" else "▶  ${file.name}", 15, text, true))
            row.addView(label(formatDuration(file.duration), 12, muted, false))
            val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
            list.addView(row, lp)
            row.setOnClickListener { playItems(items, index) }
        }
    }

    private fun playItems(items: List<MediaFile>, index: Int) {
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
        root.removeAllViews()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        val close = button("‹", false); top.addView(close, LinearLayout.LayoutParams(dp(48), dp(44))); close.setOnClickListener { player?.pause(); showHome() }
        val now = label(queue.getOrNull(currentIndex)?.name ?: "Now playing", 16, text, true)
        top.addView(now, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)
        val pv = PlayerView(this).apply { useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); player = this@MainActivity.player }
        playerView = pv
        root.addView(pv, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(18), dp(8), dp(24)) }
        val prev = button("|◀", false); val back = button("↶10", false); val toggle = button("Ⅱ", true); val forward = button("10↷", false); val next = button("▶|", false)
        listOf(prev, back, toggle, forward, next).forEachIndexed { i, b ->
            controls.addView(b, LinearLayout.LayoutParams(0, dp(if (i == 2) 58 else 48), if (i == 2) 1.3f else 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        prev.setOnClickListener { player?.seekToPreviousMediaItem() }
        back.setOnClickListener { player?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) } }
        toggle.setOnClickListener { player?.let { if (it.isPlaying) it.pause() else it.play(); toggle.text = if (it.isPlaying) "Ⅱ" else "▶" } }
        forward.setOnClickListener { player?.let { it.seekTo((it.currentPosition + 10_000).coerceAtMost(it.duration.coerceAtLeast(0))) } }
        next.setOnClickListener { player?.seekToNextMediaItem() }
        root.addView(controls)
        updatePlayerTitle()
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
        player?.release(); player = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showingPlayer) { player?.pause(); showHome() } else super.onBackPressed()
    }
}
