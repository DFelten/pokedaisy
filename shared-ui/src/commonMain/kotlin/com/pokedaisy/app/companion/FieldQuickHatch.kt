package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.data.*

/**
 * QUICK HATCH ([hatchSoon]) over the same game queue as bag / party actions: an Egg's summary gets HATCH where this game's
 * daycare step counter is known ([counterOff]) and its party calls are there.
 */
class FieldQuickHatch(
    delegate: ItemUse,
    private val items: FieldItemUse,
    private val counterOff: () -> Int?,
    private val config: () -> NativeConfig?,
    private val enabled: () -> Boolean,
    private val onField: () -> Boolean,
    private val handedOver: () -> Unit = {},
) : ItemUse by delegate {
    fun supported() = counterOff() != null && config() != null && items.supportedFields()?.party != null
    override fun canHatch() = enabled() && supported()

    override fun hatch(slot: Int, personality: Long, done: (HatchResult) -> Unit) {
        if (!enabled()) return done(HatchResult.FAILED)
        val off = counterOff() ?: return done(HatchResult.FAILED)
        val cfg = config() ?: return done(HatchResult.FAILED)
        if (!onField()) return done(HatchResult.NOT_NOW)
        items.onGame(HatchResult.FAILED, { result ->
            // The player walks next: the window over the game goes.
            if (result == HatchResult.HATCHING) handedOver()
            done(result)
        }) { fields, calls, busy ->
            if (!enabled()) HatchResult.FAILED
            else hatchSoon(fields.fns, cfg.saveBlock1Ptr, cfg.staticSaveBlocks, off, slot, personality, busy || !onField(), calls)
        }
    }
}
