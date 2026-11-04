// Offline-friendly service worker: pages come from the network first (so prices stay fresh),
// falling back to the last copy; static files come from the cache first.
const VERSION = "tt-__BUILD__";
const CORE = ["/", "/offline/", "/styles.css", "/site.js", "/lib.js", "/logo.webp", "/favicon.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const req = e.request;
  const url = new URL(req.url);
  if (req.method !== "GET" || url.origin !== location.origin) return; // Supabase, Razorpay, maps: always live
  if (req.mode === "navigate") {
    e.respondWith(
      fetch(req).then((res) => { const copy = res.clone(); caches.open(VERSION).then((c) => c.put(req, copy)); return res; })
        .catch(async () => (await caches.match(req)) || (await caches.match("/offline/")))
    );
    return;
  }
  // Code and styles: network first so a new build shows at once; cached copy only when offline.
  if (/\.(css|js|mjs)$/.test(url.pathname)) {
    e.respondWith(fetch(req).then((res) => { if (res.ok) { const copy = res.clone(); caches.open(VERSION).then((c) => c.put(req, copy)); } return res; })
      .catch(async () => (await caches.match(req, { ignoreSearch: true })) || Response.error()));
    return;
  }
  if (/\.(webp|png|svg|jpg|woff2?)$/.test(url.pathname)) {
    e.respondWith(caches.match(req).then((hit) => hit || fetch(req).then((res) => {
      if (res.ok) { const copy = res.clone(); caches.open(VERSION).then((c) => c.put(req, copy)); }
      return res;
    })));
  }
});
