// Press-on shop: listing filters, product page, bag and checkout, and "My orders".
import { $, $$, esc, money, cart, toast, liveShop, getShopData, backend, errText, loadScript, PREVIEW, store, u } from "/lib.js";
import { priceOrder, displayPrice } from "/pricing.mjs";

const page = document.body.querySelector("#pgrid") ? "list"
  : document.body.querySelector("#buyForm") ? "product"
  : document.body.querySelector("#cartApp") ? "cart"
  : document.body.querySelector("#ordersApp") ? "orders" : "";

/* ================= Listing ================= */
function paintCard(el, p, site) {
  if (!p) return;
  if (p.visible === false) { el.hidden = true; return; }
  const { price, unit, percentOff } = displayPrice(p, site);
  const sold = !p.madeToOrder && !(Number(p.stock) > 0);
  const b = el.querySelector("[data-badge]"); if (b) { b.hidden = !percentOff; b.textContent = `${percentOff}% off`; }
  const s = el.querySelector("[data-soldout]"); if (s) s.hidden = !sold;
  const u = el.querySelector("[data-unit]"); if (u) u.textContent = money(unit);
  const w = el.querySelector("[data-was]"); if (w) { w.hidden = !percentOff; w.textContent = money(price); }
}

async function initList() {
  const cards = $$(".pcard[data-pid]");
  const filters = $("#filters");
  const apply = () => {
    const shape = filters?.querySelector('input[name="shape"]:checked')?.value || "";
    const length = filters?.querySelector('input[name="length"]:checked')?.value || "";
    let shown = 0;
    cards.forEach((c) => {
      const ok = (!shape || c.dataset.shape === shape) && (!length || c.dataset.length === length) && !c.dataset.gone;
      c.hidden = !ok; if (ok) shown++;
    });
    const nm = $("#noMatch"); if (nm) nm.hidden = shown > 0;
  };
  filters?.addEventListener("change", apply);
  const { products, site } = await liveShop(cards.map((c) => c.dataset.pid));
  cards.forEach((c) => { const p = products[c.dataset.pid]; paintCard(c, p, site); if (p && p.visible === false) c.dataset.gone = "1"; });
  apply();
}

/* ================= Product page ================= */
async function initProduct() {
  const form = $("#buyForm");
  let p = JSON.parse(form.dataset.product);
  const err = $("#buyError");
  const qty = form.qty;

  $$(".gal-thumbs button").forEach((b) => b.addEventListener("click", () => {
    const main = $(".gal-main img");
    if (main) main.src = b.dataset.img;
    $$(".gal-thumbs button").forEach((x) => x.removeAttribute("aria-current"));
    b.setAttribute("aria-current", "true");
  }));
  form.addEventListener("click", (e) => {
    const s = e.target.closest("[data-step]"); if (!s) return;
    qty.value = Math.max(1, Math.min(10, (Number(qty.value) || 1) + Number(s.dataset.step)));
  });
  form.addEventListener("change", () => { err.hidden = true; });
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const size = form.querySelector('input[name="size"]:checked')?.value || "";
    if (p.sizes.length && !size) { err.textContent = "Pick a size first."; err.hidden = false; return; }
    const n = Math.max(1, Math.min(10, Number(qty.value) || 1));
    if (!p.madeToOrder && n > Number(p.stock)) { err.textContent = Number(p.stock) > 0 ? `Only ${p.stock} left.` : "Sold out."; err.hidden = false; return; }
    cart.add({ id: p.id, slug: p.slug, name: p.name, image: p.image, size, qty: n });
    toast(`Added to your bag. <a href="${u("/cart/")}">View bag</a>`);
  });

  // Refresh price and stock from the live catalogue.
  const { products, site } = await liveShop([p.id]);
  const live = products[p.id];
  if (!live) return;
  p = { ...p, ...live };
  const box = $(".product");
  paintCard(box, p, site);
  const add = form.querySelector("[data-add]");
  const sold = !p.madeToOrder && !(Number(p.stock) > 0);
  if (p.visible === false) { add.disabled = true; add.textContent = "No longer available"; }
  else { add.disabled = sold; add.textContent = sold ? "Sold out" : "Add to bag"; }
  const st = $("[data-stock]");
  if (st) st.textContent = p.madeToOrder ? "Made to order, ships in 5 to 7 working days" : sold ? "Sold out" : Number(p.stock) <= 3 ? `Only ${Number(p.stock)} left` : "In stock";
}

