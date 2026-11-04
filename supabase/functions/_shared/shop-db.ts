// Shared helpers for the shop edge functions.
import { createClient, SupabaseClient } from "npm:@supabase/supabase-js@2.45.4";

export const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export class UserError extends Error {
  constructor(message: string, public status = 400) { super(message); }
}

export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "content-type": "application/json" } });

/** Service-role client: bypasses RLS. Only ever used inside these functions. */
export const admin = (): SupabaseClient =>
  createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

/** The signed-in caller (customers sign in anonymously), or null. */
export async function caller(req: Request) {
  const token = (req.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data, error } = await admin().auth.getUser(token);
  if (error || !data.user) return null;
  return data.user;
}

export async function isOwner(uid: string) {
  const { data } = await admin().from("owners").select("user_id").eq("user_id", uid).maybeSingle();
  return !!data;
}

/** Secrets that were never filled in are stored as "unset" by the deploy workflow. */
export const secret = (name: string) => {
  const v = (Deno.env.get(name) || "").trim();
  return v && v !== "unset" ? v : "";
};

export async function hmacHex(key: string, message: string | Uint8Array) {
  const k = await crypto.subtle.importKey("raw", new TextEncoder().encode(key), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", k, typeof message === "string" ? new TextEncoder().encode(message) : message);
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function safeEqual(a: string, b: string) {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

/** Database rows → the shape pricing.mjs expects. */
export const toProduct = (r: Record<string, any>) => ({
  id: r.id, name: r.name, price: r.price, salePrice: r.sale_price, stock: r.stock, madeToOrder: r.made_to_order,
  visible: r.visible, sizes: r.sizes || [], excludeFromSale: r.exclude_from_sale,
});
export const toCoupon = (r: Record<string, any> | null) => r && ({
  code: r.code, type: r.type, value: r.value, minOrder: r.min_order, maxDiscount: r.max_discount,
  maxUses: r.max_uses, uses: r.uses, expiresAt: r.expires_at, active: r.active,
});

/** Calls mark_order_paid (stock, coupon, payment record) and returns its result. */
export async function markPaid(orderId: string, method: string, ref: string) {
  const { data, error } = await admin().rpc("mark_order_paid", { p_order: orderId, p_method: method, p_ref: ref });
  if (error) throw error;
  return data as { already: boolean; order: Record<string, any> };
}
