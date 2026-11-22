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
    fun `NO HMS NEEDED - CheckPartyMove jumps to its trampoline near the end of bank 3`() {
        rom("Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc")?.let { r ->
            val w = GbGamePatches.find(r)!!.hm!!.writes
            // mgba_dump's `rompatch` lines, to play it headless (app/build/hm-free/crystal.txt).
            fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
            File("build/hm-free").apply { mkdirs() }.resolve("crystal.txt")
                .writeText(w.joinToString("") { "rompatch %x %s %x %s\n".format(it.addr, hex(it.expect), it.addr, hex(it.value)) })
            val hook = w.last()
            assertEquals(tag + 0xC742, hook.addr)                                         // 03:4742 CheckPartyMove
            assertEquals("c3877f", hook.value.joinToString("") { "%02x".format(it) })    // jp 03:7F87
            val tramp = w.dropLast(1)
            assertEquals(tag + 0xFF87, tramp.first().addr)
            assertEquals(89, tramp.sumOf { it.value.size })
            assertTrue(tramp.all { p -> p.expect.all { it == 0.toByte() } })              // the bank's padding
            // call 03:4745 (its own check, after `ld e, 0; xor a`), HM01's count at D88B, jp nz, 03:4771 (.yes) for the first non-egg.
            val code = tramp.flatMap { it.value.toList() }.toByteArray()
            assertEquals("1e00afcd4547d07a", code.copyOfRange(0, 8).joinToString("") { "%02x".format(it) })
            assertEquals("060021 8bd8 097ea728 19".replace(" ", ""), code.copyOfRange(52, 62).joinToString("") { "%02x".format(it) }) // wTMsHMs + 50
            assertEquals("fefdc27147", code.copyOfRange(79, 84).joinToString("") { "%02x".format(it) })
        }
        rom("Pokemon - Kristall-Edition (Germany).gbc")?.let { assertNotNull(GbGamePatches.find(it)!!.hm) }
        rom("Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc")?.let { assertNotNull(GbGamePatches.find(it)!!.hm) }
        rom("Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb")?.let { assertNull(GbGamePatches.find(it)!!.hm) }
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
