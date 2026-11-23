/* ROMs in .zip files (see rom_zip.h): stored and deflated entries, no encryption, no zip64 (a ROM is at most 512 MB). */
#include "rom_zip.h"

#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>

#include "pd_fs.h"
#include "zlib.h"

#define MIN_ROM 0x200u
#define MAX_ROM (512u << 20)
#define MAX_DIRECTORY (4u << 20)
#define MAX_TAIL 65557L // the end record and the longest comment
#define CHUNK 4096u // the list reads the first few hundred bytes of every ROM

struct entry {
  char ext[5];
  uint32_t crc, packed, size, offset;
  unsigned method;
};

static unsigned le16(const unsigned char *p) { return (unsigned)p[0] | (unsigned)p[1] << 8; }
static uint32_t le32(const unsigned char *p) { return (uint32_t)le16(p) | (uint32_t)le16(p + 2) << 16; }

bool rom_zipped(const char *path) {
  const char *ext = strrchr(pd_base_name(path), '.');
  return ext && !strcasecmp(ext, ".zip");
}

// A ROM's rank by its extension, 0 = not one.
static int rank_of(const char *name) {
  const char *ext = strrchr(name, '.');
  if (!ext || ext == name)
    return 0;
  if (!strcasecmp(ext, ".gba"))
    return 3;
  if (!strcasecmp(ext, ".gb") || !strcasecmp(ext, ".gbc"))
    return 2;
  return !strcasecmp(ext, ".nds") ? 1 : 0;
}

// The zip's central directory: its bytes (free them) and how many entries.
static unsigned char *directory(FILE *f, uint32_t *size, unsigned *count) {
  if (fseek(f, 0, SEEK_END))
    return NULL;
  long len = ftell(f);
  unsigned char *buf = malloc(MAX_TAIL), *dir = NULL;
  // The end record is in the last KB, unless a long comment follows it.
  for (long tail = 1024; buf && !dir; tail = MAX_TAIL) {
    long n = len < tail ? len : tail;
    if (n < 22 || fseek(f, len - n, SEEK_SET) || fread(buf, 1, (size_t)n, f) != (size_t)n)
      break;
    for (long i = n - 22; i >= 0 && !dir; i--) {
      if (le32(buf + i) != 0x06054b50u)
        continue;
      uint32_t bytes = le32(buf + i + 12), at = le32(buf + i + 16);
      if (bytes > MAX_DIRECTORY || at > (uint32_t)len || bytes > (uint32_t)len - at)
        continue;
      dir = malloc(bytes ? bytes : 1);
      if (dir && (fseek(f, (long)at, SEEK_SET) || fread(dir, 1, bytes, f) != bytes)) {
        free(dir);
        dir = NULL;
      }
      *size = bytes;
      *count = le16(buf + i + 10);
    }
    if (n == len || tail == MAX_TAIL)
      break;
  }
  free(buf);
  return dir;
}

/* The ROM in the zip: a .gba before a .gb / .gbc before a .nds, then the biggest (the app's order). macOS's __MACOSX/
 * and ._ files and anything too small or big to be a ROM are skipped. */
static bool find(FILE *f, struct entry *best) {
  uint32_t size = 0;
  unsigned count = 0;
  unsigned char *dir = directory(f, &size, &count);
  int bestRank = 0;
  uint32_t p = 0;
  for (unsigned i = 0; dir && i < count && size - p >= 46 && le32(dir + p) == 0x02014b50u; i++) {
    const unsigned char *h = dir + p;
    uint32_t nameLen = le16(h + 28), next = 46 + nameLen + le16(h + 30) + le16(h + 32);
    if (next > size - p)
      break;
    p += next;
    char full[512];
    if (nameLen >= sizeof full || memchr(h + 46, 0, nameLen))
      continue;
    memcpy(full, h + 46, nameLen);
    full[nameLen] = 0;
    struct entry e = {.crc = le32(h + 16), .packed = le32(h + 20), .size = le32(h + 24), .offset = le32(h + 42),
                      .method = le16(h + 10)};
    const char *name = pd_base_name(full);
    int rank = rank_of(name);
    if (!rank || name[0] == '.' || !strncmp(full, "__MACOSX/", 9) || e.size < MIN_ROM || e.size > MAX_ROM ||
        (le16(h + 8) & 1) || (e.method != 0 && e.method != Z_DEFLATED) || (e.method == 0 && e.packed != e.size))
      continue;
    if (rank < bestRank || (rank == bestRank && e.size <= best->size))
      continue;
    snprintf(e.ext, sizeof e.ext, "%s", strrchr(name, '.'));
    for (char *c = e.ext; *c; c++)
      *c |= 0x20; // lower case
    *best = e;
    bestRank = rank;
  }
  free(dir);
  return bestRank > 0;
}

