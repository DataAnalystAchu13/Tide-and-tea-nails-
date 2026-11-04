// Placeholder product pictures for preview builds only: a hand of five painted nails per set.
const sets = [
  ["p1", "#c9b3e6", "#f7f2fb", "#3a1d5c", "almond"],
  ["p2", "#3a1d5c", "#9b7fc4", "#c9a35e", "coffin"],
  ["p3", "#f1e4da", "#fbf6f1", "#8a5a3c", "square"],
  ["p4", "#f4b3cb", "#fde8f0", "#c9a35e", "oval"],
  ["p5", "#a9d3cb", "#eef8f6", "#c9a35e", "stiletto"],
  ["p6", "#ece2f6", "#ffffff", "#c75a86", "almond"],
];
function nail(x, y, w, h, shape, fill, tip) {
  const r = w / 2;
  const top = shape === "square" || shape === "coffin" ? `M${x} ${y + 10}Q${x} ${y} ${x + 8} ${y}H${x + w - 8}Q${x + w} ${y} ${x + w} ${y + 10}`
    : shape === "stiletto" ? `M${x} ${y + h * 0.45}Q${x + r * 0.3} ${y + 6} ${x + r} ${y - 14}Q${x + w - r * 0.3} ${y + 6} ${x + w} ${y + h * 0.45}`
    : `M${x} ${y + r}A${r} ${shape === "almond" ? r * 1.35 : r} 0 0 1 ${x + w} ${y + r}`;
  return `<path d="${top}V${y + h}Q${x + r} ${y + h + 12} ${x} ${y + h}Z" fill="${fill}" stroke="#3a1d5c" stroke-opacity=".25" stroke-width="2"/><path d="${top}" fill="none" stroke="${tip}" stroke-width="6" stroke-linecap="round" opacity=".9"/><ellipse cx="${x + w * 0.32}" cy="${y + h * 0.42}" rx="4" ry="${h * 0.18}" fill="#fff" opacity=".45"/>`;
}
export function demoImages() {
  const out = {};
  for (const [name, fill, bg, tip, shape] of sets) {
    const fingers = [[70, 150, 60, 120], [150, 90, 64, 140], [234, 70, 64, 150], [318, 96, 62, 138], [398, 160, 54, 110]];
    out[`${name}.svg`] = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 520 520"><rect width="520" height="520" fill="${bg}"/><circle cx="430" cy="90" r="10" fill="#c9a35e"/><circle cx="90" cy="440" r="6" fill="#f4b3cb"/>${fingers.map(([x, y, w, h]) => nail(x, y, w, h, shape, fill, tip)).join("")}<text x="260" y="470" text-anchor="middle" font-family="Georgia, serif" font-size="26" fill="#3a1d5c" opacity=".55">sample photo</text></svg>`;
  }
  return out;
}
