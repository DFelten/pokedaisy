package com.pokedaisy.app.companion.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import okio.Path

/**
 * SETTINGS > FONT on both screens (Prefs.appFont, by [ordinal]: new entries go at the end): the app's text in a pixel
 * font (PIXEL SMALL, the default since the SUMMARY MINT look, or Pixel Operator; crisp pixel art, a Game Boy game's own
 * font while one runs) or a smooth font for easier reading. [capHeight] is the font's
 * capitals as a share of its em, so text comes out with Pixel Operator's capital height ([sizeScale]) and every layout
 * sized to the pixel font still fits.
 */
enum class AppFont(override val label: String, val capHeight: Float) : FontChoice {
    PIXEL("PIXEL", 0.5625f),
    /** Atkinson Hyperlegible (OFL, already the manual's reading font). */
    SMOOTH("SMOOTH", 0.668f),
    /** Atkinson Hyperlegible Bold: closer to the games' heavy menu letters. */
    SMOOTH_BOLD("SMOOTH BOLD", 0.668f),
    /** The device's own sans-serif (Roboto on most Android devices). */
    SYSTEM("SYSTEM", 0.711f),
    /** The Linux / 3DS HUD's small pixel font (assets/fonts/PixelSmall.ttf, scripts/gen_small_font.py): capitals 8 tall
     * on Pixel Operator's grid, letters 5 wide; what it lacks comes from Pixel Operator. */
    PIXEL_SMALL("PIXEL SMALL", 0.5f),
    /** Nunito (OFL; its 600 / 800 weights, Latin only: the device's sans-serif for the rest): VIEW MODERN's rounded
     * letters, after the X / Y menus'. */
    MODERN("MODERN", 0.705f),
    /** A font the player added (SETTINGS > MY LOOK, [CustomLook.fonts]): the one worn is [AppFonts.custom]. */
    CUSTOM("CUSTOM", 0.7f);

    /** Drawn at whole screen pixels per font pixel (GbaTextMetrics), a game's own font in its place. */
    override val pixel: Boolean get() = this == PIXEL || this == PIXEL_SMALL

    companion object {
        /** The default: a player who never picked one gets it (Prefs.appFont's default). */
        val DEFAULT = PIXEL_SMALL

        /** The pick-list's order (not the stored ordinals'): the pixel fonts first, the default on top. */
        val menu: List<AppFont> = listOf(PIXEL_SMALL, PIXEL, MODERN, SMOOTH, SMOOTH_BOLD, SYSTEM)
    }

    /** Font size against Pixel Operator's: its capital height, 0.9 of it for a smooth font, whose letters run wider than
     * the pixel font's (columns sized to Pixel Operator, DEFENSE beside its number, still fit). */
    override val sizeScale: Float get() = when {
        pixel -> 1f
        this == CUSTOM -> AppFonts.custom?.sizeScale ?: SMOOTH.sizeScale
        else -> 0.9f * PIXEL.capHeight / capHeight
    }

    override fun family(): FontFamily = when (this) {
        PIXEL -> FontSource.get().pixel()
        PIXEL_SMALL -> FontSource.get().pixelSmall()
        SMOOTH -> FontSource.get().smooth(false)
        SMOOTH_BOLD -> FontSource.get().smooth(true)
        SYSTEM -> FontFamily.SansSerif
        MODERN -> FontSource.get().modern()
        CUSTOM -> AppFonts.custom?.family() ?: FontSource.get().smooth(false)
    }
}

/** A font a label can be drawn in: one of the app's ([AppFont]) or one the player added ([CustomFont]). */
interface FontChoice {
    val label: String
    /** Drawn at whole screen pixels per font pixel. */
    val pixel: Boolean
    /** Its size against Pixel Operator's ([AppFont.sizeScale]). */
    val sizeScale: Float
    fun family(): FontFamily
}

/**
 * A TrueType / OpenType font the player added (SETTINGS > MY LOOK > ADD A FONT, [CustomLook]): [name] from its file,
 * [capHeight] read from its OS/2 table ([FontFileInfo]) so it comes out at Pixel Operator's capital height like the smooth
 * fonts; anything it lacks falls to Pixel Operator and the device's ([FontSource.game]).
 */
class CustomFont(override val label: String, val file: Path, val capHeight: Float) : FontChoice {
    override val pixel: Boolean get() = false
    override val sizeScale: Float get() = 0.9f * AppFont.PIXEL.capHeight / capHeight
    override fun family(): FontFamily = FontSource.get().game(file) ?: FontSource.get().smooth(false)
    override fun equals(other: Any?) = other is CustomFont && other.file == file
    override fun hashCode() = file.hashCode()
}

