package com.pokedaisy.app

/**
 * PC fan games: the native libretro host for the mkxp-z core (`cpp/pk_pc.c`). [pcStart], [pcFrame], [pcAudio] and
 * [pcStop] run on [PcGameActivity]'s GL thread (the core draws with that thread's GLES context); [pcInput] from any
 * thread; [pcMailbox] from a background thread.
 */
object PcCore {
    init {
        System.loadLibrary("pokedaisy")
    }

    /** RetroPad button bits ([pcInput]). */
    const val B = 0
    const val Y = 1
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val A = 8
    const val X = 9
    const val L = 10
    const val R = 11
    const val L2 = 12
    const val R2 = 13
    const val L3 = 14
    const val R3 = 15

    /** Loads [core] and the game at [game] (its Game.ini); false if either fails (logcat `pokedaisy/pc` says why). */
    @JvmStatic external fun pcStart(core: String, game: String, systemDir: String, saveDir: String): Boolean

    /** The core's sound rate, after [pcStart]. */
    @JvmStatic external fun pcSampleRate(): Int

    /** The frames due, then the picture into the view's framebuffer; -1 once the game has asked to close. */
    @JvmStatic external fun pcFrame(viewWidth: Int, viewHeight: Int): Int

    /** Up to [buf].size queued stereo samples; how many. */
    @JvmStatic external fun pcAudio(buf: ShortArray): Int

    /** The RetroPad buttons held, a bit each. */
    @JvmStatic external fun pcInput(mask: Int)

    /** The companion's JSON (UTF-8) when it changed since the last call, else null. */
    @JvmStatic external fun pcMailbox(): ByteArray?

    @JvmStatic external fun pcStop()
}
