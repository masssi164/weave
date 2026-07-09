variable "docker_network_name" {
  description = "Docker network name for the weave stack."
  type        = string
  default     = "weave_network"
}

variable "docker_host" {
  description = "Docker daemon endpoint used by the Terraform Docker provider."
  type        = string
  default     = "unix:///var/run/docker.sock"

  validation {
    condition     = startswith(var.docker_host, "unix://") || var.docker_host == "tcp://forgejo-runner-dind:2375"
    error_message = "docker_host must be a unix socket endpoint such as unix:///var/run/docker.sock, or the local Forgejo runner DinD endpoint tcp://forgejo-runner-dind:2375."
  }
}

variable "tenant_slug" {
  description = "Tenant identifier used for the Keycloak realm."
  type        = string
  default     = "weave"

  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.tenant_slug))
    error_message = "tenant_slug must contain only lowercase letters, numbers, and hyphens."
  }
}

variable "tenant_domain" {
  description = "Base domain used to derive public service hostnames."
  type        = string
  default     = "weave.test"
}

variable "local_lan_host" {
  description = "Optional non-canonical LAN host/IP for certificate SAN/debug compatibility. For example, when tenant_domain is weave.test, public service, app-start, issuer, and CA URLs remain DNS-first on weave.test and subdomains such as api.weave.test."
  type        = string
  default     = ""
}

variable "auth_subdomain" {
  description = "Subdomain used for Keycloak."
  type        = string
  default     = "auth"
}

variable "matrix_subdomain" {
  description = "Subdomain used for Matrix."
  type        = string
  default     = "matrix"
}

variable "nextcloud_subdomain" {
  description = "Subdomain used for the canonical Nextcloud URL."
  type        = string
  default     = "files"
}

variable "api_subdomain" {
  description = "Subdomain used for the canonical Weave backend API origin."
  type        = string
  default     = "api"
}

variable "admin_subdomain" {
  description = "Subdomain used for the separate Organization/Admin Console deploy target."
  type        = string
  default     = "admin"
}

variable "public_scheme" {
  description = "Public URL scheme for browser-facing services."
  type        = string
  default     = "https"

  validation {
    condition     = contains(["http", "https"], var.public_scheme)
    error_message = "public_scheme must be either http or https."
  }
}

variable "proxy_host_port" {
  description = "HTTPS host port exposed by the reverse proxy."
  type        = number
  default     = 443
}

variable "proxy_http_host_port" {
  description = "HTTP host port exposed by the reverse proxy for HTTP-to-HTTPS redirects."
  type        = number
  default     = 80
}

variable "keycloak_host_port" {
  description = "Direct host port for Keycloak application HTTP and bootstrap login access."
  type        = number
  default     = 8080
}

variable "keycloak_management_host_port" {
  description = "Direct host port for Keycloak management HTTP health endpoints."
  type        = number
  default     = 9000
}

variable "mailpit_image" {
  description = "Mailpit image used for dogfood/local-only mail capture."
  type        = string
  default     = "axllent/mailpit:v1.27"
}

variable "mailpit_web_host_port" {
  description = "Loopback-only host port for the Mailpit web/API inbox."
  type        = number
  default     = 8025
}

variable "mas_host_port" {
  description = "Direct host port for Matrix Authentication Service."
  type        = number
  default     = 8082
}

variable "synapse_host_port" {
  description = "Direct host port for Synapse."
  type        = number
  default     = 8008
}

variable "synapse_uid" {
  description = "UID used by Synapse for writable files in the mounted data volume."
  type        = number
  default     = 991
}

variable "synapse_gid" {
  description = "GID used by Synapse for writable files in the mounted data volume."
  type        = number
  default     = 991
}

variable "nextcloud_host_port" {
  description = "Direct host port for Nextcloud."
  type        = number
  default     = 8083
}

variable "backend_host_port" {
  description = "Direct host port for the Weave backend."
  type        = number
  default     = 8084
}

variable "backend_container_port" {
  description = "Internal HTTP port exposed by the Weave backend container."
  type        = number
  default     = 8080
}

variable "weave_backend_image" {
  description = "Docker image for the Weave backend service."
  type        = string
  default     = "weave-backend:local"
}

variable "nextcloud_trusted_proxies" {
  description = "Space-separated proxy IPs or CIDRs trusted by Nextcloud."
  type        = string
  default     = "172.16.0.0/12"
}

