package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.DirectMessage
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Private messaging client using conversations/messages instead of the removed direct_messages table. */
object DirectMessageClient {
    private val main=Handler(Looper.getMainLooper());private val http=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).writeTimeout(15,TimeUnit.SECONDS).build();private val url=BuildConfig.SUPABASE_URL.trimEnd('/');private val key=BuildConfig.SUPABASE_ANON_KEY;private val json="application/json; charset=utf-8".toMediaType()
    private fun req(path:String)=Request.Builder().url("$url$path").header("apikey",key).header("Authorization","Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type","application/json")
    private fun rpc(name:String,body:JSONObject,cb:Callback)=http.newCall(req("/rest/v1/rpc/$name").post(body.toString().toRequestBody(json)).build()).enqueue(cb)
    private fun userId(username:String,cb:(String?)->Unit){rpc("get_user_id_by_username",JSONObject().put("p_username",username),object:Callback{override fun onFailure(c:Call,e:IOException)=main.post{cb(null)};override fun onResponse(c:Call,r:Response){r.use{if(!it.isSuccessful){main.post{cb(null)};return};main.post{cb(it.body?.string()?.trim()?.removeSurrounding("\"")?.ifBlank{null})}}}})}
    private fun parse(o:JSONObject,me:String,buddy:String):DirectMessage{val uuid=o.optString("id");val sender=o.optString("sender").ifBlank{me};val recipient=if(sender.equals(me,true))buddy else me;return DirectMessage(MessageIdRegistry.register(uuid),sender,recipient,o.optString("body"),o.optString("created_at"),o.optString("state")=="read")}
    fun fetchConversation(me:String,buddy:String,afterId:Long=0,onResult:(List<DirectMessage>?)->Unit){userId(buddy){other->if(other==null){main.post{onResult(null)};return@userId};rpc("get_direct_messages",JSONObject().put("p_other_user",other).put("p_limit",100),object:Callback{override fun onFailure(c:Call,e:IOException)=main.post{onResult(null)};override fun onResponse(c:Call,r:Response){r.use{if(!it.isSuccessful){main.post{onResult(null)};return};runCatching{val a=JSONArray(it.body?.string().orEmpty());List(a.length()){i->parse(a.getJSONObject(i),me,buddy)}}.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(null)}}}}}})}}
    fun sendMessage(me:String,buddy:String,body:String,onResult:(DirectMessage?)->Unit){val clean=body.trim();if(clean.isEmpty()||clean.length>2000){main.post{onResult(null)};return};userId(buddy){other->if(other==null){main.post{onResult(null)};return@userId};rpc("send_direct_message",JSONObject().put("p_other_user",other).put("p_body",clean).put("p_client_message_id",UUID.randomUUID().toString()),object:Callback{override fun onFailure(c:Call,e:IOException)=main.post{onResult(null)};override fun onResponse(c:Call,r:Response){r.use{if(!it.isSuccessful){main.post{onResult(null)};return};runCatching{val o=JSONObject(it.body?.string().orEmpty());DirectMessage(MessageIdRegistry.register(o.optString("id")),me,buddy,clean,o.optString("created_at"),false)}.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(null)}}}}}})}}
}
