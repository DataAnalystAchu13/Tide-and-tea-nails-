// Press-on shop API. One function, several actions:
//   quote        bag totals with the coupon checked on the server
//   create       create an order (Razorpay order when keys are set, otherwise UPI)
//   verify       confirm a Razorpay payment from the browser (signature check)
//   submit_upi   customer adds their UPI transaction reference
//   confirm      owner confirms a UPI payment from the app
// Prices always come from the database, never from the browser.
import { priceOrder } from "../_shared/pricing.mjs";
import { admin, caller, cors, hmacHex, isOwner, json, markPaid, safeEqual, secret, toCoupon, toProduct, UserError } from "../_shared/shop-db.ts";

const str = (v: unknown, max: number) => String(v ?? "").trim().slice(0, max);
const uuidRe = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function cleanItems(raw: unknown) {
  if (!Array.isArray(raw) || raw.length === 0) throw new UserError("Your bag is empty.");
  if (raw.length > 30) throw new UserError("Too many items in one order.");
  return raw
    .map((i: any) => ({ productId: str(i?.productId, 40), size: str(i?.size, 20), qty: Math.floor(Number(i?.qty) || 1) }))
    .filter((i) => uuidRe.test(i.productId));
}

function cleanCustomer(c: any = {}) {
  const phone = String(c.phone || "").replace(/\D/g, "").replace(/^91(?=\d{10}$)/, "");
  const out = {
    name: str(c.name, 80), phone, email: str(c.email, 120), line1: str(c.line1, 160), line2: str(c.line2, 160),
    city: str(c.city, 60), state: str(c.state, 60), pincode: str(c.pincode, 6),
  };
  if (out.name.length < 2) throw new UserError("Add your name.");
  if (!/^[6-9]\d{9}$/.test(out.phone)) throw new UserError("Add a 10-digit mobile number.");
  if (out.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(out.email)) throw new UserError("Check your email address.");
  if (out.line1.length < 5 || !out.city) throw new UserError("Add your full address.");
  if (!/^[1-9]\d{5}$/.test(out.pincode)) throw new UserError("Add a 6-digit pincode.");
  return out;
}

async function price(items: ReturnType<typeof cleanItems>, couponRaw: unknown) {
  const db = admin();
  const code = str(couponRaw, 30).toUpperCase().replace(/\s+/g, "");
  const ids = [...new Set(items.map((i) => i.productId))];
  const [p, s, c] = await Promise.all([
    ids.length ? db.from("products").select("*").in("id", ids) : Promise.resolve({ data: [], error: null }),
    db.from("site_settings").select("data").eq("id", 1).maybeSingle(),
    code ? db.from("coupons").select("*").eq("code", code).maybeSingle() : Promise.resolve({ data: null, error: null }),
  ]);
  if (p.error) throw p.error;
  const products = Object.fromEntries((p.data || []).map((r: any) => [r.id, toProduct(r)]));
  const site = (s.data?.data as any) || {};
  return { q: priceOrder({ items, products, site, coupon: toCoupon(c.data), couponCode: code }), products };
}

async function razorpay(path: string, body: unknown) {
  const auth = btoa(`${secret("RAZORPAY_KEY_ID")}:${secret("RAZORPAY_KEY_SECRET")}`);
  const res = await fetch(`https://api.razorpay.com/v1/${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Basic ${auth}` },
    body: JSON.stringify(body),
  });
  const out = await res.json().catch(() => ({}));
  if (!res.ok) {
    console.error("Razorpay error", res.status, out);
    throw new UserError("The payment service is busy. Please try again in a minute.", 503);
  }
  return out;
}

