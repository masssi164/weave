from __future__ import annotations

import re

from compose_env import ComposeContext
from rendering.io import read_secret


def render_mas(context: ComposeContext) -> str:
    env = context.env
    insecure = "true" if context.profile == "dev" else "false"
    return f"""http:
  public_base: {env['WEAVE_MATRIX_URL']}/
  listeners:
    - name: web
      resources:
        - name: discovery
        - name: human
        - name: oauth
        - name: compat
        - name: graphql
        - name: assets
        - name: health
      binds:
        - address: \"[::]:8080\"
database:
  host: postgres
  port: 5432
  username: {env['WEAVE_MAS_DB_USERNAME']}
  password: {read_secret(context, 'mas-db-password')}
  database: {env['WEAVE_MAS_DB_NAME']}
  ssl_mode: disable
matrix:
  kind: synapse_modern
  homeserver: {env['WEAVE_MATRIX_HOST']}
  endpoint: http://synapse:8008
  secret: {read_secret(context, 'mas-matrix-secret')}
secrets:
  encryption: {read_secret(context, 'mas-encryption-secret')}
  keys:
    - kid: weave-mas-current
      key_file: /config/signing.key
passwords:
  enabled: false
account:
  password_registration_enabled: false
  login_with_email_allowed: true
policy:
  data:
    client_registration:
      allow_insecure_uris: {insecure}
upstream_oauth2:
  providers:
    - id: 01J0000000WEAVEKEYC10AKMAS
      issuer: {env['WEAVE_AUTH_URL']}/realms/weave
      human_name: Weave Identity
      client_id: matrix-mas
      client_secret: {read_secret(context, 'keycloak-matrix-mas')}
      token_endpoint_auth_method: client_secret_post
      scope: \"openid email profile\"
      discovery_mode: oidc
      pkce_method: auto
      fetch_userinfo: true
"""


def render_synapse(context: ComposeContext) -> str:
    env = context.env
    return f"""server_name: \"{env['WEAVE_MATRIX_HOST']}\"
pid_file: /data/homeserver.pid
public_baseurl: \"{env['WEAVE_MATRIX_URL']}/\"
listeners:
  - port: 8008
    tls: false
    type: http
    x_forwarded: true
    resources:
      - names: [client]
        compress: false
database:
  name: psycopg2
  args:
    user: {env['WEAVE_SYNAPSE_DB_USERNAME']}
    password: \"{read_secret(context, 'synapse-db-password')}\"
    database: {env['WEAVE_SYNAPSE_DB_NAME']}
    host: postgres
    port: 5432
    cp_min: 5
    cp_max: 10
media_store_path: /data/media_store
report_stats: false
enable_registration: false
registration_shared_secret: \"{read_secret(context, 'synapse-registration-shared-secret')}\"
macaroon_secret_key: \"{read_secret(context, 'synapse-macaroon-secret-key')}\"
form_secret: \"{read_secret(context, 'synapse-form-secret')}\"
signing_key_path: \"/data/{env['WEAVE_MATRIX_HOST']}.signing.key\"
app_service_config_files:
  - /run/weave-chat-appservice/registration.yaml
trusted_key_servers: []
suppress_key_server_warning: true
matrix_authentication_service:
  enabled: true
  endpoint: http://mas:8080
  secret: \"{read_secret(context, 'mas-matrix-secret')}\"
"""


def render_appservice(context: ComposeContext) -> str:
    env = context.env
    host_regex = re.escape(env["WEAVE_MATRIX_HOST"])
    callback = (
        f"http://host.docker.internal:{env['WEAVE_HOST_DEV_BACKEND_PORT']}/api/internal/chat/matrix/appservice"
        if context.profile == "dev"
        else "http://backend:8080/api/internal/chat/matrix/appservice"
    )
    return f"""id: weave-chat-synapse
url: {callback}
as_token: \"{read_secret(context, 'matrix-appservice-as-token')}\"
hs_token: \"{read_secret(context, 'matrix-appservice-hs-token')}\"
sender_localpart: _weave_appservice
rate_limited: true
receive_ephemeral: false
namespaces:
  users:
    - exclusive: true
      regex: '^@_weave_[a-z0-9]{{26,64}}:{host_regex}$'
  aliases:
    - exclusive: true
      regex: '^#_weave_[a-z0-9]{{26,64}}:{host_regex}$'
  rooms: []
"""
