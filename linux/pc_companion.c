// See pc_companion.h. The model is rebuilt from each snapshot; ids handed to the shared views are places in it
// (species = party slot + 1, item = bag row + 1), and their names come back through the views' sources.
#include "pc_companion.h"
#include "pc_json.h"
#include "pc_png.h"
#include "pd_snapshot.h"
#include "pd_stats.h"
#include "pd_style.h"
#include "pd_tables.h"
#include "pd_ui.h"
#include "pd_ui_internal.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define ICON 32 // the party icon as the shared slots draw it: 32 x 32 (the game's are 64 x 64, drawn 2x)
#define MAX_ITEMS 512
#define MAX_POCKETS 16
#define MAX_TYPES 64
#define MAX_CHART (MAX_TYPES * MAX_TYPES)

struct pc_move {
  char name[40];
  int pp, total, type, power;
};
struct pc_mon {
  char name[40], species[40], item[48], nature[32], ability[40];
  int level, hp, maxHp, gender, dex, type1, type2, moveCount, natureId;
  uint32_t status;
  bool egg, shiny, fusion, hasStats;
  int stats[6];
  struct pc_move moves[PD_NUM_MOVES];
  char iconKey[512]; // what the icon cache looks it up by
  const uint32_t* icon; // ICON x ICON, 0xAARRGGBB; NULL = none
};
struct pc_item {
  char name[48];
  int qty, pocket; // pocket: 1-based, the bag view's native id (0 is ALL)
};

static struct {
  char root[4096];
  bool ok;
  int partyCount;
  struct pc_mon party[PD_PARTY_SIZE];
  int pocketCount, itemCount;
  char pockets[MAX_POCKETS][40];
  struct pc_item items[MAX_ITEMS];
  char trainer[40], mapName[64];
  long trainerId, money, seconds;
  int badges;
  bool hasTrainer;
  // The game's types: names by number and the chart's pairs that aren't 1x.
  char typeNames[MAX_TYPES][24];
  const char* typeNamePtrs[MAX_TYPES];
  int typeCount;
  struct pd_type_pair chart[MAX_CHART];
  int chartCount;
} m;

static struct {
  enum pc_tab tab;
  int partySel, summaryPage, bagPocket, bagSel, bagSort, partyCols;
  bool summary, bagDetails;
  unsigned bagMask;
} ui = {PC_TAB_PARTY, 0, 0, 0, 0, 0, 2, false, false, 1};

/* ---- icons: the game's PNGs, a fusion's put together the way Infinite Fusion's createFusionIcon does ---- */

#define ICON_CACHE 48
static struct {
  char key[512];
  uint32_t px[ICON * ICON];
  bool ok;
} icons[ICON_CACHE];
static int iconNext;

static uint32_t* load_png(const char* rel, int* w, int* h) {
  if (!rel || !*rel)
    return NULL;
  char path[8192];
  snprintf(path, sizeof path, "%s/%s", m.root, rel);
  uint32_t* px = pc_png_read(path, w, h);
  if (!px && !strstr(rel, ".png")) { // Essentials hands paths without their extension
    snprintf(path, sizeof path, "%s/%s.png", m.root, rel);
    px = pc_png_read(path, w, h);
  }
  return px;
}

// The sheet's first frame (a square of its height), halved to ICON: the games' icons are drawn at 2x.
static bool first_frame(const uint32_t* px, int w, int h, uint32_t* out) {
  if (!px || h < ICON || w < h)
    return false;
  int step = h / ICON;
  for (int y = 0; y < ICON; y++)
    for (int x = 0; x < ICON; x++)
      out[y * ICON + x] = px[(size_t)(y * step) * w + x * step];
  return true;
}

