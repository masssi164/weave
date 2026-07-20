terraform {
  required_version = ">= 1.5.0"

  backend "local" {}

  required_providers {
    keycloak = {
      source  = "keycloak/keycloak"
      version = "5.8.0"
    }
  }
}

provider "keycloak" {
  client_id = "admin-cli"
  username  = var.keycloak_admin_username
  password  = var.keycloak_admin_password
  realm     = "master"
  url       = "http://${var.keycloak_admin_host}:${var.keycloak_host_port}"
}

locals {
  public_port_suffix = (
    (var.public_scheme == "http" && var.proxy_host_port == 80) ||
    (var.public_scheme == "https" && var.proxy_host_port == 443)
  ) ? "" : ":${var.proxy_host_port}"

  public_hosts = {
    weave  = var.tenant_domain
    api    = "${var.api_subdomain}.${var.tenant_domain}"
    admin  = "${var.admin_subdomain}.${var.tenant_domain}"
    auth   = "${var.auth_subdomain}.${var.tenant_domain}"
    matrix = "${var.matrix_subdomain}.${var.tenant_domain}"
    files  = "${var.nextcloud_subdomain}.${var.tenant_domain}"
  }

  public_urls = {
    for service, host in local.public_hosts :
    service => "${var.public_scheme}://${host}${local.public_port_suffix}"
  }

  # DNS-first local contract: local_lan_host is not a public issuer/redirect truth.
  client_auth_url   = local.public_urls.auth
  client_matrix_url = local.public_urls.matrix

  matrix_mas_upstream_id = "01JQ7N9R4QK6W3M5X8Y2ZC1DHF"
  smtp_from              = coalesce(var.keycloak_smtp_from, "no-reply@${var.tenant_domain}")
  test_user_email        = coalesce(var.test_user_email, "test@${var.tenant_domain}")
}

module "tenant_identity" {
  source = "./modules/tenant-identity"

  tenant_slug                             = var.tenant_slug
  product_public_url                      = local.public_urls.weave
  keycloak_public_url                     = local.client_auth_url
  mas_public_url                          = local.client_matrix_url
  nextcloud_public_url                    = local.public_urls.files
  admin_console_public_url                = local.public_urls.admin
  matrix_mas_upstream_id                  = local.matrix_mas_upstream_id
  matrix_mas_client_secret                = var.matrix_mas_client_secret
  identity_admin_client_secret            = var.identity_admin_client_secret
  weave_mcp_client_secret                 = var.weave_mcp_client_secret
  weave_mcp_resource                      = "${local.public_urls.api}/mcp"
  smtp_host                               = var.keycloak_smtp_host
  smtp_port                               = var.keycloak_smtp_port
  smtp_from                               = local.smtp_from
  smtp_from_display_name                  = var.keycloak_smtp_from_display_name
  smtp_ssl                                = var.keycloak_smtp_ssl
  smtp_starttls                           = var.keycloak_smtp_starttls
  smtp_username                           = var.keycloak_smtp_username
  smtp_password                           = var.keycloak_smtp_password
  organization_display_name               = var.organization_display_name
  test_user_email                         = local.test_user_email
  create_test_user                        = var.create_test_user
  test_user_password                      = var.test_user_password
  context_authorization_default_tenant_id = var.context_authorization_default_tenant_id
}

moved {
  from = keycloak_realm.tenant
  to   = module.tenant_identity.keycloak_realm.tenant
}

moved {
  from = keycloak_openid_client.client["weave_app"]
  to   = module.tenant_identity.keycloak_openid_client.client["weave_app"]
}

moved {
  from = keycloak_openid_client.client["weave_backend"]
  to   = module.tenant_identity.keycloak_openid_client.client["weave_backend"]
}

moved {
  from = keycloak_openid_client.client["matrix_mas"]
  to   = module.tenant_identity.keycloak_openid_client.client["matrix_mas"]
}

moved {
  from = keycloak_openid_client.client["nextcloud"]
  to   = module.tenant_identity.keycloak_openid_client.client["nextcloud"]
}

moved {
  from = keycloak_openid_group_membership_protocol_mapper.nextcloud_groups
  to   = module.tenant_identity.keycloak_openid_group_membership_protocol_mapper.nextcloud_groups
}
