import { DEFAULT_CONFIG, mergeConfig, quote, distanceKm, travelByDistance, addOnPrice, priceListHtml } from "./defaults.js";

const SETTINGS = window.TIDE_SETTINGS || {};
const SB_URL = String(SETTINGS.SUPABASE_URL || "").replace(/\/+$/, "");
const SB_KEY = String(SETTINGS.SUPABASE_ANON_KEY || "");
const PREVIEW = !SB_URL.startsWith("https://") || !SB_KEY || SB_KEY.startsWith("PASTE");
const MAPS_KEY = SETTINGS.MAPS_API_KEY && !SETTINGS.MAPS_API_KEY.startsWith("PASTE") ? SETTINGS.MAPS_API_KEY : "";
const MAX_INSPO = 10;
const MAX_NATURAL = 5;

const $ = (s, el = document) => el.querySelector(s);
const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

let cfg = mergeConfig(null);
const money = (n) => `${cfg.currency}${Number(n || 0).toLocaleString("en-IN")}`;

/* ------------------------------------------------------------------ */
/* Backend: Supabase when configured, a browser-only stand-in otherwise */
/* ------------------------------------------------------------------ */

async function supabaseBackend() {
  const { createClient } = await import("https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.45.4/+esm");
  const sb = createClient(SB_URL, SB_KEY, { auth: { persistSession: true, autoRefreshToken: true } });

  // Customers book without an account: one anonymous user per browser, kept in localStorage.
  let { data: { session } } = await sb.auth.getSession();
  if (!session) {
    const { data, error } = await sb.auth.signInAnonymously();
    if (error) throw error;
    session = data.session;
  }
  const uid = session.user.id;
  const token = async () => (await sb.auth.getSession()).data.session?.access_token || SB_KEY;

  // Runs fetch() now and again whenever a matching row changes (Realtime honours RLS).
  const live = (table, filter, fetch, cb) => {
    let alive = true, queued = false;
    const run = async () => {
      if (queued) return; queued = true;
      await new Promise((r) => setTimeout(r, 120)); queued = false;
      try { const v = await fetch(); if (alive) cb(v); } catch (e) { console.warn(table, e); }
    };
    const ch = sb.channel(`${table}:${filter || "all"}:${Math.random().toString(36).slice(2)}`)
      .on("postgres_changes", { event: "*", schema: "public", table, ...(filter ? { filter } : {}) }, run)
      .subscribe((status) => { if (status === "SUBSCRIBED") run(); });
    run();
    return () => { alive = false; sb.removeChannel(ch); };
  };
  const must = ({ data, error }) => { if (error) throw error; return data; };

  const toBooking = (r) => ({
    id: r.id, customerUid: r.customer_uid, status: r.status, name: r.name, phone: r.phone, from: r.from_place,
    instagram: r.instagram, notes: r.notes, extension: r.extension, length: r.length, shape: r.shape, addOns: r.add_ons,
    date: r.date, slot: r.slot, serviceMode: r.service_mode, location: r.location, distanceKm: Number(r.distance_km),
    inspoUrls: r.inspo_urls, naturalUrls: r.natural_urls, price: r.price, ownerNote: r.owner_note, paymentRef: r.payment_ref,
    createdAt: Date.parse(r.created_at),
  });

  // Upload with progress (supabase-js has no progress callback), returns the public URL.
  const put = (bucket, path, blob, onProgress) => token().then((jwt) => new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${SB_URL}/storage/v1/object/${bucket}/${path}`);
    xhr.setRequestHeader("Authorization", `Bearer ${jwt}`);
    xhr.setRequestHeader("apikey", SB_KEY);
    xhr.setRequestHeader("Content-Type", blob.type || "application/octet-stream");
    xhr.setRequestHeader("x-upsert", "false");
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(e.loaded / Math.max(1, e.total));
    xhr.onload = () => (xhr.status < 300
      ? resolve(`${SB_URL}/storage/v1/object/public/${bucket}/${path}`)
      : reject(new Error(`Upload failed (${xhr.status}): ${xhr.responseText}`)));
    xhr.onerror = () => reject(new Error("Upload failed: network error"));
    xhr.send(blob);
  }));

  return {
    uid,
    watchConfig(cb) {
      return live("config", "id=eq.main",
        async () => must(await sb.from("config").select("data").eq("id", "main").maybeSingle())?.data || null, cb);
    },
    watchTaken(date, cb) {
      // No filter: Realtime cannot filter DELETE events, and a freed slot must show up.
      return live("slots_taken", null,
        async () => must(await sb.from("slots_taken").select("slot").eq("date", date)).map((r) => r.slot), cb);
    },
    watchMine(cb) {
      return live("bookings", `customer_uid=eq.${uid}`, async () =>
        must(await sb.from("bookings").select("*").eq("customer_uid", uid).order("created_at", { ascending: false }).limit(50)).map(toBooking), cb);
    },
    newId() { return crypto.randomUUID(); },
    upload(bookingId, name, blob, onProgress) {
      return put("bookings", `${uid}/${bookingId}/${name}`, blob, onProgress);
    },
    async create(id, d) {
      must(await sb.from("bookings").insert({
        id, name: d.name, phone: d.phone, from_place: d.from || "", instagram: d.instagram || "", notes: d.notes || "",
        extension: d.extension, length: d.length, shape: d.shape || "", add_ons: d.addOns || [],
        date: d.date, slot: d.slot, service_mode: d.serviceMode, location: d.location, distance_km: d.distanceKm || 0,
        inspo_urls: d.inspoUrls, natural_urls: d.naturalUrls, price: d.price,
      }));
    },
    async submitPayment(id, ref) {
      must(await sb.rpc("submit_payment", { p_booking_id: id, p_ref: ref }));
    },
  };
}

function previewBackend() {
  const KEY = "tide.preview.bookings";
  const listeners = new Set();
  const read = () => { try { return JSON.parse(localStorage.getItem(KEY) || "[]"); } catch { return []; } };
  const write = (list) => {
    try { localStorage.setItem(KEY, JSON.stringify(list)); } catch (e) { console.warn("Preview storage full", e); }
    listeners.forEach((cb) => cb(read()));
  };
  const patch = (id, p) => write(read().map((b) => (b.id === id ? { ...b, ...p } : b)));
  return {
    uid: "preview",
    watchConfig(cb) { cb(null); return () => {}; },
    watchTaken(date, cb) {
      const taken = read().filter((b) => b.date === date && ["confirmed", "approved", "payment_submitted"].includes(b.status)).map((b) => b.slot);
      cb(taken); return () => {};
    },
    watchMine(cb) { listeners.add(cb); cb(read()); return () => listeners.delete(cb); },
    newId() { return "pv" + Date.now().toString(36); },
    async upload(_id, _name, blob, onProgress) {
      const small = await shrink(blob, 360, 0.7);
      onProgress(1);
      return await new Promise((res) => { const fr = new FileReader(); fr.onload = () => res(fr.result); fr.readAsDataURL(small); });
    },
    async create(id, data) { write([{ ...data, id, status: "requested", createdAt: Date.now() }, ...read()]); },
    async submitPayment(id, ref) { patch(id, { status: "payment_submitted", paymentRef: ref }); },
    // Preview-only helper so the owner can see the customer's payment step.
    demoApprove(id) {
      const b = read().find((x) => x.id === id); if (!b) return;
      patch(id, { status: "approved", ownerNote: "Preview approval: love this inspo! See you soon.", price: { ...b.price } });
    },
  };
}

/* ------------------------------------------------------------------ */
/* Images: downscale on the phone before upload so it stays fast       */
/* ------------------------------------------------------------------ */

async function shrink(file, max = 1600, quality = 0.85) {
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: "from-image" });
    const scale = Math.min(1, max / Math.max(bmp.width, bmp.height));
    const w = Math.round(bmp.width * scale), h = Math.round(bmp.height * scale);
    const c = document.createElement("canvas"); c.width = w; c.height = h;
    c.getContext("2d").drawImage(bmp, 0, 0, w, h);
    bmp.close?.();
    return await new Promise((res) => c.toBlob((b) => res(b || file), "image/jpeg", quality));
  } catch {
    return file; // e.g. HEIC on a browser that cannot decode it: upload the original.
  }
}

/* ------------------------------------------------------------------ */
/* State                                                               */
/* ------------------------------------------------------------------ */

const STEPS = ["Your set", "Time", "Place", "Inspo"];
const STEP_LEFT = ["About 2 minutes", "About a minute left", "Nearly there", "Last step"];
let step = 0;
let backend = null;
let takenOff = null;
let taken = [];
let draft = freshDraft();

function freshDraft() {
  return {
    extension: null, length: null, shape: "", addOns: [],
    date: "", slot: "",
    mode: "home", lat: null, lng: null, address: "", distanceKm: null,
    inspo: [], natural: [],
    name: "", phone: "", from: "", instagram: "", notes: "",
  };
}

function saveDraft() {
  try {
    const { inspo, natural, ...rest } = draft;
    sessionStorage.setItem("tide.draft", JSON.stringify({ step, rest }));
  } catch {}
}
function loadDraft() {
  try {
    const raw = sessionStorage.getItem("tide.draft"); if (!raw) return;
    const { step: s, rest } = JSON.parse(raw);
    draft = { ...freshDraft(), ...rest };
    step = Math.min(s || 0, STEPS.length - 1);
  } catch {}
}

/* ------------------------------------------------------------------ */
/* Date helpers                                                        */
/* ------------------------------------------------------------------ */

const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
function openDates() {
  const out = [];
  const today = new Date(); today.setHours(0, 0, 0, 0);
  for (let i = 1; i <= cfg.bookingWindowDays; i++) {
    const d = new Date(today); d.setDate(today.getDate() + i);
    const isoDay = d.getDay() === 0 ? 7 : d.getDay();
    if (!cfg.workingDays.includes(isoDay)) continue;
    if (cfg.blockedDates.includes(iso(d))) continue;
    out.push(d);
  }
  return out;
}
const prettyDate = (s) => {
  if (!s) return "";
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short" });
};

/* ------------------------------------------------------------------ */
/* Rendering: steps                                                    */
/* ------------------------------------------------------------------ */

function renderStepper() {
  $("#stepper").innerHTML = STEPS.map((s, i) =>
    `<li class="${i < step ? "done" : ""} ${i === step ? "now" : ""}" ${i === step ? 'aria-current="step"' : ""}>${esc(s)}</li>`).join("");
  $("#backBtn").style.visibility = step === 0 ? "hidden" : "visible";
  $("#nextBtn").textContent = step === STEPS.length - 1 ? "Send request" : "Next";
  const c = $("#stepCount"); if (c) c.textContent = `Step ${step + 1} of ${STEPS.length} · ${STEP_LEFT[step]}`;
}

function updateTotal() {
  const q = quote(cfg, draft);
  $("#runningTotal").textContent = money(q.total);
}

function renderStep() {
  renderStepper();
  hideError();
  const body = $("#stepBody");
  if (takenOff && step !== 1) { takenOff(); takenOff = null; }
  if (step === 0) body.innerHTML = stepSet();
  if (step === 1) { body.innerHTML = stepDate(); watchTaken(); }
  if (step === 2) { body.innerHTML = stepLocation(); mountLocation(); }
  if (step === 3) { body.innerHTML = stepFinish(); renderThumbs(); }
  updateTotal();
  saveDraft();
}

/** One line recap of what's chosen so far, shown above later steps. */
function recap() {
  const bits = [draft.extension?.name, draft.length?.name, draft.shape, draft.date && `${prettyDate(draft.date)}${draft.slot ? ", " + slotLabel(draft.slot) : ""}`].filter(Boolean);
  return bits.length ? `<p class="recap"><span>${bits.map(esc).join(" · ")}</span><b>${money(quote(cfg, draft).total)}</b></p>` : "";
}

function stepSet() {
  if (!draft.length && cfg.lengths.length) draft.length = cfg.lengths[0];
  const extras = Boolean(draft.shape || draft.addOns.length);
  return `
  <h3 class="step-title">Choose your set</h3>
  <p class="step-sub">Not sure? Pick the closest one. We'll suggest the best option after seeing your nails.</p>
  <div class="group" role="radiogroup" aria-label="Extension type">
    <span class="label">Extension type</span>
    <div class="choice-grid">
      ${cfg.extensionTypes.map((t) => `
      <label class="choice"><input type="radio" name="ext" value="${esc(t.id)}" ${draft.extension?.id === t.id ? "checked" : ""}>
        <span class="box"><span class="row"><b>${esc(t.name)}</b><span class="price">${money(t.price)}</span></span><small>${esc(t.description || "")}</small></span>
      </label>`).join("")}
    </div>
  </div>
  <div class="group">
    <span class="label">Length ${cfg.lengths.every((l) => !l.price) ? `<span class="opt">(all lengths included)</span>` : ""}</span>
    <div class="chips">${cfg.lengths.map((l) => `
      <label class="chip"><input type="radio" name="len" value="${esc(l.id)}" ${draft.length?.id === l.id ? "checked" : ""}><span>${esc(l.name)}${l.price ? ` <i>+${money(l.price)}</i>` : ""}</span></label>`).join("")}
    </div>
  </div>
  <details class="extra" ${extras ? "open" : ""}>
  <summary>Shape and add-ons <span class="opt">(optional)</span></summary>
  <div class="group">
    <span class="label">Shape</span>
    <div class="chips">${cfg.shapes.map((s) => `
      <label class="chip"><input type="radio" name="shape" value="${esc(s)}" ${draft.shape === s ? "checked" : ""}><span>${esc(s)}</span></label>`).join("")}
    </div>
  </div>
  <div class="group">
    <span class="label">Add-ons</span>
    <div class="chips">${cfg.addOns.map((a) => `
      <label class="chip"><input type="checkbox" name="addon" value="${esc(a.id)}" ${draft.addOns.some((x) => x.id === a.id) ? "checked" : ""}><span>${esc(a.name)} <i>${addOnPrice(a).replace("Price varies", "price varies")}</i></span></label>`).join("")}
    </div>
  </div>
  </details>`;
}

function stepDate() {
  const dates = openDates();
  return `
  ${recap()}
  <h3 class="step-title">Pick a date and time</h3>
  <p class="step-sub">Greyed-out times are already booked.</p>
  <div class="group">
    <span class="label">Date</span>
    <div class="dates" role="radiogroup" aria-label="Date">
      ${dates.map((d) => {
        const v = iso(d);
        return `<label class="chip date"><input type="radio" name="date" value="${v}" ${draft.date === v ? "checked" : ""}>
          <span><small>${d.toLocaleDateString("en-IN", { weekday: "short" })}</small><b>${d.getDate()}</b><small>${d.toLocaleDateString("en-IN", { month: "short" })}</small></span></label>`;
      }).join("")}
    </div>
  </div>
  <div class="group">
    <span class="label">Time</span>
    <div class="chips" id="slotChips">${slotChips()}</div>
  </div>`;
}

function slotLabel(t) { const [h, m] = t.split(":").map(Number); const d = new Date(); d.setHours(h, m); return d.toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" }); }

function slotChips() {
  if (!draft.date) return `<span class="muted">Choose a date first.</span>`;
  const label = slotLabel;
  return cfg.slots.map((t) => {
    const busy = taken.includes(t);
    return `<label class="chip"><input type="radio" name="slot" value="${esc(t)}" ${busy ? "disabled" : ""} ${draft.slot === t && !busy ? "checked" : ""}><span>${label(t)}${busy ? " <i>booked</i>" : ""}</span></label>`;
  }).join("");
}

function watchTaken() {
  if (takenOff) { takenOff(); takenOff = null; }
  taken = [];
  if (!draft.date || !backend) return;
  takenOff = backend.watchTaken(draft.date, (list) => {
    taken = list;
    if (taken.includes(draft.slot)) draft.slot = "";
    const el = $("#slotChips"); if (el) el.innerHTML = slotChips();
  });
}

function stepLocation() {
  const s = cfg.studio;
  return `
  ${recap()}
  <h3 class="step-title">Where are you?</h3>
  <p class="step-sub">We come to you anywhere in ${esc(document.getElementById("book")?.dataset.area || "Chennai and Chengalpattu")}, or you can visit the studio.</p>
  <div class="mode-toggle group" role="radiogroup" aria-label="Visit type">
    <label class="choice"><input type="radio" name="mode" value="home" ${draft.mode === "home" ? "checked" : ""}>
      <span class="box"><b>Home visit</b><small>We bring the studio to you.</small></span></label>
    <label class="choice"><input type="radio" name="mode" value="studio" ${draft.mode === "studio" ? "checked" : ""}>
      <span class="box"><b>I'll come to the studio</b><small>No travel charge.</small></span></label>
  </div>
  <div id="homeLoc" ${draft.mode === "home" ? "" : "hidden"}>
    <div class="group">
      <div class="loc-actions">
        <button type="button" class="btn btn-pink btn-sm" id="gpsBtn">Use my current location</button>
        <span class="muted" id="gpsMsg" role="status" aria-live="polite"></span>
      </div>
      <p class="hint muted loc-privacy">Your location is only used to find your address for this booking and to show it to the owner. It isn't used for anything else.</p>
      <div class="place-search" id="placeSearch">${MAPS_KEY ? "" : `
        <input type="search" id="osmSearch" autocomplete="off" enterkeyhint="search" placeholder="Or search your area, street or building" aria-label="Search your address">
        <ul class="osm-results" id="osmResults" role="listbox" hidden></ul>`}</div>
      <div class="map" id="map" aria-label="Map. Tap to move the pin to your door."></div>
      <span class="hint muted">Tap the map or drag the pin to your exact building.</span>
    </div>
    <div class="field group">
      <label for="address">Full address</label>
      <textarea id="address" autocomplete="street-address" placeholder="House or flat number, street, area, landmark">${esc(draft.address)}</textarea>
    </div>
    <div id="locSummary"></div>
    ${travelByDistance(cfg) ? "" : `<p class="note travel-note">${esc(cfg.travel.note)}</p>`}
  </div>
  <div id="studioLoc" ${draft.mode === "studio" ? "" : "hidden"}>
    <div class="note"><b>${esc(s.name)}</b><br>${esc(s.address)}<br>
      <a href="https://www.google.com/maps/search/?api=1&query=${s.lat},${s.lng}" target="_blank" rel="noopener">Open in Google Maps</a></div>
  </div>`;
}

function renderLocSummary() {
  const el = $("#locSummary"); if (!el) return;
  if (draft.lat == null) { el.innerHTML = ""; return; }
  const q = quote(cfg, draft);
  const charge = travelByDistance(cfg) ? `<span>Travel charge <b>${q.travel ? money(q.travel) : "free"}</b></span>` : "";
  el.innerHTML = `<div class="loc-summary"><span>Location saved · about <b>${draft.distanceKm.toFixed(1)} km</b> from the studio</span>
    ${charge}
    <a href="https://www.google.com/maps/search/?api=1&query=${draft.lat},${draft.lng}" target="_blank" rel="noopener">Check pin on Google Maps</a></div>`;
}

/**
 * The map. Google Maps when a key is set in config.js; otherwise OpenStreetMap through Leaflet,
 * with OpenStreetMap's Nominatim for search and address lookup. Both need no extra setup.
 * `pin` hides which one is in use: move(lat, lng, zoom) and resize().
 */
let pin = null, geocoder = null;

function setPoint(lat, lng, address) {
  draft.lat = lat; draft.lng = lng;
  draft.distanceKm = distanceKm({ lat, lng }, cfg.studio);
  if (address && !draft.address.trim()) { draft.address = address; const a = $("#address"); if (a) a.value = address; }
  renderLocSummary(); updateTotal(); saveDraft();
  if (pin) pin.move(lat, lng);
}

function loadScript(src, attrs = {}) {
  return new Promise((resolve) => {
    const s = document.createElement("script");
    s.src = src; s.async = true; Object.assign(s, attrs);
    s.onload = () => resolve(true); s.onerror = () => resolve(false);
    document.head.appendChild(s);
  });
}

let mapsReady = null;
function loadMaps() {
  if (mapsReady) return mapsReady;
  if (MAPS_KEY) {
    mapsReady = new Promise((resolve) => {
      window.__tideMapsReady = () => resolve("google");
      loadScript(`https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(MAPS_KEY)}&libraries=places,geocoding&loading=async&callback=__tideMapsReady`)
        .then((ok) => { if (!ok) resolve(""); });
    });
  } else {
    const css = document.createElement("link");
    css.rel = "stylesheet"; css.href = "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css";
    css.crossOrigin = "anonymous";
    document.head.appendChild(css);
    mapsReady = loadScript("https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js", { crossOrigin: "anonymous" })
      .then((ok) => (ok && window.L ? "osm" : ""));
  }
  return mapsReady;
}

const NOMINATIM = "https://nominatim.openstreetmap.org";
async function nominatim(path, params) {
  const u = new URL(NOMINATIM + path);
  Object.entries({ format: "jsonv2", "accept-language": "en", ...params }).forEach(([k, v]) => u.searchParams.set(k, v));
  const r = await fetch(u, { headers: { Accept: "application/json" } });
  if (!r.ok) throw new Error(`Address lookup failed (${r.status})`);
  return r.json();
}

async function reverseGeocode(lat, lng) {
  try {
    if (geocoder) {
      const r = await geocoder.geocode({ location: { lat, lng } });
      return r.results?.[0]?.formatted_address || "";
    }
    const r = await nominatim("/reverse", { lat, lon: lng, zoom: 18, addressdetails: 0 });
    return r?.display_name || "";
  } catch { return ""; }
}

async function mountLocation() {
  renderLocSummary();
  pin = null;
  const kind = await loadMaps();
  const el = $("#map");
  if (!el) return;
  if (!kind) { el.hidden = true; return; } // offline or blocked: GPS and typing still work
  const has = draft.lat != null;
  const start = has ? { lat: draft.lat, lng: draft.lng } : { lat: cfg.studio.lat, lng: cfg.studio.lng };
  const pick = async (lat, lng) => {
    draft.address = "";
    setPoint(lat, lng, "");
    const addr = await reverseGeocode(lat, lng);
    if (addr && !draft.address.trim() && draft.lat === lat && draft.lng === lng) setPoint(lat, lng, addr);
  };
  if (kind === "google") mountGoogle(el, start, has, pick);
  else mountOsm(el, start, has, pick);
}

function mountOsm(el, start, has, pick) {
  const L = window.L;
  const m = L.map(el, { zoomControl: true, attributionControl: true, tap: true }).setView([start.lat, start.lng], has ? 17 : 12);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>',
  }).addTo(m);
  const icon = L.divIcon({ className: "map-pin", html: "<span></span>", iconSize: [30, 42], iconAnchor: [15, 42] });
  const mk = L.marker([start.lat, start.lng], { draggable: true, icon, keyboard: true, title: "Your door" }).addTo(m);
  mk.on("dragend", () => { const p = mk.getLatLng(); pick(p.lat, p.lng); });
  m.on("click", (e) => { mk.setLatLng(e.latlng); pick(e.latlng.lat, e.latlng.lng); });
  pin = {
    move: (lat, lng, zoom) => { mk.setLatLng([lat, lng]); m.setView([lat, lng], zoom || Math.max(m.getZoom(), 16)); },
    resize: () => m.invalidateSize(),
  };
  setTimeout(() => m.invalidateSize(), 50);
  mountOsmSearch();
}

function mountOsmSearch() {
  const input = $("#osmSearch"), list = $("#osmResults");
  if (!input) return;
  let timer = 0, seq = 0, results = [];
  const close = () => { list.hidden = true; list.innerHTML = ""; };
  const choose = (r) => {
    close(); input.value = "";
    draft.address = "";
    setPoint(Number(r.lat), Number(r.lon), r.display_name);
    if (pin) pin.move(Number(r.lat), Number(r.lon), 17);
  };
  const run = async () => {
    const q = input.value.trim();
    if (q.length < 3) { close(); return; }
    const mine = ++seq;
    try {
      // Bias toward the studio's city but still find anywhere in India.
      const d = 0.6, s = cfg.studio;
      results = await nominatim("/search", { q, countrycodes: "in", limit: 6, viewbox: `${s.lng - d},${s.lat + d},${s.lng + d},${s.lat - d}` });
    } catch { results = []; }
    if (mine !== seq) return;
    list.innerHTML = results.length
      ? results.map((r, i) => `<li><button type="button" role="option" data-i="${i}">${esc(r.display_name)}</button></li>`).join("")
      : `<li class="muted">No match. Try a nearby landmark or area name, or type your address below.</li>`;
    list.hidden = false;
  };
  input.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(run, 600); }); // Nominatim allows ~1 request/second
  input.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); clearTimeout(timer); run(); } if (e.key === "Escape") close(); });
  list.addEventListener("click", (e) => { const b = e.target.closest("button[data-i]"); if (b) choose(results[Number(b.dataset.i)]); });
}

async function mountGoogle(el, start, has, pick) {
  const g = window.google.maps;
  const m = new g.Map(el, { center: start, zoom: has ? 16 : 12, disableDefaultUI: true, zoomControl: true, gestureHandling: "greedy", clickableIcons: false });
  const mk = new g.Marker({ position: start, map: m, draggable: true });
  geocoder = new g.Geocoder();
  pin = {
    move: (lat, lng, zoom) => { const p = { lat, lng }; mk.setPosition(p); m.panTo(p); if (zoom) m.setZoom(zoom); },
    resize: () => g.event.trigger(m, "resize"),
  };
  mk.addListener("dragend", (e) => pick(e.latLng.lat(), e.latLng.lng()));
  m.addListener("click", (e) => pick(e.latLng.lat(), e.latLng.lng()));
  try {
    const { PlaceAutocompleteElement } = await g.importLibrary("places");
    const ac = new PlaceAutocompleteElement({ includedRegionCodes: ["in"] });
    ac.setAttribute("placeholder", "Or search your building or area");
    $("#placeSearch").appendChild(ac);
    const onPick = async (place) => {
      await place.fetchFields({ fields: ["location", "formattedAddress", "displayName"] });
      if (!place.location) return;
      draft.address = "";
      setPoint(place.location.lat(), place.location.lng(), place.formattedAddress || place.displayName || "");
      pin.move(place.location.lat(), place.location.lng(), 17);
    };
    ac.addEventListener("gmp-select", (e) => onPick(e.placePrediction.toPlace()));
    ac.addEventListener("gmp-placeselect", (e) => onPick(e.place)); // older API versions
  } catch (e) { console.warn("Places search unavailable", e); }
}

function useGps() {
  const msg = $("#gpsMsg"), btn = $("#gpsBtn");
  if (!navigator.geolocation) { msg.textContent = "Location isn't available in this browser. Search or type your address below."; return; }
  if (!window.isSecureContext) { msg.textContent = "Location only works on the https:// address. Search or type your address below."; return; }
  msg.textContent = "Finding you…";
  if (btn) btn.disabled = true;
  const done = () => { if (btn) btn.disabled = false; };
  const ok = async (p) => {
    done();
    const { latitude: lat, longitude: lng, accuracy } = p.coords;
    msg.textContent = accuracy > 150 ? "Found you roughly. Drag the pin to your door." : "Found you. Check the pin is on your building.";
    setPoint(lat, lng, "");
    if (pin) pin.move(lat, lng, 17);
    const addr = await reverseGeocode(lat, lng);
    if (addr && draft.lat === lat) setPoint(lat, lng, addr);
  };
  const fail = (err, retried) => {
    // High accuracy can time out indoors; one quick retry with the network position usually works.
    if (err.code !== 1 && !retried) {
      navigator.geolocation.getCurrentPosition(ok, (e) => fail(e, true), { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 });
      return;
    }
    done();
    msg.textContent = err.code === 1
      ? "Location is blocked for this site. Allow it in your browser settings, or search for your address instead."
      : "Couldn't get your location. Search for your area or tap the map instead.";
  };
  navigator.geolocation.getCurrentPosition(ok, (e) => fail(e, false), { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 });
}

function stepFinish() {
  const q = quote(cfg, draft);
  const row = (k, v) => `<div class="sumrow"><span>${k}</span><span>${v}</span></div>`;
  const extras = Boolean(draft.natural.length || draft.instagram || draft.notes);
  return `
  <h3 class="step-title">Your inspo and number</h3>
  <p class="step-sub">After this you can pay a small advance to hold your slot. We check your inspo and confirm on WhatsApp.</p>
  <div class="group">
    <span class="label">Inspo pictures</span>
    <label class="drop" data-kind="inspo"><input type="file" accept="image/*" multiple data-kind="inspo">
      <b>Add inspo pics</b><small>Screenshots from Instagram or Pinterest are perfect (up to ${MAX_INSPO})</small></label>
    <div class="thumbs" id="thumbs-inspo"></div>
  </div>
  <div class="fields group">
    <div class="field"><label for="name">Your name</label><input id="name" autocomplete="name" value="${esc(draft.name)}" required></div>
    <div class="field"><label for="phone">WhatsApp number</label><input id="phone" type="tel" inputmode="tel" autocomplete="tel" placeholder="10-digit mobile number" value="${esc(draft.phone)}" required></div>
  </div>
  <details class="extra" ${extras ? "open" : ""}>
    <summary>Add more details <span class="opt">(optional)</span></summary>
    <div class="group">
      <span class="label">Your natural nails</span>
      <label class="drop" data-kind="natural"><input type="file" accept="image/*" multiple data-kind="natural">
        <b>Add photos of your nails</b><small>Helps us pick the right extension. Both hands, in daylight.</small></label>
      <div class="thumbs" id="thumbs-natural"></div>
    </div>
    <div class="fields">
      <div class="field"><label for="instagram">Instagram</label><input id="instagram" placeholder="@yourhandle" value="${esc(draft.instagram)}"></div>
      <div class="field wide"><label for="notes">Anything else?</label><textarea id="notes" placeholder="Allergies, nail concerns, occasion…">${esc(draft.notes)}</textarea></div>
    </div>
  </details>
  <div class="summary" aria-label="Price estimate">
    ${row(esc(draft.extension?.name || ""), money(q.base))}
    ${draft.length ? row(`${esc(draft.length.name)} length`, q.length ? money(q.length) : "included") : ""}
    ${draft.addOns.map((a) => row(esc(a.name), Number(a.price) > 0 ? money(a.price) : "price on approval")).join("")}
    ${row(draft.mode === "home" ? "Travel" : "Studio visit", draft.mode !== "home" ? "free" : travelByDistance(cfg) ? (q.travel ? money(q.travel) : "free") : "confirmed by owner")}
    <div class="sumrow total"><span>Estimated total</span><span>${money(q.total)}</span></div>
    <div class="sumrow adv"><span>Advance after approval (${cfg.advancePercent}%)</span><span>${money(q.advance)}</span></div>
    <div class="sumrow"><span>${esc(prettyDate(draft.date))} · ${esc(slotLabel(draft.slot || "00:00"))}</span><span>${draft.mode === "home" ? "Home visit" : "Studio"}</span></div>
  </div>`;
}

function renderThumbs() {
  for (const kind of ["inspo", "natural"]) {
    const el = $(`#thumbs-${kind}`); if (!el) continue;
    el.innerHTML = draft[kind].map((p, i) => `<div class="thumb"><img src="${p.url}" alt="${kind === "inspo" ? "Inspo" : "Natural nails"} photo ${i + 1}"><button type="button" data-remove="${kind}:${i}" aria-label="Remove photo ${i + 1}">×</button></div>`).join("");
  }
}

