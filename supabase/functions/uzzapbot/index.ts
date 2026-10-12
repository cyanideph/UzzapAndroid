import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { db, out, authorized, receipt, activityTick, BOT_VERSION, ENGINE_NAME } from "./lib/core.ts";
import { handleAnswer } from "./lib/questions.ts";
import { handleDotAI } from "./lib/ai.ts";
import { parseCommand, routeCommand } from "./lib/commands.ts";

// Thin dispatch entrypoint. Game logic lives in lib/questions.ts,
// AI in lib/ai.ts, command routing in lib/commands.ts, help in lib/help.ts,
// shared primitives in lib/core.ts.
async function main(b:any){
  const id=Number(b.message_id);
  if(!Number.isSafeInteger(id)||id<=0)throw new Error("message_id must be a positive integer");
  const {data:m,error}=await db.from("room_messages").select("id,room_name,sender_id,sender,body,created_at").eq("id",id).maybeSingle();
  if(error)throw error;
  if(!m)throw new Error("Message not found");
  const room=String(m.room_name||"").trim(),text=String(m.body||"").trim(),uid=String(m.sender_id||"");
  if(!room||!text)return;
  if(await handleDotAI(room,text,uid,String(m.sender||"user").trim()||"user"))return;
  const {c,args}=parseCommand(text);
  if(!text.startsWith("/")){await handleAnswer(room,uid,String(m.sender||""),text);return;}
  await routeCommand(room,text,uid,String(m.sender||""),c,args);
}

Deno.serve(async(req)=>{
  if(req.method!=="POST")return out({error:"POST required"},405);
  if(!(await authorized(req)))return out({error:"Unauthorized"},401);
  let b:any;
  try{b=await req.json()}catch{return out({error:"Invalid JSON"},400)}
  if(b.action==="health")return out({ok:true,service:"uzzapbot",runtime:"supabase-edge",version:BOT_VERSION,engine:ENGINE_NAME,qna_source:"supabase.uzzapbot_questions",word_source:"supabase.uzzapbot_word_datasets"});
  if(b.action==="activity_tick")return out({ok:true,action:"activity_tick",...(await activityTick())});
  try{
    const id=Number(b.message_id);
    if(!Number.isSafeInteger(id)||id<=0)throw new Error("message_id must be a positive integer");
    const {data:r,error:re}=await db.rpc("uzzapbot_claim_receipt",{p_message_id:id});
    if(re)throw re;
    if(r==="processed")return out({ok:true,duplicate:true,message_id:id});
    await main({message_id:id});
    await receipt(id,"processed");
    return out({ok:true,message_id:id});
  }catch(e){
    const msg=e instanceof Error?e.message:String(e);
    const id=Number(b.message_id);
    if(Number.isSafeInteger(id)&&id>0)await receipt(id,"failed",msg);
    return out({error:msg,message_id:id},500);
  }
});
