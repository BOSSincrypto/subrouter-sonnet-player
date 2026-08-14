package com.sonnet.player.manager

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sonnet.player.model.PlaybackError
import com.sonnet.player.model.PlaybackSpeed
import com.sonnet.player.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Singleton manager for ExoPlayer instance with hardware acceleration and optimization
 */
@OptIn(UnstableApi::class)
class PlayerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var player: ExoPlayer? = null
    private var cache: SimpleCache? = null
    private var updateJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isBuffering = false,
                error = PlaybackError(
                    code = error.errorCode,
                    message = error.message ?: "Unknown playback error"
                )
            )
        }
    }

    /**
     * Initialize the player with hardware acceleration and optimizations
     */
    fun initialize() {
        if (player != null) return

        // Setup cache for efficient playback
        val cacheDir = File(appContext.cacheDir, "media_cache")
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(300 * 1024 * 1024L) // 300MB cache
        cache = SimpleCache(cacheDir, cacheEvictor, androidx.media3.database.StandaloneDatabaseProvider(appContext))

        // Configure renderers factory with hardware acceleration
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        // Configure track selector for best quality
        val trackSelector = DefaultTrackSelector(appContext).apply {
            parameters = buildUponParameters()
                .setPreferredVideoMimeTypes(
                    "video/avc",    // H.264 - best hardware support
                    "video/hevc",   // H.265
                    "video/vp9"     // VP9
                )
                .build()
        }

        // Configure load control for smooth playback
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,  // min buffer
                50000,  // max buffer
                2500,   // playback buffer
                5000    // playback rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Create data source factory with cache
        val dataSourceFactory = DefaultDataSource.Factory(appContext)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Build ExoPlayer
        player = ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
            .apply {
                addListener(playerListener)
                playWhenReady = false
                // Hardware acceleration settings
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
    }

    /**
     * Get the ExoPlayer instance
     */
    fun getPlayer(): ExoPlayer {
        if (player == null) {
            initialize()
        }
        return player!!
    }

    /**
     * Prepare video for playback with optional preloading
     */
    fun prepareVideo(uri: Uri, startPosition: Long = 0L, preload: Boolean = true) {
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .build()

        getPlayer().apply {
            setMediaItem(mediaItem)
            prepare()
            if (startPosition > 0) {
                seekTo(startPosition)
            }
        }

        updatePlaybackState()
    }

    /**
     * Play the current video
     */
    fun play() {
        getPlayer().play()
    }

    /**
     * Pause playback
     */
    fun pause() {
        getPlayer().pause()
    }

    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        val player = getPlayer()
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    /**
     * Seek to position with frame accuracy
     */
    fun seekTo(positionMs: Long) {
        getPlayer().seekTo(positionMs)
    }

    /**
     * Seek relative to current position
     */
    fun seekRelative(deltaMs: Long) {
        val player = getPlayer()
        val newPosition = (player.currentPosition + deltaMs).coerceIn(0, player.duration)
        seekTo(newPosition)
    }

    /**
     * Set playback speed
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 2.0f)
        getPlayer().setPlaybackSpeed(clampedSpeed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clampedSpeed)
    }

    /**
     * Set playback speed from enum
     */
    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        setPlaybackSpeed(speed.value)
    }

    /**
     * Stop playback and reset
     */
    fun stop() {
        getPlayer().apply {
            stop()
            clearMediaItems()
        }
        _playbackState.value = PlaybackState.IDLE
        stopPositionUpdates()
    }

    /**
     * Release all resources
     */
    fun release() {
        stopPositionUpdates()
        player?.apply {
            removeListener(playerListener)
            release()
        }
        player = null

        cache?.release()
        cache = null

        _playbackState.value = PlaybackState.IDLE
    }

    private fun updatePlaybackState() {
        val player = player ?: return

        _playbackState.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentPosition = player.currentPosition.coerceAtLeast(0),
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            bufferedPosition = player.bufferedPosition.coerceAtLeast(0),
            playbackSpeed = player.playbackParameters.speed,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            error = _playbackState.value.error
        )
    }

    private fun startPositionUpdates() {
        if (updateJob?.isActive == true) return

        updateJob = scope.launch {
            while (isActive) {
                updatePlaybackState()
                delay(16L) // ~60fps updates for smooth UI
            }
        }
    }

    private fun stopPositionUpdates() {
        updateJob?.cancel()
        updateJob = null
    }

    companion object {
        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context).also { instance = it }
            }
        }

        fun releaseInstance() {
            instance?.release()
            instance = null
        }
    }
}
