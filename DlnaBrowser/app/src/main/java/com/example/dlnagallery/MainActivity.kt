package com.example.dlnagallery

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

/** 第一步：搜索并选择 DLNA 服务器 */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ServerAdapter
    private val listener: (List<DlnaServer>) -> Unit = { list ->
        runOnUiThread {
            adapter.submit(list)
            findViewById<TextView>(R.id.tvEmpty).visibility =
                if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.app_name)
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_refresh) {
                UpnpApp.instance.search()
                true
            } else false
        }

        adapter = ServerAdapter { server ->
            val intent = Intent(this, BrowserActivity::class.java).apply {
                putExtra(BrowserActivity.EXTRA_UDN, server.id)
                putExtra(BrowserActivity.EXTRA_NAME, server.name)
            }
            startActivity(intent)
        }
        val rv = findViewById<RecyclerView>(R.id.rvServers)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        UpnpApp.instance.search()
    }

    override fun onResume() {
        super.onResume()
        UpnpApp.instance.addServerListener(listener)
    }

    override fun onPause() {
        UpnpApp.instance.removeServerListener(listener)
        super.onPause()
    }

    private inner class ServerAdapter(
        private val onClick: (DlnaServer) -> Unit
    ) : RecyclerView.Adapter<ServerAdapter.VH>() {

        private var items: List<DlnaServer> = emptyList()

        fun submit(list: List<DlnaServer>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvName)
            val detail: TextView = view.findViewById(R.id.tvDetail)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_server, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.name.text = s.name
            holder.detail.text = s.details
            holder.itemView.setOnClickListener { onClick(s) }
        }
    }
}
