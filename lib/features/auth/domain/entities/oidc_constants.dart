const oidcRedirectScheme = 'weaveapp';
const oidcRedirectUri = 'weaveapp://login/callback';
const oidcPostLogoutRedirectUri = 'weaveapp://logout/callback';
const oidcDefaultClientId = 'weave-app';

const oidcDefaultScopes = <String>[
  'openid',
  'profile',
  'email',
  'offline_access',
];
