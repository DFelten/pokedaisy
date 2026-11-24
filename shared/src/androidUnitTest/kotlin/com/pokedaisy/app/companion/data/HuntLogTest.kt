package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHINY HUNT's log ([HuntLog]), its counting ([HuntCounter]) and its odds ([HuntOdds]). */
class HuntLogTest {
    private fun mon(species: Int, personality: Long, shiny: Boolean = false, egg: Boolean = false) = MonView(
        species = species, name = "M$species", level = 5, hp = 10, maxHp = 10, status = "", types = emptyList(),
        iconAsset = null, personality = personality, shiny = shiny, isEgg = egg,
    )

    private val party = listOf(mon(1, 11), mon(4, 12))

    @Test
    fun aLogRoundTripsAndKeepsOneActive() {
        val log = HuntLog()
            .with(Hunt(id = 1, method = HuntMethod.RESETS, target = 150, targetName = "MEW\tTWO", count = 4321, odds = 4096,
                playMillis = 90_000, phases = listOf(HuntShiny(16, "PIDGEY", 1200, 5)), status = HuntStatus.ACTIVE))
            .with(Hunt(id = 2, count = 7))
        // The newer one is active, the first went ON HOLD.
        assertEquals(2L, log.active?.id)
        assertEquals(HuntStatus.ON_HOLD, log.hunts.first { it.id == 1L }.status)
        val back = HuntLog.decode(log.encode())
        assertEquals(log.hunts.map { it.copy(targetName = it.targetName.replace('\t', ' ')) }, back.hunts)
        assertTrue(HuntLog.decode("junk\nhunt\tx").hunts.isEmpty())
    }

    @Test
    fun encountersCountEachWildPokemonOnce() {
        val c = HuntCounter()
        var total = 0
        repeat(3) {
            listOf(emptyList(), listOf(mon(16, 100 + it.toLong())), listOf(mon(16, 100 + it.toLong()))).forEach { foes ->
                total += c.sample(HuntMethod.ENCOUNTERS, inBattle = true, wild = true, foes = foes, partyNow = party).add
            }
            c.sample(HuntMethod.ENCOUNTERS, inBattle = false, wild = false, foes = emptyList(), partyNow = party)
        }
        // A double battle: two.
        total += c.sample(HuntMethod.ENCOUNTERS, true, true, listOf(mon(16, 7), mon(19, 8)), party).add
        // A trainer's: none.
        c.sample(HuntMethod.ENCOUNTERS, false, false, emptyList(), party)
        total += c.sample(HuntMethod.ENCOUNTERS, true, false, listOf(mon(16, 9)), party).add
        assertEquals(5, total)
    }

    @Test
    fun aShinyFoeIsReportedOnceABattle() {
        val c = HuntCounter()
        val foes = listOf(mon(16, 1, shiny = true))
        assertEquals(16, c.sample(HuntMethod.ENCOUNTERS, true, true, foes, party).shiny?.species)
        assertNull(c.sample(HuntMethod.ENCOUNTERS, true, true, foes, party).shiny)
    }

    @Test
    fun resetsCountTheNextPokemonReadAndNotARepeat() {
        val c = HuntCounter()
        // A static Pokémon: load, walk in, the battle reads it.
        c.reset()
        assertEquals(1, c.sample(HuntMethod.RESETS, true, true, listOf(mon(150, 500)), party).add)
        // The same battle's later samples: nothing.
        assertEquals(0, c.sample(HuntMethod.RESETS, true, true, listOf(mon(150, 500)), party).add)
        // Loaded again and the same one came: a repeat, not counted.
        c.reset()
        val again = c.sample(HuntMethod.RESETS, true, true, listOf(mon(150, 500)), party)
        assertEquals(0, again.add)
        assertTrue(again.repeat)
        // Loaded again, another one.
        c.reset()
        assertEquals(1, c.sample(HuntMethod.RESETS, true, true, listOf(mon(150, 501)), party).add)
        // No load in between (a second battle): not a try.
        c.sample(HuntMethod.RESETS, false, false, emptyList(), party)
        assertEquals(0, c.sample(HuntMethod.RESETS, true, true, listOf(mon(150, 502)), party).add)
    }

