package com.pokedaisy.app

import com.pokedaisy.app.companion.data.GENDER_SYMBOL_FEMALE
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_NONE
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.POCKET_KEY_ITEMS
import com.pokedaisy.app.companion.data.POCKET_MEDICINE
import com.pokedaisy.app.companion.data.STAT_SPEED
import com.pokedaisy.app.companion.data.STAT_SPATK
import com.pokedaisy.app.companion.data.activeGame
import com.pokedaisy.app.companion.data.typeMultiplierPct
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A PC fan game's data into the companion's snapshot ([PcCompanion]): names, the game's own types and chart, stats in
 * the companion's order, Essentials' pockets. */
class PcCompanionTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun game() { activeGame = GameKind.ESSENTIALS }
    @After fun reset() { activeGame = GameKind.FIRERED }

    private fun view() = PcCompanion(tmp.newFolder("game"), tmp.newFolder("files")).snapshot(PcCompanionSample.JSON)!!

    @Test fun partyAndPlace() {
        val v = view()
        assertEquals(GameKind.ESSENTIALS, v.game)
        assertEquals(4, v.party.size)
        assertEquals("Route 2", v.location.mapSecName)
        assertEquals(3820L, v.money)
        val pip = v.party[0]
        assertEquals("EMBERPUP", pip.name)
        assertEquals("Pip", pip.shownName)
        assertEquals(listOf("Fire"), pip.types)
        assertEquals("Ember Charm", pip.heldItem)
        assertEquals("Blaze", pip.abilityName)
        assertEquals(listOf("Ember", "Tackle", "Growl"), pip.moves.map { it.name })
        assertEquals("Fire", pip.moves[0].type)
        assertEquals(25, pip.moves[0].maxPp)
    }

    @Test fun statusGenderAndEgg() {
        val v = view()
        assertEquals("PSN", v.party[1].status)
        assertTrue(v.party[1].shiny)
        assertEquals(GENDER_SYMBOL_FEMALE, v.party[1].genderSymbol)
        assertEquals("FNT", v.party[2].status)
        assertEquals(GENDER_SYMBOL_NONE, v.party[2].genderSymbol)
        assertTrue(v.party[3].isEgg)
        assertTrue(v.party[3].moves.isEmpty())
        assertNull(v.party[3].stats)
    }

    @Test fun statsInTheCompanionsOrder() {
        // The game sends HP, Atk, Def, SpA, SpD, Spe; the companion keeps HP, Atk, Def, Spe, SpA, SpD.
        val s = view().party[0].stats!!
        assertEquals(30, s.stats[STAT_SPEED])
        assertEquals(22, s.stats[STAT_SPATK])
        assertEquals(30, s.ivs[STAT_SPEED])
        assertEquals(30, s.evs[STAT_SPEED])
        assertEquals(3, s.nature)
    }

    @Test fun theGamesOwnChart() {
        val v = view()
        // Water: weak to Grass and Electric, resists Fire.
        val water = v.party[1]
        assertEquals(setOf("Grass", "Electric"), water.weaknesses.map { it.type }.toSet())
        assertTrue(water.resistances.any { it.type == "Fire" })
        // Electric / Flying: Electric 1x (resisted by Electric, Flying is weak to it), Rock 2x.
        assertEquals(100, typeMultiplierPct(13, 13, 2))
        assertTrue(v.party[2].weaknesses.any { it.type == "Rock" })
        // A combined type of the game's own is only ever a defender: never listed as an attacker.
        assertTrue(v.party.flatMap { it.weaknesses + it.resistances + it.immunities }.none { "/" in it.type })
    }

    @Test fun pocketsAndNames() {
        val c = PcCompanion(tmp.newFolder("game2"), tmp.newFolder("files2"))
        val v = c.snapshot(PcCompanionSample.JSON)!!
        assertEquals(6, v.items.size)
        val potion = v.items.first { it.name == "Potion" }
        assertEquals(POCKET_MEDICINE, potion.pocket)
        assertEquals("Restores 20 HP.", potion.description)
        assertEquals(POCKET_KEY_ITEMS, v.items.first { it.name == "Bicycle" }.pocket)
        assertEquals("Potion", c.itemNames[potion.itemId])
        assertEquals(v.items.size, v.items.map { it.itemId }.toSet().size)
    }

    @Test fun battle() {
        val v = PcCompanion(tmp.newFolder("gb"), tmp.newFolder("fb")).snapshot(PcCompanionSample.BATTLE_JSON)!!
        assertTrue(v.inBattle)
        assertEquals(true, v.isTrainerBattle)
        assertEquals(listOf("EMBERPUP"), v.battlePlayer.map { it.name })
        assertEquals(listOf("BUBBLEFIN"), v.battleOpponent.map { it.name })
        val pip = v.battlePlayer[0]
        assertEquals(40, pip.hp)
        assertEquals("BRN", pip.status)
        assertEquals(listOf(32, 25, 30, 22, 24), pip.battleStats)
        assertEquals(1, pip.statStages!![0])
        // The game's own chart: Fire into Water is resisted, a status move has no verdict.
        assertEquals(50, pip.moves.first { it.name == "Ember" }.vs.single().pct)
        assertTrue(pip.moves.first { it.name == "Growl" }.vs.isEmpty())
        // ...and the foe's Water move is super effective on Pip.
        assertEquals(200, v.battleOpponent[0].moves.first { it.name == "Bubble" }.vs.single().pct)
        assertEquals(listOf("BUBBLEFIN", "THORNLET"), v.enemyParty.map { it.name })
        assertEquals(0, v.enemyActive)
        assertEquals(listOf(0), v.playerBattlers)
        // The game's command window waits: the companion's buttons are live.
        assertEquals(com.pokedaisy.app.companion.data.BATTLE_INPUT_ACTION_SELECT, v.battleInputState)
    }

    @Test fun theWaitingMenu() {
        fun state(menu: String?) = PcCompanion(tmp.newFolder(), tmp.newFolder()).snapshot(
            org.json.JSONObject(PcCompanionSample.BATTLE_JSON).apply { getJSONObject("battle").put("menu", menu ?: org.json.JSONObject.NULL) }.toString(),
        )!!.battleInputState
        assertEquals(com.pokedaisy.app.companion.data.BATTLE_INPUT_MOVE_SELECT, state("fight"))
        assertEquals(com.pokedaisy.app.companion.data.BATTLE_INPUT_PARTY_OPEN, state("party"))
        assertEquals(com.pokedaisy.app.companion.data.BATTLE_INPUT_BUSY, state(null))
        assertEquals(com.pokedaisy.app.companion.data.BATTLE_INPUT_NONE, view().battleInputState)
    }

    @Test fun partySlotsAreTheSwitchKey() {
        assertEquals(listOf(1L, 2L, 3L, 4L), view().party.map { it.personality })
    }

    @Test fun picksAreTheGamesRequests() {
        val sent = mutableListOf<String>()
        val input = PcBattleInput { sent += it; true }
        input.selectMove(2)
        input.selectAction(3)
        input.back()
        input.switchTo(3)
        input.switchTo(0)   // unknown: nothing
        input.selectMove(7) // no such move: nothing
        assertEquals(listOf(
            "PokeDaisyCompanion.request(:fight, 2)", "PokeDaisyCompanion.request(:action, 3)",
            "PokeDaisyCompanion.request(:back)", "PokeDaisyCompanion.request(:switch, 2)",
        ), sent)
    }

    @Test fun noBattleNoBattleFields() {
        val v = view()
        assertTrue(!v.inBattle)
        assertTrue(v.battlePlayer.isEmpty() && v.enemyParty.isEmpty())
    }

    @Test fun anErrorSnapshotIsNone() {
        assertNull(PcCompanion(tmp.newFolder("g"), tmp.newFolder("f")).snapshot("""{"v":3,"error":"NoMethodError"}"""))
    }
}
