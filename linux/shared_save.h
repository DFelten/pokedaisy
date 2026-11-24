/* Saves shared with the firmware's emulators (SETTINGS > SHARED SAVES): a game in POCKET_ROM_DIRS uses RetroArch's save or
 * a standalone DS emulator's. POCKET_SAVE_DIRS, a save folder (or none) for each, goes before RetroArch's settings. */
#ifndef POCKET_SHARED_SAVE_H
#define POCKET_SHARED_SAVE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Whether there's a folder to share with. */
bool shared_saves_find(void);
void shared_saves_use(bool on);

/* The save of the ROM (or zip) at [romPath], named [romName], there or not yet. 1 = in [out], 0 = not shared, -1 = it
 * can't be used. */
int shared_save_path(const char *romPath, const char *romName, char *out, size_t size);

/* RetroArch's clock file for the save, Gambatte's or mGBA's, as the core's clock memory at the time [now]. */
bool shared_clock_load(const char *savePath, unsigned char *rtc, size_t n, int64_t now);
/* That file's new bytes for [rtc] in [out] (n at least) and its path; 0 = leave it. */
size_t shared_clock_image(const char *savePath, const unsigned char *rtc, size_t n, unsigned char *out, char *path,
                          size_t size);

#endif
