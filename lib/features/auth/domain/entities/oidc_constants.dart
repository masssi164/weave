const oidcRedirectScheme = 'com.massimotter.weave';
const oidcRedirectUri = '$oidcRedirectScheme:/oauthredirect';
const oidcPostLogoutRedirectUri = '$oidcRedirectScheme:/logout';
const oidcDefaultClientId = 'weave-app';
const oidcWorkspaceScope = 'weave:workspace';

const oidcDefaultScopes = <String>[
  'openid',
  'profile',
  'email',
  'offline_access',
  oidcWorkspaceScope,
];
