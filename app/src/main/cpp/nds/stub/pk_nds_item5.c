// Gen 5's bag on a party Pokémon (Black / White, Black 2 / White 2), for pk_nds_call.cpp. Their item effects live in
// an overlay only the party screen loads (where the field's own overlay sits), so the field can't call them: USE here
// applies the effect the app describes (HP, a revive, status, PP) through the game's own get / set of a Pokémon's data
// (its encryption and checksum) and its own bag calls; GIVE / TAKE are the game's calls alone. Each re-reads the Pokémon
// first (its personality, not an egg), and USE takes the item out of the bag only once it would do something. One call
// from a job's ops; it answers with a PK_NDS_ITEM_* code (CHECK: a mask, as Gen 4's).
//
// Built by scripts/gen_nds_stub.py like the hook (clang, armv5te, ARM mode, nothing to relocate): copied into the spare
// ITCM at PK_NDS_ITEM_ADDR, its arguments in a PkNdsItem5 the host writes beside it.

typedef unsigned int u32;
typedef unsigned short u16;
typedef unsigned char u8;

#include "../pk_nds_call_abi.h"

typedef u32 (*Fn)(u32, u32, u32, u32);

// Everything inlined into the entry: only .text.entry is copied into the game, so a call out of it would land nowhere.
#define INLINE static inline __attribute__((always_inline))

// No division instruction on the ARM9 and no runtime library here: a shift-and-subtract divide (HP is at most 999).
INLINE u32 udiv(u32 n, u32 d)
{
    u32 q = 0;
    for (int bit = 15; bit >= 0; bit--)
        if ((n >> bit) >= d) {
            n -= d << bit;
            q |= 1u << bit;
        }
    return q;
}

INLINE u32 call(u32 fn, u32 a, u32 b, u32 c) { return ((Fn) fn)(a, b, c, 0); }

// The bag's calls: Black / White's load the item's data with [heap] (Black 2 / White 2's don't use it).
INLINE u32 bag(volatile PkNdsItem5* s, u32 fn, u32 item) { return ((Fn) fn)(s->bag, item, 1, s->heap); }

INLINE u32 get(volatile PkNdsItem5* s, u32 mon, u32 param) { return call(s->getValue, mon, param, 0); }
INLINE void put(volatile PkNdsItem5* s, u32 mon, u32 param, u32 v) { call(s->setValue, mon, param, v); }

INLINE u32 monAt(volatile PkNdsItem5* s, u32 slot) { return s->party + slot * s->stride; }

// The Pokémon in [slot] if it's still the one the pick showed, else 0.
INLINE u32 same(volatile PkNdsItem5* s)
{
    if (s->slot >= s->count) return 0;
    u32 mon = monAt(s, s->slot);
    if (get(s, mon, s->pPersonality) != s->personality || get(s, mon, s->pEgg)) return 0;
    return mon;
}

INLINE int refused(volatile PkNdsItem5* s, u32 item)
{
    if (item >= s->mailFirst && item <= s->mailLast) return 1;
    for (u32 i = 0; i < PK_NDS_ITEM5_KEEP; i++)
        if (item && item == s->keep[i]) return 1;
    return 0;
}

// Whether move [m] would take PP (it has a move, below its maximum).
INLINE int ppHelps(volatile PkNdsItem5* s, u32 mon, u32 m)
{
    return get(s, mon, s->pMove1 + m) && get(s, mon, s->pPp1 + m) < get(s, mon, s->pMaxPp1 + m);
}

// What the effect would do to [mon]: bit m for a PP item's move m, else bit 0.
INLINE u32 helps(volatile PkNdsItem5* s, u32 mon)
{
    if (s->pp) {
        u32 mask = 0;
        for (u32 m = 0; m < 4; m++)
            if (ppHelps(s, mon, m)) mask |= 1u << m;
        return mask;
    }
    u32 hp = get(s, mon, s->pHp), max = get(s, mon, s->pMaxHp);
    if (s->revive) return hp == 0;
    if (hp == 0) return 0;
    if (s->heal && hp < max) return 1;
    if (s->statusMask && (get(s, mon, s->pStatus) & s->statusMask)) return 1;
    return 0;
}

INLINE void apply(volatile PkNdsItem5* s, u32 mon)
{
    u32 max = get(s, mon, s->pMaxHp);
    if (s->revive) {
        u32 hp = s->revive == 1 ? max : udiv(max, s->revive);
        put(s, mon, s->pHp, hp ? hp : 1);
        return;
    }
    if (s->heal) {
        u32 add = s->heal == PK_NDS_ITEM5_FULL ? max : s->healDiv ? udiv(max, s->healDiv) : s->heal;
        u32 hp = get(s, mon, s->pHp) + (add ? add : 1);
        put(s, mon, s->pHp, hp > max ? max : hp);
    }
    if (s->statusMask) put(s, mon, s->pStatus, get(s, mon, s->pStatus) & ~s->statusMask);
    if (s->pp) {
        for (u32 m = 0; m < 4; m++) {
            if (!(s->ppAll || m == s->moveSlot) || !ppHelps(s, mon, m)) continue;
            u32 maxPp = get(s, mon, s->pMaxPp1 + m);
            u32 pp = s->pp == PK_NDS_ITEM5_FULL ? maxPp : get(s, mon, s->pPp1 + m) + s->pp;
            put(s, mon, s->pPp1 + m, pp > maxPp ? maxPp : pp);
        }
    }
}

__attribute__((section(".text.entry"))) u32 pk_nds_item5(volatile PkNdsItem5* s)
{
    s->out = 0;
    if (s->mode == PK_NDS_ITEM_CHECK) {
        u32 mask = 0;
        for (u32 slot = 0; slot < s->count && slot < 6; slot++) {
            u32 mon = monAt(s, slot);
            if (get(s, mon, s->pEgg)) continue;
            mask |= (helps(s, mon) & 0xF) << (slot * 4);
        }
        return mask;
    }
    u32 mon = same(s);
    if (!mon) return PK_NDS_ITEM_CHANGED;
    u32 held = get(s, mon, s->pHeld);
    s->out = held;
    switch (s->mode) {
    case PK_NDS_ITEM_USE: {
        u32 h = helps(s, mon);
        if (s->pp && !s->ppAll ? !(h & (1u << s->moveSlot)) : !h) return PK_NDS_ITEM_NO_EFFECT;
        if (!bag(s, s->bagRemove, s->item)) return PK_NDS_ITEM_NONE_LEFT;
        apply(s, mon);
        return PK_NDS_ITEM_DONE;
    }
    case PK_NDS_ITEM_GIVE:
        if (held == s->item) return PK_NDS_ITEM_HOLDS_IT;
        if (refused(s, held) || refused(s, s->item)) return PK_NDS_ITEM_REFUSED;
        if (held && !bag(s, s->bagCanFit, held)) return PK_NDS_ITEM_BAG_FULL;
        if (!bag(s, s->bagRemove, s->item)) return PK_NDS_ITEM_NONE_LEFT;
        if (held) bag(s, s->bagAdd, held);
        put(s, mon, s->pHeld, s->item);
        return PK_NDS_ITEM_DONE;
    case PK_NDS_ITEM_TAKE:
        if (!held) return PK_NDS_ITEM_NOTHING;
        if (refused(s, held)) return PK_NDS_ITEM_REFUSED;
        if (!bag(s, s->bagAdd, held)) return PK_NDS_ITEM_BAG_FULL;
        put(s, mon, s->pHeld, 0);
        return PK_NDS_ITEM_DONE;
    }
    return PK_NDS_ITEM_CHANGED;
}
