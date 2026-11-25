package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHINY ALERT's reads on the Game Boy Color and DS games: their games' own rules. */
class GbDsShinyTest {
    @Test
    fun `Gen 2 - Defense, Speed and Special 10, Attack with its 2 bit`() {
        val shinyAttacks = (0..15).filter { gen2Shiny(it shl 4 or 10, 0xAA) }
        assertEquals(listOf(2, 3, 6, 7, 10, 11, 14, 15), shinyAttacks)
        assertFalse(gen2Shiny(0xAA, 0xAB))   // Special 11
        assertFalse(gen2Shiny(0xAB, 0xAA))   // Defense 11
        assertFalse(gen2Shiny(0xAA, 0x9A))   // Speed 9
    }

    @Test
    fun `Gen 4 and 5 - the IDs XOR the personality under 8`() {
        assertTrue(gen45Shiny(0x00000000L, 0x00000007L))
        assertFalse(gen45Shiny(0x00000000L, 0x00000008L))
        // trainer 12345, secret 54321: a personality whose halves XOR to theirs, within 7
        val tid = 12345L; val sid = 54321L
        assertTrue(gen45Shiny(((tid xor sid xor 3L) shl 16) or 0L, (sid shl 16) or tid))
        assertFalse(gen45Shiny(((tid xor sid xor 9L) shl 16) or 0L, (sid shl 16) or tid))
    }

    @Test
    fun `the Game Boy and DS fixtures' parties read no shiny`() {
        activeGame = GameKind.GOLD
        assertTrue(readGen2Telemetry(FixtureMemoryReader.load("gold"), GEN2_GOLD).party.none { it.shiny })
    }

    @Test
    fun `Gen 1 - shiny by the DVs Gen 2 would read, in the party and in battle`() {
        val cfg = GEN1_YELLOW
        val pikachu = cfg.internalToDex.indexOf(25)
        fun partyMon(dv0: Int, dv1: Int) = ByteArray(GEN1_PARTY_MON_SIZE).also {
            it[0] = pikachu.toByte(); it[33] = 5; it[27] = dv0.toByte(); it[28] = dv1.toByte()
        }
        assertTrue(decodeGen1PartyMon(partyMon(0x2A, 0xAA), 0, cfg)!!.shiny)
        assertFalse(decodeGen1PartyMon(partyMon(0x1A, 0xAA), 0, cfg)!!.shiny)   // Attack 1
        fun battleMon(dv0: Int, dv1: Int) = ByteArray(GEN1_BATTLE_MON_SIZE).also {
            it[0] = pikachu.toByte(); it[14] = 5; it[12] = dv0.toByte(); it[13] = dv1.toByte()
        }
        assertTrue(decodeGen1BattleMon(battleMon(0xFA, 0xAA), cfg)!!.shiny)
        assertFalse(decodeGen1BattleMon(battleMon(0xFA, 0xAB), cfg)!!.shiny)   // Special 11
        activeGame = GameKind.YELLOW
        assertTrue(readGen1Telemetry(FixtureMemoryReader.load("yellow"), cfg).party.none { it.shiny })
    }
}
