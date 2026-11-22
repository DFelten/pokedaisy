package com.pokedaisy.app.companion.ui.theme

import com.pokedaisy.app.Prefs

/** Theme choices use the same preference keys and defaults on both platforms. */
class PrefsThemeStore(prefs: Prefs) : ThemeStore {
    override var appTheme by prefs::appTheme
    override var themeBeforeSeasonal by prefs::themeBeforeSeasonal
    override var appFont by prefs::appFont
    override var appFontRetro by prefs::appFontRetro
    override var fontSize by prefs::fontSize
    override var fontSizePercent by prefs::fontSizePercent
    override var customFont by prefs::customFont
    override var appBackground by prefs::appBackground
    override var companionBackground by prefs::companionBackground
    override var sideBars by prefs::sideBars
    override var tabIcons by prefs::tabIcons
    override var tabBarPlace by prefs::tabBarPlace
    override var tabBarCompact by prefs::tabBarCompact
    override var menuBarPlace by prefs::menuBarPlace
    override var menuBarCompact by prefs::menuBarCompact
    override var appView by prefs::appView
    override var darkMode by prefs::darkMode
}
