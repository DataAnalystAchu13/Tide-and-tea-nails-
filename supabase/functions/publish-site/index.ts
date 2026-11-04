// Owner taps "Publish website" in the app → this asks GitHub to rebuild and deploy the site.
// Needs secrets GH_DISPATCH_TOKEN (fine-grained token with Contents: read and write on the repo) and GH_REPO ("owner/name").
import { admin, caller, cors, isOwner, json, secret } from "../_shared/shop-db.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const user = await caller(req);
  if (!user || !(await isOwner(user.id))) return json({ error: "Only the studio can publish." }, 403);

  const db = admin();
  const { data: row } = await db.from("site_publishes").insert({ requested_by: user.id }).select("id").single();
  const token = (secret("GH_DISPATCH_TOKEN") || "").trim();
  const repo = secret("GH_REPO") || "DataAnalystAchu13/Tide-and-tea-nails-";
  if (!token) {
    await db.from("site_publishes").update({ status: "failed", detail: "GH_DISPATCH_TOKEN not set" }).eq("id", row?.id);
    return json({ ok: false, error: "Publishing isn't set up yet. Your changes are saved and go live with the next deploy." }, 200);
  }
  const res = await fetch(`https://api.github.com/repos/${repo}/dispatches`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, accept: "application/vnd.github+json", "user-agent": "tide-and-tea-publish" },
    body: JSON.stringify({ event_type: "publish-site", client_payload: { by: user.id } }),
  });
  const ok = res.status === 204;
  let why = "";
  if (!ok) {
    const body = (await res.text().catch(() => "")).slice(0, 300);
    let msg = "";
    try { msg = JSON.parse(body).message || ""; } catch { msg = body; }
    why = res.status === 401
      ? "The publish token is wrong or expired. Make a new PUBLISH_TOKEN."
      : res.status === 403 || res.status === 404
        ? "The publish token can't reach the repository. It needs resource owner DataAnalystAchu13, this repository selected, and Contents: Read and write."
        : `GitHub said ${res.status}.`;
    why += msg ? ` (GitHub: ${msg})` : "";
    console.error(`publish dispatch failed: ${res.status} ${body}`);
  }
  await db.from("site_publishes").update({ status: ok ? "dispatched" : "failed", detail: ok ? "" : why }).eq("id", row?.id);
  return json(ok ? { ok: true } : { ok: false, error: `Couldn't start publishing. ${why}` });
});
