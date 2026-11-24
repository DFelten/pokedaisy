package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32

/**
 * Red's, Blue's and Yellow's TRAINER CARD ([Gen1TrainerCard]): the save's side from the fixtures, the pictures from the
 * carts (skipped without them; `GB_ROM_DIR` moves them). The pinned CRCs are the game's own card screens, captured
 * headless in English Yellow, German Yellow, English Blue and German Red (the yellow / red fixtures' save with every badge,
 * none, and badges 1 3 6 8 poked in): [Gen1TrainerCard.render] matched each of them pixel for pixel. English Red's art
 * is Blue's and German Blue's German Red's, byte for byte. `GEN1_CARD_SHOTS=<dir of those .ppm>` compares them again.
 */
class Gen1TrainerCardTest {
    private val dir = File(System.getenv("GB_ROM_DIR") ?: File(File(RETAIL_ROM_DIR).parentFile, "gbc").path)
    private fun art(name: String, t: Gen1TrainerCard.Tables): Gen1TrainerCard.Art? {
        val f = File(dir, name)
        if (!f.isFile) return null
        return Gen1TrainerCard.parse(Gen1TrainerCard.extract(f.readBytes(), t) ?: error("$name: no picture"), t.style) ?: error("no card art")
    }
    private val carts by lazy {
        listOfNotNull(
            art("Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb", Gen1TrainerCard.YELLOW_TABLES)?.let { "y" to it },
            art("Pokemon - Gelbe Edition - Special Pikachu Edition (Germany) (GBC,SGB Enhanced).gb", Gen1TrainerCard.YELLOW_DE_TABLES)?.let { "yde" to it },
            art("Pokemon - Blue Version (USA, Europe) (SGB Enhanced).gb", Gen1TrainerCard.RED_BLUE_TABLES)?.let { "b" to it },
            art("Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb", Gen1TrainerCard.RED_BLUE_TABLES)?.let { "r" to it },
            art("Pokemon - Rote Edition (Germany) (SGB Enhanced).gb", Gen1TrainerCard.RED_BLUE_DE_TABLES)?.let { "rde" to it },
            art("Pokemon - Blaue Edition (Germany) (SGB Enhanced).gb", Gen1TrainerCard.RED_BLUE_DE_TABLES)?.let { "bde" to it },
        )
    }

    private val nelson = listOf(0x8D, 0xA4, 0xAB, 0xB2, 0xAE, 0xAD)
    private fun card(style: CardStyle, badges: Int) = TrainerCardInfo(
        style = style, name = nelson, female = false, trainerId = 0, hours = 24, minutes = 18, money = 999999,
        dexCaught = null, badges = badges, stars = 0, hofDebut = 0,
    )
    private val badgeSets = mapOf("all" to 0xFF, "some" to 0xA5, "none" to 0)

    @Test
    fun theSavesSide() {
        for ((fixture, cfg, style) in listOf(
            Triple("yellow", GEN1_YELLOW, CardStyle.YELLOW), Triple("yellow_de", GEN1_YELLOW_DE, CardStyle.YELLOW_DE),
            Triple("red", GEN1_RED, CardStyle.RED_BLUE), Triple("blue", GEN1_BLUE, CardStyle.RED_BLUE),
            Triple("red_de", GEN1_RED_DE, CardStyle.RED_BLUE_DE), Triple("blue_de", GEN1_BLUE_DE, CardStyle.RED_BLUE_DE),
        )) {
            val c = Gen1TrainerCard.read(FixtureMemoryReader.load(fixture), cfg) ?: error("$fixture: no card")
            assertEquals(fixture, style, c.style)
            assertEquals(fixture, nelson, c.name)
            println("$fixture: ${c.hours}:${c.minutes} money ${c.money} badges ${c.badges.toString(2)}")
        }
        assertEquals(null, Gen1TrainerCard.read(FixtureMemoryReader.load("yellow"), GEN1_YELLOW_LEGACY))
    }

    /** The game's card screens' CRCs: the same for every cart of a kind (English Red = Blue, German Red = Blue). */
    private val pinned = mapOf(
        "YELLOW_all" to 0x5cf1a5bcL, "YELLOW_some" to 0x02496af0L, "YELLOW_none" to 0x0f31a104L,
        "YELLOW_DE_all" to 0x94acc34dL, "YELLOW_DE_some" to 0xca140c01L, "YELLOW_DE_none" to 0xc76cc7f5L,
        "RED_BLUE_all" to 0xd45b21aeL, "RED_BLUE_some" to 0x3eb507bbL, "RED_BLUE_none" to 0x4ebd0615L,
        "RED_BLUE_DE_all" to 0xe694fbffL, "RED_BLUE_DE_some" to 0x0c7addeaL, "RED_BLUE_DE_none" to 0x7c72dc44L,
    )

    @Test
    fun theGamesOwnCard() {
        assumeTrue("no Gen 1 carts in $dir", carts.isNotEmpty())
        val shots = System.getenv("GEN1_CARD_SHOTS")?.let(::File)
        val out = File("build/gen1-card").apply { mkdirs() }
        for ((tag, art) in carts) for ((set, badges) in badgeSets) {
            val img = Gen1TrainerCard.render(art, card(art.style, badges))
            File(out, "${tag}_$set.png").writeBytes(img.png())
            val crc = CRC32().apply { img.argb.forEach { v -> update(byteArrayOf((v shr 16).toByte(), (v shr 8).toByte(), v.toByte())) } }.value
            println("$tag $set crc ${crc.toString(16)}")
            pinned["${art.style}_$set"]?.let { assertEquals("$tag $set", it, crc) }
            val shot = shots?.let { File(it, "${tag}_$set.ppm") }?.takeIf { it.isFile } ?: continue
            val b = shot.readBytes()
            var o = 0
            repeat(3) { while (b[o] != '\n'.code.toByte()) o++; o++ }
            var diff = 0
            for (i in 0 until 160 * 144) {
                val v = ((b[o + 3 * i].toInt() and 0xFF) shl 16) or ((b[o + 3 * i + 1].toInt() and 0xFF) shl 8) or (b[o + 3 * i + 2].toInt() and 0xFF)
                if (v != img.argb[i] and 0xFFFFFF) {
                    if (diff < 5) println("  $tag $set: (${i % 160}, ${i / 160}) game ${v.toString(16)} ours ${(img.argb[i] and 0xFFFFFF).toString(16)}")
                    diff++
                }
            }
            assertEquals("$tag $set: pixels off", 0, diff)
        }
    }
}
