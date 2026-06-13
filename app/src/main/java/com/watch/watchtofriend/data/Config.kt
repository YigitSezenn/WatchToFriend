package com.watch.watchtofriend.data

import com.watch.watchtofriend.BuildConfig

// YouTube Data API v3 — anahtar local.properties içinde youtube.api.key=... (git'e girmez).
// Google Cloud Console'da Android paket adı + SHA-1 ile kısıtla.
object Config {
    val YOUTUBE_API_KEY: String = BuildConfig.YOUTUBE_API_KEY
}
