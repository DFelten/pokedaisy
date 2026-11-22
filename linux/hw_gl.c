// See hw_gl.h. EGL: the surfaceless platform where the driver has it (Mesa), else the default display with a
// 1x1 pbuffer. The host's SDL renderer may use GL on the same thread, so every core call is bracketed by
// hwgl_begin / hwgl_end, which make our context current and then SDL's again.
#include "hw_gl.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <SDL.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// GL enums and entry points we need, without depending on desktop GL or GLES headers.
typedef unsigned int GLenum, GLuint;
typedef int GLint, GLsizei;
typedef unsigned char GLubyte;
#define GL_FRAMEBUFFER 0x8D40
#define GL_RENDERBUFFER 0x8D41
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_DEPTH_STENCIL_ATTACHMENT 0x821A
#define GL_DEPTH24_STENCIL8 0x88F0
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_TEXTURE_2D 0x0DE1
#define GL_RGBA 0x1908
#define GL_RGBA8 0x8058
#define GL_BGRA 0x80E1
#define GL_UNSIGNED_BYTE 0x1401
#define GL_PACK_ALIGNMENT 0x0D05
#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01

static struct {
  void (*GenFramebuffers)(GLsizei, GLuint *);
  void (*DeleteFramebuffers)(GLsizei, const GLuint *);
  void (*BindFramebuffer)(GLenum, GLuint);
  void (*FramebufferTexture2D)(GLenum, GLenum, GLenum, GLuint, GLint);
  void (*GenRenderbuffers)(GLsizei, GLuint *);
  void (*DeleteRenderbuffers)(GLsizei, const GLuint *);
  void (*BindRenderbuffer)(GLenum, GLuint);
  void (*RenderbufferStorage)(GLenum, GLenum, GLsizei, GLsizei);
  void (*FramebufferRenderbuffer)(GLenum, GLenum, GLenum, GLuint);
  GLenum (*CheckFramebufferStatus)(GLenum);
  void (*GenTextures)(GLsizei, GLuint *);
  void (*DeleteTextures)(GLsizei, const GLuint *);
  void (*BindTexture)(GLenum, GLuint);
  void (*TexImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void *);
  void (*ReadPixels)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void *);
  void (*PixelStorei)(GLenum, GLint);
  const GLubyte *(*GetString)(GLenum);
} gl;

static struct retro_hw_render_callback hw;
static bool active, gles;
static EGLDisplay dpy = EGL_NO_DISPLAY;
static EGLContext ctx = EGL_NO_CONTEXT;
static EGLSurface surf = EGL_NO_SURFACE;
static GLuint fbo, tex, rb;
static unsigned fbW, fbH;
static uint32_t *frame;
static unsigned frameW, frameH;
static bool haveFrame;
static SDL_GLContext sdlCtx;
static SDL_Window *sdlWin;

static uintptr_t get_fb(void) { return fbo; }
static retro_proc_address_t get_proc(const char *sym) { return (retro_proc_address_t)eglGetProcAddress(sym); }

static bool make_context(EGLenum api, EGLint renderable, const EGLint *attrs) {
  if (dpy == EGL_NO_DISPLAY) {
    PFNEGLGETPLATFORMDISPLAYEXTPROC getPlatform = (void *)eglGetProcAddress("eglGetPlatformDisplayEXT");
    const char *ext = eglQueryString(EGL_NO_DISPLAY, EGL_EXTENSIONS);
    if (getPlatform && ext && strstr(ext, "EGL_MESA_platform_surfaceless"))
      dpy = getPlatform(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, NULL);
    if (dpy == EGL_NO_DISPLAY)
      dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (dpy == EGL_NO_DISPLAY || !eglInitialize(dpy, NULL, NULL)) {
      fprintf(stderr, "hwgl: no EGL display\n");
      dpy = EGL_NO_DISPLAY;
      return false;
    }
  }
  if (!eglBindAPI(api))
    return false;
  EGLint cfgAttrs[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, renderable, EGL_RED_SIZE, 8,
                       EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE};
  EGLConfig cfg;
  EGLint n = 0;
  if (!eglChooseConfig(dpy, cfgAttrs, &cfg, 1, &n) || n < 1)
    return false;
  ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, attrs);
  if (ctx == EGL_NO_CONTEXT)
    return false;
  const char *ext = eglQueryString(dpy, EGL_EXTENSIONS);
  if (!ext || !strstr(ext, "EGL_KHR_surfaceless_context")) {
    EGLint pb[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
    surf = eglCreatePbufferSurface(dpy, cfg, pb);
  }
  return true;
}

