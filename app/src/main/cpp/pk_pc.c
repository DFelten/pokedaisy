// PC fan games (FeatureFlags.pcGames, docs/specs/pc-fan-games.md): a libretro host for the mkxp-z core, which runs
// Pokemon Essentials games (RPG Maker XP + Ruby). The core is a separate .so loaded from the app's own files (it is
// never bundled), and draws with OpenGL ES 3.2 into our framebuffer object on PcGameActivity's GL thread, which then
// blits it onto the screen. Everything here runs on that GL thread except pcInput (any thread) and pcMailbox (a
// background thread, under the core lock). Linux's host does the same in linux/hw_gl.c / pc_mailbox.c.
#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <zlib.h>

#include "libretro.h"

#define TAG "pokedaisy/pc"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

static struct {
    void (*init)(void), (*deinit)(void), (*run)(void), (*unload)(void);
    void (*set_env)(retro_environment_t), (*set_video)(retro_video_refresh_t), (*set_audio)(retro_audio_sample_t),
        (*set_audio_batch)(retro_audio_sample_batch_t), (*set_poll)(retro_input_poll_t), (*set_state)(retro_input_state_t);
    bool (*load)(const struct retro_game_info *);
    void (*av)(struct retro_system_av_info *);
    void *(*mem)(unsigned);
    size_t (*mem_size)(unsigned);
    void (*cheat_set)(unsigned, bool, const char *);
    size_t (*ser_size)(void);
    bool (*ser)(void *, size_t), (*unser)(const void *, size_t);
} core;

static void *lib, *gles;
static char sysDir[512], saveDir[512];
static struct retro_hw_render_callback hw;
static bool hwSet, running;
static GLuint fbo, tex, rb;
static int fbw = 640, fbh = 480, frameW, frameH;
static double fps = 60.0, nextFrame;
static retro_audio_callback_t audioCb;
static atomic_uint inputMask;

// The core's options: defaults from SET_CORE_OPTIONS_V2, ours on top (as linux/main.c sets them).
static struct { char key[128], value[64]; } defs[512];
static int ndefs;

// Core lock: pcMailbox reads the core's memory from another thread; pcStop unloads it.
static pthread_rwlock_t coreLock = PTHREAD_RWLOCK_INITIALIZER;

// Sound: interleaved stereo int16 at the core's rate, a ring the GL thread drains after each draw.
#define AUDIO_CAP (48000 * 2 / 2) // half a second of stereo samples
static int16_t audioRing[AUDIO_CAP];
static size_t audioHead, audioLen;
static double sampleRate = 48000;

static double now(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec + t.tv_nsec / 1e9;
}

static void logcb(enum retro_log_level level, const char *fmt, ...) {
    if (level < RETRO_LOG_WARN) return; // the core's debug level traces every WASI call
    va_list ap;
    va_start(ap, fmt);
    __android_log_vprint(level >= RETRO_LOG_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, TAG, fmt, ap);
    va_end(ap);
}

static uintptr_t getFramebuffer(void) { return fbo; }

static retro_proc_address_t getProc(const char *name) {
    // Core GLES functions from the library itself; extensions through EGL.
    void *f = gles ? dlsym(gles, name) : NULL;
    return (retro_proc_address_t) (f ? f : (void *) eglGetProcAddress(name));
}

static const char *optionOverride(const char *key) {
    // Our preloads (linux/pc_games/*.rb; "mkxp-z_preload-" + the file name in hex, "pokedaisy_" = 706f6b6564616973795f),
    // threaded audio off (states and the sound callback need it), and states big enough for Infinite Fusion's heap.
    if (!strncmp(key, "mkxp-z_preload-706f6b6564616973795f", 35)) return "enabled";
    if (!strcmp(key, "mkxp-z_threadedAudio")) return "disabled";
    if (!strcmp(key, "mkxp-z_saveStateSize")) return "512";
    return NULL;
}

