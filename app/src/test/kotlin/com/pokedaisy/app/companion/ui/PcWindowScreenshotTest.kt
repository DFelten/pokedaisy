package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.pokedaisy.app.PcBattleInput
import com.pokedaisy.app.PcCompanion
import com.pokedaisy.app.PcCompanionSample
import com.pokedaisy.app.PcCompanionWindow
import com.pokedaisy.app.companion.TouchButton
import com.pokedaisy.app.companion.TouchControlsStyle
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.activeGame
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

/**
 * A PC fan game on a device with one screen (PcGameActivity, the RP6's 1920 x 1080 at 369 dpi): the touch controls with
 * the tab that opens the companion, and the companion's window over the game with its CLOSE GAME. The game is a plain
 * 4:3 box here (never a game's art).
 *
 *     ./gradlew :app:recordPaparazziDebug --tests '*PcWindowScreenshotTest'
 */
class PcWindowScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, density = Density.create(369),
        ),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Before fun game() {
        installTestHosts(paparazzi.context)
        activeGame = GameKind.ESSENTIALS
        OptionColors.inGame = true
    }

    @After fun reset() {
        activeGame = GameKind.FIRERED
        OptionColors.inGame = false
        com.pokedaisy.app.companion.data.PcTownMap.clear()
        com.pokedaisy.app.companion.data.PcGuide.clear()
    }

    private val snapshot by lazy {
        PcCompanion(Files.createTempDirectory("pc-game").toFile(), Files.createTempDirectory("pc-files").toFile()).snapshot(PcCompanionSample.JSON)!!
    }

    @Test fun closed() = paparazzi.snapshot { Game { Window(open = false) } }

    @Test fun open() = paparazzi.snapshot { Game { Window(open = true) } }

    @Composable
    private fun Window(open: Boolean) = PcCompanionWindow(
        open, snapshot, CompanionBack(), CompanionKeys(), PcBattleInput { true },
        onOpen = {}, onClose = {}, onCloseGame = {}, compactText = true,
    )

    @Composable
    private fun Game(over: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxHeight().aspectRatio(4f / 3f).align(Alignment.Center).background(Color(0xFF6FA86A)))
            TouchControls(
                style = TouchControlsStyle(), portrait = false, input = com.pokedaisy.app.companion.ui.TouchInput(),
                modifier = Modifier.fillMaxSize(), gameAspect = 4f / 3f, ds = true,
                unavailable = TouchButton.entries.filter { it.extra }.toSet(),
            )
            over()
        }
    }
}
