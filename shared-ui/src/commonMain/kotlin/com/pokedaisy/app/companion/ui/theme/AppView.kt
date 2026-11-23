package com.pokedaisy.app.companion.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * SETTINGS > SCREEN > VIEW on both screens (Prefs.appView, by [ordinal]: new entries at the end; the user's call,
 * 2026-11-19): the whole app's look, Android only for now.
 * - [RETRO]: the pixel look (SUMMARY MINT menus, pixel corners and icons, a pixel font).
 * - [MODERN]: smooth, after Pokemon X / Y and the games since: rounded cards with soft shadows, gradients, a rounded
 *   sans-serif ([AppFont.MODERN], put on with the view), drawn icons ([com.pokedaisy.app.companion.ui.ModernIcons]).
 *   It's [UiStyle.MODERN]'s palette under [com.pokedaisy.app.companion.ui.CompanionSkins.MODERN], everywhere: the
 *   Library, Settings, TOOLS and the companion (a 3DS game's own smooth skin still wins there).
 * Players who had the app before it are asked once ([AppViewIntro]); a new install starts on [DEFAULT].
 */
enum class AppView(val label: String) {
    RETRO("RETRO"), MODERN("MODERN");

    companion object {
        val DEFAULT = RETRO
    }
}

object AppViews {
    var setting by mutableStateOf(AppView.DEFAULT)

    /** ui-preview / screenshot tests: this view whatever the setting. */
    var override: AppView? = null

    val current: AppView get() = override ?: setting

    /** MODERN applies: everywhere, a game's companion and HUD too (the user's call, 2026-11-20: one VIEW for the whole app;
     * IN GAME only picks the colours there, [com.pokedaisy.app.companion.ui.CompanionSkins.gameHue]). A 3DS game's own
     * smooth skin still wins while it's up ([com.pokedaisy.app.companion.ui.CompanionSkins.current]). */
    val modern: Boolean get() = current == AppView.MODERN

    fun load() {
        setting = AppView.entries.getOrNull(ThemeStore.current?.appView ?: AppView.DEFAULT.ordinal) ?: AppView.DEFAULT
    }

    /**
     * Puts on [view], and its font with it: MODERN wears [AppFont.MODERN] (the font RETRO had is kept), RETRO gives
     * back that font (or the default pixel one) when MODERN's is on. FONT still picks any font in either view.
     */
    fun set(view: AppView) {
        val store = ThemeStore.current
        if (view == AppView.MODERN && AppFonts.setting != AppFont.MODERN) {
            store?.appFontRetro = AppFonts.setting.ordinal
            AppFonts.set(AppFont.MODERN)
        } else if (view == AppView.RETRO && AppFonts.setting == AppFont.MODERN) {
            val back = AppFont.entries.getOrNull(store?.appFontRetro ?: AppFont.DEFAULT.ordinal)
                ?.takeIf { it != AppFont.MODERN } ?: AppFont.DEFAULT
            AppFonts.set(back)
        }
        store?.appView = view.ordinal
        setting = view
    }
}
