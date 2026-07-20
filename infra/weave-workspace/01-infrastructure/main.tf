terraform {
  required_version = ">= 1.5.0"

  backend "local" {}

  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

provider "docker" {
  host = var.docker_host
}

locals {
  resource_prefix = var.isolated_e2e_enabled ? var.isolated_e2e_namespace : "weave"
  volume_prefix   = replace(local.resource_prefix, "-", "_")

  service_names = {
    db        = "${local.resource_prefix}-db"
    proxy     = "${local.resource_prefix}-proxy"
    keycloak  = "${local.resource_prefix}-keycloak"
    mailpit   = "${local.resource_prefix}-mailpit"
    backend   = "${local.resource_prefix}-backend"
    mcp       = "${local.resource_prefix}-mcp-server"
    mas       = "${local.resource_prefix}-mas"
    synapse   = "${local.resource_prefix}-synapse"
    nextcloud = "${local.resource_prefix}-nextcloud"
  }

  volume_names = {
    caddy_data      = "${local.volume_prefix}_caddy_data"
    caddy_config    = "${local.volume_prefix}_caddy_config"
    db              = "${local.volume_prefix}_db_data"
    keycloak        = "${local.volume_prefix}_keycloak_data"
    mailpit         = var.isolated_e2e_enabled ? "${local.volume_prefix}_mailpit_data" : var.mailpit_volume_name
    nextcloud       = "${local.volume_prefix}_nextcloud_data"
    synapse         = "${local.volume_prefix}_synapse_data"
    chat_appservice = "${local.volume_prefix}_matrix_chat_appservice_runtime"
  }

  resource_labels = var.isolated_e2e_enabled ? {
    "com.massimotter.weave.managed"   = "true"
    "com.massimotter.weave.scope"     = "isolated"
    "com.massimotter.weave.namespace" = local.resource_prefix
  } : {}

  runtime_asset_root = var.isolated_e2e_enabled ? "${path.module}/.generated/isolated/${var.isolated_e2e_namespace}" : "${path.module}/.generated"

  public_port_suffix = (
    (var.public_scheme == "http" && var.proxy_host_port == 80) ||
    (var.public_scheme == "https" && var.proxy_host_port == 443)
  ) ? "" : ":${var.proxy_host_port}"

  public_hosts = {
    weave  = var.tenant_domain
    api    = "${var.api_subdomain}.${var.tenant_domain}"
    admin  = "${var.admin_subdomain}.${var.tenant_domain}"
    auth   = "${var.auth_subdomain}.${var.tenant_domain}"
    mail   = "mail.${var.tenant_domain}"
    matrix = "${var.matrix_subdomain}.${var.tenant_domain}"
    files  = "${var.nextcloud_subdomain}.${var.tenant_domain}"
  }

  public_urls = {
    for service, host in local.public_hosts :
    service => "${var.public_scheme}://${host}${local.public_port_suffix}"
  }

  # Client-facing local URLs are DNS-first. local_lan_host is a deprecated
  # compatibility input and must not create a second public truth for app,
  # issuer, Matrix, product, or CA URLs.
  client_public_url           = local.public_urls.weave
  client_api_origin           = local.public_urls.api
  client_api_base_url         = "${local.client_api_origin}/api"
  client_auth_url             = local.public_urls.auth
  client_matrix_facade_url    = local.client_api_origin
  matrix_provider_public_url  = local.public_urls.matrix
  client_files_product_url    = "${local.client_public_url}/files"
  client_calendar_product_url = "${local.client_public_url}/calendar"

  site_hosts = {
    weave  = [local.public_hosts.weave]
    api    = [local.public_hosts.api]
    admin  = [local.public_hosts.admin]
    auth   = [local.public_hosts.auth]
    mail   = [local.public_hosts.mail]
    matrix = [local.public_hosts.matrix]
    files  = [local.public_hosts.files]
  }

  site_addresses = {
    for service, hosts in local.site_hosts :
    service => join(", ", flatten([
      for host in hosts : local.public_port_suffix == "" ? ["https://${host}"] : ["https://${host}", "https://${host}${local.public_port_suffix}"]
    ]))
  }

  matrix_mas_upstream_id = "01JQ7N9R4QK6W3M5X8Y2ZC1DHF"

  matrix_chat_appservice = {
    id                  = "weave-chat-synapse"
    sender_localpart    = "_weave_appservice"
    virtual_user_prefix = "_weave_"
    callback_url        = "http://${local.service_names.backend}:${var.backend_container_port}/api/internal/chat/matrix/appservice"
    runtime_path        = "/run/weave-chat-appservice"
    runtime_volume      = local.volume_names.chat_appservice
  }
  matrix_chat_appservice_forbidden_credentials = [
    var.db_admin_password,
    var.backend_db_password,
    var.keycloak_admin_password,
    var.keycloak_db_password,
    var.matrix_mas_client_secret,
    var.identity_admin_client_secret,
    var.identity_events_hmac_secret,
    var.mas_db_password,
    var.mas_encryption_secret,
    var.mas_signing_key_pem,
    var.mas_matrix_secret,
    var.synapse_db_password,
    var.synapse_registration_shared_secret,
    var.synapse_macaroon_secret_key,
    var.synapse_form_secret,
    var.nextcloud_db_password,
    var.nextcloud_admin_password,
    var.nextcloud_backend_actor_token,
    var.devops_gitlab_api_token,
    var.office_onlyoffice_jwt_secret,
    var.livekit_api_key,
    var.livekit_api_secret,
    var.boards_openproject_api_token,
    var.openproject_secret_key_base,
  ]

  # Caddy TLS (from #3)
  caddy_tls_cert_file = abspath(coalesce(var.caddy_tls_cert_file, "${local.runtime_asset_root}/caddy/certs/weave.test.pem"))
  caddy_tls_key_file  = abspath(coalesce(var.caddy_tls_key_file, "${local.runtime_asset_root}/caddy/certs/weave.test-key.pem"))
  caddy_tls_ca_file   = abspath(coalesce(var.caddy_tls_ca_file, "${local.runtime_asset_root}/caddy/certs/weave-local-ca.pem"))
  caddy_certs_dir     = dirname(local.caddy_tls_cert_file)
  caddyfile_path      = abspath("${local.runtime_asset_root}/caddy/Caddyfile")
  connector_provider_callbacks_guard = var.connector_provider_callbacks_exposed ? "" : join("\n", [
    "\t@connector_provider_callbacks path /api/interop/slack/oauth/callback /api/interop/slack/events",
    "\thandle @connector_provider_callbacks {",
    "\t\trespond \"Connector provider callbacks are disabled by infrastructure defaults.\" 404",
    "\t}",
  ])
  caddyfile_content = templatefile("${path.module}/templates/Caddyfile.tpl", {
    weave_site_addresses  = local.site_addresses.weave
    api_site_addresses    = local.site_addresses.api
    admin_site_addresses  = local.site_addresses.admin
    auth_site_addresses   = local.site_addresses.auth
    mail_site_addresses   = local.site_addresses.mail
    files_site_addresses  = local.site_addresses.files
    matrix_site_addresses = local.site_addresses.matrix
    keycloak_upstream     = "${local.service_names.keycloak}:8080"
    mailpit_upstream      = "${local.service_names.mailpit}:8025"
    mailpit_enabled       = var.mailpit_enabled
    mailpit_allowed_cidrs = join(" ", var.mailpit_allowed_cidrs)
    nextcloud_upstream    = "${local.service_names.nextcloud}:80"
    api_public_url        = local.public_urls.api
    auth_public_url       = local.public_urls.auth
    client_public_url     = local.client_public_url
    nextcloud_public_url  = local.public_urls.files
    matrix_public_url     = local.public_urls.matrix
    matrix_facade_url     = local.client_matrix_facade_url
    mas_upstream          = "${local.service_names.mas}:8080"
    synapse_upstream      = "${local.service_names.synapse}:8008"
    # Backend is routed via Caddy (api_upstream); no Traefik labels needed
    api_upstream                       = "${local.service_names.backend}:${var.backend_container_port}"
    mcp_upstream                       = "${local.service_names.mcp}:${var.mcp_container_port}"
    tls_cert_filename                  = basename(local.caddy_tls_cert_file)
    tls_key_filename                   = basename(local.caddy_tls_key_file)
    connector_provider_callbacks_guard = local.connector_provider_callbacks_guard
    ca_bootstrap_host                  = local.public_hosts.weave
    ca_bootstrap_url                   = "http://${local.public_hosts.weave}:${var.proxy_http_host_port}"
    tls_ca_filename                    = basename(local.caddy_tls_ca_file)
  })

  # Backend / Keycloak contract: validate public iss values while fetching JWKS over the Docker network.
  keycloak_issuer_url    = "${local.client_auth_url}/realms/${var.tenant_slug}"
  keycloak_jwk_set_uri   = "http://${local.service_names.keycloak}:8080/realms/${var.tenant_slug}/protocol/openid-connect/certs"
  weave_app_client_id    = "weave-app"
  weave_backend_audience = "weave-backend"
  weave_mcp_client_id    = "weave-mcp-server"
  weave_mcp_resource     = "${local.public_urls.api}/mcp"
  weave_mcp_audience     = local.weave_mcp_resource

  # Backend-to-Nextcloud adapter traffic runs inside the Docker network.
  # Public 127.0.0.1.sslip.io URLs work for the host/browser, but loop back to
  # the backend container itself when used from inside the backend container.
  nextcloud_internal_base_url         = "http://${local.service_names.nextcloud}"
  backend_actor_workspace_calendar_id = var.isolated_e2e_enabled ? "weave-e2e-workspace" : "personal"

  legacy_context_authorization_memberships = concat(
    var.context_authorization_bootstrap_enabled ? [{
      tenant_id     = var.context_authorization_default_tenant_id
      context_id    = var.context_authorization_bootstrap_context_id
      principal_ref = var.context_authorization_bootstrap_principal_ref
      role          = var.context_authorization_bootstrap_role
      source        = "local-dev-bootstrap"
    }] : [],
    var.context_authorization_bootstrap_enabled && var.context_authorization_dogfood_principal_ref != "" ? [{
      tenant_id     = var.context_authorization_default_tenant_id
      context_id    = var.context_authorization_bootstrap_context_id
      principal_ref = var.context_authorization_dogfood_principal_ref
      role          = var.context_authorization_bootstrap_role
      source        = "local-dogfood-bootstrap"
    }] : [],
  )
  context_authorization_memberships = var.isolated_e2e_enabled ? var.isolated_e2e_context_memberships : local.legacy_context_authorization_memberships

  service_databases = {
    backend = {
      database_name        = "${var.db_name}_backend"
      username             = var.backend_db_username
      escaped_password     = replace(var.backend_db_password, "'", "''")
      create_statement_sql = "format('CREATE DATABASE %I OWNER %I', '${var.db_name}_backend', '${var.backend_db_username}')"
      bootstrap_sql        = ""
    }
    keycloak = {
      database_name        = "${var.db_name}_keycloak"
      username             = var.keycloak_db_username
      escaped_password     = replace(var.keycloak_db_password, "'", "''")
      create_statement_sql = "format('CREATE DATABASE %I OWNER %I', '${var.db_name}_keycloak', '${var.keycloak_db_username}')"
      bootstrap_sql        = ""
    }
    mas = {
      database_name        = "${var.db_name}_mas"
      username             = var.mas_db_username
      escaped_password     = replace(var.mas_db_password, "'", "''")
      create_statement_sql = "format('CREATE DATABASE %I OWNER %I', '${var.db_name}_mas', '${var.mas_db_username}')"
      bootstrap_sql        = ""
    }
    synapse = {
      database_name        = "${var.db_name}_synapse"
      username             = var.synapse_db_username
      escaped_password     = replace(var.synapse_db_password, "'", "''")
      create_statement_sql = "format('CREATE DATABASE %I OWNER %I TEMPLATE template0 LC_COLLATE ''C'' LC_CTYPE ''C''', '${var.db_name}_synapse', '${var.synapse_db_username}')"
      bootstrap_sql        = ""
    }
    nextcloud = {
      database_name        = var.db_name
      username             = var.nextcloud_db_username
      escaped_password     = replace(var.nextcloud_db_password, "'", "''")
      create_statement_sql = "format('CREATE DATABASE %I OWNER %I', '${var.db_name}', '${var.nextcloud_db_username}')"
      database_exists_sql  = "SELECT 1 FROM pg_database WHERE datname = '${var.db_name}'"
      bootstrap_sql        = <<-EOSCHEMA
        SELECT EXISTS (
          SELECT 1
          FROM pg_database
          WHERE datname = '${var.db_name}'
        ) AS nextcloud_database_exists \gset
        \if :nextcloud_database_exists
        \connect ${var.db_name}
        DO $$
        BEGIN
          IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'nextcloud') THEN
            EXECUTE format('CREATE SCHEMA %I AUTHORIZATION %I', 'nextcloud', '${var.nextcloud_db_username}');
          END IF;
        END
        $$;

        ALTER SCHEMA nextcloud OWNER TO ${var.nextcloud_db_username};
        GRANT USAGE, CREATE ON SCHEMA nextcloud TO ${var.nextcloud_db_username};
        ALTER ROLE ${var.nextcloud_db_username} IN DATABASE ${var.db_name} SET search_path TO nextcloud, public;
        \connect postgres
        \endif
      EOSCHEMA
    }
  }

  postgres_init_sql = <<-SQL
    ${join("\n\n", [
  for _, service in local.service_databases : <<-EOS
        DO $$
        BEGIN
          IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${service.username}') THEN
            CREATE ROLE ${service.username} LOGIN PASSWORD '${service.escaped_password}';
          ELSE
            ALTER ROLE ${service.username} WITH LOGIN PASSWORD '${service.escaped_password}';
          END IF;
        END
        $$;

        ${service.create_statement_sql != "''" ? format("SELECT %s\nWHERE NOT EXISTS (\n  SELECT 1\n  FROM pg_database\n  WHERE datname = '%s'\n) \\gexec\n", service.create_statement_sql, service.database_name) : ""}

        ${try(service.database_exists_sql, "SELECT 1 FROM pg_database WHERE datname = '${service.database_name}'") != "" ? format("SELECT format('ALTER DATABASE %%I OWNER TO %%I', '%s', '%s')\nWHERE EXISTS (\n  %s\n) \\gexec\n\nSELECT format('REVOKE ALL ON DATABASE %%I FROM PUBLIC', '%s')\nWHERE EXISTS (\n  %s\n) \\gexec\n\nSELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %%I TO %%I', '%s', '%s')\nWHERE EXISTS (\n  %s\n) \\gexec", service.database_name, service.username, try(service.database_exists_sql, "SELECT 1 FROM pg_database WHERE datname = '${service.database_name}'"), service.database_name, try(service.database_exists_sql, "SELECT 1 FROM pg_database WHERE datname = '${service.database_name}'"), service.database_name, service.username, try(service.database_exists_sql, "SELECT 1 FROM pg_database WHERE datname = '${service.database_name}'")) : ""}
        ${service.bootstrap_sql}
      EOS
])}
  SQL

generated_files = {
  postgres_init_sql = {
    filename = "${local.runtime_asset_root}/db/001-init.sql"
    content  = local.postgres_init_sql
  }
  mas_signing_key = {
    filename = "${local.runtime_asset_root}/mas/signing.key"
    content  = var.mas_signing_key_pem
  }
  mas_config = {
    filename = "${local.runtime_asset_root}/mas/config.yaml"
    content = templatefile("${path.module}/templates/mas-config.yaml.tpl", {
      mas_public_url         = local.matrix_provider_public_url
      mas_db_host            = local.service_names.db
      mas_db_port            = 5432
      mas_db_name            = local.service_databases.mas.database_name
      mas_db_username        = var.mas_db_username
      mas_db_password        = var.mas_db_password
      matrix_homeserver      = local.public_hosts.matrix
      matrix_endpoint        = "http://${local.service_names.synapse}:8008"
      matrix_secret          = var.mas_matrix_secret
      encryption_secret      = var.mas_encryption_secret
      signing_key_kid        = "mas-default"
      upstream_provider_id   = local.matrix_mas_upstream_id
      upstream_issuer        = local.keycloak_issuer_url
      upstream_client_id     = "matrix-mas"
      upstream_client_secret = var.matrix_mas_client_secret
      keycloak_human_name    = "Keycloak"
    })
  }
  synapse_homeserver = {
    filename = "${local.runtime_asset_root}/synapse/homeserver.yaml"
    content = templatefile("${path.module}/templates/homeserver.yaml.tpl", {
      matrix_homeserver                        = local.public_hosts.matrix
      matrix_public_url                        = local.matrix_provider_public_url
      synapse_db_host                          = local.service_names.db
      synapse_db_port                          = 5432
      synapse_db_name                          = local.service_databases.synapse.database_name
      synapse_db_username                      = var.synapse_db_username
      synapse_db_password                      = var.synapse_db_password
      synapse_registration_secret              = var.synapse_registration_shared_secret
      synapse_macaroon_secret_key              = var.synapse_macaroon_secret_key
      synapse_form_secret                      = var.synapse_form_secret
      mas_internal_endpoint                    = "http://${local.service_names.mas}:8080/"
      mas_matrix_secret                        = var.mas_matrix_secret
      matrix_chat_appservice_registration_path = "${local.matrix_chat_appservice.runtime_path}/registration.yaml"
    })
  }
  matrix_chat_appservice_registration = {
    filename = "${local.runtime_asset_root}/synapse/appservices/weave-chat.yaml"
    content = templatefile("${path.module}/templates/synapse-appservice.yaml.tpl", {
      appservice_id               = local.matrix_chat_appservice.id
      appservice_callback_url     = local.matrix_chat_appservice.callback_url
      appservice_as_token         = var.matrix_chat_appservice_as_token
      appservice_hs_token         = var.matrix_chat_appservice_hs_token
      appservice_sender_localpart = local.matrix_chat_appservice.sender_localpart
      virtual_user_prefix         = local.matrix_chat_appservice.virtual_user_prefix
      matrix_homeserver_regex     = replace(local.public_hosts.matrix, ".", "\\.")
    })
  }
  matrix_chat_appservice_as_token = {
    filename = "${local.runtime_asset_root}/synapse/appservices/as-token"
    content  = "${var.matrix_chat_appservice_as_token}\n"
  }
  matrix_chat_appservice_hs_token = {
    filename = "${local.runtime_asset_root}/synapse/appservices/hs-token"
    content  = "${var.matrix_chat_appservice_hs_token}\n"
  }
  provider_selections = {
    filename = "${local.runtime_asset_root}/provider-selections.json"
    content = jsonencode({
      "identity-idm" = {
        category                = "identity-idm"
        providerKey             = "keycloak-realm"
        choiceModel             = "recommended_self_hosted_default"
        secretRef               = "secretref://weave/provider/keycloak-realm/client-secret"
        selectedBy              = "actor:local-live-bootstrap"
        selectedAt              = "2026-01-01T00:00:00Z"
        applied                 = true
        supportSafe             = true
        migrationDryRunRequired = false
        lossyMappingNotes       = []
      }
      chat = {
        category                = "chat"
        providerKey             = "synapse-homeserver"
        choiceModel             = "recommended_self_hosted_default"
        secretRef               = "secretref://weave/provider/synapse-homeserver/signing-key"
        selectedBy              = "actor:local-live-bootstrap"
        selectedAt              = "2026-01-01T00:00:00Z"
        applied                 = true
        supportSafe             = true
        migrationDryRunRequired = false
        lossyMappingNotes       = []
      }
      files = {
        category                = "files"
        providerKey             = "nextcloud-files"
        choiceModel             = "recommended_self_hosted_default"
        secretRef               = "secretref://weave/provider/nextcloud-files/backend-token"
        selectedBy              = "actor:local-live-bootstrap"
        selectedAt              = "2026-01-01T00:00:00Z"
        applied                 = true
        supportSafe             = true
        migrationDryRunRequired = false
        lossyMappingNotes       = []
      }
      calendar = {
        category                = "calendar"
        providerKey             = "nextcloud-caldav"
        choiceModel             = "recommended_self_hosted_default"
        secretRef               = "secretref://weave/provider/nextcloud-caldav/backend-token"
        selectedBy              = "actor:local-live-bootstrap"
        selectedAt              = "2026-01-01T00:00:00Z"
        applied                 = true
        supportSafe             = true
        migrationDryRunRequired = false
        lossyMappingNotes       = []
      }
      "boards-tasks" = {
        category                = "boards-tasks"
        providerKey             = "openproject-primary"
        choiceModel             = "recommended_self_hosted_default"
        secretRef               = "secretref://weave/provider/openproject-primary/api-token"
        selectedBy              = "actor:local-live-bootstrap"
        selectedAt              = "2026-01-01T00:00:00Z"
        applied                 = true
        supportSafe             = true
        migrationDryRunRequired = false
        lossyMappingNotes       = []
      }
      "meetings-calls" = {
        category                = "meetings-calls"
        providerKey             = "livekit"
        choiceModel             = "recommended_self_hosted_default"
        secretRef               = "secretref://weave/provider/livekit/api-key"
        selectedBy              = "actor:local-live-bootstrap"
        selectedAt              = "2026-01-01T00:00:00Z"
        applied                 = true
        supportSafe             = true
        migrationDryRunRequired = false
        lossyMappingNotes       = []
      }
    })
  }
}
}

