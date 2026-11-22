#include "pd_style.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

#include "pd_games.h"
#include "pd_gb.h"
#include "pd_icons_gen.h"
#include "pd_tr.h"
#include "pd_ui.h"

const struct pd_look* pd_cur = &pd_themes[0].look[0];
int pd_gb_look;
uint32_t pd_gb_ink = 0x181818, pd_gb_paper = 0xFFFFFF;
uint32_t pd_backdrop_colors[3] = { 0x4AADA5, 0x398C8C, 0x216B63 };

// NIGHT (theme/NightColors.kt): what a dark mode backdrop dims towards.
#define NIGHT 0x12141A

static uint32_t channels(uint32_t a, uint32_t b, int (*f)(int, int, int), int k) {
    uint32_t out = 0;
    for (int sh = 0; sh <= 16; sh += 8) out |= (uint32_t) f((int) (a >> sh & 0xFF), (int) (b >> sh & 0xFF), k) << sh;
    return out;
}

static int multiply(int a, int b, int k) {
    (void) k;
    return a * b / 255;
}

static int mix(int a, int b, int k) {
    return (a * (255 - k) + b * k) / 255;
}

bool pd_dark;

// The wash and scrim over the game's backdrop right now.
static uint32_t backdropWash = 0xFFFFFF;
static int backdropScrim;

uint32_t pd_backdrop_tint(uint32_t c) {
    return channels(channels(c, backdropWash, multiply, 0), NIGHT, mix, backdropScrim);
}

void pd_separator(struct pd_canvas* c, int x, int y, int len, bool vertical) {
    // A Game Boy game's windows: its divider, the rows' own rule.
    uint32_t dark = pd_gb_look ? pd_cur->divider : pd_dark ? 0x0E1116 : 0xB8B8C0;
    uint32_t light = pd_gb_look ? pd_cur->listFill : pd_dark ? 0x3A414D : 0xE8E8EC;
    pd_fill(c, x, y, vertical ? 1 : len, vertical ? len : 1, dark);
    pd_fill(c, x + (vertical ? 1 : 0), y + (vertical ? 0 : 1), vertical ? 1 : len, vertical ? len : 1, light);
}

uint32_t pd_battle_tint(uint32_t c) {
    return channels(c, NIGHT, mix, backdropScrim);
}

// DsBackdrops.kt's BattleBackdrop: HeartGold's battle screens' manner, sampled and drawn here (no game art) - pale mint between
// grey bands, a big faint Poké Ball in the middle, in whole DS-sized pixels (one canvas pixel on the 3DS's 240 rows).
void pd_battle_backdrop(struct pd_canvas* c) {
    static const uint32_t MINT = 0xCBFBF3, RING = 0xBADBCB, THIN = 0xC3E3D3, PEACH = 0xF3D3B2;
    static const struct { int rows; uint32_t color; } BANDS[3] = { { 16, 0xAAAA9A }, { 2, 0xB2C3A2 }, { 2, 0xBADBB2 } };
    int k = c->h / 192 > 1 ? c->h / 192 : 1;
    pd_fill(c, 0, 0, c->w, c->h, pd_backdrop_tint(MINT));
    int edge = 0;
    for (int i = 0; i < 3; i++) {
        int h = BANDS[i].rows * k;
        pd_fill(c, 0, edge, c->w, h, pd_backdrop_tint(BANDS[i].color));
        pd_fill(c, 0, c->h - edge - h, c->w, h, pd_backdrop_tint(BANDS[i].color));
        edge += h;
    }
    int cx = c->w / 2 / k * k, cy = c->h / 2 / k * k;
    const int R = 56, BAND = 5;
    for (int dy = -R; dy <= R; dy++) {
        int radii[5] = { R, R - 5, 20, 15, 10 };
        uint32_t cols[5] = { RING, dy < -BAND ? PEACH : dy > BAND ? MINT : RING, RING, MINT, THIN };
        for (int i = 0; i < 6; i++) {
            int r = i < 5 ? radii[i] : 8;
            uint32_t col = i < 5 ? cols[i] : MINT;
            if (dy * dy >= r * r) continue;
            int half = 0;
            while ((half + 1) * (half + 1) <= r * r - dy * dy) half++;
            if (half > 0) pd_fill(c, cx - half * k, cy + dy * k, half * 2 * k, k, pd_backdrop_tint(col));
        }
    }
}