    @Test
    fun aGiftAfterAResetIsATryAndItsShininessShows() {
        val c = HuntCounter()
        c.reset()
        c.sample(HuntMethod.RESETS, false, false, emptyList(), party)
        val gift = c.sample(HuntMethod.RESETS, false, false, emptyList(), party + mon(1, 900, shiny = true))
        assertEquals(1, gift.add)
        assertEquals(1, gift.shiny?.species)
    }

    @Test
    fun aJustCaughtOrWithdrawnPokemonIsNotAGift() {
        val c = HuntCounter()
        c.sample(HuntMethod.ENCOUNTERS, false, false, emptyList(), party)
        // Taken out of the PC on an ENCOUNTERS hunt: no shiny reported.
        assertNull(c.sample(HuntMethod.ENCOUNTERS, false, false, emptyList(), party + mon(25, 77, shiny = true)).shiny)
        // RESETS: one that joins right after a battle was caught in it.
        val r = HuntCounter()
        r.sample(HuntMethod.RESETS, false, false, emptyList(), party)
        r.sample(HuntMethod.RESETS, true, true, listOf(mon(25, 78, shiny = true)), party)
        assertNull(r.sample(HuntMethod.RESETS, false, false, emptyList(), party + mon(25, 78, shiny = true)).shiny)
    }

    @Test
    fun eggsCountHatchesAndAShinyHatch() {
        val c = HuntCounter()
        val eggs = party + mon(0, 300, egg = true) + mon(0, 301, egg = true)
        c.sample(HuntMethod.EGGS, false, false, emptyList(), eggs)
        val one = c.sample(HuntMethod.EGGS, false, false, emptyList(), party + mon(25, 300) + mon(0, 301, egg = true))
        assertEquals(1, one.add)
        assertNull(one.shiny)
        val two = c.sample(HuntMethod.EGGS, false, false, emptyList(), party + mon(25, 300) + mon(25, 301, shiny = true))
        assertEquals(1, two.add)
        assertEquals(25, two.shiny?.species)
    }

    @Test
    fun byHandCountsNothingItself() {
        val c = HuntCounter()
        assertEquals(0, c.sample(HuntMethod.BY_HAND, true, true, listOf(mon(16, 1)), party).add)
        assertFalse(c.sample(HuntMethod.BY_HAND, true, true, listOf(mon(16, 1)), party).repeat)
    }

    @Test
    fun oddsMilestones() {
        assertEquals(5678, HuntOdds.countFor(0.5, 8192))
        assertEquals(18862, HuntOdds.countFor(0.9, 8192))
        assertEquals(0.5, HuntOdds.chanceBy(5678, 8192), 0.001)
        assertEquals(8192, HuntOdds.forGame(null))
        assertEquals(512, HuntOdds.forGame(512))
    }
}

/** SHINY HUNT's target picture: the front picture in gMonShinyPaletteTable's colours, read from retail FireRed rev 1. */
class HuntSpriteTest {
    @Test
    fun aFireRedTargetIsDrawnInItsShinyColours() {
        val rom = RomFileReader.load(RomFileReader.FIRERED_REV1_PATH) ?: return
        val before = PokedexSource.reader
        PokedexSource.reader = rom
        try {
            val t = POKEDEX_FIRERED_REV1
            // The table sits right after gMonPaletteTable's 440 entries (0xDC0 on).
            assertEquals(t.palettes + 0xDC0, findShinyPalettes(t.palettes) { a, n -> rom.readCoreMemory(a, n) })
            for (species in listOf(4, 16, 25, 150)) {
                val plain = PokedexSource.frontSprite(t, species)!!
                val shiny = PokedexSource.huntSprite(t, species)!!
                // The same picture, other colours.
                assertEquals(plain.argb.map { it ushr 24 }, shiny.argb.map { it ushr 24 })
                assertFalse("$species", plain.argb.contentEquals(shiny.argb))
            }
        } finally { PokedexSource.reader = before }
    }
}
