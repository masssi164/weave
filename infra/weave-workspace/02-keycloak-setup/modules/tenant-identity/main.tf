terraform {
  required_providers {
    keycloak = {
      source = "keycloak/keycloak"
    }
  }
}

locals {
  test_user = {
    username   = "test"
    email      = var.test_user_email
    first_name = "Test"
    last_name  = "User"
    password   = var.test_user_password
  }

  weave_app_optional_scopes = [
    "address",
    "microprofile-jwt",
    "offline_access",
    "organization",
    "phone",
  ]

  weave_app_default_scopes = [
    "acr",
    "basic",
    "email",
    "profile",
    "roles",
    "web-origins",
    "weave:workspace",
  ]

  weave_admin_console_default_scopes = [
    "acr",
    "basic",
    "email",
    "profile",
    "web-origins",
  ]

  weave_product_roles = {
    owner  = "Full local/dev workspace ownership for bootstrap operators."
    admin  = "Workspace administration without owner bootstrap authority."
    member = "Standard authenticated workspace member."
    guest  = "Constrained guest identity for feature-flagged guest portal flows."
  }

  weave_product_role_groups = {
    owner  = "workspace-owners"
    admin  = "workspace-admins"
    member = "workspace-members"
    guest  = "workspace-guests"
  }

  weave_offline_session_product_roles = toset([
    "owner",
    "admin",
    "member",
  ])

  weave_capability_groups = {
    board_editors    = "weave-board-editors"
    calendar_editors = "weave-calendar-editors"
    document_editors = "weave-document-editors"
    meeting_hosts    = "weave-meeting-hosts"
    decision_records = "weave-decision-recorders"
    weaver_runtime   = "weave-weaver-runtime"
  }

  live_e2e_test_user_capability_groups = [
    "board_editors",
    "calendar_editors",
    "weaver_runtime",
  ]

  client_defaults = {
    enabled                                        = true
    standard_flow_enabled                          = false
    implicit_flow_enabled                          = false
    direct_access_grants_enabled                   = false
    valid_redirect_uris                            = []
    valid_post_logout_redirect_uris                = []
    web_origins                                    = []
    pkce_code_challenge_method                     = null
    client_secret                                  = null
    client_secret_regenerate_when_changed          = null
    backchannel_logout_url                         = null
    backchannel_logout_session_required            = null
    service_accounts_enabled                       = false
    standard_token_exchange_enabled                = null
    full_scope_allowed                             = null
    access_token_lifespan                          = null
    allow_refresh_token_in_standard_token_exchange = null
    use_refresh_tokens                             = null
    use_refresh_tokens_client_credentials          = null
    extra_config                                   = {}
  }

  client_specs = {
    weave_app = merge(local.client_defaults, {
      name                         = "weave-app"
      client_id                    = "weave-app"
      access_type                  = "PUBLIC"
      standard_flow_enabled        = true
      direct_access_grants_enabled = var.create_test_user
      pkce_code_challenge_method   = "S256"
      valid_redirect_uris          = ["com.massimotter.weave:/oauthredirect"]
      valid_post_logout_redirect_uris = [
        "com.massimotter.weave:/logout",
      ]
    })
    weave_backend = merge(local.client_defaults, {
      name        = "weave-backend"
      client_id   = "${var.api_public_url}/api"
      access_type = "BEARER-ONLY"
    })
    weave_identity_admin = merge(local.client_defaults, {
      name                                  = "weave-identity-admin"
      client_id                             = "weave-identity-admin"
      access_type                           = "CONFIDENTIAL"
      service_accounts_enabled              = true
      client_secret_regenerate_when_changed = { rotation_epoch = var.admin_client_secret_rotation_epoch }
    })
    weave_agent_runtime_admin = merge(local.client_defaults, {
      name                                  = "weave-agent-runtime-admin"
      client_id                             = "weave-agent-runtime-admin"
      access_type                           = "CONFIDENTIAL"
      service_accounts_enabled              = true
      client_secret_regenerate_when_changed = { rotation_epoch = var.admin_client_secret_rotation_epoch }
    })
    weave_mcp_server = merge(local.client_defaults, {
      name                                           = "weave-mcp-server"
      client_id                                      = "weave-mcp-server"
      access_type                                    = "CONFIDENTIAL"
      client_secret                                  = var.weave_mcp_client_secret
      service_accounts_enabled                       = true
      standard_token_exchange_enabled                = true
      full_scope_allowed                             = false
      access_token_lifespan                          = 60
      allow_refresh_token_in_standard_token_exchange = false
      use_refresh_tokens                             = false
      use_refresh_tokens_client_credentials          = false
      extra_config = {
        "weave.mcp.exchange-requester"     = "true"
        "access.token.header.type.rfc9068" = "true"
      }
    })
    weave_admin_console = merge(local.client_defaults, {
      name                         = "weave-admin-console"
      client_id                    = "weave-admin-console"
      access_type                  = "PUBLIC"
      standard_flow_enabled        = true
      direct_access_grants_enabled = var.create_test_user
      pkce_code_challenge_method   = "S256"
      valid_redirect_uris = [
        "${var.admin_console_public_url}/*",
        "http://localhost:5173/*",
      ]
      valid_post_logout_redirect_uris = [
        "${var.admin_console_public_url}/*",
        "http://localhost:5173/*",
      ]
      web_origins = [
        var.admin_console_public_url,
        "http://localhost:5173",
      ]
    })
    matrix_mas = merge(local.client_defaults, {
      name                  = "matrix-mas"
      client_id             = "matrix-mas"
      access_type           = "CONFIDENTIAL"
      standard_flow_enabled = true
      client_secret         = var.matrix_mas_client_secret
      valid_redirect_uris = [
        "${var.mas_public_url}/upstream/callback/${var.matrix_mas_upstream_id}",
      ]
      web_origins = ["+"]
    })
    nextcloud = merge(local.client_defaults, {
      name                                = "nextcloud"
      client_id                           = "nextcloud"
      access_type                         = "CONFIDENTIAL"
      standard_flow_enabled               = true
      valid_redirect_uris                 = ["${var.nextcloud_public_url}/*"]
      valid_post_logout_redirect_uris     = ["${var.nextcloud_public_url}/*"]
      backchannel_logout_url              = "${var.nextcloud_public_url}/index.php/apps/user_oidc/backchannel-logout/keycloak"
      backchannel_logout_session_required = true
      web_origins                         = ["+"]
    })
  }
}

