package com.pokedaisy.app.achievements

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What makes RetroAchievements idle instead of running a frame ([achievementsIdle]). */
class AchievementsIdleTest {

    @Test
    fun idleWhileAWildPinIsOn() {
        assertTrue(achievementsIdle(cheats = false, shinyOdds = false, coopBattle = false, wildPin = true))
        assertFalse(achievementsIdle(cheats = false, shinyOdds = false, coopBattle = false, wildPin = false))
        assertTrue(achievementsIdle(cheats = true, shinyOdds = false, coopBattle = false, wildPin = false))
    }
}
