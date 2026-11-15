// See pk_nds_call.h. Addresses from pret/pokeplatinum's xMAP (the decomp builds the retail ROM byte for byte); every
// function there is Thumb, so they're called with bit 0 set. Black's (Gen 5, no decomp) were traced in the running game:
// see BLACK below.

#include "pk_nds_call_memory.h"

#include <stddef.h>
#include <string.h>

#include <vector>

#include "pk_nds_ports_gen.h"
#include "pk_nds_stub_gen.h"

using u8 = uint8_t;
using u16 = uint16_t;
using u32 = uint32_t;

namespace {

// Platinum's functions for the jobs.
struct CallFns {
    uint32_t scriptManagerSet;          // ScriptManager_Set(fieldSystem, scriptID, object): a script task, not started
    uint32_t fieldTaskGetEnv;           // FieldTask_GetEnv(task): its ScriptManager
    uint32_t scriptContextCreateAndStart;   // (fieldSystem, scriptID): a context on that script's file and text bank
    uint32_t scriptContextJump;         // (ctx, ptr)
    uint32_t stringTemplateNew;         // (maxArgs, maxLen, heapID)
    uint32_t stringInit;                // (size, heapID)
    uint32_t scriptManagerGetMemberPtr;   // (scriptManager, member)
    uint32_t fieldMovesSetUsableMoves;  // (fieldSystem, FieldMoveContext*): what the party menu builds before its checks
    uint32_t fieldMovesCheckFly;        // (const FieldMoveContext*): FIELD_MOVE_ERROR_* (static in field_move_tasks.c)
    uint32_t checkFlyLocationUnlocked;  // (fieldSystem, spawn id 1..)
    uint32_t saveDataGetParty;          // (saveData)
    uint32_t partyGetPokemonBySlotIndex;   // (party, slot)
    uint32_t flyContextNew;             // (heapID, fieldSystem, mon, mapID, x, z): x / z stored after (4 register args)
    uint32_t fieldMovesFlyTask;         // the field task FLY runs
    uint32_t fieldSystemCreateTask;     // (fieldSystem, func, env)
    uint32_t journalCreateEventUsedMove;   // (fieldMove, mapID, heapID)
    uint32_t journalSaveData;           // (journalEntry, data, JOURNAL_LOCATION)
    int offSaveData, offJournalEntry;   // FieldSystem.saveData / .journalEntry
    int offFlyX, offFlyZ;               // FlyContext.x / .z (s16)
    int fieldMoveFly, journalLocation;
    uint32_t commonScriptBase;          // a common script id: its file's text bank (CommonStrings) is the one our scripts use
    int heapField2;                     // HEAP_ID_FIELD2
    int memberMainContext, memberNumActiveContexts, memberStrTemplate, memberMessageBuf, memberTemporaryBuf;
    int offScriptManagerState;          // ScriptManager.state (u8)
    // HeartGold / SoulSilver (script_manager.c): StartMapSceneScript's pieces by hand - a ScriptEnvironment, a context on
    // [hostScript]'s file and text bank, jumped to our bytes, then Task_RunScripts' state 0 done here and its task made.
    uint32_t scriptEnvironmentNew;      // ()
    uint32_t createScriptContext;       // (fieldSystem, script)
    uint32_t taskRunScripts;            // the field task that runs the environment's contexts
    int hostScript;                     // a std script whose file and text bank ours borrows
    int envState, envContextCount, envScriptNumber, envContexts, envMsgFmt, envString0, envString1;
    // HeartGold / SoulSilver's FLY (start_menu.c's Task_UseFlyInField after the Pokégear's pick): sub_02067BF8(heapID,
    // fieldSystem, mon, partySlot, mapID, x, z) - the last three on the stack, so stored after it at these offsets - then
    // sub_02067C30 run as a field task. Which towns are unlocked the app reads from the save's flypoint flags itself.
    int flyHasSlotArg, offFlyMap;
};

const CallFns PLATINUM = {
    0x0203E881, 0x02050A65, 0x0203EAB9, 0x0203E819, 0x0200B369, 0x02023791, 0x0203EF61,
    0x02070729, 0x02070951, 0x0203A921, 0x0207A269, 0x0207A0FD, 0x0207064D, 0x02070681, 0x02050905, 0x0202BE01, 0x0202B759,
    0x0C, 0x9C,
    0x0A, 0x0C,
    1, 1,
    2018,   // CommonScript_PC (scripts_common.s), used only for its file + text bank: our script replaces it at once
    11,
    13, 7, 15, 16, 17,
    4,
    0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0,
    0, 0,
};

// French Platinum (CPUF rev 0): the US cart's functions, found by their code (scripts/port_nds.py); the structs and the
// script ids are the US cart's.
const CallFns PLATINUM_FR = {
    0x0203E925, 0x02050B09, 0x0203EB5D, 0x0203E8BD, 0x0200B37D, 0x02023801, 0x0203F005,
    0x020707BD, 0x020709E5, 0x0203A9C5, 0x0207A309, 0x0207A19D, 0x020706E1, 0x02070715, 0x020509A9, 0x0202BE71, 0x0202B7C9,
    0x0C, 0x9C,
    0x0A, 0x0C,
    1, 1,
    2018,
    11,
    13, 7, 15, 16, 17,
    4,
    0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0,
    0, 0,
};

// pret/pokeheartgold's .xMAP: every address the same in the US HeartGold / SoulSilver builds and the German and French
// ones, rev 0 (checked against their ARM9s, including Task_RunScripts' calls and environment layout).
const CallFns HEARTGOLD = {
    0, 0, 0,
    0x0203FE0D,   // ScriptJump
    0x0200BD19,   // MessageFormat_New_Custom
    0x02026355,   // String_New
    0,
    0x02067E11,   // FieldMove_InitCheckData(fieldSystem, checkData)
    0x0206800D,   // FieldMove_CheckFly(checkData): FIELD_MOVE_RESPONSE_*, Platinum's numbers
    0,
    0x02074905,   // SaveArray_Party_Get
    0x02074645,   // Party_GetMonByIndex
    0x02067BF9,   // sub_02067BF8
    0x02067C31,   // sub_02067C30
    0x020504F1,   // FieldSystem_CreateTask
    0, 0,
    0x0C, 0,
    0x0A, 0x0C,
    0, 0,
    0,
    11,
    0, 0, 0, 0, 0,
    0,
    0x0204001D,   // ScriptEnvironment_New
    0x020400AD,   // CreateScriptContext
    0x0203FF45,   // Task_RunScripts
    2010,         // std_pokecenter_pc: scr_seq_0003 and msg_0040, the PC's own lines
    // ScriptEnvironment (script.h; checked against Task_RunScripts' code): state, activeScriptContextCount,
    // activeScriptNumber, scriptContexts[3], msgfmt, stringBuffer0 / 1
    4, 9, 0x0A, 0x38, 0x44, 0x48, 0x4C,
    1, 0x08,
};

// Origin HeartGold (nds/pk_nds_field.cpp's ORIGIN_HEARTGOLD): HeartGold's functions in the hack's build, each found by its
// US code (BLs and pool words masked) and its environment layout checked in Task_RunScripts' code; the script borrowed is
// the US one's (its file and text bank kept by the hack).
const CallFns ORIGIN_HEARTGOLD = {
    0, 0, 0,
    0x0203F515,   // ScriptJump
    0x0200BCB9,   // MessageFormat_New_Custom
    0x02026865,   // String_New
    0,
    0x020670AD,   // FieldMove_InitCheckData
    0x020672A9,   // FieldMove_CheckFly
    0,
    0x0207365D,   // SaveArray_Party_Get
    0x0207339D,   // Party_GetMonByIndex
    0x02066E95,   // sub_02067BF8
    0x02066ECD,   // sub_02067C30
    0x0204F979,   // FieldSystem_CreateTask
    0, 0,
    0x0C, 0,
    0x0A, 0x0C,
    0, 0,
    0,
    11,
    0, 0, 0, 0, 0,
    0,
    0x0203F725,   // ScriptEnvironment_New
    0x0203F7B5,   // CreateScriptContext
    0x0203F64D,   // Task_RunScripts
    2010,         // std_pokecenter_pc
    4, 9, 0x0A, 0x38, 0x44, 0x48, 0x4C,
    1, 0x08,
};

// Black (IRBO rev 0). There's no decomp, so these were traced in the running game with nds_dump's `watch` (the ARM9's
// writes to the GameSystem's event, their registers and stack): the PC's furniture, a Pokémon Center nurse and FLY
// from the party menu, each followed back to the function that made the event.
//
// Gen 5's GameSystem runs one GMEVENT at a time (+0x18). Every frame with none, GameSystem_Main calls its event check
// (+0x10: the field's, overlay 21, given the field map from +0x14), and whatever event that returns becomes the running
// one - pressing A at the PC is the check returning the PC script's event. So the hook takes the check's place for one
// frame: the game only calls it when no event runs, the moment pressing A would have started one. A job that starts an
// event stores the real check back, makes the event, sets it, and has the hook go on into a function that returns 0
// (the ARM9's `movs r0, #0; bx lr`), so the check's own answer can't replace it.
struct Gen5Fns {
    u32 gameSystemPtr;
    int offEventCheck, offEvent, offFieldMap;   // GameSystem: the event check, the running GMEVENT, the field map
    u32 eventCheck;       // the field's event check (overlay 21), what +0x10 holds on the field
    u32 returnZero;       // an ARM9 `movs r0, #0; bx lr` (Thumb)
    u32 setEvent;         // GameSystem_SetEvent(gs, event): str r1, [r0, #0x18]
    u32 scriptEvent;      // (gs, script id, object, -): a field script's event (overlay 10) - the nurse's and the PC's
    u32 pcScript;         // the Pokémon Center PC's script (what the field's check starts for A at Accumula's PC)
    // FLDSKILL (overlay 10): the party menu fills a check work from the field map, asks the skill's test, then the
    // skill's event (the X menu's event changes into it). Skill 4 is FLY: its test = the zone allows flying (the zone
    // header's +0x1E bit 13; outdoors), its event flies to the zone in the use parameters' word - landing where the
    // zone's FLY spot is (the game's own).
    u32 skillInitCheck, skillCanUse, skillUse;
    u32 skillFly;
    // Overlays 10 and 21 are the field's: their code where these words are, or no job (another overlay in their place).
    struct { u32 addr, word; } code[6];
};

const Gen5Fns BLACK = {
    0x02146248,
    0x10, 0x18, 0x20,
    0x0218A075,
    0x0200565D,
    0x02012109,
    0x02158769,
    10090,
    0x0215E841, 0x0215EA15, 0x0215EA39,
    4,
    {
        {0x0218A074, 0xB082B5F8}, {0x02158768, 0x1C0DB538}, {0x02158920, 0xB083B5F0},
        {0x0215E840, 0xB08AB5F8}, {0x0215EA14, 0x1C02B510}, {0x0215EA38, 0x1C03B538},
    },
};

// White (IRAO rev 0): Black's functions, found again by their code; the overlays and the GameSystem pointer sit 0x20
// higher, the ARM9's code where Black's is. Checked headless like Black's (the PC, FLY).
const Gen5Fns WHITE = {
    0x02146268,
    0x10, 0x18, 0x20,
    0x0218A095,
    0x0200565D,
    0x02012109,
    0x02158789,
    10090,
    0x0215E861, 0x0215EA35, 0x0215EA59,
    4,
    {
        {0x0218A094, 0xB082B5F8}, {0x02158788, 0x1C0DB538}, {0x02158940, 0xB083B5F0},
        {0x0215E860, 0xB08AB5F8}, {0x0215EA34, 0x1C02B510}, {0x0215EA58, 0x1C03B538},
    },
};

// French Black / White (IRBF / IRAF rev 0): the US / Europe functions, found by their code in the same overlays
// (scripts/port_nds.py), overlay 21 and the start of overlay 10 0x80 lower, overlay 10 from FLDSKILL on 0x90.
const Gen5Fns BLACK_FR = {
    0x021461C8,
    0x10, 0x18, 0x20,
    0x02189FF5,
    0x0200565D,
    0x02012109,
    0x021586E9,
    10090,
    0x0215E7B1, 0x0215E985, 0x0215E9A9,
    4,
    {
        {0x02189FF4, 0xB082B5F8}, {0x021586E8, 0x1C0DB538}, {0x021588A0, 0xB083B5F0},
        {0x0215E7B0, 0xB08AB5F8}, {0x0215E984, 0x1C02B510}, {0x0215E9A8, 0x1C03B538},
    },
};

const Gen5Fns WHITE_FR = {
    0x021461E8,
    0x10, 0x18, 0x20,
    0x0218A015,
    0x0200565D,
    0x02012109,
    0x02158709,
    10090,
    0x0215E7D1, 0x0215E9A5, 0x0215E9C9,
    4,
    {
        {0x0218A014, 0xB082B5F8}, {0x02158708, 0x1C0DB538}, {0x021588C0, 0xB083B5F0},
        {0x0215E7D0, 0xB08AB5F8}, {0x0215E9A4, 0x1C02B510}, {0x0215E9C8, 0x1C03B538},
    },
};

// Black 2 (IREO rev 0): Black's functions in a new build, each found by its code (BLs masked) - overlay 12 holds what
// Black's 10 did, 36 the field's event check (Black's 21) - and checked headless on the player's save (the PC, FLY).
const Gen5Fns BLACK2 = {
    0x021410F4,
    0x10, 0x18, 0x20,
    0x0218185D,
    0x020056B9,
    0x02016AED,
    0x0215366D,
    10090,
    0x02159231, 0x021593FD, 0x02159421,
    4,
    {
        {0x0218185C, 0xB082B5F8}, {0x0215366C, 0x1C0DB538}, {0x02153824, 0xB083B5F0},
        {0x02159230, 0xB08AB5F8}, {0x021593FC, 0x1C02B510}, {0x02159420, 0x1C03B538},
    },
};

// White 2 (IRDO rev 0): Black 2's functions, found again by their code, the overlays and the GameSystem pointer 0x40 higher
// (the ARM9's low code where Black 2's is). Checked headless like Black 2's.
const Gen5Fns WHITE2 = {
    0x02141134,
    0x10, 0x18, 0x20,
    0x0218189D,
    0x020056B9,
    0x02016AED,
    0x021536AD,
    10090,
    0x02159271, 0x0215943D, 0x02159461,
    4,
    {
        {0x0218189C, 0xB082B5F8}, {0x021536AC, 0x1C0DB538}, {0x02153864, 0xB083B5F0},
        {0x02159270, 0xB08AB5F8}, {0x0215943C, 0x1C02B510}, {0x02159460, 0x1C03B538},
    },
};

// The bag on a party Pokémon (stub/pk_nds_item.c): the party menu's functions, the map's label for the friendship an
// item gives (Pokemon_ApplyItemEffects' location), and the save's party / bag.
struct ItemFns {
    u32 saveDataGetBag;   // (saveData)
    u32 mapLabel;         // (mapHeaderID): Platinum's MapHeader_GetMapLabelTextID, HG / SS' MapHeader_GetMapSec
    PkNdsItem item;       // the helper's functions and constants; the request filled in per job
    int offLocation;      // FieldSystem.location (its mapHeaderID first)
    // Each function's first word, checked before a job: callSet 2 is the German and French HeartGold too, and a cart
    // whose code isn't where this table says gets no job rather than a call into something else.
    struct { u32 addr, word; } code[11];
};

// pret/pokeplatinum's xMAP (US rev 1), the party menu's own calls (party_menu/main.c, callbacks.c, context_menu.c).
const ItemFns PLATINUM_ITEMS = {
    0x0207D991,   // SaveData_GetBag
    0x0203A139,   // MapHeader_GetMapLabelTextID
    {
        0x0207A0FD,   // Party_GetPokemonBySlotIndex
        0x02074471,   // Pokemon_GetValue
        0x02074B31,   // Pokemon_SetValue
        0x02096421,   // Pokemon_CheckItemEffects
        0x02096955,   // Pokemon_ApplyItemEffects
        0x0207D60D,   // Bag_TryRemoveItem
        0x0207D571,   // Bag_TryAddItem
        0x0207D55D,   // Bag_CanFitItem
        0x02077929,   // Pokemon_SetArceusForm
        0, 6, 76,     // MON_DATA_PERSONALITY, _HELD_ITEM, _IS_EGG
        137, 148,     // Mail
        112,          // the Griseous Orb: the party menu changes Giratina's form with it (and not in the Distortion World)
        11,           // HEAP_ID_FIELD2
    },
    0x1C,
    {
        {0x0207D990, 0x21034B01}, {0x0203A138, 0xF7FFB508}, {0x0207A0FC, 0x1C05B538},
        {0x02074470, 0x1C05B570}, {0x02074B30, 0x1C05B570}, {0x02096420, 0xB086B5F8},
        {0x02096954, 0xB09CB5F8}, {0x0207D60C, 0xB083B5F0}, {0x0207D570, 0xB083B5F0},
        {0x0207D55C, 0xF7FFB508}, {0x02077928, 0x47184B00},
    },
};

// pret/pokeheartgold's xMAP (built in its Docker image: the US HeartGold byte for byte), its party menu's calls
// (party_menu.c, party_menu_items.c, party_menu_list_items.c); SoulSilver's ARM9 is the same.
const ItemFns HEARTGOLD_ITEMS = {
    0x0207879D,   // Save_Bag_Get
    0x0203B36D,   // MapHeader_GetMapSec (PartyMenu_GetCurrentMapSec)
    {
        0x02074645,   // Party_GetMonByIndex
        0x0206E541,   // GetMonData
        0x0206EC41,   // SetMonData
        0x0208FD9D,   // CanUseItemOnPokemon
        0x020902D1,   // UseItemOnPokemon
        0x02078435,   // Bag_TakeItem
        0x02078399,   // Bag_AddItem
        0x02078385,   // Bag_HasSpaceForItem
        0x02071BC9,   // Pokemon_UpdateArceusForm
        0, 6, 76,
        137, 148,
        112,
        11,
    },
    0x20,
    {
        {0x0207879C, 0x21034B01}, {0x0203B36C, 0xF7FFB508}, {0x02074644, 0x1C05B538},
        {0x0206E540, 0x1C05B570}, {0x0206EC40, 0x1C05B570}, {0x0208FD9C, 0xB086B5F8},
        {0x020902D0, 0xB09DB5F0}, {0x02078434, 0xB083B5F0}, {0x02078398, 0xB083B5F0},
        {0x02078384, 0xF7FFB508}, {0x02071BC8, 0x47184B00},
    },
};

const ItemFns* itemFnsFor(const PkNdsFieldGame* game)
{
    if (!game) return nullptr;
    switch (game->callSet) {
    case 1: return &PLATINUM_ITEMS;
    case 2: return &HEARTGOLD_ITEMS;
    default: return nullptr;
    }
}

// Gen 5's bag on a party Pokémon (stub/pk_nds_item5.c): no decomp, so traced in the running game (nds_dump's `watch` on
// the Pokémon's record and the bag's slot while the game's own bag used a Potion on the player's Black 2 save), each then
// called headless. The save image (SaveControl -> backup system -> image) holds the party and the bag.
struct ItemFns5 {
    u32 saveControlPtr;
    int offBackupSystem, offImage, offParty, offBag;
    PkNdsItem5 item;
    struct { u32 addr, word; } code[10];
};

// The MON_DATA numbers (the same in all four: Gen 4's, three lower from STATUS on), Mail, and the items GIVE / TAKE
// leave to the game: the Griseous Orb, the Drives and the Plates (their Pokémon's form goes with them).
#define GEN5_ITEM_PARAMS 0, 6, 76, 157, 160, 161, 58, 66, 54, 137, 148, \
    {112, 116, 117, 118, 119, 298, 299, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313}, 0, 0xDC, 0, 0

// Black 2 (IREO rev 0): traced on the player's save (above), each function then called headless. Its bag calls take
// (bag, item, count) and ignore a fourth.
const ItemFns5 BLACK2_ITEMS = {
    0x0209A338, 0x10, 0x34, 0x18E00 + 8, 0x18400,
    {
        0x0201CCF9,   // the getter: decrypts, reads [param], encrypts again
        0x0201CD1D,   // the setter (by value): decrypts, writes, fixes the checksum, encrypts again
        0x0200842D,   // the bag's remove (bag, item, count): FALSE without that many
        0x02008269,   // its add
        0x02008239,   // whether the add has room
        GEN5_ITEM_PARAMS, 1,
    },
    {
        {0x0201CCF8, 0x1C05B570}, {0x0201CCFC, 0x1C161C0C}, {0x0201CD1C, 0x1C05B570}, {0x0201CD20, 0x1C161C0C},
        {0x0200842C, 0xB082B570}, {0x02008430, 0x1C0E1C05}, {0x02008268, 0xF7FFB508}, {0x0200826C, 0x2800FFEF},
        {0x02008238, 0xF7FFB508}, {0x0200823C, 0x2800FFDD},
    },
};

// White 2 (IRDO rev 0): Black 2's bag calls where Black 2's are, its getter / setter 0x2C later (found by Black 2's
// code with its calls masked), its SaveControl with its statics.
const ItemFns5 WHITE2_ITEMS = {
    0x0209A378, 0x10, 0x34, 0x18E00 + 8, 0x18400,
    {0x0201CD25, 0x0201CD49, 0x0200842D, 0x02008269, 0x02008239, GEN5_ITEM_PARAMS, 1},
    {
        {0x0201CD24, 0x1C05B570}, {0x0201CD28, 0x1C161C0C}, {0x0201CD48, 0x1C05B570}, {0x0201CD4C, 0x1C161C0C},
        {0x0200842C, 0xB082B570}, {0x02008430, 0x1C0E1C05}, {0x02008268, 0xF7FFB508}, {0x0200826C, 0x2800FFEF},
        {0x02008238, 0xF7FFB508}, {0x0200823C, 0x2800FFDD},
    },
};

// Black (IRBO rev 0): the getter / setter found by Black 2's code, the bag calls by their shape (the field's own item
// script commands call them, overlay 21). They take a heap for the item's data: HEAPID 1 (one the field always has;
// 2 and 6 aren't there and hang).
const ItemFns5 BLACK_ITEMS = {
    0x020A7298, 0x10, 0x34, 0x18E00 + 8, 0x18400,
    {0x02017E1D, 0x02017E41, 0x02007F1D, 0x02007E51, 0x02007E3D, GEN5_ITEM_PARAMS, 1},
    {
        {0x02017E1C, 0x1C05B570}, {0x02017E20, 0x1C161C0C}, {0x02017E40, 0x1C05B570}, {0x02017E44, 0x1C161C0C},
        {0x02007F1C, 0xB083B5F0}, {0x02007F20, 0x1C0F1C06}, {0x02007E50, 0xB086B5F8}, {0x02007E54, 0x1C0D1C06},
        {0x02007E3C, 0xF7FFB508}, {0x02007E40, 0x2800FFDB},
    },
};

// White (IRAO rev 0): Black's bag calls where Black's are, its getter / setter 0x1C later.
const ItemFns5 WHITE_ITEMS = {
    0x020A72B8, 0x10, 0x34, 0x18E00 + 8, 0x18400,
    {0x02017E39, 0x02017E5D, 0x02007F1D, 0x02007E51, 0x02007E3D, GEN5_ITEM_PARAMS, 1},
    {
        {0x02017E38, 0x1C05B570}, {0x02017E3C, 0x1C161C0C}, {0x02017E5C, 0x1C05B570}, {0x02017E60, 0x1C161C0C},
        {0x02007F1C, 0xB083B5F0}, {0x02007F20, 0x1C0F1C06}, {0x02007E50, 0xB086B5F8}, {0x02007E54, 0x1C0D1C06},
        {0x02007E3C, 0xF7FFB508}, {0x02007E40, 0x2800FFDB},
    },
};

const ItemFns5* itemFns5For(const PkNdsFieldGame* game)
{
    if (!game) return nullptr;
    switch (game->callSet) {
    case 3: return &BLACK_ITEMS;
    case 4: return &WHITE_ITEMS;
    case 5: return &BLACK2_ITEMS;
    case 6: return &WHITE2_ITEMS;
    default: return nullptr;
    }
}

// TRADE EVOLUTIONS (stub/pk_nds_evo.c): the game's own evolution as its link trade runs it, in a field task of ours
// that leaves and restores the map as the start menu's evolution does (StartMenu_EvolveInit / StartMenu_Evolve, HG's
// Task_StartMenu_Evolution / _WaitEvolution: their heap, the music stopped before and the map's put back after).
struct EvoFns {
    u32 getParty;   // (saveData)
    PkNdsEvo evo;   // the helper's functions and constants; the request filled in per job
    struct { u32 addr, word; } code[21];
};

// pret/pokeplatinum's xMAP (US rev 1); the heap id, the music and FieldSystem.location as StartMenu_Evolve uses them.
const EvoFns PLATINUM_EVO = {
    0x0207A269,   // SaveData_GetParty
    {
        0x02050905,   // FieldSystem_CreateTask
        0x02055975,   // FieldTransition_FadeOutAndFinishMap
        0x020559CD,   // FieldTransition_StartMapAndFadeIn
        0x02017FC9, 0x0201807D, 3, 0x49, 0x30000,   // Heap_Create / Heap_Destroy: HEAP_ID_EVOLUTION in HEAP_ID_APPLICATION
        0x0207AE69, 0x0207B0D1, 0x0207B0E1,         // Evolution_Begin / _IsDone / _Free
        0x0207A0FD,   // Party_GetPokemonBySlotIndex
        0x02074471,   // Pokemon_GetValue
        0x02076B95,   // Pokemon_GetEvolutionTargetSpecies
        0x02025E45, 0x0208C325, 0x02027561, 0x0207D991, 0x0202CD89, 0x02056B25,   // SaveData_GetOptions,
        // PokemonSummaryScreen_ShowContestData, SaveData_GetPokedex / _GetBag / _GetGameRecords / _GetPoketch
        0x0200569D, 0x020055D1, 0x475, 0x02004235, 0x020556A1,   // Sound_StopWaveOutAndSequences, Sound_StopBGM(SEQ_SHINKA),
        // Sound_SetScene, FieldBGM_PlayEffectiveForMapHeader
        0x1C,         // FieldSystem.location
        0, 6, 76,     // MON_DATA_PERSONALITY, _HELD_ITEM, _IS_EGG
        1, 4,         // EVO_CLASS_BY_TRADE; the trade's flags
    },
    {
        {0x0207A268, 0x21024B01}, {0x02050904, 0x1C05B570}, {0x02055974, 0x49024B01}, {0x020559CC, 0x49024B01},
        {0x02017FC8, 0x2304B508}, {0x0201807C, 0x1C04B510}, {0x0207AE68, 0xB089B5F0}, {0x0207B0D0, 0x78003067},
        {0x0207B0E0, 0x1C04B510}, {0x0207A0FC, 0x1C05B538}, {0x02074470, 0x1C05B570}, {0x02076B94, 0xB091B5F0},
        {0x02025E44, 0x21014B01}, {0x0208C324, 0xF7C4B508}, {0x02027560, 0x21074B01}, {0x0207D990, 0x21034B01},
        {0x0202CD88, 0x21144B01}, {0x02056B24, 0x21054B01}, {0x0200569C, 0x2010B538}, {0x020055D0, 0x1C04B510},
        {0x020556A0, 0x1C05B570},
    },
};

// pret/pokeheartgold's xMAP (US; SoulSilver's ARM9 is the same, every word below checked in both).
const EvoFns HEARTGOLD_EVO = {
    0x02074905,   // SaveArray_Party_Get
    {
        0x020504F1,   // FieldSystem_CreateTask
        0x020553B1,   // sub_020553B0: fade out, then CallTask_LeaveOverworld
        0x02055409,   // sub_02055408: CallTask_RestoreOverworld, then the map name and CallTask_FadeFromBlack
        0x0201A911, 0x0201A9C5, 3, 0x47, 0x30000,   // Heap_Create / Heap_Destroy: HEAP_ID_EVOLUTION in HEAP_ID_3
        0x02075A7D, 0x02075D3D, 0x02075D4D,         // sub_02075A7C (the evolution), sub_02075D3C (done), sub_02075D4C (free)
        0x02074645,   // Party_GetMonByIndex
        0x0206E541,   // GetMonData
        0x02070E35,   // GetMonEvolution
        0x02028EA9, 0x02088289, 0x0202A635, 0x0207879D, 0x0202CF55, 0,   // Save_PlayerData_GetOptionsAddr, sub_02088288,
        // Save_Pokedex_Get, Save_Bag_Get, Save_GameStats_Get; no Poketch
        0x02005FA1, 0x02005EB5, 0x3F3, 0x02004AD9, 0x02055165,   // Sound_Stop, StopBGM(SEQ_GS_SHINKA), Sound_SetScene,
        // FieldBGM_PlayEffectiveForMapHeader
        0x20,
        0, 6, 76,
        1, 4,         // EVOCTX_TRADE; the trade's flags
    },
    {
        {0x02074904, 0x21024B01}, {0x020504F0, 0x1C05B570}, {0x020553B0, 0x49024B01}, {0x02055408, 0x49024B01},
        {0x0201A910, 0x2304B508}, {0x0201A9C4, 0x1C04B510}, {0x02075A7C, 0xB089B5F0}, {0x02075D3C, 0x78003067},
        {0x02075D4C, 0x1C04B510}, {0x02074644, 0x1C05B538}, {0x0206E540, 0x1C05B570}, {0x02070E34, 0xB091B5F0},
        {0x02028EA8, 0x21014B01}, {0x02088288, 0x47702001}, {0x0202A634, 0x21064B01}, {0x0207879C, 0x21034B01},
        {0x0202CF54, 0x21104B01}, {0x02005FA0, 0x2010B538}, {0x02005EB4, 0x1C05B538}, {0x02004AD8, 0x1C05B538},
        {0x02055164, 0x1C05B570},
    },
};

const EvoFns* evoFnsFor(const PkNdsFieldGame* game)
{
    if (!game) return nullptr;
    switch (game->callSet) {
    case 1: return &PLATINUM_EVO;
    case 2: return &HEARTGOLD_EVO;
    default: return nullptr;
    }
}

// TRADE EVOLUTIONS on Gen 5 (stub/pk_nds_evo5.c): the evolution scene's proc as the link trade starts it (its param's
// method class 1, flags 1 / 0), from a GMEVENT of ours that leaves and comes back to the field as the bag's evolution
// does. No decomp: the functions from Ghidra on the carts' own code (the trade's and the bag's callers of the scene).
struct EvoFns5 {
    PkNdsEvo5 evo;   // the helper's functions and constants; the request filled in per job
    struct { u32 addr, word; } code[10];
};

const EvoFns5 BLACK2_EVO = {
    {
        0x02016CB5, 0x02016AED, 0x02016D69,   // GMEVENT_Create, GameSystem_SetEvent, the child event call
        0x021B870D, 0x020193A5, 0x02019495, 0x021B878D,   // fade out, end the field map, start it, fade in
        0x02016A99, 0x11C, 0x021E7D9C,        // the proc call; the evolution scene's overlay 284 and proc table
        0x0201CCF9, 0x02020789, 4,            // the Pokémon getter, the evolution check; the events' heap
        0x1C, 0x194, 0x20, 0xC,               // GameSystem.gameData, GameData's party, GameSystem's field map / proc
        0, 0x4C,                              // personality, is egg
    },
    {
        {0x02016CB4, 0xB082B5F8}, {0x02016AEC, 0x47706181}, {0x02016D68, 0x69006008}, {0x021B870C, 0xB081B518},
        {0x020193A4, 0x4A0AB5F8}, {0x02019494, 0x4A0AB5F8}, {0x021B878C, 0xB081B518}, {0x02016A98, 0x6880B508},
        {0x0201CCF8, 0x1C05B570}, {0x02020788, 0xB093B5F0},
    },
};

// White 2: Black 2's functions found again by their code (the field's 0x38 higher), its scene's proc table 0x40 higher.
const EvoFns5 WHITE2_EVO = {
    {
        0x02016CB5, 0x02016AED, 0x02016D69,
        0x021B8745, 0x020193D1, 0x020194C1, 0x021B87C5,
        0x02016A99, 0x11C, 0x021E7DDC,
        0x0201CD25, 0x020207B5, 4,
        0x1C, 0x194, 0x20, 0xC,
        0, 0x4C,
    },
    {
        {0x02016CB4, 0xB082B5F8}, {0x02016AEC, 0x47706181}, {0x02016D68, 0x69006008}, {0x021B8744, 0xB081B518},
        {0x020193D0, 0x4A0AB5F8}, {0x020194C0, 0x4A0AB5F8}, {0x021B87C4, 0xB081B518}, {0x02016A98, 0x6880B508},
        {0x0201CD24, 0x1C05B570}, {0x020207B4, 0xB093B5F0},
    },
};

// Black: the same functions in Black's build (its fade out takes no fifth argument), the scene overlay 195's proc.
const EvoFns5 BLACK_EVO = {
    {
        0x020122C1, 0x02012109, 0x02012375,
        0x021BE8D5, 0x020144DD, 0x020145CD, 0x021BE92D,
        0x020120B5, 0xC3, 0x02208C40,
        0x02017E1D, 0x0201B2B1, 4,
        0x1C, 0x194, 0x20, 0xC,
        0, 0x4C,
    },
    {
        {0x020122C0, 0xB082B5F8}, {0x02012108, 0x47706181}, {0x02012374, 0x69006008}, {0x021BE8D4, 0x2A09B510},
        {0x020144DC, 0x4A0AB5F8}, {0x020145CC, 0x4A0AB5F8}, {0x021BE92C, 0xB081B518}, {0x020120B4, 0x6880B508},
        {0x02017E1C, 0x1C05B570}, {0x0201B2B0, 0xB093B5F0},
    },
};

// White: Black's, found again by their code (the field's 0x18 higher), its proc table 0x20 higher.
const EvoFns5 WHITE_EVO = {
    {
        0x020122C1, 0x02012109, 0x02012375,
        0x021BE8ED, 0x020144F9, 0x020145E9, 0x021BE945,
        0x020120B5, 0xC3, 0x02208C60,
        0x02017E39, 0x0201B2CD, 4,
        0x1C, 0x194, 0x20, 0xC,
        0, 0x4C,
    },
    {
        {0x020122C0, 0xB082B5F8}, {0x02012108, 0x47706181}, {0x02012374, 0x69006008}, {0x021BE8EC, 0x2A09B510},
        {0x020144F8, 0x4A0AB5F8}, {0x020145E8, 0x4A0AB5F8}, {0x021BE944, 0xB081B518}, {0x020120B4, 0x6880B508},
        {0x02017E38, 0x1C05B570}, {0x0201B2CC, 0xB093B5F0},
    },
};

const EvoFns5* evoFns5For(const PkNdsFieldGame* game)
{
    if (!game || game->gen != 5) return nullptr;
    switch (game->callSet) {
    case 3: return &BLACK_EVO;
    case 4: return &WHITE_EVO;
    case 5: return &BLACK2_EVO;
    case 6: return &WHITE2_EVO;
    default: return nullptr;
    }
}

static_assert(PK_NDS_EVO_ADDR + sizeof(PK_NDS_EVO5_CODE) <= PK_NDS_EVO_ARGS, "the Gen 5 evolution helper runs into its arguments");
static_assert(PK_NDS_EVO_ARGS + sizeof(PkNdsEvo5) <= PK_NDS_SCRIPT_ADDR, "the Gen 5 evolution helper's arguments run into the scripts");

static_assert(PK_NDS_EVO_ADDR >= PK_NDS_JOB + sizeof(PkNdsJob), "the evolution helper runs into the job block");
static_assert(PK_NDS_EVO_ADDR + sizeof(PK_NDS_EVO_CODE) <= PK_NDS_EVO_ARGS, "the evolution helper runs into its arguments");
static_assert(PK_NDS_EVO_ARGS + sizeof(PkNdsEvo) <= PK_NDS_SCRIPT_ADDR, "the evolution helper's arguments run into the scripts");

// The helpers' code ends before their arguments, and the arguments before the ITCM does.
static_assert(sizeof(PK_NDS_ITEM_CODE) <= PK_NDS_ITEM_ARGS - PK_NDS_ITEM_ADDR, "Gen 4's bag helper runs into its arguments");
static_assert(sizeof(PK_NDS_ITEM5_CODE) <= PK_NDS_ITEM_ARGS - PK_NDS_ITEM5_ADDR, "Gen 5's bag helper runs into its arguments");
static_assert(PK_NDS_ITEM_ARGS + sizeof(PkNdsItem5) <= PK_NDS_HM_ADDR && PK_NDS_ITEM_ARGS + sizeof(PkNdsItem) <= PK_NDS_HM_ADDR,
              "the bag helpers' arguments run into NO HMS NEEDED's helper");

struct Pending {
    int seq;
    uint32_t parent;   // the field map's ApplicationManager the hook sits in (Gen 5: the GameSystem)
    uint32_t slot, orig;   // the pointer the hook replaced and its value, put back if the game never calls the hook
    int frames;
    int state;         // 0 waiting, 1 ran, -1 taken out
};

int nextSeq = 1;
PkNdsItem5Effect item5Effect = {};
Pending pending = {};
int lastDone = 0, lastFailed = 0;
uint32_t lastResults[PK_NDS_MAX_RESULTS];

// The language ports' functions (pk_nds_ports_gen.h): HeartGold's and Black's tables with each port's addresses. Black 2's
// structs and script ids are Black's, and every guard word below is the same instruction in all of them.
const CallFns* portFns(int n)
{
    static const std::vector<CallFns> fns = [] {
        std::vector<CallFns> v;
        for (int i = 0; i < PK_NDS_PORTS4_COUNT; i++) {
            const PkNdsPort4& r = PK_NDS_PORTS4[i];
            CallFns f = HEARTGOLD;
            f.scriptContextJump = r.scriptJump;
            f.stringTemplateNew = r.messageFormatNew;
            f.stringInit = r.stringNew;
            f.fieldMovesSetUsableMoves = r.fieldMoveInit;
            f.fieldMovesCheckFly = r.fieldMoveCheckFly;
            f.saveDataGetParty = r.partyGet;
            f.partyGetPokemonBySlotIndex = r.partyGetMon;
            f.flyContextNew = r.flyNew;
            f.fieldMovesFlyTask = r.flyTask;
            f.fieldSystemCreateTask = r.createTask;
            f.scriptEnvironmentNew = r.scriptEnvNew;
            f.createScriptContext = r.createScriptContext;
            f.taskRunScripts = r.taskRunScripts;
            v.push_back(f);
        }
        return v;
    }();
    return n >= 0 && n < (int) fns.size() ? &fns[n] : nullptr;
}

// The Platinum ports' functions: the US rev 1 table (its structs and script ids) with each port's addresses.
const CallFns* portPlatinumFns(int n)
{
    static const std::vector<CallFns> fns = [] {
        std::vector<CallFns> v;
        for (int i = 0; i < PK_NDS_PORTS4P_COUNT; i++) {
            const PkNdsPort4P& r = PK_NDS_PORTS4P[i];
            CallFns f = PLATINUM;
            f.scriptManagerSet = r.scriptManagerSet;
            f.fieldTaskGetEnv = r.fieldTaskGetEnv;
            f.scriptContextCreateAndStart = r.scriptContextCreateAndStart;
            f.scriptContextJump = r.scriptContextJump;
            f.stringTemplateNew = r.stringTemplateNew;
            f.stringInit = r.stringInit;
            f.scriptManagerGetMemberPtr = r.scriptManagerGetMemberPtr;
            f.fieldMovesSetUsableMoves = r.fieldMovesSetUsableMoves;
            f.fieldMovesCheckFly = r.fieldMovesCheckFly;
            f.checkFlyLocationUnlocked = r.checkFlyLocationUnlocked;
            f.saveDataGetParty = r.saveDataGetParty;
            f.partyGetPokemonBySlotIndex = r.partyGetPokemonBySlotIndex;
            f.flyContextNew = r.flyContextNew;
            f.fieldMovesFlyTask = r.fieldMovesFlyTask;
            f.fieldSystemCreateTask = r.fieldSystemCreateTask;
            f.journalCreateEventUsedMove = r.journalCreateEventUsedMove;
            f.journalSaveData = r.journalSaveData;
            v.push_back(f);
        }
        return v;
    }();
    return n >= 0 && n < (int) fns.size() ? &fns[n] : nullptr;
}

const Gen5Fns* portGen5Fns(int n)
{
    static const std::vector<Gen5Fns> fns = [] {
        std::vector<Gen5Fns> v;
        for (int i = 0; i < PK_NDS_PORTS5_COUNT; i++) {
            const PkNdsPort5& r = PK_NDS_PORTS5[i];
            Gen5Fns f = BLACK;
            f.gameSystemPtr = r.gameSystem;
            f.eventCheck = r.eventCheck;
            f.returnZero = r.returnZero;
            f.setEvent = r.setEvent;
            f.scriptEvent = r.scriptEvent;
            f.skillInitCheck = r.skillInitCheck;
            f.skillCanUse = r.skillCanUse;
            f.skillUse = r.skillUse;
            const u32 at[6] = { r.eventCheck, r.scriptEvent, r.scriptEventCheck, r.skillInitCheck, r.skillCanUse, r.skillUse };
            for (int k = 0; k < 6; k++) f.code[k].addr = at[k] & ~1u;
            v.push_back(f);
        }
        return v;
    }();
    return n >= 0 && n < (int) fns.size() ? &fns[n] : nullptr;
}

const CallFns* fnsFor(const PkNdsFieldGame* game)
{
    if (!game || !game->fieldMapMain) return nullptr;
    switch (game->callSet) {
    case 1: return &PLATINUM;
    case 2: return &HEARTGOLD;
    case 7: return &ORIGIN_HEARTGOLD;
    case 8: return &PLATINUM_FR;
    default:
        if (game->callSet >= 100 && game->callSet < 200) return portFns(game->callSet - 100);
        if (game->callSet >= 300 && game->callSet < 400) return portPlatinumFns(game->callSet - 300);
        return nullptr;
    }
}

const Gen5Fns* gen5FnsFor(const PkNdsFieldGame* game)
{
    if (!game || game->gen != 5) return nullptr;
    switch (game->callSet) {
    case 3: return &BLACK;
    case 4: return &WHITE;
    case 5: return &BLACK2;
    case 6: return &WHITE2;
    case 9: return &BLACK_FR;
    case 10: return &WHITE_FR;
    default: return game->callSet >= 200 && game->callSet < 300 ? portGen5Fns(game->callSet - 200) : nullptr;
    }
}

u32 rd32(const PkNdsCallMemory& mem, u32 a)
{
    return (a & 0xFF000000u) == 0x02000000u ? mem.read32(mem.context, a) : 0;
}

void wr32(const PkNdsCallMemory& mem, u32 a, u32 v)
{
    if ((a & 0xFF000000u) == 0x02000000u) mem.write32(mem.context, a, v);
}

bool inMain(u32 p) { return p >= 0x02000000u && p < 0x02400000u; }

void itcmWrite(const PkNdsCallMemory& mem, u32 addr, const void* src, u32 len)
{
    mem.writeItcm(mem.context, addr, src, len);
}

void itcmRead(const PkNdsCallMemory& mem, u32 addr, void* dst, u32 len)
{
    mem.readItcm(mem.context, addr, dst, len);
}

// The field map's ApplicationManager when the field is free to start something (field_system.c's HandleFieldInput:
// no pause, the map running, no task; no child process), else 0.
u32 freeFieldParent(const PkNdsCallMemory& mem, const PkNdsFieldGame* g)
{
    u32 fs = rd32(mem, g->fieldSystemPtr);
    if (!inMain(fs)) return 0;
    u32 pm = rd32(mem, fs + g->offProcessManager);
    if (!inMain(pm)) return 0;
    u32 parent = rd32(mem, pm), child = rd32(mem, pm + 4), pause = rd32(mem, pm + 8);
    if (!inMain(parent) || child || pause) return 0;
    if (rd32(mem, fs + g->offTask) || !rd32(mem, fs + g->offRunningFieldMap)) return 0;
    return parent;
}

// Gen 5: the GameSystem when its field is up and free - the field map's proc running with a field map, no event, the
// field's own event check in place and overlays 10 / 21 holding the code the jobs call - else 0.
u32 gen5FreeField(const PkNdsCallMemory& mem, const PkNdsFieldGame* g, const Gen5Fns& f)
{
    u32 gs = rd32(mem, f.gameSystemPtr);
    if (!inMain(gs)) return 0;
    u32 ps = rd32(mem, gs + g->offProcSys);
    u32 proc = inMain(ps) ? rd32(mem, ps + g->offCurrentProc) : 0;
    if (!inMain(proc) || (int) rd32(mem, proc + g->offProcOverlay) != g->fieldOverlay) return 0;
    if (rd32(mem, gs + f.offEvent) || !inMain(rd32(mem, gs + f.offFieldMap)) || !inMain(rd32(mem, gs + g->offFieldMap))) return 0;
    if (rd32(mem, gs + f.offEventCheck) != f.eventCheck) return 0;
    for (const auto& c : f.code)
        if (c.addr && rd32(mem, c.addr) != c.word) return 0;
    return gs;
}

struct Ops {
    PkNdsOp op[PK_NDS_MAX_OPS];
    int n = 0;
    int add(u32 kind, u32 a = 0, u32 b = 0, u32 c = 0, u32 d = 0)
    {
        op[n] = {kind, {a, b, c, d}};
        return n++;
    }
    static u32 res(int i) { return PK_NDS_RESULT_REF + (u32) i; }
};

// HeartGold / SoulSilver's: the environment and its first context set up as Task_RunScripts' state 0 would, the context
// on our bytes, and the task made - it runs the script from the next frame.
/** ScriptContext.taskman (script.h: 4 bytes, the native and script pointers, a 20-deep stack, the command table, its
 * count and data[4] before it). */
constexpr u32 HG_CONTEXT_TASKMAN = 0x74;

void hgScriptJob(Ops& o, const CallFns& f, u32 fs, const PkNdsFieldGame& g)
{
    int env = o.add(f.scriptEnvironmentNew);
    o.add(PK_NDS_OP_STORE16, Ops::res(env), (u32) f.envScriptNumber, (u32) f.hostScript);
    int ctx = o.add(f.createScriptContext, fs, (u32) f.hostScript);
    o.add(f.scriptContextJump, Ops::res(ctx), PK_NDS_SCRIPT_ADDR);
    o.add(PK_NDS_OP_STORE32, Ops::res(env), (u32) f.envContexts, Ops::res(ctx));
    o.add(PK_NDS_OP_STORE8, Ops::res(env), (u32) f.envContextCount, 1);
    int fmt = o.add(f.stringTemplateNew, 8, 64, (u32) f.heapField2);
    o.add(PK_NDS_OP_STORE32, Ops::res(env), (u32) f.envMsgFmt, Ops::res(fmt));
    int s0 = o.add(f.stringInit, 1024, (u32) f.heapField2);
    o.add(PK_NDS_OP_STORE32, Ops::res(env), (u32) f.envString0, Ops::res(s0));
    int s1 = o.add(f.stringInit, 1024, (u32) f.heapField2);
    o.add(PK_NDS_OP_STORE32, Ops::res(env), (u32) f.envString1, Ops::res(s1));
    o.add(PK_NDS_OP_STORE8, Ops::res(env), (u32) f.envState, 1);
    o.add(f.fieldSystemCreateTask, fs, f.taskRunScripts, Ops::res(env));
    // The context's task manager: the game's own state 0 creates the context once the task runs, so it gets
    // FieldSystem's (SetUpScriptContextForMap); made before the task, ours had none - fine for the PC, but a command that
    // starts a task of its own (a battle: ScrCmd_WildBattle's ctx->taskman) read through NULL.
    int tm = o.add(PK_NDS_OP_LOAD32, fs, (u32) g.offTask);
    o.add(PK_NDS_OP_STORE32, Ops::res(ctx), HG_CONTEXT_TASKMAN, Ops::res(tm));
}

// A field script of ours, from PK_NDS_SCRIPT_ADDR: ScriptManager_Set's own task, then what its first step would do
// (FieldTask_RunScript's state 0: the main context, the string template and buffers, one active context) with the
// context jumped to our script before it reads a byte; state 1 then runs it from the next frame like any script.
void scriptJob(Ops& o, const CallFns& f, u32 fs, const PkNdsFieldGame& g)
{
    o.add(f.scriptManagerSet, fs, f.commonScriptBase, 0);
    int task = o.add(PK_NDS_OP_LOAD32, fs, (u32) g.offTask);
    int sm = o.add(f.fieldTaskGetEnv, Ops::res(task));
    int ctx = o.add(f.scriptContextCreateAndStart, fs, f.commonScriptBase);
    o.add(f.scriptContextJump, Ops::res(ctx), PK_NDS_SCRIPT_ADDR);
    int tmpl = o.add(f.stringTemplateNew, 8, 64, (u32) f.heapField2);
    int msg = o.add(f.stringInit, 1024, (u32) f.heapField2);
    int tmp = o.add(f.stringInit, 1024, (u32) f.heapField2);
    int p = o.add(f.scriptManagerGetMemberPtr, Ops::res(sm), (u32) f.memberMainContext);
    o.add(PK_NDS_OP_STORE32, Ops::res(p), 0, Ops::res(ctx));
    p = o.add(f.scriptManagerGetMemberPtr, Ops::res(sm), (u32) f.memberStrTemplate);
    o.add(PK_NDS_OP_STORE32, Ops::res(p), 0, Ops::res(tmpl));
    p = o.add(f.scriptManagerGetMemberPtr, Ops::res(sm), (u32) f.memberMessageBuf);
    o.add(PK_NDS_OP_STORE32, Ops::res(p), 0, Ops::res(msg));
    p = o.add(f.scriptManagerGetMemberPtr, Ops::res(sm), (u32) f.memberTemporaryBuf);
    o.add(PK_NDS_OP_STORE32, Ops::res(p), 0, Ops::res(tmp));
    p = o.add(f.scriptManagerGetMemberPtr, Ops::res(sm), (u32) f.memberNumActiveContexts);
    o.add(PK_NDS_OP_STORE8, Ops::res(p), 0, 1);
    o.add(PK_NDS_OP_STORE8, Ops::res(sm), (u32) f.offScriptManagerState, 1);
}

}   // namespace

