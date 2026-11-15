// What pk_nds_call.cpp (the host) and stub/pk_nds_hook.c (inside the game) agree on: where the hook and its job sit in
// the ARM9's ITCM and the job's layout. Plain C, both sides.
#pragma once

/** The hook's code: the top 16 KB of the ITCM, which Platinum never uses (its ITCM ends at 0x01FF8660; checked empty on
 * the field, in menus and in battle). */
#define PK_NDS_HOOK_ADDR 0x01FFC000u
#define PK_NDS_JOB 0x01FFC400u
/** A script of ours the game's script engine reads straight from here (ScriptContext_Jump). */
#define PK_NDS_SCRIPT_ADDR 0x01FFD000u
#define PK_NDS_SCRIPT_MAX 0x1000u

#define PK_NDS_MAX_OPS 24
#define PK_NDS_MAX_RESULTS 32
/** An argument of PK_NDS_RESULT_REF + i is op i's result. */
#define PK_NDS_RESULT_REF 0xFFFFFFE0u

/** Op kinds; anything else is a function's address (Thumb with bit 0 set), called with the four arguments. */
#define PK_NDS_OP_END 0u
#define PK_NDS_OP_STORE8 1u    // *(u8*) (a0 + a1) = a2
#define PK_NDS_OP_STORE16 2u
#define PK_NDS_OP_STORE32 3u
#define PK_NDS_OP_LOAD32 4u    // result = *(u32*) (a0 + a1)

typedef struct PkNdsOp {
    unsigned int kind;
    unsigned int a[4];
} PkNdsOp;

/** Gen 5's field-skill helper (stub/pk_nds_skill.c): its code, then its PkNdsSkill, in the spare ITCM (Black has no
 * scripts of ours to put there). */
#define PK_NDS_HELPER_ADDR 0x01FFD000u
#define PK_NDS_SKILL_ADDR 0x01FFD400u

/** The helper's arguments: the game's three field-skill functions, the skill, its use parameters (FLDSKILL's: the party
 * slot, the skill, a word - FLY's zone) and the check work the game fills (0x14 bytes in Black). */
typedef struct PkNdsSkill {
    unsigned int initCheck;   // (fieldMap, check*)
    unsigned int canUse;      // (skill, check*): 0 = may
    unsigned int use;         // (skill, param*, check*): the skill's event
    unsigned int skill;
    unsigned short paramSlot, paramSkill;
    unsigned int paramArg;
    unsigned int check[8];
} PkNdsSkill;

/** Gen 4's bag helper (stub/pk_nds_item.c): its code, then its PkNdsItem, at the top of the spare ITCM (past the
 * scripts and the FLY check's scratch). */
#define PK_NDS_ITEM_ADDR 0x01FFF000u
#define PK_NDS_ITEM_ARGS 0x01FFF800u

/** What the helper does (PkNdsItem.mode) and what it answers. */
#define PK_NDS_ITEM_CHECK 0u     // a mask: bit slot * 4 + move = the item would do something there
#define PK_NDS_ITEM_USE 1u
#define PK_NDS_ITEM_GIVE 2u
#define PK_NDS_ITEM_TAKE 3u
/** CHECK's moveSlot for a one-move PP item: every move of every Pokémon. */
#define PK_NDS_ITEM_EACH_MOVE 0xFFu

#define PK_NDS_ITEM_DONE 0u
#define PK_NDS_ITEM_NO_EFFECT 1u
#define PK_NDS_ITEM_NONE_LEFT 2u
#define PK_NDS_ITEM_CHANGED 3u    // not the Pokémon the pick showed (or an egg now)
#define PK_NDS_ITEM_HOLDS_IT 4u
#define PK_NDS_ITEM_REFUSED 5u    // Mail, or the item kept out ([keepItem])
#define PK_NDS_ITEM_BAG_FULL 6u
#define PK_NDS_ITEM_NOTHING 7u    // TAKE: it holds nothing

/** The helper's arguments: the game's functions, the party / bag / map from the job's ops, the request; [out] = the
 * item the Pokémon held before (GIVE / TAKE). */
