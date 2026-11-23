package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.data.Logs
import com.pokedaisy.app.companion.data.PlayerLook
import com.pokedaisy.app.companion.data.WildPin
import com.pokedaisy.app.game.PlayerLookCore
import kotlinx.atomicfu.atomic
import okio.Buffer

/** The running game's appearance overlay. Choices can arrive from UI or asset workers. Only the
 * game thread changes the core or handles palette restoration after loading a state.
 *
 * WILD PIN ([WildPin]) shares it: the core's block call replaces every block, so the look's blocks and the pin's go in
 * together. A pin whose bytes don't match the ROM is dropped and the look goes in alone; [onPinResult] (game thread) says
 * which pin is in, or null. */
class GamePlayerLook(
    private val core: PlayerLookCore,
    private val onPinResult: (requested: WildPin.Pin?, installed: Boolean) -> Unit = { _, _ -> },
) {
    private val requested = atomic<PlayerLook.Patch?>(null)
    private val requestedPin = atomic<WildPin.Pin?>(null)
    private var attempted: PlayerLook.Patch? = null
    private var attemptedPin: WildPin.Pin? = null
    private var installed: PlayerLook.Patch? = null
    private var refreshFrames = 0
    val ride get() = installed?.ride?.takeIf { installed?.blocks?.isNotEmpty() == true }
    /** The pin asked for (any thread). */
    val pin: WildPin.Pin? get() = requestedPin.value

    fun select(patch: PlayerLook.Patch?) {
        while (true) {
            val previous = requested.value
            if (patch === previous || patch?.sameAs(previous) == true) return
            if (requested.compareAndSet(previous, patch)) return
        }
    }

    /** WILD PIN in or out on the next [service]. */
    fun selectPin(pin: WildPin.Pin?) {
        requestedPin.value = pin
    }

    /** Drops [pin] only if it's still the one asked for (the game thread ending it, a new one already picked stays). */
    fun clearPin(pin: WildPin.Pin): Boolean = requestedPin.compareAndSet(pin, null)

    fun service(force: Boolean = false): Boolean {
        val patch = requested.value
        val pin = requestedPin.value
        val lookChanged = force || patch !== attempted
        if (!lookChanged && pin === attemptedPin) return false
        attempted = patch
        attemptedPin = pin
        val look = patch?.blocks.orEmpty().map { WildPin.RomBlock(0x08000000 + it.off, it.crc, it.data) }
        var lookIn = look.isEmpty()
        var pinIn = false
        val all = WildPin.withPin(look, pin)
        if (all.isEmpty()) {
            replace(all)
        } else if (replace(all) >= 0) {
            lookIn = true
            pinIn = pin != null
        } else if (pin != null) {
            // Together refused (all or none): one of the two doesn't match. The look alone first; if that's the one
            // refused, the pin may still fit on its own.
            lookIn = replace(look) >= 0
            if (!lookIn) pinIn = replace(listOf(pin.block)) >= 0
        }
        installed = patch.takeIf { lookIn }
        if (!lookIn) Logs.i("player look: the ROM didn't match, left as it is")
        if (pin != null) {
            Logs.i("wild pin: ${if (pinIn) "species ${pin.species} on ${pin.group}.${pin.num}" else "the map's slots didn't match, no pin"}")
            if (!pinIn) requestedPin.compareAndSet(pin, null)
        }
        onPinResult(pin, pinIn)
        // Only when the look itself changed: a pin coming or going never re-settles palettes mid-battle.
        if (lookChanged) settle()
        return true
    }

    private fun replace(blocks: List<WildPin.RomBlock>): Int {
        val bytes = Buffer()
        blocks.forEach { bytes.write(it.data) }
        return core.replaceLookBlocks(
            IntArray(blocks.size) { blocks[it].addr },
            IntArray(blocks.size) { blocks[it].size },
            IntArray(blocks.size) { blocks[it].crc },
            bytes.readByteArray(),
        )
    }

    fun settle() {
        refreshFrames = 600
        for (palette in installed?.palettes.orEmpty()) {
            val bytes = Buffer()
            palette.from.forEach { bytes.write(it) }
            core.repaletteLook(bytes.readByteArray(), palette.to)
        }
    }

    fun fieldFrame() {
        if (refreshFrames == 0) return
        if (core.refreshLook(installed?.playerPal)) {
            refreshFrames = 0
        } else if (--refreshFrames == 0) {
            Logs.i("player look: the sprite's palette never settled")
        }
    }
}
