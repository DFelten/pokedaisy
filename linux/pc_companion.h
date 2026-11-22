// The PC games' companion (Pokemon Essentials on the mkxp-z core): PARTY, BAG and TRAINER, drawn by the shared
// companion pieces (3ds/core's pd_ui_party_view / pd_ui_summary_page / pd_ui_bag_view, as the DS companion does) from
// the snapshot pc_games/pokedaisy_companion.rb publishes. Every name is the game's own; icons are read from the game's
// folder. docs/specs/pc-fan-games.md.
#pragma once
#include <stdbool.h>
#include <stddef.h>

struct pd_canvas;

enum pc_key { PC_KEY_UP, PC_KEY_DOWN, PC_KEY_LEFT, PC_KEY_RIGHT, PC_KEY_A, PC_KEY_B, PC_KEY_X, PC_KEY_L, PC_KEY_R };
enum pc_tab { PC_TAB_PARTY, PC_TAB_BAG, PC_TAB_TRAINER, PC_TAB_COUNT };

// The game's folder (icons' paths are relative to it). Clears what was shown.
void pc_companion_start(const char* gameDir);
// A new snapshot (JSON). False if it didn't parse (the last one stays).
bool pc_companion_update(const char* json, size_t len);
bool pc_companion_has_data(void);
void pc_companion_draw(struct pd_canvas* c);
// A button while the companion is open. False = B with nothing left to go back from: close it.
bool pc_companion_key(enum pc_key k);
// For the render tool: which page shows.
void pc_companion_show(enum pc_tab tab, int selected, bool summary, int summaryPage, int pocket);
