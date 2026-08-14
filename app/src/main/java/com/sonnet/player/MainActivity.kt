package com.sonnet.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.sonnet.player.adapter.VideoAdapter
import com.sonnet.player.databinding.ActivityMainBinding
import com.sonnet.player.model.VideoItem
import com.sonnet.player.util.VideoScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var recentAdapter: VideoAdapter
    private val videos = mutableListOf<VideoItem>()
    private val recentVideos = mutableListOf<VideoItem>()
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        checkPermissions()
    }
    
    private fun setupViews() {
        setSupportActionBar(binding.toolbar)
        
        // Setup local videos RecyclerView
        videoAdapter = VideoAdapter(videos) { video ->
            openPlayer(video)
        }
        
        binding.localRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = videoAdapter
            setHasFixedSize(true)
        }
        
        // Setup recent videos RecyclerView
        recentAdapter = VideoAdapter(recentVideos) { video ->
            openPlayer(video)
        }
        
        binding.recentRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = recentAdapter
            setHasFixedSize(true)
        }
        
        // Setup search functionality
        binding.searchBar.setOnClickListener {
            // Implement search
        }
        
        binding.fab.setOnClickListener {
            // Add URL dialog
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                scanVideos()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                scanVideos()
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanVideos()
            }
        }
    }
    
    private fun scanVideos() {
        CoroutineScope(Dispatchers.IO).launch {
            val scannedVideos = VideoScanner.scanVideos(this@MainActivity)
            withContext(Dispatchers.Main) {
                videos.clear()
                videos.addAll(scannedVideos)
                videoAdapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun openPlayer(video: VideoItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("VIDEO_PATH", video.path)
            putExtra("VIDEO_TITLE", video.title)
        }
        startActivity(intent)
    }
}