// ---- SUMMARY MINT in PokeDaisy's blues (UiStyle.kt's retroStylePalette, light and dark) ----
struct retro_palette {
    uint32_t titleFill, titleText, titleShadow;
    struct pd_layer title[2];
    uint32_t listFill;
    struct pd_layer list[3];
    uint32_t stripe, selected, label, labelShadow, sectionFill, sectionText, sectionShadow, separatorDark, well;
    struct pd_key button, tab, tabOpen;
};
static const struct retro_palette RETRO[2] = {
    { 0x3D6FE0, 0xFFFFFF, 0x2449B0, { { 0x1A3A90, 1 }, { 0x9DB8FF, 1 } },
      0xFFFFFF, { { 0x2449B0, 1 }, { 0xA9C1F5, 2 }, { 0x5A86EA, 1 } },
      0xF2F6FD, 0xE5EDFF, 0x232B40, 0xD5DCEA, 0x5A86EA, 0xFFFFFF, 0x2449B0, 0xA9C1F5, 0xEEF3FC,
      { 0x1A3A90, 0xFFFFFF, 0xE5EDFF, 0x5A86EA, 0x232B40, 0xD5DCEA, PD_NO_SHADOW, false },
      { 0x1A3A90, 0xC9D8FA, 0xA9C1F5, 0x3D6FE0, 0x1A2F6A, 0xE8EFFD, 0xEEF3FF, false },
      { 0x1A3A90, 0x4C7BE6, 0x3D6FE0, 0x1A3A90, 0xFFFFFF, 0x1A3A90, 0x8AA8F2, true } },
    { 0x3560D0, 0xFFFFFF, 0x0E1A40, { { 0x050A18, 1 }, { 0x5A7FD8, 1 } },
      0x1C2333, { { 0x05070D, 1 }, { 0x2E4A8A, 2 }, { 0x233A6A, 1 } },
      0x20283A, 0x2B3A62, 0xE8ECF5, 0x2A3348, 0x2F55B8, 0xFFFFFF, 0x0E1A40, 0x05070D, 0x232B3E,
      { 0x05070D, 0x2E3850, 0x262F44, 0x151B28, 0xE8ECF5, 0x141A26, 0x3E4A66, false },
      { 0x05070D, 0x283147, 0x222A3D, 0x151B28, 0xB8C2D8, 0x10141E, 0x364260, false },
      { 0x05070D, 0x4A70D8, 0x3560D0, 0x1A3A90, 0xFFFFFF, 0x0E1A40, 0x6A8CE8, true } },
};
// Each THEME's hue (Theme.kt's ThemeSpec.hue): the style's blues move to it. POKéDAISY and RAINBOW keep the blues.
static const struct { int id; float hue; } THEME_HUES[] = {
    { 20, 345 }, { 22, 200 }, { 23, 155 }, { 24, 265 }, { 25, 22 }, { 26, 110 }, { 27, 50 },
    { 40, 4 }, { 41, 340 }, { 42, 135 }, { 43, 290 }, { 44, 28 }, { 46, 140 }, { 47, 45 },
};

static bool styleOn;
const struct pd_style_colors* pd_style;
void pd_style_enable(bool on) { styleOn = on; }

