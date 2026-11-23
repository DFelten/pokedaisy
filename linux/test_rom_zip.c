/* test_rom_zip.py's way in: each command prints one line. */
#include "rom_zip.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "zlib.h"

static bool crc_take(void *user, const void *data, size_t n) {
  uLong *crc = user;
  *crc = crc32(*crc, data, (uInt)n);
  return true;
}

int main(int argc, char **argv) {
  if (argc < 3)
    return 2;
  const char *cmd = argv[1], *path = argv[2];
  char out[8192];
  if (!strcmp(cmd, "name"))
    puts(rom_name(path, out, sizeof out) ? out : "-");
  else if (!strcmp(cmd, "crc")) {
    uLong crc = crc32(0, NULL, 0);
    if (rom_read(path, crc_take, &crc))
      printf("%08lx\n", crc);
    else
      puts("-");
  } else if (!strcmp(cmd, "head")) {
    unsigned char h[0x150];
    size_t n = rom_head(path, h, sizeof h);
    printf("%zu %08lx\n", n, crc32(crc32(0, NULL, 0), h, (uInt)n));
  } else if (!strcmp(cmd, "unzip") && argc > 3)
    puts(rom_unzip(path, argv[3], out, sizeof out) ? out : "-");
  else
    return 2;
  return 0;
}