static bool env(unsigned cmd, void *data) {
    switch (cmd & 0xFFFF) {
    case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
        ((struct retro_log_callback *) data)->log = logcb;
        return true;
    case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        *(const char **) data = sysDir;
        return true;
    case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
        *(const char **) data = saveDir;
        return true;
    case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
        return *(enum retro_pixel_format *) data == RETRO_PIXEL_FORMAT_XRGB8888;
    case RETRO_ENVIRONMENT_SET_HW_RENDER: {
        // The core asks for desktop GL first, then GLES: take GLES 3.0-3.2.
        struct retro_hw_render_callback *h = data;
        bool es = h->context_type == RETRO_HW_CONTEXT_OPENGLES3 ||
                  (h->context_type == RETRO_HW_CONTEXT_OPENGLES_VERSION && h->version_major == 3 && h->version_minor <= 2);
        if (!es) return false;
        h->get_current_framebuffer = getFramebuffer;
        h->get_proc_address = getProc;
        hw = *h;
        hwSet = true;
        LOGI("hw render: GLES %u.%u", h->version_major, h->version_minor);
        return true;
    }
    case RETRO_ENVIRONMENT_GET_CAN_DUPE:
        *(bool *) data = true;
        return true;
    case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
        *(unsigned *) data = 2;
        return true;
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2: {
        const struct retro_core_options_v2 *o = data;
        ndefs = 0;
        for (const struct retro_core_option_v2_definition *d = o->definitions; d && d->key && ndefs < 512; d++) {
            snprintf(defs[ndefs].key, sizeof defs[ndefs].key, "%s", d->key);
            snprintf(defs[ndefs].value, sizeof defs[ndefs].value, "%s",
                     d->default_value ? d->default_value : (d->values[0].value ? d->values[0].value : ""));
            ndefs++;
        }
        return true;
    }
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
    case RETRO_ENVIRONMENT_SET_VARIABLES:
        return true;
    case RETRO_ENVIRONMENT_GET_VARIABLE: {
        struct retro_variable *v = data;
        const char *mine = optionOverride(v->key);
        if (mine) {
            v->value = mine;
            return true;
        }
        for (int i = 0; i < ndefs; i++)
            if (!strcmp(defs[i].key, v->key)) {
                v->value = defs[i].value;
                return true;
            }
        v->value = NULL;
        return false;
    }
    case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
        *(bool *) data = false;
        return true;
    case RETRO_ENVIRONMENT_SET_AUDIO_CALLBACK:
        audioCb = ((struct retro_audio_callback *) data)->callback;
        return true;
    case RETRO_ENVIRONMENT_GET_TARGET_REFRESH_RATE:
        *(float *) data = 60.0f;
        return true;
    case RETRO_ENVIRONMENT_SET_GEOMETRY:
    case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
        return true;
    case RETRO_ENVIRONMENT_SET_MESSAGE:
        LOGI("core message: %s", ((struct retro_message *) data)->msg);
        return true;
    case RETRO_ENVIRONMENT_SET_MESSAGE_EXT:
        LOGI("core message: %s", ((struct retro_message_ext *) data)->msg);
        return true;
    case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION:
        *(unsigned *) data = 1;
        return true;
    case RETRO_ENVIRONMENT_SHUTDOWN:
        LOGI("the game asked to close");
        running = false;
        return true;
    default:
        return false;
    }
}

static void video(const void *data, unsigned w, unsigned h, size_t pitch) {
    (void) pitch;
    if (data) { // NULL = a dupe: the framebuffer keeps the last picture
        frameW = (int) w;
        frameH = (int) h;
    }
}

static void pushAudio(const int16_t *s, size_t samples) {
    for (size_t i = 0; i < samples; i++) {
        if (audioLen == AUDIO_CAP) { // the device fell behind: drop the oldest
            audioHead = (audioHead + 1) % AUDIO_CAP;
            audioLen--;
        }
        audioRing[(audioHead + audioLen) % AUDIO_CAP] = s[i];
        audioLen++;
    }
}
static void audio1(int16_t l, int16_t r) {
    int16_t s[2] = {l, r};
    pushAudio(s, 2);
}
static size_t audioBatch(const int16_t *d, size_t frames) {
    pushAudio(d, frames * 2);
    return frames;
}
static void poll(void) {}
static int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    (void) index;
    if (port || device != RETRO_DEVICE_JOYPAD) return 0;
    unsigned mask = atomic_load(&inputMask);
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t) mask;
    return (int16_t) ((mask >> id) & 1);
}

