import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const db = createClient(supabaseUrl, serviceKey, { auth: { autoRefreshToken: false, persistSession: false } });

const esc = (s: string) => s.replace(/[&<>"']/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;" }[c]!));

const page = (title: string, body: string) => new Response(`<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title} — Uzzap</title>
<style>
body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.6;max-width:850px;margin:0 auto;padding:24px;color:#202124;background:#fff}
h1,h2{line-height:1.25}h1{font-size:32px}h2{margin-top:32px}a{color:#087f23}
.card{padding:18px;border:1px solid #ddd;border-radius:14px;margin:18px 0}
input,button{font:inherit;padding:11px;border-radius:8px;border:1px solid #bbb}
input{width:min(100%,420px)}button{background:#087f23;color:white;border:0;cursor:pointer}
small{color:#666}
</style></head><body>${body}</body></html>`, {headers:{"content-type":"text/html; charset=utf-8"}});

const policy = `
<h1>Uzzap Privacy Policy</h1>
<p><strong>Last updated: September 23, 2026</strong></p>
<p>Uzzap is a social messaging application operated by CY. This Privacy Policy explains how Uzzap handles information when you use the Uzzap Android application (<strong>com.cy.uzzap</strong>).</p>

<h2>1. Information we collect</h2>
<p>Depending on the features you use, Uzzap may process account and authentication information such as your email address, user ID, username, nickname, profile information and account status.</p>
<p>Uzzap also processes content you voluntarily submit, including room messages, private messages, buddy activity, reports and other user-generated content.</p>
<p>Uzzap may process push-notification identifiers needed to deliver notifications to your device.</p>

<h2>2. How we use information</h2>
<ul>
<li>Provide authentication, profiles and messaging.</li>
<li>Operate rooms, buddies, private messages and delivery/read receipts.</li>
<li>Deliver push notifications.</li>
<li>Provide blocking, reporting and moderation.</li>
<li>Operate UzzapBot game and conversational features.</li>
<li>Prevent abuse, fraud and unauthorized access.</li>
<li>Maintain security, troubleshoot problems and provide support.</li>
<li>Comply with applicable legal obligations.</li>
</ul>

<h2>3. Service providers</h2>
<p><strong>Supabase:</strong> Uzzap uses Supabase for authentication, database storage and realtime/backend services.</p>
<p><strong>Firebase Cloud Messaging:</strong> Uzzap uses Firebase Cloud Messaging (FCM) for push notifications. Firebase Analytics collection is disabled in the Uzzap application.</p>
<p><strong>Groq:</strong> When UzzapBot's AI conversational feature is enabled and a message is selected for AI processing, relevant message text may be sent to Groq's API so that the bot can generate a response. Uzzap does not send passwords or authentication credentials to the AI provider as part of this feature.</p>

<h2>4. Chat and public content</h2>
<p>Messages sent to a room may be visible to other members of that room. Private messages are intended for their participants, subject to security, reporting and moderation functionality.</p>
<p>Do not send passwords, authentication credentials, financial information, government identification numbers or other highly sensitive information through chat.</p>

<h2>5. Moderation, blocking and reports</h2>
<p>Uzzap processes information necessary to operate blocking, reporting and moderation features. Reports and moderation records may be retained where reasonably necessary for abuse prevention, security, fraud prevention or legal compliance.</p>

<h2>6. Data sharing</h2>
<p>Uzzap does not sell personal information. Information may be processed by service providers acting on our behalf, including Supabase, Firebase Cloud Messaging and, when the AI feature is used, Groq. Information may also be disclosed when required by law or reasonably necessary to protect users, the service or others.</p>

<h2>7. Security</h2>
<p>Uzzap uses reasonable technical and organizational safeguards and encrypted network connections for supported backend communication. No Internet service can guarantee absolute security.</p>

<h2>8. Retention</h2>
<p>Information is retained only as reasonably necessary to operate Uzzap, provide messaging and moderation, prevent abuse and fraud, maintain security, resolve disputes, and meet applicable legal obligations.</p>

<h2>9. Account and data deletion</h2>
<p>Uzzap provides an in-app account deletion path and this web resource provides an external deletion-request path. When an account is deleted, associated account data is deleted unless specific information must be retained for legitimate security, fraud-prevention or legal reasons. Any retained information is limited to what is reasonably necessary.</p>
<p><a href="/functions/v1/uzzap-privacy?delete=1">Request Uzzap account deletion</a></p>

<h2>10. Children's privacy</h2>
<p>Uzzap is not intended to knowingly collect personal information from children in violation of applicable law. Users must meet applicable minimum-age requirements. Accounts created in violation of applicable age requirements may be restricted or deleted.</p>

<h2>11. Notifications and permissions</h2>
<p>Uzzap may send notifications for messages, buddy activity, security events and other app activity. Android permissions used by the current app include Internet access, network-state information, notifications and vibration.</p>

<h2>12. Changes to this policy</h2>
<p>We may update this Privacy Policy when Uzzap's features, services, legal requirements or data practices change. The last-updated date will be changed when material updates are made.</p>

<h2>13. Privacy contact</h2>
<p>For privacy questions or deletion requests, use the account-deletion form on this page. The request will be reviewed and processed according to this policy.</p>

<p><small>Application: Uzzap · Package: com.cy.uzzap · Developer: CY</small></p>`;

