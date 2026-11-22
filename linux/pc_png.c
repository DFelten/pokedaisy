// See pc_png.h. Chunks are checked for length only (no CRC): these are the player's own game files, and a bad one
// decodes to garbage or NULL, never past a buffer.
#include "pc_png.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

static uint32_t be32(const uint8_t *p) { return (uint32_t)p[0] << 24 | p[1] << 16 | p[2] << 8 | p[3]; }

static int paeth(int a, int b, int c) {
  int p = a + b - c, pa = abs(p - a), pb = abs(p - b), pc = abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

uint32_t *pc_png_decode(const uint8_t *d, size_t len, int *outW, int *outH) {
  static const uint8_t SIG[8] = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  if (len < 8 + 25 || memcmp(d, SIG, 8))
    return NULL;
  uint32_t w = 0, h = 0;
  int depth = 0, type = -1, interlace = 0;
  uint32_t palette[256];
  int paletteN = 0;
  memset(palette, 0, sizeof palette);
  int trnsGrey = -1, trnsR = -1, trnsG = -1, trnsB = -1;
  uint8_t *idat = NULL;
  size_t idatLen = 0, idatCap = 0;
  size_t at = 8;
  bool ended = false;
  while (at + 12 <= len && !ended) {
    uint32_t n = be32(d + at);
    const uint8_t *tag = d + at + 4, *body = d + at + 8;
    if (n > len - at - 12)
      break;
    if (!memcmp(tag, "IHDR", 4) && n >= 13) {
      w = be32(body);
      h = be32(body + 4);
      depth = body[8];
      type = body[9];
      interlace = body[12];
    } else if (!memcmp(tag, "PLTE", 4)) {
      paletteN = (int)(n / 3 > 256 ? 256 : n / 3);
      for (int i = 0; i < paletteN; i++)
        palette[i] = 0xFF000000u | body[i * 3] << 16 | body[i * 3 + 1] << 8 | body[i * 3 + 2];
    } else if (!memcmp(tag, "tRNS", 4)) {
      if (type == 3)
        for (uint32_t i = 0; i < n && i < 256; i++)
          palette[i] = (palette[i] & 0xFFFFFF) | (uint32_t)body[i] << 24;
      else if (type == 0 && n >= 2)
        trnsGrey = body[1];
      else if (type == 2 && n >= 6) {
        trnsR = body[1];
        trnsG = body[3];
        trnsB = body[5];
      }
    } else if (!memcmp(tag, "IDAT", 4)) {
      if (idatLen + n > idatCap) {
        size_t cap = (idatLen + n) * 2;
        uint8_t *grown = realloc(idat, cap);
        if (!grown) {
          free(idat);
          return NULL;
        }
        idat = grown;
        idatCap = cap;
      }
      memcpy(idat + idatLen, body, n);
      idatLen += n;
    } else if (!memcmp(tag, "IEND", 4))
      ended = true;
    at += 12 + n;
  }
  int channels = type == 0 ? 1 : type == 2 ? 3 : type == 3 ? 1 : type == 4 ? 2 : type == 6 ? 4 : 0;
  bool depthOk = type == 3 ? (depth == 1 || depth == 2 || depth == 4 || depth == 8) : depth == 8;
  if (!idat || !channels || !depthOk || interlace || !w || !h || w > 4096 || h > 4096) {
    free(idat);
    return NULL;
  }
  size_t rowBytes = ((size_t)w * channels * depth + 7) / 8, bpp = (size_t)(channels * depth + 7) / 8;
  uLongf rawLen = (uLongf)((rowBytes + 1) * h);
  uint8_t *raw = malloc(rawLen);
  uint32_t *px = malloc((size_t)w * h * 4);
  if (!raw || !px || uncompress(raw, &rawLen, idat, (uLong)idatLen) != Z_OK || rawLen != (rowBytes + 1) * h) {
    free(idat);
    free(raw);
    free(px);
    return NULL;
  }
  free(idat);
  // Undo the filters in place: each row's first byte names its filter.
  for (uint32_t y = 0; y < h; y++) {
    uint8_t *row = raw + y * (rowBytes + 1) + 1, *up = y ? row - (rowBytes + 1) : NULL;
    int f = row[-1];
    for (size_t x = 0; x < rowBytes; x++) {
      int a = x >= bpp ? row[x - bpp] : 0, b = up ? up[x] : 0, c = up && x >= bpp ? up[x - bpp] : 0;
      int add = f == 1 ? a : f == 2 ? b : f == 3 ? (a + b) / 2 : f == 4 ? paeth(a, b, c) : 0;
      row[x] = (uint8_t)(row[x] + add);
    }
  }
  for (uint32_t y = 0; y < h; y++) {
    const uint8_t *row = raw + y * (rowBytes + 1) + 1;
    uint32_t *out = px + (size_t)y * w;
    for (uint32_t x = 0; x < w; x++) {
      uint32_t p;
      if (type == 3) {
        int bit = (int)(x * depth), v = (row[bit / 8] >> (8 - depth - bit % 8)) & ((1 << depth) - 1);
        p = v < paletteN ? palette[v] : 0;
      } else if (type == 0) {
        int g = row[x];
        p = (g == trnsGrey ? 0u : 0xFF000000u) | (uint32_t)g << 16 | g << 8 | g;
      } else if (type == 4) {
        int g = row[x * 2];
        p = (uint32_t)row[x * 2 + 1] << 24 | (uint32_t)g << 16 | g << 8 | g;
      } else if (type == 2) {
        int r = row[x * 3], g = row[x * 3 + 1], b = row[x * 3 + 2];
        p = (r == trnsR && g == trnsG && b == trnsB ? 0u : 0xFF000000u) | (uint32_t)r << 16 | g << 8 | b;
      } else
        p = (uint32_t)row[x * 4 + 3] << 24 | (uint32_t)row[x * 4] << 16 | row[x * 4 + 1] << 8 | row[x * 4 + 2];
      out[x] = p;
    }
  }
  free(raw);
  *outW = (int)w;
  *outH = (int)h;
  return px;
}

uint32_t *pc_png_read(const char *path, int *w, int *h) {
  FILE *f = fopen(path, "rb");
  if (!f)
    return NULL;
  fseek(f, 0, SEEK_END);
  long n = ftell(f);
  fseek(f, 0, SEEK_SET);
  uint32_t *px = NULL;
  if (n > 0 && n < (32L << 20)) {
    uint8_t *d = malloc((size_t)n);
    if (d && fread(d, 1, (size_t)n, f) == (size_t)n)
      px = pc_png_decode(d, (size_t)n, w, h);
    free(d);
  }
  fclose(f);
  return px;
}
