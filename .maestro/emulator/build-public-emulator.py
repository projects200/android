#!/usr/bin/env python3
"""Build and package one coherent public-source emulator pair on a Linux CI host.

The shell entry point passes exactly two paths. This process needs no credentials.
All fetched sources, tool state, build outputs and packages stay under RUNNER_TEMP.
The first Repo resolution is locked and checked again before/after the second build.
"""

import gzip
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import xml.etree.ElementTree as ET


ASSETS = Path(__file__).resolve().parent
PATCH_ASSETS = ("inputs.json", "patch-framebuffer.py", "color-buffer-trace.h", "trace-schema.json")
SHA1 = re.compile(r"[0-9a-f]{40}\Z")
MAX_ARCHIVE_BYTES = 4 * 1024 ** 3
MAX_EXPANDED_BYTES = 12 * 1024 ** 3
MAX_MEMBER_BYTES = 2 * 1024 ** 3
MAX_MEMBERS = 50000


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def patch_digest():
    # Unambiguous length-prefixed bundle hash. Includes every patch/config input,
    # not just the transform program while omitting the native logger's contents.
    digest = hashlib.sha256()
    for name in PATCH_ASSETS:
        content = (ASSETS / name).read_bytes()
        digest.update(name.encode("ascii") + b"\0" + str(len(content)).encode("ascii") + b"\0")
        digest.update(content)
    return digest.hexdigest()


def validate_paths(source, output, runner_temp, application):
    source = source.resolve()
    output = output.resolve()
    runner_temp = runner_temp.resolve(strict=True)
    application = application.resolve(strict=True)
    for path in (source, output):
        if path == runner_temp or not path.is_relative_to(runner_temp):
            raise ValueError("Source and output must be separate child directories of RUNNER_TEMP")
        if path.is_relative_to(application) or application.is_relative_to(path):
            raise ValueError("Native build paths must not overlap the application checkout")
    if source.is_relative_to(output) or output.is_relative_to(source):
        raise ValueError("Source and package output directories must not overlap")
    return source, output


def empty_directory(path):
    if path.exists() and (not path.is_dir() or any(path.iterdir())):
        raise ValueError("Native build requires fresh empty source and output directories")
    path.mkdir(parents=True, exist_ok=True)


def private_environment(home):
    home.mkdir(mode=0o700)
    (home / ".gitconfig").write_text(
        "[user]\n\tname = Public Emulator Diagnostics\n"
        "\temail = emulator-diagnostics@example.invalid\n[color]\n\tui = false\n",
        encoding="utf-8",
    )
    # Upstream nested Bazel calls have no command-line jobs override. Home rc is
    # loaded after workspace rc, and this private HOME is shared by both builds.
    (home / ".bazelrc").write_text("build --jobs=2\n", encoding="utf-8")
    # Do not propagate GitHub tokens, app secrets, SSH agents, git credential
    # helpers, private configuration, proxies, or a caller's tracing environment.
    return {
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "HOME": str(home),
        "TMPDIR": str(home.parent),
        "LANG": "C.UTF-8",
        "LC_ALL": "C.UTF-8",
        "GIT_TERMINAL_PROMPT": "0",
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_CONFIG_GLOBAL": str(home / ".gitconfig"),
        "CMAKE_BUILD_PARALLEL_LEVEL": "2",
    }


def run(command, cwd, env, *, capture=False):
    result = subprocess.run(
        [str(part) for part in command], cwd=cwd, env=env, check=True,
        stdin=subprocess.DEVNULL, stdout=subprocess.PIPE if capture else None,
        text=capture,
    )
    return result.stdout.strip() if capture else None


def verify_checkout(source, inputs, env):
    for path, key in (("external/qemu", "qemu_revision"),
                      ("hardware/google/gfxstream", "gfxstream_revision"),
                      (".repo/manifests", "manifest_revision")):
        actual = run(["git", "-C", source / path, "rev-parse", "HEAD"], source, env, capture=True)
        if actual != inputs[key]:
            raise ValueError("Resolved checkout does not match required " + key)


def manifest_projects(manifest):
    root = ET.parse(manifest).getroot()
    projects = {}
    for project in root.iter("project"):
        name = project.get("name")
        path = project.get("path", name)
        revision = project.get("revision", "")
        if not name or not path or not SHA1.fullmatch(revision) or path in projects:
            raise ValueError("Resolved manifest contains an unpinned or duplicate project")
        projects[path] = revision
    if not projects:
        raise ValueError("Resolved manifest contains no projects")
    return projects


