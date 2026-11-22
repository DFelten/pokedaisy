/* pc_probe: a headless libretro frontend for the mkxp-z core (spike).
 * EGL surfaceless (Mesa llvmpipe) GL context + an FBO for the core's hw render.
 * Build: cc -O2 -I../../third_party/mgba/src/platform/libretro -o pc_probe pc_probe.c -ldl -lEGL -lGL
 * Usage: pc_probe CORE.so GAME_PATH SYSDIR SAVEDIR SCRIPT
 * Android (the Thor over adb): $NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android29-clang -O2
 *   -I../../third_party/mgba/src/platform/libretro -o pc_probe pc_probe.c -ldl -lEGL -lGLESv3
 * SCRIPT lines: "run N" | "press BTN N" (hold a RetroPad button N frames) | "key KEY N" (a keyboard key: RETURN, ESCAPE,
 * BACKSPACE or one character) | "shot FILE.ppm"
 *               | "save FILE" | "load FILE" | "ram FILE" | "mbx FILE" | "cheat RUBY" | "time" | "mem" | "opt KEY VALUE"
 */
#define _GNU_SOURCE
#include <ctype.h>
#include <dlfcn.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#ifdef __ANDROID__
/* Android (the Thor, adb shell): GLES 3.2 on the device's own driver, a 1x1 pbuffer as the surface. */
#include <GLES3/gl32.h>
#define GL_RGBA8_OR_ES GL_RGBA8
#else
#include <GL/gl.h>
#include <GL/glext.h>
#endif
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "libretro.h"

static void (*p_init)(void), (*p_deinit)(void), (*p_run)(void), (*p_reset)(void), (*p_unload)(void);
static void (*p_set_env)(retro_environment_t), (*p_set_video)(retro_video_refresh_t),
    (*p_set_audio)(retro_audio_sample_t), (*p_set_audio_batch)(retro_audio_sample_batch_t),
    (*p_set_poll)(retro_input_poll_t), (*p_set_state)(retro_input_state_t);
static bool (*p_load)(const struct retro_game_info *);
static void (*p_av)(struct retro_system_av_info *);
static size_t (*p_ser_size)(void);
static bool (*p_ser)(void *, size_t), (*p_unser)(const void *, size_t);
static void *(*p_mem)(unsigned);
static void (*p_cheat)(unsigned, bool, const char *); static unsigned ncheat;
static size_t (*p_mem_size)(unsigned);

static const char *sysdir, *savedir;
static struct retro_hw_render_callback hw;
static GLuint fbo, tex, rb;
static int fbw = 640, fbh = 480, lastw, lasth;
static unsigned held; static int held_frames;
static long audio_frames, video_frames, dupes;
static retro_audio_callback_t audio_cb;
static retro_keyboard_event_t keyboard_cb;
static struct { char k[96], v[96]; } opts[32]; static int nopts;
static struct { char k[128], v[64]; } defs[512]; static int ndefs;

#ifdef __ANDROID__
typedef void (*PFNGLGENFRAMEBUFFERSPROC)(GLsizei, GLuint *); typedef void (*PFNGLBINDFRAMEBUFFERPROC)(GLenum, GLuint);
typedef void (*PFNGLFRAMEBUFFERTEXTURE2DPROC)(GLenum, GLenum, GLenum, GLuint, GLint); typedef void (*PFNGLGENRENDERBUFFERSPROC)(GLsizei, GLuint *);
typedef void (*PFNGLBINDRENDERBUFFERPROC)(GLenum, GLuint); typedef void (*PFNGLRENDERBUFFERSTORAGEPROC)(GLenum, GLenum, GLsizei, GLsizei);
typedef void (*PFNGLFRAMEBUFFERRENDERBUFFERPROC)(GLenum, GLenum, GLenum, GLuint); typedef GLenum (*PFNGLCHECKFRAMEBUFFERSTATUSPROC)(GLenum);
#endif
static PFNGLGENFRAMEBUFFERSPROC glGenFramebuffers_; static PFNGLBINDFRAMEBUFFERPROC glBindFramebuffer_;
static PFNGLFRAMEBUFFERTEXTURE2DPROC glFramebufferTexture2D_; static PFNGLGENRENDERBUFFERSPROC glGenRenderbuffers_;
static PFNGLBINDRENDERBUFFERPROC glBindRenderbuffer_; static PFNGLRENDERBUFFERSTORAGEPROC glRenderbufferStorage_;
static PFNGLFRAMEBUFFERRENDERBUFFERPROC glFramebufferRenderbuffer_; static PFNGLCHECKFRAMEBUFFERSTATUSPROC glCheckFramebufferStatus_;

