// See pc_json.h.
#include "pc_json.h"
#include <stdlib.h>
#include <string.h>

struct parser {
  const char *p, *end;
  int depth;
};

static void skip_ws(struct parser *s) {
  while (s->p < s->end && (*s->p == ' ' || *s->p == '\n' || *s->p == '\r' || *s->p == '\t'))
    s->p++;
}

static void free_value(struct pc_json *v) {
  if (!v)
    return;
  free(v->string);
  for (int i = 0; i < v->count; i++) {
    free_value(&v->items[i]);
    if (v->keys)
      free(v->keys[i]);
  }
  free(v->items);
  free(v->keys);
}

static int hex4(const char *p) {
  int v = 0;
  for (int i = 0; i < 4; i++) {
    char c = p[i];
    v <<= 4;
    if (c >= '0' && c <= '9')
      v |= c - '0';
    else if (c >= 'a' && c <= 'f')
      v |= c - 'a' + 10;
    else if (c >= 'A' && c <= 'F')
      v |= c - 'A' + 10;
    else
      return -1;
  }
  return v;
}

static int put_utf8(char *o, unsigned cp) {
  if (cp < 0x80) {
    o[0] = (char)cp;
    return 1;
  }
  if (cp < 0x800) {
    o[0] = (char)(0xC0 | cp >> 6);
    o[1] = (char)(0x80 | (cp & 0x3F));
    return 2;
  }
  if (cp < 0x10000) {
    o[0] = (char)(0xE0 | cp >> 12);
    o[1] = (char)(0x80 | ((cp >> 6) & 0x3F));
    o[2] = (char)(0x80 | (cp & 0x3F));
    return 3;
  }
  o[0] = (char)(0xF0 | cp >> 18);
  o[1] = (char)(0x80 | ((cp >> 12) & 0x3F));
  o[2] = (char)(0x80 | ((cp >> 6) & 0x3F));
  o[3] = (char)(0x80 | (cp & 0x3F));
  return 4;
}

static char *parse_string(struct parser *s) {
  if (s->p >= s->end || *s->p != '"')
    return NULL;
  s->p++;
  const char *start = s->p;
  while (s->p < s->end && *s->p != '"')
    s->p += *s->p == '\\' && s->p + 1 < s->end ? 2 : 1;
  if (s->p >= s->end)
    return NULL;
  size_t n = (size_t)(s->p - start);
  char *out = malloc(n + 1), *o = out; // decoded is never longer than the escaped text
  if (!out)
    return NULL;
  for (const char *q = start; q < s->p; q++) {
    if (*q != '\\') {
      *o++ = *q;
      continue;
    }
    q++;
    switch (*q) {
    case 'n': *o++ = '\n'; break;
    case 't': *o++ = '\t'; break;
    case 'r': *o++ = '\r'; break;
    case 'b': *o++ = '\b'; break;
    case 'f': *o++ = '\f'; break;
    case 'u': {
      int cp = q + 4 < s->p ? hex4(q + 1) : -1;
      if (cp < 0) {
        free(out);
        return NULL;
      }
      q += 4;
      // A surrogate pair: 😀.
      if (cp >= 0xD800 && cp < 0xDC00 && q + 6 < s->p && q[1] == '\\' && q[2] == 'u') {
        int lo = hex4(q + 3);
        if (lo >= 0xDC00 && lo < 0xE000) {
          cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
          q += 6;
        }
      }
      o += put_utf8(o, (unsigned)cp); // at most 4 bytes for the 6 (or 12) escaped
      break;
    }
    default: *o++ = *q; break; // \" \\ \/
    }
  }
  *o = 0;
  s->p++;
  return out;
}

static bool parse_value(struct parser *s, struct pc_json *v);

