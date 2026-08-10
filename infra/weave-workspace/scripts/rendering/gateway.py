from __future__ import annotations

from urllib.parse import urlsplit

from compose_env import ComposeContext, ContractError


def _origin(value: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise ContractError(f"expected HTTPS public URL: {value}")
    return f"{parsed.scheme}://{parsed.netloc}"


def _site(value: str) -> str:
    parsed = urlsplit(_origin(value))
    if parsed.hostname is None or ":" in parsed.hostname:
        raise ContractError(f"expected DNS-hosted HTTPS gateway URL: {value}")
    return f"https://{parsed.hostname}"


def render_caddy(context: ComposeContext) -> str:
    env = context.env
    public_site = _site(env["WEAVE_PUBLIC_URL"])
    api_site = _site(env["WEAVE_API_URL"])
    auth_site = _site(env["WEAVE_AUTH_URL"])
    matrix_site = _site(env["WEAVE_MATRIX_URL"])
    files_site = _site(env["WEAVE_FILES_URL"])
    backend = "host.docker.internal:8080" if context.profile == "dev" else "backend:8080"
    mcp = "host.docker.internal:8091" if context.profile == "dev" else "mcp:8091"
    mcp_handler = f"reverse_proxy {mcp}"
    matrix_handler = "reverse_proxy synapse:8008"
    mas_handler = "reverse_proxy mas:8080"
    files_handler = """reverse_proxy nextcloud:80 {
    header_up X-Forwarded-For {http.request.remote.host}
    header_up X-Forwarded-Host {host}
    header_up X-Forwarded-Proto {scheme}
  }"""
    if env["WEAVE_CHAT_PROVIDER"] != "matrix-synapse":
        matrix_handler = 'respond `{"error":"matrix provider disabled"}` 404'
        mas_handler = matrix_handler
    if env["WEAVE_FILES_PROVIDER"] != "nextcloud-webdav" and env["WEAVE_CALENDAR_PROVIDER"] != "nextcloud-caldav":
        files_handler = 'respond `{"error":"nextcloud provider disabled"}` 404'

    mailpit_block = ""
    if "dev-tools" in context.active_profiles or context.profile in {"dogfood", "e2e"}:
        mailpit_url = env.get("WEAVE_MAILPIT_URL")
        if not mailpit_url:
            raise ContractError(f"{context.profile} Mailpit gateway requires WEAVE_MAILPIT_URL")
        mailpit_block = f"""{_site(mailpit_url)} {{
  tls /certs/mailpit-cert.pem /certs/mailpit-key.pem
  @private_network remote_ip private_ranges
  handle @private_network {{
    reverse_proxy mailpit:8025
  }}
  respond "Forbidden" 403
}}
"""

    return f"""{{
  admin off
  auto_https off
}}

{public_site} {{
  tls /certs/cert.pem /certs/key.pem
  @internal path /api/internal/* /actuator/*
  handle @internal {{
    respond 404
  }}
  handle_path /api/* {{
    reverse_proxy {backend}
  }}
  handle_path /mcp* {{
    {mcp_handler}
  }}
  reverse_proxy {backend}
}}

{api_site} {{
  tls /certs/cert.pem /certs/key.pem
  @internal path /api/internal/* /actuator/*
  handle @internal {{
    respond 404
  }}
  handle /.well-known/oauth-protected-resource* {{
    {mcp_handler}
  }}
  handle /mcp* {{
    {mcp_handler}
  }}
  reverse_proxy {backend}
}}

{auth_site} {{
  tls /certs/cert.pem /certs/key.pem
  reverse_proxy keycloak:8080
}}
{mailpit_block}
{matrix_site} {{
  tls /certs/cert.pem /certs/key.pem
  @internal path /api/internal/* /actuator/*
  handle @internal {{
    respond 404
  }}
  @client_well_known path /.well-known/matrix/client
  handle @client_well_known {{
    header Content-Type application/json
    respond `{{"m.homeserver":{{"base_url":"{env['WEAVE_MATRIX_URL']}"}}}}` 200
  }}
  @synapse path /_matrix/* /_synapse/client/* /_synapse/mas/*
  handle @synapse {{
    {matrix_handler}
  }}
  handle {{
    {mas_handler}
  }}
}}

{files_site} {{
  tls /certs/cert.pem /certs/key.pem
  {files_handler}
}}
"""