// NO HMS NEEDED (stub/pk_nds_hm.c): the game's two party move checks jump into the helper at the top of the ITCM.
// Each check's routine starts on a word (the jump's ARM half needs it): every one below does. Each
// hook is the routine's first 16 bytes made a Thumb `bx pc; nop` into ARM `mov r3, #which; ldr pc, [pc, #-4]` and the
// helper's address; put in only where the routine's own bytes are, taken out only where ours are.
struct HmSite { u32 addr; u32 which; u32 orig[4]; };
struct HmFns {
    PkNdsHm hm;
    HmSite sites[2];
    struct { u32 addr, word; } code[7];
};

// pret/pokeplatinum's xMAP (US rev 1): Party_HasMonWithMove, ScrCmd_FindPartySlotWithMove and what they call.
const HmFns PLATINUM_HM = {
    {
        0x0207A0F9, 0x0207A0FD, 0x02074471, 0x0207A269,   // Party_GetCurrentCount, _GetPokemonBySlotIndex, Pokemon_GetValue,
        // SaveData_GetParty
        0x0203E839, 0x0203F119, 0x0203F151,               // ScriptContext_ReadHalfWord, FieldSystem_GetVarPointer / _TryGetVar
        0x80, 0x0C, 76, 54, 0xFF, 0,                      // ScriptContext.fieldSystem, FieldSystem.saveData; IS_EGG, MOVE1
        {15, 19, 57, 70, 127, 249, 431, 432, 0},          // CUT, FLY, SURF, STRENGTH, WATERFALL, ROCK SMASH, ROCK CLIMB, DEFOG
    },
    {
        {0x020549A0, PK_NDS_HM_HAS_MOVE, {0x9000B5F8, 0xF0251C0D, 0x1C07FBA7, 0x2F002600}},
        {0x0204D2D8, PK_NDS_HM_SCRIPT, {0xB082B5F8, 0x1C211C04, 0x68093180, 0xF7F19101}},
    },
    {
        {0x0207A0F8, 0x47706840}, {0x0207A0FC, 0x1C05B538}, {0x02074470, 0x1C05B570}, {0x0207A268, 0x21024B01},
        {0x0203E838, 0x1C4B6881}, {0x0203F118, 0x1C05B538}, {0x0203F150, 0x1C0CB510},
    },
};

