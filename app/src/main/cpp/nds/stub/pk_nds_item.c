// Gen 4's bag on a party Pokémon (Platinum, HeartGold / SoulSilver), the party menu's way, for pk_nds_call.cpp: USE runs
// the game's own check, takes one from the bag and applies the item; GIVE / TAKE set the held item with the game's own
// bag calls around it. Each re-reads the Pokémon first (its personality, not an egg), so a pick made before a switch never
// lands on another one. One call from a job's ops; it answers with a PK_NDS_ITEM_* code (CHECK: a mask).
//
// Built by scripts/gen_nds_stub.py like the hook (clang, armv5te, ARM mode, nothing to relocate): copied into the spare
// ITCM at PK_NDS_ITEM_ADDR, its arguments in a PkNdsItem the host writes beside it.

typedef unsigned int u32;
typedef unsigned short u16;
typedef unsigned char u8;

#include "../pk_nds_call_abi.h"

typedef u32 (*Fn)(u32, u32, u32, u32);
typedef u32 (*Fn5)(u32, u32, u32, u32, u32);

static u32 call(u32 fn, u32 a, u32 b, u32 c, u32 d) { return ((Fn) fn)(a, b, c, d); }

// The Pokémon in [slot] if it's still the one the pick showed, else 0.
static u32 same(volatile PkNdsItem* s)
{
    if (s->slot >= s->count) return 0;
    u32 mon = call(s->getMon, s->party, s->slot, 0, 0);
    if (!mon) return 0;
    if (call(s->getValue, mon, s->paramPersonality, 0, 0) != s->personality) return 0;
    if (call(s->getValue, mon, s->paramEgg, 0, 0)) return 0;
    return mon;
}

static int refused(volatile PkNdsItem* s, u32 item)
{
    return (item >= s->mailFirst && item <= s->mailLast) || item == s->keepItem;
}

static void hold(volatile PkNdsItem* s, u32 mon, u32 item)
{
    s->value = item;
    call(s->setValue, mon, s->paramHeld, (u32) &s->value, 0);
    if (s->arceusForm) call(s->arceusForm, mon, 0, 0, 0);
}

__attribute__((section(".text.entry"))) u32 pk_nds_item(volatile PkNdsItem* s)
{
    s->out = 0;
    if (s->mode == PK_NDS_ITEM_CHECK) {
        // bit slot * 4 + move: the item would do something (one bit a slot unless it's a one-move PP item)
        u32 mask = 0;
        u32 moves = s->moveSlot == PK_NDS_ITEM_EACH_MOVE ? 4 : 1;
        for (u32 slot = 0; slot < s->count && slot < 6; slot++) {
            u32 mon = call(s->getMon, s->party, slot, 0, 0);
            if (!mon || call(s->getValue, mon, s->paramEgg, 0, 0)) continue;
            for (u32 m = 0; m < moves; m++)
                if (call(s->check, mon, s->item, moves == 4 ? m : s->moveSlot, s->heap)) mask |= 1u << (slot * 4 + m);
        }
        return mask;
    }
    u32 mon = same(s);
    if (!mon) return PK_NDS_ITEM_CHANGED;
    u32 held = call(s->getValue, mon, s->paramHeld, 0, 0);
    s->out = held;
    switch (s->mode) {
    case PK_NDS_ITEM_USE:
        if (!call(s->check, mon, s->item, s->moveSlot, s->heap)) return PK_NDS_ITEM_NO_EFFECT;
        if (!call(s->bagRemove, s->bag, s->item, 1, s->heap)) return PK_NDS_ITEM_NONE_LEFT;
        ((Fn5) s->apply)(mon, s->item, s->moveSlot, s->location, s->heap);
        return PK_NDS_ITEM_DONE;
    case PK_NDS_ITEM_GIVE:
        if (held == s->item) return PK_NDS_ITEM_HOLDS_IT;
        if (refused(s, held) || refused(s, s->item)) return PK_NDS_ITEM_REFUSED;
        if (held && !call(s->bagCanFit, s->bag, held, 1, s->heap)) return PK_NDS_ITEM_BAG_FULL;
        if (!call(s->bagRemove, s->bag, s->item, 1, s->heap)) return PK_NDS_ITEM_NONE_LEFT;
        if (held) call(s->bagAdd, s->bag, held, 1, s->heap);
        hold(s, mon, s->item);
        return PK_NDS_ITEM_DONE;
    case PK_NDS_ITEM_TAKE:
        if (!held) return PK_NDS_ITEM_NOTHING;
        if (refused(s, held)) return PK_NDS_ITEM_REFUSED;
        if (!call(s->bagAdd, s->bag, held, 1, s->heap)) return PK_NDS_ITEM_BAG_FULL;
        hold(s, mon, 0);
        return PK_NDS_ITEM_DONE;
    }
    return PK_NDS_ITEM_CHANGED;
}
