package com.sonnet.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sonnet.player.databinding.ItemVideoBinding

class VideoAdapter(
    private val videos: List<VideoItem>,
    private val onVideoClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount(): Int = videos.size

    inner class VideoViewHolder(
        private val binding: ItemVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoItem) {
            binding.title.text = video.title
            binding.subtitle.text = video.getSubtitle()
            binding.duration.text = video.getFormattedDuration()

            // Load thumbnail - for now, use placeholder
            // In production, use Glide or Coil to load video thumbnail
            binding.thumbnail.setImageDrawable(null)

            binding.root.setOnClickListener {
                onVideoClick(video)
            }

            binding.menuButton.setOnClickListener {
                // Show popup menu for additional options
                // TODO: Implement menu (share, delete, info, etc.)
            }
        }
    }
}