static void logcb(enum retro_log_level l, const char *fmt, ...) {
  va_list ap; va_start(ap, fmt); fprintf(stderr, "[core %d] ", l); vfprintf(stderr, fmt, ap); va_end(ap);
}
static uintptr_t get_fb(void) { return fbo; }
#ifdef __ANDROID__
static void *gles_lib;
static retro_proc_address_t get_proc(const char *s) {
  /* core GLES functions from the library itself; extensions through EGL */
  void *f = gles_lib ? dlsym(gles_lib, s) : NULL;
  return (retro_proc_address_t)(f ? f : (void *)eglGetProcAddress(s));
}
#else
static retro_proc_address_t get_proc(const char *s) { return (retro_proc_address_t)eglGetProcAddress(s); }
#endif
static void mem_line(const char *tag) {
  /* resident memory now and at its peak (Linux / Android) */
  FILE *f = fopen("/proc/self/status", "r"); char l[256], rss[64] = "?", hwm[64] = "?";
  if (!f) return;
  while (fgets(l, sizeof l, f)) {
    if (!strncmp(l, "VmRSS:", 6)) sscanf(l + 6, " %63[^\n]", rss);
    if (!strncmp(l, "VmHWM:", 6)) sscanf(l + 6, " %63[^\n]", hwm);
  }
  fclose(f);
  fprintf(stderr, "probe: mem %s: rss %s, peak %s\n", tag, rss, hwm);
}

