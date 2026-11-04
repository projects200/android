#!/usr/bin/env python3
"""Behavioral tests for native-build boundaries, manifest pins and complete archives."""

import importlib.util
import os
from pathlib import Path
import stat
import tarfile
import tempfile
import unittest
from unittest import mock


HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("public_emulator_build", HERE / "build-public-emulator.py")
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)


class NativeAssetsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.runner = self.root / "runner"
        self.runner.mkdir()
        self.application = self.root / "application"
        self.application.mkdir()

    def install(self):
        install = self.runner / "distribution" / "emulator"
        install.mkdir(parents=True)
        launcher = install / "emulator"
        launcher.write_bytes(b"launcher-test-bytes")
        launcher.chmod(0o755)
        (install / "lib64").mkdir()
        (install / "lib64" / "renderer.so").write_bytes(b"renderer-test-bytes")
        (install / "qemu").mkdir()
        (install / "qemu" / "runtime").write_bytes(b"runtime-test-bytes")
        return install

    def test_paths_require_separate_runner_children(self):
        source, output = BUILD.validate_paths(self.runner / "source", self.runner / "packages",
                                               self.runner, self.application)
        self.assertEqual(source, self.runner / "source")
        self.assertEqual(output, self.runner / "packages")
        for source, output in (
            (self.runner, self.runner / "packages"),
            (self.root / "elsewhere", self.runner / "packages"),
            (self.runner / "source", self.runner / "source" / "packages"),
            (self.runner / "packages" / "source", self.runner / "packages"),
        ):
            with self.subTest(source=source, output=output), self.assertRaises(ValueError):
                BUILD.validate_paths(source, output, self.runner, self.application)

    def test_paths_reject_application_overlap_and_symlink_escape(self):
        application = self.runner / "checkout"
        application.mkdir()
        with self.assertRaises(ValueError):
            BUILD.validate_paths(application / "source", self.runner / "packages", self.runner, application)
        (self.runner / "escape").symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            BUILD.validate_paths(self.runner / "escape" / "source", self.runner / "packages",
                                 self.runner, application)

    def test_empty_directory_never_reuses_existing_source(self):
        source = self.runner / "source"
        BUILD.empty_directory(source)
        (source / "user-file").write_text("preserve", encoding="utf-8")
        with self.assertRaises(ValueError):
            BUILD.empty_directory(source)
        self.assertEqual((source / "user-file").read_text(encoding="utf-8"), "preserve")

    def test_resolved_manifest_requires_each_project_revision(self):
        manifest = self.runner / "manifest.xml"
        revision = "a" * 40
        manifest.write_text('<manifest><project name="one" path="first" revision="' + revision + '"/>'
                            '<project name="two" revision="' + "b" * 40 + '"/></manifest>', encoding="utf-8")
        self.assertEqual(BUILD.manifest_projects(manifest), {"first": revision, "two": "b" * 40})
        for content in (
            '<manifest><default revision="main"/><project name="one"/></manifest>',
            '<manifest><project name="one" revision="main"/></manifest>',
            '<manifest><project name="one" path="same" revision="' + revision + '"/>'
            '<project name="two" path="same" revision="' + revision + '"/></manifest>',
            '<manifest/>',
        ):
            manifest.write_text(content, encoding="utf-8")
            with self.subTest(content=content), self.assertRaises(ValueError):
                BUILD.manifest_projects(manifest)

    def test_archive_preserves_entire_runtime_without_hardlinks(self):
        install = self.install()
        (install / "lib64" / "renderer-link.so").symlink_to("renderer.so")
        os.link(install / "lib64" / "renderer.so", install / "lib64" / "renderer-alias.so")
        destination = self.runner / "baseline.tar.gz"
        digest = BUILD.package_install(install, destination)
        self.assertEqual(digest, BUILD.sha256_file(destination))
        with tarfile.open(destination, "r:gz") as archive:
            members = {member.name: member for member in archive.getmembers()}
            self.assertEqual(set(members), {
                "emulator", "emulator/emulator", "emulator/lib64", "emulator/lib64/renderer.so",
                "emulator/lib64/renderer-alias.so", "emulator/lib64/renderer-link.so",
                "emulator/qemu", "emulator/qemu/runtime",
            })
            self.assertTrue(members["emulator/emulator"].mode & stat.S_IXUSR)
            self.assertTrue(members["emulator/lib64/renderer-link.so"].issym())
            self.assertEqual(members["emulator/lib64/renderer-link.so"].linkname, "renderer.so")
            self.assertFalse(any(member.islnk() for member in members.values()))
            for name in ("emulator/lib64/renderer.so", "emulator/lib64/renderer-alias.so"):
                self.assertEqual(archive.extractfile(name).read(), b"renderer-test-bytes")

    def test_archive_rejects_external_link_special_file_and_privilege(self):
        install = self.install()
        bad = install / "bad"
        bad.symlink_to(self.application, target_is_directory=True)
        with self.assertRaises(ValueError):
            BUILD.validate_install_tree(install)
        bad.unlink()
        os.mkfifo(bad)
        with self.assertRaises(ValueError):
            BUILD.validate_install_tree(install)
        bad.unlink()
        bad.write_bytes(b"privileged")
        bad.chmod(0o4755)
        with self.assertRaises(ValueError):
            BUILD.validate_install_tree(install)

    def test_archive_enforces_member_and_total_limits(self):
        install = self.install()
        with mock.patch.object(BUILD, "MAX_MEMBER_BYTES", 1), self.assertRaises(ValueError):
            BUILD.validate_install_tree(install)
        with mock.patch.object(BUILD, "MAX_EXPANDED_BYTES", 1), self.assertRaises(ValueError):
            BUILD.validate_install_tree(install)
        with mock.patch.object(BUILD, "MAX_MEMBERS", 1), self.assertRaises(ValueError):
            BUILD.validate_install_tree(install)

    def test_build_environment_does_not_inherit_credentials(self):
        with mock.patch.dict(os.environ, {"GITHUB_TOKEN": "do-not-inherit", "SSH_AUTH_SOCK": "/private/socket",
                                         "GFXSTREAM_COLOR_BUFFER_TRACE_FILE": "/private/trace",
                                         "GFXSTREAM_COLOR_BUFFER_TRACE_STATUS_FD": "99"}):
            env = BUILD.private_environment(self.runner / "home")
        self.assertNotIn("GITHUB_TOKEN", env)
        self.assertNotIn("SSH_AUTH_SOCK", env)
        self.assertNotIn("GFXSTREAM_COLOR_BUFFER_TRACE_FILE", env)
        self.assertNotIn("GFXSTREAM_COLOR_BUFFER_TRACE_STATUS_FD", env)
        self.assertEqual(env["GIT_TERMINAL_PROMPT"], "0")
        self.assertEqual(Path(env["HOME"]).stat().st_mode & 0o777, 0o700)

    def test_patch_bundle_hash_changes_with_native_logger(self):
        assets = self.runner / "assets"
        assets.mkdir()
        for name in BUILD.PATCH_ASSETS:
            (assets / name).write_bytes((HERE / name).read_bytes())
        with mock.patch.object(BUILD, "ASSETS", assets):
            before = BUILD.patch_digest()
            with (assets / "color-buffer-trace.h").open("ab") as handle:
                handle.write(b"\n// changed native logger\n")
            self.assertNotEqual(before, BUILD.patch_digest())


if __name__ == "__main__":
    unittest.main()
