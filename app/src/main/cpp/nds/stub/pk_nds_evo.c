// TRADE EVOLUTIONS on Platinum and HeartGold / SoulSilver, for pk_nds_call.cpp: the game's own evolution, as its link
// trade runs it (Platinum's unk_0203D1B8.c case 5 / 6, HeartGold's Task_WirelessTrade): the trade's evolution target
// from the game's own rules (EVO_CLASS_BY_TRADE / EVOCTX_TRADE with the held item), then Evolution_Begin with the trade's
// flags (no B to stop it) in a field task of ours that leaves the map the way the start menu's evolution does (its
// heap, its music stopped and the map's put back after), fading out and back in like the game's own map changes.
//
// One entry, two callers: a job's op calls it with its PkNdsEvo (CHECK: each slot's target; START: checks the Pokémon
// again and makes the field task), and the game's task runner calls it each frame with the FieldTask (its state below).
// The scene itself uses up a held item the evolution needed, adds the Pokédex entry and learns the move, as in a trade.
//
// Built by scripts/gen_nds_stub.py like the hook (clang, armv5te, ARM mode, nothing to relocate): copied into the spare
// ITCM at PK_NDS_EVO_ADDR, its PkNdsEvo at PK_NDS_EVO_ARGS. Both stay put while the scene plays (no other job runs
// then: the field isn't free).

typedef unsigned int u32;

#include "../pk_nds_call_abi.h"

typedef u32 (*Fn)(u32, u32, u32, u32);
typedef u32 (*Fn5)(u32, u32, u32, u32, u32);
typedef u32 (*Fn11)(u32, u32, u32, u32, u32, u32, u32, u32, u32, u32, u32);
typedef u32 (*Fn12)(u32, u32, u32, u32, u32, u32, u32, u32, u32, u32, u32, u32);

#define INLINE static inline __attribute__((always_inline))

INLINE u32 call(u32 fn, u32 a, u32 b, u32 c) { return ((Fn) fn)(a, b, c, 0); }

// The trade's target for [mon] (0: none), its method into s->method.
INLINE u32 target(volatile PkNdsEvo* s, u32 mon)
{
    u32 item = call(s->getValue, mon, s->pHeld, 0);
    return ((Fn5) s->getEvolution)(0, mon, s->ctxTrade, item, (u32) &s->method) & 0xFFFF;
}

INLINE u32 check(volatile PkNdsEvo* s)
{
    for (u32 slot = 0; slot < 6; slot++) {
        s->targets[slot] = 0;
        if (slot >= s->count) continue;
        u32 mon = call(s->getMon, s->party, slot, 0);
        if (!mon || call(s->getValue, mon, s->pEgg, 0)) continue;
        s->targets[slot] = target(s, mon);
    }
    return PK_NDS_EVO_STARTED;
}

INLINE u32 start(volatile PkNdsEvo* s)
{
    if (s->state != PK_NDS_EVO_IDLE && s->state != PK_NDS_EVO_FINISHED) return PK_NDS_EVO_BUSY;
    if (s->slot >= s->count) return PK_NDS_EVO_MOVED;
    u32 mon = call(s->getMon, s->party, s->slot, 0);
    if (!mon || call(s->getValue, mon, s->pPersonality, 0) != s->personality) return PK_NDS_EVO_MOVED;
    if (call(s->getValue, mon, s->pEgg, 0)) return PK_NDS_EVO_NONE;
    u32 t = target(s, mon);
    if (!t) return PK_NDS_EVO_NONE;
    s->mon = mon;
    s->target = t;
    s->state = PK_NDS_EVO_LEAVING;
    call(s->createTask, s->fieldSystem, PK_NDS_EVO_ADDR, PK_NDS_EVO_ARGS);
    return PK_NDS_EVO_STARTED;
}

// The field task: TRUE when it's done (the runner frees the task; its env is ours, never freed).
INLINE u32 task(volatile PkNdsEvo* s, u32 t)
{
    switch (s->state) {
    case PK_NDS_EVO_LEAVING:
        call(s->leaveMap, t, 0, 0);   // fade out, then finish the map (a sub-task: we're called again once it's done)
        s->state = PK_NDS_EVO_BEGIN;
        return 0;
    case PK_NDS_EVO_BEGIN: {
        call(s->soundStop, 0, 0, 0);
        call(s->heapCreate, s->heapParent, s->heapId, s->heapSize);
        u32 sd = s->saveData;
        u32 options = call(s->getOptions, sd, 0, 0), contest = call(s->getContest, sd, 0, 0);
        u32 dex = call(s->getPokedex, sd, 0, 0), bag = call(s->getBag, sd, 0, 0), records = call(s->getRecords, sd, 0, 0);
        if (s->getPoketch)
            s->evo = ((Fn12) s->begin)(0, s->mon, s->target, options, contest, dex, bag, records,
                                       call(s->getPoketch, sd, 0, 0), s->method, s->flags, s->heapId);
        else
            s->evo = ((Fn11) s->begin)(0, s->mon, s->target, options, contest, dex, bag, records, s->method, s->flags,
                                       s->heapId);
        s->state = PK_NDS_EVO_PLAYING;
        return 0;
    }
    case PK_NDS_EVO_PLAYING: {
        if (call(s->isDone, s->evo, 0, 0) != 1) return 0;
        call(s->free, s->evo, 0, 0);
        call(s->heapDestroy, s->heapId, 0, 0);
        call(s->stopBgm, s->bgm, 0, 0);
        call(s->setScene, 0, 0, 0);
        u32 location = *(volatile u32*) (s->fieldSystem + s->offLocation);
        call(s->playFieldBgm, s->fieldSystem, *(volatile u32*) location, 0);
        call(s->backToMap, t, 0, 0);   // start the map, then fade in (a sub-task again)
        s->state = PK_NDS_EVO_RETURNING;
        return 0;
    }
    case PK_NDS_EVO_RETURNING:
        s->state = PK_NDS_EVO_FINISHED;
        return 1;
    }
    s->state = PK_NDS_EVO_FINISHED;
    return 1;
}

__attribute__((section(".text.entry"))) u32 pk_nds_evo(u32 arg)
{
    volatile PkNdsEvo* s = (volatile PkNdsEvo*) PK_NDS_EVO_ARGS;
    if (arg != PK_NDS_EVO_ARGS) return task(s, arg);
    return s->mode == PK_NDS_EVO_CHECK ? check(s) : start(s);
}
