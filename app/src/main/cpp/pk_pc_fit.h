// pk_pc_fit.h - where a PC fan game's picture goes on the screen, and how it gets there sharp (pk_pc.c's pcFrame).
//
// The picture keeps its shape at the largest size the view holds (Infinite Fusion's 512 x 384 on the Thor's 1920 x 1080:
// 1440 x 1080, 2.81x). A smooth stretch by 2.81 blurs every pixel's edge, so the picture is first drawn [prescale]
// times as big with hard pixels (3x: 1536 x 1152), then smoothed down the last step to its size - each pixel's edge
// blends over one screen pixel at most ("sharp bilinear"). A whole-number scale needs no second step ([exact]).
// Pure maths, no GL: native-capture/pc_probe/pc_fit_test.c checks it.
#ifndef PK_PC_FIT_H
#define PK_PC_FIT_H

typedef struct {
    int x, y, w, h;  // the picture's place in the view, in pixels from the view's bottom left
    int prescale;    // the whole-number size it's drawn at with hard pixels first (1 = straight to the view)
    int exact;       // 1: the view size is prescale times the picture: one hard-pixel draw, no smoothing
} PkPcFit;

/** [w] x [h] into [viewW] x [viewH], with [maxTex] the largest texture side the GPU takes (its prescaled copy). */
static inline PkPcFit pk_pc_fit(int w, int h, int viewW, int viewH, int maxTex) {
    PkPcFit f = {0, 0, viewW, viewH, 1, 0};
    if (w <= 0 || h <= 0 || viewW <= 0 || viewH <= 0) return f;
    // Largest size with the picture's shape: as wide as the view, else as tall.
    long outW = viewW, outH = (long) viewW * h / w;
    if (outH > viewH) {
        outH = viewH;
        outW = (long) viewH * w / h;
    }
    f.w = (int) outW;
    f.h = (int) outH;
    f.x = (viewW - f.w) / 2;
    f.y = (viewH - f.h) / 2;
    if (f.w % w == 0 && f.h % h == 0 && f.w / w == f.h / h) {
        f.prescale = f.w / w;
        f.exact = 1;
        return f;
    }
    // Up to the next whole number past the size on screen (never under it: smoothing up would blur again).
    int k = (f.w + w - 1) / w;
    while (k > 1 && (w * k > maxTex || h * k > maxTex)) k--;
    f.prescale = k < 1 ? 1 : k;
    return f;
}

#endif
