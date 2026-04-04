const oidcRedirectScheme = 'weaveapp';
const oidcRedirectUri = '$oidcRedirectScheme://login/callback';
const oidcPostLogoutRedirectUri = '$oidcRedirectScheme://logout/callback';
const oidcDefaultClientId = 'weave-app';

const oidcDefaultScopes = <String>[
  'openid',
  'profile',
  'email',
  'offline_access',
];