def validate_install_tree(install):
    if not install.is_dir() or not (install / "emulator").is_file():
        raise ValueError("Upstream install did not produce distribution/emulator/emulator")
    if not os.access(install / "emulator", os.X_OK):
        raise ValueError("Installed emulator launcher is not executable")
    if not (install / "lib64").is_dir() or not (install / "qemu").is_dir():
        raise ValueError("Upstream install is not a complete emulator runtime tree")
    paths = [install, *sorted(install.rglob("*"))]
    if len(paths) > MAX_MEMBERS:
        raise ValueError("Installed emulator exceeds package member limit")
    expanded = 0
    for path in paths:
        status = path.lstat()
        if status.st_mode & (stat.S_ISUID | stat.S_ISGID):
            raise ValueError("Installed emulator contains a privileged file")
        if path.is_symlink():
            link = os.readlink(path)
            if os.path.isabs(link) or not path.resolve(strict=True).is_relative_to(install.resolve()):
                raise ValueError("Installed emulator contains an external or broken symbolic link")
        elif stat.S_ISREG(status.st_mode):
            if status.st_size > MAX_MEMBER_BYTES:
                raise ValueError("Installed emulator exceeds package member byte limit")
            expanded += status.st_size
        elif not stat.S_ISDIR(status.st_mode):
            raise ValueError("Installed emulator contains a non-runtime special file")
    if expanded > MAX_EXPANDED_BYTES:
        raise ValueError("Installed emulator exceeds package expanded byte limit")
    return paths


