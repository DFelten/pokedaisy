// Checks app/src/main/cpp/pk_pc_fit.h, the PC game screen's sharp scaling:
//   cc -I app/src/main/cpp -o /tmp/pc_fit_test native-capture/pc_probe/pc_fit_test.c && /tmp/pc_fit_test
#include <stdio.h>
#include "pk_pc_fit.h"

static int fails;

static void check(const char *what, PkPcFit f, int x, int y, int w, int h, int prescale, int exact) {
    if (f.x != x || f.y != y || f.w != w || f.h != h || f.prescale != prescale || f.exact != exact) {
        printf("FAIL %s: got %d,%d %dx%d k%d exact%d, want %d,%d %dx%d k%d exact%d\n", what, f.x, f.y, f.w, f.h, f.prescale,
               f.exact, x, y, w, h, prescale, exact);
        fails++;
    }
}

int main(void) {
    // Infinite Fusion on the Thor: 2.81x, drawn 3x with hard pixels then smoothed down.
    check("thor", pk_pc_fit(512, 384, 1920, 1080, 16384), 240, 0, 1440, 1080, 3, 0);
    // A whole-number fit: hard pixels, one step.
    check("exact", pk_pc_fit(640, 480, 1280, 960, 16384), 0, 0, 1280, 960, 2, 1);
    check("same size", pk_pc_fit(640, 480, 640, 480, 16384), 0, 0, 640, 480, 1, 1);
    // A tall view (an upright phone): as wide as the view.
    check("upright", pk_pc_fit(512, 384, 1080, 2400, 16384), 0, 795, 1080, 810, 3, 0);
    // Smaller than the picture: smoothed straight down.
    check("small", pk_pc_fit(640, 480, 320, 240, 16384), 0, 0, 320, 240, 1, 0);
    check("smaller", pk_pc_fit(640, 480, 300, 240, 16384), 0, 7, 300, 225, 1, 0);
    // A GPU with small textures: the biggest whole number it takes.
    check("max texture", pk_pc_fit(512, 384, 3840, 2160, 2048), 480, 0, 2880, 2160, 4, 0);
    check("tiny max texture", pk_pc_fit(512, 384, 1920, 1080, 600), 240, 0, 1440, 1080, 1, 0);
    check("nothing", pk_pc_fit(0, 0, 1920, 1080, 16384), 0, 0, 1920, 1080, 1, 0);
    if (!fails) printf("pc_fit: ok\n");
    return fails != 0;
}
