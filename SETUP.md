# Tide & Tea Nails: booking website + owner app

Two parts share one **Supabase** project (database, logins, photo storage, live updates). Firebase is used only on its free plan, to host the website and to send push notifications to your phone.

| Part | Who uses it | Folder |
|---|---|---|
| Booking website | Customers | `website/` (deploys to Firebase Hosting) |
| Database, security and push function | Both | `supabase/` (deploys to Supabase) |
| Studio app (Android) | You, the owner | `owner-app/` (open in Android Studio) |

## How a booking flows

1. The customer opens the website, picks an extension type, length, shape and add-ons, chooses a date and time, shares their location (GPS or Google Maps search), uploads inspo pictures (and natural nail photos if they want), and sends the request with their name, WhatsApp number and where they're from.
2. The request appears instantly in the studio app under **New**, with the inspo pics, nail pics, extension type and location. Tap **Open in Maps** to see exactly where they are.
3. You adjust the price if needed, add a note, and tap **Approve**. That time slot is then marked as booked on the website.
4. The customer sees **Approved · pay advance** on the website, pays the advance with any UPI app (button or QR code), and enters the UPI transaction ID.
5. The booking moves to **Check payment** in the app. You check the reference in your UPI app and tap **Confirm booking**. The advance is added to **Payments**.
6. After the appointment, tap **Paid in cash** or **Paid by UPI** to record the balance and close the booking.

Your phone gets a push notification for every new request, every advance a client says they've paid, and every chat message. Clients can chat with you from their booking on the website and send photos and short videos (up to 60 MB); you reply from the **Chats** tab. **Insights** shows money collected, requests, approval rate, average booking, a weekly chart and your most requested sets.

## One-time setup (about 40 minutes, no card needed)

### 1. Supabase project (the database)
1. Sign up at https://supabase.com and create a project named `tide-and-tea-nails`, region **Mumbai (ap-south-1)**. Save the database password in your password manager.
2. **Authentication → Sign In / Providers**: turn on **Allow anonymous sign-ins** (customers book without an account). Email is on by default.
3. **Authentication → Users → Add user → Create new user**: your studio email and a strong password, with **Auto confirm user** ticked.
4. **SQL Editor**, run this once with your email so your login can see bookings (add team members the same way):
   ```sql
   insert into public.owners (user_id) select id from auth.users where email = 'you@example.com';
   ```
   Run it after step 3 below, once the first deploy has created the tables.
5. **Project Settings → API**: copy the **Project URL** and the **anon public** key into `website/public/config.js` and `owner-app/app/src/main/java/com/tideandtea/owner/data/SupabaseConfig.kt`. Both are safe to publish. Never put the `service_role` key anywhere in the code.

### 2. Firebase project (free Spark plan: website hosting + push only)
1. https://console.firebase.google.com → create a project named `tide-and-tea-nails`. Stay on the free **Spark** plan.
2. Project settings → **Your apps** → add an **Android** app with package `com.tideandtea.owner`. Download `google-services.json` into `owner-app/app/`.
3. Project settings → **Service accounts** → **Generate new private key**. In Supabase → **Edge Functions → Secrets**, add a secret named `FCM_SERVICE_ACCOUNT` and paste the whole JSON file as its value. This lets Supabase send push notifications to your phone. Delete the downloaded file afterwards.

### 3. Automatic deploys from GitHub
GitHub repo → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Where to get it |
|---|---|
| `SUPABASE_ACCESS_TOKEN` | supabase.com/dashboard/account/tokens → Generate new token |
| `SUPABASE_PROJECT_REF` | the part before `.supabase.co` in your Project URL |
| `SUPABASE_DB_PASSWORD` | the database password from step 1 |
| `FIREBASE_SERVICE_ACCOUNT` | Google Cloud console → IAM → Service accounts → create one with the **Firebase Hosting Admin** role → Keys → Add key → JSON (paste the whole file) |
| `FIREBASE_PROJECT_ID` | `tide-and-tea-nails` (or the ID Firebase gave your project) |

Then **Actions → Deploy Supabase → Run workflow** and **Deploy website → Run workflow**. From then on, every change merged to `main` deploys itself:
- `supabase/` changes apply the database tables, security policies, photo storage and the push function.
- `website/` changes publish the site to `https://<project>.web.app`.

### 4. Google Maps key (location step on the website)
1. https://console.cloud.google.com, select the Firebase project.
2. Enable **Maps JavaScript API**, **Places API (New)** and **Geocoding API**. Maps has a monthly free allowance; Google asks for a card only to switch the key on.
3. Credentials → Create API key → restrict to **Websites**: `https://tideandteanails.in/*`, `https://www.tideandteanails.in/*`, `https://<project>.web.app/*`. Paste it into `MAPS_API_KEY` in `website/public/config.js`.
Without a key the site still works: customers tap **Use my current location** and type their address.

### 5. Build the studio app
1. Open the `owner-app` folder in Android Studio, let it sync, plug in your phone (USB debugging on), and press **Run**.
2. Sign in with the email and password from step 1.3. Allow notifications when asked.
3. The starter price list is already in the database (Table Editor → `config` → `main`).

### 6. Connect tideandteanails.in
1. Firebase console → **Hosting** → **Add custom domain** → `tideandteanails.in`, then add `www.tideandteanails.in` and redirect it to the main domain.
2. Firebase shows a **TXT** record and one or two **A** records. At the company you bought the domain from, open DNS settings for tideandteanails.in, add exactly those records, and remove any old A records for `@`.
3. Wait for Firebase to show **Connected** (usually under an hour, up to 24). SSL is set up automatically.
4. Supabase → **Authentication → URL Configuration**: set Site URL to `https://tideandteanails.in`.

## Changing prices, slots, studio location and UPI ID

Everything customers see lives in one database row: Supabase → **Table Editor → `config` → `main` → `data`**. Edit the JSON there and the website updates instantly, no redeploy needed.

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
- Inspo photos are stored in the Supabase `bookings` bucket and chat photos and videos in the `chats` bucket. Files sit under random IDs, so a link only works for someone who was given it, the same model as Instagram DM media. Videos are uploaded as-is, so ask clients for short clips. The free plan includes 1 GB of storage.
- Payment history only counts money you confirm in the app (advances when you tap **Confirm booking**, balances when you mark a booking done).
- Row-level security in `supabase/migrations/` makes sure customers only see their own booking, can only add a payment reference after approval, and only owner accounts can read other customers' details, addresses, photo links, chats and payment history. A client can only read and write the chat for their own booking.
- Taken slots, payment history and chat summaries are written by the database itself (triggers), so they stay correct even if the app is closed mid-way.
- Supabase pauses free projects after 7 days with no activity at all. Any booking, app sign-in or website visit that loads prices counts as activity.
