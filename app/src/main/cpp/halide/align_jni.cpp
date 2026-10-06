// JNI bridge for the Halide CPU burst alignment (halide-align project).
// Frames arrive as the tight fp16 half-float buffers produced by
// Allocator.createF16 (per-Bayer-site black/white normalized). white is the
// effective white in normalized units: 1.0 for the base frame,
// pair.layerMpy for an alt frame.
//
// Optional refinement (halide-align's alignflow kernels): when flow
// refinement is enabled (nSetRefineEnabled, default on), nBase also
// computes the per-tile gradient normal-matrix products once per burst
// (alignflow_base) and every nAlignFrame runs a block Lucas-Kanade
// Gauss-Newton refinement (alignflow_f16) seeded by the median-filtered
// stage-2 field. The returned array then holds the level-0 (raw/2) field
// - 2x denser per axis, tile origins every 16 raw px = 1:1 with the merge
// atlas cells - instead of the level-1 field. Java picks the grid and
// broadcast via nRefineEnabled(), so flipping the flag at runtime (no
// rebuild) is the whole A/B switch.
//
// Fault trapping: signals (SIGILL/SIGSEGV/SIGBUS/SIGFPE/SIGABRT) raised by
// the kernels are caught, logged to logcat with the faulting PC, the raw
// instruction word at the PC and the nearest symbol, and - when they hit
// the thread that entered the JNI call - converted into a Java
// IllegalStateException carrying the same text. On a Halide worker thread a
// longjmp would unwind the wrong stack, so there we log and re-raise for a
// normal tombstone. The handler uses snprintf/__android_log_print/dladdr,
// which are not strictly async-signal-safe; this is a debugging aid, not a
// production crash reporter.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

#include <dlfcn.h>
#include <pthread.h>
#include <setjmp.h>
#include <signal.h>
#include <unistd.h>
#include <unwind.h>
#include <ucontext.h>

#include "HalideBuffer.h"
#include "HalideRuntime.h"
#include "align_pads.h"
#include "alignburst_base_f16.h"
#include "alignburst_f16.h"
#include "alignflow_base.h"
#include "alignflow_f16.h"

#define TAG "HalideAlignment"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int kMaxLevel = 4;
constexpr int kTile = 16;
constexpr int kStride = 8;   // 50% tile overlap: origins every 8 texels
constexpr int kRadius = 4;
constexpr int kMinLevel = 1;

// Runtime switch for the level-0 flow refinement (see the file header):
// true = refine every frame's field onto the raw/2 grid (denser,
// sub-texel-continuous vectors; roughly doubles the alignment cost - gate
// on phone budget/ISO if needed), false = the plain hierarchical level-1
// field. Set from Java via nSetRefineEnabled() before nInit (the ESD4D
// "Halide flow refinement" tunable); nInit snapshots it into the context
// so a burst runs one consistent mode, and nRefineEnabled() reports it so
// the Java side picks the matching grid and broadcast.
bool g_refine_flow = true;

// CPU 3x3 median of the alignment field (per component, edge-replicated) -
// the stage replaces isolated outlier tiles (confident
// mislocks surrounded by correct neighbors) with their neighborhood median
// before the merge consumes the field. Layout: [c][ty][tx], c = 0,1.
static void median3x3_2ch(std::vector<float> &f, int ntx, int nty) {
    std::vector<float> g(f.size());
    const size_t plane = (size_t)ntx * nty;
    for (int c = 0; c < 2; c++)
        for (int ty = 0; ty < nty; ty++)
            for (int tx = 0; tx < ntx; tx++) {
                float v[9];
                int n = 0;
                for (int j = -1; j <= 1; j++)
                    for (int i = -1; i <= 1; i++) {
                        int x = std::max(0, std::min(ntx - 1, tx + i));
                        int y = std::max(0, std::min(nty - 1, ty + j));
                        v[n++] = f[c * plane + (size_t)y * ntx + x];
                    }
                std::nth_element(v, v + 4, v + 9);
                g[c * plane + (size_t)ty * ntx + tx] = v[4];
            }
    f.swap(g);
}

// ---------------------------------------------------------------------------
// Fault trap
// ---------------------------------------------------------------------------

