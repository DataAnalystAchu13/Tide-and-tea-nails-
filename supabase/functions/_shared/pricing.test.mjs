import { test } from "node:test";
import assert from "node:assert/strict";
import { priceOrder, unitPrice, saleActive, displayPrice, couponProblem } from "./pricing.mjs";

const NOW = Date.parse("2026-11-10T12:00:00Z");
const site = { sale: { enabled: false, percent: 20 }, shipping: { flat: 80, freeAbove: 999 } };
const products = {
  a: { id: "a", name: "Lilac almond", price: 899, stock: 3, visible: true, sizes: ["S", "M", "L"] },
  b: { id: "b", name: "Chrome coffin", price: 1200, salePrice: 999, stock: 0, madeToOrder: true, visible: true },
  c: { id: "c", name: "Hidden", price: 500, stock: 5, visible: false },
  d: { id: "d", name: "Sold out", price: 700, stock: 0, visible: true },
};

test("own sale price wins when lower", () => {
  assert.equal(unitPrice(products.b, site, NOW), 999);
  assert.deepEqual(displayPrice(products.b, site, NOW), { price: 1200, unit: 999, percentOff: 17 });
});

test("site-wide sale applies inside its dates only", () => {
  const on = { ...site, sale: { enabled: true, percent: 20, startsAt: "2026-11-01", endsAt: "2026-11-10" } };
  assert.equal(saleActive(on, NOW), true);
  assert.equal(unitPrice(products.a, on, NOW), 719);
  assert.equal(saleActive(on, Date.parse("2026-11-11T00:00:01Z")), false);
  assert.equal(saleActive({ ...on, sale: { ...on.sale, startsAt: "2026-11-15" } }, NOW), false);
});

test("lower of own sale and site sale", () => {
  const on = { ...site, sale: { enabled: true, percent: 30 } };
  assert.equal(unitPrice(products.b, on, NOW), 840); // 1200 * 0.7 beats 999
});

test("cart totals, free shipping threshold", () => {
  const r = priceOrder({ items: [{ productId: "a", size: "M", qty: 1 }], products, site, now: NOW });
  assert.equal(r.subtotal, 899);
  assert.equal(r.shipping, 80);
  assert.equal(r.total, 979);
  const r2 = priceOrder({ items: [{ productId: "a", size: "M", qty: 2 }], products, site, now: NOW });
  assert.equal(r2.shipping, 0);
  assert.equal(r2.total, 1798);
});

test("hidden, sold out and over-stock items are reported, not charged", () => {
  const r = priceOrder({ items: [{ productId: "c", qty: 1 }, { productId: "d", qty: 1 }, { productId: "a", size: "S", qty: 4 }, { productId: "zz", qty: 1 }], products, site, now: NOW });
  assert.equal(r.lines.length, 0);
  assert.equal(r.errors.length, 4);
  assert.equal(r.total, 0);
  assert.match(r.errors[2].reason, /Only 3 left/);
});

test("made to order ignores stock", () => {
  const r = priceOrder({ items: [{ productId: "b", qty: 3 }], products, site, now: NOW });
  assert.equal(r.lines[0].amount, 2997);
});

test("quantity is clamped to 1..10", () => {
  const r = priceOrder({ items: [{ productId: "b", qty: 50 }, { productId: "b", qty: -2 }], products, site, now: NOW });
  assert.deepEqual(r.lines.map((l) => l.qty), [10, 1]);
});

test("invalid size is rejected", () => {
  const r = priceOrder({ items: [{ productId: "a", size: "XXL", qty: 1 }], products, site, now: NOW });
  assert.equal(r.lines.length, 0);
});

test("percent coupon with cap, and minimum order", () => {
  const coupon = { code: "BUNNY10", type: "percent", value: 10, maxDiscount: 150, minOrder: 500, active: true };
  const r = priceOrder({ items: [{ productId: "b", qty: 2 }], products, site, coupon, couponCode: "bunny10", now: NOW });
  assert.equal(r.discount, 150);
  assert.equal(r.coupon, "BUNNY10");
  assert.equal(r.total, 1998 - 150);
  assert.match(couponProblem(coupon, 400, NOW), /at least/);
});

test("expired, used up and missing coupons", () => {
  assert.match(couponProblem({ type: "flat", value: 100, expiresAt: "2026-11-09", active: true }, 1000, NOW), /expired/);
  assert.equal(couponProblem({ type: "flat", value: 100, expiresAt: "2026-11-10", active: true }, 1000, NOW), "");
  assert.match(couponProblem({ type: "flat", value: 100, maxUses: 5, uses: 5, active: true }, 1000, NOW), /fully used/);
  const r = priceOrder({ items: [{ productId: "b", qty: 1 }], products, site, coupon: null, couponCode: "NOPE", now: NOW });
  assert.equal(r.discount, 0);
  assert.match(r.couponError, /isn't valid/);
});

test("flat coupon never exceeds the subtotal and shipping is judged after discount", () => {
  const coupon = { code: "BIG", type: "flat", value: 5000, active: true };
  const r = priceOrder({ items: [{ productId: "a", size: "S", qty: 2 }], products, site, coupon, couponCode: "BIG", now: NOW });
  assert.equal(r.discount, 1798);
  assert.equal(r.shipping, 80);
  assert.equal(r.total, 80);
});
