package com.pokedaisy.app.companion

import com.pokedaisy.app.Prefs
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.LocationView
import com.pokedaisy.app.companion.data.MonView
import com.pokedaisy.app.companion.data.SnapshotView
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SPOILERS ([Spoilers]): the choice per ROM, what counts as seen, moves still to come and when the question comes. */
class SpoilersTest {
    private fun mon(species: Int, level: Int = 5, egg: Boolean = false) = MonView(
        species = species, name = "M$species", level = level, hp = 10, maxHp = 10, status = "", types = emptyList(),
        iconAsset = null, personality = species.toLong(), isEgg = egg,
    )

    private fun field(party: List<MonView>, foes: List<MonView> = emptyList()) = SnapshotView(
        connected = true, game = GameKind.FIRERED, party = party, battleOpponent = foes, inBattle = foes.isNotEmpty(),
        location = LocationView("ROUTE 1", null, 0, 0, 0, 0),
    )

    private lateinit var prefs: Prefs
    private var game = 0

    @BeforeTest fun on() {
        FlaggedFeatures.spoilerFree = true
        prefs = Prefs(PrefsCompanionSettingsTest.Store())
        game = Spoilers.start(prefs, "firered")
    }

    @AfterTest fun off() {
        Spoilers.close(game)
        FlaggedFeatures.spoilerFree = false
    }

    @Test fun nothingIsHiddenUntilHideIsPickedAndTheFlagIsOn() {
        assertEquals(Spoilers.UNANSWERED, Spoilers.choice)
        assertFalse(Spoilers.hiding)
        // No Pokédex: every screen's old rule, all seen.
        assertTrue(Spoilers.seen(150, null))
        Spoilers.set(true)
        assertTrue(Spoilers.hiding)
        assertFalse(Spoilers.seen(150, null))
        FlaggedFeatures.spoilerFree = false
        assertFalse(Spoilers.hiding)
        assertTrue(Spoilers.seen(150, null))
    }

    @Test fun theChoiceIsKeptPerRom() {
        Spoilers.set(true)
        assertEquals(Spoilers.HIDE, prefs.spoilersFor("firered"))
        Spoilers.close(game)
        game = Spoilers.start(prefs, "emerald")
        assertFalse(Spoilers.hiding)
        Spoilers.close(game)
        game = Spoilers.start(prefs, "firered")
        assertTrue(Spoilers.hiding)
    }

    @Test fun whatTheCompanionReadAndWhatWasTappedOpenCountAsSeen() {
        Spoilers.set(true)
        Spoilers.onSample(game, field(listOf(mon(4), mon(0, egg = true)), foes = listOf(mon(16))))
        assertTrue(Spoilers.seen(4, null))
        assertTrue(Spoilers.seen(16, null))
        assertFalse(Spoilers.seen(25, null))
        Spoilers.reveal(Spoilers.monKey(25))
        assertTrue(Spoilers.seen(25, null))
        // Kept for the next start.
        Spoilers.close(game)
        game = Spoilers.start(prefs, "firered")
        assertTrue(Spoilers.seen(16, null))
        assertTrue(Spoilers.seen(25, null))
        assertFalse(Spoilers.seen(150, null))
    }

    @Test fun movesPastTheLevelYouHaveItAtAreStillToCome() {
        Spoilers.set(true)
        Spoilers.onSample(game, field(listOf(mon(4, level = 12))))
        assertEquals(12, Spoilers.knownLevel(4))
        assertFalse(Spoilers.moveAhead(4, 1, 10, Spoilers.knownLevel(4)))
        assertFalse(Spoilers.moveAhead(4, 12, 11, Spoilers.knownLevel(4)))
        assertTrue(Spoilers.moveAhead(4, 13, 12, Spoilers.knownLevel(4)))
        // Not had at all: only its first moves are known; a move on evolving too, once you have one.
        assertTrue(Spoilers.moveAhead(5, 0, 13, Spoilers.knownLevel(5)))
        assertFalse(Spoilers.moveAhead(4, 0, 13, Spoilers.knownLevel(4)))
        Spoilers.reveal(Spoilers.moveKey(4, 13, 12))
        assertFalse(Spoilers.moveAhead(4, 13, 12, 12))
        Spoilers.set(false)
        assertFalse(Spoilers.moveAhead(4, 40, 14, 12))
    }

    @Test fun anEarlySaveIsAskedAMomentIntoTheGame() {
        repeat(2) { Spoilers.onSample(game, field(listOf(mon(4)))) }
        assertFalse(Spoilers.asking)
        Spoilers.onSample(game, field(listOf(mon(4))))
        assertTrue(Spoilers.asking)
        assertFalse(Spoilers.askingOnCompanion)
        // The bar ran out: nothing kept, not asked again this run.
        Spoilers.answer(null)
        assertFalse(Spoilers.asking)
        assertEquals(Spoilers.UNANSWERED, prefs.spoilersFor("firered"))
        Spoilers.pageOpened("DEX")
        assertFalse(Spoilers.asking)
    }

    @Test fun aSaveFurtherOnIsAskedOverTheFirstDexOrGuide() {
        val team = listOf(mon(4), mon(16), mon(19))
        repeat(5) { Spoilers.onSample(game, field(team)) }
        assertFalse(Spoilers.asking)
        Spoilers.pageOpened("BATTLE")
        assertFalse(Spoilers.asking)
        Spoilers.pageOpened("GUIDE")
        assertTrue(Spoilers.asking)
        assertTrue(Spoilers.askingOnCompanion)
        Spoilers.answer(true)
        assertFalse(Spoilers.asking)
        assertEquals(Spoilers.HIDE, prefs.spoilersFor("firered"))
    }

    @Test fun anAnsweredGameIsNeverAsked() {
        prefs.setSpoilersFor("firered", Spoilers.SHOW)
        Spoilers.close(game)
        game = Spoilers.start(prefs, "firered")
        repeat(5) { Spoilers.onSample(game, field(listOf(mon(4)))) }
        Spoilers.pageOpened("DEX")
        assertFalse(Spoilers.asking)
    }
}
