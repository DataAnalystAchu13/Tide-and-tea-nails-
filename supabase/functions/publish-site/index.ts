// Owner taps "Publish website" in the app → this asks GitHub to rebuild and deploy the site.
// Needs secrets GH_DISPATCH_TOKEN (fine-grained token with Actions: write on the repo) and GH_REPO ("owner/name").
import { admin, caller, cors, isOwner, json, secret } from "../_shared/shop-db.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const user = await caller(req);
  if (!user || !(await isOwner(user.id))) return json({ error: "Only the studio can publish." }, 403);

  const db = admin();
  const { data: row } = await db.from("site_publishes").insert({ requested_by: user.id }).select("id").single();
  const token = secret("GH_DISPATCH_TOKEN");
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
  await db.from("site_publishes").update({ status: ok ? "dispatched" : "failed", detail: ok ? "" : `GitHub ${res.status}` }).eq("id", row?.id);
  return json(ok ? { ok: true } : { ok: false, error: "Couldn't start publishing. Try again in a minute." });
});