static void to_hsl(uint32_t c, float* h, float* s, float* l) {
    float r = (c >> 16 & 0xFF) / 255.f, g = (c >> 8 & 0xFF) / 255.f, b = (c & 0xFF) / 255.f;
    float mx = fmaxf(r, fmaxf(g, b)), mn = fminf(r, fminf(g, b)), d = mx - mn;
    *l = (mx + mn) / 2;
    *s = d == 0 ? 0 : d / (1 - fabsf(2 * *l - 1));
    if (d == 0) *h = 0;
    else if (mx == r) *h = 60 * fmodf((g - b) / d + 6, 6);
    else if (mx == g) *h = 60 * ((b - r) / d + 2);
    else *h = 60 * ((r - g) / d + 4);
}
static uint32_t from_hsl(float h, float s, float l) {
    float c = (1 - fabsf(2 * l - 1)) * s, x = c * (1 - fabsf(fmodf(h / 60, 2) - 1)), m = l - c / 2, r, g, b;
    if (h < 60) r = c, g = x, b = 0;
    else if (h < 120) r = x, g = c, b = 0;
    else if (h < 180) r = 0, g = c, b = x;
    else if (h < 240) r = 0, g = x, b = c;
    else if (h < 300) r = x, g = 0, b = c;
    else r = c, g = 0, b = x;
    int R = (int) lroundf((r + m) * 255), G = (int) lroundf((g + m) * 255), B = (int) lroundf((b + m) * 255);
    return (uint32_t) ((R < 0 ? 0 : R > 255 ? 255 : R) << 16 | (G < 0 ? 0 : G > 255 ? 255 : G) << 8 | (B < 0 ? 0 : B > 255 ? 255 : B));
}
// NightColors.kt's Color.rehued over RETRO_FAMILY (195-250): a blue moved to [to], its saturation and lightness kept,
// greens and yellows calmed; greys and other colours stay.
static uint32_t rehue(uint32_t c, float to) {
    if (c == PD_NO_SHADOW) return c;
    float h, s, l;
    to_hsl(c, &h, &s, &l);
    if (s < 0.04f || h < 195 || h > 250) return c;
    float calm = to >= 45 && to <= 175 ? 0.6f : 1;
    float s2 = s > 0.35f ? s * calm : s;
    float l2 = calm < 1 && s > 0.35f && l >= 0.45f && l <= 0.9f ? l - 0.06f : l;
    return from_hsl(to, s2, l2);
}
static void rehue_key(struct pd_key* k, float to) {
    k->outline = rehue(k->outline, to), k->face = rehue(k->face, to), k->low = rehue(k->low, to);
    k->depth = rehue(k->depth, to), k->text = rehue(k->text, to), k->shadow = rehue(k->shadow, to), k->shine = rehue(k->shine, to);
}

// The theme's look with the style's windows over it (OptionColors under a UiStylePalette: the header band is the title
// window, the list window, the rows' colours, the section bands).
static struct pd_look styledLook;
static struct pd_style_colors styleColors;
static void style_build(const struct pd_look* base, int themeId, int dark) {
    struct retro_palette p = RETRO[dark];
    float hue = -1;
    for (size_t i = 0; i < sizeof THEME_HUES / sizeof *THEME_HUES; i++)
        if (THEME_HUES[i].id == themeId) hue = THEME_HUES[i].hue;
    if (hue >= 0) {
        uint32_t* cols[] = { &p.titleFill, &p.titleText, &p.titleShadow, &p.title[0].color, &p.title[1].color, &p.listFill,
                             &p.list[0].color, &p.list[1].color, &p.list[2].color, &p.stripe, &p.selected, &p.label,
                             &p.labelShadow, &p.sectionFill, &p.sectionText, &p.sectionShadow, &p.separatorDark, &p.well };
        for (size_t i = 0; i < sizeof cols / sizeof *cols; i++) *cols[i] = rehue(*cols[i], hue);
        rehue_key(&p.button, hue);
        rehue_key(&p.tab, hue);
        rehue_key(&p.tabOpen, hue);
    }
    styledLook = *base;
    struct pd_look* l = &styledLook;
    for (int i = 0; i < 3; i++) l->list[i] = p.list[i];
    l->listCount = 3;
    l->listFill = p.listFill, l->rowSelected = p.selected, l->label = p.label, l->labelShadow = p.labelShadow;
    l->groupTitleFill = p.sectionFill, l->groupTitleText = p.sectionText, l->groupTitleShadow = p.sectionShadow;
    l->divider = p.separatorDark, l->imageWell = p.well;
    l->chip[0] = (struct pd_layer) { p.tab.outline, 1 };
    l->chipCount = 1;
    l->tabSelectedFill = p.tabOpen.face, l->tabIdleFill = p.tab.face;
    styleColors = (struct pd_style_colors) { p.titleFill, p.titleText, p.titleShadow, { p.title[0], p.title[1] },
                                              p.sectionFill, p.sectionText, p.sectionShadow, p.stripe, p.well,
                                              p.button, p.tab, p.tabOpen };
}

