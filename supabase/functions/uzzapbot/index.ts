import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
const memoryEmbeddingModel = new Supabase.ai.Session("gte-small");

const BOT_VERSION="4.9.6";
const ENGINE_NAME="CY Aether Core";
const url=Deno.env.get("SUPABASE_URL")!;
const key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
if(!url||!key) throw new Error("Supabase server configuration is missing");
const db=createClient(url,key,{auth:{autoRefreshToken:false,persistSession:false}});
const out=(x:unknown,s=200)=>new Response(JSON.stringify(x),{status:s,headers:{"content-type":"application/json"}});
const norm=(v:string)=>String(v).normalize("NFKC").toLowerCase().replace(/\[[^\]]+\]/g,"").normalize("NFKD").replace(/[\u0300-\u036f]/g,"").replace(/[^\p{L}\p{N}]/gu,"");
async function authorized(req:Request){
  const supplied=req.headers.get("x-uzzapbot-internal-token")||"";
  if(!supplied)return false;
  const {data,error}=await db.rpc("uzzapbot_validate_dispatch_token",{p_token:supplied});
  return !error && data===true;
}
const POOLS:any={random1:["algebra1","algebra2","algebra3","add","minus","multiply","add1","minus1","multiply1","filipino","filipino","filipino","filipino","love","love","love","love","summonnight","summonnight","summonnight","summonnight","summonnight","summonnight","summonnight2","summonnight2","summonnight2","summonnight2"],random2:["algebra1","algebra2","algebra3","add","minus","multiply","add1","minus1","multiply1","filipino","filipino","filipino","love","love","summonnight","summonnight","summonnight","summonnight","summonnight2","summonnight2","summonnight2","trivia","trivia","trivia","trivia","trivia","trivia","trivia","gtaopm","gtaopm","gtaopm","gtaopm","gtaforeign","gtaforeign","gtaforeign","gtaforeign"],random3:["logic","logic","logic","anime","anime","anime"],randomgta:["gtaforeign","gtaopm"],math:["add","minus","multiply","add1","minus1","multiply1"],algebra:["algebra1","algebra2","algebra3"]};
Object.keys(POOLS).filter(k=>k==="random3").forEach(()=>{POOLS.random3=[...POOLS.random2,...POOLS.random3]});
function pick(arr:any[]){return arr[Math.floor(Math.random()*arr.length)]}
async function approvedQuestion(game:string){
  const categories=game==="gtaforeign"?["gtaforeign"]:game==="gtaopm"?["gtaopm"]:game==="summonnight2"?["tagalog","wordhunt","summonnight2"]:game==="wordhunt"||game==="filipino"||game==="love"||game==="twist"?["english","tagalog","wordhunt",game]:[game];
  const base=db.from("uzzapbot_questions").select("id,question,answer,aliases,points",{count:"exact",head:true}).eq("status","approved").is("duplicate_of",null).in("category",categories);
  const {count,error:countError}=await base;
  if(countError||!count)return null;
  const offset=Math.floor(Math.random()*count);
  const {data,error}=await db.from("uzzapbot_questions").select("id,question,answer,aliases,points").eq("status","approved").is("duplicate_of",null).in("category",categories).range(offset,offset);
  if(error||!data?.length)return null;
  const z=data[0];
  return {q:String(z.question),a:String(z.answer),aliases:Array.isArray(z.aliases)?z.aliases.map(String):[],points:Number(z.points)||10,id:Number(z.id)};
}
async function math(game:string){let a:number,b:number,r:number,op:string;if(game.includes("multiply")){a=game==="multiply"?Math.floor(Math.random()*100)+1:Math.floor(Math.random()*10)+1;b=game==="multiply"?Math.floor(Math.random()*10)+1:Math.floor(Math.random()*100)+1;r=a*b;op="x"}else{a=Math.floor(Math.random()*1001);b=Math.floor(Math.random()*1001);op=game.includes("minus")?"-":"+";r=op==="-"?a-b:a+b}return {q:`MATH: ${a} ${op} ${game.endsWith("1")||game.endsWith("2")||game.endsWith("3")?`(${b})`:b} = ?`,a:String(r)}}
async function datasetWord(kind:string,min=3,max=15){const {data,error}=await db.rpc("uzzapbot_random_word",{p_dataset_id:kind,p_min_len:min,p_max_len:max});if(error||!data?.length)return null;const w=String(data[0].word||"").trim().toUpperCase();return /^[A-Z]+$/.test(w)&&w.length>=min&&w.length<=max?w:null}
function scrambleWord(word:string){const chars=word.split("");if(chars.length<2)return word;for(let attempt=0;attempt<8;attempt++){for(let i=chars.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[chars[i],chars[j]]=[chars[j],chars[i]]}const out=chars.join("");if(out!==word)return out}const rotated=word.slice(1)+word[0];return rotated!==word?rotated:word}
async function question(game:string){
  const managed=await approvedQuestion(game);
  if(managed)return {q:managed.q,a:managed.a,aliases:managed.aliases,points:managed.points,id:managed.id};
  if(["add","minus","multiply","add1","minus1","multiply1"].includes(game))return math(game);
  if(["algebra1","algebra2","algebra3"].includes(game)){const a=Math.floor(Math.random()*11),b=Math.floor(Math.random()*11),x=Math.floor(Math.random()*10)+1,y=Math.floor(Math.random()*10)+1;const op=game==="algebra1"?"+":game==="algebra2"?"-":"x";const r=op==="+"?a*x+b*y:op==="-"?a*x-b*y:(a*x)*(b*y);return {q:"If X="+x+" & Y="+y+", solve "+a+"X "+op+" "+b+"Y = ?",a:String(r)}}
  if(["trivia","anime","logic","gtaforeign","gtaopm"].includes(game))throw new Error("No approved question available for "+game);
  const dataset=game==="summonnight2"?"salita":"words";
  const fallback=dataset==="salita"?["BAHAY","SALAMAT","PAGIBIG","KAIBIGAN","MASAYA"]:["APPLE","ORANGE","PLANET","SUMMER","FRIEND","MUSIC","FLOWER"];
  const w=(await datasetWord(dataset,3,15))||pick(fallback);
  const scrambled=scrambleWord(w);
  const label=game==="filipino"?"PINoy HENYO":game==="love"?"LOVE":game==="twist"?"TT=>":game==="summonnight2"?"TAGALOG WordHunt":"ENG WordHunt";
  return {q:label+": "+scrambled,a:w}
}
async function choose(mode:string,state:any){const fallback:string[]=(POOLS[mode]||[mode]) as string[];const {data,error}=await db.from("uzzapbot_game_pools").select("game_id,weight").eq("pool_id",mode).eq("enabled",true).order("position",{ascending:true});const pool:string[]=!error&&data?.length?data.flatMap((r:any)=>Array(Math.max(1,Number(r.weight)||1)).fill(String(r.game_id))):fallback;const used=new Set<string>(state.cycle_games_used||[]);if(used.size>=new Set<string>(pool).size)used.clear();const candidates=Array.from(new Set<string>(pool)).filter((g:string)=>!used.has(g));const recent=new Set<string>((state.recent_games||[]).slice(-2));const non=candidates.filter((g:string)=>!recent.has(g));const list=non.length?non:candidates;const weights=list.map((g:string)=>pool.filter((x:string)=>x===g).length);let n=Math.random()*weights.reduce((a:number,b:number)=>a+b,0);let chosen=list[0];for(let i=0;i<list.length;i++){n-=weights[i];if(n<=0){chosen=list[i];break}}used.add(chosen);state.cycle_games_used=[...used];state.recent_games=[...(state.recent_games||[]),chosen].slice(-3);return chosen}
const PROVINCE_LANGUAGE:any={
  "southern leyte":{dialect:"Bisaya/Cebuano"},
  "cebu":{dialect:"Bisaya/Cebuano"},
  "bohol":{dialect:"Bisaya/Cebuano"},
  "leyte":{dialect:"Waray"},
  "eastern samar":{dialect:"Waray"},
  "samar":{dialect:"Waray"},
  "northern samar":{dialect:"Waray"},
  "iloilo":{dialect:"Hiligaynon"},
  "negros occidental":{dialect:"Hiligaynon"},
  "capiz":{dialect:"Hiligaynon"},
  "antique":{dialect:"Kinaray-a"},
  "aklan":{dialect:"Akeanon"},
  "pampanga":{dialect:"Kapampangan"},
  "cavite":{dialect:"Tagalog"},
  "laguna":{dialect:"Tagalog"},
  "batangas":{dialect:"Tagalog"},
  "rizal":{dialect:"Tagalog"},
  "quezon":{dialect:"Tagalog"}
};

function roomLanguage(room:string){
  const key=String(room||"").trim().toLowerCase().replace(/\s+/g," ");
  return PROVINCE_LANGUAGE[key]||{dialect:"Tagalog"};
}

