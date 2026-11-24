// NO HMS NEEDED on Platinum, HeartGold / SoulSilver and Black / White / Black 2 / White 2, for pk_nds_call_hm_free: the
// game's two party move checks (Party_HasMonWithMove - the A press on water, the waterfall - and the field move scripts'
// FindPartySlotWithMove / GetPartySlotWithMove command; Gen 5's counterparts, found on its carts) jump here, and this
// does what they did with one more step: nobody knowing one of the HMs (PkNdsHm.hms), the first Pokémon that isn't an
// egg does it. One that knows it still goes first; the scripts' badge checks come after and stay.
//
// Built by scripts/gen_nds_stub.py like the others (clang, armv5te, ARM mode, nothing to relocate): at PK_NDS_HM_ADDR,
// its PkNdsHm at PK_NDS_HM_ARGS. The jump (Thumb `bx pc` into ARM `mov r3, #which; ldr pc, =PK_NDS_HM_ADDR`) leaves the
// caller's lr, so returning lands back in the caller.

typedef unsigned int u32;
typedef unsigned short u16;

#include "../pk_nds_call_abi.h"

typedef u32 (*Fn)(u32, u32, u32, u32);

#define INLINE static inline __attribute__((always_inline))

INLINE u32 call(u32 fn, u32 a, u32 b, u32 c) { return ((Fn) fn)(a, b, c, 0); }

INLINE int isHm(volatile PkNdsHm* s, u32 move)
{
#pragma clang loop unroll(disable)
    for (int i = 0; i < 12 && s->hms[i]; i++)
        if (s->hms[i] == move) return 1;
    return 0;
}

// The slot that does [move]: one that knows it, else (an HM) the first that isn't an egg; 6 = nobody.
INLINE u32 slotFor(volatile PkNdsHm* s, u32 party, u32 move)
{
    u32 n = call(s->getCount, party, 0, 0);
    u32 first = 6;
#pragma clang loop unroll(disable)
    for (u32 i = 0; i < n && i < 6; i++) {
        u32 mon = call(s->getMon, party, i, 0);
        if (call(s->getValue, mon, s->pEgg, 0)) continue;
        if (first == 6) first = i;
#pragma clang loop unroll(disable)
        for (u32 k = 0; k < 4; k++)
            if (call(s->getValue, mon, s->pMove1 + k, 0) == move) return i;
    }
    return isHm(s, move) ? first : 6;
}

__attribute__((section(".text.entry"))) u32 pk_nds_hm(u32 a, u32 b, u32 c, u32 which)
{
    volatile PkNdsHm* s = (volatile PkNdsHm*) PK_NDS_HM_ARGS;
    (void) c;
    u32 party = a, move = b & 0xFFFF;
    volatile u16* dest = 0;
    if (which != PK_NDS_HM_HAS_MOVE) {
        if (s->gen5) {
            // (ctx, work): the variable, then the move, read through the script's own calls
            u32 gameData = call(s->readHalf, b, 0, 0);
            dest = (volatile u16*) call(s->getVarPtr, a, b, 0);
            move = call(s->tryGetVar, a, b, 0) & 0xFFFF;
            party = call(s->getParty, gameData, 0, 0);
        } else {
            // the script command: its variable, then the move (each a half-word of the script)
            u32 fs = *(volatile u32*) (a + s->offFieldSystem);
            dest = (volatile u16*) call(s->getVarPtr, fs, call(s->readHalf, a, 0, 0) & 0xFFFF, 0);
            move = call(s->tryGetVar, fs, call(s->readHalf, a, 0, 0) & 0xFFFF, 0) & 0xFFFF;
            party = call(s->getParty, *(volatile u32*) (fs + s->offSaveData), 0, 0);
        }
    } else if (s->gen5) {
        party = call(s->getParty, a, 0, 0);   // (gameData, move)
    }
    u32 slot = slotFor(s, party, move);
    if (!dest) return slot < 6 ? slot : s->none;
    *dest = (u16) slot;
    return 0;
}
