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
    email      = "test@weave.test"
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
    weaver_pilot     = "weave-weaver-pilot"
    weaver_runtime   = "weave-weaver-runtime"
    weaver_group     = "weaver-group"
  }

  live_e2e_test_user_capability_groups = [
    "board_editors",
    "calendar_editors",
    "weaver_pilot",
    "weaver_runtime",
    "weaver_group",
  ]

  client_defaults = {
    enabled                             = true
    standard_flow_enabled               = false
    implicit_flow_enabled               = false
    direct_access_grants_enabled        = false
    valid_redirect_uris                 = []
    valid_post_logout_redirect_uris     = []
    web_origins                         = []
    pkce_code_challenge_method          = null
    client_secret                       = null
    backchannel_logout_url              = null
    backchannel_logout_session_required = null
    service_accounts_enabled            = false
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
      client_id   = "weave-backend"
      access_type = "BEARER-ONLY"
    })
    weave_identity_admin = merge(local.client_defaults, {
      name                     = "weave-identity-admin"
      client_id                = "weave-identity-admin"
      access_type              = "CONFIDENTIAL"
      client_secret            = var.identity_admin_client_secret
      service_accounts_enabled = true
    })
    weave_admin_console = merge(local.client_defaults, {
      name                       = "weave-admin-console"
      client_id                  = "weave-admin-console"
      access_type                = "PUBLIC"
      standard_flow_enabled      = true
      pkce_code_challenge_method = "S256"
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

  smtp_server {
    host              = var.smtp_host
    port              = var.smtp_port
    from              = var.smtp_from
    from_display_name = "Weave Dogfood"
    ssl               = false
    starttls          = false
  }
}

resource "keycloak_organization" "tenant" {
  realm        = keycloak_realm.tenant.realm
  name         = var.tenant_slug
  alias        = var.tenant_slug
  description  = "Weave organization whose identity lifecycle is managed by Keycloak."
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

  access_type                         = each.value.access_type
  enabled                             = each.value.enabled
  standard_flow_enabled               = each.value.standard_flow_enabled
  implicit_flow_enabled               = each.value.implicit_flow_enabled
  direct_access_grants_enabled        = each.value.direct_access_grants_enabled
  pkce_code_challenge_method          = each.value.pkce_code_challenge_method
  client_secret                       = each.value.client_secret
  valid_redirect_uris                 = each.value.valid_redirect_uris
  valid_post_logout_redirect_uris     = each.value.valid_post_logout_redirect_uris
  web_origins                         = each.value.web_origins
  backchannel_logout_url              = each.value.backchannel_logout_url
  backchannel_logout_session_required = each.value.backchannel_logout_session_required
  service_accounts_enabled            = each.value.service_accounts_enabled
}

data "keycloak_openid_client" "realm_management" {
  realm_id  = keycloak_realm.tenant.id
  client_id = "realm-management"
}

resource "keycloak_openid_client_service_account_role" "identity_admin" {
  for_each = toset([
    "manage-organizations",
    "query-organizations",
    "view-organizations",
    "query-users",
  ])

  realm_id                = keycloak_realm.tenant.id
  service_account_user_id = keycloak_openid_client.client["weave_identity_admin"].service_account_user_id
  client_id               = data.keycloak_openid_client.realm_management.id
  role                    = each.value
}

resource "keycloak_openid_client_scope" "weave_workspace" {
  realm_id               = keycloak_realm.tenant.id
  name                   = "weave:workspace"
  description            = "Grants Weave mobile clients access to workspace APIs."
  include_in_token_scope = true
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
  claim_value         = "Weave Dogfood"
  claim_value_type    = "String"
  add_to_id_token     = false
  add_to_access_token = true
  add_to_userinfo     = true
}

resource "keycloak_openid_audience_protocol_mapper" "weave_backend_audience" {
  realm_id                 = keycloak_realm.tenant.id
  client_scope_id          = keycloak_openid_client_scope.weave_workspace.id
  name                     = "weave-app-audience"
  included_client_audience = keycloak_openid_client.client["weave_app"].client_id
  add_to_id_token          = false
  add_to_access_token      = true
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

  default_scopes = local.weave_app_default_scopes

  depends_on = [
    keycloak_openid_client_scope.weave_workspace,
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
