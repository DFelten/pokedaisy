package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.data.PlayerLook
import com.pokedaisy.app.companion.data.TextLook
import com.pokedaisy.app.companion.data.WildPin
import com.pokedaisy.app.game.PlayerLookCore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamePlayerLookTest {
    @Test fun equivalentChoicesSkipReapplyAndOnlyInstalledPalettesAreRestored() {
        val core = Core()
        val look = GamePlayerLook(core)
        val palette = ByteArray(32) { 0x11 }
        fun patch(bytes: ByteArray) = PlayerLook.Patch(
            listOf(PlayerLook.Block(0x20, -1, bytes)),
            listOf(PlayerLook.Pal(listOf(ByteArray(32)), palette)),
            palette,
        )

        look.select(patch(byteArrayOf(1, 2)))
        assertTrue(look.service())
        assertContentEquals(intArrayOf(0x08000020), core.addresses)
        assertContentEquals(byteArrayOf(1, 2), core.bytes)
        look.select(patch(byteArrayOf(1, 2)))
        assertFalse(look.service())
        look.settle()
        assertEquals(2, core.swaps)
        look.fieldFrame()
        assertContentEquals(palette, core.lastPalette)

        core.reject = true
        look.select(patch(byteArrayOf(3)))
        assertTrue(look.service())
        look.settle()
        look.fieldFrame()
        assertEquals(2, core.swaps)
        assertNull(core.lastPalette)

        core.reject = false
        look.select(null)
        assertTrue(look.service())
        assertTrue(core.addresses.isEmpty())
    }

    @Test fun clearingTextLeavesTheChosenHeroInstalled() {
        val core = Core()
        val look = GamePlayerLook(core)
        val palette = ByteArray(32) { 0x11 }
        val hero = PlayerLook.Patch(listOf(PlayerLook.Block(0x20, -1, byteArrayOf(1, 2))), emptyList(), palette)
        val text = listOf(PlayerLook.Block(0x40, -1, byteArrayOf(3)))

        look.select(TextLook.withAppearance(null, text))
        assertTrue(look.service())
        assertContentEquals(intArrayOf(0x08000040), core.addresses)

        look.select(TextLook.withAppearance(hero, text))
        assertTrue(look.service())
        assertContentEquals(intArrayOf(0x08000020, 0x08000040), core.addresses)
        assertContentEquals(byteArrayOf(1, 2, 3), core.bytes)
        look.fieldFrame()
        assertContentEquals(palette, core.lastPalette)

        look.select(TextLook.withAppearance(hero, emptyList()))
        assertTrue(look.service())
        assertContentEquals(intArrayOf(0x08000020), core.addresses)
        assertContentEquals(byteArrayOf(1, 2), core.bytes)
    }

    @Test fun aWildPinGoesInWithTheLookAndARejectedOneLeavesTheLookAlone() {
        val core = Core()
        val results = mutableListOf<Pair<WildPin.Pin?, Boolean>>()
        val look = GamePlayerLook(core) { pin, installed -> results += pin to installed }
        val slots = ByteArray(48).also { b -> for (i in 0 until 12) { b[i * 4] = 2; b[i * 4 + 1] = 4; b[i * 4 + 2] = 10 } }
        val pin = WildPin.pin(10, 3, 19, 0, WildPin.Slots(0x083C9CB8L, slots))!!
        val hero = PlayerLook.Patch(listOf(PlayerLook.Block(0x20, -1, byteArrayOf(1, 2))), emptyList(), null)

        look.select(hero)
        look.selectPin(pin)
        assertTrue(look.service())
        assertContentEquals(intArrayOf(0x08000020, 0x083C9CB8), core.addresses)
        assertEquals(pin to true, results.last())
        assertFalse(look.service())

        // Its battle started: the look stays, the pin goes.
        assertTrue(look.clearPin(pin))
        assertTrue(look.service())
        assertContentEquals(intArrayOf(0x08000020), core.addresses)
        assertEquals(null to false, results.last())

        // A pin whose bytes don't match: dropped, the look goes in alone.
        core.rejectOver = 1
        look.selectPin(pin)
        assertTrue(look.service())
        assertContentEquals(intArrayOf(0x08000020), core.addresses)
        assertEquals(pin to false, results.last())
        assertNull(look.pin)
    }

    @Test fun aRejectedLookStillLetsThePinGoInAlone() {
        val core = Core()
        val results = mutableListOf<Pair<WildPin.Pin?, Boolean>>()
        val look = GamePlayerLook(core) { pin, installed -> results += pin to installed }
        val slots = ByteArray(48).also { b -> for (i in 0 until 12) { b[i * 4] = 2; b[i * 4 + 1] = 4; b[i * 4 + 2] = 10 } }
        val pin = WildPin.pin(10, 3, 19, 0, WildPin.Slots(0x083C9CB8L, slots))!!
        val hero = PlayerLook.Patch(listOf(PlayerLook.Block(0x20, -1, byteArrayOf(1, 2))), emptyList(), null)

        // The look's bytes don't match this ROM: together refused, the look alone refused, the pin alone goes in.
        core.rejectAddress = 0x08000020
        look.select(hero)
        look.selectPin(pin)
        assertTrue(look.service())
        assertContentEquals(intArrayOf(0x083C9CB8), core.addresses)
        assertEquals(pin to true, results.last())
        assertEquals(pin, look.pin)
    }

    private class Core : PlayerLookCore {
        var reject = false
        /** Reject any call with more blocks than this. */
        var rejectOver = Int.MAX_VALUE
        /** Reject any call holding a block at this address (its bytes don't match). */
        var rejectAddress: Int? = null
        var addresses = intArrayOf()
        var bytes = byteArrayOf()
        var swaps = 0
        var lastPalette: ByteArray? = null

        override fun replaceLookBlocks(addresses: IntArray, lengths: IntArray, crcs: IntArray, data: ByteArray): Int {
            this.addresses = addresses
            bytes = data
            return if (reject || addresses.size > rejectOver || rejectAddress?.let { it in addresses } == true) -1 else addresses.size
        }

        override fun repaletteLook(from: ByteArray, to: ByteArray): Int {
            swaps++
            return 1
        }

        override fun refreshLook(palette: ByteArray?): Boolean {
            lastPalette = palette
            return true
        }
    }
}
