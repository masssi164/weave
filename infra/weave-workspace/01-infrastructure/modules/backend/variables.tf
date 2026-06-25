variable "network_name" {
  description = "Docker network name for the Weave backend."
  type        = string
}

variable "container_name" {
  description = "Container name for the Weave backend."
  type        = string
}

variable "image_name" {
  description = "Weave backend image reference."
  type        = string
}

variable "host_port" {
  description = "Direct host port exposed by the Weave backend."
  type        = number
}

variable "container_port" {
  description = "Internal HTTP port exposed by the Weave backend container."
  type        = number
}

variable "public_host" {
  description = "Browser-facing hostname for the Weave backend API."
  type        = string
}

variable "public_base_url" {
  description = "Public Weave product base URL."
  type        = string
}

variable "api_origin" {
  description = "Public Weave backend API origin."
  type        = string
}

variable "api_base_url" {
  description = "Public Weave backend API base URL."
  type        = string
}

variable "auth_base_url" {
  description = "Public Keycloak/Auth base URL."
  type        = string
}

variable "matrix_base_url" {
  description = "Public Matrix base URL."
  type        = string
}

variable "files_product_url" {
  description = "Public Weave files product route."
  type        = string
}

variable "calendar_product_url" {
  description = "Public Weave calendar product route."
  type        = string
}

variable "nextcloud_base_url" {
  description = "Internal Nextcloud base URL consumed by backend adapters."
  type        = string
}

variable "nextcloud_public_base_url" {
  description = "Support-safe public Nextcloud/protocol fallback URL exposed in platform config."
  type        = string
}

variable "nextcloud_files_actor_model" {
  description = "Backend-to-Nextcloud actor model used by the files facade."
  type        = string
}

variable "nextcloud_files_actor_username" {
  description = "Backend-owned Nextcloud actor username used by the files facade."
  type        = string
}

variable "nextcloud_files_actor_token" {
  description = "Backend-owned Nextcloud actor app password/token used by the files facade."
  type        = string
  sensitive   = true
}

variable "nextcloud_files_webdav_root_path" {
  description = "Nextcloud WebDAV files root path consumed by the backend files facade."
  type        = string
}

variable "caldav_base_url" {
  description = "Canonical Nextcloud base URL consumed by the backend CalDAV adapter."
  type        = string
}

variable "caldav_calendar_path_template" {
  description = "CalDAV calendar collection path template consumed by the backend calendar facade."
  type        = string
}

variable "caldav_auth_mode" {
  description = "Backend CalDAV actor authentication mode."
  type        = string
}

variable "caldav_backend_username" {
  description = "Backend-owned Nextcloud actor username used by the CalDAV adapter."
  type        = string
}

variable "caldav_backend_token" {
  description = "Backend-owned Nextcloud actor app password/token used by the CalDAV adapter."
  type        = string
  sensitive   = true
}

variable "caldav_request_timeout_seconds" {
  description = "Request timeout in seconds for backend CalDAV calls."
  type        = number
}

variable "caldav_external_discovery_url" {
  description = "Secret-free public CalDAV discovery URL that backend metadata may expose to native clients."
  type        = string
}

variable "caldav_external_credential_mode" {
  description = "Supported credential model for external CalDAV clients. Must not imply backend actor credential reuse."
  type        = string
}

variable "caldav_external_profile_password_mode" {
  description = "Password handling policy for generated external CalDAV setup profiles."
  type        = string
}

variable "caldav_external_private_user_calendars" {
  description = "Feature flag for private personal calendar exposure through external CalDAV metadata."
  type        = string
}


variable "context_authorization_tenant_claim" {
  description = "JWT claim used to derive the tenant for Context/Space authorization. Defaults to weave_tenant_id."
  type        = string
}

variable "context_authorization_tenant_fallback_claim" {
  description = "Fallback JWT tenant claim used for Context/Space authorization when the primary tenant claim is absent."
  type        = string
}

variable "context_authorization_default_tenant_id" {
  description = "Tenant used for non-JWT local/dev Context/Space authorization. JWT tokens must carry the primary or fallback tenant claim."
  type        = string
}

variable "context_authorization_principal_claim" {
  description = "JWT claim used to derive the principal reference for Context/Space authorization. Production should prefer sub; local live E2E may use preferred_username with deterministic seeded users."
  type        = string
}

