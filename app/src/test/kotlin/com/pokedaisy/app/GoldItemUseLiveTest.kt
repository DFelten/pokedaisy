package com.pokedaisy.app

import com.pokedaisy.app.companion.data.GameCalls
import com.pokedaisy.app.companion.data.GbField
import com.pokedaisy.app.companion.data.Gen2Config
import com.pokedaisy.app.companion.data.GEN2_GOLD
import com.pokedaisy.app.companion.data.GEN2_GOLD_DE
import com.pokedaisy.app.companion.data.GEN2_GOLD_FR
import com.pokedaisy.app.companion.data.GEN2_SILVER_FR
import com.pokedaisy.app.companion.data.GEN2_GOLD_IT
import com.pokedaisy.app.companion.data.GEN2_SILVER_IT
import com.pokedaisy.app.companion.data.GEN2_GOLD_ES
import com.pokedaisy.app.companion.data.GEN2_SILVER_ES
import com.pokedaisy.app.companion.data.GEN2_SILVER
import com.pokedaisy.app.companion.data.GEN2_SILVER_DE
import com.pokedaisy.app.companion.data.ItemUseResult
import com.pokedaisy.app.companion.data.RETAIL_ROM_DIR
import com.pokedaisy.app.companion.data.SlotVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Gold / Silver's bag USE run as the app runs it ([GbField.CrystalEngine.useItem] / [GbField.CrystalEngine.itemTargets])
 * against the running game (mgba_dump headless, [GbLiveGame]): each kind of item, the result read back from the game's RAM.
 * The bag is written into RAM first (no save holds all of it).
 *
 * Opt-in (Docker, the player's ROMs and saves): POKEDAISY_GB_LIVE=1 make test-quick T='*GoldItemUseLiveTest'.
 * GOLD_LIVE_SHOTS=<dir> keeps a PPM of the screen after each step.
 */
class GoldItemUseLiveTest {
    private val gbc = File(File(RETAIL_ROM_DIR).parentFile, "gbc")

    private class Calls(val g: GbLiveGame, val romBytes: ByteArray) : GameCalls {
        override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int) = -1L
        private fun bankOf(addr: Long) = ((addr shr 16).toInt()).takeIf { it != 0 } ?: if ((addr and 0xFFFF) >= 0xD000) 1 else 0
        override fun read(addr: Long, len: Int): ByteArray {
            val a = (addr and 0xFFFF).toInt()
            val out = ByteArray(len)
            var done = 0
            while (done < len) {
                val n = minOf(256, len - done)
                val line = g.ask("gbpeek %x %x %x".format(bankOf(addr), a + done, n), "gbpeek ")
                line.substringAfter(": ").trim().split(' ').forEachIndexed { i, h -> out[done + i] = h.toInt(16).toByte() }
                done += n
            }
            return out
        }
        override fun write(addr: Long, bytes: ByteArray): Boolean {
            g.run("gbpoke %x %x %s".format(bankOf(addr), (addr and 0xFFFF).toInt(), bytes.joinToString(" ") { "%02x".format(it) }))
            return true
        }
        override fun rom(off: Long, len: Int) = romBytes.copyOfRange(off.toInt(), off.toInt() + len)
        override fun gbCallAt(at: Int, fn: Int, a: Int, b: Int, hl: Int) =
            g.ask("gbcallat %x %x %x %x %x".format(at, fn, a, b, hl), "gbcallat ").endsWith("called")
    }

    private fun u8(c: Calls, addr: Long) = c.read(addr, 1)[0].toInt() and 0xFF

    @Test fun gold() = run(GbField.Gold, GEN2_GOLD, "Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc",
        // No English save on this machine: the German one loads in it (the same RAM and save layout).
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Goldene Edition (Germany) (SGB Enhanced) (GB Compatible).sav"))

    @Test fun silver() = run(GbField.Silver, GEN2_SILVER, "Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Silberne Edition (Germany) (SGB Enhanced) (GB Compatible).sav"))

    @Test fun goldDe() = run(GbField.GoldDe, GEN2_GOLD_DE, "Pokemon - Goldene Edition (Germany) (SGB Enhanced) (GB Compatible).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Goldene Edition (Germany) (SGB Enhanced) (GB Compatible).sav"))

    @Test fun silverDe() = run(GbField.SilverDe, GEN2_SILVER_DE, "Pokemon - Silberne Edition (Germany) (SGB Enhanced) (GB Compatible).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Silberne Edition (Germany) (SGB Enhanced) (GB Compatible).sav"))

    @Test fun goldFr() = run(GbField.GoldFr, GEN2_GOLD_FR, "Pokemon - Version Or (France) (SGB Enhanced).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Version Or (France) (SGB Enhanced).sav"))

    @Test fun silverFr() = run(GbField.SilverFr, GEN2_SILVER_FR, "Pokemon - Version Argent (France) (SGB Enhanced).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Version Argent (France) (SGB Enhanced).sav"))

    @Test fun goldIt() = run(GbField.GoldIt, GEN2_GOLD_IT, "Pokemon - Versione Oro (Italy) (SGB Enhanced).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Versione Oro (Italy) (SGB Enhanced).sav"))

    @Test fun silverIt() = run(GbField.SilverIt, GEN2_SILVER_IT, "Pokemon - Versione Argento (Italy) (SGB Enhanced).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Versione Argento (Italy) (SGB Enhanced).sav"))

    @Test fun goldEs() = run(GbField.GoldEs, GEN2_GOLD_ES, "Pokemon - Edicion Oro (Spain) (SGB Enhanced).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Edicion Oro (Spain) (SGB Enhanced).sav"))

    @Test fun silverEs() = run(GbField.SilverEs, GEN2_SILVER_ES, "Pokemon - Edicion Plata (Spain) (SGB Enhanced).gbc",
        File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA/Pokemon - Edicion Plata (Spain) (SGB Enhanced).sav"))

    private fun run(field: GbField.CrystalEngine, cfg: Gen2Config, romName: String, save: File?) {
        assumeTrue("POKEDAISY_GB_LIVE not set", System.getenv("POKEDAISY_GB_LIVE") != null)
        val rom = File(gbc, romName)
        assumeTrue("ROM / save not on this machine", rom.isFile && save?.isFile == true)
        val shots = System.getenv("GOLD_LIVE_SHOTS")?.let { File(it, romName.substringBefore(" (")).also { d -> d.mkdirs() } }
        GbLiveGame(rom, save, log = { println("  mgba_dump: $it") }).use { g ->
            val c = Calls(g, rom.readBytes())
            val calls = field.itemCalls!!
            assertTrue("the item code in the cart", calls.codeMatches { off, len -> c.rom(off, len) })
            var step = 0
            fun shot(name: String) { shots?.let { g.shot(File(it, "%02d-%s.ppm".format(++step, name))) } }
            fun press(times: Int, wait: Int = 40) { repeat(times) { g.run("hold A 3", "wait $wait") } }
            fun closeText() { repeat(8) { g.run("hold B 3", "wait 60") }; g.run("wait 200") }
            // native-capture/boot/gold.txt
            g.run("wait 600", "hold START 3", "wait 200", "hold A 3", "wait 120", "hold A 3", "wait 120", "hold A 3", "wait 300")
            shot("field")

            // The bag: POTION, REPEL, RARE CANDY, MOON STONE, PROTEIN, PP UP, ESCAPE ROPE; keys: BICYCLE, OLD ROD, COIN CASE; HM01.
            val items = listOf(0x12 to 5, 0x14 to 5, 0x20 to 3, 0x08 to 2, 0x1B to 2, 0x3E to 2, 0x13 to 2)
            c.write(cfg.numItems, (listOf(items.size) + items.flatMap { listOf(it.first, it.second) } + 0xFF).map { it.toByte() }.toByteArray())
            c.write(cfg.numKeyItems, byteArrayOf(3, 0x07, 0x3A, 0x36, 0xFF.toByte()))
            c.write(cfg.tmsHms + 50, byteArrayOf(1))
            fun qty(id: Int): Int {
                val b = c.read(cfg.numItems, 1 + 2 * 20)
                return (0 until (b[0].toInt() and 0xFF)).firstOrNull { (b[1 + 2 * it].toInt() and 0xFF) == id }?.let { b[2 + 2 * it].toInt() and 0xFF } ?: 0
            }
            // COIN CASE: its text. Not on English Gold / Silver here: with the German save in it (no English save on this machine), the
            // game's own SELECT COIN CASE leaves junk in the script flags too, so the save can't show it.
            if (cfg !== GEN2_GOLD && cfg !== GEN2_SILVER) {
                assertEquals(ItemUseResult.USED, field.useItem(c, 0x36, null, 0).result)
                g.run("wait 150"); shot("coins"); closeText()
            }

            val mon0 = cfg.partyMons
            val MON = 0x30L

            // REPEL on the spot.
            val repel = field.useItem(c, 0x14, null, 0)
            assertEquals(ItemUseResult.USED, repel.result)
            assertEquals("wRepelEffect", 100, u8(c, calls.repelEffect))
            assertEquals("a REPEL spent", 4, qty(0x14))
            c.write(calls.repelEffect, byteArrayOf(0))

            // POTION on the first Pokémon, its HP down to 3.
            c.write(mon0 + 0x22, byteArrayOf(0, 3))
            val t = field.itemTargets(c, 0x12)
            assertEquals(ItemUseResult.USED, t.result)
            assertEquals(SlotVerdict.OK, t.targets[0].verdict)
            val potion = field.useItem(c, 0x12, t.targets[0], 0)
            println("POTION: $potion")
            g.run("wait 120"); press(3); g.run("wait 200")
            val hp = c.read(mon0 + 0x22, 2).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }
            println("HP after POTION: $hp")
            assertTrue("HP healed", hp > 3)
            assertEquals("a POTION spent", 4, qty(0x12))
            shot("potion")

            // PROTEIN: the attack stat experience's high byte + 10.
            val expBefore = u8(c, mon0 + 0x0B + 2)
            val protein = field.useItem(c, 0x1B, field.itemTargets(c, 0x1B).targets[0], 0)
            println("PROTEIN: $protein")
            g.run("wait 120"); press(3); g.run("wait 200")
            assertEquals("stat experience", expBefore + 10, u8(c, mon0 + 0x0B + 2))
            assertEquals("a PROTEIN spent", 1, qty(0x1B))
            shot("protein")

            // PP UP on the first move.
            val ppBefore = u8(c, mon0 + 0x17)
            val ppup = field.itemTargets(c, 0x3E).targets[0]
            println("PP UP: " + field.useItem(c, 0x3E, ppup, 0))
            g.run("wait 120"); press(3); g.run("wait 200")
            assertEquals("a PP UP on move 1", (ppBefore and 0xC0) + 0x40, u8(c, mon0 + 0x17) and 0xC0)
            shot("ppup")

            // HM01 (CUT) taught to the first Pokémon, if it can learn it; else to whoever can.
            val hm = field.itemTargets(c, 0xF3)
            println("HM01 targets: ${hm.targets.map { it.verdict }}")
            hm.targets.firstOrNull { it.verdict == SlotVerdict.OK }?.let { tgt ->
                assertEquals(ItemUseResult.TEACHING, field.useItem(c, 0xF3, tgt, 0).result)
                g.run("wait 60"); shot("hm-text"); press(8, 60); g.run("wait 200")
                val moves = c.read(mon0 + tgt.slot * MON + 2, 4).map { it.toInt() and 0xFF }
                println("moves after HM01: $moves")
                assertTrue("CUT learned", 0x0F in moves)
                shot("hm")
            }

            // RARE CANDY: a level up.
            val lvBefore = u8(c, mon0 + 0x1F)
            assertEquals(ItemUseResult.USING_IN_GAME, field.useItem(c, 0x20, field.itemTargets(c, 0x20).targets[0], 0).result)
            g.run("wait 90"); shot("candy-menu"); press(9, 60); g.run("wait 200")
            assertEquals("a level", lvBefore + 1, u8(c, mon0 + 0x1F))
            assertEquals("a RARE CANDY spent", 2, qty(0x20))
            shot("candy")

            // MOON STONE on a CLEFAIRY (slot 2 made one: species in the list and the struct).
            c.write(cfg.partyCount + 2, byteArrayOf(0x23))
            c.write(mon0 + MON, byteArrayOf(0x23))
            val st = field.itemTargets(c, 0x08)
            println("MOON STONE targets: ${st.targets.map { it.verdict }}")
            assertEquals(SlotVerdict.OK, st.targets[1].verdict)
            assertEquals(ItemUseResult.USING_IN_GAME, field.useItem(c, 0x08, st.targets[1], 0).result)
            g.run("wait 90"); shot("stone-menu"); g.run("wait 600"); shot("stone-evolving"); press(12, 80); g.run("wait 400")
            assertEquals("CLEFABLE", 0x24, u8(c, mon0 + MON))
            assertEquals("a MOON STONE spent", 1, qty(0x08))
            shot("stone")

            // BICYCLE on ROUTE 29: on, then off.
            val bike = field.useItem(c, 0x07, null, 0)
            println("BICYCLE: $bike")
            assertEquals(ItemUseResult.USED, bike.result)
            g.run("wait 120"); press(1); g.run("wait 120")
            assertEquals("on the bike", 1, u8(c, 0x1D682))
            shot("bike")
            assertEquals(ItemUseResult.USED, field.useItem(c, 0x07, null, 0).result)
            g.run("wait 120"); press(1); g.run("wait 120")
            assertEquals("off the bike", 0, u8(c, 0x1D682))

            // OLD ROD away from water and ESCAPE ROPE outdoors: the game says no, nothing spent, nothing stuck.
            println("OLD ROD: " + field.useItem(c, 0x3A, null, 0))
            g.run("wait 60"); shot("rod"); closeText()
            println("ESCAPE ROPE: " + field.useItem(c, 0x13, null, 0))
            g.run("wait 60"); shot("rope"); closeText()
            assertEquals("no ESCAPE ROPE spent outdoors", 2, qty(0x13))
            // The field is free again.
            val again = field.useItem(c, 0x14, null, 0)
            assertEquals("REPEL again: the field free", ItemUseResult.USED, again.result)
        }
    }
}
