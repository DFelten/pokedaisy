// A small PNG reader for the PC games' companion (pc_companion.c): the icons RPG Maker games keep as PNG files.
// 8-bit grey / grey + alpha / RGB / RGBA, and palette at 1 / 2 / 4 / 8 bits with tRNS; no interlacing (refused).
#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Decoded pixels: w * h of 0xAARRGGBB, top row first. free() them.
uint32_t *pc_png_read(const char *path, int *w, int *h);
uint32_t *pc_png_decode(const uint8_t *data, size_t len, int *w, int *h);
