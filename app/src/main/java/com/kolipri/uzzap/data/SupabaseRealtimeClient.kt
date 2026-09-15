package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.DirectMessage
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Supabase Realtime client for the current messages table. */
object SupabaseRealtimeClient{
    private const val TAG="SupabaseRealtime";private val main=Handler(Looper.getMainLooper());private val http=OkHttpClient.Builder().readTimeout(0,TimeUnit.MILLISECONDS).connectTimeout(10,TimeUnit.SECONDS).build();private val refs=AtomicLong(1);private var ws:WebSocket?=null;private var connected=false;private var connecting=false;private var shouldConnect=false
    private val roomListeners=CopyOnWriteArrayList<(ChatMessage,String)->Unit>();private val directListeners=CopyOnWriteArrayList<(DirectMessage)->Unit>();private val connectionListeners=CopyOnWriteArrayList<(Boolean)->Unit>()
    private val heartbeat=object:Runnable{override fun run(){if(connected){ws?.send(JSONObject().put("topic","phoenix").put("event","heartbeat").put("payload",JSONObject()).put("ref",refs.incrementAndGet()).toString());main.postDelayed(this,25000)}}}
    private val reconnect=Runnable{if(shouldConnect&&!connected&&!connecting)connect()}
    fun start(){shouldConnect=true;if(!connected&&!connecting)connect()};fun stop(){shouldConnect=false;main.removeCallbacks(heartbeat);main.removeCallbacks(reconnect);ws?.close(1000,"Client stop");ws=null;connected=false;connecting=false;notifyConnection(false)}
    fun addRoomMessageListener(l:(ChatMessage,String)->Unit){roomListeners.add(l)};fun removeRoomMessageListener(l:(ChatMessage,String)->Unit){roomListeners.remove(l)};fun addDirectMessageListener(l:(DirectMessage)->Unit){directListeners.add(l)};fun removeDirectMessageListener(l:(DirectMessage)->Unit){directListeners.remove(l)};fun addConnectionListener(l:(Boolean)->Unit){connectionListeners.add(l);l(connected)};fun removeConnectionListener(l:(Boolean)->Unit){connectionListeners.remove(l)}
    private fun notifyConnection(v:Boolean){main.post{connectionListeners.forEach{it(v)}}}
    @Synchronized private fun connect(){if(connected||connecting)return;val raw=BuildConfig.SUPABASE_URL.trimEnd('/');if(raw.isBlank())return;val key=BuildConfig.SUPABASE_ANON_KEY;val base=if(raw.startsWith("https://"))"wss://"+raw.removePrefix("https://") else "ws://"+raw.removePrefix("http://");val u="$base/realtime/v1/websocket?apikey=$key&vsn=1.0.0";connecting=true;ws=http.newWebSocket(Request.Builder().url(u).build(),object:WebSocketListener(){override fun onOpen(s:WebSocket,r:Response){ws=s;connecting=false;connected=true;notifyConnection(true);main.postDelayed(heartbeat,25000);joinMessages(s)};override fun onMessage(s:WebSocket,text:String){handle(text)};override fun onClosed(s:WebSocket,c:Int,r:String)=disconnect();override fun onFailure(s:WebSocket,t:Throwable,r:Response?)=disconnect();override fun onClosing(s:WebSocket,c:Int,r:String){s.close(1000,null)}})}
    private fun joinMessages(s:WebSocket){val pg=JSONArray().put(JSONObject().put("event","INSERT").put("schema","public").put("table","messages")).put(JSONObject().put("event","UPDATE").put("schema","public").put("table","messages"));val payload=JSONObject().put("config",JSONObject().put("postgres_changes",pg));s.send(JSONObject().put("topic","realtime:public:messages").put("event","phx_join").put("payload",payload).put("ref",refs.incrementAndGet()).toString())}
    private fun handle(text:String){runCatching{val o=JSONObject(text);if(o.optString("event")!="postgres_changes")return;val d=o.optJSONObject("payload")?.optJSONObject("data")?:return;val rec=d.optJSONObject("record")?:return;if(d.optString("table")!="messages")return;val uuid=rec.optString("id");val msg=ChatMessage(rec.optString("sender_id"),if(rec.optString("deleted_at").isNotBlank())"[deleted]" else rec.optString("body"),false,MessageIdRegistry.register(uuid),rec.optString("created_at"));val roomId=rec.optString("room_id");if(roomId.isNotBlank()){val path="/rest/v1/rooms?id=eq.$roomId&select=name&limit=1";http.newCall(Request.Builder().url("${BuildConfig.SUPABASE_URL.trimEnd('/')}$path").header("apikey",BuildConfig.SUPABASE_ANON_KEY).header("Authorization","Bearer ${SupabaseClient.userAccessToken.orEmpty()}").build()).enqueue(object:Callback{override fun onFailure(c:Call,e:java.io.IOException){};override fun onResponse(c:Call,r:Response){r.use{val a=JSONArray(it.body?.string().orEmpty());val name=a.optJSONObject(0)?.optString("name").orEmpty();if(name.isNotBlank())main.post{roomListeners.forEach{it(msg,name)}}}}})}else{main.post{directListeners.forEach{it(DirectMessage(msg.id,msg.sender,AppState.nickname,msg.body,msg.createdAt,false))}}}}.onFailure{e->Log.w(TAG,"Realtime parse error",e)}}
    private fun disconnect(){connected=false;connecting=false;main.removeCallbacks(heartbeat);notifyConnection(false);if(shouldConnect){main.removeCallbacks(reconnect);main.postDelayed(reconnect,5000)}}
}
