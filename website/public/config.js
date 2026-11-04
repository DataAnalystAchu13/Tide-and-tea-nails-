// Tide & Tea Nails: website settings. These values are public by design (they ship to every browser);
// Supabase row-level security, not secrecy, is what protects the data.
// 1. SUPABASE_URL and SUPABASE_ANON_KEY: Supabase dashboard > Project Settings > API.
// 2. MAPS_API_KEY: a Google Maps JavaScript API key (Places API enabled), restricted to your domain.
// While SUPABASE_ANON_KEY is still "PASTE_...", the site runs in PREVIEW MODE:
// bookings are kept only in this browser so you can click through the whole flow.
window.TIDE_SETTINGS = {
  SUPABASE_URL: "https://PASTE_PROJECT_REF.supabase.co",
  SUPABASE_ANON_KEY: "PASTE_SUPABASE_ANON_KEY",
  MAPS_API_KEY: "PASTE_GOOGLE_MAPS_KEY",
};
