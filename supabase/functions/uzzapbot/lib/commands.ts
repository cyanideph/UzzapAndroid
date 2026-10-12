import { db, send, sendAnswer, session, save, admin, settings } from "./core.ts";
import { newRound, start, handleAnswer, clue, addGamePlayer } from "./questions.ts";
import { handleHelp, handleAicheck } from "./help.ts";

export const botCommands=new Set(["aicheck","activate","challenge","help","about","version","start","stop","pause","resume","lock","unlock","join","leave","players","leaderboard","score","clue","repost","next","reveal","stat","stats","status","wcbot","wmsg","powerup","use","ewordhunt","twordhunt","trivia","anime","logic","gtaopm","gtaforeign","gta","games","game","random","random1","random2","random3","randomgta","math","algebra","profile","rank","xp","streak","achievements","title","history","hint","fifty","skip","double","time","shield","timed","speed","survival","sudden","daily","battle","tournament","room","ph","tt"]);
export function parseCommand(text:string){const a=text.startsWith("/")?text.slice(1).trim().split(/\s+/):[];const rawCommand=(a[0]||"").toLowerCase(),c=rawCommand==="50"?"fifty":rawCommand==="ai"&&String(a[1]||"").toLowerCase()==="check"?"aicheck":rawCommand,args=rawCommand==="ai"&&String(a[1]||"").toLowerCase()==="check"?a.slice(2):a.slice(1);return {c,args};}

