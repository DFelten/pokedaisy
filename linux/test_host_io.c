/* Fault injection around upstream's real VFile fallback; no game data. */
#include "host_io.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utime.h>
#include "pd_fs.h"
struct fault_file {
  struct VFile d;
  struct VFile *inner;
  bool noMap, failRead, failWrite, failSync, failTruncate, failClose;
  int writes, unmaps, closes;
};
static bool fc(struct VFile *v) {
  struct fault_file *f = (void *)v;
  f->closes++;
  bool ok = f->inner->close(f->inner);
  return !f->failClose && ok;
}
static off_t fs(struct VFile *v, off_t o, int w) {
  struct fault_file *f = (void *)v;
  return f->inner->seek(f->inner, o, w);
}
static ssize_t fr(struct VFile *v, void *p, size_t n) {
  struct fault_file *f = (void *)v;
  return f->failRead ? -1 : f->inner->read(f->inner, p, n);
}
static ssize_t fl(struct VFile *v, char *p, size_t n) {
  struct fault_file *f = (void *)v;
  return f->inner->readline(f->inner, p, n);
}
static ssize_t fw(struct VFile *v, const void *p, size_t n) {
  struct fault_file *f = (void *)v;
  f->writes++;
  return f->failWrite ? -1 : f->inner->write(f->inner, p, n);
}
static void *fm(struct VFile *v, size_t n, int mode) {
  struct fault_file *f = (void *)v;
  return f->noMap ? NULL : f->inner->map(f->inner, n, mode);
}
static void fu(struct VFile *v, void *p, size_t n) {
  struct fault_file *f = (void *)v;
  f->unmaps++;
  f->inner->unmap(f->inner, p, n);
}
static void ft(struct VFile *v, size_t n) {
  struct fault_file *f = (void *)v;
  if (!f->failTruncate)
    f->inner->truncate(f->inner, n);
}
static ssize_t fz(struct VFile *v) {
  struct fault_file *f = (void *)v;
  return f->inner->size(f->inner);
}
static bool fy(struct VFile *v, void *p, size_t n) {
  struct fault_file *f = (void *)v;
  return !f->failSync && f->inner->sync(f->inner, p, n);
}
static struct fault_file file(bool failMap) {
  unsigned char seed[] = {1, 2, 3, 4};
  struct fault_file f = {.inner = VFileMemChunk(seed, 4), .noMap = failMap};
  assert(f.inner);
  f.d = (struct VFile){.close = fc,
                       .seek = fs,
                       .read = fr,
                       .readline = fl,
                       .write = fw,
                       .map = fm,
                       .unmap = fu,
                       .truncate = ft,
                       .size = fz,
                       .sync = fy};
  return f;
}
static int backups(const char *dir) {
  char path[600];
  snprintf(path, sizeof path, "%s/" POCKET_BACKUP_DIR, dir);
  DIR *d = opendir(path);
  int n = 0;
  for (struct dirent *e; d && (e = readdir(d));)
    n += !strncmp(e->d_name, "game.gba.backup-", 16) && strstr(e->d_name, ".sav") && !strstr(e->d_name, ".tmp");
  if (d)
    closedir(d);
  return n;
}
static void write_save(const char *path, unsigned char fill, unsigned char clock) {
  unsigned char data[512 + 16];
  memset(data, fill, 512);
  memset(data + 512, clock, 16);
  FILE *f = fopen(path, "wb");
  assert(f && fwrite(data, 1, sizeof data, f) == sizeof data && !fclose(f));
}
static void test_backups(void) {
  char dir[] = "/tmp/pd-linux-backup-XXXXXX", save[600];
  assert(mkdtemp(dir));
  snprintf(save, sizeof save, "%s/game.gba.sav", dir);
  time_t t = 1760000000;
  assert(pocket_save_backup(save, t) && backups(dir) == 0); // no save yet
  write_save(save, 1, 0);
  assert(pocket_save_backup(save, t++) && backups(dir) == 1);
  assert(pocket_save_backup(save, t++) && backups(dir) == 1); // unchanged
  write_save(save, 1, 9);
  assert(pocket_save_backup(save, t++) && backups(dir) == 1); // only the clock moved
  for (int i = 2; i < 20; i++) {
    write_save(save, (unsigned char)i, 0);
    assert(pocket_save_backup(save, t++));
    assert(backups(dir) == (i < POCKET_BACKUP_KEEP ? i : POCKET_BACKUP_KEEP));
  }
  char path[700];
  snprintf(path, sizeof path, "%s/" POCKET_BACKUP_DIR, dir);
  DIR *d = opendir(path);
  for (struct dirent *e; d && (e = readdir(d));) {
    if (e->d_name[0] == '.')
      continue;
    snprintf(path, sizeof path, "%s/" POCKET_BACKUP_DIR "/%s", dir, e->d_name);
    FILE *f = fopen(path, "rb");
    assert(f && fgetc(f) >= 20 - POCKET_BACKUP_KEEP); // the newest are kept
    fclose(f);
    unlink(path);
  }
  closedir(d);
  snprintf(path, sizeof path, "%s/" POCKET_BACKUP_DIR, dir);
  rmdir(path);
  unlink(save);
  rmdir(dir);
}
static void touch_at(const char *path, time_t when) {
  FILE *f = fopen(path, "wb");
  assert(f && fputc(1, f) == 1 && !fclose(f));
  struct utimbuf t = {when, when};
  assert(!utime(path, &t));
}
static void expect_save(const char *dir, const char *rom, const char *name) {
  char out[700], want[700];
  pd_save_path(dir, rom, out, sizeof out);
  snprintf(want, sizeof want, "%s/%s", dir, name);
  if (strcmp(out, want))
    fprintf(stderr, "%s: %s, wanted %s\n", rom, out, want);
  assert(!strcmp(out, want));
}
// A save named the way Android, the 3DS port and other emulators name it (FireRed.sav), the old whole-name one still read.
static void test_save_names(void) {
  char dir[] = "/tmp/pd-linux-saves-XXXXXX", roms[600], saves[600], rom[700], p[700], q[700];
  assert(mkdtemp(dir));
  snprintf(roms, sizeof roms, "%s/roms", dir);
  snprintf(saves, sizeof saves, "%s/saves", dir);
  assert(!pd_mkdir(roms) && !pd_mkdir(saves));
  snprintf(rom, sizeof rom, "%s/FireRed.gba", roms);
  touch_at(rom, 1760000000);
  expect_save(saves, rom, "FireRed.sav"); // a new game: the base name
  snprintf(p, sizeof p, "%s/FireRed.gba.sav", saves);
  touch_at(p, 1760000000);
  expect_save(saves, rom, "FireRed.gba.sav"); // an old host's save stays the save
  snprintf(q, sizeof q, "%s/FireRed.sav", saves);
  touch_at(q, 1760000100);
  expect_save(saves, rom, "FireRed.sav"); // a newer one synced in from a phone wins
  touch_at(p, 1760000200);
  expect_save(saves, rom, "FireRed.gba.sav"); // played here after it: the old name was written last
  // Another ROM with the same base name: FireRed.sav could be either's, so each keeps its whole name.
  char twin[700];
  snprintf(twin, sizeof twin, "%s/FireRed.gb", roms);
  touch_at(twin, 1760000000);
  expect_save(saves, rom, "FireRed.gba.sav");
  expect_save(saves, twin, "FireRed.gb.sav");
  unlink(twin);
  unlink(p);
  unlink(q);
  unlink(rom);
  rmdir(roms);
  rmdir(saves);
  rmdir(dir);
}
int main(void) {
  test_backups();
  test_save_names();
  struct pocket_save_status status;
  struct fault_file f = file(true);
  struct VFile *v = pocket_save_wrap(&f.d, &status);
  assert(v);
  assert(v->seek(v, 2, SEEK_SET) == 2);
  unsigned char *p = v->map(v, 8, MAP_WRITE);
  assert(p && p[0] == 1 && p[3] == 4 && p[7] == 0);
  assert(v->seek(v, 0, SEEK_CUR) == 2);
  p[0] = 9;
  assert(v->sync(v, p, 8));
  assert(v->seek(v, 0, SEEK_CUR) == 2);
  unsigned char out[8];
  assert(f.inner->seek(f.inner, 0, SEEK_SET) == 0);
  assert(f.inner->read(f.inner, out, 8) == 8 && out[0] == 9);
  v->unmap(v, p, 8);
  assert(!f.unmaps);
  assert(v->close(v));
  assert(status.closed && !status.failed && f.closes == 1);
  f = file(false);
  v = pocket_save_wrap(&f.d, &status);
  p = v->map(v, 4, MAP_WRITE);
  assert(p && p[0] == 1);
  v->unmap(v, p, 4);
  assert(f.unmaps == 1);
  assert(v->close(v) && !status.failed);
  f = file(true);
  f.failRead = true;
  v = pocket_save_wrap(&f.d, &status);
  p = v->map(v, 4, MAP_WRITE);
  assert(p && status.failed);
  p[0] = 77;
  assert(!v->sync(v, p, 4));
  v->unmap(v, p, 4);
  assert(f.writes == 0);
  v->close(v);
  f = file(true);
  v = pocket_save_wrap(&f.d, &status);
  p = v->map(v, 4, MAP_WRITE);
  assert(p);
  f.failWrite = true;
  assert(!v->sync(v, p, 4) && status.failed);
  v->unmap(v, p, 4);
  assert(f.writes == 1);
  v->close(v);
  f = file(true);
  v = pocket_save_wrap(&f.d, &status);
  f.failSync = true;
  assert(!v->sync(v, NULL, 0) && status.failed);
  v->close(v);
  f = file(true);
  v = pocket_save_wrap(&f.d, &status);
  f.failTruncate = true;
  v->truncate(v, 8);
  assert(status.failed);
  v->close(v);
  f = file(false);
  v = pocket_save_wrap(&f.d, &status);
  f.failClose = true;
  assert(!v->close(v) && status.failed && status.closed);
  assert(!pocket_save_wrap(NULL, &status) && status.failed);
  puts("PASS save mapping/fallback, latched I/O failures, ownership and bounded backups");
}