resource "keycloak_realm" "tenant" {
  realm                          = var.tenant_slug
  enabled                        = true
  registration_allowed           = false
  login_with_email_allowed       = true
  registration_email_as_username = false
  edit_username_allowed          = false
  reset_password_allowed         = true
  duplicate_emails_allowed       = false
  organizations_enabled          = true
  login_theme                    = "weave"
  email_theme                    = "weave"

  smtp_server {
    host              = var.smtp_host
    port              = var.smtp_port
    from              = var.smtp_from
    from_display_name = var.smtp_from_display_name
    ssl               = var.smtp_ssl
    starttls          = var.smtp_starttls
    dynamic "auth" {
      for_each = var.smtp_username != "" ? [1] : []

      content {
        username = var.smtp_username
        password = var.smtp_password
      }
    }
  }
}

resource "keycloak_realm_events" "identity_bridge" {
  realm_id             = keycloak_realm.tenant.id
  events_enabled       = true
  admin_events_enabled = true
  events_listeners     = ["jboss-logging", "weave-identity-events"]
}

resource "keycloak_organization" "tenant" {
  realm        = keycloak_realm.tenant.realm
  name         = var.tenant_slug
  alias        = var.tenant_slug
  description  = "${var.organization_display_name} organization whose identity lifecycle is managed by Keycloak."
  redirect_url = "${var.product_public_url}/join"
}

resource "keycloak_required_action" "passwordless_passkey" {
  realm_id       = keycloak_realm.tenant.realm
  alias          = "webauthn-register-passwordless"
  name           = "Register a passkey"
  enabled        = true
  default_action = false
}

