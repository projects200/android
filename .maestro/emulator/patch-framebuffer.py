#!/usr/bin/env python3
"""Apply the reviewed metadata patch only to the pinned, byte-exact gfxstream blob.

This is an executable patch description, not a best-effort port across revisions.
Every anchor has an exact cardinality. All transformations complete in memory
before either source asset is written. Native ownership and return values stay
unchanged; trace reasons are internal fixed literals, never guest strings.
"""

import hashlib
import json
from pathlib import Path
import re
import sys
import textwrap


ASSETS = Path(__file__).resolve().parent


def git_blob(content):
    return hashlib.sha1(b"blob " + str(len(content)).encode("ascii") + b"\0" + content).hexdigest()


def replace_once(text, before, after):
    if text.count(before) != 1:
        raise ValueError("Pinned patch anchor changed: " + before)
    return text.replace(before, after, 1)


def insert_line(text, anchor, addition, *, before=False, count=1):
    lines = text.splitlines()
    matches = [index for index, line in enumerate(lines) if line.strip() == anchor]
    if len(matches) != count:
        raise ValueError("Pinned patch line changed: " + anchor)
    for index in reversed(matches):
        indent = lines[index][:len(lines[index]) - len(lines[index].lstrip())]
        extra = [indent + line if line else "" for line in addition.splitlines()]
        offset = index if before else index + 1
        lines[offset:offset] = extra
    return "\n".join(lines) + "\n"


def function(text, name, transform):
    # Definitions in this pinned file have an unindented closing brace. Class
    # declarations are not qualified, so they cannot match this definition anchor.
    pattern = re.compile(r"(?m)^(?![ \t/*])[^\n;]*\bFrameBuffer::Impl::" + re.escape(name) + r"\(")
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise ValueError("Pinned function changed: " + name)
    start = matches[0].start()
    end_match = re.search(r"(?m)^}[ \t]*$", text[start:])
    if not end_match:
        raise ValueError("Pinned function terminator changed: " + name)
    end = start + end_match.end()
    original = text[start:end].rstrip() + "\n"
    return text[:start] + transform(original).rstrip() + text[end:]


def at_entry(body, addition):
    # Signatures can span lines, but the first line ending with '{' starts the body.
    lines = body.splitlines()
    index = next(index for index, line in enumerate(lines) if line.rstrip().endswith("{"))
    lines[index + 1:index + 1] = ["    " + line for line in addition.splitlines()]
    return "\n".join(lines) + "\n"


def clear_records(body, reasons):
    anchor = "m_colorbuffers.clear();"
    if sum(line.strip() == anchor for line in body.splitlines()) != len(reasons):
        raise ValueError("Pinned color-buffer clear sites changed")
    for reason in reversed(reasons):
        # Work from the end so each clear receives its own native-path reason.
        lines = body.splitlines()
        indices = [index for index, line in enumerate(lines) if line.strip() == anchor]
        index = indices[len(reasons) - 1]
        indent = lines[index][:-len(anchor)]
        lines[index:index] = [
            indent + "for (const auto& entry : m_colorbuffers) {",
            indent + '    cbtrace::record("erase", entry.first, "' + reason + '", entry.second.refcount, 1);',
            indent + "}",
        ]
        # Hide already handled anchors only in the patch algorithm, not native code.
        lines[index + 3] = indent + "m_colorbuffers.clear(); /* traced */"
        body = "\n".join(lines) + "\n"
        reasons = reasons[:-1]
    return body.replace("m_colorbuffers.clear(); /* traced */", anchor)


