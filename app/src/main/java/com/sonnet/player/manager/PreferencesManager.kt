package com.sonnet.player.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class PreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // Playback Settings
    fun getDefaultPlaybackSpeed(): Float {
        return prefs.getString(KEY_DEFAULT_SPEED, "1.0")?.toFloatOrNull() ?: 1.0f
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit().putString(KEY_DEFAULT_SPEED, speed.toString()).apply()
    }

    // Display Mode
    fun getDefaultDisplayMode(): String {
        return prefs.getString(KEY_DISPLAY_MODE, DISPLAY_MODE_FIT) ?: DISPLAY_MODE_FIT
    }

    fun setDefaultDisplayMode(mode: String) {
        prefs.edit().putString(KEY_DISPLAY_MODE, mode).apply()
    }

    // Hardware Acceleration
    fun isHardwareAccelerationEnabled(): Boolean {
        return prefs.getBoolean(KEY_HARDWARE_ACCEL, true)
    }

    fun setHardwareAccelerationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HARDWARE_ACCEL, enabled).apply()
    }

    // Cache Settings
    fun getCacheSize(): Int {
        return prefs.getString(KEY_CACHE_SIZE, "50")?.toIntOrNull() ?: 50
    }

    fun setCacheSize(sizeMB: Int) {
        prefs.edit().putString(KEY_CACHE_SIZE, sizeMB.toString()).apply()
    }

    // Resume Playback
    fun isResumePlaybackEnabled(): Boolean {
        return prefs.getBoolean(KEY_RESUME_PLAYBACK, true)
    }

    fun setResumePlaybackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME_PLAYBACK, enabled).apply()
    }

    // Auto Cleanup
    fun isAutoCleanupEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CLEANUP, true)
    }

    fun setAutoCleanupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLEANUP, enabled).apply()
    }

    fun getHistoryLimit(): Int {
        return prefs.getString(KEY_HISTORY_LIMIT, "100")?.toIntOrNull() ?: 100
    }

    // Last played video
    fun getLastPlayedPath(): String? {
        return prefs.getString(KEY_LAST_PLAYED, null)
    }

    fun setLastPlayedPath(path: String?) {
        if (path != null) {
            prefs.edit().putString(KEY_LAST_PLAYED, path).apply()
        } else {
            prefs.edit().remove(KEY_LAST_PLAYED).apply()
        }
    }

    // Volume and Brightness
    fun getLastVolume(): Float {
        return prefs.getFloat(KEY_LAST_VOLUME, 1.0f)
    }

    fun setLastVolume(volume: Float) {
        prefs.edit().putFloat(KEY_LAST_VOLUME, volume).apply()
    }

    fun getLastBrightness(): Float {
        return prefs.getFloat(KEY_LAST_BRIGHTNESS, -1.0f)
    }

    fun setLastBrightness(brightness: Float) {
        prefs.edit().putFloat(KEY_LAST_BRIGHTNESS, brightness).apply()
    }

    // Gesture Settings
    fun areGesturesEnabled(): Boolean {
        return prefs.getBoolean(KEY_GESTURES_ENABLED, true)
    }

    fun setGesturesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURES_ENABLED, enabled).apply()
    }

    fun getSeekAmount(): Int {
        return prefs.getString(KEY_SEEK_AMOUNT, "10")?.toIntOrNull() ?: 10
    }

    companion object {
        // Keys
        private const val KEY_DEFAULT_SPEED = "pref_default_speed"
        private const val KEY_DISPLAY_MODE = "pref_display_mode"
        private const val KEY_HARDWARE_ACCEL = "pref_hardware_accel"
        private const val KEY_CACHE_SIZE = "pref_cache_size"
        private const val KEY_RESUME_PLAYBACK = "pref_resume_playback"
        private const val KEY_AUTO_CLEANUP = "pref_auto_cleanup"
        private const val KEY_HISTORY_LIMIT = "pref_history_limit"
        private const val KEY_LAST_PLAYED = "last_played_path"
        private const val KEY_LAST_VOLUME = "last_volume"
        private const val KEY_LAST_BRIGHTNESS = "last_brightness"
        private const val KEY_GESTURES_ENABLED = "pref_gestures_enabled"
        private const val KEY_SEEK_AMOUNT = "pref_seek_amount"

        // Display Modes
        const val DISPLAY_MODE_FIT = "fit"
        const val DISPLAY_MODE_FILL = "fill"
        const val DISPLAY_MODE_STRETCH = "stretch"
        const val DISPLAY_MODE_ZOOM = "zoom"

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferencesManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
