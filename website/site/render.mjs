// Page templates for the static site. Every value from Firestore goes through esc() or md().
import { esc, md, plain } from "./md.mjs";
import { displayPrice, saleActive } from "../../supabase/functions/_shared/pricing.mjs";

const SPRITE = `<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>
<symbol id="sparkle" viewBox="0 0 24 24"><path d="M12 0c.6 6.3 5.7 11.4 12 12-6.3.6-11.4 5.7-12 12-.6-6.3-5.7-11.4-12-12C6.3 11.4 11.4 6.3 12 0z"/></symbol>
<symbol id="bunny" viewBox="0 0 120 130"><g class="ears"><g class="ear-l"><ellipse cx="42" cy="34" rx="13" ry="32" fill="#fffaff" stroke="#3a1d5c" stroke-width="3"/><ellipse cx="42" cy="36" rx="6" ry="22" fill="#f4b3cb"/></g><g class="ear-r"><ellipse cx="78" cy="34" rx="13" ry="32" fill="#fffaff" stroke="#3a1d5c" stroke-width="3"/><ellipse cx="78" cy="36" rx="6" ry="22" fill="#f4b3cb"/></g></g><ellipse cx="60" cy="90" rx="40" ry="34" fill="#fffaff" stroke="#3a1d5c" stroke-width="3"/><circle cx="45" cy="86" r="4.5" fill="#2a1442"/><circle cx="75" cy="86" r="4.5" fill="#2a1442"/><circle cx="46.6" cy="84.4" r="1.5" fill="#fff"/><circle cx="76.6" cy="84.4" r="1.5" fill="#fff"/><ellipse cx="35" cy="98" rx="7" ry="4.5" fill="#f4b3cb" opacity=".85"/><ellipse cx="85" cy="98" rx="7" ry="4.5" fill="#f4b3cb" opacity=".85"/><path d="M57 94.5h6l-3 3.2z" fill="#e58aab"/><path d="M60 97.7c0 3-2.4 4.6-5 3.6M60 97.7c0 3 2.4 4.6 5 3.6" fill="none" stroke="#3a1d5c" stroke-width="2" stroke-linecap="round"/></symbol>
<symbol id="i-bag" viewBox="0 0 24 24"><path d="M6 8h12l-1 12H7L6 8z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M9 8V6a3 3 0 0 1 6 0v2" fill="none" stroke="currentColor" stroke-width="2"/></symbol>
<symbol id="i-ig" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="5" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="12" r="4" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="17.5" cy="6.5" r="1.2" fill="currentColor"/></symbol>
<symbol id="i-wa" viewBox="0 0 24 24"><path d="M4 20l1.2-4A8 8 0 1 1 8 18.8L4 20z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M9 9c0 3 3 6 6 6l1-1.5-2-1-1 1c-1-.5-2-1.5-2.5-2.5l1-1-1-2L9 9z" fill="currentColor"/></symbol>
<symbol id="i-star" viewBox="0 0 24 24"><path d="M12 2l3 6.6 7 .7-5.3 4.8 1.6 7L12 17.5 5.7 21l1.6-7L2 9.3l7-.7z"/></symbol>
</defs></svg>`;

const NAV = [
  ["/book/", "Book"],
  ["/services/", "Services"],
  ["/shop/", "Shop"],
  ["/reviews/", "Reviews"],
  ["/blog/", "Blog"],
  ["/about/", "About"],
  ["/contact/", "Contact"],
];

/* ---------- helpers ---------- */
export const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");
const rich = (s) => esc(s).replace(/\*([^*]+)\*/g, "<em>$1</em>"); // *word* → italic accent in titles
const unstar = (s) => String(s ?? "").replace(/\*/g, "");
export function igHandle(raw) {
  const s = String(raw || "").trim();
  if (!s) return "";
  const m = s.match(/instagram\.com\/([A-Za-z0-9._]+)/i);
  return (m ? m[1] : s.replace(/^@/, "")).replace(/[^A-Za-z0-9._]/g, "");
}
const digits = (s) => String(s || "").replace(/\D/g, "");
const intlPhone = (s) => { const d = digits(s); return d.length === 10 ? "91" + d : d; };
const absUrl = (c, path) => c.site.seo.siteUrl.replace(/\/$/, "") + path;
const img = (src, alt, cls = "", eager = false) =>
  `<img src="${esc(src)}" alt="${esc(alt)}"${cls ? ` class="${cls}"` : ""} ${eager ? 'fetchpriority="high"' : 'loading="lazy"'} decoding="async">`;
const ld = (obj) => `<script type="application/ld+json">${JSON.stringify(obj).replace(/</g, "\\u003c")}</script>`;

function contactLinks(c) {
  const k = c.site.contact;
  const ig = igHandle(k.instagram);
  return {
    ig, igUrl: ig ? `https://instagram.com/${ig}` : "",
    tel: digits(k.phone) ? `tel:+${intlPhone(k.phone)}` : "",
    wa: digits(k.whatsapp || k.phone) ? `https://wa.me/${intlPhone(k.whatsapp || k.phone)}` : "",
    mail: k.email ? `mailto:${k.email}` : "",
  };
}

function addressLine(c) {
  const k = c.site.contact;
  if (!k.address && !k.area) return "";
  return [k.address, k.area, k.city, k.pincode].filter(Boolean).join(", ");
}

/* ---------- structured data ---------- */
function salonLd(c) {
  const k = c.site.contact;
  const L = contactLinks(c);
  const url = c.site.seo.siteUrl;
  const o = {
    "@context": "https://schema.org",
    "@type": "NailSalon",
    "@id": url + "/#salon",
    name: c.site.brand.name,
    description: c.site.seo.description,
    url,
    logo: url + "/logo.png",
    image: [c.site.home.heroImage || url + "/logo.png"],
    priceRange: "₹₹",
    currenciesAccepted: "INR",
    paymentAccepted: "UPI, Cash, Card",
    address: {
      "@type": "PostalAddress",
      ...(k.address && !k.homeVisitsOnly ? { streetAddress: k.address } : {}),
      addressLocality: k.area ? `${k.area}, ${k.city || "Chennai"}` : k.city || "Chennai",
      addressRegion: "Tamil Nadu",
      ...(k.pincode ? { postalCode: k.pincode } : {}),
      addressCountry: "IN",
    },
    areaServed: [{ "@type": "City", name: "Chennai" }, ...c.areas.map((a) => ({ "@type": "Place", name: `${a.name}, Chennai` }))],
    sameAs: [L.igUrl, k.googleProfileUrl].filter(Boolean),
    hasOfferCatalog: {
      "@type": "OfferCatalog",
      name: "Nail extensions",
      itemListElement: c.config.extensionTypes.map((t) => ({
        "@type": "Offer",
        price: t.price,
        priceCurrency: "INR",
        itemOffered: { "@type": "Service", name: t.name, description: t.description || "" },
      })),
    },
  };
  if (k.phone) o.telephone = "+" + intlPhone(k.phone);
  if (k.email) o.email = k.email;
  if (!k.homeVisitsOnly && k.address && c.config.studio && c.config.studio.lat) {
    o.geo = { "@type": "GeoCoordinates", latitude: c.config.studio.lat, longitude: c.config.studio.lng };
  }
  return o;
}

