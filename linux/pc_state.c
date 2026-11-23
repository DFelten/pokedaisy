// See pc_state.h.
#include "pc_state.h"
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <errno.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#ifdef POCKET_ZSTD
#include <zstd.h>
#endif

#define CHUNK (1u << 20)

bool pc_state_compressed(void) {
#ifdef POCKET_ZSTD
  return true;
#else
  return false;
#endif
}

#ifdef POCKET_ZSTD
// Level 1 and two worker threads: Infinite Fusion's 512 MB came to 107 MB in 0.9 s on one x86 core (gzip -1: 99 MB in
// 3.8 s). A libzstd built without threads refuses the workers and compresses on this thread.
static bool write_zstd(FILE *f, const void *data, size_t size) {
  ZSTD_CCtx *cc = ZSTD_createCCtx();
  void *out = malloc(CHUNK);
  bool ok = cc && out;
  if (ok) {
    ZSTD_CCtx_setParameter(cc, ZSTD_c_compressionLevel, 1);
    ZSTD_CCtx_setParameter(cc, ZSTD_c_nbWorkers, 2);
    ZSTD_CCtx_setPledgedSrcSize(cc, size); // the size goes in the frame's header: the reader allocates once
    ZSTD_inBuffer in = {data, size, 0};
    for (;;) {
      ZSTD_outBuffer o = {out, CHUNK, 0};
      size_t left = ZSTD_compressStream2(cc, &o, &in, ZSTD_e_end);
      if (ZSTD_isError(left) || fwrite(out, 1, o.pos, f) != o.pos) {
        ok = false;
        break;
      }
      if (!left)
        break;
    }
  }
  free(out);
  ZSTD_freeCCtx(cc);
  return ok;
}

static unsigned char *read_zstd(FILE *f, size_t *size, size_t max) {
  unsigned char head[32];
  size_t hn = fread(head, 1, sizeof head, f);
  unsigned long long n = ZSTD_getFrameContentSize(head, hn);
  if (n == ZSTD_CONTENTSIZE_UNKNOWN || n == ZSTD_CONTENTSIZE_ERROR || n == 0 || n > max)
    return NULL;
  unsigned char *d = malloc((size_t)n), *chunk = malloc(CHUNK);
  ZSTD_DCtx *dc = ZSTD_createDCtx();
  ZSTD_outBuffer o = {d, (size_t)n, 0};
  bool ok = d && chunk && dc;
  memcpy(chunk, head, hn);
  size_t got = hn;
  bool done = false;
  while (ok && !done) {
    ZSTD_inBuffer in = {chunk, got, 0};
    while (in.pos < in.size) {
      size_t before = in.pos + o.pos, r = ZSTD_decompressStream(dc, &o, &in);
      if (!r) {
        done = true; // the frame's end
        break;
      }
      if (ZSTD_isError(r) || in.pos + o.pos == before) {
        ok = false; // broken, or more than the header said (no room left, nothing taken)
        break;
      }
    }
    if (!ok || done)
      break;
    got = fread(chunk, 1, CHUNK, f);
    if (!got)
      ok = false; // the file ended before the state did
  }
  ZSTD_freeDCtx(dc);
  free(chunk);
  if (!ok || o.pos != o.size) {
    free(d);
    return NULL;
  }
  *size = (size_t)n;
  return d;
}
#endif

bool pc_state_write(const char *path, const void *data, size_t size) {
  char tmp[4096];
  if ((size_t)snprintf(tmp, sizeof tmp, "%s.tmp", path) >= sizeof tmp)
    return false;
  FILE *f = fopen(tmp, "wb");
  if (!f)
    return false;
#ifdef POCKET_ZSTD
  bool ok = write_zstd(f, data, size);
#else
  bool ok = fwrite(data, 1, size, f) == size;
#endif
  ok = fflush(f) == 0 && ok;
  ok = fclose(f) == 0 && ok;
  if (ok && rename(tmp, path) == 0)
    return true;
  unlink(tmp);
  return false;
}

unsigned char *pc_state_read(const char *path, size_t *size, size_t max) {
  *size = 0;
  FILE *f = fopen(path, "rb");
  if (!f)
    return NULL;
  unsigned char magic[4] = {0};
  size_t mn = fread(magic, 1, 4, f);
  rewind(f);
  unsigned char *d = NULL;
  if (mn == 4 && magic[0] == 0x28 && magic[1] == 0xB5 && magic[2] == 0x2F && magic[3] == 0xFD) {
#ifdef POCKET_ZSTD
    d = read_zstd(f, size, max);
#endif
  } else {
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    rewind(f);
    d = len > 0 && (size_t)len <= max ? malloc((size_t)len) : NULL;
    if (d && fread(d, 1, (size_t)len, f) != (size_t)len) {
      free(d);
      d = NULL;
    }
    *size = d ? (size_t)len : 0;
  }
  fclose(f);
  return d;
}

/* ---- the games' save folders ---- */
#define MAX_FILES 64
#define KEEP 10
#define MAX_SAVE_FILE (64u << 20)

#define PATHN 4096
// DIR/NAME into out (PATHN bytes); false when it doesn't fit.
static bool join(char *out, const char *dir, const char *name) {
  int r = snprintf(out, PATHN, "%s/%s", dir, name);
  return r >= 0 && r < PATHN;
}