const code6 = () => [...crypto.getRandomValues(new Uint8Array(6))].map((b) => "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[b % 32]).join("");

async function handle(req: Request) {
  const body = await req.json().catch(() => ({}));
  const action = String(body.action || "");
  const user = await caller(req);
  const db = admin();

  if (action === "quote") {
    const { q } = await price(cleanItems(body.items), body.coupon);
    return { subtotal: q.subtotal, discount: q.discount, coupon: q.coupon, couponError: q.couponError, shipping: q.shipping, total: q.total };
  }

  if (!user) throw new UserError("Please refresh the page and try again.", 401);

  if (action === "create") {
    const items = cleanItems(body.items);
    const customer = cleanCustomer(body.customer);
    const notes = str(body.notes, 600);

    const since = new Date(Date.now() - 3600_000).toISOString();
    const { count } = await db.from("orders").select("id", { count: "exact", head: true })
      .eq("customer_uid", user.id).eq("status", "pending_payment").gt("created_at", since);
    if ((count || 0) >= 5) throw new UserError("Too many unpaid orders. Please pay one from My orders first.", 429);

    const { q, products } = await price(items, body.coupon);
    if (q.errors.length) throw new UserError(`${products[q.errors[0].productId]?.name || "An item"}: ${q.errors[0].reason}`, 409);
    if (q.couponError) throw new UserError(q.couponError, 409);
    if (q.total < 1) throw new UserError("Your order total is zero.");

    const { data: cfg } = await db.from("config").select("data").eq("id", "main").maybeSingle();
    const cfgData: any = (cfg as any)?.data ?? {};
    const useRazorpay = !!(secret("RAZORPAY_KEY_ID") && secret("RAZORPAY_KEY_SECRET"));
    const id = crypto.randomUUID();
    const order: Record<string, unknown> = {
      id, code: code6(), customer_uid: user.id, status: "pending_payment", mode: useRazorpay ? "razorpay" : "upi",
      customer, notes, lines: q.lines, subtotal: q.subtotal, discount: q.discount, coupon: q.coupon, shipping: q.shipping, total: q.total,
      upi: { id: cfgData.upiId || "", name: cfgData.upiName || "Tide and Tea Nails" },
    };
    if (useRazorpay) {
      const rz = await razorpay("orders", { amount: q.total * 100, currency: "INR", receipt: id, notes: { orderId: id, code: order.code } });
      order.razorpay_order_id = rz.id;
      order.razorpay_key_id = secret("RAZORPAY_KEY_ID");
    }
    const { error } = await db.from("orders").insert(order);
    if (error) throw error;
    return {
      orderId: id, code: order.code, mode: order.mode, total: q.total,
      ...(useRazorpay ? { keyId: order.razorpay_key_id, razorpayOrderId: order.razorpay_order_id, amountPaise: q.total * 100 } : {}),
    };
  }

  if (action === "verify") {
    const orderId = str(body.orderId, 40);
    const { data: o } = await db.from("orders").select("id, customer_uid, razorpay_order_id").eq("id", orderId).maybeSingle();
    if (!o || o.customer_uid !== user.id) throw new UserError("Order not found.", 404);
    const expected = await hmacHex(secret("RAZORPAY_KEY_SECRET"), `${o.razorpay_order_id}|${str(body.razorpay_payment_id, 60)}`);
    if (!secret("RAZORPAY_KEY_SECRET") || !safeEqual(str(body.razorpay_signature, 128), expected)) {
      throw new UserError("We couldn't verify that payment. If money was taken, message us with your order number.", 403);
    }
    await markPaid(orderId, "Razorpay", str(body.razorpay_payment_id, 60));
    return { ok: true };
  }

  if (action === "submit_upi") {
    const orderId = str(body.orderId, 40);
    const ref = str(body.ref, 40);
    if (ref.replace(/\D/g, "").length < 6) throw new UserError("Enter the transaction ID from your UPI app.");
    const { data, error } = await db.from("orders")
      .update({ status: "payment_submitted", payment_ref: ref, payment_submitted_at: new Date().toISOString() })
      .eq("id", orderId).eq("customer_uid", user.id).eq("status", "pending_payment").eq("mode", "upi")
      .select("id");
    if (error) throw error;
    if (!data || !data.length) throw new UserError("This order isn't waiting for a UPI payment.", 409);
    return { ok: true };
  }

  if (action === "confirm") {
    if (!(await isOwner(user.id))) throw new UserError("Only the studio can do this.", 403);
    const orderId = str(body.orderId, 40);
    const { data: o } = await db.from("orders").select("payment_ref").eq("id", orderId).maybeSingle();
    if (!o) throw new UserError("Order not found.", 404);
    const r = await markPaid(orderId, str(body.method, 20) || "UPI", o.payment_ref || "");
    return { ok: true, already: r.already };
  }

  throw new UserError("Unknown action.");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);
  try {
    return json(await handle(req));
  } catch (e) {
    if (e instanceof UserError) return json({ error: e.message }, e.status);
    console.error(e);
    return json({ error: "Something went wrong. Please try again." }, 500);
  }
});