static bool env(unsigned cmd, void *data) {
  switch (cmd & 0xFFFF) {
  case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: ((struct retro_log_callback *)data)->log = logcb; return true;
  case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: *(const char **)data = sysdir; return true;
  case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: *(const char **)data = savedir; return true;
  case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: return *(enum retro_pixel_format *)data == RETRO_PIXEL_FORMAT_XRGB8888;
  case RETRO_ENVIRONMENT_SET_HW_RENDER: {
    struct retro_hw_render_callback *h = data;
#ifdef __ANDROID__
    bool es = h->context_type == RETRO_HW_CONTEXT_OPENGLES3 ||
              (h->context_type == RETRO_HW_CONTEXT_OPENGLES_VERSION && h->version_major == 3 && h->version_minor <= 2);
    if (!es) return false;
#else
    if (h->context_type != RETRO_HW_CONTEXT_OPENGL_CORE || h->version_major > 3 || (h->version_major == 3 && h->version_minor > 3)) return false;
#endif
    hw = *h; h->get_current_framebuffer = get_fb; h->get_proc_address = get_proc; hw = *h;
    fprintf(stderr, "probe: hw render type %d %u.%u\n", h->context_type, h->version_major, h->version_minor);
    return true;
  }
  case RETRO_ENVIRONMENT_GET_CAN_DUPE: *(bool *)data = true; return true;
  case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION: *(unsigned *)data = 2; return true;
  case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2: {
    const struct retro_core_options_v2 *o = data;
    ndefs = 0;
    for (const struct retro_core_option_v2_definition *d = o->definitions; d && d->key && ndefs < 512; d++) {
      snprintf(defs[ndefs].k, sizeof defs[ndefs].k, "%s", d->key);
      snprintf(defs[ndefs].v, sizeof defs[ndefs].v, "%s", d->default_value ? d->default_value : (d->values[0].value ? d->values[0].value : ""));
      ndefs++;
    }
    fprintf(stderr, "probe: %d core options\n", ndefs);
    return true;
  }
  case RETRO_ENVIRONMENT_SET_CORE_OPTIONS: case RETRO_ENVIRONMENT_SET_VARIABLES: return true;
  case RETRO_ENVIRONMENT_GET_VARIABLE: {
    struct retro_variable *v = data;
    for (int i = 0; i < nopts; i++) if (!strcmp(opts[i].k, v->key)) { v->value = opts[i].v; return true; }
    for (int i = 0; i < ndefs; i++) if (!strcmp(defs[i].k, v->key)) { v->value = defs[i].v; return true; }
    fprintf(stderr, "probe: unknown option %s\n", v->key);
    v->value = NULL; return false;
  }
  case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: *(bool *)data = false; return true;
  case RETRO_ENVIRONMENT_SET_AUDIO_CALLBACK: audio_cb = ((struct retro_audio_callback *)data)->callback; return true;
  case RETRO_ENVIRONMENT_SET_FRAME_TIME_CALLBACK: return false;
  case RETRO_ENVIRONMENT_GET_TARGET_REFRESH_RATE: *(float *)data = 60.0f; return true;
  case RETRO_ENVIRONMENT_SET_GEOMETRY: case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: return true;
  case RETRO_ENVIRONMENT_SET_MESSAGE: fprintf(stderr, "probe: message: %s\n", ((struct retro_message *)data)->msg); return true;
  case RETRO_ENVIRONMENT_SET_MESSAGE_EXT: fprintf(stderr, "probe: message: %s\n", ((struct retro_message_ext *)data)->msg); return true;
  case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION: *(unsigned *)data = 1; return true;
  case RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK: keyboard_cb = ((struct retro_keyboard_callback *)data)->callback; return true;
  case RETRO_ENVIRONMENT_SHUTDOWN: fprintf(stderr, "probe: core asked to shut down\n"); exit(3);
  default: return false;
  }
}
static void video(const void *d, unsigned w, unsigned h, size_t p) {
  (void)p; video_frames++;
  if (!d) { dupes++; return; }
  lastw = w; lasth = h;
}
static void audio1(int16_t l, int16_t r) { (void)l; (void)r; audio_frames++; }
static size_t audiob(const int16_t *d, size_t f) { (void)d; audio_frames += f; return f; }
static void poll(void) {}
static int16_t istate(unsigned port, unsigned dev, unsigned idx, unsigned id) {
  (void)idx;
  if (port || dev != RETRO_DEVICE_JOYPAD) return 0;
  if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t)held;
  return (held >> id) & 1;
}

static void make_fbo(int w, int h) {
  glGenFramebuffers_(1, &fbo); glBindFramebuffer_(GL_FRAMEBUFFER, fbo);
  glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex);
  glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
  glFramebufferTexture2D_(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
  glGenRenderbuffers_(1, &rb); glBindRenderbuffer_(GL_RENDERBUFFER, rb);
  glRenderbufferStorage_(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h);
  glFramebufferRenderbuffer_(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rb);
  if (glCheckFramebufferStatus_(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { fprintf(stderr, "probe: fbo incomplete\n"); exit(1); }
  glBindFramebuffer_(GL_FRAMEBUFFER, 0);
}

static void shot(const char *path) {
  int w = lastw ? lastw : fbw, h = lasth ? lasth : fbh;
  unsigned char *px = malloc((size_t)w * h * 4);
  glBindFramebuffer_(GL_FRAMEBUFFER, fbo);
  glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px);
  glBindFramebuffer_(GL_FRAMEBUFFER, 0);
  FILE *f = fopen(path, "wb"); fprintf(f, "P6\n%d %d\n255\n", w, h);
  /* bottom_left_origin = false: row 0 of the FBO is the top */
  for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) fwrite(px + ((size_t)y * w + x) * 4, 1, 3, f);
  fclose(f); free(px);
  fprintf(stderr, "probe: shot %s (%dx%d)\n", path, w, h);
}

