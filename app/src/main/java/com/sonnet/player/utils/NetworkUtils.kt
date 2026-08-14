package com.sonnet.player.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class for network operations
 * Handles URL validation, network state checking, and streaming protocol detection
 */
object NetworkUtils {

    /**
     * Network connection type
     */
    enum class ConnectionType {
        WIFI,
        CELLULAR,
        ETHERNET,
        NONE
    }

    /**
     * Streaming protocol type
     */
    enum class StreamingProtocol {
        HTTP,
        HTTPS,
        RTSP,
        RTMP,
        HLS,
        DASH,
        UNKNOWN
    }

    /**
     * Network state data class
     */
    data class NetworkState(
        val isConnected: Boolean,
        val connectionType: ConnectionType,
        val isMetered: Boolean = false,
        val linkDownstreamBandwidthKbps: Int = 0
    )

    /**
     * Validate URL format
     */
    fun isValidUrl(url: String): Boolean {
        return try {
            Patterns.WEB_URL.matcher(url).matches() &&
                (url.startsWith("http://", ignoreCase = true) ||
                 url.startsWith("https://", ignoreCase = true) ||
                 url.startsWith("rtsp://", ignoreCase = true) ||
                 url.startsWith("rtmp://", ignoreCase = true))
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate streaming URL with additional checks
     */
    fun isValidStreamingUrl(url: String): Boolean {
        if (!isValidUrl(url)) return false

        return try {
            val uri = URL(url)
            val protocol = uri.protocol.lowercase()

            protocol in setOf("http", "https", "rtsp", "rtmp")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect streaming protocol from URL
     */
    fun detectStreamingProtocol(url: String): StreamingProtocol {
        return when {
            url.startsWith("https://", ignoreCase = true) -> {
                when {
                    url.contains(".m3u8", ignoreCase = true) -> StreamingProtocol.HLS
                    url.contains(".mpd", ignoreCase = true) -> StreamingProtocol.DASH
                    else -> StreamingProtocol.HTTPS
                }
            }
            url.startsWith("http://", ignoreCase = true) -> {
                when {
                    url.contains(".m3u8", ignoreCase = true) -> StreamingProtocol.HLS
                    url.contains(".mpd", ignoreCase = true) -> StreamingProtocol.DASH
                    else -> StreamingProtocol.HTTP
                }
            }
            url.startsWith("rtsp://", ignoreCase = true) -> StreamingProtocol.RTSP
            url.startsWith("rtmp://", ignoreCase = true) -> StreamingProtocol.RTMP
            else -> StreamingProtocol.UNKNOWN
        }
    }

    /**
     * Check if network is available
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }

    /**
     * Get current network state
     */
    fun getNetworkState(context: Context): NetworkState {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkState(false, ConnectionType.NONE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            if (capabilities == null) {
                return NetworkState(false, ConnectionType.NONE)
            }

            val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val bandwidth = capabilities.linkDownstreamBandwidthKbps

            val connectionType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.NONE
            }

            return NetworkState(
                isConnected = isConnected,
                connectionType = connectionType,
                isMetered = isMetered,
                linkDownstreamBandwidthKbps = bandwidth
            )
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            val isConnected = networkInfo?.isConnected == true

            @Suppress("DEPRECATION")
            val connectionType = when (networkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> ConnectionType.WIFI
                ConnectivityManager.TYPE_MOBILE -> ConnectionType.CELLULAR
                ConnectivityManager.TYPE_ETHERNET -> ConnectionType.ETHERNET
                else -> ConnectionType.NONE
            }

            @Suppress("DEPRECATION")
            val isMetered = connectivityManager.isActiveNetworkMetered

            return NetworkState(
                isConnected = isConnected,
                connectionType = connectionType,
                isMetered = isMetered
            )
        }
    }

    /**
     * Check if connected to WiFi
     */
    fun isWifiConnected(context: Context): Boolean {
        return getNetworkState(context).connectionType == ConnectionType.WIFI
    }

    /**
     * Check if connection is metered (cellular data)
     */
    fun isMeteredConnection(context: Context): Boolean {
        return getNetworkState(context).isMetered
    }

    /**
     * Observe network state changes as Flow
     */
    fun observeNetworkState(context: Context): Flow<NetworkState> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (connectivityManager == null) {
            trySend(NetworkState(false, ConnectionType.NONE))
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getNetworkState(context))
            }

            override fun onLost(network: Network) {
                trySend(getNetworkState(context))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getNetworkState(context))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }

        // Send initial state
        trySend(getNetworkState(context))

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    /**
     * Test URL accessibility
     * Performs HEAD request to check if URL is reachable
     */
    suspend fun testUrlAccessibility(url: String, timeoutMs: Int = 5000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val urlConnection = URL(url).openConnection() as HttpURLConnection
                urlConnection.apply {
                    requestMethod = "HEAD"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = true
                }

                val responseCode = urlConnection.responseCode
                urlConnection.disconnect()

                responseCode in 200..399
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Get content type from URL
     */
    suspend fun getContentType(url: String, timeoutMs: Int = 5000): String? =
        withContext(Dispatchers.IO) {
            try {
                val urlConnection = URL(url).openConnection() as HttpURLConnection
                urlConnection.apply {
                    requestMethod = "HEAD"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                }

                val contentType = urlConnection.contentType
                urlConnection.disconnect()

                contentType
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Estimate if bandwidth is sufficient for streaming
     * @param requiredBitrate Required bitrate in Kbps
     */
    fun isBandwidthSufficient(context: Context, requiredBitrate: Int): Boolean {
        val networkState = getNetworkState(context)

        if (!networkState.isConnected) return false

        // For WiFi and Ethernet, assume sufficient bandwidth
        if (networkState.connectionType in listOf(ConnectionType.WIFI, ConnectionType.ETHERNET)) {
            return true
        }

        // For cellular, check reported bandwidth (if available)
        return networkState.linkDownstreamBandwidthKbps >= requiredBitrate
    }

    /**
     * Get recommended quality based on network conditions
     */
    fun getRecommendedQuality(context: Context): VideoQuality {
        val networkState = getNetworkState(context)

        return when {
            !networkState.isConnected -> VideoQuality.LOW
            networkState.connectionType == ConnectionType.WIFI -> VideoQuality.HIGH
            networkState.connectionType == ConnectionType.ETHERNET -> VideoQuality.HIGH
            networkState.connectionType == ConnectionType.CELLULAR -> {
                when {
                    networkState.linkDownstreamBandwidthKbps >= 5000 -> VideoQuality.HIGH
                    networkState.linkDownstreamBandwidthKbps >= 2500 -> VideoQuality.MEDIUM
                    else -> VideoQuality.LOW
                }
            }
            else -> VideoQuality.MEDIUM
        }
    }

    /**
     * Video quality enum
     */
    enum class VideoQuality {
        LOW,    // 360p or lower
        MEDIUM, // 480p-720p
        HIGH    // 1080p or higher
    }

    /**
     * Extract domain from URL
     */
    fun extractDomain(url: String): String? {
        return try {
            URL(url).host
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if URL is a local network address
     */
    fun isLocalNetworkUrl(url: String): Boolean {
        return try {
            val host = URL(url).host.lowercase()
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.startsWith("172.16.") ||
            host == "localhost" ||
            host == "127.0.0.1"
        } catch (e: Exception) {
            false
        }
    }
}
