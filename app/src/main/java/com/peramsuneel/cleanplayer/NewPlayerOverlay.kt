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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat

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
            val iconPx = dp(24)
            val inset = ((dp(size) - iconPx) / 2).coerceAtLeast(0)
            setPadding(inset, inset, inset, inset)
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), null, null
            )
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            stateListAnimator = null
        }

    fun build(): Result {
        val top = ConstraintLayout(context).apply {
            id = View.generateViewId()
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(0, topInset, 0, 0)
                insets
            }
        }
        val bottom = ConstraintLayout(context).apply {
            id = View.generateViewId()
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                view.setPadding(0, 0, 0, bottomInset)
                insets
            }
        }

        val sideMargin = context.resources.getDimensionPixelSize(R.dimen.player_overlay_side_margin)
        val topShade = View(context).apply {
            id = View.generateViewId()
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xB8000000.toInt(), 0x00000000)
            )
        }
        top.addView(topShade, ConstraintLayout.LayoutParams(0, dp(136)))

        val back = iconButton(R.drawable.pl_ic_arrow_back, "Back").apply { id = View.generateViewId() }
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
        val aspect = iconButton(R.drawable.pl_ic_aspect_ratio, "Aspect ratio")
        if (PLAYER_TOP_ROW_LAYOUT_ENABLED) {
            rightActions.addView(aspect, LinearLayout.LayoutParams(dp(48), dp(48)))
            aspect.setOnClickListener { onAspect(aspect) }
        }
        val mute = iconButton(R.drawable.pl_ic_volume_up, "Mute")
        rightActions.addView(mute, LinearLayout.LayoutParams(dp(48), dp(48)))
        mute.setOnClickListener { onMute(mute) }
        top.addView(rightActions, ConstraintLayout.LayoutParams(-2, dp(48)))

        val optionsScroll = HorizontalScrollView(context).apply {
            id = View.generateViewId()
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            isFillViewport = true
            setPadding(dp(8), 0, dp(8), 0)
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

        option(R.drawable.pl_ic_audiotrack, "Audio track", onAudio)
        val subtitleButton = option(R.drawable.pl_ic_closed_caption, "Subtitles", onSubtitles)
        subtitleButton.tag = "player-subtitles-button"
        option(R.drawable.pl_ic_speed, "Playback speed", onSpeed)
        if (NEW_PLAYER_PIP_ENABLED) option(R.drawable.pl_ic_picture_in_picture_alt, "Picture in picture", onPictureInPicture)
        option(R.drawable.pl_ic_playlist_play, "Playlist", onPlaylist)
        option(R.drawable.pl_ic_flip, "Flip video", onFlip)
        val repeat = iconButton(R.drawable.pl_ic_repeat, "Repeat")
        options.addView(repeat, LinearLayout.LayoutParams(dp(48), dp(48)))
        repeat.setOnClickListener { onRepeat(repeat) }
        optionsScroll.addView(options, ViewGroup.LayoutParams(-2, dp(48)))
        top.addView(optionsScroll, ConstraintLayout.LayoutParams(0, dp(48)))

        ConstraintSet().apply {
            clone(top)
            connect(topShade.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(topShade.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(topShade.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(back.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, sideMargin)
            connect(back.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dp(8))
            connect(name.id, ConstraintSet.START, back.id, ConstraintSet.END, dp(4))
            connect(name.id, ConstraintSet.END, rightActions.id, ConstraintSet.START, dp(4))
            connect(name.id, ConstraintSet.TOP, back.id, ConstraintSet.TOP)
            connect(name.id, ConstraintSet.BOTTOM, back.id, ConstraintSet.BOTTOM)
            connect(rightActions.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, sideMargin)
            connect(rightActions.id, ConstraintSet.TOP, back.id, ConstraintSet.TOP)
            connect(rightActions.id, ConstraintSet.BOTTOM, back.id, ConstraintSet.BOTTOM)
            connect(optionsScroll.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(optionsScroll.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(optionsScroll.id, ConstraintSet.TOP, back.id, ConstraintSet.BOTTOM, dp(8))
            applyTo(top)
        }
        back.setOnClickListener(onBack)

        val bottomShade = View(context).apply {
            id = View.generateViewId()
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0xB8000000.toInt())
            )
        }
        bottom.addView(bottomShade, ConstraintLayout.LayoutParams(0, dp(116)))
        seekView.id = View.generateViewId()
        bottom.addView(seekView, ConstraintLayout.LayoutParams(0, dp(24)))

        val lock = iconButton(R.drawable.pl_ic_lock, "Lock controls").apply {
            id = View.generateViewId()
            // Keep the lock target visually distinct and above the bottom shade/seek layer.
            elevation = dp(6).toFloat()
            imageAlpha = 255
            isClickable = true
            isFocusable = true
        }
        val rotate = iconButton(R.drawable.pl_ic_screen_rotation, "Rotate screen")
        rotate.id = View.generateViewId()
        val group = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val back10 = iconButton(R.drawable.pl_ic_fast_rewind, "Back 10 seconds")
        val previous = iconButton(R.drawable.pl_ic_skip_previous, "Previous")
        val play = iconButton(R.drawable.pl_ic_pause, "Pause")
        play.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x66FFFFFF),
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2F80ED.toInt()) },
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        )
        val next = iconButton(R.drawable.pl_ic_skip_next, "Next")
        val forward10 = iconButton(R.drawable.pl_ic_fast_forward, "Forward 10 seconds")
        val groupGap = context.resources.getDimensionPixelSize(R.dimen.player_control_group_gap)
        listOf(back10, previous, play, next, forward10).forEachIndexed { index, button ->
            group.addView(button, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                if (index > 0) marginStart = groupGap / 2
                if (index < 4) marginEnd = groupGap / 2
            })
        }
        bottom.addView(lock, ConstraintLayout.LayoutParams(dp(48), dp(48)))
        bottom.addView(rotate, ConstraintLayout.LayoutParams(dp(48), dp(48)))
        bottom.addView(group, ConstraintLayout.LayoutParams(-2, dp(48)))

        ConstraintSet().apply {
            clone(bottom)
            connect(bottomShade.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(bottomShade.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(bottomShade.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(group.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(group.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(group.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dp(4))
            connect(lock.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, sideMargin)
            connect(lock.id, ConstraintSet.BOTTOM, group.id, ConstraintSet.BOTTOM)
            connect(rotate.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, sideMargin)
            connect(rotate.id, ConstraintSet.BOTTOM, group.id, ConstraintSet.BOTTOM)
            connect(seekView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, sideMargin)
            connect(seekView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, sideMargin)
            connect(seekView.id, ConstraintSet.BOTTOM, group.id, ConstraintSet.TOP)
            applyTo(bottom)
        }

        previous.setOnClickListener { onPrevious(it) }
        back10.setOnClickListener { onBack10(it) }
        play.setOnClickListener { onPlayPause(play) }
        forward10.setOnClickListener { onForward10(it) }
        next.setOnClickListener { onNext(it) }
        rotate.setOnClickListener { onRotate(rotate) }
        lock.setOnClickListener { onLock(lock) }

        return Result(top, bottom, play, mute, rotate, repeat, lock, name)
    }
}
