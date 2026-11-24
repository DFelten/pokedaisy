package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.data.RomArt
import kotlin.test.Test
import kotlin.test.assertEquals

/** [paperFilled]: what the outline closes in turns white, but an area holding a gap pixel stays see-through. */
class PaperFilledTest {
    private val ink = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** Two 3x3 rings side by side in a 9x5 picture, each closing in its middle pixel (2, 2) and (6, 2). */
    private fun rings(): RomArt.Image {
        val px = IntArray(9 * 5)
        for (cx in listOf(2, 6)) for (y in 1..3) for (x in cx - 1..cx + 1) if (x != cx || y != 2) px[y * 9 + x] = ink
        return RomArt.Image(9, 5, px)
    }

    @Test fun insideTurnsWhite() {
        val out = paperFilled(rings()).argb
        assertEquals(white, out[2 * 9 + 2]); assertEquals(white, out[2 * 9 + 6]); assertEquals(0, out[0])
    }

    @Test fun gapStaysClear() {
        val out = paperFilled(rings(), intArrayOf(6, 2)).argb
        assertEquals(white, out[2 * 9 + 2]); assertEquals(0, out[2 * 9 + 6])
    }
}
