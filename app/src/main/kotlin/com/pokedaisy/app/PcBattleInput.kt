package com.pokedaisy.app

import android.os.SystemClock
import com.pokedaisy.app.companion.BattleInput

/**
 * The companion's battle buttons for a PC fan game (PcGameActivity): each pick is one line of Ruby for our preload
 * (linux/pc_games/pokedaisy_companion.rb's request), which the game's own menus then take - FIGHT and a move through the
 * game's command and fight windows, RUN as its RUN, a switch through the battle's own switch checks. [send] hands the
 * line to the core ([PcCore.pcRuby]); the battle's waiting menu comes back with the next snapshot.
 */
class PcBattleInput(private val send: (String) -> Boolean = PcCore::pcRuby) : BattleInput {
    /** When the last pick went out; null = none yet. */
    @Volatile private var sentAt: Long? = null

    /** A pick just sent: taps wait until the game has had it ([BUSY_MS]). */
    override val busy: Boolean get() = sentAt?.let { SystemClock.uptimeMillis() - it < BUSY_MS } ?: false

    override val canSwitch: Boolean get() = true

    override fun selectAction(actionIndex: Int) {
        // FIGHT opens the game's moves; BAG its bag; POKEMON its party screen (the companion's own pick list sends a
        // switch instead, [switchTo]); RUN runs.
        if (actionIndex in 0..3) request(":action, $actionIndex")
    }

    override fun selectMove(moveIndex: Int) {
        if (moveIndex in 0..3) request(":fight, $moveIndex")
    }

    override fun back() = request(":back")

    /** [personality] is the party slot + 1 here ([PcCompanion]): the battle's own index for its switch. */
    override fun switchTo(personality: Long) {
        val slot = personality - 1
        if (slot in 0..5) request(":switch, $slot")
    }

    private fun request(args: String) {
        if (send("PokeDaisyCompanion.request($args)")) sentAt = SystemClock.uptimeMillis()
    }

    private companion object {
        const val BUSY_MS = 600L
    }
}
