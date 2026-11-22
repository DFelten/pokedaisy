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
import okio.Path.Companion.toOkioPath

/**
 * A PC fan game's companion (PcGameActivity's second screen): PARTY, a summary, the bag and the MAP, from a sample snapshot of
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
    private var artBefore: okio.Path? = null

    @Before fun game() {
        installTestHosts(paparazzi.context)
        activeGame = GameKind.ESSENTIALS
        OptionColors.inGame = true
        val game = Files.createTempDirectory("pc-game").toFile()
        drawTownMap(game)
        val files = Files.createTempDirectory("pc-files").toFile()
        // The map's picture as the app keeps it: rom-art under the files folder.
        artBefore = com.pokedaisy.app.companion.data.RomArt.dirOverride
        com.pokedaisy.app.companion.data.RomArt.dirOverride = java.io.File(files, "rom-art").toOkioPath()
        companion = PcCompanion(game, files)
        OwnItemNames.lookup = { companion.itemNames[it] }
    }

    @After fun reset() {
        activeGame = GameKind.FIRERED
        OptionColors.inGame = false
        OwnItemNames.lookup = null
        com.pokedaisy.app.companion.data.PcTownMap.clear()
        com.pokedaisy.app.companion.data.RomArt.dirOverride = artBefore
    }

    /** A stand-in for the game's Town Map picture and mark (drawn here: never a game's art), on the sample's places: a
     * 16-pixel tile from tile * 16 (Better Region Map's rule). */
    private fun drawTownMap(game: java.io.File) {
        val dir = java.io.File(game, "Graphics/Pictures").apply { mkdirs() }
        val map = android.graphics.Bitmap.createBitmap(16 * 15, 16 * 12, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(map)
        val p = android.graphics.Paint()
        fun tiles(x0: Int, y0: Int, x1: Int, y1: Int, color: Long, inset: Int = 0) {
            p.color = color.toInt()
            c.drawRect((x0 * 16 + inset).toFloat(), (y0 * 16 + inset).toFloat(), ((x1 + 1) * 16 - inset).toFloat(), ((y1 + 1) * 16 - inset).toFloat(), p)
        }
        c.drawColor(0xFF4FA3D8.toInt())
        tiles(2, 1, 10, 10, 0xFF8FD18A)
        tiles(11, 1, 13, 3, 0xFF8FD18A)
        tiles(4, 4, 4, 9, 0xFFF4F0E0, 5)
        tiles(4, 9, 9, 9, 0xFFF4F0E0, 5)
        tiles(4, 3, 5, 3, 0xFFD8434B, 2)
        tiles(4, 9, 4, 9, 0xFFD8434B, 2)
        tiles(9, 9, 9, 9, 0xFF8A8A8A, 2)
        map.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, java.io.File(dir, "town_map.png").outputStream())
        val head = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(head).apply {
            p.color = 0xFF202020.toInt(); drawCircle(8f, 8f, 7f, p)
            p.color = 0xFFFFD84A.toInt(); drawCircle(8f, 8f, 5f, p)
        }
        head.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, java.io.File(dir, "town_head.png").outputStream())
    }

    private fun shot(tab: String, mon: Int? = null, json: String = PcCompanionSample.JSON, controls: Boolean = false) {
        val view = companion.snapshot(json)!!
        val input = if (controls) com.pokedaisy.app.PcBattleInput { true } else null
        paparazzi.snapshot { CompanionScreen(view, initialTab = tab, initialMonIndex = mon, battleInput = input) }
    }

    @Test fun party() = shot("PARTY")
    @Test fun summary() = shot("PARTY", mon = 0)
    @Test fun summaryWater() = shot("PARTY", mon = 1)
    @Test fun bag() = shot("ITEMS")
    @Test fun map() = shot("MAP")
    @Test fun battle() = shot("BATTLE", json = PcCompanionSample.BATTLE_JSON)
    @Test fun battleButtons() = shot("BATTLE", json = PcCompanionSample.BATTLE_JSON, controls = true)

    /** The summary's STATS page: IVs and EVs as the game sends them, its nature. */
    @Test fun stats() {
        val party = companion.snapshot(PcCompanionSample.JSON)!!.party
        paparazzi.snapshot { MonDetailScreen(party, 0, onIndexChange = {}, onBack = {}, initialShowStats = true) }
    }
}