/* ================= Bag and checkout ================= */
const ADDR = "tt.address";
const STATES = ["Tamil Nadu", "Andhra Pradesh", "Karnataka", "Kerala", "Puducherry", "Telangana", "Maharashtra", "Delhi", "Gujarat", "West Bengal", "Uttar Pradesh", "Rajasthan", "Madhya Pradesh", "Punjab", "Haryana", "Bihar", "Odisha", "Assam", "Goa", "Jharkhand", "Chhattisgarh", "Uttarakhand", "Himachal Pradesh", "Jammu and Kashmir", "Tripura", "Meghalaya", "Manipur", "Mizoram", "Nagaland", "Arunachal Pradesh", "Sikkim", "Chandigarh", "Ladakh", "Andaman and Nicobar Islands", "Dadra and Nagar Haveli and Daman and Diu", "Lakshadweep"];

const co = { live: null, coupon: "", quote: null, busy: false };

async function initCart() {
  co.live = await liveShop(cart.items().map((i) => i.id));
  co.coupon = store.get("tt.coupon", "");
  renderCart();
  window.addEventListener("cart", renderCart);
  if (co.coupon) refreshQuote();
}

function localQuote() {
  const items = cart.items().map((i) => ({ productId: i.id, size: i.size, qty: i.qty }));
  const demo = co.live.data.demoCoupons || [];
  const coupon = PREVIEW ? demo.find((c) => c.code === co.coupon.toUpperCase()) || null : null;
  const q = priceOrder({ items, products: co.live.products, site: co.live.site, coupon, couponCode: PREVIEW ? co.coupon : "" });
  // Live coupons are checked on the server; use its answer for the discount.
  if (!PREVIEW && co.quote && co.coupon) {
    q.discount = co.quote.discount; q.coupon = co.quote.coupon; q.couponError = co.quote.couponError;
    const ship = co.live.site.shipping || {};
    const after = q.subtotal - q.discount;
    q.shipping = q.lines.length === 0 ? 0 : Number(ship.freeAbove) > 0 && after >= Number(ship.freeAbove) ? 0 : Number(ship.flat) || 0;
    q.total = after + q.shipping;
  }
  return q;
}

let quoteT;
function refreshQuote() {
  if (PREVIEW || !co.coupon) { co.quote = null; renderCart(); return; }
  clearTimeout(quoteT);
  quoteT = setTimeout(async () => {
    try {
      const fb = await backend();
      co.quote = await fb.call("quote", { items: cart.items().map((i) => ({ productId: i.id, size: i.size, qty: i.qty })), coupon: co.coupon });
    } catch (e) { console.warn(e); co.quote = { discount: 0, coupon: "", couponError: "Couldn't check that code right now." }; }
    renderCart();
  }, 250);
}

