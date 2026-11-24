/* ROMs in .zip files (the app's RomArchive): the list reads them in place, playing one unpacks it under the zip's name,
 * which saves and states go by. All but rom_unzip take a loose ROM too. */
#ifndef POCKET_ROM_ZIP_H
#define POCKET_ROM_ZIP_H

#include <stdbool.h>
#include <stddef.h>

bool rom_zipped(const char *path);
/* [path]'s file name, a zip's with its ROM's extension. false = a zip without a ROM. */
bool rom_name(const char *path, char *out, size_t size);
/* The ROM's bytes in pieces; false = unreadable, or [take] returned false. */
bool rom_read(const char *path, bool (*take)(void *user, const void *data, size_t n), void *user);
/* Its first [n] bytes; how many there were. */
size_t rom_head(const char *path, void *out, size_t n);
/* Unpacks to [dataDir]/unzipped, which keeps the last game only; its path in [out]. */
bool rom_unzip(const char *path, const char *dataDir, char *out, size_t size);

#endif
