package com.saikou.sozo_tv.presentation.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.engine.download.TvDownloads
import com.saikou.sozo_tv.utils.applyTvFocusScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Per-household offline library. Completed rows use the disk-only player. */
@UnstableApi
class DownloadsActivity : AppCompatActivity() {
    private val adapter = DownloadAdapter { row -> showActions(row) }
    private val entries = mutableMapOf<String, Download>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(24), dp(40), dp(24))
            setBackgroundColor(Color.rgb(20, 20, 20))
        }
        val title = TextView(this).apply {
            text = getString(R.string.tv_downloads_title)
            textSize = 28f; setTextColor(Color.WHITE)
        }
        val back = TextView(this).apply {
            text = getString(R.string.tv_downloads_back)
            textSize = 18f; setTextColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isFocusable = true; setBackgroundResource(R.drawable.background_button)
            setOnClickListener { finish() }
        }
        val status = TextView(this).apply {
            textSize = 16f; setTextColor(Color.LTGRAY)
            setPadding(0, dp(12), 0, dp(12))
        }
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DownloadsActivity)
            adapter = this@DownloadsActivity.adapter
            clipToPadding = false
            itemAnimator = null
        }
        layout.addView(title); layout.addView(back); layout.addView(status)
        layout.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        back.requestFocus()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    runCatching { TvDownloads.list(this@DownloadsActivity) }.onSuccess { downloads ->
                        entries.clear(); downloads.forEach { entries[it.request.id] = it }
                        status.text = if (downloads.isEmpty()) getString(R.string.tv_downloads_empty)
                            else getString(R.string.tv_downloads_count, downloads.size)
                        adapter.submitList(downloads.map { download ->
                            Row(download.request.id, TvDownloads.title(download), description(download), download.state)
                        })
                    }.onFailure { status.text = it.message ?: getString(R.string.tv_downloads_failed) }
                    delay(1000)
                }
            }
        }
    }

    private fun description(download: Download): String {
        val size = String.format(Locale.ROOT, "%.1f MB", download.bytesDownloaded / 1_048_576.0)
        return when (download.state) {
            Download.STATE_COMPLETED -> getString(R.string.tv_downloads_ready, size)
            Download.STATE_FAILED -> getString(R.string.tv_downloads_failed)
            Download.STATE_STOPPED -> getString(R.string.tv_downloads_paused, size)
            Download.STATE_REMOVING -> getString(R.string.tv_downloads_removing)
            Download.STATE_DOWNLOADING -> {
                val percent = download.percentDownloaded
                if (percent >= 0) "${percent.toInt()}% · $size" else size
            }
            else -> getString(R.string.tv_downloads_queued, size)
        }
    }

    private fun showActions(row: Row) {
        val download = entries[row.id] ?: return
        val actions = buildList<Pair<String, () -> Unit>> {
            if (download.state == Download.STATE_COMPLETED) {
                add(getString(R.string.tv_downloads_play) to {
                    startActivity(Intent(this@DownloadsActivity, OfflinePlayerActivity::class.java).putExtra("download_id", row.id))
                })
            } else if (download.state != Download.STATE_REMOVING) {
                val resume = download.state == Download.STATE_STOPPED || download.state == Download.STATE_FAILED
                add(getString(if (resume) R.string.tv_downloads_resume else R.string.tv_downloads_pause) to {
                    runAction {
                        if (download.state == Download.STATE_FAILED) TvDownloads.retry(this@DownloadsActivity, row.id)
                        else TvDownloads.pause(this@DownloadsActivity, row.id, !resume)
                    }
                })
            }
            add(getString(R.string.tv_downloads_delete) to {
                AlertDialog.Builder(this@DownloadsActivity).setTitle(R.string.tv_downloads_delete)
                    .setMessage(row.title).setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.tv_downloads_delete) { _, _ -> runAction { TvDownloads.remove(this@DownloadsActivity, row.id) } }.show()
            })
        }
        AlertDialog.Builder(this).setTitle(row.title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index ->
                runCatching { actions[index].second() }.onFailure {
                    Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun runAction(action: suspend () -> Unit) {
        lifecycleScope.launch {
            runCatching { action() }.onFailure { Toast.makeText(this@DownloadsActivity, it.message, Toast.LENGTH_LONG).show() }
        }
    }

    private data class Row(val id: String, val title: String, val status: String, val state: Int)
    private class DownloadAdapter(private val click: (Row) -> Unit) : ListAdapter<Row, DownloadAdapter.Holder>(object : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
    }) {
        class Holder(val label: TextView) : RecyclerView.ViewHolder(label)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = 12 }
            isFocusable = true; isClickable = true
            textSize = 18f; setTextColor(Color.WHITE)
            setPadding(24, 20, 24, 20); minHeight = 96; gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.background_button)
            applyTvFocusScale(scale = 1.02f)
        })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = getItem(position)
            holder.label.text = "${row.title}\n${row.status}"
            holder.label.setOnClickListener { click(row) }
        }
    }
    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
}