variable "proxy_image" {
  description = "Caddy image used for the reverse proxy."
  type        = string
  default     = "caddy:2.8"
}

variable "caddy_tls_cert_file" {
  description = "Host path to the local TLS certificate served by Caddy. Keep cert, key, and CA files in the same directory."
  type        = string
  default     = null
}

variable "caddy_tls_key_file" {
  description = "Host path to the local TLS private key served by Caddy. Keep cert, key, and CA files in the same directory."
  type        = string
  default     = null
}

variable "caddy_tls_ca_file" {
  description = "Host path to the local CA certificate trusted by containers that call public HTTPS URLs. Keep cert, key, and CA files in the same directory."
  type        = string
  default     = null
}

variable "api_upstream" {
  description = "Internal upstream address reserved for the future Weave backend API."
  type        = string
  default     = "weave-backend:8080"
}

variable "connector_provider_callbacks_exposed" {
  description = "Expose provider callback/webhook routes such as Slack OAuth/events through Caddy. Defaults false so connector runtime remains fail-closed until a reviewed connector rollout enables it."
  type        = bool
  default     = false
}


variable "provider_stack_profile" {
  description = "Provider-stack runtime posture profile. Keep fail-closed by default so provider seams are visible but runtimes/secrets are disabled."
  type        = string
  default     = "fail-closed"

  validation {
    condition     = contains(["fail-closed", "local-live"], var.provider_stack_profile)
    error_message = "provider_stack_profile must be fail-closed or local-live."
  }
}

variable "provider_stack_readiness" {
  description = "Support-safe aggregate provider-stack readiness label exported to backend/provider-status validation."
  type        = string
  default     = "fail-closed"

  validation {
    condition     = contains(["fail-closed", "ready", "degraded"], var.provider_stack_readiness)
    error_message = "provider_stack_readiness must be fail-closed, ready, or degraded."
  }
}

variable "devops_primary_provider" {
  description = "Primary DevOps provider candidate. GitLab CE/FOSS is the default; no Premium/Ultimate dependency is allowed."
  type        = string
  default     = "gitlab-ce-foss"

  validation {
    condition     = contains(["gitlab-ce-foss"], var.devops_primary_provider)
    error_message = "devops_primary_provider must be gitlab-ce-foss."
  }
}

variable "devops_alternative_provider" {
  description = "Alternative DevOps provider candidate. Forgejo stays first-class but disabled unless explicitly enabled."
  type        = string
  default     = "forgejo"

  validation {
    condition     = contains(["forgejo"], var.devops_alternative_provider)
    error_message = "devops_alternative_provider must be forgejo."
  }
}

variable "devops_gitlab_runtime_enabled" {
  description = "Enable GitLab CE/FOSS source-control/CI/issues/releases provider runtime. Defaults false/fail-closed."
  type        = bool
  default     = false
}

variable "devops_gitlab_base_url" {
  description = "Backend-only GitLab CE/FOSS base URL. Leave blank unless the GitLab provider runtime is intentionally enabled."
  type        = string
  default     = ""
}

variable "devops_gitlab_api_token" {
  description = "Backend-held GitLab service token. Never expose to Flutter, app config, support bundles, or provider status output."
  type        = string
  default     = ""
  sensitive   = true
}

variable "devops_gitlab_writes_enabled" {
  description = "Enable writes to GitLab. Defaults false; the current backend DevOps facade is read-only."
  type        = bool
  default     = false
}

variable "devops_forgejo_runtime_enabled" {
  description = "Enable Forgejo source-control/CI/issues/releases provider runtime. Defaults false/fail-closed."
  type        = bool
  default     = false
}

variable "devops_forgejo_base_url" {
  description = "Backend-only Forgejo base URL. Leave blank unless the Forgejo provider runtime is intentionally enabled."
  type        = string
  default     = ""
}

variable "devops_forgejo_api_token" {
  description = "Backend-held Forgejo service token. Never expose to Flutter, app config, support bundles, or provider status output."
  type        = string
  default     = ""
  sensitive   = true
}

variable "devops_forgejo_writes_enabled" {
  description = "Enable writes to Forgejo. Defaults false; the current backend DevOps facade is read-only."
  type        = bool
  default     = false
}

variable "office_primary_provider" {
  description = "Primary Office provider candidate. ONLYOFFICE Docs Community is the default candidate."
  type        = string
  default     = "onlyoffice-community"

  validation {
    condition     = contains(["onlyoffice-community"], var.office_primary_provider)
    error_message = "office_primary_provider must be onlyoffice-community."
  }
}