static void makeFramebuffer(void) {
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, fbw, fbh, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    glGenRenderbuffers(1, &rb);
    glBindRenderbuffer(GL_RENDERBUFFER, rb);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, fbw, fbh);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rb);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) LOGW("framebuffer incomplete");
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

static void unloadLocked(void) {
    if (!lib) return;
    if (running || hwSet) {
        if (hwSet && hw.context_destroy) hw.context_destroy();
        if (core.unload) core.unload(); // before deinit: without it the core crashed in its audio destructor
        if (core.deinit) core.deinit();
    }
    if (fbo) glDeleteFramebuffers(1, &fbo);
    if (tex) glDeleteTextures(1, &tex);
    if (rb) glDeleteRenderbuffers(1, &rb);
    fbo = tex = rb = 0;
    dlclose(lib);
    lib = NULL;
    running = hwSet = false;
    audioCb = NULL;
    audioHead = audioLen = 0;
}

JNIEXPORT jboolean JNICALL Java_com_pokedaisy_app_PcCore_pcStart(JNIEnv *jenv, jclass cls, jstring jcore, jstring jgame,
                                                                 jstring jsys, jstring jsave) {
    (void) cls;
    const char *corePath = (*jenv)->GetStringUTFChars(jenv, jcore, NULL);
    const char *gamePath = (*jenv)->GetStringUTFChars(jenv, jgame, NULL);
    const char *sys = (*jenv)->GetStringUTFChars(jenv, jsys, NULL);
    const char *save = (*jenv)->GetStringUTFChars(jenv, jsave, NULL);
    snprintf(sysDir, sizeof sysDir, "%s", sys);
    snprintf(saveDir, sizeof saveDir, "%s", save);
    bool ok = false;

    pthread_rwlock_wrlock(&coreLock);
    unloadLocked();
    if (!gles) gles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
    lib = dlopen(corePath, RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        LOGW("core: %s", dlerror());
        goto done;
    }
#define SYM(field, name)                                                                                                \
    if (!(*(void **) &core.field = dlsym(lib, name))) {                                                                 \
        LOGW("core: missing %s", name);                                                                                 \
        goto done;                                                                                                      \
    }
    SYM(init, "retro_init") SYM(deinit, "retro_deinit") SYM(run, "retro_run") SYM(unload, "retro_unload_game")
    SYM(set_env, "retro_set_environment") SYM(set_video, "retro_set_video_refresh")
    SYM(set_audio, "retro_set_audio_sample") SYM(set_audio_batch, "retro_set_audio_sample_batch")
    SYM(set_poll, "retro_set_input_poll") SYM(set_state, "retro_set_input_state") SYM(load, "retro_load_game")
    SYM(av, "retro_get_system_av_info") SYM(mem, "retro_get_memory_data") SYM(mem_size, "retro_get_memory_size")
    SYM(cheat_set, "retro_cheat_set") SYM(ser_size, "retro_serialize_size") SYM(ser, "retro_serialize") SYM(unser, "retro_unserialize")
#undef SYM

    core.set_env(env);
    core.set_video(video);
    core.set_audio(audio1);
    core.set_audio_batch(audioBatch);
    core.set_poll(poll);
    core.set_state(inputState);
    core.init();
    struct retro_game_info info = {gamePath, NULL, 0, NULL};
    double t = now();
    if (!core.load(&info) || !hwSet) {
        LOGW("the core didn't load %s (hw render %d)", gamePath, hwSet);
        core.deinit();
        goto done;
    }
    struct retro_system_av_info av;
    core.av(&av);
    fbw = (int) av.geometry.max_width;
    fbh = (int) av.geometry.max_height;
    frameW = (int) av.geometry.base_width;
    frameH = (int) av.geometry.base_height;
    fps = av.timing.fps > 1 ? av.timing.fps : 60.0;
    sampleRate = av.timing.sample_rate > 0 ? av.timing.sample_rate : 48000;
    LOGI("loaded %s in %.2f s: %dx%d (max %dx%d), %.2f fps, %.0f Hz", gamePath, now() - t, frameW, frameH, fbw, fbh, fps,
         sampleRate);
    makeFramebuffer();
    GLuint vao;
    glGenVertexArrays(1, &vao);
    glBindVertexArray(vao);
    if (hw.context_reset) hw.context_reset();
    running = true;
    nextFrame = 0;
    ok = true;
done:
    if (!ok && lib) {
        dlclose(lib);
        lib = NULL;
    }
    pthread_rwlock_unlock(&coreLock);
    (*jenv)->ReleaseStringUTFChars(jenv, jcore, corePath);
    (*jenv)->ReleaseStringUTFChars(jenv, jgame, gamePath);
    (*jenv)->ReleaseStringUTFChars(jenv, jsys, sys);
    (*jenv)->ReleaseStringUTFChars(jenv, jsave, save);
    return ok;
}