const crumbs = (c, items) => ({
  "@context": "https://schema.org",
  "@type": "BreadcrumbList",
  itemListElement: items.map(([name, path], i) => ({ "@type": "ListItem", position: i + 1, name, item: absUrl(c, path) })),
});

/* ---------- layout ---------- */
function offerStrip(c) {
  const s = c.site.sale;
  const text = s.enabled ? `${s.label ? s.label + ": " : ""}${s.percent}% off every press-on set` : c.site.home.offerText;
  if (!text) return "";
  return `<div class="offer" data-starts="${esc(s.enabled ? s.startsAt || "" : "")}" data-ends="${esc(s.enabled ? s.endsAt || "" : "")}" ${s.enabled ? "data-sale" : ""}>
  <svg aria-hidden="true"><use href="#sparkle"/></svg><a href="/shop/">${esc(text)}</a><svg aria-hidden="true"><use href="#sparkle"/></svg></div>`;
}

function header(c, path) {
  const link = ([href, label]) => `<a href="${href}"${path.startsWith(href) ? ' aria-current="page"' : ""}>${label}</a>`;
  return `<header class="site-head">
  <div class="wrap head-inner">
    <a class="brand" href="/"><img src="/logo.webp" alt="" width="40" height="40"><span>${esc(c.site.brand.name.replace(/ Nails$/, ""))}</span></a>
    <nav class="mainnav" aria-label="Main">${NAV.filter(([h]) => h !== "/book/").map(link).join("")}</nav>
    <div class="head-actions">
      <a class="cart-link" href="/cart/" aria-label="Cart"><svg aria-hidden="true"><use href="#i-bag"/></svg><span class="cart-count" data-cart-count hidden>0</span></a>
      <a class="btn btn-pink btn-sm" href="/book/">Book</a>
      <details class="menu">
        <summary aria-label="Menu"><span></span><span></span><span></span></summary>
        <nav class="menu-panel" aria-label="Mobile">${NAV.map(link).join("")}<a href="/orders/">My orders</a></nav>
      </details>
    </div>
  </div>
</header>`;
}

function footer(c) {
  const k = c.site.contact;
  const L = contactLinks(c);
  const hours = (k.hours || []).filter((h) => h && h.days).map((h) => `<li><span>${esc(h.days)}</span><span>${esc(h.time)}</span></li>`).join("");
  return `<footer class="site-foot">
  <div class="wrap foot-grid">
    <div class="foot-brand">
      <img src="/logo.webp" alt="${esc(c.site.brand.name)}" width="72" height="72" loading="lazy">
      <p class="foot-name">${esc(c.site.brand.name)}</p>
      <p class="foot-tag">${esc(c.site.brand.tagline)}</p>
      <div class="social">${L.igUrl ? `<a href="${esc(L.igUrl)}" rel="noopener" target="_blank" aria-label="Instagram"><svg><use href="#i-ig"/></svg></a>` : ""}${L.wa ? `<a href="${esc(L.wa)}" rel="noopener" target="_blank" aria-label="WhatsApp"><svg><use href="#i-wa"/></svg></a>` : ""}</div>
    </div>
    <div>
      <h2 class="foot-h">Visit or call</h2>
      <address>
        ${k.homeVisitsOnly ? `Home visits across ${esc(k.city || "Chennai")}<br>` : ""}${addressLine(c) ? esc(addressLine(c)) + "<br>" : ""}
        ${L.tel ? `<a href="${L.tel}">${esc(k.phone)}</a><br>` : ""}${k.email ? `<a href="${esc(L.mail)}">${esc(k.email)}</a>` : ""}
      </address>
      ${hours ? `<ul class="hours">${hours}</ul>` : ""}
    </div>
    <div>
      <h2 class="foot-h">Explore</h2>
      <ul class="foot-links">${NAV.map(([h, l]) => `<li><a href="${h}">${l}</a></li>`).join("")}<li><a href="/orders/">My orders</a></li><li><a href="/policies/">Policies</a></li></ul>
    </div>
    <div>
      <h2 class="foot-h">Areas we serve</h2>
      <ul class="foot-links">${c.areas.map((a) => `<li><a href="/nail-extensions-in-${a.slug}/">${esc(a.name)}</a></li>`).join("")}</ul>
    </div>
  </div>
  <p class="wrap copy">© ${new Date().getFullYear()} ${esc(c.site.brand.name)}, ${esc(k.city || "Chennai")}</p>
</footer>`;
}

