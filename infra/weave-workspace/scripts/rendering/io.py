from __future__ import annotations

import json
import os
import stat
from pathlib import Path

from compose_env import ComposeContext, ContractError


def private_mode(path: Path) -> bool:
    return stat.S_IMODE(path.stat().st_mode) == 0o600


def read_secret(context: ComposeContext, name: str) -> str:
    path = context.secret_root / name
    if path.is_symlink() or not path.is_file() or not private_mode(path):
        raise ContractError(f"secret must be a regular mode-0600 file: {path}")
    value = path.read_text(encoding="utf-8").strip()
    if not value:
        raise ContractError(f"secret is empty: {path}")
    return value


def write(path: Path, payload: str | bytes, *, private: bool, runtime_owner: tuple[int, int] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700 if private else 0o755)
    if path.is_symlink():
        raise ContractError(f"refusing generated symlink target: {path}")
    data = payload.encode("utf-8") if isinstance(payload, str) else payload
    expected_mode = 0o600 if private else 0o644
    if path.is_file() and path.read_bytes() == data:
        os.chmod(path, expected_mode)
        if private and runtime_owner is not None:
            uid, gid = runtime_owner
            if path.stat().st_uid != uid or path.stat().st_gid != gid:
                try:
                    os.chown(path, uid, gid)
                except PermissionError as error:
                    raise ContractError(f"cannot bind private config {path} to runtime uid/gid {uid}:{gid}") from error
        return
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, expected_mode)
    try:
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, expected_mode)
        if private and runtime_owner is not None:
            uid, gid = runtime_owner
            if path.stat().st_uid != uid or path.stat().st_gid != gid:
                try:
                    os.chown(path, uid, gid)
                except PermissionError as error:
                    raise ContractError(f"cannot bind private config {path} to runtime uid/gid {uid}:{gid}") from error
    finally:
        if temporary.exists():
            temporary.unlink()


def runtime_directory(path: Path, runtime_owner: tuple[int, int]) -> None:
    if path.is_symlink():
        raise ContractError(f"refusing generated symlink directory: {path}")
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path, 0o700)
    uid, gid = runtime_owner
    if path.stat().st_uid != uid or path.stat().st_gid != gid:
        try:
            os.chown(path, uid, gid)
        except PermissionError as error:
            raise ContractError(f"cannot bind generated directory {path} to runtime uid/gid {uid}:{gid}") from error


def json_object(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ContractError(f"expected JSON object: {path}")
    return value
