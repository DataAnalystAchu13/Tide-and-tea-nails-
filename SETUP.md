# Tide & Tea Nails: booking website + owner app

Two parts share one Firebase project:

| Part | Who uses it | Folder |
|---|---|---|
| Booking website | Customers | `website/` (deploys to Firebase Hosting) |
| Studio app (Android) | You, the owner | `owner-app/` (open in Android Studio) |

## How a booking flows

1. The customer opens the website, picks an extension type, length, shape and add-ons, chooses a date and time, shares their location (GPS or Google Maps search), uploads inspo pictures (and natural nail photos if they want), and sends the request with their name, WhatsApp number and where they're from.
2. The request appears instantly in the studio app under **New**, with the inspo pics, nail pics, extension type and location. Tap **Open in Maps** to see exactly where they are.
3. You adjust the price if needed, add a note, and tap **Approve**. That time slot is then marked as booked on the website.
4. The customer sees **Approved · pay advance** on the website, pays the advance with any UPI app (button or QR code), and enters the UPI transaction ID.
5. The booking moves to **Check payment** in the app. You check the reference in your UPI app and tap **Confirm booking**. The advance is added to **Payments**.
6. After the appointment, tap **Paid in cash** or **Paid by UPI** to record the balance and close the booking.

Your phone gets a push notification for every new request, every advance a client says they've paid, and every chat message. Clients can chat with you from their booking on the website and send photos and short videos (up to 60 MB); you reply from the **Chats** tab. **Insights** shows money collected, requests, approval rate, average booking, a weekly chart and your most requested sets.

## One-time setup (about 30 minutes)

### 1. Firebase project
1. Go to https://console.firebase.google.com and create a project (for example `tide-and-tea`).
2. Switch the project to the **Blaze** plan. Photo uploads use Cloud Storage, which new Firebase projects can only turn on with Blaze. A small studio stays inside the free allowance, but set a budget alert (for example ₹500) under Billing to be safe.

### 2. Sign-in
Firebase console → **Authentication** → Sign-in method:
- Turn on **Anonymous** (customers book without making an account).
- Turn on **Email/Password**.
- Under **Users**, click **Add user** and create your studio login (your email and a strong password). Copy the **User UID** shown in the list.

### 3. Database and photo storage
1. **Firestore Database** → Create database → choose the region closest to you (for India: `asia-south1`) → start in production mode.
2. In Firestore, click **Start collection**, name it `owners`. Use your **User UID** from step 2 as the document ID, and add one field `role` = `owner`. This is what lets your login see bookings. Add more UIDs later for team members.
3. **Storage** → Get started (same region).

### 4. Google Maps key (for the location step on the website)
1. Open https://console.cloud.google.com, select the same project.
2. Enable **Maps JavaScript API**, **Places API (New)** and **Geocoding API**.
3. Credentials → Create API key. Restrict it to **Websites**: `https://YOUR-PROJECT.web.app/*` and `https://YOUR-PROJECT.firebaseapp.com/*` (plus your own domain if you add one).
Without a key the site still works: customers tap **Use my current location** and type their address, but there's no map or address search.

### 5. Put the website online
1. Firebase console → Project settings → **Your apps** → add a **Web** app. Copy the `firebaseConfig` values into `website/public/config.js`, and paste the Maps key into `MAPS_API_KEY`.
2. On your computer, install Node.js, then run:
   ```
   npm install -g firebase-tools
   firebase login
   cd website
   firebase use --add        # pick your project
   firebase deploy           # uploads the site and the security rules
   ```
3. Your site is live at `https://YOUR-PROJECT.web.app`. Put this link in your Instagram bio.
4. Turn on push notifications: in the same `website` folder run
   ```
   cd functions && npm install && cd ..
   firebase deploy --only functions
   ```
   This uploads three small Cloud Functions that notify your phone. They run in `asia-south1` (Mumbai); change the region at the top of `functions/index.js` if your database is elsewhere. They stay well inside the free allowance for a studio.

### 6. Build the studio app
1. Firebase console → Project settings → add an **Android** app with package name `com.tideandtea.owner`. Download `google-services.json` and put it in `owner-app/app/`.
2. Open the `owner-app` folder in Android Studio, let it sync, plug in your phone (USB debugging on), and press **Run**.
3. Sign in with the email and password from step 2.
4. Allow notifications when the app asks.
5. Tap **⋮ → Publish starter price list** once. This copies the starter prices to Firestore so the website shows them.

### 7. Connect tideandteanails.in
1. Firebase console → **Hosting** → **Add custom domain** → enter `tideandteanails.in` (then add `www.tideandteanails.in` and choose to redirect it to the main domain).
2. Firebase shows a **TXT** record and one or two **A** records. Log in to the company you bought the domain from (GoDaddy, Hostinger, BigRock and so on), open DNS settings for tideandteanails.in, and add exactly those records. Remove any old A records for `@`.
3. Wait for Firebase to show **Connected** (usually under an hour, sometimes up to 24). The SSL certificate is set up automatically.
4. Add `https://tideandteanails.in/*` to the Maps API key's website restrictions (step 4).

### 8. Automatic deploys from GitHub (optional)
The workflow in `.github/workflows/deploy-website.yml` republishes the site every time `website/` changes on `main`.
1. Google Cloud console → IAM → Service accounts → create one with the **Firebase Hosting Admin** role → Keys → Add key → JSON.
2. GitHub repo → Settings → Secrets and variables → Actions → add `FIREBASE_SERVICE_ACCOUNT` (paste the whole JSON) and `FIREBASE_PROJECT_ID`.
Security rules still deploy with `firebase deploy` from your computer.

## Changing prices, slots, studio location and UPI ID

Everything customers see lives in one Firestore document: **config/main**. Edit it in the Firebase console and the website updates instantly, no redeploy needed.

| Field | What it controls |
|---|---|
| `extensionTypes`, `lengths`, `addOns`, `shapes` | Price list and booking choices |
| `travel.slabs`, `travel.perKmBeyond` | Home-visit charge by distance |
| `studio.lat`, `studio.lng`, `studio.address` | Where distance is measured from. **Set this to your real studio location.** |
| `slots`, `workingDays` (1 = Mon … 7 = Sun), `blockedDates` (`"2026-12-25"`), `bookingWindowDays` | When customers can book |
| `advancePercent` | Advance asked after approval |
| `upiId`, `upiName` | Where advance payments go. **Change before going live.** |

All starter prices are placeholders I made up. Replace them with your real prices.

## Good to know
- Customers' bookings are tied to the browser they booked from. If they switch phones, they can still reach you on WhatsApp; you see everything in the app.
- Chat photos and videos are stored in Firebase Storage under `chats/`. Videos are uploaded as-is, so ask clients for short clips.
- Payment history only counts money you confirm in the app (advances when you tap **Confirm booking**, balances when you mark a booking done).
- Security rules (`website/firestore.rules`, `website/storage.rules`) make sure customers only see their own booking, can only add a payment reference after approval, and only owner accounts can read other customers' details, addresses, photo links, chats and payment history. A client can only read and write the chat for their own booking.
