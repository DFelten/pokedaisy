/* ROMs in .zip files (the app's RomArchive): the list reads them in place, playing one unpacks it under the name its saves
 * go by (rom_save_name), which states go by too. All but rom_unzip take a loose ROM too. */
#ifndef POCKET_ROM_ZIP_H
#define POCKET_ROM_ZIP_H

#include <stdbool.h>
#include <stddef.h>

bool rom_zipped(const char *path);
/* [path]'s file name, a zip's with its ROM's extension: the library's name for it. false = a zip without a ROM. */
bool rom_name(const char *path, char *out, size_t size);
/* The ROM file name [path]'s saves go by (the app's RomArchive.saveNames): a loose ROM's own; a zip's ROM inside's, unless
 * only the zip's own name (Pack.zip -> Pack.gba) has a save, PokeDaisy's in [savesDir] (NULL: not looked at) or a shared
 * one. */
bool rom_save_name(const char *path, const char *savesDir, char *out, size_t size);
/* The ROM's bytes in pieces; false = unreadable, or [take] returned false. */
bool rom_read(const char *path, bool (*take)(void *user, const void *data, size_t n), void *user);
/* Its first [n] bytes; how many there were. */
size_t rom_head(const char *path, void *out, size_t n);
/* Unpacks to [dataDir]/unzipped, which keeps the last game only, under rom_save_name's name; its path in [out].
 * [unpacking] (or NULL) is called first when there's no copy yet, which takes a while for a big ROM. */
bool rom_unzip(const char *path, const char *dataDir, const char *savesDir, void (*unpacking)(void), char *out,
               size_t size);

#endif
