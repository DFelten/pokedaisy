// The FireRed OPTION screen's look, shared by the companion and the ROM list:
// OptionColors (app/.../companion/ui/GbaMenu.kt) and the window boxes built
// from them.
#ifndef PD_STYLE_H
#define PD_STYLE_H

#include <stdint.h>

#include "pd_canvas.h"
#include "pd_font.h"

// OptionColors at this moment (a theme, dark mode, a Game Boy game's windows): pd_look_apply picks one.
struct pd_look {
    struct pd_layer title[4], list[6], chip[4], fieldMove[3];
    int titleCount, listCount, chipCount, fieldMoveCount;
    uint32_t titleFill, titleText, titleShadow, listFill, rowSelected, label, labelShadow, value, valueShadow, success;
    uint32_t fieldMoveText, fieldMoveShadow, featureFill, featureText, featureShadow, muted, mutedShadow, frameDark, frameLight;
    uint32_t onBackdrop, onBackdropShadow, tabSelectedFill, tabIdleFill, groupTitleFill, groupTitleText, groupTitleShadow;
    uint32_t divider, badgeText, newBadge, favorite, imageWell, hintBar, hintShadow;
    int scrim; // over a game's backdrop in dark mode: 0-255
};

struct pd_theme {
    int id;
    const char* label;
    uint32_t wash; // multiplied over a game's backdrop; 0xFFFFFF = none
    bool rainbow, seasonal;
    struct pd_look look[2]; // light, dark
};

extern const struct pd_theme pd_themes[];
extern const int pd_theme_count;
// The Game Boy games' own windows: Yellow, Red / Blue, PureRed / PureBlue, the Gen 2 games.
enum { PD_GB_LOOK_YELLOW, PD_GB_LOOK_RED, PD_GB_LOOK_PURE, PD_GB_LOOK_GEN2, PD_GB_LOOK_COUNT };
extern const struct pd_look pd_gb_looks[PD_GB_LOOK_COUNT][2];

extern const struct pd_look* pd_cur;
extern bool pd_dark; // DARK MODE, as pd_look_apply last saw it
// The backdrop's stripes and band as they stand now (a game's own, the theme's wash, dark mode's scrim).
extern uint32_t pd_backdrop_colors[3];
// A colour of a game's own backdrop art under the same wash and scrim.
uint32_t pd_backdrop_tint(uint32_t rgb);

// The summary's Separator: a dark line beside a light one (OptionLook.separatorDark / Light).
void pd_separator(struct pd_canvas* c, int x, int y, int len, bool vertical);
// A battle-screen colour under dark mode's scrim (BattlePlaceholder's night()).
uint32_t pd_battle_tint(uint32_t rgb);
// The BATTLE tab's background (BattleBackdrop).
void pd_battle_backdrop(struct pd_canvas* c);

struct pd_game;
struct pd_settings;
// What the screens are drawn in: [g] may be NULL (the library).
void pd_look_apply(const struct pd_game* g, const struct pd_settings* s);

#define PD_TITLE_FILL (pd_cur->titleFill)
#define PD_TITLE_TEXT (pd_cur->titleText)
#define PD_TITLE_SHADOW (pd_cur->titleShadow)
#define PD_LIST_FILL (pd_cur->listFill)
#define PD_ROW_SELECTED (pd_cur->rowSelected)
#define PD_LABEL (pd_cur->label)
#define PD_LABEL_SHADOW (pd_cur->labelShadow)
#define PD_VALUE (pd_cur->value)
#define PD_VALUE_SHADOW (pd_cur->valueShadow)
#define PD_MUTED (pd_cur->muted)
#define PD_MUTED_SHADOW (pd_cur->mutedShadow)
#define PD_DIVIDER (pd_cur->divider)
// Text straight on the backdrop.
#define PD_ON_BACKDROP (pd_cur->onBackdrop)
#define PD_ON_BACKDROP_SHADOW (pd_cur->onBackdropShadow)
// Dev's Game Boy look switches, kept for the code that asks: pd_gb_look is set while a Game Boy game's windows are on
// (pd_look_apply), and its ink / paper are that look's label and list fill.
extern int pd_gb_look;
// Hosts may select the dark window palette for a companion draw.
#define pd_dark_look pd_dark
#define PD_GB(a, b) (pd_gb_look ? (b) : (a))
extern uint32_t pd_gb_ink, pd_gb_paper;
#define PD_GB_INK pd_gb_ink
#define PD_GB_PAPER pd_gb_paper
// The backdrop's stripes and band, as pd_backdrop draws them.
#define PD_BACKDROP_A (pd_backdrop_colors[0])
#define PD_BACKDROP_B (pd_backdrop_colors[1])
#define PD_BACKDROP_BAND (pd_backdrop_colors[2])