volatile sig_atomic_t g_guard_active = 0;
pthread_t g_guard_thread;
sigjmp_buf g_fault_jmp;
char g_fault_msg[512];
// Message from the most recent Halide runtime error (set via the error
// handler below), so kernel failures can be thrown into Java with the text.
char g_last_halide_error[512] = {0};

struct SigSlot {
    int sig;
    struct sigaction old;
};
SigSlot g_trapped[] = {{SIGILL, {}}, {SIGBUS, {}}, {SIGFPE, {}},
                       {SIGSEGV, {}}, {SIGABRT, {}}};
bool g_trap_installed = false;
char g_alt_stack[64 * 1024];

const char *sig_name(int sig) {
    switch (sig) {
        case SIGILL: return "SIGILL";
        case SIGBUS: return "SIGBUS";
        case SIGFPE: return "SIGFPE";
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        default: return "?";
    }
}

// Best-effort hint of what an AArch64 instruction word encodes. Only used to
// annotate SIGILL reports - paste the logged insn hex into a disassembler for
// the definitive answer.
const char *insn_hint_a64(uint32_t w) {
    if ((w & 0x3F203C00) == 0x38203C00)
        return "ARMv8.1 LSE atomic (SWP/LDADD/LDSET/... family)";
    if ((w & 0xFFFF0000) == 0x1E7E0000) return "FJCVTZS (ARMv8.3 JSConv)";
    if ((w & 0xFFFFFF3F) == 0xD503241F) return "BTI (ARMv8.5)";
    if (w == 0xD503233F || w == 0xD50323BF)
        return "pointer authentication (PACIASP/AUTIASP, ARMv8.3)";
    // SVE/SVE2 occupy the encodings whose top byte ends in 4 or 5; branch and
    // exception encodings share that nibble, so exclude those top bytes.
    uint8_t t = (uint8_t)(w >> 24);
    if ((t & 0x0F) == 4 || (t & 0x0F) == 5) {
        switch (t) {
            case 0x14: case 0x34: case 0x54: case 0x74:
            case 0x94: case 0xB4: case 0xD4: case 0xF4:
                break;  // branches/exceptions, not SVE
            default:
                return "likely SVE/SVE2";
        }
    }
    return nullptr;
}

struct UnwindState {
    int depth;
    void *pcs[12];
};

_Unwind_Reason_Code unwind_cb(struct _Unwind_Context *ctx, void *arg) {
    auto *st = (UnwindState *)arg;
    if (st->depth < 12) st->pcs[st->depth++] = (void *)_Unwind_GetIP(ctx);
    return _URC_NO_REASON;
}

void log_frames() {
    UnwindState st{};
    st.depth = 0;
    _Unwind_Backtrace(unwind_cb, &st);
    for (int i = 0; i < st.depth; i++) {
        Dl_info di{};
        const char *sym = "?";
        void *base = nullptr;
        if (dladdr(st.pcs[i], &di) && di.dli_fname != nullptr) {
            sym = di.dli_sname ? di.dli_sname : di.dli_fname;
            base = di.dli_fbase;
        }
        LOGE("  #%02d pc %p %s (%p)", i, st.pcs[i], sym, base);
    }
}