resource "keycloak_user" "test" {
  count = var.create_test_user ? 1 : 0

  realm_id       = keycloak_realm.tenant.id
  username       = local.test_user.username
  enabled        = true
  email          = local.test_user.email
  first_name     = local.test_user.first_name
  last_name      = local.test_user.last_name
  email_verified = true

  attributes = {
    weave_tenant_id = var.context_authorization_default_tenant_id
  }

  initial_password {
    value     = local.test_user.password
    temporary = false
  }
}

resource "keycloak_role" "weave_product" {
  for_each = local.weave_product_roles

  realm_id    = keycloak_realm.tenant.id
  client_id   = keycloak_openid_client.client["weave_app"].id
  name        = each.key
  description = each.value
}

resource "keycloak_role" "weaver_runtime" {
  realm_id    = keycloak_realm.tenant.id
  name        = "weaver-runtime"
  description = "Machine-only role assigned exactly to one managed Weaver cell service account."
}

data "keycloak_role" "offline_access" {
  realm_id = keycloak_realm.tenant.id
  name     = "offline_access"
}

resource "keycloak_group" "weave_product_role" {
  for_each = local.weave_product_role_groups

  realm_id = keycloak_realm.tenant.id
  name     = each.value
}

resource "keycloak_group" "weave_capability" {
  for_each = local.weave_capability_groups

  realm_id = keycloak_realm.tenant.id
  name     = each.value
}

resource "keycloak_group_roles" "weave_product_role" {
  for_each = local.weave_product_role_groups

  realm_id = keycloak_realm.tenant.id
  group_id = keycloak_group.weave_product_role[each.key].id
  role_ids = concat(
    [keycloak_role.weave_product[each.key].id],
    contains(local.weave_offline_session_product_roles, each.key) ? [data.keycloak_role.offline_access.id] : [],
  )
}

resource "keycloak_user_roles" "test_member" {
  count = var.create_test_user ? 1 : 0

  realm_id = keycloak_realm.tenant.id
  user_id  = keycloak_user.test[0].id
  role_ids = [keycloak_role.weave_product["member"].id]
}

resource "keycloak_user_groups" "test_member" {
  count = var.create_test_user ? 1 : 0

  realm_id = keycloak_realm.tenant.id
  user_id  = keycloak_user.test[0].id
  group_ids = concat(
    [keycloak_group.weave_product_role["member"].id],
    [for group_key in local.live_e2e_test_user_capability_groups : keycloak_group.weave_capability[group_key].id],
  )
}

resource "keycloak_openid_client" "client" {
  for_each = local.client_specs

  realm_id  = keycloak_realm.tenant.id
  client_id = each.value.client_id
  name      = each.value.name

  access_type                                    = each.value.access_type
  enabled                                        = each.value.enabled
  standard_flow_enabled                          = each.value.standard_flow_enabled
  implicit_flow_enabled                          = each.value.implicit_flow_enabled
  direct_access_grants_enabled                   = each.value.direct_access_grants_enabled
  pkce_code_challenge_method                     = each.value.pkce_code_challenge_method
  client_secret                                  = each.value.client_secret
  client_secret_regenerate_when_changed          = each.value.client_secret_regenerate_when_changed
  valid_redirect_uris                            = each.value.valid_redirect_uris
  valid_post_logout_redirect_uris                = each.value.valid_post_logout_redirect_uris
  web_origins                                    = each.value.web_origins
  backchannel_logout_url                         = each.value.backchannel_logout_url
  backchannel_logout_session_required            = each.value.backchannel_logout_session_required
  service_accounts_enabled                       = each.value.service_accounts_enabled
  standard_token_exchange_enabled                = each.value.standard_token_exchange_enabled
  full_scope_allowed                             = each.value.full_scope_allowed
  access_token_lifespan                          = each.value.access_token_lifespan
  allow_refresh_token_in_standard_token_exchange = each.value.allow_refresh_token_in_standard_token_exchange
  use_refresh_tokens                             = each.value.use_refresh_tokens
  use_refresh_tokens_client_credentials          = each.value.use_refresh_tokens_client_credentials
  extra_config                                   = each.value.extra_config
}