// Lines of Ruby for the game (the companion's battle picks: linux/pc_games/pokedaisy_companion.rb's request): queued from
// any thread, handed to the core's cheat call on the GL thread just before a frame (it runs each once, then clears it).
#define RUBY_QUEUE 8
static char *rubyQueue[RUBY_QUEUE];
static int rubyCount;
static unsigned rubyIndex;
static pthread_mutex_t rubyLock = PTHREAD_MUTEX_INITIALIZER;

static void drainRuby(void) {
    pthread_mutex_lock(&rubyLock);
    for (int i = 0; i < rubyCount; i++) {
        if (running && core.cheat_set) core.cheat_set(rubyIndex++, true, rubyQueue[i]);
        free(rubyQueue[i]);
    }
    rubyCount = 0;
    pthread_mutex_unlock(&rubyLock);
}

/** Queues [code] (one line of Ruby) for the game; false when the queue is full. Any thread. */
JNIEXPORT jboolean JNICALL Java_com_pokedaisy_app_PcCore_pcRuby(JNIEnv *jenv, jclass cls, jstring jcode) {
    (void) cls;
    const char *code = (*jenv)->GetStringUTFChars(jenv, jcode, NULL);
    pthread_mutex_lock(&rubyLock);
    bool ok = rubyCount < RUBY_QUEUE && (rubyQueue[rubyCount] = strdup(code)) != NULL;
    if (ok) rubyCount++;
    pthread_mutex_unlock(&rubyLock);
    (*jenv)->ReleaseStringUTFChars(jenv, jcode, code);
    return ok;
}

/** The core's sample rate (after pcStart). */
JNIEXPORT jint JNICALL Java_com_pokedaisy_app_PcCore_pcSampleRate(JNIEnv *jenv, jclass cls) {
    (void) jenv;
    (void) cls;
    return (jint) sampleRate;
}

/**
 * One screen refresh: the frames due by the clock (at most two, so a slow frame never turns into a burst), then the
 * picture blitted into the view's framebuffer at the largest size that keeps its shape. Returns how many frames ran,
 * or -1 once the game has asked to close.
 */
