package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.pokedaisy.app.companion.data.DecompIconSource
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV1
import com.pokedaisy.app.companion.data.RomArt
import com.pokedaisy.app.companion.data.RomFileReader
import com.pokedaisy.app.companion.data.activeGame
import okio.Path.Companion.toOkioPath
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The companion's window ([SidePanelHost]) on a Retroid Pocket Classic-sized screen (1240x1080 sideways, density 2.0):
 * nearly square, so the window leaves no room beside it and its X sits in its top bar.
 *
 *     ./gradlew :app:recordPaparazziDebug --tests '*WindowRpcScreenshotTest'
 */
class WindowRpcScreenshotTest {
    @Before fun hosts() = installTestHosts(paparazzi.context)
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = W, screenHeight = H, orientation = ScreenOrientation.LANDSCAPE, density = Density.XHIGH,
        ),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Before
    fun game() {
        activeGame = GameKind.FIRERED
        OptionColors.inGame = true
        val art = java.io.File("build/rom-art-paparazzi").also { RomArt.dirOverride = it.toOkioPath() }
        java.io.File(RomFileReader.FIRERED_REV1_PATH).takeIf { it.isFile }?.let { RomArt.extractTo(it.readBytes(), art.toOkioPath()) }
        RomFileReader.load(RomFileReader.FIRERED_REV1_PATH)?.let { rom ->
            DecompIconSource.reader = rom
            DecompIconSource.tables = NATIVE_FIRERED_REV1.iconTables
            SampleCompanion.snapshot.party.forEach { DecompIconSource.get(it.species) }
        }
    }

    @Test fun party() = paparazzi.snapshot { Window("PARTY") }
    @Test fun settings() = paparazzi.snapshot { Window("SETTINGS") }
    @Test fun map() = paparazzi.snapshot { Window("MAP") }
    @Test fun bag() = paparazzi.snapshot { Window("ITEMS") }
    @Test fun dex() = paparazzi.snapshot { Window("DEX") }
    @Test fun guide() = paparazzi.snapshot { Window("GUIDE") }
    @Test fun partyStatusBar() = paparazzi.snapshot { Window("PARTY", statusBar = true) }
    /** A summary: its buttons that don't fit go under MORE. */
    @Test fun summary() = paparazzi.snapshot { Window("PARTY", mon = 0) }

    /** Seven summary buttons in a row too narrow for them: the ones that fit, then MORE. */
    @Test fun summaryButtonsOverflow() = paparazzi.snapshot {
        Box(Modifier.fillMaxSize().background(Color(0xFF303030))) {
            val m = rememberGbaTextMetrics()
            val a = listOf("STATS", "LEARN", "EVOLVE", "SWITCH", "CENTER", "DEPOSIT", "WITHDRAW").map { l ->
                SummaryAction(l, Color(0xFF7060C0), Color(0xFF403080)) {}
            }
            Box(Modifier.offset(x = px(40), y = px(40)).size(px(700), px(120))) {
                SummaryButtons(a, 52.dp, m, Modifier.fillMaxSize())
            }
        }
    }

    @Composable
    private fun Window(tab: String, statusBar: Boolean = false, mon: Int? = null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val gameW = W
            FakeGame(Modifier.offset(y = px((H - W * 2 / 3) / 2)).size(px(gameW), px(W * 2 / 3)))
            // The bar shows when it is passed (CompanionScreen draws a non-null statusBar).
            SidePanelHost(window = true, onClose = {}) {
                CompanionScreen(
                    SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(), null, initialTab = tab, initialMonIndex = mon,
                    statusBar = if (statusBar) ({ GameStatusBar("Pokémon FireRed", "PALLET TOWN", 3175, "12:34") }) else null,
                )
            }
        }
    }

    @Composable
    private fun px(v: Int): Dp = with(LocalDensity.current) { v.toDp() }

    companion object {
        const val W = 1240
        const val H = 1080
    }
}