function detectUserLanguage(text:string,room:string){
  const t=String(text||"").toLowerCase().normalize("NFKD").replace(/[\u0300-\u036f]/g,"");
  const cfg=roomLanguage(room);
  const patterns:any={
    "Bisaya/Cebuano":[
      /\b(unsa|unsay|ngano|nganong|asa|kinsa|kanus-a|bai|dong|day|naa|nimo|ninyo|imong|inyong|ganahan|dili|gyud|jud|kaayo|mao|ingon|gibuhat|buhaton|karun|karon|unya|ugma|gabii|pila|palihog|sige|tara|kapoy|tibuok|murag|matulog)\b/g,
      /\b(ako|ikaw|nato|amo|imo|gusto|kay|man|ra|lagi|bitaw|buhat|salamat|pwede|kumusta|kamusta)\b/g
    ],
    "Waray":[/\b(maupay|diri|waray|hain|diin|hin|kayano|akon|imo|iya|aton|inyo|mayada|ngan|ginhimo)\b/g],
    "Hiligaynon":[/\b(maayo|indi|gani|gid|basi|san-o|diin|kag|sang|akon|imo|iya|aton|inyo)\b/g],
    "Kapampangan":[/\b(nanu|nokarin|ninu|mayap|aliwa|kareng|king|keng|pota)\b/g],
    "Tagalog":[/\b(ano|bakit|saan|nasaan|sino|kailan|hindi|mayroon|meron|paano|maganda|ngayon|bukas|kahapon|dito|doon|niyo|ninyo)\b/g],
    "English":[/\b(the|is|are|was|were|what|why|where|who|when|how|hello|hi|thanks|thank|please|can|could|would|should|does|did|your|they|this|that|here|there|good|morning|night|today|tomorrow|bored|doing|going|want|need)\b/g]
  };
  const scores:any={};
  for(const [lang,rs] of Object.entries(patterns)) scores[lang]=(rs as RegExp[]).reduce((n,r)=>n+(t.match(r)||[]).length,0);
  const strongBisaya=/\b(unsa|unsay|ngano|nganong|imong|gibuhat|buhaton|karon|karun|gyud|jud|kaayo|ganahan|dili|naa|nimo|ninyo|kapoy|tibuok|murag|matulog|ikaw)\b/.test(t);
  if(strongBisaya && scores["Bisaya/Cebuano"]>=1)return "Bisaya/Cebuano";
  const ranked=Object.entries(scores).sort((a:any,b:any)=>b[1]-a[1]);
  const top=ranked[0], second=ranked[1];
  if(!top || Number(top[1])<1)return cfg.dialect;
  if(second && Number(top[1])===Number(second[1]))return cfg.dialect;
  return String(top[0]);
}

async function callAI(messages:any[],primaryUrl:string,secret:string,backupUrl:string){
  const targets=[
    {name:"cloudflare",url:primaryUrl},
    {name:"puter",url:backupUrl}
  ].filter((x:any)=>!!x.url);

  let lastError="";
  for(const target of targets){
    try{
      const response=await fetch(target.url,{
        method:"POST",
        headers:{
          "Content-Type":"application/json",
          ...(secret && target.name==="cloudflare"?{"Authorization":"Bearer "+secret}:{})
        },
        body:JSON.stringify({messages})
      });
      const body=await response.text();
      if(!response.ok){
        lastError=target.name+" "+response.status+": "+body.slice(0,300);
        continue;
      }
      let data:any;
      try{data=JSON.parse(body)}catch{
        lastError=target.name+" returned invalid JSON";
        continue;
      }
      const reply=String(data?.response||data?.choices?.[0]?.message?.content||"").trim();
      if(!reply){
        lastError=target.name+" returned an empty response";
        continue;
      }
      return reply;
    }catch(e){
      lastError=target.name+": "+(e instanceof Error?e.message:String(e));
    }
  }
  throw new Error("All UzzapBot AI providers failed: "+lastError);
}

function memoryKeyFromText(value:string){
  return String(value||"").toLowerCase().normalize("NFKD").replace(/[\u0300-\u036f]/g,"")
    .replace(/[^\p{L}\p{N}\s]/gu," ").replace(/\s+/g," ").trim().slice(0,120);
}
function parseMemoryRequest(text:string){
  const t=String(text||"").trim();
  for(const re of [/^remember(?:\s+that)?\s+(.+)$/i,/^please\s+remember(?:\s+that)?\s+(.+)$/i,/^dont\s+forget(?:\s+that)?\s+(.+)$/i]){
    const m=t.match(re); if(!m)continue;
    const raw=String(m[1]||"").trim();
    const eq=raw.match(/^(.+?)\s+(?:is|=|means|likes?|loves?|prefers?)\s+(.+)$/i);
    if(eq){const key=memoryKeyFromText(eq[1]),value=String(eq[2]).trim().slice(0,500);if(key&&value)return {key,value,type:"fact"};}
    const key=memoryKeyFromText(raw); if(key)return {key:"note:"+key.slice(0,100),value:raw.slice(0,500),type:"context"};
  }
  const forget=t.match(/^(?:forget|delete|remove)(?:\s+that)?\s+(.+)$/i);
  if(forget)return {forget:true,key:memoryKeyFromText(forget[1])};
  return null;
}
async function cleanupMemory(userId:string,room:string){
  const {error}=await db.rpc("uzzapbot_cleanup_memory_v1",{
    p_user_id:userId,
    p_room_name:room,
    p_max_per_room:50
  });
  if(error)throw error;
}
async function embedMemoryText(text:string){
  const input=String(text||"").trim().slice(0,2000);
  if(!input)return null;
  const output=await memoryEmbeddingModel.run(input,{mean_pool:true,normalize:true});
  const vector=Array.isArray(output)?output:(output&&Array.isArray((output as any).data)?Array.from((output as any).data):null);
  if(!vector||vector.length!==384)throw new Error("UzzapBot memory embedding returned an invalid vector");
  return vector;
}
function memoryIntentKind(query:string){
  const t=String(query||"").toLowerCase();
  if(/\b(call me|what do i want you to call me|what should you call me|nickname|pangalan|itawag|tawag)\b/.test(t))return "nickname";
  if(/\b(language|dialect|pinulongan|yinaknan|wika|bisaya|cebuano|waray|hiligaynon|tagalog)\b/.test(t))return "language";
  if(/\b(music|musika|kanta|song|reggae|roots)\b/.test(t))return "music";
  if(/\b(work|job|profession|occupation|trabaho|cabling|fiber|installation)\b/.test(t))return "work";
  if(/\b(style|reply|replies|response|casual|corporate|tubag)\b/.test(t))return "style";
  return null;
}
function memoryIntentBonus(query:string,row:any){
  const kind=memoryIntentKind(query);
  if(!kind)return 0;
  const text=(String(row.memory_key||"")+" "+String(row.memory_value||"")+" "+String(row.memory_type||"")).toLowerCase();
  const hints:any={
    nickname:["nickname","name","call","tawag","itawag"],
    language:["language","dialect","bisaya","cebuano","waray","hiligaynon","tagalog","pinulongan","wika"],
    music:["music","musika","kanta","song","reggae","roots"],
    work:["work","job","profession","trabaho","cabling","fiber","installation"],
    style:["style","reply","replies","response","casual","corporate","tubag"]
  };
  return hints[kind].some((x:string)=>text.includes(x))?0.18:0;
}
async function backfillMemoryEmbeddings(userId:string,room:string){
  const {data,error}=await db.from("uzzapbot_memory")
    .select("id,memory_key,memory_value")
    .eq("user_id",userId)
    .eq("room_name",room)
    .is("embedding",null)
    .limit(20);
  if(error)throw error;
  for(const row of data||[]){
    const embedding=await embedMemoryText(String(row.memory_key||"")+": "+String(row.memory_value||""));
    if(embedding){
      const {error:updateError}=await db.from("uzzapbot_memory").update({embedding}).eq("id",row.id);
      if(updateError)throw updateError;
    }
  }
}
async function getMemory(userId:string,room:string,query:string=""){
  await cleanupMemory(userId,room);
  try{
    await backfillMemoryEmbeddings(userId,room);
    const embedding=await embedMemoryText(query||"memory");
    if(embedding){
      const {data,error}=await db.rpc("uzzapbot_memory_semantic_search_v1",{
        p_user_id:userId,
        p_room_name:room,
        query_embedding:embedding,
        p_match_threshold:0.55,
        p_match_count:8
      });
      if(!error&&Array.isArray(data)&&data.length){
        return data
          .map((x:any)=>({...x,_memory_rank:(Number(x.similarity)||0)+memoryIntentBonus(query,x)}))
          .sort((a:any,b:any)=>Number(b._memory_rank)-Number(a._memory_rank))
          .map((x:any)=>{const y={...x};delete y._memory_rank;return y;});
      }
    }
  }catch(e){
    console.warn("semantic memory retrieval unavailable; using existing memory retrieval",e);
  }
  const {data,error}=await db.rpc("uzzapbot_get_memory_v3",{
    p_user_id:userId,
    p_room_name:room,
    p_query:String(query||""),
    p_limit:8
  });
  if(!error)return Array.isArray(data)?data:[];
  const fallback=await db.rpc("uzzapbot_get_memory_v2",{
    p_user_id:userId,
    p_room_name:room,
    p_limit:20
  });
  if(fallback.error)throw error;
  return Array.isArray(fallback.data)?fallback.data:[];
}
async function upsertMemory(userId:string,room:string,key:string,value:string,type:string="fact"){
  const {data,error}=await db.rpc("uzzapbot_upsert_memory_v3",{
    p_user_id:userId,
    p_room_name:room,
    p_memory_key:key,
    p_memory_value:value,
    p_memory_type:type,
    p_source:"explicit",
    p_confidence:1.00,
    p_importance:50,
    p_memory_scope:"long_term",
    p_expires_at:null
  });
  if(error)throw error;
  const memoryId=Number((data as any)?.id||0);
  if(memoryId){
    const embedding=await embedMemoryText(String(key||"")+": "+String(value||""));
    if(embedding){
      const {error:embeddingError}=await db.from("uzzapbot_memory").update({embedding}).eq("id",memoryId).eq("user_id",userId).eq("room_name",room);
      if(embeddingError)throw embeddingError;
    }
  }
  await cleanupMemory(userId,room);
  return data;
}
function formatMemoryList(rows:any[]){
  if(!rows.length)return "[c12]Wala pa koy na-save nga memory about you in this room.[c01]";
  return "Ah oo 😄 Mao ni akong naaalala about nimo:\n"+rows.slice(0,20).map((x:any)=>"• "+String(x.memory_value||"")).join("\n");
}