def patch_source(source):
    source = replace_once(source, '#include "frame_buffer.h"',
                          '#include "frame_buffer.h"\n#include "color_buffer_trace.h"')
    source = insert_line(
        source, "IColorBufferRef findColorBuffer(HandleType p_colorbuffer) override;",
        "IColorBufferRef findColorBufferTraced(HandleType p_colorbuffer, const char* event, const char* reason);\n"
        "void traceColorBufferState(HandleType handle, const char* event, const char* reason);")
    source = replace_once(
        source, "bool closeColorBufferLocked(HandleType p_colorbuffer, bool forced = false);",
        'bool closeColorBufferLocked(HandleType p_colorbuffer, bool forced = false, const char* traceReason = "guest");')
    source = replace_once(
        source, "bool decColorBufferRefCountLocked(HandleType p_colorbuffer);",
        'bool decColorBufferRefCountLocked(HandleType p_colorbuffer, const char* traceReason = "final_ref");')

    def registration(body):
        pattern = re.compile(r"m_colorbuffers\.try_emplace\(handle,\s*"
                             r"ReferenceCountedColorBuffer\{std::move\(cb\), ([01]), false, 0\}\);")
        reasons = iter(("create_refcount_pipe", "create_legacy", "create_guest"))
        def instrument(match):
            reason = next(reasons)
            return 'cbtrace::registered(' + match.group(0)[:-1] + ', "' + reason + '");'
        body, count = pattern.subn(instrument, body)
        if count != 3:
            raise ValueError("Pinned registration sites changed")
        return body
    source = function(source, "createColorBufferWithResourceHandleLocked", registration)

    def guest_open(body):
        body = replace_once(body, "if (m_refCountPipeEnabled) return 0;", textwrap.dedent('''\
            if (m_refCountPipeEnabled) {
                    traceColorBufferState(p_colorbuffer, "open", "refcount_pipe");
                    return 0;
                }'''))
        body = insert_line(body, "if (c == m_colorbuffers.end()) {",
                           'cbtrace::record("open", p_colorbuffer, "guest", -1, 0);')
        return insert_line(body, "c->second.refcount++;",
                           'cbtrace::record("open", p_colorbuffer, "guest", c->second.refcount, 1);')
    source = function(source, "openColorBuffer", guest_open)
    source = function(source, "closeColorBuffer", lambda body: at_entry(
        body, 'cbtrace::record("close", p_colorbuffer, m_refCountPipeEnabled ? "refcount_pipe" : "guest");'))

    def close_locked(body):
        body = replace_once(body, "HandleType p_colorbuffer, bool forced)",
                            "HandleType p_colorbuffer, bool forced, const char* traceReason)")
        body = insert_line(body, "if (c == m_colorbuffers.end()) {",
                           'cbtrace::record("refcount", p_colorbuffer, traceReason, -1, 0);')
        body = replace_once(body, "if (--c->second.refcount == 0) {", textwrap.dedent('''\
            --c->second.refcount;
                    cbtrace::record("refcount", p_colorbuffer, forced ? "forced_close" : traceReason,
                                    c->second.refcount, 1);
                    if (c->second.refcount == 0) {'''))
        body = insert_line(body, "m_colorbuffers.erase(c);",
                           'cbtrace::record("erase", c->first, "forced_close", c->second.refcount, 1);', before=True)
        return insert_line(body,
                           "m_colorBufferDelayedCloseList.push_back({c->second.closedTs, p_colorbuffer});",
                           'cbtrace::record("delay", p_colorbuffer, traceReason, c->second.refcount, 1);')
    source = function(source, "closeColorBufferLocked", close_locked)

    def release_post(body):
        body = insert_line(body, "if (c == m_colorbuffers.end()) {",
                           'cbtrace::record("refcount", p_colorbuffer, "post_release", -1, 0);')
        body = replace_once(body, "if (--c->second.refcount == 0) {", textwrap.dedent('''\
            --c->second.refcount;
                cbtrace::record("refcount", p_colorbuffer, "post_release", c->second.refcount, 1);
                if (c->second.refcount == 0) {'''))
        return insert_line(body,
                           "m_colorBufferDelayedCloseList.push_back({c->second.closedTs, p_colorbuffer});",
                           'cbtrace::record("delay", p_colorbuffer, "post_release", c->second.refcount, 1);')
    source = function(source, "decColorBufferRefCountNoDestroy", release_post)
    source = function(source, "performDelayedColorBufferCloseLocked", lambda body: insert_line(
        body, "m_colorbuffers.erase(cb);",
        'cbtrace::record("erase", cb->first, forced ? "delayed_forced" : "delayed_expiry", cb->second.refcount, 1);',
        before=True))

    source = function(source, "onLastColorBufferRef", lambda body: insert_line(
        at_entry(body, 'cbtrace::record("destroy_request", handle, "final_ref");'),
        "if (!mOutstandingColorBufferDestroys.trySend((HandleType)handle)) {",
        'cbtrace::record("destroy_request", handle, "queue_full");'))
    source = function(source, "sweepColorBuffersLocked", lambda body: insert_line(
        body, "while (mOutstandingColorBufferDestroys.tryReceive(&handleToDestroy)) {",
        'cbtrace::record("destroy_request", handleToDestroy, "deferred_sweep");'))

    def decrement(body):
        body = replace_once(body, "HandleType p_colorbuffer)",
                            "HandleType p_colorbuffer, const char* traceReason)")
        body = insert_line(body, "const auto& it = m_colorbuffers.find(p_colorbuffer);",
                           'if (it == m_colorbuffers.end()) {\n'
                           '    cbtrace::record("refcount", p_colorbuffer, traceReason, -1, 0);\n}')
        body = insert_line(body, "it->second.refcount -= 1;",
                           'cbtrace::record("refcount", p_colorbuffer, traceReason, it->second.refcount, 1);')
        return insert_line(body, "m_colorbuffers.erase(p_colorbuffer);",
                           'cbtrace::record("erase", it->first, traceReason, it->second.refcount, 1);', before=True)
    source = function(source, "decColorBufferRefCountLocked", decrement)

    # Every clear in this pinned blob is covered, including both reset branches.
    source = function(source, "~Impl", lambda body: clear_records(body, ["shutdown"]))
    def snapshot(body):
        body = clear_records(body, ["snapshot_reset", "snapshot_stale"])
        return replace_once(body, "bool colorBuffersLoaded = tryLoadCollection(",
                            "bool colorBuffersLoaded = cbtrace::loadColorBuffers(")
    source = function(source, "onLoad", snapshot)

    def lookup(body):
        body = replace_once(body, "FrameBuffer::Impl::findColorBuffer(HandleType p_colorbuffer)",
                            "FrameBuffer::Impl::findColorBufferTraced(HandleType p_colorbuffer, "
                            "const char* event, const char* reason)")
        body = insert_line(body, "auto it = m_colorbuffers.find(p_colorbuffer);",
                           'cbtrace::record(event, p_colorbuffer, reason,\n'
                           '                it == m_colorbuffers.end() ? -1 : static_cast<int64_t>(it->second.refcount),\n'
                           '                it != m_colorbuffers.end());')
        return textwrap.dedent('''\
            IColorBufferRef FrameBuffer::Impl::findColorBuffer(HandleType p_colorbuffer) {
                return findColorBufferTraced(p_colorbuffer, "lookup", "generic");
            }

            void FrameBuffer::Impl::traceColorBufferState(HandleType handle, const char* event, const char* reason) {
                if (!cbtrace::enabled()) return;
                AutoLock colorBufferMapLock(m_colorBufferMapLock);
                const auto it = m_colorbuffers.find(handle);
                cbtrace::record(event, handle, reason,
                                it == m_colorbuffers.end() ? -1 : static_cast<int64_t>(it->second.refcount),
                                it != m_colorbuffers.end());
            }

            ''') + body
    source = function(source, "findColorBuffer", lookup)
    for method, reason in (("bindColorBufferToTexture", "texture"),
                           ("bindColorBufferToTexture2", "texture2"),
                           ("bindColorBufferToRenderbuffer", "renderbuffer")):
        source = function(source, method, lambda body, reason=reason: replace_once(
            body, "findColorBuffer(colorBufferHandle)",
            'findColorBufferTraced(colorBufferHandle, "bind", "' + reason + '")'))

    def post_open(body):
        body = insert_line(body, "auto c = m_colorbuffers.find(p_colorbuffer);",
                           'if (c == m_colorbuffers.end()) {\n'
                           '    cbtrace::record("open", p_colorbuffer, "post", -1, 0);\n}')
        return insert_line(body, "markOpened(&c->second);",
                           'cbtrace::record("open", p_colorbuffer, "post", c->second.refcount, 1);')
    source = function(source, "postImpl", post_open)
    # Window open observes the post-operation count (including guest-managed no-op).
    def window_open(body):
        body = insert_line(body, "auto c = m_colorbuffers.find(colorBufferHandle);",
                           'if (c == m_colorbuffers.end()) {\n'
                           '    cbtrace::record("open", colorBufferHandle, "window", -1, 0);\n}')
        return replace_once(body, "\n    }\n}\n", '\n        cbtrace::record("open", colorBufferHandle, "window", c->second.refcount, 1);\n    }\n}\n')
    source = function(source, "openColorBufferByWindow", window_open)
    def window_close(body):
        body = at_entry(body, 'cbtrace::record("close", colorBufferHandle, "window");')
        body = replace_once(body, "decColorBufferRefCountLocked(colorBufferHandle)",
                            'decColorBufferRefCountLocked(colorBufferHandle, "window")')
        return replace_once(body, "closeColorBufferLocked(colorBufferHandle)",
                            'closeColorBufferLocked(colorBufferHandle, false, "window")')
    source = function(source, "closeColorBufferByWindow", window_close)

    def process_cleanup(body):
        body = insert_line(body, "for (auto cb : colorBuffers) {",
                           'cbtrace::record("close", cb, "process_cleanup");')
        body = insert_line(body, "for (auto cb : procIte->second) {",
                           'cbtrace::record("close", cb, "process_cleanup");')
        body = replace_once(body, "decColorBufferRefCountLocked(cb)",
                            'decColorBufferRefCountLocked(cb, "process_cleanup")')
        if body.count("closeColorBufferLocked(cb, forced)") != 2:
            raise ValueError("Pinned process cleanup sites changed")
        return body.replace("closeColorBufferLocked(cb, forced)",
                            'closeColorBufferLocked(cb, forced, "process_cleanup")')
    source = function(source, "cleanupProcGLObjects_locked", process_cleanup)
    def surface_cleanup(body):
        body = insert_line(body, "for (auto cb : colorBuffers) {",
                           'cbtrace::record("close", cb, "surface_destroy");')
        body = replace_once(body, "decColorBufferRefCountLocked(cb)",
                            'decColorBufferRefCountLocked(cb, "surface_destroy")')
        return replace_once(body, "closeColorBufferLocked(cb)",
                            'closeColorBufferLocked(cb, false, "surface_destroy")')
    return function(source, "destroyEmulatedEglWindowSurfaceLocked", surface_cleanup)


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch-framebuffer.py <gfxstream-source-dir>")
    root = Path(sys.argv[1]).resolve(strict=True)
    target = root / "host" / "frame_buffer.cpp"
    inputs = json.loads((ASSETS / "inputs.json").read_text(encoding="utf-8"))
    content = target.read_bytes()
    if git_blob(content) != inputs["framebuffer_blob"]:
        raise SystemExit("Refusing patch: gfxstream frame_buffer.cpp differs from the pinned source blob")
    header = target.with_name("color_buffer_trace.h")
    if header.exists():
        raise SystemExit("Refusing patch: native trace header already exists")
    patched = patch_source(content.decode("utf-8"))
    header.write_bytes((ASSETS / "color-buffer-trace.h").read_bytes())
    target.write_text(patched, encoding="utf-8")


if __name__ == "__main__":
    main()