bool hwgl_set_hw_render(struct retro_hw_render_callback *cb) {
  if (active)
    return false;
  EGLint attrs[16];
  int a = 0;
  bool ok = false;
  switch (cb->context_type) {
  case RETRO_HW_CONTEXT_OPENGL_CORE:
    attrs[a++] = EGL_CONTEXT_MAJOR_VERSION;
    attrs[a++] = (EGLint)cb->version_major;
    attrs[a++] = EGL_CONTEXT_MINOR_VERSION;
    attrs[a++] = (EGLint)cb->version_minor;
    attrs[a++] = EGL_CONTEXT_OPENGL_PROFILE_MASK;
    attrs[a++] = EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT;
    attrs[a] = EGL_NONE;
    ok = make_context(EGL_OPENGL_API, EGL_OPENGL_BIT, attrs);
    break;
  case RETRO_HW_CONTEXT_OPENGL:
    attrs[0] = EGL_NONE;
    ok = make_context(EGL_OPENGL_API, EGL_OPENGL_BIT, attrs);
    break;
  case RETRO_HW_CONTEXT_OPENGLES2:
  case RETRO_HW_CONTEXT_OPENGLES3:
  case RETRO_HW_CONTEXT_OPENGLES_VERSION: {
    EGLint major = cb->context_type == RETRO_HW_CONTEXT_OPENGLES2   ? 2
                   : cb->context_type == RETRO_HW_CONTEXT_OPENGLES3 ? 3
                                                                    : (EGLint)cb->version_major;
    attrs[a++] = EGL_CONTEXT_MAJOR_VERSION;
    attrs[a++] = major;
    if (cb->context_type == RETRO_HW_CONTEXT_OPENGLES_VERSION) {
      attrs[a++] = EGL_CONTEXT_MINOR_VERSION;
      attrs[a++] = (EGLint)cb->version_minor;
    }
    attrs[a] = EGL_NONE;
    ok = make_context(EGL_OPENGL_ES_API, major >= 3 ? EGL_OPENGL_ES3_BIT : EGL_OPENGL_ES2_BIT, attrs);
    gles = ok;
    break;
  }
  default:
    return false;
  }
  if (!ok)
    return false;
  cb->get_current_framebuffer = get_fb;
  cb->get_proc_address = get_proc;
  hw = *cb;
  active = true;
#define P(n) *(void **)&gl.n = (void *)eglGetProcAddress("gl" #n)
  P(GenFramebuffers); P(DeleteFramebuffers); P(BindFramebuffer); P(FramebufferTexture2D); P(GenRenderbuffers);
  P(DeleteRenderbuffers); P(BindRenderbuffer); P(RenderbufferStorage); P(FramebufferRenderbuffer);
  P(CheckFramebufferStatus); P(GenTextures); P(DeleteTextures); P(BindTexture); P(TexImage2D); P(ReadPixels);
  P(PixelStorei); P(GetString);
#undef P
  return true;
}

bool hwgl_active(void) { return active; }

void hwgl_begin(void) {
  if (!active)
    return;
  sdlWin = SDL_GL_GetCurrentWindow();
  sdlCtx = SDL_GL_GetCurrentContext();
  eglBindAPI(gles ? EGL_OPENGL_ES_API : EGL_OPENGL_API);
  eglMakeCurrent(dpy, surf, surf, ctx);
}

void hwgl_end(void) {
  if (!active)
    return;
  eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
  if (sdlCtx) {
    // Twice: SDL may skip a MakeCurrent of the context it thinks is still current.
    SDL_GL_MakeCurrent(sdlWin, NULL);
    SDL_GL_MakeCurrent(sdlWin, sdlCtx);
  }
}