function casualResponseDecision(userText:string, recent:any[], userId:string){
  const t=String(userText||"").trim();
  if(!t)return {respond:false,reason:"empty"};
  if(/^\/{1}/.test(t))return {respond:false,reason:"command"};
  if(/^(?:[.\-_=~*]+|(?:lol|lmao|haha|hehe|hahaha|ok|okay|k|gg|test|asdf|qwerty|1234)+[!?. ]*)$/i.test(t) && t.length<10)return {respond:false,reason:"low_signal"};
  const recentUser=(recent||[]).filter((x:any)=>String(x.sender_id||"")===userId).slice(1,7);
  const normalizePrompt=(value:string)=>String(value||"").trim().replace(/^\/bot\s+/i,"").replace(/\s+/g," ").toLowerCase();
  const explicitBotCommand=t.toLowerCase().startsWith("/bot ");
  if(!explicitBotCommand && recentUser.some((x:any)=>normalizePrompt(String(x.body||""))===normalizePrompt(t)))return {respond:false,reason:"duplicate"};
  const direct=/(?:^|\\s)(?:uzzapbot|uzzap bot|bot)(?:$|[,:!?\\s])/i.test(t);
  const question=/[?¿]$/.test(t)||/^(?:who|what|why|where|when|how|can|could|would|should|do|does|did|is|are|may|pwede|puwede|ano|bakit|saan|sino|kailan|paano|unsa|ngano|asa|kinsa|kanus-a|kumusta|kamusta)\\b/i.test(t);
  return {respond:true,reason:direct?"direct":question?"question":"conversation"};
}
function classifyResponseMode(userText:string, decision:any){
  const t=String(userText||"").trim();
  const lower=t.toLowerCase();
  const absurd=/\b(kung|if)\b[\s\S]{0,180}\b(nganong|why|bakit|unsa|what)\b/i.test(t) &&
    /\b(iro|irng|iring|manok|isda|bulan|buwan|aso|cat|dog|chicken|fish|moon|payong|sweldo|trabaho|eroplano|taxi|passport|school|trabaho)\b/i.test(lower);
  const playfulQuestion=decision?.reason==="question" &&
    /\b(nganong|ngano|unsa|why|bakit|how come|paano|how)\b/i.test(lower) &&
    /\b(dili|wala|naa|makalupad|tulog|sweldo|trabaho|payong|taxi|eroplano|magic|ghost|superman)\b/i.test(lower);
  const teasing=/(?:hoy|oy|haha|hahaha|piste|buang|bogo|sira|maldita|maldito|samok|joke|char)/i.test(lower);
  if(absurd||playfulQuestion)return "PILOSOPO";
  if(teasing)return "BANTER";
  if(decision?.reason==="question")return "FACTUAL";
  if(/\b(sakit|problema|namatyan|namatay|emergency|help|scared|afraid|depressed|suicide|hurt)\b/i.test(lower))return "SERIOUS";
  if(/^(hi|hello|hey|kumusta|kamusta|unsa man|oy|yo)\b/i.test(lower))return "GREETING";
  return "CASUAL";
}

function relationshipLevel(memoryCount:number,recentUserCount:number){
  if(memoryCount>=3||recentUserCount>=12)return "familiar regular";
  if(memoryCount>=1||recentUserCount>=4)return "regular";
  return "new/acquaintance";
}

