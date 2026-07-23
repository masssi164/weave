#!/usr/bin/env python3
"""PostgreSQL-backed lease and fencing primitives for Keycloak reconciliation.

The host supervisor uses :class:`PsqlLeaseController`; the sanitizer sidecar
uses :class:`DatabaseLeaseVerifier`.  Both enforce the same exact tuple and no
credential value is returned in evidence.
"""

from __future__ import annotations

import hashlib
import json
import re
import secrets
import subprocess
from dataclasses import dataclass
from pathlib import Path


IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
SAFE_VALUE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.:/@+-]{0,255}$")


class LeaseError(RuntimeError):
    pass


def _sha256(value: str) -> str:
    return "sha256:" + hashlib.sha256(value.encode("utf-8")).hexdigest()


@dataclass(frozen=True)
class Lease:
    lease_id: str
    reconciliation_id: str
    lock_key: str
    database_fingerprint: str
    fencing_token: int
    acquired_at: str
    expires_at: str
    status: str
    validation_count: int = 0
    stale_fence_rejections: int = 0
    released_at: str | None = None

    def evidence(self) -> dict[str, object]:
        value: dict[str, object] = {
            "leaseId": self.lease_id,
            "lockKey": self.lock_key,
            "databaseFingerprint": self.database_fingerprint,
            "fencingToken": self.fencing_token,
            "acquiredAt": self.acquired_at,
            "expiresAt": self.expires_at,
            "status": self.status,
            "validationCount": self.validation_count,
            "staleFenceRejections": self.stale_fence_rejections,
        }
        if self.released_at:
            value["releasedAt"] = self.released_at
        return value