variable "office_onlyoffice_runtime_enabled" {
  description = "Enable ONLYOFFICE backend-owned document-session runtime. Defaults false/fail-closed."
  type        = bool
  default     = false
}

variable "office_onlyoffice_document_server_url" {
  description = "Backend-only ONLYOFFICE Document Server URL. Leave blank unless the Office runtime is intentionally enabled."
  type        = string
  default     = ""
}

variable "office_onlyoffice_jwt_secret" {
  description = "Backend-held ONLYOFFICE JWT secret. Never expose to Flutter, app config, support bundles, or provider status output."
  type        = string
  default     = ""
  sensitive   = true
}

variable "office_nextcloud_integration_mode" {
  description = "Office integration path. Default keeps ONLYOFFICE behind Nextcloud and the backend facade, not direct Flutter/provider calls."
  type        = string
  default     = "nextcloud-onlyoffice-app-behind-backend-facade"

  validation {
    condition     = contains(["nextcloud-onlyoffice-app-behind-backend-facade"], var.office_nextcloud_integration_mode)
    error_message = "office_nextcloud_integration_mode must be nextcloud-onlyoffice-app-behind-backend-facade."
  }
}

variable "office_collabora_runtime_enabled" {
  description = "Enable Collabora/CODE runtime. Defaults false; non-default candidate with licensing/runtime fit risk."
  type        = bool
  default     = false
}

variable "groupware_contacts_runtime_enabled" {
  description = "Enable Nextcloud Contacts provider runtime. Defaults false until backend PR #104 is merged and validated."
  type        = bool
  default     = false
}

variable "groupware_forms_runtime_enabled" {
  description = "Enable Nextcloud Forms provider runtime. Defaults false until backend PR #104 is merged and validated."
  type        = bool
  default     = false
}


variable "livekit_runtime_enabled" {
  description = "Enable LiveKit as the active meetings/video-call provider runtime. Defaults false/fail-closed unless explicitly configured."
  type        = bool
  default     = false
}

variable "livekit_url" {
  description = "Backend-only LiveKit server URL. Leave blank unless LiveKit meetings are intentionally configured."
  type        = string
  default     = ""
}

variable "livekit_api_key" {
  description = "Backend-held LiveKit API key. Never expose to Flutter, app config, support bundles, or provider status output."
  type        = string
  default     = ""
  sensitive   = true
}

variable "livekit_api_secret" {
  description = "Backend-held LiveKit API secret. Never expose to Flutter, app config, support bundles, or provider status output."
  type        = string
  default     = ""
  sensitive   = true
}

variable "livekit_token_endpoint" {
  description = "Optional backend/internal LiveKit token endpoint alternative. Leave blank unless a token broker is configured."
  type        = string
  default     = ""
}

variable "livekit_image" {
  description = "LiveKit server image for optional local/demo provider-stack validation."
  type        = string
  default     = "livekit/livekit-server:v1.8"
}

variable "livekit_host_port" {
  description = "Host HTTP/WebSocket port for optional local LiveKit provider-stack validation."
  type        = number
  default     = 48091
}

variable "livekit_rtc_tcp_host_port" {
  description = "Host TCP RTC port for optional local LiveKit provider-stack validation."
  type        = number
  default     = 48092
}

variable "livekit_rtc_udp_host_port" {
  description = "Host UDP RTC port for optional local LiveKit provider-stack validation."
  type        = number
  default     = 48092
}

variable "boards_runtime_enabled" {
  description = "Enable the backend Boards/Tasks workspace facade. Defaults false; live feature-proof runs may set true to validate the guarded active workspace path."
  type        = bool
  default     = false
}

variable "boards_provider" {
  description = "Provider backing the Boards/Tasks workspace facade. Defaults to local-workspace; set openproject only for explicit OpenProject workspace-sync validation."
  type        = string
  default     = "local-workspace"

  validation {
    condition     = contains(["local-workspace", "openproject"], var.boards_provider)
    error_message = "boards_provider must be local-workspace or openproject."
  }
}

variable "boards_openproject_runtime_enabled" {
  description = "Enable the OpenProject provider runtime gate. Defaults false so the core stack remains independent from OpenProject."
  type        = bool
  default     = false
}

variable "boards_openproject_read_sync_enabled" {
  description = "Enable OpenProject workspace synchronization. Defaults false and must not enable direct provider writes."
  type        = bool
  default     = false
}

