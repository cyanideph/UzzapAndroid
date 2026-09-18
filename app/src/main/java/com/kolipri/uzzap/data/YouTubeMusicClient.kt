package com.kolipri.uzzap.data

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal YouTube Music client built on the private InnerTube API.
 * Same approach as yt-dlp / Metrolist: WEB_REMIX context for search,
 * ANDROID_VR (Oculus Quest 3) context for player streams, which still
 * returns direct audio URLs without poToken in most cases.
 *
 * NOTE: violates YouTube ToS; will break when Google rotates clients.
 */
object YouTubeMusicClient {
    data class Track(val videoId: String, val title: String, val artist: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private const val FALLBACK_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30" // public WEB_REMIX key
    private const val FALLBACK_VERSION = "1.20260912.01.00" // refreshed from music.youtube.com at runtime
    private const val VR_CLIENT_VERSION = "1.65.10"
    private const val VR_UA = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="

    @Volatile private var config: Pair<String, String>? = null // api key to WEB_REMIX client version

    @Synchronized
    private fun ensureConfig(onReady: (String, String) -> Unit, onError: (String) -> Unit) {
        config?.let { return onReady(it.first, it.second) }
        val req = Request.Builder().url("https://music.youtube.com")
            .header("User-Agent", BROWSER_UA).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onReady(FALLBACK_KEY, FALLBACK_VERSION)
            override fun onResponse(call: Call, response: Response) {
                val (key, version) = response.use {
                    val responseText = it.body?.string() ?: return@use null
                    val k = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(responseText)?.groupValues?.get(1)
                    val v = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(responseText)?.groupValues?.get(1)
                    if (k != null) k to (v ?: FALLBACK_VERSION) else null
                } ?: (FALLBACK_KEY to FALLBACK_VERSION)
                config = key to version
                onReady(key, version)
            }
        })
    }

    private fun post(url: String, ua: String, json: String, cb: (Result<JSONObject>) -> Unit) {
        val body = json.toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url(url).header("User-Agent", ua).post(body).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { cb(Result.failure(e)) }
                override fun onResponse(call: Call, response: Response) {
                    cb(runCatching {
                        response.use {
                            val responseText = it.body?.string()
                                ?: throw IOException("YouTube returned an empty response body")
                            JSONObject(responseText)
                        }
                    })
                }
            })
    }

    fun search(query: String, onResult: (List<Track>) -> Unit, onError: (String) -> Unit) {
        ensureConfig({ key, clientVersion ->
            val body = JSONObject()
                .put("context", JSONObject().put("client", JSONObject()
                    .put("clientName", "WEB_REMIX")
                    .put("clientVersion", clientVersion)
                    .put("hl", "en")))
                .put("query", query)
                .put("params", SONGS_FILTER)
                .toString()
            post("https://music.youtube.com/youtubei/v1/search?key=$key&prettyPrint=false", BROWSER_UA, body) { res ->
                res.onSuccess { root ->
                    runCatching {
                        val contents = root.optJSONObject("contents")
                        if (contents == null) {
                            val errMsg = root.optJSONObject("error")?.optString("message")
                            return@runCatching onError(
                                if (!errMsg.isNullOrBlank()) "YouTube rejected search: $errMsg"
                                else "Unexpected YouTube response (no results payload)"
                            )
                        }
                        val tracks = mutableListOf<Track>()
                        val shelves = contents
                            ?.optJSONObject("tabbedSearchResultsRenderer")
                            ?.optJSONArray("tabs")?.optJSONObject(0)
                            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                        shelves?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val items = arr.optJSONObject(i)
                                    ?.optJSONObject("musicShelfRenderer")?.optJSONArray("contents") ?: continue
                                for (j in 0 until items.length()) {
                                    val item = items.optJSONObject(j)
                                        ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                                    val videoId = item.optJSONObject("playlistItemData")?.optString("videoId") ?: continue
                                    fun col(n: Int): String {
                                        val runs = item.optJSONArray("flexColumns")?.optJSONObject(n)
                                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                            ?.optJSONObject("text")?.optJSONArray("runs")
                                        val sb = StringBuilder()
                                        runs?.let { for (k in 0 until it.length()) sb.append(it.optJSONObject(k)?.optString("text") ?: "") }
                                        return sb.toString()
                                    }
                                    val title = col(0); val artist = col(1)
                                    if (title.isNotBlank()) tracks.add(Track(videoId, title, artist))
                                }
                            }
                        }
                        if (tracks.isEmpty()) onError("No songs found") else onResult(tracks)
                    }.onFailure { onError("Search response changed format") }
                }.onFailure { onError("Search failed: ${it.message ?: "network error"}") }
            }
        }, onError)
    }

    /**
     * Resolve a playable audio URL. Tries InnerTube (ANDROID_VR) first; if YouTube's
     * bot check ("Sign in to confirm you're not a bot") or signature cipher blocks it,
     * falls back to public Piped instances, which generate poTokens server-side.
     * Piped instances are third-party proxies: rate-limited, sometimes down.
     */
    fun getStreamUrl(videoId: String, onUrl: (String) -> Unit, onError: (String) -> Unit) {
        getStreamUrlInnerTube(videoId, onUrl) { innerTubeError ->
            tryPiped(videoId, 0, onUrl) { pipedError ->
                onError("InnerTube blocked ($innerTubeError); Piped fallback failed ($pipedError)")
            }
        }
    }

    private fun getStreamUrlInnerTube(videoId: String, onUrl: (String) -> Unit, onBlocked: (String) -> Unit) {
        ensureConfig({ key, _ ->
            val vrClient = JSONObject()
                .put("clientName", "ANDROID_VR")
                .put("clientVersion", VR_CLIENT_VERSION)
                .put("deviceMake", "Oculus")
                .put("deviceModel", "Quest 3")
                .put("androidSdkVersion", 32)
                .put("osName", "Android")
                .put("osVersion", "12L")
                .put("hl", "en")
            val body = JSONObject()
                .put("context", JSONObject().put("client", vrClient))
                .put("videoId", videoId)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true)
                .toString()
            post("https://www.youtube.com/youtubei/v1/player?key=$key&prettyPrint=false", VR_UA, body) { res ->
                res.onSuccess { root ->
                    runCatching {
                        val status = root.optJSONObject("playabilityStatus")?.optString("status")
                        if (status != "OK") {
                            val reason = root.optJSONObject("playabilityStatus")?.optString("reason") ?: status
                            return@runCatching onBlocked("Playback blocked: $reason")
                        }
                        val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                            ?: return@runCatching onBlocked("No stream data (YouTube may require poToken on this network)")
                        var best: JSONObject? = null
                        formats.let { arr ->
                            for (i in 0 until arr.length()) {
                                val f = arr.optJSONObject(i) ?: continue
                                val mime = f.optString("mimeType")
                                val url = f.optString("url")
                                if (mime.startsWith("audio/") && url.isNotBlank()) {
                                    if (best == null || f.optInt("bitrate") > best!!.optInt("bitrate")) best = f
                                }
                            }
                        }
                        val url = best?.optString("url")
                        if (url.isNullOrBlank()) onBlocked("No direct audio stream (signature-ciphered; poToken not supported yet)")
                        else onUrl(url)
                    }.onFailure { onBlocked("Player response changed format") }
                }.onFailure { onBlocked("Stream lookup failed: ${it.message ?: "network error"}") }
            }
        }, onBlocked)
    }

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.leptons.xyz",
    )

    private fun tryPiped(videoId: String, index: Int, onUrl: (String) -> Unit, onError: (String) -> Unit) {
        if (index >= pipedInstances.size) return onError("all instances failed or blocked")
        val req = Request.Builder()
            .url("${pipedInstances[index]}/streams/$videoId")
            .header("User-Agent", BROWSER_UA)
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) =
                tryPiped(videoId, index + 1, onUrl, onError)
            override fun onResponse(call: Call, response: Response) {
                val root = response.use {
                    it.body?.string()?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
                }
                val streams = root?.optJSONArray("audioStreams")
                var best: JSONObject? = null
                streams?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val f = arr.optJSONObject(i) ?: continue
                        if (best == null || f.optInt("bitrate") > best!!.optInt("bitrate")) best = f
                    }
                }
                val url = best?.optString("url")
                if (!url.isNullOrBlank()) onUrl(url) else tryPiped(videoId, index + 1, onUrl, onError)
            }
        })
    }
}