function addFiles(kind, files) {
  const max = kind === "inspo" ? MAX_INSPO : MAX_NATURAL;
  const imgs = [...files].filter((f) => f.type.startsWith("image/") || /\.(heic|heif)$/i.test(f.name));
  const room = max - draft[kind].length;
  if (imgs.length > room) toast(`You can add up to ${max} photos here.`);
  imgs.slice(0, Math.max(0, room)).forEach((f) => draft[kind].push({ file: f, url: URL.createObjectURL(f) }));
  renderThumbs();
}

/* ------------------------------------------------------------------ */
/* Validation and navigation                                           */
/* ------------------------------------------------------------------ */

function collect() {
  if (step === 3) {
    for (const k of ["name", "phone", "instagram", "notes"]) { const el = $("#" + k); if (el) draft[k] = el.value.trim(); }
    // "Where are you from" is no longer asked: the area comes from the address (or the studio).
    draft.from = draft.mode === "home" ? areaOf(draft.address) : "Studio visit";
  }
  if (step === 2) { const a = $("#address"); if (a) draft.address = a.value.trim(); }
}

function validate() {
  if (step === 0) {
    if (!draft.extension) return "Choose an extension type.";
    if (!draft.length) return "Choose a length.";
  }
  if (step === 1) {
    if (!draft.date) return "Choose a date.";
    if (!draft.slot) return "Choose a time.";
  }
  if (step === 2 && draft.mode === "home") {
    if (draft.lat == null) return "Tap “Use my current location”, search your area, or tap the map so we can find you.";
    if (draft.address.length < 8) return "Add your full address so we can find your door.";
  }
  if (step === 3) {
    if (draft.inspo.length === 0) return "Add at least one inspo picture.";
    if (!draft.name) return "Add your name.";
    if (draft.phone.replace(/\D/g, "").length < 10) return "Add a WhatsApp number with at least 10 digits.";
  }
  return "";
}

