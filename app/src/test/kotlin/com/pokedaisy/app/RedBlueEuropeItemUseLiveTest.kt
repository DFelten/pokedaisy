package com.pokedaisy.app

import com.pokedaisy.app.companion.data.GameCalls
import com.pokedaisy.app.companion.data.GbField
import com.pokedaisy.app.companion.data.GB_FIELD_BLUE_IT
import com.pokedaisy.app.companion.data.GB_FIELD_RED_BLUE_ES
import com.pokedaisy.app.companion.data.GB_FIELD_RED_BLUE_FR
import com.pokedaisy.app.companion.data.GB_FIELD_RED_IT
import com.pokedaisy.app.companion.data.Gen1ItemCalls
import com.pokedaisy.app.companion.data.ItemUseResult
import com.pokedaisy.app.companion.data.RETAIL_ROM_DIR
import com.pokedaisy.app.companion.data.SlotVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * French, Italian and Spanish Red / Blue's bag USE run as the app runs it ([GbField.Gen1]) against the running game
 * (mgba_dump headless, [GbLiveGame]), on each cart's save (the red_fr .. blue_es fixtures' READMEs): an item spent by the
 * game's RemoveItemFromInventory, the companion-picked ones through the game's own UseItem, the result read back from RAM.
 *
 * Opt-in (Docker, the player's ROMs and saves): POKEDAISY_GB_LIVE=1 make test-quick T='*RedBlueEuropeItemUseLiveTest'.
 * RB_LIVE_SHOTS=<dir> keeps a PPM of the screen after each step.
 */
class RedBlueEuropeItemUseLiveTest {
    private val gbc = File(File(RETAIL_ROM_DIR).parentFile, "gbc")
    private val saves = File(System.getProperty("user.home"), "Documents/RetroArch/saves/mGBA")

    private class Calls(val g: GbLiveGame, val romBytes: ByteArray) : GameCalls {
        override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int) = -1L
        private fun bankOf(addr: Long) = if ((addr and 0xFFFF) >= 0xD000) 1 else 0
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

    @Test fun redFr() = run(GB_FIELD_RED_BLUE_FR, "Pokemon - Version Rouge (France) (SGB Enhanced)")
    @Test fun blueFr() = run(GB_FIELD_RED_BLUE_FR, "Pokemon - Version Bleue (France) (SGB Enhanced)")
    @Test fun redIt() = run(GB_FIELD_RED_IT, "Pokemon - Versione Rossa (Italy) (SGB Enhanced)")
    @Test fun blueIt() = run(GB_FIELD_BLUE_IT, "Pokemon - Versione Blu (Italy) (SGB Enhanced)")
    @Test fun redEs() = run(GB_FIELD_RED_BLUE_ES, "Pokemon - Edicion Roja (Spain) (SGB Enhanced)")
    @Test fun blueEs() = run(GB_FIELD_RED_BLUE_ES, "Pokemon - Edicion Azul (Spain) (SGB Enhanced)")

    private fun run(field: GbField.Gen1, name: String) {
        assumeTrue("POKEDAISY_GB_LIVE not set", System.getenv("POKEDAISY_GB_LIVE") != null)
        val rom = File(gbc, "$name.gb")
        val save = File(saves, "$name.sav")
        assumeTrue("ROM / save not on this machine", rom.isFile && save.isFile)
        val shots = System.getenv("RB_LIVE_SHOTS")?.let { File(it, name.substringAfter(" - ").substringBefore(" (")).also { d -> d.mkdirs() } }
        GbLiveGame(rom, save, log = { println("  mgba_dump: $it") }).use { g ->
            val c = Calls(g, rom.readBytes())
            val calls = field.bagUse as Gen1ItemCalls
            assertTrue("the item code in the cart", calls.codeMatches { off, len -> c.rom(off, len) })
            var step = 0
            fun shot(s: String) { shots?.let { g.shot(File(it, "%02d-%s.ppm".format(++step, s))) } }
            fun press(times: Int, wait: Int = 40) { repeat(times) { g.run("hold A 3", "wait $wait") } }
            fun u8(addr: Long) = c.read(addr, 1)[0].toInt() and 0xFF
            // native-capture/boot/red.txt
            g.run("wait 1600", "hold START 3", "wait 200", "hold A 3", "wait 120", "hold A 3", "wait 300")
            shot("field")

            // The bag: POTION, REPEL, HP UP, PP UP, RARE CANDY, THUNDER STONE.
            val items = listOf(0x14 to 5, 0x1E to 5, 0x23 to 2, 0x4F to 2, 0x28 to 3, 0x21 to 2)
            c.write(calls.numBagItems, (listOf(items.size) + items.flatMap { listOf(it.first, it.second) } + 0xFF).map { it.toByte() }.toByteArray())
            fun qty(id: Int): Int {
                val b = c.read(calls.numBagItems, 1 + 2 * 20)
                return (0 until (b[0].toInt() and 0xFF)).firstOrNull { (b[1 + 2 * it].toInt() and 0xFF) == id }?.let { b[2 + 2 * it].toInt() and 0xFF } ?: 0
            }
            val mon0 = calls.partyCount + 8
            val MON = 0x2CL

            // REPEL on the spot.
            assertEquals(ItemUseResult.USED, field.useItem(c, 0x1E, null, 0).result)
            assertEquals("wRepelRemainingSteps", 100, u8(calls.repelSteps))
            assertEquals("a REPEL spent", 4, qty(0x1E))

            // POTION on the first Pokémon, its HP down to 3.
            c.write(mon0 + 1, byteArrayOf(0, 3))
            val t = field.itemTargets(c, 0x14)
            assertEquals(SlotVerdict.OK, t.targets[0].verdict)
            assertEquals(ItemUseResult.USED, field.useItem(c, 0x14, t.targets[0], 0).result)
            assertEquals("HP + 20", 23, c.read(mon0 + 1, 2).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) })
            assertEquals("a POTION spent", 4, qty(0x14))

            // PP UP on the first move.
            val ppBefore = u8(mon0 + 0x1D)
            println("PP UP: " + field.useItem(c, 0x4F, field.itemTargets(c, 0x4F).targets[0], 0))
            assertEquals("a PP UP on move 1", (ppBefore and 0xC0) + 0x40, u8(mon0 + 0x1D) and 0xC0)
            assertEquals("a PP UP spent", 1, qty(0x4F))

            // HP UP through the game's UseItem: HP stat experience's high byte + 10, from 0.
            c.write(mon0 + 0x11, byteArrayOf(0, 0))
            val hpUp = field.useItem(c, 0x23, field.itemTargets(c, 0x23).targets[0], 0)
            println("HP UP: $hpUp")
            g.run("wait 120"); shot("hpup"); press(4); g.run("wait 200")
            assertEquals("stat experience", 10, u8(mon0 + 0x11))
            assertEquals("an HP UP spent", 1, qty(0x23))

            // RARE CANDY on the second Pokémon, brought down to level 50.
            c.write(mon0 + MON + 0x21, byteArrayOf(50))
            c.write(mon0 + MON + 3, byteArrayOf(50))
            val candy = field.itemTargets(c, 0x28)
            assertEquals(SlotVerdict.OK, candy.targets[1].verdict)
            assertEquals(ItemUseResult.USING_IN_GAME, field.useItem(c, 0x28, candy.targets[1], 0).result)
            g.run("wait 90"); shot("candy-menu"); press(8, 60); g.run("wait 200")
            assertEquals("a level", 51, u8(mon0 + MON + 0x21))
            assertEquals("a RARE CANDY spent", 2, qty(0x28))
            shot("candy")

            // THUNDER STONE on PIKACHU (slot 0): RAICHU.
            val stone = field.itemTargets(c, 0x21)
            assertEquals(SlotVerdict.OK, stone.targets[0].verdict)
            assertEquals(ItemUseResult.USING_IN_GAME, field.useItem(c, 0x21, stone.targets[0], 0).result)
            g.run("wait 90"); shot("stone-menu"); g.run("wait 600"); shot("stone-evolving"); press(8, 80); g.run("wait 300")
            assertEquals("RAICHU", 0x55, u8(mon0))
            assertEquals("a THUNDER STONE spent", 1, qty(0x21))
            shot("stone")

            // The field is free again.
            assertEquals("REPEL again: the field free", ItemUseResult.USED, field.useItem(c, 0x1E, null, 0).result)
        }
    }
}