function renderCart() {
  const app = $("#cartApp");
  const items = cart.items();
  if (!items.length) {
    app.innerHTML = `<div class="empty card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><h2>Your bag is empty</h2><p>Find a set you love.</p><a class="btn btn-primary" href="${u("/shop/")}">Shop press-ons</a></div>`;
    return;
  }
  const q = localQuote();
  const errs = new Map(q.errors.map((e) => [e.productId, e.reason]));
  const lineFor = (i) => q.lines.find((l) => l.productId === i.id && (l.size || "") === (i.size || ""));
  const saved = store.get(ADDR, {});
  const formOpen = !!$("#coForm");
  const keep = formOpen ? Object.fromEntries(new FormData($("#coForm"))) : saved;

  app.innerHTML = `
  <div class="cart-grid">
    <div class="cart-lines card">
      ${items.map((i, ix) => {
        const l = lineFor(i);
        const e = errs.get(i.id);
        return `<div class="cline${e ? " bad" : ""}">
          <a class="cline-img" href="${u(`/shop/${esc(i.slug)}/`)}">${i.image ? `<img src="${esc(i.image)}" alt="" loading="lazy">` : ""}</a>
          <div class="cline-body">
            <a href="${u(`/shop/${esc(i.slug)}/`)}"><b>${esc(i.name)}</b></a>
            ${i.size ? `<span class="muted">Size ${esc(i.size)}</span>` : ""}
            ${e ? `<span class="form-error">${esc(e)}</span>` : l ? `<span>${money(l.unit)}${l.unit < l.price ? ` <s class="muted">${money(l.price)}</s>` : ""}</span>` : ""}
            <div class="qty sm"><button type="button" data-q="${ix}" data-d="-1" aria-label="One less">−</button><output>${i.qty}</output><button type="button" data-q="${ix}" data-d="1" aria-label="One more">+</button></div>
          </div>
          <div class="cline-amt">${l ? money(l.amount) : ""}<button type="button" class="linkbtn" data-rm="${ix}">Remove</button></div>
        </div>`;
      }).join("")}
    </div>
    <aside class="cart-sum card">
      <form class="coupon" id="couponForm">
        <label class="sr" for="couponIn">Coupon code</label>
        <input id="couponIn" name="code" placeholder="Coupon code" value="${esc(co.coupon)}" autocomplete="off" autocapitalize="characters">
        <button class="btn btn-quiet btn-sm">${co.coupon ? "Update" : "Apply"}</button>
      </form>
      ${q.couponError ? `<p class="form-error">${esc(q.couponError)}</p>` : q.coupon ? `<p class="ok-note">Code ${esc(q.coupon)} applied <button type="button" class="linkbtn" id="couponRm">Remove</button></p>` : ""}
      <div class="summary">
        <div class="sumrow"><span>Subtotal</span><span>${money(q.subtotal)}</span></div>
        ${q.discount ? `<div class="sumrow"><span>Coupon</span><span>−${money(q.discount)}</span></div>` : ""}
        <div class="sumrow"><span>Shipping</span><span>${q.shipping ? money(q.shipping) : "Free"}</span></div>
        <div class="sumrow total"><span>Total</span><span>${money(q.total)}</span></div>
        ${q.savings ? `<p class="saving">You save ${money(q.savings)}</p>` : ""}
        ${co.live.site.shipping.freeAbove && q.shipping ? `<p class="muted small">Add ${money(Number(co.live.site.shipping.freeAbove) - (q.subtotal - q.discount))} more for free shipping.</p>` : ""}
      </div>
    </aside>
  </div>

  <form class="card checkout" id="coForm" novalidate>
    <h2>Delivery details</h2>
    <div class="fields">
      <div class="field"><label for="f-name">Full name</label><input id="f-name" name="name" autocomplete="name" required maxlength="80" value="${esc(keep.name || "")}"></div>
      <div class="field"><label for="f-phone">Phone</label><input id="f-phone" name="phone" type="tel" inputmode="tel" autocomplete="tel" required maxlength="15" value="${esc(keep.phone || "")}" placeholder="10-digit mobile"></div>
      <div class="field wide"><label for="f-email">Email <span class="opt">for your receipt</span></label><input id="f-email" name="email" type="email" autocomplete="email" maxlength="120" value="${esc(keep.email || "")}"></div>
      <div class="field wide"><label for="f-l1">Address</label><input id="f-l1" name="line1" autocomplete="address-line1" required maxlength="160" value="${esc(keep.line1 || "")}" placeholder="House, street"></div>
      <div class="field wide"><label for="f-l2" class="sr">Address line 2</label><input id="f-l2" name="line2" autocomplete="address-line2" maxlength="160" value="${esc(keep.line2 || "")}" placeholder="Area, landmark (optional)"></div>
      <div class="field"><label for="f-city">City</label><input id="f-city" name="city" autocomplete="address-level2" required maxlength="60" value="${esc(keep.city || "Chennai")}"></div>
      <div class="field"><label for="f-pin">Pincode</label><input id="f-pin" name="pincode" inputmode="numeric" autocomplete="postal-code" required maxlength="6" value="${esc(keep.pincode || "")}"></div>
      <div class="field wide"><label for="f-state">State</label><select id="f-state" name="state" autocomplete="address-level1">${STATES.map((s) => `<option${(keep.state || "Tamil Nadu") === s ? " selected" : ""}>${s}</option>`).join("")}</select></div>
      <div class="field wide"><label for="f-notes">Notes <span class="opt">nail sizes for custom sets, gift message</span></label><textarea id="f-notes" name="notes" maxlength="600">${esc(keep.notes || "")}</textarea></div>
    </div>
    <p class="form-error" id="coError" role="alert" hidden></p>
    <div class="co-foot">
      <p class="muted small">By placing this order you agree to our <a href="${u("/policies/")}">shipping and returns policy</a>.</p>
      <button class="btn btn-primary" id="payBtn" ${q.lines.length === 0 || q.errors.length ? "disabled" : ""}>Pay ${money(q.total)}</button>
    </div>
  </form>`;
  bindCart(q);
}