/** A short area name from a typed address: the last part that isn't a PIN code, city or state. */
function areaOf(address) {
  const skip = /^(chennai|tamil nadu|india|chengalpattu|\d{6}|tn)$/i;
  const parts = String(address || "").split(/[,\n]/).map((x) => x.trim().replace(/\s*-?\s*\d{6}$/, "")).filter((x) => x && !skip.test(x));
  return (parts.length > 1 ? parts[parts.length - 1] : parts[0] || "").slice(0, 120);
}

function showError(m) {
  const e = $("#formError"); e.textContent = m; e.hidden = false;
  e.scrollIntoView({ behavior: "smooth", block: "center" });
  toast(m); // the message can sit below the sticky footer on phones; a toast is always seen
}
function hideError() { $("#formError").hidden = true; }

async function next(e) {
  e.preventDefault();
  collect();
  const err = validate();
  if (err) { showError(err); return; }
  if (step < STEPS.length - 1) {
    step++; renderStep();
    $("#book").scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
    return;
  }
  await submit();
}

async function submit() {
  const btn = $("#nextBtn"); btn.disabled = true;
  const overlay = $("#overlay"); overlay.hidden = false;
  const bar = $("#overlayBar"), text = $("#overlayText");
  try {
    const id = backend.newId();
    const all = [...draft.inspo.map((p, i) => ({ ...p, kind: "inspo", i })), ...draft.natural.map((p, i) => ({ ...p, kind: "natural", i }))];
    const progress = new Array(all.length).fill(0);
    const tick = () => { bar.style.width = `${Math.round((progress.reduce((a, b) => a + b, 0) / Math.max(1, all.length)) * 92)}%`; };
    text.textContent = all.length ? `Uploading ${all.length} photo${all.length > 1 ? "s" : ""}…` : "Sending your request…";
    const urls = await Promise.all(all.map(async (p, n) => {
      const blob = await shrink(p.file);
      const ext = blob.type === "image/jpeg" ? "jpg" : (p.file.name.split(".").pop() || "img");
      return { kind: p.kind, url: await backend.upload(id, `${p.kind}_${p.i + 1}.${ext}`, blob, (f) => { progress[n] = f; tick(); }) };
    }));
    text.textContent = "Sending your request…";
    const q = quote(cfg, draft);
    await backend.create(id, {
      name: draft.name, phone: draft.phone, from: draft.from, instagram: draft.instagram, notes: draft.notes,
      extension: draft.extension, length: draft.length, shape: draft.shape, addOns: draft.addOns,
      date: draft.date, slot: draft.slot, serviceMode: draft.mode,
      location: draft.mode === "home" ? { lat: draft.lat, lng: draft.lng, address: draft.address } : null,
      distanceKm: draft.mode === "home" ? Math.round(draft.distanceKm * 10) / 10 : 0,
      inspoUrls: urls.filter((u) => u.kind === "inspo").map((u) => u.url),
      naturalUrls: urls.filter((u) => u.kind === "natural").map((u) => u.url),
      price: q,
    });
    bar.style.width = "100%";
    draft.inspo.concat(draft.natural).forEach((p) => URL.revokeObjectURL(p.url));
    const sent = { ...draft, q };
    draft = freshDraft(); step = 0;
    try { sessionStorage.removeItem("tide.draft"); } catch {}
    showSuccess(sent, id);
  } catch (err) {
    console.error(err);
    showError("We couldn't send your request. Check your connection and try again. Your details are still here.");
  } finally {
    overlay.hidden = true; btn.disabled = false;
  }
}

