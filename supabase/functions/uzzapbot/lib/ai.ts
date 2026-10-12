import { db, BOT_VERSION, ENGINE_NAME, sendAIReply } from "./core.ts";

export const PROVINCE_LANGUAGE:any={
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

export function roomLanguage(room:string){
  const key=String(room||"").trim().toLowerCase().replace(/\s+/g," ");
  return PROVINCE_LANGUAGE[key]||{dialect:"Tagalog"};
}

export function detectUserLanguage(text:string,room:string){
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

export async function callAI(messages:any[],primaryUrl:string,secret:string,backupUrl:string){
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
export function memoryKeyFromText(value:string){
  return String(value||"").toLowerCase().normalize("NFKD").replace(/[\u0300-\u036f]/g,"")
    .replace(/[^\p{L}\p{N}\s]/gu," ").replace(/\s+/g," ").trim().slice(0,120);
}
export function parseMemoryRequest(text:string){
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
export async function cleanupMemory(userId:string,room:string){
  const {error}=await db.rpc("uzzapbot_cleanup_memory_v1",{
    p_user_id:userId,
    p_room_name:room,
    p_max_per_room:50
  });
  if(error)throw error;
}
export async function embedMemoryText(text:string){
  const input=String(text||"").trim().slice(0,2000);
  if(!input)return null;
  const output=await memoryEmbeddingModel.run(input,{mean_pool:true,normalize:true});
  const vector=Array.isArray(output)?output:(output&&Array.isArray((output as any).data)?Array.from((output as any).data):null);
  if(!vector||vector.length!==384)throw new Error("UzzapBot memory embedding returned an invalid vector");
  return vector;
}
export function memoryIntentKind(query:string){
  const t=String(query||"").toLowerCase();
  if(/\b(call me|what do i want you to call me|what should you call me|nickname|pangalan|itawag|tawag)\b/.test(t))return "nickname";
  if(/\b(language|dialect|pinulongan|yinaknan|wika|bisaya|cebuano|waray|hiligaynon|tagalog)\b/.test(t))return "language";
  if(/\b(music|musika|kanta|song|reggae|roots)\b/.test(t))return "music";
  if(/\b(work|job|profession|occupation|trabaho|cabling|fiber|installation)\b/.test(t))return "work";
  if(/\b(style|reply|replies|response|casual|corporate|tubag)\b/.test(t))return "style";
  return null;
}
export function memoryIntentBonus(query:string,row:any){
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
export async function backfillMemoryEmbeddings(userId:string,room:string){
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
export async function getMemory(userId:string,room:string,query:string=""){
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
export async function upsertMemory(userId:string,room:string,key:string,value:string,type:string="fact"){
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
export function formatMemoryList(rows:any[]){
  if(!rows.length)return "[c12]Wala pa koy na-save nga memory about you in this room.[c01]";
  return "Ah oo 😄 Mao ni akong naaalala about nimo:\n"+rows.slice(0,20).map((x:any)=>"• "+String(x.memory_value||"")).join("\n");
}
export function casualResponseDecision(userText:string, recent:any[], userId:string){
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
export function classifyResponseMode(userText:string, decision:any){
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

export function relationshipLevel(memoryCount:number,recentUserCount:number){
  if(memoryCount>=3||recentUserCount>=12)return "familiar regular";
  if(memoryCount>=1||recentUserCount>=4)return "regular";
  return "new/acquaintance";
}

export async function botConversation(room:string,nickname:string,userText:string,userId:string){
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

export async function handleDotAI(room:string,text:string,uid:string,sender:string):Promise<boolean>{
  if(!(text.startsWith(".")&&text.slice(1).trim().length>0))return false;
  const prompt=text.slice(1).trim();
  const nickname=(sender||"user").trim()||"user";
  const result=await botConversation(room,nickname,prompt,uid);
  if(result?.silent||!String(result?.reply||"").trim())return true;
  await sendAIReply(room,result.reply);
  return true;
}
