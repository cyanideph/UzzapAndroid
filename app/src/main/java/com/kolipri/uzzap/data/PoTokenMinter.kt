package com.kolipri.uzzap.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * PoToken minter ported from Metrolist/NewPipe's WebView BotGuard implementation (GPLv3).
 * Loads po_token.html (a JS BotGuard reimplementation) in a hidden WebView, obtains an
 * integrity token from YouTube's jnn service, then mints per-identifier poTokens.
 * One instance is cached app-wide; it recreates itself when the integrity token expires.
 */
class PoTokenMinter private constructor(
    context: Context,
    private val onReady: (PoTokenMinter) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()
    private val webView = WebView(context)
    private var expiresAtMs = 0L
    @Volatile private var ready = false
    @Volatile private var dead = false
    private val pending = mutableListOf<Triple<String, (String) -> Unit, (String) -> Unit>>()
    private val continuations = HashMap<Long, Pair<(String) -> Unit, (String) -> Unit>>()
    private var counter = 0L
    private val initTimeout = Runnable { if (!ready) failAll("poToken init timed out") }

    init {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.blockNetworkLoads = true
        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) = downloadAndRunBotguard()
        }
        Thread {
            try {
                val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                main.post { webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null) }
            } catch (e: Exception) {
                main.post { failAll("po_token.html missing: ${e.message}") }
            }
        }.start()
        main.postDelayed(initTimeout, 20_000)
    }

    // --- JS bridge --------------------------------------------------------

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        botguardRequest("https://www.youtube.com/api/jnn/v1/Create", "[ \"$REQUEST_KEY\" ]") { body ->
            val challengeData = runCatching { parseChallengeData(body) }.getOrNull()
            if (challengeData == null) {
                main.post { if (!ready) failAll("challenge parse failed") }
                return@botguardRequest
            }
            main.post {
                webView.evaluateJavascript(
                    """try {
                        data = $challengeData
                        runBotGuard(data).then(function (result) {
                            this.webPoSignalOutput = result.webPoSignalOutput
                            $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                        }, function (error) { $JS_INTERFACE.onJsError(String(error)) })
                    } catch (error) { $JS_INTERFACE.onJsError(String(error)) }""", null)
            }
        }
    }

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        botguardRequest("https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]") { body ->
            val parsed = runCatching { parseIntegrityTokenData(body) }.getOrNull()
            if (parsed == null) {
                main.post { if (!ready) failAll("integrity token parse failed") }
                return@botguardRequest
            }
            val (integrityU8, ttlSec) = parsed
            expiresAtMs = System.currentTimeMillis() + ttlSec * 1000 - 10 * 60_000
            main.post {
                webView.evaluateJavascript(
                    """try {
                        this.integrityToken = $integrityU8
                        createPoTokenMinter(webPoSignalOutput, integrityToken).then(function () {
                            $JS_INTERFACE.onMinterCreated()
                        }, function (error) { $JS_INTERFACE.onJsError(String(error)) })
                    } catch (error) { $JS_INTERFACE.onJsError(String(error)) }""", null)
            }
        }
    }

    @JavascriptInterface
    fun onMinterCreated() {
        ready = true
        main.removeCallbacks(initTimeout)
        val queued = pending.toList()
        pending.clear()
        onReady(this)
        queued.forEach { (id, ok, err) -> generateInternal(id, ok, err) }
    }

    @JavascriptInterface
    fun onJsError(error: String) {
        if (!ready) failAll("BotGuard init failed: ${error.take(120)}")
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(key: Long, poTokenU8: String) {
        val (ok, _) = continuations.remove(key) ?: return
        runCatching { ok(u8ToBase64(poTokenU8)) }
    }

    @JavascriptInterface
    fun onObtainPoTokenError(key: Long, error: String) {
        val (_, err) = continuations.remove(key) ?: return
        err("mint failed: ${error.take(120)}")
    }

    // --- public API -------------------------------------------------------

    val isExpired get() = System.currentTimeMillis() > expiresAtMs

    fun generate(identifier: String, onToken: (String) -> Unit, onError: (String) -> Unit) {
        main.post {
            when {
                dead -> onError("minter dead")
                ready -> generateInternal(identifier, onToken, onError)
                else -> pending.add(Triple(identifier, onToken, onError))
            }
        }
    }

    private fun generateInternal(identifier: String, onToken: (String) -> Unit, onError: (String) -> Unit) {
        val key = ++counter
        continuations[key] = onToken to onError
        webView.evaluateJavascript(
            """(function() {
                var requestKey = $key
                try {
                    obtainPoToken(${stringToU8(identifier)}).then(function (poTokenU8) {
                        $JS_INTERFACE.onObtainPoTokenResult(requestKey, poTokenU8.join(','))
                    }, function (error) {
                        $JS_INTERFACE.onObtainPoTokenError(requestKey, String(error))
                    })
                } catch (error) { $JS_INTERFACE.onObtainPoTokenError(requestKey, String(error)) }
            })()""".trimIndent(), null)
        main.postDelayed({
            if (continuations.remove(key) != null) onError("mint timed out")
        }, 15_000)
    }

    fun close() {
        dead = true
        main.post { webView.destroy() }
    }

    private fun failAll(msg: String) {
        dead = true
        main.removeCallbacks(initTimeout)
        val queued = pending.toList()
        pending.clear()
        val open = continuations.values.toList()
        continuations.clear()
        onError(msg)
        queued.forEach { it.third(msg) }
        open.forEach { it.second(msg) }
        webView.destroy()
    }

    // --- BotGuard service over OkHttp -------------------------------------

    private fun botguardRequest(url: String, data: String, cb: (String) -> Unit) {
        val req = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json+protobuf")
            .header("x-goog-api-key", GOOGLE_API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .post(data.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { if (!ready) failAll("botguard network: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.use { if (it.code == 200) it.body?.string() else null }
                if (body != null) cb(body)
                else main.post { if (!ready) failAll("botguard HTTP ${response.code}") }
            }
        })
    }

    // --- parsers (ported from Metrolist's JavaScriptUtil) ------------------

    private fun parseChallengeData(raw: String): String {
        val scrambled = JSONArray(raw)
        val challenge: JSONArray = if (scrambled.length() > 1 && scrambled.opt(1) is String)
            JSONArray(descramble(scrambled.getString(1))) else scrambled.getJSONArray(0)
        fun wrapped(i: Int): Any? {
            if (challenge.isNull(i)) return null
            val arr = challenge.getJSONArray(i)
            for (k in 0 until arr.length()) {
                val v = arr.opt(k)
                if (v is String) return v
            }
            return null
        }
        return JSONObject().apply {
            put("messageId", challenge.getString(0))
            put("interpreterJavascript", JSONObject().apply {
                put("privateDoNotAccessOrElseSafeScriptWrappedValue", wrapped(1) ?: JSONObject.NULL)
                put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", wrapped(2) ?: JSONObject.NULL)
            })
            put("interpreterHash", challenge.getString(3))
            put("program", challenge.getString(4))
            put("globalName", challenge.getString(5))
            put("clientExperimentsStateBlob", challenge.getString(7))
        }.toString()
    }

    private fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
        val arr = JSONArray(raw)
        return base64ToU8(arr.getString(0)) to arr.getLong(1)
    }

    private fun descramble(scrambled: String): String {
        val bytes = ytBase64Decode(scrambled)
        return ByteArray(bytes.size) { (bytes[it] + 97).toByte() }.decodeToString()
    }

    private fun ytBase64Decode(s: String): ByteArray {
        val mod = s.replace('-', '+').replace('_', '/').replace('.', '=')
        return Base64.decode(mod, Base64.DEFAULT)
    }

    private fun base64ToU8(b64: String) =
        "new Uint8Array([" + ytBase64Decode(b64).joinToString(",") { (it.toInt() and 0xFF).toString() } + "])"

    private fun stringToU8(s: String) =
        "new Uint8Array([" + s.toByteArray().joinToString(",") { (it.toInt() and 0xFF).toString() } + "])"

    private fun u8ToBase64(csv: String): String {
        val bytes = csv.split(",").filter { it.isNotBlank() }
            .map { it.trim().toInt().and(0xFF).toByte() }.toByteArray()
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        private const val JS_INTERFACE = "PoTokenMinter"

        @Volatile private var instance: PoTokenMinter? = null

        /** Lazily create (or recreate if expired/dead) the shared minter, on the main thread. */
        fun acquire(context: Context, onReady: (PoTokenMinter) -> Unit, onError: (String) -> Unit) {
            val existing = instance
            if (existing != null && !existing.dead && !existing.isExpired) return onReady(existing)
            Handler(Looper.getMainLooper()).post {
                synchronized(this) {
                    val cur = instance
                    if (cur != null && !cur.dead && !cur.isExpired) {
                        onReady(cur)
                    } else {
                        cur?.close()
                        instance = PoTokenMinter(context.applicationContext, onReady, onError)
                    }
                }
            }
        }
    }
}