function showSuccess(sent, id) {
  $("#stepper").innerHTML = "";
  $("#stepBody").innerHTML = `
  <div class="success">
    <svg class="bunny bunny-hop" aria-hidden="true"><use href="#bunny"/></svg>
    <h3 class="step-title">Request sent! One last thing</h3>
    <p>Thank you, ${esc(sent.name)}. Pay the advance now to hold ${esc(prettyDate(sent.date))} at ${esc(slotLabel(sent.slot))} for you. We'll check your inspo and confirm on WhatsApp.</p>
    ${payBlock({ id, status: "requested", price: sent.q })}
    <a class="btn btn-quiet" href="#mine">See my bookings</a>
  </div>`;
  $(".step-foot").hidden = true;
  drawQrs();
  $("#book").scrollIntoView({ behavior: "smooth" });
}

/* ------------------------------------------------------------------ */
/* My bookings + advance payment                                       */
/* ------------------------------------------------------------------ */

const STATUS = {
  requested: "Pay advance to hold your slot",
  approved: "Approved · pay advance",
  payment_submitted: "Slot held · checking payment",
  confirmed: "Confirmed",
  completed: "Done",
  declined: "Not available",
};

function upiLink(b) {
  const p = new URLSearchParams({ pa: cfg.upiId, pn: cfg.upiName, am: String(b.price.advance), cu: "INR", tn: `Tide and Tea advance ${b.id.slice(-6)}` });
  return `upi://pay?${p.toString()}`;
}