// Sits at the entry's first packed byte.
static bool seek_data(FILE *f, const struct entry *e) {
  unsigned char h[30];
  return e->offset <= 0x7fffffffu - 30u - 2u * 0xffffu && !fseek(f, (long)e->offset, SEEK_SET) &&
         fread(h, 1, sizeof h, f) == sizeof h && le32(h) == 0x04034b50u &&
         !fseek(f, (long)(le16(h + 26) + le16(h + 28)), SEEK_CUR);
}

static bool read_entry(FILE *f, const struct entry *e, bool (*take)(void *, const void *, size_t), void *user) {
  if (!seek_data(f, e))
    return false;
  unsigned char *in = malloc(CHUNK * 2), *out = in ? in + CHUNK : NULL;
  z_stream z = {0};
  bool packed = e->method == Z_DEFLATED, ok = in != NULL, end = false;
  if (ok && packed && inflateInit2(&z, -MAX_WBITS) != Z_OK) {
    free(in);
    return false;
  }
  uint32_t left = e->packed, done = 0;
  uLong crc = crc32(0, NULL, 0);
  while (ok && !end) {
    size_t n;
    const unsigned char *data = out;
    if (!packed) {
      n = left < CHUNK ? left : CHUNK;
      ok = fread(in, 1, n, f) == n;
      data = in;
      left -= (uint32_t)n;
      end = !left;
    } else {
      if (!z.avail_in && left) {
        size_t want = left < CHUNK ? left : CHUNK;
        if (fread(in, 1, want, f) != want)
          break;
        left -= (uint32_t)want;
        z.next_in = in;
        z.avail_in = (uInt)want;
      }
      z.next_out = out;
      z.avail_out = CHUNK;
      int r = inflate(&z, Z_NO_FLUSH);
      n = CHUNK - z.avail_out;
      end = r == Z_STREAM_END;
      ok = end || (r == Z_OK && (n || z.avail_in || left));
    }
    ok = ok && n <= e->size - done; // no more than the zip said
    if (!ok)
      break;
    crc = crc32(crc, data, (uInt)n);
    done += (uint32_t)n;
    ok = !n || take(user, data, n);
  }
  if (packed)
    inflateEnd(&z);
  free(in);
  return ok && end && done == e->size && (uint32_t)crc == e->crc;
}

// The zip's name with the ROM's extension.
static bool named(const char *path, const struct entry *e, char *out, size_t size) {
  const char *name = pd_base_name(path);
  return snprintf(out, size, "%.*s%s", (int)(strrchr(name, '.') - name), name, e->ext) < (int)size;
}

bool rom_name(const char *path, char *out, size_t size) {
  if (!rom_zipped(path))
    return snprintf(out, size, "%s", pd_base_name(path)) < (int)size;
  struct entry e = {0};
  FILE *f = fopen(path, "rb");
  bool ok = f && find(f, &e) && named(path, &e, out, size);
  if (f)
    fclose(f);
  return ok;
}

