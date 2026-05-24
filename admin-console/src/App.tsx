import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  AppBar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  CssBaseline,
  Divider,
  FormControl,
  FormHelperText,
  InputLabel,
  Link,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  TextField,
  Toolbar,
  Typography,
} from '@mui/material';
import { AdminControlPlaneApi, adminConsoleConfig, ControlPlaneResponse, ProviderCategory, sampleControlPlane } from './api';

const stateColor: Record<ProviderCategory['state'], 'success' | 'default' | 'warning' | 'error' | 'info'> = {
  ready: 'success',
  disabled: 'default',
  degraded: 'warning',
  'policy-blocked': 'info',
  misconfigured: 'error',
  unsupported: 'error',
};

function readableState(state: ProviderCategory['state']): string {
  return state.replace('-', ' ');
}

interface AppProps {
  api?: AdminControlPlaneApi;
}

export default function App({ api = new AdminControlPlaneApi() }: AppProps) {
  const [controlPlane, setControlPlane] = useState<ControlPlaneResponse>(sampleControlPlane);
  const [loadState, setLoadState] = useState<'loading' | 'loaded' | 'offline-sample'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [selectedProvider, setSelectedProvider] = useState(sampleControlPlane.providerCategories[0]?.key ?? '');
  const [policyDraft, setPolicyDraft] = useState(sampleControlPlane.whitelistPolicy.allowedCapabilities.join('\n'));
  const [statusMessage, setStatusMessage] = useState('Admin Console is loading backend control-plane data.');

  useEffect(() => {
    let alive = true;
    api
      .getControlPlane()
      .then((response) => {
        if (!alive) return;
        setControlPlane(response);
        setPolicyDraft(response.whitelistPolicy.allowedCapabilities.join('\n'));
        setSelectedProvider(response.providerCategories[0]?.key ?? '');
        setLoadState('loaded');
        setStatusMessage('Backend control-plane data loaded.');
      })
      .catch((cause: unknown) => {
        if (!alive) return;
        setLoadState('offline-sample');
        setError(cause instanceof Error ? cause.message : 'Admin API is unavailable; showing the contract-backed sample state.');
        setStatusMessage('Admin API unavailable. Showing support-safe sample data only.');
      });
    return () => {
      alive = false;
    };
  }, [api]);

  const selectedProviderDetails = useMemo(
    () => controlPlane.providerCategories.find((provider) => provider.key === selectedProvider) ?? controlPlane.providerCategories[0],
    [controlPlane.providerCategories, selectedProvider],
  );

  async function savePolicy() {
    const allowedCapabilities = policyDraft
      .split('\n')
      .map((capability) => capability.trim())
      .filter(Boolean);
    const response = await api.updateWhitelistPolicy(allowedCapabilities);
    setControlPlane((current) => ({ ...current, whitelistPolicy: response }));
    setStatusMessage(`Whitelist policy saved with ${response.allowedCapabilities.length} allowed capabilities.`);
  }

  async function testReadiness(providerKey: string) {
    const result = await api.testProviderReadiness(providerKey);
    setStatusMessage(`Readiness test queued for ${result.providerKey}: ${readableState(result.state)}.`);
  }

  return (
    <>
      <CssBaseline />
      <AppBar position="static" color="primary">
        <Toolbar>
          <Typography variant="h1" component="h1" sx={{ fontSize: { xs: '1.35rem', md: '1.7rem' }, fontWeight: 700 }}>
            Weave Organization Admin Console
          </Typography>
        </Toolbar>
      </AppBar>
      <Container component="main" maxWidth="lg" sx={{ py: 4 }}>
        <Stack spacing={3}>
          <Alert severity={loadState === 'loaded' ? 'success' : loadState === 'loading' ? 'info' : 'warning'} role="status">
            {statusMessage}
          </Alert>
          {error ? <Alert severity="warning">{error}</Alert> : null}

          <Card component="section" aria-labelledby="oidc-heading">
            <CardContent>
              <Typography id="oidc-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 1 }}>
                Admin sign-in contract
              </Typography>
              <Typography>
                Sign in through OIDC/Keycloak client <strong>{adminConsoleConfig.oidcClientId}</strong>. This console calls only Weave backend admin APIs;
                it does not call Keycloak, Nextcloud, Matrix, Microsoft Graph, or other providers directly.
              </Typography>
              <Typography sx={{ mt: 1 }}>
                Issuer: <code>{adminConsoleConfig.oidcIssuerUrl}</code>
              </Typography>
              <Button variant="outlined" sx={{ mt: 2 }} href={`${adminConsoleConfig.oidcIssuerUrl}/protocol/openid-connect/auth`}>
                Open identity broker
              </Button>
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="org-heading">
            <CardContent>
              <Typography id="org-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 1 }}>
                Organization overview
              </Typography>
              <Stack spacing={1}>
                <Typography>
                  <strong>{controlPlane.organization.displayName}</strong> ({controlPlane.organization.id})
                </Typography>
                <Typography>
                  Member manifest: <code>{controlPlane.organization.manifestUrl}</code>
                </Typography>
                <Typography>
                  Auth issuer: <code>{controlPlane.organization.authIssuerUrl}</code>
                </Typography>
              </Stack>
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="providers-heading">
            <CardContent>
              <Typography id="providers-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 2 }}>
                Provider categories
              </Typography>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                {controlPlane.providerCategories.map((provider) => (
                  <Card key={provider.key} variant="outlined" sx={{ flex: 1 }}>
                    <CardContent>
                      <Typography variant="h3" sx={{ fontSize: '1.05rem' }}>
                        {provider.label}
                      </Typography>
                      <Chip
                        sx={{ mt: 1 }}
                        color={stateColor[provider.state]}
                        label={`Status: ${readableState(provider.state)}`}
                        aria-label={`${provider.label} status is ${readableState(provider.state)}`}
                      />
                      <Typography sx={{ mt: 1 }}>{provider.summary}</Typography>
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="provider-detail-heading">
            <CardContent>
              <Typography id="provider-detail-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 2 }}>
                Provider detail and readiness
              </Typography>
              <FormControl fullWidth sx={{ mb: 2 }}>
                <InputLabel id="provider-select-label">Provider category</InputLabel>
                <Select
                  labelId="provider-select-label"
                  id="provider-select"
                  value={selectedProvider}
                  label="Provider category"
                  onChange={(event) => setSelectedProvider(event.target.value)}
                >
                  {controlPlane.providerCategories.map((provider) => (
                    <MenuItem key={provider.key} value={provider.key}>
                      {provider.label}
                    </MenuItem>
                  ))}
                </Select>
                <FormHelperText>Readiness tests are sent to the backend control plane.</FormHelperText>
              </FormControl>
              {selectedProviderDetails ? (
                <Stack spacing={1}>
                  <Typography>Adapter: {selectedProviderDetails.selectedAdapter}</Typography>
                  <Typography>Status text: {readableState(selectedProviderDetails.state)}</Typography>
                  <Typography>Secret references: {selectedProviderDetails.secretRefs.length ? selectedProviderDetails.secretRefs.join(', ') : 'none'}</Typography>
                  <Button variant="contained" onClick={() => void testReadiness(selectedProviderDetails.key)}>
                    Test readiness through backend
                  </Button>
                </Stack>
              ) : null}
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="policy-heading">
            <CardContent>
              <Typography id="policy-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 1 }}>
                Policy and whitelist
              </Typography>
              <Alert severity="info" sx={{ mb: 2 }}>
                Policy is deny-by-default. Add one capability per line only after the organization has approved it.
              </Alert>
              <TextField
                label="Allowed capabilities"
                value={policyDraft}
                onChange={(event) => setPolicyDraft(event.target.value)}
                fullWidth
                multiline
                minRows={5}
                helperText="Example: files.read. Do not paste secrets, provider tokens, or raw diagnostics here."
              />
              <Button sx={{ mt: 2 }} variant="contained" onClick={() => void savePolicy()}>
                Save whitelist policy
              </Button>
              <Divider sx={{ my: 2 }} />
              <Typography>Blocked examples: {controlPlane.whitelistPolicy.blockedCapabilities.join(', ')}</Typography>
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="audit-heading">
            <CardContent>
              <Typography id="audit-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 1 }}>
                Audit trail
              </Typography>
              <List aria-label="Recent admin audit events">
                {controlPlane.auditEvents.map((event) => (
                  <ListItem key={event.id} alignItems="flex-start">
                    <ListItemText primary={`${event.action} by ${event.actor}`} secondary={`${event.createdAt} — ${event.summary}`} />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>

          <Box component="footer">
            <Typography variant="body2">
              Need member behavior? Use the provider-agnostic Weave Client. Admin/provider setup belongs here and in backend policy.{' '}
              <Link href="/api/organization/manifest">Organization manifest</Link>
            </Typography>
          </Box>
        </Stack>
      </Container>
    </>
  );
}
