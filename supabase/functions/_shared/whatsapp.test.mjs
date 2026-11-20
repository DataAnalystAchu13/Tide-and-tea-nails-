import { test } from "node:test";
import assert from "node:assert/strict";
import { alertText, normalizeNumber, whatsappConfig, whatsappRequests } from "./whatsapp.mjs";

const env = (o) => (k) => o[k];
const booking = {
  phone: "98765 43210", shape: "Almond", length: { name: "Medium" }, service_mode: "home",
  location: { lat: 12.9, lng: 80.2, address: "12 Beach Rd,\nChennai" }, from_place: "Instagram", notes: "",
};
const payload = { title: "New request from Priya", body: "Soft gel · Sat 22 Nov at 11:00 · ₹1,500", data: { type: "booking", bookingId: "b1" } };

test("numbers get India's code and junk is dropped", () => {
  assert.equal(normalizeNumber("98765 43210"), "919876543210");
  assert.equal(normalizeNumber("+91 98765-43210"), "919876543210");
  assert.equal(normalizeNumber("12345"), "");
});

test("WhatsApp stays off until the number and a provider key are set", () => {
  assert.equal(whatsappConfig(env({})), null);
  assert.equal(whatsappConfig(env({ OWNER_WHATSAPP: "9876543210" })), null);
  assert.equal(whatsappConfig(env({ OWNER_WHATSAPP: "9876543210", CALLMEBOT_APIKEY: "k" })).provider, "callmebot");
  const meta = whatsappConfig(env({ OWNER_WHATSAPP: "9876543210, 919000000001", WHATSAPP_TOKEN: "t", WHATSAPP_PHONE_ID: "p" }));
  assert.equal(meta.provider, "meta");
  assert.deepEqual(meta.to, ["919876543210", "919000000001"]);
  assert.equal(meta.template, "new_order_alert");
});

test("booking alert carries the client's chat link and map pin", () => {
  const t = alertText(payload, booking);
  assert.match(t, /^\*New request from Priya\*/);
  assert.match(t, /Client: https:\/\/wa\.me\/919876543210/);
  assert.match(t, /Map: https:\/\/www\.google\.com\/maps\/search\/\?api=1&query=12\.9,80\.2/);
  assert.match(t, /Home visit: 12 Beach Rd, Chennai/);
});

test("order alert lists the items and shipping address", () => {
  const order = { total: 1798, lines: [{ name: "Lilac almond", size: "M", qty: 2 }], customer: { phone: "9876543210", line1: "5 Lake St", city: "Chennai", pincode: "600001" } };
  const t = alertText({ title: "New press-on order AB12CD", body: "Priya · 2 item(s)", data: { type: "order" } }, order);
  assert.match(t, /• Lilac almond \(M\) × 2/);
  assert.match(t, /Ship to: 5 Lake St, Chennai, 600001/);
  assert.match(t, /Total: ₹1,798/);
});

test("CallMeBot gets one GET with the text; Meta gets a template per number without new lines", () => {
  const [cmb] = whatsappRequests({ provider: "callmebot", to: ["919876543210"], apikey: "k" }, payload, booking);
  const u = new URL(cmb.url);
  assert.equal(u.hostname, "api.callmebot.com");
  assert.equal(u.searchParams.get("phone"), "919876543210");
  assert.match(u.searchParams.get("text"), /New request from Priya/);

  const reqs = whatsappRequests({ provider: "meta", to: ["919876543210", "919000000001"], token: "t", phoneId: "p", template: "new_order_alert", lang: "en" }, payload, booking);
  assert.equal(reqs.length, 2);
  const body = JSON.parse(reqs[0].init.body);
  assert.equal(body.template.name, "new_order_alert");
  const params = body.template.components[0].parameters.map((p) => p.text);
  assert.equal(params.length, 3);
  for (const p of params) assert.doesNotMatch(p, /\n/);
});
