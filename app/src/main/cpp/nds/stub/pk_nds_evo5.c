// TRADE EVOLUTIONS on Black / White / Black 2 / White 2, for pk_nds_call.cpp: the game's own evolution, as its link
// trade starts it. The target from the game's own check with the trade's method class (1: plain, or the held item;
// the partner Pokémon given as the Pokémon itself, so the two that evolve by trading for each other never match), then
// the evolution scene's proc with the trade's param (the scene can't be stopped, it uses up the held item, adds the
// Pokédex entry and learns the move) in a GMEVENT of ours that leaves the field and comes back to it the way the bag's
// evolution does (the field's own child events: fade out, end the map, the scene, start the map, fade in).
//
// One entry, two callers: a job's op calls it with its PkNdsEvo5 (CHECK: each slot's target; START: checks the Pokémon
// again and sets the event), and the game's event runner calls it each frame with (event, &seq, work).
//
// Built by scripts/gen_nds_stub.py like the others: copied into the spare ITCM at PK_NDS_EVO_ADDR, its PkNdsEvo5 at
// PK_NDS_EVO_ARGS. Both stay put while the scene plays (no other job runs then: the field isn't free).

typedef unsigned int u32;

#include "../pk_nds_call_abi.h"

typedef u32 (*Fn)(u32, u32, u32, u32);
typedef u32 (*Fn7)(u32, u32, u32, u32, u32, u32, u32);

#define INLINE static inline __attribute__((always_inline))

INLINE u32 call(u32 fn, u32 a, u32 b, u32 c) { return ((Fn) fn)(a, b, c, 0); }
INLINE u32 rd(u32 a) { return *(volatile u32*) a; }

INLINE u32 party(volatile PkNdsEvo5* s) { return rd(rd(s->gs + s->offGameData) + s->offParty); }
INLINE u32 mon(volatile PkNdsEvo5* s, u32 slot) { return party(s) + 8 + slot * 0xDC; }

// The trade's target for [m] (0: none), its method into s->method.
INLINE u32 target(volatile PkNdsEvo5* s, u32 m)
{
    if (call(s->getValue, m, s->pEgg, 0)) return 0;
    return ((Fn7) s->getEvolution)(party(s), m, 1, m, 0, (u32) &s->method, s->heap) & 0xFFFF;
}

INLINE u32 check(volatile PkNdsEvo5* s)
{
    for (u32 slot = 0; slot < 6; slot++) s->targets[slot] = slot < s->count ? target(s, mon(s, slot)) : 0;
    return PK_NDS_EVO_STARTED;
}

INLINE u32 start(volatile PkNdsEvo5* s)
{
    if (s->slot >= s->count) return PK_NDS_EVO_MOVED;
    u32 m = mon(s, s->slot);
    if (call(s->getValue, m, s->pPersonality, 0) != s->personality) return PK_NDS_EVO_MOVED;
    u32 t = target(s, m);
    if (!t) return PK_NDS_EVO_NONE;
    s->target = t;
    s->scene[0] = rd(s->gs + s->offGameData);
    s->scene[1] = party(s);
    s->scene[2] = t | s->slot << 16 | (s->method & 0xFF) << 24;
    s->scene[3] = 1;
    s->scene[4] = 0;
    u32 ev = ((Fn) s->createEvent)(s->gs, 0, PK_NDS_EVO_ADDR, 4);
    call(s->setEvent, s->gs, ev, 0);
    return PK_NDS_EVO_STARTED;
}

INLINE void child(volatile PkNdsEvo5* s, u32 ev, u32 c) { call(s->callChild, ev, c, 0); }

// The event: 1 when it's done (the runner frees it and goes back to no event).
INLINE u32 event(volatile PkNdsEvo5* s, u32 ev, volatile u32* seq)
{
    u32 gs = s->gs;
    switch (*seq) {
    case 0:
        child(s, ev, call(s->fadeOut, gs, rd(gs + s->offFieldMap), 0));
        break;
    case 1:
        child(s, ev, call(s->endField, gs, rd(gs + s->offFieldMap), 0));
        break;
    case 2:
        ((Fn) s->callProc)(gs, s->evoOverlay, s->evoProc, (u32) s->scene);
        break;
    case 3:
        if (rd(gs + s->offProc)) return 0;   // the scene still running
        child(s, ev, call(s->startField, gs, 0, 0));
        break;
    case 4:
        // the map is a new one now: its field map read again
        child(s, ev, ((Fn7) s->fadeIn)(gs, rd(gs + s->offFieldMap), 0, 0, 0, 0, 0));
        break;
    default:
        return 1;
    }
    *seq = *seq + 1;
    return 0;
}

__attribute__((section(".text.entry"))) u32 pk_nds_evo5(u32 a, u32 b)
{
    volatile PkNdsEvo5* s = (volatile PkNdsEvo5*) PK_NDS_EVO_ARGS;
    if (a != PK_NDS_EVO_ARGS) return event(s, a, (volatile u32*) b);
    return s->mode == PK_NDS_EVO_CHECK ? check(s) : start(s);
}