function bindCart(q) {
  const app = $("#cartApp");
  app.querySelectorAll("[data-q]").forEach((b) => b.addEventListener("click", () => {
    const i = Number(b.dataset.q); const it = cart.items()[i];
    cart.setQty(i, it.qty + Number(b.dataset.d));
    if (co.coupon) refreshQuote();
  }));
  app.querySelectorAll("[data-rm]").forEach((b) => b.addEventListener("click", () => { cart.setQty(Number(b.dataset.rm), 0); if (co.coupon) refreshQuote(); }));
  $("#couponForm").addEventListener("submit", (e) => {
    e.preventDefault();
    co.coupon = e.target.code.value.trim().toUpperCase().replace(/\s+/g, "");
    store.set("tt.coupon", co.coupon);
    co.quote = null;
    if (PREVIEW) renderCart(); else { renderCart(); refreshQuote(); }
  });
  $("#couponRm")?.addEventListener("click", () => { co.coupon = ""; co.quote = null; store.set("tt.coupon", ""); renderCart(); });
  $("#coForm").addEventListener("input", (e) => { const f = e.currentTarget; store.set(ADDR, Object.fromEntries(new FormData(f))); $("#coError").hidden = true; });
  $("#coForm").addEventListener("submit", (e) => { e.preventDefault(); checkout(q); });
}

function readCustomer() {
  const f = Object.fromEntries(new FormData($("#coForm")));
  const t = (k) => String(f[k] || "").trim();
  const phone = t("phone").replace(/[^\d+]/g, "").replace(/^\+?91(?=\d{10}$)/, "");
  if (t("name").length < 2) return { error: "Add your name." };
  if (!/^[6-9]\d{9}$/.test(phone)) return { error: "Add a 10-digit mobile number." };
  if (t("email") && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(t("email"))) return { error: "Check your email address." };
  if (t("line1").length < 5) return { error: "Add your full address." };
  if (!t("city")) return { error: "Add your city." };
  if (!/^[1-9]\d{5}$/.test(t("pincode"))) return { error: "Add a 6-digit pincode." };
  return { customer: { name: t("name"), phone, email: t("email"), line1: t("line1"), line2: t("line2"), city: t("city"), state: t("state"), pincode: t("pincode") }, notes: t("notes") };
}

function coError(m) { const e = $("#coError"); e.textContent = m; e.hidden = false; e.scrollIntoView({ block: "center", behavior: "smooth" }); }