JNIEXPORT jint JNICALL Java_com_pokedaisy_app_PcCore_pcFrame(JNIEnv *jenv, jclass cls, jint viewW, jint viewH) {
    (void) jenv;
    (void) cls;
    if (!running) return -1;
    double t = now();
    if (nextFrame == 0 || t - nextFrame > 0.25) nextFrame = t; // first frame, or a long stall: don't catch up
    int ran = 0;
    while (ran < 2 && t >= nextFrame) {
        if (ran == 0) drainRuby();
        pthread_rwlock_rdlock(&coreLock);
        core.run();
        if (audioCb) audioCb();
        pthread_rwlock_unlock(&coreLock);
        nextFrame += 1.0 / fps;
        ran++;
        if (!running) return -1;
    }

    // The picture: rows from the top in our framebuffer (bottom_left_origin false), so the blit flips it.
    GLboolean scissor = glIsEnabled(GL_SCISSOR_TEST);
    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    glViewport(0, 0, viewW, viewH);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    int w = frameW > 0 ? frameW : fbw, h = frameH > 0 ? frameH : fbh;
    int outW = viewW, outH = viewW * h / w;
    if (outH > viewH) {
        outH = viewH;
        outW = viewH * w / h;
    }
    int x0 = (viewW - outW) / 2, y0 = (viewH - outH) / 2;
    glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
    glBlitFramebuffer(0, 0, w, h, x0, y0 + outH, x0 + outW, y0, GL_COLOR_BUFFER_BIT, GL_LINEAR);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    if (scissor) glEnable(GL_SCISSOR_TEST);
    return ran;
}

/** Copies up to buf.length queued stereo samples into buf; returns how many. */
JNIEXPORT jint JNICALL Java_com_pokedaisy_app_PcCore_pcAudio(JNIEnv *jenv, jclass cls, jshortArray buf) {
    (void) cls;
    size_t cap = (size_t) (*jenv)->GetArrayLength(jenv, buf);
    size_t n = audioLen < cap ? audioLen : cap;
    n &= ~(size_t) 1; // whole stereo frames
    if (!n) return 0;
    jshort *out = (*jenv)->GetShortArrayElements(jenv, buf, NULL);
    for (size_t i = 0; i < n; i++) out[i] = audioRing[(audioHead + i) % AUDIO_CAP];
    (*jenv)->ReleaseShortArrayElements(jenv, buf, out, 0);
    audioHead = (audioHead + n) % AUDIO_CAP;
    audioLen -= n;
    return (jint) n;
}

/** The RetroPad buttons held (bit = RETRO_DEVICE_ID_JOYPAD_*); any thread. */
JNIEXPORT void JNICALL Java_com_pokedaisy_app_PcCore_pcInput(JNIEnv *jenv, jclass cls, jint mask) {
    (void) jenv;
    (void) cls;
    atomic_store(&inputMask, (unsigned) mask);
}

// The companion's mailbox (linux/pc_games/pokedaisy_companion.rb): magic[16], seq u32 LE (odd while written), len u32
// LE, then the JSON, inside the Ruby heap (the core's SYSTEM_RAM). Found once by its magic, then read in place.
#define MBX_HEADER 24
#define MBX_CAP (256u * 1024u)
static const char MBX_MAGIC[16] = {'P', 'K', 'D', 'A', 'I', 'S', 'Y', '-', 'M', 'B', 'X', '-', '0', '0', '0', '1'};
static size_t mbxAt = (size_t) -1;
static uint32_t mbxSeq;
static double mbxScan;

static uint32_t u32(const uint8_t *p) { return p[0] | p[1] << 8 | p[2] << 16 | (uint32_t) p[3] << 24; }
static bool mbxValid(const uint8_t *m, size_t size, size_t off) {
    return off + MBX_HEADER <= size && !memcmp(m + off, MBX_MAGIC, 16) && u32(m + off + 20) <= MBX_CAP &&
           off + MBX_HEADER + u32(m + off + 20) <= size;
}

/**
 * The mailbox's JSON (UTF-8 bytes) when it changed since the last call, else null. A background thread, every second
 * or so. Bytes, not a jstring: NewStringUTF takes modified UTF-8 and would abort on a name with an emoji.
 */
