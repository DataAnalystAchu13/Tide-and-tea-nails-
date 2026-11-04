// Default studio settings. The live copy lives in Firestore at config/main and
// overrides these. The owner app can publish this list to Firestore in one tap.
// Every price, slot and location below is a PLACEHOLDER until the owner edits it.
export const DEFAULT_CONFIG = {
  currency: "₹",
  extensionTypes: [
    { id: "softgel", name: "Soft gel tips", price: 1500, description: "Light and natural feeling. Lasts 3 to 4 weeks." },
    { id: "acrylic", name: "Acrylic extensions", price: 1900, description: "The strongest option. Best for long and dramatic shapes." },
    { id: "polygel", name: "PolyGel extensions", price: 2000, description: "Lightweight and flexible with almost no smell." },
    { id: "biab", name: "Gel overlay on natural nails", price: 1000, description: "Strengthens your own nails. No tips added." },
    { id: "pressons", name: "Custom press-on designs", price: 400, description: "Starting at ₹400 + shipping. Made to your size, reusable at home." }
  ],
  // No extra charge for length: every length is included in the set price.
  lengths: [
    { id: "short", name: "Short", price: 0 },
    { id: "medium", name: "Medium", price: 0 },
    { id: "long", name: "Long", price: 0 },
    { id: "xl", name: "Extra long", price: 0 }
  ],
  shapes: ["Almond", "Oval", "Square", "Squoval", "Coffin", "Stiletto", "Not sure yet"],
  // An add-on with price 0 shows as "Price varies" and adds nothing to the estimate.
  addOns: [
    { id: "french", name: "French tips", price: 300 },
    { id: "chrome", name: "Chrome finish", price: 300 },
    { id: "cateye", name: "Cat-eye finish", price: 300 },
    { id: "gelremoval", name: "Gel polish removal", price: 300 },
    { id: "removal", name: "Nail extension removal", price: 500 },
    { id: "art", name: "Painted nail art", price: 0 },
    { id: "charms", name: "3D charms and gems", price: 0 }
  ],
  // With no distance slabs, travel is not added to the estimate; the note is shown instead
  // and the owner confirms the travel charge.
  travel: {
    note: "Travel charges will be applicable for home visits. Please contact the owner to confirm the travel charges.",
    slabs: [],
    perKmBeyond: 0
  },
  studio: { name: "Tide & Tea studio", address: "Studio address to be added", lat: 13.0827, lng: 80.2707 },
  slots: ["10:00", "12:30", "15:00", "17:30"],
  workingDays: [1, 2, 3, 4, 5, 6, 7],
  blockedDates: [],
  bookingWindowDays: 30,
  advancePercent: 30,
  upiId: "9043450513@upi",
  upiName: "Tide and Tea Nails",
  whatsapp: ""
};

export function mergeConfig(remote) {
  const c = { ...DEFAULT_CONFIG, ...(remote || {}) };
  c.travel = { ...DEFAULT_CONFIG.travel, ...((remote && remote.travel) || {}) };
  c.studio = { ...DEFAULT_CONFIG.studio, ...((remote && remote.studio) || {}) };
  return c;
}

/** True when travel is priced by distance; false when the owner confirms it per booking. */
export const travelByDistance = (cfg) => (cfg.travel.slabs || []).length > 0;

export function travelCharge(cfg, km) {
  if (km == null || isNaN(km) || !travelByDistance(cfg)) return 0;
  const slabs = [...cfg.travel.slabs].sort((a, b) => a.upToKm - b.upToKm);
  for (const s of slabs) if (km <= s.upToKm) return s.charge;
  const last = slabs[slabs.length - 1] || { upToKm: 0, charge: 0 };
  return last.charge + Math.ceil(km - last.upToKm) * cfg.travel.perKmBeyond;
}

export function quote(cfg, d) {
  const base = d.extension ? d.extension.price : 0;
  const length = d.length ? d.length.price : 0;
  const addOns = (d.addOns || []).reduce((s, a) => s + (Number(a.price) || 0), 0);
  const travel = d.mode === "home" ? travelCharge(cfg, d.distanceKm) : 0;
  const total = base + length + addOns + travel;
  const advance = Math.ceil((total * cfg.advancePercent) / 100 / 10) * 10;
  return { base, length, addOns, travel, total, advance };
}

export function distanceKm(a, b) {
  const R = 6371, rad = (x) => (x * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat), dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

const escHtml = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const rupees = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");

/** How an add-on's price reads: "+₹300", or "Price varies" when it has no fixed price. */
export const addOnPrice = (a) => (Number(a.price) > 0 ? "+" + rupees(a.price) : "Price varies");

/**
 * The price list, shared by the services page, area pages and the booking page so they never
 * disagree. Two equal cards (extensions, add-ons) and a full-width home-visit note.
 */
export function priceListHtml(cfg) {
  const li = (name, amt, sub = "") => `<li><span>${escHtml(name)}</span><span class="amt">${amt}</span>${sub ? `<small>${escHtml(sub)}</small>` : ""}</li>`;
  const lengthNote = (cfg.lengths || []).every((l) => !Number(l.price))
    ? `<p class="pnote">Every length is included. No extra charge for long nails.</p>`
    : `<ul class="plist">${cfg.lengths.map((l) => li(`${l.name} length`, l.price ? "+" + rupees(l.price) : "Included")).join("")}</ul>`;
  let travel;
  if (travelByDistance(cfg)) {
    const slabs = [...cfg.travel.slabs].sort((a, b) => a.upToKm - b.upToKm);
    let prev = 0;
    const rows = slabs.map((s) => { const r = `<li><span>${prev} to ${s.upToKm} km</span><b>${s.charge ? rupees(s.charge) : "Free"}</b></li>`; prev = s.upToKm; return r; }).join("")
      + (cfg.travel.perKmBeyond ? `<li><span>Beyond ${prev} km</span><b>+${rupees(cfg.travel.perKmBeyond)}/km</b></li>` : "");
    travel = `<ul class="travel-slabs">${rows}</ul>`;
  } else {
    travel = `<p>${escHtml(cfg.travel.note)}</p>`;
  }
  return `<div class="price-grid">
    <div class="card price-card"><h3>Extensions</h3><ul class="plist">${cfg.extensionTypes.map((t) => li(t.name, rupees(t.price), t.description)).join("")}</ul>${lengthNote}</div>
    <div class="card price-card"><h3>Add-ons</h3><ul class="plist">${cfg.addOns.map((a) => li(a.name, addOnPrice(a))).join("")}</ul></div>
    <div class="card price-card visit-card"><h3>Home visits</h3>${travel}
      <p class="muted">An advance of ${Number(cfg.advancePercent) || 0}% paid when you book holds your slot. The balance is paid on the day.</p></div>
  </div>`;
}