async function checkout(q) {
  if (co.busy) return;
  const r = readCustomer();
  if (r.error) return coError(r.error);
  const btn = $("#payBtn");
  co.busy = true; btn.disabled = true; btn.textContent = "Placing your order…";
  const items = cart.items().map((i) => ({ productId: i.id, size: i.size || "", qty: i.qty }));
  try {
    if (PREVIEW) {
      const id = "PV" + Date.now().toString(36).toUpperCase();
      const orders = store.get("tt.preview.orders", []);
      orders.unshift({ id, code: id.slice(-6), status: "pending_payment", mode: "upi", createdAt: Date.now(), customer: r.customer, notes: r.notes, lines: q.lines, subtotal: q.subtotal, discount: q.discount, coupon: q.coupon, shipping: q.shipping, total: q.total });
      store.set("tt.preview.orders", orders);
      store.set("tt.coupon", "");
      cart.clear();
      location.href = u(`/orders/?placed=${id}`);
      return;
    }
    const fb = await backend();
    const res = await fb.call("create", { items, coupon: co.coupon, customer: r.customer, notes: r.notes });
    store.set("tt.coupon", "");
    if (res.mode === "razorpay") {
      await payRazorpay(fb, res, r.customer);
    } else {
      cart.clear();
      location.href = u(`/orders/?placed=${res.orderId}`);
    }
  } catch (e) {
    console.error(e);
    coError(errText(e, "We couldn't place your order. Please try again."));
  } finally {
    co.busy = false;
    const b = $("#payBtn"); if (b) { b.disabled = false; b.textContent = `Pay ${money(localQuote().total)}`; }
  }
}

/** Opens Razorpay Checkout for an order and confirms the payment on the server. */
async function payRazorpay(fb, o, customer = {}) {
  await loadScript("https://checkout.razorpay.com/v1/checkout.js");
  return new Promise((resolve) => {
    const rzp = new window.Razorpay({
      key: o.keyId,
      amount: o.amountPaise,
      currency: "INR",
      order_id: o.razorpayOrderId,
      name: "Tide & Tea Nails",
      description: `Order ${o.code}`,
      image: location.origin + "/icon-192.png",
      prefill: { name: customer.name || "", email: customer.email || "", contact: customer.phone ? "+91" + customer.phone : "" },
      notes: { orderId: o.orderId },
      theme: { color: "#3a1d5c" },
      handler: async (resp) => {
        try {
          await fb.call("verify", { orderId: o.orderId, ...resp });
        } catch (e) { console.error(e); } // the webhook confirms it anyway; the orders page shows the result
        cart.clear();
        location.href = u(`/orders/?placed=${o.orderId}`);
        resolve();
      },
      modal: {
        ondismiss: () => {
          cart.clear();
          location.href = u(`/orders/?placed=${o.orderId}&unpaid=1`);
          resolve();
        },
      },
    });
    rzp.on("payment.failed", () => toast("Payment didn't go through. You can try again."));
    rzp.open();
  });
}

/* ================= My orders ================= */
const OSTATUS = {
  pending_payment: ["Awaiting payment", "st-approved"],
  payment_submitted: ["Checking payment", "st-payment_submitted"],
  paid: ["Paid, packing soon", "st-confirmed"],
  packed: ["Packed", "st-confirmed"],
  shipped: ["Shipped", "st-confirmed"],
  delivered: ["Delivered", "st-completed"],
  cancelled: ["Cancelled", "st-declined"],
};

function upiLink(o, upi) {
  const p = new URLSearchParams({ pa: upi.id, pn: upi.name || "Tide and Tea Nails", am: String(o.total), cu: "INR", tn: `Order ${o.code}` });
  return `upi://pay?${p.toString()}`;
}

