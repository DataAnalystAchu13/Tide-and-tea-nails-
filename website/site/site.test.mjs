import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { md, plain, slugify, esc } from "./md.mjs";
import { igHandle, themeCss } from "./render.mjs";
import { fillBlanks } from "./content.mjs";
import { build } from "./build.mjs";

test("markdown escapes HTML typed in the app", () => {
  const out = md('<script>alert(1)</script>\n\n[x](javascript:alert(1)) **bold**');
  assert.ok(!out.includes("<script>"));
  assert.ok(out.includes("&lt;script&gt;"));
  assert.ok(out.includes('href="#"'));
  assert.ok(out.includes("<strong>bold</strong>"));
});

test("markdown lists and headings", () => {
  const out = md("## Care\n\n- one\n- two\n\n1. a\n2. b");
  assert.equal(out, "<h2>Care</h2>\n<ul><li>one</li><li>two</li></ul>\n<ol><li>a</li><li>b</li></ol>");
});

test("slugs and plain text", () => {
  assert.equal(slugify("T. Nagar"), "t-nagar");
  assert.equal(slugify("Milk Tea & Honey!"), "milk-tea-and-honey");
  assert.ok(plain("x ".repeat(200), 50).length <= 50);
  assert.equal(esc('"<'), "&quot;&lt;");
});

test("instagram handle from any format", () => {
  assert.equal(igHandle("@tide.nails"), "tide.nails");
  assert.equal(igHandle("https://www.instagram.com/tide.nails/?hl=en"), "tide.nails");
  assert.equal(igHandle(""), "");
});

test("preview build writes every page with valid structured data", async () => {
  const { pages } = await build({ quiet: true });
  const paths = pages.map((p) => p.path);
  for (const p of ["/", "/book/", "/services/", "/shop/", "/cart/", "/orders/", "/about/", "/reviews/", "/blog/", "/contact/", "/policies/", "/hi/", "/404.html"]) {
    assert.ok(paths.includes(p), `missing ${p}`);
  }
  for (const p of pages) {
    for (const m of p.html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)) JSON.parse(m[1]);
    if (!p.noindex) assert.match(p.html, /<link rel="canonical" href="https:\/\/tideandteanails\.in\//, p.path);
  }
  const sitemap = await readFile(new URL("../dist/sitemap.xml", import.meta.url), "utf8");
  assert.ok(!sitemap.includes("/cart/") && sitemap.includes("/shop/"));
});

test("owner colours accept only hex codes", () => {
  const c = { site: { theme: { primary: "#112233", accent: "red;}body{display:none", background: "" } } };
  const css = themeCss(c);
  assert.ok(css.includes("--royal:#112233"));
  assert.ok(!css.includes("display:none"));
  assert.equal(themeCss({ site: { theme: {} } }), "");
});

test("blank texts fall back to defaults, hidden sections disappear", async () => {
  const base = { a: "Default", rows: [{ t: "x" }], show: { steps: true } };
  assert.deepEqual(fillBlanks(base, { a: " ", rows: [{ t: "" }], show: { steps: false } }), { a: "Default", rows: [{ t: "x" }], show: { steps: false } });
  const { pages } = await build({ quiet: true });
  const h = pages.find((p) => p.path === "/").html;
  assert.ok(h.includes('class="splash"'), "opening screen with the booking steps");
  assert.ok(h.includes('tt.splash'), "splash shows once per visit");
  assert.ok(h.includes("Pay advance"));
});
