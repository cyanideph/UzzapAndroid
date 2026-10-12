import { db, send, BOT_VERSION, ENGINE_NAME } from "./core.ts";

export async function handleAicheck(room:string){
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

export async function handleHelp(room:string,c:string,args:string[]){
  if(c==="about")return await send(room,"[c11]🤖 UzzapBot[c01] — Supabase Edge runtime\n[c01]Game engine, clues, scoring, leaderboards and room controls.");
  if(c==="version")return await send(room,"[c11]🤖 UzzapBot[c01] [c14]v"+BOT_VERSION+"[c01]\n[c02]🎮 Full Edge Game Core[c01]\n[c02]🟢 Status: ONLINE[c01]");
  const topic=(args[0]||"").toLowerCase(); const command=args.join(" ").toLowerCase();
  const help:any={
    main:"[c11]🤖 UZZAPBOT HELP[c01]\n\n[c07]🎮 Games[c01] /HELP GAMES\n[c07]👤 Players[c01] /HELP PLAYERS\n[c07]🏆 Scores & Stats[c01] /HELP STATS\n[c07]⚙️ Room Controls[c01] /HELP ROOM\n[c07]🛡️ Moderation[c01] /HELP MOD\n[c07]🤖 Bot[c01] /HELP BOT\n\n[c01]Tip: /HELP <command> for details.",
    games:"[c11]GAME COMMANDS[c01]\n\n[c01]Quiz, word and arena games start instantly.\n\n[c07]Quiz[c01]\n[c03]/TRIVIA ON[c01]  [c03]/MATH ON[c01]  [c03]/ANIME ON[c01]\n[c03]/LOGIC ON[c01]  [c03]/ALGEBRA ON[c01]  [c03]/PH ON[c01]  [c03]/TT ON[c01]\n[c03]/RANDOM QUIZ1[c01]  [c03]/RANDOM QUIZ2[c01]  [c03]/RANDOM QUIZ3[c01]\n[c07]Word Games[c01]\n[c03]/RANDOM GTA[c01]  [c03]/GTA OPM[c01]  [c03]/GTA FOREIGN[c01]\n[c03]/EWORDHUNT[c01]  [c03]/TWORDHUNT[c01]\n[c07]Arena[c01]\n[c03]/TIMED ON[c01]  [c03]/SPEED ON[c01]  [c03]/SURVIVAL ON[c01]  [c03]/SUDDEN DEATH[c01]\n[c03]/DAILY ON[c01]  [c03]/DAILY STATUS[c01]\n[c03]/ROOM CHALLENGE[c01]  [c03]/ROOM CHALLENGE END[c01]\n[c03]/BATTLE @user[c01]  [c03]/BATTLE ACCEPT[c01]  [c03]/BATTLE DECLINE[c01]  [c03]/BATTLE STATUS[c01]\n[c03]/TOURNAMENT[c01]  [c03]/TOURNAMENT JOIN[c01]  [c03]/TOURNAMENT START[c01]  [c03]/TOURNAMENT STATUS[c01]\n[c07]Perya (coming soon)[c01] Color Game - Bingo\nSend answers as normal messages.",
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