locals {
  matrix_chat_appservice_registration_contract = yamldecode(templatefile("${path.module}/templates/synapse-appservice.yaml.tpl", {
    appservice_id               = "weave-chat-synapse"
    appservice_callback_url     = "http://weave-backend:8080/api/internal/chat/matrix/appservice"
    appservice_as_token         = "contract-as-token"
    appservice_hs_token         = "contract-hs-token"
    appservice_sender_localpart = "_weave_appservice"
    virtual_user_prefix         = "_weave_"
    matrix_homeserver_regex     = "matrix\\.weave\\.test"
  }))
}

resource "docker_network" "weave_network" {
  name = var.docker_network_name

  dynamic "labels" {
    for_each = local.resource_labels
    content {
      label = labels.key
      value = labels.value
    }
  }
}

resource "terraform_data" "isolated_e2e_guard" {
  input = var.isolated_e2e_namespace

  lifecycle {
    precondition {
      condition     = var.isolated_e2e_enabled || (var.isolated_e2e_namespace == "" && length(var.isolated_e2e_context_memberships) == 0)
      error_message = "isolated E2E namespace/memberships require isolated_e2e_enabled=true."
    }
    precondition {
      condition = !var.isolated_e2e_enabled || (
        startswith(var.isolated_e2e_namespace, "weave-e2e-") &&
        var.docker_network_name == "${var.isolated_e2e_namespace}_network" &&
        alltrue([for name in values(local.service_names) : startswith(name, "${var.isolated_e2e_namespace}-")]) &&
        alltrue([for name in values(local.volume_names) : startswith(name, "${replace(var.isolated_e2e_namespace, "-", "_")}_")]) &&
        length(distinct([
          var.proxy_http_host_port,
          var.proxy_host_port,
          var.keycloak_host_port,
          var.keycloak_management_host_port,
          var.mailpit_web_host_port,
          var.mas_host_port,
          var.synapse_host_port,
          var.nextcloud_host_port,
          var.backend_host_port,
          var.mcp_host_port,
        ])) == 10 &&
        var.context_authorization_principal_claim == "preferred_username" &&
        !var.context_authorization_bootstrap_enabled &&
        var.context_authorization_dogfood_principal_ref == "" &&
        !var.create_test_user &&
        length(var.isolated_e2e_context_memberships) == 3
      )
      error_message = "isolated E2E requires a weave-e2e-* namespace across containers, volumes, network and unique host ports, preferred_username, exactly three run-scoped memberships, and all persistent/static test-user inputs disabled."
    }
    precondition {
      condition = !var.chat_e2e_proof_enabled || (
        var.isolated_e2e_enabled &&
        var.chat_e2e_proof_run_id != "" &&
        var.chat_e2e_proof_token_host_path != "" &&
        var.isolated_e2e_namespace == "weave-e2e-${substr(sha256(var.chat_e2e_proof_run_id), 0, 16)}" &&
        startswith(var.chat_e2e_proof_token_host_path, "/") &&
        basename(var.chat_e2e_proof_token_host_path) == "chat-provider-proof.token" &&
        basename(dirname(var.chat_e2e_proof_token_host_path)) == var.isolated_e2e_namespace &&
        can(regex("/weave-e2e-[a-z0-9][a-z0-9-]{5,47}/chat-provider-proof[.]token$", var.chat_e2e_proof_token_host_path)) &&
        fileexists(var.chat_e2e_proof_token_host_path)
      )
      error_message = "Chat provider proof requires an isolated namespace, exact run ID, and an existing run-scoped host credential file."
    }
    precondition {
      condition = var.chat_e2e_proof_enabled || (
        var.chat_e2e_proof_run_id == "" &&
        var.chat_e2e_proof_token_host_path == ""
      )
      error_message = "Persistent/default deployments must not retain a Chat provider proof run binding or credential path."
    }
  }
}

