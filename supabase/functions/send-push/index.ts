import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "https://deno.land/x/jose@v4.14.4/index.ts";

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  schema: string;
  record: {
    id?: number;
    sender?: string;
    recipient?: string;
    body?: string;
    created_at?: string;
  };
}

async function getAccessToken(clientEmail: string, privateKey: string): Promise<string> {
  const pem = privateKey.replace(/\\n/g, "\n");
  const key = await importPKCS8(pem, "RS256");
  const now = Math.floor(Date.now() / 1000);

  return await new SignJWT({
    iss: clientEmail,
    sub: clientEmail,
    aud: "https://oauth2.googleapis.com/token",
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    iat: now,
    exp: now + 3600,
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .sign(key);
}

serve(async (req) => {
  try {
    const raw = await req.json();
    const payload = raw as WebhookPayload;
    const record = payload.record;

    if (!record || !record.recipient || !record.sender) {
      return new Response(JSON.stringify({ message: "No recipient or sender" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // 1. Fetch recipient's active FCM tokens
    const { data: tokens, error } = await supabase
      .from("user_fcm_tokens")
      .select("fcm_token")
      .eq("username", record.recipient.trim().toLowerCase());

    if (error || !tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ message: "No registered tokens for recipient" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 2. Parse Firebase service account credentials
    const rawCreds = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!rawCreds) {
      console.error("Missing FIREBASE_SERVICE_ACCOUNT environment variable");
      return new Response(JSON.stringify({ error: "Missing FIREBASE_SERVICE_ACCOUNT" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    const serviceAccount = JSON.parse(rawCreds);
    const accessToken = await getAccessToken(serviceAccount.client_email, serviceAccount.private_key);

    const projectId = serviceAccount.project_id || "expo-340f1";
    const fcmEndpoint = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

    const results = [];
    for (const row of tokens) {
      const fcmMessage = {
        message: {
          token: row.fcm_token,
          notification: {
            title: `Message from ${record.sender}`,
            body: record.body || "",
          },
          data: {
            sender: record.sender,
            body: record.body || "",
            created_at: record.created_at || "",
          },
          android: {
            priority: "high",
            notification: {
              sound: "alert",
              channel_id: "uzzap_messages_v2",
            },
          },
        },
      };

      const fcmResponse = await fetch(fcmEndpoint, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(fcmMessage),
      });

      const resText = await fcmResponse.text();
      results.push({ token: row.fcm_token, status: fcmResponse.status, result: resText });
    }

    return new Response(JSON.stringify({ success: true, count: results.length, details: results }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("Error sending FCM notification:", err);
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
