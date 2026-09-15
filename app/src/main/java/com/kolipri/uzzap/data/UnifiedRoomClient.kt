package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private const val TAG = "UnifiedRoomClient"
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String): Request.Builder = Request.Builder()
        .url("$url$path")
        .header("apikey", key)
        .header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}")
        .header("Content-Type", "application/json")

    private fun rpc(name: String, body: JSONObject, cb: Callback) {
        http.newCall(req("/rest/v1/rpc/$name").post(body.toString().toRequestBody(mediaType)).build()).enqueue(cb)
    }

    private fun errorBody(response: Response): String = response.body?.string().orEmpty().take(500)

    /**
     * Loads the Philippine Regions -> Provinces -> Rooms directory.
     * The RPC is preferred, but the app also has a direct-table fallback so a
     * stale PostgREST function cache or RPC deployment cannot blank the room list.
     */
    fun fetchCategoriesAndRooms(onResult: (List<ChatRoomCategory>?) -> Unit) {
        rpc("get_room_directory", JSONObject(), object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "get_room_directory network failure; using table fallback", e)
                fetchDirectoryFromTables(onResult)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful) {
                        val parsed = runCatching { parseDirectory(JSONArray(body)) }.getOrNull()
                        if (!parsed.isNullOrEmpty()) {
                            main.post { onResult(parsed) }
                            return
                        }
                        Log.w(TAG, "get_room_directory returned empty/invalid data; using table fallback")
                    } else {
                        Log.w(TAG, "get_room_directory HTTP ${it.code}: ${body.take(500)}; using table fallback")
                    }
                    fetchDirectoryFromTables(onResult)
                }
            }
        })
    }

    private fun parseDirectory(a: JSONArray): List<ChatRoomCategory> {
        val rooms = linkedMapOf<String, MutableList<ChatRoomInfo>>()
        val names = linkedMapOf<String, String>()
        val order = linkedMapOf<String, Int>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val regionId = o.optString("region_id")
            if (regionId.isBlank()) continue
            names[regionId] = o.optString("region_name")
            order[regionId] = o.optInt("region_sort_order")
            rooms.getOrPut(regionId) { mutableListOf() }.add(
                ChatRoomInfo(o.optString("room_name"), 0, 20)
            )
        }
        return rooms.entries.sortedBy { order[it.key] ?: 0 }.map { (id, list) ->
            ChatRoomCategory(names[id].orEmpty(), 0, list.sumOf { it.capacity }, list)
        }
    }

    private fun fetchDirectoryFromTables(onResult: (List<ChatRoomCategory>?) -> Unit) {
        val regionsPath = "/rest/v1/regions?select=id,code,name,sort_order&order=sort_order.asc,name.asc"
        http.newCall(req(regionsPath).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "regions fallback failed", e)
                main.post { onResult(null) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        Log.e(TAG, "regions fallback HTTP ${it.code}: ${body.take(500)}")
                        main.post { onResult(null) }
                        return
                    }
                    val regions = runCatching { JSONArray(body) }.getOrNull()
                    if (regions == null) { main.post { onResult(null) }; return }
                    fetchProvincesAndRooms(regions, onResult)
                }
            }
        })
    }

    private fun fetchProvincesAndRooms(regions: JSONArray, onResult: (List<ChatRoomCategory>?) -> Unit) {
        val provincesPath = "/rest/v1/provinces?select=id,region_id,code,name,sort_order&order=sort_order.asc,name.asc"
        http.newCall(req(provincesPath).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.e(TAG, "provinces fallback failed", e); main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) { Log.e(TAG, "provinces fallback HTTP ${it.code}: ${body.take(500)}"); main.post { onResult(null) }; return }
                    val provinces = runCatching { JSONArray(body) }.getOrNull()
                    if (provinces == null) { main.post { onResult(null) }; return }
                    fetchRoomsForDirectory(regions, provinces, onResult)
                }
            }
        })
    }

    private fun fetchRoomsForDirectory(regions: JSONArray, provinces: JSONArray, onResult: (List<ChatRoomCategory>?) -> Unit) {
        val roomsPath = "/rest/v1/rooms?select=id,province_id,name,description&order=name.asc"
        http.newCall(req(roomsPath).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.e(TAG, "rooms fallback failed", e); main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) { Log.e(TAG, "rooms fallback HTTP ${it.code}: ${body.take(500)}"); main.post { onResult(null) }; return }
                    val rooms = runCatching { JSONArray(body) }.getOrNull()
                    if (rooms == null) { main.post { onResult(null) }; return }

                    val regionNames = mutableMapOf<String, String>()
                    val regionOrder = mutableMapOf<String, Int>()
                    for (i in 0 until regions.length()) {
                        val r = regions.getJSONObject(i)
                        regionNames[r.optString("id")] = r.optString("name")
                        regionOrder[r.optString("id")] = r.optInt("sort_order")
                    }
                    val provinceRegion = mutableMapOf<String, String>()
                    val provinceNames = mutableMapOf<String, String>()
                    val provinceOrder = mutableMapOf<String, Int>()
                    for (i in 0 until provinces.length()) {
                        val p = provinces.getJSONObject(i)
                        val id = p.optString("id")
                        provinceRegion[id] = p.optString("region_id")
                        provinceNames[id] = p.optString("name")
                        provinceOrder[id] = p.optInt("sort_order")
                    }
                    val grouped = linkedMapOf<String, MutableList<ChatRoomInfo>>()
                    for (i in 0 until rooms.length()) {
                        val room = rooms.getJSONObject(i)
                        val provinceId = room.optString("province_id")
                        if (provinceId.isBlank()) continue
                        val regionId = provinceRegion[provinceId] ?: continue
                        grouped.getOrPut(regionId) { mutableListOf() }.add(ChatRoomInfo(room.optString("name"), 0, 20))
                    }
                    val result = grouped.entries.sortedBy { regionOrder[it.key] ?: 0 }.map { (regionId, list) ->
                        val sortedRooms = list.sortedBy { it.name.lowercase() }
                        ChatRoomCategory(regionNames[regionId].orEmpty(), 0, sortedRooms.sumOf { it.capacity }, sortedRooms)
                    }
                    Log.i(TAG, "Loaded ${result.size} Philippine regions and ${result.sumOf { it.rooms.size }} province rooms from tables")
                    main.post { onResult(result) }
                }
            }
        })
    }

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) {
        rpc("get_live_room_counts", JSONObject(), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(emptyMap()) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { onResult(emptyMap()) }; return }; runCatching { val a=JSONArray(it.body?.string().orEmpty()); buildMap<String,Int>{ for(i in 0 until a.length()){val o=a.getJSONObject(i);put(o.optString("room_name"),o.optInt("user_count"))} } }.onSuccess{main.post{onResult(it)}}.onFailure{main.post{onResult(emptyMap())}} } }
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
