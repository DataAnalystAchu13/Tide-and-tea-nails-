// Runs on every page: bag count, offer strip dates, mobile menu.
import { cart, $, $$ } from "/lib.js";

function paintCount() {
  const n = cart.count();
  $$("[data-cart-count]").forEach((el) => { el.textContent = n; el.hidden = n === 0; });
}
paintCount();
window.addEventListener("cart", paintCount);
window.addEventListener("storage", (e) => { if (e.key === "tt.cart") paintCount(); });

// A sale can start or end between site rebuilds, so check its dates here too (India time).
const offer = $(".offer[data-sale]");
if (offer) {
  const day = (s, end) => (s ? Date.parse(`${s.slice(0, 10)}T00:00:00+05:30`) + (end ? 86400000 - 1 : 0) : null);
  const start = day(offer.dataset.starts), end = day(offer.dataset.ends, true), now = Date.now();
  if ((start && now < start) || (end && now > end)) offer.hidden = true;
}

const menu = $(".menu");
if (menu) {
  document.addEventListener("click", (e) => { if (menu.open && !menu.contains(e.target)) menu.open = false; });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") menu.open = false; });
}

// Installable app: register the service worker (skipped in local previews without HTTPS).
if ("serviceWorker" in navigator && !window.__ROOT && (location.protocol === "https:" || location.hostname === "localhost")) {
  window.addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
}

// Gentle reveal of sections as they scroll into view. Anything at or above the
// fold is shown at once, so a fast scroll or an anchor jump never leaves gaps.
if (!matchMedia("(prefers-reduced-motion: reduce)").matches) {
  let pending = $$("main .section .sec-head, main .svc, main .pcard, main .review, main .postcard, main .how li, main .igband")
    .filter((el) => el.getBoundingClientRect().top > innerHeight);
  pending.forEach((el) => el.classList.add("reveal"));
  const check = () => {
    const fold = innerHeight * 0.92;
    pending = pending.filter((el) => {
      if (el.getBoundingClientRect().top >= fold) return true;
      el.classList.add("in");
      return false;
    });
    if (!pending.length) removeEventListener("scroll", onScroll);
  };
  const onScroll = () => check();
  if (pending.length) { addEventListener("scroll", onScroll, { passive: true }); addEventListener("resize", onScroll); }
}