async function botConversation(room:string,nickname:string,userText:string,userId:string){
  // TEMPORARY TEST SWITCH: original Groq conversation AI is disabled while LAYA is being prepared.
  // Commands, games, moderation, and DB-backed memory remain available.
  const ORIGINAL_AI_ENABLED = true;
  const aiUrl = Deno.env.get("UZZAPBOT_AI_URL") || "https://uzzapbot-ai.mharbalaba.workers.dev/ai";
  const aiSecret = Deno.env.get("UZZAPBOT_AI_SECRET") || "";
  const aiBackupUrl = Deno.env.get("UZZAPBOT_AI_BACKUP_URL") || "https://charming-circle-330040.puter.work/ai";
  const cfg=roomLanguage(room);
  const detected=detectUserLanguage(userText,room);
  const memoryRequest=parseMemoryRequest(userText);
  if(memoryRequest?.forget){
    const rows=await getMemory(userId,room,memoryRequest?.forget?memoryRequest.key:"");
    const target=rows.find((x:any)=>String(x.memory_key||"")===memoryRequest.key||String(x.memory_key||"").includes(memoryRequest.key)||String(x.memory_value||"").toLowerCase().includes(memoryRequest.key));
    if(!target)return {reply:"[c12]Wala koy makita nga matching memory para i-forget.[c01]",detected_language:detected,room_default:cfg.dialect,context_items:0};
    await db.from("uzzapbot_memory").delete().eq("user_id",userId).eq("room_name",room).eq("memory_key",String(target.memory_key));
    return {reply:"Sige 😄 Nakalimtan na nako na.",detected_language:detected,room_default:cfg.dialect,context_items:0};
  }
  if(/^(?:what do you remember about me|what do u remember about me|what do you remember|ano ang naaalala mo tungkol sa akin|unsa imong nahinumduman about nako)\??$/i.test(String(userText).trim())){
    const rows=await getMemory(userId,room);
    return {reply:formatMemoryList(rows),detected_language:detected,room_default:cfg.dialect,context_items:rows.length};
  }
  if(memoryRequest && !memoryRequest.forget){
    const blocked=/(password|passcode|pin|otp|one[- ]time|api[ -]?key|secret|token|private key|credit card|card number|cvv|ssn|social security|bank account|account number)/i.test(memoryRequest.key+" "+memoryRequest.value);
    if(blocked)return {reply:"Dili ko mag-save ug passwords, API keys, tokens, PIN/OTP, o uban pang sensitibong impormasyon. 👍",detected_language:detected,room_default:cfg.dialect,context_items:0};
    await upsertMemory(userId,room,memoryRequest.key,memoryRequest.value,memoryRequest.type);
    return {reply:"Got it 😄 I’ll remember that for this room.",detected_language:detected,room_default:cfg.dialect,context_items:0};
  }
  const identityQuery=/\b(?:what(?:\s+is|\'s)?\s+your\s+(?:app\s+)?version|what\s+version(?:\s+are\s+you|\s+do\s+you\s+have)?|anong\s+version(?:\s+mo)?|unsang\s+version(?:\s+nimo)?|version\s+mo|what\s+model(?:\s+are\s+you\s+using)?|anong\s+model(?:\s+gamit\s+mo)?|unsang\s+model(?:\s+gamit\s+nimo)?|what\s+ai\s+are\s+you)\b/i.test(String(userText).trim());
  if(identityQuery){
    const asksVersion=/\b(version|bersyon)\b/i.test(String(userText));
    return {reply:asksVersion?"UzzapBot v"+BOT_VERSION+" — CY Aether Core. Your AI Tambay sa Uzzap.":"CY Aether Core — the intelligence engine behind UzzapBot v"+BOT_VERSION+".",detected_language:detected,room_default:cfg.dialect,context_items:0,silent:false,relationship:"direct"};
  }
  if (!ORIGINAL_AI_ENABLED) return {reply:"",detected_language:detected,room_default:cfg.dialect,context_items:0,silent:true,reason:"original_groq_ai_temporarily_disabled"};
  const {data:recent,error}=await db.from("room_messages")
    .select("sender,sender_id,body,created_at")
    .eq("room_name",room)
    .order("created_at",{ascending:false})
    .limit(8);
  if(error)throw error;
  const decision=casualResponseDecision(userText,recent||[],userId);
  if(!decision.respond) return {reply:"",detected_language:detected,room_default:cfg.dialect,context_items:0,silent:true,reason:decision.reason};
  const admitted=await db.rpc("uzzapbot_ai_admit",{p_room:room,p_min_interval_seconds:3,p_window_seconds:60,p_window_limit:8});
  if(admitted.error) throw admitted.error;
  if(admitted.data!==true) return {reply:"",detected_language:detected,room_default:cfg.dialect,context_items:0,silent:true,reason:"ai_rate_gate"};
  const recentUserCount=(recent||[]).filter((x:any)=>String(x.sender_id||"")===userId).length;
  const responseMode=classifyResponseMode(userText,decision);
  const memoryLimit=(responseMode==="FACTUAL"||responseMode==="SERIOUS"||responseMode==="PILOSOPO")?3:0;
  const memory=memoryLimit?await getMemory(userId,room,userText):[];
  const relationship=relationshipLevel(memory.length,recentUserCount);
  const manilaTime=new Intl.DateTimeFormat("en-PH",{timeZone:"Asia/Manila",hour:"numeric",minute:"2-digit",hour12:true}).format(new Date());
  // Keep context deliberately small. Use the last two human messages only:
  // user statements are conversational evidence for the current turn, while
  // previous UzzapBot output is generated/unverified text and must never crowd
  // out the user's own context or become a fact on the next turn.
  const contextLimit=2;
  const contextSource=(recent||[])
    .filter((x:any)=>String(x.sender||"").toLowerCase()!=="uzzapbot")
    .reverse()
    .slice(-contextLimit);
  const context=contextSource.map((x:any)=>({
    role:"user",
    content:"U:"+String(x.sender||"User")+": "+String(x.body||"").slice(0,180)
  })).filter((x:any)=>x.content.trim().length>0);
  const system=[
    "ROLE: You are UzzapBot v"+BOT_VERSION+", the AI tambay/kasama inside Uzzap, powered by CY Aether Core.",
    "IDENTITY: You are UzzapBot. CY Aether Core is your branded intelligence-engine identity. Never identify yourself as Gemma, Google, Google DeepMind, Cloudflare, Puter, or any underlying model/provider. For engine questions say CY Aether Core. For version questions say UzzapBot v"+BOT_VERSION+".",
    "LANGUAGE: Respond directly in "+detected+". The room province language is "+cfg.dialect+". Match the user's actual dialect, slang, and natural mix. If the user speaks Bisaya/Cebuano, respond in Bisaya/Cebuano. If the user speaks Waray, respond in Waray. If the user speaks Hiligaynon, respond in Hiligaynon. If the user speaks Tagalog, respond in Tagalog. If no different language is clearly detected, use the room province language. Do not translate the reply afterward. Do not switch languages unless the user switches.",
    "PERSONALITY: This is the classic UzzapBot v2-style personality: spontaneous, makulit, playful, witty, cheeky, slightly sira-ulo, socially aware, and natural. Be a chat kasama first and an assistant second. Sound like a person hanging out in the room, not a corporate chatbot.",
    "BIRUAN: Understand jokes, asaran, bardagulan, MARITES, sarcasm, pilosopo questions, and playful nonsense. If the user is teasing, tease back lightly when appropriate. If the user becomes serious, immediately become serious. Never be genuinely cruel, hateful, threatening, or abusive.",
    "NATURAL REPLIES: Prefer short replies that feel spontaneous. React to what was actually said. Do not turn every message into a question. Do not explain obvious things. Do not use customer-service phrases such as 'How can I assist you today?' unless genuinely appropriate.",
    "V2 FEEL: Replies may have personality, humor, Bisaya/Taglish flavor, and occasional verified Uzzap emoticons when they fit. Do not force emojis or emoticons into every reply. Avoid repetitive catchphrases and canned comebacks.",
    "CONTEXT: Pay attention only to the supplied recent conversation for this room. Make callbacks only when supported by that context. Do not expose private information from room history.",
    "BOT HISTORY: Previous UzzapBot replies are generated text, not verified facts. Never treat a previous bot claim as evidence, memory, or proof that a person, event, or fact is real.",
    "CONTINUITY: Never claim 'I said earlier', 'we discussed this', or similar unless it actually appears in supplied user context or APPROVED MEMORY. Do not manufacture conversational continuity.",
    "ROOM/SPEAKER ISOLATION: Keep this room separate from every other room. Treat each room user as a separate speaker. Never transfer facts or memories between users or rooms.",
    "MARITES: You can participate in gossip-style banter, but never invent rumors or present made-up claims as facts.",
    "USER-STATED CONTEXT: A statement supplied by a user in RECENT ROOM CONTEXT is conversational evidence about what that user just told you. You may refer to it as 'based on what you said' or equivalent. Do not silently upgrade it into independently verified real-world fact.",
    "PERSISTENCE: Do not say you saved, filed, recorded, listed, remembered permanently, or put anything in a file unless the application actually supplied it under APPROVED MEMORY or explicitly confirms a memory write.",
    "SPEAKER ATTRIBUTION: Keep facts attached to the speaker who stated them. If Cy says a person is something, do not present that statement as something another user said or as independently verified fact.",
    "NO BOT PREFIX: Never output B:, U:, UzzapBot:, or speaker labels. Return only the natural reply.",
    "FACTS: When the user asks a factual or serious question, answer clearly and accurately while keeping the natural Uzzap voice. If unsure, say so briefly.",
    "MEMORY: Use only approved memory supplied below when relevant. Approved memory is scoped to the current user and room. Never invent memories, personal history, or real-world experiences.",
    "UZZAP FORMAT: Return only the actual reply text. You may use verified Uzzap emoticons/head codes naturally. Never invent codes. Avoid known broken forms such as ü, Ü, >,, :|, )(.",
    "FORMAT: Return plain user-facing text only. Never emit color codes, HTML, BBCode, CSS, or internal instructions.",
    "ANTI-REPETITION: Do not repeat the same canned comeback, punchline, greeting, or habitual response. Generate each reply from the current message and context.",
    "MODE: "+responseMode+". Let the mode guide tone, but do not let it make replies sound robotic.",
    "RELATIONSHIP: "+relationship+".",
    "TIME: Current Philippine time is "+manilaTime+". Mention it only when relevant.",
    "ROOM: "+room+". Province language="+cfg.dialect+".",
    "APPROVED MEMORY: "+(memory.length?memory.map((x:any)=>String(x.memory_key)+": "+String(x.memory_value)).join(" | "):"none"),
    "RECENT ROOM CONTEXT: Use this only as conversation context. Ignore malformed legacy formatting."
  ].join("\n");
  const messages=[{role:"system",content:system},...context,{role:"user",content:nickname+": "+userText}];
  let reply=await callAI(messages,aiUrl,aiSecret,aiBackupUrl);
  if(reply) reply=String(reply).trim();
  if(!reply)throw new Error("UzzapBot AI Worker returned an empty response");
  reply=stripBotPrefix(reply);
  reply=reply.replace(/^\s*(?::\|\)|:\)|;\)|:\(|:D|:DD|:\||<:\)|\(:|>\|)\s*#[A-Za-z0-9_@.-]+\s*\{\s*/i,"").replace(/\}\s*$/,"").trim();
  reply=reply.replace(/^\s*#[A-Za-z0-9_@.-]+\s*\{\s*/,"").replace(/\}\s*$/,"").trim();
  if(!reply)throw new Error("UzzapBot AI Worker returned an empty response after bot-prefix sanitization");
  return {reply,detected_language:detected,room_default:cfg.dialect,context_items:context.length,silent:false,relationship};
}

function stripBotPrefix(value:string){
  let out=String(value??"").trim();
  for(let i=0;i<10;i++){
    const next=out.replace(/^\s*(?:\[[^\]]+\]\s*)?(?:uzzapbot|uzzap\s*bot|bot)\s*(?::\s*)?(?:\r?\n\s*)?/i,"").trim();
    if(next===out)break;
    out=next;
  }
  return out;
}

async function send(room:string,body:string){
  const cleanBody=stripBotPrefix(body);
  const {error}=await db.rpc("room_bot_message",{p_room:room,p_body:cleanBody,p_is_system:false});if(error)throw error
}
function stripAiColorTags(value:string){
  return String(value??"")
    .replace(/\[\/c[^\]]*\]/gi,"")
    .replace(/\[c(?:0[1-9]|[12][0-9]|30)\]/gi,"")
    .replace(/\[[^\]]+\]/g,"")
    .trim();
}

