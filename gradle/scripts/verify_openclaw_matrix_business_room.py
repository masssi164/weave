#!/usr/bin/env python3
"""Send one business-room event with the installed OpenClaw Matrix client.

The caller checks readback independently through the Weave Matrix facade. The
normal member bearer lives only in a private, disposable OpenClaw state.
"""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from urllib.parse import urlsplit


class ProofError(Exception):
    pass


def command(argv, environment, timeout):
    result = subprocess.run(
        argv, env=environment, capture_output=True, text=True, timeout=timeout,
        check=False,
    )
    if result.returncode != 0 or len(result.stdout) > 1_000_000:
        try:
            payload = json.loads(result.stdout)
            detail = payload.get("error", {}).get("message", "")
        except (ValueError, AttributeError):
            detail = ""
        if not isinstance(detail, str) or not detail:
            detail = result.stderr or result.stdout
        detail = detail.split("FetchHttpApi:", 1)[0]
        detail = detail.replace(
            environment.get("WEAVE_MATRIX_MEMBER_TOKEN", ""), "[redacted]"
        )
        detail = re.sub(r"[^A-Za-z0-9 .:_/-]", " ", detail).strip()[:200]
        raise ProofError("real OpenClaw Matrix " + argv[1]
                         + " failed: " + detail)
    return result.stdout


def run(args):
    origin = urlsplit(args.homeserver)
    if (origin.scheme != "https" or origin.hostname != "api.weave.test"
            or not origin.port or origin.path not in ("", "/")
            or origin.query or origin.fragment):
        raise ProofError("Matrix facade origin is not the isolated test authority")
    if not re.fullmatch(r"![^:]{1,200}:[A-Za-z0-9.:-]+", args.room):
        raise ProofError("business room ID is invalid")
    if not re.fullmatch(r"@[A-Za-z0-9._=/-]+:[A-Za-z0-9.:-]+", args.user):
        raise ProofError("member Matrix user ID is invalid")
    if not args.message.startswith("Weave OpenClaw Matrix E2E ") or len(args.message) > 256:
        raise ProofError("business room message is invalid")
    token = os.environ.get("WEAVE_MATRIX_MEMBER_TOKEN", "")
    if not token or len(token) > 8192 or not re.fullmatch(r"[A-Za-z0-9._~-]+", token):
        raise ProofError("normal member bearer is unavailable")
    if (args.ca.is_symlink() or not args.ca.is_file()
            or args.private_root.is_symlink() or not args.private_root.is_dir()
            or args.private_root.stat().st_mode & 0o077):
        raise ProofError("private Matrix proof inputs are unavailable")

    version = command(["openclaw", "--version"], os.environ.copy(), 10).strip()
    if not re.fullmatch(r"OpenClaw 20[0-9]{2}\.[0-9]+\.[0-9]+.*", version):
        raise ProofError("installed OpenClaw version is unsupported")
    installed = json.loads(command(
        ["openclaw", "plugins", "list", "--json"], os.environ.copy(), 20,
    ))
    plugins = [plugin for plugin in installed.get("plugins", [])
               if plugin.get("id") == "matrix" and plugin.get("status") == "loaded"]
    if len(plugins) != 1 or plugins[0].get("version") != version.split(" ")[1]:
        raise ProofError("version-matched OpenClaw Matrix plugin is unavailable")
    with tempfile.TemporaryDirectory(prefix="weave-openclaw-matrix-",
                                     dir=args.private_root) as scratch:
        root = Path(scratch)
        root.chmod(0o700)
        state = root / "state"
        state.mkdir(mode=0o700)
        config_path = root / "openclaw.json"
        config_path.write_text("{}", encoding="utf-8")
        config_path.chmod(0o600)
        environment = dict(os.environ)
        environment["OPENCLAW_CONFIG_PATH"] = str(config_path)
        environment["OPENCLAW_STATE_DIR"] = str(state)
        environment["NODE_EXTRA_CA_CERTS"] = str(args.ca)
        command([
            "openclaw", "plugins", "install",
            "@openclaw/matrix@" + plugins[0]["version"],
            "--accept-capabilities",
        ], environment, 120)
        config = json.loads(config_path.read_text(encoding="utf-8"))
        config["channels"] = {"matrix": {
            "enabled": True,
            "homeserver": args.homeserver,
            "userId": args.user,
            "accessToken": token,
            "network": {"dangerouslyAllowPrivateNetwork": True},
        }}
        config_path.write_text(json.dumps(config, separators=(",", ":")),
                               encoding="utf-8")
        config_path.chmod(0o600)
        try:
            sent = json.loads(command([
                "openclaw", "message", "send", "--channel", "matrix",
                "--target", args.room, "--message", args.message, "--json",
            ], environment, 90))
        except ProofError as failure:
            private_plugins = json.loads(command(
                ["openclaw", "plugins", "list", "--json"], environment, 20,
            ))
            matrix = [plugin for plugin in private_plugins.get("plugins", [])
                      if plugin.get("id") == "matrix"
                      and plugin.get("status") == "loaded"]
            diagnostic = "matrixSdkProbe=plugin-unavailable"
            if len(matrix) == 1:
                package_path = Path(matrix[0]["source"]).parent.parent / "package.json"
                if (package_path.is_file()
                        and package_path.resolve().is_relative_to(state.resolve())):
                    probe_env = dict(environment)
                    probe_env["WEAVE_MATRIX_PLUGIN_PACKAGE"] = str(package_path)
                    probe_env["WEAVE_MATRIX_HOMESERVER"] = args.homeserver
                    probe_env["WEAVE_MATRIX_USER_ID"] = args.user
                    try:
                        probe = subprocess.run(
                            ["node", str(Path(__file__).with_name(
                                "probe_matrix_js_sdk_sync.cjs"))],
                            env=probe_env, capture_output=True, text=True,
                            timeout=30, check=False,
                        )
                        if probe.returncode == 0:
                            diagnostic = next(
                                (line[:200] for line in probe.stdout.splitlines()
                                 if line.startswith("matrixSdkProbe=")),
                                "matrixSdkProbe=no-result",
                            )
                    except subprocess.TimeoutExpired:
                        diagnostic = "matrixSdkProbe=timeout"
            raise ProofError(diagnostic + "; " + str(failure)) from failure
        if sent.get("ok") is False or sent.get("dryRun") is True:
            raise ProofError("OpenClaw Matrix did not send a live event")
    print("WEAVE_OPENCLAW_MATRIX_RESULT status=passed clientVersion="
          + version.split(" ")[1]
          + " profile=business-room normalMemberBearer=true supportSafe=true")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--homeserver", required=True)
    parser.add_argument("--user", required=True)
    parser.add_argument("--room", required=True)
    parser.add_argument("--message", required=True)
    parser.add_argument("--ca", required=True, type=Path)
    parser.add_argument("--private-root", required=True, type=Path)
    try:
        run(parser.parse_args())
    except ProofError as failure:
        print("WEAVE_OPENCLAW_MATRIX_ERROR " + str(failure))
        return 1
    except Exception as failure:
        print("WEAVE_OPENCLAW_MATRIX_ERROR unexpected "
              + type(failure).__name__)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
