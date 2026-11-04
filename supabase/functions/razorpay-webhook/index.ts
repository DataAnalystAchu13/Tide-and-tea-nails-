// Razorpay webhook (events: payment.captured, order.paid). Confirms an order even when the
// customer closed the tab before the browser could. Deployed with verify_jwt = false;
// authenticity comes from the X-Razorpay-Signature HMAC instead.
import { admin, hmacHex, markPaid, safeEqual, secret } from "../_shared/shop-db.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });
  const raw = new Uint8Array(await req.arrayBuffer());
  const key = secret("RAZORPAY_WEBHOOK_SECRET");
  const sig = req.headers.get("x-razorpay-signature") || "";
  if (!key || !safeEqual(sig, await hmacHex(key, raw))) return new Response("bad signature", { status: 400 });

  const ev = JSON.parse(new TextDecoder().decode(raw));
  const pay = ev?.payload?.payment?.entity;
  if (!["payment.captured", "order.paid"].includes(ev?.event) || !pay) return new Response("ignored");

  const { data: o } = await admin().from("orders").select("id, total").eq("razorpay_order_id", pay.order_id).maybeSingle();
  if (!o) return new Response("no order");
  if (Number(pay.amount) !== Number(o.total) * 100) {
    console.error("Amount mismatch", o.id, pay.amount);
    return new Response("amount mismatch");
  }
  await markPaid(o.id, "Razorpay", pay.id);
  return new Response("ok");
});