void fault_handler(int sig, siginfo_t *info, void *uctx) {
    ucontext_t *uc = (ucontext_t *)uctx;
    uintptr_t pc = 0;
#if defined(__aarch64__)
    pc = (uintptr_t)uc->uc_mcontext.pc;
#elif defined(__arm__)
    pc = (uintptr_t)uc->uc_mcontext.arm_pc;
#elif defined(__x86_64__)
    pc = (uintptr_t)uc->uc_mcontext.gregs[REG_RIP];
#elif defined(__i386__)
    pc = (uintptr_t)uc->uc_mcontext.gregs[REG_EIP];
#endif

    uint32_t insn = 0;
    if (pc != 0) insn = *(const volatile uint32_t *)pc;

    Dl_info di{};
    const char *sym = nullptr;
    uintptr_t fbase = 0;
    if (pc != 0 && dladdr((const void *)pc, &di)) {
        sym = di.dli_sname;
        fbase = (uintptr_t)di.dli_fbase;
    }

    const char *hint = (sig == SIGILL && pc != 0) ? insn_hint_a64(insn) : nullptr;
    snprintf(g_fault_msg, sizeof(g_fault_msg),
             "%s pc=%p insn=0x%08x%s%s%s si_addr=%p sym=%s base=0x%lx off=0x%lx",
             sig_name(sig), (void *)pc, insn, hint ? " [" : "",
             hint ? hint : "", hint ? "]" : "", info ? info->si_addr : nullptr,
             sym ? sym : "?", (unsigned long)fbase,
             (unsigned long)(fbase ? pc - fbase : 0));
    // Report: one-line summary + backtrace to logcat. Guarded-thread faults
    // additionally throw into Java carrying the g_fault_msg summary, which
    // the Java side writes to its own log.
    LOGE("caught fault in halidealign native code: %s", g_fault_msg);
    log_frames();

    if (g_guard_active && pthread_equal(pthread_self(), g_guard_thread)) {
        g_guard_active = 0;
        siglongjmp(g_fault_jmp, sig);
    }
    // Fault on a Halide worker (or any non-guarded) thread: longjmp would
    // unwind the wrong stack, so restore the default disposition and die with
    // a proper tombstone - the report above is already in logcat.
    LOGE("fault on non-guarded thread; re-raising %s", sig_name(sig));
    for (auto &s : g_trapped) {
        if (s.sig == sig) {
            sigaction(sig, &s.old, nullptr);
            break;
        }
    }
    raise(sig);
    _exit(70);
}

void ensure_fault_trap() {
    if (g_trap_installed) return;
    stack_t ss{};
    ss.ss_sp = g_alt_stack;
    ss.ss_size = sizeof(g_alt_stack);
    ss.ss_flags = 0;
    sigaltstack(&ss, nullptr);
    struct sigaction sa{};
    sa.sa_sigaction = fault_handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    for (auto &s : g_trapped) sigaction(s.sig, &sa, &s.old);
    g_trap_installed = true;
}

// Halide runtime errors (buffer constraint violations, allocation failures...)
// default to stderr, which is invisible on Android; route them to logcat and
// remember the text for the Java exception thrown when the kernel returns.
void halide_log_error(void *, const char *msg) {
    LOGE("Halide runtime error: %s", msg);
    strncpy(g_last_halide_error, msg, sizeof(g_last_halide_error) - 1);
    g_last_halide_error[sizeof(g_last_halide_error) - 1] = '\0';
}

// Arms the fault guard inside a JNI function: on a caught signal on this
// thread, siglongjmp returns here with the signal number, an
// IllegalStateException with the fault description is thrown and `ret` is
// returned. The RAII disarmer covers early returns.
#define ALIGN_FAULT_GUARD(ret)                                                \
    do {                                                                      \
        ensure_fault_trap();                                                  \
    } while (0);                                                              \
    if (sigsetjmp(g_fault_jmp, 1) != 0) {                                     \
        g_guard_active = 0;                                                   \
        jclass excCls = env->FindClass("java/lang/IllegalStateException");    \
        if (excCls != nullptr) env->ThrowNew(excCls, g_fault_msg);            \
        return ret;                                                           \
    }                                                                         \
    g_guard_thread = pthread_self();                                          \
    g_guard_active = 1;                                                       \
    struct GuardDisarm {                                                      \
        ~GuardDisarm() { g_guard_active = 0; }                                \
    } guardDisarm

// Level paddings for the baked generator params - from the shared source
// of truth (align_pads.h, vendored next to the generated headers); stage 2
// derives the raw size from base_l0's extent using the same baked values,
// so a local formula copy that drifts from the kernels silently corrupts
// every alignment.
static std::vector<int> level_paddings() {
    halide_align::PadParams pads{};
    pads.tile_size = kTile;
    pads.search_radius = kRadius;
    pads.max_level = kMaxLevel;
    return halide_align::level_paddings(pads);
}