JNIEXPORT jbyteArray JNICALL Java_com_pokedaisy_app_PcCore_pcMailbox(JNIEnv *jenv, jclass cls) {
    (void) cls;
    jbyteArray result = NULL;
    pthread_rwlock_rdlock(&coreLock);
    const uint8_t *m = running && core.mem ? core.mem(RETRO_MEMORY_SYSTEM_RAM) : NULL;
    size_t size = m ? core.mem_size(RETRO_MEMORY_SYSTEM_RAM) : 0;
    if (m && size >= MBX_HEADER) {
        if (mbxAt == (size_t) -1 || !mbxValid(m, size, mbxAt)) {
            double t = now();
            if (mbxScan == 0 || t - mbxScan >= 3) { // the scan reads the whole heap: not too often
                mbxScan = t;
                mbxAt = (size_t) -1;
                uint32_t best = 0;
                for (size_t i = 0; i + MBX_HEADER <= size; i += 8) { // a Ruby String's block: 8-byte aligned
                    if (m[i] != 'P' || !mbxValid(m, size, i)) continue;
                    uint32_t s = u32(m + i + 16);
                    if (s >= best) {
                        best = s;
                        mbxAt = i;
                    }
                }
            }
        }
        if (mbxAt != (size_t) -1) {
            uint32_t s = u32(m + mbxAt + 16);
            uint32_t n = u32(m + mbxAt + 20);
            if (!(s & 1) && s != mbxSeq && n) {
                char *copy = malloc(n);
                if (copy) {
                    memcpy(copy, m + mbxAt + MBX_HEADER, n);
                    if (u32(m + mbxAt + 16) == s) { // not rewritten while we copied
                        mbxSeq = s;
                        result = (*jenv)->NewByteArray(jenv, (jsize) n);
                        if (result) (*jenv)->SetByteArrayRegion(jenv, result, 0, (jsize) n, (const jbyte *) copy);
                    }
                    free(copy);
                }
            }
        }
    }
    pthread_rwlock_unlock(&coreLock);
    return result;
}

// Resume states. A PC game's state is the whole Ruby heap (mkxp-z_saveStateSize, 512 MB): copied out on the GL thread
// (~0.2 s on the Thor), then gzip -1'd (about a fifth of that) on a thread of its own into <path>.tmp, renamed over
// <path>, and only then the sidecar (<path>.json, the game's save folder as it was) written beside it, so a sidecar
// always belongs to the state next to it. One write at a time: a pause while one is still writing keeps the older.
static atomic_bool stateWriting;

struct StateJob {
    void *buf;
    size_t size;
    char path[512], sidecar[512];
    char *sidecarText;
};

static void *writeState(void *arg) {
    struct StateJob *job = arg;
    char tmp[600];
    snprintf(tmp, sizeof tmp, "%s.tmp", job->path);
    double t = now();
    bool ok = false;
    gzFile f = gzopen(tmp, "wb1");
    if (f) {
        size_t done = 0;
        ok = true;
        while (done < job->size) {
            unsigned chunk = (unsigned) (job->size - done > (1u << 26) ? (1u << 26) : job->size - done);
            if (gzwrite(f, (const char *) job->buf + done, chunk) != (int) chunk) {
                ok = false;
                break;
            }
            done += chunk;
        }
        if (gzclose(f) != Z_OK) ok = false;
    }
    free(job->buf);
    if (ok && rename(tmp, job->path) == 0) {
        char stmp[600];
        snprintf(stmp, sizeof stmp, "%s.tmp", job->sidecar);
        FILE *sf = fopen(stmp, "wb");
        if (sf) {
            fputs(job->sidecarText, sf);
            ok = fclose(sf) == 0 && rename(stmp, job->sidecar) == 0;
        } else ok = false;
        LOGI("state written: %s in %.2f s (%s)", job->path, now() - t, ok ? "ok" : "sidecar FAILED");
    } else {
        unlink(tmp);
        LOGW("state write FAILED: %s", job->path);
    }
    free(job->sidecarText);
    free(job);
    atomic_store(&stateWriting, false);
    return NULL;
}

/**
 * Copies the game's state out and writes it to [path] (gzip), then [sidecar] next to it, on a thread of its own. The
 * GL thread. False if nothing was taken (no game, a write still running, no memory).
 */