static bool parse_list(struct parser *s, struct pc_json *v, bool object) {
  char close = object ? '}' : ']';
  s->p++;
  int cap = 0;
  for (;;) {
    skip_ws(s);
    if (s->p < s->end && *s->p == close && v->count == 0) {
      s->p++;
      return true;
    }
    if (v->count == cap) {
      cap = cap ? cap * 2 : 8;
      struct pc_json *items = realloc(v->items, sizeof *items * cap);
      if (!items)
        return false;
      v->items = items;
      if (object) {
        char **keys = realloc(v->keys, sizeof *keys * cap);
        if (!keys)
          return false;
        v->keys = keys;
      }
    }
    struct pc_json *item = &v->items[v->count];
    memset(item, 0, sizeof *item);
    if (object) {
      v->keys[v->count] = NULL;
      char *key = parse_string(s);
      if (!key)
        return false;
      v->keys[v->count] = key;
      skip_ws(s);
      if (s->p >= s->end || *s->p != ':') {
        v->count++;
        return false;
      }
      s->p++;
    }
    v->count++;
    if (!parse_value(s, item))
      return false;
    skip_ws(s);
    if (s->p < s->end && *s->p == ',') {
      s->p++;
      continue;
    }
    if (s->p < s->end && *s->p == close) {
      s->p++;
      return true;
    }
    return false;
  }
}

static bool parse_value(struct parser *s, struct pc_json *v) {
  skip_ws(s);
  if (s->p >= s->end || ++s->depth > 32)
    return false;
  bool ok = true;
  char c = *s->p;
  if (c == '{' || c == '[') {
    v->type = c == '{' ? PC_JSON_OBJECT : PC_JSON_ARRAY;
    ok = parse_list(s, v, c == '{');
  } else if (c == '"') {
    v->type = PC_JSON_STRING;
    ok = (v->string = parse_string(s)) != NULL;
  } else if (s->end - s->p >= 4 && !memcmp(s->p, "true", 4)) {
    v->type = PC_JSON_BOOL;
    v->boolean = true;
    s->p += 4;
  } else if (s->end - s->p >= 5 && !memcmp(s->p, "false", 5)) {
    v->type = PC_JSON_BOOL;
    s->p += 5;
  } else if (s->end - s->p >= 4 && !memcmp(s->p, "null", 4)) {
    v->type = PC_JSON_NULL;
    s->p += 4;
  } else if (c == '-' || (c >= '0' && c <= '9')) {
    char buf[64];
    size_t n = 0;
    while (s->p < s->end && n < sizeof buf - 1 && strchr("+-0123456789.eE", *s->p))
      buf[n++] = *s->p++;
    buf[n] = 0;
    v->type = PC_JSON_NUMBER;
    v->number = strtod(buf, NULL);
  } else
    ok = false;
  s->depth--;
  return ok;
}

struct pc_json *pc_json_parse(const char *text, size_t len) {
  if (!text || len > (8u << 20))
    return NULL;
  struct parser s = {text, text + len, 0};
  struct pc_json *v = calloc(1, sizeof *v);
  if (!v)
    return NULL;
  if (!parse_value(&s, v)) {
    free_value(v);
    free(v);
    return NULL;
  }
  return v;
}

void pc_json_free(struct pc_json *v) {
  free_value(v);
  free(v);
}

const struct pc_json *pc_json_get(const struct pc_json *obj, const char *key) {
  if (!obj || obj->type != PC_JSON_OBJECT)
    return NULL;
  for (int i = 0; i < obj->count; i++)
    if (!strcmp(obj->keys[i], key))
      return &obj->items[i];
  return NULL;
}
const struct pc_json *pc_json_at(const struct pc_json *arr, int i) {
  return arr && arr->type == PC_JSON_ARRAY && i >= 0 && i < arr->count ? &arr->items[i] : NULL;
}
int pc_json_len(const struct pc_json *arr) { return arr && arr->type == PC_JSON_ARRAY ? arr->count : 0; }
const char *pc_json_str(const struct pc_json *v, const char *fallback) {
  return v && v->type == PC_JSON_STRING ? v->string : fallback;
}
double pc_json_num(const struct pc_json *v, double fallback) {
  return v && v->type == PC_JSON_NUMBER ? v->number : fallback;
}
bool pc_json_bool(const struct pc_json *v) { return v && v->type == PC_JSON_BOOL && v->boolean; }