class PsqlLeaseController:
    """Fenced lease operations executed inside the selected PostgreSQL node.

    Acquisition opens one long-lived ``psql`` process and takes a PostgreSQL
    *session* advisory lock.  Every lease assertion and the final
    release/quarantine transition uses that same backend session.  A one-shot
    ``docker exec psql`` cannot implement this contract because a session lock
    would disappear as soon as that process exits.
    """

    def __init__(self, container: str, administrator: str) -> None:
        for label, value in (("container", container), ("administrator", administrator)):
            if not SAFE_VALUE.fullmatch(value):
                raise LeaseError(f"invalid {label}")
        self.container = container
        self.administrator = administrator
        self._session: subprocess.Popen[str] | None = None
        self._held_lock_key: str | None = None

    @staticmethod
    def _validated_variables(variables: dict[str, str] | None) -> dict[str, str]:
        checked: dict[str, str] = {}
        for key, value in sorted((variables or {}).items()):
            if (
                not IDENTIFIER.fullmatch(key)
                or "\x00" in value
                or "\n" in value
                or "\r" in value
                or "'" in value
                or "\\" in value
            ):
                raise LeaseError("unsafe psql lease variable")
            checked[key] = value
        return checked

    def _psql_once(self, sql: str, variables: dict[str, str] | None = None) -> str:
        command = [
            "docker", "exec", "--interactive", self.container,
            "psql", "--no-psqlrc", "--set=ON_ERROR_STOP=1", "--quiet", "--tuples-only", "--no-align",
            "-U", self.administrator, "-d", "postgres",
        ]
        for key, value in self._validated_variables(variables).items():
            command.extend(("--variable", f"{key}={value}"))
        result = subprocess.run(
            command,
            input=sql,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if result.returncode != 0:
            raise LeaseError("PostgreSQL rejected the fenced lease operation")
        return result.stdout.strip()

    def _open_session(self) -> None:
        if self._session is not None:
            raise LeaseError("lease controller already owns a PostgreSQL session")
        self._session = subprocess.Popen(
            [
                "docker", "exec", "--interactive", self.container,
                "psql", "--no-psqlrc", "--set=ON_ERROR_STOP=1", "--quiet",
                "--tuples-only", "--no-align", "-U", self.administrator,
                "-d", "postgres",
            ],
            text=True,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            bufsize=1,
        )
        if self._session.stdin is None or self._session.stdout is None:
            self._session.kill()
            self._session = None
            raise LeaseError("could not open the PostgreSQL lease session")

    def _psql_session(self, sql: str, variables: dict[str, str] | None = None) -> str:
        session = self._session
        if (
            session is None
            or session.poll() is not None
            or session.stdin is None
            or session.stdout is None
        ):
            raise LeaseError("PostgreSQL lease session is unavailable")
        marker = secrets.token_hex(16)
        start = f"__WEAVE_PSQL_START_{marker}__"
        end = f"__WEAVE_PSQL_END_{marker}__"
        assignments = "".join(
            f"\\set {key} '{value}'\n"
            for key, value in self._validated_variables(variables).items()
        )
        session.stdin.write(f"\\echo {start}\n{assignments}{sql.rstrip()}\n\\echo {end}\n")
        session.stdin.flush()
        output: list[str] = []
        started = False
        while True:
            line = session.stdout.readline()
            if line == "":
                raise LeaseError("PostgreSQL lease session ended during a fenced operation")
            value = line.rstrip("\r\n")
            if not started:
                if value == start:
                    started = True
                continue
            if value == end:
                return "\n".join(output).strip()
            output.append(value)

    def _psql(self, sql: str, variables: dict[str, str] | None = None) -> str:
        if self._session is not None:
            return self._psql_session(sql, variables)
        return self._psql_once(sql, variables)

    @property
    def session_lock_held(self) -> bool:
        return (
            self._session is not None
            and self._session.poll() is None
            and self._held_lock_key is not None
        )

    def close(self) -> None:
        """Close a finished controller.

        An active lock cannot be silently dropped.  The caller must first
        release or quarantine the matching row through :meth:`finish`.
        """

        if self._held_lock_key is not None:
            raise LeaseError("refusing to drop an active advisory lock without release or quarantine")
        session = self._session
        self._session = None
        if session is None:
            return
        if session.stdin is not None and session.poll() is None:
            try:
                session.stdin.write("\\quit\n")
                session.stdin.flush()
            except BrokenPipeError:
                pass
        try:
            session.wait(timeout=5)
        except subprocess.TimeoutExpired:
            session.terminate()
            try:
                session.wait(timeout=5)
            except subprocess.TimeoutExpired:
                session.kill()
                session.wait(timeout=5)

    def database_fingerprint(self) -> str:
        identifier = self._psql("SELECT system_identifier FROM pg_control_system();")
        if not identifier.isdigit():
            raise LeaseError("PostgreSQL system identifier is unavailable")
        return _sha256(identifier)

    def consume_receipt(
        self,
        *,
        reconciliation_id: str,
        request_nonce: str,
        specification_commit: str,
        candidate_commit: str,
        receipt_payload_digest: str,
    ) -> None:
        """Atomically consume one successful apply receipt.

        Re-running ``up`` with a stale receipt is intentionally rejected. A
        repeated deployment starts with a fresh protected apply invocation and
        therefore receives a new reconciliation ID and unpredictable nonce.
        """

        values = {
            "reconciliation_id": reconciliation_id,
            "request_nonce": request_nonce,
            "specification_commit": specification_commit,
            "candidate_commit": candidate_commit,
            "receipt_payload_digest": receipt_payload_digest,
        }
        patterns = {
            "reconciliation_id": r"keycloak-reconcile:[0-9a-f-]{36}",
            "request_nonce": r"[A-Za-z0-9_-]{22,128}",
            "specification_commit": r"[0-9a-f]{40}",
            "candidate_commit": r"[0-9a-f]{40}",
            "receipt_payload_digest": r"sha256:[0-9a-f]{64}",
        }
        if any(re.fullmatch(patterns[key], value) is None for key, value in values.items()):
            raise LeaseError("invalid reconciliation receipt consumption binding")
        output = self._psql(
            """
WITH consumed AS (
  INSERT INTO weave_control.keycloak_reconciliation_consumptions (
    reconciliation_id, request_nonce, specification_commit,
    candidate_commit, receipt_payload_digest
  ) VALUES (
    :'reconciliation_id', :'request_nonce', :'specification_commit',
    :'candidate_commit', :'receipt_payload_digest'
  )
  ON CONFLICT DO NOTHING
  RETURNING reconciliation_id
)
SELECT reconciliation_id FROM consumed;
""",
            values,
        )
        if output != reconciliation_id:
            raise LeaseError("successful Keycloak apply receipt was already consumed or conflicts with prior evidence")

    def acquire(
        self,
        *,
        deployment_scope: str,
        deployment_instance: str,
        compose_project: str,
        realm: str,
        lease_id: str,
        reconciliation_id: str,
        duration_seconds: int = 900,
    ) -> Lease:
        if self._session is not None:
            raise LeaseError("lease controller cannot acquire twice")
        if not 60 <= duration_seconds <= 1800:
            raise LeaseError("lease duration is outside the closed bound")
        self._open_session()
        try:
            return self._acquire_in_session(
                deployment_scope=deployment_scope,
                deployment_instance=deployment_instance,
                compose_project=compose_project,
                realm=realm,
                lease_id=lease_id,
                reconciliation_id=reconciliation_id,
                duration_seconds=duration_seconds,
            )
        except Exception:
            self._held_lock_key = None
            self.close()
            raise

    def _acquire_in_session(
        self,
        *,
        deployment_scope: str,
        deployment_instance: str,
        compose_project: str,
        realm: str,
        lease_id: str,
        reconciliation_id: str,
        duration_seconds: int,
    ) -> Lease:
        values = {
            "deployment_scope": deployment_scope,
            "deployment_instance": deployment_instance,
            "compose_project": compose_project,
            "realm": realm,
            "lease_id": lease_id,
            "reconciliation_id": reconciliation_id,
            "duration": str(duration_seconds),
            "database_fingerprint": self.database_fingerprint(),
        }
        for key, value in values.items():
            if key != "duration" and not SAFE_VALUE.fullmatch(value):
                raise LeaseError(f"invalid lease binding {key}")
        lock_key = (
            f"keycloak-reconcile:{compose_project}:{deployment_instance}:"
            f"{realm}:{values['database_fingerprint'][7:19]}"
        )
        values["lock_key"] = lock_key
        output = self._psql_session(
            """
WITH advisory AS (
  SELECT pg_try_advisory_lock(hashtextextended(:'lock_key', 0)) AS acquired
), existing AS (
  SELECT status, expires_at
  FROM weave_control.keycloak_reconciliation_leases
  WHERE lock_key = :'lock_key'
    AND (SELECT acquired FROM advisory)
  FOR UPDATE
), eligible AS (
  SELECT 1 FROM advisory
  WHERE acquired
    AND (
      NOT EXISTS (SELECT 1 FROM existing)
      OR EXISTS (
        SELECT 1 FROM existing
        WHERE status = 'released'
           OR (status = 'active' AND expires_at <= clock_timestamp())
      )
    )
), changed AS (
  INSERT INTO weave_control.keycloak_reconciliation_leases (
    lock_key, deployment_scope, deployment_instance, compose_project,
    database_fingerprint, realm, lease_id, reconciliation_id, fencing_token,
    acquired_at, expires_at, released_at, quarantined_at, status,
    validation_count, stale_fence_rejections
  )
  SELECT :'lock_key', :'deployment_scope', :'deployment_instance', :'compose_project',
         :'database_fingerprint', :'realm', :'lease_id', :'reconciliation_id', 1,
         clock_timestamp(), clock_timestamp() + (:'duration' || ' seconds')::interval,
         NULL, NULL, 'active', 0, 0
  FROM eligible
  ON CONFLICT (lock_key) DO UPDATE SET
    deployment_scope = EXCLUDED.deployment_scope,
    deployment_instance = EXCLUDED.deployment_instance,
    compose_project = EXCLUDED.compose_project,
    database_fingerprint = EXCLUDED.database_fingerprint,
    realm = EXCLUDED.realm,
    lease_id = EXCLUDED.lease_id,
    reconciliation_id = EXCLUDED.reconciliation_id,
    fencing_token = weave_control.keycloak_reconciliation_leases.fencing_token + 1,
    acquired_at = EXCLUDED.acquired_at,
    expires_at = EXCLUDED.expires_at,
    released_at = NULL,
    quarantined_at = NULL,
    status = 'active',
    validation_count = 0,
    stale_fence_rejections = 0
  WHERE weave_control.keycloak_reconciliation_leases.status = 'released'
     OR (
       weave_control.keycloak_reconciliation_leases.status = 'active'
       AND weave_control.keycloak_reconciliation_leases.expires_at <= clock_timestamp()
     )
  RETURNING lease_id, reconciliation_id, lock_key, database_fingerprint,
            fencing_token, acquired_at, expires_at, status,
            validation_count, stale_fence_rejections
)
SELECT row_to_json(changed) FROM changed;
""",
            values,
        )
        rows = [line for line in output.splitlines() if line.startswith("{")]
        if len(rows) != 1:
            raise LeaseError("another reconciliation holds or quarantined the exact deployment lease")
        lease = self._lease(json.loads(rows[0]))
        self._held_lock_key = lock_key
        return lease

    def assert_active(self, lease: Lease, *, increment: bool = True) -> Lease:
        values = {
            "lock_key": lease.lock_key,
            "lease_id": lease.lease_id,
            "reconciliation_id": lease.reconciliation_id,
            "fencing_token": str(lease.fencing_token),
        }
        output = self._psql(
            """
WITH checked AS (
  UPDATE weave_control.keycloak_reconciliation_leases
  SET validation_count = validation_count + CASE WHEN :'increment' = 'true' THEN 1 ELSE 0 END
  WHERE lock_key = :'lock_key'
    AND lease_id = :'lease_id'
    AND reconciliation_id = :'reconciliation_id'
    AND fencing_token = :'fencing_token'::bigint
    AND status = 'active'
    AND expires_at > clock_timestamp()
  RETURNING lease_id, reconciliation_id, lock_key, database_fingerprint,
            fencing_token, acquired_at, expires_at, status,
            validation_count, stale_fence_rejections
)
SELECT row_to_json(checked) FROM checked;
""",
            {**values, "increment": "true" if increment else "false"},
        )
        rows = [line for line in output.splitlines() if line.startswith("{")]
        if len(rows) != 1:
            self._record_stale_attempt(lease.lock_key)
            raise LeaseError("lease is stale, expired, released, quarantined, or fenced")
        return self._lease(json.loads(rows[0]))

    def _record_stale_attempt(self, lock_key: str) -> None:
        try:
            self._psql(
                "UPDATE weave_control.keycloak_reconciliation_leases "
                "SET stale_fence_rejections=stale_fence_rejections+1 WHERE lock_key=:'lock_key';",
                {"lock_key": lock_key},
            )
        except LeaseError:
            pass

    def finish(self, lease: Lease, *, quarantine: bool) -> Lease:
        if not self.session_lock_held or self._held_lock_key != lease.lock_key:
            raise LeaseError("cannot finish without the matching session advisory lock")
        values = {
            "lock_key": lease.lock_key,
            "lease_id": lease.lease_id,
            "reconciliation_id": lease.reconciliation_id,
            "fencing_token": str(lease.fencing_token),
            "status": "quarantined" if quarantine else "released",
        }
        output = self._psql_session(
            """
WITH changed AS (
  UPDATE weave_control.keycloak_reconciliation_leases
  SET status = :'status',
      released_at = CASE WHEN :'status' = 'released' THEN clock_timestamp() ELSE NULL END,
      quarantined_at = CASE WHEN :'status' = 'quarantined' THEN clock_timestamp() ELSE NULL END
  WHERE lock_key = :'lock_key'
    AND lease_id = :'lease_id'
    AND reconciliation_id = :'reconciliation_id'
    AND fencing_token = :'fencing_token'::bigint
    AND status = 'active'
  RETURNING lease_id, reconciliation_id, lock_key, database_fingerprint,
            fencing_token, acquired_at, expires_at, status, released_at,
            validation_count, stale_fence_rejections
), unlocked AS (
  SELECT pg_advisory_unlock(hashtextextended(:'lock_key', 0)) AS released
  FROM changed
)
SELECT json_build_object('lease', row_to_json(changed), 'advisory_lock_released', unlocked.released)
FROM changed CROSS JOIN unlocked;
""",
            values,
        )
        rows = [line for line in output.splitlines() if line.startswith("{")]
        if len(rows) != 1:
            raise LeaseError("cannot finish a lease no longer owned by this reconciliation")
        result = json.loads(rows[0])
        if result.get("advisory_lock_released") is not True or not isinstance(result.get("lease"), dict):
            raise LeaseError("PostgreSQL did not release the matching session advisory lock")
        finished = self._lease(result["lease"])
        self._held_lock_key = None
        self.close()
        return finished

    @staticmethod
    def _lease(row: dict[str, object]) -> Lease:
        def timestamp(name: str) -> str:
            value = str(row[name])
            return value.replace(" ", "T").replace("+00:00", "Z")

        return Lease(
            lease_id=str(row["lease_id"]),
            reconciliation_id=str(row["reconciliation_id"]),
            lock_key=str(row["lock_key"]),
            database_fingerprint=str(row["database_fingerprint"]),
            fencing_token=int(row["fencing_token"]),
            acquired_at=timestamp("acquired_at"),
            expires_at=timestamp("expires_at"),
            status=str(row["status"]),
            validation_count=int(row["validation_count"]),
            stale_fence_rejections=int(row["stale_fence_rejections"]),
            released_at=timestamp("released_at") if row.get("released_at") else None,
        )


class DatabaseLeaseVerifier:
    """Live lease verifier used inside the sanitizer network boundary."""

    def __init__(
        self,
        *,
        host: str,
        port: int,
        database: str,
        username: str,
        password_file: Path,
        lock_key: str,
        lease_id: str,
        reconciliation_id: str,
        fencing_token: int,
    ) -> None:
        if password_file.is_symlink() or not password_file.is_file():
            raise LeaseError("control-store credential file is unavailable")
        self.connection = {
            "host": host,
            "port": port,
            "dbname": database,
            "user": username,
            "password": password_file.read_text(encoding="utf-8").strip(),
            "connect_timeout": 5,
            "sslmode": "prefer",
        }
        self.bindings = (lock_key, lease_id, reconciliation_id, fencing_token)

    def __call__(self) -> None:
        try:
            import psycopg2  # type: ignore[import-not-found]

            with psycopg2.connect(**self.connection) as connection:
                with connection.cursor() as cursor:
                    cursor.execute(
                        """
UPDATE weave_control.keycloak_reconciliation_leases
SET validation_count = validation_count + 1
WHERE lock_key=%s AND lease_id=%s AND reconciliation_id=%s
  AND fencing_token=%s AND status='active' AND expires_at > clock_timestamp()
RETURNING fencing_token
""",
                        self.bindings,
                    )
                    row = cursor.fetchone()
                    if row != (self.bindings[3],):
                        connection.rollback()
                        raise LeaseError("sanitizer rejected a stale fencing holder")
        except ImportError as error:
            raise LeaseError("sanitizer image lacks the pinned PostgreSQL driver") from error
