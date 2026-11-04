// A small, safe Markdown subset for content typed in the owner app.
// Supports: ## and ### headings, paragraphs, - bullet and 1. numbered lists,
// **bold**, *italic*, [links](https://...), ![images](https://...), > quotes.
// Everything is HTML-escaped first, so nothing typed in the app can inject markup.

export const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

const safeUrl = (u) => (/^(https:\/\/|\/|mailto:|tel:)/i.test(u) ? u : "#");

function inline(s) {
  return esc(s)
    .replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, (_, alt, src) => `<img src="${safeUrl(src)}" alt="${alt}" loading="lazy" decoding="async">`)
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, t, href) => {
      const u = safeUrl(href);
      const ext = /^https:/i.test(u) && !/tideandteanails\.in/i.test(u);
      return `<a href="${u}"${ext ? ' rel="noopener" target="_blank"' : ""}>${t}</a>`;
    })
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, "$1<em>$2</em>");
}

export function md(src) {
  const lines = String(src ?? "").replace(/\r\n?/g, "\n").split("\n");
  const out = [];
  let para = [];
  let list = null; // { tag, items }
  const flushPara = () => { if (para.length) { out.push(`<p>${inline(para.join(" "))}</p>`); para = []; } };
  const flushList = () => { if (list) { out.push(`<${list.tag}>${list.items.map((i) => `<li>${inline(i)}</li>`).join("")}</${list.tag}>`); list = null; } };
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) { flushPara(); flushList(); continue; }
    let m;
    if ((m = line.match(/^(#{2,3})\s+(.*)$/))) { flushPara(); flushList(); const n = m[1].length; out.push(`<h${n}>${inline(m[2])}</h${n}>`); continue; }
    if ((m = line.match(/^#\s+(.*)$/))) { flushPara(); flushList(); out.push(`<h2>${inline(m[1])}</h2>`); continue; }
    if ((m = line.match(/^>\s?(.*)$/))) { flushPara(); flushList(); out.push(`<blockquote>${inline(m[1])}</blockquote>`); continue; }
    if ((m = line.match(/^[-*•]\s+(.*)$/))) { flushPara(); if (!list || list.tag !== "ul") { flushList(); list = { tag: "ul", items: [] }; } list.items.push(m[1]); continue; }
    if ((m = line.match(/^\d+[.)]\s+(.*)$/))) { flushPara(); if (!list || list.tag !== "ol") { flushList(); list = { tag: "ol", items: [] }; } list.items.push(m[1]); continue; }
    flushList();
    para.push(line);
  }
  flushPara(); flushList();
  return out.join("\n");
}

/** Plain text for meta descriptions: strips Markdown and trims to n characters at a word boundary. */
export function plain(src, n = 155) {
  const t = String(src ?? "")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, "")
    .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
    .replace(/[#>*_`-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  if (t.length <= n) return t;
  const cut = t.slice(0, n - 1);
  return cut.slice(0, cut.lastIndexOf(" ") > 60 ? cut.lastIndexOf(" ") : cut.length) + "…";
}

export function slugify(s) {
  return String(s ?? "")
    .toLowerCase()
    .normalize("NFKD").replace(/[̀-ͯ]/g, "")
    .replace(/&/g, " and ")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 70) || "item";
}