// SUMMARY MINT in PokeDaisy's blues, the app's RETRO look (UiStyle.kt's retroStylePalette, 2026-11-22): a framed blue
// header over a white list window, full-width section bands, rows banded in [stripe], tabs and buttons as keys (a two-tone
// face standing on a [depth] edge). A host turns it on (pd_style_enable: the desktop cores); off, the windows are the
// FireRed OPTION screen's as before (the 3DS for now). A Game Boy game keeps its own windows either way.
struct pd_key {
    uint32_t outline, face, low, depth, text, shadow, shine; // shine PD_NO_SHADOW = none
    bool pressed;                                            // the open tab: it sits down on its edge
};
// A block of connected cells (CellColors: HG's stats and moves, Black's move plates): ruled in [line], rows in [a] and
// [b] by turn, a heading cell in [head] with [headText] on [headShadow].
struct pd_cells {
    uint32_t line, a, b, head, headText, headShadow;
};
struct pd_style_colors {
    uint32_t headerFill, headerText, headerShadow; // the header band over a list (OptionTitleWindow: headerFill / Layers)
    struct pd_layer header[2];
    uint32_t sectionFill, sectionText, sectionShadow, stripe, well;
    struct pd_key button, tab, tabOpen;
    struct pd_cells cells, foeCells; // your side's and the foe's (battle INFO, SUGGESTIONS)
};
// A cell: [fill] inside a 1-pixel [line]. Cells that share an edge overlap by a pixel, so it's drawn once.
void pd_cell(struct pd_canvas* c, int x, int y, int w, int h, uint32_t line, uint32_t fill);
extern const struct pd_style_colors* pd_style; // NULL = the OPTION look
void pd_style_enable(bool on);
// A key filling the box (the app's drawKey): its outline, the depth edge along the bottom, the two-tone face, the shine.
void pd_key(struct pd_canvas* c, int x, int y, int w, int h, const struct pd_key* k);
// A tab chip's face, its label's colour and shadow: a key in the style, the OPTION chip without.
void pd_chip_face(struct pd_canvas* c, int x, int y, int w, int h, bool open);
uint32_t pd_chip_text(bool open);
uint32_t pd_chip_shadow(bool open);
// A section's title (a settings group, a list's heading) at row (x, y) of a row [w] wide and [h] tall: the style's band
// across the row (StyledGroupTitle), else the OPTION screen's red title (Gen 1's black band). Returns the text's x.
void pd_section_title(struct pd_canvas* c, int x, int y, int w, int h, const char* title);

// A screen's or a list's title bar (the app's OptionTitleWindow: headerFill / headerLayers): the style's blue band, the
// title window without the style. Its text in PD_HEADER_TEXT / PD_HEADER_SHADOW. Returns the frame's width.
int pd_header_box(struct pd_canvas* c, int x, int y, int w, int h);
#define PD_HEADER_TEXT (pd_style ? pd_style->headerText : pd_cur->titleText)
#define PD_HEADER_SHADOW (pd_style ? pd_style->headerShadow : pd_cur->titleShadow)
#define PD_HEADER_FILL (pd_style ? pd_style->headerFill : pd_cur->titleFill)

// The white title window and the grey list window; each returns its frame's width.
int pd_title_box(struct pd_canvas* c, int x, int y, int w, int h, uint32_t fill);
int pd_list_box(struct pd_canvas* c, int x, int y, int w, int h);
// The battery in a title window: a green bolt while [charging], the level drawn (red at 15% and under), the percentage,
// ending at [end] with text at [ty]. pd_battery_width is how wide that is.
// TEXT SIZE SMALL's line between a list's rows (the bag's divider, on the grey lists): a pixel at [y]. Nothing at NORMAL.
void pd_row_divider(struct pd_canvas* c, int x, int y, int w);
void pd_battery_draw(struct pd_canvas* c, int end, int ty, int pct, bool charging);
int pd_battery_width(int pct, bool charging);
void pd_backdrop(struct pd_canvas* c);
// A coloured icon: 1 canvas pixel a pixel at scale 1, half size at 0 (every other pixel).
struct pd_art;
void ui_art_at(struct pd_canvas* c, const struct pd_art* art, int x, int y, int scale);
// Text centred in a row of height h at y: the capitals (rows 4-12 of the 16 px cell) and their shadow's row under them,
// 10 rows, in the middle. Centring the whole cell (+1) left every label two pixels low.
static inline int pd_text_y(int y, int h) {
    return y + (h - 18) / 2;
}

#endif
