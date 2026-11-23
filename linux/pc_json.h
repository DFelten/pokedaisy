// A small JSON reader for the PC games' companion snapshot (pc_games/pokedaisy_companion.rb): a tree in one block,
// strings decoded to UTF-8. Depth and size are capped; anything malformed is NULL.
#pragma once
#include <stdbool.h>
#include <stddef.h>

enum pc_json_type { PC_JSON_NULL, PC_JSON_BOOL, PC_JSON_NUMBER, PC_JSON_STRING, PC_JSON_ARRAY, PC_JSON_OBJECT };

struct pc_json {
  enum pc_json_type type;
  bool boolean;
  double number;
  char *string;           // PC_JSON_STRING
  struct pc_json *items;  // PC_JSON_ARRAY / OBJECT: count values
  char **keys;            // PC_JSON_OBJECT: count keys
  int count;
};

struct pc_json *pc_json_parse(const char *text, size_t len);
void pc_json_free(struct pc_json *v);

// Lookups that take NULL and the wrong type quietly: missing = NULL / the fallback.
const struct pc_json *pc_json_get(const struct pc_json *obj, const char *key);
const struct pc_json *pc_json_at(const struct pc_json *arr, int i);
int pc_json_len(const struct pc_json *arr);
const char *pc_json_str(const struct pc_json *v, const char *fallback);
double pc_json_num(const struct pc_json *v, double fallback);
bool pc_json_bool(const struct pc_json *v);
