// Shared helpers for the site pages: cart storage, money, toasts, Supabase (lazy) and live product data.
const SETTINGS = window.TIDE_SETTINGS || {};
export const SB_URL = String(SETTINGS.SUPABASE_URL || "").replace(/\/$/, "");
export const SB_KEY = String(SETTINGS.SUPABASE_ANON_KEY || "");
export const PREVIEW = !/^https:\/\//.test(SB_URL) || /PASTE|your-project/i.test(SB_URL + SB_KEY) || SB_KEY.length < 20;

/** Site root. "/" on the real site; the preview copy sets window.__ROOT so links work inside a sub-folder. */
export const ROOT = window.__ROOT || "/";
export const u = (path) => (ROOT === "/" ? path : ROOT + path.replace(/^\//, "").replace(/(^|\/)(\?|$)/, "$1index.html$2"));

export const $ = (s, el = document) => el.querySelector(s);
export const $$ = (s, el = document) => [...el.querySelectorAll(s)];
export const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
export const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");

const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};
export { store };

/* ---------- Cart (kept on this device) ---------- */
const CART = "tt.cart";
export const cart = {
  items() { return store.get(CART, []).filter((i) => i && i.id && i.qty > 0); },
  save(list) { store.set(CART, list); window.dispatchEvent(new Event("cart")); },
  add(item) {
    const list = cart.items();
    const k = item.id + "|" + (item.size || "");
    const hit = list.find((i) => i.id + "|" + (i.size || "") === k);
    if (hit) hit.qty = Math.min(10, hit.qty + item.qty);
    else list.push({ ...item, qty: Math.min(10, item.qty) });
    cart.save(list);
  },
  setQty(index, qty) {
    const list = cart.items();
    if (!list[index]) return;
    if (qty <= 0) list.splice(index, 1); else list[index].qty = Math.min(10, qty);
    cart.save(list);
  },
  clear() { cart.save([]); },
  count() { return cart.items().reduce((s, i) => s + i.qty, 0); },
};

/* ---------- Toast ---------- */
let toastT;
export function toast(html) {
  const t = $("#toast");
  if (!t) return;
  t.innerHTML = html; t.hidden = false;
  clearTimeout(toastT); toastT = setTimeout(() => (t.hidden = true), 3200);
}

/* ---------- Shop data: built into the page, refreshed live from Supabase ---------- */
let shopData = null;
export async function getShopData() {
  if (!shopData) shopData = fetch(new URL("./shop-data.json", import.meta.url), { cache: "no-cache" }).then((r) => r.json());
  return shopData;
}

const rest = (path) => fetch(`${SB_URL}/rest/v1/${path}`, { headers: { apikey: SB_KEY, authorization: `Bearer ${SB_KEY}` } })
  .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))));

/** Current price, stock and sale settings. Falls back to the build-time copy if Supabase can't be reached. */
export async function liveShop(ids) {
  const data = await getShopData();
  const products = Object.fromEntries(data.products.map((p) => [p.id, p]));
  let site = { sale: data.sale, shipping: data.shipping };
  if (PREVIEW) return { products, site, data };
  try {
    const wanted = [...new Set(ids)].filter((id) => /^[0-9a-f-]{36}$/i.test(id)).slice(0, 100);
    const [settings, rows] = await Promise.all([
      rest("site_settings?id=eq.1&select=data"),
      wanted.length ? rest(`products?id=in.(${wanted.join(",")})&select=id,name,price,sale_price,stock,made_to_order,exclude_from_sale,sizes,visible`) : [],
    ]);
    const s = (settings[0] && settings[0].data) || {};
    site = { sale: { ...site.sale, ...(s.sale || {}) }, shipping: { ...site.shipping, ...(s.shipping || {}) } };
    const found = new Set(rows.map((r) => r.id));
    wanted.forEach((id) => { if (!found.has(id)) products[id] = { ...(products[id] || { id }), visible: false }; });
    rows.forEach((r) => {
      products[r.id] = { ...(products[r.id] || {}), id: r.id, name: r.name, price: r.price, salePrice: r.sale_price, stock: r.stock,
        madeToOrder: r.made_to_order, excludeFromSale: r.exclude_from_sale, sizes: r.sizes || [], visible: r.visible };
    });
  } catch (e) { console.warn("Live prices unavailable, showing saved prices", e); }
  return { products, site, data };
}

/* ---------- Supabase (only on the bag and orders pages) ---------- */
let sbPromise = null;
export function backend() {
  if (PREVIEW) return Promise.reject(new Error("preview"));
  if (sbPromise) return sbPromise;
  sbPromise = (async () => {
    const { createClient } = await import("https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.45.4/+esm");
    const sb = createClient(SB_URL, SB_KEY, { auth: { persistSession: true, storageKey: "tt.auth" } });
    let { data: { session } } = await sb.auth.getSession();
    if (!session) {
      const { data, error } = await sb.auth.signInAnonymously();
      if (error) throw error;
      session = data.session;
    }
    return {
      sb,
      uid: session.user.id,
      /** Calls the shop edge function; throws an Error whose message is safe to show. */
      async call(action, data = {}) {
        const { data: out, error } = await sb.functions.invoke("shop", { body: { action, ...data } });
        if (error) {
          let msg = "";
          try { msg = (await error.context.json()).error; } catch {}
          const e = new Error(msg || error.message); e.friendly = !!msg; throw e;
        }
        return out;
      },
    };
  })();
  sbPromise.catch(() => { sbPromise = null; });
  return sbPromise;
}

/** Friendly text for an error from the shop function. */
export function errText(e, fallback = "Something went wrong. Please try again.") {
  if (e && e.friendly) return e.message;
  if (e && /fetch|network/i.test(e.message || "")) return "You seem to be offline. Check your connection and try again.";
  return fallback;
}

export function loadScript(src) {
  return new Promise((res, rej) => {
    if ([...document.scripts].some((s) => s.src === src)) return res();
    const s = document.createElement("script");
    s.src = src; s.async = true; s.onload = res; s.onerror = () => rej(new Error("Couldn't load " + src));
    document.head.appendChild(s);
  });
}
