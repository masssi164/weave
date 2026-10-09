#!/usr/bin/env python3
"""Run Flutter's live macOS journey with an XCUITest AppAuth browser driver.

The parent Fresh product flow supplies a disposable member through process
environment. Credentials cross to XCTest through a private one-use pipe, never
through Flutter build settings, process arguments, or an on-disk fixture.
"""

from __future__ import annotations

import json
import os
import queue
import shutil
import signal
import subprocess
import tempfile
import threading
import time
from pathlib import Path
from urllib.parse import urlparse


CLIENT = Path(__file__).resolve().parents[1]
SCHEME = "NativeAcceptance"
TEST = "RunnerUITests/RunnerUITests/testNativeAppAuthCallback"


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


def xcode_command(action: str, derived: Path, result: Path | None = None) -> list[str]:
    command = [
        "xcodebuild", action,
        "-workspace", "macos/Runner.xcworkspace",
        "-scheme", SCHEME,
        "-configuration", "Debug",
        "-destination", "platform=macOS,arch=arm64",
        "-derivedDataPath", str(derived),
        "-only-testing:" + TEST,
        "-quiet",
    ]
    if result is not None:
        command += ["-resultBundlePath", str(result)]
    return command


def collect_flutter_output(process: subprocess.Popen[str], sink: queue.Queue[str]) -> None:
    assert process.stdout is not None
    for line in process.stdout:
        marker = next(
            (candidate for candidate in
             ("NATIVE_PRODUCT_SIGN_IN_RESULT", "PHYSICAL_AUTH_SESSION_RESULT",
              "NATIVE_PRODUCT_STAGE")
             if candidate in line),
            None,
        )
        if marker:
            sink.put(line[line.index(marker):].strip())
        elif "All tests passed" in line:
            sink.put("FLUTTER_NATIVE_TEST_RUN status=passed")
        elif "Some tests failed" in line or "Test failed" in line:
            sink.put("FLUTTER_NATIVE_TEST_RUN status=failed")


def write_once(path: Path, payload: bytes) -> None:
    with path.open("wb", buffering=0) as output:
        output.write(payload)


def stop_checkout_app() -> None:
    """Remove only orphaned Flutter apps from this checkout's build output."""
    executable = str((CLIENT / "build/macos/Build/Products/Debug/weave.app"
                      / "Contents/MacOS/weave").resolve())
    process_list = subprocess.run(
        ["ps", "-axo", "pid=,command="], capture_output=True, text=True,
        check=True, timeout=10,
    )
    owned = []
    for line in process_list.stdout.splitlines():
        fields = line.strip().split(maxsplit=2)
        if len(fields) >= 2 and fields[1] == executable and fields[0].isdigit():
            owned.append(int(fields[0]))
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


def _process_exists(pid: int) -> bool:
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False


def report_xcode_result(bundle: Path) -> None:
    """Emit a bounded XCTest diagnosis without copying browser or fixture data."""
    try:
        report = subprocess.run(
            ["xcrun", "xcresulttool", "get", "test-results", "summary",
             "--path", str(bundle)],
            capture_output=True, text=True, timeout=15,
        )
        if report.returncode:
            print("NATIVE_XCTEST_DIAGNOSIS stage=result-unavailable", flush=True)
            return
        summary = json.loads(report.stdout)
        counts = (summary.get("passedTests"), summary.get("failedTests"),
                  summary.get("skippedTests"))
        if not all(isinstance(value, int) and 0 <= value <= 100 for value in counts):
            print("NATIVE_XCTEST_DIAGNOSIS stage=invalid-summary", flush=True)
            return
        stages = (
            ("app-window", "Flutter's native test app did not start"),
            ("issuer-window", "The expected disposable IdP did not appear"),
            ("account-field", "IdP account field unavailable"),
            ("password-field", "IdP password field unavailable"),
            ("sign-in-action", "IdP sign-in action unavailable"),
            ("callback", "Native application did not return after authentication"),
        )
        failures = summary.get("testFailures", [])
        stage = "none" if counts[1] == 0 else "unclassified"
        if isinstance(failures, list):
            for failure in failures:
                detail = failure.get("failureText", "") if isinstance(failure, dict) else ""
                stage = next((name for name, phrase in stages if phrase in detail), stage)
        print(
            f"NATIVE_XCTEST_DIAGNOSIS passed={counts[0]} failed={counts[1]} "
            f"skipped={counts[2]} stage={stage}", flush=True,
        )
    except (OSError, ValueError, subprocess.TimeoutExpired):
        print("NATIVE_XCTEST_DIAGNOSIS stage=result-unavailable", flush=True)


