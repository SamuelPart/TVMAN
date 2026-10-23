package com.tvman.app

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlin.math.abs

/** Playback UX built for a TV remote: predictable DPAD focus, a scrub timeline and a small transport set. */
class TvPlaybackOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Delegate {
        fun snapshot(): Snapshot
        fun isLive(): Boolean = false
        fun onPlayPause()
        fun onSeekBy(deltaSec: Int)
        fun onSeekTo(positionMs: Long)
        fun onSpeedPicked(speed: Float) {}
        fun onSubtitlesPicked(index: Int) {}
        fun onAudioPicked(index: Int) {}
        fun subtitleTrackCount(): Int = 0
        fun audioTrackCount(): Int = 0
        fun onClose()
        fun onPip() {}
        fun onCast() {}
        fun onShare() {}
        fun onInfo() {}
        fun onContinueWatching() {}
    }

    data class Snapshot(
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val bufferedMs: Long = 0L,
        val speed: Float = 1f
    )

    var delegate: Delegate? = null

    private val topScrim: View
    private val bottomScrim: View
    private val topBar: LinearLayout
    private val bottomPanel: LinearLayout
    private val closeButton: TextView
    private val titleView: TextView
    private val subtitleView: TextView
    private val liveBadge: TextView
    private val positionView: TextView
    private val durationView: TextView
    private val seekBar: SeekBar
    private val rewindButton: TextView
    private val playPauseButton: TextView
    private val forwardButton: TextView
    private val optionsButton: TextView
    private val seekFeedback: TextView

    private var controlsVisible = false
    private var isLiveMode = false
    private var scrubbing = false
    private var lastSpeed = 1f
    private var pollRunnable: Runnable? = null
    private var hideRunnable: Runnable? = null
    private var feedbackRunnable: Runnable? = null

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        private const val AUTO_HIDE_MS = 5_500L
        private const val SEEK_FEEDBACK_MS = 1_100L
        private const val POLL_MS = 500L

        fun splitTitleInfo(raw: String): Pair<String, String> {
            val text = raw.trim()
            val full = Regex("""(?i)\b(?:temporada|t)\s*(\d{1,2})\b[\s\-–:.]*(?:cap[ií]tulo|episodio|ep|cap|e)?\s*(\d{1,4})\b""")
            val onlyEpisode = Regex("""(?i)\b(?:cap[ií]tulo|episodio|ep|cap)\s*(\d{1,4})\b""")
            val match = full.find(text)
            val detail = when {
                match != null -> "T${match.groupValues[1]} · Capítulo ${match.groupValues[2]}"
                else -> onlyEpisode.find(text)?.let { "Capítulo ${it.groupValues[1]}" } ?: ""
            }
            val found = match ?: onlyEpisode.find(text) ?: return text to ""
            val main = text.replaceRange(found.range, " ")
                .replace(Regex("""[\s\-–:.]+$"""), "")
                .replace(Regex("""^[\s\-–:.]+"""), "")
                .trim()
            return (main.ifBlank { text }) to detail
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_tv_playback_overlay, this, true)
        isFocusable = true
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        topScrim = findViewById(R.id.tvPlayerTopScrim)
        bottomScrim = findViewById(R.id.tvPlayerBottomScrim)
        topBar = findViewById(R.id.tvPlayerTopBar)
        bottomPanel = findViewById(R.id.tvPlayerBottomPanel)
        closeButton = findViewById(R.id.btnTvPlayerClose)
        titleView = findViewById(R.id.txtTvPlayerTitle)
        subtitleView = findViewById(R.id.txtTvPlayerSubtitle)
        liveBadge = findViewById(R.id.tvPlayerLiveBadge)
        positionView = findViewById(R.id.txtTvPlayerPosition)
        durationView = findViewById(R.id.txtTvPlayerDuration)
        seekBar = findViewById(R.id.tvPlayerSeekBar)
        rewindButton = findViewById(R.id.btnTvPlayerRewind)
        playPauseButton = findViewById(R.id.btnTvPlayerPlayPause)
        forwardButton = findViewById(R.id.btnTvPlayerForward)
        optionsButton = findViewById(R.id.btnTvPlayerOptions)
        seekFeedback = findViewById(R.id.txtTvPlayerSeekFeedback)

        listOf(closeButton, rewindButton, playPauseButton, forwardButton, optionsButton).forEach { control ->
            control.setOnFocusChangeListener { _, _ -> if (controlsVisible) scheduleAutoHide() }
            control.setOnKeyListener { _, _, event ->
                if (event.action == KeyEvent.ACTION_DOWN && controlsVisible) scheduleAutoHide()
                false
            }
        }
        seekBar.setOnFocusChangeListener { _, _ -> if (controlsVisible) scheduleAutoHide() }
        seekBar.setOnKeyListener { _, _, event ->
            if (event.action == KeyEvent.ACTION_DOWN && controlsVisible) scheduleAutoHide()
            false
        }

        closeButton.setOnClickListener { delegate?.onClose() }
        rewindButton.setOnClickListener { seekBy(-10) }
        playPauseButton.setOnClickListener { togglePlayback() }
        forwardButton.setOnClickListener { seekBy(10) }
        optionsButton.setOnClickListener { showOptionsMenu() }

        closeButton.contentDescription = "Volver y cerrar el reproductor"
        rewindButton.contentDescription = "Retroceder diez segundos"
        playPauseButton.contentDescription = "Reproducir o pausar"
        forwardButton.contentDescription = "Avanzar diez segundos"
        optionsButton.contentDescription = "Opciones de reproducción"
        seekBar.contentDescription = "Línea de tiempo; izquierda y derecha para buscar"

        setupTimeline()
        applyControlsVisible(false, animated = false)
    }

    fun setTitleInfo(mainTitle: String?, subLine: String?) {
        titleView.text = mainTitle?.takeIf { it.isNotBlank() } ?: "Reproduciendo"
        subtitleView.text = subLine.orEmpty()
        subtitleView.visibility = if (subLine.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** Shows the transport bar; hosts can call this when playback starts or pauses. */
    fun showControls() {
        if (!controlsVisible) applyControlsVisible(true, animated = true)
        delegate?.snapshot()?.let(::applyState)
        scheduleAutoHide()
    }

    fun focusForRemote() {
        showControls()
        playPauseButton.requestFocus()
    }

    fun showRemoteMessage(message: String) {
        showTransientControls()
        showSeekFeedback(message)
    }

    fun revealForRemote(): Boolean {
        if (controlsVisible && findFocus() != null && findFocus() !== this) return false
        focusForRemote()
        return true
    }

    /** Hardware remote map. Visible controls are left to Android's normal DPAD focus engine. */
    fun handleRemoteKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayback()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (delegate?.snapshot()?.playing != true) togglePlayback()
                else showTransientControls()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (delegate?.snapshot()?.playing == true) togglePlayback()
                else showTransientControls()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (controlsVisible) {
                    hideControls()
                    return true
                }
                return false
            }
        }

        if (controlsVisible) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                scheduleAutoHide()
            }
            return false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isLiveMode) seekBy(-10) else focusForRemote()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isLiveMode) seekBy(10) else focusForRemote()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                focusForRemote()
                return true
            }
        }
        return false
    }

    fun notifyPlayingStateChanged() {
        delegate?.snapshot()?.let(::applyState)
        if (delegate?.snapshot()?.playing == true) scheduleAutoHide() else cancelAutoHide()
    }

    private fun setupTimeline() {
        seekBar.max = 1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = delegate?.snapshot()?.durationMs ?: return
                if (duration <= 0L) return
                val target = progress.toLong() * duration / 1000L
                positionView.text = formatTime(target)
                delegate?.onSeekTo(target)
                scheduleAutoHide()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                scrubbing = true
                cancelAutoHide()
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                scrubbing = false
                val duration = delegate?.snapshot()?.durationMs ?: 0L
                if (duration > 0L) delegate?.onSeekTo(seekBar.progress.toLong() * duration / 1000L)
                scheduleAutoHide()
            }
        })
    }

    private fun applyState(state: Snapshot) {
        playPauseButton.text = if (state.playing) "Ⅱ" else "▶"
        playPauseButton.contentDescription = if (state.playing) "Pausar" else "Reproducir"
        val live = delegate?.isLive() ?: (state.durationMs <= 0L)
        if (live != isLiveMode) {
            isLiveMode = live
            liveBadge.visibility = if (live) View.VISIBLE else View.GONE
            seekBar.visibility = if (live) View.GONE else View.VISIBLE
            positionView.visibility = if (live) View.GONE else View.VISIBLE
            durationView.visibility = if (live) View.GONE else View.VISIBLE
            rewindButton.visibility = if (live) View.GONE else View.VISIBLE
            forwardButton.visibility = if (live) View.GONE else View.VISIBLE
        }
        if (!live && state.durationMs > 0L) {
            if (!scrubbing) {
                seekBar.progress = ((state.positionMs.coerceIn(0L, state.durationMs) * 1000L) / state.durationMs)
                    .toInt().coerceIn(0, 1000)
            }
            positionView.text = formatTime(state.positionMs)
            durationView.text = formatTime(state.durationMs)
        } else if (live) {
            positionView.text = formatTime(state.positionMs)
        }
        lastSpeed = state.speed.takeIf { it > 0f } ?: 1f
    }

    private fun togglePlayback() {
        delegate?.onPlayPause()
        notifyPlayingStateChanged()
        showControls()
    }

    private fun seekBy(seconds: Int) {
        if (isLiveMode) return
        val wasHidden = !controlsVisible
        delegate?.onSeekBy(seconds)
        showControls()
        if (wasHidden) playPauseButton.requestFocus()
        showSeekFeedback(if (seconds < 0) "−10 segundos" else "+10 segundos")
    }

    private fun showTransientControls() {
        if (!controlsVisible) applyControlsVisible(true, animated = true)
        scheduleAutoHide(2_400L)
    }

    private fun showSeekFeedback(text: String) {
        seekFeedback.animate().cancel()
        seekFeedback.text = text
        seekFeedback.visibility = View.VISIBLE
        seekFeedback.alpha = 0f
        seekFeedback.animate().alpha(1f).setDuration(120L).withEndAction {
            feedbackRunnable?.let(::removeCallbacks)
            val hide = Runnable {
                seekFeedback.animate().alpha(0f).setDuration(180L).withEndAction {
                    seekFeedback.visibility = View.GONE
                }.start()
            }
            feedbackRunnable = hide
            postDelayed(hide, SEEK_FEEDBACK_MS)
        }.start()
    }

    private fun showOptionsMenu() {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if ((delegate?.subtitleTrackCount() ?: 0) > 0) {
            actions.add(Pair("Subtítulos", { showSubtitlesMenu() }))
        }
        if ((delegate?.audioTrackCount() ?: 0) > 0) {
            actions.add(Pair("Pista de audio", { showAudioMenu() }))
        }
        if (!isLiveMode) {
            actions.add(Pair("Velocidad · ${formatSpeed(lastSpeed)}×", { showSpeedMenu() }))
        }
        actions.add(Pair("Transmitir a una pantalla", { delegate?.onCast(); Unit }))
        if (actions.isEmpty()) return
        cancelAutoHide()
        AlertDialog.Builder(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setTitle("Opciones de reproducción")
            .setItems(actions.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actions[which].second.invoke()
            }
            .setOnDismissListener { if (controlsVisible) scheduleAutoHide() }
            .show()
    }

    private fun showSpeedMenu() {
        cancelAutoHide()
        val labels = SPEEDS.map { if (it == 1f) "Normal · 1×" else "${formatSpeed(it)}×" }.toTypedArray()
        val checked = SPEEDS.indices.minByOrNull { abs(SPEEDS[it] - lastSpeed) } ?: 2
        AlertDialog.Builder(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setTitle("Velocidad")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                delegate?.onSpeedPicked(SPEEDS[which])
                lastSpeed = SPEEDS[which]
                dialog.dismiss()
            }
            .setNegativeButton("Volver", null)
            .setOnDismissListener { if (controlsVisible) scheduleAutoHide() }
            .show()
    }

    private fun showSubtitlesMenu() {
        cancelAutoHide()
        val count = delegate?.subtitleTrackCount() ?: 0
        if (count <= 0) return
        val labels = (listOf("Desactivados") + (1..count).map { "Pista $it" }).toTypedArray()
        AlertDialog.Builder(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setTitle("Subtítulos")
            .setSingleChoiceItems(labels, -1) { dialog, which ->
                delegate?.onSubtitlesPicked(which - 1)
                dialog.dismiss()
            }
            .setNegativeButton("Volver", null)
            .setOnDismissListener { if (controlsVisible) scheduleAutoHide() }
            .show()
    }

    private fun showAudioMenu() {
        cancelAutoHide()
        val count = delegate?.audioTrackCount() ?: 0
        if (count <= 0) return
        val labels = (1..count).map { "Pista de audio $it" }.toTypedArray()
        AlertDialog.Builder(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setTitle("Pista de audio")
            .setSingleChoiceItems(labels, -1) { dialog, which ->
                delegate?.onAudioPicked(which)
                dialog.dismiss()
            }
            .setNegativeButton("Volver", null)
            .setOnDismissListener { if (controlsVisible) scheduleAutoHide() }
            .show()
    }

    private fun hideControls() {
        applyControlsVisible(false, animated = true)
        cancelAutoHide()
    }

    private fun applyControlsVisible(visible: Boolean, animated: Boolean) {
        controlsVisible = visible
        cancelAutoHide()
        val views = listOf(topScrim, bottomScrim, topBar, bottomPanel)
        views.forEach { view ->
            view.animate().cancel()
            if (visible) {
                view.visibility = View.VISIBLE
                if (animated) {
                    view.alpha = 0f
                    view.animate().alpha(1f).setDuration(200L).start()
                } else view.alpha = 1f
            } else if (animated) {
                view.animate().alpha(0f).setDuration(180L).withEndAction {
                    if (!controlsVisible) view.visibility = View.INVISIBLE
                }.start()
            } else {
                view.visibility = View.INVISIBLE
                view.alpha = 0f
            }
        }
        if (!visible) {
            findFocus()?.clearFocus()
            requestFocus()
        }
    }

    private fun scheduleAutoHide(delay: Long = AUTO_HIDE_MS) {
        cancelAutoHide()
        if (!controlsVisible || scrubbing || delegate?.snapshot()?.playing != true) return
        val runnable = Runnable {
            if (controlsVisible && !scrubbing && delegate?.snapshot()?.playing == true) hideControls()
        }
        hideRunnable = runnable
        postDelayed(runnable, delay)
    }

    private fun cancelAutoHide() {
        hideRunnable?.let(::removeCallbacks)
        hideRunnable = null
    }

    private fun startPolling() {
        if (pollRunnable != null) return
        val poll = object : Runnable {
            override fun run() {
                delegate?.let { target ->
                    runCatching { target.snapshot() }.getOrNull()?.let(::applyState)
                }
                postDelayed(this, POLL_MS)
            }
        }
        pollRunnable = poll
        post(poll)
    }

    private fun stopPolling() {
        pollRunnable?.let(::removeCallbacks)
        pollRunnable = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startPolling()
    }

    override fun onDetachedFromWindow() {
        stopPolling()
        cancelAutoHide()
        feedbackRunnable?.let(::removeCallbacks)
        super.onDetachedFromWindow()
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) / 1000L)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainder = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
        else "%d:%02d".format(minutes, remainder)
    }

    private fun formatSpeed(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString()
        else "%.2f".format(value).trimEnd('0').trimEnd('.')
}