export async function routeCommand(room:string,text:string,uid:string,sender:string,c:string,args:string[]){
if(!botCommands.has(c)){
  return await send(room,"[c08]Unknown command. Use /HELP for available commands.[c01]");
}const cfg=await settings(room);const isAdmin=await admin(uid);if(c!=="activate"&&!cfg.activated){await send(room,"[c11]UzzapBot is inactive in this room. An administrator must use /ACTIVATE.");return}
if(c==="challenge"){
  if(!isAdmin)return await send(room,"[c08]Admin-only command.");
  const target=args.join(" ").trim(); const challengeRoom=target.toLowerCase()==="off"?"":target;
  await db.from("uzzapbot_room_settings").upsert({room_name:room,activated:true,challenge_room:challengeRoom,updated_at:new Date().toISOString()});
  return await send(room,challengeRoom?"[c03]Challenge room set to "+challengeRoom+".":"[c03]Challenge room disabled.");
}
  if(c==="aicheck")return await handleAicheck(room);
  if(["help","about","version"].includes(c))return await handleHelp(room,c,args);
if(c==="activate"){if(!isAdmin)return await send(room,"[c08]Admin-only command.");await db.from("uzzapbot_room_settings").upsert({room_name:room,activated:true,locked:false});return await send(room,"[c03]UzzapBot ACTIVATED in this room.")}
if(c==="timed"&&args.length<=1&&(!args[0]||args[0].toLowerCase()==="on"))return await start(room,"trivia","timed",15);
if(c==="speed"&&args.length<=1&&(!args[0]||args[0].toLowerCase()==="on"))return await start(room,"trivia","speed",10);
if(c==="daily"&&args.length<=1&&(!args[0]||["on","status"].includes(args[0].toLowerCase()))){const today=new Date().toISOString().slice(0,10);const {data:existing}=await db.from("uzzapbot_daily_challenges").select("*").eq("challenge_date",today).eq("user_id",uid).maybeSingle();if(args[0]?.toLowerCase()==="status")return await send(room,existing?`[c11]📅 DAILY CHALLENGE[c01]\n[c03]Date:[c01] ${today}\n[c03]Score:[c07] ${existing.score||0}[c01]\n[c03]Correct:[c01] ${existing.correct||0}/${existing.attempts||0}\n[c03]Status:[c01] ${existing.completed_at?"COMPLETED":"IN PROGRESS"}`:"[c12]No Daily Challenge started today. Use /DAILY ON.[c01]");if(existing?.completed_at)return await send(room,"[c12]📅 You already completed today's Daily Challenge.[c01]");if(existing)return await send(room,"[c12]📅 Your Daily Challenge is already in progress.[c01]");if(await session(room))return await send(room,"[c12]A game is already active in this room. Finish it before starting the Daily Challenge.[c01]");const r=await start(room,"trivia","daily",0);const ss=await session(room);if(ss){const {data:dc,error:de}=await db.from("uzzapbot_daily_challenges").insert({challenge_date:today,user_id:uid,room_name:room,session_id:ss.id,score:0,correct:0,attempts:0}).select("id").single();if(de)throw de;ss.daily_challenge_date=today;ss.state_json={...(ss.state_json||{}),daily_user_id:uid,daily_challenge_date:today,daily_challenge_id:dc.id,daily_questions:10};await save(ss);}return r;}
if(c==="battle"&&args.length>=1){const sub=args[0].toLowerCase();if(["accept","decline","status"].includes(sub)){const id=Number(args[1]);if(!Number.isSafeInteger(id)||id<=0)return await send(room,"[c08]Usage: /BATTLE ACCEPT <id> | /BATTLE DECLINE <id> | /BATTLE STATUS <id>[c01]");const {data:b}=await db.from("uzzapbot_battles").select("*").eq("id",id).maybeSingle();if(!b)return await send(room,"[c08]Battle not found.[c01]");if(sub==="status")return await send(room,"[c11]⚔️ BATTLE #"+b.id+"[c01]\n[c03]Status:[c01] "+b.status);if(sub==="decline"){if(b.challenged_id!==uid||b.status!=="pending")return await send(room,"[c08]You cannot decline this battle.[c01]");await db.from("uzzapbot_battles").update({status:"declined",completed_at:new Date().toISOString()}).eq("id",id);await db.from("game_sessions").delete().eq("id",b.session_id);return await send(room,"[c12]⚔️ Battle declined.[c01]");}if(b.challenged_id!==uid||b.status!=="pending")return await send(room,"[c08]This battle is not available to accept.[c01]");await db.from("uzzapbot_battles").update({status:"active",accepted_at:new Date().toISOString()}).eq("id",id);const bs=await db.from("game_sessions").select("*").eq("id",b.session_id).maybeSingle();if(!bs.data)return await send(room,"[c08]Battle session missing.[c01]");bs.data.paused=false;bs.data.state_json={...(bs.data.state_json||{}),challenge_status:"active"};await db.from("game_sessions").update({paused:false,state_json:bs.data.state_json,updated_at:new Date().toISOString()}).eq("id",b.session_id);return await send(room,"[c07]⚔️ BATTLE ACCEPTED[c01]\n"+await newRound(bs.data));}const target=args[0].replace(/^@/,"");const {data:p}=await db.from("profiles").select("id,username").ilike("username",target).maybeSingle();if(!p)return await send(room,"[c08]Player not found.[c01]");if(p.id===uid)return await send(room,"[c08]You cannot battle yourself.[c01]");const {data:existing}=await db.from("uzzapbot_battles").select("id").or("and(challenger_id.eq."+uid+",challenged_id.eq."+p.id+"),and(challenger_id.eq."+p.id+",challenged_id.eq."+uid+")").in("status",["pending","accepted","active"]).limit(1).maybeSingle();if(existing)return await send(room,"[c12]⚔️ An active battle challenge already exists between these players.[c01]");const {data:s}=await db.from("game_sessions").insert({room_name:room,game:"trivia",mode:"trivia",mode_variant:"battle",points:10,limit_count:50,endless:false,paused:true,question_number:0,question:"",answer:"",clue_text:"",used_questions:[],state_json:{room,game:"trivia",mode:"trivia",mode_variant:"battle",challenger:uid,opponent:p.id,challenge_status:"pending"}}).select().single();if(s){const {data:b,error:be}=await db.from("uzzapbot_battles").insert({challenger_id:uid,challenged_id:p.id,room_name:room,session_id:s.id,status:"pending"}).select("id").single();if(be||!b){await db.from("game_sessions").delete().eq("id",s.id);return await send(room,"[c08]Could not create battle. Please try again.[c01]");}let gp1:any=null,gp2:any=null;try{gp1=await addGamePlayer(Number(s.id),uid,String(m.sender||"Player"),String(m.sender||"Player"));gp2=await addGamePlayer(Number(s.id),p.id,String(p.username),String(p.username));}catch(e){await db.from("game_players").delete().eq("session_id",s.id);await db.from("uzzapbot_battles").delete().eq("id",b.id);await db.from("game_sessions").delete().eq("id",s.id);return await send(room,"[c08]Could not add battle players. Please try again.[c01]");}if(!gp1||!gp2){await db.from("game_players").delete().eq("session_id",s.id);await db.from("uzzapbot_battles").delete().eq("id",b.id);await db.from("game_sessions").delete().eq("id",s.id);return await send(room,"[c08]Could not add battle players. Please try again.[c01]");}return await send(room,"[c07]⚔️ BATTLE CREATED[c01] @"+target+" challenged. Accept with /BATTLE ACCEPT "+String(b.id)+"[c01]");}}
if(c==="room"&&args[0]?.toLowerCase()==="challenge"){if(args[1]?.toLowerCase()==="end"){const {data:rc}=await db.from("uzzapbot_room_challenges").select("*").eq("room_name",room).eq("status","active").maybeSingle();if(!rc)return await send(room,"[c12]No active Room Challenge.[c01]");await db.from("uzzapbot_room_challenges").update({status:"completed",completed_at:new Date().toISOString()}).eq("id",rc.id);const ss=await session(room);if(ss)await db.from("game_sessions").delete().eq("id",ss.id);return await send(room,"[c03]Room Challenge ended.[c01]");}const current=await session(room);if(current)return await send(room,"[c12]A game is already active in this room.[c01]");const r=await start(room,"trivia","room_challenge",0);const ss=await session(room);if(ss){ss.limit_count=50;await save(ss);await db.from("uzzapbot_room_challenges").insert({room_name:room,creator_id:uid,session_id:ss.id,status:"active"});}return r;}
if(c==="tournament"&&args.length<=1){const sub=(args[0]||"").toLowerCase();if(sub==="join"){const s=await session(room);if(!s||s.mode_variant!=="tournament")return await send(room,"[c08]No active tournament.[c01]");const {data:p}=await db.from("game_players").select("id").eq("session_id",s.id).eq("user_id",uid).maybeSingle();if(p)return await send(room,"[c12]Already joined.[c01]");const {error:pe}=await db.from("game_players").insert({session_id:s.id,user_id:uid,username:String(m.sender||"Player"),nickname:String(m.sender||"Player"),score:0,correct:0,attempts:0,clues_used:0});if(pe)return await send(room,"[c08]Could not join tournament. Please try again.[c01]");return await send(room,"[c03]Joined tournament.[c01]");}if(sub==="status"){const s=await session(room);if(!s||s.mode_variant!=="tournament")return await send(room,"[c08]No active tournament.[c01]");const {data:t}=await db.from("uzzapbot_tournaments").select("*").eq("session_id",s.id).maybeSingle();return await send(room,"[c11]🏆 TOURNAMENT[c01]\n[c03]Status:[c01] "+(t?.status||"active")+"\n[c03]Round:[c01] "+(t?.current_round||1)+"/"+(t?.rounds||3));}if(sub==="start"){const s=await session(room);if(!s||s.mode_variant!=="tournament")return await send(room,"[c08]No active tournament.[c01]");const {data:t}=await db.from("uzzapbot_tournaments").select("*").eq("session_id",s.id).maybeSingle();const {count}=await db.from("game_players").select("id",{count:"exact",head:true}).eq("session_id",s.id);if(!t||t.status!=="active")return await send(room,"[c08]Tournament is not available to start.[c01]");if(Number(count||0)<2)return await send(room,"[c12]At least 2 players are required to start the tournament.[c01]");s.paused=false;s.question="";s.answer="";s.question_number=0;s.state_json={...(s.state_json||{}),tournament_round:1,tournament_rounds:Number(t.rounds||3),round_wins:{},tournament_status:"active"};await save(s);await db.from("uzzapbot_tournaments").update({current_round:1}).eq("id",t.id);return await send(room,"[c11]🏆 TOURNAMENT STARTED[c01]\n"+await newRound(s));}const current=await session(room);if(current)return await send(room,"[c12]A game is already active in this room.[c01]");let ss:any=null;const {data:createdSession,error:se}=await db.from("game_sessions").insert({room_name:room,game:"trivia",mode:"trivia",mode_variant:"tournament",timer_seconds:0,deadline_at:null,points:10,limit_count:50,endless:false,paused:true,question_number:0,question:"",answer:"",clue_text:"",used_questions:[],state_json:{room,game:"trivia",mode:"trivia",mode_variant:"tournament",tournament_round:1,tournament_rounds:3,round_wins:{},tournament_status:"registration"}}).select().single();if(se)throw se;ss=createdSession;if(ss){await save(ss);const {data:t,error:te}=await db.from("uzzapbot_tournaments").insert({room_name:room,creator_id:uid,session_id:ss.id,status:"active",rounds:3,current_round:1}).select("id").single();if(te||!t){await db.from("game_sessions").delete().eq("id",ss.id);return await send(room,"[c08]Could not create tournament. Please try again.[c01]");}let gp:any=null;try{gp=await addGamePlayer(Number(ss.id),uid,String(m.sender||"Player"),String(m.sender||"Player"));}catch(e){gp=null;}if(!gp){await db.from("uzzapbot_tournaments").delete().eq("id",t.id);await db.from("game_sessions").delete().eq("id",ss.id);return await send(room,"[c08]Could not add tournament creator. Please try again.[c01]");}}return await send(room,"[c11]🏆 TOURNAMENT REGISTRATION[c01]\n[c03]Tournament created. Use /TOURNAMENT JOIN to join.\n[c03]At least 2 players are required. Then use /TOURNAMENT START.[c01]");}
if(c==="survival"&&args.length<=1&&(!args[0]||args[0].toLowerCase()==="on")){const r=await start(room,"trivia","survival",0);const ss=await session(room);if(ss){ss.survival_lives=3;await save(ss);}return r;}
if(c==="sudden"&&args.length<=1&&(!args[0]||args[0].toLowerCase()==="death")){const r=await start(room,"trivia","sudden_death",0);const ss=await session(room);if(ss){ss.survival_lives=1;ss.sudden_death=true;await save(ss);}return r;}
if(["add","minus","multiply","add1","minus1","multiply1"].includes(c)&&args.length===1&&args[0].toLowerCase()==="on")return await start(room,c);
const map:any={math:"math",trivia:"trivia",anime:"anime",logic:"logic",algebra:"algebra",ph:"filipino",tt:"twist"};if(map[c]&&args.length===1&&args[0].toLowerCase()==="on")return await start(room,map[c]);
if(c==="ewordhunt"&&args.length===0)return await start(room,"wordhunt");if(c==="twordhunt"&&args.length===0)return await start(room,"summonnight2");const multi:any={random:{"quiz1":"random1","quiz2":"random2","quiz3":"random3","gta":"randomgta"},gta:{opm:"gtaopm",foreign:"gtaforeign"},english:{wordhunt:"wordhunt"},tagalog:{wordhunt:"summonnight2"}};if(args.length===1&&multi[c]?.[args[0].toLowerCase()])return await start(room,multi[c][args[0].toLowerCase()]);
if(["profile","rank","xp","streak"].includes(c)){const ps=await ensurePlayerStats(uid);const xp=Number(ps?.xp||0),level=Number(ps?.level||1),next=level*100;if(c==="rank")return await send(room,"[c11]🏅 PLAYER RANK[c01]\n[c03]Level:[c01] "+level+"\n[c07]XP:[c01] "+xp+"/"+next);if(c==="xp")return await send(room,"[c11]⭐ PLAYER XP[c01]\n[c07]XP:[c01] "+xp+"\n[c03]Next level:[c01] "+(next-xp)+" XP");if(c==="streak")return await send(room,"[c11]🔥 PLAYER STREAK[c01]\n[c07]Current:[c01] "+(ps?.current_streak||0)+"\n[c03]Best:[c01] "+(ps?.best_streak||0));return await send(room,"[c11]👤 PLAYER PROFILE[c01]\n[c03]Level:[c01] "+level+"\n[c07]XP:[c01] "+xp+"\n[c03]Current streak:[c01] "+(ps?.current_streak||0)+"\n[c03]Best streak:[c01] "+(ps?.best_streak||0)+"\n[c03]Correct:[c01] "+(ps?.total_correct||0)+"\n[c03]Attempts:[c01] "+(ps?.total_attempts||0));}
if(["profile","rank","xp","streak","achievements","title","history"].includes(c)){
 const ps=await ensurePlayerStats(uid); const xp=Number(ps?.xp||0), level=Number(ps?.level||1), next=level*100;
 if(c==="rank")return await send(room,"[c11]🏅 PLAYER RANK[c01]\n[c03]Level:[c01] "+level+"\n[c07]XP:[c01] "+xp+"/"+next);
 if(c==="xp")return await send(room,"[c11]⭐ PLAYER XP[c01]\n[c07]XP:[c01] "+xp+"\n[c03]Next level:[c01] "+Math.max(0,next-xp)+" XP");
 if(c==="streak")return await send(room,"[c11]🔥 PLAYER STREAK[c01]\n[c07]Current:[c01] "+(ps?.current_streak||0)+"\n[c03]Best:[c01] "+(ps?.best_streak||0));
 if(c==="achievements"){const {data}=await db.from("uzzapbot_player_achievements").select("achievement_id,unlocked_at").eq("user_id",uid).order("unlocked_at",{ascending:false});if(!data?.length)return await send(room,"[c11]🏆 ACHIEVEMENTS[c01]\n[c12]No achievements unlocked yet.[c01]");const ids=data.map((x:any)=>x.achievement_id);const {data:a}=await db.from("uzzapbot_achievements").select("id,name,icon").in("id",ids);const names:any={};for(const x of a||[])names[x.id]=(x.icon||"🏆")+" "+x.name;return await send(room,"[c11]🏆 ACHIEVEMENTS[c01]\n"+data.map((x:any)=>"[c07]✓[c01] "+(names[x.achievement_id]||x.achievement_id)).join("\n"));}
 if(c==="title"){if(args[0]?.toLowerCase()==="list"){const {data}=await db.from("uzzapbot_player_titles").select("title_id").eq("user_id",uid);if(!data?.length)return await send(room,"[c11]🏅 TITLES[c01]\n[c12]No titles unlocked yet.[c01]");const ids=data.map((x:any)=>x.title_id);const {data:t}=await db.from("uzzapbot_titles").select("id,name,description").in("id",ids);return await send(room,"[c11]🏅 TITLES[c01]\n"+(t||[]).map((x:any)=>"[c07]•[c01] "+x.name+" — "+x.description).join("\n"));}if(args[0]){const {data:t}=await db.from("uzzapbot_titles").select("id,name").eq("id",args[0].toLowerCase()).maybeSingle();if(!t)return await send(room,"[c08]Unknown title.[c01]");const {data:owned}=await db.from("uzzapbot_player_titles").select("title_id").eq("user_id",uid).eq("title_id",t.id).maybeSingle();if(!owned)return await send(room,"[c12]You have not unlocked that title yet.[c01]");await db.from("uzzapbot_player_stats").update({title_id:t.id,updated_at:new Date().toISOString()}).eq("user_id",uid);return await send(room,"[c07]🏅 Title equipped: "+t.name+"[c01]");} const currentTitle=ps?.title_id?((await db.from("uzzapbot_titles").select("name").eq("id",ps.title_id).maybeSingle()).data?.name||ps.title_id):"None"; return await send(room,"[c11]🏅 PLAYER TITLE[c01]\n[c03]Current:[c01] "+currentTitle+"[c01]\n[c03]Use:[c01] /TITLE LIST or /TITLE <id>");}
 if(c==="history"){const {data}=await db.from("uzzapbot_xp_history").select("xp_delta,reason,created_at").eq("user_id",uid).order("created_at",{ascending:false}).limit(10);if(!data?.length)return await send(room,"[c11]📜 PLAYER HISTORY[c01]\n[c12]No XP history yet.[c01]");return await send(room,"[c11]📜 PLAYER HISTORY[c01]\n"+data.map((x:any)=>"[c03]"+(x.xp_delta>0?"+":"")+x.xp_delta+" XP[c01] — "+x.reason).join("\n"));}
 return await send(room,"[c11]👤 PLAYER PROFILE[c01]\n[c03]Level:[c01] "+level+"\n[c07]XP:[c01] "+xp+"\n[c03]Current streak:[c01] "+(ps?.current_streak||0)+"\n[c03]Best streak:[c01] "+(ps?.best_streak||0)+"\n[c03]Correct:[c01] "+(ps?.total_correct||0)+"\n[c03]Attempts:[c01] "+(ps?.total_attempts||0));
}
if(c==="leaderboard"){
 const rawArgs=args.join(" ").toLowerCase().trim();
 const sub=(args[0]||"").toLowerCase();
 const current=await session(room);
 const games=["math","trivia","anime","logic","algebra","filipino","twist","gtaopm","gtaforeign","wordhunt","summonnight2"];
 const aliases:any={"ph":"filipino","tt":"twist","gta opm":"gtaopm","gta foreign":"gtaforeign","ewordhunt":"wordhunt","twordhunt":"summonnight2"};
 const normalized=aliases[rawArgs]||sub||"current";
 if(normalized==="current"){
   if(!current)return await send(room,"[c12]No active game in this room.[c01]");
   const {data:ps,error}=await db.from("game_players").select("username,nickname,score,correct,attempts,user_id").eq("session_id",current.id).order("score",{ascending:false}).order("correct",{ascending:false}).order("attempts",{ascending:true});
   if(error)throw error;
   const rows=ps||[];
   if(!rows.length)return await send(room,"[c12]No players have joined yet. Use /JOIN to play.[c01]");
   const title=(current.current_game||current.mode||"GAME").toUpperCase();
   return await send(room,"[c11]🏆 "+title+" LEADERBOARD[c01]\n\n"+rows.slice(0,10).map((p:any,i:number)=>(i+1)+". "+(p.nickname||p.username||"Player")+" — [c07]"+Number(p.score||0)+" pts[c01]").join("\n"));
 }
 const valid=["daily","weekly","monthly","all",...games];
 if(!valid.includes(normalized))
   return await send(room,"[c08]Usage:[c01] /LEADERBOARD [DAILY|WEEKLY|MONTHLY|ALL|MATH|TRIVIA|ANIME|LOGIC|ALGEBRA|PH|TT|GTA OPM|GTA FOREIGN|ENGLISH WORDHUNT|TAGALOG WORDHUNT][c01]");
 const game=games.includes(normalized)?normalized:null;
 let since:string|null=null;
 if(normalized==="daily")since=new Date(Date.now()-86400000).toISOString();
 else if(normalized==="weekly")since=new Date(Date.now()-7*86400000).toISOString();
 else if(normalized==="monthly")since=new Date(Date.now()-30*86400000).toISOString();
 let q=db.from("uzzapbot_player_activity").select("user_id,game,points,correct,created_at");
 if(since)q=q.gte("created_at",since);
 if(game)q=q.eq("game",game);
 const {data,error}=await q.limit(10000);
 if(error)throw error;
 const rows:any={};
 for(const x of data||[]){
   if(!rows[x.user_id])rows[x.user_id]={points:0,correct:0,attempts:0};
   rows[x.user_id].points+=Number(x.points||0);
   rows[x.user_id].correct+=x.correct?1:0;
   rows[x.user_id].attempts++;
 }
 const ids=Object.keys(rows);
 if(!ids.length)return await send(room,"[c12]No leaderboard data for this period.[c01]");
 const {data:profiles,error:pe}=await db.from("profiles").select("id,username,nickname").in("id",ids);
 if(pe)throw pe;
 const names:any={};
 for(const p of profiles||[])names[p.id]=p.nickname||p.username||"Player";
 const sorted=ids.sort((a,b)=>rows[b].points-rows[a].points||rows[b].correct-rows[a].correct||rows[a].attempts-rows[b].attempts).slice(0,10);
 const title=game?game.toUpperCase()+" LEADERBOARD":normalized==="all"?"ALL-TIME LEADERBOARD":normalized.toUpperCase()+" LEADERBOARD";
 return await send(room,"[c11]🏆 "+title+"[c01]\n\n"+sorted.map((id,i)=>(i+1)+". "+(names[id]||"Player")+" — [c07]"+rows[id].points+" pts[c01] • [c03]"+rows[id].correct+" correct[c01]").join("\n"));
}
if(["hint","fifty","skip","double","time","shield"].includes(c)){
 const labels:any={hint:"HINT",fifty:"50/50",skip:"SKIP",double:"DOUBLE",time:"TIME",shield:"SHIELD"};
 const power=c; const label=labels[c];
 const s=await session(room);
 if(!s)return await send(room,"[c08]No active game.[c01]");
 if(s.paused)return await send(room,"[c12]The game is paused.[c01]");
 const {data:p,error:pe}=await db.from("game_players").select("id,score,clues_used").eq("session_id",s.id).eq("user_id",uid).maybeSingle();
 if(pe)throw pe;
 if(!p)return await send(room,"[c12]Use /JOIN before using power-ups.[c01]");
 await db.rpc("uzzapbot_grant_default_powerups",{p_user_id:uid});
 const {data:wallet,error:we}=await db.from("uzzapbot_powerups").select("charges").eq("user_id",uid).eq("powerup",power).maybeSingle();
 if(we)throw we;
 const charges=Number(wallet?.charges||0);
 if(charges<=0)return await send(room,"[c12]You have no "+label+" power-up charges.[c01]");
 const tuneVals=await tuning();const cooldown=power==="hint"?(tuneVals["powerup.hint.cooldown_seconds"]??15):power==="skip"?(tuneVals["powerup.skip.cooldown_seconds"]??15):(tuneVals["powerup.default.cooldown_seconds"]??15);
 const {data:last,error:le}=await db.from("uzzapbot_powerup_uses").select("used_at").eq("user_id",uid).eq("room_name",room).eq("powerup",power).order("used_at",{ascending:false}).limit(1).maybeSingle();
 if(le)throw le;
 if(last){
   const remain=Math.ceil(cooldown-(Date.now()-new Date(last.used_at).getTime())/1000);
   if(remain>0)return await send(room,"[c12]"+label+" cooldown: "+remain+"s remaining.[c01]");
 }
 let reply="";
 const meta:any={question_number:s.question_number};
 if(power==="hint"){
   reply=await clue(room,uid);
   meta.clue=true;
 } else if(power==="fifty"){
   const choices=Array.isArray((s.state_json||{}).choices)?(s.state_json||{}).choices:[];
   if(choices.length<3)return await send(room,"[c12]50/50 is not available for this game round.[c01]");
   const correct=String(s.answer||"");
   const wrong=choices.filter((x:any)=>String(x)!==correct);
   const keep=[correct,...wrong.sort(()=>Math.random()-0.5).slice(0,1)];
   meta.choices=keep;
   reply="[c12]🎯 50/50[c01] Remaining choices: "+keep.join(" | ");
 } else if(power==="skip"){
   reply=await newRound(s);
   meta.skipped=true;
   meta.consumes_round=true;
 } else if(power==="double"){
   const {data:activeDouble}=await db.from("uzzapbot_powerup_uses").select("id").eq("user_id",uid).eq("session_id",s.id).eq("powerup","double").is("expires_at",null).eq("metadata->>consumed","false").limit(1).maybeSingle();
   if(activeDouble)return await send(room,"[c12]DOUBLE is already active for this round.[c01]");
   reply="[c07]⚡ DOUBLE ACTIVE[c01] Your next correct answer will earn [c07]2× points[c01].";
   meta.multiplier=2;
   meta.consumed=false;
 } else if(power==="time"){
   const timeBonus=((await tune("powerup.time.add_seconds"))||10); const base=s.deadline_at?new Date(s.deadline_at).getTime():Date.now(); const nextDeadline=new Date(Math.max(Date.now(),base)+timeBonus*1000); s.deadline_at=nextDeadline.toISOString(); await save(s); reply="[c07]⏱️ TIME BOOST[c01] +"+timeBonus+" seconds added to this round."; meta.time_bonus_seconds=timeBonus; meta.consumed=true;
 } else {
   const {data:activeShield}=await db.from("uzzapbot_powerup_uses").select("id").eq("user_id",uid).eq("session_id",s.id).eq("powerup","shield").is("expires_at",null).eq("metadata->>consumed","false").limit(1).maybeSingle();
   if(activeShield)return await send(room,"[c12]SHIELD is already active for this round.[c01]");
   reply="[c07]🛡️ SHIELD ACTIVE[c01] Your next wrong answer will not break your streak.";
   meta.shield=true;
   meta.consumed=false;
 }
 const {data:updated,error:ue}=await db.from("uzzapbot_powerups").update({charges:charges-1,updated_at:new Date().toISOString()}).eq("user_id",uid).eq("powerup",power).eq("charges",charges).select("charges").maybeSingle();
 if(ue)throw ue;
 if(!updated)return await send(room,"[c12]Power-up changed before it could be used. Try again.[c01]");
 await db.from("uzzapbot_powerup_uses").insert({user_id:uid,room_name:room,session_id:s.id,powerup:power,expires_at:(power==="double"||power==="shield"||power==="time")?null:new Date(Date.now()+cooldown*1000).toISOString(),metadata:meta});
 await unlockAchievement(uid,"powerup_user");
 return await send(room,reply);
}
if(c==="join"){const s=await session(room);if(!s)return await send(room,"[c08]No active game.");const {data:p}=await db.from("game_players").select("id").eq("session_id",s.id).eq("user_id",uid).maybeSingle();if(p)return await send(room,"[c12]You are already in the game.");await db.from("game_players").insert({session_id:s.id,user_id:uid,username:String(m.sender||""),nickname:String(m.sender||""),score:0,correct:0,attempts:0,clues_used:0}); await db.rpc("uzzapbot_record_game_start",{p_user_id:uid});return await send(room,`[c03]🎮 ${String(m.sender||"Player")} joined the game!\n[c01]You can now answer the questions.`)}
if(c==="leave"){const s=await session(room);if(!s)return await send(room,"[c08]No active game.");await db.from("game_players").delete().eq("session_id",s.id).eq("user_id",uid);return await send(room,"[c12]👋 You left the game.")}
if(c==="players"||c==="leaderboard"||c==="score"){const s=await session(room);if(!s)return await send(room,"[c08]No active game.");const {data:ps}=await db.from("game_players").select("username,nickname,score,correct,attempts,clues_used,user_id").eq("session_id",s.id).order("score",{ascending:false});if(c==="score"){const p=(ps||[]).find(x=>x.user_id===uid)||(ps||[]).find(x=>x.username===String(m.sender||""));return await send(room,p?`[c03]YOUR SCORE\n[c01]${p.nickname}\n[c07]Points: ${p.score} | Correct: ${p.correct} | Attempts: ${p.attempts} | Clues: ${p.clues_used}`:"[c12]No score yet.")}return await send(room,`[c03]${c==="players"?"🎮 PLAYERS":"LEADERBOARD"}\n`+(ps||[]).map((p,i)=>`${i+1}. ${p.nickname} — ${p.score} pts`).join("\n")||"[c12]No players have joined yet. Use /JOIN to play.")}
if(c==="clue"){return await send(room,await clue(room,uid))}
if(c==="repost"){const s=await session(room);return await send(room,s?`[c03]CURRENT CHALLENGE[c01]\n[c01]${s.question}\n[c07]⭐ ${s.points} POINTS[c01]`:"[c08]No active game.")}
if(c==="next"){if(!isAdmin)return await send(room,"[c08]Admin-only command.");const s=await session(room);if(!s)return await send(room,"[c08]No active game.");return await send(room,await newRound(s))}
if(c==="reveal"){if(!isAdmin)return await send(room,"[c08]Admin-only command.");const s=await session(room);if(!s)return await send(room,"[c08]No active game.");await send(room,`[c03]The Correct Answer is [c04]   ${s.answer}`);return await send(room,await newRound(s))}
if(c==="stat"||c==="stats"){
  if(!isAdmin)return await send(room,"[c08]Admin-only command.");
  const view=(args[0]||"summary").toLowerCase();
  if(args.length>1||view!=="summary")return await send(room,"[c08]Usage: /STATS");
  const since=new Date(Date.now()-86400000).toISOString();
  const {data:events,error}=await db.from("uzzapbot_event_receipts")
    .select("received_at,processed_at,status,error_text,retry_count,duplicate_count")
    .gte("received_at",since)
    .order("received_at",{ascending:true});
  if(error)throw error;
  const rows=events||[];
  const processed=rows.filter((x:any)=>x.status==="processed"&&x.processed_at);
  const failed=rows.filter((x:any)=>x.status==="failed");
  const now=Date.now();
  const pending=rows.filter((x:any)=>x.status!=="processed"&&x.status!=="failed"&&now-new Date(x.received_at).getTime()>30000);
  const latencies=processed.map((x:any)=>Math.max(0,new Date(x.processed_at).getTime()-new Date(x.received_at).getTime())).filter((x:number)=>Number.isFinite(x)).sort((a:number,b:number)=>a-b);
  const percentile=(p:number)=>latencies.length?latencies[Math.min(latencies.length-1,Math.max(0,Math.ceil(p*latencies.length)-1))]:null;
  const average=latencies.length?latencies.reduce((a:number,b:number)=>a+b,0)/latencies.length:null;
  const fmt=(n:number|null)=>n==null?"N/A":n<1000?Math.round(n)+" ms":(n/1000).toFixed(2)+" s";
  const lastActivity=rows.length?new Date(rows[rows.length-1].received_at):null;
  const lastResponse=processed.length?new Date(processed[processed.length-1].processed_at):null;
  const lastFailure=failed.length?new Date(failed[failed.length-1].received_at):null;
  const last5m=rows.filter((x:any)=>now-new Date(x.received_at).getTime()<=300000).length;
  const lastHour=rows.filter((x:any)=>now-new Date(x.received_at).getTime()<=3600000).length;
  let consecutiveFailures=0;
  for(let i=rows.length-1;i>=0&&rows[i].status==="failed";i--)consecutiveFailures++;
  const total=processed.length+failed.length;
  const successRate=total?processed.length/total*100:100;
  const errorRate=total?failed.length/total*100:0;
  const retries=rows.reduce((n:any,x:any)=>n+(Number(x.retry_count)||0),0);
  const duplicates=rows.reduce((n:any,x:any)=>n+(Number(x.duplicate_count)||0),0);
  let status="OFFLINE";
  if(lastResponse){
    const age=now-lastResponse.getTime();
    status=age<=1800000&&consecutiveFailures<3?"ONLINE":age<=7200000?"DEGRADED":"OFFLINE";
  }
  const timeouts=failed.filter((x:any)=>/timeout|timed out|deadline/i.test(String(x.error_text||""))).length;
  return await send(room,
    "[c11]🤖 UZZAPBOT STATUS[c01]\n\n"+
    "[c03]HEALTH[c01]\n"+
    "Status: [c07]"+status+"[c01]\n"+
    "Bot Version: "+BOT_VERSION+"\n"+
    "Last Activity: "+(lastActivity?lastActivity.toLocaleTimeString("en-PH",{hour12:false}):"N/A")+"\n"+
    "Events (24H): "+rows.length+"\n"+
    "Processed (24H): "+processed.length+"\n"+
    "Failed (24H): "+failed.length+"\n"+
    (pending.length?"Pending (24H): "+pending.length+"\n":"")+
    "Success Rate: "+successRate.toFixed(1)+"%\n\n"+
    "[c03]PERFORMANCE[c01]\n"+
    "Average Response: "+fmt(average)+"\n"+
    "Fastest Response: "+fmt(latencies.length?latencies[0]:null)+"\n"+
    "Slowest Response: "+fmt(latencies.length?latencies[latencies.length-1]:null)+"\n"+
    "P50 Response: "+fmt(percentile(.50))+"\n"+
    "P95 Response: "+fmt(percentile(.95))+"\n"+
    "P99 Response: "+fmt(percentile(.99))+"\n"+
    "Events/Minute: "+(last5m/5).toFixed(1)+"\n"+
    "Events/Hour: "+lastHour+"\n\n"+
    "[c03]RELIABILITY[c01]\n"+
    "Error Rate: "+errorRate.toFixed(1)+"%\n"+
    "Timeouts: "+timeouts+"\n"+
    "Retries: "+retries+"\n"+
    "Duplicates Ignored: "+duplicates+"\n\n"+
    "[c03]ACTIVITY[c01]\n"+
    "Last Response: "+(lastResponse?lastResponse.toLocaleTimeString("en-PH",{hour12:false}):"N/A")+"\n"+
    "Last Error: "+(lastFailure?lastFailure.toLocaleTimeString("en-PH",{hour12:false}):"None")+"\n"+
    "Consecutive Failures: "+consecutiveFailures
  );
}
if(c==="status"){const s=await session(room);return await send(room,s?`[c03]Mode: ${s.mode} | Current: ${s.current_game} | Points: ${s.points} | Score limit: ${s.endless?"ENDLESS":s.limit_count} | Paused: ${s.paused?"yes":"no"} | Players: ${(await db.from("game_players").select("id",{count:"exact",head:true}).eq("session_id",s.id)).count||0}`:"[c08]No active game.")}
if(["stop","pause","resume","lock","unlock"].includes(c)){if(!isAdmin)return await send(room,"[c08]Admin-only command.");const s=await session(room);if(c==="stop"){if(s){await db.from("game_sessions").delete().eq("room_name",room);return await send(room,"[c08]Game stopped.")}return await send(room,"[c08]No active game.")}if(c==="lock"){await db.from("uzzapbot_room_settings").upsert({room_name:room,locked:true});if(s){s.paused=true;await save(s)}return await send(room,"[c12]Systems LOCK!!! Game input is locked in this room.")}if(c==="unlock"){await db.from("uzzapbot_room_settings").upsert({room_name:room,locked:false});if(s){s.paused=false;await save(s)}return await send(room,"[c03]Systems UNLOCK!!! Game input is enabled in this room.")}if(s){s.paused=c==="pause";await save(s)}return await send(room,c==="pause"?"[c12]Game paused.":"[c03]Game resumed.")}
if(c==="wcbot"&&args.length===1){if(!isAdmin)return await send(room,"[c08]Admin-only command.");await db.from("uzzapbot_room_settings").upsert({room_name:room,wcbot:args[0].toLowerCase()==="on"});return await send(room,`[c03]Welcome bot ${args[0].toLowerCase()==="on"?"ON.":"OFF."}`)}
if(c==="wmsg"){if(!isAdmin)return await send(room,"[c08]Admin-only command.");const msg=args.join(" ").trim();await db.from("uzzapbot_room_settings").upsert({room_name:room,welcome_message:msg||"welcome to {room} {nickname}"});return await send(room,"[c03]Welcome message updated.")}
throw new Error("Unknown command. Use /HELP")}
}