def report_live_xctest_stage(pipe: Path) -> None:
    stage_file = Path(str(pipe) + ".stage")
    allowed = {"fixture-read", "app-window", "browser-requested",
               "issuer-visible", "form-visible", "form-submitted",
               "callback-returned"}
    try:
        stage = stage_file.read_text(encoding="utf-8").strip()
    except OSError:
        stage = "not-started"
    print(
        "NATIVE_XCTEST_LAST_STAGE stage="
        + (stage if stage in allowed else "unrecognized"), flush=True,
    )


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
    with tempfile.TemporaryDirectory(prefix="weave-native-acceptance-") as temporary:
        root = Path(temporary)
        pipe = root / "member.pipe"
        os.mkfifo(pipe, 0o600)
        derived = root / "DerivedData"
        result = root / "NativeAcceptance.xcresult"
        fixture = json.dumps(
            {"email": email, "password": password,
             "issuerHost": urlparse(issuer).hostname},
            separators=(",", ":"),
        ).encode()
        try:
            run_quiet(
                xcode_command("build-for-testing", derived)
                + [f"WEAVE_NATIVE_FIXTURE_PATH={pipe}",
                   "FLUTTER_TARGET=lib/main.dart", "DART_DEFINES="], timeout=300,
            )
            env = os.environ.copy()
            env.update({
                "WEAVE_PHYSICAL_DEVICE_ID": "macos",
                "WEAVE_DEVICE_DISPOSABLE_STACK": "true",
                "WEAVE_DEVICE_API_BASE_URL": api,
                "WEAVE_DEVICE_OIDC_ISSUER_URL": issuer,
                "WEAVE_DEVICE_MATRIX_HOMESERVER_URL": matrix,
                "WEAVE_NATIVE_TEST_RUN_ID": run_id,
            })
            # The Flutter command receives only non-secret endpoint defines.
            env.pop("WEAVE_NATIVE_MEMBER_PASSWORD", None)
            env.pop("WEAVE_NATIVE_MEMBER_EMAIL", None)
            flutter = subprocess.Popen(
                ["make", "physical-device-product-e2e"],
                cwd=CLIENT, env=env, stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT, text=True,
            )
            markers: queue.Queue[str] = queue.Queue()
            reader = threading.Thread(
                target=collect_flutter_output, args=(flutter, markers), daemon=True
            )
            reader.start()
            writer = threading.Thread(target=write_once, args=(pipe, fixture), daemon=True)
            writer.start()
            try:
                try:
                    driver = subprocess.run(
                        xcode_command("test-without-building", derived, result),
                        cwd=CLIENT, capture_output=True, text=True, timeout=420,
                    )
                except subprocess.TimeoutExpired:
                    print("NATIVE_APP_AUTH_DRIVER_RESULT status=failed reason=timeout", flush=True)
                    report_xcode_result(result)
                    report_live_xctest_stage(pipe)
                    while not markers.empty():
                        print(markers.get_nowait(), flush=True)
                    print(f"NATIVE_FLUTTER_PROCESS_RESULT running={flutter.poll() is None}", flush=True)
                    raise
                print(
                    "NATIVE_APP_AUTH_DRIVER_RESULT status="
                    + ("passed" if driver.returncode == 0 else "failed"),
                    flush=True,
                )
                report_xcode_result(result)
                report_live_xctest_stage(pipe)
                if driver.returncode:
                    while not markers.empty():
                        print(markers.get_nowait(), flush=True)
                    print(f"NATIVE_FLUTTER_PROCESS_RESULT running={flutter.poll() is None}", flush=True)
                    raise RuntimeError("native AppAuth UI driver failed")
                flutter_status = flutter.wait(timeout=900)
                reader.join(timeout=2)
                observed = []
                while not markers.empty():
                    observed.append(markers.get_nowait())
                for marker in observed:
                    print(marker, flush=True)
                if flutter_status or not any(
                    line.startswith("NATIVE_PRODUCT_SIGN_IN_RESULT status=passed")
                    for line in observed
                ):
                    raise RuntimeError("Flutter native product assertions failed")
                print("NATIVE_FLUTTER_ACCEPTANCE_RESULT status=passed", flush=True)
            finally:
                if flutter.poll() is None:
                    flutter.terminate()
                    try:
                        flutter.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        flutter.kill()
                        flutter.wait(timeout=10)
                stop_checkout_app()
        finally:
            # XCTest can put typed form values in its result bundle; the
            # temporary directory and all raw runner output are discarded.
            shutil.rmtree(result, ignore_errors=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.TimeoutExpired, subprocess.CalledProcessError) as failure:
        print(f"NATIVE_FLUTTER_ACCEPTANCE_RESULT status=failed reason={type(failure).__name__}")
        raise SystemExit(1)
