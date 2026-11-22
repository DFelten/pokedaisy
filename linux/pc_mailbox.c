// See pc_mailbox.h. Layout (pokedaisy_companion.rb): magic[16], seq u32 LE (odd while writing), len u32 LE, JSON.
#include "pc_mailbox.h"
#include <SDL.h>
#include <stdlib.h>
#include <string.h>

#define HEADER 24
#define CAPACITY (256u * 1024u)
static const char MAGIC[16] = {'P', 'K', 'D', 'A', 'I', 'S', 'Y', '-', 'M', 'B', 'X', '-', '0', '0', '0', '1'};

static size_t at = (size_t)-1;
static uint32_t lastSeq;
static uint64_t lastScan;
static char *copy;

static uint32_t u32(const uint8_t *p) { return p[0] | p[1] << 8 | p[2] << 16 | (uint32_t)p[3] << 24; }

static bool valid(const uint8_t *mem, size_t size, size_t off) {
  return off + HEADER <= size && !memcmp(mem + off, MAGIC, 16) && u32(mem + off + 20) <= CAPACITY &&
         off + HEADER + u32(mem + off + 20) <= size;
}

void pc_mailbox_reset(void) {
  at = (size_t)-1;
  lastSeq = 0;
  lastScan = 0;
}

const char *pc_mailbox_read(const uint8_t *mem, size_t size, size_t *len, uint32_t *seq) {
  if (!mem || size < HEADER)
    return NULL;
  if (at == (size_t)-1 || !valid(mem, size, at)) {
    uint64_t now = SDL_GetTicks64();
    if (lastScan && now - lastScan < 2000)
      return NULL;
    lastScan = now;
    at = (size_t)-1;
    // The buffer is a Ruby String's heap block: 8-byte aligned in the WASM heap. Of several matches (an old
    // buffer not yet reused), the one with the highest sequence is the live one.
    uint32_t best = 0;
    for (size_t i = 0; i + HEADER <= size; i += 8) {
      if (mem[i] != 'P' || !valid(mem, size, i))
        continue;
      uint32_t s = u32(mem + i + 16);
      if (s >= best) {
        best = s;
        at = i;
      }
    }
    if (at == (size_t)-1)
      return NULL;
  }
  uint32_t s = u32(mem + at + 16);
  if ((s & 1) || s == lastSeq) // being written, or nothing new
    return NULL;
  uint32_t n = u32(mem + at + 20);
  if (!copy && !(copy = malloc(CAPACITY)))
    return NULL;
  memcpy(copy, mem + at + HEADER, n);
  if (u32(mem + at + 16) != s) // rewritten while we copied: next time
    return NULL;
  lastSeq = s;
  *len = n;
  if (seq)
    *seq = s;
  return copy;
}
