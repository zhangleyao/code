package com.example.dlnagallery

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 第二步：浏览服务器目录，点击图片/视频进入全屏。
 * 文件夹和媒体混合展示，文件夹排在前面。
 */
class BrowserActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_UDN = "extra_udn"
        const val EXTRA_NAME = "extra_name"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var udn: String
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: GridAdapter

    // 目录栈：根目录为 "0"
    private data class Entry(val id: String, val title: String)
    private val stack = mutableListOf(Entry("0", ""))
    private val currentMedia = mutableListOf<MediaItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        udn = intent.getStringExtra(EXTRA_UDN) ?: return finish()

        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rv = findViewById(R.id.rvMedia)
        progress = findViewById(R.id.progress)

        adapter = GridAdapter(
            onFolderClick = { folder ->
                stack.add(Entry(folder.id, folder.title))
                browseTop()
            },
            onMediaClick = { media ->
                val idx = currentMedia.indexOfFirst { it.id == media.id && it.url == media.url }
                val intent = Intent(this, ViewerActivity::class.java).apply {
                    putParcelableArrayListExtra(
                        ViewerActivity.EXTRA_MEDIA,
                        ArrayList(currentMedia)
                    )
                    putExtra(ViewerActivity.EXTRA_INDEX, if (idx >= 0) idx else 0)
                    putExtra(ViewerActivity.EXTRA_UDN, udn)
                }
                startActivity(intent)
            }
        )
        rv.layoutManager = GridLayoutManager(this, 3)
        rv.adapter = adapter

        browseTop()
    }

    private fun browseTop() {
        val top = stack.last()
        toolbar.title = top.title.ifEmpty { intent.getStringExtra(EXTRA_NAME) ?: "DLNA" }
        progress.visibility = View.VISIBLE

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DlnaRepository.browse(udn, top.id) }
            }
            progress.visibility = View.GONE
            result.onSuccess { r ->
                currentMedia.clear()
                currentMedia.addAll(r.media)
                adapter.submit(r.folders, r.media)
            }.onFailure { e ->
                com.google.android.material.snackbar.Snackbar
                    .make(rv, e.message ?: "浏览失败", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            browseTop()
        } else {
            super.onBackPressed()
        }
    }

    // ---------------- adapter ----------------

    private sealed class Cell {
        data class Folder(val item: FolderItem) : Cell()
        data class Media(val item: MediaItem) : Cell()
    }

    private inner class GridAdapter(
        private val onFolderClick: (FolderItem) -> Unit,
        private val onMediaClick: (MediaItem) -> Unit
    ) : RecyclerView.Adapter<GridAdapter.VH>() {

        private var cells: List<Cell> = emptyList()

        fun submit(folders: List<FolderItem>, media: List<MediaItem>) {
            cells = buildList {
                folders.forEach { add(Cell.Folder(it)) }
                media.forEach { add(Cell.Media(it)) }
            }
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val thumb: ImageView = view.findViewById(R.id.ivThumb)
            val label: TextView = view.findViewById(R.id.tvLabel)
            val badge: ImageView = view.findViewById(R.id.ivBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media, parent, false)
            return VH(v)
        }

        override fun getItemCount() = cells.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            when (val c = cells[position]) {
                is Cell.Folder -> {
                    holder.thumb.setImageResource(R.drawable.ic_folder)
                    holder.thumb.scaleType = ImageView.ScaleType.CENTER
                    holder.label.text = c.item.title
                    holder.badge.visibility = View.GONE
                    holder.itemView.setOnClickListener { onFolderClick(c.item) }
                }
                is Cell.Media -> {
                    holder.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                    holder.label.text = c.item.title
                    if (c.item.isVideo) {
                        holder.thumb.setImageResource(R.drawable.bg_video_placeholder)
                        holder.badge.visibility = View.VISIBLE
                    } else {
                        holder.badge.visibility = View.GONE
                        holder.thumb.load(c.item.url) {
                            placeholder(R.drawable.bg_video_placeholder)
                            error(R.drawable.bg_video_placeholder)
                            size(480, 480)
                        }
                    }
                    holder.itemView.setOnClickListener { onMediaClick(c.item) }
                }
            }
        }
    }
}
