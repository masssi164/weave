#!/usr/bin/env python3

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path
from unittest import mock
from urllib.parse import urlsplit

import sys


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from prepare_dev_dependencies import select_source_env  # noqa: E402
from compose_env import ContractError, load_context  # noqa: E402


PORT_KEYS = {
    "WEAVE_PROXY_HTTP_HOST_PORT",
    "WEAVE_PROXY_HTTPS_HOST_PORT",
    "WEAVE_KEYCLOAK_HOST_PORT",
    "WEAVE_KEYCLOAK_MANAGEMENT_HOST_PORT",
    "WEAVE_MAILPIT_WEB_HOST_PORT",
    "WEAVE_MAS_HOST_PORT",
    "WEAVE_SYNAPSE_HOST_PORT",
    "WEAVE_NEXTCLOUD_HOST_PORT",
    "WEAVE_BACKEND_HOST_PORT",
    "WEAVE_MCP_HOST_PORT",
}
VOLUME_KEYS = {
    "WEAVE_CADDY_DATA_VOLUME",
    "WEAVE_CADDY_CONFIG_VOLUME",
    "WEAVE_DB_DATA_VOLUME",
    "WEAVE_KEYCLOAK_DATA_VOLUME",
    "WEAVE_MAILPIT_DATA_VOLUME",
    "WEAVE_NEXTCLOUD_DATA_VOLUME",
    "WEAVE_SYNAPSE_DATA_VOLUME",
    "WEAVE_MATRIX_APPSERVICE_VOLUME",
}


def literal_env(path: Path) -> dict[str, str]:
    return {
        line.split("=", 1)[0]: line.split("=", 1)[1]
        for line in path.read_text(encoding="utf-8").splitlines()
        if line and not line.startswith("#") and "=" in line
    }


def run(arguments: list[str], env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        arguments,
        cwd=REPOSITORY,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"command failed ({result.returncode}): {' '.join(arguments)}\n"
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )
    return result


def env_file(source: Path, target: Path, generated: Path) -> None:
    replacements = {
        "WEAVE_GENERATED_ROOT": str(generated),
        "WEAVE_SECRET_ROOT": str(generated / "secrets"),
        "WEAVE_TLS_ROOT": str(generated / "tls"),
    }
    lines = []
    for line in source.read_text(encoding="utf-8").splitlines():
        key = line.split("=", 1)[0] if "=" in line else ""
        lines.append(f"{key}={replacements[key]}" if key in replacements else line)
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")


