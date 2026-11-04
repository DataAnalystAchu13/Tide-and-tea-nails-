package com.tideandtea.owner.data

/**
 * Supabase project settings (Supabase dashboard > Project Settings > API).
 * Both values are public by design; row-level security in supabase/migrations protects the data.
 */
object SupabaseConfig {
    const val URL = "https://PASTE_PROJECT_REF.supabase.co"
    const val ANON_KEY = "PASTE_SUPABASE_ANON_KEY"
}