typedef struct PkNdsItem {
    unsigned int getMon;      // Party_GetPokemonBySlotIndex(party, slot)
    unsigned int getValue;    // Pokemon_GetValue(mon, param, NULL)
    unsigned int setValue;    // Pokemon_SetValue(mon, param, &value)
    unsigned int check;       // Pokemon_CheckItemEffects(mon, item, moveSlot, heap): TRUE = it would do something
    unsigned int apply;       // Pokemon_ApplyItemEffects(mon, item, moveSlot, location, heap)
    unsigned int bagRemove;   // Bag_TryRemoveItem(bag, item, count, heap)
    unsigned int bagAdd;      // Bag_TryAddItem(bag, item, count, heap)
    unsigned int bagCanFit;   // Bag_CanFitItem(bag, item, count, heap)
    unsigned int arceusForm;  // Pokemon_SetArceusForm(mon) after the held item changes (0: none)
    unsigned int paramPersonality, paramHeld, paramEgg;   // the MON_DATA_* numbers
    unsigned int mailFirst, mailLast, keepItem;           // items GIVE / TAKE leave alone
    unsigned int heap;
    unsigned int party, bag, location, count;             // stored by the job's ops
    unsigned int mode, item, slot, moveSlot, personality;
    unsigned int value;       // SetValue's argument
    unsigned int out;
} PkNdsItem;

/** Gen 5's bag helper (stub/pk_nds_item5.c): its code from the Gen 4 FLY check's scratch (no Gen 5 job uses it), its
 * PkNdsItem5 at PK_NDS_ITEM_ARGS like Gen 4's (a game runs one or the other). */
#define PK_NDS_ITEM5_ADDR 0x01FFE000u
#define PK_NDS_ITEM5_KEEP 24
/** An amount meaning "all the way" (HP to its maximum, PP to the move's). */
#define PK_NDS_ITEM5_FULL 0xFFFFu

/** Its arguments: the game's get / set of a Pokémon's data (by value) and its bag calls (bag, item, count), the MON_DATA
 * numbers, the party (the first Pokémon and the stride), the request and - for USE - the effect. */
typedef struct PkNdsItem5 {
    unsigned int getValue;    // (mon, param, NULL)
    unsigned int setValue;    // (mon, param, value)
    unsigned int bagRemove;   // (bag, item, count): TRUE = taken
    unsigned int bagAdd;      // (bag, item, count)
    unsigned int bagCanFit;   // (bag, item, count)
    unsigned int pPersonality, pHeld, pEgg, pStatus, pHp, pMaxHp, pPp1, pMaxPp1, pMove1;
    unsigned int mailFirst, mailLast;
    unsigned int keep[PK_NDS_ITEM5_KEEP];   // items GIVE / TAKE leave to the game (forms change with them)
    unsigned int party, stride, count, bag;
    unsigned int heap;        // the bag calls' 4th argument (Black / White load the item's data with it)
    unsigned int mode, item, slot, moveSlot, personality;
    // USE's effect: heal (points, PK_NDS_ITEM5_FULL, or with healDiv a share of the maximum), revive (0, 1 = to full,
    // n = to max / n), the status bits it clears, PP (points or PK_NDS_ITEM5_FULL; ppAll: every move, else moveSlot's)
    unsigned int heal, healDiv, revive, statusMask, pp, ppAll;
    unsigned int out;
} PkNdsItem5;

/** TRADE EVOLUTIONS' helper on Platinum / HeartGold / SoulSilver (stub/pk_nds_evo.c): its code after the job block and
 * its PkNdsEvo after that, both below the scripts. They stay there while the evolution plays (its field task runs this
 * code every frame), so nothing else may be written there. */
#define PK_NDS_EVO_ADDR 0x01FFC680u
#define PK_NDS_EVO_ARGS 0x01FFCE00u

#define PK_NDS_EVO_CHECK 0u   // each party slot's trade evolution target into targets[]
#define PK_NDS_EVO_START 1u   // slot / personality: evolve it