static double now(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return t.tv_sec + t.tv_nsec / 1e9; }
static double run_time; static long run_frames;
static void frame(void) {
  if (held_frames > 0 && --held_frames == 0) held = 0;
  double t = now(); p_run(); if (audio_cb) audio_cb(); run_time += now() - t; run_frames++;
}

static int button(const char *s) {
  static const char *n[] = {"B","Y","SELECT","START","UP","DOWN","LEFT","RIGHT","A","X","L","R","L2","R2","L3","R3"};
  for (int i = 0; i < 16; i++) if (!strcmp(n[i], s)) return i;
  return -1;
}

int main(int argc, char **argv) {
  if (argc < 6) { fprintf(stderr, "usage: pc_probe CORE GAME SYSDIR SAVEDIR SCRIPT\n"); return 2; }
  sysdir = argv[3]; savedir = argv[4];
  void *h = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL); if (!h) { fprintf(stderr, "%s\n", dlerror()); return 1; }
#define S(v, n) *(void **)&v = dlsym(h, n); if (!v) { fprintf(stderr, "missing %s\n", n); return 1; }
  S(p_init, "retro_init") S(p_unload, "retro_unload_game") S(p_deinit, "retro_deinit") S(p_run, "retro_run") S(p_reset, "retro_reset")
  S(p_set_env, "retro_set_environment") S(p_set_video, "retro_set_video_refresh") S(p_set_audio, "retro_set_audio_sample")
  S(p_set_audio_batch, "retro_set_audio_sample_batch") S(p_set_poll, "retro_set_input_poll") S(p_set_state, "retro_set_input_state")
  S(p_load, "retro_load_game") S(p_av, "retro_get_system_av_info") S(p_ser_size, "retro_serialize_size")
  S(p_ser, "retro_serialize") S(p_unser, "retro_unserialize") S(p_mem, "retro_get_memory_data") S(p_cheat, "retro_cheat_set") S(p_mem_size, "retro_get_memory_size")

#ifdef __ANDROID__
  gles_lib = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
  EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
  if (!eglInitialize(dpy, NULL, NULL)) { fprintf(stderr, "egl init failed\n"); return 1; }
  eglBindAPI(EGL_OPENGL_ES_API);
  EGLint cfgattr[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                      EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE};
  EGLConfig cfg; EGLint n; eglChooseConfig(dpy, cfgattr, &cfg, 1, &n);
  EGLint ctxattr[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 2, EGL_NONE};
  EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxattr);
  EGLint pbattr[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
  EGLSurface pb = n ? eglCreatePbufferSurface(dpy, cfg, pbattr) : EGL_NO_SURFACE;
  if (!ctx || !eglMakeCurrent(dpy, pb, pb, ctx)) { fprintf(stderr, "egl ctx failed 0x%x\n", eglGetError()); return 1; }
#else
  EGLDisplay dpy = eglGetPlatformDisplay(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, NULL);
  if (!eglInitialize(dpy, NULL, NULL)) { fprintf(stderr, "egl init failed\n"); return 1; }
  eglBindAPI(EGL_OPENGL_API);
  EGLint cfgattr[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT, EGL_NONE};
  EGLConfig cfg; EGLint n; eglChooseConfig(dpy, cfgattr, &cfg, 1, &n);
  EGLint ctxattr[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 3,
                      EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT, EGL_NONE};
  EGLContext ctx = eglCreateContext(dpy, n ? cfg : NULL, EGL_NO_CONTEXT, ctxattr);
  if (!ctx || !eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx)) { fprintf(stderr, "egl ctx failed\n"); return 1; }
#endif
  fprintf(stderr, "probe: GL %s / %s\n", glGetString(GL_VERSION), glGetString(GL_RENDERER));
