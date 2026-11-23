// Renders the PC games' companion (pc_companion.c) from a saved snapshot to PNGs, to look at its pages without a game
// running: pc_companion_shot SNAPSHOT.json GAME_DIR OUT_DIR [WxH]. A snapshot comes from native-capture/pc_probe's
// `mbx`. Pages: party, summary (moves / stats) of every slot, the bag's tabs, the trainer.
#include "pc_companion.h"
#include "../3ds/desktop/pd_png.h"
#include "pd_canvas.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static uint32_t* px;
static struct pd_canvas canvas;
static const char* outDir;

static void shot(const char* name) {
  pc_companion_draw(&canvas);
  char path[4096];
  snprintf(path, sizeof path, "%s/%s.png", outDir, name);
  if (!pd_png_write(path, canvas.w, canvas.h, px))
    fprintf(stderr, "can't write %s\n", path);
  else
    printf("%s\n", path);
}

int main(int argc, char** argv) {
  if (argc < 4) {
    fprintf(stderr, "usage: %s SNAPSHOT.json GAME_DIR OUT_DIR [WxH]\n", argv[0]);
    return 2;
  }
  int w = 400, h = 240;
  if (argc > 4 && sscanf(argv[4], "%dx%d", &w, &h) != 2)
    return 2;
  outDir = argv[3];
  FILE* f = fopen(argv[1], "rb");
  if (!f)
    return 1;
  fseek(f, 0, SEEK_END);
  long n = ftell(f);
  fseek(f, 0, SEEK_SET);
  char* json = malloc((size_t)n);
  if (!json || fread(json, 1, (size_t)n, f) != (size_t)n)
    return 1;
  fclose(f);
  px = calloc((size_t)w * h, 4);
  pd_canvas_init(&canvas, w, h, px);
  pc_companion_start(argv[2]);
  pc_companion_show(PC_TAB_PARTY, 0, false, 0, 0);
  shot("0-waiting");
  if (!pc_companion_update(json, (size_t)n)) {
    fprintf(stderr, "snapshot didn't parse\n");
    return 1;
  }
  pc_companion_show(PC_TAB_PARTY, 0, false, 0, 0);
  shot("1-party");
  pc_companion_show(PC_TAB_PARTY, 2, false, 0, 0);
  shot("1-party-slot2");
  for (int slot = 0; slot < 6; slot++)
    for (int page = 0; page < 2; page++) {
      char name[64];
      snprintf(name, sizeof name, "2-summary-%d-%s", slot, page ? "stats" : "moves");
      pc_companion_show(PC_TAB_PARTY, slot, true, page, 0);
      shot(name);
    }
  for (int pocket = 0; pocket < 9; pocket++) {
    char name[64];
    snprintf(name, sizeof name, "3-bag-%d", pocket);
    pc_companion_show(PC_TAB_BAG, 0, false, 0, pocket);
    shot(name);
  }
  pc_companion_show(PC_TAB_TRAINER, 0, false, 0, 0);
  shot("4-trainer");
  free(json);
  free(px);
  return 0;
}
