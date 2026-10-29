"""Native Keycloak RealmRepresentation projection for the coordinated LDAP cutover.

This transforms an import document, never a live realm. The existing renderer is
not switched until the new corpus pin, backend admission and migration agree.
"""
from __future__ import annotations

import copy
import re
import uuid
from urllib.parse import urlsplit

HUMAN_CLIENT = 'weave-app'
HUMAN_ROLES = ('member', 'workspace-admin', 'platform-admin')
USER_STORAGE = 'org.keycloak.storage.UserStorageProvider'
LDAP_MAPPER = 'org.keycloak.storage.ldap.mappers.LDAPStorageMapper'


def directory_realm(source: dict, *, ldap_url: str, directory_domain: str) -> dict:
    realm = copy.deepcopy(source)
    name = realm.get('realm')
    if not isinstance(name, str) or not re.fullmatch(r'[a-z][a-z0-9-]{0,62}', name):
        raise ValueError('explicit-stable-realm-name-required')
    if not isinstance(ldap_url, str) or any(character.isspace() for character in ldap_url):
        raise ValueError('ldap-requires-ldaps-without-credentials')
    connection = urlsplit(ldap_url)
    # Accessing port validates its numeric range instead of silently accepting
    # an unusable connection URL in an otherwise valid import.
    _ = connection.port
    if (connection.scheme != 'ldaps' or not connection.hostname or connection.username is not None
            or connection.password is not None or connection.path not in ('', '/')
            or connection.query or connection.fragment):
        raise ValueError('ldap-requires-ldaps-without-credentials')
    labels = directory_domain.split('.')
    if len(labels) < 2 or any(not re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?', label)
                              for label in labels):
        raise ValueError('explicit-lowercase-directory-domain-required')
    suffix = ','.join('dc=' + label for label in labels)
    # Never silently turn an exported human account into a new federated user.
    if any(not user.get('serviceAccountClientId') for user in realm.get('users', [])):
        raise ValueError('existing-human-users-require-explicit-identity-migration')
    clients = [client for client in realm.get('clients', []) if client.get('clientId') == HUMAN_CLIENT]
    if len(clients) != 1 or clients[0].get('publicClient') is not True:
        raise ValueError('existing-public-weave-app-client-required')
    realm.update(registrationAllowed=False, resetPasswordAllowed=False, editUsernameAllowed=False,
                 verifyEmail=False,
                 organizationsEnabled=False, internationalizationEnabled=True,
                 supportedLocales=['de', 'en'], defaultLocale='de', accessTokenLifespan=300)
    realm.pop('organizations', None)
    obsolete_mappers = {'oidc-organization-membership-mapper',
                        'oidc-organization-group-membership-mapper'}
    realm['clientScopes'] = [scope for scope in realm.get('clientScopes', [])
                             if scope.get('name') != 'organization']
    for target in [realm, *realm.get('clients', []), *realm['clientScopes']]:
        for field in ('defaultClientScopes', 'optionalClientScopes',
                      'defaultDefaultClientScopes', 'defaultOptionalClientScopes'):
            if field in target:
                target[field] = [scope for scope in target[field] if scope != 'organization']
        if 'protocolMappers' in target:
            target['protocolMappers'] = [entry for entry in target['protocolMappers']
                                         if entry.get('protocolMapper') not in obsolete_mappers]
    # Role grants come only from the three managed LDAP groups. Department
    # topology need not be copied into a second Keycloak group database.
    if realm.get('groups') or realm.get('defaultGroups'):
        raise ValueError('legacy-human-groups-require-explicit-migration')
    realm.pop('defaultRoles', None)
    if 'defaultRole' in realm:
        # A default role can carry legacy composite membership grants.
        raise ValueError('default-role-composites-require-explicit-migration')
    roles = realm.setdefault('roles', {}).setdefault('client', {})
    roles[HUMAN_CLIENT] = [
        {'name': 'member', 'description': 'Use installed collaboration domains'},
        {'name': 'workspace-admin', 'description': 'Administer the workspace, not private resources',
         'composite': True, 'composites': {'client': {HUMAN_CLIENT: ['member']}}},
        {'name': 'platform-admin', 'description': 'Operate Weave, not Keycloak realm-management',
         'composite': True, 'composites': {'client': {HUMAN_CLIENT: ['workspace-admin']}}},
    ]
    client = clients[0]
    client.update(directAccessGrantsEnabled=False, serviceAccountsEnabled=False,
                  fullScopeAllowed=False, standardFlowEnabled=True)
    client.setdefault('attributes', {})['pkce.code.challenge.method'] = 'S256'
    scopes = client.setdefault('defaultClientScopes', [])
    for scope in ('profile', 'email', 'roles'):
        if scope not in scopes:
            scopes.append(scope)
    # Keycloak uses clientScopeMappings for roles belonging to another/client
    # role container. Preserve mappings for unrelated workload clients.
    mappings = realm.setdefault('clientScopeMappings', {}).setdefault(HUMAN_CLIENT, [])
    mappings[:] = [mapping for mapping in mappings if mapping.get('client') != HUMAN_CLIENT]
    mappings.append({'client': HUMAN_CLIENT, 'roles': list(HUMAN_ROLES)})
    provider_id = str(uuid.uuid5(uuid.NAMESPACE_URL, 'weave:ldap:' + name))
    def mapper(label: str, provider: str, config: dict) -> dict:
        return {'id': str(uuid.uuid5(uuid.UUID(provider_id), label)), 'name': label,
                'providerId': provider, 'providerType': LDAP_MAPPER,
                'config': {key: [value] for key, value in config.items()}}
    mappers = [mapper(label, 'user-attribute-ldap-mapper', {
        'user.model.attribute': model, 'ldap.attribute': attribute, 'read.only': 'true',
        'always.read.value.from.ldap': 'true',
        'is.mandatory.in.ldap': 'true' if attribute in {'uid', 'sn'} else 'false',
    }) for label, model, attribute in (
        ('username', 'username', 'uid'), ('first name', 'firstName', 'givenName'),
        ('last name', 'lastName', 'sn'), ('email', 'email', 'mail'))]
    mappers.append(mapper('weave roles', 'role-ldap-mapper', {
        'roles.dn': 'ou=groups,' + suffix, 'role.name.ldap.attribute': 'cn',
        'role.object.classes': 'groupOfNames', 'membership.ldap.attribute': 'member',
        'membership.attribute.type': 'DN', 'membership.user.ldap.attribute': 'uid',
        'mode': 'LDAP_ONLY', 'use.realm.roles.mapping': 'false', 'client.id': HUMAN_CLIENT,
        'user.roles.retrieve.strategy': 'LOAD_ROLES_BY_MEMBER_ATTRIBUTE',
        'roles.ldap.filter': '(|(cn=member)(cn=workspace-admin)(cn=platform-admin))',
    }))
    component = {'id': provider_id, 'name': 'OpenLDAP', 'providerId': 'ldap',
                 'providerType': USER_STORAGE, 'config': {key: [value] for key, value in {
                     'enabled': 'true', 'priority': '0', 'vendor': 'other', 'editMode': 'READ_ONLY',
                     'importEnabled': 'true', 'syncRegistrations': 'false', 'cachePolicy': 'NO_CACHE',
                     'connectionUrl': ldap_url, 'usersDn': 'ou=people,' + suffix,
                     'bindDn': 'uid=keycloak,ou=services,' + suffix,
                     'bindCredential': '${vault.ldapBindPassword}', 'authType': 'simple',
                     'usernameLDAPAttribute': 'uid', 'rdnLDAPAttribute': 'uid',
                     'uuidLDAPAttribute': 'entryUUID', 'userObjectClasses': 'inetOrgPerson',
                     'searchScope': '1', 'connectionPooling': 'true',
                     'connectionTimeout': '5000', 'readTimeout': '5000',
                     'startTls': 'false', 'pagination': 'true',
                 }.items()}, 'subComponents': {LDAP_MAPPER: mappers}}
    providers = realm.setdefault('components', {}).get(USER_STORAGE, [])
    if providers and providers != [component]:
        raise ValueError('existing-federation-requires-explicit-identity-migration')
    realm['components'][USER_STORAGE] = [component]
    # locale is a Keycloak INTERNAL_ATTRIBUTE in 26.7.1. Do not map it to LDAP
    # or enable UNSYNCED/WRITABLE merely to save the user's language choice.
    return realm
