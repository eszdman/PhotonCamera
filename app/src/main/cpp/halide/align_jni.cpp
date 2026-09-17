// JNI bridge for the Halide CPU burst alignment (halide-align project).
// Frames arrive as the tight fp16 half-float buffers produced by
// Allocator.createF16 (per-Bayer-site black/white normalized). white is the
// effective white in normalized units: 1.0 for the base frame,
// pair.layerMpy for an alt frame.
//
// Fault trapping: signals (SIGILL/SIGSEGV/SIGBUS/SIGFPE/SIGABRT) raised by
// the kernels are caught, logged to logcat with the faulting PC, the raw
// instruction word at the PC and the nearest symbol, and - when they hit the
// thread that entered the JNI call - converted into a Java
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

#define TAG "HalideAlignment"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int kMaxLevel = 4;
constexpr int kTile = 16;
constexpr int kRadius = 4;
constexpr int kMinLevel = 1;

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
    int rawW = 0, rawH = 0;
    int ntx = 0, nty = 0;  // alignment vector grid at kMinLevel
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

    const std::vector<int> B = level_paddings();
    for (int l = 0; l <= kMaxLevel; l++) {
        int w = rawW / 2, h = rawH / 2;
        for (int i = 0; i < l; i++) { w /= 2; h /= 2; }
        ctx->levels[l] = Halide::Runtime::Buffer<uint8_t>(w + 2 * B[l], h + 2 * B[l]);
        ctx->levels[l].translate(0, -B[l]);
        ctx->levels[l].translate(1, -B[l]);
    }

    // Finest aligned level is kMinLevel: one vector per kTile texels there.
    int w1 = rawW / 2, h1 = rawH / 2;
    for (int i = 0; i < kMinLevel; i++) { w1 /= 2; h1 /= 2; }
    ctx->ntx = (w1 + kTile - 1) / kTile;
    ctx->nty = (h1 + kTile - 1) / kTile;
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
    return err;
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

    const jsize n = 2 * ctx->ntx * ctx->nty;
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

    jfloatArray result = env->NewFloatArray(n);
    if (result == nullptr) return nullptr;
    // out(c, tx, ty) -> flat [c][ty][tx] with tx fastest, matching the
    // indexing used by HalideAlignment.Run().
    std::vector<jfloat> flat(n);
    for (int c = 0; c < 2; c++)
        for (int ty = 0; ty < ctx->nty; ty++)
            for (int tx = 0; tx < ctx->ntx; tx++)
                flat[(size_t)c * ctx->ntx * ctx->nty + (size_t)ty * ctx->ntx + tx] =
                        out(c, tx, ty);
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
        delete ctx;
    }
}

JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_processing_cpu_HalideAlignment_nSetThreads(
        JNIEnv *env, jclass clazz, jint n) {
    ALIGN_FAULT_GUARD((void)0);
    halide_set_num_threads(n);
}

}  // extern "C"
