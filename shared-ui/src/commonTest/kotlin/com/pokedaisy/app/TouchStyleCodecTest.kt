package com.pokedaisy.app

import com.pokedaisy.app.companion.*
import kotlin.test.*

class TouchStyleCodecTest {
    private val saved = mutableMapOf<String, Any?>()
    private fun read() = TouchStyleCodec.read(
        text = { saved[it] as? String },
        int = { k, default -> saved[k] as? Int ?: default },
        bool = { k, default -> saved[k] as? Boolean ?: default })
    private fun write(style: TouchControlsStyle) = TouchStyleCodec.write(style,
        text = { k, v -> saved[k] = v }, int = { k, v -> saved[k] = v }, bool = { k, v -> saved[k] = v })

    @Test fun everyOptionAndBothOrientationsSurviveReopening() {
        assertEquals(TouchControlsStyle(), read())
        val portrait = TouchLayout(mapOf(TouchButton.A to TouchPlace(
            TouchPlace.Anchor.END, -31f, TouchPlace.Anchor.CENTER, 18f, 1.25f, false)))
        val landscape = TouchLayout(mapOf(TouchButton.SELECT to TouchPlace(
            TouchPlace.Anchor.START, 12f, TouchPlace.Anchor.END, -20f, 0.75f, true)))
        val style = TouchControlsStyle(show = TouchShow.NEVER, preset = TouchPreset.MIRRORED,
            size = TouchSize.entries.last(), look = TouchLook.entries.last(), colors = TouchColors.entries.last(),
            skinGba = "gba-skin", skinGb = "gb-skin", opacity = 75, extras = setOf(TouchButton.TURBO_B),
            xyButtons = false, position = TouchPosition.BOTTOM, eightWay = false, fadeIdle = true,
            vibrate = true, vibrationStrength = 3, useCustom = true, portrait = portrait, landscape = landscape)
        saved["unrelated"] = "keep"
        write(style)
        assertEquals(style, read())
        assertEquals("keep", saved["unrelated"])
        write(style.copy(skinGba = null, extras = emptySet()))
        assertNull(read().skinGba); assertTrue(read().extras.isEmpty())
    }

    @Test fun oldVibrationAndExtraButtonPreferencesKeepTheirMeaning() {
        saved["touch_vibration"] = true
        saved["touch_extras"] = "FF,TURBO_A,UNKNOWN,A"
        val old = read()
        assertTrue(old.vibrate); assertEquals(1, old.vibrationStrength)
        assertEquals(setOf(TouchButton.FF, TouchButton.TURBO_A, TouchButton.ZOOM, TouchButton.REWIND), old.extras)
        saved["touch_vibration_level"] = 3
        assertEquals(3, read().vibrationStrength)
        saved["touch_vibrate"] = false
        assertFalse(read().vibrate)
    }

    @Test fun unknownChoicesAndDamagedLayoutsUseDefaults() {
        saved["touch_controls"] = 99
        saved["touch_preset"] = "FUTURE"
        saved["touch_layout_portrait"] = "A:broken;UNKNOWN:0,1,0,1,1,0"
        assertEquals(TouchShow.AUTO, read().show)
        assertEquals(TouchPreset.SIDES, read().preset)
        assertTrue(read().portrait.isEmpty)
    }

    /** Several layouts of the player's (2026-11-23): each kept by name, a preset never written over, the one before
     * several were kept becomes MY LAYOUT 1. */
    @Test fun customLayoutsAreKeptApart() {
        val a = TouchLayout(mapOf(TouchButton.A to TouchPlace(TouchPlace.Anchor.END, -40f, TouchPlace.Anchor.END, -40f)))
        val b = TouchLayout(mapOf(TouchButton.B to TouchPlace(TouchPlace.Anchor.START, 20f, TouchPlace.Anchor.END, -30f)))
        // EDIT LAYOUT on a preset makes a layout of the player's; again on another preset, a second one.
        val one = TouchControlsStyle().withLayout(portrait = false, a)
        assertEquals(listOf("MY LAYOUT 1"), one.customs.map { it.name })
        val two = one.copy(useCustom = false, preset = TouchPreset.CLASSIC).withLayout(portrait = false, b)
        assertEquals(listOf("MY LAYOUT 1", "MY LAYOUT 2"), two.customs.map { it.name })
        assertEquals(a, two.customs[0].landscape)
        assertEquals(b, two.landscape)
        // Back to the first: its places, untouched.
        assertEquals(a, two.pickCustom(0).landscape)
        write(two)
        assertEquals(two, read())
        // DELETE LAYOUT: the one in use goes, the preset comes back.
        val left = two.deleteCustom()
        assertEquals(listOf("MY LAYOUT 1"), left.customs.map { it.name })
        assertFalse(left.useCustom)
    }

    @Test fun theOneLayoutKeptBeforeBecomesTheFirst() {
        val a = TouchLayout(mapOf(TouchButton.A to TouchPlace(TouchPlace.Anchor.END, -40f, TouchPlace.Anchor.END, -40f)))
        saved["touch_use_custom"] = true
        saved["touch_layout_landscape"] = a.encode()
        val style = read()
        assertEquals(listOf("MY LAYOUT 1"), style.customs.map { it.name })
        assertEquals(a, style.currentCustom?.landscape)
    }

    @Test fun hudLayoutsRoundTrip() {
        val c = HudCustom("MY LAYOUT 1", mapOf(HudBlock.PARTY to HudPlace(0.25f, 1f), HudBlock.MAP to HudPlace(0f, 0f)))
        assertEquals(c, HudCustom.decode(c.encode()))
        val style = FieldHudStyle().withPlaces(c.places)
        assertEquals("MY LAYOUT 1", style.layoutLabel)
        // LAYOUT steps through the built-in ones, then the player's, then round.
        var s = style.copy(custom = -1)
        val seen = (0 until FieldHudLayout.entries.size + 1).map { s = s.nextLayout(); s.layoutLabel }
        assertEquals(FieldHudLayout.entries.drop(1).map { it.label } + "MY LAYOUT 1" + FieldHudLayout.entries.first().label, seen)
        assertNull(style.deleteCustom().currentCustom)
    }
}