data "keycloak_openid_client" "realm_management" {
  realm_id  = keycloak_realm.tenant.id
  client_id = "realm-management"
}

resource "keycloak_openid_client_service_account_role" "identity_admin" {
  for_each = toset([
    "manage-users",
    "manage-organizations",
    "query-organizations",
    "view-organizations",
    "view-users",
    "query-users",
  ])

  realm_id                = keycloak_realm.tenant.id
  service_account_user_id = keycloak_openid_client.client["weave_identity_admin"].service_account_user_id
  client_id               = data.keycloak_openid_client.realm_management.id
  role                    = each.value
}

resource "keycloak_openid_client_service_account_role" "agent_runtime_admin" {
  for_each = toset([
    "manage-clients",
    "manage-users",
    "query-clients",
    "query-users",
    "view-clients",
    "view-realm",
    "view-users",
  ])

  realm_id                = keycloak_realm.tenant.id
  service_account_user_id = keycloak_openid_client.client["weave_agent_runtime_admin"].service_account_user_id
  client_id               = data.keycloak_openid_client.realm_management.id
  role                    = each.value
}

resource "keycloak_openid_client_scope" "weave_workspace" {
  realm_id               = keycloak_realm.tenant.id
  name                   = "weave:workspace"
  description            = "Grants Weave mobile clients access to workspace APIs."
  include_in_token_scope = true
}

resource "keycloak_openid_client_scope" "agent_runtime_profile_read" {
  realm_id               = keycloak_realm.tenant.id
  name                   = "agent-runtime.profile.read"
  description            = "Machine-only scope for one cell to fetch its current signed RuntimeProfile."
  include_in_token_scope = true
}

resource "keycloak_openid_client_scope" "weaver_runtime_workload" {
  realm_id               = keycloak_realm.tenant.id
  name                   = "weaver-runtime.workload"
  description            = "Fixed role-scope boundary attached by ARC to managed per-cell workload clients."
  include_in_token_scope = false
}

resource "keycloak_generic_role_mapper" "weaver_runtime_workload" {
  realm_id        = keycloak_realm.tenant.id
  client_scope_id = keycloak_openid_client_scope.weaver_runtime_workload.id
  role_id         = keycloak_role.weaver_runtime.id
}

resource "keycloak_openid_client_scope" "agent_runtime_admin" {
  realm_id               = keycloak_realm.tenant.id
  name                   = "agent-runtime.admin"
  description            = "Interactive owner/admin authority for the Agent Runtime lifecycle control plane."
  include_in_token_scope = true
}

resource "keycloak_openid_client_scope" "mcp_tools" {
  realm_id               = keycloak_realm.tenant.id
  name                   = "mcp:tools"
  description            = "Machine-only scope for an active bound Weaver cell to reach the exact MCP resource."
  include_in_token_scope = true
}

resource "keycloak_openid_client_scope" "calendar_read" {
  realm_id               = keycloak_realm.tenant.id
  name                   = "calendar.read"
  description            = "Machine-only, downscopable Calendar read authority for a currently bound Weaver cell."
  include_in_token_scope = true
}

resource "keycloak_openid_client_scope" "mcp_backend_exchange" {
  realm_id               = keycloak_realm.tenant.id
  name                   = "weave-mcp-backend.exchange"
  description            = "Internal default scope that makes only the Weave API audience available to MCP token exchange."
  include_in_token_scope = false
}

resource "keycloak_openid_hardcoded_claim_protocol_mapper" "weave_tenant_id" {
  realm_id            = keycloak_realm.tenant.id
  client_scope_id     = keycloak_openid_client_scope.weave_workspace.id
  name                = "weave-tenant-id"
  claim_name          = "weave_tenant_id"
  claim_value         = var.context_authorization_default_tenant_id
  claim_value_type    = "String"
  add_to_id_token     = false
  add_to_access_token = true
  add_to_userinfo     = true
}

