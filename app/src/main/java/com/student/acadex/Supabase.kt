package com.student.acadex

/** Supabase project settings. Paste your own from Project Settings > API. */
object Supabase {
    const val URL = "https://xepkjvqkyzikrfmiieaj.supabase.co"
    const val KEY = "sb_publishable_biEr1Mj7vahF8nwzp7Ns2Q_xqkWlTSv" // public key only, never the service_role key

    val isConfigured get() = !URL.contains("YOUR-PROJECT")
}
