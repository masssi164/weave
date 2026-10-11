#!/usr/bin/env python3
"""Use the installed OpenClaw agent to invoke one real Weave MCP tool.

A deterministic local model fixture chooses Tool Search and Tool Call. OpenClaw
itself owns MCP discovery, transport, argument dispatch and result delivery.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit


class ProofError(RuntimeError):
    pass


def tool_message(messages: list[dict[str, Any]]) -> str:
    for item in reversed(messages):
        if item.get("role") == "tool" and isinstance(item.get("content"), str):
            return item["content"]
    raise ProofError("OpenClaw did not return a Tool Search result")


def candidates(value: Any):
    if isinstance(value, dict):
        if isinstance(value.get("id"), str) and isinstance(value.get("source"), str):
            yield value
        for child in value.values():
            yield from candidates(child)
    elif isinstance(value, list):
        for child in value:
            yield from candidates(child)


def control_json(text: str) -> Any:
    decoder = json.JSONDecoder()
    for index, character in enumerate(text[:20_000]):
        if character not in "[{":
            continue
        try:
            value, _ = decoder.raw_decode(text[index:])
        except json.JSONDecodeError:
            continue
        if isinstance(value, (list, dict)) and any(candidates(value)):
            return value
    raise ProofError("OpenClaw Tool Search result has no bounded catalog")


def model_chunk(delta: dict[str, Any], finish: str) -> bytes:
    def frame(change: dict[str, Any], reason: str | None) -> bytes:
        value = {
            "id": "weave-mcp-proof", "object": "chat.completion.chunk",
            "created": 1, "model": "proof",
            "choices": [{"index": 0, "delta": change, "finish_reason": reason}],
        }
        return ("data: " + json.dumps(value, separators=(",", ":")) + "\n\n").encode()

    return frame(delta, None) + frame({}, finish) + b"data: [DONE]\n\n"


def model_server(expected: dict[str, Any]):
    state: dict[str, Any] = {"calls": 0, "resultVerified": False, "failure": None}
    tool = expected["tool"]

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):  # noqa: N802
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0 or length > 1_000_000 or self.path != "/v1/chat/completions":
                    raise ProofError("local model request is outside its bound")
                request = json.loads(self.rfile.read(length))
                state["calls"] += 1
                available = {
                    item.get("function", {}).get("name")
                    for item in request.get("tools", []) if isinstance(item, dict)
                }
                messages = request.get("messages", [])
                if state["calls"] == 1:
                    if "tool_search" not in available:
                        raise ProofError("OpenClaw did not expose Tool Search")
                    name = "tool_search"
                    args = {"query": "Weave " + tool, "limit": 20}
                    finish = "tool_calls"
                elif state["calls"] == 2:
                    search = control_json(tool_message(messages))
                    matches = [item for item in candidates(search)
                               if item.get("source") == "mcp"
                               and (item.get("sourceName") == "weave"
                                    or item.get("mcp", {}).get("serverName") == "weave")
                               and (item.get("mcp", {}).get("toolName") == tool
                                    or item.get("name") in (tool, tool.replace(".", "_"),
                                                            "weave__" + tool.replace(".", "_")))]
                    if len(matches) != 1 or "tool_call" not in available:
                        raise ProofError("OpenClaw did not discover one exact Weave tool")
                    name = "tool_call"
                    args = {"id": matches[0]["id"], "args": expected["arguments"]}
                    finish = "tool_calls"
                elif state["calls"] == 3:
                    result = tool_message(messages)
                    if any(value not in result for value in expected["contains"]):
                        raise ProofError("OpenClaw tool result differs from User API readback")
                    if any(value in result for value in ("providerId", "/remote.php/dav")):
                        raise ProofError("OpenClaw tool result leaked a provider reference")
                    state["resultVerified"] = True
                    payload = model_chunk({"role": "assistant", "content": "DONE"}, "stop")
                    self.send_response(200)
                    self.send_header("Content-Type", "text/event-stream")
                    self.end_headers()
                    self.wfile.write(payload)
                    return
                else:
                    raise ProofError("OpenClaw exceeded the bounded model sequence")
                delta = {"role": "assistant", "tool_calls": [{
                    "index": 0, "id": "weave-proof-" + str(state["calls"]),
                    "type": "function", "function": {
                        "name": name,
                        "arguments": json.dumps(args, separators=(",", ":")),
                    },
                }]}
                payload = model_chunk(delta, finish)
            except (ProofError, ValueError, TypeError, KeyError) as failure:
                state["failure"] = str(failure)
                payload = model_chunk({"role": "assistant", "content": "FAILED"}, "stop")
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_args):
            pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, state


def run(args: argparse.Namespace) -> None:
    target = urlsplit(args.mcp_url)
    if (target.scheme != "http" or target.hostname != "127.0.0.1"
            or target.path != "/mcp" or not target.port or target.query or target.fragment):
        raise ProofError("release MCP test endpoint is not exact loopback")
    token = os.environ.get("WEAVE_MCP_WORKLOAD_TOKEN", "")
    if not token or len(token) > 8192 or not re.fullmatch(r"[A-Za-z0-9._~-]+", token):
        raise ProofError("protected workload bearer is unavailable")
    if args.expected.is_symlink() or not args.expected.is_file():
        raise ProofError("independent expected User API result is unavailable")
    if (args.private_root.is_symlink() or not args.private_root.is_dir()
            or args.private_root.stat().st_mode & 0o077):
        raise ProofError("OpenClaw proof workspace is not private")
    expected = json.loads(args.expected.read_text(encoding="utf-8"))
    if (expected.get("tool") not in (
            "files.search", "calendar.agenda", "calendar.create",
            "calendar.update", "calendar.delete")
            or not isinstance(expected.get("arguments"), dict)
            or not isinstance(expected.get("contains"), list)
            or not expected["contains"]
            or any(not isinstance(value, str) or not value for value in expected["contains"])):
        raise ProofError("independent expected User API result is malformed")
    version = subprocess.run(["openclaw", "--version"], capture_output=True, text=True,
                             timeout=10, check=True).stdout.strip()
    if not re.fullmatch(r"OpenClaw 20[0-9]{2}\.[0-9]+\.[0-9]+.*", version):
        raise ProofError("installed OpenClaw version is unsupported")

    with tempfile.TemporaryDirectory(prefix="weave-openclaw-proof-", dir=args.private_root) as scratch:
        root = Path(scratch)
        root.chmod(0o700)
        state_dir = root / "state"
        state_dir.mkdir(mode=0o700)
        config = {
            "mcp": {"servers": {"weave": {
                "transport": "streamable-http", "url": args.mcp_url,
                "headers": {"Authorization": "Bearer ${WEAVE_MCP_WORKLOAD_TOKEN}"},
                "toolFilter": {"include": [expected["tool"]]},
            }}},
            "models": {"providers": {"weave-proof": {
                "baseUrl": "http://127.0.0.1:1/v1", "api": "openai-completions",
                "apiKey": "local-fixture", "models": [{
                    "id": "proof", "name": "Weave deterministic proof", "reasoning": False,
                    "input": ["text"], "cost": {"input": 0, "output": 0,
                                              "cacheRead": 0, "cacheWrite": 0},
                    "contextWindow": 32768, "maxTokens": 1024,
                }],
            }}},
        }
        config_path = root / "openclaw.json"
        environment = dict(os.environ)
        environment["OPENCLAW_CONFIG_PATH"] = str(config_path)
        environment["OPENCLAW_STATE_DIR"] = str(state_dir)
        config_path.write_text(json.dumps(config, separators=(",", ":")), encoding="utf-8")
        config_path.chmod(0o600)
        probe = subprocess.run(["openclaw", "mcp", "probe", "weave", "--json"],
                               env=environment, capture_output=True, text=True, timeout=35)
        if probe.returncode != 0 or len(probe.stdout) > 1_000_000:
            raise ProofError("real OpenClaw MCP catalog probe failed")
        catalog = json.loads(probe.stdout)
        if not catalog.get("servers") or not catalog.get("tools"):
            raise ProofError("real OpenClaw MCP catalog is empty")

        server, result = model_server(expected)
        try:
            config["models"]["providers"]["weave-proof"]["baseUrl"] = (
                f"http://127.0.0.1:{server.server_port}/v1"
            )
            config_path.write_text(json.dumps(config, separators=(",", ":")), encoding="utf-8")
            turn = subprocess.run([
                "openclaw", "agent", "exec", "Call the authorized Weave " + expected["tool"],
                "--config", str(config_path), "--state-dir", str(state_dir),
                "--model", "weave-proof/proof", "--cwd", str(root),
                "--timeout", "45", "--json",
            ], env=environment, capture_output=True, text=True, timeout=65)
        finally:
            server.shutdown()
            server.server_close()
        if turn.returncode != 0 or len(turn.stdout) > 1_000_000:
            raise ProofError("real OpenClaw agent turn failed")
        outcome = json.loads(turn.stdout)
        if (result["failure"] or not result["resultVerified"] or result["calls"] != 3
                or outcome.get("ok") is not True or outcome.get("final") != "DONE"
                or outcome.get("bridgeCalls", {}).get("call", 0) < 1
                or not {"tool_search", "tool_call"}.issubset(
                    set(outcome.get("toolSummary", {}).get("tools", [])))):
            raise ProofError("real OpenClaw did not invoke and read back the expected Weave tool")
    print("WEAVE_OPENCLAW_RELEASE_MCP_RESULT status=passed tool=" + expected["tool"]
          + " clientVersion=" + version.split(" ")[1]
          + " catalog=discovered invocation=real-client supportSafe=true")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mcp-url", required=True)
    parser.add_argument("--expected", type=Path, required=True)
    parser.add_argument("--private-root", type=Path, required=True)
    try:
        run(parser.parse_args())
    except Exception as failure:
        print("WEAVE_OPENCLAW_RELEASE_MCP_ERROR " + str(failure).replace("\n", " ")[:256],
              file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
