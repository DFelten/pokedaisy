package com.pokedaisy.app.companion.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppFontTest {
    /** Prefs.appFont stores ordinals: the old ones keep their meaning, and its default (a literal) is PIXEL SMALL's. */
    @Test fun ordinalsAreStable() {
        assertEquals(listOf("PIXEL", "SMOOTH", "SMOOTH_BOLD", "SYSTEM", "PIXEL_SMALL", "MODERN", "CUSTOM"), AppFont.entries.map { it.name })
        assertEquals(4, AppFont.DEFAULT.ordinal)
        assertEquals(AppFont.PIXEL_SMALL, AppFont.DEFAULT)
    }

    @Test fun menuListsEveryFontOnceDefaultFirst() {
        // CUSTOM is the player's added fonts, listed by their own names (AppFonts.menu).
        assertEquals(AppFont.entries.toSet() - AppFont.CUSTOM, AppFont.menu.toSet())
        assertEquals(AppFont.entries.size - 1, AppFont.menu.size)
        assertEquals(AppFont.DEFAULT, AppFont.menu.first())
    }

    @Test fun bothPixelFontsDrawAtWholePixels() {
        assertTrue(AppFont.PIXEL.pixel && AppFont.PIXEL_SMALL.pixel)
        assertEquals(1f, AppFont.PIXEL_SMALL.sizeScale)
    }

    /** Prefs.fontSize stores ordinals: MORE SIZES went in last, its factor is its percent. */
    @Test fun moreSizesFollowsItsPercent() {
        assertEquals(listOf("SMALL", "MEDIUM", "LARGE", "CUSTOM"), AppFontSize.entries.map { it.name })
        val before = AppFonts.customPercent
        try {
            AppFonts.customPercent = 140
            assertEquals(1.4f, AppFontSize.CUSTOM.factor, 0.0001f)
            assertEquals(1.25f, AppFontSize.LARGE.factor, 0.0001f)
        } finally {
            AppFonts.customPercent = before
        }
    }
}