bool rom_read(const char *path, bool (*take)(void *, const void *, size_t), void *user) {
  FILE *f = fopen(path, "rb");
  if (!f)
    return false;
  bool ok;
  if (rom_zipped(path)) {
    struct entry e = {0};
    ok = find(f, &e) && read_entry(f, &e, take, user);
  } else {
    unsigned char *buf = malloc(CHUNK);
    size_t n;
    ok = buf != NULL;
    while (ok && (n = fread(buf, 1, CHUNK, f)) > 0)
      ok = take(user, buf, n);
    ok = ok && !ferror(f);
    free(buf);
  }
  fclose(f);
  return ok;
}

struct head {
  unsigned char *out;
  size_t size, done;
};
static bool head_take(void *user, const void *data, size_t n) {
  struct head *h = user;
  size_t k = h->size - h->done < n ? h->size - h->done : n;
  memcpy(h->out + h->done, data, k);
  h->done += k;
  return h->done < h->size;
}
size_t rom_head(const char *path, void *out, size_t n) {
  struct head h = {out, n, 0};
  rom_read(path, head_take, &h);
  return h.done;
}

static bool file_take(void *user, const void *data, size_t n) { return fwrite(data, 1, n, user) == n; }

// Every copy in DATA/unzipped/<CRC32>/ but this one.
static void prune(const char *dir, const char *id, const char *rom) {
  DIR *d = opendir(dir);
  struct dirent *e;
  while (d && (e = readdir(d))) {
    if (strlen(e->d_name) != 8 || strspn(e->d_name, "0123456789abcdef") != 8)
      continue;
    char sub[PATH_MAX + 16], file[PATH_MAX + 16 + 260];
    snprintf(sub, sizeof sub, "%s/%.8s", dir, e->d_name);
    DIR *s = opendir(sub);
    struct dirent *x;
    while (s && (x = readdir(s)))
      if (strcmp(x->d_name, ".") && strcmp(x->d_name, "..") && (strcmp(e->d_name, id) || strcmp(x->d_name, rom)) &&
          snprintf(file, sizeof file, "%s/%s", sub, x->d_name) < (int)sizeof file)
        remove(file);
    if (s)
      closedir(s);
    rmdir(sub); // not this one's, which still has it
  }
  if (d)
    closedir(d);
}

bool rom_unzip(const char *path, const char *dataDir, char *out, size_t size) {
  struct entry e = {0};
  FILE *f = fopen(path, "rb");
  if (!f)
    return false;
  char dir[PATH_MAX], folder[PATH_MAX + 16], tmp[PATH_MAX + 300], id[9], name[256];
  bool ok = find(f, &e) && named(path, &e, name, sizeof name) &&
            snprintf(dir, sizeof dir, "%s/unzipped", dataDir) < (int)sizeof dir;
  snprintf(id, sizeof id, "%08x", (unsigned)e.crc);
  ok = ok && snprintf(folder, sizeof folder, "%s/%s", dir, id) < (int)sizeof folder &&
       snprintf(out, size, "%s/%s", folder, name) < (int)size;
  if (!ok) {
    fclose(f);
    return false;
  }
  prune(dir, id, name);
  // Named by its CRC32, so a copy that's there with the right size is this ROM.
  FILE *have = fopen(out, "rb");
  bool whole = have && !fseek(have, 0, SEEK_END) && ftell(have) == (long)e.size;
  if (have)
    fclose(have);
  if (!whole) {
    pd_mkdir(dir);
    pd_mkdir(folder);
    snprintf(tmp, sizeof tmp, "%s/rom.part", folder);
    FILE *w = fopen(tmp, "wb");
    ok = w && read_entry(f, &e, file_take, w) && !fflush(w) && !fsync(fileno(w));
    ok = w && !fclose(w) && ok && !pd_rename(tmp, out);
    if (!ok)
      remove(tmp);
  }
  fclose(f);
  // The copy is alone in its folder: an empty .zip beside it tells pd_save_path what it can't see there, another ROM
  // beside the zip with its base name.
  if (ok && pd_stem_shared(path)) {
    snprintf(tmp, sizeof tmp, "%s/%s", folder, pd_base_name(path));
    FILE *mark = fopen(tmp, "wb");
    if (mark)
      fclose(mark);
  }
  return ok;
}
