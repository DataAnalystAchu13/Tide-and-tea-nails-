// Sends a push notification to every phone signed in to the Tide & Tea owner app,
// and a WhatsApp message to the owner for new bookings and orders (see _shared/whatsapp.mjs).
//
// Called only by the database (public.notify_owners, via pg_net) with the shared secret
// generated in private.app_settings. Deploy with --no-verify-jwt; the secret is the auth.
//
// Edge Function secrets (each part is skipped when its secrets are missing):
//   FCM_SERVICE_ACCOUNT  the JSON key of a Firebase service account
//                        (Firebase console > Project settings > Service accounts > Generate new private key)
//   OWNER_WHATSAPP + CALLMEBOT_APIKEY (or the Meta Cloud API ones)  WhatsApp alerts, see whatsapp.mjs
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase automatically.

import { createClient } from "npm:@supabase/supabase-js@2.45.4";
import { WHATSAPP_TYPES, whatsappConfig, whatsappRequests } from "../_shared/whatsapp.mjs";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

interface NotifyPayload {
  title: string;
  body: string;
  data?: Record<string, unknown>;
}

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);

let cachedToken: { value: string; expiresAt: number } | null = null;

const b64url = (bytes: Uint8Array | string) =>
  btoa(typeof bytes === "string" ? bytes : String.fromCharCode(...bytes))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

/** OAuth access token for FCM HTTP v1, from the service account (signed JWT grant). Cached ~55 min. */
async function accessToken(sa: ServiceAccount): Promise<string> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value;
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claims}.${b64url(sig)}`,
    }),
  });
  if (!res.ok) throw new Error(`Google token request failed: ${res.status} ${await res.text()}`);
  const json = await res.json();
  cachedToken = { value: json.access_token, expiresAt: Date.now() + (json.expires_in ?? 3600) * 1000 };
  return cachedToken.value;
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "POST only" });

  const secret = req.headers.get("x-notify-secret") ?? "";
  const { data: allowed, error: authErr } = await supabase.rpc("check_notify_secret", { p_secret: secret });
  if (authErr || allowed !== true) return json(401, { error: "unauthorized" });

  let payload: NotifyPayload;
  try {
    payload = await req.json();
  } catch {
    return json(400, { error: "invalid JSON" });
  }
  if (!payload?.title) return json(400, { error: "title required" });

  const [push, whatsapp] = await Promise.all([
    sendPush(payload).catch((e) => ({ error: String(e) })),
    sendWhatsApp(payload).catch((e) => ({ error: String(e) })),
  ]);
  return json(200, { push, whatsapp });
});

/** FCM push to every owner phone in push_tokens. */
async function sendPush(payload: NotifyPayload) {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) {
    console.warn("FCM_SERVICE_ACCOUNT is not set; skipping push");
    return { sent: 0, skipped: "FCM_SERVICE_ACCOUNT not set" };
  }
  const sa: ServiceAccount = JSON.parse(raw);

  const { data: rows, error } = await supabase.from("push_tokens").select("token");
  if (error) throw new Error(error.message);
  const tokens = (rows ?? []).map((r) => r.token as string);
  if (!tokens.length) return { sent: 0 };

  // FCM data values must be strings.
  const data = Object.fromEntries(
    Object.entries(payload.data ?? {}).map(([k, v]) => [k, typeof v === "string" ? v : JSON.stringify(v)]),
  );
  const bearer = await accessToken(sa);
  const endpoint = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;

  const dead: string[] = [];
  let sent = 0;
  await Promise.all(tokens.map(async (token) => {
    const res = await fetch(endpoint, {
      method: "POST",
      headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          notification: { title: payload.title, body: payload.body ?? "" },
          data,
          android: { priority: "HIGH", notification: { channel_id: "requests", color: "#3A1D5C" } },
        },
      }),
    });
    if (res.ok) {
      sent++;
      return;
    }
    const text = await res.text();
    // Phone uninstalled the app or the token rotated: forget it.
    if (res.status === 404 || text.includes("UNREGISTERED") || (text.includes("INVALID_ARGUMENT") && text.includes("token"))) {
      dead.push(token);
    } else {
      console.error(`FCM send failed (${res.status}): ${text}`);
    }
  }));

  if (dead.length) await supabase.from("push_tokens").delete().in("token", dead);
  return { sent, removed: dead.length };
}

/** WhatsApp message to the owner for bookings and orders, with the client's details. */
async function sendWhatsApp(payload: NotifyPayload) {
  const type = String(payload.data?.type ?? "");
  if (!WHATSAPP_TYPES.has(type)) return { skipped: "not a booking or order" };
  const cfg = whatsappConfig((k: string) => Deno.env.get(k));
  if (!cfg) return { skipped: "WhatsApp is not set up" };

  const id = String(payload.data?.bookingId ?? "");
  let row = null;
  if (id) {
    const { data } = await supabase.from(type === "order" ? "orders" : "bookings").select("*").eq("id", id).maybeSingle();
    row = data;
  }
  let sent = 0;
  for (const { url, init } of whatsappRequests(cfg, payload, row)) {
    const res = await fetch(url, { ...init, signal: AbortSignal.timeout(10_000) });
    const text = await res.text();
    // CallMeBot answers 200 with an HTML page even for some errors; look for its error words too.
    const failed = !res.ok || (cfg.provider === "callmebot" && /APIKey is invalid|ERROR/i.test(text));
    if (!failed) sent++;
    else console.error(`WhatsApp send failed (${cfg.provider} ${res.status}): ${text.slice(0, 300)}`);
  }
  return { provider: cfg.provider, sent };
}