variable "boards_openproject_context_authorization_enabled" {
  description = "Require Context/Space authorization for OpenProject workspace sync requests. Keep false until backend ReBAC validation is promoted."
  type        = bool
  default     = false
}

variable "boards_openproject_audit_consent_enabled" {
  description = "Enable audit/consent posture for OpenProject provider actions. Required before any future provider writes."
  type        = bool
  default     = false
}

variable "boards_openproject_provider_writes_enabled" {
  description = "Enable writes to OpenProject. Must remain false for the audited provider-runtime path."
  type        = bool
  default     = false
}


variable "boards_nextcloud_deck_runtime_enabled" {
  description = "Enable Nextcloud Deck as an alternative Boards provider. Defaults false; OpenProject remains the primary workspace sync assumption."
  type        = bool
  default     = false
}

variable "boards_openproject_auth_mode" {
  description = "OpenProject backend auth mode. Use service-token for backend-held API-token workspace-sync; disabled keeps runtime fail-closed."
  type        = string
  default     = "disabled"

  validation {
    condition     = contains(["disabled", "service-token"], var.boards_openproject_auth_mode)
    error_message = "boards_openproject_auth_mode must be disabled or service-token."
  }
}

variable "boards_openproject_base_url" {
  description = "Internal or external OpenProject base URL consumed only by the backend adapter. Leave blank to fail closed."
  type        = string
  default     = ""
}

variable "boards_openproject_api_token" {
  description = "Backend-held OpenProject service-account API token for workspace sync. Never expose to Flutter, app config, or support bundles."
  type        = string
  default     = ""
  sensitive   = true
}

variable "context_authorization_tenant_claim" {
  description = "JWT claim used to derive the tenant for Context/Space authorization."
  type        = string
  default     = "weave_tenant_id"
}

variable "context_authorization_tenant_fallback_claim" {
  description = "Fallback JWT claim used to derive the tenant for Context/Space authorization when the primary claim is absent."
  type        = string
  default     = "tenant_id"
}

variable "context_authorization_default_tenant_id" {
  description = "Default local/dev tenant for non-JWT Context/Space authorization. JWT tokens must carry the primary or fallback tenant claim."
  type        = string
  default     = "tenant-default"
}

variable "context_authorization_principal_claim" {
  description = "JWT claim used for Context/Space principal references. Use sub by default; local live E2E may use preferred_username with deterministic seeded users."
  type        = string
  default     = "sub"
}

variable "context_authorization_principal_ref_prefix" {
  description = "Prefix prepended to Context/Space principal claim values."
  type        = string
  default     = "user:"
}

variable "context_authorization_bootstrap_enabled" {
  description = "Seed a deterministic local/dev Context/Space membership for live E2E. Defaults false and must be explicitly enabled by install.sh when TF_VAR_create_test_user=true."
  type        = bool
  default     = false
}

variable "context_authorization_bootstrap_context_id" {
  description = "Context ID granted to the bootstrap principal."
  type        = string
  default     = "workspace-default"
}

variable "context_authorization_bootstrap_principal_ref" {
  description = "Principal reference granted local/dev Context/Space membership."
  type        = string
  default     = "user:test"
}

variable "context_authorization_dogfood_principal_ref" {
  description = "Optional additional local dogfood principal reference granted the same bootstrap Context/Space membership."
  type        = string
  default     = ""
}

variable "context_authorization_bootstrap_role" {
  description = "Context role granted to the bootstrap principal."
  type        = string
  default     = "MEMBER"

  validation {
    condition     = contains(["OWNER", "ADMIN", "MEMBER", "GUEST", "VIEWER"], var.context_authorization_bootstrap_role)
    error_message = "context_authorization_bootstrap_role must be one of OWNER, ADMIN, MEMBER, GUEST, or VIEWER."
  }
}

variable "openproject_image" {
  description = "Optional self-hosted OpenProject image for the off-by-default local/demo compose profile."
  type        = string
  default     = "openproject/openproject:15"
}

variable "openproject_host_port" {
  description = "Optional direct host port for the self-hosted OpenProject profile. Not part of the default Weave product routes."
  type        = number
  default     = 48086
}

variable "openproject_secret_key_base" {
  description = "Secret key base for the optional self-hosted OpenProject profile. Set only in private env files."
  type        = string
  default     = ""
  sensitive   = true
}

