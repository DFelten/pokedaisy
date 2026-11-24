package com.pokedaisy.app

/** The app's generated BuildConfig (AGP makes it; the preview has no AGP). */
object BuildConfig {
    const val DEBUG = true
    /** -PappVersion=1.6.1 puts another version on the shots (marketing pictures); 1.1.0 otherwise. */
    @JvmField val VERSION_NAME: String = System.getProperty("appVersion").orEmpty().ifEmpty { "1.1.0" }
    const val SCREENSCRAPER_DEV_ID = ""
    const val SCREENSCRAPER_DEV_PASSWORD = ""
    /** On as in a default build (since 2026-11-23). */
    @JvmField var FEATURE_SCREENSCRAPER = true
    /** On in the preview: the CO-OP group and the invite pages are what the co-op shots show. */
    @JvmField var FEATURE_COOP = true
    @JvmField var FEATURE_SETTINGS_PLUS = System.getProperty("flagsOff") != "true"
    @JvmField var FEATURE_MODERN_LOOK = System.getProperty("flagsOff") != "true"
    @JvmField var FEATURE_SHINY_HUNT = System.getProperty("flagsOff") != "true"
    @JvmField var FEATURE_SPOILER_FREE = System.getProperty("flagsOff") != "true"
    /** Off as in a release: the blocked-game shots show the notice. */
    @JvmField var FEATURE_ALLOW_BLOCKED = false
    @JvmField var FEATURE_KEEP_BLOCKED = false
    /** Off as in a default build: 3DS games (the n3ds module) aren't in the preview's build. */
    const val FEATURE_N3DS = false
    /** Off as in a default build: PC fan games are a prototype. */
    const val FEATURE_PC_GAMES = false
    const val COOP_RELAY_URL = ""
}