let myBookings = [];
/** WhatsApp link to the studio about one booking, or "" when no number is set. */
function waLink(b) {
  let n = String(cfg.whatsapp || document.getElementById("book")?.dataset.wa || "").replace(/\D/g, "");
  if (!n) return "";
  if (n.length === 10) n = "91" + n;
  const text = `Hi Tide & Tea! About my booking for ${b.extension?.name || "a nail set"} on ${prettyDate(b.date)} at ${b.slot} (ref ${b.id.slice(-6)}): `;
  return `https://wa.me/${n}?text=${encodeURIComponent(text)}`;
}

/** UPI QR, pay button and the box for the transaction ID. Works for 'requested' and 'approved'. */
function payBlock(b) {
  const price = b.price || {};
  const approved = b.status === "approved";
  return `
  <div class="pay">
    <div class="qr" hidden data-qr="${esc(upiLink(b))}" aria-label="UPI QR code for ${money(price.advance)}"></div>
    <div class="pay-body">
      <div><b>Pay ${money(price.advance)} advance</b> to ${approved ? "confirm" : "hold"} your slot. Balance ${money((price.total || 0) - (price.advance || 0))} is paid on the day.</div>
      ${approved ? "" : `<div class="muted small">Scan the QR with any UPI app, or tap the button on your phone. If we can't do your set, we refund the advance in full.</div>`}
      <div class="pay-row"><a class="btn btn-primary btn-sm" href="${esc(upiLink(b))}">Pay with UPI app</a></div>
      <div class="upi">UPI ID: ${esc(cfg.upiId)} <button type="button" class="linkbtn" data-copy="${esc(cfg.upiId)}">Copy</button></div>
      <div class="pay-row" data-pay="${esc(b.id)}">
        <input name="ref" placeholder="UPI transaction ID (12 digits)" inputmode="numeric" aria-label="UPI transaction ID">
        <button type="button" class="btn btn-pink btn-sm" data-paid>I've paid</button>
      </div>
    </div>
  </div>`;
}

/** Draws QR codes once the QR library has loaded (it loads with defer, so retry briefly). */
function drawQrs(tries = 0) {
  if (!window.QRCode) { if (tries < 20) setTimeout(() => drawQrs(tries + 1), 250); return; }
  document.querySelectorAll("[data-qr]").forEach((el) => {
    if (!el.dataset.done) {
      new window.QRCode(el, { text: el.dataset.qr, width: 240, height: 240, colorDark: "#241238", colorLight: "#ffffff", correctLevel: window.QRCode.CorrectLevel.M });
      el.dataset.done = "1";
      el.hidden = false;
    }
  });
}

async function sendPaid(btn) {
  const box = btn.closest("[data-pay]"); if (!box) return;
  const ref = box.querySelector("input[name=ref]").value.trim();
  if (ref.replace(/\D/g, "").length < 6) { toast("Enter the transaction ID from your UPI app."); return; }
  btn.disabled = true;
  try {
    await backend.submitPayment(box.dataset.pay, ref);
    toast("Thanks! Your slot is held. We'll confirm on WhatsApp shortly.");
    const succ = btn.closest(".success");
    if (succ) succ.querySelector(".pay").outerHTML = `<div class="note">Payment reference <b>${esc(ref)}</b> received. Your slot is held, and we'll confirm on WhatsApp shortly.</div>`;
  } catch (err) { console.error(err); toast("Couldn't save that. Try again."); btn.disabled = false; }
}

