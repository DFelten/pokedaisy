// The PC games' save states (mkxp-z): the core's state is the whole Ruby heap, a fixed 512 MB
// (mkxp-z_saveStateSize), so it's written zstd-compressed (~105 MB for Infinite Fusion) when the host was built with
// libzstd, else as it is. Reading takes either: a zstd frame by its magic, anything else as raw bytes.
// docs/specs/pc-fan-games.md.
#pragma once
#include <stdbool.h>
#include <stddef.h>

// Written beside, then renamed over (a crash leaves the old state or the new one).
bool pc_state_write(const char *path, const void *data, size_t size);
// The state's bytes (malloc'd), or NULL. max = the largest state taken, compressed or not.
unsigned char *pc_state_read(const char *path, size_t *size, size_t max);
// Whether states are compressed in this build.
bool pc_state_compressed(void);

// The games' own saves (Essentials writes them where the core says: SAVES/mkxp-z/Saves/<game>/): before a PC game
// opens, each game's folder is copied to BACKUPS/mkxp-z/<game>/<YYYYmmdd-HHMMSS>/ when its files differ from the newest
// copy, the newest 10 kept, like the battery saves' backups. False = a copy failed (the game stays closed).
bool pc_saves_backup(const char *savesDir, const char *backupsDir);