resource "keycloak_openid_hardcoded_claim_protocol_mapper" "weave_organization_name" {
  realm_id            = keycloak_realm.tenant.id
  client_scope_id     = keycloak_openid_client_scope.weave_workspace.id
  name                = "weave-organization-name"
  claim_name          = "weave_organization_name"
  claim_value         = var.organization_display_name
  claim_value_type    = "String"
  add_to_id_token     = false
  add_to_access_token = true
  add_to_userinfo     = true
}

resource "keycloak_openid_audience_protocol_mapper" "weave_backend_audience" {
  realm_id                 = keycloak_realm.tenant.id
  client_scope_id          = keycloak_openid_client_scope.weave_workspace.id
  name                     = "weave-backend-audience"
  included_client_audience = keycloak_openid_client.client["weave_backend"].client_id
  add_to_id_token          = false
  add_to_access_token      = true
}

resource "keycloak_openid_audience_protocol_mapper" "agent_runtime_profile_read_audience" {
  realm_id                 = keycloak_realm.tenant.id
  client_scope_id          = keycloak_openid_client_scope.agent_runtime_profile_read.id
  name                     = "agent-runtime-control-resource"
  included_custom_audience = "${var.api_public_url}/api/v1/agent-runtime"
  add_to_id_token          = false
  add_to_access_token      = true
}

resource "keycloak_openid_hardcoded_claim_protocol_mapper" "agent_runtime_admin_tenant" {
  realm_id            = keycloak_realm.tenant.id
  client_scope_id     = keycloak_openid_client_scope.agent_runtime_admin.id
  name                = "agent-runtime-admin-tenant"
  claim_name          = "weave_tenant_id"
  claim_value         = var.context_authorization_default_tenant_id
  claim_value_type    = "String"
  add_to_id_token     = false
  add_to_access_token = true
  add_to_userinfo     = false
}

resource "keycloak_openid_audience_protocol_mapper" "agent_runtime_admin_audience" {
  realm_id                 = keycloak_realm.tenant.id
  client_scope_id          = keycloak_openid_client_scope.agent_runtime_admin.id
  name                     = "weave-agent-runtime-admin-api-resource"
  included_client_audience = keycloak_openid_client.client["weave_backend"].client_id
  add_to_id_token          = false
  add_to_access_token      = true
}

resource "keycloak_openid_audience_protocol_mapper" "mcp_tools_audience" {
  realm_id                 = keycloak_realm.tenant.id
  client_scope_id          = keycloak_openid_client_scope.mcp_tools.id
  name                     = "weave-mcp-resource"
  included_custom_audience = "${var.api_public_url}/mcp"
  add_to_id_token          = false
  add_to_access_token      = true
}

resource "keycloak_openid_audience_protocol_mapper" "mcp_exchange_requester_audience" {
  realm_id                 = keycloak_realm.tenant.id
  client_scope_id          = keycloak_openid_client_scope.mcp_tools.id
  name                     = "weave-mcp-exchange-requester"
  included_client_audience = keycloak_openid_client.client["weave_mcp_server"].client_id
  add_to_id_token          = false
  add_to_access_token      = true
}

resource "keycloak_openid_audience_protocol_mapper" "mcp_backend_resource_audience" {
  realm_id                 = keycloak_realm.tenant.id
  client_scope_id          = keycloak_openid_client_scope.mcp_backend_exchange.id
  name                     = "weave-api-resource"
  included_client_audience = keycloak_openid_client.client["weave_backend"].client_id
  add_to_id_token          = false
  add_to_access_token      = true
}

resource "keycloak_realm_client_policy_profile" "token_exchange_downscope" {
  realm_id    = keycloak_realm.tenant.id
  name        = "weave-token-exchange-downscope"
  description = "Reject token exchange scopes that were not present in the subject token."

  executor {
    name = "downscope-assertion-grant-enforcer"
  }
}

