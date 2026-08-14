package com.sonnet.player.controller

import android.content.Context
import android.net.Uri
import com.sonnet.player.manager.PlayerManager
import com.sonnet.player.model.PlaybackSpeed
import com.sonnet.player.model.PlaybackState
import com.sonnet.player.model.VideoFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Controller for playback operations with zero-lag seek and smooth scrubbing
 */
class PlaybackController(context: Context) {

    private val playerManager = PlayerManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentVideo: VideoFile? = null
    private var isScrubbing = false
    private var scrubPosition = 0L

    val playbackState: StateFlow<PlaybackState>
        get() = playerManager.playbackState

    /**
     * Load and prepare a video for playback
     */
    fun loadVideo(video: VideoFile, autoPlay: Boolean = false) {
        currentVideo = video

        scope.launch {
            playerManager.prepareVideo(
                uri = video.uri,
                startPosition = video.lastPlayedPosition,
                preload = true
            )

            if (autoPlay) {
                playerManager.play()
            }
        }
    }

    /**
     * Load video from URI
     */
    fun loadVideo(uri: Uri, autoPlay: Boolean = false) {
        val video = VideoFile(
            uri = uri,
            title = uri.lastPathSegment ?: "Unknown"
        )
        loadVideo(video, autoPlay)
    }

    /**
     * Play current video
     */
    fun play() {
        playerManager.play()
    }

    /**
     * Pause playback
     */
    fun pause() {
        playerManager.pause()
    }

    /**
     * Toggle between play and pause
     */
    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    /**
     * Seek to specific position with frame accuracy
     * @param positionMs Target position in milliseconds
     * @param resumePlayback Whether to resume playback after seeking
     */
    fun seekTo(positionMs: Long, resumePlayback: Boolean = false) {
        val wasPlaying = playbackState.value.isPlaying

        if (wasPlaying && !resumePlayback) {
            playerManager.pause()
        }

        playerManager.seekTo(positionMs)

        if (resumePlayback && wasPlaying) {
            playerManager.play()
        }
    }

    /**
     * Seek relative to current position
     * @param deltaMs Delta in milliseconds (positive = forward, negative = backward)
     */
    fun seekRelative(deltaMs: Long) {
        playerManager.seekRelative(deltaMs)
    }

    /**
     * Skip forward by standard interval (10 seconds)
     */
    fun skipForward(seconds: Int = 10) {
        seekRelative(seconds * 1000L)
    }

    /**
     * Skip backward by standard interval (10 seconds)
     */
    fun skipBackward(seconds: Int = 10) {
        seekRelative(-seconds * 1000L)
    }

    /**
     * Start scrubbing mode for smooth timeline interaction
     */
    fun startScrubbing() {
        isScrubbing = true
        scrubPosition = playbackState.value.currentPosition

        // Pause playback during scrubbing for performance
        if (playbackState.value.isPlaying) {
            playerManager.pause()
        }
    }

    /**
     * Update scrub position during drag
     * @param position Target position in milliseconds
     */
    fun updateScrubPosition(position: Long) {
        if (!isScrubbing) return

        scrubPosition = position.coerceIn(0, playbackState.value.duration)

        // Seek immediately for real-time preview
        playerManager.seekTo(scrubPosition)
    }

    /**
     * Update scrub position by progress (0.0 to 1.0)
     */
    fun updateScrubProgress(progress: Float) {
        val duration = playbackState.value.duration
        if (duration > 0) {
            val position = (duration * progress.coerceIn(0f, 1f)).toLong()
            updateScrubPosition(position)
        }
    }

    /**
     * End scrubbing and optionally resume playback
     */
    fun endScrubbing(resumePlayback: Boolean = true) {
        if (!isScrubbing) return

        isScrubbing = false

        // Final seek to exact position
        playerManager.seekTo(scrubPosition)

        if (resumePlayback) {
            playerManager.play()
        }
    }

    /**
     * Set playback speed
     */
    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    /**
     * Set playback speed from preset
     */
    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        playerManager.setPlaybackSpeed(speed)
    }

    /**
     * Cycle to next playback speed
     */
    fun cyclePlaybackSpeed() {
        val currentSpeed = playbackState.value.playbackSpeed
        val speeds = PlaybackSpeed.entries
        val currentIndex = speeds.indexOfFirst { abs(it.value - currentSpeed) < 0.01f }
        val nextIndex = (currentIndex + 1) % speeds.size
        setPlaybackSpeed(speeds[nextIndex])
    }

    /**
     * Stop playback and clear video
     */
    fun stop() {
        playerManager.stop()
        currentVideo = null
        isScrubbing = false
    }

    /**
     * Get current video
     */
    fun getCurrentVideo(): VideoFile? = currentVideo

    /**
     * Check if currently scrubbing
     */
    fun isScrubbing(): Boolean = isScrubbing

    /**
     * Get current scrub position
     */
    fun getScrubPosition(): Long = scrubPosition

    /**
     * Release resources
     */
    fun release() {
        stop()
        // PlayerManager is singleton, don't release it here
    }
}