static const uint32_t* icon_for(const struct pc_json* icon, char* key, size_t keyLen) {
  const char *file = pc_json_str(pc_json_get(icon, "file"), NULL), *head = pc_json_str(pc_json_get(icon, "head"), NULL),
             *body = pc_json_str(pc_json_get(icon, "body"), NULL);
  int offset = (int)pc_json_num(pc_json_get(icon, "offset"), 10);
  if (file)
    snprintf(key, keyLen, "%s", file);
  else if (head && body)
    snprintf(key, keyLen, "%s|%s|%d", head, body, offset);
  else
    return NULL;
  for (int i = 0; i < ICON_CACHE; i++)
    if (icons[i].key[0] && !strcmp(icons[i].key, key))
      return icons[i].ok ? icons[i].px : NULL;
  int slot = iconNext++ % ICON_CACHE;
  snprintf(icons[slot].key, sizeof icons[slot].key, "%s", key);
  icons[slot].ok = false;
  int w, h, bw, bh;
  uint32_t* px = load_png(file ? file : head, &w, &h);
  if (px && !file) {
    // The head's frame, then the body's rows from [offset] below the middle down, every pixel copied (set_pixel).
    uint32_t* bpx = load_png(body, &bw, &bh);
    if (bpx && bh == h && bw >= h) {
      for (int y = h / 2 + offset * h / 64; y < h; y++)
        for (int x = 0; x < h; x++)
          px[(size_t)y * w + x] = bpx[(size_t)y * bw + x];
    }
    free(bpx);
  }
  icons[slot].ok = first_frame(px, w, h, icons[slot].px);
  free(px);
  return icons[slot].ok ? icons[slot].px : NULL;
}

static void draw_icon(struct pd_canvas* c, const uint32_t* px, int x, int y, int size, int scale, bool faded) {
  if (!px)
    return;
  for (int yy = 0; yy < size; yy++)
    for (int xx = 0; xx < size; xx++) {
      uint32_t p = px[(yy * ICON / size) * ICON + xx * ICON / size];
      if (p >> 24 < 0x80)
        continue;
      p &= 0xFFFFFF;
      pd_fill(c, x + xx * scale, y + yy * scale, scale, scale, faded ? (p & 0xFEFEFE) >> 1 : p);
    }
}

/* ---- the snapshot ---- */

static void copy(char* dst, size_t n, const char* src) { snprintf(dst, n, "%s", src ? src : ""); }

// Essentials' status symbols as Gen 3's bits, which the shared badges read.
static uint32_t status_bits(const char* s) {
  if (!s)
    return 0;
  if (!strcmp(s, "SLEEP"))
    return 2;
  if (!strcmp(s, "POISON"))
    return 1u << 3;
  if (!strcmp(s, "BURN"))
    return 1u << 4;
  if (!strcmp(s, "FROZEN"))
    return 1u << 5;
  if (!strcmp(s, "PARALYSIS"))
    return 1u << 6;
  return 0;
}

static int type_at(const struct pc_json* list, int i) {
  const struct pc_json* t = pc_json_at(list, i);
  return t && t->type == PC_JSON_NUMBER && t->number >= 0 && t->number < MAX_TYPES ? (int)t->number : PD_TYPE_NONE;
}

static void read_types(const struct pc_json* table) {
  m.typeCount = 0;
  m.chartCount = 0;
  for (int i = 0; i < MAX_TYPES; i++)
    m.typeNames[i][0] = 0;
  for (int i = 0; i < pc_json_len(table); i++) {
    const struct pc_json* t = pc_json_at(table, i);
    int id = (int)pc_json_num(pc_json_get(t, "id"), -1);
    if (id < 0 || id >= MAX_TYPES)
      continue;
    copy(m.typeNames[id], sizeof m.typeNames[id], pc_json_str(pc_json_get(t, "name"), ""));
    if (id + 1 > m.typeCount)
      m.typeCount = id + 1;
    static const struct {
      const char* key;
      int pct;
    } kinds[] = {{"weak", 200}, {"resist", 50}, {"immune", 0}};
    for (int k = 0; k < 3; k++) {
      const struct pc_json* atk = pc_json_get(t, kinds[k].key);
      for (int j = 0; j < pc_json_len(atk) && m.chartCount < MAX_CHART; j++) {
        int a = type_at(atk, j);
        if (a != PD_TYPE_NONE)
          m.chart[m.chartCount++] = (struct pd_type_pair){a, id, kinds[k].pct};
      }
    }
  }
  for (int i = 0; i < MAX_TYPES; i++)
    m.typeNamePtrs[i] = m.typeNames[i];
}

