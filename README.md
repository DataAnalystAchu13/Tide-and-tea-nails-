# Tide & Tea Nails

Booking system for Tide & Tea Nails.

- **`website/`**: the customer booking site (price list, extension type, date and time, location with Google Maps, inspo and natural nail photos, UPI advance after approval). Static site on Firebase Hosting, served at tideandteanails.in.
- **`owner-app/`**: the studio's Android app (Kotlin, Jetpack Compose) for reviewing requests, viewing photos, opening client locations in Google Maps, approving and confirming payments.
- Both use one Firebase project (Auth, Firestore, Storage). Security rules live in `website/firestore.rules` and `website/storage.rules`.

Start with **[SETUP.md](SETUP.md)**.

Until `website/public/config.js` has your Firebase settings, the website runs in preview mode and keeps bookings only in the visitor's browser.
