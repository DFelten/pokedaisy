package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import okio.Path.Companion.toOkioPath

/**
 * NO HMS NEEDED's two places, found in each ROM by their code ([HmFree.locate]): ScrCmd_checkpartymove (its jump to scripts/hm_free.s in the padding) and PartyHasMonWithSurf's `beq`. What they call must be the GetMonData of that build's field-item
 * table (found its own way: port_field_items.py, the decomps). Played headless with mgba_dump's `rompatch` on FireRed rev 1
 * and Emerald. Skips a ROM that isn't here.
 */
class HmFreeTest {
    private val cases = listOf(
        "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba" to true,
        "Pokemon - FireRed Version (USA).gba" to true,
        "Pokemon - LeafGreen Version (USA).gba" to true,
        "Pokemon - Emerald Version (USA, Europe).gba" to true,
        "Pokemon - Ruby Version (USA, Europe) (Rev 1).gba" to true,
        "Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba" to true,
        // Hacks that kept that code.
        "Pokemon Emerald Kaizo (v2.0).gba" to true,
        "Pokemon Emerald Legacy (v1.1.4).gba" to true,
        "Pokemon Glazed (v9.2.0).gba" to true,
        "Pokemon Unbound (v2.1.1.1).gba" to true,
        "Pokemon Radical Red (v4.1).gba" to true,
        "Pokemon Ultra Violet (v1.22).gba" to true,
        // gcc builds: another shape.
        "Pokemon Lazarus (v2.0).gba" to false,
        "Pokemon SoulGold (v1.1.4).gba" to false,
        "Pokemon Recharged Emerald (v2.2.8).gba" to false,
        "Pokemon Emerald Rogue (v2.2.1-EX).gba" to false,
        // Celia keeps the command, not Surf's check: none (all or nothing).
        "Pokemon Celia's Stupid Romhack (v1.1.4).gba" to false,
    )

    @Test
    fun findsEachGame() {
        for ((name, want) in cases) {
            val rom = File(RETAIL_ROM_DIR + name)
            if (!rom.isFile) continue
            assertEquals(name, want, HmFree.locate(rom.toOkioPath()) != null)
        }
    }

    @Test
    fun retailFireRedAndEmerald() {
        val fr = File(RETAIL_ROM_DIR + "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba")
        if (fr.isFile) check(fr, check = 0x0806C0BCL, surf = 0x0805C850L, PARTY_ITEMS_FIRERED_REV1, FIELD_ITEMS_FIRERED_REV1)
        val em = File(RETAIL_ROM_DIR + "Pokemon - Emerald Version (USA, Europe).gba")
        if (em.isFile) check(em, check = 0x0809B3DCL, surf = 0x0808BE00L, PARTY_ITEMS_EMERALD, FIELD_ITEMS_EMERALD)
    }