JNIEXPORT jboolean JNICALL Java_com_pokedaisy_app_PcCore_pcSaveState(JNIEnv *jenv, jclass cls, jstring jpath, jstring jsidecar,
                                                                     jstring jtext) {
    (void) cls;
    if (!running || !core.ser) return false;
    bool expected = false;
    if (!atomic_compare_exchange_strong(&stateWriting, &expected, true)) {
        LOGW("a state is still being written: this pause keeps the older one");
        return false;
    }
    struct StateJob *job = calloc(1, sizeof *job);
    size_t size = core.ser_size();
    void *buf = job && size ? malloc(size) : NULL;
    double t = now();
    pthread_rwlock_rdlock(&coreLock);
    bool ok = buf && core.ser(buf, size);
    pthread_rwlock_unlock(&coreLock);
    if (!ok) {
        LOGW("serialize failed (%zu bytes)", size);
        free(buf);
        free(job);
        atomic_store(&stateWriting, false);
        return false;
    }
    LOGI("state taken: %zu bytes in %.0f ms", size, (now() - t) * 1e3);
    job->buf = buf;
    job->size = size;
    const char *path = (*jenv)->GetStringUTFChars(jenv, jpath, NULL);
    const char *sidecar = (*jenv)->GetStringUTFChars(jenv, jsidecar, NULL);
    const char *text = (*jenv)->GetStringUTFChars(jenv, jtext, NULL);
    snprintf(job->path, sizeof job->path, "%s", path);
    snprintf(job->sidecar, sizeof job->sidecar, "%s", sidecar);
    job->sidecarText = strdup(text);
    (*jenv)->ReleaseStringUTFChars(jenv, jpath, path);
    (*jenv)->ReleaseStringUTFChars(jenv, jsidecar, sidecar);
    (*jenv)->ReleaseStringUTFChars(jenv, jtext, text);
    pthread_t th;
    if (pthread_create(&th, NULL, writeState, job) != 0) {
        free(job->buf);
        free(job->sidecarText);
        free(job);
        atomic_store(&stateWriting, false);
        return false;
    }
    pthread_detach(th);
    return true;
}

/** True while a state is still being written (a close waits for it). */
JNIEXPORT jboolean JNICALL Java_com_pokedaisy_app_PcCore_pcStateWriting(JNIEnv *jenv, jclass cls) {
    (void) jenv;
    (void) cls;
    return atomic_load(&stateWriting);
}

/**
 * Loads the state at [path] (gzip). The GL thread, after at least one frame: a load before the first retro_run traps
 * in the core's WASM.
 */
JNIEXPORT jboolean JNICALL Java_com_pokedaisy_app_PcCore_pcLoadState(JNIEnv *jenv, jclass cls, jstring jpath) {
    (void) cls;
    if (!running || !core.unser) return false;
    const char *path = (*jenv)->GetStringUTFChars(jenv, jpath, NULL);
    double t = now();
    bool ok = false;
    size_t size = core.ser_size();
    void *buf = size ? malloc(size) : NULL;
    gzFile f = buf ? gzopen(path, "rb") : NULL;
    if (f) {
        gzbuffer(f, 1 << 20);
        size_t got = 0;
        while (got < size) {
            unsigned want = (unsigned) (size - got > (1u << 26) ? (1u << 26) : size - got);
            int n = gzread(f, (char *) buf + got, want);
            if (n <= 0) break;
            got += (size_t) n;
        }
        gzclose(f);
        if (got == size) {
            pthread_rwlock_wrlock(&coreLock);
            ok = core.unser(buf, size);
            pthread_rwlock_unlock(&coreLock);
        } else LOGW("state %s: %zu of %zu bytes", path, got, size);
    }
    free(buf);
    LOGI("state load %s: %s in %.2f s", path, ok ? "ok" : "FAILED", now() - t);
    (*jenv)->ReleaseStringUTFChars(jenv, jpath, path);
    nextFrame = 0;
    return ok;
}

/** Unloads the game and the core (the GL thread, while its context is still current). */
JNIEXPORT void JNICALL Java_com_pokedaisy_app_PcCore_pcStop(JNIEnv *jenv, jclass cls) {
    (void) jenv;
    (void) cls;
    pthread_rwlock_wrlock(&coreLock);
    unloadLocked();
    mbxAt = (size_t) -1;
    mbxSeq = 0;
    mbxScan = 0;
    pthread_rwlock_unlock(&coreLock);
}