export function layout(c, { path, title, description, body, jsonld = [], noindex = false, scripts = "", ogImage = "", bodyClass = "", head = "" }) {
  const url = absUrl(c, path);
  const fullTitle = title.includes(c.site.brand.name) ? title : `${title} | ${c.site.brand.name}`;
  const image = ogImage || absUrl(c, "/logo.png");
  return `<!doctype html>
<html lang="en-IN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(fullTitle)}</title>
<meta name="description" content="${esc(description)}">
${noindex ? '<meta name="robots" content="noindex, follow">' : `<link rel="canonical" href="${esc(url)}">`}
<meta name="theme-color" content="#3a1d5c">
<meta property="og:type" content="website">
<meta property="og:site_name" content="${esc(c.site.brand.name)}">
<meta property="og:title" content="${esc(fullTitle)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:url" content="${esc(url)}">
<meta property="og:image" content="${esc(image)}">
<meta property="og:locale" content="en_IN">
<meta name="twitter:card" content="summary_large_image">
<meta name="geo.region" content="IN-TN">
<meta name="geo.placename" content="Chennai">
<link rel="icon" type="image/png" href="/favicon.png">
<link rel="apple-touch-icon" href="/icon-192.png">
<link rel="manifest" href="/manifest.webmanifest">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="Tide &amp; Tea">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,500;0,600;1,500&family=Nunito:wght@400;600;700;800&display=swap">
<link rel="stylesheet" href="/styles.css">
${head}
${jsonld.map(ld).join("\n")}
</head>
<body class="${bodyClass}">
${SPRITE}
<a class="skip" href="#main">Skip to content</a>
<div id="previewBanner" class="preview-banner"${c.demo ? "" : " hidden"}>Preview with sample content. Connect Firebase in <code>config.js</code> to go live.</div>
${offerStrip(c)}
${header(c, path)}
<main id="main">
${body}
</main>
${footer(c)}
<div id="toast" class="toast" role="status" hidden></div>
<script src="/config.js"></script>
<script type="module" src="/site.js"></script>
${scripts}
</body>
</html>`;
}

/* ---------- shared blocks ---------- */
function pageHead(eyebrow, title, text = "", extra = "") {
  return `<section class="pagehead"><div class="wrap">
  ${eyebrow ? `<p class="eyebrow">${esc(eyebrow)}</p>` : ""}<h1>${rich(title)}</h1>${text ? `<p class="lede">${esc(text)}</p>` : ""}${extra}
  <svg class="sp sp1" aria-hidden="true"><use href="#sparkle"/></svg><svg class="sp sp3" aria-hidden="true"><use href="#sparkle"/></svg>
</div></section>`;
}

function productCard(c, p, now) {
  const { price, unit, percentOff } = displayPrice(p, c.site, now);
  const sold = !p.madeToOrder && !(Number(p.stock) > 0);
  const image = (p.images || [])[0];
  return `<a class="pcard" href="/shop/${p.slug}/" data-pid="${esc(p.id)}" data-shape="${esc(p.shape || "")}" data-length="${esc(p.length || "")}" data-tags="${esc((p.tags || []).join("|"))}">
  <div class="pimg">${image ? img(image, p.name) : `<svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg>`}
    <span class="badge sale" data-badge ${percentOff ? "" : "hidden"}>${percentOff}% off</span>
    <span class="badge soldout" data-soldout ${sold ? "" : "hidden"}>Sold out</span>
  </div>
  <h3>${esc(p.name)}</h3>
  <p class="pprice"><b data-unit>${money(unit)}</b> <s data-was ${percentOff ? "" : "hidden"}>${money(price)}</s>${p.madeToOrder ? ' <small class="muted">Made to order</small>' : ""}</p>
</a>`;
}

function stars(n) {
  const k = Math.max(0, Math.min(5, Math.round(Number(n) || 5)));
  return `<span class="stars" aria-label="${k} out of 5">${'<svg aria-hidden="true"><use href="#i-star"/></svg>'.repeat(k)}</span>`;
}

function reviewCard(r) {
  return `<figure class="review card">
  ${stars(r.rating)}
  <blockquote>${esc(r.text)}</blockquote>
  ${(r.images || []).length ? `<div class="review-pics">${r.images.slice(0, 3).map((u) => img(u, `Nails done for ${r.name}`)).join("")}</div>` : ""}
  <figcaption><b>${esc(r.name)}</b>${r.area ? ` · ${esc(r.area)}` : ""}</figcaption>
</figure>`;
}

function postCard(p) {
  return `<a class="postcard card" href="/blog/${p.slug}/">
  ${p.cover ? `<div class="post-cover">${img(p.cover, p.title)}</div>` : ""}
  <div class="post-body"><time datetime="${esc(String(p.publishedAt || "").slice(0, 10))}">${esc(prettyDate(p.publishedAt))}</time><h3>${esc(p.title)}</h3><p>${esc(p.excerpt || plain(p.body, 140))}</p></div>
</a>`;
}

function prettyDate(d) {
  const ms = Date.parse(d || "");
  if (!ms) return "";
  return new Date(ms).toLocaleDateString("en-IN", { day: "numeric", month: "long", year: "numeric", timeZone: "Asia/Kolkata" });
}

function priceGrid(c) {
  const cfg = c.config;
  const li = (name, amt, sub = "") => `<li><span>${esc(name)}</span><span class="amt">${amt}</span>${sub ? `<small>${esc(sub)}</small>` : ""}</li>`;
  const slabs = [...cfg.travel.slabs].sort((a, b) => a.upToKm - b.upToKm);
  let prev = 0;
  const travel = slabs.map((s) => { const r = li(`${prev} to ${s.upToKm} km`, s.charge ? money(s.charge) : "Free"); prev = s.upToKm; return r; }).join("")
    + li(`Beyond ${prev} km`, `+${money(cfg.travel.perKmBeyond)}/km`);
  return `<div class="price-grid">
    <div class="card price-card"><h3>Extensions</h3><ul class="plist">${cfg.extensionTypes.map((t) => li(t.name, money(t.price), t.description)).join("")}</ul></div>
    <div class="card price-card"><h3>Length</h3><ul class="plist">${cfg.lengths.map((l) => li(l.name, l.price ? "+" + money(l.price) : "Included")).join("")}</ul>
      <h3 class="mt">Add-ons</h3><ul class="plist">${cfg.addOns.map((a) => li(a.name, "+" + money(a.price))).join("")}</ul></div>
    <div class="card price-card"><h3>Home visit travel</h3><ul class="plist">${travel}</ul>
      <p class="muted small">An advance of ${cfg.advancePercent}% after we approve your request confirms your slot. The balance is paid on the day.</p></div>
  </div>`;
}

function faqBlock(c) {
  const f = (c.site.faq || []).filter((x) => x && x.q && x.a);
  if (!f.length) return "";
  return `<section class="section"><div class="wrap narrow">
  <h2>Questions, answered</h2>
  <div class="faq">${f.map((x) => `<details><summary>${esc(x.q)}</summary><div>${md(x.a)}</div></details>`).join("")}</div>
</div></section>`;
}
const faqLd = (c) => {
  const f = (c.site.faq || []).filter((x) => x && x.q && x.a);
  return f.length ? [{ "@context": "https://schema.org", "@type": "FAQPage", mainEntity: f.map((x) => ({ "@type": "Question", name: x.q, acceptedAnswer: { "@type": "Answer", text: plain(x.a, 1000) } })) }] : [];
};

