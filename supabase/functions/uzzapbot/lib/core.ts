import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
export const memoryEmbeddingModel = new Supabase.ai.Session("gte-small");

export const BOT_VERSION="4.9.7";
export const ENGINE_NAME="CY Aether Core";
export const url=Deno.env.get("SUPABASE_URL")!;
export const key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
if(!url||!key) throw new Error("Supabase server configuration is missing");
export const db=createClient(url,key,{auth:{autoRefreshToken:false,persistSession:false}});
// Live tuning from uzzapbot_game_config (60s cache). Every value falls back
// to the long-standing hardcoded default, so a missing/disabled row can
// never break the game. Previously these knobs existed in the table but the
// code never read them.
export const DEFAULT_TUNING:Record<string,number>={"activity.cooldown_seconds":86400,"battle.target_score":50,"clue.max_count":3,"daily.max_attempts":10,"game.default_timer_seconds":15,"game.speed_timer_seconds":10,"powerup.default.cooldown_seconds":15,"powerup.hint.cooldown_seconds":30,"powerup.skip.cooldown_seconds":20,"powerup.time.add_seconds":10,"room_challenge.target_score":50,"tournament.default_rounds":3,"tournament.target_score":50,"xp.correct":10,"xp.level_divisor":100,"xp.wrong":1};
export let tuningCache:{at:number;vals:Record<string,number>}|null=null;
export async function tuning():Promise<Record<string,number>>{
  const now=Date.now();
  if(tuningCache&&now-tuningCache.at<60000)return tuningCache.vals;
  const vals:Record<string,number>={...DEFAULT_TUNING};
  try{
    const {data}=await db.from("uzzapbot_game_config").select("config_key,config_value").eq("enabled",true);
    for(const row of (data||[])as any[]){
      const n=Number(row?.config_value);
      if(typeof row?.config_key==="string"&&Number.isFinite(n))vals[row.config_key]=n;
    }
  }catch{/* fall back to defaults */}
  tuningCache={at:now,vals};
  return vals;
}
export async function tune(key:string):Promise<number>{
  const vals=await tuning();
  const v=vals[key];
  return Number.isFinite(v)?v:DEFAULT_TUNING[key];
}
export const out=(x:unknown,s=200)=>new Response(JSON.stringify(x),{status:s,headers:{"content-type":"application/json"}});
export const norm=(v:string)=>String(v).normalize("NFKC").toLowerCase().replace(/\[[^\]]+\]/g,"").normalize("NFKD").replace(/[\u0300-\u036f]/g,"").replace(/[^\p{L}\p{N}]/gu,"");
export async function authorized(req:Request){
  const supplied=req.headers.get("x-uzzapbot-internal-token")||"";
  if(!supplied)return false;
  const {data,error}=await db.rpc("uzzapbot_validate_dispatch_token",{p_token:supplied});
  return !error && data===true;
}
export const POOLS:any={random1:["algebra1","algebra2","algebra3","add","minus","multiply","add1","minus1","multiply1","filipino","filipino","filipino","filipino","love","love","love","love","summonnight","summonnight","summonnight","summonnight","summonnight","summonnight","summonnight2","summonnight2","summonnight2","summonnight2"],random2:["algebra1","algebra2","algebra3","add","minus","multiply","add1","minus1","multiply1","filipino","filipino","filipino","love","love","summonnight","summonnight","summonnight","summonnight","summonnight2","summonnight2","summonnight2","trivia","trivia","trivia","trivia","trivia","trivia","trivia","gtaopm","gtaopm","gtaopm","gtaopm","gtaforeign","gtaforeign","gtaforeign","gtaforeign"],random3:["logic","logic","logic","anime","anime","anime"],randomgta:["gtaforeign","gtaopm"],math:["add","minus","multiply","add1","minus1","multiply1"],algebra:["algebra1","algebra2","algebra3"]};
Object.keys(POOLS).filter(k=>k==="random3").forEach(()=>{POOLS.random3=[...POOLS.random2,...POOLS.random3]});
export function pick(arr:any[]){return arr[Math.floor(Math.random()*arr.length)]}
export function stripBotPrefix(value:string){
  let out=String(value??"").trim();
  for(let i=0;i<10;i++){
    const next=out.replace(/^\s*(?:\[[^\]]+\]\s*)?(?:uzzapbot|uzzap\s*bot|bot)\s*(?::\s*)?(?:\r?\n\s*)?/i,"").trim();
    if(next===out)break;
    out=next;
  }
  return out;
}

