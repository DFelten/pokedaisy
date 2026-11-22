package com.pokedaisy.app

import android.view.KeyEvent
import com.pokedaisy.app.Hotkeys.Action
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A controller with no L3 / R3 (the Retroid Pocket Classic): QUICK MENU on Y, COMPANION on X, which a DS game keeps. */
class HotkeyNoStickClicksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val x = KeyEvent.KEYCODE_BUTTON_X
    private val y = KeyEvent.KEYCODE_BUTTON_Y

    @After fun reset() { Hotkeys.noStickClicks = false; Hotkeys.dsOwnKeys = { emptySet() } }

    @Test
    fun aNewFilePutsTheQuickMenuOnYAndTheCompanionOnX() {
        Hotkeys.noStickClicks = true
        val dir = tmp.newFolder().toOkioPath()
        val raw = Hotkeys.load(dir).rawBindings
        assertEquals(listOf("BUTTON_Y"), raw[Action.QUICK_MENU])
        assertEquals(listOf("BUTTON_X"), raw[Action.COMPANION])
        val gba = GbaControls.rawBindings(dir)
        assertTrue(gba[GbaControls.Btn.START_2].orEmpty().isEmpty())
        assertTrue(gba[GbaControls.Btn.SELECT_2].orEmpty().isEmpty())
        // The DS games keep X / Y.
        assertEquals(listOf("BUTTON_X"), gba[GbaControls.Btn.X])
        assertEquals(listOf("BUTTON_Y"), gba[GbaControls.Btn.Y])
    }

    @Test
    fun elsewhereTheyStayL3AndUnbound() {
        val dir = tmp.newFolder().toOkioPath()
        val raw = Hotkeys.load(dir).rawBindings
        assertEquals(listOf("BUTTON_THUMBL"), raw[Action.QUICK_MENU])
        assertTrue(raw[Action.COMPANION].orEmpty().isEmpty())
        assertEquals(listOf("BUTTON_X"), GbaControls.rawBindings(dir)[GbaControls.Btn.START_2])
    }

    @Test
    fun anOldFileStillOnL3MovesButAPlayersOwnPickStays() {
        val dir = tmp.newFolder().toOkioPath()
        Hotkeys.load(dir)   // written with L3
        GbaControls.rawBindings(dir)
        Hotkeys.noStickClicks = true
        assertEquals(listOf("BUTTON_Y"), Hotkeys.load(dir).rawBindings[Action.QUICK_MENU])
        assertTrue(GbaControls.rawBindings(dir)[GbaControls.Btn.START_2].orEmpty().isEmpty())

        val mine = tmp.newFolder().toOkioPath()
        Hotkeys.setBindingNamed(mine, Action.QUICK_MENU, listOf("BUTTON_SELECT"))
        Hotkeys.clearBinding(mine, Action.COMPANION)
        assertEquals(listOf("BUTTON_SELECT"), Hotkeys.load(mine).rawBindings[Action.QUICK_MENU])
        assertTrue(Hotkeys.load(mine).rawBindings[Action.COMPANION].orEmpty().isEmpty())
    }

    @Test
    fun aDsGameKeepsItsXAndY() {
        Hotkeys.noStickClicks = true
        val hotkeys = Hotkeys.load(tmp.newFolder().toOkioPath())
        assertEquals(listOf(Hotkeys.Event(Action.QUICK_MENU, true)), hotkeys.onKey(y, true))
        assertTrue(hotkeys.consumes(y))
        hotkeys.onKey(y, false)
        Hotkeys.dsOwnKeys = { setOf(x, y) }
        assertTrue(hotkeys.onKey(y, true).isEmpty())
        assertFalse(hotkeys.consumes(y))
        hotkeys.onKey(y, false)
        assertTrue(hotkeys.onKey(x, true).isEmpty())
    }
}