function followBand(c) {
  const L = contactLinks(c);
  if (!L.ig) return "";
  return `<section class="section"><div class="wrap"><div class="igband">
  <svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg>
  <div><h2>Fresh sets every week</h2><p>Follow <b>@${esc(L.ig)}</b> for new designs, offers and press-on drops.</p></div>
  <a class="btn btn-pink" href="${esc(L.igUrl)}" rel="noopener" target="_blank"><svg class="ico" aria-hidden="true"><use href="#i-ig"/></svg>Follow on Instagram</a>
</div></div></section>`;
}

const scriptsFor = (...names) => names.map((n) => `<script type="module" src="/${n}"></script>`).join("\n");

/* ================= PAGES ================= */

export function home(c, now) {
  const h = c.site.home;
  const featured = c.products.filter((p) => p.featured).slice(0, 4);
  const shopPicks = featured.length ? featured : c.products.slice(0, 4);
  const reviews = c.reviews.filter((r) => r.featured).slice(0, 3);
  const showReviews = reviews.length ? reviews : c.reviews.slice(0, 3);
  const from = Math.min(...c.config.extensionTypes.map((t) => t.price));
  const k = c.site.contact;
  const body = `
<section class="hero home-hero">
  <div class="hero-inner">
    <div class="hero-grid">
      <div class="hero-copy">
        <p class="eyebrow">${esc(h.eyebrow)}</p>
        <h1>${rich(h.title)}</h1>
        <p class="lede">${esc(h.text)}</p>
        <div class="hero-cta">
          <a class="btn btn-pink" href="/book/">Book a home visit</a>
          <a class="btn btn-ghost" href="/shop/">Shop press-ons</a>
        </div>
        <p class="hero-note">Sets from ${money(from)} · serving ${esc(k.city || "Chennai")}${c.areas.length ? ` · ${c.areas.slice(0, 3).map((a) => esc(a.name)).join(", ")} and more` : ""}</p>
      </div>
      <div class="hero-art${h.heroImage ? " has-photo" : ""}" aria-hidden="true">
        ${h.heroImage ? img(h.heroImage, "", "hero-photo", true) : `<img class="logo-badge" src="/logo.webp" alt="" width="320" height="320" fetchpriority="high">`}
        <svg class="bunny bunny-hero"><use href="#bunny"/></svg>
        <svg class="sp sp1"><use href="#sparkle"/></svg><svg class="sp sp2"><use href="#sparkle"/></svg><svg class="sp sp3"><use href="#sparkle"/></svg>
      </div>
    </div>
  </div>
</section>

<section class="steps-strip" aria-label="How booking works">
  <ol class="how">
    <li><b>Request</b><span>Pick your set, slot and location, add inspo pics.</span></li>
    <li><b>We review</b><span>We check your inspo and nails, then approve.</span></li>
    <li><b>Pay advance</b><span>A small advance locks your slot.</span></li>
    <li><b>Nail day</b><span>We arrive with everything. You relax.</span></li>
  </ol>
</section>

<section class="section"><div class="wrap">
  <div class="sec-head"><h2>Nail extensions at home</h2><a class="more" href="/services/">Full price list</a></div>
  <div class="svc-grid">${c.config.extensionTypes.slice(0, 6).map((t) => `<a class="svc card" href="/book/"><h3>${esc(t.name)}</h3><p>${esc(t.description || "")}</p><span class="svc-price">from ${money(t.price)}</span></a>`).join("")}</div>
</div></section>

${shopPicks.length ? `<section class="section"><div class="wrap">
  <div class="sec-head"><h2>Press-on drops</h2><a class="more" href="/shop/">Shop all</a></div>
  <div class="pgrid">${shopPicks.map((p) => productCard(c, p, now)).join("")}</div>
</div></section>` : ""}

${showReviews.length ? `<section class="section"><div class="wrap">
  <div class="sec-head"><h2>Kind words</h2><a class="more" href="/reviews/">All reviews</a></div>
  <div class="review-grid">${showReviews.map(reviewCard).join("")}</div>
  ${k.googleReviewUrl ? `<p class="center"><a class="btn btn-quiet" href="${esc(k.googleReviewUrl)}" rel="noopener" target="_blank">Read our Google reviews</a></p>` : ""}
</div></section>` : ""}

${c.posts.length ? `<section class="section"><div class="wrap">
  <div class="sec-head"><h2>From the blog</h2><a class="more" href="/blog/">All posts</a></div>
  <div class="post-grid">${c.posts.slice(0, 3).map(postCard).join("")}</div>
</div></section>` : ""}

${faqBlock(c)}
${followBand(c)}
${c.areas.length ? `<section class="section areas-sec"><div class="wrap">
  <h2>Home visits across Chennai</h2>
  <div class="area-chips">${c.areas.map((a) => `<a class="area-chip" href="/nail-extensions-in-${a.slug}/">${esc(a.name)}</a>`).join("")}</div>
</div></section>` : ""}`;
  return {
    path: "/",
    html: layout(c, {
      path: "/",
      title: c.site.seo.title,
      description: c.site.seo.description,
      ogImage: h.heroImage,
      bodyClass: "is-home",
      body,
      jsonld: [salonLd(c), { "@context": "https://schema.org", "@type": "WebSite", name: c.site.brand.name, url: c.site.seo.siteUrl }, ...faqLd(c)],
    }),
  };
}

export function services(c) {
  const body = `${pageHead("Price list", "Services and *prices*", `Every extension is done at your home in ${c.site.contact.city || "Chennai"}. Final price is confirmed when we approve your request.`, `<div class="hero-cta"><a class="btn btn-pink" href="/book/">Book a set</a></div>`)}
<section class="section"><div class="wrap">${priceGrid(c)}</div></section>
${faqBlock(c)}`;
  return {
    path: "/services/",
    html: layout(c, {
      path: "/services/",
      title: `Nail Extension Prices in ${c.site.contact.city || "Chennai"}`,
      description: `Prices for ${c.config.extensionTypes.map((t) => t.name).slice(0, 4).join(", ")} nail extensions at home in Chennai, with add-ons and travel charges.`,
      body,
      jsonld: [salonLd(c), crumbs(c, [["Home", "/"], ["Services", "/services/"]])],
    }),
  };
}

