// CI behavioral harness: compile the real logger and inject syscall failures.
#include <atomic>
#include <cerrno>
#include <climits>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <utility>

static int s_writeFailure = 0;
static bool s_clockFailure = false;
static bool s_lockFailure = false;
static bool s_exitFailure = false;

static ssize_t injected_write(int fd, const void* data, size_t size) {
    if (s_writeFailure == 1 || s_writeFailure == 2) {
        errno = s_writeFailure == 1 ? ENOSPC : EINTR;
        return -1;
    }
    if (s_writeFailure == 3) return ::write(fd, data, size / 2);
    return ::write(fd, data, size);
}

static int injected_clock(clockid_t clock, timespec* value) {
    if (s_clockFailure) {
        errno = EINVAL;
        return -1;
    }
    return ::clock_gettime(clock, value);
}

static int injected_lock(pthread_mutex_t* lock) {
    return s_lockFailure ? EINVAL : ::pthread_mutex_lock(lock);
}

static int injected_atexit(void (*callback)()) {
    return s_exitFailure ? -1 : ::atexit(callback);
}

#define write injected_write
#define clock_gettime injected_clock
#define pthread_mutex_lock injected_lock
#define atexit injected_atexit
#include "color-buffer-trace.h"
#undef atexit
#undef pthread_mutex_lock
#undef clock_gettime
#undef write

int main(int argc, char** argv) {
    if (argc != 2) return 2;
    const char* scenario = argv[1];
    if (!strcmp(scenario, "initial-write")) s_writeFailure = 1;
    if (!strcmp(scenario, "initial-clock")) s_clockFailure = true;
    if (!strcmp(scenario, "initial-lock")) s_lockFailure = true;
    if (!strcmp(scenario, "initial-atexit")) s_exitFailure = true;
    gfxstream::host::cbtrace::record("register", 42, "create_guest", 0, 1);
    if (!strcmp(scenario, "abrupt")) _exit(0);
    gfxstream::host::cbtrace::finish();
    if (!strcmp(scenario, "late-write")) s_writeFailure = 1;
    if (!strcmp(scenario, "late-eintr")) s_writeFailure = 2;
    if (!strcmp(scenario, "late-partial")) s_writeFailure = 3;
    if (!strcmp(scenario, "late-clock")) s_clockFailure = true;
    if (!strcmp(scenario, "late-lock")) s_lockFailure = true;
    if (!strncmp(scenario, "late-", 5)) {
        gfxstream::host::cbtrace::record("bind", 42, "texture", -1, 0);
        gfxstream::host::cbtrace::finish();
    }
    return 0;
}
