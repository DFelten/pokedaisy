// The PC games' companion mailbox (pc_games/pokedaisy_companion.rb): a buffer in the mkxp-z core's Ruby heap
// (RETRO_MEMORY_SYSTEM_RAM) that the preload script rewrites a few times a second. Found once by its magic,
// then read in place; scanned again only if the magic is gone (a load, a new game).
#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// The latest whole snapshot (JSON, not NUL-terminated) if it changed since the last call, else NULL.
// mem / size: the core's SYSTEM_RAM. Scans at most every 2 s while nothing is found.
const char *pc_mailbox_read(const uint8_t *mem, size_t size, size_t *len, uint32_t *seq);
void pc_mailbox_reset(void);