export function shop(c, now) {
  const shapes = [...new Set(c.products.map((p) => p.shape).filter((s) => s && s !== "Any"))];
  const lengths = [...new Set(c.products.map((p) => p.length).filter((s) => s && s !== "Any"))];
  const chip = (group, v) => `<label class="chip"><input type="radio" name="${group}" value="${esc(v)}"${v === "" ? " checked" : ""}><span>${esc(v || "All")}</span></label>`;
  const ship = c.site.shipping;
  const body = `${pageHead("Press-on nails", "Press-ons, *made to fit*", `Reusable salon-quality press-on sets, shipped across India.${ship.freeAbove ? ` Free shipping over ${money(ship.freeAbove)}.` : ""}`)}
<section class="section"><div class="wrap">
  ${c.products.length ? `<div class="filters" id="filters">
    ${shapes.length > 1 ? `<div class="chips" role="radiogroup" aria-label="Shape">${chip("shape", "")}${shapes.map((s) => chip("shape", s)).join("")}</div>` : ""}
    ${lengths.length > 1 ? `<div class="chips" role="radiogroup" aria-label="Length">${chip("length", "")}${lengths.map((s) => chip("length", s)).join("")}</div>` : ""}
  </div>
  <div class="pgrid" id="pgrid">${c.products.map((p) => productCard(c, p, now)).join("")}</div>
  <p class="muted center" id="noMatch" hidden>No sets match those filters.</p>` : `<div class="empty card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><h2>New sets dropping soon</h2><p>Follow us on Instagram to hear first.</p></div>`}
  <details class="sizing card"><summary>Sizing guide</summary><div class="prose">${md(c.site.sizing)}</div></details>
</div></section>`;
  return {
    path: "/shop/",
    html: layout(c, {
      path: "/shop/",
      title: "Press On Nails Online in India",
      description: `Shop reusable press-on nail sets by ${c.site.brand.name}, Chennai. Almond, coffin, stiletto and custom sizes, shipped across India.`,
      body,
      scripts: scriptsFor("shop.js"),
      jsonld: [crumbs(c, [["Home", "/"], ["Shop", "/shop/"]]), {
        "@context": "https://schema.org", "@type": "ItemList",
        itemListElement: c.products.map((p, i) => ({ "@type": "ListItem", position: i + 1, url: absUrl(c, `/shop/${p.slug}/`), name: p.name })),
      }],
    }),
  };
}

export function product(c, p, now) {
  const { price, unit, percentOff } = displayPrice(p, c.site, now);
  const images = (p.images || []).filter(Boolean);
  const sold = !p.madeToOrder && !(Number(p.stock) > 0);
  const path = `/shop/${p.slug}/`;
  const ship = c.site.shipping;
  const saleEnds = saleActive(c.site, now) && c.site.sale.endsAt ? String(c.site.sale.endsAt).slice(0, 10) : "";
  const avail = p.madeToOrder ? "https://schema.org/MadeToOrder" : sold ? "https://schema.org/OutOfStock" : "https://schema.org/InStock";
  const body = `<section class="section"><div class="wrap">
  <nav class="crumbs" aria-label="Breadcrumb"><a href="/shop/">Shop</a> / <span>${esc(p.name)}</span></nav>
  <div class="product" data-pid="${esc(p.id)}">
    <div class="gallery">
      <div class="gal-main">${images.length ? img(images[0], p.name, "", true) : `<svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg>`}
        <span class="badge sale" data-badge ${percentOff ? "" : "hidden"}>${percentOff}% off</span></div>
      ${images.length > 1 ? `<div class="gal-thumbs">${images.map((u, i) => `<button type="button" data-img="${esc(u)}" aria-label="Photo ${i + 1}"${i === 0 ? ' aria-current="true"' : ""}>${img(u, "")}</button>`).join("")}</div>` : ""}
    </div>
    <div class="pinfo">
      <h1>${esc(p.name)}</h1>
      <p class="pprice big"><b data-unit>${money(unit)}</b> <s data-was ${percentOff ? "" : "hidden"}>${money(price)}</s></p>
      <p class="stock" data-stock>${p.madeToOrder ? "Made to order, ships in 5 to 7 working days" : sold ? "Sold out" : Number(p.stock) <= 3 ? `Only ${Number(p.stock)} left` : "In stock"}</p>
      <div class="prose">${md(p.description)}</div>
      ${(p.shape && p.shape !== "Any") || (p.length && p.length !== "Any") ? `<dl class="specs">${p.shape && p.shape !== "Any" ? `<div><dt>Shape</dt><dd>${esc(p.shape)}</dd></div>` : ""}${p.length && p.length !== "Any" ? `<div><dt>Length</dt><dd>${esc(p.length)}</dd></div>` : ""}</dl>` : ""}
      <form class="buy" id="buyForm" data-product='${esc(JSON.stringify({ id: p.id, slug: p.slug, name: p.name, image: images[0] || "", price: p.price, salePrice: p.salePrice || 0, stock: p.stock || 0, madeToOrder: !!p.madeToOrder, sizes: p.sizes || [] }))}'>
        ${(p.sizes || []).length ? `<fieldset class="group"><legend class="label">Size</legend><div class="chips">${p.sizes.map((s, i) => `<label class="chip"><input type="radio" name="size" value="${esc(s)}"${i === 0 ? " required" : ""}><span>${esc(s)}</span></label>`).join("")}</div>
          <details class="sizing-mini"><summary>Which size am I?</summary><div class="prose">${md(c.site.sizing)}</div></details></fieldset>` : ""}
        <div class="buy-row">
          <label class="qty"><span class="sr">Quantity</span><button type="button" data-step="-1" aria-label="One less">−</button><input name="qty" type="number" min="1" max="10" value="1" inputmode="numeric"><button type="button" data-step="1" aria-label="One more">+</button></label>
          <button class="btn btn-primary grow" type="submit" ${sold ? "disabled" : ""} data-add>${sold ? "Sold out" : "Add to bag"}</button>
        </div>
        <p class="form-error" id="buyError" role="alert" hidden></p>
      </form>
      <ul class="perks">
        <li>${ship.freeAbove ? `Free shipping over ${money(ship.freeAbove)}, otherwise ${money(ship.flat)}` : `Shipping ${money(ship.flat)}`}</li>
        <li>${esc(ship.days)}</li>
        <li>Each set includes glue tabs, a mini file and a cuticle pusher</li>
      </ul>
    </div>
  </div>
</div></section>`;
  const productLd = {
    "@context": "https://schema.org",
    "@type": "Product",
    name: p.name,
    sku: p.id,
    description: plain(p.description, 5000),
    image: images.length ? images : [absUrl(c, "/logo.png")],
    brand: { "@type": "Brand", name: c.site.brand.name },
    category: "Press-on nails",
    offers: {
      "@type": "Offer",
      url: absUrl(c, path),
      priceCurrency: "INR",
      price: unit,
      ...(saleEnds ? { priceValidUntil: saleEnds } : {}),
      availability: avail,
      itemCondition: "https://schema.org/NewCondition",
      seller: { "@type": "Organization", name: c.site.brand.name },
      shippingDetails: {
        "@type": "OfferShippingDetails",
        shippingRate: { "@type": "MonetaryAmount", value: ship.freeAbove && unit >= ship.freeAbove ? 0 : Number(ship.flat) || 0, currency: "INR" },
        shippingDestination: { "@type": "DefinedRegion", addressCountry: "IN" },
        deliveryTime: {
          "@type": "ShippingDeliveryTime",
          handlingTime: { "@type": "QuantitativeValue", minValue: p.madeToOrder ? 5 : 2, maxValue: p.madeToOrder ? 7 : 4, unitCode: "DAY" },
          transitTime: { "@type": "QuantitativeValue", minValue: 3, maxValue: 6, unitCode: "DAY" },
        },
      },
      hasMerchantReturnPolicy: {
        "@type": "MerchantReturnPolicy",
        applicableCountry: "IN",
        returnPolicyCategory: "https://schema.org/MerchantReturnNotPermitted",
      },
    },
  };
  return {
    path,
    html: layout(c, {
      path,
      title: `${p.name}, Press On Nails`,
      description: plain(`${p.name}. ${p.description}`, 155),
      ogImage: images[0],
      body,
      scripts: scriptsFor("shop.js"),
      jsonld: [productLd, crumbs(c, [["Home", "/"], ["Shop", "/shop/"], [p.name, path]])],
    }),
  };
}

export function cart(c) {
  const body = `${pageHead("", "Your *bag*")}
<section class="section"><div class="wrap">
  <div id="cartApp" class="cart-app"><p class="muted">Loading your bag…</p></div>
</div></section>`;
  return {
    path: "/cart/",
    html: layout(c, {
      path: "/cart/", title: "Your bag", description: "Review your press-on order and check out.", noindex: true, body,
      scripts: scriptsFor("shop.js"),
    }),
  };
}

export function orders(c) {
  const body = `${pageHead("", "My *orders*", "Orders placed on this phone or computer.")}
<section class="section"><div class="wrap narrow">
  <div id="ordersApp" class="my-list"><p class="muted">Loading…</p></div>
</div></section>`;
  return {
    path: "/orders/",
    html: layout(c, {
      path: "/orders/", title: "My orders", description: "Track your press-on orders.", noindex: true, body,
      scripts: `<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js" defer></script>\n` + scriptsFor("shop.js"),
    }),
  };
}

