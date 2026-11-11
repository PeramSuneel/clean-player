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

/**
 * Isolated v3 controls overlay. The media engine and all playback actions remain owned by MainActivity.
 * All icon resources in this class are official Google Material Design Icons outlined Android vectors.
 */
internal class NewPlayerOverlay(
    private val context: Context,
    private val title: String,
    private val seekView: View,
    private val onBack: (View) -> Unit,
    private val onScreenshot: (View) -> Unit,
    private val onMute: (ImageButton) -> Unit,
    private val onRotate: (ImageButton) -> Unit,
    private val onAudio: (View) -> Unit,
    private val onSubtitles: (View) -> Unit,
    private val onSpeed: (View) -> Unit,
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

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + .5f).toInt()

    private fun iconButton(icon: Int, description: String, size: Int = 48): ImageButton =
        ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            contentDescription = description
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val iconDp = if (context.resources.configuration.smallestScreenWidthDp > 600) 28 else 24
            val inset = ((size - iconDp) / 2).coerceAtLeast(8)
            setPadding(dp(inset), dp(inset), dp(inset), dp(inset))
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x44FFFFFF), null, null
            )
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            stateListAnimator = null
        }

    private fun addHorizontalGuide(layout: ConstraintLayout, percent: Float): Guideline =
        Guideline(context).also {
            val lp = ConstraintLayout.LayoutParams(0, 0).apply {
                orientation = ConstraintLayout.LayoutParams.HORIZONTAL
                guidePercent = percent
            }
            layout.addView(it, lp)
        }

    private fun addVerticalGuide(layout: ConstraintLayout, percent: Float): Guideline =
        Guideline(context).also {
            val lp = ConstraintLayout.LayoutParams(0, 0).apply {
                orientation = ConstraintLayout.LayoutParams.VERTICAL
                guidePercent = percent
            }
            layout.addView(it, lp)
        }

    fun build(): Result {
        val top = ConstraintLayout(context).apply {
            id = View.generateViewId()
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xB8000000.toInt(), 0x00000000)
            )
        }
        val bottom = ConstraintLayout(context).apply {
            id = View.generateViewId()
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0xB8000000.toInt())
            )
        }

        val topTen = addHorizontalGuide(top, .10f)
        val optionsLine = addHorizontalGuide(top, .63f)
        val leftSafe = addVerticalGuide(top, .03f)
        val rightSafe = addVerticalGuide(top, .96f)
        val toolbar = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val back = iconButton(R.drawable.ic_v3_arrow_back, "Back")
            addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
            back.setOnClickListener { onBack(it) }
            val name = TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
            }
            addView(name, LinearLayout.LayoutParams(0, dp(48), 1f))
            val camera = iconButton(R.drawable.ic_v3_photo_camera, "Screenshot")
            addView(camera, LinearLayout.LayoutParams(dp(48), dp(48)))
            camera.setOnClickListener { onScreenshot(it) }
            val mute = iconButton(R.drawable.ic_v3_volume_up, "Mute")
            addView(mute, LinearLayout.LayoutParams(dp(48), dp(48)))
            mute.setOnClickListener { onMute(mute) }
            val rotate = iconButton(R.drawable.ic_v3_screen_rotation, "Rotate screen")
            addView(rotate, LinearLayout.LayoutParams(dp(48), dp(48)))
            rotate.setOnClickListener { onRotate(rotate) }
            tag = arrayOf(mute, rotate)
        }
        top.addView(toolbar, ConstraintLayout.LayoutParams(0, dp(48)))
        val topSet = ConstraintSet().apply {
            clone(top)
            connect(toolbar.id, ConstraintSet.START, leftSafe.id, ConstraintSet.START)
            connect(toolbar.id, ConstraintSet.END, rightSafe.id, ConstraintSet.END)
            connect(toolbar.id, ConstraintSet.TOP, topTen.id, ConstraintSet.TOP)
            connect(toolbar.id, ConstraintSet.BOTTOM, topTen.id, ConstraintSet.BOTTOM)
        }

        val optionsScroll = HorizontalScrollView(context).apply {
            id = View.generateViewId()
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            isFillViewport = false
        }
        val options = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        fun option(icon: Int, description: String, click: (View) -> Unit): ImageButton {
            val b = iconButton(icon, description)
            options.addView(b, LinearLayout.LayoutParams(dp(48), dp(48)))
            b.setOnClickListener { view -> click(view) }
            return b
        }
        option(R.drawable.ic_v3_chevron_right, "More options") { optionsScroll.smoothScrollTo(optionsScroll.scrollX + dp(240), 0) }
        option(R.drawable.ic_v3_music_note, "Audio track", onAudio)
        option(R.drawable.ic_v3_closed_caption, "Subtitles", onSubtitles)
        option(R.drawable.ic_v3_speed, "Playback speed", onSpeed)
        // Cast and PiP are intentionally omitted until their platform integrations are configured.
        option(R.drawable.ic_v3_playlist_play, "Playlist", onPlaylist)
        option(R.drawable.ic_v3_flip, "Flip video", onFlip)
        val repeat = iconButton(R.drawable.ic_v3_repeat, "Repeat", 48)
        options.addView(repeat, LinearLayout.LayoutParams(dp(48), dp(48)))
        repeat.setOnClickListener { onRepeat(repeat) }
        optionsScroll.addView(options, ViewGroup.LayoutParams(-2, dp(48)))
        top.addView(optionsScroll, ConstraintLayout.LayoutParams(0, dp(48)))
        topSet.connect(optionsScroll.id, ConstraintSet.START, leftSafe.id, ConstraintSet.START)
        topSet.connect(optionsScroll.id, ConstraintSet.END, rightSafe.id, ConstraintSet.END)
        topSet.connect(optionsScroll.id, ConstraintSet.TOP, optionsLine.id, ConstraintSet.TOP)
        topSet.connect(optionsScroll.id, ConstraintSet.BOTTOM, optionsLine.id, ConstraintSet.BOTTOM)
        topSet.applyTo(top)

        val seekLine = addHorizontalGuide(bottom, .75f)
        val controlsLine = addHorizontalGuide(bottom, .90f)
        val seekStart = addVerticalGuide(bottom, .03f)
        val seekEnd = addVerticalGuide(bottom, .97f)
        seekView.id = View.generateViewId()
        bottom.addView(seekView, ConstraintLayout.LayoutParams(0, dp(48)))
        val bottomSet = ConstraintSet().apply {
            clone(bottom)
            connect(seekView.id, ConstraintSet.START, seekStart.id, ConstraintSet.START)
            connect(seekView.id, ConstraintSet.END, seekEnd.id, ConstraintSet.END)
            connect(seekView.id, ConstraintSet.TOP, seekLine.id, ConstraintSet.TOP)
            connect(seekView.id, ConstraintSet.BOTTOM, seekLine.id, ConstraintSet.BOTTOM)
        }

        val lock = iconButton(R.drawable.ic_v3_lock, "Lock controls")
        val aspect = iconButton(R.drawable.ic_v3_aspect_ratio, "Aspect ratio")
        val group = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val previous = iconButton(R.drawable.ic_v3_skip_previous, "Previous")
        val back10 = iconButton(R.drawable.ic_v3_replay_10, "Back 10 seconds")
        val play = iconButton(R.drawable.ic_v3_pause, "Pause")
        play.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF2F80ED.toInt())
        }
        play.setPadding(dp(12), dp(12), dp(12), dp(12))
        val forward10 = iconButton(R.drawable.ic_v3_forward_10, "Forward 10 seconds")
        val next = iconButton(R.drawable.ic_v3_skip_next, "Next")
        listOf(previous, back10, play, forward10, next).forEachIndexed { index, button ->
            val size = if (button === play) 56 else 48
            group.addView(button, LinearLayout.LayoutParams(dp(size), dp(size)).apply {
                if (index > 0) marginStart = dp(8)
                if (index < 4) marginEnd = dp(8)
            })
        }
        bottom.addView(lock, ConstraintLayout.LayoutParams(dp(48), dp(48)))
        bottom.addView(aspect, ConstraintLayout.LayoutParams(dp(48), dp(48)))
        bottom.addView(group, ConstraintLayout.LayoutParams(-2, dp(56)))
        bottomSet.connect(lock.id, ConstraintSet.START, bottom.id, ConstraintSet.START, dp(8))
        bottomSet.connect(lock.id, ConstraintSet.TOP, controlsLine.id, ConstraintSet.TOP)
        bottomSet.connect(lock.id, ConstraintSet.BOTTOM, controlsLine.id, ConstraintSet.BOTTOM)
        bottomSet.connect(aspect.id, ConstraintSet.END, bottom.id, ConstraintSet.END, dp(8))
        bottomSet.connect(aspect.id, ConstraintSet.TOP, controlsLine.id, ConstraintSet.TOP)
        bottomSet.connect(aspect.id, ConstraintSet.BOTTOM, controlsLine.id, ConstraintSet.BOTTOM)
        bottomSet.connect(group.id, ConstraintSet.START, bottom.id, ConstraintSet.START)
        bottomSet.connect(group.id, ConstraintSet.END, bottom.id, ConstraintSet.END)
        bottomSet.connect(group.id, ConstraintSet.TOP, controlsLine.id, ConstraintSet.TOP)
        bottomSet.connect(group.id, ConstraintSet.BOTTOM, controlsLine.id, ConstraintSet.BOTTOM)
        bottomSet.setHorizontalBias(group.id, .5f)
        bottomSet.applyTo(bottom)

        previous.setOnClickListener { onPrevious(it) }
        back10.setOnClickListener { onBack10(it) }
        play.setOnClickListener { onPlayPause(play) }
        forward10.setOnClickListener { onForward10(it) }
        next.setOnClickListener { onNext(it) }
        aspect.setOnClickListener { onAspect(it) }
        lock.setOnClickListener { onLock(lock) }

        return Result(
            top, bottom, play,
            toolbar.tag.let { (it as Array<*>)[0] as ImageButton },
            toolbar.tag.let { (it as Array<*>)[1] as ImageButton },
            repeat, lock, toolbar.getChildAt(1) as TextView
        )
    }
}