void pd_key(struct pd_canvas* c, int x, int y, int w, int h, const struct pd_key* k) {
    const int r = 3, d = k->pressed ? 1 : 2;
    pd_round_rect(c, x, y, w, h, r, k->outline);
    pd_round_rect(c, x + 1, y + 1, w - 2, h - 2, r - 1, k->depth);
    pd_two_tone(c, x + 1, y + 1, w - 2, h - 2 - d, r - 1, k->face, k->low, 1);
    if (k->shine != PD_NO_SHADOW) pd_fill(c, x + 1 + (r - 1), y + 2, w - 2 - 2 * (r - 1), 1, k->shine);
}

void pd_chip_face(struct pd_canvas* c, int x, int y, int w, int h, bool open) {
    if (pd_style) pd_key(c, x, y, w, h, open ? &pd_style->tabOpen : &pd_style->tab);
    else pd_layered_two_tone(c, x, y, w, h, 3, pd_cur->chip, pd_cur->chipCount, open ? pd_cur->tabSelectedFill : pd_cur->tabIdleFill, 0);
}
uint32_t pd_chip_text(bool open) { return pd_style ? (open ? pd_style->tabOpen.text : pd_style->tab.text) : open ? PD_VALUE : PD_TITLE_TEXT; }
uint32_t pd_chip_shadow(bool open) {
    return pd_style ? (open ? pd_style->tabOpen.shadow : pd_style->tab.shadow) : open ? PD_VALUE_SHADOW : PD_TITLE_SHADOW;
}

void pd_section_title(struct pd_canvas* c, int x, int y, int w, int h, const char* title) {
    if (pd_style) {
        // StyledGroupTitle: a band 2 pixels in from the row's sides, rounded 3, the title 6 in from its left.
        pd_round_rect(c, x + 2, y + 1, w - 4, h - 2, 3, pd_style->sectionFill);
        pd_text(c, x + 8, pd_text_y(y, h), title, pd_style->sectionText, pd_style->sectionShadow);
        return;
    }
    // Gen 1's value colour is the rows' black: its titles are a black band (groupTitleFill).
    if (pd_cur->groupTitleFill != PD_NO_SHADOW) pd_fill(c, x, y + 1, pd_text_width(title) + 8, h - 2, pd_cur->groupTitleFill);
    pd_text(c, x + 4, pd_text_y(y, h), title, pd_cur->groupTitleText, pd_cur->groupTitleShadow);
}

void pd_look_apply(const struct pd_game* g, const struct pd_settings* s) {
    // LANGUAGE follows the same calls: the setting when given (a call without settings keeps the last), the game's letter.
    static int language = PD_LANG_AUTO;
    if (s) language = s->language;
    pd_tr_apply(language, g ? g->language : 0);
    int dark = s && s->darkMode ? 1 : 0;
    pd_dark = dark;
    const struct pd_theme* theme = &pd_themes[0];
    for (int i = 0; s && i < pd_theme_count; i++)
        if (pd_themes[i].id == s->theme) theme = &pd_themes[i];
    const struct pd_game_entry* e = g ? g->entry : NULL;
    const struct pd_gb_config* gb = g && g->cfg ? g->cfg->gb : NULL;
    pd_gb_look = gb != NULL;
    if (gb) {
        // Gen 1's windows by cart (the app's isYellow / Red and Blue's Super Game Boy colours / PureRGB's), Gen 2's for the rest.
        int k = gb->gen == 2 ? PD_GB_LOOK_GEN2 : gb == &pd_gb_red || gb == &pd_gb_blue ? PD_GB_LOOK_RED :
            gb == &pd_gb_pure_red ? PD_GB_LOOK_PURE : PD_GB_LOOK_YELLOW;
        pd_cur = &pd_gb_looks[k][dark];
        pd_gb_ink = pd_cur->label;
        pd_gb_paper = pd_cur->listFill;
    } else {
        pd_cur = &theme->look[dark];
    }
    pd_style = NULL;
    if (styleOn && !gb) {
        style_build(pd_cur, theme->id, dark);
        pd_cur = &styledLook;
        pd_style = &styleColors;
    }
    pd_party_cur = e ? e->party[dark] : pd_party_default[dark];
    pd_bag_cur = e ? e->bag[dark] : pd_bag_default[dark];
    static const uint32_t FIRERED[3] = { 0x4AADA5, 0x398C8C, 0x216B63 };
    static const uint32_t PLAIN[3] = { 0xFFFFFF, 0xFFFFFF, 0xFFFFFF };
    const uint32_t* base = gb ? PLAIN : e ? e->backdrop : FIRERED;
    int scrim = pd_cur->scrim;
    if (scrim && gb) scrim = 230; // Gen 1's white goes nearly black
    backdropWash = theme->wash;
    backdropScrim = scrim;
    for (int i = 0; i < 3; i++) {
        uint32_t c = base[i];
        if (!(gb)) c = channels(c, theme->wash, multiply, 0);
        pd_backdrop_colors[i] = channels(c, NIGHT, mix, scrim);
    }
}

