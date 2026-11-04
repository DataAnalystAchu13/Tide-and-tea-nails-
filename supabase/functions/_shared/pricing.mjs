// Press-on shop pricing. One source of truth for the website (display) and the
// Cloud Functions (what is actually charged). Pure functions, no Firebase imports.
//
// Product:  { id, name, price, salePrice?, stock, madeToOrder, visible, sizes? }
// Site:     { sale: { enabled, percent, label, startsAt?, endsAt? }, shipping: { flat, freeAbove } }
// Coupon:   { code, type: "percent" | "flat", value, minOrder?, maxDiscount?, maxUses?, uses?, expiresAt?, active }
// Dates are ISO strings ("2026-11-20", read as India time) or millisecond numbers.

const MAX_QTY = 10;

const toMs = (d) => {
  if (d == null || d === "") return null;
  if (typeof d === "number") return d;
  if (typeof d === "object" && typeof d.toMillis === "function") return d.toMillis();
  const ms = Date.parse(/^\d{4}-\d{2}-\d{2}$/.test(d) ? `${d}T00:00:00+05:30` : d);
  return Number.isNaN(ms) ? null : ms;
};

const whole = (n) => Math.max(0, Math.round(Number(n) || 0));

/** True while a site-wide sale is switched on and inside its dates (end date counts in full). */
export function saleActive(site, now = Date.now()) {
  const s = site && site.sale;
  if (!s || !s.enabled || !(Number(s.percent) > 0)) return false;
  const start = toMs(s.startsAt);
  let end = toMs(s.endsAt);
  if (end != null && typeof s.endsAt === "string" && s.endsAt.length <= 10) end += 86400000 - 1;
  if (start != null && now < start) return false;
  if (end != null && now > end) return false;
  return true;
}

/** What one unit costs right now: the lower of the product's own sale price and the site-wide sale. */
export function unitPrice(product, site, now = Date.now()) {
  const price = whole(product.price);
  let best = price;
  const own = whole(product.salePrice);
  if (own > 0 && own < best) best = own;
  if (saleActive(site, now) && !product.excludeFromSale) {
    const pct = Math.min(90, Number(site.sale.percent));
    const sitePrice = Math.round(price * (1 - pct / 100));
    if (sitePrice < best) best = sitePrice;
  }
  return best;
}

/** For badges: { price, unit, percentOff } where percentOff is 0 when not discounted. */
export function displayPrice(product, site, now = Date.now()) {
  const price = whole(product.price);
  const unit = unitPrice(product, site, now);
  const percentOff = price > 0 && unit < price ? Math.round(((price - unit) / price) * 100) : 0;
  return { price, unit, percentOff };
}

export function inStock(product, qty = 1) {
  return !!product.madeToOrder || whole(product.stock) >= qty;
}

/** Returns a reason string when a coupon can't be used, or "" when it can. */
export function couponProblem(coupon, subtotal, now = Date.now()) {
  if (!coupon || coupon.active === false) return "That code isn't valid.";
  const exp = toMs(coupon.expiresAt);
  const endOfDay = exp != null && typeof coupon.expiresAt === "string" && coupon.expiresAt.length <= 10 ? exp + 86400000 - 1 : exp;
  if (endOfDay != null && now > endOfDay) return "That code has expired.";
  if (coupon.maxUses && whole(coupon.uses) >= whole(coupon.maxUses)) return "That code has been fully used.";
  if (coupon.minOrder && subtotal < whole(coupon.minOrder)) return `That code needs an order of at least ₹${whole(coupon.minOrder).toLocaleString("en-IN")}.`;
  if (!["percent", "flat"].includes(coupon.type) || !(Number(coupon.value) > 0)) return "That code isn't valid.";
  return "";
}

export function couponDiscount(coupon, subtotal) {
  let d = coupon.type === "percent"
    ? Math.floor((subtotal * Math.min(100, Number(coupon.value))) / 100)
    : whole(coupon.value);
  if (coupon.maxDiscount) d = Math.min(d, whole(coupon.maxDiscount));
  return Math.min(d, subtotal);
}

/**
 * Prices a cart.
 * items:    [{ productId, size?, qty }]
 * products: { [id]: product }  (missing or hidden products are reported, not charged)
 * coupon:   the coupon document or null; couponCode is what the customer typed.
 */
export function priceOrder({ items, products, site, coupon = null, couponCode = "", now = Date.now() }) {
  const errors = [];
  const lines = [];
  for (const it of items || []) {
    const p = products[it.productId];
    const qty = Math.min(MAX_QTY, Math.max(1, Math.floor(Number(it.qty) || 1)));
    if (!p || p.visible === false) { errors.push({ productId: it.productId, reason: "This item is no longer available." }); continue; }
    if (!inStock(p, qty)) { errors.push({ productId: it.productId, reason: whole(p.stock) > 0 ? `Only ${whole(p.stock)} left.` : "Sold out." }); continue; }
    if (Array.isArray(p.sizes) && p.sizes.length && it.size && !p.sizes.includes(it.size)) {
      errors.push({ productId: it.productId, reason: "Pick a size again." }); continue;
    }
    const { price, unit } = displayPrice(p, site, now);
    lines.push({ productId: it.productId, name: p.name, size: it.size || "", qty, price, unit, amount: unit * qty });
  }
  const subtotal = lines.reduce((s, l) => s + l.amount, 0);

  let discount = 0;
  let couponError = "";
  let appliedCoupon = "";
  if (couponCode) {
    couponError = couponProblem(coupon, subtotal, now);
    if (!couponError) { discount = couponDiscount(coupon, subtotal); appliedCoupon = coupon.code || couponCode; }
  }

  const ship = (site && site.shipping) || {};
  const afterDiscount = subtotal - discount;
  const freeAbove = whole(ship.freeAbove);
  const shipping = lines.length === 0 ? 0 : freeAbove > 0 && afterDiscount >= freeAbove ? 0 : whole(ship.flat);
  const total = afterDiscount + shipping;
  const savings = lines.reduce((s, l) => s + (l.price - l.unit) * l.qty, 0) + discount;

  return { lines, subtotal, discount, coupon: appliedCoupon, couponError, shipping, total, savings, errors };
}