resource "terraform_data" "matrix_chat_appservice_secret_guard" {
  input = local.matrix_chat_appservice.id

  lifecycle {
    precondition {
      condition = nonsensitive(
        length(var.matrix_chat_appservice_as_token) >= 43 &&
        length(var.matrix_chat_appservice_hs_token) >= 43
      )
      error_message = "Matrix Chat Application Service tokens must each contain at least 43 characters of private random material."
    }
    precondition {
      condition = nonsensitive(
        var.matrix_chat_appservice_as_token != var.matrix_chat_appservice_hs_token &&
        !contains(local.matrix_chat_appservice_forbidden_credentials, var.matrix_chat_appservice_as_token) &&
        !contains(local.matrix_chat_appservice_forbidden_credentials, var.matrix_chat_appservice_hs_token)
      )
      error_message = "Matrix Chat Application Service tokens must be independent from each other and every MAS, Synapse, and identity credential."
    }
    precondition {
      condition = try(
        local.matrix_chat_appservice_registration_contract.rate_limited == true &&
        local.matrix_chat_appservice_registration_contract.receive_ephemeral == false &&
        local.matrix_chat_appservice_registration_contract.namespaces.users[0].exclusive == true &&
        local.matrix_chat_appservice_registration_contract.namespaces.users[0].regex == "^@_weave_[a-z0-9]{26,64}:matrix\\.weave\\.test$" &&
        local.matrix_chat_appservice_registration_contract.namespaces.aliases[0].exclusive == true &&
        local.matrix_chat_appservice_registration_contract.namespaces.aliases[0].regex == "^#_weave_[a-z0-9]{26,64}:matrix\\.weave\\.test$" &&
        length(local.matrix_chat_appservice_registration_contract.namespaces.rooms) == 0,
        false,
      )
      error_message = "Matrix Chat Application Service registration must remain valid YAML with exact Weave virtual-user and alias namespaces."
    }
  }
}