export async function send(room:string,body:string){
  const cleanBody=stripBotPrefix(body);
  const {error}=await db.rpc("room_bot_message",{p_room:room,p_body:cleanBody,p_is_system:false});if(error)throw error
}
export function stripAiColorTags(value:string){
  return String(value??"")
    .replace(/\[\/c[^\]]*\]/gi,"")
    .replace(/\[c(?:0[1-9]|[12][0-9]|30)\]/gi,"")
    .replace(/\[[^\]]+\]/g,"")
    .trim();
}

export async function sendAIReply(room:string,body:string){
  const cleanReply=stripAiColorTags(stripBotPrefix(body));
  if(!cleanReply)throw new Error("UzzapBot AI reply is empty");
  const formatted="UzzapBot:\n\n[c02]"+cleanReply;
  const {error}=await db.rpc("room_bot_message",{p_room:room,p_body:formatted,p_is_system:false});
  if(error)throw error;
}
export async function receipt(id:number,status:string,error_text?:string){const {error}=await db.rpc("uzzapbot_finish_receipt",{p_message_id:id,p_status:status,p_error:error_text??null});if(error)throw error}
export async function session(room:string){const {data,error}=await db.from("game_sessions").select("*").eq("room_name",room).maybeSingle();if(error)throw error;return data}
export async function save(s:any){const state={...(s.state_json||{}),room:s.room_name,game:s.game,mode:s.mode,current_game:s.current_game,points:s.points,limit:s.limit_count,endless:s.endless,paused:s.paused,number:s.question_number,question:s.question,answer:s.answer,clue_text:s.clue_text,cycle_games_used:(s.state_json||{}).cycle_games_used||[],recent_games:(s.state_json||{}).recent_games||[]};const {error}=await db.from("game_sessions").update({game:s.game,mode:s.mode,current_game:s.current_game,points:s.points,limit_count:s.limit_count,endless:!!s.endless,paused:!!s.paused,question_number:s.question_number,question:s.question,answer:s.answer,clue_text:s.clue_text,used_questions:s.used_questions||[],state_json:state,updated_at:new Date().toISOString()}).eq("room_name",s.room_name);if(error)throw error}
export async function admin(uid:string){if(!uid)return false;const {data}=await db.from("moderator_roles").select("role").eq("user_id",uid).eq("role","admin").maybeSingle();return !!data}
export async function settings(room:string){const {data,error}=await db.from("uzzapbot_room_settings").select("*").eq("room_name",room).maybeSingle();if(error)throw error;return data||{room_name:room,activated:false,locked:false,wcbot:false,welcome_message:"welcome to {room} {nickname}",challenge_room:""}}
export async function sendAnswer(room:string,body:string){
  await send(room,body);
  const cfg=await settings(room);
  const target=String(cfg.challenge_room||"").trim();
  if(target&&target!==room)await send(target,body);
}
export async function addGamePlayer(sessionId:number,userId:string,user:string,nick:string){
  const ins=await db.from("game_players").insert({session_id:sessionId,user_id:userId,username:user,nickname:nick,score:0,correct:0,attempts:0,clues_used:0});
  if(ins.error) throw ins.error;
  const row=await db.from("game_players").select("id").eq("session_id",sessionId).eq("user_id",userId).maybeSingle();
  if(row.error) throw row.error;
  return row.data?.id?Number(row.data.id):null;
}
export async function playerStats(uid:string){ const {data}=await db.from("uzzapbot_player_stats").select("*").eq("user_id",uid).maybeSingle(); return data; }
export async function ensurePlayerStats(uid:string){ if(!uid)return null; const {data}=await db.from("uzzapbot_player_stats").upsert({user_id:uid},{onConflict:"user_id",ignoreDuplicates:true}).select("*").maybeSingle(); return data||await playerStats(uid); }
export async function unlockAchievement(uid:string,id:string){const {data:a}=await db.from("uzzapbot_achievements").select("xp_reward").eq("id",id).maybeSingle();if(!a)return false;const {data:ins}=await db.from("uzzapbot_player_achievements").insert({user_id:uid,achievement_id:id}).select("achievement_id").maybeSingle();if(ins){const {data:t}=await db.from("uzzapbot_titles").select("id").eq("achievement_id",id).maybeSingle();if(t)await db.from("uzzapbot_player_titles").insert({user_id:uid,title_id:t.id},{onConflict:"user_id,title_id",ignoreDuplicates:true});}
if(ins&&Number(a.xp_reward||0)>0){const cur=await ensurePlayerStats(uid);const xp=Number(cur?.xp||0)+Number(a.xp_reward||0);await db.from("uzzapbot_player_stats").update({xp,level:Math.floor(xp/(((await tune("xp.level_divisor"))||100)))+1,updated_at:new Date().toISOString()}).eq("user_id",uid);await db.from("uzzapbot_xp_history").insert({user_id:uid,xp_delta:Number(a.xp_reward||0),reason:"achievement:"+id,metadata:{achievement_id:id}});}return !!ins}
export async function evaluateAchievements(uid:string,stats:any){if(!uid||!stats)return;const checks:any[]=[["first_game",Number(stats.total_games||0)>=1],["first_correct",Number(stats.total_correct||0)>=1],["streak_5",Number(stats.best_streak||0)>=5],["streak_10",Number(stats.best_streak||0)>=10],["points_100",Number(stats.xp||0)>=100],["games_10",Number(stats.total_games||0)>=10],["correct_50",Number(stats.total_correct||0)>=50]];for(const [id,ok] of checks)if(ok)await unlockAchievement(uid,id)}
export async function recordPlayerResult(uid:string,correct:boolean){ if(!uid)return; const cur=await ensurePlayerStats(uid); const streak=correct?Number(cur?.current_streak||0)+1:0; const tuneVals2=await tuning();const delta=correct?(tuneVals2["xp.correct"]??10):(tuneVals2["xp.wrong"]??1); const xp=Number(cur?.xp||0)+delta; const next={user_id:uid,xp,level:Math.floor(xp/(((await tune("xp.level_divisor"))||100)))+1,current_streak:streak,best_streak:Math.max(Number(cur?.best_streak||0),streak),total_games:Number(cur?.total_games||0),total_correct:Number(cur?.total_correct||0)+(correct?1:0),total_attempts:Number(cur?.total_attempts||0)+1,updated_at:new Date().toISOString()}; await db.from("uzzapbot_player_stats").upsert(next,{onConflict:"user_id"}); await db.from("uzzapbot_xp_history").insert({user_id:uid,xp_delta:delta,reason:correct?"correct_answer":"attempt"}); await evaluateAchievements(uid,next); if(streak>=5)await unlockAchievement(uid,"streak_5"); if(streak>=10)await unlockAchievement(uid,"streak_10"); }
export async function recordLeaderboardActivity(uid:string,game:string,points:number,correct:boolean){if(!uid)return;await db.from("uzzapbot_player_activity").insert({user_id:uid,game:game||"unknown",points:Number(points||0),correct});}
export async function activityTick(){
  const {data:rooms,error}=await db.from("uzzapbot_room_activity").select("*").eq("enabled",true);
  if(error)throw error;
  const now=Date.now(); let processed=0,posted=0;
  const inactiveMessage="[c11]💬 This room has been quiet for 24 hours. Wake it up with /TRIVIA ON or /RANDOM QUIZ1.";
  for(const a of rooms||[]){
    const room=String(a.room_name||"").trim(); if(!room)continue;
    const {data:cfg}=await db.from("uzzapbot_room_settings").select("activated").eq("room_name",room).maybeSingle();
    if(!cfg?.activated)continue;
    const {data:last}=await db.from("room_messages").select("created_at").eq("room_name",room).neq("sender","UzzapBot").order("created_at",{ascending:false}).limit(1).maybeSingle();
    const lastHuman=last?.created_at?new Date(last.created_at):null;
    const lastRecorded=a.last_human_activity_at?new Date(a.last_human_activity_at):null;
    const humanAt=lastHuman||lastRecorded;
    const ageSec=humanAt?Math.max(0,(now-humanAt.getTime())/1000):0;
    const threshold=86400;
    const state=ageSec>=threshold?"INACTIVE":"ACTIVE";
    const {count:dayCount}=await db.from("room_messages").select("id",{count:"exact",head:true}).eq("room_name",room).neq("sender","UzzapBot").gte("created_at",new Date(now-86400000).toISOString());
    await db.from("uzzapbot_room_activity").update({last_human_activity_at:humanAt?humanAt.toISOString():null,activity_state:state,human_message_count_hour:0,human_message_count_day:dayCount||0,updated_at:new Date().toISOString()}).eq("room_name",room);
    processed++; if(state!=="INACTIVE")continue;
    const lastBot=a.last_bot_activity_at?new Date(a.last_bot_activity_at).getTime():0;
    const cooldown=((await tune("activity.cooldown_seconds"))||86400)*1000;
    if(lastBot&&now-lastBot<cooldown)continue;
    const {count:recentBot}=await db.from("room_messages").select("id",{count:"exact",head:true}).eq("room_name",room).eq("sender","UzzapBot").gte("created_at",new Date(now-86400000).toISOString());
    if((recentBot||0)>0)continue;
    await send(room,inactiveMessage);
    await db.from("uzzapbot_room_activity").update({last_bot_activity_at:new Date().toISOString(),updated_at:new Date().toISOString()}).eq("room_name",room);
    posted++;
  }
  return {processed,posted};
}