static void read_mon(const struct pc_json* j, struct pc_mon* p) {
  memset(p, 0, sizeof *p);
  copy(p->name, sizeof p->name, pc_json_str(pc_json_get(j, "name"), "?"));
  copy(p->species, sizeof p->species, pc_json_str(pc_json_get(j, "species"), p->name));
  copy(p->item, sizeof p->item, pc_json_str(pc_json_get(j, "item"), ""));
  copy(p->nature, sizeof p->nature, pc_json_str(pc_json_get(j, "nature"), ""));
  copy(p->ability, sizeof p->ability, pc_json_str(pc_json_get(j, "ability"), ""));
  p->natureId = (int)pc_json_num(pc_json_get(j, "natureId"), -1);
  p->level = (int)pc_json_num(pc_json_get(j, "level"), 0);
  p->hp = (int)pc_json_num(pc_json_get(j, "hp"), 0);
  p->maxHp = (int)pc_json_num(pc_json_get(j, "totalhp"), 0);
  p->gender = (int)pc_json_num(pc_json_get(j, "gender"), 2);
  p->dex = (int)pc_json_num(pc_json_get(j, "dex"), 0);
  p->status = status_bits(pc_json_str(pc_json_get(j, "status"), NULL));
  p->egg = pc_json_bool(pc_json_get(j, "egg"));
  p->shiny = pc_json_bool(pc_json_get(j, "shiny"));
  p->fusion = pc_json_bool(pc_json_get(j, "fusion"));
  const struct pc_json* types = pc_json_get(j, "types");
  p->type1 = type_at(types, 0);
  p->type2 = pc_json_len(types) > 1 ? type_at(types, 1) : PD_TYPE_NONE;
  const struct pc_json* stats = pc_json_get(j, "stats");
  p->hasStats = pc_json_len(stats) == 6;
  for (int i = 0; i < 6 && p->hasStats; i++)
    p->stats[i] = (int)pc_json_num(pc_json_at(stats, i), 0);
  const struct pc_json* moves = pc_json_get(j, "moves");
  for (int i = 0; i < pc_json_len(moves) && p->moveCount < PD_NUM_MOVES; i++) {
    const struct pc_json* mv = pc_json_at(moves, i);
    struct pc_move* o = &p->moves[p->moveCount++];
    copy(o->name, sizeof o->name, pc_json_str(pc_json_get(mv, "name"), "?"));
    o->pp = (int)pc_json_num(pc_json_get(mv, "pp"), 0);
    o->total = (int)pc_json_num(pc_json_get(mv, "total"), 0);
    const struct pc_json* t = pc_json_get(mv, "type");
    o->type = t && t->type == PC_JSON_NUMBER && t->number >= 0 && t->number < MAX_TYPES ? (int)t->number : PD_TYPE_NONE;
    o->power = (int)pc_json_num(pc_json_get(mv, "power"), 0);
  }
  p->icon = icon_for(pc_json_get(j, "icon"), p->iconKey, sizeof p->iconKey);
}

void pc_companion_start(const char* gameDir) {
  memset(&m, 0, sizeof m);
  memset(icons, 0, sizeof icons);
  iconNext = 0;
  copy(m.root, sizeof m.root, gameDir);
  ui.tab = PC_TAB_PARTY;
  ui.partySel = ui.summaryPage = ui.bagPocket = ui.bagSel = 0;
  ui.summary = ui.bagDetails = false;
}

