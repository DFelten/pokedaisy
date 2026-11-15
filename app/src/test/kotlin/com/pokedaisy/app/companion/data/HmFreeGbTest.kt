package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * NO HMS NEEDED on the Game Boy (HmFreeGb.kt): each English cart takes its patch (every byte it replaces checked), in
 * 4-byte writes; another cart's bytes, or a changed one, get none. The patched games were played headless with
 * mgba_dump's `rompatch` (the party menus, STRENGTH / FLASH used, Gold surfing with nobody knowing the move).
 */
class HmFreeGbTest {
    private val dirs = listOf("gb", "gbc").map { File(File(RETAIL_ROM_DIR).parentFile, it) }

    private fun rom(name: String): ByteArray? = dirs.map { File(it, name) }.firstOrNull { it.isFile }?.readBytes()

    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }

    private val carts = listOf(
        "Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb",
        "Pokemon - Blue Version (USA, Europe) (SGB Enhanced).gb",
        "Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb",
        "Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc",
        "Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc",
        "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc",
        "Pokemon - Crystal Version (USA).gbc",
        "Pokemon - Crystal Version (Australia).gbc",
    )

    @Test
    fun `every English cart takes its patch`() {
        var checked = 0
        for (name in carts) {
            val b = rom(name) ?: continue
            val patch = HmFreeGb.find(sha1(b), b)
            assertNotNull(name, patch)
            assertTrue(name, patch!!.writes.all { it.value.size in 1..4 && it.expect.size == it.value.size })
            // each write's bytes are the cart's own
            for (w in patch.writes) assertTrue(name, b.copyOfRange(w.addr.toInt(), w.addr.toInt() + w.expect.size).contentEquals(w.expect))
            checked++
        }
        assumeTrue("no English Game Boy cart on this machine", checked > 0)
    }

    @Test
    fun `a cart whose bytes differ gets nothing`() {
        val b = rom(carts[0]) ?: return assumeTrue("Red not on this machine", false)
        val hash = sha1(b)
        // the bank's unused end taken by something else
        val busy = b.copyOf().also { it[0x7F10] = 0x12 }
        assertNull(HmFreeGb.find(hash, busy))
        // the jump's place changed
        val moved = b.copyOf().also { it[0x76ED] = 0 }
        assertNull(HmFreeGb.find(hash, moved))
        // an unknown hash
        assertNull(HmFreeGb.find("0".repeat(40), b))
    }

    @Test
    fun `the generated table covers the six games`() {
        assertEquals(listOf("RED", "BLUE", "YELLOW", "GOLD", "SILVER", "CRYSTAL"), HM_FREE_GB.map { it.name })
    }
}
