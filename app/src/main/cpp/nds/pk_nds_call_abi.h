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

/** The Gen 4 FLY check's scratch (a FieldMoveContext a job hands the game): past the scripts, up to INSTANT TEXT. */
#define PK_NDS_SCRATCH_ADDR (PK_NDS_SCRIPT_ADDR + PK_NDS_SCRIPT_MAX)
#define PK_NDS_SCRATCH_MAX 0x1000u

/** INSTANT TEXT's routine on the Gen 4 games (DsInstantText.ROUTINE in Kotlin, written through pk_nds_code_write): it stays
 * here for as long as the option is on, between and during our jobs, so nothing else of ours may be written over it. The
 * routine is 54 bytes; 256 are kept for it. DsInstantTextTest reads these two lines. */
#define PK_NDS_INSTANT_TEXT_ADDR 0x01FFF000u
#define PK_NDS_INSTANT_TEXT_MAX 0x100u

/** Gen 4's bag helper (stub/pk_nds_item.c): its code past INSTANT TEXT's routine, then its PkNdsItem. The code is
 * position independent (gen_nds_stub.py checks there's nothing to relocate), so it runs from any address. */
#define PK_NDS_ITEM_ADDR (PK_NDS_INSTANT_TEXT_ADDR + PK_NDS_INSTANT_TEXT_MAX)
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

/** Gen 5's bag helper (stub/pk_nds_item5.c): its code from the Gen 4 FLY check's scratch (no Gen 5 job uses it) up to
 * INSTANT TEXT's routine, its PkNdsItem5 at PK_NDS_ITEM_ARGS like Gen 4's (a game runs one or the other). */
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

/** NO HMS NEEDED on Platinum / HeartGold / SoulSilver and Black / White / Black 2 / White 2 (stub/pk_nds_hm.c): its code and arguments at the very top of the
 * spare ITCM (past the bag helpers' arguments), kept there while the option is on: the game's own party move checks
 * jump into it (pk_nds.cpp's hmFreeTick). */
#define PK_NDS_HM_ADDR 0x01FFFC00u
#define PK_NDS_HM_ARGS 0x01FFFF00u

/** Which routine the hook stands in for (r3, set by the jump). */
#define PK_NDS_HM_HAS_MOVE 1u      // Party_HasMonWithMove(party, move) (Gen 5: (gameData, move)): a slot, or `none`
#define PK_NDS_HM_SCRIPT 2u        // ScrCmd_FindPartySlotWithMove(ctx) (Gen 5: (ctx, work)): the slot into its variable, or 6

typedef struct PkNdsHm {
    unsigned int getCount;       // Party_GetCurrentCount(party)
    unsigned int getMon;         // Party_GetPokemonBySlotIndex(party, slot)
    unsigned int getValue;       // Pokemon_GetValue(mon, param, NULL)
    unsigned int getParty;       // SaveData_GetParty(saveData)
    unsigned int readHalf;       // ScriptContext_ReadHalfWord(ctx); Gen 5: the work's game data (work)
    unsigned int getVarPtr;      // FieldSystem_GetVarPointer(fieldSystem, id); Gen 5: the next variable's pointer (ctx, work)
    unsigned int tryGetVar;      // FieldSystem_TryGetVar(fieldSystem, id); Gen 5: the next value (ctx, work)
    unsigned int offFieldSystem; // ScriptContext.fieldSystem
    unsigned int offSaveData;    // FieldSystem.saveData
    unsigned int pEgg, pMove1;   // MON_DATA_IS_EGG, MON_DATA_MOVE1
    unsigned int none;           // Party_HasMonWithMove's "nobody" (PARTY_SLOT_NONE; Gen 5's: 6)
    unsigned int gen5;           // Gen 5's argument shapes (above)
    unsigned short hms[12];      // the moves anyone may stand in for, 0-terminated
} PkNdsHm;

/* The map of the spare ITCM, checked here (the stubs' code sizes are checked in pk_nds_call.cpp, which has the bytes).
 * A Gen 4 game and a Gen 5 game never run together, so the Gen 5 helpers (HELPER / SKILL in the scripts' place, ITEM5 in
 * the scratch's) may share space with Gen 4's; INSTANT TEXT's routine, the item arguments and NO HMS NEEDED, which can
 * stay resident while a job runs, collide with nothing of either. */
#ifdef __cplusplus
#define PK_NDS_ABI_ASSERT(c, m) static_assert(c, m)
#else
#define PK_NDS_ABI_ASSERT(c, m) _Static_assert(c, m)
#endif
#define PK_NDS_ABI_MAX(a, b) ((a) > (b) ? (a) : (b))
PK_NDS_ABI_ASSERT(PK_NDS_HOOK_ADDR < PK_NDS_JOB, "the hook runs into the job block");
PK_NDS_ABI_ASSERT(PK_NDS_JOB + sizeof(PkNdsJob) <= PK_NDS_EVO_ADDR, "the job block runs into the evolution helper");
PK_NDS_ABI_ASSERT(PK_NDS_EVO_ADDR < PK_NDS_EVO_ARGS, "the evolution helper's arguments come before its code");
PK_NDS_ABI_ASSERT(PK_NDS_EVO_ARGS + PK_NDS_ABI_MAX(sizeof(PkNdsEvo), sizeof(PkNdsEvo5)) <= PK_NDS_SCRIPT_ADDR,
                  "the evolution helper's arguments run into the scripts");
PK_NDS_ABI_ASSERT(PK_NDS_SCRATCH_ADDR + PK_NDS_SCRATCH_MAX <= PK_NDS_INSTANT_TEXT_ADDR, "the scripts' scratch runs into INSTANT TEXT");
PK_NDS_ABI_ASSERT(PK_NDS_HELPER_ADDR >= PK_NDS_EVO_ARGS + sizeof(PkNdsEvo5) && PK_NDS_HELPER_ADDR < PK_NDS_SKILL_ADDR,
                  "Gen 5's field-skill helper is out of place");
PK_NDS_ABI_ASSERT(PK_NDS_SKILL_ADDR + sizeof(PkNdsSkill) <= PK_NDS_ITEM5_ADDR, "the field-skill arguments run into Gen 5's bag helper");
PK_NDS_ABI_ASSERT(PK_NDS_ITEM5_ADDR < PK_NDS_INSTANT_TEXT_ADDR, "Gen 5's bag helper starts past INSTANT TEXT");
PK_NDS_ABI_ASSERT(PK_NDS_INSTANT_TEXT_ADDR + PK_NDS_INSTANT_TEXT_MAX <= PK_NDS_ITEM_ADDR, "INSTANT TEXT runs into Gen 4's bag helper");
PK_NDS_ABI_ASSERT(PK_NDS_ITEM_ADDR < PK_NDS_ITEM_ARGS, "Gen 4's bag helper starts past its arguments");
PK_NDS_ABI_ASSERT(PK_NDS_ITEM_ARGS + PK_NDS_ABI_MAX(sizeof(PkNdsItem), sizeof(PkNdsItem5)) <= PK_NDS_HM_ADDR,
                  "the bag helpers' arguments run into NO HMS NEEDED's helper");
PK_NDS_ABI_ASSERT(PK_NDS_HM_ADDR < PK_NDS_HM_ARGS, "NO HMS NEEDED's helper starts past its arguments");
PK_NDS_ABI_ASSERT(PK_NDS_HM_ARGS + sizeof(PkNdsHm) <= 0x02000000u, "NO HMS NEEDED's arguments run past the ITCM");