// pret/pokeheartgold's xMAP (US; SoulSilver's ARM9 is the same): GetIdxOfFirstPartyMonWithMove, ScrCmd_GetPartySlotWithMove.
const HmFns HEARTGOLD_HM = {
    {
        0x02074641, 0x02074645, 0x0206E541, 0x02074905,   // Party_GetCount, Party_GetMonByIndex, GetMonData, SaveArray_Party_Get
        0x0203FE2D, 0x02040375, 0x020403AD,               // ScriptReadHalfword, GetVarPointer, FieldSystem_VarGet
        0x80, 0x0C, 76, 54, 0xFF, 0,
        {15, 19, 57, 70, 127, 249, 250, 431, 0},          // CUT, FLY, SURF, STRENGTH, WATERFALL, ROCK SMASH, WHIRLPOOL, ROCK CLIMB
    },
    {
        {0x020542E8, PK_NDS_HM_HAS_MOVE, {0x9000B5F8, 0xF0201C0D, 0x1C07F9A7, 0x2F002600}},
        {0x0204D3CC, PK_NDS_HM_SCRIPT, {0xB082B5F8, 0x1C211C04, 0x68093180, 0xF7F29101}},
    },
    {
        {0x02074640, 0x47706840}, {0x02074644, 0x1C05B538}, {0x0206E540, 0x1C05B570}, {0x02074904, 0x21024B01},
        {0x0203FE2C, 0x1C4B6881}, {0x02040374, 0x1C05B538}, {0x020403AC, 0x1C0CB510},
    },
};

