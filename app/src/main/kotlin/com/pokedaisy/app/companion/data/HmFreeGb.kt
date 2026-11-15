package com.pokedaisy.app.companion.data

/**
 * NO HMS NEEDED on the Game Boy (the English Red, Blue, Yellow, Gold, Silver and Crystal): code of our own in a bank's
 * unused end (scripts/hm_free_gb.asm, assembled per cart from pret's .sym by scripts/gen_hm_free_gb.py into
 * [HM_FREE_GB]) and a jump to it in the game's own routine, a game-option patch like [HmFree]'s GBA one.
 *
 * - Red / Blue / Yellow use their field moves only from the party menu, which lists a Pokémon's own. Its list then gets
 *   the HMs whose badge is in (CUT, SURF, STRENGTH, FLASH, FLY, up to the menu's four rows), and the game's own handlers
 *   run them with their own badge checks.
 * - Gold / Silver / Crystal's field (a tree, the water, a boulder, a whirlpool, a waterfall) asks CheckPartyMove: with
 *   nobody knowing that HM, the first Pokémon that isn't an egg does it. Headbutt and Rock Smash (TMs) keep the game's
 *   rule. Their party menu also gets FLY and FLASH once their badges are in, while there's room beside CANCEL.
 *
 * Every byte a write replaces is checked against the cart first (all or nothing), so a cart that differs (another
 * language, a hack on the same hash's code) gets nothing.
 */
object HmFreeGb {
    /** A run of bytes at [offset] in the file: [expect] (hex; null = as many zeros) becomes [value] (hex). */
    class Edit(val offset: Long, val expect: String?, val value: String)

    class Cart(val name: String, val sha1s: Set<String>, val edits: List<Edit>)

    /** The patch for the cart whose SHA-1 is [sha1] and whose bytes are [rom], or null (the row shows N/A). */
    fun find(sha1: String, rom: ByteArray): HmFree.Patch? {
        val cart = HM_FREE_GB.firstOrNull { sha1 in it.sha1s } ?: return null
        val writes = ArrayList<InstantText.Write>()
        for (e in cart.edits) {
            val value = hex(e.value)
            val expect = e.expect?.let(::hex) ?: ByteArray(value.size)
            if (expect.size != value.size) return null
            val at = e.offset.toInt()
            if (at < 0 || rom.size < at + expect.size || !rom.copyOfRange(at, at + expect.size).contentEquals(expect)) return null
            // pkRomPatch takes 4 bytes a write
            for (i in value.indices step 4) {
                val n = minOf(4, value.size - i)
                writes += InstantText.Write(e.offset + i, expect.copyOfRange(i, i + n), value.copyOfRange(i, i + n))
            }
        }
        return HmFree.Patch(writes)
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
