package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32

/**
 * Gold / Silver's TRAINER CARD ([Gen2TrainerCard] with [Gen2TrainerCard.GOLD_SILVER_TABLES]): the save's side from the gold
 * fixtures, the pictures from the carts (skipped without them; Silver's card art is Gold's, byte for byte). The pinned CRCs
 * are the game's own card screens, captured headless in English and German Gold (the gold fixture's save with no POKéDEX
 * and no badges; and with 999999 money, 123:09, the POKéDEX and badges 1 3 6 8 poked in), every frame of the badges' spin
 * and both colon states: [Gen2TrainerCard.render] matched each of them pixel for pixel. `GOLD_CARD_SHOTS=<dir of those
 * .ppm>` compares them again.
 */
class GoldSilverTrainerCardTest {
    private val dir = File(File(RETAIL_ROM_DIR).parentFile, "gbc")
    private fun art(name: String, t: Gen2TrainerCard.Tables): Gen2TrainerCard.Art? {
        val f = File(dir, name)
        if (!f.isFile) return null
        return Gen2TrainerCard.parse(Gen2TrainerCard.extract(f.readBytes(), t, Gen2Art.GOLD.font), t.style) ?: error("no card art")
    }
    private val carts by lazy {
        listOfNotNull(
            art("Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc", Gen2TrainerCard.GOLD_SILVER_TABLES)?.let { "en" to it },
            art("Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc", Gen2TrainerCard.GOLD_SILVER_TABLES)?.let { "en-silver" to it },
            art("Pokemon - Goldene Edition (Germany) (SGB Enhanced) (GB Compatible).gbc", Gen2TrainerCard.GOLD_SILVER_DE_TABLES)?.let { "de" to it },
            art("Pokemon - Silberne Edition (Germany) (SGB Enhanced) (GB Compatible).gbc", Gen2TrainerCard.GOLD_SILVER_DE_TABLES)?.let { "de-silver" to it },
        )
    }

    private val gold = listOf(0x86, 0x8E, 0x8B, 0x83)
    private fun card(full: Boolean, dex: Int) = TrainerCardInfo(
        style = CardStyle.GOLD_SILVER, name = gold, female = false, trainerId = 54088,
        hours = if (full) 123 else 0, minutes = if (full) 9 else 4, money = if (full) 999999 else 3000,
        dexCaught = if (full) dex else null, badges = if (full) 0xA5 else 0, stars = 0, hofDebut = 0,
    )

    @Test
    fun `the save's side - the gold fixtures, English and German`() {
        for ((fixture, cfg) in listOf("gold" to GEN2_GOLD, "gold_de_boxes" to GEN2_GOLD_DE)) {
            val c = Gen2TrainerCard.read(FixtureMemoryReader.load(fixture), cfg) ?: error("$fixture: no card")
            assertEquals(fixture, CardStyle.GOLD_SILVER, c.style)
            assertEquals(fixture, gold, c.name)
            assertEquals(fixture, 54088, c.trainerId)
            assertEquals(fixture, false, c.female)
        }
    }

    @Test
    fun `both pages, every frame - the game's own, pixel for pixel`() {
        assumeTrue("no Gold / Silver cart on this machine", carts.isNotEmpty())
        val pins = mapOf("en" to 0xDAF26538L, "de" to 0xC140AE64L)
        val out = File("build/gold-card").also { it.mkdirs() }
        val crcs = HashMap<String, Long>()
        for ((lang, art) in carts) {
            val crc = CRC32()
            for (full in listOf(true, false)) for (page2 in listOf(false, true)) for (phase in 0 until 8) {
                val img = Gen2TrainerCard.render(art, card(full, 17), page2, phase)
                img.argb.forEach { crc.update(it) }
                if (phase == 0 || phase == 4) File(out, "$lang-${if (full) "full" else "plain"}-p${if (page2) 2 else 1}-$phase.png").writeBytes(img.png())
            }
            println("$lang 0x%08XL".format(crc.value))
            crcs[lang] = crc.value
        }
        // Silver's card is Gold's
        crcs["en-silver"]?.let { assertEquals(crcs["en"], it) }
        crcs["de-silver"]?.let { assertEquals(crcs["de"], it) }
        for ((lang, pinned) in pins) crcs[lang]?.let { assertEquals(lang, pinned, it) }
        val shots = System.getenv("GOLD_CARD_SHOTS")?.let(::File) ?: return
        val bad = ArrayList<String>()
        for ((lang, art) in carts.filter { "-" !in it.first }) {
            val files = shots.listFiles().orEmpty().filter { it.name.startsWith("${lang}_") && "_p" in it.name && it.name.endsWith(".ppm") }
            assumeTrue(files.isNotEmpty())
            for (f in files) {
                val ref = readPpm(f)
                val dex = if (lang == "de") 13 else 17
                val off = (0 until 8).minOf { ph ->
                    val img = Gen2TrainerCard.render(art, card("_full" in f.name, dex), "_p2" in f.name, ph)
                    img.argb.indices.count { img.argb[it] != ref[it] }
                }
                if (off != 0) bad += "${f.name}:$off"
            }
        }
        assertEquals(emptyList<String>(), bad.sorted())
    }

    private fun readPpm(f: File): IntArray {
        val b = f.readBytes()
        var o = 0
        val fields = ArrayList<String>()
        while (fields.size < 4) {
            while (b[o].toInt().toChar().isWhitespace()) o++
            val s = o
            while (!b[o].toInt().toChar().isWhitespace()) o++
            fields += String(b, s, o - s)
        }
        o++
        return IntArray(fields[1].toInt() * fields[2].toInt()) { i ->
            (0xFF shl 24) or ((b[o + 3 * i].toInt() and 0xFF) shl 16) or ((b[o + 3 * i + 1].toInt() and 0xFF) shl 8) or (b[o + 3 * i + 2].toInt() and 0xFF)
        }
    }
}