def compose_model(profile: str, profile_env: Path, shell_env: dict[str, str]) -> dict[str, object]:
    result = run(
        [
            "docker",
            "compose",
            "--env-file",
            str(ROOT / "environments/common.env"),
            "--env-file",
            str(profile_env),
            "--file",
            str(ROOT / "compose.yaml"),
            "--file",
            str(ROOT / f"compose.{profile}.yaml"),
            "--profile",
            profile,
            "config",
            "--format",
            "json",
        ],
        shell_env,
    )
    return json.loads(result.stdout)


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="weave-compose-contract-") as temporary:
        temp = Path(temporary)
        generated = temp / "generated"
        dev_env = temp / "dev.env"
        env_file(ROOT / "environments/dev.env", dev_env, generated)
        assert select_source_env(ROOT, str(dev_env)) == dev_env.resolve()
        dev_defaults = literal_env(ROOT / "environments/dev.env")
        dogfood_defaults = literal_env(ROOT / "environments/dogfood.env.example")
        assert dev_defaults["WEAVE_COMPOSE_PROJECT"] != dogfood_defaults["WEAVE_COMPOSE_PROJECT"]
        assert dev_defaults["WEAVE_DOCKER_NETWORK"] != dogfood_defaults["WEAVE_DOCKER_NETWORK"]
        assert {dev_defaults[key] for key in PORT_KEYS}.isdisjoint(
            {dogfood_defaults[key] for key in PORT_KEYS}
        )
        assert {dev_defaults[key] for key in VOLUME_KEYS}.isdisjoint(
            {dogfood_defaults[key] for key in VOLUME_KEYS}
        )
        for key in ("WEAVE_PUBLIC_URL", "WEAVE_API_URL", "WEAVE_AUTH_URL", "WEAVE_MATRIX_URL", "WEAVE_FILES_URL"):
            assert urlsplit(dev_defaults[key]).netloc != urlsplit(dogfood_defaults[key]).netloc
        shell_env = dict(os.environ)
        shell_env["WEAVE_ENV_FILE"] = str(dev_env)
        lock = json.loads((REPOSITORY / "specs/weave-specs.lock.json").read_text(encoding="utf-8"))
        configured_corpus = shell_env.get("WEAVE_SPEC_CORPUS_ROOT")
        corpus_root = (
            Path(configured_corpus).resolve()
            if configured_corpus
            else (REPOSITORY / lock["specCorpus"]["localPath"]).resolve()
        )
        shell_env["WEAVE_SPEC_CORPUS_ROOT"] = str(corpus_root)
        run([str(ROOT / "compose.sh"), "dev", "secrets-init"], shell_env)
        run([str(ROOT / "compose.sh"), "dev", "render"], shell_env)
        first_hashes = {
            str(path.relative_to(generated)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in generated.rglob("*")
            if path.is_file() and "secrets" not in path.parts and path.name != "secret-generation.json"
        }
        run([str(ROOT / "compose.sh"), "dev", "render"], shell_env)
        second_hashes = {
            str(path.relative_to(generated)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in generated.rglob("*")
            if path.is_file() and "secrets" not in path.parts and path.name != "secret-generation.json"
        }
        assert first_hashes == second_hashes, "render must be byte-for-byte deterministic"
        desired = json.loads((generated / "keycloak/desired-state.json").read_text(encoding="utf-8"))
        manifest = json.loads((generated / "render-manifest.json").read_text(encoding="utf-8"))
        assert manifest["desiredStateRevision"] == desired["revision"]
        assert manifest["specificationCommit"] == lock["specCorpus"]["gitCommit"]
        assert manifest["overlayRevision"] == desired["provenance"]["overlayRevision"]
        assert desired["apiVersion"] == "weave.keycloak-desired-state/v1"
        assert "users" not in desired
        assert next(scope for scope in desired["clientScopes"] if scope["key"] == "scope:mcp-tools")["name"] == "mcp.tools"
        assert "mcp:tools" not in json.dumps(desired)
        private_jwk = generated / "secrets/keycloak-weave-mcp-server-jwk.json"
        assert private_jwk.is_file() and not private_jwk.is_symlink()
        assert private_jwk.stat().st_mode & 0o777 == 0o600
        generated_jwk = json.loads(private_jwk.read_text(encoding="utf-8"))
        assert generated_jwk["kty"] == "RSA"
        assert generated_jwk["use"] == "sig"
        assert generated_jwk["alg"] == "PS256"
        assert generated_jwk["key_ops"] == ["sign"]
        assert generated_jwk["kid"] == "weave-mcp-server-current"
        assert all(generated_jwk.get(name) for name in ("n", "e", "d", "p", "q", "dp", "dq", "qi"))
        mcp_public = literal_env(generated / "mcp/public.env")
        assert mcp_public["WEAVE_MCP_REQUIRED_SCOPES"].split(",") == ["mcp.tools", "calendar.read"]
        assert set(mcp_public["WEAVE_MCP_EXCHANGE_SCOPES"].split(",")).issubset(
            set(mcp_public["WEAVE_MCP_REQUIRED_SCOPES"].split(","))
        )
        assert not (generated / "provider-selections.json").exists()
        dev_model = compose_model("dev", dev_env, shell_env)
        assert set(dev_model["services"]).isdisjoint(
            {"backend", "mcp", "mcp-secret-check", "mcp-keycloak-connectivity-check"}
        )
        compose_text = json.dumps(dev_model)
        for secret in (generated / "secrets").iterdir():
            if secret.is_file():
                value = secret.read_text(encoding="utf-8", errors="ignore").strip()
                assert not value or value not in compose_text

        for profile in ("dogfood", "main"):
            source = ROOT / f"environments/{profile}.env.example"
            target = temp / f"{profile}.env"
            profile_generated = temp / profile
            env_file(source, target, profile_generated)
            (profile_generated / "backend").mkdir(parents=True)
            (profile_generated / "mcp").mkdir(parents=True)
            backend_environment = {
                "SPRING_PROFILES_ACTIVE": profile,
                "WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL": "http://synapse:8008",
                "WEAVE_CALDAV_BASE_URL": "http://nextcloud",
                "WEAVE_CALDAV_BACKEND_USERNAME": "weave-backend",
                "WEAVE_IDENTITY_KEYCLOAK_BASE_URL": "http://keycloak:8080",
                "WEAVE_IDENTITY_KEYCLOAK_ORGANIZATION_ALIAS": f"weave-{profile}",
                "WEAVE_OIDC_ISSUER_URI": "https://auth.weave.test/realms/weave",
                "WEAVE_OIDC_JWK_SET_URI": "http://keycloak:8080/realms/weave/protocol/openid-connect/certs",
                "WEAVE_WORKSPACE_CALENDAR_ENABLED": "true",
                "WEAVE_WORKSPACE_CALENDAR_READINESS": "ready",
            }
            (profile_generated / "backend/public.env").write_text(
                "".join(f"{key}={value}\n" for key, value in sorted(backend_environment.items())),
                encoding="utf-8",
            )
            (profile_generated / "mcp/public.env").write_text(
                "WEAVE_MCP_REQUIRED_SCOPES=mcp.tools,calendar.read\n"
                "WEAVE_MCP_EXCHANGE_SCOPES=calendar.read\n"
                "WEAVE_OIDC_ISSUER_URI=https://auth.weave.test/realms/weave\n"
                "WEAVE_OIDC_JWK_SET_URI=http://keycloak:8080/realms/weave/protocol/openid-connect/certs\n"
                "WEAVE_MCP_TOKEN_URI=http://keycloak:8080/realms/weave/protocol/openid-connect/token\n",
                encoding="utf-8",
            )
            text = target.read_text(encoding="utf-8").replace("replace-with-approved-digest", "a" * 64).replace("replace-with-candidate-digest", "b" * 64)
            target.write_text(text, encoding="utf-8")
            model = compose_model(profile, target, shell_env)
            assert {
                "backend", "mcp", "mcp-secret-check", "mcp-keycloak-connectivity-check"
            }.issubset(model["services"])
            assert "mailpit" in model["services"] if profile == "dogfood" else "mailpit" not in model["services"]
            mcp = model["services"]["mcp"]
            mounts = mcp.get("volumes", [])
            assert any(item.get("target") == "/run/secrets/weave/mcp-private-jwk.json" and item.get("type") == "bind" for item in mounts)
            assert not any(item.get("target") == "/run/secrets/weave/mcp-private-jwk.json" for item in mcp.get("secrets", []))
            check_command = str(model["services"]["mcp-secret-check"].get("command", ""))
            assert "stat -c" in check_command and "600" in check_command
            model_text = json.dumps(model)
            assert "WEAVE_MCP_EXCHANGE_CLIENT_SECRET" not in model_text
            assert "mcp:tools" not in model_text
            backend = model["services"]["backend"]
            assert not any(
                item.get("target") == "/app/provider-selections.json"
                for item in backend.get("volumes", [])
            )
            assert backend["environment"]["WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL"] == "http://synapse:8008"
            assert "WEAVE_CHAT_MATRIX_BASE_URL" not in backend["environment"]
            assert backend["environment"]["WEAVE_IDENTITY_KEYCLOAK_BASE_URL"] == "http://keycloak:8080"
            assert backend["environment"]["WEAVE_CALDAV_BASE_URL"] == "http://nextcloud"
            assert backend["environment"]["WEAVE_CALDAV_BACKEND_USERNAME"] == "weave-backend"
            assert backend["environment"]["WEAVE_WORKSPACE_CALENDAR_ENABLED"] == "true"
            assert backend["environment"]["WEAVE_WORKSPACE_CALENDAR_READINESS"] == "ready"
            assert backend["environment"]["WEAVE_OIDC_ISSUER_URI"].startswith("https://")
            assert backend["environment"]["WEAVE_OIDC_JWK_SET_URI"] == "http://keycloak:8080/realms/weave/protocol/openid-connect/certs"
            assert mcp["environment"]["WEAVE_OIDC_ISSUER_URI"].startswith("https://")
            assert mcp["environment"]["WEAVE_OIDC_JWK_SET_URI"] == "http://keycloak:8080/realms/weave/protocol/openid-connect/certs"
            assert mcp["environment"]["WEAVE_MCP_TOKEN_URI"] == "http://keycloak:8080/realms/weave/protocol/openid-connect/token"
            connectivity_command = str(
                model["services"]["mcp-keycloak-connectivity-check"].get("command", "")
            )
            assert "http://keycloak:8080/realms/weave/protocol/openid-connect/certs" in connectivity_command
            assert "http://keycloak:8080/realms/weave/protocol/openid-connect/token" in connectivity_command
            secret_targets = {
                target if target.startswith("/") else f"/run/secrets/{target}"
                for item in backend.get("secrets", [])
                if isinstance((target := str(item.get("target", ""))), str) and target
            }
            assert "/run/secrets/weave/weave.nextcloud.files.actor-token" in secret_targets
            assert "/run/secrets/weave/weave.calendar.caldav.backend-token" in secret_targets

            application_yaml = (REPOSITORY / "server/src/main/resources/application.yml").read_text(encoding="utf-8")
            for binding in (
                "WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL",
                "WEAVE_IDENTITY_KEYCLOAK_BASE_URL",
                "WEAVE_IDENTITY_KEYCLOAK_ORGANIZATION_ALIAS",
                "WEAVE_CALDAV_BASE_URL",
                "WEAVE_CALDAV_BACKEND_USERNAME",
                "WEAVE_CALDAV_BACKEND_TOKEN",
                "WEAVE_WORKSPACE_CALENDAR_ENABLED",
                "WEAVE_WORKSPACE_CALENDAR_READINESS",
            ):
                assert binding in application_yaml, f"server boot configuration does not bind {binding}"

        isolated_run_id = "fixture-run-42"
        isolated_namespace = "weave-e2e-" + hashlib.sha256(isolated_run_id.encode("ascii")).hexdigest()[:16]
        isolated_process = {
            "WEAVE_E2E_STACK_SCOPE": "isolated",
            "WEAVE_E2E_RUN_ID": isolated_run_id,
            "WEAVE_E2E_RUN_NAMESPACE": isolated_namespace,
        }
        for index, name in enumerate(sorted(PORT_KEYS)):
            isolated_process[name] = str(42000 + index)
        with mock.patch.dict(os.environ, isolated_process, clear=True):
            isolated = load_context("dogfood", ROOT, str(temp / "dogfood.env"))
        assert isolated.isolated_namespace == isolated_namespace
        assert isolated.env["WEAVE_COMPOSE_PROJECT"] == isolated_namespace
        assert isolated.env["WEAVE_RESOURCE_PREFIX"] == isolated_namespace
        assert isolated.env["WEAVE_DOCKER_NETWORK"] == f"{isolated_namespace}_network"
        assert isolated.generated_root == ROOT / ".generated/isolated" / isolated_namespace
        assert {isolated.env[name] for name in PORT_KEYS} == set(isolated_process[name] for name in PORT_KEYS)
        volume_prefix = isolated_namespace.replace("-", "_")
        assert all(isolated.env[name].startswith(volume_prefix + "_") for name in VOLUME_KEYS)

        mismatched_namespace = dict(isolated_process)
        mismatched_namespace["WEAVE_E2E_RUN_NAMESPACE"] = "weave-e2e-wrong-namespace"
        with mock.patch.dict(os.environ, mismatched_namespace, clear=True):
            try:
                load_context("dogfood", ROOT, str(temp / "dogfood.env"))
            except ContractError as error:
                assert "does not match the deterministic isolated run namespace" in str(error)
            else:
                raise AssertionError("isolated Compose accepted a mismatched namespace")

        override_env = dict(shell_env)
        override_env["WEAVE_BACKEND_IMAGE"] = "ghcr.io/masssi164/weave-backend@sha256:" + "c" * 64
        override_env["WEAVE_MCP_IMAGE"] = "ghcr.io/masssi164/weave-mcp-server@sha256:" + "d" * 64
        override_env["WEAVE_BACKEND_PASSWORD"] = "must-not-enter-compose"
        dogfood_override = compose_model("dogfood", temp / "dogfood.env", override_env)
        assert dogfood_override["services"]["backend"]["image"].endswith("c" * 64)
        assert dogfood_override["services"]["mcp"]["image"].endswith("d" * 64)
        assert "must-not-enter-compose" not in json.dumps(dogfood_override)

        relative_override = dict(shell_env)
        relative_override["WEAVE_SPEC_CORPUS_ROOT"] = "../weave-specs"
        rejected = subprocess.run(
            [str(ROOT / "compose.sh"), "dev", "render"],
            cwd=REPOSITORY,
            env=relative_override,
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        assert rejected.returncode != 0
        assert "absolute Git worktree path" in rejected.stderr

        mismatched_corpus = temp / "mismatched-corpus"
        run(["git", "init", "--quiet", str(mismatched_corpus)], shell_env)
        run(["git", "-C", str(mismatched_corpus), "config", "user.name", "Compose Contract"], shell_env)
        run(["git", "-C", str(mismatched_corpus), "config", "user.email", "compose-contract@invalid"], shell_env)
        (mismatched_corpus / "README.md").write_text("not the pinned corpus\n", encoding="utf-8")
        run(["git", "-C", str(mismatched_corpus), "add", "README.md"], shell_env)
        run(["git", "-C", str(mismatched_corpus), "commit", "--quiet", "-m", "fixture"], shell_env)
        mismatched_override = dict(shell_env)
        mismatched_override["WEAVE_SPEC_CORPUS_ROOT"] = str(mismatched_corpus)
        rejected = subprocess.run(
            [str(ROOT / "compose.sh"), "dev", "render"],
            cwd=REPOSITORY,
            env=mismatched_override,
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        assert rejected.returncode != 0
        assert "lock requires" in rejected.stderr

    print("compose profile contract tests passed")


if __name__ == "__main__":
    main()
