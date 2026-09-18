package com.kolipri.uzzap.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.YouTubeMusicClient

/** YouTube Music streaming player (InnerTube). Replaces the classic Subscription shortcut. */
class MusicStreamActivity : BaseChromeActivity() {
    override fun contentLayoutRes() = R.layout.content_music_stream

    private var player: ExoPlayer? = null
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
            player?.let { if (prepared && it.duration > 0) seekBar.progress = it.currentPosition.toInt() }
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
                if (fromUser && prepared) player?.seekTo(progress.toLong())
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
        // ExoPlayer fed through OkHttp: identical fetch layer to our preflight probe
        // (the default HttpURLConnection stack gets different GVS treatment), and it
        // software-decodes every codec.
        val dataSourceFactory = OkHttpDataSource.Factory(OkHttpClient())
            .setUserAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().also { player = it }
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        prepared = true
                        if (exo.duration > 0) seekBar.max = exo.duration.toInt()
                        tvNowPlaying.text = "♪ ${track.title} — ${track.artist}"
                        btnPlay.text = "⏸"
                        handler.post(ticker)
                    }
                    Player.STATE_ENDED -> {
                        btnPlay.text = "▶"
                        seekBar.progress = 0
                        handler.removeCallbacks(ticker)
                    }
                    Player.STATE_BUFFERING ->
                        tvNowPlaying.text = "Buffering: ${track.title}"
                    else -> {}
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                btnPlay.text = if (playing) "⏸" else "▶"
                if (playing) handler.post(ticker) else handler.removeCallbacks(ticker)
            }
            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                val httpCode = (cause as? InvalidResponseCodeException)?.responseCode
                AppDiagnostics.failed(
                    "Music.stream",
                    "ExoPlayer ${error.errorCodeName} http=$httpCode cause=${cause?.javaClass?.simpleName}: ${cause?.message?.take(120)}"
                )
                toast("Stream failed to play")
                tvNowPlaying.text = "Nothing playing"
                btnPlay.text = "▶"
            }
        })
        exo.prepare()
        exo.playWhenReady = true
    }


    private fun togglePlay() {
        val exo = player ?: return toast("Pick a song first")
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    private fun stopPlayback() {
        val exo = player ?: return
        exo.pause()
        exo.seekTo(0)
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
        player?.pause()
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
