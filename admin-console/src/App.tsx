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
import { AdminControlPlaneApi, adminConsoleConfig, CapabilityState, ControlPlaneResponse, ProviderCategory, sampleControlPlane } from './api';

const stateColor: Record<CapabilityState, 'success' | 'default' | 'warning' | 'error' | 'info'> = {
  ready: 'success',
  disabled: 'default',
  degraded: 'warning',
  'policy-blocked': 'info',
  misconfigured: 'error',
  unsupported: 'error',
  not_configured: 'default',
  configured: 'info',
};

function readableState(state: string): string {
  return state.replace(/[-_]/g, ' ');
}

function defaultProviderKey(category?: ProviderCategory): string {
  if (!category) return '';
  return category.selectedAdapter === 'awaiting_admin_selection' ? category.providerCandidates[0] ?? '' : category.selectedAdapter;
}

interface AppProps {
  api?: AdminControlPlaneApi;
}

export default function App({ api = new AdminControlPlaneApi() }: AppProps) {
  const [controlPlane, setControlPlane] = useState<ControlPlaneResponse>(sampleControlPlane);
  const [loadState, setLoadState] = useState<'loading' | 'loaded' | 'offline-sample'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState(sampleControlPlane.providerCategories[0]?.key ?? '');
  const [providerDraft, setProviderDraft] = useState(defaultProviderKey(sampleControlPlane.providerCategories[0]));
  const [choiceModelDraft, setChoiceModelDraft] = useState('recommended_self_hosted_default');
  const [policyDraft, setPolicyDraft] = useState(sampleControlPlane.whitelistPolicy.allowedCapabilities.join('\n'));
  const [statusMessage, setStatusMessage] = useState('Admin Console is loading backend control-plane data.');

  useEffect(() => {
    let alive = true;
    api
      .getControlPlane()
      .then((response) => {
        if (!alive) return;
        const firstCategory = response.providerCategories[0];
        setControlPlane(response);
        setPolicyDraft(response.whitelistPolicy.allowedCapabilities.join('\n'));
        setSelectedCategory(firstCategory?.key ?? '');
        setProviderDraft(defaultProviderKey(firstCategory));
        setChoiceModelDraft(firstCategory?.choiceModel === 'not_selected' ? 'recommended_self_hosted_default' : firstCategory?.choiceModel ?? 'recommended_self_hosted_default');
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

  const selectedCategoryDetails = useMemo(
    () => controlPlane.providerCategories.find((category) => category.key === selectedCategory) ?? controlPlane.providerCategories[0],
    [controlPlane.providerCategories, selectedCategory],
  );

  function changeCategory(categoryKey: string) {
    const category = controlPlane.providerCategories.find((candidate) => candidate.key === categoryKey);
    setSelectedCategory(categoryKey);
    setProviderDraft(defaultProviderKey(category));
    setChoiceModelDraft(category?.choiceModel === 'not_selected' ? 'recommended_self_hosted_default' : category?.choiceModel ?? 'recommended_self_hosted_default');
  }

  async function savePolicy() {
    const allowedCapabilities = policyDraft
      .split('\n')
      .map((capability) => capability.trim())
      .filter(Boolean);
    const response = await api.updateWhitelistPolicy(allowedCapabilities);
    setControlPlane((current) => ({ ...current, whitelistPolicy: response }));
    setStatusMessage(`Whitelist policy saved with ${allowedCapabilities.length} requested capabilities.`);
  }

  async function selectProvider(dryRun: boolean) {
    if (!selectedCategoryDetails || !providerDraft) return;
    await api.selectProvider(selectedCategoryDetails.key, providerDraft, choiceModelDraft, dryRun);
    if (dryRun) {
      setStatusMessage(`Dry-run validated for ${selectedCategoryDetails.key}: ${providerDraft}.`);
      return;
    }
    const refreshed = await api.getControlPlane();
    setControlPlane(refreshed);
    setStatusMessage(`Provider selection applied for ${selectedCategoryDetails.key}: ${providerDraft}. Backend control plane refreshed as source of truth.`);
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
                it does not call Keycloak, Nextcloud, Matrix, Microsoft Graph, Slack, Teams, or other providers directly.
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
                  Provider source of truth: <code>{controlPlane.providerConfigSource}</code>
                </Typography>
                <Typography>
                  Bootstrap defaults are suggestions only: <strong>{controlPlane.bootstrapDefaultsAreSuggestionsOnly ? 'yes' : 'no'}</strong>
                </Typography>
                <Typography>Member clients may configure providers: <strong>no</strong></Typography>
              </Stack>
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="providers-heading">
            <CardContent>
              <Typography id="providers-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 2 }}>
                Provider categories
              </Typography>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ flexWrap: 'wrap' }} useFlexGap>
                {controlPlane.providerCategories.map((category) => (
                  <Card key={category.key} variant="outlined" sx={{ flex: '1 1 260px' }}>
                    <CardContent>
                      <Typography variant="h3" sx={{ fontSize: '1.05rem' }}>
                        {category.label}
                      </Typography>
                      <Chip
                        sx={{ mt: 1 }}
                        color={stateColor[category.state]}
                        label={`Status: ${readableState(category.state)}`}
                        aria-label={`${category.label} status is ${readableState(category.state)}`}
                      />
                      <Typography sx={{ mt: 1 }}>{category.summary}</Typography>
                      <Typography variant="body2" sx={{ mt: 1 }}>
                        Selected: {category.selectedAdapter}; candidates: {category.providerCandidates.join(', ')}
                      </Typography>
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="provider-selection-heading">
            <CardContent>
              <Typography id="provider-selection-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 2 }}>
                Provider selection and readiness
              </Typography>
              <Alert severity="info" sx={{ mb: 2 }}>
                Admin Console-selected mappings are the source of truth. Secrets stay as SecretRef handles; readiness tests run only through backend admin APIs.
              </Alert>
              <Stack spacing={2}>
                <FormControl fullWidth>
                  <InputLabel id="provider-category-select-label">Provider category</InputLabel>
                  <Select labelId="provider-category-select-label" id="provider-category-select" value={selectedCategory} label="Provider category" onChange={(event) => changeCategory(event.target.value)}>
                    {controlPlane.providerCategories.map((category) => (
                      <MenuItem key={category.key} value={category.key}>
                        {category.label}
                      </MenuItem>
                    ))}
                  </Select>
                  <FormHelperText>Category-first canonical Weave contracts stay separate from adapter choices.</FormHelperText>
                </FormControl>

                {selectedCategoryDetails ? (
                  <>
                    <FormControl fullWidth>
                      <InputLabel id="provider-candidate-select-label">Selected provider adapter</InputLabel>
                      <Select labelId="provider-candidate-select-label" id="provider-candidate-select" value={providerDraft} label="Selected provider adapter" onChange={(event) => setProviderDraft(event.target.value)}>
                        {selectedCategoryDetails.providerCandidates.map((candidate) => (
                          <MenuItem key={candidate} value={candidate}>
                            {candidate}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <FormControl fullWidth>
                      <InputLabel id="choice-model-select-label">Choice model</InputLabel>
                      <Select labelId="choice-model-select-label" id="choice-model-select" value={choiceModelDraft} label="Choice model" onChange={(event) => setChoiceModelDraft(event.target.value)}>
                        <MenuItem value="recommended_self_hosted_default">recommended self-hosted default</MenuItem>
                        <MenuItem value="external_existing_provider">external existing provider</MenuItem>
                        <MenuItem value="managed_cloud_provider">managed cloud provider</MenuItem>
                      </Select>
                    </FormControl>
                    <Typography>SecretRefs: {selectedCategoryDetails.secretRefs.join(', ') || `secretref://weave/provider/${providerDraft}`}</Typography>
                    <Typography>Never paste raw secrets, bearer tokens, provider URLs with credentials, or downstream diagnostics.</Typography>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                      <Button variant="outlined" onClick={() => void selectProvider(true)}>
                        Dry-run provider selection
                      </Button>
                      <Button variant="contained" onClick={() => void selectProvider(false)}>
                        Apply selected provider
                      </Button>
                      <Button variant="contained" color="secondary" onClick={() => void testReadiness(providerDraft)}>
                        Test readiness through backend
                      </Button>
                    </Stack>
                  </>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="policy-heading">
            <CardContent>
              <Typography id="policy-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 1 }}>
                Policy and whitelist
              </Typography>
              <Alert severity="info" sx={{ mb: 2 }}>
                Policy is deny-by-default. Add one canonical Weave capability per line only after the organization has approved it.
              </Alert>
              <TextField label="Allowed capabilities" value={policyDraft} onChange={(event) => setPolicyDraft(event.target.value)} fullWidth multiline minRows={5} helperText="Example: files.read. Do not paste secrets, provider tokens, raw diagnostics, or provider-specific payloads here." />
              <Button sx={{ mt: 2 }} variant="contained" onClick={() => void savePolicy()}>
                Save whitelist policy
              </Button>
              <Divider sx={{ my: 2 }} />
              <Typography>Blocked examples: {controlPlane.whitelistPolicy.blockedCapabilities.join(', ')}</Typography>
            </CardContent>
          </Card>

          <Card component="section" aria-labelledby="secrets-heading">
            <CardContent>
              <Typography id="secrets-heading" variant="h2" sx={{ fontSize: '1.35rem', mb: 1 }}>
                SecretRef inventory
              </Typography>
              <List aria-label="Support-safe SecretRef handles">
                {controlPlane.providerCategories.flatMap((category) => category.secretRefs.map((secretRef) => ({ category, secretRef }))).map(({ category, secretRef }) => (
                  <ListItem key={`${category.key}-${secretRef}`} alignItems="flex-start">
                    <ListItemText primary={`${category.label}: SecretRef handle`} secondary={`${secretRef} — raw secret exposed: no`} />
                  </ListItem>
                ))}
              </List>
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
              Need member behavior? Use the provider-agnostic Weave Client. Admin/provider setup belongs here and in backend policy. <Link href="/api/organization/manifest">Organization manifest</Link>
            </Typography>
          </Box>
        </Stack>
      </Container>
    </>
  );
}
