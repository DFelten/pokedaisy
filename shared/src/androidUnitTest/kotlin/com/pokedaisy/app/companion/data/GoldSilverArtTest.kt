package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import okio.Path.Companion.toOkioPath

/**
 * Gold's and Silver's art and dex pages, rebuilt from the player's own ROMs (skipped without them): the two carts give
 * the same art from their own tables (Silver's icon tables sit 0x1A bytes earlier), and every species has its version's
 * page and picture - the pages' bank computed, the pics' banks fixed by FixPicBank's pairs. The art lands in
 * app/build/gen2-art-gold to look at.
 */
class GoldSilverArtTest {
    private val gbc = File(RETAIL_ROM_DIR).parentFile!!.let { File(it, "gbc") }
    private val gold = File(gbc, "Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc")
    private val silver = File(gbc, "Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc")

    private fun rom(f: File): ByteArray {
        assumeTrue("${f.name} not on this machine", f.isFile)
        return f.readBytes()
    }

    @Test
    fun `Gold and Silver give the same art`() {
        val out = File("build/gen2-art-gold")
        out.deleteRecursively()
        val wrote = Gen2Art.extractTo(rom(gold), out.toOkioPath())
        assertTrue(wrote.size > 250)
        val other = File.createTempFile("gen2", "").also { it.delete() }
        assertEquals(wrote.toSet(), Gen2Art.extractTo(rom(silver), other.toOkioPath()).toSet())
        for (path in wrote) assertTrue(path, File(out, path).readBytes().contentEquals(File(other, path).readBytes()))
        other.deleteRecursively()
    }

    /**
     * Every [gen2PictureGaps] pixel sits in a closed see-through area of its cart's front picture (one the border can't
     * reach), of 3 pixels or more: a moved picture or a typo would leave a belly or a face see-through instead.
     */
    @Test
    fun `picture gaps are closed areas of each cart's picture`() {
        val crystal = File(gbc, "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc")
        var checked = 0
        try {
            for ((game, pair) in listOf(GameKind.GOLD to (gold to GEN2_DEX_GOLD_SILVER),
                GameKind.SILVER to (silver to GEN2_DEX_GOLD_SILVER), GameKind.CRYSTAL to (crystal to GEN2_DEX_CRYSTAL))) {
                Gen1Dex.rom = rom(pair.first)
                for (s in 1..251) {
                    val gaps = gen2PictureGaps(game, s) ?: continue
                    val px = Gen2Dex.frontPixels(pair.second, s)!!
                    for (k in 0 until gaps.size / 2) {
                        val start = gaps[2 * k + 1] * 56 + gaps[2 * k]
                        assertEquals("$game $s: not see-through", 0, px[start] ushr 24)
                        val seen = HashSet<Int>(); val stack = ArrayDeque(listOf(start)); var edge = false
                        while (stack.isNotEmpty()) {
                            val i = stack.removeLast()
                            if (!seen.add(i)) continue
                            val x = i % 56; val y = i / 56
                            if (x == 0 || y == 0 || x == 55 || y == 55) edge = true
                            for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1))
                                if (nx in 0..55 && ny in 0..55 && px[ny * 56 + nx] ushr 24 == 0) stack.add(ny * 56 + nx)
                        }
                        assertTrue("$game $s: reaches the border", !edge)
                        assertTrue("$game $s: ${seen.size} pixels", seen.size >= 3)
                        checked++
                    }
                }
            }
        } finally { Gen1Dex.rom = null }
        assertEquals(33, checked)
    }

    @Test
    fun `party icons, town maps and the player`() {
        val icons = Gen2Art.icons(rom(gold), Gen2Art.GOLD)
        assertEquals(252, icons.size)   // 251 + the egg
        assertEquals(icons.keys, Gen2Art.icons(rom(silver), Gen2Art.SILVER).keys)
        for (i in 0..1) {
            val map = Gen2Art.townMap(rom(gold), Gen2Art.GOLD, i)
            assertNotNull(map)
            assertEquals(160 to 144, map!!.width to map.height)
        }
        assertTrue(Gen2Art.player(rom(gold), Gen2Art.GOLD, 0).argb.any { it != 0 })
    }

    @Test
    fun `dex pages from each cart`() {
        activeGame = GameKind.GOLD
        try {
            for ((f, dex) in listOf(gold to GEN2_DEX_GOLD_SILVER, silver to GEN2_DEX_GOLD_SILVER)) {
                Gen1Dex.rom = rom(f)
                val e = Gen2Dex.entry(dex, 152)!!
                assertEquals("LEAF", e.category)
                assertEquals(2 * 12 + 11 to 140, e.heightIn to e.weightLbs10)   // 2'11", 14.0 lbs
                assertEquals(listOf(45, 49, 65, 49, 65, 45), e.baseStats)
                assertTrue(e.description.isNotEmpty())
                assertEquals("Restores #MON HP by 20.".replace("#", "POKé"), Gen2Dex.itemDescription(dex, 0x12))   // POTION
                for (s in 1..251) {
                    assertNotNull("entry $s", Gen2Dex.entry(dex, s))
                    assertNotNull("pic $s", Gen2Dex.frontPixels(dex, s))
                    // POKéMON PICTURES' shiny colours: the second palette of the pair, never the usual one.
                    val shiny = Gen2Dex.frontPixels(dex, s, shiny = true)
                    assertNotNull("shiny pic $s", shiny)
                    if (s == 130) assertTrue("shiny GYARADOS differs", !shiny!!.contentEquals(Gen2Dex.frontPixels(dex, s)!!))
                }
            }
            // Each version's own text.
            Gen1Dex.rom = rom(gold)
            val goldText = Gen2Dex.entry(GEN2_DEX_GOLD_SILVER, 152)!!.description
            Gen1Dex.rom = rom(silver)
            val silverText = Gen2Dex.entry(GEN2_DEX_GOLD_SILVER, 152)!!.description
            assertTrue("$goldText / $silverText", goldText != silverText)
            val out = File("build/gen2-art-gold-dex").apply { mkdirs() }
            for (s in listOf(1, 25, 152, 201, 249, 250)) {
                File(out, "$s.png").writeBytes(RomArt.Image(56, 56, Gen2Dex.frontPixels(GEN2_DEX_GOLD_SILVER, s)!!).png())
            }
        } finally {
            Gen1Dex.rom = null
        }
    }

    @Test
    fun `the font parses`() {
        val ttf = Gen2Art.font(rom(gold), Gen2Art.GOLD)
        val fontClass = Class.forName("java.awt.Font")
        val font = fontClass.getMethod("createFont", Int::class.java, java.io.InputStream::class.java)
            .invoke(null, 0 /* TRUETYPE_FONT */, ttf.inputStream())
        assertEquals("Gen2 Pokemon", fontClass.getMethod("getFamily").invoke(font))
        val canDisplay = fontClass.getMethod("canDisplay", Char::class.java)
        for (c in "CYNDAQUIL Lv5 ♂♀ 20/20 ¥3000") assertTrue("glyph $c", canDisplay.invoke(font, c) as Boolean)
    }
}
