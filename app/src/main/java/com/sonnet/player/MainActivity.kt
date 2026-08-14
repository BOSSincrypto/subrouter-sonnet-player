package com.sonnet.player

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sonnet.player.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val localVideos = mutableListOf<VideoItem>()
    private val recentVideos = mutableListOf<VideoItem>()
    private val allVideos = mutableListOf<VideoItem>()
    private lateinit var localAdapter: VideoAdapter
    private lateinit var recentAdapter: VideoAdapter

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val PREFS_NAME = "video_player_prefs"
        private const val KEY_RECENT_VIDEOS = "recent_videos"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerViews()
        setupSearchBar()
        setupFab()
        checkPermissionsAndLoadVideos()
        loadRecentVideos()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupRecyclerViews() {
        // Local videos
        localAdapter = VideoAdapter(localVideos) { video ->
            playVideo(video)
        }
        binding.localRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = localAdapter
            setHasFixedSize(false)
        }

        // Recent videos
        recentAdapter = VideoAdapter(recentVideos) { video ->
            playVideo(video)
        }
        binding.recentRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = recentAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupSearchBar() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterVideos(s?.toString() ?: "")
            }
        })
    }

    private fun setupFab() {
        binding.fabAddUrl.setOnClickListener {
            showAddUrlDialog()
        }
    }

    private fun checkPermissionsAndLoadVideos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                loadLocalVideos()
            } else {
                requestVideoPermission()
            }
        } else {
            // Below Android 13
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                loadLocalVideos()
            } else {
                requestVideoPermission()
            }
        }
    }

    private fun requestVideoPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalVideos()
            } else {
                showPermissionDeniedMessage()
            }
        }
    }

    private fun showPermissionDeniedMessage() {
        binding.emptyState.visibility = View.VISIBLE
        binding.localRecyclerView.visibility = View.GONE
    }

    private fun loadLocalVideos() {
        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                scanLocalVideos()
            }

            allVideos.clear()
            allVideos.addAll(videos)
            localVideos.clear()
            localVideos.addAll(videos)
            localAdapter.notifyDataSetChanged()

            updateEmptyState()
        }
    }

    private fun scanLocalVideos(): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val path = cursor.getString(dataColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                videos.add(
                    VideoItem(
                        id = id,
                        title = name,
                        uri = contentUri,
                        duration = duration,
                        size = size,
                        width = width,
                        height = height,
                        path = path
                    )
                )
            }
        }

        return videos
    }

    private fun loadRecentVideos() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val recentJson = prefs.getString(KEY_RECENT_VIDEOS, null)

        // TODO: Parse JSON and populate recentVideos list
        // For now, just hide the recent section if empty
        updateRecentSection()
    }

    private fun updateRecentSection() {
        if (recentVideos.isEmpty()) {
            binding.recentTitle.visibility = View.GONE
            binding.recentRecyclerView.visibility = View.GONE
        } else {
            binding.recentTitle.visibility = View.VISIBLE
            binding.recentRecyclerView.visibility = View.VISIBLE
            recentAdapter.notifyDataSetChanged()
        }
    }

    private fun filterVideos(query: String) {
        if (query.isEmpty()) {
            localVideos.clear()
            localVideos.addAll(allVideos)
        } else {
            val filtered = allVideos.filter {
                it.title.contains(query, ignoreCase = true)
            }
            localVideos.clear()
            localVideos.addAll(filtered)
        }
        localAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (localVideos.isEmpty() && recentVideos.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.localRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.localRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showAddUrlDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.dialog_url_hint)
            setPadding(64, 32, 64, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_add_url_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_add) { dialog, _ ->
                val url = input.text.toString().trim()
                if (isValidUrl(url)) {
                    playVideoUrl(url)
                    dialog.dismiss()
                } else {
                    // Show error
                    input.error = getString(R.string.invalid_url)
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("rtsp://", ignoreCase = true)
    }

    private fun playVideo(video: VideoItem) {
        saveToRecent(video)

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("VIDEO_URI", video.uri.toString())
            putExtra("VIDEO_TITLE", video.title)
        }
        startActivity(intent)
    }

    private fun playVideoUrl(url: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("VIDEO_URI", url)
            putExtra("VIDEO_TITLE", url)
        }
        startActivity(intent)
    }

    private fun saveToRecent(video: VideoItem) {
        // TODO: Save to SharedPreferences
    }
}
