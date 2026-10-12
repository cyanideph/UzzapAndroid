import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "https://deno.land/x/jose@v4.14.4/index.ts";

interface WebhookPayload { type?: "INSERT" | "UPDATE" | "DELETE"; table?: string; schema?: string; record?: Record<string, unknown>; }

async function getAccessToken(clientEmail: string, privateKey: string): Promise<string> {
  const pem = privateKey.replace(/\\n/g, "\n");
  const key = await importPKCS8(pem, "RS256");
  const now = Math.floor(Date.now() / 1000);
  return await new SignJWT({ iss: clientEmail, sub: clientEmail, aud: "https://oauth2.googleapis.com/token", scope: "https://www.googleapis.com/auth/firebase.messaging", iat: now, exp: now + 3600 })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" }).sign(key);
}

serve(async (req) => {
  try {
    if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
    // pg_net triggers call this function without an Authorization header, so
    // verify_jwt must stay false (see supabase/config.toml). Instead require an
    // optional shared webhook secret once configured: triggers add
    // `x-uzzap-webhook: <PUSH_WEBHOOK_SECRET>` and direct callers without it
    // are rejected. Until the secret is deployed this check is a no-op.
    const expectedSecret = Deno.env.get("PUSH_WEBHOOK_SECRET")?.trim();
    if (expectedSecret) {
      const provided = req.headers.get("x-uzzap-webhook")?.trim();
      if (provided !== expectedSecret) {
        return Response.json({ ok: false, error: "unauthorized webhook caller" }, { status: 401 });
      }
    }
    const payload = await req.json() as WebhookPayload;
    if (payload.type && payload.type !== "INSERT") return Response.json({ ok: true, skipped: payload.type });
    const table = payload.table || "direct_messages";
    const record = payload.record || {};
    let recipient = ""; let sender = "Uzzap"; let body = ""; let type = "generic"; const eventId = String(record.id ?? Date.now());
    const data: Record<string, string> = { type: "generic", event_id: eventId };

    if (table === "direct_messages") {
      recipient = String(record.recipient ?? "").trim().toLowerCase(); sender = String(record.sender ?? "Uzzap").trim(); body = String(record.body ?? ""); type = "private_message";
      data.sender = sender; data.body = body; data.created_at = String(record.created_at ?? "");
    } else if (table === "buddy_requests") {
      recipient = String(record.recipient_handle ?? record.recipient ?? "").trim().toLowerCase();
      sender = String(record.requester_handle ?? record.sender ?? "Uzzap").trim(); body = `${sender} wants to be your buddy`; type = "buddy_request";
      data.sender = sender; data.body = body;
    } else if (table === "room_invitations") {
      recipient = String(record.invitee_username ?? "").trim().toLowerCase(); sender = String(record.inviter_username ?? "Uzzap").trim();
      const roomName = String(record.room_name ?? "a chatroom"); body = `${sender} invited you to ${roomName}`; type = "room_invitation";
      data.sender = sender; data.body = body; data.room_name = roomName;
    } else return Response.json({ ok: true, skipped: `unsupported table: ${table}` });

    data.type = type;
    if (!recipient) return Response.json({ ok: true, skipped: "missing recipient" });
    const supabaseUrl = Deno.env.get("SUPABASE_URL"); const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !supabaseServiceKey) throw new Error("Missing Supabase service configuration");
    const supabase = createClient(supabaseUrl, supabaseServiceKey);
    const { data: tokens, error } = await supabase.from("user_fcm_tokens").select("id,fcm_token").eq("username", recipient);
    if (error) throw error;
    if (!tokens?.length) return Response.json({ ok: true, sent: 0, reason: "no registered tokens" });
    const rawCreds = Deno.env.get("FIREBASE_SERVICE_ACCOUNT"); if (!rawCreds) throw new Error("Missing FIREBASE_SERVICE_ACCOUNT");
    const serviceAccount = JSON.parse(rawCreds); if (!serviceAccount.project_id) throw new Error("Firebase service account is missing project_id");
    const accessToken = await getAccessToken(serviceAccount.client_email, serviceAccount.private_key);
    const endpoint = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`;
    let sent = 0; let removed = 0; const failures: string[] = [];
    for (const row of tokens) {
      const response = await fetch(endpoint, { method: "POST", headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" }, body: JSON.stringify({ message: { token: row.fcm_token, data, android: { priority: "high" } } }) });
      const text = await response.text();
      if (response.ok) { sent++; continue; }
      const permanent = response.status === 404 || (response.status === 400 && /UNREGISTERED|INVALID_ARGUMENT/i.test(text));
      if (permanent && row.id) { const { error: deleteError } = await supabase.from("user_fcm_tokens").delete().eq("id", row.id); if (!deleteError) removed++; }
      else failures.push(`FCM ${response.status}: ${text.slice(0, 300)}`);
    }
    return Response.json({ ok: failures.length === 0, type, event_id: eventId, sent, removed, failed: failures.length, failures });
  } catch (err) { console.error("send-push error", err); return Response.json({ ok: false, error: String(err) }, { status: 500 }); }
});