let ordersData = null;
async function initOrders() {
  ordersData = await getShopData();
  const placed = new URLSearchParams(location.search).get("placed");
  const unpaid = new URLSearchParams(location.search).get("unpaid");
  const app = $("#ordersApp");
  const render = (list) => {
    list.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    if (!list.length) {
      app.innerHTML = `<div class="empty card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><h2>No orders yet</h2><p>Orders you place on this device show up here.</p><a class="btn btn-primary" href="${u("/shop/")}">Shop press-ons</a></div>`;
      return;
    }
    app.innerHTML = (placed ? `<div class="placed card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><div><h2>${unpaid ? "Your order is saved" : "Thank you!"}</h2><p>${unpaid ? "The payment wasn't completed. You can pay below whenever you're ready." : "We've got your order. You'll see updates here as we pack and ship it."}</p></div></div>` : "")
      + list.map((o) => orderCard(o, o.id === placed)).join("");
    paintQrs();
  };

  if (PREVIEW) {
    render(store.get("tt.preview.orders", []));
    app.addEventListener("submit", (e) => {
      const f = e.target.closest("[data-utr]"); if (!f) return;
      e.preventDefault();
      const ref = f.ref.value.trim();
      if (ref.replace(/\D/g, "").length < 6) return toast("Enter the transaction ID from your UPI app.");
      const list = store.get("tt.preview.orders", []).map((o) => (o.id === f.dataset.utr ? { ...o, status: "payment_submitted", paymentRef: ref } : o));
      store.set("tt.preview.orders", list); render(list); toast("Thanks! We'll confirm your payment shortly.");
    });
    bindCopy(app);
    return;
  }

  let fb;
  try { fb = await backend(); } catch (e) { console.error(e); app.innerHTML = `<p class="form-error">We couldn't connect. Check your internet and refresh the page.</p>`; return; }
  const { sb, uid } = fb;
  const byId = new Map();
  const toOrder = (r) => ({
    id: r.id, code: r.code, status: r.status, mode: r.mode, customer: r.customer, notes: r.notes, lines: r.lines,
    subtotal: r.subtotal, discount: r.discount, coupon: r.coupon, shipping: r.shipping, total: r.total, upi: r.upi,
    razorpayKeyId: r.razorpay_key_id, razorpayOrderId: r.razorpay_order_id, paymentRef: r.payment_ref,
    courier: r.courier, trackingNumber: r.tracking_number, trackingUrl: r.tracking_url, createdAt: Date.parse(r.created_at) || Date.now(),
  });
  const load = async () => {
    const { data, error } = await sb.from("orders").select("*").eq("customer_uid", uid).order("created_at", { ascending: false }).limit(50);
    if (error) { console.error(error); app.innerHTML = `<p class="form-error">We couldn't load your orders. Refresh to try again.</p>`; return; }
    const list = data.map(toOrder);
    list.forEach((o) => byId.set(o.id, o));
    render(list);
  };
  await load();
  // Live updates when the studio packs or ships an order (falls back to a refresh every minute).
  sb.channel("my-orders").on("postgres_changes", { event: "*", schema: "public", table: "orders", filter: `customer_uid=eq.${uid}` }, load).subscribe();
  setInterval(() => { if (!document.hidden) load(); }, 60000);

  app.addEventListener("submit", async (e) => {
    const f = e.target.closest("[data-utr]"); if (!f) return;
    e.preventDefault();
    const ref = f.ref.value.trim();
    if (ref.replace(/\D/g, "").length < 6) return toast("Enter the transaction ID from your UPI app.");
    const btn = f.querySelector("button"); btn.disabled = true;
    try { await fb.call("submit_upi", { orderId: f.dataset.utr, ref }); toast("Thanks! We'll confirm your payment shortly."); load(); }
    catch (err) { console.error(err); toast(esc(errText(err, "Couldn't save that. Try again."))); btn.disabled = false; }
  });
  app.addEventListener("click", async (e) => {
    const b = e.target.closest("[data-repay]"); if (!b) return;
    const o = byId.get(b.dataset.repay); if (!o) return;
    b.disabled = true;
    try { await payRazorpay(fb, { orderId: o.id, code: o.code, keyId: o.razorpayKeyId, amountPaise: o.total * 100, razorpayOrderId: o.razorpayOrderId }, o.customer); }
    catch (err) { console.error(err); toast("Couldn't open the payment window. Try again."); }
    finally { b.disabled = false; }
  });
  bindCopy(app);
}

function bindCopy(app) {
  app.addEventListener("click", async (e) => {
    const c = e.target.closest("[data-copy]"); if (!c) return;
    try { await navigator.clipboard.writeText(c.dataset.copy); toast("UPI ID copied"); } catch { toast(esc(c.dataset.copy)); }
  });
}