bool pc_companion_update(const char* json, size_t len) {
  struct pc_json* root = pc_json_parse(json, len);
  if (!root || root->type != PC_JSON_OBJECT) {
    pc_json_free(root);
    return false;
  }
  read_types(pc_json_get(root, "typeTable"));
  const struct pc_json* t = pc_json_get(root, "trainer");
  m.hasTrainer = t != NULL;
  copy(m.trainer, sizeof m.trainer, pc_json_str(pc_json_get(t, "name"), ""));
  m.trainerId = (long)pc_json_num(pc_json_get(t, "id"), -1);
  m.money = (long)pc_json_num(pc_json_get(t, "money"), -1);
  m.badges = (int)pc_json_num(pc_json_get(t, "badges"), -1);
  m.seconds = (long)pc_json_num(pc_json_get(t, "seconds"), -1);
  copy(m.mapName, sizeof m.mapName, pc_json_str(pc_json_get(pc_json_get(root, "map"), "name"), ""));
  const struct pc_json* party = pc_json_get(root, "party");
  m.partyCount = 0;
  for (int i = 0; i < pc_json_len(party) && m.partyCount < PD_PARTY_SIZE; i++)
    read_mon(pc_json_at(party, i), &m.party[m.partyCount++]);
  const struct pc_json* bag = pc_json_get(root, "bag");
  m.pocketCount = m.itemCount = 0;
  for (int i = 0; i < pc_json_len(bag) && m.pocketCount < MAX_POCKETS; i++) {
    const struct pc_json* pk = pc_json_at(bag, i);
    copy(m.pockets[m.pocketCount], sizeof m.pockets[0], pc_json_str(pc_json_get(pk, "name"), ""));
    m.pocketCount++;
    const struct pc_json* items = pc_json_get(pk, "items");
    for (int k = 0; k < pc_json_len(items) && m.itemCount < MAX_ITEMS; k++) {
      const struct pc_json* it = pc_json_at(items, k);
      struct pc_item* o = &m.items[m.itemCount++];
      copy(o->name, sizeof o->name, pc_json_str(pc_json_get(it, "name"), "?"));
      o->qty = (int)pc_json_num(pc_json_get(it, "qty"), 1);
      o->pocket = m.pocketCount; // 1-based: 0 is ALL
    }
  }
  m.ok = true;
  pc_json_free(root);
  if (ui.partySel >= m.partyCount)
    ui.partySel = m.partyCount ? m.partyCount - 1 : 0;
  if (!m.partyCount)
    ui.summary = false;
  return true;
}

bool pc_companion_has_data(void) { return m.ok; }

/* ---- drawing ---- */

// The game's own type names and chart, for the summary's type pills and matchups.
static struct pd_text gameText;
static struct pd_config gameCfg;
static struct pd_game game;
static const struct pd_game* game_types(void) {
  gameText.typeNames = m.typeNamePtrs;
  gameText.typeNamesCount = m.typeCount;
  gameText.typeChart = m.chart;
  gameText.typeChartCount = m.chartCount;
  gameCfg.text = &gameText;
  game.cfg = &gameCfg;
  return m.typeCount ? &game : NULL;
}

static bool src_egg(void* ctx, const struct pd_mon* mon) {
  (void)ctx;
  int slot = mon->species - 1;
  return slot >= 0 && slot < m.partyCount && m.party[slot].egg;
}
static const char* src_species(void* ctx, int id) {
  (void)ctx;
  return id >= 1 && id <= m.partyCount ? m.party[id - 1].species : "";
}
static bool src_icon(void* ctx, struct pd_canvas* c, const struct pd_mon* mon, int x, int y, int scale, bool faded) {
  (void)ctx;
  int slot = mon->species - 1;
  if (slot < 0 || slot >= m.partyCount || !m.party[slot].icon)
    return false;
  draw_icon(c, m.party[slot].icon, x, y, ICON, scale, faded);
  return true;
}
static bool summary_icon(void* ctx, struct pd_canvas* c, int slot, int x, int y, bool small) {
  (void)ctx;
  if (slot < 0 || slot >= m.partyCount || !m.party[slot].icon)
    return false;
  draw_icon(c, m.party[slot].icon, x, y, small ? 16 : ICON, 1, false);
  return true;
}

