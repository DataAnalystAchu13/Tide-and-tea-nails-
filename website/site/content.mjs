// Loads everything the static pages need.
// Live: reads Supabase over its REST API with the public anon key (row level security
// only exposes visible products, published posts and visible reviews).
// Preview: when config.js still has placeholders, uses defaults plus demo content.
import { readFile } from "node:fs/promises";
import { slugify } from "./md.mjs";

const here = (p) => new URL(p, import.meta.url);
const readJson = async (p) => JSON.parse(await readFile(here(p), "utf8"));

/** Reads the Supabase URL and anon key from website/public/config.js without executing it. */
export async function readSettings() {
  const src = await readFile(here("../public/config.js"), "utf8");
  const pick = (k) => (src.match(new RegExp(`${k}\\s*:\\s*["']([^"']*)["']`)) || [])[1] || "";
  const url = (process.env.SUPABASE_URL || pick("SUPABASE_URL")).replace(/\/$/, "");
  const anonKey = process.env.SUPABASE_ANON_KEY || pick("SUPABASE_ANON_KEY");
  const live = /^https:\/\//.test(url) && !/PASTE|your-project/i.test(url + anonKey) && anonKey.length > 20;
  return { url, anonKey, live };
}

/* ---------- Supabase REST (PostgREST); RLS lets the anon key read public content only ---------- */
async function rest({ url, anonKey }, path) {
  for (let attempt = 1; ; attempt++) {
    const res = await fetch(`${url}/rest/v1/${path}`, { headers: { apikey: anonKey, authorization: `Bearer ${anonKey}`, accept: "application/json" } });
    if (res.ok) return res.json();
    const text = await res.text();
    if (attempt >= 3 || res.status < 500) throw new Error(`Supabase ${path} failed: ${res.status} ${text.slice(0, 300)}`);
    await new Promise((r) => setTimeout(r, 1000 * attempt));
  }
}

const toProduct = (r) => ({
  id: r.id, slug: r.slug, name: r.name, description: r.description, price: r.price, salePrice: r.sale_price, stock: r.stock,
  madeToOrder: r.made_to_order, excludeFromSale: r.exclude_from_sale, shape: r.shape, length: r.length, tags: r.tags || [],
  sizes: r.sizes || [], images: r.images || [], featured: r.featured, visible: r.visible, order: r.sort, updatedAt: r.updated_at,
});
const toPost = (r) => ({ id: r.id, slug: r.slug, title: r.title, excerpt: r.excerpt, body: r.body, cover: r.cover, status: r.status, publishedAt: r.published_at, updatedAt: r.updated_at });
const toReview = (r) => ({ id: r.id, name: r.name, area: r.area, rating: r.rating, text: r.text, images: r.images || [], featured: r.featured, visible: r.visible, order: r.sort, createdAt: r.created_at });

/* ---------- Merging with defaults ---------- */
const isObj = (v) => v && typeof v === "object" && !Array.isArray(v);
export function deepMerge(base, over) {
  if (!isObj(over)) return base;
  const out = { ...base };
  for (const [k, v] of Object.entries(over)) {
    if (v === null || v === undefined) continue;
    out[k] = isObj(v) && isObj(base[k]) ? deepMerge(base[k], v) : v;
  }
  return out;
}

/** Empty text fields in the app mean "use the default wording", so fill them back in. */
export function fillBlanks(base, over) {
  if (typeof base === "string") return typeof over === "string" && over.trim() ? over : base;
  if (Array.isArray(base)) {
    const rows = Array.isArray(over) ? over.filter((r) => r && Object.values(r).some((v) => String(v ?? "").trim())) : [];
    return rows.length ? rows : base;
  }
  if (isObj(base)) {
    const out = { ...(isObj(over) ? over : {}) };
    for (const k of Object.keys(base)) out[k] = fillBlanks(base[k], isObj(over) ? over[k] : undefined);
    return out;
  }
  return over ?? base;
}

const byOrder = (a, b) => (Number(a.order) || 999) - (Number(b.order) || 999) || String(a.name || "").localeCompare(String(b.name || ""));

/** Gives every product, post and area a unique, stable URL slug. */
function withSlugs(list, label) {
  const seen = new Set();
  return list.map((x) => {
    let base = slugify(x.slug || x[label] || x.id);
    let s = base, n = 2;
    while (seen.has(s)) s = `${base}-${n++}`;
    seen.add(s);
    return { ...x, slug: s };
  });
}

export async function loadContent() {
  const settings = await readSettings();
  const defaults = await readJson("./default-site.json");
  let site, config, products, posts, reviews, demo = false;

  if (settings.live) {
    const [s, c, p, b, r] = await Promise.all([
      rest(settings, "site_settings?id=eq.1&select=data"),
      rest(settings, "config?id=eq.main&select=data"),
      rest(settings, "products?visible=eq.true&select=*"),
      rest(settings, "posts?status=eq.published&select=*"),
      rest(settings, "reviews?visible=eq.true&select=*"),
    ]);
    site = deepMerge(defaults, (s[0] && s[0].data) || {});
    config = (c[0] && c[0].data) || null;
    products = p.map(toProduct); posts = b.map(toPost); reviews = r.map(toReview);
  } else {
    const d = await readJson("./demo-content.json");
    demo = true;
    site = deepMerge(defaults, { contact: { instagram: "your.instagram" }, home: { offerText: "Preview: turn on a sale in the app to show an offer here" } });
    config = null;
    products = d.products; posts = d.posts; reviews = d.reviews;
  }

  // Booking prices: config/main, falling back to the same defaults the booking page uses.
  const { DEFAULT_CONFIG, mergeConfig } = await import(here("../public/defaults.js"));
  config = mergeConfig(config || DEFAULT_CONFIG);

  const time = (x) => Date.parse(x.publishedAt || x.createdAt || 0) || 0;
  site.texts = fillBlanks(defaults.texts, site.texts);
  return {
    settings,
    demo,
    site,
    config,
    products: withSlugs(products.filter((p) => p.visible !== false).sort(byOrder), "name"),
    posts: withSlugs(posts.filter((p) => p.status === "published").sort((a, b) => time(b) - time(a)), "title"),
    reviews: reviews.filter((r) => r.visible !== false).sort((a, b) => time(b) - time(a) || byOrder(a, b)),
    areas: withSlugs((site.areas || []).filter((a) => a && a.name), "name"),
  };
}
