package com.pokedaisy.app.companion.data

/**
 * NO HMS NEEDED on the DS (melonDS: pk_nds_call.cpp's pk_nds_call_hm_free, stub/pk_nds_hm.c): the game's two party move
 * checks - the field move scripts' "which Pokémon knows this move" command and the field's own check (the A press on
 * water, a waterfall) - jump into a helper of ours that answers as they did, and with nobody knowing one of the HMs, the
 * first Pokémon that isn't an egg. One that knows the move still goes first; the badges stay. Platinum / HeartGold /
 * SoulSilver from pret's xMAPs; Black / White / Black 2 / White 2 found on the carts' own code (Ghidra).
 *
 * Checked headless on each cart: the field check and (Gen 4) the script command answering slot 0 for the HMs with nobody
 * knowing them and the game's own answer for other moves, back as it was when off; Black 2 surfing from the field with
 * nobody knowing Surf. US / English carts only; not the hacks (Renegade Platinum and Blaze Black 2 / Volt White 2 Redux
 * keep the retail header, Origin HeartGold has its own engine); DSperate: N/A.
 */
object DsHmFree {
    /** The carts it was checked on: code to revision. */
    val CARTS = setOf("CPUE" to 1, "IPKE" to 0, "IPGE" to 0, "IRBO" to 0, "IRAO" to 0, "IREO" to 0, "IRDO" to 0)

    fun proved(code: String, revision: Int?): Boolean = (code to revision) in CARTS
}