variable "postgres_image" {
  description = "PostgreSQL image used for the shared database."
  type        = string
  default     = "postgres:15"
}

variable "keycloak_image" {
  description = "Keycloak image used for identity management."
  type        = string
  default     = "quay.io/keycloak/keycloak:26.0.7"
}

variable "mas_image" {
  description = "Matrix Authentication Service image. The default supports the synapse_modern adapter and localpart conflict mode used by the generated config."
  type        = string
  default     = "ghcr.io/element-hq/matrix-authentication-service:1.15.0"
}

variable "synapse_image" {
  description = "Synapse image. Matrix Authentication Service delegated auth requires Synapse 1.136.0 or later."
  type        = string
  default     = "matrixdotorg/synapse:v1.136.0"
}

variable "nextcloud_image" {
  description = "Nextcloud image."
  type        = string
  default     = "nextcloud:apache"
}

variable "db_name" {
  description = "Base name used to derive per-service PostgreSQL databases inside the shared PostgreSQL instance."
  type        = string
  default     = "weave"
}

variable "db_admin_username" {
  description = "PostgreSQL bootstrap administrator username."
  type        = string
  default     = "weave_admin"
}

variable "db_admin_password" {
  description = "PostgreSQL bootstrap administrator password."
  type        = string
  sensitive   = true
}

variable "backend_db_username" {
  description = "Weave backend PostgreSQL username."
  type        = string
  default     = "weave_backend"
}

variable "backend_db_password" {
  description = "Weave backend PostgreSQL password."
  type        = string
  sensitive   = true
}

variable "keycloak_admin_username" {
  description = "Initial Keycloak admin username."
  type        = string
  default     = "admin"
}

variable "keycloak_admin_password" {
  description = "Initial Keycloak admin password."
  type        = string
  sensitive   = true
}

variable "keycloak_db_username" {
  description = "Keycloak PostgreSQL username."
  type        = string
  default     = "keycloak"
}

variable "keycloak_db_password" {
  description = "Keycloak PostgreSQL password."
  type        = string
  sensitive   = true
}

variable "mas_db_username" {
  description = "MAS PostgreSQL username."
  type        = string
  default     = "mas"
}

variable "mas_db_password" {
  description = "MAS PostgreSQL password."
  type        = string
  sensitive   = true
}

variable "synapse_db_username" {
  description = "Synapse PostgreSQL username."
  type        = string
  default     = "synapse"
}

variable "synapse_db_password" {
  description = "Synapse PostgreSQL password."
  type        = string
  sensitive   = true
}

variable "nextcloud_db_username" {
  description = "Nextcloud PostgreSQL username."
  type        = string
  default     = "nextcloud"
}

variable "nextcloud_db_password" {
  description = "Nextcloud PostgreSQL password."
  type        = string
  sensitive   = true
}

variable "nextcloud_admin_username" {
  description = "Initial Nextcloud admin username."
  type        = string
  default     = "admin"
}

variable "nextcloud_admin_password" {
  description = "Initial Nextcloud admin password."
  type        = string
  sensitive   = true
}

variable "nextcloud_backend_actor_username" {
  description = "Backend-owned local/dev Nextcloud service account username for files and calendar facade adapters."
  type        = string
  default     = "weave-backend"
}

variable "nextcloud_backend_actor_token" {
  description = "Backend-owned local/dev Nextcloud service account password/app token for files and calendar facade adapters."
  type        = string
  sensitive   = true
}

variable "matrix_mas_client_secret" {
  description = "Shared confidential client secret for the matrix-mas Keycloak client."
  type        = string
  sensitive   = true
}

variable "mas_encryption_secret" {
  description = "32-byte hex encoded MAS encryption secret."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[0-9a-fA-F]{64}$", var.mas_encryption_secret))
    error_message = "mas_encryption_secret must be a 64-character hex string."
  }
}

variable "mas_signing_key_pem" {
  description = "PEM-encoded RSA private key used by MAS for signing."
  type        = string
  sensitive   = true
}

variable "mas_matrix_secret" {
  description = "Shared secret between MAS and Synapse."
  type        = string
  sensitive   = true
}

variable "synapse_registration_shared_secret" {
  description = "Synapse registration shared secret."
  type        = string
  sensitive   = true
}

variable "synapse_macaroon_secret_key" {
  description = "Synapse macaroon secret."
  type        = string
  sensitive   = true
}

variable "synapse_form_secret" {
  description = "Synapse form secret."
  type        = string
  sensitive   = true
}
