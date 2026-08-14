package com.sonnet.player.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Utility class for runtime permissions handling
 * Handles storage access with Android 13+ photo picker support
 */
object PermissionUtils {

    /**
     * Storage permissions based on Android version
     */
    private val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * All video-related permissions
     */
    private val VIDEO_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    /**
     * Permission result callback
     */
    interface PermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied(permanentlyDenied: Boolean)
    }

    /**
     * Check if storage permission is granted
     */
    fun hasStoragePermission(context: Context): Boolean {
        // Android 13+ uses granular media permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        }

        // Android 10-12 uses READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if all video permissions are granted
     */
    fun hasVideoPermissions(context: Context): Boolean {
        return VIDEO_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if specific permission is granted
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if permission was permanently denied
     * (user selected "Don't ask again")
     */
    fun isPermissionPermanentlyDenied(activity: Activity, permission: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return !activity.shouldShowRequestPermissionRationale(permission) &&
                   !hasPermission(activity, permission)
        }
        return false
    }

    /**
     * Request storage permissions from Activity
     */
    fun requestStoragePermission(
        activity: Activity,
        requestCode: Int = REQUEST_CODE_STORAGE
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(STORAGE_PERMISSIONS, requestCode)
        }
    }

    /**
     * Request video permissions from Activity
     */
    fun requestVideoPermissions(
        activity: Activity,
        requestCode: Int = REQUEST_CODE_VIDEO
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(VIDEO_PERMISSIONS, requestCode)
        }
    }

    /**
     * Handle permission result
     */
    fun handlePermissionResult(
        activity: Activity,
        permissions: Array<out String>,
        grantResults: IntArray,
        callback: PermissionCallback
    ) {
        if (grantResults.isEmpty()) {
            callback.onPermissionDenied(false)
            return
        }

        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            callback.onPermissionGranted()
        } else {
            // Check if permanently denied
            val permanentlyDenied = permissions.any { permission ->
                isPermissionPermanentlyDenied(activity, permission)
            }
            callback.onPermissionDenied(permanentlyDenied)
        }
    }

    /**
     * Open app settings for manual permission grant
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Show permission rationale message
     */
    fun shouldShowStorageRationale(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        return STORAGE_PERMISSIONS.any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }

    /**
     * Get permission rationale message
     */
    fun getStorageRationaleMessage(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "This app needs access to your videos to display and play them. " +
            "Grant video access permission to continue."
        } else {
            "This app needs storage permission to access video files on your device. " +
            "Grant storage permission to continue."
        }
    }

    /**
     * Get settings redirect message
     */
    fun getSettingsMessage(): String {
        return "Permission was denied. Please enable it manually in Settings to use this feature."
    }

    /**
     * Check if should use photo picker (Android 13+)
     */
    fun shouldUsePhotoPicker(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Create photo picker intent for videos
     */
    fun createVideoPickerIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Intent.ACTION_PICK).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*"))
            }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
    }

    /**
     * Create multiple video picker intent
     */
    fun createMultipleVideoPickerIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Intent.ACTION_PICK).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
    }

    /**
     * Permission request launcher helper for Fragment
     */
    class PermissionLauncher(
        fragment: Fragment,
        private val callback: PermissionCallback
    ) {
        private val launcher: ActivityResultLauncher<Array<String>> =
            fragment.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }

                if (allGranted) {
                    callback.onPermissionGranted()
                } else {
                    val permanentlyDenied = permissions.keys.any { permission ->
                        fragment.activity?.let {
                            isPermissionPermanentlyDenied(it, permission)
                        } ?: false
                    }
                    callback.onPermissionDenied(permanentlyDenied)
                }
            }

        fun launch(permissions: Array<String>) {
            launcher.launch(permissions)
        }

        fun requestStoragePermission() {
            launch(STORAGE_PERMISSIONS)
        }

        fun requestVideoPermissions() {
            launch(VIDEO_PERMISSIONS)
        }
    }

    /**
     * Permission request launcher helper for Activity
     */
    class ActivityPermissionLauncher(
        activity: Activity,
        private val callback: PermissionCallback
    ) {
        private val launcher: ActivityResultLauncher<Array<String>> =
            (activity as? androidx.activity.ComponentActivity)?.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }

                if (allGranted) {
                    callback.onPermissionGranted()
                } else {
                    val permanentlyDenied = permissions.keys.any { permission ->
                        isPermissionPermanentlyDenied(activity, permission)
                    }
                    callback.onPermissionDenied(permanentlyDenied)
                }
            } ?: throw IllegalStateException("Activity must be ComponentActivity")

        fun launch(permissions: Array<String>) {
            launcher.launch(permissions)
        }

        fun requestStoragePermission() {
            launch(STORAGE_PERMISSIONS)
        }

        fun requestVideoPermissions() {
            launch(VIDEO_PERMISSIONS)
        }
    }

    // Request codes for traditional permission requests
    const val REQUEST_CODE_STORAGE = 1001
    const val REQUEST_CODE_VIDEO = 1002
    const val REQUEST_CODE_SETTINGS = 1003

    /**
     * Network permissions (not runtime permissions on most Android versions)
     */
    fun hasNetworkPermission(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.INTERNET)
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        }
        return true // Not required before Android 13
    }

    /**
     * Request notification permission (Android 13+)
     */
    fun requestNotificationPermission(
        activity: Activity,
        requestCode: Int = 1004
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }
}