/**
 * SETTINGS > FONT SIZE on both screens (Prefs.fontSize, by [ordinal]): the app's text a size down or up (the user's call,
 * 2026-11-20), [factor] times the screen pixels a font pixel takes before they're rounded to whole ones (so the pixel fonts
 * stay crisp); GBA pixels (paddings) don't change.
 */
enum class AppFontSize(val label: String, private val base: Float) {
    SMALL("SMALL", 0.8f), MEDIUM("MEDIUM", 1f), LARGE("LARGE", 1.25f),
    /** MORE SIZES: any size from [MIN_PERCENT] to [MAX_PERCENT] on a slider (the user's call, 2026-11-21), [AppFonts.customPercent]. */
    CUSTOM("MORE SIZES", 1f);

    val factor: Float get() = if (this == CUSTOM) AppFonts.customPercent / 100f else base

    companion object {
        val DEFAULT = MEDIUM
        const val MIN_PERCENT = 50
        const val MAX_PERCENT = 200
        const val STEP_PERCENT = 5
    }
}

object AppFonts {
    var setting by mutableStateOf(AppFont.DEFAULT)

    /** FONT SIZE ([AppFontSize]) as set; Compose state. */
    var size by mutableStateOf(AppFontSize.DEFAULT)

    /** ui-preview / screenshot tests: this size whatever the setting. */
    var sizeOverride: AppFontSize? = null

    /** The size text is drawn at. */
    val currentSize: AppFontSize get() = sizeOverride ?: size

    /** FONT SIZE > MORE SIZES' size, in percent of MEDIUM's; Compose state. */
    var customPercent by mutableStateOf(100)

    fun changeSize(s: AppFontSize) {
        ThemeStore.current?.fontSize = s.ordinal
        size = s
    }

    /** MORE SIZES at [percent] (snapped to the slider's steps and kept in its range). */
    fun changeCustomPercent(percent: Int) {
        val p = percent.coerceIn(AppFontSize.MIN_PERCENT, AppFontSize.MAX_PERCENT)
        ThemeStore.current?.fontSizePercent = p
        customPercent = p
        if (size != AppFontSize.CUSTOM) changeSize(AppFontSize.CUSTOM)
    }

    /** FONT SIZE's value: SMALL / MEDIUM / LARGE, or MORE SIZES' percent. */
    val sizeLabel: String get() = if (size == AppFontSize.CUSTOM) "$customPercent%" else size.label

    /** The added font worn while [setting] is [AppFont.CUSTOM]; Compose state. */
    var custom by mutableStateOf<CustomFont?>(null)
        private set

    /** FONT TYPE's value: the font's name (an added one's own). */
    val currentLabel: String get() = if (current == AppFont.CUSTOM) custom?.label ?: AppFont.DEFAULT.label else current.label

    /** Wears the added font [font]. */
    fun wear(font: CustomFont) {
        ThemeStore.current?.customFont = font.file.name
        custom = font
        set(AppFont.CUSTOM)
    }

    /** An added font was removed: off it, if it was worn. */
    fun forget(font: CustomFont) {
        if (custom == font) {
            custom = null
            if (setting == AppFont.CUSTOM) set(AppFont.DEFAULT)
        }
    }

    /** FONT TYPE's pick-list: the app's fonts, then the player's. */
    fun menu(): List<FontChoice> = AppFont.menu + CustomLook.fonts

    /** Puts on the font picked from [menu] by its label. */
    fun pick(label: String): Boolean {
        AppFont.menu.firstOrNull { it.label == label }?.let { set(it); return true }
        CustomLook.fonts.firstOrNull { it.label == label }?.let { wear(it); return true }
        return false
    }

    /** ui-preview / screenshot tests: this font whatever the setting. */
    var override: AppFont? = null

    /** The font everywhere, a game's companion included (a Game Boy game draws its own letters for the pixel fonts). */
    val current: AppFont get() = override ?: setting

    fun load() {
        val store = ThemeStore.current
        size = AppFontSize.entries.getOrNull(store?.fontSize ?: AppFontSize.DEFAULT.ordinal) ?: AppFontSize.DEFAULT
        customPercent = (store?.fontSizePercent ?: 100).coerceIn(AppFontSize.MIN_PERCENT, AppFontSize.MAX_PERCENT)
        setting = runCatching { AppFont.entries[store?.appFont ?: AppFont.DEFAULT.ordinal] }.getOrDefault(AppFont.DEFAULT)
        custom = store?.customFont?.takeIf { it.isNotEmpty() }?.let { name -> CustomLook.fonts.firstOrNull { it.file.name == name } }
        // An added font that's gone (removed, or the app's files cleared): the default.
        if (setting == AppFont.CUSTOM && custom == null) setting = AppFont.DEFAULT
    }

    fun set(font: AppFont) {
        ThemeStore.current?.appFont = font.ordinal
        setting = font
    }
}