variable "context_authorization_principal_ref_prefix" {
  description = "Prefix prepended to principal claim values before Context/Space authorization checks."
  type        = string
}

variable "context_authorization_bootstrap_enabled" {
  description = "Enable deterministic local/dev Context/Space bootstrap membership. Keep false unless the stack intentionally seeds the matching identity."
  type        = bool
}

variable "context_authorization_bootstrap_context_id" {
  description = "Context ID granted to the bootstrap principal for local/dev live E2E."
  type        = string
}

variable "context_authorization_bootstrap_principal_ref" {
  description = "Principal reference granted local/dev Context/Space membership."
  type        = string
}

variable "context_authorization_bootstrap_role" {
  description = "Context role granted to the bootstrap principal."
  type        = string
}

variable "interop_enabled" {
  description = "Enable backend interop gateway runtime. Defaults false for connector preview guardrails."
  type        = bool
}

variable "interop_slack_enabled" {
  description = "Enable Slack-specific interop runtime. Defaults false; infra must not expose Slack callbacks unless explicitly reviewed."
  type        = bool
}

variable "interop_teams_enabled" {
  description = "Enable Teams-specific interop runtime. Defaults false until Slack hardening proves the connector boundary."
  type        = bool
}

variable "connectors_public_sdk_enabled" {
  description = "Enable public connector SDK behavior. Defaults false; the public SDK is deferred until real connectors prove the boundary."
  type        = bool
}

variable "boards_runtime_enabled" {
  description = "Enable the backend Boards/Tasks workspace facade. Keep false by default; live E2E may enable it explicitly to prove the guarded workspace path."
  type        = bool
}

variable "boards_provider" {
  description = "Provider backing the Boards/Tasks workspace facade. Keep local-workspace unless OpenProject workspace sync is explicitly enabled."
  type        = string
}

variable "boards_openproject_runtime_enabled" {
  description = "Enable the OpenProject provider runtime gate. Defaults false; must be true with read-sync for the first real provider-backed Boards path."
  type        = bool
}

variable "boards_openproject_read_sync_enabled" {
  description = "Enable OpenProject workspace synchronization. Defaults false and never implies direct provider writes."
  type        = bool
}

variable "boards_openproject_context_authorization_enabled" {
  description = "Require Context/Space authorization for OpenProject workspace sync requests. Keep false until the backend ReBAC seam is fully validated."
  type        = bool
}

variable "boards_openproject_audit_consent_enabled" {
  description = "Enable audit/consent posture for OpenProject provider actions. Required before future provider writes."
  type        = bool
}

variable "boards_openproject_provider_writes_enabled" {
  description = "Enable writes to OpenProject. Must remain false for the audited provider-runtime path."
  type        = bool
}

variable "boards_openproject_auth_mode" {
  description = "OpenProject backend auth mode. Use service-token for backend-held API-token workspace-sync; disabled keeps runtime fail-closed."
  type        = string
}

variable "boards_openproject_base_url" {
  description = "Internal or external OpenProject base URL consumed only by the backend adapter. Leave blank to fail closed."
  type        = string
}

variable "boards_openproject_api_token" {
  description = "Backend-held OpenProject service-account API token for workspace sync. Never expose to Flutter, app config, or support bundles."
  type        = string
  sensitive   = true
}

variable "provider_selections_source" {
  description = "Generated support-safe provider selection seed copied into the backend container."
  type        = string
}

variable "provider_selections_source_hash" {
  description = "Content hash for the generated support-safe provider selection seed."
  type        = string
}

variable "provider_selections_storage_path" {
  description = "Container path used by the backend provider selection repository."
  type        = string
}

variable "provider_stack_profile" {
  description = "Provider-stack runtime posture profile. The default fail-closed profile advertises provider seams without enabling provider runtimes or secrets."
  type        = string
}

variable "provider_stack_readiness" {
  description = "Support-safe aggregate provider-stack readiness label exported to the backend."
  type        = string
}

variable "devops_primary_provider" {
  description = "Primary DevOps provider candidate exported to the backend. GitLab CE/FOSS is the default primary path."
  type        = string
}

variable "devops_alternative_provider" {
  description = "Alternative DevOps provider candidate exported to the backend. Forgejo remains first-class but disabled by default."
  type        = string
}

