package com.kolipri.uzzap.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.YouTubeMusicClient

/** YouTube Music streaming player (InnerTube). Replaces the classic Subscription shortcut. */
class MusicStreamActivity : BaseChromeActivity() {
    override fun contentLayoutRes() = R.layout.content_music_stream

    private var player: MediaPlayer? = null
    private var prepared = false
    private var tracks: List<YouTubeMusicClient.Track> = emptyList()

    private lateinit var etSearch: EditText
    private lateinit var listView: ListView
    private lateinit var tvNowPlaying: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            player?.let { if (prepared) seekBar.progress = it.currentPosition }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YouTubeMusicClient.init(applicationContext)
        setScreenTitle("Music")
        showConnectedBall(false)
        setSoftkeys(getString(R.string.softkey_search), getString(R.string.softkey_back),
            onLeft = { doSearch() },
            onRight = { finish() })

        etSearch = findViewById(R.id.et_search)
        listView = findViewById(R.id.lv_results)
        tvNowPlaying = findViewById(R.id.tv_now_playing)
        seekBar = findViewById(R.id.sb_progress)
        btnPlay = findViewById(R.id.btn_play_pause)

        findViewById<TextView>(R.id.btn_search).setOnClickListener { doSearch() }
        btnPlay.setOnClickListener { togglePlay() }
        findViewById<TextView>(R.id.btn_stop).setOnClickListener { stopPlayback() }

        listView.setOnItemClickListener { _, _, position, _ ->
            tracks.getOrNull(position)?.let { play(it) }
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && prepared) player?.seekTo(progress)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
    }

    private fun doSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) { toast("Type a song or artist name"); return }
        toast("Searching…")
        AppDiagnostics.event("Music.search", AppDiagnostics.Status.RUNNING, "query=${query.take(40)}")
        YouTubeMusicClient.search(query,
            onResult = { results -> runOnUiThread {
                AppDiagnostics.event("Music.search", AppDiagnostics.Status.PASS, "${results.size} results")
                showResults(results)
            } },
            onError = { msg -> runOnUiThread {
                AppDiagnostics.failed("Music.search", msg)
                toast(msg)
            } })
    }

    private fun showResults(results: List<YouTubeMusicClient.Track>) {
        tracks = results
        listView.adapter = ArrayAdapter(this, R.layout.item_menu_row, R.id.tv_label,
            results.map { "${it.title} — ${it.artist}" })
    }

    private fun play(track: YouTubeMusicClient.Track) {
        tvNowPlaying.text = "Loading: ${track.title}"
        AppDiagnostics.event("Music.stream", AppDiagnostics.Status.RUNNING, track.videoId)
        YouTubeMusicClient.getStreamUrl(track.videoId,
            onUrl = { url -> runOnUiThread {
                AppDiagnostics.event("Music.stream", AppDiagnostics.Status.PASS, "stream acquired")
                startStream(track, url)
            } },
            onError = { msg -> runOnUiThread {
                AppDiagnostics.failed("Music.stream", msg)
                tvNowPlaying.text = "Nothing playing"
                toast(msg)
            } })
    }

    private fun startStream(track: YouTubeMusicClient.Track, url: String) {
        releasePlayer()
        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                prepared = true
                seekBar.max = it.duration
                tvNowPlaying.text = "♪ ${track.title} — ${track.artist}"
                btnPlay.text = "⏸"
                it.start()
                handler.post(ticker)
            }
            mp.setOnCompletionListener {
                btnPlay.text = "▶"
                seekBar.progress = 0
                handler.removeCallbacks(ticker)
            }
            mp.setOnErrorListener { _, what, extra ->
                AppDiagnostics.failed("Music.stream", "MediaPlayer error what=$what extra=$extra")
                toast("Stream failed to play")
                releasePlayer()
                true
            }
            mp.prepareAsync()
            player = mp
            // Some devices hang forever in prepare on unsupported formats — surface it
            handler.postDelayed({
                if (player === mp && !prepared) {
                    AppDiagnostics.failed("Music.stream", "MediaPlayer prepare timed out (format/codec?)")
                    tvNowPlaying.text = "Stream stuck loading — unsupported format on this device"
                }
            }, 20_000)
        }.onFailure {
            mp.release()
            toast("Cannot stream on this connection")
        }
    }

    private fun togglePlay() {
        val mp = player ?: return toast("Pick a song first")
        if (!prepared) return toast("Still loading…")
        if (mp.isPlaying) {
            mp.pause(); btnPlay.text = "▶"; handler.removeCallbacks(ticker)
        } else {
            mp.start(); btnPlay.text = "⏸"; handler.post(ticker)
        }
    }

    private fun stopPlayback() {
        val mp = player ?: return
        if (prepared) { mp.pause(); mp.seekTo(0) }
        btnPlay.text = "▶"
        seekBar.progress = 0
        handler.removeCallbacks(ticker)
    }

    private fun releasePlayer() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        prepared = false
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onPause() {
        player?.let { if (it.isPlaying) { it.pause(); btnPlay.text = "▶" } }
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
