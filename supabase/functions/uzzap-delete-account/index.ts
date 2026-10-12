import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const publishableKey =
  Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ??
  Deno.env.get("SUPABASE_ANON_KEY") ??
  "";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Content-Type": "application/json; charset=utf-8",
};

// NOTE: vendored from production (uzzap-delete-account v1, verify_jwt=true)
// for repo completeness. Forwards the caller's JWT so delete_my_account()
// resolves auth.uid() to the requesting account.
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "method_not_allowed" }), {
      status: 405,
      headers: cors,
    });
  }

  const authorization = req.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) {
    return new Response(JSON.stringify({ error: "missing_authorization" }), {
      status: 401,
      headers: cors,
    });
  }

  if (!publishableKey) {
    return new Response(JSON.stringify({ error: "server_not_configured" }), {
      status: 500,
      headers: cors,
    });
  }

  // The platform validates the JWT because verify_jwt=true.
  // Forward the caller's JWT to PostgREST so auth.uid() resolves to
  // exactly the authenticated account that requested deletion.
  const db = createClient(supabaseUrl, publishableKey, {
    auth: { autoRefreshToken: false, persistSession: false },
    global: { headers: { Authorization: authorization } },
  });

  const { error } = await db.rpc("delete_my_account");

  if (error) {
    console.error("delete_my_account failed", {
      code: error.code,
      message: error.message,
    });

    return new Response(
      JSON.stringify({
        error: "account_deletion_failed",
        code: error.code ?? null,
      }),
      { status: 400, headers: cors },
    );
  }

  return new Response(JSON.stringify({ ok: true }), {
    status: 200,
    headers: cors,
  });
});