/** What START answers (CHECK answers STARTED). */
#define PK_NDS_EVO_STARTED 0u
#define PK_NDS_EVO_NONE 1u     // it doesn't evolve by trading now (another species, an Everstone, its item missing, an egg)
#define PK_NDS_EVO_MOVED 2u    // the slot holds another Pokémon now
#define PK_NDS_EVO_BUSY 3u     // an evolution of ours is still playing

/** The field task's state (PkNdsEvo.state). */
#define PK_NDS_EVO_IDLE 0u
#define PK_NDS_EVO_LEAVING 1u
#define PK_NDS_EVO_BEGIN 2u
#define PK_NDS_EVO_PLAYING 3u
#define PK_NDS_EVO_RETURNING 4u
#define PK_NDS_EVO_FINISHED 5u

/** The helper's arguments: the game's functions and constants, what the job's ops store, the request, the task's state. */
typedef struct PkNdsEvo {
    unsigned int createTask;     // FieldSystem_CreateTask(fieldSystem, func, env)
    unsigned int leaveMap;       // (task): fade out + finish the map (Platinum FieldTransition_FadeOutAndFinishMap)
    unsigned int backToMap;      // (task): start the map + fade in (FieldTransition_StartMapAndFadeIn)
    unsigned int heapCreate, heapDestroy, heapParent, heapId, heapSize;
    unsigned int begin;          // Evolution_Begin: 12 arguments with a Poketch (Platinum), 11 without (HeartGold)
    unsigned int isDone, free;
    unsigned int getMon;         // (party, slot)
    unsigned int getValue;       // (mon, param, NULL)
    unsigned int getEvolution;   // (party, mon, context, item, &method)
    unsigned int getOptions, getContest, getPokedex, getBag, getRecords, getPoketch;   // (saveData); getPoketch 0: none
    unsigned int soundStop, stopBgm, bgm, setScene, playFieldBgm;
    unsigned int offLocation;    // FieldSystem.location (its map id first)
    unsigned int pPersonality, pHeld, pEgg;
    unsigned int ctxTrade, flags;   // the trade's evolution context and Evolution_Begin flags
    unsigned int fieldSystem, saveData, party, count;   // stored by the job's ops
    unsigned int mode, slot, personality;
    unsigned int state, mon, target, method, evo;
    unsigned int targets[6];
} PkNdsEvo;

/** TRADE EVOLUTIONS on Black / White / Black 2 / White 2 (stub/pk_nds_evo5.c): in PkNdsEvo's place (one game runs at a
 * time), the same modes and answers. Its event's steps (PkNdsEvo5.state) are the GMEVENT's own seq. */
typedef struct PkNdsEvo5 {
    unsigned int createEvent;    // GMEVENT_Create(gs, parent, func, work size)
    unsigned int setEvent;       // GameSystem_SetEvent(gs, event)
    unsigned int callChild;      // (event, child): the child runs, then the event again
    unsigned int fadeOut, endField, startField, fadeIn;   // the field's own child events, as the bag's evolution runs them
    unsigned int callProc;       // (gs, overlay, proc table, param)
    unsigned int evoOverlay, evoProc;   // the evolution scene's overlay and proc table
    unsigned int getValue;       // (mon, param, NULL)
    unsigned int getEvolution;   // (party, mon, method class, other mon, time, &method, heap)
    unsigned int heap;           // where the check's scratch is allocated (the events' heap)
    unsigned int offGameData, offParty, offFieldMap, offProc;   // GameSystem's / GameData's
    unsigned int pPersonality, pEgg;
    unsigned int gs, count, mode, slot, personality;   // filled in per job
    unsigned int target, method;
    unsigned int scene[5];       // the scene's param: game data, party, target | slot << 16 | method << 24, 1, 0
    unsigned int targets[6];
} PkNdsEvo5;

typedef struct PkNdsJob {
    unsigned int seq;    // the host's number for this job
    unsigned int done;   // the hook sets it to seq once the ops ran
    unsigned int slot;   // the function pointer the hook replaced
    unsigned int orig;   // its value, put back first thing
    unsigned int res[PK_NDS_MAX_RESULTS];
    PkNdsOp ops[PK_NDS_MAX_OPS];
} PkNdsJob;