export function about(c) {
  const a = c.site.about;
  const body = `${pageHead("About us", a.title)}
<section class="section"><div class="wrap about">
  <div class="prose">${md(a.body)}</div>
  ${(a.images || []).length ? `<div class="about-pics">${a.images.slice(0, 4).map((u) => img(u, `${c.site.brand.name}`)).join("")}</div>` : `<div class="about-pics solo"><img class="logo-badge" src="/logo.webp" alt="${esc(c.site.brand.name)} logo" width="320" height="320" loading="lazy"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg></div>`}
</div></section>
${followBand(c)}`;
  return {
    path: "/about/",
    html: layout(c, { path: "/about/", title: "About us", description: plain(a.body, 155), body, jsonld: [salonLd(c), crumbs(c, [["Home", "/"], ["About", "/about/"]])] }),
  };
}

export function reviews(c) {
  const k = c.site.contact;
  const body = `${pageHead("Reviews", "Kind *words*", "What our clients say about their sets.", k.googleReviewUrl ? `<div class="hero-cta"><a class="btn btn-pink" href="${esc(k.googleReviewUrl)}" rel="noopener" target="_blank">Review us on Google</a></div>` : "")}
<section class="section"><div class="wrap">
  ${c.reviews.length ? `<div class="review-grid">${c.reviews.map(reviewCard).join("")}</div>` : `<div class="empty card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><p>Reviews are on their way.</p></div>`}
</div></section>`;
  return {
    path: "/reviews/",
    html: layout(c, { path: "/reviews/", title: `Reviews of ${c.site.brand.name}, Chennai`, description: `Client reviews and photos of nail extensions and press-ons by ${c.site.brand.name} in Chennai.`, body, jsonld: [crumbs(c, [["Home", "/"], ["Reviews", "/reviews/"]])] }),
  };
}

export function blog(c) {
  const body = `${pageHead("Blog", "Nail *notes*", "Care tips, trends and press-on how-tos.")}
<section class="section"><div class="wrap">
  ${c.posts.length ? `<div class="post-grid">${c.posts.map(postCard).join("")}</div>` : `<div class="empty card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><p>First post coming soon.</p></div>`}
</div></section>`;
  return {
    path: "/blog/",
    html: layout(c, { path: "/blog/", title: "Nail Care Tips and Trends", description: `Nail care, extension aftercare and press-on tips from ${c.site.brand.name}, Chennai.`, body, jsonld: [crumbs(c, [["Home", "/"], ["Blog", "/blog/"]])] }),
  };
}

export function post(c, p) {
  const path = `/blog/${p.slug}/`;
  const date = String(p.publishedAt || "").slice(0, 10);
  const body = `<article class="section"><div class="wrap narrow">
  <nav class="crumbs" aria-label="Breadcrumb"><a href="/blog/">Blog</a> / <span>${esc(p.title)}</span></nav>
  <header class="post-head"><time datetime="${esc(date)}">${esc(prettyDate(p.publishedAt))}</time><h1>${esc(p.title)}</h1></header>
  ${p.cover ? `<div class="post-hero">${img(p.cover, p.title, "", true)}</div>` : ""}
  <div class="prose">${md(p.body)}</div>
  <div class="post-cta card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><div><h2>Ready for a new set?</h2><p>Book a home visit or browse press-ons.</p></div><div class="hero-cta"><a class="btn btn-primary" href="/book/">Book</a><a class="btn btn-quiet" href="/shop/">Shop</a></div></div>
</div></article>`;
  return {
    path,
    html: layout(c, {
      path, title: p.title, description: p.excerpt || plain(p.body, 155), ogImage: p.cover, body,
      jsonld: [{
        "@context": "https://schema.org", "@type": "BlogPosting", headline: p.title, ...(date ? { datePublished: date } : {}),
        ...(p.updatedAt ? { dateModified: String(p.updatedAt).slice(0, 10) } : {}),
        image: p.cover ? [p.cover] : [absUrl(c, "/logo.png")], mainEntityOfPage: absUrl(c, path),
        author: { "@type": "Organization", name: c.site.brand.name, url: c.site.seo.siteUrl },
        publisher: { "@type": "Organization", name: c.site.brand.name, logo: { "@type": "ImageObject", url: absUrl(c, "/logo.png") } },
      }, crumbs(c, [["Home", "/"], ["Blog", "/blog/"], [p.title, path]])],
    }),
  };
}

export function contact(c) {
  const k = c.site.contact;
  const L = contactLinks(c);
  const addr = addressLine(c);
  const mapQ = encodeURIComponent(addr || `${c.site.brand.name}, ${k.city || "Chennai"}`);
  const rows = [
    L.tel && `<a class="crow card" href="${L.tel}"><b>Call</b><span>${esc(k.phone)}</span></a>`,
    L.wa && `<a class="crow card" href="${esc(L.wa)}" rel="noopener" target="_blank"><b>WhatsApp</b><span>Message us</span></a>`,
    k.email && `<a class="crow card" href="${esc(L.mail)}"><b>Email</b><span>${esc(k.email)}</span></a>`,
    L.igUrl && `<a class="crow card" href="${esc(L.igUrl)}" rel="noopener" target="_blank"><b>Instagram</b><span>@${esc(L.ig)}</span></a>`,
  ].filter(Boolean).join("");
  const hours = (k.hours || []).filter((h) => h && h.days).map((h) => `<li><span>${esc(h.days)}</span><span>${esc(h.time)}</span></li>`).join("");
  const body = `${pageHead("Contact", "Say *hello*", k.homeVisitsOnly ? `We come to you, anywhere in ${k.city || "Chennai"}. Message us with any question.` : "Visit the studio or message us with any question.")}
<section class="section"><div class="wrap contact">
  <div class="contact-list">${rows || `<p class="muted">Contact details coming soon.</p>`}
    ${hours ? `<div class="card hours-card"><h3>Hours</h3><ul class="hours">${hours}</ul></div>` : ""}
    ${addr ? `<div class="card hours-card"><h3>${k.homeVisitsOnly ? "Based in" : "Studio"}</h3><p>${esc(addr)}</p>${k.googleProfileUrl ? `<a class="linkbtn" href="${esc(k.googleProfileUrl)}" rel="noopener" target="_blank">Open in Google Maps</a>` : ""}</div>` : ""}
  </div>
  <div class="mapframe card"><iframe title="Map" loading="lazy" referrerpolicy="no-referrer-when-downgrade" src="https://www.google.com/maps?q=${mapQ}&output=embed"></iframe></div>
</div></section>`;
  return {
    path: "/contact/",
    html: layout(c, { path: "/contact/", title: `Contact ${c.site.brand.name}, Chennai`, description: `Call, WhatsApp or message ${c.site.brand.name} for nail extensions at home in Chennai and press-on orders.`, body, jsonld: [salonLd(c), crumbs(c, [["Home", "/"], ["Contact", "/contact/"]])] }),
  };
}

export function area(c, a, now) {
  const path = `/nail-extensions-in-${a.slug}/`;
  const others = c.areas.filter((x) => x.slug !== a.slug);
  const picks = c.products.filter((p) => p.featured).slice(0, 4);
  const body = `${pageHead(`Home visits · ${a.name}`, `Nail extensions in *${a.name}*`, "", `<div class="hero-cta"><a class="btn btn-pink" href="/book/">Book a home visit</a><a class="btn btn-ghost" href="/services/">See prices</a></div>`)}
<section class="section"><div class="wrap narrow">
  <div class="prose">${md(a.text)}
  <p>The travel charge to ${esc(a.name)} is worked out from your exact location when you book, and you see it before you send your request.</p></div>
</div></section>
<section class="section"><div class="wrap">${priceGrid(c)}</div></section>
${picks.length ? `<section class="section"><div class="wrap"><div class="sec-head"><h2>Prefer press-ons?</h2><a class="more" href="/shop/">Shop all</a></div><div class="pgrid">${picks.map((p) => productCard(c, p, now)).join("")}</div></div></section>` : ""}
${others.length ? `<section class="section areas-sec"><div class="wrap"><h2>We also visit</h2><div class="area-chips">${others.map((x) => `<a class="area-chip" href="/nail-extensions-in-${x.slug}/">${esc(x.name)}</a>`).join("")}</div></div></section>` : ""}`;
  const ldSalon = salonLd(c);
  return {
    path,
    html: layout(c, {
      path,
      title: `Nail Extensions in ${a.name}, Chennai | Home Visits`,
      description: plain(`Nail extensions at home in ${a.name}, Chennai. ${a.text}`, 155),
      body,
      jsonld: [ldSalon, crumbs(c, [["Home", "/"], [`Nail extensions in ${a.name}`, path]])],
    }),
  };
}

export function policies(c) {
  const p = c.site.policies;
  const sections = [["shipping", "Shipping"], ["returns", "Returns and damaged orders"], ["cancellation", "Cancellations and refunds"], ["privacy", "Privacy"], ["terms", "Terms"]];
  const k = c.site.contact;
  const body = `${pageHead("Policies", "The *small print*")}
<section class="section"><div class="wrap narrow">
  <nav class="toc">${sections.map(([id, t]) => `<a href="#${id}">${t}</a>`).join("")}</nav>
  ${sections.map(([id, t]) => `<section id="${id}" class="policy"><h2>${t}</h2><div class="prose">${md(p[id] || "")}</div></section>`).join("")}
  <p class="muted">Questions? ${k.email ? `Email <a href="mailto:${esc(k.email)}">${esc(k.email)}</a>` : `<a href="/contact/">Contact us</a>`}. Business: ${esc(c.site.brand.name)}, ${esc(addressLine(c) || k.city || "Chennai")}.</p>
</div></section>`;
  return { path: "/policies/", html: layout(c, { path: "/policies/", title: "Shipping, returns and privacy", description: `Shipping, returns, cancellation, privacy and terms for ${c.site.brand.name}.`, body }) };
}

export function book(c, partial) {
  const body = `${pageHead("Book online", "Book your *set*", "Takes about two minutes. Nothing is charged until we approve your request.", `<div class="hero-cta"><a class="btn btn-ghost btn-sm" href="#mine" id="navMine" hidden>My bookings</a><a class="btn btn-ghost btn-sm" href="#prices">Price list</a></div>`)}
${partial}`;
  return {
    path: "/book/",
    html: layout(c, {
      path: "/book/",
      title: `Book Nail Extensions at Home in ${c.site.contact.city || "Chennai"}`,
      description: `Book Gel-X, acrylic, PolyGel or BIAB nail extensions at home in Chennai. Share your inspo, pick a slot and see the price before you send.`,
      bodyClass: "is-book",
      body,
      scripts: `<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js" defer></script>\n<script type="module" src="/app.js"></script>`,
      jsonld: [salonLd(c), crumbs(c, [["Home", "/"], ["Book", "/book/"]])],
    }),
  };
}