// Black 2 (no decomp; Ghidra on the cart's own code): the field's (gameData, move) check (Surf, Waterfall from the A
// press) and the script command that writes the slot knowing a move (6: nobody), command 44 of the field scripts' table.
#define GEN5_HMS {15, 19, 57, 70, 127, 291, 0}   // CUT, FLY, SURF, STRENGTH, WATERFALL, DIVE
const HmFns BLACK2_HM = {
    {
        0x0201FDF9, 0x0201FF09, 0x0201CCF9, 0x0201735D,   // the party's count, its Pokemon, the getter, the game data's party
        0x02155175, 0x021548E9, 0x02154911,               // the work's game data, the next variable's pointer, the next value
        0, 0, 76, 54, 6, 1,
        GEN5_HMS,
    },
    {
        {0x02182F14, PK_NDS_HM_HAS_MOVE, {0xB082B5F8, 0xF6941C0D, 0x9000FA1F, 0xFF6AF69C}},
        {0x02156860, PK_NDS_HM_SCRIPT, {0x1C0DB5F8, 0x1C281C04, 0xFC84F7FE, 0xFD76F6C0}},
    },
    {
        {0x02155174, 0x68806A00}, {0x0201735C, 0x00892165}, {0x021548E8, 0x1C0EB570}, {0x02154910, 0x1C0EB570},
        {0x0201FDF8, 0x47706840}, {0x0201FF08, 0x300822DC}, {0x0201CCF8, 0x1C05B570},
    },
};

