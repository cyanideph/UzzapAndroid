package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

data class BuddyPresence(val username:String,val isOnline:Boolean,val lastSeen:String,val status:String)

object PresenceClient{
    private const val ONLINE_WINDOW_SECONDS=35L
    private val main=Handler(Looper.getMainLooper());private val http=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).build();private val url=BuildConfig.SUPABASE_URL.trimEnd('/');private val key=BuildConfig.SUPABASE_ANON_KEY;private val json="application/json; charset=utf-8".toMediaType()
    private fun req(path:String)=Request.Builder().url("$url$path").header("apikey",key).header("Authorization","Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type","application/json")
    fun heartbeat(isOnline:Boolean=true){http.newCall(req("/rest/v1/rpc/set_presence").post(JSONObject().put("p_status",if(isOnline)"online" else "offline").toString().toRequestBody(json)).build()).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=Unit;override fun onResponse(c:Call,r:Response){r.close()}})}
    fun fetch(username:String,onResult:(BuddyPresence?)->Unit){http.newCall(req("/rest/v1/rpc/get_user_presence").post(JSONObject().put("p_username",username).toString().toRequestBody(json)).build()).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=main.post{onResult(null)};override fun onResponse(c:Call,r:Response){r.use{if(!it.isSuccessful){main.post{onResult(null)};return};runCatching{val a=JSONArray(it.body?.string().orEmpty());val o=a.optJSONObject(0)?:return@runCatching null;val last=o.optString("last_seen");val recent=runCatching{Instant.parse(last).isAfter(Instant.now().minusSeconds(ONLINE_WINDOW_SECONDS))}.getOrDefault(false);BuddyPresence(o.optString("username"),o.optBoolean("is_online")&&recent,last,o.optString("status","offline"))}.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(null)}}}}})}
    fun fetchMultiple(usernames:List<String>,onResult:(Map<String,BuddyPresence>)->Unit){if(usernames.isEmpty()){main.post{onResult(emptyMap())};return};val results=java.util.concurrent.ConcurrentHashMap<String,BuddyPresence>();var remaining=usernames.distinct().size;if(remaining==0){main.post{onResult(emptyMap())};return};for(u in usernames.distinct())fetch(u){p->if(p!=null)results[p.username.lowercase()]=p;remaining--;if(remaining==0)main.post{onResult(results)}}}
}