function renderMine(list) {
  myBookings = list;
  const sec = $("#mine");
  sec.hidden = list.length === 0; $("#navMine").hidden = list.length === 0;
  $("#myBookings").innerHTML = list.map((b) => {
    const st = b.status || "requested";
    const price = b.price || {};
    let action = "";
    if (st === "requested" || st === "approved") {
      action = payBlock(b);
    } else if (st === "payment_submitted") {
      action = `<div class="note">We received your payment reference <b>${esc(b.paymentRef || "")}</b>. Your slot is held, and we'll confirm shortly.</div>`;
    } else if (st === "confirmed") {
      action = `<div class="note">You're booked! ${b.serviceMode === "home" ? "We'll arrive at your address" : "See you at the studio"} at ${esc(b.slot)}.</div>`;
    } else if (st === "declined") {
      action = `<div class="note">${esc(b.ownerNote || "This slot didn't work out. Please try another date.")}</div>`;
    }
    const note = b.ownerNote && st !== "declined" ? `<div class="note"><b>From Tide & Tea:</b> ${esc(b.ownerNote)}</div>` : "";
    const wa = waLink(b);
    const chatBtn = wa ? `<div class="pay-row"><a class="btn btn-quiet btn-sm" href="${esc(wa)}" target="_blank" rel="noopener">Message us on WhatsApp</a></div>` : "";
    const demo = "";
    return `
    <article class="card mybk">
      <div class="mybk-top"><h3>${esc(b.extension?.name || "Nail set")}${b.length ? ` · ${esc(b.length.name)}` : ""}</h3><span class="pill st-${esc(st)}">${esc(STATUS[st] || st)}</span></div>
      <div class="meta"><span>${esc(prettyDate(b.date))} at ${esc(b.slot)}</span><span>${b.serviceMode === "home" ? "Home visit" : "Studio visit"}</span><span>Total ${money(price.total)}</span></div>
      ${note}${action}${chatBtn}${demo}
    </article>`;
  }).join("");
  drawQrs();
}