def package_install(install, destination):
    paths = validate_install_tree(install)
    # Package the entire upstream CMake install tree, not selected libraries copied
    # into an unrelated SDK. Preserve executable modes and internal relative links.
    with destination.open("xb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode="w", format=tarfile.PAX_FORMAT) as archive:
                for path in paths:
                    name = "emulator" if path == install else "emulator/" + path.relative_to(install).as_posix()
                    member = archive.gettarinfo(str(path), arcname=name)
                    # tarfile can deduplicate inode aliases as hard links. Export
                    # each regular file independently; the consumer rejects links.
                    if member.islnk():
                        member.type = tarfile.REGTYPE
                        member.linkname = ""
                        member.size = path.stat().st_size
                    member.uid = member.gid = 0
                    member.uname = member.gname = ""
                    member.mtime = 0
                    member.pax_headers = {}
                    if member.isreg():
                        with path.open("rb") as data:
                            archive.addfile(member, data)
                    else:
                        archive.addfile(member)
    if destination.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ValueError("Installed emulator exceeds compressed package byte limit")
    return sha256_file(destination)


def build_pair(source, output, work, inputs, env):
    tooling = work / "git-repo"
    tooling.mkdir()
    run(["git", "-C", tooling, "init", "--quiet"], work, env)
    run(["git", "-C", tooling, "fetch", "--depth=1", inputs["repo_url"], inputs["repo_revision"]], work, env)
    run(["git", "-C", tooling, "checkout", "--detach", "FETCH_HEAD"], work, env)
    repo = [sys.executable, tooling / "repo"]
    # The exact content-addressed Repo revision is checked out above and below.
    # --no-repo-verify disables tag-based version selection, not the commit pin.
    run([*repo, "init", "-u", inputs["manifest_url"], "-b", inputs["manifest_revision"],
         "--depth=1", "--platform=linux", "--no-clone-bundle",
         "--repo-url", inputs["repo_url"], "--repo-rev", inputs["repo_revision"],
         "--no-repo-verify"], source, env)
    local = source / ".repo" / "local_manifests"
    local.mkdir()
    override = ET.Element("manifest")
    for name, path, revision in (
        ("platform/external/qemu", "external/qemu", inputs["qemu_revision"]),
        ("platform/hardware/google/gfxstream", "hardware/google/gfxstream", inputs["gfxstream_revision"]),
    ):
        ET.SubElement(override, "extend-project", name=name, path=path, revision=revision)
    ET.ElementTree(override).write(local / "color-buffer.xml", encoding="utf-8", xml_declaration=True)
    run([*repo, "sync", "-c", "-d", "-j4", "--no-clone-bundle"], source, env)
    verify_checkout(source, inputs, env)
    repo_revision = run(["git", "-C", source / ".repo" / "repo", "rev-parse", "HEAD"], source, env, capture=True)
    if repo_revision != inputs["repo_revision"]:
        raise ValueError("Repo launcher changed its pinned revision")
    resolved = output / "resolved-manifest.xml"
    run([*repo, "manifest", "-r", "-o", resolved], source, env)
    projects = manifest_projects(resolved)
    if projects.get("external/qemu") != inputs["qemu_revision"] or projects.get("hardware/google/gfxstream") != inputs["gfxstream_revision"]:
        raise ValueError("Resolved manifest does not contain the required native snapshots")

    build = work / "build"
    qemu = source / "external" / "qemu"
    command = ["bash", qemu / "android" / "rebuild.sh", "--out", build,
               "--target", inputs["build_target"], "--config", inputs["build_config"],
               "--crash", "none", "--ccache", "none", "--test_jobs", "2",
               "--sdk_build_number", inputs["sdk_build_number"]]
    run(command, qemu, env)
    baseline_hash = package_install(build / inputs["install_relative_path"], output / "baseline.tar.gz")
    run([sys.executable, ASSETS / "patch-framebuffer.py", source / "hardware" / "google" / "gfxstream"], source, env)
    # Clean is the upstream task that would discard the already-built baseline.
    # Reconfigure and the full Compile/install task still run and rebuild all
    # affected dependents. Never replace a library in an SDK install tree.
    run([*command, "--task-disable", "Clean"], qemu, env)
    instrumented_hash = package_install(build / inputs["install_relative_path"], output / "instrumented.tar.gz")
    verify_checkout(source, inputs, env)
    after = work / "resolved-after.xml"
    run([*repo, "manifest", "-r", "-o", after], source, env)
    if manifest_projects(after) != projects:
        raise ValueError("Resolved source revisions changed between emulator builds")
    if baseline_hash == instrumented_hash:
        raise ValueError("Baseline and instrumented packages unexpectedly have identical hashes")
    provenance = {
        "schema": 1,
        "scope": inputs["scope"],
        "manifest_revision": inputs["manifest_revision"],
        "qemu_revision": inputs["qemu_revision"],
        "gfxstream_revision": inputs["gfxstream_revision"],
        "repo_revision": inputs["repo_revision"],
        "resolved_manifest_sha256": sha256_file(resolved),
        "patch_sha256": patch_digest(),
        "patch_hash_format": "sha256(name NUL decimal-byte-length NUL content), inputs.json/patch-framebuffer.py/color-buffer-trace.h/trace-schema.json in this order",
        "patch_asset_sha256": {name: sha256_file(ASSETS / name) for name in PATCH_ASSETS},
        "archives": {
            "baseline": {"filename": "baseline.tar.gz", "sha256": baseline_hash},
            "instrumented": {"filename": "instrumented.tar.gz", "sha256": instrumented_hash},
        },
        "build": {
            "target": inputs["build_target"], "configuration": inputs["build_config"],
            "sdk_build_number": inputs["sdk_build_number"],
            "entry": "external/qemu/android/rebuild.sh", "install": inputs["install_relative_path"],
            "standalone_gfxstream": False, "project_count": len(projects),
            "compile_jobs": 2, "test_jobs": 2, "bazel_jobs": 2,
        },
        "framebuffer_before_git_blob": inputs["framebuffer_blob"],
        "framebuffer_after_sha256": sha256_file(source / "hardware/google/gfxstream/host/frame_buffer.cpp"),
    }
    (output / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: build-public-emulator.sh <source-dir> <output-dir>")
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        raise SystemExit("The public emulator pair requires a Linux x86_64 native builder")
    if not os.environ.get("RUNNER_TEMP"):
        raise SystemExit("RUNNER_TEMP must identify the isolated native build workspace")
    source, output = validate_paths(Path(sys.argv[1]), Path(sys.argv[2]),
                                    Path(os.environ["RUNNER_TEMP"]), ASSETS.parents[1])
    for binary in ("git", "bash", "make", "gcc", "g++"):
        if not shutil.which(binary, path="/usr/local/bin:/usr/bin:/bin"):
            raise SystemExit("Native builder is missing required host tool: " + binary)
    empty_directory(source)
    empty_directory(output)
    inputs = json.loads((ASSETS / "inputs.json").read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory(prefix="public-emulator-", dir=os.environ["RUNNER_TEMP"]) as temp:
        work = Path(temp)
        env = private_environment(work / "home")
        build_pair(source, output, work, inputs, env)
    print("Built complete public-source baseline and instrumented emulator packages; provenance is ready.")


if __name__ == "__main__":
    main()