    /** [rom]'s two places, its calls / party, the jump into the padding and the replacement's words. */
    private fun check(rom: File, check: Long, surf: Long, party: PartyItemFns, field: FieldItemFns) {
        val f = HmFree.locate(rom.toOkioPath())
        assertNotNull(rom.name, f)
        f!!
        assertEquals("%08X".format(check), "%08X".format(f.check))
        assertEquals("%08X".format(surf), "%08X".format(f.surf))
        assertEquals("%08X".format(party.getMonData), "%08X".format(f.getMonData))
        assertEquals("%08X".format(party.party), "%08X".format(f.party))
        assertEquals("%08X".format(field.checkBagHasItem), "%08X".format(f.checkBagHasItem))
        val bytes = rom.readBytes()
        val patched = bytes.copyOf()
        f.patch.writes.forEach { w ->
            val at = (w.addr - 0x08000000L).toInt()
            assertTrue(rom.name, w.expect.contentEquals(bytes.copyOfRange(at, at + w.expect.size)))
            w.value.copyInto(patched, at)
        }
        fun u32(at: Int) = (0..3).fold(0L) { v, i -> v or ((patched[at + i].toLong() and 0xFF) shl (8 * i)) }
        val c = (check - 0x08000000L).toInt()
        // `ldr r3, [pc, #0]; bx r3` and the replacement's address (Thumb), into 0xFF padding.
        assertEquals(0x47184B00L, u32(c))
        assertEquals(f.at + 1, u32(c + 4))
        val at = (f.at - 0x08000000L).toInt()
        assertTrue((0 until 204).all { bytes[at + it] == 0xFF.toByte() })
        assertEquals(f.party, u32(at + 0xA0))
        assertEquals(f.getMonData + 1, u32(at + 0xAC))
        assertEquals(f.monKnowsMove + 1, u32(at + 0xB0))
        assertEquals(f.checkBagHasItem + 1, u32(at + 0xB4))
        assertEquals(339L, u32(at + 0xB8))
        // mgba_dump's `rompatch` lines, to play it headless (app/build/hm-free/<rom>.txt).
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        File("build/hm-free").apply { mkdirs() }.resolve(rom.nameWithoutExtension + ".txt")
            .writeText(f.patch.writes.joinToString("") { "rompatch %x %s %x %s\n".format(it.addr, hex(it.expect), it.addr, hex(it.value)) })
        // The rest of the original is left as it was.
        assertTrue((8 until 124).all { patched[c + it] == bytes[c + it] })
        // Surf's `beq` is a `nop` now, nothing else of it changed.
        val s = (surf - 0x08000000L).toInt()
        assertEquals(0xC0, patched[s + 0x1E].toInt() and 0xFF)
        assertEquals(0x46, patched[s + 0x1F].toInt() and 0xFF)
        assertTrue((0 until 0x50).filter { it !in 0x1E..0x1F }.all { patched[s + it] == bytes[s + it] })
    }

    /** Every retail cart in the retail ROM dir (-DmultiRomDir=...): found, calling its build's GetMonData. */
    @Test
    fun everyRetailBuild() {
        val dir = File(System.getProperty("multiRomDir") ?: RETAIL_ROM_DIR)
        assumeTrue(dir.isDirectory)
        var checked = 0
        for (f in retailRomFiles(dir).filter { listOf("Pirate", "Debug").none { x -> x in it.name } }) {
            val h = f.inputStream().use { ByteArray(0xC0).also { b -> it.read(b) } }
            val found = HmFree.locate(f.toOkioPath())
            assertNotNull(f.name, found)
            val party = fieldItemsFor(String(h, 0xAC, 4), h[0xBC].toInt())?.party ?: continue
            assertEquals(f.name, "%08X".format(party.getMonData), "%08X".format(found!!.getMonData))
            assertEquals(f.name, "%08X".format(party.party), "%08X".format(found.party))
            val bag = fieldItemsFor(String(h, 0xAC, 4), h[0xBC].toInt())!!.checkBagHasItem
            if (bag != 0L) assertEquals(f.name, "%08X".format(bag), "%08X".format(found.checkBagHasItem))
            checked++
        }
        assertTrue("checked $checked", checked >= 30)
    }

    /** A ROM whose HMs aren't vanilla's: the replacement without the bag check, nothing else different. */
    @Test
    fun withoutVanillaHmsNoBagCheck() {
        val em = File(RETAIL_ROM_DIR + "Pokemon - Emerald Version (USA, Europe).gba")
        if (!em.isFile) return
        val bytes = em.readBytes()
        val hm = byteArrayOf(0x0F, 0, 0x13, 0, 0x39, 0, 0x46, 0, 0x94.toByte(), 0, 0xF9.toByte(), 0, 0x7F, 0, 0x23, 1)
        val at = (0 until bytes.size - 16).filter { i -> (0 until 16).all { bytes[i + it] == hm[it] } }
        assertTrue(at.isNotEmpty())
        at.forEach { bytes[it] = 0x10 }
        val f = HmFree.locate(LittleEndianBytes(bytes))
        assertNotNull(f)
        assertEquals(0L, f!!.checkBagHasItem)
        assertEquals(0L, f.patch.checkBagHasItem)
        val skip = f.patch.writes.first { it.addr == f.at + 0x60 }
        assertEquals(0x0A, skip.value[2].toInt() and 0xFF)
        assertEquals(0xE0, skip.value[3].toInt() and 0xFF)
    }

    @Test
    fun nothingInAnEmptyRom() {
        assertNull(HmFree.locate(LittleEndianBytes(ByteArray(0x10000))))
    }
}
