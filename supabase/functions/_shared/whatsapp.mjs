// WhatsApp alerts to the business owner, used by the notify-owners Edge Function.
//
// Pure helpers (no network, no Deno APIs) so they run under `node --test` too.
// Providers sit behind one function, so a brand can switch provider by changing secrets only:
//
//   WHATSAPP_PROVIDER   "callmebot" (default when CALLMEBOT_APIKEY is set) or "meta"
//   OWNER_WHATSAPP      owner's WhatsApp number(s), with country code, comma-separated.
//                       A 10-digit Indian number gets 91 added. CallMeBot uses the first one only.
//   CALLMEBOT_APIKEY    CallMeBot: the key CallMeBot sends back on WhatsApp after you allow it
//   WHATSAPP_TOKEN      Meta Cloud API: permanent System User access token
//   WHATSAPP_PHONE_ID   Meta Cloud API: "Phone number ID" of the sending number
//   WHATSAPP_TEMPLATE   Meta Cloud API: approved template with 3 body variables (default "new_order_alert")
//   WHATSAPP_LANG       Meta Cloud API: template language code (default "en")

/** Notification types that go to WhatsApp. Chat messages stay as app pushes only. */
export const WHATSAPP_TYPES = new Set(["booking", "order"]);

/** Digits only, with 91 added to a bare 10-digit Indian mobile number. */
export function normalizeNumber(raw) {
  const d = String(raw ?? "").replace(/\D/g, "").replace(/^0+/, "");
  if (/^[6-9]\d{9}$/.test(d)) return "91" + d;
  return d.length >= 11 && d.length <= 15 ? d : "";
}

/** Reads the WhatsApp settings from an env getter; returns null when WhatsApp is not set up. */
export function whatsappConfig(get) {
  const to = String(get("OWNER_WHATSAPP") ?? "").split(",").map(normalizeNumber).filter(Boolean);
  if (!to.length) return null;
  const apikey = get("CALLMEBOT_APIKEY") ?? "";
  const token = get("WHATSAPP_TOKEN") ?? "";
  const phoneId = get("WHATSAPP_PHONE_ID") ?? "";
  const provider = (get("WHATSAPP_PROVIDER") || (apikey ? "callmebot" : token ? "meta" : "")).toLowerCase();
  if (provider === "callmebot" && apikey) return { provider, to: to.slice(0, 1), apikey };
  if (provider === "meta" && token && phoneId) {
    return {
      provider, to, token, phoneId,
      template: get("WHATSAPP_TEMPLATE") || "new_order_alert",
      lang: get("WHATSAPP_LANG") || "en",
    };
  }
  return null;
}

const oneLine = (s, max) => String(s ?? "").replace(/\s+/g, " ").trim().slice(0, max);
const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");

/**
 * Extra lines for the owner, from the booking or order row (may be null):
 * the client's number as a wa.me link, the address or map pin, and what was asked for.
 */
export function detailLines(type, row) {
  if (!row) return [];
  const lines = [];
  if (type === "booking") {
    const extras = [row.shape, row.length?.name].filter(Boolean).join(", ");
    if (extras) lines.push(`Style: ${oneLine(extras, 80)}`);
    if (row.service_mode === "home") {
      const loc = row.location;
      if (loc?.lat != null && loc?.lng != null) {
        lines.push(`Home visit: ${oneLine(loc.address, 120) || "pin shared"}`);
        lines.push(`Map: https://www.google.com/maps/search/?api=1&query=${loc.lat},${loc.lng}`);
      } else {
        lines.push("Home visit");
      }
    }
    if (row.from_place) lines.push(`From: ${oneLine(row.from_place, 60)}`);
    if (row.notes) lines.push(`Note: ${oneLine(row.notes, 160)}`);
    const n = normalizeNumber(row.phone);
    if (n) lines.push(`Client: https://wa.me/${n}`);
  } else if (type === "order") {
    for (const l of (Array.isArray(row.lines) ? row.lines : []).slice(0, 6)) {
      lines.push(`• ${oneLine(l.name, 60)}${l.size ? ` (${oneLine(l.size, 10)})` : ""} × ${Number(l.qty) || 1}`);
    }
    const c = row.customer ?? {};
    const addr = [c.line1, c.line2, c.city, c.pincode].filter(Boolean).join(", ");
    if (addr) lines.push(`Ship to: ${oneLine(addr, 200)}`);
    if (row.total) lines.push(`Total: ${money(row.total)}`);
    const n = normalizeNumber(c.phone);
    if (n) lines.push(`Client: https://wa.me/${n}`);
  }
  return lines;
}

/** The plain-text WhatsApp message (CallMeBot), built from the push title/body plus details. */
export function alertText(payload, row) {
  const type = payload?.data?.type;
  return [
    `*${oneLine(payload.title, 120)}*`,
    oneLine(payload.body, 240),
    ...detailLines(type, row),
    "Open the Tide & Tea owner app to reply.",
  ].filter(Boolean).join("\n");
}

/**
 * HTTP requests that send one alert, one per recipient: [{ url, init }].
 * Meta template variables cannot hold new lines, so details are joined with " | ".
 */
export function whatsappRequests(cfg, payload, row) {
  if (cfg.provider === "callmebot") {
    const q = new URLSearchParams({ phone: cfg.to[0], text: alertText(payload, row), apikey: cfg.apikey });
    return [{ url: `https://api.callmebot.com/whatsapp.php?${q}`, init: { method: "GET" } }];
  }
  const details = oneLine(detailLines(payload?.data?.type, row).join(" | "), 900) || "-";
  const params = [oneLine(payload.title, 120) || "-", oneLine(payload.body, 240) || "-", details];
  return cfg.to.map((to) => ({
    url: `https://graph.facebook.com/v21.0/${encodeURIComponent(cfg.phoneId)}/messages`,
    init: {
      method: "POST",
      headers: { Authorization: `Bearer ${cfg.token}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        messaging_product: "whatsapp",
        to,
        type: "template",
        template: {
          name: cfg.template,
          language: { code: cfg.lang },
          components: [{ type: "body", parameters: params.map((text) => ({ type: "text", text })) }],
        },
      }),
    },
  }));
}