/** The shareable link-in-bio card: tideandteanails.in/hi */
export function hi(c) {
  const L = contactLinks(c);
  const f = c.site.follow;
  if (!L.ig || f.enabled === false) {
    return { path: "/hi/", html: `<!doctype html><meta charset="utf-8"><meta name="robots" content="noindex"><meta http-equiv="refresh" content="0;url=/"><title>${esc(c.site.brand.name)}</title><a href="/">Continue</a>` };
  }
  const seconds = Math.max(2, Math.min(10, Number(f.seconds) || 3));
  const body = `<section class="hi-wrap">
  <div class="hi-card">
    <img class="hi-logo" src="/logo.webp" alt="${esc(c.site.brand.name)}" width="120" height="120">
    <svg class="bunny hi-bunny" aria-hidden="true"><use href="#bunny"/></svg>
    <svg class="sp sp1" aria-hidden="true"><use href="#sparkle"/></svg><svg class="sp sp3" aria-hidden="true"><use href="#sparkle"/></svg>
    <h1>${esc(f.message)}</h1>
    <p class="hi-handle">@${esc(L.ig)}</p>
    ${f.coupon ? `<p class="hi-coupon">Use <b>${esc(f.coupon)}</b> ${esc(f.couponNote || "on your first press-on order")}</p>` : ""}
    <a class="btn btn-pink hi-follow" id="hiFollow" href="${esc(L.igUrl)}" rel="noopener" target="_blank"><svg class="ico" aria-hidden="true"><use href="#i-ig"/></svg>Follow on Instagram</a>
    <a class="hi-skip" id="hiSkip" href="/">Continue to the site</a>
    <div class="bar hi-bar" aria-hidden="true"><span id="hiBar"></span></div>
    <p class="hi-note" id="hiNote" aria-live="polite">Taking you to the site in ${seconds}…</p>
  </div>
</section>`;
  const html = `<!doctype html>
<html lang="en-IN"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(c.site.brand.name)}: follow us</title>
<meta name="robots" content="noindex, follow">
<meta name="description" content="${esc(f.message)}">
<meta property="og:title" content="${esc(c.site.brand.name)}">
<meta property="og:description" content="${esc(c.site.brand.tagline)}">
<meta property="og:image" content="${esc(absUrl(c, "/logo.png"))}">
<meta property="og:url" content="${esc(absUrl(c, "/hi/"))}">
<meta name="theme-color" content="#3a1d5c">
<link rel="icon" type="image/png" href="/favicon.png">
<script>/* Seen the card before? Go straight on. */try{var t=new URLSearchParams(location.search).get("to")||"/";if(!/^\\/(?!\\/)/.test(t))t="/";window.__hiTo=t;if(localStorage.getItem("tt.hi.seen"))location.replace(t)}catch(e){}</script>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,500;0,600;1,500&family=Nunito:wght@400;600;700;800&display=swap">
<link rel="stylesheet" href="/styles.css">
</head><body class="is-hi">${SPRITE}${body}
<script type="module" src="/hi.js" data-seconds="${seconds}"></script>
</body></html>`;
  return { path: "/hi/", html };
}