bool hwgl_start(unsigned maxW, unsigned maxH) {
  if (!active)
    return false;
  hwgl_begin();
  fbW = maxW ? maxW : 640;
  fbH = maxH ? maxH : 480;
  gl.GenFramebuffers(1, &fbo);
  gl.BindFramebuffer(GL_FRAMEBUFFER, fbo);
  gl.GenTextures(1, &tex);
  gl.BindTexture(GL_TEXTURE_2D, tex);
  gl.TexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, (GLsizei)fbW, (GLsizei)fbH, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
  gl.FramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
  if (hw.depth || hw.stencil) {
    gl.GenRenderbuffers(1, &rb);
    gl.BindRenderbuffer(GL_RENDERBUFFER, rb);
    gl.RenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, (GLsizei)fbW, (GLsizei)fbH);
    gl.FramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rb);
  }
  bool ok = gl.CheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
  gl.BindFramebuffer(GL_FRAMEBUFFER, 0);
  fprintf(stderr, "hwgl: %s on %s, %ux%u framebuffer%s\n", gl.GetString(GL_VERSION), gl.GetString(GL_RENDERER), fbW, fbH,
          ok ? "" : " INCOMPLETE");
  frame = calloc((size_t)fbW * fbH, 4);
  if (ok && hw.context_reset)
    hw.context_reset();
  hwgl_end();
  return ok && frame;
}

void hwgl_capture(unsigned w, unsigned h) {
  if (!active || !frame || !w || !h)
    return;
  if (w > fbW)
    w = fbW;
  if (h > fbH)
    h = fbH;
  gl.BindFramebuffer(GL_FRAMEBUFFER, fbo);
  gl.PixelStorei(GL_PACK_ALIGNMENT, 4);
  // GLES only promises RGBA reads; desktop GL reads BGRA, which is XRGB8888 in memory on little-endian.
  gl.ReadPixels(0, 0, (GLsizei)w, (GLsizei)h, gles ? GL_RGBA : GL_BGRA, GL_UNSIGNED_BYTE, frame);
  gl.BindFramebuffer(GL_FRAMEBUFFER, 0);
  if (gles)
    for (size_t i = 0, n = (size_t)w * h; i < n; i++) {
      uint32_t p = frame[i]; // bytes R G B A -> B G R X
      frame[i] = (p & 0xFF00FF00u) | ((p & 0xFF) << 16) | ((p >> 16) & 0xFF);
    }
  if (hw.bottom_left_origin)
    for (unsigned y = 0; y < h / 2; y++)
      for (unsigned x = 0; x < w; x++) {
        uint32_t t = frame[(size_t)y * w + x];
        frame[(size_t)y * w + x] = frame[(size_t)(h - 1 - y) * w + x];
        frame[(size_t)(h - 1 - y) * w + x] = t;
      }
  frameW = w;
  frameH = h;
  haveFrame = true;
}

const uint32_t *hwgl_frame(unsigned *w, unsigned *h, size_t *pitch) {
  if (!haveFrame)
    return NULL;
  *w = frameW;
  *h = frameH;
  *pitch = (size_t)frameW * 4;
  return frame;
}

void hwgl_context_destroy(void) {
  if (!active || !fbo || !hw.context_destroy)
    return;
  hwgl_begin();
  hw.context_destroy();
  hwgl_end();
}

void hwgl_stop(void) {
  if (!active)
    return;
  hwgl_begin();
  if (fbo)
    gl.DeleteFramebuffers(1, &fbo);
  if (tex)
    gl.DeleteTextures(1, &tex);
  if (rb)
    gl.DeleteRenderbuffers(1, &rb);
  fbo = tex = rb = 0;
  hwgl_end();
  if (surf != EGL_NO_SURFACE)
    eglDestroySurface(dpy, surf);
  eglDestroyContext(dpy, ctx);
  ctx = EGL_NO_CONTEXT;
  surf = EGL_NO_SURFACE;
  free(frame);
  frame = NULL;
  haveFrame = active = gles = false;
  memset(&hw, 0, sizeof hw);
}
