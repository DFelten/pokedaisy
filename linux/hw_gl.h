// Hardware-rendered libretro cores (the mkxp-z core for PC fan games) on a host that draws with SDL_Renderer.
// The core gets an offscreen OpenGL / OpenGL ES context of its own (EGL, no window) and an FBO; after each
// retro_run the frame is read back into memory and handed to the host's normal frame texture, so the host's
// filters, overlay and menus stay as they are. Behind POCKET_PC_GAMES (CMake option, off by default).
#pragma once
#include "libretro.h"
#include <stdbool.h>
#include <stdint.h>

// RETRO_ENVIRONMENT_SET_HW_RENDER: true if a context of the asked type was made (the core then asks no further).
bool hwgl_set_hw_render(struct retro_hw_render_callback *cb);
bool hwgl_active(void);
// After retro_load_game: the FBO at the core's max size, then the core's context_reset.
bool hwgl_start(unsigned maxW, unsigned maxH);
// Around every retro_run / retro_serialize / retro_unserialize: our context current, then SDL's put back.
void hwgl_begin(void);
void hwgl_end(void);
// From video_refresh(RETRO_HW_FRAME_BUFFER_VALID, w, h): reads the frame (XRGB8888, top row first).
void hwgl_capture(unsigned w, unsigned h);
// The last captured frame (NULL if none), its size and pitch in bytes.
const uint32_t *hwgl_frame(unsigned *w, unsigned *h, size_t *pitch);
// The core's context_destroy (while its game is still loaded), then everything of ours freed.
void hwgl_context_destroy(void);
void hwgl_stop(void);