// White 2: Black 2's, found again by their code.
const HmFns WHITE2_HM = {
    {
        0x0201FE25, 0x0201FF35, 0x0201CD25, 0x0201735D,
        0x021551B5, 0x02154929, 0x02154951,
        0, 0, 76, 54, 6, 1,
        GEN5_HMS,
    },
    {
        {0x02182F54, PK_NDS_HM_HAS_MOVE, {0xB082B5F8, 0xF6941C0D, 0x9000F9FF, 0xFF60F69C}},
        {0x021568A0, PK_NDS_HM_SCRIPT, {0x1C0DB5F8, 0x1C281C04, 0xFC84F7FE, 0xFD56F6C0}},
    },
    {
        {0x021551B4, 0x68806A00}, {0x0201735C, 0x00892165}, {0x02154928, 0x1C0EB570}, {0x02154950, 0x1C0EB570},
        {0x0201FE24, 0x47706840}, {0x0201FF34, 0x300822DC}, {0x0201CD24, 0x1C05B570},
    },
};

// Black and White: Black 2's, found again by their code (the same calls in the same order).
const HmFns BLACK_HM = {
    {
        0x0201AA19, 0x0201AB29, 0x02017E1D, 0x02012935,
        0x0215A5E5, 0x02159AE9, 0x02159B11,
        0, 0, 76, 54, 6, 1,
        GEN5_HMS,
    },
    {
        {0x0218B630, PK_NDS_HM_HAS_MOVE, {0xB082B5F8, 0xF6871C0D, 0x9000F97D, 0xF9ECF68F}},
        {0x0215BAF4, PK_NDS_HM_SCRIPT, {0x1C0DB5F8, 0x1C281C04, 0xFD72F7FE, 0xFF18F6B6}},
    },
    {
        {0x0215A5E4, 0x68806A00}, {0x02012934, 0x00892165}, {0x02159AE8, 0x1C0EB570}, {0x02159B10, 0x1C0EB570},
        {0x0201AA18, 0x47706840}, {0x0201AB28, 0x300822DC}, {0x02017E1C, 0x1C05B570},
    },
};

