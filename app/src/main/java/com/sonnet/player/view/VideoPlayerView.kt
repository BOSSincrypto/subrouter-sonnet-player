package com.sonnet.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.GestureDetectorCompat
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import com.sonnet.player.model.AspectRatioMode
import kotlin.math.abs

/**
 * Custom video player view with gesture controls and aspect ratio handling
 * Uses SurfaceView for better performance and lower battery consumption
 */
@SuppressLint("ClickableViewAccessibility")
class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val surfaceView: SurfaceView
    private val aspectRatioFrameLayout: AspectRatioFrameLayout

    private var player: Player? = null
    private var gestureDetector: GestureDetectorCompat

    private var aspectRatioMode = AspectRatioMode.FIT
    private var videoAspectRatio = 16f / 9f

    // Gesture callbacks
    var onSeekGesture: ((deltaMs: Long) -> Unit)? = null
    var onBrightnessGesture: ((delta: Float) -> Unit)? = null
    var onVolumeGesture: ((delta: Float) -> Unit)? = null
    var onSingleTap: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {

        private var totalScrollX = 0f
        private var totalScrollY = 0f
        private var isHorizontalScroll = false
        private var isVerticalScroll = false
        private var scrollStarted = false

        override fun onDown(e: MotionEvent): Boolean {
            totalScrollX = 0f
            totalScrollY = 0f
            scrollStarted = false
            isHorizontalScroll = false
            isVerticalScroll = false
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap?.invoke()
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1 == null) return false

            totalScrollX += abs(distanceX)
            totalScrollY += abs(distanceY)

            // Determine scroll direction on first significant movement
            if (!scrollStarted && (totalScrollX > 20 || totalScrollY > 20)) {
                scrollStarted = true
                isHorizontalScroll = totalScrollX > totalScrollY
                isVerticalScroll = !isHorizontalScroll
            }

            if (!scrollStarted) return false

            when {
                isHorizontalScroll -> {
                    // Horizontal swipe: seek video
                    val seekDelta = (-distanceX * 100).toLong() // 100ms per pixel
                    onSeekGesture?.invoke(seekDelta)
                }
                isVerticalScroll -> {
                    // Vertical swipe: brightness (left) or volume (right)
                    val delta = -distanceY / height * 0.02f // 2% per screen height percent

                    if (e1.x < width / 2) {
                        // Left side: brightness
                        onBrightnessGesture?.invoke(delta)
                    } else {
                        // Right side: volume
                        onVolumeGesture?.invoke(delta)
                    }
                }
            }

            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            // Quick fling for faster seeking
            if (abs(velocityX) > abs(velocityY)) {
                val seekDelta = (velocityX / 10).toLong() // Scale velocity to seek amount
                onSeekGesture?.invoke(seekDelta)
                return true
            }
            return false
        }
    }

    init {
        // Create aspect ratio container
        aspectRatioFrameLayout = AspectRatioFrameLayout(context).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        addView(aspectRatioFrameLayout)

        // Create surface view for video rendering
        surfaceView = SurfaceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        aspectRatioFrameLayout.addView(surfaceView)

        // Setup gesture detector
        gestureDetector = GestureDetectorCompat(context, gestureListener)

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    /**
     * Attach player to this view
     */
    fun setPlayer(player: Player?) {
        this.player?.clearVideoSurface()
        this.player = player
        player?.setVideoSurfaceView(surfaceView)
    }

    /**
     * Set aspect ratio mode
     */
    fun setAspectRatioMode(mode: AspectRatioMode) {
        aspectRatioMode = mode
        applyAspectRatioMode()
    }

    /**
     * Set video aspect ratio (width/height)
     */
    fun setVideoAspectRatio(aspectRatio: Float) {
        if (aspectRatio > 0) {
            videoAspectRatio = aspectRatio
            applyAspectRatioMode()
        }
    }

    /**
     * Get current aspect ratio mode
     */
    fun getAspectRatioMode(): AspectRatioMode = aspectRatioMode

    /**
     * Get surface view for advanced operations
     */
    fun getSurfaceView(): SurfaceView = surfaceView

    /**
     * Switch to TextureView mode (for effects/transformations)
     * Note: TextureView uses more memory and battery
     */
    fun useTextureView(): TextureView {
        aspectRatioFrameLayout.removeView(surfaceView)

        val textureView = TextureView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        aspectRatioFrameLayout.addView(textureView)
        player?.setVideoTextureView(textureView)

        return textureView
    }

    private fun applyAspectRatioMode() {
        aspectRatioFrameLayout.apply {
            when (aspectRatioMode) {
                AspectRatioMode.FIT -> {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setAspectRatio(videoAspectRatio)
                }
                AspectRatioMode.FILL -> {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setAspectRatio(videoAspectRatio)
                }
                AspectRatioMode.CROP -> {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    setAspectRatio(videoAspectRatio)
                }
                AspectRatioMode.STRETCH -> {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    setAspectRatio(RATIONAL_UNSET)
                }
            }
        }
        requestLayout()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        player?.clearVideoSurface()
        player = null
    }

    companion object {
        private const val RATIONAL_UNSET = 0f
    }
}