static void party_page(struct pd_canvas* c) {
  // The shared grid's columns (ui_party_grid's PartyGrid rule: three on a page wider than 2:1), for the D-pad.
  int gw = c->w - 2 * MARGIN, gh = c->h - (CONTENT_Y - 2) - MARGIN;
  ui.partyCols = gw > 2 * gh ? 3 : 2;
  struct pd_snapshot view;
  memset(&view, 0, sizeof view);
  view.valid = m.ok;
  view.partyCount = m.partyCount;
  for (int i = 0; i < m.partyCount; i++) {
    struct pd_mon* o = &view.party[i];
    const struct pc_mon* p = &m.party[i];
    o->species = i + 1;
    copy(o->nickname, sizeof o->nickname, p->egg ? "EGG" : p->name);
    o->level = p->level;
    o->hp = p->hp;
    o->maxHp = p->maxHp;
    o->status = p->status;
    o->gender = p->egg || p->gender == 2 ? PD_GENDER_NONE : p->gender == 0 ? PD_GENDER_MALE : PD_GENDER_FEMALE;
    o->shiny = p->shiny;
    o->egg = p->egg;
  }
  struct pd_party_source source;
  memset(&source, 0, sizeof source);
  source.egg = src_egg;
  source.species = src_species;
  source.icon = src_icon;
  struct pd_ui pu;
  memset(&pu, 0, sizeof pu);
  pu.partySource = &source;
  pu.summarySlot = -1;
  pu.pressedId = -1;
  pu.keepBackdrop = true;
  struct pd_game none;
  memset(&none, 0, sizeof none);
  pd_ui_party_view(&pu, c, &none, &view, ui.partySel);
}

static void summary_page(struct pd_canvas* c) {
  const struct pc_mon* p = &m.party[ui.partySel];
  struct pd_summary_view v;
  memset(&v, 0, sizeof v);
  v.game = game_types();
  v.icon = summary_icon;
  v.name = p->egg ? "EGG" : p->name;
  v.primary = p->egg ? NULL : ui.summaryPage ? "A MOVES" : "A STATS";
  v.handheldControls = true;
  v.speciesName = p->species;
  v.heldItem = *p->item ? p->item : "NONE";
  v.friendship = -1;
  struct pd_mon_stats stats;
  memset(&stats, 0, sizeof stats);
  stats.valid = p->hasStats;
  for (int i = 0; i < 6; i++)
    stats.values[i] = p->stats[i];
  stats.nature = p->natureId;
  stats.hasNature = p->natureId >= 0 && p->natureId < 25;
  v.stats = p->hasStats ? &stats : NULL;
  v.selected = ui.partySel;
  v.count = m.partyCount;
  v.level = p->level;
  v.hp = p->hp;
  v.maxHp = p->maxHp;
  v.type1 = p->type1;
  v.type2 = p->type2;
  v.status = p->status;
  v.egg = p->egg;
  v.expPercent = -1;
  for (int i = 0; i < p->moveCount; i++)
    v.moves[i] = (struct pd_summary_move){p->moves[i].name, p->moves[i].type, p->moves[i].power, p->moves[i].pp};
  pd_ui_summary_page(c, &v, ui.summaryPage ? PD_SUMMARY_STATS : PD_SUMMARY_MOVES);
  pd_ui_summary_footer(NULL, c, &v, ui.summaryPage != 0);
}

static const char* bag_pocket_name(void* ctx, int tab) {
  (void)ctx;
  if (tab == 0)
    return "ALL";
  return tab >= 1 && tab <= m.pocketCount ? m.pockets[tab - 1] : "";
}
static bool bag_item(void* ctx, int row, int* pocket, struct pd_item* out, bool* quantity) {
  (void)ctx;
  if (row < 0 || row >= m.itemCount)
    return false;
  *pocket = m.items[row].pocket;
  out->id = (uint16_t)(row + 1);
  out->quantity = (uint16_t)m.items[row].qty;
  *quantity = true;
  return true;
}
static const char* bag_name(void* ctx, int id) {
  (void)ctx;
  return id >= 1 && id <= m.itemCount ? m.items[id - 1].name : "";
}