const HmFns WHITE_HM = {
    {
        0x0201AA35, 0x0201AB45, 0x02017E39, 0x02012935,
        0x0215A605, 0x02159B09, 0x02159B31,
        0, 0, 76, 54, 6, 1,
        GEN5_HMS,
    },
    {
        {0x0218B650, PK_NDS_HM_HAS_MOVE, {0xB082B5F8, 0xF6871C0D, 0x9000F96D, 0xF9EAF68F}},
        {0x0215BB14, PK_NDS_HM_SCRIPT, {0x1C0DB5F8, 0x1C281C04, 0xFD72F7FE, 0xFF08F6B6}},
    },
    {
        {0x0215A604, 0x68806A00}, {0x02012934, 0x00892165}, {0x02159B08, 0x1C0EB570}, {0x02159B30, 0x1C0EB570},
        {0x0201AA34, 0x47706840}, {0x0201AB44, 0x300822DC}, {0x02017E38, 0x1C05B570},
    },
};

static const HmFns* hmFnsFor(const PkNdsFieldGame* game)
{
    if (!game) return nullptr;
    if (game->gen == 5) {
        switch (game->callSet) {
        case 3: return &BLACK_HM;
        case 4: return &WHITE_HM;
        case 5: return &BLACK2_HM;
        case 6: return &WHITE2_HM;
        default: return nullptr;
        }
    }
    switch (game->callSet) {   // Gen 4's gen is 0
    case 1: return &PLATINUM_HM;
    case 2: return &HEARTGOLD_HM;
    default: return nullptr;
    }
}

bool pk_nds_call_hm_supported(const PkNdsFieldGame* game)
{
    return hmFnsFor(game) != nullptr;
}

int pk_nds_call_hm_free(const PkNdsCallMemory& mem, const PkNdsFieldGame* game, bool on, uint32_t changed[8])
{
    const HmFns* h = hmFnsFor(game);
    if (!h) return 0;
    for (const auto& c : h->code)
        if (rd32(mem, c.addr) != c.word) return 0;
    if (on) {
        // the helper first, then the jumps into it
        u8 have[sizeof(PK_NDS_HM_CODE)];
        itcmRead(mem, PK_NDS_HM_ADDR, have, sizeof(have));
        if (memcmp(have, PK_NDS_HM_CODE, sizeof(have))) itcmWrite(mem, PK_NDS_HM_ADDR, PK_NDS_HM_CODE, sizeof(have));
        PkNdsHm args;
        itcmRead(mem, PK_NDS_HM_ARGS, &args, sizeof(args));
        if (memcmp(&args, &h->hm, sizeof(args))) itcmWrite(mem, PK_NDS_HM_ARGS, &h->hm, sizeof(args));
    }
    int n = 0;
    for (const HmSite& site : h->sites) {
        const u32 hook[4] = {0x46C04778u, 0xE3A03000u | site.which, 0xE51FF004u, PK_NDS_HM_ADDR};
        u32 now[4];
        for (int i = 0; i < 4; i++) now[i] = rd32(mem, site.addr + 4 * i);
        const u32* from = on ? site.orig : hook;
        const u32* to = on ? hook : site.orig;
        if (memcmp(now, from, sizeof(now))) continue;
        for (int i = 0; i < 4; i++) {
            mem.write32(mem.context, site.addr + 4 * i, to[i]);
            changed[n++] = site.addr + 4 * i;
        }
    }
    return n;
}

static_assert(PK_NDS_HM_ADDR + sizeof(PK_NDS_HM_CODE) <= PK_NDS_HM_ARGS, "NO HMS NEEDED's helper runs into its arguments");
static_assert(PK_NDS_HM_ARGS + sizeof(PkNdsHm) <= 0x02000000u, "NO HMS NEEDED's arguments run past the ITCM");

bool pk_nds_call_supported(const PkNdsFieldGame* game)
{
    return fnsFor(game) || gen5FnsFor(game);
}

