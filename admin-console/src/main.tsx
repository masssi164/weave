import React from 'react';
import ReactDOM from 'react-dom/client';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import App from './App';
import { AdminControlPlaneApi, resolveAdminConsoleConfig } from './api';
import { BrowserOidcPkceClient } from './auth';

const theme = createTheme({
  typography: {
    fontFamily: ['Inter', 'system-ui', 'Segoe UI', 'sans-serif'].join(','),
  },
  palette: {
    primary: {
      main: '#2945ff',
    },
  },
});

async function bootstrap(): Promise<void> {
  const adminConfig = await resolveAdminConsoleConfig();
  const oidc = new BrowserOidcPkceClient(adminConfig);
  let authenticationError: string | null = null;
  try {
    await oidc.completeAuthorizationCallback();
  } catch (cause: unknown) {
    authenticationError = cause instanceof Error
      ? cause.message
      : 'OIDC authorization could not be completed.';
  }
  const api = new AdminControlPlaneApi(
    adminConfig,
    fetch,
    () => oidc.token(),
  );

  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <ThemeProvider theme={theme}>
        <App
          api={api}
          adminConfig={adminConfig}
          authenticationError={authenticationError}
          onSignIn={() => oidc.beginAuthorization()}
        />
      </ThemeProvider>
    </React.StrictMode>,
  );
}

void bootstrap();
