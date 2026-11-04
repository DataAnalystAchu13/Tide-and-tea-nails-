// Makes a copy of dist/ that works from a sub-folder (for sharing a preview link):
// rewrites root-relative links to relative ones and tells the scripts where the root is.
//   node site/preview.mjs <out-dir>
import { readdir, readFile, writeFile, mkdir, cp, rm } from "node:fs/promises";
import { join, relative, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const dist = fileURLToPath(new URL("../dist", import.meta.url));
const out = process.argv[2];
if (!out) { console.error("usage: node site/preview.mjs <out-dir>"); process.exit(1); }
await rm(out, { recursive: true, force: true });
await cp(dist, out, { recursive: true });
await rm(join(out, "sw.js"), { force: true });

async function* walk(d) {
  for (const e of await readdir(d, { withFileTypes: true })) {
    const p = join(d, e.name);
    if (e.isDirectory()) yield* walk(p); else yield p;
  }
}
const fix = (prefix) => (m, attr, path) => {
  let p = path.replace(/^\//, "");
  const [base, rest = ""] = p.split(/(?=[?#])/);
  let b = base;
  if (b === "" || b.endsWith("/")) b += "index.html";
  return `${attr}="${prefix}${b}${rest}"`;
};
for await (const f of walk(out)) {
  if (f.endsWith(".html")) {
    const depth = relative(out, dirname(f)).split("/").filter(Boolean).length;
    const prefix = "../".repeat(depth) || "./";
    let h = await readFile(f, "utf8");
    h = h.replace(/\b(href|src)="(\/(?!\/)[^"]*)"/g, fix(prefix));
    h = h.replace("<head>", `<head>\n<script>window.__ROOT=${JSON.stringify(prefix)}</script>`);
    h = h.replace(/location\.replace\(t\)/, "location.replace(t==='/'?window.__ROOT+'index.html':t)");
    await writeFile(f, h);
  } else if (f.endsWith(".js")) {
    let j = await readFile(f, "utf8");
    j = j.replace(/from "\/([\w.-]+)"/g, 'from "./$1"');
    await writeFile(f, j);
  }
}
console.log("Preview copy written to", out);