static void bag_page(struct pd_canvas* c) {
  struct pd_bag_source source;
  memset(&source, 0, sizeof source);
  source.tabs = m.pocketCount + 1;
  source.count = m.itemCount;
  source.pocketName = bag_pocket_name;
  source.item = bag_item;
  source.name = bag_name;
  struct pd_snapshot none;
  memset(&none, 0, sizeof none);
  none.bagOk = m.ok;
  struct pd_ui bu;
  memset(&bu, 0, sizeof bu);
  bu.bagSource = &source;
  bu.bagPocket = ui.bagPocket;
  bu.bagSelected = ui.bagSel;
  bu.bagDetails = ui.bagDetails;
  bu.bagSort = ui.bagSort; // X cycles it (SELECT closes the companion here, so no "SEL" hint: shoulderTabs off)
  bu.pressedId = -1;
  bu.focusId = -1;
  bu.keepBackdrop = true;
  struct pd_game g;
  memset(&g, 0, sizeof g);
  pd_ui_bag_view(&bu, c, &g, &none);
  ui.bagPocket = bu.bagPocket;
  ui.bagMask = bu.bagPocketMask ? bu.bagPocketMask : 1;
}

static void trainer_page(struct pd_canvas* c) {
  int x = MARGIN, w = c->w - 2 * MARGIN, titleH = 26;
  int f = pd_title_box(c, x, MARGIN, w, titleH, PD_TITLE_FILL);
  pd_text(c, x + f + 6, pd_text_y(MARGIN, titleH), "TRAINER", PD_TITLE_TEXT, PD_TITLE_SHADOW);
  int y = MARGIN + titleH + 4, rows = 6, h = rows * ROW_H + 12;
  int lf = pd_list_box(c, x, y, w, h);
  char id[16] = "-", money[24] = "-", badges[16] = "-", time[32] = "-";
  if (m.trainerId >= 0)
    snprintf(id, sizeof id, "%05ld", m.trainerId % 100000);
  if (m.money >= 0)
    snprintf(money, sizeof money, "$%ld", m.money);
  if (m.badges >= 0)
    snprintf(badges, sizeof badges, "%d", m.badges);
  if (m.seconds >= 0)
    snprintf(time, sizeof time, "%ld:%02ld", m.seconds / 3600, (m.seconds / 60) % 60);
  const char* rowsText[][2] = {{"NAME", *m.trainer ? m.trainer : "-"}, {"ID No.", id},     {"MONEY", money},
                               {"BADGES", badges},                     {"TIME", time},    {"PLACE", *m.mapName ? m.mapName : "-"}};
  for (int i = 0; i < rows; i++)
    ui_row(c, x + lf + 4, y + lf + 2 + i * ROW_H, w - 2 * lf - 8, rowsText[i][0], rowsText[i][1], false);
}

// The tab strip along the bottom: PARTY / BAG / TRAINER, L and R step it.
static void tab_bar(struct pd_canvas* c, int y, int h) {
  static const char* const names[PC_TAB_COUNT] = {"PARTY", "BAG", "TRAINER"};
  int w = (c->w - 2 * MARGIN - 2 * (PC_TAB_COUNT - 1)) / PC_TAB_COUNT;
  for (int i = 0; i < PC_TAB_COUNT; i++) {
    int x = MARGIN + i * (w + 2);
    bool on = (int)ui.tab == i;
    int f = pd_title_box(c, x, y, w, h, on ? PD_TITLE_FILL : PD_LIST_FILL);
    (void)f;
    int tw = pd_text_width(names[i]);
    pd_text(c, x + (w - tw) / 2, pd_text_y(y, h), names[i], on ? PD_VALUE : PD_LABEL, on ? PD_VALUE_SHADOW : PD_LABEL_SHADOW);
  }
}

void pc_companion_draw(struct pd_canvas* c) {
  pd_backdrop(c);
  const int barH = 24, barY = c->h - barH - 2;
  struct pd_canvas page = *c;
  page.h = barY - 2;
  if (page.cy1 > page.h)
    page.cy1 = page.h;
  if (!m.ok) {
    ui_notice(&page, "COMPANION", "Waiting for the game...", "");
  } else if (ui.tab == PC_TAB_PARTY && ui.summary && m.partyCount)
    summary_page(&page);
  else if (ui.tab == PC_TAB_PARTY)
    party_page(&page);
  else if (ui.tab == PC_TAB_BAG)
    bag_page(&page);
  else
    trainer_page(&page);
  tab_bar(c, barY, barH);
}