async function sendAIReply(room:string,body:string){
  const cleanReply=stripAiColorTags(stripBotPrefix(body));
  if(!cleanReply)throw new Error("UzzapBot AI reply is empty");
  const formatted="UzzapBot:\n\n[c02]"+cleanReply;
  const {error}=await db.rpc("room_bot_message",{p_room:room,p_body:formatted,p_is_system:false});
  if(error)throw error;
}
async function receipt(id:number,status:string,error_text?:string){const {error}=await db.rpc("uzzapbot_finish_receipt",{p_message_id:id,p_status:status,p_error:error_text??null});if(error)throw error}
async function session(room:string){const {data,error}=await db.from("game_sessions").select("*").eq("room_name",room).maybeSingle();if(error)throw error;return data}
async function save(s:any){const state={...(s.state_json||{}),room:s.room_name,game:s.game,mode:s.mode,current_game:s.current_game,points:s.points,limit:s.limit_count,endless:s.endless,paused:s.paused,number:s.question_number,question:s.question,answer:s.answer,clue_text:s.clue_text,cycle_games_used:(s.state_json||{}).cycle_games_used||[],recent_games:(s.state_json||{}).recent_games||[]};const {error}=await db.from("game_sessions").update({game:s.game,mode:s.mode,current_game:s.current_game,points:s.points,limit_count:s.limit_count,endless:!!s.endless,paused:!!s.paused,question_number:s.question_number,question:s.question,answer:s.answer,clue_text:s.clue_text,used_questions:s.used_questions||[],state_json:state,updated_at:new Date().toISOString()}).eq("room_name",s.room_name);if(error)throw error}
async function admin(uid:string){if(!uid)return false;const {data}=await db.from("moderator_roles").select("role").eq("user_id",uid).eq("role","admin").maybeSingle();return !!data}
async function settings(room:string){const {data,error}=await db.from("uzzapbot_room_settings").select("*").eq("room_name",room).maybeSingle();if(error)throw error;return data||{room_name:room,activated:false,locked:false,wcbot:false,welcome_message:"welcome to {room} {nickname}",challenge_room:""}}
async function newRound(s:any){const st={...(s.state_json||{})};const game=["random1","random2","random3","randomgta","math","algebra"].includes(s.mode)?await choose(s.mode,st):s.mode;const q=await question(game);const choices:string[]=[]; if(game==="trivia"&&q.id){const correct=String(q.a||"").trim(); const numeric=/^[\\d\\s.,+\\-x×/=]+$/.test(correct); const stop=new Set(["what","which","where","when","who","whom","whose","how","the","a","an","is","are","was","were","be","been","being","has","have","had","do","does","did","of","in","on","at","to","for","from","with","and","or","as","by","about","into","than","then","that","this","these","those","it","its","their","there","here","known","name","called","call","country","longest","highest","order","group","kind","type","many","much"]); const tokens=(v:string)=>{const m=String(v||"").toLowerCase().normalize("NFKD").replace(/[\\u0300-\\u036f]/g,"").match(/[a-z]{4,}/g)||[];return [...new Set(m.filter((w:string)=>!stop.has(w)))];}; const qTokens=tokens(String(q.q||"")); const qSet=new Set(qTokens); const len=norm(correct).length; const {data:alts}=await db.from("uzzapbot_questions").select("question,answer").eq("category","trivia").eq("status","approved").is("duplicate_of",null).neq("id",q.id).not("answer","is",null).limit(500); const scored=(alts||[]).map((x:any)=>{const a=String(x.answer||"").trim();const ct=tokens(String(x.question||""));const overlap=ct.reduce((n:number,w:string)=>n+(qSet.has(w)?1:0),0);const answerType=/^[\\d\\s.,+\\-x×/=]+$/.test(a);const lengthOk=Math.abs(norm(a).length-len)<=6;return {a,overlap,answerType,lengthOk};}).filter((x:any)=>x.a&&norm(x.a)!==norm(correct)&&x.answerType===numeric&&x.lengthOk&&x.overlap>=1).sort((a:any,b:any)=>b.overlap-a.overlap||Math.abs(norm(a.a).length-len)-Math.abs(norm(b.a).length-len)); const unique:string[]=[]; for(const x of scored){if(!unique.some(v=>norm(v)===norm(x.a)))unique.push(x.a);if(unique.length>=3)break;} if(unique.length>=3)choices.push(correct,...unique);}s.current_game=game;s.question_number=(s.question_number||0)+1;s.question=q.q;s.answer=q.a;s.points=Number(q.points)||s.points;s.state_json={...st,answer_aliases:q.aliases||[],question_points:s.points,managed_question_id:q.id||null,question_usage_id:null,question_started_at:new Date().toISOString(),clue_level:0,choices:choices.length>=3?choices.slice(0,4):[]}; if(s.mode_variant==="timed"||s.mode_variant==="speed"){s.timer_seconds=Number(s.timer_seconds)||15;s.deadline_at=new Date(Date.now()+s.timer_seconds*1000).toISOString();} else {s.deadline_at=null;}s.clue_text="";s.used_questions=[...(s.used_questions||[]),norm(q.q)].slice(-500);await save(s);
if(q.id){const {data:u}=await db.from("uzzapbot_question_usage").insert({question_id:q.id,room_name:s.room_name,game, outcome:"pending"}).select("id").maybeSingle();if(u?.id){s.state_json={...(s.state_json||{}),question_usage_id:u.id};await save(s);}}
return `[c03]🎮 NEW ROUND[c01]\n[c14]${s.mode.toUpperCase()} · Q#${s.question_number}[c01]\n[c01]${s.question}\n[c07]⭐ ${s.points} POINTS[c01]`}
async function start(room:string,mode:string,variant:string="classic",timerSeconds:number=15){if(!["add","minus","multiply","add1","minus1","multiply1","algebra1","algebra2","algebra3","trivia","anime","gtaforeign","gtaopm","logic","wordhunt","summonnight","summonnight2","filipino","love","twist","random1","random2","random3","randomgta","math","algebra"].includes(mode))throw new Error("Unknown game: "+mode);let s=await session(room);if(!s){s={room_name:room,game:mode,mode,mode_variant:variant,timer_seconds:timerSeconds,deadline_at:null,points:10,limit_count:100,endless:false,paused:false,question_number:0,question:"",answer:"",clue_text:"",used_questions:[],state_json:{room,game:mode,mode,cycle_games_used:[],recent_games:[]}};const {error}=await db.from("game_sessions").insert(s);if(error)throw error;s=await session(room)}else{s.game=mode;s.mode=mode;s.mode_variant=variant;s.timer_seconds=timerSeconds;s.deadline_at=null;s.points=10;s.limit_count=100;s.endless=false;s.paused=false;s.question_number=0;s.used_questions=[];s.state_json={room,game:mode,mode,cycle_games_used:[],recent_games:[]};const {error:resetError}=await db.from("game_players").update({score:0,correct:0,attempts:0,clues_used:0}).eq("session_id",s.id);if(resetError)throw resetError}const msg=await newRound(s);await send(room,msg)}
async function sendAnswer(room:string,body:string){
  await send(room,body);
  const cfg=await settings(room);
  const target=String(cfg.challenge_room||"").trim();
  if(target&&target!==room)await send(target,body);
}
async function addGamePlayer(sessionId:number,userId:string,user:string,nick:string){
  const ins=await db.from("game_players").insert({session_id:sessionId,user_id:userId,username:user,nickname:nick,score:0,correct:0,attempts:0,clues_used:0});
  if(ins.error) throw ins.error;
  const row=await db.from("game_players").select("id").eq("session_id",sessionId).eq("user_id",userId).maybeSingle();
  if(row.error) throw row.error;
  return row.data?.id?Number(row.data.id):null;
}
async function playerStats(uid:string){ const {data}=await db.from("uzzapbot_player_stats").select("*").eq("user_id",uid).maybeSingle(); return data; }
async function ensurePlayerStats(uid:string){ if(!uid)return null; const {data}=await db.from("uzzapbot_player_stats").upsert({user_id:uid},{onConflict:"user_id",ignoreDuplicates:true}).select("*").maybeSingle(); return data||await playerStats(uid); }
async function unlockAchievement(uid:string,id:string){const {data:a}=await db.from("uzzapbot_achievements").select("xp_reward").eq("id",id).maybeSingle();if(!a)return false;const {data:ins}=await db.from("uzzapbot_player_achievements").insert({user_id:uid,achievement_id:id}).select("achievement_id").maybeSingle();if(ins){const {data:t}=await db.from("uzzapbot_titles").select("id").eq("achievement_id",id).maybeSingle();if(t)await db.from("uzzapbot_player_titles").insert({user_id:uid,title_id:t.id},{onConflict:"user_id,title_id",ignoreDuplicates:true});}
if(ins&&Number(a.xp_reward||0)>0){const cur=await ensurePlayerStats(uid);const xp=Number(cur?.xp||0)+Number(a.xp_reward||0);await db.from("uzzapbot_player_stats").update({xp,level:Math.floor(xp/100)+1,updated_at:new Date().toISOString()}).eq("user_id",uid);await db.from("uzzapbot_xp_history").insert({user_id:uid,xp_delta:Number(a.xp_reward||0),reason:"achievement:"+id,metadata:{achievement_id:id}});}return !!ins}
async function evaluateAchievements(uid:string,stats:any){if(!uid||!stats)return;const checks:any[]=[["first_game",Number(stats.total_games||0)>=1],["first_correct",Number(stats.total_correct||0)>=1],["streak_5",Number(stats.best_streak||0)>=5],["streak_10",Number(stats.best_streak||0)>=10],["points_100",Number(stats.xp||0)>=100],["games_10",Number(stats.total_games||0)>=10],["correct_50",Number(stats.total_correct||0)>=50]];for(const [id,ok] of checks)if(ok)await unlockAchievement(uid,id)}
async function recordPlayerResult(uid:string,correct:boolean){ if(!uid)return; const cur=await ensurePlayerStats(uid); const streak=correct?Number(cur?.current_streak||0)+1:0; const delta=correct?10:1; const xp=Number(cur?.xp||0)+delta; const next={user_id:uid,xp,level:Math.floor(xp/100)+1,current_streak:streak,best_streak:Math.max(Number(cur?.best_streak||0),streak),total_games:Number(cur?.total_games||0),total_correct:Number(cur?.total_correct||0)+(correct?1:0),total_attempts:Number(cur?.total_attempts||0)+1,updated_at:new Date().toISOString()}; await db.from("uzzapbot_player_stats").upsert(next,{onConflict:"user_id"}); await db.from("uzzapbot_xp_history").insert({user_id:uid,xp_delta:delta,reason:correct?"correct_answer":"attempt"}); await evaluateAchievements(uid,next); if(streak>=5)await unlockAchievement(uid,"streak_5"); if(streak>=10)await unlockAchievement(uid,"streak_10"); }
async function recordLeaderboardActivity(uid:string,game:string,points:number,correct:boolean){if(!uid)return;await db.from("uzzapbot_player_activity").insert({user_id:uid,game:game||"unknown",points:Number(points||0),correct});}
async function handleAnswer(room:string,uid:string,username:string,guess:string){const s=await session(room);if(!s||s.paused)return;if(!uid)return;const st=s.state_json||{};if(s.mode_variant==="daily"&&st.daily_challenge_id&&String(st.daily_user_id||"")!==uid)return await send(room,"[c12]📅 This Daily Challenge belongs to another player.[c01]");if(s.deadline_at&&Date.now()>new Date(s.deadline_at).getTime()){await send(room,"[c12]⏱️ Time's up! The round expired.[c01]");await newRound(s);return;}const {data:p}=await db.from("game_players").select("*").eq("session_id",s.id).eq("user_id",uid).maybeSingle();if(!p)return;const usageId=Number((s.state_json||{}).question_usage_id||0);
const usageStarted=String((s.state_json||{}).question_started_at||"");
const responseMs=usageStarted?Math.max(0,Date.now()-new Date(usageStarted).getTime()):null;
const aliases=Array.isArray((s.state_json||{}).answer_aliases)?(s.state_json||{}).answer_aliases.map((x:any)=>String(x)):[];
const answers=[String(s.answer||""),...aliases].filter(Boolean);
const ok=answers.some((ans:string)=>norm(guess)===norm(ans)||(norm(guess).length>=4&&norm(ans).length>=5&&levenshtein(norm(guess),norm(ans))>=0.88));p.attempts++;if(ok){if(usageId)await db.from("uzzapbot_question_usage").update({outcome:"correct",response_ms:responseMs}).eq("id",usageId);await recordPlayerResult(uid,true);const {data:doubleUse}=await db.from("uzzapbot_powerup_uses").select("id,metadata").eq("user_id",uid).eq("session_id",s.id).eq("powerup","double").is("expires_at",null).eq("metadata->>consumed","false").limit(1).maybeSingle();
const multiplier=doubleUse?2:1;
const awarded=Number(s.points||10)*multiplier;
if(doubleUse)await db.from("uzzapbot_powerup_uses").update({metadata:{...(doubleUse.metadata||{}),consumed:true,consumed_at:new Date().toISOString()}}).eq("id",doubleUse.id);
await recordLeaderboardActivity(uid,String(s.current_game||s.mode||"unknown"),awarded,true);p.correct++;p.score+=awarded;await db.from("game_players").update({attempts:p.attempts,correct:p.correct,score:p.score,updated_at:new Date().toISOString()}).eq("id",p.id);if(s.mode_variant==="daily"){await db.from("uzzapbot_daily_challenges").update({score:p.score,correct:p.correct,attempts:p.attempts,completed_at:p.attempts>=10?new Date().toISOString():null}).eq("id",Number(st.daily_challenge_id||0)).eq("user_id",uid);}await sendAnswer(room,"[c02]🎉 CORRECT, "+(p.nickname||username)+"! [c07]+"+awarded+" POINTS"+(multiplier===2?" (2×)":"")+"[c01]");if(s.mode_variant==="daily"&&p.attempts>=10){s.paused=true;await save(s);await sendAnswer(room,`[c14]📅 DAILY CHALLENGE COMPLETE 🏆[c01]\n[c03]Player:[c01] ${p.nickname||username}\n[c03]Score:[c07] ${p.score} points[c01]\n[c03]Correct:[c01] ${p.correct}/${p.attempts}`);return;}if(s.mode_variant==="daily"){await sendAnswer(room,await newRound(s));return;}if(s.mode_variant==="battle"&&p.score>=50){const {data:b}=await db.from("uzzapbot_battles").select("id").eq("session_id",s.id).in("status",["active","accepted"]).maybeSingle();if(b)await db.from("uzzapbot_battles").update({status:"completed",winner_id:uid,completed_at:new Date().toISOString()}).eq("id",b.id);s.paused=true;await save(s);await sendAnswer(room,"[c14]⚔️ BATTLE COMPLETE 🏆[c01]\n[c03]Winner:[c07] "+(p.nickname||username)+"[c01]\n[c03]Score:[c07] "+p.score+" points[c01]");return;}if(s.mode_variant==="room_challenge"&&p.score>=50){const {data:rc}=await db.from("uzzapbot_room_challenges").select("id").eq("session_id",s.id).eq("status","active").maybeSingle();if(rc)await db.from("uzzapbot_room_challenges").update({status:"completed",completed_at:new Date().toISOString()}).eq("id",rc.id);s.paused=true;await save(s);await sendAnswer(room,"[c14]🏆 ROOM CHALLENGE COMPLETE[c01]\n[c03]Winner:[c07] "+(p.nickname||username)+"[c01]\n[c03]Score:[c07] "+p.score+" points[c01]");return;}if(s.mode_variant==="tournament"&&p.score>=50){const ts={...(s.state_json||{})},wins={...(ts.round_wins||{})};wins[uid]=Number(wins[uid]||0)+1;const rounds=Number(ts.tournament_rounds||3),round=Number(ts.tournament_round||1);if(round>=rounds){const winnerId=Object.keys(wins).sort((a,b)=>Number(wins[b]||0)-Number(wins[a]||0))[0]||uid;const {data:t}=await db.from("uzzapbot_tournaments").select("id").eq("session_id",s.id).maybeSingle();if(t)await db.from("uzzapbot_tournaments").update({status:"completed",winner_id:winnerId,current_round:round,completed_at:new Date().toISOString()}).eq("id",t.id);s.paused=true;s.state_json={...ts,round_wins:wins,tournament_winner:winnerId};await save(s);return await sendAnswer(room,"[c14]🏆 TOURNAMENT COMPLETE[c01]\n[c03]Winner ID:[c07] "+winnerId+"[c01]");}const {data:players}=await db.from("game_players").select("id").eq("session_id",s.id);for(const gp of players||[])await db.from("game_players").update({score:0,correct:0,attempts:0,updated_at:new Date().toISOString()}).eq("id",gp.id);s.state_json={...ts,round_wins:wins,tournament_round:round+1};s.question_number=0;s.paused=false;await save(s);await sendAnswer(room,"[c11]🏆 NEXT TOURNAMENT ROUND[c01]");return await sendAnswer(room,await newRound(s));}if(s.mode_variant==="battle"&&p.score>=50){return;}if(s.mode_variant==="room_challenge"&&p.score>=50){return;}if(s.mode_variant==="tournament"&&p.score>=50){return;} if(!s.endless&&p.score>=s.limit_count){s.paused=true;await save(s);await sendAnswer(room,`[c14]🏆 GAME WINNER 🏆\n[c01]Congratulations, ${p.nickname||username}![c01]\n[c07]⭐ FINAL SCORE: ${p.score}`)}else{await sendAnswer(room,await newRound(s))}}else{if(usageId)await db.from("uzzapbot_question_usage").update({outcome:"incorrect",response_ms:responseMs}).eq("id",usageId);const {data:shieldUse}=await db.from("uzzapbot_powerup_uses").select("id,metadata").eq("user_id",uid).eq("session_id",s.id).eq("powerup","shield").is("expires_at",null).eq("metadata->>consumed","false").limit(1).maybeSingle();
if(shieldUse){await db.from("uzzapbot_powerup_uses").update({metadata:{...(shieldUse.metadata||{}),consumed:true,consumed_at:new Date().toISOString()}}).eq("id",shieldUse.id);await recordPlayerResult(uid,true);await recordLeaderboardActivity(uid,String(s.current_game||s.mode||"unknown"),0,false);await db.from("game_players").update({attempts:p.attempts,updated_at:new Date().toISOString()}).eq("id",p.id);if(s.mode_variant==="daily"){await db.from("uzzapbot_daily_challenges").update({score:p.score,correct:p.correct,attempts:p.attempts,completed_at:p.attempts>=10?new Date().toISOString():null}).eq("id",Number(st.daily_challenge_id||0)).eq("user_id",uid);if(p.attempts>=10){s.paused=true;await save(s);await sendAnswer(room,`[c14]📅 DAILY CHALLENGE COMPLETE 🏆[c01]\n[c03]Player:[c01] ${p.nickname||username}\n[c03]Score:[c07] ${p.score} points[c01]\n[c03]Correct:[c01] ${p.correct}/${p.attempts}`);return;}await sendAnswer(room,await newRound(s));return;}await sendAnswer(room,"[c07]🛡️ SHIELD SAVED YOU, "+(p.nickname||username)+".[c01] No streak loss.")}else{if(s.mode_variant==="survival"||s.mode_variant==="sudden_death"){s.survival_lives=Math.max(0,Number(s.survival_lives||1)-1);await save(s);if(s.survival_lives<=0){await send(room,"[c08]💥 GAME OVER[c01] "+(p.nickname||username)+" ran out of lives.");await db.from("game_sessions").delete().eq("id",s.id);return;}await send(room,"[c12]❤️ Lives remaining: "+s.survival_lives+"[c01]");}await recordPlayerResult(uid,false);await recordLeaderboardActivity(uid,String(s.current_game||s.mode||"unknown"),0,false);await db.from("game_players").update({attempts:p.attempts,updated_at:new Date().toISOString()}).eq("id",p.id);await sendAnswer(room,"[c08]❌ Not quite, "+(p.nickname||username)+".[c01] Keep trying!")}}}
function levenshtein(a:string,b:string){const d=Array.from({length:b.length+1},(_,i)=>i);for(let i=0;i<a.length;i++){let prev=i;d[0]=i+1;for(let j=0;j<b.length;j++){const cur=d[j+1];d[j+1]=Math.min(d[j+1]+1,d[j]+1,prev+(a[i]===b[j]?0:1));prev=cur}}return 1-(d[b.length]/Math.max(a.length,b.length))}
async function clue(room:string,uid:string){const s=await session(room);if(!s)return "[c08]No active game.";const n=Number((s.state_json||{}).clue_level||0)+1;if(n>3)return "[c12]Maximum clues reached. Try your answer!";const compact=String(s.answer||"").replace(/\s+/g,"");const reveal=Math.max(1,Math.floor(compact.length*({1:1/3,2:1/2,3:2/3} as any)[n]));let seen=0,out="";for(const ch of String(s.answer)){if(/\s/.test(ch)||/[^\p{L}\p{N}]/u.test(ch))out+=ch;else if(seen++<reveal)out+=ch;else out+="_"}s.clue_text=out;s.state_json={...(s.state_json||{}),clue_level:n};const {data:p}=await db.from("game_players").select("id,clues_used").eq("session_id",s.id).eq("user_id",uid).maybeSingle();if(p)await db.from("game_players").update({clues_used:(p.clues_used||0)+1}).eq("id",p.id);await save(s);return `[c12]💡 Here's a clue... [c01]${n}/3: ${out}`}
async function activityTick(){
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
    const cooldown=86400*1000;
    if(lastBot&&now-lastBot<cooldown)continue;
    const {count:recentBot}=await db.from("room_messages").select("id",{count:"exact",head:true}).eq("room_name",room).eq("sender","UzzapBot").gte("created_at",new Date(now-86400000).toISOString());
    if((recentBot||0)>0)continue;
    await send(room,inactiveMessage);
    await db.from("uzzapbot_room_activity").update({last_bot_activity_at:new Date().toISOString(),updated_at:new Date().toISOString()}).eq("room_name",room);
    posted++;
  }
  return {processed,posted};
}

