// Diagnostic-only Linux host instrumentation. No renderer ownership is stored here.
#pragma once

#include <atomic>
#include <cerrno>
#include <climits>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <utility>

namespace gfxstream {
namespace host {
namespace cbtrace {

// Constant-initialized, trivially destructible process-lifetime state. In particular,
// logging from late renderer destructors cannot access a destroyed C++ mutex.
static pthread_mutex_t s_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_once_t s_once = PTHREAD_ONCE_INIT;
static std::atomic<pid_t> s_owner{0};
static std::atomic<bool> s_failed{false};
static int s_fd = -1;
static uint32_t* s_status = nullptr;
static_assert(__atomic_always_lock_free(sizeof(uint32_t), nullptr),
              "The Linux native trace status requires lock-free uint32 stores.");
static uint64_t s_seq = 0;
static uint64_t s_bytes = 0;
static uint64_t s_records = 0;
static bool s_ended = false;
static constexpr uint64_t kMaxRecords = 1000000;
static constexpr uint64_t kMaxBytes = 128 * 1024 * 1024;
static bool s_started = false;
static constexpr size_t kRecordBytes = 512;

struct PreserveErrno {
    int value = errno;
    ~PreserveErrno() { errno = value; }
};

// This status is anonymous shared memory supplied by the parent. In particular,
// failure stores do not depend on successful writes to the JSONL filesystem.
static void markFailed() {
    s_failed.store(true, std::memory_order_relaxed);
    if (s_status) __atomic_store_n(s_status, 3U, __ATOMIC_SEQ_CST);
}

static void unlock() {
    if (pthread_mutex_unlock(&s_lock) != 0) markFailed();
}

static void disable() {
    markFailed();
    if (s_fd >= 0) {
        close(s_fd);
        s_fd = -1;
    }
}

static bool initializeStatus() {
    const char* value = getenv("GFXSTREAM_COLOR_BUFFER_TRACE_STATUS_FD");
    if (!value || !*value) return false;
    unsigned int descriptor = 0;
    for (const char* digit = value; *digit; ++digit) {
        if (*digit < '0' || *digit > '9') return false;
        const unsigned int next = static_cast<unsigned int>(*digit - '0');
        if (descriptor > (static_cast<unsigned int>(INT_MAX) - next) / 10) return false;
        descriptor = descriptor * 10 + next;
    }
    struct stat status{};
    if (fstat(static_cast<int>(descriptor), &status) != 0 || status.st_size != 4) return false;
    void* mapping = mmap(nullptr, 4, PROT_READ | PROT_WRITE, MAP_SHARED,
                         static_cast<int>(descriptor), 0);
    if (mapping == MAP_FAILED) return false;
    // mmap alignment permits lock-free native uint32 atomics. The parent seals
    // this memfd's size and retains its mapping until actual renderer death.
    s_status = static_cast<uint32_t*>(mapping);
    if (__atomic_load_n(s_status, __ATOMIC_SEQ_CST) != 0) {
        markFailed();
        return false;
    }
    return true;
}

// Caller holds s_lock, including during pthread_once initialization. One bounded
// write, no EINTR loop, queues, vectors, strings, callbacks, or per-event allocation.
static bool emit(const char* event, const char* reason, int64_t handle,
                 int64_t refcount, int found) {
    timespec now{};
    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) {
        disable();
        return false;
    }
    char handleText[24] = "null";
    char refcountText[24] = "null";
    if (handle >= 0) {
        snprintf(handleText, sizeof(handleText), "%llu", static_cast<unsigned long long>(handle));
    }
    if (refcount >= 0) {
        snprintf(refcountText, sizeof(refcountText), "%llu", static_cast<unsigned long long>(refcount));
    }
    char record[kRecordBytes];
    const auto mono = static_cast<uint64_t>(now.tv_sec) * 1000000000ULL + now.tv_nsec;
    const int length = snprintf(
        record, sizeof(record),
        "{\"schema\":1,\"seq\":%llu,\"mono_ns\":%llu,\"pid\":%llu,\"tid\":%llu,"
        "\"event\":\"%s\",\"handle\":%s,\"reason\":\"%s\",\"refcount\":%s,\"found\":%s}\n",
        static_cast<unsigned long long>(++s_seq), static_cast<unsigned long long>(mono),
        static_cast<unsigned long long>(s_owner.load(std::memory_order_relaxed)),
        static_cast<unsigned long long>(syscall(SYS_gettid)), event, handleText, reason,
        refcountText, found < 0 ? "null" : (found ? "true" : "false"));
    if (length <= 0 || static_cast<size_t>(length) >= sizeof(record) ||
        s_bytes + static_cast<uint64_t>(length) > kMaxBytes) {
        disable();
        return false;
    }
    const ssize_t written = write(s_fd, record, static_cast<size_t>(length));
    if (written != length) {
        // A partial line or absent clean footer makes the capture invalid.
        disable();
        return false;
    }
    s_bytes += static_cast<uint64_t>(length);
    return true;
}

static void finish() {
    PreserveErrno preserve;
    if (s_owner.load(std::memory_order_relaxed) != getpid()) return;
    if (pthread_mutex_lock(&s_lock) != 0) {
        markFailed();
        return;
    }
    if (!s_failed.load(std::memory_order_relaxed) && s_fd >= 0 && !s_ended) {
        s_ended = emit("session_end", "clean", -1, -1, -1);
        if (s_ended) {
            uint32_t expected = 1;
            // A concurrent failure must never transition from failed back to clean.
            __atomic_compare_exchange_n(s_status, &expected, 2U, false,
                                        __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
        }
        // Keep both descriptor and shared mapping until process exit. A late
        // callback must mark failure even if appending its loss record fails.
    }
    unlock();
}

static void initialize() {
    s_owner.store(getpid(), std::memory_order_relaxed);
    const char* path = getenv("GFXSTREAM_COLOR_BUFFER_TRACE_FILE");
    if (!path || path[0] != '/') return;
    if (!initializeStatus()) return;
    if (pthread_mutex_lock(&s_lock) != 0) {
        markFailed();
        return;
    }
    s_fd = open(path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK, 0600);
    if (s_fd < 0) {
        disable();
        unlock();
        return;
    }
    struct stat status{};
    if (fstat(s_fd, &status) != 0 || !S_ISREG(status.st_mode) || status.st_nlink != 1 ||
        status.st_uid != geteuid() || (status.st_mode & 0777) != 0600 || atexit(finish) != 0) {
        disable();
        unlock();
        return;
    }
    s_started = emit("session_start", "enabled", -1, -1, -1);
    if (s_started) {
        uint32_t expected = 0;
        if (!__atomic_compare_exchange_n(s_status, &expected, 1U, false,
                                         __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST)) {
            disable();
        }
    }
    unlock();
}

static bool enabled() {
    PreserveErrno preserve;
    // A fork child does not enter a possibly inherited locked mutex, initialize a
    // second session in the parent's file, or overwrite a restart's existing file.
    const pid_t owner = s_owner.load(std::memory_order_relaxed);
    if (owner != 0 && owner != getpid()) return false;
    if (pthread_once(&s_once, initialize) != 0) {
        markFailed();
        return false;
    }
    return s_started && !s_failed.load(std::memory_order_relaxed);
}

static void record(const char* event, uint32_t handle, const char* reason,
                   int64_t refcount = -1, int found = -1) {
    PreserveErrno preserve;
    if (!enabled()) return;
    if (pthread_mutex_lock(&s_lock) != 0) {
        markFailed();
        return;
    }
    if (!s_failed.load(std::memory_order_relaxed) && s_fd >= 0) {
        if (s_ended) {
            // This atomic store precedes the fallible JSONL append. A zero-byte
            // ENOSPC/EINTR write cannot leave the parent seeing clean status.
            markFailed();
            emit("loss", "late_event", -1, -1, -1);
            disable();
        } else if (s_records >= kMaxRecords || s_bytes + 2 * kRecordBytes > kMaxBytes) {
            emit("loss", "limit", -1, -1, -1);
            disable();
        } else if (emit(event, reason, handle, refcount, found)) {
            ++s_records;
        }
    }
    unlock();
}

template <class InsertResult>
static void registered(const InsertResult& result, const char* reason) {
    if (result.second) {
        record("register", result.first->first, reason, result.first->second.refcount, 1);
    }
}

// Preserve tryLoadCollection's exact insertion/failure behavior, but observe the
// successful map insertion, not the loader's earlier construction of a ColorBuffer.
// The caller retains the native color-buffer-map lock throughout this operation.
template <class StreamType, class Collection, class LoadFunc>
static bool loadColorBuffers(StreamType* stream, Collection* collection, size_t maxSize,
                             LoadFunc&& loader) {
    const uint32_t size = stream->getBe32();
    if (size > maxSize) return false;
    for (uint32_t i = 0; i < size; ++i) {
        auto loadedVal = loader(stream);
        if (!loadedVal.has_value()) return false;
        const auto result = collection->emplace(std::move(*loadedVal));
        if (result.second) {
            record("register", result.first->first, "snapshot_load", result.first->second.refcount, 1);
        }
    }
    return true;
}

}  // namespace cbtrace
}  // namespace host
}  // namespace gfxstream