/* ---- buttons ---- */

static int pocket_items(int pocket) {
  int n = 0;
  for (int i = 0; i < m.itemCount; i++)
    n += pocket == 0 || m.items[i].pocket == pocket;
  return n;
}

static void bag_step_pocket(int dir) {
  int tabs = m.pocketCount + 1;
  for (int k = 1; k <= tabs; k++) {
    int p = ((ui.bagPocket + dir * k) % tabs + tabs) % tabs;
    if (p == 0 || (ui.bagMask & (1u << p)) || pocket_items(p)) {
      ui.bagPocket = p;
      break;
    }
  }
  ui.bagSel = 0;
  ui.bagDetails = false;
}

bool pc_companion_key(enum pc_key k) {
  if (k == PC_KEY_L || k == PC_KEY_R) {
    ui.tab = (enum pc_tab)(((int)ui.tab + (k == PC_KEY_R ? 1 : PC_TAB_COUNT - 1)) % PC_TAB_COUNT);
    ui.summary = ui.bagDetails = false;
    return true;
  }
  if (ui.tab == PC_TAB_PARTY && ui.summary) {
    if (k == PC_KEY_B)
      ui.summary = false;
    else if (k == PC_KEY_A)
      ui.summaryPage = !ui.summaryPage;
    else if ((k == PC_KEY_UP || k == PC_KEY_LEFT) && m.partyCount)
      ui.partySel = (ui.partySel + m.partyCount - 1) % m.partyCount;
    else if ((k == PC_KEY_DOWN || k == PC_KEY_RIGHT) && m.partyCount)
      ui.partySel = (ui.partySel + 1) % m.partyCount;
    return true;
  }
  if (ui.tab == PC_TAB_PARTY) {
    // The slots, filled across in the grid's columns (0 1 / 2 3 / 4 5, or 0 1 2 / 3 4 5).
    int s = ui.partySel, cols = ui.partyCols > 0 ? ui.partyCols : 2;
    if (k == PC_KEY_LEFT && s % cols)
      s--;
    else if (k == PC_KEY_RIGHT && s % cols < cols - 1)
      s++;
    else if (k == PC_KEY_UP)
      s -= cols;
    else if (k == PC_KEY_DOWN)
      s += cols;
    else if (k == PC_KEY_A && m.partyCount) {
      ui.summary = true;
      ui.summaryPage = 0;
    } else if (k == PC_KEY_B)
      return false;
    if (s >= 0 && s < m.partyCount)
      ui.partySel = s;
    return true;
  }
  if (ui.tab == PC_TAB_BAG) {
    int n = pocket_items(ui.bagPocket);
    if (k == PC_KEY_LEFT || k == PC_KEY_RIGHT)
      bag_step_pocket(k == PC_KEY_RIGHT ? 1 : -1);
    else if (k == PC_KEY_UP && ui.bagSel > 0)
      ui.bagSel--;
    else if (k == PC_KEY_DOWN && ui.bagSel + 1 < n)
      ui.bagSel++;
    else if (k == PC_KEY_A && n)
      ui.bagDetails = !ui.bagDetails;
    else if (k == PC_KEY_X)
      ui.bagSort = (ui.bagSort + 1) % 3;
    else if (k == PC_KEY_B) {
      if (!ui.bagDetails)
        return false;
      ui.bagDetails = false;
    }
    return true;
  }
  return k != PC_KEY_B;
}

void pc_companion_show(enum pc_tab tab, int selected, bool summary, int summaryPage, int pocket) {
  ui.tab = tab;
  ui.partySel = selected;
  ui.summary = summary;
  ui.summaryPage = summaryPage;
  ui.bagPocket = pocket;
  ui.bagSel = tab == PC_TAB_BAG ? selected : 0;
  ui.bagDetails = false;
}