async function main(b:any){const id=Number(b.message_id);if(!Number.isSafeInteger(id)||id<=0)throw new Error("message_id must be a positive integer");const {data:m,error}=await db.from("room_messages").select("id,room_name,sender_id,sender,body,created_at").eq("id",id).maybeSingle();if(error)throw error;if(!m)throw new Error("Message not found");const room=String(m.room_name||"").trim(),text=String(m.body||"").trim(),uid=String(m.sender_id||"");if(!room||!text)return;const dotAI=text.startsWith(".")&&text.slice(1).trim().length>0;
if(dotAI){
  const prompt=text.slice(1).trim();
  const nickname=String(m.sender||"user").trim()||"user";
  const result=await botConversation(room,nickname,prompt,uid);
  if(result?.silent || !String(result?.reply||"").trim()) return;
  return await sendAIReply(room,result.reply);
}
const a=text.startsWith("/")?text.slice(1).trim().split(/\s+/):[];if(!text.startsWith("/")){await handleAnswer(room,uid,String(m.sender||""),text);return}const rawCommand=(a[0]||"").toLowerCase(),c=rawCommand==="50"?"fifty":rawCommand==="ai"&&String(a[1]||"").toLowerCase()==="check"?"aicheck":rawCommand,args=rawCommand==="ai"&&String(a[1]||"").toLowerCase()==="check"?a.slice(2):a.slice(1);
const botCommands=new Set(["aicheck","activate","challenge","help","about","version","start","stop","pause","resume","lock","unlock","join","leave","players","leaderboard","score","clue","repost","next","reveal","stat","stats","status","wcbot","wmsg","powerup","use","ewordhunt","twordhunt","trivia","anime","logic","gtaopm","gtaforeign","gta","games","game","random","random1","random2","random3","randomgta","math","algebra","profile","rank","xp","streak","achievements","title","history","hint","fifty","skip","double","time","shield","timed","speed","survival","sudden","daily","battle","tournament","room","ph","tt"]);

if(c==="aicheck"){
  const started=performance.now();
  const primaryUrl=Deno.env.get("UZZAPBOT_AI_URL")||"https://uzzapbot-ai.mharbalaba.workers.dev/ai";
  const primarySecret=Deno.env.get("UZZAPBOT_AI_SECRET")||"";
  try{
    const response=await fetch(primaryUrl,{method:"POST",headers:{"Content-Type":"application/json",...(primarySecret?{"Authorization":"Bearer "+primarySecret}:{})},body:JSON.stringify({messages:[{role:"system",content:"Diagnostic check. Return only PASS."},{role:"user",content:"AI CHECK"}]})});
    const body=await response.text();
    let data:any=null; try{data=JSON.parse(body)}catch{}
    const reply=String(data?.response||data?.choices?.[0]?.message?.content||"").trim();
    const latency=Math.max(0,Math.round(performance.now()-started));
    if(!response.ok||!reply)throw new Error("AI diagnostic failed");
    const lines=[
      "[c11]🤖 UZZAPBOT AI CHECK",
      "",
      "[c03]Status:[c01] [c07]ONLINE",
      "Core: "+ENGINE_NAME,
      "Version: "+BOT_VERSION,
      "[c05]Uzzap Intel:[c01] [c07]READY",
      "Response - [c07]PASS[c01]",
      "Latency: "+latency+" ms"
    ];
    return await send(room,lines.join("\n"));
  }catch{
    const latency=Math.max(0,Math.round(performance.now()-started));
    const lines=[
      "[c11]🤖 UZZAPBOT AI CHECK",
      "",
      "[c03]Status:[c01] [c08]OFFLINE",
      "Core: "+ENGINE_NAME,
      "Version: "+BOT_VERSION,
      "[c05]Uzzap Intel:[c01] [c08]NOT READY",
      "Response - [c08]FAIL[c01]",
      "Latency: "+latency+" ms"
    ];
    return await send(room,lines.join("\n"));
  }
}
if(!botCommands.has(c)){
  return await send(room,"[c08]Unknown command. Use /HELP for available commands.[c01]");
}const cfg=await settings(room);const isAdmin=await admin(uid);if(c!=="activate"&&!cfg.activated){await send(room,"[c11]UzzapBot is inactive in this room. An administrator must use /ACTIVATE.");return}
if(c==="challenge"){
  if(!isAdmin)return await send(room,"[c08]Admin-only command.");
  const target=args.join(" ").trim(); const challengeRoom=target.toLowerCase()==="off"?"":target;
  await db.from("uzzapbot_room_settings").upsert({room_name:room,activated:true,challenge_room:challengeRoom,updated_at:new Date().toISOString()});
  return await send(room,challengeRoom?"[c03]Challenge room set to "+challengeRoom+".":"[c03]Challenge room disabled.");
}
if(["help","about","version"].includes(c)){
  if(c==="about")return await send(room,"[c11]🤖 UzzapBot[c01] — Supabase Edge runtime\n[c01]Game engine, clues, scoring, leaderboards and room controls.");
  if(c==="version")return await send(room,"[c11]🤖 UzzapBot[c01] [c14]v"+BOT_VERSION+"[c01]\n[c02]🎮 Full Edge Game Core[c01]\n[c02]🟢 Status: ONLINE[c01]");
  const topic=(args[0]||"").toLowerCase(); const command=args.join(" ").toLowerCase();
  const help:any={
    main:"[c11]🤖 UZZAPBOT HELP[c01]\n\n[c07]🎮 Games[c01] /HELP GAMES\n[c07]👤 Players[c01] /HELP PLAYERS\n[c07]🏆 Scores & Stats[c01] /HELP STATS\n[c07]⚙️ Room Controls[c01] /HELP ROOM\n[c07]🛡️ Moderation[c01] /HELP MOD\n[c07]🤖 Bot[c01] /HELP BOT\n\n[c01]Tip: /HELP <command> for details.",
    games:"[c11]🎮 GAME COMMANDS[c01]\n\n[c03]/MATH ON[c01]\n[c03]/TRIVIA ON[c01]\n[c03]/ANIME ON[c01]\n[c03]/LOGIC ON[c01]\n[c03]/ALGEBRA ON[c01]\n[c03]/PH ON[c01]\n[c03]/TT ON[c01]\n[c03]/RANDOM QUIZ1[c01]\n[c03]/RANDOM QUIZ2[c01]\n[c03]/RANDOM QUIZ3[c01]\n[c03]/RANDOM GTA[c01]\n[c03]/GTA OPM[c01]\n[c03]/GTA FOREIGN[c01]\n[c03]/EWORDHUNT[c01]\n[c03]/TWORDHUNT[c01]\n[c03]/TIMED ON[c01]\n[c03]/SPEED ON[c01]\n[c03]/SURVIVAL ON[c01]\n[c03]/SUDDEN DEATH[c01]\n[c03]/DAILY ON[c01]\n[c03]/DAILY STATUS[c01]\n[c03]/ROOM CHALLENGE[c01]\n[c03]/ROOM CHALLENGE END[c01]\n[c03]/BATTLE @user[c01]\n[c03]/BATTLE ACCEPT <id>[c01]\n[c03]/BATTLE DECLINE <id>[c01]\n[c03]/BATTLE STATUS <id>[c01]\n[c03]/TOURNAMENT[c01]\n[c03]/TOURNAMENT JOIN[c01]\n[c03]/TOURNAMENT START[c01]\n[c03]/TOURNAMENT STATUS[c01]\n\n[c01]Send answers as normal messages.",
    players:"[c11]👤 PLAYER COMMANDS[c01]\n\n[c03]/PROFILE[c01]  [c03]/RANK[c01]  [c03]/XP[c01]  [c03]/STREAK[c01]\n[c03]/ACHIEVEMENTS[c01]  [c03]/TITLE[c01]  [c03]/HISTORY[c01]\n[c03]/HINT[c01]  [c03]/50[c01]  [c03]/SKIP[c01]  [c03]/DOUBLE[c01]  [c03]/TIME[c01]  [c03]/SHIELD[c01]\n[c03]/JOIN[c01]  [c03]/LEAVE[c01]  [c03]/PLAYERS[c01]  [c03]/SCORE[c01]\n[c03]/LEADERBOARD[c01]\n[c03]/LEADERBOARD DAILY|WEEKLY|MONTHLY|ALL[c01]\n[c03]/LEADERBOARD MATH|TRIVIA|ANIME|LOGIC|ALGEBRA[c01]\n[c03]/LEADERBOARD PH|TT|GTA OPM|GTA FOREIGN[c01]\n[c03]/LEADERBOARD ENGLISH WORDHUNT[c01]\n[c03]/LEADERBOARD TAGALOG WORDHUNT[c01]\n[c03]/CLUE[c01]  [c03]/REPOST[c01]",
    stats:"[c11]🏆 SCORES & STATS[c01]\n\n[c03]/SCORE[c01] — Current game score.\n[c03]/LEADERBOARD[c01] — Current game ranking.\n[c03]/LEADERBOARD DAILY|WEEKLY|MONTHLY|ALL[c01] — Period rankings.\n[c03]/LEADERBOARD <GAME>[c01] — Game rankings.\n[c03]/STATS[c01] — Bot health/performance summary (admin).\n[c03]/PLAYERS[c01] — Current players.",
    room:"[c11]⚙️ ROOM & GAME CONTROLS[c01]\n\n[c03]/STATUS[c01]  [c03]/STOP[c01]  [c03]/PAUSE[c01]  [c03]/RESUME[c01]\n[c03]/NEXT[c01]  [c03]/REVEAL[c01]  [c03]/CLUE[c01]  [c03]/REPOST[c01]\n[c03]/LOCK[c01]  [c03]/UNLOCK[c01]\n[c03]/ROOM CHALLENGE[c01]  [c03]/ROOM CHALLENGE END[c01]\n[c03]/TOURNAMENT[c01]  [c03]/TOURNAMENT JOIN[c01]  [c03]/TOURNAMENT START[c01]  [c03]/TOURNAMENT STATUS[c01]\n\n[c01]Control commands require moderator/admin permission.",
    mod:"[c11]🛡️ MODERATOR COMMANDS[c01]\n\n[c03]/ACTIVATE[c01]\n[c03]/STOP[c01] / [c03]/PAUSE[c01] / [c03]/RESUME[c01]\n[c03]/NEXT[c01] / [c03]/REVEAL[c01]\n[c03]/LOCK[c01] / [c03]/UNLOCK[c01]\n[c03]/WCBOT ON[c01] / [c03]/WCBOT OFF[c01]\n[c03]/WMSG <message>[c01]\n[c03]/CHALLENGE <room>[c01] / [c03]/CHALLENGE OFF[c01]\n[c03]/STATS[c01]\n\n[c01]Moderator/admin permission required.",
    bot:"[c11]🤖 BOT COMMANDS[c01]\n\n[c03]/HELP[c01]  [c03]/HELP <topic>[c01]  [c03]/HELP <command>[c01]\n[c03]/ABOUT[c01]  [c03]/VERSION[c01]  [c03]/STATUS[c01]  [c03]/REPOST[c01]"
  };
  const details:any={
    math:"[c03]/MATH ON[c01]\nStarts Math.",
    trivia:"[c03]/TRIVIA ON[c01]\nStarts Trivia.",
    anime:"[c03]/ANIME ON[c01]\nStarts Anime quiz.",
    logic:"[c03]/LOGIC ON[c01]\nStarts Logic quiz.",
    algebra:"[c03]/ALGEBRA ON[c01]\nStarts Algebra.",
    ph:"[c03]/PH ON[c01]\nStarts Filipino/PH.",
    tt:"[c03]/TT ON[c01]\nStarts Twist.",
    timed:"[c03]/TIMED ON[c01]\nStarts timed Trivia with a 15-second timer.",
    speed:"[c03]/SPEED ON[c01]\nStarts fast Trivia with a 10-second timer.",
    survival:"[c03]/SURVIVAL ON[c01]\nStarts Survival with 3 lives.",
    "sudden death":"[c03]/SUDDEN DEATH[c01]\nStarts Sudden Death with 1 life.",
    daily:"[c03]/DAILY ON[c01]\nStarts today's Daily Challenge.\n[c03]/DAILY STATUS[c01]\nShows today's progress.",
    battle:"[c03]/BATTLE @user[c01]\nChallenge a player.\n[c03]/BATTLE ACCEPT <id>[c01]\n[c03]/BATTLE DECLINE <id>[c01]\n[c03]/BATTLE STATUS <id>[c01]",
    tournament:"[c03]/TOURNAMENT[c01] creates registration.\n[c03]/TOURNAMENT JOIN[c01] joins.\n[c03]/TOURNAMENT START[c01] starts with 2+ players.\n[c03]/TOURNAMENT STATUS[c01] shows status.",
    profile:"[c03]/PROFILE[c01]\nShows XP, level, streak and lifetime stats.",
    rank:"[c03]/RANK[c01]\nShows level and XP.",
    xp:"[c03]/XP[c01]\nShows XP progress.",
    streak:"[c03]/STREAK[c01]\nShows current and best streak.",
    achievements:"[c03]/ACHIEVEMENTS[c01]\nShows unlocked achievements.",
    title:"[c03]/TITLE[c01]\nShows equipped title. Use /TITLE LIST or /TITLE <id>.",
    history:"[c03]/HISTORY[c01]\nShows recent XP history.",
    hint:"[c03]/HINT[c01]\nUses a hint power-up.",
    "50":"[c03]/50[c01]\nUses the 50/50 power-up.",
    skip:"[c03]/SKIP[c01]\nSkips the current question.",
    double:"[c03]/DOUBLE[c01]\nDoubles your next correct-answer points.",
    time:"[c03]/TIME[c01]\nAdds 10 seconds to the round.",
    shield:"[c03]/SHIELD[c01]\nProtects your next wrong answer from breaking your streak.",
    join:"[c03]/JOIN[c01]\nJoin the current active game.",
    leave:"[c03]/LEAVE[c01]\nLeave the current game.",
    players:"[c03]/PLAYERS[c01]\nShows current players.",
    score:"[c03]/SCORE[c01]\nShows your current score.",
    leaderboard:"[c03]/LEADERBOARD[c01]\nShows the current leaderboard.",
    stats:"[c03]/STATS[c01]\nAdmin-only bot health/performance statistics.",
    status:"[c03]/STATUS[c01]\nShows current game state.",
    stop:"[c03]/STOP[c01]\nStops the active game. Admin only.",
    pause:"[c03]/PAUSE[c01]\nPauses the active game. Admin only.",
    resume:"[c03]/RESUME[c01]\nResumes the active game. Admin only.",
    next:"[c03]/NEXT[c01]\nAdvances to the next question. Admin only.",
    reveal:"[c03]/REVEAL[c01]\nReveals the answer and advances. Admin only.",
    activate:"[c03]/ACTIVATE[c01]\nActivates UzzapBot. Admin only.",
    lock:"[c03]/LOCK[c01]\nLocks game input. Admin only.",
    unlock:"[c03]/UNLOCK[c01]\nUnlocks game input. Admin only.",
    wcbot:"[c03]/WCBOT ON[c01] / [c03]/WCBOT OFF[c01]\nWelcome bot toggle. Admin only.",
    wmsg:"[c03]/WMSG <message>[c01]\nSets the welcome message. Admin only.",
    challenge:"[c03]/CHALLENGE <room>[c01] / [c03]/CHALLENGE OFF[c01]\nResponse mirroring. Admin only.",
    "room challenge":"[c03]/ROOM CHALLENGE[c01]\nStarts a room challenge.\n[c03]/ROOM CHALLENGE END[c01]\nEnds it.",
    "random quiz1":"[c03]/RANDOM QUIZ1[c01]\nStarts Random Quiz 1.",
    "random quiz2":"[c03]/RANDOM QUIZ2[c01]\nStarts Random Quiz 2.",
    "random quiz3":"[c03]/RANDOM QUIZ3[c01]\nStarts Random Quiz 3.",
    "random gta":"[c03]/RANDOM GTA[c01]\nStarts Random GTA.",
    "gta opm":"[c03]/GTA OPM[c01]\nStarts GTA OPM.",
    "gta foreign":"[c03]/GTA FOREIGN[c01]\nStarts GTA Foreign.",
    "english wordhunt":"[c03]/ENGLISH WORDHUNT[c01]\nStarts English WordHunt.",
    "tagalog wordhunt":"[c03]/TAGALOG WORDHUNT[c01]\nStarts Tagalog WordHunt.",
    about:"[c03]/ABOUT[c01]\nShows information about UzzapBot.",
    version:"[c03]/VERSION[c01]\nShows the current UzzapBot version."
  };
  if(!topic)return await send(room,help.main);
  if(help[topic])return await send(room,help[topic]);
  if(details[command])return await send(room,details[command]);
  return await send(room,"[c08]Unknown help topic.[c01] Try /HELP, /HELP GAMES, /HELP PLAYERS, /HELP STATS, /HELP ROOM, /HELP MOD or /HELP <command>.");
}
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
 const cooldown=power==="hint"?30:power==="skip"?20:15;
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
   const base=s.deadline_at?new Date(s.deadline_at).getTime():Date.now(); const nextDeadline=new Date(Math.max(Date.now(),base)+10000); s.deadline_at=nextDeadline.toISOString(); await save(s); reply="[c07]⏱️ TIME BOOST[c01] +10 seconds added to this round."; meta.time_bonus_seconds=10; meta.consumed=true;
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
Deno.serve(async(req)=>{if(req.method!=="POST")return out({error:"POST required"},405);if(!(await authorized(req)))return out({error:"Unauthorized"},401);let b:any;try{b=await req.json()}catch{return out({error:"Invalid JSON"},400)}if(b.action==="health")return out({ok:true,service:"uzzapbot",runtime:"supabase-edge",version:BOT_VERSION,engine:ENGINE_NAME,qna_source:"supabase.uzzapbot_questions",word_source:"supabase.uzzapbot_word_datasets"});
if(b.action==="activity_tick")return out({ok:true,action:"activity_tick",...(await activityTick())});try{const id=Number(b.message_id);if(!Number.isSafeInteger(id)||id<=0)throw new Error("message_id must be a positive integer");const {data:r,error:re}=await db.rpc("uzzapbot_claim_receipt",{p_message_id:id});if(re)throw re;if(r==="processed")return out({ok:true,duplicate:true,message_id:id});await main({message_id:id});await receipt(id,"processed");return out({ok:true,message_id:id})}catch(e){const msg=e instanceof Error?e.message:String(e);const id=Number(b.message_id);if(Number.isSafeInteger(id)&&id>0)await receipt(id,"failed",msg);return out({error:msg,message_id:id},500)}});