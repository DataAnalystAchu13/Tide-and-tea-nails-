// The link-in-bio card: offer an Instagram follow, then continue to the site by itself.
const me = document.querySelector("script[data-seconds]");
const seconds = Number(me && me.dataset.seconds) || 3;
const to = window.__hiTo && window.__hiTo !== "/" ? window.__hiTo : window.__ROOT ? window.__ROOT + "index.html" : "/";
const bar = document.getElementById("hiBar");
const note = document.getElementById("hiNote");
const skip = document.getElementById("hiSkip");
skip.href = to;

let gone = false;
function go() {
  if (gone) return;
  gone = true;
  try { localStorage.setItem("tt.hi.seen", String(Date.now())); } catch {}
  location.replace(to);
}

const start = performance.now();
const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
let paused = false;
function tick(t) {
  if (gone || paused) return;
  const left = Math.max(0, seconds * 1000 - (t - start));
  bar.style.width = `${100 - (left / (seconds * 1000)) * 100}%`;
  note.textContent = left > 0 ? `Taking you to the site in ${Math.ceil(left / 1000)}…` : "Opening the site…";
  if (left <= 0) return go();
  requestAnimationFrame(tick);
}
if (!reduce) requestAnimationFrame(tick); else setTimeout(go, seconds * 1000);

// Following opens Instagram (the app on phones) in a new tab; this tab moves on to the site.
document.getElementById("hiFollow").addEventListener("click", () => {
  paused = true;
  note.textContent = "Thanks! Opening the site…";
  setTimeout(go, 700);
});
skip.addEventListener("click", (e) => { e.preventDefault(); go(); });