variable "devops_gitlab_runtime_enabled" {
  description = "Enable GitLab CE/FOSS DevOps provider runtime. Defaults false/fail-closed."
  type        = bool
}

variable "devops_gitlab_base_url" {
  description = "Backend-only GitLab CE/FOSS base URL. Leave blank unless the GitLab provider runtime is intentionally enabled."
  type        = string
}

variable "devops_gitlab_api_token" {
  description = "Backend-held GitLab service token. Never expose to Flutter, app config, or support bundles."
  type        = string
  sensitive   = true
}

variable "devops_gitlab_writes_enabled" {
  description = "Enable GitLab provider writes. Defaults false; current DevOps facade is read-only."
  type        = bool
}

variable "devops_forgejo_runtime_enabled" {
  description = "Enable Forgejo DevOps provider runtime. Defaults false/fail-closed."
  type        = bool
}

variable "devops_forgejo_base_url" {
  description = "Backend-only Forgejo base URL. Leave blank unless the Forgejo provider runtime is intentionally enabled."
  type        = string
}

variable "devops_forgejo_api_token" {
  description = "Backend-held Forgejo service token. Never expose to Flutter, app config, or support bundles."
  type        = string
  sensitive   = true
}

variable "devops_forgejo_writes_enabled" {
  description = "Enable Forgejo provider writes. Defaults false; current DevOps facade is read-only."
  type        = bool
}

variable "office_primary_provider" {
  description = "Primary Office provider candidate exported to the backend. ONLYOFFICE Docs Community is the default candidate."
  type        = string
}

variable "office_onlyoffice_runtime_enabled" {
  description = "Enable ONLYOFFICE runtime/session bridge. Defaults false/fail-closed."
  type        = bool
}

variable "office_onlyoffice_document_server_url" {
  description = "Backend-only ONLYOFFICE Document Server URL. Leave blank unless the Office runtime is intentionally enabled."
  type        = string
}

variable "office_onlyoffice_jwt_secret" {
  description = "Backend-held ONLYOFFICE JWT secret. Never expose to Flutter, app config, or support bundles."
  type        = string
  sensitive   = true
}

variable "office_nextcloud_integration_mode" {
  description = "Office integration path exported to backend/operator tooling. Defaults to Nextcloud ONLYOFFICE app behind backend facade."
  type        = string
}

variable "office_collabora_runtime_enabled" {
  description = "Enable Collabora/CODE runtime. Defaults false; non-default candidate with licensing/runtime fit risk."
  type        = bool
}

variable "groupware_contacts_runtime_enabled" {
  description = "Enable Nextcloud Contacts provider runtime. Defaults false until backend PR #104 is merged and validated."
  type        = bool
}

variable "groupware_forms_runtime_enabled" {
  description = "Enable Nextcloud Forms provider runtime. Defaults false until backend PR #104 is merged and validated."
  type        = bool
}


variable "livekit_runtime_enabled" {
  description = "Enable LiveKit meetings runtime. Defaults false/fail-closed."
  type        = bool
}

variable "livekit_url" {
  description = "Backend-only LiveKit server URL."
  type        = string
}

variable "livekit_api_key" {
  description = "Backend-held LiveKit API key. Never expose to Flutter, app config, or support bundles."
  type        = string
  sensitive   = true
}

variable "livekit_api_secret" {
  description = "Backend-held LiveKit API secret. Never expose to Flutter, app config, or support bundles."
  type        = string
  sensitive   = true
}

variable "livekit_token_endpoint" {
  description = "Optional backend/internal LiveKit token endpoint alternative."
  type        = string
}

variable "boards_nextcloud_deck_runtime_enabled" {
  description = "Enable Nextcloud Deck as an alternative Boards provider. Defaults false; OpenProject remains the primary workspace-sync path."
  type        = bool
}

variable "oidc_issuer_uri" {
  description = "OIDC issuer URI consumed by the Weave backend."
  type        = string
}

variable "oidc_jwk_set_uri" {
  description = "OIDC JWKS URI consumed by the Weave backend."
  type        = string
}

variable "oidc_required_audience" {
  description = "Required OIDC audience value enforced by the Weave backend."
  type        = string
}

variable "client_id" {
  description = "Expected authorized-party client ID enforced by the Weave backend."
  type        = string
}

variable "healthcheck_path" {
  description = "HTTP path used by Docker to check backend health."
  type        = string
}