// Hand-rolled fp16 input descriptor: the runtime headers expose no named
// 16-bit float element type, but the C API only needs a halide_buffer_t
// with type = (float, 16 bits).
static void make_f16_buffer(void *host, int w, int h,
                            halide_buffer_t *out, halide_dimension_t dims[2]) {
    memset(out, 0, sizeof(*out));
    out->host = reinterpret_cast<uint8_t *>(host);
    out->type = halide_type_t(halide_type_float, 16);
    out->dimensions = 2;
    dims[0] = {0, w, 1};
    dims[1] = {0, h, w};
    out->dim = dims;
}

// Rejects undersized input buffers with a clean Java error instead of a
// SIGSEGV deep inside the kernel. Returns true if the buffer is usable.
static bool check_input(JNIEnv *env, jobject rawBuf, int rawW, int rawH,
                        const char *what) {
    size_t need = (size_t)rawW * (size_t)rawH * 2u;  // fp16 = 2 bytes/px
    jlong cap = env->GetDirectBufferCapacity(rawBuf);
    if (cap >= 0 && (size_t)cap < need) {
        char m[160];
        snprintf(m, sizeof(m),
                 "%s: input buffer too small: %lld bytes, need %zu",
                 what, (long long)cap, need);
        LOGE("%s", m);
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), m);
        return false;
    }
    return true;
}

struct AlignCtx {
    Halide::Runtime::Buffer<uint8_t> levels[kMaxLevel + 1];
    // Flow-refinement precompute (alignflow_base output), once per burst.
    Halide::Runtime::Buffer<int32_t> gprod;
    int rawW = 0, rawH = 0;
    int ntx = 0, nty = 0;    // stage-2 (seed) grid at kMinLevel
    int nt0x = 0, nt0y = 0;  // refined output grid at level 0
    bool refine = true;      // g_refine_flow snapshot taken by nInit
};