export function notFound(c) {
  const body = `<section class="section"><div class="wrap narrow"><div class="empty card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><h1>This page hopped away</h1><p>Try the home page, the shop or book a set.</p><div class="hero-cta center"><a class="btn btn-primary" href="/">Home</a><a class="btn btn-quiet" href="/shop/">Shop</a><a class="btn btn-quiet" href="/book/">Book</a></div></div></div></section>`;
  return { path: "/404.html", html: layout(c, { path: "/404.html", title: "Page not found", description: "Page not found.", noindex: true, body }) };
}

export function offline(c) {
  const body = `<section class="section"><div class="wrap narrow"><div class="empty card"><svg class="bunny" aria-hidden="true"><use href="#bunny"/></svg><h1>You're offline</h1><p>Check your connection and try again. Your bag is saved on this phone.</p><a class="btn btn-primary" href="/">Try again</a></div></div></section>`;
  return { path: "/offline/", html: layout(c, { path: "/offline/", title: "Offline", description: "You're offline.", noindex: true, body }) };
}

export function sitemap(c, pages) {
  const today = new Date().toISOString().slice(0, 10);
  const urls = pages.filter((p) => !p.noindex).map((p) => `<url><loc>${esc(absUrl(c, p.path))}</loc><lastmod>${p.lastmod || today}</lastmod></url>`);
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join("\n")}\n</urlset>\n`;
}

export function robots(c) {
  return `User-agent: *\nAllow: /\nDisallow: /cart/\nDisallow: /orders/\nDisallow: /hi/\n\nSitemap: ${absUrl(c, "/sitemap.xml")}\n`;
}
