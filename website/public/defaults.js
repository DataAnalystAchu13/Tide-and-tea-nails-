// Default studio settings. The live copy lives in Firestore at config/main and
// overrides these. The owner app can publish this list to Firestore in one tap.
// Every price, slot and location below is a PLACEHOLDER until the owner edits it.
export const DEFAULT_CONFIG = {
  currency: "₹",
  extensionTypes: [
    { id: "softgel", name: "Soft gel tips (Gel-X)", price: 1800, description: "Light and natural feeling. Lasts 3 to 4 weeks." },
    { id: "acrylic", name: "Acrylic", price: 1500, description: "The strongest option. Best for long and dramatic shapes." },
    { id: "polygel", name: "PolyGel", price: 2000, description: "Lightweight and flexible with almost no smell." },
    { id: "biab", name: "Builder gel overlay (BIAB)", price: 1200, description: "Strengthens your own nails. No tips added." },
    { id: "pressons", name: "Custom press-ons", price: 1000, description: "Made to your size. Reusable at home." }
  ],
  lengths: [
    { id: "short", name: "Short", price: 0 },
    { id: "medium", name: "Medium", price: 200 },
    { id: "long", name: "Long", price: 400 },
    { id: "xl", name: "Extra long", price: 700 }
  ],
  shapes: ["Almond", "Oval", "Square", "Squoval", "Coffin", "Stiletto", "Not sure yet"],
  addOns: [
    { id: "french", name: "French tips", price: 200 },
    { id: "chrome", name: "Chrome finish", price: 300 },
    { id: "cateye", name: "Cat-eye", price: 300 },
    { id: "art", name: "Hand-painted art (up to 4 nails)", price: 400 },
    { id: "charms", name: "3D charms and gems", price: 350 },
    { id: "removal", name: "Removal of old extensions", price: 300 }
  ],
  travel: {
    note: "Home visits are charged by distance from the studio.",
    slabs: [ { upToKm: 3, charge: 0 }, { upToKm: 8, charge: 150 }, { upToKm: 15, charge: 300 } ],
    perKmBeyond: 25
  },
  studio: { name: "Tide & Tea studio", address: "Studio address to be added", lat: 13.0827, lng: 80.2707 },
  slots: ["10:00", "12:30", "15:00", "17:30"],
  workingDays: [2, 3, 4, 5, 6, 7],
  blockedDates: [],
  bookingWindowDays: 30,
  advancePercent: 30,
  upiId: "yourname@upi",
  upiName: "Tide and Tea Nails",
  whatsapp: ""
};

export function mergeConfig(remote) {
  const c = { ...DEFAULT_CONFIG, ...(remote || {}) };
  c.travel = { ...DEFAULT_CONFIG.travel, ...((remote && remote.travel) || {}) };
  c.studio = { ...DEFAULT_CONFIG.studio, ...((remote && remote.studio) || {}) };
  return c;
}

export function travelCharge(cfg, km) {
  if (km == null || isNaN(km)) return 0;
  const slabs = [...cfg.travel.slabs].sort((a, b) => a.upToKm - b.upToKm);
  for (const s of slabs) if (km <= s.upToKm) return s.charge;
  const last = slabs[slabs.length - 1] || { upToKm: 0, charge: 0 };
  return last.charge + Math.ceil(km - last.upToKm) * cfg.travel.perKmBeyond;
}

export function quote(cfg, d) {
  const base = d.extension ? d.extension.price : 0;
  const length = d.length ? d.length.price : 0;
  const addOns = (d.addOns || []).reduce((s, a) => s + a.price, 0);
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