resource "terraform_data" "network_ready" {
  triggers_replace = [docker_network.weave_network.id]

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    environment = {
      WEAVE_NETWORK_NAME = docker_network.weave_network.name
    }
    command = <<-EOT
      set -euo pipefail
      docker network inspect "$${WEAVE_NETWORK_NAME}" >/dev/null
    EOT
  }
}

resource "local_sensitive_file" "generated" {
  for_each = local.generated_files

  filename        = each.value.filename
  content         = each.value.content
  file_permission = "0600"
}

resource "local_file" "caddyfile" {
  filename        = local.caddyfile_path
  content         = local.caddyfile_content
  file_permission = "0644"
}

module "postgres" {
  source = "./modules/postgres"

  network_name    = docker_network.weave_network.name
  container_name  = local.service_names.db
  image_name      = var.postgres_image
  volume_name     = local.volume_names.db
  resource_labels = local.resource_labels
  database_name   = "postgres"
  admin_username  = var.db_admin_username
  admin_password  = var.db_admin_password
  depends_on      = [terraform_data.network_ready]
}

resource "terraform_data" "postgres_bootstrap" {
  triggers_replace = [
    sha256(local.generated_files["postgres_init_sql"].content),
    var.db_admin_username,
    var.db_admin_password,
    module.postgres.container_name,
  ]

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    environment = {
      CONTAINER_NAME = module.postgres.container_name
      DATABASE_NAME  = "postgres"
      DATABASE_USER  = var.db_admin_username
      DATABASE_PASS  = var.db_admin_password
      SQL_FILE       = local_sensitive_file.generated["postgres_init_sql"].filename
    }
    command = <<-EOT
      set -euo pipefail

      for attempt in $(seq 1 60); do
        if docker exec "$${CONTAINER_NAME}" pg_isready -h 127.0.0.1 -U "$${DATABASE_USER}" -d "$${DATABASE_NAME}" >/dev/null 2>&1; then
          docker exec -e PGPASSWORD="$${DATABASE_PASS}" -i "$${CONTAINER_NAME}" \
            psql -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "$${DATABASE_USER}" -d "$${DATABASE_NAME}" < "$${SQL_FILE}"
          exit 0
        fi

        sleep 2
      done

      echo "PostgreSQL bootstrap did not become ready in time." >&2
      exit 1
    EOT
  }

  depends_on = [
    module.postgres,
    local_sensitive_file.generated["postgres_init_sql"],
  ]
}