resource "keycloak_realm_client_policy_profile_policy" "token_exchange_downscope" {
  realm_id    = keycloak_realm.tenant.id
  name        = "weave-token-exchange-downscope"
  description = "Apply strict downscoping to every Standard Token Exchange V2 request in this realm."
  enabled     = true
  profiles    = [keycloak_realm_client_policy_profile.token_exchange_downscope.name]

  condition {
    name = "grant-type"
    configuration = {
      grant_types = jsonencode(["urn:ietf:params:oauth:grant-type:token-exchange"])
    }
  }
}

resource "keycloak_openid_audience_protocol_mapper" "nextcloud_bearer_audience" {
  realm_id                 = keycloak_realm.tenant.id
  client_scope_id          = keycloak_openid_client_scope.weave_workspace.id
  name                     = "nextcloud-bearer-audience"
  included_client_audience = keycloak_openid_client.client["nextcloud"].client_id
  add_to_id_token          = true
  add_to_access_token      = true
}

resource "keycloak_openid_client_optional_scopes" "weave_app" {
  realm_id  = keycloak_realm.tenant.id
  client_id = keycloak_openid_client.client["weave_app"].id

  optional_scopes = local.weave_app_optional_scopes

  depends_on = [
    keycloak_openid_client_scope.weave_workspace,
  ]
}

resource "keycloak_openid_client_default_scopes" "weave_app" {
  realm_id  = keycloak_realm.tenant.id
  client_id = keycloak_openid_client.client["weave_app"].id

  default_scopes = local.weave_app_default_scopes

  depends_on = [
    keycloak_openid_client_scope.weave_workspace,
  ]
}

resource "keycloak_openid_client_default_scopes" "weave_admin_console" {
  realm_id  = keycloak_realm.tenant.id
  client_id = keycloak_openid_client.client["weave_admin_console"].id

  default_scopes = local.weave_admin_console_default_scopes
}

resource "keycloak_openid_client_optional_scopes" "weave_admin_console" {
  realm_id  = keycloak_realm.tenant.id
  client_id = keycloak_openid_client.client["weave_admin_console"].id

  optional_scopes = [keycloak_openid_client_scope.agent_runtime_admin.name]

  depends_on = [
    keycloak_openid_client_scope.agent_runtime_admin,
  ]
}

resource "keycloak_openid_client_default_scopes" "weave_mcp_server" {
  realm_id  = keycloak_realm.tenant.id
  client_id = keycloak_openid_client.client["weave_mcp_server"].id

  default_scopes = [keycloak_openid_client_scope.mcp_backend_exchange.name]

  depends_on = [
    keycloak_openid_client_scope.mcp_backend_exchange,
  ]
}

resource "keycloak_openid_client_optional_scopes" "weave_mcp_server" {
  realm_id  = keycloak_realm.tenant.id
  client_id = keycloak_openid_client.client["weave_mcp_server"].id

  optional_scopes = [keycloak_openid_client_scope.calendar_read.name]

  depends_on = [
    keycloak_openid_client_scope.calendar_read,
  ]
}

resource "keycloak_openid_group_membership_protocol_mapper" "weave_app_groups" {
  realm_id            = keycloak_realm.tenant.id
  client_id           = keycloak_openid_client.client["weave_app"].id
  name                = "groups"
  claim_name          = "groups"
  full_path           = false
  add_to_id_token     = true
  add_to_access_token = true
  add_to_userinfo     = true
}

resource "keycloak_openid_group_membership_protocol_mapper" "weave_admin_console_groups" {
  realm_id            = keycloak_realm.tenant.id
  client_id           = keycloak_openid_client.client["weave_admin_console"].id
  name                = "groups"
  claim_name          = "groups"
  full_path           = false
  add_to_id_token     = true
  add_to_access_token = true
  add_to_userinfo     = true
}

resource "keycloak_openid_group_membership_protocol_mapper" "nextcloud_groups" {
  realm_id            = keycloak_realm.tenant.id
  client_id           = keycloak_openid_client.client["nextcloud"].id
  name                = "groups"
  claim_name          = "groups"
  full_path           = false
  add_to_id_token     = true
  add_to_access_token = true
  add_to_userinfo     = true
}
