package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.ChatRoomCategory
import com.kolipri.uzzap.model.ChatRoomInfo
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

object UnifiedRoomClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun rpc(name: String, body: JSONObject, cb: Callback) { http.newCall(req("/rest/v1/rpc/$name").post(body.toString().toRequestBody(mediaType)).build()).enqueue(cb) }

    fun fetchCategoriesAndRooms(onResult: (List<ChatRoomCategory>?) -> Unit) {
        rpc("get_room_directory", JSONObject(), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { onResult(null) }; return }; runCatching { val a = JSONArray(it.body?.string().orEmpty()); val rooms = linkedMapOf<String, MutableList<ChatRoomInfo>>(); val names = linkedMapOf<String, String>(); for (i in 0 until a.length()) { val o=a.getJSONObject(i); val rid=o.optString("region_id"); names[rid]=o.optString("region_name"); rooms.getOrPut(rid){mutableListOf()}.add(ChatRoomInfo(o.optString("room_name"),0,20)) }; rooms.map { (id, list) -> ChatRoomCategory(names[id].orEmpty(),0,list.sumOf{it.capacity},list) } }.onSuccess { main.post { onResult(it) } }.onFailure { main.post { onResult(null) } } } }
        })
    }

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) {
        rpc("get_live_room_counts", JSONObject(), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { onResult(null) }; return }; runCatching { val a=JSONArray(it.body?.string().orEmpty()); buildMap<String,Int>{ for(i in 0 until a.length()){val o=a.getJSONObject(i);put(o.optString("room_name"),o.optInt("user_count"))} } }.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(null)}} } }
        })
    }

    fun join(roomName: String, onResult: (Boolean, String?) -> Unit) {
        rpc("join_room_by_name", JSONObject().put("p_room_name", roomName), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false, "Network error") } }
            override fun onResponse(call: Call, response: Response) { response.use { val body=it.body?.string().orEmpty(); val error=when{body.contains("ROOM_FULL")->"Room is full (20/20).";body.contains("ROOM_BANNED")->"You are banned from this room.";else->"Could not join room."}; main.post{onResult(it.isSuccessful,if(it.isSuccessful)null else error)} } }
        })
    }
    fun leave(roomName: String, onResult: ((Boolean)->Unit)? = null) { rpc("leave_room_by_name",JSONObject().put("p_room_name",roomName),object:Callback{ override fun onFailure(call:Call,e:IOException){main.post{onResult?.invoke(false)}}; override fun onResponse(call:Call,response:Response){response.use{main.post{onResult?.invoke(it.isSuccessful)}}} }) }
    fun fetchParticipants(roomName:String,onResult:(List<String>?)->Unit){rpc("get_room_members_by_name",JSONObject().put("p_room_name",roomName),object:Callback{override fun onFailure(call:Call,e:IOException){main.post{onResult(null)}};override fun onResponse(call:Call,response:Response){response.use{if(!it.isSuccessful){main.post{onResult(null)};return};runCatching{val a=JSONArray(it.body?.string().orEmpty());List(a.length()){i->a.getJSONObject(i).optString("username")}}.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(null)}}}}})}
    private fun parseMessage(o:JSONObject):ChatMessage{val uuid=o.optString("id");return ChatMessage(o.optString("sender"),o.optString("body"),o.optBoolean("is_system"),MessageIdRegistry.register(uuid),o.optString("created_at"))}
    fun fetchLatestMessages(roomName:String,limit:Int=60,onResult:(List<ChatMessage>?)->Unit){rpc("get_room_messages_by_name",JSONObject().put("p_room_name",roomName).put("p_limit",limit.coerceIn(1,100)),object:Callback{override fun onFailure(call:Call,e:IOException){main.post{onResult(null)}};override fun onResponse(call:Call,response:Response){response.use{if(!it.isSuccessful){main.post{onResult(null)};return};runCatching{val a=JSONArray(it.body?.string().orEmpty());List(a.length()){i->parseMessage(a.getJSONObject(i))}.reversed()}.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(null)}}}}})}
    fun fetchMessages(roomName:String,afterId:Long=0,onResult:(List<ChatMessage>?)->Unit){fetchLatestMessages(roomName,100){list->onResult(list?.filter{it.id>afterId})}}
    fun fetchMessagesBefore(roomName:String,beforeId:Long,limit:Int=50,onResult:(List<ChatMessage>?)->Unit){val uuid=MessageIdRegistry.uuidFor(beforeId)?:run{main.post{onResult(emptyList())};return};val path="/rest/v1/messages?id=eq.$uuid&select=created_at&limit=1";http.newCall(req(path).get().build()).enqueue(object:Callback{override fun onFailure(call:Call,e:IOException){main.post{onResult(null)}};override fun onResponse(call:Call,response:Response){response.use{if(!it.isSuccessful){main.post{onResult(null)};return};val cursor=runCatching{JSONArray(it.body?.string().orEmpty()).optJSONObject(0)?.optString("created_at")}.getOrNull();if(cursor.isNullOrBlank()){main.post{onResult(emptyList())};return};rpc("get_room_messages_by_name",JSONObject().put("p_room_name",roomName).put("p_before",cursor).put("p_limit",limit.coerceIn(1,100)),object:Callback{override fun onFailure(c:Call,e:IOException){main.post{onResult(null)}};override fun onResponse(c:Call,r:Response){r.use{if(!it.isSuccessful){main.post{onResult(null)};return};runCatching{val a=JSONArray(it.body?.string().orEmpty());List(a.length()){i->parseMessage(a.getJSONObject(i))}.reversed()}.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(null)}}}}})}}})}
    fun sendMessage(roomName:String,bodyText:String,onResult:(ChatMessage?)->Unit){if(bodyText.trim().isEmpty()||bodyText.length>2000){main.post{onResult(null)};return};rpc("send_room_message",JSONObject().put("p_room_name",roomName).put("p_body",bodyText.trim()).put("p_client_message_id",UUID.randomUUID().toString()),object:Callback{override fun onFailure(call:Call,e:IOException){main.post{onResult(null)}};override fun onResponse(call:Call,response:Response){response.use{if(!it.isSuccessful){main.post{onResult(null)};return};runCatching{parseMessage(JSONObject(it.body?.string().orEmpty()))}.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(null)}}}}})}
}