module "reverse_proxy" {
  source = "./modules/reverse-proxy"

  network_name       = docker_network.weave_network.name
  container_name     = local.service_names.proxy
  image_name         = var.proxy_image
  http_host_port     = var.proxy_http_host_port
  https_host_port    = var.proxy_host_port
  caddyfile_path     = local_file.caddyfile.filename
  caddyfile_content  = local.caddyfile_content
  tls_cert_file      = local.caddy_tls_cert_file
  tls_key_file       = local.caddy_tls_key_file
  tls_ca_file        = local.caddy_tls_ca_file
  tls_certs_dir      = local.caddy_certs_dir
  data_volume_name   = local.volume_names.caddy_data
  config_volume_name = local.volume_names.caddy_config
  resource_labels    = local.resource_labels
  public_hosts       = local.public_hosts
  depends_on         = [terraform_data.network_ready, local_file.caddyfile]
}

module "keycloak" {
  source = "./modules/keycloak"

  network_name                = docker_network.weave_network.name
  container_name              = local.service_names.keycloak
  image_name                  = var.keycloak_image
  image_build_context         = abspath("${path.module}/../../keycloak-event-listener")
  keycloak_version            = var.keycloak_version
  volume_name                 = local.volume_names.keycloak
  resource_labels             = local.resource_labels
  host_port                   = var.keycloak_host_port
  management_host_port        = var.keycloak_management_host_port
  public_url                  = local.client_auth_url
  db_host                     = module.postgres.container_name
  db_port                     = 5432
  db_name                     = local.service_databases.keycloak.database_name
  db_schema                   = "public"
  db_username                 = var.keycloak_db_username
  db_password                 = var.keycloak_db_password
  admin_username              = var.keycloak_admin_username
  admin_password              = var.keycloak_admin_password
  identity_events_endpoint    = "http://${local.service_names.backend}:${var.backend_container_port}/api/internal/keycloak/events"
  identity_events_hmac_secret = var.identity_events_hmac_secret
  depends_on                  = [terraform_data.network_ready, terraform_data.postgres_bootstrap]
}

