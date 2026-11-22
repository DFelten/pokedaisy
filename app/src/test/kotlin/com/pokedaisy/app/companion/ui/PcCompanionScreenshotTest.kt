package com.pokedaisy.app.companion.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.pokedaisy.app.PcCompanion
import com.pokedaisy.app.PcCompanionSample
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.activeGame
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

/**
 * A PC fan game's companion (PcGameActivity's second screen): PARTY, a summary and the bag, from a sample snapshot of
 * our preload's ([PcCompanionSample], no icons), at the Thor bottom screen's 1240 x 1080. A look, not a gate:
 *
 *     ./gradlew :app:recordPaparazziDebug --tests '*PcCompanionScreenshotTest'
 */
class PcCompanionScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1240, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE),
        theme = "android:Theme.Material.NoActionBar",
    )

    private lateinit var companion: PcCompanion

    @Before fun game() {
        activeGame = GameKind.ESSENTIALS
        OptionColors.inGame = true
        companion = PcCompanion(Files.createTempDirectory("pc-game").toFile(), Files.createTempDirectory("pc-files").toFile())
        OwnItemNames.lookup = { companion.itemNames[it] }
    }

    @After fun reset() {
        activeGame = GameKind.FIRERED
        OptionColors.inGame = false
        OwnItemNames.lookup = null
    }

    private fun shot(tab: String, mon: Int? = null) {
        val view = companion.snapshot(PcCompanionSample.JSON)!!
        paparazzi.snapshot { CompanionScreen(view, initialTab = tab, initialMonIndex = mon) }
    }

    @Test fun party() = shot("PARTY")
    @Test fun summary() = shot("PARTY", mon = 0)
    @Test fun summaryWater() = shot("PARTY", mon = 1)
    @Test fun bag() = shot("ITEMS")

    /** The summary's STATS page: IVs and EVs as the game sends them, its nature. */
    @Test fun stats() {
        val party = companion.snapshot(PcCompanionSample.JSON)!!.party
        paparazzi.snapshot { MonDetailScreen(party, 0, onIndexChange = {}, onBack = {}, initialShowStats = true) }
    }
}