/* ------------------------------------------------------------------ */
/* Price list                                                          */
/* ------------------------------------------------------------------ */

function renderPrices() {
  $("#priceList").outerHTML = `<div id="priceList">${priceListHtml(cfg)}</div>`;
  const fs = $("#footStudio"); if (fs) fs.textContent = cfg.studio.address;
}

/* ------------------------------------------------------------------ */
/* Events                                                              */
/* ------------------------------------------------------------------ */

let toastT;
function toast(m) { const t = $("#toast"); t.textContent = m; t.hidden = false; clearTimeout(toastT); toastT = setTimeout(() => (t.hidden = true), 2600); }

function bind() {
  const form = $("#stepForm");
  form.addEventListener("submit", next);
  $("#backBtn").addEventListener("click", () => { collect(); if (step > 0) { step--; renderStep(); } });

  form.addEventListener("change", (e) => {
    const t = e.target;
    if (t.name === "ext") draft.extension = cfg.extensionTypes.find((x) => x.id === t.value) || null;
    if (t.name === "len") draft.length = cfg.lengths.find((x) => x.id === t.value) || null;
    if (t.name === "shape") draft.shape = t.value;
    if (t.name === "addon") draft.addOns = [...form.querySelectorAll('input[name="addon"]:checked')].map((i) => cfg.addOns.find((a) => a.id === i.value)).filter(Boolean);
    if (t.name === "date") { draft.date = t.value; draft.slot = ""; watchTaken(); const el = $("#slotChips"); if (el) el.innerHTML = slotChips(); }
    if (t.name === "slot") draft.slot = t.value;
    if (t.name === "mode") { draft.mode = t.value; $("#homeLoc").hidden = draft.mode !== "home"; $("#studioLoc").hidden = draft.mode !== "studio"; if (pin) setTimeout(pin.resize, 30); }
    if (t.type === "file") { addFiles(t.dataset.kind, t.files); t.value = ""; }
    hideError(); updateTotal(); saveDraft();
  });
  form.addEventListener("input", (e) => { if (e.target.id === "address") draft.address = e.target.value; });
  form.addEventListener("click", (e) => {
    const rm = e.target.closest("[data-remove]");
    if (rm) {
      const [kind, i] = rm.dataset.remove.split(":");
      const [p] = draft[kind].splice(Number(i), 1); if (p) URL.revokeObjectURL(p.url);
      renderThumbs();
    }
    if (e.target.closest("#gpsBtn")) useGps();
  });
  form.addEventListener("dragover", (e) => { const d = e.target.closest(".drop"); if (d) { e.preventDefault(); d.classList.add("drag"); } });
  form.addEventListener("dragleave", (e) => { const d = e.target.closest(".drop"); if (d) d.classList.remove("drag"); });
  form.addEventListener("drop", (e) => { const d = e.target.closest(".drop"); if (d) { e.preventDefault(); d.classList.remove("drag"); addFiles(d.dataset.kind, e.dataTransfer.files); } });

  // Pay boxes live in My bookings and on the success screen, so listen on the whole page.
  document.addEventListener("click", async (e) => {
    const paid = e.target.closest("[data-paid]");
    if (paid) { e.preventDefault(); sendPaid(paid); return; }
    const c = e.target.closest("[data-copy]");
    if (c) { try { await navigator.clipboard.writeText(c.dataset.copy); toast("UPI ID copied"); } catch { toast(c.dataset.copy); } }
    const d = e.target.closest("[data-demo-approve]");
    if (d && backend.demoApprove) backend.demoApprove(d.dataset.demoApprove);
  });
}