module "mailpit" {
  source = "./modules/mailpit"
  count  = var.mailpit_enabled ? 1 : 0

  network_name    = docker_network.weave_network.name
  container_name  = local.service_names.mailpit
  image_name      = var.mailpit_image
  volume_name     = local.volume_names.mailpit
  resource_labels = local.resource_labels
  max_messages    = var.mailpit_max_messages
  web_host_port   = var.mailpit_web_host_port
  depends_on      = [terraform_data.network_ready]
}

module "backend" {
  source = "./modules/backend"

  network_name                                     = docker_network.weave_network.name
  container_name                                   = local.service_names.backend
  image_name                                       = var.weave_backend_image
  host_port                                        = var.backend_host_port
  container_port                                   = var.backend_container_port
  public_host                                      = local.public_hosts.api
  public_base_url                                  = local.client_public_url
  api_origin                                       = local.client_api_origin
  api_base_url                                     = local.client_api_base_url
  auth_base_url                                    = local.client_auth_url
  matrix_base_url                                  = local.matrix_provider_public_url
  matrix_facade_url                                = local.client_matrix_facade_url
  chat_provider                                    = "matrix-synapse"
  chat_storage_mode                                = "jdbc"
  matrix_internal_base_url                         = "http://${local.service_names.synapse}:8008"
  matrix_server_name                               = local.public_hosts.matrix
  matrix_appservice_id                             = local.matrix_chat_appservice.id
  matrix_virtual_user_prefix                       = local.matrix_chat_appservice.virtual_user_prefix
  matrix_appservice_runtime_volume_name            = module.matrix.appservice_runtime_volume_name
  matrix_appservice_runtime_container_path         = local.matrix_chat_appservice.runtime_path
  matrix_appservice_as_token_file                  = "${local.matrix_chat_appservice.runtime_path}/as-token"
  matrix_appservice_hs_token_file                  = "${local.matrix_chat_appservice.runtime_path}/hs-token"
  chat_e2e_proof_enabled                           = var.chat_e2e_proof_enabled
  chat_e2e_proof_token_host_path                   = var.chat_e2e_proof_enabled ? var.chat_e2e_proof_token_host_path : ""
  chat_e2e_proof_token_container_path              = "/run/weave-chat-e2e-proof/token"
  chat_e2e_proof_run_id                            = var.chat_e2e_proof_enabled ? var.chat_e2e_proof_run_id : ""
  files_product_url                                = local.client_files_product_url
  calendar_product_url                             = local.client_calendar_product_url
  nextcloud_public_base_url                        = local.public_urls.files
  nextcloud_base_url                               = local.nextcloud_internal_base_url
  nextcloud_files_actor_model                      = "backend-service-account"
  nextcloud_files_actor_username                   = var.nextcloud_backend_actor_username
  nextcloud_files_actor_token                      = var.nextcloud_backend_actor_token
  nextcloud_files_webdav_root_path                 = "/remote.php/dav/files"
  caldav_base_url                                  = local.nextcloud_internal_base_url
  caldav_calendar_path_template                    = "/remote.php/dav/calendars/${var.nextcloud_backend_actor_username}/${local.backend_actor_workspace_calendar_id}/"
  caldav_auth_mode                                 = "BASIC"
  caldav_backend_username                          = var.nextcloud_backend_actor_username
  caldav_backend_token                             = var.nextcloud_backend_actor_token
  caldav_request_timeout_seconds                   = 10
  caldav_external_discovery_url                    = "${local.public_urls.files}/remote.php/dav"
  caldav_external_credential_mode                  = "nextcloud-login-flow-app-password"
  caldav_external_profile_password_mode            = "omit"
  caldav_external_private_user_calendars           = "disabled"
  context_authorization_tenant_claim               = var.context_authorization_tenant_claim
  context_authorization_tenant_fallback_claim      = var.context_authorization_tenant_fallback_claim
  context_authorization_default_tenant_id          = var.context_authorization_default_tenant_id
  context_authorization_principal_claim            = var.context_authorization_principal_claim
  context_authorization_principal_ref_prefix       = var.context_authorization_principal_ref_prefix
  context_authorization_memberships                = local.context_authorization_memberships
  isolated_e2e_namespace                           = var.isolated_e2e_enabled ? var.isolated_e2e_namespace : ""
  context_authorization_bootstrap_enabled          = var.context_authorization_bootstrap_enabled
  context_authorization_bootstrap_context_id       = var.context_authorization_bootstrap_context_id
  context_authorization_bootstrap_principal_ref    = var.context_authorization_bootstrap_principal_ref
  context_authorization_dogfood_principal_ref      = var.context_authorization_dogfood_principal_ref
  context_authorization_bootstrap_role             = var.context_authorization_bootstrap_role
  interop_enabled                                  = false
  interop_slack_enabled                            = false
  interop_teams_enabled                            = false
  connectors_public_sdk_enabled                    = false
  provider_stack_profile                           = var.provider_stack_profile
  provider_stack_readiness                         = var.provider_stack_readiness
  devops_primary_provider                          = var.devops_primary_provider
  devops_gitlab_runtime_enabled                    = var.devops_gitlab_runtime_enabled
  devops_gitlab_base_url                           = var.devops_gitlab_base_url
  devops_gitlab_api_token                          = var.devops_gitlab_api_token
  devops_gitlab_writes_enabled                     = var.devops_gitlab_writes_enabled
  office_primary_provider                          = var.office_primary_provider
  office_onlyoffice_runtime_enabled                = var.office_onlyoffice_runtime_enabled
  office_onlyoffice_document_server_url            = var.office_onlyoffice_document_server_url
  office_onlyoffice_jwt_secret                     = var.office_onlyoffice_jwt_secret
  office_nextcloud_integration_mode                = var.office_nextcloud_integration_mode
  office_collabora_runtime_enabled                 = var.office_collabora_runtime_enabled
  groupware_contacts_runtime_enabled               = var.groupware_contacts_runtime_enabled
  groupware_forms_runtime_enabled                  = var.groupware_forms_runtime_enabled
  livekit_runtime_enabled                          = var.livekit_runtime_enabled
  livekit_url                                      = var.livekit_url
  livekit_api_key                                  = var.livekit_api_key
  livekit_api_secret                               = var.livekit_api_secret
  livekit_token_endpoint                           = var.livekit_token_endpoint
  boards_runtime_enabled                           = var.boards_runtime_enabled
  boards_provider                                  = var.boards_provider
  boards_openproject_runtime_enabled               = var.boards_openproject_runtime_enabled
  boards_openproject_read_sync_enabled             = var.boards_openproject_read_sync_enabled
  boards_openproject_context_authorization_enabled = var.boards_openproject_context_authorization_enabled
  boards_openproject_audit_consent_enabled         = var.boards_openproject_audit_consent_enabled
  boards_openproject_provider_writes_enabled       = var.boards_openproject_provider_writes_enabled
  boards_nextcloud_deck_runtime_enabled            = var.boards_nextcloud_deck_runtime_enabled
  boards_openproject_auth_mode                     = var.boards_openproject_auth_mode
  boards_openproject_base_url                      = var.boards_openproject_base_url
  boards_openproject_api_token                     = var.boards_openproject_api_token
  provider_selections_source                       = local_sensitive_file.generated["provider_selections"].filename
  provider_selections_source_hash                  = sha256(local.generated_files["provider_selections"].content)
  provider_selections_storage_path                 = "/app/provider-selections.json"
  persistence_jdbc_url                             = "jdbc:postgresql://${module.postgres.container_name}:5432/${local.service_databases.backend.database_name}"
  persistence_jdbc_username                        = var.backend_db_username
  persistence_jdbc_password                        = var.backend_db_password
  device_credential_storage_mode                   = "jdbc"
  oidc_issuer_uri                                  = local.keycloak_issuer_url
  oidc_jwk_set_uri                                 = local.keycloak_jwk_set_uri
  oidc_required_audience                           = local.weave_backend_audience
  client_id                                        = local.weave_app_client_id
  identity_keycloak_base_url                       = "http://${local.service_names.keycloak}:8080"
  identity_keycloak_realm                          = var.tenant_slug
  identity_keycloak_organization_alias             = var.tenant_slug
  identity_keycloak_client_secret                  = var.identity_admin_client_secret
  identity_events_hmac_secret                      = var.identity_events_hmac_secret
  healthcheck_path                                 = "/api/health/ready"
  resource_labels                                  = local.resource_labels
  depends_on                                       = [terraform_data.isolated_e2e_guard, terraform_data.matrix_chat_appservice_secret_guard, terraform_data.network_ready, terraform_data.postgres_bootstrap, module.keycloak, module.matrix, local_sensitive_file.generated]
}

