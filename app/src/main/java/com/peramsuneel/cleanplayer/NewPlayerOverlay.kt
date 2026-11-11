package com.peramsuneel.cleanplayer

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline

/** Isolated percentage-guideline player overlay. Playback actions remain owned by MainActivity. */
internal class NewPlayerOverlay(
    private val context: Context,
    private val title: String,
    private val seekView: View,
    private val onBack: (View) -> Unit,
    private val onMute: (ImageButton) -> Unit,
    private val onRotate: (ImageButton) -> Unit,
    private val onAudio: (View) -> Unit,
    private val onSubtitles: (View) -> Unit,
    private val onSpeed: (View) -> Unit,
    private val onPictureInPicture: (View) -> Unit,
    private val onPlaylist: (View) -> Unit,
    private val onFlip: (View) -> Unit,
    private val onRepeat: (ImageButton) -> Unit,
    private val onLock: (ImageButton) -> Unit,
    private val onPrevious: (View) -> Unit,
    private val onBack10: (View) -> Unit,
    private val onPlayPause: (ImageButton) -> Unit,
    private val onForward10: (View) -> Unit,
    private val onNext: (View) -> Unit,
    private val onAspect: (View) -> Unit
) {
    data class Result(
        val topLayer: ConstraintLayout,
        val bottomLayer: ConstraintLayout,
        val playButton: ImageButton,
        val muteButton: ImageButton,
        val rotationButton: ImageButton,
        val repeatButton: ImageButton,
        val lockButton: ImageButton,
        val titleView: TextView
    )

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()

    private fun iconButton(icon: Int, description: String, size: Int = 48): ImageButton =
        ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            contentDescription = description
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val iconPx = context.resources.getDimensionPixelSize(R.dimen.player_control_icon_size)
            val inset = ((dp(size) - iconPx) / 2).coerceAtLeast(dp(8))
            setPadding(inset, inset, inset, inset)
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), null, null
            )
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            stateListAnimator = null
        }

    private fun horizontalGuide(layout: ConstraintLayout, percent: Float): Guideline =
        Guideline(context).also {
            layout.addView(it, ConstraintLayout.LayoutParams(0, 0).apply {
                orientation = ConstraintLayout.LayoutParams.HORIZONTAL
                guidePercent = percent
            })
        }

    private fun verticalGuide(layout: ConstraintLayout, percent: Float): Guideline =
        Guideline(context).also {
            layout.addView(it, ConstraintLayout.LayoutParams(0, 0).apply {
                orientation = ConstraintLayout.LayoutParams.VERTICAL
                guidePercent = percent
            })
        }

    fun build(): Result {
        val top = ConstraintLayout(context).apply {
            id = View.generateViewId()
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        val bottom = ConstraintLayout(context).apply {
            id = View.generateViewId()
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Percent positions are relative to the actual player surface, not a device-size preset.
        val landscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val topTen = horizontalGuide(top, if (PLAYER_PERCENT_ALIGNMENT_ENABLED && landscape) .10f else .08f)
        val optionsLine = horizontalGuide(top, if (PLAYER_PERCENT_ALIGNMENT_ENABLED && landscape) .63f else .61f)
        val backCenter = verticalGuide(top, .09f)
        val titleStart = verticalGuide(top, .13f)
        val topRight = verticalGuide(top, .96f)
        val optionsStart = verticalGuide(top, .03f)
        val optionsEnd = verticalGuide(top, .95f)

        val topShade = View(context).apply {
            id = View.generateViewId()
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xB8000000.toInt(), 0x00000000)
            )
        }
        top.addView(topShade, ConstraintLayout.LayoutParams(0, dp(80)))

        val back = iconButton(R.drawable.ic_v3_arrow_back, "Back")
        back.id = View.generateViewId()
        top.addView(back, ConstraintLayout.LayoutParams(dp(48), dp(48)))

        val name = TextView(context).apply {
            id = View.generateViewId()
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(name, ConstraintLayout.LayoutParams(0, dp(48)))

        val rightActions = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        val aspect = iconButton(R.drawable.ic_v3_aspect_ratio, "Aspect ratio")
        if (PLAYER_TOP_ROW_LAYOUT_ENABLED) {
            try {
                rightActions.addView(aspect, LinearLayout.LayoutParams(dp(48), dp(48)))
                aspect.setOnClickListener { onAspect(aspect) }
            } catch (_: Exception) { aspect.visibility = View.GONE }
        }
        val mute = iconButton(R.drawable.ic_v3_volume_up, "Mute")
        rightActions.addView(mute, LinearLayout.LayoutParams(dp(48), dp(48)))
        mute.setOnClickListener { onMute(mute) }
        top.addView(rightActions, ConstraintLayout.LayoutParams(-2, dp(48)))

        val topSet = ConstraintSet().apply {
            clone(top)
            connect(topShade.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(topShade.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(topShade.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(back.id, ConstraintSet.TOP, topTen.id, ConstraintSet.TOP)
            connect(back.id, ConstraintSet.BOTTOM, topTen.id, ConstraintSet.BOTTOM)
            connect(back.id, ConstraintSet.START, backCenter.id, ConstraintSet.START)
            connect(back.id, ConstraintSet.END, backCenter.id, ConstraintSet.START)
            setHorizontalBias(back.id, .5f)
            connect(name.id, ConstraintSet.START, titleStart.id, ConstraintSet.START)
            connect(name.id, ConstraintSet.END, rightActions.id, ConstraintSet.START, dp(4))
            connect(name.id, ConstraintSet.TOP, topTen.id, ConstraintSet.TOP)
            connect(name.id, ConstraintSet.BOTTOM, topTen.id, ConstraintSet.BOTTOM)
            connect(rightActions.id, ConstraintSet.END, topRight.id, ConstraintSet.START)
            connect(rightActions.id, ConstraintSet.TOP, topTen.id, ConstraintSet.TOP)
            connect(rightActions.id, ConstraintSet.BOTTOM, topTen.id, ConstraintSet.BOTTOM)
        }
        back.setOnClickListener(onBack)

        val optionsScroll = HorizontalScrollView(context).apply {
            id = View.generateViewId()
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            isFillViewport = true
        }
        val options = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        fun option(icon: Int, description: String, click: (View) -> Unit): ImageButton {
            val button = iconButton(icon, description)
            options.addView(button, LinearLayout.LayoutParams(dp(48), dp(48)))
            button.setOnClickListener { view -> click(view) }
            return button
        }

        val chevron = option(R.drawable.ic_v3_chevron_right, "Collapse or expand options") {
            try {
                val target = if (optionsScroll.scrollX <= 0)
                    (options.width - optionsScroll.width).coerceAtLeast(0) else 0
                optionsScroll.smoothScrollTo(target, 0)
                chevronRotation(it, target > 0)
            } catch (_: Exception) { }
        }
        option(R.drawable.ic_v3_music_note, "Audio track", onAudio)
        val subtitleButton = option(R.drawable.ic_v3_closed_caption, "Subtitles", onSubtitles)
        subtitleButton.tag = "player-subtitles-button"
        option(R.drawable.ic_v3_speed, "Playback speed", onSpeed)
        if (NEW_PLAYER_PIP_ENABLED) {
            option(R.drawable.ic_v3_picture_in_picture_alt, "Picture in picture", onPictureInPicture)
        }
        option(R.drawable.ic_v3_playlist_play, "Playlist", onPlaylist)
        option(R.drawable.ic_v3_flip, "Flip video", onFlip)
        val repeat = iconButton(R.drawable.ic_v3_repeat, "Repeat")
        options.addView(repeat, LinearLayout.LayoutParams(dp(48), dp(48)))
        repeat.setOnClickListener { onRepeat(repeat) }

        optionsScroll.addView(options, ViewGroup.LayoutParams(-2, dp(48)))
        top.addView(optionsScroll, ConstraintLayout.LayoutParams(0, dp(48)))
        try {
            optionsScroll.visibility = if (PLAYER_ICON_ROW_LAYOUT_ENABLED) View.VISIBLE else View.GONE
        } catch (_: Exception) { optionsScroll.visibility = View.GONE }
        topSet.connect(optionsScroll.id, ConstraintSet.START, optionsStart.id, ConstraintSet.START)
        topSet.connect(optionsScroll.id, ConstraintSet.END, optionsEnd.id, ConstraintSet.START)
        topSet.connect(optionsScroll.id, ConstraintSet.TOP, optionsLine.id, ConstraintSet.TOP)
        topSet.connect(optionsScroll.id, ConstraintSet.BOTTOM, optionsLine.id, ConstraintSet.BOTTOM)
        topSet.applyTo(top)

        val seekLine = horizontalGuide(bottom, if (PLAYER_PERCENT_ALIGNMENT_ENABLED && landscape) .75f else .82f)
        val controlsLine = horizontalGuide(bottom, if (PLAYER_PERCENT_ALIGNMENT_ENABLED && landscape) .90f else .93f)
        val seekStart = verticalGuide(bottom, .03f)
        val seekEnd = verticalGuide(bottom, .97f)
        val leftEdge = verticalGuide(bottom, .03f)
        val rightEdge = verticalGuide(bottom, .97f)
        val bottomShadeHeight = if (PLAYER_COMPACT_BOTTOM_ENABLED) dp(64) else dp(80)
        val bottomShade = View(context).apply {
            id = View.generateViewId()
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0xB8000000.toInt())
            )
        }
        bottom.addView(bottomShade, ConstraintLayout.LayoutParams(0, bottomShadeHeight))

        seekView.id = View.generateViewId()
        bottom.addView(seekView, ConstraintLayout.LayoutParams(0, if (PLAYER_COMPACT_BOTTOM_ENABLED) dp(48) else dp(52)))

        val lock = iconButton(R.drawable.ic_v3_lock, "Lock controls")
        val rotate = iconButton(R.drawable.ic_v3_screen_rotation, "Rotate screen")
        val group = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val back10 = iconButton(R.drawable.ic_v3_fast_rewind, "Back 10 seconds")
        val previous = iconButton(R.drawable.ic_v3_skip_previous, "Previous")
        val play = iconButton(R.drawable.ic_v3_pause, "Pause", 48)
        play.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF2F80ED.toInt())
        }
        val next = iconButton(R.drawable.ic_v3_skip_next, "Next")
        val forward10 = iconButton(R.drawable.ic_v3_fast_forward, "Forward 10 seconds")
        val transportButtons = try {
            if (PLAYER_BOTTOM_ROW_LAYOUT_ENABLED) listOf(back10, previous, play, next, forward10)
            else listOf(previous, back10, play, forward10, next)
        } catch (_: Exception) { listOf(previous, back10, play, forward10, next) }
        transportButtons.forEachIndexed { index, button ->
            group.addView(button, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                if (index > 0) marginStart = dp(4)
                if (index < 4) marginEnd = dp(4)
            })
        }
        bottom.addView(lock, ConstraintLayout.LayoutParams(dp(48), dp(48)))
        bottom.addView(rotate, ConstraintLayout.LayoutParams(dp(48), dp(48)))
        bottom.addView(group, ConstraintLayout.LayoutParams(-2, dp(56)))

        val bottomSet = ConstraintSet().apply {
            clone(bottom)
            connect(bottomShade.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(bottomShade.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(bottomShade.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(seekView.id, ConstraintSet.START, seekStart.id, ConstraintSet.START)
            connect(seekView.id, ConstraintSet.END, seekEnd.id, ConstraintSet.END)
            connect(seekView.id, ConstraintSet.TOP, seekLine.id, ConstraintSet.TOP)
            connect(seekView.id, ConstraintSet.BOTTOM, seekLine.id, ConstraintSet.BOTTOM)
            connect(lock.id, ConstraintSet.START, leftEdge.id, ConstraintSet.START)
            connect(lock.id, ConstraintSet.TOP, controlsLine.id, ConstraintSet.TOP)
            connect(lock.id, ConstraintSet.BOTTOM, controlsLine.id, ConstraintSet.BOTTOM)
            connect(rotate.id, ConstraintSet.END, rightEdge.id, ConstraintSet.START)
            connect(rotate.id, ConstraintSet.TOP, controlsLine.id, ConstraintSet.TOP)
            connect(rotate.id, ConstraintSet.BOTTOM, controlsLine.id, ConstraintSet.BOTTOM)
            connect(group.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(group.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(group.id, ConstraintSet.TOP, controlsLine.id, ConstraintSet.TOP)
            connect(group.id, ConstraintSet.BOTTOM, controlsLine.id, ConstraintSet.BOTTOM)
            setHorizontalBias(group.id, .5f)
        }
        bottomSet.applyTo(bottom)

        previous.setOnClickListener { onPrevious(it) }
        back10.setOnClickListener { onBack10(it) }
        play.setOnClickListener { onPlayPause(play) }
        forward10.setOnClickListener { onForward10(it) }
        next.setOnClickListener { onNext(it) }
        rotate.setOnClickListener { onRotate(rotate) }
        lock.setOnClickListener { onLock(lock) }

        return Result(top, bottom, play, mute, rotate, repeat, lock, name)
    }

    private fun chevronRotation(view: View, expanded: Boolean) {
        view.animate().rotation(if (expanded) 180f else 0f).setDuration(150L).start()
    }
}