const deletionForm = `
<h1>Delete Your Uzzap Account</h1>
<p><strong>Uzzap</strong> provides this page so you can request account deletion even if you no longer have the Android application installed.</p>
<div class="card">
<h2 style="margin-top:0">Request account deletion</h2>
<p>Enter the email address associated with your Uzzap account. We will use the request to verify and process deletion of the associated account and data.</p>
<form method="post">
<input type="email" name="email" required maxlength="254" autocomplete="email" placeholder="Email address">
<br><br><button type="submit">Request Account Deletion</button>
</form>
</div>
<h2>What will be deleted?</h2>
<p>When the deletion request is verified and completed, Uzzap will delete the account and associated user data, including profile information, buddy data, user notification tokens, user-generated messages and applicable UzzapBot user data.</p>
<p>Certain records may be retained only where reasonably necessary for security, fraud prevention or legal compliance, as described in the <a href="/functions/v1/uzzap-privacy">Uzzap Privacy Policy</a>.</p>
<p><small>This page is an external account-deletion resource for Uzzap users.</small></p>`;

// NOTE: vendored from production (uzzap-privacy v1) for repo completeness.
// The deletion endpoint consumes form-encoded POST bodies (email field),
// not JSON — web clients must match that shape.
Deno.serve(async (req) => {
  const url = new URL(req.url);
  if (req.method === "GET") {
    if (url.searchParams.get("delete") === "1") return page("Delete Account", deletionForm);
    return page("Privacy Policy", policy);
  }
  if (req.method !== "POST") return new Response("Method Not Allowed", {status:405});

  const form = await req.formData().catch(() => null);
  const email = String(form?.get("email") ?? "").trim().toLowerCase();
  if (!email || email.length > 254 || !email.includes("@") || !email.includes(".")) {
    return page("Deletion Request", "<h1>Invalid request</h1><p>Please enter a valid Uzzap account email address.</p><p><a href='?delete=1'>Try again</a></p>");
  }

  const { error } = await db.from("account_deletion_requests").insert({email});
  if (error) {
    return page("Deletion Request", "<h1>Request could not be submitted</h1><p>Please try again later.</p>");
  }
  return page("Deletion Request Received", "<h1>Deletion request received</h1><p>Your Uzzap account-deletion request has been submitted. The request will be verified and processed according to the Uzzap Privacy Policy.</p><p><a href='/functions/v1/uzzap-privacy'>Return to Privacy Policy</a></p>");
});