module "mcp" {
  source = "./modules/mcp"

  network_name             = docker_network.weave_network.name
  container_name           = local.service_names.mcp
  image_name               = var.weave_mcp_server_image
  host_port                = var.mcp_host_port
  container_port           = var.mcp_container_port
  backend_base_url         = "http://${local.service_names.backend}:${var.backend_container_port}"
  oidc_issuer_uri          = local.keycloak_issuer_url
  oidc_jwk_set_uri         = local.keycloak_jwk_set_uri
  oidc_required_audience   = local.weave_mcp_audience
  mcp_resource             = local.weave_mcp_resource
  oidc_token_uri           = "http://${local.service_names.keycloak}:8080/realms/${var.tenant_slug}/protocol/openid-connect/token"
  mcp_client_id            = local.weave_mcp_client_id
  mcp_client_secret_file   = var.weave_mcp_client_secret_file
  inbound_authorized_party = local.weave_app_client_id
  backend_oidc_audience    = local.weave_backend_audience
  backend_scope            = "weave:mcp-backend"
  resource_labels          = local.resource_labels
  depends_on               = [terraform_data.network_ready, module.backend, module.keycloak]
}

module "matrix" {
  source = "./modules/matrix"

  network_name                      = docker_network.weave_network.name
  mas_container_name                = local.service_names.mas
  synapse_container_name            = local.service_names.synapse
  mas_image_name                    = var.mas_image
  synapse_image_name                = var.synapse_image
  synapse_volume_name               = local.volume_names.synapse
  appservice_runtime_volume_name    = local.matrix_chat_appservice.runtime_volume
  appservice_runtime_container_path = local.matrix_chat_appservice.runtime_path
  appservice_registration_source    = local_sensitive_file.generated["matrix_chat_appservice_registration"].filename
  appservice_registration_hash      = sha256(local.generated_files["matrix_chat_appservice_registration"].content)
  appservice_as_token_source        = local_sensitive_file.generated["matrix_chat_appservice_as_token"].filename
  appservice_as_token_hash          = sha256(local.generated_files["matrix_chat_appservice_as_token"].content)
  appservice_hs_token_source        = local_sensitive_file.generated["matrix_chat_appservice_hs_token"].filename
  appservice_hs_token_hash          = sha256(local.generated_files["matrix_chat_appservice_hs_token"].content)
  mas_host_port                     = var.mas_host_port
  synapse_host_port                 = var.synapse_host_port
  matrix_public_host                = local.public_hosts.matrix
  mas_config_source                 = local_sensitive_file.generated["mas_config"].filename
  mas_config_hash                   = sha256(local.generated_files["mas_config"].content)
  mas_signing_key_source            = local_sensitive_file.generated["mas_signing_key"].filename
  mas_signing_key_hash              = sha256(local.generated_files["mas_signing_key"].content)
  synapse_config_source             = local_sensitive_file.generated["synapse_homeserver"].filename
  synapse_config_hash               = sha256(local.generated_files["synapse_homeserver"].content)
  tls_ca_file                       = local.caddy_tls_ca_file
  tls_ca_filename                   = basename(local.caddy_tls_ca_file)
  synapse_uid                       = var.synapse_uid
  synapse_gid                       = var.synapse_gid
  resource_labels                   = local.resource_labels
  depends_on                        = [terraform_data.matrix_chat_appservice_secret_guard, terraform_data.network_ready, terraform_data.postgres_bootstrap, module.keycloak, local_sensitive_file.generated]
}