int pd_header_box(struct pd_canvas* c, int x, int y, int w, int h) {
    if (pd_style) return pd_layered_box(c, x, y, w, h, 3, pd_style->header, 2, pd_style->headerFill);
    return pd_title_box(c, x, y, w, h, PD_TITLE_FILL);
}

int pd_title_box(struct pd_canvas* c, int x, int y, int w, int h, uint32_t fill) {
    return pd_layered_box(c, x, y, w, h, 3, pd_cur->title, pd_cur->titleCount, fill);
}

static const uint16_t BOLT[7] = { 0x18, 0x0C, 0x06, 0x1F, 0x0C, 0x06, 0x03 }; // bit 0 = left, 5 wide

int pd_battery_width(int pct, bool charging) {
    char text[16];
    snprintf(text, sizeof text, "%d%%", pct > 100 ? 100 : pct < 0 ? 0 : pct);
    return 24 + pd_text_width(text) + (charging ? 8 : 0);
}

void pd_battery_draw(struct pd_canvas* c, int end, int ty, int pct, bool charging) {
    if (pct < 0) return;
    if (pct > 100) pct = 100;
    char text[8];
    snprintf(text, sizeof text, "%d%%", pct);
    int x = end - pd_battery_width(pct, charging);
    if (charging) {
        pd_bitmap(c, x + 1, ty + 6, BOLT, 7, 0x1E6A1E); // a darker copy a pixel down-right: the text's shadow
        pd_bitmap(c, x, ty + 5, BOLT, 7, 0x40B840);
        x += 8;
    }
    pd_fill(c, x, ty + 5, 18, 8, PD_HEADER_TEXT);
    pd_fill(c, x + 1, ty + 6, 16, 6, PD_HEADER_FILL);
    pd_fill(c, x + 2, ty + 7, 14 * pct / 100, 4, charging ? 0x40B840 : pct <= 15 ? PD_VALUE : PD_HEADER_TEXT);
    pd_fill(c, x + 18, ty + 7, 2, 4, PD_HEADER_TEXT);
    pd_text(c, end - pd_text_width(text), ty, text, PD_HEADER_TEXT, PD_HEADER_SHADOW);
}

void pd_row_divider(struct pd_canvas* c, int x, int y, int w) {
    if (pd_font_size_small()) pd_fill(c, x, y, w, 1, pd_mix(PD_LIST_FILL, PD_DIVIDER, 150));
}

int pd_list_box(struct pd_canvas* c, int x, int y, int w, int h) {
    return pd_layered_box(c, x, y, w, h, 3, pd_cur->list, pd_cur->listCount, PD_LIST_FILL);
}

void pd_backdrop(struct pd_canvas* c) {
    for (int y = 0; y < c->h; y += 8) pd_fill(c, 0, y, c->w, 8, pd_backdrop_colors[(y / 8) % 2]);
    pd_fill(c, 0, c->h - 4, c->w, 4, pd_backdrop_colors[2]);
}

void ui_art_at(struct pd_canvas* c, const struct pd_art* art, int x, int y, int scale) {
    int step = scale < 1 ? 2 : 1, px = scale < 1 ? 1 : scale;
    for (int r = 0; r < art->h; r += step) {
        for (int col = 0; art->rows[r][col]; col += step) {
            const char* k = strchr(art->keys, art->rows[r][col]);
            if (k && art->rows[r][col] != '.') pd_fill(c, x + col / step * px, y + r / step * px, px, px, art->colors[k - art->keys]);
        }
    }
}
