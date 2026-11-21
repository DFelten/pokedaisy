package com.pokedaisy.app.companion.ui.theme

import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test

/** VIEW (AppViews): its stored order, and the font it puts on and gives back. */
class AppViewTest {
    private class Store : ThemeStore {
        override var appTheme = 0
        override var themeBeforeSeasonal = 0
        override var darkMode = 0
        override var appFont = AppFont.DEFAULT.ordinal
        override var appFontRetro = AppFont.DEFAULT.ordinal
        override var appView = AppView.DEFAULT.ordinal
    }

    private val store = Store()

    @BeforeTest fun setUp() {
        ThemeStore.current = store
        AppFonts.load()
        AppViews.load()
    }

    @AfterTest fun tearDown() {
        ThemeStore.current = null
        AppViews.setting = AppView.DEFAULT
        AppFonts.setting = AppFont.DEFAULT
    }

    @Test fun ordinalsAreStable() {
        assertEquals(listOf("RETRO", "MODERN"), AppView.entries.map { it.name })
        assertEquals(AppView.RETRO, AppView.DEFAULT)
    }

    @Test fun modernPutsOnItsFontAndRetroGivesTheOldOneBack() {
        AppFonts.set(AppFont.PIXEL)
        AppViews.set(AppView.MODERN)
        assertEquals(AppFont.MODERN, AppFonts.setting)
        assertEquals(AppView.MODERN.ordinal, store.appView)
        AppViews.set(AppView.RETRO)
        assertEquals(AppFont.PIXEL, AppFonts.setting)
        assertEquals(AppView.RETRO.ordinal, store.appView)
    }

    @Test fun aFontPickedInModernStaysWhenGoingBack() {
        AppViews.set(AppView.MODERN)
        AppFonts.set(AppFont.SMOOTH)
        AppViews.set(AppView.RETRO)
        assertEquals(AppFont.SMOOTH, AppFonts.setting)
    }
}