module "nextcloud" {
  source = "./modules/nextcloud"

  network_name       = docker_network.weave_network.name
  container_name     = local.service_names.nextcloud
  image_name         = var.nextcloud_image
  volume_name        = local.volume_names.nextcloud
  resource_labels    = local.resource_labels
  host_port          = var.nextcloud_host_port
  public_host        = local.public_hosts.files
  public_url         = local.public_urls.files
  public_scheme      = var.public_scheme
  public_port_suffix = local.public_port_suffix
  tls_ca_file        = local.caddy_tls_ca_file
  tls_ca_filename    = basename(local.caddy_tls_ca_file)
  db_host            = module.postgres.container_name
  db_name            = local.service_databases.nextcloud.database_name
  db_username        = var.nextcloud_db_username
  db_password        = var.nextcloud_db_password
  admin_username     = var.nextcloud_admin_username
  admin_password     = var.nextcloud_admin_password
  depends_on         = [terraform_data.network_ready, terraform_data.postgres_bootstrap]
}

moved {
  from = docker_image.service["postgres"]
  to   = module.postgres.docker_image.this
}

moved {
  from = docker_volume.data["postgres"]
  to   = module.postgres.docker_volume.data
}

moved {
  from = docker_container.weave_db
  to   = module.postgres.docker_container.this
}

moved {
  from = docker_image.service["proxy"]
  to   = module.reverse_proxy.docker_image.this
}

moved {
  from = docker_container.weave_proxy
  to   = module.reverse_proxy.docker_container.this
}

moved {
  from = docker_image.service["keycloak"]
  to   = module.keycloak.docker_image.this
}

moved {
  from = docker_volume.data["keycloak"]
  to   = module.keycloak.docker_volume.data
}

moved {
  from = docker_container.weave_keycloak
  to   = module.keycloak.docker_container.this
}

moved {
  from = docker_image.service["mas"]
  to   = module.matrix.docker_image.mas
}

moved {
  from = docker_image.service["synapse"]
  to   = module.matrix.docker_image.synapse
}

moved {
  from = docker_volume.data["synapse"]
  to   = module.matrix.docker_volume.synapse_data
}

moved {
  from = docker_container.weave_mas
  to   = module.matrix.docker_container.mas
}

moved {
  from = docker_container.weave_synapse
  to   = module.matrix.docker_container.synapse
}

moved {
  from = docker_image.service["nextcloud"]
  to   = module.nextcloud.docker_image.this
}

moved {
  from = docker_volume.data["nextcloud"]
  to   = module.nextcloud.docker_volume.data
}

moved {
  from = docker_container.weave_nextcloud
  to   = module.nextcloud.docker_container.this
}