static unsigned char *slurp(const char *path, size_t *n) {
  FILE *f = fopen(path, "rb");
  if (!f)
    return NULL;
  fseek(f, 0, SEEK_END);
  long len = ftell(f);
  rewind(f);
  unsigned char *d = len >= 0 && (size_t)len <= MAX_SAVE_FILE ? malloc(len ? (size_t)len : 1) : NULL;
  if (d && fread(d, 1, (size_t)len, f) != (size_t)len) {
    free(d);
    d = NULL;
  }
  fclose(f);
  *n = d ? (size_t)len : 0;
  return d;
}

static int by_name(const void *a, const void *b) { return strcmp(*(char *const *)a, *(char *const *)b); }

// The plain files in a folder, sorted (malloc'd names). -1 = can't be read.
static int files_in(const char *dir, char **out, bool dirs) {
  DIR *d = opendir(dir);
  if (!d)
    return -1;
  int n = 0;
  char path[PATHN];
  for (struct dirent *e; (e = readdir(d)) && n < MAX_FILES;) {
    if (e->d_name[0] == '.')
      continue;
    struct stat st;
    if (!join(path, dir, e->d_name))
      continue;
    if (stat(path, &st) == 0 && (dirs ? S_ISDIR(st.st_mode) : S_ISREG(st.st_mode)))
      out[n++] = strdup(e->d_name);
  }
  closedir(d);
  qsort(out, n, sizeof *out, by_name);
  return n;
}

static void free_names(char **names, int n) {
  for (int i = 0; i < n; i++)
    free(names[i]);
}

// Whether two folders hold the same files with the same bytes.
static bool same_folder(const char *a, const char *b) {
  char *fa[MAX_FILES], *fb[MAX_FILES];
  int na = files_in(a, fa, false), nb = files_in(b, fb, false);
  bool same = na >= 0 && na == nb;
  for (int i = 0; same && i < na; i++) {
    char pa[PATHN], pb[PATHN];
    if (!join(pa, a, fa[i]) || !join(pb, b, fb[i])) {
      same = false;
      break;
    }
    size_t la, lb;
    unsigned char *da = strcmp(fa[i], fb[i]) ? NULL : slurp(pa, &la), *db = da ? slurp(pb, &lb) : NULL;
    same = da && db && la == lb && !memcmp(da, db, la);
    free(da);
    free(db);
  }
  if (na > 0)
    free_names(fa, na);
  if (nb > 0)
    free_names(fb, nb);
  return same;
}

static void remove_folder(const char *dir) {
  char *names[MAX_FILES];
  int n = files_in(dir, names, false);
  for (int i = 0; i < n; i++) {
    char p[PATHN];
    if (!join(p, dir, names[i]))
      continue;
    unlink(p);
  }
  if (n > 0)
    free_names(names, n);
  rmdir(dir);
}

static bool backup_game(const char *src, const char *dest) {
  char *copies[256];
  mkdir(dest, 0755);
  int count = files_in(dest, copies, true); // YYYYmmdd-HHMMSS: by name = oldest first
  if (count < 0)
    return false;
  char newest[PATHN];
  if (count && join(newest, dest, copies[count - 1])) {
    if (same_folder(src, newest)) {
      free_names(copies, count);
      return true;
    }
  }
  char stamp[32], to[PATHN];
  time_t now = time(NULL);
  strftime(stamp, sizeof stamp, "%Y%m%d-%H%M%S", localtime(&now));
  if (!join(to, dest, stamp)) {
    free_names(copies, count);
    return false;
  }
  if (mkdir(to, 0755) != 0) {
    free_names(copies, count);
    return errno == EEXIST; // a copy from this same second
  }
  bool ok = true;
  char *files[MAX_FILES];
  int n = ok ? files_in(src, files, false) : -1;
  ok = n >= 0;
  for (int i = 0; ok && i < n; i++) {
    char a[PATHN], b[PATHN];
    if (!join(a, src, files[i]) || !join(b, to, files[i])) {
      ok = false;
      break;
    }
    size_t len;
    unsigned char *d = slurp(a, &len);
    FILE *f = d ? fopen(b, "wb") : NULL;
    ok = f && fwrite(d, 1, len, f) == len;
    ok = f && fflush(f) == 0 && fsync(fileno(f)) == 0 && ok;
    if (f)
      ok = fclose(f) == 0 && ok;
    free(d);
  }
  if (n > 0)
    free_names(files, n);
  if (!ok)
    remove_folder(to); // never a half copy that looks like a backup
  // The newest 10 stay (a new one counts as the newest).
  for (int i = 0; ok && i < count - (KEEP - 1); i++) {
    char old[PATHN];
    if (!join(old, dest, copies[i]))
      continue;
    remove_folder(old);
  }
  free_names(copies, count);
  return ok;
}

bool pc_saves_backup(const char *savesDir, const char *backupsDir) {
  char saves[PATHN], into[PATHN];
  if (!join(saves, savesDir, "mkxp-z/Saves"))
    return false;
  char *games[256];
  int n = files_in(saves, games, true);
  if (n < 0)
    return true; // nothing saved yet
  mkdir(backupsDir, 0755);
  if (!join(into, backupsDir, "mkxp-z")) {
    free_names(games, n);
    return false;
  }
  mkdir(into, 0755);
  bool ok = true;
  for (int i = 0; i < n; i++) {
    char src[PATHN], dest[PATHN], *files[MAX_FILES];
    if (!join(src, saves, games[i]) || !join(dest, into, games[i]))
      continue;
    int fn = files_in(src, files, false);
    if (fn > 0) {
      free_names(files, fn);
      ok = backup_game(src, dest) && ok;
    }
  }
  free_names(games, n);
  return ok;
}
