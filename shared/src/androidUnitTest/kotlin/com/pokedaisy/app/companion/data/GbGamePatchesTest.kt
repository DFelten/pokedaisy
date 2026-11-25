package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** INSTANT TEXT, REUSABLE TMS and LOW HP BEEP on the Game Boy carts ([GbGamePatches]): where pret's symbols put the code. */
class GbGamePatchesTest {
    private val dir = File(File(RETAIL_ROM_DIR).parentFile, "gbc")
    private fun rom(name: String) = File(dir, name).takeIf { it.isFile }?.readBytes()
    private val tag = GbGamePatches.TAG

    @Test
    fun `Yellow - PrintLetterDelay, ItemUseTMHM's ret c, the alarm's set`() {
        val f = GbGamePatches.find(rom("Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb") ?: return assumeTrue(false))!!
        assertEquals(tag + 0x38C8, f.text!!.writes.single().addr)                       // 00:38C8 PrintLetterDelay
        assertEquals(tag + 0xE47F, f.tms.single().branch)                               // 03:6374 ItemUseTMHM's end
        assertEquals(0xC9.toByte(), f.tms.single().value.single())
        assertEquals(tag + 0x3CEAE, f.beep!!.writes.single().addr)                      // 0F:4EAB .setLowHealthAlarm + 3
        assertEquals(tag + 0xC407, f.poison!!.writes.single().addr)                     // its jp nz, in ApplyOutOfBattlePoisonDamage
        assertEquals(0x1D057L, f.exp!!.sentPokes)                                         // wPartyGainExpFlags (bank 1)
        assertEquals(1, f.exp!!.width)
        assertEquals(tag + 0x5547B + 17, f.exp!!.full.single().addr)                    // DivideExpDataByNumMonsGainingExp's cp 2
    }

    @Test
    fun `Crystal and Gold - their own TM and alarm code`() {
        rom("Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc")?.let { r ->
            val f = GbGamePatches.find(r)!!
            assertEquals(tag + 0x313D, f.text!!.writes.single().addr)                   // 00:313D PrintLetterDelay
            assertEquals(tag + 0x2C8B1, f.tms.single().branch)                          // call ConsumeTM
            assertEquals(3, f.tms.single().value.size)
            assertEquals(tag + 0x3DFBC, f.beep!!.writes.single().addr)                  // 0F:5FB9 CheckDanger.danger + 3
            assertEquals(0xC664L, f.exp!!.sentPokes)                                     // wBattleParticipantsNotFainted
            assertEquals(tag + 0x3F0D4 + 16, f.exp!!.full.single().addr)               // .EvenlyDivideExpAmongParticipants' cp 2
        }
        rom("Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc")?.let { r ->
            val f = GbGamePatches.find(r)!!
            assertEquals(tag + 0x31E2, f.text!!.writes.single().addr)                   // 00:31E2 PrintLetterDelay
            assertEquals(tag + 0x3DE1D, f.beep!!.writes.single().addr)                  // 0F:5E1A CheckDanger.danger + 3
        }
    }

