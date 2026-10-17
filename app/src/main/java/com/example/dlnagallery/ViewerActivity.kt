package com.example.dlnagallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.github.chrisbanes.photoview.PhotoView

/**
 * 全屏查看器：竖向 ViewPager2。
 * 上滑 = 下一个，下滑 = 上一个；图片支持双指缩放。
 * 视频自动播放，滑走后自动释放播放器。
 */
class ViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDIA = "extra_media"
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_UDN = "extra_udn"
    }

    private lateinit var pager: ViewPager2
    private lateinit var items: List<MediaItem>
    private lateinit var adapter: MediaPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        @Suppress("UNCHECKED_CAST")
        items = intent.getParcelableArrayListExtra<MediaItem>(EXTRA_MEDIA) ?: emptyList()
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))

        pager = findViewById(R.id.viewPager)
        pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        adapter = MediaPagerAdapter(items)
        pager.adapter = adapter
        pager.offscreenPageLimit = 1
        pager.setCurrentItem(startIndex, false)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                adapter.setActivePosition(position)
            }
        })
        adapter.setActivePosition(startIndex)

        // 沉浸式全屏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onDestroy() {
        adapter.releaseAll()
        super.onDestroy()
    }

    // ---------------- pager adapter ----------------

    private inner class MediaPagerAdapter(
        private val items: List<MediaItem>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_IMAGE = 0
            private const val TYPE_VIDEO = 1
        }

        private var activePosition = -1
        private val holders = mutableMapOf<Int, RecyclerView.ViewHolder>()

        fun setActivePosition(position: Int) {
            activePosition = position
            // 只有当前页的视频才播放
            holders.forEach { (pos, h) ->
                if (h is VideoVH) h.setActive(pos == position)
            }
        }

        fun releaseAll() {
            holders.values.filterIsInstance<VideoVH>().forEach { it.release() }
            holders.clear()
        }

        override fun getItemViewType(position: Int): Int =
            if (items[position].isVideo) TYPE_VIDEO else TYPE_IMAGE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_VIDEO) {
                VideoVH(inflater.inflate(R.layout.page_video, parent, false))
            } else {
                ImageVH(inflater.inflate(R.layout.page_image, parent, false))
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            when (holder) {
                is ImageVH -> holder.bind(item)
                is VideoVH -> {
                    holder.bind(item)
                    holder.setActive(position == activePosition)
                }
            }
            holders[position] = holder
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is VideoVH) holder.release()
            holders.entries.removeAll { it.value == holder }
            super.onViewRecycled(holder)
        }
    }

    private inner class ImageVH(view: View) : RecyclerView.ViewHolder(view) {
        private val photoView: PhotoView = view.findViewById(R.id.photoView)

        fun bind(item: MediaItem) {
            photoView.setScale(1f, false)
            photoView.load(item.url) {
                placeholder(R.drawable.bg_video_placeholder)
                error(R.drawable.bg_video_placeholder)
            }
            // 图片放大后，把滑动事件留给 PhotoView 自己处理（平移/缩放），
            // 未放大时交给 ViewPager2 做上下翻页。
            photoView.setOnMatrixChangeListener {
                val zoomed = photoView.scale > 1.05f
                (itemView.parent as? View)?.parent?.let { pagerParent ->
                    pagerParent.requestDisallowInterceptTouchEvent(zoomed)
                }
            }
        }
    }

    private inner class VideoVH(view: View) : RecyclerView.ViewHolder(view) {
        private val playerView: PlayerView = view.findViewById(R.id.playerView)
        private var player: ExoPlayer? = null
        private var boundUrl: String? = null

        fun bind(item: MediaItem) {
            boundUrl = item.url
        }

        fun setActive(active: Boolean) {
            if (active) {
                if (player == null) {
                    player = ExoPlayer.Builder(itemView.context).build().also { p ->
                        playerView.player = p
                        p.repeatMode = ExoPlayer.REPEAT_MODE_ONE
                    }
                }
                val url = boundUrl
                if (url != null && player?.currentMediaItem?.localConfiguration?.uri.toString() != url) {
                    player?.setMediaItem(ExoMediaItem.fromUri(url))
                    player?.prepare()
                }
                player?.playWhenReady = true
            } else {
                player?.playWhenReady = false
            }
        }

        fun release() {
            playerView.player = null
            player?.release()
            player = null
        }
    }
}