// The ops into the job, the hook into [slot] (holding [orig]): it runs them the next time the game calls it, then goes
// on into [then] (orig, or a stand-in: Gen 5's event jobs). [script] (len bytes) is written to PK_NDS_SCRIPT_ADDR first.
static int postAt(const PkNdsCallMemory& mem, u32 parent, u32 slot, u32 orig, u32 then, Ops& o, const unsigned char* script, u32 scriptLen)
{
    if (rd32(mem, slot) != orig) return 0;
    if (o.n < PK_NDS_MAX_OPS) o.add(PK_NDS_OP_END);

    // The code only when it isn't there already (a state from before, a reset): rewriting it drops the JIT's blocks.
    u8 have[sizeof(PK_NDS_HOOK_CODE)];
    itcmRead(mem, PK_NDS_HOOK_ADDR, have, sizeof(have));
    if (memcmp(have, PK_NDS_HOOK_CODE, sizeof(have))) itcmWrite(mem, PK_NDS_HOOK_ADDR, PK_NDS_HOOK_CODE, sizeof(have));
    if (script) itcmWrite(mem, PK_NDS_SCRIPT_ADDR, script, scriptLen);

    int seq = nextSeq++;
    PkNdsJob job = {};
    job.seq = (u32) seq;
    job.slot = slot;
    job.orig = then;
    memcpy(job.ops, o.op, sizeof(PkNdsOp) * (size_t) o.n);
    itcmWrite(mem, PK_NDS_JOB, &job, sizeof(job));
    wr32(mem, slot, PK_NDS_HOOK_ADDR);
    pending = {seq, parent, slot, orig, 0, 0};
    return seq;
}

// Gen 4: the hook into the field map's main (ApplicationManager.template.main).
static int post(const PkNdsCallMemory& mem, const PkNdsFieldGame* game, u32 parent, Ops& o, const unsigned char* script, u32 scriptLen)
{
    return postAt(mem, parent, parent + 4, game->fieldMapMain, game->fieldMapMain, o, script, scriptLen);
}

// Gen 5's jobs (see Gen5Fns): the hook in the event check's place. [startsEvent]: the ops set an event of their own, so
// the check is stored back first and the hook goes on into returnZero instead of it.
static int postGen5(const PkNdsCallMemory& mem, const Gen5Fns& f, u32 gs, Ops& o, bool startsEvent)
{
    if (!startsEvent) return postAt(mem, gs, gs + f.offEventCheck, f.eventCheck, f.eventCheck, o, nullptr, 0);
    Ops all;
    all.add(PK_NDS_OP_STORE32, gs, (u32) f.offEventCheck, f.eventCheck);
    for (int i = 0; i < o.n && all.n < PK_NDS_MAX_OPS; i++) {
        PkNdsOp op = o.op[i];
        // results move one op down
        for (u32& a : op.a)
            if ((a & ~(u32) (PK_NDS_MAX_RESULTS - 1)) == PK_NDS_RESULT_REF) a += 1;
        all.op[all.n++] = op;
    }
    return postAt(mem, gs, gs + f.offEventCheck, f.eventCheck, f.returnZero, all, nullptr, 0);
}

// Black's actions (pk_nds_call_action's, Gen 5's way). Results are the ops' (postGen5 puts its own op first when the
// job starts an event: the event made is then result 1, 0 when none was - FLY from a place the game says no to).
static int gen5Action(const PkNdsCallMemory& mem, const PkNdsFieldGame* game, const Gen5Fns& f, int action, const uint32_t args[4])
{
    u32 gs = gen5FreeField(mem, game, f);
    if (!gs) return 0;
    u32 fieldMap = rd32(mem, gs + f.offFieldMap);
    Ops o;
    switch (action) {
    case PK_NDS_ACTION_PC: {
        int ev = o.add(f.scriptEvent, gs, f.pcScript, 0, 0);
        o.add(f.setEvent, gs, Ops::res(ev));
        return postGen5(mem, f, gs, o, true);
    }
    case PK_NDS_ACTION_FLY_CHECK: {
        // result 1 = the skill's test (0 = may fly from here), as Gen 4's; which towns the app reads from the flags
        constexpr u32 CHECK = PK_NDS_SKILL_ADDR + offsetof(PkNdsSkill, check);
        o.add(f.skillInitCheck, fieldMap, CHECK);
        o.add(f.skillCanUse, f.skillFly, CHECK);
        return postGen5(mem, f, gs, o, false);
    }
    case PK_NDS_ACTION_FLY: {
        // arg0 = the party slot of the Pokémon that flies, arg1 = the zone to fly to
        PkNdsSkill sk = {};
        sk.initCheck = f.skillInitCheck;
        sk.canUse = f.skillCanUse;
        sk.use = f.skillUse;
        sk.skill = f.skillFly;
        sk.paramSlot = (u16) args[0];
        sk.paramSkill = (u16) f.skillFly;
        sk.paramArg = args[1];
        u8 have[sizeof(PK_NDS_SKILL_CODE)];
        itcmRead(mem, PK_NDS_HELPER_ADDR, have, sizeof(have));
        if (memcmp(have, PK_NDS_SKILL_CODE, sizeof(have))) itcmWrite(mem, PK_NDS_HELPER_ADDR, PK_NDS_SKILL_CODE, sizeof(have));
        itcmWrite(mem, PK_NDS_SKILL_ADDR, &sk, sizeof(sk));
        int ev = o.add(PK_NDS_HELPER_ADDR, PK_NDS_SKILL_ADDR, fieldMap);
        o.add(f.setEvent, gs, Ops::res(ev));
        return postGen5(mem, f, gs, o, true);
    }
    case PK_NDS_ACTION_ITEM: {
        // arg0 = mode | slot << 8 | move << 16, arg1 = the item, arg2 = the personality, arg3 = the party's count; USE's
        // effect from pk_nds_call_item5_effect (the app's, per item)
        const ItemFns5* it = itemFns5For(game);
        if (!it) return 0;
        for (const auto& c : it->code)
            if (c.addr && rd32(mem, c.addr) != c.word) return 0;
        u32 sc = rd32(mem, it->saveControlPtr);
        u32 bs = inMain(sc) ? rd32(mem, sc + it->offBackupSystem) : 0;
        u32 img = inMain(bs) ? rd32(mem, bs + it->offImage) : 0;
        if (!inMain(img)) return 0;
        PkNdsItem5 req = it->item;
        req.party = img + it->offParty;
        req.bag = img + it->offBag;
        req.mode = args[0] & 0xFF;
        req.slot = (args[0] >> 8) & 0xFF;
        req.moveSlot = (args[0] >> 16) & 0xFF;
        req.item = args[1];
        req.personality = args[2];
        req.count = args[3] < 6 ? args[3] : 6;
        req.heal = item5Effect.heal;
        req.healDiv = item5Effect.healDiv;
        req.revive = item5Effect.revive;
        req.statusMask = item5Effect.statusMask;
        req.pp = item5Effect.pp;
        req.ppAll = item5Effect.ppAll;
        u8 have[sizeof(PK_NDS_ITEM5_CODE)];
        itcmRead(mem, PK_NDS_ITEM5_ADDR, have, sizeof(have));
        if (memcmp(have, PK_NDS_ITEM5_CODE, sizeof(have))) itcmWrite(mem, PK_NDS_ITEM5_ADDR, PK_NDS_ITEM5_CODE, sizeof(have));
        itcmWrite(mem, PK_NDS_ITEM_ARGS, &req, sizeof(req));
        // the helper's answer at PK_NDS_ITEM_RESULT and the item held before after it, as Gen 4's (padded to its op)
        for (int i = 0; i < PK_NDS_ITEM_RESULT; i++) o.add(PK_NDS_OP_LOAD32, PK_NDS_ITEM_ARGS, (u32) offsetof(PkNdsItem5, mode));
        o.add(PK_NDS_ITEM5_ADDR, PK_NDS_ITEM_ARGS);
        o.add(PK_NDS_OP_LOAD32, PK_NDS_ITEM_ARGS, (u32) offsetof(PkNdsItem5, out));
        return postGen5(mem, f, gs, o, false);
    }
    case PK_NDS_ACTION_TRADE_EVO: {
        const EvoFns5* ev = evoFns5For(game);
        if (!ev) return 0;
        for (const auto& c : ev->code)
            if (c.addr && rd32(mem, c.addr) != c.word) return 0;
        PkNdsEvo5 req = ev->evo;
        req.gs = gs;
        req.mode = args[0] & 0xFF;
        req.slot = (args[0] >> 8) & 0xFF;
        req.personality = args[2];
        req.count = args[3] < 6 ? args[3] : 6;
        u8 have[sizeof(PK_NDS_EVO5_CODE)];
        itcmRead(mem, PK_NDS_EVO_ADDR, have, sizeof(have));
        if (memcmp(have, PK_NDS_EVO5_CODE, sizeof(have))) itcmWrite(mem, PK_NDS_EVO_ADDR, PK_NDS_EVO5_CODE, sizeof(have));
        itcmWrite(mem, PK_NDS_EVO_ARGS, &req, sizeof(req));
        // START sets its event itself; its answer at PK_NDS_EVO_RESULT (postGen5's own op first then), the targets after
        bool starts = req.mode == PK_NDS_EVO_START;
        for (int i = starts ? 1 : 0; i < PK_NDS_EVO_RESULT; i++) o.add(PK_NDS_OP_LOAD32, PK_NDS_EVO_ARGS, (u32) offsetof(PkNdsEvo5, mode));
        o.add(PK_NDS_EVO_ADDR, PK_NDS_EVO_ARGS);
        for (u32 i = 0; i < 6; i++) o.add(PK_NDS_OP_LOAD32, PK_NDS_EVO_ARGS, (u32) (offsetof(PkNdsEvo5, targets) + 4 * i));
        return postGen5(mem, f, gs, o, starts);
    }
    default:
        return 0;
    }
}

// Scratch for structs a job hands the game (a FieldMoveContext): past the script, in the same unused ITCM.
constexpr u32 SCRATCH = PK_NDS_SCRIPT_ADDR + PK_NDS_SCRIPT_MAX;

