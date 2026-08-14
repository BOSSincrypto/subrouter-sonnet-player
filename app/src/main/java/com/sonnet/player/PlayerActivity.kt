package com.sonnet.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sonnet.player.databinding.ActivityPlayerBinding
import kotlin.math.abs

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var isOrientationLocked = false
    private var currentSpeed = 1.0f
    private var currentDisplayMode = DisplayMode.FIT
    private lateinit var gestureDetector: GestureDetectorCompat

    companion object {
        private val SPEED_OPTIONS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }

    enum class DisplayMode {
        FIT, FILL, ZOOM
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupImmersiveMode()
        setupGestureDetector()
        initializePlayer()
        setupControls()
        loadVideo()
    }

    private fun setupImmersiveMode() {
        // Hide system bars for immersive experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupGestureDetector() {
        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val screenWidth = binding.root.width
                if (e.x < screenWidth / 3) {
                    // Left third - rewind 10 seconds
                    seekRelative(-10000)
                } else if (e.x > screenWidth * 2 / 3) {
                    // Right third - forward 10 seconds
                    seekRelative(10000)
                } else {
                    // Middle - play/pause
                    togglePlayPause()
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControlsVisibility()
                return true
            }
        }

        gestureDetector = GestureDetectorCompat(this, gestureListener)

        binding.gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            binding.playerView.player = this

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> showLoading(true)
                        Player.STATE_READY -> showLoading(false)
                        Player.STATE_ENDED -> finish()
                        else -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    showError(error.message ?: "Unknown playback error")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseButton(isPlaying)
                }
            })
        }
    }

    private fun setupControls() {
        // Find control views from custom layout
        val controlsRoot = binding.playerView.findViewById<View>(R.id.bottomControlBar)?.parent as? View

        controlsRoot?.findViewById<ImageButton>(R.id.backButton)?.setOnClickListener {
            finish()
        }

        controlsRoot?.findViewById<ImageButton>(R.id.playPauseButton)?.setOnClickListener {
            togglePlayPause()
        }

        controlsRoot?.findViewById<ImageButton>(R.id.centerPlayPause)?.setOnClickListener {
            togglePlayPause()
        }

        controlsRoot?.findViewById<ImageButton>(R.id.rewindButton)?.setOnClickListener {
            seekRelative(-10000)
        }

        controlsRoot?.findViewById<ImageButton>(R.id.forwardButton)?.setOnClickListener {
            seekRelative(10000)
        }

        controlsRoot?.findViewById<View>(R.id.speedButton)?.setOnClickListener {
            showSpeedMenu()
        }

        controlsRoot?.findViewById<View>(R.id.displayModeButton)?.setOnClickListener {
            cycleDisplayMode()
        }

        controlsRoot?.findViewById<ImageButton>(R.id.lockButton)?.setOnClickListener {
            toggleOrientationLock()
        }

        controlsRoot?.findViewById<ImageButton>(R.id.fullscreenButton)?.setOnClickListener {
            toggleFullscreen()
        }

        controlsRoot?.findViewById<ImageButton>(R.id.moreButton)?.setOnClickListener {
            showMoreOptions()
        }
    }

    private fun loadVideo() {
        val videoUriString = intent.getStringExtra("VIDEO_URI") ?: return
        val videoTitle = intent.getStringExtra("VIDEO_TITLE") ?: "Video"

        // Update title in controls
        val controlsRoot = binding.playerView.findViewById<View>(R.id.topControlBar)?.parent as? View
        controlsRoot?.findViewById<android.widget.TextView>(R.id.videoTitle)?.text = videoTitle

        val mediaItem = MediaItem.fromUri(Uri.parse(videoUriString))
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val iconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val controlsRoot = binding.playerView.findViewById<View>(R.id.bottomControlBar)?.parent as? View
        controlsRoot?.findViewById<ImageButton>(R.id.playPauseButton)?.setImageResource(iconRes)
        controlsRoot?.findViewById<ImageButton>(R.id.centerPlayPause)?.setImageResource(iconRes)
    }

    private fun seekRelative(milliseconds: Long) {
        player?.let {
            val newPosition = (it.currentPosition + milliseconds).coerceIn(0, it.duration)
            it.seekTo(newPosition)
        }
    }

    private fun toggleControlsVisibility() {
        binding.playerView.apply {
            if (isControllerFullyVisible) {
                hideController()
            } else {
                showController()
            }
        }
    }

    private fun showSpeedMenu() {
        val speedLabels = arrayOf("0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
        val currentIndex = SPEED_OPTIONS.indexOf(currentSpeed)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.speed_control)
            .setSingleChoiceItems(speedLabels, currentIndex) { dialog, which ->
                currentSpeed = SPEED_OPTIONS[which]
                player?.setPlaybackSpeed(currentSpeed)
                updateSpeedButton(speedLabels[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun updateSpeedButton(label: String) {
        val controlsRoot = binding.playerView.findViewById<View>(R.id.bottomControlBar)?.parent as? View
        controlsRoot?.findViewById<com.google.android.material.button.MaterialButton>(R.id.speedButton)?.text = label
    }

    private fun cycleDisplayMode() {
        currentDisplayMode = when (currentDisplayMode) {
            DisplayMode.FIT -> DisplayMode.FILL
            DisplayMode.FILL -> DisplayMode.ZOOM
            DisplayMode.ZOOM -> DisplayMode.FIT
        }

        val resizeMode = when (currentDisplayMode) {
            DisplayMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            DisplayMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            DisplayMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        binding.playerView.resizeMode = resizeMode
        updateDisplayModeButton()
    }

    private fun updateDisplayModeButton() {
        val label = when (currentDisplayMode) {
            DisplayMode.FIT -> getString(R.string.display_fit)
            DisplayMode.FILL -> getString(R.string.display_fill)
            DisplayMode.ZOOM -> getString(R.string.display_zoom)
        }

        val controlsRoot = binding.playerView.findViewById<View>(R.id.bottomControlBar)?.parent as? View
        controlsRoot?.findViewById<com.google.android.material.button.MaterialButton>(R.id.displayModeButton)?.text = label
    }

    private fun toggleOrientationLock() {
        isOrientationLocked = !isOrientationLocked

        requestedOrientation = if (isOrientationLocked) {
            // Lock to current orientation
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }

        val iconRes = if (isOrientationLocked) {
            android.R.drawable.ic_lock_lock
        } else {
            android.R.drawable.ic_lock_idle_lock
        }

        val controlsRoot = binding.playerView.findViewById<View>(R.id.bottomControlBar)?.parent as? View
        controlsRoot?.findViewById<ImageButton>(R.id.lockButton)?.setImageResource(iconRes)
    }

    private fun toggleFullscreen() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun showMoreOptions() {
        val options = arrayOf(
            getString(R.string.pip_mode),
            getString(R.string.settings)
        )

        MaterialAlertDialogBuilder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> enterPictureInPictureMode()
                    1 -> showSettings()
                }
            }
            .show()
    }

    private fun enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    private fun showSettings() {
        // TODO: Show settings dialog
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        binding.errorOverlay.visibility = View.VISIBLE
        binding.errorText.text = message
        binding.retryButton.setOnClickListener {
            binding.errorOverlay.visibility = View.GONE
            loadVideo()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.playerView.hideController()
        } else {
            binding.playerView.showController()
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