/* ------------------------------------------------------------------ */
/* Boot                                                                */
/* ------------------------------------------------------------------ */

async function boot() {
  $("#previewBanner").hidden = !PREVIEW;
  loadDraft();
  // Arriving from a "book this set" link (e.g. the shop's Custom press-on card): pick that set and start at step 1.
  const want = new URLSearchParams(location.search).get("set");
  if (want) {
    const t = cfg.extensionTypes.find((x) => x.id === want);
    if (t) { draft.extension = t; step = 0; saveDraft(); }
    history.replaceState(null, "", location.pathname + location.hash);
  }
  bind();
  renderPrices();
  renderStep();
  try {
    backend = PREVIEW ? previewBackend() : await supabaseBackend();
  } catch (e) {
    console.error(e);
    showError("We couldn't connect. Check your internet and refresh the page.");
    return;
  }
  backend.watchConfig((remote) => {
    cfg = mergeConfig(remote);
    renderPrices();
    // Re-resolve selections against the live price list so prices are current.
    if (draft.extension) draft.extension = cfg.extensionTypes.find((x) => x.id === draft.extension.id) || null;
    if (draft.length) draft.length = cfg.lengths.find((x) => x.id === draft.length.id) || null;
    draft.addOns = draft.addOns.map((a) => cfg.addOns.find((x) => x.id === a.id)).filter(Boolean);
    if (draft.lat != null) draft.distanceKm = distanceKm({ lat: draft.lat, lng: draft.lng }, cfg.studio);
    if (step !== 2 && step !== 3) renderStep(); else updateTotal();
  });
  backend.watchMine(renderMine);
  if (step === 1) watchTaken();
  loadMaps(); // warm up in the background so the map step opens instantly
}

boot();