function orderCard(o, highlight) {
  const [label, cls] = OSTATUS[o.status] || [o.status, "st-requested"];
  const upi = o.upi || ordersData.upi || {};
  const date = new Date(o.createdAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
  let action = "";
  if (o.status === "pending_payment" && o.mode === "razorpay") {
    action = `<div class="pay"><div class="pay-body"><b>Payment pending</b><span class="muted">Complete the payment to confirm your order.</span><div class="pay-row"><button class="btn btn-primary" type="button" data-repay="${esc(o.id)}">Pay ${money(o.total)}</button></div></div></div>`;
  } else if (o.status === "pending_payment") {
    const link = upiLink(o, upi);
    action = `<div class="pay">
      <div class="qr" data-qr="${esc(link)}" hidden></div>
      <div class="pay-body">
        <b>Pay ${money(o.total)} by UPI</b>
        <span class="upi">${esc(upi.id || "")} <button type="button" class="linkbtn" data-copy="${esc(upi.id || "")}">Copy</button></span>
        <a class="btn btn-pink btn-sm" href="${esc(link)}">Open UPI app</a>
        <form class="pay-row" data-utr="${esc(o.id)}"><input name="ref" placeholder="UPI transaction ID (UTR)" inputmode="numeric" maxlength="40" aria-label="UPI transaction ID"><button class="btn btn-primary btn-sm">I've paid</button></form>
      </div></div>`;
  } else if (o.status === "payment_submitted") {
    action = `<p class="note">We're checking your payment (ref ${esc(o.paymentRef || "")}). You'll see it confirmed here shortly.</p>`;
  } else if (o.status === "shipped" && (o.trackingUrl || o.trackingNumber)) {
    action = `<p class="note">Shipped${o.courier ? ` with ${esc(o.courier)}` : ""}${o.trackingNumber ? `, tracking number ${esc(o.trackingNumber)}` : ""}. ${o.trackingUrl && /^https:\/\//.test(o.trackingUrl) ? `<a href="${esc(o.trackingUrl)}" rel="noopener" target="_blank">Track your parcel</a>` : ""}</p>`;
  }
  return `<article class="card mybk${highlight ? " hl" : ""}" id="o-${esc(o.id)}">
    <div class="mybk-top"><h3>Order ${esc(o.code || o.id.slice(-6).toUpperCase())}</h3><span class="pill ${cls}">${esc(label)}</span></div>
    <div class="meta"><span>${esc(date)}</span><span>${(o.lines || []).reduce((s, l) => s + l.qty, 0)} item(s)</span><span><b>${money(o.total)}</b></span></div>
    <ul class="olines">${(o.lines || []).map((l) => `<li><span>${esc(l.name)}${l.size ? ` · ${esc(l.size)}` : ""} × ${l.qty}</span><span>${money(l.amount)}</span></li>`).join("")}
      ${o.discount ? `<li><span>Coupon ${esc(o.coupon || "")}</span><span>−${money(o.discount)}</span></li>` : ""}
      <li><span>Shipping</span><span>${o.shipping ? money(o.shipping) : "Free"}</span></li></ul>
    ${o.customer ? `<p class="muted small">To ${esc(o.customer.name)}, ${esc([o.customer.line1, o.customer.line2, o.customer.city, o.customer.pincode].filter(Boolean).join(", "))}</p>` : ""}
    ${action}
  </article>`;
}

function paintQrs() {
  const draw = () => $$("[data-qr]").forEach((el) => {
    if (el.dataset.drawn || !window.QRCode) return;
    try { new window.QRCode(el, { text: el.dataset.qr, width: 240, height: 240, colorDark: "#241238", colorLight: "#ffffff" }); el.hidden = false; el.dataset.drawn = "1"; } catch {}
  });
  if (window.QRCode) draw(); else window.addEventListener("load", draw, { once: true });
}

/* ================= Go ================= */
({ list: initList, product: initProduct, cart: initCart, orders: initOrders })[page]?.().catch((e) => console.error(e));
