#!/usr/bin/env python3
"""Native realm projection contracts; live LDAP/Keycloak evidence is separate."""
import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'keycloak'))
from directory_identity import directory_realm, HUMAN_CLIENT, HUMAN_ROLES, USER_STORAGE, LDAP_MAPPER


class DirectoryIdentityTest(unittest.TestCase):
    def setUp(self):
        self.source = {'realm': 'weave', 'enabled': True,
                       'clients': [{'clientId': HUMAN_CLIENT, 'publicClient': True,
                                    'redirectUris': ['com.massimotter.weave:/oauthredirect'],
                                    'protocolMappers': [{'name': 'api audience', 'protocolMapper': 'oidc-audience-mapper'}]},
                                   {'clientId': 'weave-backend', 'publicClient': False}],
                       'users': [{'username': 'service-account-weave-backend',
                                  'serviceAccountClientId': 'weave-backend'}],
                       'roles': {'realm': [{'name': 'weaver-runtime'}],
                                 'client': {'weave-backend': [{'name': 'workload'}]}},
                       'components': {'org.example.Workload': [{'name': 'existing-workload-policy'}]}}

    def render(self, source=None, **changes):
        args = {'ldap_url': 'ldaps://ldap.weave.test:636', 'directory_domain': 'weave.test'}
        args.update(changes)
        return directory_realm(self.source if source is None else source, **args)

    def provider(self, result):
        return result['components'][USER_STORAGE][0]

    def test_native_schema_uses_readonly_import_and_entry_uuid(self):
        config = self.provider(self.render())['config']
        for key, value in {'editMode': 'READ_ONLY', 'importEnabled': 'true',
                           'syncRegistrations': 'false', 'uuidLDAPAttribute': 'entryUUID',
                           'usernameLDAPAttribute': 'uid', 'cachePolicy': 'NO_CACHE'}.items():
            self.assertEqual(config[key], [value])

    def test_rerender_preserves_component_ids_and_does_not_mutate_source(self):
        original = copy.deepcopy(self.source)
        first = self.render()
        self.assertEqual(self.source, original)
        self.assertEqual(self.render(first), first)

    def test_scope_preserves_workload_clients_roles_and_components(self):
        result = self.render()
        self.assertEqual(result['clients'][1], self.source['clients'][1])
        self.assertEqual(result['users'], self.source['users'])
        self.assertEqual(result['roles']['realm'], self.source['roles']['realm'])
        self.assertEqual(result['roles']['client']['weave-backend'], [{'name': 'workload'}])
        self.assertEqual(result['components']['org.example.Workload'],
                         self.source['components']['org.example.Workload'])
        self.assertEqual(result['clients'][0]['redirectUris'], self.source['clients'][0]['redirectUris'])
        self.assertEqual(result['clients'][0]['protocolMappers'], self.source['clients'][0]['protocolMappers'])

    def test_users_are_not_automatically_members_or_keycloak_administrators(self):
        result = self.render()
        self.assertNotIn('defaultRoles', result)
        self.assertNotIn('defaultRole', result)
        self.assertNotIn('defaultGroups', result)
        self.assertEqual({role['name'] for role in result['roles']['client'][HUMAN_CLIENT]}, set(HUMAN_ROLES))
        for role in result['roles']['client'][HUMAN_CLIENT]:
            self.assertNotIn('realm-management', role.get('composites', {}).get('client', {}))
        self.assertFalse(result['clients'][0]['fullScopeAllowed'])

    def test_roles_are_exact_ldap_groups_not_a_parallel_group_hierarchy(self):
        result = self.render()
        mappers = self.provider(result)['subComponents'][LDAP_MAPPER]
        role = next(mapper for mapper in mappers if mapper['providerId'] == 'role-ldap-mapper')
        self.assertEqual(role['config']['mode'], ['LDAP_ONLY'])
        self.assertEqual(role['config']['roles.ldap.filter'],
                         ['(|(cn=member)(cn=workspace-admin)(cn=platform-admin))'])
        self.assertEqual(role['config']['client.id'], ['weave-app'])
        self.assertEqual(role['config']['use.realm.roles.mapping'], ['false'])
        self.assertNotIn('groups', result)

    def test_local_locale_does_not_enable_directory_writes(self):
        result = self.render()
        self.assertEqual(result['supportedLocales'], ['de', 'en'])
        self.assertTrue(result['internationalizationEnabled'])
        mappers = self.provider(result)['subComponents'][LDAP_MAPPER]
        self.assertFalse(any(mapper['config'].get('user.model.attribute') == ['locale'] for mapper in mappers))
        attrs = [mapper for mapper in mappers if mapper['providerId'] == 'user-attribute-ldap-mapper']
        self.assertTrue(all(mapper['config']['read.only'] == ['true'] for mapper in attrs))

    def test_directory_credentials_are_vault_references_only(self):
        config = self.provider(self.render())['config']
        self.assertEqual(config['bindCredential'], ['${vault.ldapBindPassword}'])
        self.assertEqual(config['bindDn'], ['uid=keycloak,ou=services,dc=weave,dc=test'])
        self.assertEqual(config['usersDn'], ['ou=people,dc=weave,dc=test'])

    def test_registration_password_reset_and_password_grant_are_off(self):
        result = self.render()
        for flag in ('registrationAllowed', 'resetPasswordAllowed', 'editUsernameAllowed', 'organizationsEnabled', 'verifyEmail'):
            self.assertFalse(result[flag])
        self.assertFalse(result['clients'][0]['directAccessGrantsEnabled'])
        self.assertFalse(result['clients'][0]['serviceAccountsEnabled'])
        self.assertEqual(result['clients'][0]['attributes']['pkce.code.challenge.method'], 'S256')
        self.assertEqual(result['accessTokenLifespan'], 300)

    def test_old_organization_claims_are_removed_without_losing_stock_profile_or_roles(self):
        source = copy.deepcopy(self.source)
        stock = Path(__file__).resolve().parents[1] / 'keycloak/stock-client-scopes-26.7.1.json'
        source.update(json.loads(stock.read_text()))
        client = source['clients'][0]
        client.update(defaultClientScopes=['profile', 'organization'],
                      optionalClientScopes=['email', 'organization'])
        client['protocolMappers'].append({'name': 'obsolete groups',
                                         'protocolMapper': 'oidc-organization-group-membership-mapper'})
        result = self.render(source)
        self.assertNotIn('organization', [scope['name'] for scope in result['clientScopes']])
        self.assertNotIn('organization', result.get('defaultOptionalClientScopes', []))
        self.assertEqual(result['clients'][0]['defaultClientScopes'], ['profile', 'email', 'roles'])
        self.assertEqual(result['clients'][0]['optionalClientScopes'], ['email'])
        self.assertEqual(result['clients'][0]['protocolMappers'], self.source['clients'][0]['protocolMappers'])
        profile = next(scope for scope in result['clientScopes'] if scope['name'] == 'profile')
        locale = [mapper for mapper in profile['protocolMappers']
                  if mapper['config'].get('claim.name') == 'locale']
        self.assertEqual(len(locale), 1)
        roles = next(scope for scope in result['clientScopes'] if scope['name'] == 'roles')
        self.assertTrue(any(mapper['protocolMapper'] == 'oidc-usermodel-client-role-mapper'
                            for mapper in roles['protocolMappers']))

    def test_only_schema_required_attributes_are_mandatory(self):
        mappers = self.provider(self.render())['subComponents'][LDAP_MAPPER]
        required = {mapper['config']['ldap.attribute'][0] for mapper in mappers
                    if mapper['providerId'] == 'user-attribute-ldap-mapper'
                    and mapper['config']['is.mandatory.in.ldap'] == ['true']}
        self.assertEqual(required, {'uid', 'sn'})

    def test_unknown_federation_is_not_recreated_under_new_id(self):
        source = copy.deepcopy(self.source)
        source['components'][USER_STORAGE] = [{'id': 'existing-provider'}]
        with self.assertRaisesRegex(ValueError, 'explicit-identity-migration'):
            self.render(source)

    def test_exported_human_is_not_merged_by_username_or_email(self):
        source = copy.deepcopy(self.source)
        source['users'].append({'username': 'alice', 'email': 'alice@example.test'})
        with self.assertRaisesRegex(ValueError, 'explicit-identity-migration'):
            self.render(source)

    def test_no_implicit_conversion_of_legacy_group_and_default_role_grants(self):
        for key, value in (('groups', [{'name': 'admins'}]), ('defaultGroups', ['/members']),
                           ('defaultRole', {'name': 'default-roles-weave'})):
            with self.subTest(key=key):
                source = copy.deepcopy(self.source)
                source[key] = value
                with self.assertRaises(ValueError):
                    self.render(source)

    def test_insecure_or_credential_bearing_directory_urls_fail(self):
        for url in ('ldap://ldap.weave.test', 'ldaps://user:password@ldap.weave.test',
                    'ldaps://ldap.weave.test/path', 'ldaps://ldap.weave.test?q=secret',
                    'ldaps://ldap.weave.test:99999', 'ldaps://ldap.weave.test\n',
                    'ldaps://ldap.weave.test:bad'):
            with self.subTest(url=url), self.assertRaises(ValueError):
                self.render(ldap_url=url)

    def test_directory_domain_cannot_inject_dn_components(self):
        for domain in ('weave.test,ou=admin', 'weave..test', '-weave.test', 'Weave.test', 'test'):
            with self.subTest(domain=domain), self.assertRaises(ValueError):
                self.render(directory_domain=domain)


if __name__ == '__main__':
    unittest.main()
