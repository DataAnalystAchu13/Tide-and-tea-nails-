// Push notifications to the studio app (Firebase Cloud Messaging).
// Deploy with:  cd website && firebase deploy --only functions
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
// asia-south1 (Mumbai). Change if your Firestore database is in another region.
setGlobalOptions({ region: "asia-south1", maxInstances: 5 });

const db = getFirestore();

/** Sends a notification to every phone signed in to the studio app. Removes dead tokens. */
async function notifyOwners({ title, body, data }) {
  const owners = await db.collection("owners").get();
  const byToken = new Map();
  owners.forEach((o) => (o.get("fcmTokens") || []).forEach((t) => byToken.set(t, o.ref)));
  const tokens = [...byToken.keys()];
  if (!tokens.length) return;

  const res = await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data,
    android: { priority: "high", notification: { channelId: "requests", color: "#3A1D5C" } },
  });

  const dead = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      dead.push(tokens[i]);
    }
  });
  await Promise.all(dead.map((t) => byToken.get(t).update({ fcmTokens: FieldValue.arrayRemove(t) })));
}

const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");

// New booking request from the website.
exports.onNewBooking = onDocumentCreated("bookings/{bookingId}", async (event) => {
  const b = event.data && event.data.data();
  if (!b) return;
  await notifyOwners({
    title: `New request from ${b.name || "a client"}`,
    body: `${(b.extension && b.extension.name) || "Nail set"} · ${b.date} at ${b.slot} · ${money(b.price && b.price.total)}`,
    data: { type: "booking", bookingId: event.params.bookingId },
  });
});

// Client says they paid the advance.
exports.onPaymentSubmitted = onDocumentUpdated("bookings/{bookingId}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (before.status === after.status || after.status !== "payment_submitted") return;
  await notifyOwners({
    title: `${after.name} paid the advance`,
    body: `${money(after.price && after.price.advance)} · UPI ref ${after.paymentRef}. Tap to confirm.`,
    data: { type: "booking", bookingId: event.params.bookingId },
  });
});

// New chat message from a client.
exports.onClientMessage = onDocumentCreated("chats/{bookingId}/messages/{messageId}", async (event) => {
  const m = event.data && event.data.data();
  if (!m || m.from !== "customer") return;
  const chat = await db.doc(`chats/${event.params.bookingId}`).get();
  const name = (chat.exists && chat.get("name")) || "A client";
  const body = m.mediaType === "video" ? "Sent a video" : m.mediaType === "image" ? (m.text || "Sent a photo") : m.text;
  await notifyOwners({
    title: name,
    body: String(body || "").slice(0, 140),
    data: { type: "chat", bookingId: event.params.bookingId },
  });
});
