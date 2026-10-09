#!/usr/bin/env python3
"""Run Flutter's real macOS product test and its bounded AppAuth browser driver.

FreshProductFlow supplies a disposable member. Credentials move through a
private one-use pipe to a native Accessibility driver, never through Flutter
defines, command arguments, Xcode results, or retained logs.
"""

from __future__ import annotations

import json
import os
import queue
import re
import signal
import subprocess
import tempfile
import threading
import time
from pathlib import Path
from urllib.parse import urlparse


CLIENT = Path(__file__).resolve().parents[1]
DRIVER = CLIENT / "tool/native_macos_appauth_driver.swift"


def require(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise RuntimeError(f"missing {name}")
    return value


def run_quiet(command: list[str], *, timeout: int) -> None:
    result = subprocess.run(
        command, cwd=CLIENT, capture_output=True, text=True, timeout=timeout
    )
    if result.returncode:
        raise RuntimeError(f"{command[0]} exited {result.returncode}")


def collect_flutter_output(process: subprocess.Popen[str], sink: queue.Queue[str]) -> None:
    assert process.stdout is not None
    for line in process.stdout:
        marker = next(
            (item for item in (
                "NATIVE_PRODUCT_SIGN_IN_RESULT", "PHYSICAL_AUTH_SESSION_RESULT",
                "NATIVE_PRODUCT_STAGE") if item in line),
            None,
        )
        if marker:
            sink.put(line[line.index(marker):].strip())
        elif "All tests passed" in line:
            sink.put("FLUTTER_NATIVE_TEST_RUN status=passed")
        elif "Some tests failed" in line or "Test failed" in line:
            sink.put("FLUTTER_NATIVE_TEST_RUN status=failed")


def write_once(path: Path, payload: bytes, transferred: threading.Event) -> None:
    with path.open("wb", buffering=0) as output:
        output.write(payload)
    transferred.set()
    print("NATIVE_FIXTURE_TRANSFER_RESULT status=passed", flush=True)


def _process_exists(pid: int) -> bool:
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False


def checkout_app_pids() -> list[int]:
    """Find only the native Flutter executable built by this checkout."""
    executable = str((CLIENT / "build/macos/Build/Products/Debug/weave.app"
                      / "Contents/MacOS/weave").resolve())
    listing = subprocess.run(
        ["ps", "-axo", "pid=,command="], capture_output=True, text=True,
        check=True, timeout=10,
    )
    owned: list[int] = []
    for line in listing.stdout.splitlines():
        fields = line.strip().split(maxsplit=2)
        if len(fields) >= 2 and fields[1] == executable and fields[0].isdigit():
            owned.append(int(fields[0]))
    return owned


def stop_checkout_app() -> None:
    """Stop only an orphaned Flutter app built by this checkout."""
    owned = checkout_app_pids()
    for pid in owned:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    for _ in range(30):
        if not any(_process_exists(pid) for pid in owned):
            break
        time.sleep(0.1)
    if any(_process_exists(pid) for pid in owned):
        raise RuntimeError("checkout native app did not stop")
    if owned:
        print("NATIVE_APP_PROCESS_CLEANUP status=passed scope=checkout", flush=True)


def report_driver_output(output: str) -> bool:
    consent = re.findall(
        r"NATIVE_MACOS_CONSENT_RESULT status=(accepted)", output
    )
    if consent:
        print("NATIVE_MACOS_CONSENT_RESULT status=accepted", flush=True)
    for stage in re.findall(
        r"NATIVE_AUTH_STAGE phase=(username-submitted|password-submitted)", output
    ):
        print("NATIVE_AUTH_STAGE phase=" + stage, flush=True)
    result = re.findall(
        r"NATIVE_APP_AUTH_DRIVER_RESULT status=(passed|failed) "
        r"stage=([a-z-]{1,60})", output
    )
    status, stage = result[-1] if result else ("failed", "result-unavailable")
    print(f"NATIVE_APP_AUTH_DRIVER_RESULT status={status} stage={stage}", flush=True)
    return status == "passed" and stage == "form-submitted"


def report_flutter_markers(markers: queue.Queue[str]) -> list[str]:
    observed = []
    while not markers.empty():
        observed.append(markers.get_nowait())
    for marker in observed:
        print(marker, flush=True)
    return observed


def main() -> int:
    email = require("WEAVE_NATIVE_MEMBER_EMAIL")
    password = require("WEAVE_NATIVE_MEMBER_PASSWORD")
    issuer = require("WEAVE_NATIVE_ISSUER")
    api = require("WEAVE_NATIVE_API_BASE_URL")
    matrix = require("WEAVE_NATIVE_MATRIX_URL")
    ca = Path(require("WEAVE_NATIVE_CA"))
    run_id = require("WEAVE_E2E_RUN_ID")
    if not ca.is_file() or urlparse(issuer).hostname != "auth.weave.localhost":
        raise RuntimeError("native acceptance requires the disposable localhost IdP")
    if urlparse(api).hostname != "api.weave.localhost":
        raise RuntimeError("native acceptance requires the disposable localhost API")
    trusted = subprocess.run(
        ["security", "verify-cert", "-c", str(ca), "-p", "ssl"],
        capture_output=True, timeout=15,
    )
    if trusted.returncode:
        raise RuntimeError("the dedicated native test CA is not trusted by macOS")
    print("NATIVE_TEST_CA_RESULT status=trusted scope=user-ssl-preconfigured", flush=True)
    stop_checkout_app()
    run_quiet(["flutter", "build", "macos", "--debug"], timeout=900)
    print("NATIVE_BUILD_PREPARATION_RESULT status=passed target=macos", flush=True)

    with tempfile.TemporaryDirectory(prefix="weave-native-acceptance-") as temporary:
        pipe = Path(temporary) / "member.pipe"
        os.mkfifo(pipe, 0o600)
        fixture = json.dumps({
            "email": email, "password": password,
            "issuerHost": urlparse(issuer).hostname,
            "issuerAuthority": urlparse(issuer).netloc,
            "appExecutable": str((CLIENT / "build/macos/Build/Products/Debug/weave.app"
                                  / "Contents/MacOS/weave").resolve()),
        }, separators=(",", ":")).encode()
        env = os.environ.copy()
        env.update({
            "WEAVE_PHYSICAL_DEVICE_ID": "macos",
            "WEAVE_DEVICE_DISPOSABLE_STACK": "true",
            "WEAVE_DEVICE_API_BASE_URL": api,
            "WEAVE_DEVICE_OIDC_ISSUER_URL": issuer,
            "WEAVE_DEVICE_MATRIX_HOMESERVER_URL": matrix,
            "WEAVE_NATIVE_TEST_RUN_ID": run_id,
        })
        env.pop("WEAVE_NATIVE_MEMBER_EMAIL", None)
        env.pop("WEAVE_NATIVE_MEMBER_PASSWORD", None)
        markers: queue.Queue[str] = queue.Queue()
        flutter: subprocess.Popen[str] | None = None
        driver: subprocess.Popen[str] | None = None
        try:
            flutter = subprocess.Popen(
                ["make", "physical-device-product-e2e"], cwd=CLIENT, env=env,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
            )
            reader = threading.Thread(
                target=collect_flutter_output, args=(flutter, markers), daemon=True
            )
            reader.start()
            app_deadline = time.monotonic() + 120
            while not checkout_app_pids() and time.monotonic() < app_deadline:
                if flutter.poll() is not None:
                    report_flutter_markers(markers)
                    raise RuntimeError("Flutter exited before native app launch")
                time.sleep(0.5)
            if not checkout_app_pids():
                raise RuntimeError("native Flutter app did not launch")
            print("NATIVE_APP_LAUNCH_RESULT status=passed scope=checkout", flush=True)
            driver = subprocess.Popen(
                ["swift", str(DRIVER), str(pipe)], cwd=CLIENT, env=env,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            )
            transferred = threading.Event()
            threading.Thread(
                target=write_once, args=(pipe, fixture, transferred), daemon=True
            ).start()
            if not transferred.wait(timeout=30):
                raise RuntimeError("native browser driver did not receive private fixture")
            deadline = time.monotonic() + 210
            while driver.poll() is None and time.monotonic() < deadline:
                if flutter.poll() is not None:
                    driver.terminate()
                    driver.wait(timeout=10)
                    report_flutter_markers(markers)
                    raise RuntimeError("Flutter exited before native IdP form completed")
                time.sleep(0.5)
            if driver.poll() is None:
                raise RuntimeError("native IdP form driver timed out")
            stdout, _ = driver.communicate(timeout=10)
            driver_ok = report_driver_output(stdout)
            if driver.returncode or not driver_ok:
                report_flutter_markers(markers)
                raise RuntimeError("native AppAuth browser driver failed")
            flutter_status = flutter.wait(timeout=900)
            reader.join(timeout=2)
            observed = report_flutter_markers(markers)
            if flutter_status or not any(
                line.startswith("NATIVE_PRODUCT_SIGN_IN_RESULT status=passed")
                for line in observed
            ) or "FLUTTER_NATIVE_TEST_RUN status=passed" not in observed:
                raise RuntimeError("Flutter native product assertions failed")
            print("NATIVE_FLUTTER_ACCEPTANCE_RESULT status=passed", flush=True)
        finally:
            if driver is not None and driver.poll() is None:
                driver.terminate()
                driver.wait(timeout=10)
            if flutter is not None and flutter.poll() is None:
                flutter.terminate()
                try:
                    flutter.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    flutter.kill()
                    flutter.wait(timeout=10)
            stop_checkout_app()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.TimeoutExpired, subprocess.CalledProcessError) as failure:
        print(f"NATIVE_FLUTTER_ACCEPTANCE_RESULT status=failed reason={type(failure).__name__}")
        raise SystemExit(1)