    @Test
    fun `EXP SHARE MODERN - Gen 2's gained box and done hooked, its trampolines and line in the battle bank's padding`() {
        val carts = listOf(
            "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc", "Pokemon - Kristall-Edition (Germany).gbc",
            "Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc",
            "Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc",
            "Pokemon - Goldene Edition (Germany) (SGB Enhanced) (GB Compatible).gbc",
            "Pokemon - Silberne Edition (Germany) (SGB Enhanced) (GB Compatible).gbc",
            "Pokemon - Version Cristal (France).gbc", "Pokemon - Versione Cristallo (Italy).gbc", "Pokemon - Edicion Cristal (Spain).gbc",
            "Pokemon - Version Or (France) (SGB Enhanced).gbc", "Pokemon - Version Argent (France) (SGB Enhanced).gbc",
            "Pokemon - Versione Oro (Italy) (SGB Enhanced).gbc", "Pokemon - Versione Argento (Italy) (SGB Enhanced).gbc",
            "Pokemon - Edicion Oro (Spain) (SGB Enhanced).gbc", "Pokemon - Edicion Plata (Spain) (SGB Enhanced).gbc")
        var seen = 0
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        for (name in carts) {
            val r = rom(name) ?: continue
            seen++
            val exp = GbGamePatches.find(r)!!.exp!!
            val m = assertNotNull(name, exp.modern).let { exp.modern!! }
            assertEquals(name, exp.full.single().addr, m.first().addr)                     // FULL's ret first
            // Every write but the two hooks goes over the bank's padding, all in bank 0F.
            assertTrue(name, m.all { (it.addr - tag) / 0x4000 == 0xFL })
            val hooks = m.filter { p -> p.expect.any { it != 0.toByte() } }
            assertEquals(name, 1 + 5 + 2 + 1, hooks.size)                                    // the ret, the check (18 bytes), the call (6), the jp
            // mgba_dump's `rompatch` lines, to play it headless (app/build/exp-modern/<cart>.txt).
            File("build/exp-modern").apply { mkdirs() }.resolve(name.substringBefore(" (") + ".txt")
                .writeText(m.joinToString("") { "rompatch %x %s %x %s\n".format(it.addr, hex(it.expect), it.addr, hex(it.value)) })
        }
        rom("Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc")?.let { r ->
            val m = GbGamePatches.find(r)!!.exp!!.modern!!
            val hooks = m.drop(1).filter { p -> p.expect.any { it != 0.toByte() } }.map { it.addr }
            assertEquals(listOf(0x3EE5A, 0x3EE5E, 0x3EE62, 0x3EE66, 0x3EE6A, 0x3EF0B, 0x3EF0F, 0x3F0B9 + 24).map { tag + it }, hooks)   // the check, the gained box's call, .done's jp
        }
        assumeTrue(seen > 0)
    }

    @Test
    fun `EXP SHARE MODERN on Red, Blue and Yellow - four hooks into GainExperience's bank, the line in the cart's language`() {
        val carts = listOf("Pokemon - Rote Edition (Germany) (SGB Enhanced).gb", "Pokemon - Blaue Edition (Germany) (SGB Enhanced).gb",
            "Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb", "Pokemon - Blue Version (USA, Europe) (SGB Enhanced).gb",
            "Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb",
            "Pokemon - Gelbe Edition - Special Pikachu Edition (Germany) (GBC,SGB Enhanced).gb")
        var seen = 0
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        for (name in carts) {
            val r = rom(name) ?: continue
            seen++
            val exp = GbGamePatches.find(r)!!.exp!!
            val m = assertNotNull(name, exp.modern).let { exp.modern!! }
            assertEquals(name, exp.full.single().addr, m.first().addr)
            val bank = (exp.full.single().addr - tag) / 0x4000
            assertTrue(name, m.all { (it.addr - tag) / 0x4000 == bank })
            val hooks = m.drop(1).filter { p -> p.expect.any { it != 0.toByte() } }
            assertEquals(name, 4 + 1 + 2 + 1, hooks.size)                                    // the check (16 bytes), the boost's call, the box's (6), .done's jp
            // "erhalten auch EP!" in German: its "EP!" bytes (84 8F E7) in the trampolines' writes.
            val all = m.filter { p -> p.expect.none { it != 0.toByte() } }.flatMap { it.value.toList() }.toByteArray()
            assertEquals(name, "Germany" in name, hex(all).contains("848fe7"))
            File("build/exp-modern").apply { mkdirs() }.resolve(name.substringBefore(" (") + ".txt")
                .writeText(m.joinToString("") { "rompatch %x %s %x %s\n".format(it.addr, hex(it.expect), it.addr, hex(it.value)) })
        }
        assumeTrue(seen > 0)
    }

    @Test
    fun `every Game Boy cart here - all three or the ones it has, never a guess`() {
        assumeTrue(dir.isDirectory)
        var n = 0
        dir.listFiles()!!.filter { it.extension == "gb" || it.extension == "gbc" }.forEach { file ->
            val f = GbGamePatches.find(file.readBytes()) ?: return@forEach
            val polished = "Polished" in file.name || "Prism" in file.name
            if (polished) assertTrue(file.name, f.tms.isEmpty() && f.text == null && f.beep == null)
            if ("Pokemon - " in file.name) { assertNotNull(file.name, f.poison); assertNotNull(file.name, f.exp) }
            if ("Pokemon - " in file.name) {
                assertNotNull(file.name, f.text); assertNotNull(file.name, f.beep); assertEquals(file.name, 1, f.tms.size)
                n++
            }
            if ("PureRed" in file.name || "PureBlue" in file.name) assertNull(file.name, f.text)   // three look-alikes
        }
        assertTrue("checked $n", n >= 12)
    }
}
