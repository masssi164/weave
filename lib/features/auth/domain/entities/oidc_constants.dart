const oidcRedirectScheme = 'com.massimotter.weave';
const oidcRedirectUri = '$oidcRedirectScheme:/oauthredirect';
const oidcPostLogoutRedirectUri = '$oidcRedirectScheme:/logout';

const oidcDefaultScopes = <String>[
  'openid',
  'profile',
  'email',
  'offline_access',
];
