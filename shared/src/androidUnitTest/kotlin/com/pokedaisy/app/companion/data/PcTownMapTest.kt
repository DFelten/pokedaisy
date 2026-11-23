package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A PC fan game's Town Map ([PcTownMap]): its places as the MAP tab's sections, on the game's own tile grid. */
class PcTownMapTest {
    private val points = listOf(
        PcTownMap.Point(4, 3, "Petal Town", null),
        PcTownMap.Point(5, 3, "Petal Town", null),
        PcTownMap.Point(4, 6, "Route 2", null),
        PcTownMap.Point(9, 9, "Mossy Cave", "Moss Tower"),
        PcTownMap.Point(12, 2, "Secret Island", null),
    )

    @After fun reset() {
        PcTownMap.clear()
        activeGame = GameKind.FIRERED
    }

    @Test fun placesAreSections() {
        val m = PcTownMap.build("pc/t1", 16, 8, points)!!
        val petal = m.pick(0, 5, 3)!!.mapsec
        assertEquals("Petal Town", m.sections[petal]?.name)
        // A town's every tile is its place: one cursor frame round both.
        assertEquals(listOf(MapTiles(4, 3, 2, 1)), m.tilesOf(petal))
        // A point of interest is the second line, as FireRed's dungeons.
        val cave = m.pick(0, 9, 9)!!
        assertEquals("Mossy Cave", m.sections[cave.mapsec]?.name)
        assertEquals("Moss Tower", m.sections[cave.dungeon]?.name)
        assertEquals(listOf("Petal Town", "Route 2", "Mossy Cave", "Moss Tower", "Secret Island").sorted(),
            m.places().flatMap { it.second }.map { m.sections.getValue(it).name }.sorted())
    }

    @Test fun scatteredTilesOneByOne() {
        val m = PcTownMap.build("pc/t1", 16, 8, points + PcTownMap.Point(6, 4, "Petal Town", null))!!
        val petal = m.pick(0, 6, 4)!!.mapsec
        assertEquals(setOf(MapTiles(4, 3, 1, 1), MapTiles(5, 3, 1, 1), MapTiles(6, 4, 1, 1)), m.tilesOf(petal).toSet())
    }

    @Test fun theGamesOwnTileRule() {
        // Better Region Map (Infinite Fusion): a tile from tile * 16, its middle 8 on; Essentials' own: centred on tile * 16.
        assertEquals(0, PcTownMap.build("pc/t1", 16, 8, points)!!.originX)
        assertEquals(-8, PcTownMap.build("pc/t1", 16, 0, points)!!.originY)
    }

    @Test fun aSwitchedOffPlaceIsUnnamed() {
        val m = PcTownMap.build("pc/t1", 16, 8, points, hidden = setOf(4))!!
        assertNull(m.pick(0, 12, 2))
        assertTrue(m.places().flatMap { it.second }.none { m.sections[it]?.name == "Secret Island" })
    }

    @Test fun theUnnamedTileIsThePlayersOwn() {
        val m = PcTownMap.build("pc/t1", 16, 8, points)!!
        assertEquals(PcTownMap.PLAYER_ONLY, PcTownMap.sectionAt(m, 7, 7))
        assertEquals("", m.sections[PcTownMap.PLAYER_ONLY]?.name)
        assertTrue(m.tilesOf(PcTownMap.PLAYER_ONLY).isEmpty())
    }

    @Test fun theMapTabReadsItWhileTheGameRuns() {
        val m = PcTownMap.build("pc/t1", 16, 8, points)
        val v = PcTownMap.version
        PcTownMap.set(m)
        assertTrue(PcTownMap.version != v)
        activeGame = GameKind.ESSENTIALS
        assertSame(m, activeRegionMap())
    }

    @Test fun noPlacesNoMap() {
        assertNull(PcTownMap.build("pc/t1", 16, 8, emptyList()))
    }
}