int pk_nds_call_action(const PkNdsCallMemory& mem, const PkNdsFieldGame* game, int action, const uint32_t args[4])
{
    if (const Gen5Fns* g5 = gen5FnsFor(game)) return !(pending.seq && pending.state == 0) ? gen5Action(mem, game, *g5, action, args) : 0;
    const CallFns* f = fnsFor(game);
    if (!f || (pending.seq && pending.state == 0)) return 0;
    u32 parent = freeFieldParent(mem, game);
    if (!parent) return 0;
    if (rd32(mem, parent + 4) != game->fieldMapMain) return 0;
    u32 fs = rd32(mem, game->fieldSystemPtr);

    Ops o;
    const unsigned char* script = nullptr;
    u32 scriptLen = 0;
    switch (action) {
    case PK_NDS_ACTION_PC:
        if (f->scriptEnvironmentNew) {
            hgScriptJob(o, *f, fs, *game);
            script = PK_NDS_HEARTGOLD_PC;
            scriptLen = sizeof(PK_NDS_HEARTGOLD_PC);
        } else {
            scriptJob(o, *f, fs, *game);
            script = PK_NDS_PLATINUM_PC;
            scriptLen = sizeof(PK_NDS_PLATINUM_PC);
        }
        break;
    case PK_NDS_ACTION_FLY_CHECK: {
        if (!f->fieldMovesCheckFly) return 0;
        u32 spots = args[0] < PK_NDS_MAX_OPS - 2 ? args[0] : PK_NDS_MAX_OPS - 2;
        o.add(f->fieldMovesSetUsableMoves, fs, SCRATCH);
        o.add(f->fieldMovesCheckFly, SCRATCH);
        if (f->checkFlyLocationUnlocked)
            for (u32 i = 1; i <= spots; i++) o.add(f->checkFlyLocationUnlocked, fs, i);
        break;
    }
    case PK_NDS_ACTION_FLY: {
        if (!f->flyContextNew) return 0;
        int sd = o.add(PK_NDS_OP_LOAD32, fs, (u32) f->offSaveData);
        int party = o.add(f->saveDataGetParty, Ops::res(sd));
        int mon = o.add(f->partyGetPokemonBySlotIndex, Ops::res(party), args[0]);
        int ctx = o.add(f->flyContextNew, (u32) f->heapField2, fs, Ops::res(mon), f->flyHasSlotArg ? args[0] : args[1]);
        if (f->flyHasSlotArg) o.add(PK_NDS_OP_STORE16, Ops::res(ctx), (u32) f->offFlyMap, args[1]);
        o.add(PK_NDS_OP_STORE16, Ops::res(ctx), (u32) f->offFlyX, args[2]);
        o.add(PK_NDS_OP_STORE16, Ops::res(ctx), (u32) f->offFlyZ, args[3]);
        o.add(f->fieldSystemCreateTask, fs, f->fieldMovesFlyTask, Ops::res(ctx));
        if (!f->journalCreateEventUsedMove) break;
        int ev = o.add(f->journalCreateEventUsedMove, (u32) f->fieldMoveFly, args[1], (u32) f->heapField2);
        int j = o.add(PK_NDS_OP_LOAD32, fs, (u32) f->offJournalEntry);
        o.add(f->journalSaveData, Ops::res(j), Ops::res(ev), (u32) f->journalLocation);
        break;
    }
    case PK_NDS_ACTION_ITEM: {
        const ItemFns* it = itemFnsFor(game);
        if (!it) return 0;
        for (const auto& c : it->code)
            if (c.addr && rd32(mem, c.addr) != c.word) return 0;
        PkNdsItem req = it->item;
        req.mode = args[0] & 0xFF;
        req.slot = (args[0] >> 8) & 0xFF;
        req.moveSlot = (args[0] >> 16) & 0xFF;
        req.item = args[1];
        req.personality = args[2];
        req.count = args[3] < 6 ? args[3] : 6;
        u8 have[sizeof(PK_NDS_ITEM_CODE)];
        itcmRead(mem, PK_NDS_ITEM_ADDR, have, sizeof(have));
        if (memcmp(have, PK_NDS_ITEM_CODE, sizeof(have))) itcmWrite(mem, PK_NDS_ITEM_ADDR, PK_NDS_ITEM_CODE, sizeof(have));
        itcmWrite(mem, PK_NDS_ITEM_ARGS, &req, sizeof(req));
        int sd = o.add(PK_NDS_OP_LOAD32, fs, (u32) f->offSaveData);
        int party = o.add(f->saveDataGetParty, Ops::res(sd));
        int bag = o.add(it->saveDataGetBag, Ops::res(sd));
        int loc = o.add(PK_NDS_OP_LOAD32, fs, (u32) it->offLocation);
        int hdr = o.add(PK_NDS_OP_LOAD32, Ops::res(loc), 0);
        int label = o.add(it->mapLabel, Ops::res(hdr));
        o.add(PK_NDS_OP_STORE32, PK_NDS_ITEM_ARGS, (u32) offsetof(PkNdsItem, party), Ops::res(party));
        o.add(PK_NDS_OP_STORE32, PK_NDS_ITEM_ARGS, (u32) offsetof(PkNdsItem, bag), Ops::res(bag));
        o.add(PK_NDS_OP_STORE32, PK_NDS_ITEM_ARGS, (u32) offsetof(PkNdsItem, location), Ops::res(label));
        int answer = o.add(PK_NDS_ITEM_ADDR, PK_NDS_ITEM_ARGS);
        o.add(PK_NDS_OP_LOAD32, PK_NDS_ITEM_ARGS, (u32) offsetof(PkNdsItem, out));
        if (answer != PK_NDS_ITEM_RESULT) return 0;
        break;
    }
    case PK_NDS_ACTION_TRADE_EVO: {
        const EvoFns* ev = evoFnsFor(game);
        if (!ev) return 0;
        for (const auto& c : ev->code)
            if (c.addr && rd32(mem, c.addr) != c.word) return 0;
        PkNdsEvo req = ev->evo;
        req.fieldSystem = fs;
        req.mode = args[0] & 0xFF;
        req.slot = (args[0] >> 8) & 0xFF;
        req.personality = args[2];
        req.count = args[3] < 6 ? args[3] : 6;
        req.state = PK_NDS_EVO_IDLE;   // the field is free here, so none of ours is playing
        u8 have[sizeof(PK_NDS_EVO_CODE)];
        itcmRead(mem, PK_NDS_EVO_ADDR, have, sizeof(have));
        if (memcmp(have, PK_NDS_EVO_CODE, sizeof(have))) itcmWrite(mem, PK_NDS_EVO_ADDR, PK_NDS_EVO_CODE, sizeof(have));
        itcmWrite(mem, PK_NDS_EVO_ARGS, &req, sizeof(req));
        int sd = o.add(PK_NDS_OP_LOAD32, fs, (u32) f->offSaveData);
        int party = o.add(ev->getParty, Ops::res(sd));
        o.add(PK_NDS_OP_STORE32, PK_NDS_EVO_ARGS, (u32) offsetof(PkNdsEvo, saveData), Ops::res(sd));
        o.add(PK_NDS_OP_STORE32, PK_NDS_EVO_ARGS, (u32) offsetof(PkNdsEvo, party), Ops::res(party));
        int answer = o.add(PK_NDS_EVO_ADDR, PK_NDS_EVO_ARGS);
        for (u32 i = 0; i < 6; i++) o.add(PK_NDS_OP_LOAD32, PK_NDS_EVO_ARGS, (u32) (offsetof(PkNdsEvo, targets) + 4 * i));
        if (answer != PK_NDS_EVO_RESULT) return 0;
        break;
    }
    default:
        return 0;
    }
    return post(mem, game, parent, o, script, scriptLen);
}

int pk_nds_call_status(int seq)
{
    if (seq == pending.seq) return pending.state;
    if (seq == lastDone) return 1;
    return -1;
}

bool pk_nds_call_results(int seq, uint32_t* out)
{
    if (seq != lastDone) return false;
    memcpy(out, lastResults, sizeof(lastResults));
    return true;
}

void pk_nds_call_frame(const PkNdsCallMemory& mem, const PkNdsFieldGame* game)
{
    if (!pending.seq || pending.state != 0) return;
    u32 done;
    itcmRead(mem, PK_NDS_JOB + offsetof(PkNdsJob, done), &done, 4);
    if (done == (u32) pending.seq) {
        pending.state = 1;
        lastDone = pending.seq;
        itcmRead(mem, PK_NDS_JOB + offsetof(PkNdsJob, res), lastResults, sizeof(lastResults));
        return;
    }
    if (++pending.frames < 8) return;
    // Not called: put the field map's main back if it's still the same manager holding our hook (Gen 5: the event
    // check, if it's still the same GameSystem's).
    if (const Gen5Fns* g5 = gen5FnsFor(game)) {
        if (rd32(mem, g5->gameSystemPtr) == pending.parent && rd32(mem, pending.slot) == PK_NDS_HOOK_ADDR)
            wr32(mem, pending.slot, pending.orig);
        pending.state = -1;
        lastFailed = pending.seq;
        return;
    }
    u32 fs = game ? rd32(mem, game->fieldSystemPtr) : 0;
    u32 pm = inMain(fs) ? rd32(mem, fs + game->offProcessManager) : 0;
    if (inMain(pm) && rd32(mem, pm) == pending.parent && rd32(mem, pending.slot) == PK_NDS_HOOK_ADDR)
        wr32(mem, pending.slot, pending.orig);
    pending.state = -1;
    lastFailed = pending.seq;
}

int pk_nds_call_ops(const PkNdsCallMemory& mem, const PkNdsFieldGame* game, const PkNdsOp* ops, int n)
{
    if (const Gen5Fns* g5 = gen5FnsFor(game)) {
        if ((pending.seq && pending.state == 0) || n <= 0 || n > PK_NDS_MAX_OPS) return 0;
        u32 gs = gen5FreeField(mem, game, *g5);
        if (!gs) return 0;
        Ops o;
        for (int i = 0; i < n; i++) o.op[o.n++] = ops[i];
        return postGen5(mem, *g5, gs, o, false);
    }
    if (!fnsFor(game) || (pending.seq && pending.state == 0) || n <= 0 || n > PK_NDS_MAX_OPS) return 0;
    u32 parent = freeFieldParent(mem, game);
    if (!parent) return 0;
    Ops o;
    for (int i = 0; i < n; i++) o.op[o.n++] = ops[i];
    return post(mem, game, parent, o, nullptr, 0);
}

int pk_nds_call_script(const PkNdsCallMemory& mem, const PkNdsFieldGame* game, const uint8_t* bytes, uint32_t len)
{
    const CallFns* f = fnsFor(game);
    if (!f || (pending.seq && pending.state == 0) || !bytes || len == 0 || len > PK_NDS_SCRIPT_MAX) return 0;
    u32 parent = freeFieldParent(mem, game);
    if (!parent) return 0;
    Ops o;
    if (f->scriptEnvironmentNew) hgScriptJob(o, *f, rd32(mem, game->fieldSystemPtr), *game);
    else scriptJob(o, *f, rd32(mem, game->fieldSystemPtr), *game);
    return post(mem, game, parent, o, bytes, len);
}

void pk_nds_call_itcm_write(const PkNdsCallMemory& mem, uint32_t addr, const void* src, uint32_t len)
{
    if (addr >= PK_NDS_SCRIPT_ADDR && addr + len <= 0x02000000u) itcmWrite(mem, addr, src, len);
}

void pk_nds_call_reset(void)
{
    pending = {};
}

void pk_nds_call_item5_effect(const PkNdsItem5Effect& effect)
{
    item5Effect = effect;
}