#define G(v, n) *(void **)&v = (void *)get_proc(n);
  G(glGenFramebuffers_, "glGenFramebuffers") G(glBindFramebuffer_, "glBindFramebuffer") G(glFramebufferTexture2D_, "glFramebufferTexture2D")
  G(glGenRenderbuffers_, "glGenRenderbuffers") G(glBindRenderbuffer_, "glBindRenderbuffer") G(glRenderbufferStorage_, "glRenderbufferStorage")
  G(glFramebufferRenderbuffer_, "glFramebufferRenderbuffer") G(glCheckFramebufferStatus_, "glCheckFramebufferStatus")
  GLuint vao; void (*genva)(GLsizei, GLuint *) = (void *)get_proc("glGenVertexArrays");
  void (*bindva)(GLuint) = (void *)get_proc("glBindVertexArray"); genva(1, &vao); bindva(vao);

  FILE *sf = fopen(argv[5], "r"); char line[8192];
  /* options first: lines "opt K V" before anything else */
  while (fgets(line, sizeof line, sf)) {
    char k[96], v[96];
    if (sscanf(line, "opt %95s %95s", k, v) == 2 && nopts < 32) { strcpy(opts[nopts].k, k); strcpy(opts[nopts].v, v); nopts++; }
  }
  rewind(sf);

  p_set_env(env); p_set_video(video); p_set_audio(audio1); p_set_audio_batch(audiob); p_set_poll(poll); p_set_state(istate);
  p_init();
  struct retro_game_info gi = {argv[2], NULL, 0, NULL};
  double t0 = now();
  if (!p_load(&gi)) { fprintf(stderr, "probe: load failed\n"); return 1; }
  struct retro_system_av_info av; p_av(&av);
  fbw = av.geometry.max_width; fbh = av.geometry.max_height;
  fprintf(stderr, "probe: av %ux%u (max %ux%u) %.2f fps %.0f Hz, load %.2fs\n", av.geometry.base_width, av.geometry.base_height,
          av.geometry.max_width, av.geometry.max_height, av.timing.fps, av.timing.sample_rate, now() - t0);
  make_fbo(fbw, fbh);
  if (hw.context_reset) hw.context_reset();

  while (fgets(line, sizeof line, sf)) {
    char a[256], b[256]; int k;
    if (sscanf(line, "run %d", &k) == 1) { for (int i = 0; i < k; i++) frame(); }
    else if (sscanf(line, "key %31s %d", a, &k) == 2) {
      // A keyboard key held k frames: RETURN, ESCAPE, BACKSPACE or one character (RETROK_* = ASCII for those).
      unsigned code = !strcmp(a, "RETURN") ? 13 : !strcmp(a, "ESCAPE") ? 27 : !strcmp(a, "BACKSPACE") ? 8 : (unsigned char)tolower((unsigned char)a[0]);
      if (!keyboard_cb) {
        fprintf(stderr, "probe: the core has no keyboard callback\n");
        continue;
      }
      keyboard_cb(true, code, code < 128 ? code : 0, 0);
      for (int i = 0; i < k; i++) frame();
      keyboard_cb(false, code, 0, 0);
      frame();
    } else if (sscanf(line, "press %31s %d", a, &k) == 2) { int bt = button(a); held = 1u << bt; held_frames = k; for (int i = 0; i < k; i++) frame(); held = 0; }
    else if (sscanf(line, "shot %255s", a) == 1) shot(a);
    else if (sscanf(line, "save %255s", a) == 1) {
      size_t sz = p_ser_size(); void *buf = malloc(sz); double t = now(); bool ok = p_ser(buf, sz);
      fprintf(stderr, "probe: serialize %s, %zu bytes, %.1f ms\n", ok ? "ok" : "FAILED", sz, (now() - t) * 1e3);
      FILE *f = fopen(a, "wb"); fwrite(buf, 1, sz, f); fclose(f); free(buf); fprintf(stderr, "probe: state written\n");
    } else if (sscanf(line, "load %255s", a) == 1) {
      FILE *f = fopen(a, "rb"); fseek(f, 0, SEEK_END); long sz = ftell(f); rewind(f); void *buf = malloc(sz);
      size_t got = fread(buf, 1, sz, f); fclose(f); double t = now(); bool ok = got == (size_t)sz && p_unser(buf, sz);
      fprintf(stderr, "probe: unserialize %s, %.1f ms\n", ok ? "ok" : "FAILED", (now() - t) * 1e3); free(buf);
    } else if (!strncmp(line, "cheat ", 6)) {
      line[strcspn(line, "\n")] = 0;
      p_cheat(ncheat++, true, line + 6);
      fprintf(stderr, "probe: cheat %u: %s\n", ncheat - 1, line + 6);
    } else if (sscanf(line, "ram %255s", a) == 1) {
      void *m = p_mem(RETRO_MEMORY_SYSTEM_RAM); size_t sz = p_mem_size(RETRO_MEMORY_SYSTEM_RAM);
      FILE *f = fopen(a, "wb"); if (m) fwrite(m, 1, sz, f); fclose(f);
      fprintf(stderr, "probe: ram %zu bytes -> %s\n", sz, a);
    } else if (sscanf(line, "mbx %255s", a) == 1) {
      /* the companion's mailbox (pokedaisy_companion.rb): found once by its magic, then read in place */
      static size_t at = (size_t)-1;
      const unsigned char *m = p_mem(RETRO_MEMORY_SYSTEM_RAM); size_t sz = p_mem_size(RETRO_MEMORY_SYSTEM_RAM);
      static const char magic[] = "PKDAISY-MBX-0001";
      double t = now(); int found = 0;
      if (m && (at == (size_t)-1 || at + 24 > sz || memcmp(m + at, magic, 16))) {
        at = (size_t)-1;
        for (size_t i = 0; i + 24 <= sz; i += 1) if (m[i] == 'P' && !memcmp(m + i, magic, 16)) {
          unsigned seq = m[i+16] | m[i+17] << 8 | m[i+18] << 16 | (unsigned)m[i+19] << 24;
          unsigned len = m[i+20] | m[i+21] << 8 | m[i+22] << 16 | (unsigned)m[i+23] << 24;
          found++;
          fprintf(stderr, "probe: magic at 0x%zx seq %u len %u\n", i, seq, len);
          if (seq && len && len < 262144) at = i;
        }
        fprintf(stderr, "probe: scan %.1f ms, %d candidates\n", (now() - t) * 1e3, found);
      }
      if (m && at != (size_t)-1) {
        unsigned seq = m[at+16] | m[at+17] << 8 | m[at+18] << 16 | (unsigned)m[at+19] << 24;
        unsigned len = m[at+20] | m[at+21] << 8 | m[at+22] << 16 | (unsigned)m[at+23] << 24;
        FILE *f = fopen(a, "wb"); fwrite(m + at + 24, 1, len, f); fclose(f);
        fprintf(stderr, "probe: mailbox seq %u (%s), %u bytes -> %s\n", seq, seq & 1 ? "mid-write" : "whole", len, a);
      } else fprintf(stderr, "probe: no mailbox\n");
    } else if (!strncmp(line, "mem", 3)) {
      mem_line("now");
    } else if (!strncmp(line, "time", 4)) {
      fprintf(stderr, "probe: %ld frames in %.2fs = %.2f ms/frame (%.1f fps possible), video %ld (dupes %ld), audio frames %ld\n",
              run_frames, run_time, run_frames ? run_time * 1e3 / run_frames : 0, run_time > 0 ? run_frames / run_time : 0,
              video_frames, dupes, audio_frames);
      run_time = 0; run_frames = 0; video_frames = 0; dupes = 0; audio_frames = 0;
    }
    (void)b;
  }
  if (hw.context_destroy) hw.context_destroy();
  p_unload();
  p_deinit();
  fprintf(stderr, "probe: clean exit\n");
  return 0;
}
