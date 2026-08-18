#!/usr/bin/env python3
"""Regression tests for run-scoped Docker registry authority creation."""

from __future__ import annotations

import base64
import contextlib
import io
import json
import os
from pathlib import Path
import stat
import sys
import tempfile

from write_docker_auth_config import main, write_config


def expect_failure(operation, message: str) -> None:
    try:
        operation()
    except (FileExistsError, ValueError):
        return
    raise AssertionError(message)


def main_test() -> None:
    actor = "github-actions[bot]"
    token = "fixture-package-token-that-must-not-be-logged"
    with tempfile.TemporaryDirectory() as temporary_root:
        root = Path(temporary_root) / "weave-live-docker-auth-test"
        root.mkdir(mode=0o700)
        output = root / "config.json"
        plugin_dir = Path(temporary_root) / "cli-plugins"
        plugin_dir.mkdir(mode=0o700)

        write_config(output, "ghcr.io", actor, token, plugin_dir)
        config = json.loads(output.read_text(encoding="utf-8"))
        assert set(config) == {"auths", "cliPluginsExtraDirs"}
        assert set(config["auths"]) == {"ghcr.io"}
        assert config["cliPluginsExtraDirs"] == [str(plugin_dir)]
        assert "credsStore" not in config
        assert "credHelpers" not in config
        decoded = base64.b64decode(config["auths"]["ghcr.io"]["auth"]).decode(
            "utf-8"
        )
        assert decoded == f"{actor}:{token}"
        assert stat.S_ISREG(output.lstat().st_mode)
        assert stat.S_IMODE(output.lstat().st_mode) == 0o600

        expect_failure(
            lambda: write_config(output, "ghcr.io", actor, token, plugin_dir),
            "an existing Docker authority must not be overwritten",
        )

    with tempfile.TemporaryDirectory() as temporary_root:
        unsafe_root = Path(temporary_root) / "unsafe"
        unsafe_root.mkdir(mode=0o755)
        unsafe_root.chmod(0o755)
        plugin_dir = Path(temporary_root) / "cli-plugins"
        plugin_dir.mkdir(mode=0o700)
        assert stat.S_IMODE(unsafe_root.stat().st_mode) == 0o755
        expect_failure(
            lambda: write_config(
                unsafe_root / "config.json",
                "ghcr.io",
                actor,
                token,
                plugin_dir,
            ),
            "a broadly accessible parent must be rejected",
        )

    with tempfile.TemporaryDirectory() as temporary_root:
        root = Path(temporary_root) / "symlink"
        root.mkdir(mode=0o700)
        plugin_dir = Path(temporary_root) / "cli-plugins"
        plugin_dir.mkdir(mode=0o700)
        target = root / "target.json"
        target.write_text("must-remain-unchanged\n", encoding="utf-8")
        output = root / "config.json"
        output.symlink_to(target)
        expect_failure(
            lambda: write_config(output, "ghcr.io", actor, token, plugin_dir),
            "a symbolic-link output must be rejected",
        )
        assert target.read_text(encoding="utf-8") == "must-remain-unchanged\n"

    with tempfile.TemporaryDirectory() as temporary_root:
        root = Path(temporary_root) / "cli"
        root.mkdir(mode=0o700)
        output = root / "config.json"
        plugin_dir = Path(temporary_root) / "cli-plugins"
        plugin_dir.mkdir(mode=0o700)
        previous_actor = os.environ.get("GITHUB_ACTOR")
        previous_token = os.environ.get("GHCR_TOKEN")
        os.environ["GITHUB_ACTOR"] = actor
        os.environ["GHCR_TOKEN"] = token
        try:
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                previous_arguments = sys.argv
                sys.argv = [
                    "write_docker_auth_config.py",
                    "--output",
                    str(output),
                    "--cli-plugin-dir",
                    str(plugin_dir),
                ]
                try:
                    assert main() == 0
                finally:
                    sys.argv = previous_arguments
            assert token not in stdout.getvalue()
            assert actor not in stdout.getvalue()
        finally:
            if previous_actor is None:
                os.environ.pop("GITHUB_ACTOR", None)
            else:
                os.environ["GITHUB_ACTOR"] = previous_actor
            if previous_token is None:
                os.environ.pop("GHCR_TOKEN", None)
            else:
                os.environ["GHCR_TOKEN"] = previous_token

    with tempfile.TemporaryDirectory() as temporary_root:
        root = Path(temporary_root) / "invalid-plugin"
        root.mkdir(mode=0o700)
        expect_failure(
            lambda: write_config(
                root / "config.json",
                "ghcr.io",
                actor,
                token,
                Path("relative-cli-plugins"),
            ),
            "a relative Docker CLI plugin directory must be rejected",
        )

        plugin_file = Path(temporary_root) / "docker-compose"
        plugin_file.write_text("fixture\n", encoding="utf-8")
        expect_failure(
            lambda: write_config(
                root / "config.json",
                "ghcr.io",
                actor,
                token,
                plugin_file,
            ),
            "a non-directory Docker CLI plugin path must be rejected",
        )

        real_plugin_dir = Path(temporary_root) / "real-cli-plugins"
        real_plugin_dir.mkdir(mode=0o700)
        symlink_plugin_dir = Path(temporary_root) / "symlink-cli-plugins"
        symlink_plugin_dir.symlink_to(real_plugin_dir, target_is_directory=True)
        expect_failure(
            lambda: write_config(
                root / "config.json",
                "ghcr.io",
                actor,
                token,
                symlink_plugin_dir,
            ),
            "a symbolic-link Docker CLI plugin directory must be rejected",
        )


if __name__ == "__main__":
    main_test()
    print("write-docker-auth-config: ok isolated=true mode=0600 keychain=false")
