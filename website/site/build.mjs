// Builds the static website into website/dist.
//   node site/build.mjs            (from the website folder)
// Copies website/public, then writes one HTML file per page from Firestore content.
// Runs before every Hosting deploy (firebase.json predeploy) and in the GitHub Action.
import { cp, mkdir, rm, writeFile, readFile } from "node:fs/promises";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { loadContent } from "./content.mjs";
import * as R from "./render.mjs";
import { demoImages } from "./demo-images.mjs";

const root = fileURLToPath(new URL("..", import.meta.url));
const dist = root + "dist";

async function put(path, data) {
  const file = dist + (path.endsWith("/") ? path + "index.html" : path);
  await mkdir(dirname(file), { recursive: true });
  await writeFile(file, data);
}

export async function build({ quiet = false } = {}) {
  const t0 = Date.now();
  const c = await loadContent();
  const now = Date.now();

  await rm(dist, { recursive: true, force: true });
  await cp(root + "public", dist, { recursive: true });
  await cp(root + "../supabase/functions/_shared/pricing.mjs", dist + "/pricing.mjs");
  // Preview only: sample product pictures inlined as data URIs so the preview works from any folder.
  if (c.demo) {
    const imgs = demoImages();
    const data = (path) => (path && path.startsWith("/demo/") ? "data:image/svg+xml;base64," + Buffer.from(imgs[path.slice(6)]).toString("base64") : path);
    c.products.forEach((p) => (p.images = (p.images || []).map(data)));
    c.posts.forEach((p) => (p.cover = data(p.cover)));
  }

  const bookPartial = await readFile(root + "site/pages/book.html", "utf8");
  const pages = [
    R.home(c, now),
    R.book(c, bookPartial),
    R.services(c),
    R.shop(c, now),
    ...c.products.map((p) => ({ ...R.product(c, p, now), lastmod: String(p.updatedAt || "").slice(0, 10) || undefined })),
    { ...R.cart(c), noindex: true },
    { ...R.orders(c), noindex: true },
    R.about(c),
    R.reviews(c),
    R.blog(c),
    ...c.posts.map((p) => ({ ...R.post(c, p), lastmod: String(p.updatedAt || p.publishedAt || "").slice(0, 10) || undefined })),
    R.contact(c),
    ...c.areas.map((a) => R.area(c, a, now)),
    R.policies(c),
    { ...R.hi(c), noindex: true },
    { ...R.notFound(c), noindex: true },
    { ...R.offline(c), noindex: true },
  ];
  // Version our own CSS and JS links so a new build is never shown with an old stylesheet.
  const v = String(now);
  const bust = (html) => html.replace(/(href|src)="(\/[\w.-]+\.(?:css|js))"/g, `$1="$2?v=${v}"`);
  for (const p of pages) await put(p.path, bust(p.html));
  await put("/sitemap.xml", R.sitemap(c, pages));
  await put("/robots.txt", R.robots(c));
  // New cache name on every build so phones pick up the new pages and styles.
  const sw = await readFile(dist + "/sw.js", "utf8");
  await writeFile(dist + "/sw.js", sw.replace("__BUILD__", String(now)));

  // Data the shop scripts use to show prices instantly (refreshed live from Firestore in the browser).
  const shopData = {
    builtAt: now,
    demo: c.demo,
    live: c.settings.live,
    sale: c.site.sale,
    shipping: c.site.shipping,
    upi: { id: c.config.upiId, name: c.config.upiName },
    whatsapp: c.site.contact.whatsapp || c.site.contact.phone || "",
    products: c.products.map((p) => ({
      id: p.id, slug: p.slug, name: p.name, image: (p.images || [])[0] || "", price: p.price, salePrice: p.salePrice || 0,
      stock: p.stock || 0, madeToOrder: !!p.madeToOrder, sizes: p.sizes || [], visible: true, excludeFromSale: !!p.excludeFromSale,
    })),
    demoCoupons: c.demo ? [{ code: "BUNNY10", type: "percent", value: 10, active: true, minOrder: 0 }] : [],
  };
  await put("/shop-data.json", JSON.stringify(shopData));

  if (!quiet) {
    console.log(`Built ${pages.length} pages in ${Date.now() - t0} ms (${c.demo ? "preview content" : `live content from ${c.settings.url}`}).`);
    console.log(`  ${c.products.length} products, ${c.posts.length} posts, ${c.reviews.length} reviews, ${c.areas.length} area pages`);
  }
  return { pages, content: c };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  build().catch((e) => { console.error(e); process.exit(1); });
}