// Throws IllegalStateException carrying the kernel name, Halide's error code
// and the remembered runtime error text, so the Java-side logger records the
// actual failure reason instead of just a null return.
static void throw_kernel_error(JNIEnv *env, const char *kernel, int err) {
    char m[768];
    snprintf(m, sizeof(m), "%s failed: err=%d: %s", kernel, err,
             g_last_halide_error[0] ? g_last_halide_error
                                    : "no Halide error message captured");
    LOGE("%s", m);
    jclass c = env->FindClass("java/lang/IllegalStateException");
    if (c != nullptr) env->ThrowNew(c, m);
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_particlesdevs_photoncamera_processing_cpu_HalideAlignment_nInit(
        JNIEnv *env, jclass clazz, jint rawW, jint rawH) {
    ALIGN_FAULT_GUARD(0);
    halide_set_error_handler(halide_log_error);
    auto *ctx = new (std::nothrow) AlignCtx();
    if (ctx == nullptr) return 0;
    ctx->rawW = rawW;
    ctx->rawH = rawH;
    ctx->refine = g_refine_flow;

    const std::vector<int> B = level_paddings();
    for (int l = 0; l <= kMaxLevel; l++) {
        int w = rawW / 2, h = rawH / 2;
        for (int i = 0; i < l; i++) { w /= 2; h /= 2; }
        ctx->levels[l] = Halide::Runtime::Buffer<uint8_t>(w + 2 * B[l], h + 2 * B[l]);
        ctx->levels[l].translate(0, -B[l]);
        ctx->levels[l].translate(1, -B[l]);
    }

    // Finest aligned level is kMinLevel: overlapped grid - kTile-texel
    // tiles with origins every kStride texels, last tile at the image edge
    // (must match the kernels' level_tiles(raw, level, tile, stride)).
    int w1 = rawW / 2, h1 = rawH / 2;
    for (int i = 0; i < kMinLevel; i++) { w1 /= 2; h1 /= 2; }
    ctx->ntx = std::max(1, (w1 - kTile) / kStride + 1);
    ctx->nty = std::max(1, (h1 - kTile) / kStride + 1);

    // Refinement output lives one level finer (raw/2); its grid formula is
    // the level_tiles convention with level = 0. Allocated here so gprod
    // and the refined output agree on the extent.
    ctx->nt0x = std::max(1, (rawW / 2 - kTile) / kStride + 1);
    ctx->nt0y = std::max(1, (rawH / 2 - kTile) / kStride + 1);
    if (ctx->refine) {
        // (3, nt0x, nt0y) int32 - the per-tile Gauss-Newton normal-matrix
        // products; ~550 KB for a 4000x3000 raw.
        ctx->gprod = Halide::Runtime::Buffer<int32_t>(3, ctx->nt0x, ctx->nt0y);
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jint JNICALL
Java_com_particlesdevs_photoncamera_processing_cpu_HalideAlignment_nBase(
        JNIEnv *env, jclass clazz, jlong handle, jobject rawBuf, jfloat white) {
    ALIGN_FAULT_GUARD(-3);
    auto *ctx = reinterpret_cast<AlignCtx *>(handle);
    if (ctx == nullptr) return -1;
    void *pix = env->GetDirectBufferAddress(rawBuf);
    if (pix == nullptr) {
        LOGE("nBase: buffer is not direct");
        return -2;
    }
    if (!check_input(env, rawBuf, ctx->rawW, ctx->rawH, "nBase")) return -3;
    g_last_halide_error[0] = '\0';
    halide_buffer_t base;
    halide_dimension_t base_dims[2];
    make_f16_buffer(pix, ctx->rawW, ctx->rawH, &base, base_dims);
    int err = alignburst_base_f16(&base, white,
                                  ctx->levels[0].raw_buffer(),
                                  ctx->levels[1].raw_buffer(),
                                  ctx->levels[2].raw_buffer(),
                                  ctx->levels[3].raw_buffer(),
                                  ctx->levels[4].raw_buffer());
    if (err != 0) {
        throw_kernel_error(env, "alignburst_base_f16", err);
        return err;
    }
    if (ctx->refine) {
        // Per-tile gradient products at level 0 from the just-realized
        // base_l0 (central differences reach +-1 texel past the tile; the
        // existing padding covers it). alignflow_base has no f16 variant:
        // it reads only the u8 level, so the input flavor is irrelevant.
        int ferr = alignflow_base(ctx->levels[0].raw_buffer(),
                                  ctx->gprod.raw_buffer());
        if (ferr != 0) {
            throw_kernel_error(env, "alignflow_base", ferr);
            return ferr;
        }
    }
    return 0;
}

JNIEXPORT jfloatArray JNICALL
Java_com_particlesdevs_photoncamera_processing_cpu_HalideAlignment_nAlignFrame(
        JNIEnv *env, jclass clazz, jlong handle, jobject rawBuf, jfloat white) {
    ALIGN_FAULT_GUARD(nullptr);
    auto *ctx = reinterpret_cast<AlignCtx *>(handle);
    if (ctx == nullptr) return nullptr;
    void *pix = env->GetDirectBufferAddress(rawBuf);
    if (pix == nullptr) {
        LOGE("nAlignFrame: buffer is not direct");
        return nullptr;
    }
    if (!check_input(env, rawBuf, ctx->rawW, ctx->rawH, "nAlignFrame")) {
        return nullptr;
    }

    const bool refine = ctx->refine;
    const int gx = refine ? ctx->nt0x : ctx->ntx;
    const int gy = refine ? ctx->nt0y : ctx->nty;
    const jsize n = 2 * gx * gy;
    halide_buffer_t alt;
    halide_dimension_t alt_dims[2];
    make_f16_buffer(pix, ctx->rawW, ctx->rawH, &alt, alt_dims);
    Halide::Runtime::Buffer<float> out(2, ctx->ntx, ctx->nty);

    g_last_halide_error[0] = '\0';
    int err = alignburst_f16(ctx->levels[0].raw_buffer(),
                             ctx->levels[1].raw_buffer(),
                             ctx->levels[2].raw_buffer(),
                             ctx->levels[3].raw_buffer(),
                             ctx->levels[4].raw_buffer(),
                             &alt,
                             white,
                             out.raw_buffer());
    if (err != 0) {
        throw_kernel_error(env, "alignburst_f16", err);
        return nullptr;
    }

    // out(c, tx, ty) -> flat [c][ty][tx] with tx fastest, matching the
    // indexing used by HalideAlignment.Run().
    auto repack = [&](Halide::Runtime::Buffer<float> &buf, int nx, int ny) {
        std::vector<jfloat> f(2 * (size_t)nx * ny);
        for (int c = 0; c < 2; c++)
            for (int ty = 0; ty < ny; ty++)
                for (int tx = 0; tx < nx; tx++)
                    f[(size_t)c * nx * ny + (size_t)ty * nx + tx] = buf(c, tx, ty);
        return f;
    };

    std::vector<jfloat> flat;
    if (refine) {
        // Median the L1 seed BEFORE refinement (the measured order - see
        // FLOW_REFINEMENT.md section 6 in the halide-align project): the
        // refinement's travel clamp can never rescue a seed mislocked by
        // more than max_travel, while the median repairs isolated mislocks
        // of any magnitude before they seed 4 child L0 tiles each.
        std::vector<jfloat> seedflat = repack(out, ctx->ntx, ctx->nty);
        median3x3_2ch(seedflat, ctx->ntx, ctx->nty);
        Halide::Runtime::Buffer<float> seed(2, ctx->ntx, ctx->nty);
        for (int c = 0; c < 2; c++)
            for (int ty = 0; ty < ctx->nty; ty++)
                for (int tx = 0; tx < ctx->ntx; tx++)
                    seed(c, tx, ty) =
                            seedflat[(size_t)c * ctx->ntx * ctx->nty +
                                     (size_t)ty * ctx->ntx + tx];

        // Refine with the same fp16 alt buffer + effective white the
        // hierarchical stage just used.
        Halide::Runtime::Buffer<float> refined(2, ctx->nt0x, ctx->nt0y);
        int ferr = alignflow_f16(ctx->levels[0].raw_buffer(),
                                 ctx->gprod.raw_buffer(),
                                 &alt,
                                 white,
                                 seed.raw_buffer(),
                                 refined.raw_buffer());
        if (ferr != 0) {
            throw_kernel_error(env, "alignflow_f16", ferr);
            return nullptr;
        }
        // L0 output median: cheap insurance - after a medianed-seed
        // refinement only single-digit isolated outliers per frame remain
        // (drop on phone budgets if every millisecond counts).
        flat = repack(refined, ctx->nt0x, ctx->nt0y);
        median3x3_2ch(flat, ctx->nt0x, ctx->nt0y);
    } else {
        // Plain path: median the stage-2 field in place (isolated mislocks
        // -> neighborhood median) before handing it to the atlas packing.
        flat = repack(out, ctx->ntx, ctx->nty);
        median3x3_2ch(flat, ctx->ntx, ctx->nty);
    }

    jfloatArray result = env->NewFloatArray(n);
    if (result == nullptr) return nullptr;
    env->SetFloatArrayRegion(result, 0, n, flat.data());
    return result;
}

JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_processing_cpu_HalideAlignment_nRelease(
        JNIEnv *env, jclass clazz, jlong handle) {
    ALIGN_FAULT_GUARD((void)0);
    auto *ctx = reinterpret_cast<AlignCtx *>(handle);
    if (ctx != nullptr) {
        for (auto &l : ctx->levels) l.deallocate();
        if (ctx->refine) ctx->gprod.deallocate();
        delete ctx;
    }
}

// Lets the Java side pick the vector grid and atlas broadcast for this
// run of the library: refined arrays are on the level-0 grid (1:1 with
// atlas cells), plain ones on the level-1 grid (2x2 broadcast).
JNIEXPORT jboolean JNICALL
Java_com_particlesdevs_photoncamera_processing_cpu_HalideAlignment_nRefineEnabled(
        JNIEnv *env, jclass clazz) {
    return g_refine_flow ? JNI_TRUE : JNI_FALSE;
}

// Runtime A/B switch for the flow refinement (the ESD4D "Halide flow
// refinement" tunable). Must be called before nInit; nInit snapshots the
// flag so a whole burst runs one consistent mode.
JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_processing_cpu_HalideAlignment_nSetRefineEnabled(
        JNIEnv *env, jclass clazz, jboolean on) {
    g_refine_flow = on != JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_processing_cpu_HalideAlignment_nSetThreads(
        JNIEnv *env, jclass clazz, jint n) {
    ALIGN_FAULT_GUARD((void)0);
    halide_set_num_threads(n);
}

}  // extern "C"
