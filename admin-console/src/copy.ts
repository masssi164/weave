export const adminConsoleMessages = {
  en: {
    appTitle: 'Weave Organization Admin Console',
    productSlogan: 'Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.',
    loadingStatus: 'Admin Console is loading backend control-plane data.',
    loadedStatus: 'Backend control-plane data loaded.',
    unavailableSampleError:
      'Admin API is unavailable; showing the contract-backed sample state.',
    offlineSampleStatus:
      'Admin API unavailable. Showing support-safe sample data only.',
    offlineSampleWarning:
      'Offline/demo sample state — not live organization status. Do not use sample readiness as approval evidence.',
    yes: 'yes',
    no: 'no',
    none: 'none',
    blocked: 'blocked',
    allowed: 'allowed',
    enabled: 'enabled',
    exposed: 'exposed',
    appBarStatusRole: 'status',
    effectivePolicyHeading: 'Effective policy explanation',
    effectivePolicySummary:
      'Owner/admin choices define provider mappings and whitelist policy. Operators can inspect support-safe readiness. Members receive only stable capability states.',
    roleVisibilityHeading: 'Role visibility boundaries',
    ownerAdminRole: 'Owner/Admin',
    ownerAdminDescription:
      'Configure provider categories, replacement dry-runs, whitelist policy, and apply changes through backend admin APIs.',
    operatorRole: 'Operator',
    operatorDescription:
      'Inspect readiness, audit evidence, and support-safe diagnostics without seeing raw provider secrets or downstream bodies.',
    memberRole: 'Member',
    memberDescription:
      'Use Weave product capabilities with only available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later states.',
    memberPreviewHeading: 'Member capability preview',
    memberPreviewDescription:
      'This preview intentionally hides provider adapters, SecretRefs, tenant URLs, raw diagnostics, and admin-only controls.',
    memberCapabilityStatesLabel: 'Member-visible capability states',
    memberStateLabel: 'Member state',
    memberStateDescription:
      'Members see only the stable capability state for this product area.',
    setupAssistantHeading: 'Guided setup assistant',
    setupAssistantDescription:
      'Admins bind, unbind, validate, switch, or detach provider adapters only through backend admin APIs. Every apply path requires dry-run/preflight, member impact preview, clear consequences, and recovery guidance before an irreversible change.',
    setupAssistantStepsLabel: 'Admin setup assistant steps',
    adminSignInHeading: 'Admin sign-in contract',
    adminSignInDescriptionStart: 'Sign in through OIDC/Keycloak client',
    adminSignInDescriptionEnd:
      'This console calls only Weave backend admin APIs; it does not call identity, chat, files, office, task, meeting, or other providers directly.',
    adminSignInIssuerLabel: 'Issuer',
    adminSignInOpenBrokerButton: 'Open identity broker',
    organizationOverviewHeading: 'Organization overview',
    organizationProviderSourceLabel: 'Provider source of truth',
    organizationBootstrapDefaultsLabel: 'Bootstrap defaults are suggestions only',
    organizationViewerRoleLabel: 'Current viewer role',
    organizationMemberProviderConfigLabel: 'Member clients may configure providers',
    providerCategoriesHeading: 'Provider categories',
    providerStatusLabel: 'Status',
    providerSelectedAdapterLabel: 'Selected adapter',
    providerRealityLevelLabel: 'Reality level',
    providerEvidenceFreshnessLabel: 'Evidence freshness',
    providerMemberImpactLabel: 'Member impact',
    providerRequiredNextActionLabel: 'Required next action',
    providerSafeNextActionLabel: 'Safe next action',
    providerSecretRefStatusLabel: 'SecretRef status',
    providerPolicyStateLabel: 'Policy state',
    providerMigrationStateLabel: 'Migration / dry-run state',
    providerEvidenceRefsLabel: 'Evidence refs',
    providerRestartEvidenceLabel: 'Restart survival evidence',
    providerBackendEvidenceRequired: 'backend evidence required',
    providerBackendRestartEvidenceRequired:
      'backend restart evidence required before persistence claim',
    providerCandidatesLabel: 'Candidates',
    readinessDashboardHeading: 'Readiness dashboard',
    readinessDashboardDescription:
      'Domain readiness is actionable for admins and operators but support-safe by default: provider diagnostics are redacted, SecretRef handles stay out of member contracts, and member preview states remain provider-neutral.',
    readinessDashboardLabel: 'Domain readiness dashboard',
    betaReadinessHeading: 'Beta setup and control readiness preview',
    betaReadinessDescription:
      'This Admin Console preview ties IDM/RBAC, provider adapters, Weaver eligibility, and evidence posture into one screen-reader-friendly checklist before members are invited. It is support-safe: admins see action labels and evidence refs, not raw provider payloads or secrets.',
    betaReadinessChecklistLabel: 'Beta setup and control readiness checklist',
    goLiveHeading: 'Organization go-live readiness',
    goLiveStateLabel: 'State',
    goLiveMemberPreviewLabel: 'member preview',
    goLiveSetupControlsLabel: 'setup controls exposed to normal members',
    goLiveRawDiagnosticsLabel: 'raw provider diagnostics exposed',
    goLiveBlockersLabel: 'Blockers',
    goLiveAdminActionsLabel: 'Admin actions',
    goLiveAuditRefsLabel: 'Audit refs',
    rcClaimHeading: 'RC claim control',
    suiteFacadesHeading: 'Suite facade readiness',
    suiteFacadesDescription:
      'Files/Documents, Boards/Tasks, and Calendar readiness is projected through provider-neutral Weave facades. The backend owns provider mappings; normal member flows never receive raw provider setup or credential-bearing config.',
    identityReadinessHeading: 'Identity provider readiness',
    identityReadinessDescription:
      'Keycloak is the organization identity system of record. This console reads support-safe readiness through Weave APIs; member clients never receive realm internals, administrative URLs, raw errors, or credentials.',
    identityAuthorityNotice:
      'Keycloak is fixed as the central identity authority. Configure LDAP, Active Directory, or external OIDC/SAML connections in the operator-managed Keycloak setup; runtime IDM switching is not supported.',
    providerSelectionHeading: 'Provider selection and readiness',
    providerSelectionDescription:
      'Admin Console-selected mappings are the source of truth. Secrets stay as SecretRef handles; readiness tests run only through backend admin APIs.',
    providerCategoryLabel: 'Provider category',
    providerCategoryHelper:
      'Category-first canonical Weave contracts stay separate from adapter choices.',
    selectedProviderAdapterLabel: 'Selected provider adapter',
    choiceModelLabel: 'Choice model',
    secretRefsLabel: 'SecretRefs',
    providerSecretWarning:
      'Never paste raw secrets, bearer tokens, provider URLs with credentials, or downstream diagnostics.',
    providerApplyPrefix: 'Provider apply is',
    providerApplySuffix:
      'by backend gates, current-session dry-run evidence, and explicit consequence confirmation.',
    providerApplyAllGatesPassed: 'All required evidence gates passed.',
    providerApplyMissingGates: 'Missing gates',
    providerDryRunEvidencePrefix: 'Current-session dry-run evidence is',
    providerDryRunFreshTrusted: 'fresh and trusted',
    providerDryRunMissing:
      'missing, stale, or untrusted',
    providerDryRunPrompt:
      'Run a dry-run for the selected category, adapter, and choice model before apply.',
    providerConsequenceConfirmLabel:
      'I confirm I reviewed member impact, rollback evidence, and provider-switch consequences for this dry-run.',
    dryRunProviderSelectionButton: 'Dry-run provider selection',
    applySelectedProviderButton: 'Apply selected provider',
    testReadinessButton: 'Test readiness through backend',
    replacementHeading: 'Provider replacement dry-run results',
    replacementSummary:
      'Dry-run replacement checks validate adapter swaps before apply. Results show lossy mapping, cutover gates, lifecycle expectations, and member impact only after backend redaction.',
    replacementButton: 'Dry-run replacement contract',
    replacementEmpty:
      'Run a replacement dry-run to review support-safe evidence before applying provider changes.',
    replacementStatusSuccess: 'Replacement dry-run completed',
    weaverProjectionEligibilityLabel: 'Eligibility preview',
    weaverProjectionBlockedWithoutPolicyLabel: 'Blocked without policy',
    weaverProjectionBlockedWithoutGroupLabel: 'blocked without group',
    weaverProjectionProfileVersionLabel: 'Profile version',
    weaverProjectionRuntimeHashLabel: 'RuntimeProfile hash',
    weaverProjectionExpiresLabel: 'expires',
    weaverProjectionAuditRefsLabel: 'Audit receipt refs',
    weaverProjectionRevocationRefsLabel: 'Revocation refs',
    weaverProjectionEligibilityBlockersLabel: 'Eligibility blockers',
    weaverProjectionHeading: 'Weaver RuntimeProfile projection',
    weaverProjectionSummary:
      'Admin Console shows label-only chat, model, tool, skill, and MCP projections before profile regeneration. Raw provider secrets, downstream diagnostics, runtime configuration files, and credential-bearing URLs are never displayed.',
    weaverDistributionHeading: 'Weaver distribution policy',
    weaverDistributionDescription:
      'Admin Console is the source of Weaver Chat, model, tool, skill, and MCP distribution policy. RuntimeProfile regeneration is blocked until readiness, migration, effective policy, revocation, and audit consequences are visible before apply.',
    weaverChatProviderLabel: 'Weaver Chat-domain provider',
    weaverChatProviderHelper:
      'Members keep the stable channels.matrix facade; this selects backend Chat routing/providerRef for profile vNext.',
    weaverModelAliasesLabel: 'Model aliases (alias=provider/model selectable|locked)',
    weaverDefaultModelLabel: 'Default model alias',
    weaverFallbackModelsLabel: 'Fallback model aliases',
    weaverAllowedToolsLabel: 'Allowed Weaver tools',
    weaverAllowedToolsHelper:
      'Canonical Weave domain tools only, e.g. chat.search_messages or notifications.create_action_request.',
    weaverAllowedSkillsLabel: 'Allowed Weaver skills',
    weaverAllowedMcpLabel: 'Allowed MCP servers (server=tool1,tool2 approval-required)',
    weaverMcpRegistryHeading: 'Admin-bound MCP server registry',
    weaverMcpRegistryDescription:
      'Admins bind Streamable HTTP MCP servers for Weaver here; members never wire raw MCP endpoints or runtime tokens.',
    weaverEffectivePolicyPreviewHeading: 'Effective RuntimeProfile policy preview',
    weaverPolicyConfirmLabel:
      'I confirm the effective Weaver policy preview, Chat migration consequences, model fallback order, tool/skill/MCP grants, revocation, and audit refs before apply.',
    saveWeaverPolicyButton: 'Save Weaver distribution policy',
    revokeRuntimeProfileButton: 'Revoke active RuntimeProfile',
    policyWhitelistHeading: 'Policy and whitelist',
    policyWhitelistDescription:
      'Policy is deny-by-default. Add one canonical Weave capability per line only after the organization has approved it.',
    allowedCapabilitiesLabel: 'Allowed capabilities',
    allowedCapabilitiesHelper:
      'Example: files.read. Do not paste secrets, provider tokens, raw diagnostics, or provider-specific payloads here.',
    saveWhitelistPolicyButton: 'Save whitelist policy',
    blockedExamplesLabel: 'Blocked examples',
    secretRefInventoryHeading: 'SecretRef inventory',
    secretRefInventoryLabel: 'Support-safe SecretRef handles',
    auditTrailHeading: 'Audit trail',
    auditTrailLabel: 'Recent admin audit events',
    footerText:
      'Need member behavior? Use the provider-agnostic Weave Client. Admin/provider setup belongs here and in backend policy.',
    organizationManifestLink: 'Organization manifest',
  },
  de: {
    appTitle: 'Weave Organisations-Admin-Konsole',
    productSlogan: 'Weave – Zusammenarbeit nahtlos mit agentischer KI verwoben, aktiviert nach euren Regeln.',
    loadingStatus: 'Die Admin-Konsole lädt Backend-Control-Plane-Daten.',
    loadedStatus: 'Backend-Control-Plane-Daten geladen.',
    unavailableSampleError:
      'Die Admin-API ist nicht erreichbar; der vertragsbasierte Beispielzustand wird angezeigt.',
    offlineSampleStatus:
      'Admin-API nicht erreichbar. Es werden nur support-sichere Beispieldaten angezeigt.',
    offlineSampleWarning:
      'Offline-/Demo-Beispielzustand — kein Live-Organisationsstatus. Beispielbereitschaft nicht als Freigabeevidenz verwenden.',
    yes: 'ja',
    no: 'nein',
    none: 'keine',
    blocked: 'blockiert',
    allowed: 'erlaubt',
    enabled: 'aktiviert',
    exposed: 'sichtbar',
    appBarStatusRole: 'status',
    effectivePolicyHeading: 'Wirksame Richtlinienerklärung',
    effectivePolicySummary:
      'Owner-/Admin-Entscheidungen definieren Provider-Zuordnungen und Whitelist-Richtlinien. Operatoren prüfen support-sichere Bereitschaft. Mitglieder erhalten nur stabile Fähigkeitszustände.',
    roleVisibilityHeading: 'Rollensichtbarkeitsgrenzen',
    ownerAdminRole: 'Owner/Admin',
    ownerAdminDescription:
      'Provider-Kategorien, Ersatz-Dry-Runs und Whitelist-Richtlinien konfigurieren und Änderungen über Backend-Admin-APIs anwenden.',
    operatorRole: 'Operator',
    operatorDescription:
      'Bereitschaft, Auditevidenz und support-sichere Diagnosen prüfen, ohne rohe Provider-Secrets oder Downstream-Inhalte zu sehen.',
    memberRole: 'Mitglied',
    memberDescription:
      'Weave-Produktfähigkeiten nur mit verfügbaren, richtliniengesperrten, nicht konfigurierten, eingeschränkten, nicht verfügbaren oder später kommenden Zuständen nutzen.',
    memberPreviewHeading: 'Mitgliederfähigkeitsvorschau',
    memberPreviewDescription:
      'Diese Vorschau verbirgt Provider-Adapter, SecretRefs, Tenant-URLs, Rohdiagnosen und Admin-Steuerungen bewusst.',
    memberCapabilityStatesLabel: 'Für Mitglieder sichtbare Fähigkeitszustände',
    memberStateLabel: 'Mitgliedszustand',
    memberStateDescription:
      'Mitglieder sehen nur den stabilen Fähigkeitszustand dieses Produktbereichs.',
    setupAssistantHeading: 'Geführter Einrichtungsassistent',
    setupAssistantDescription:
      'Admins binden, lösen, validieren, wechseln oder entfernen Provider-Adapter nur über Backend-Admin-APIs. Jeder Apply-Pfad verlangt Dry-Run/Preflight, Mitgliederfolgenvorschau, klare Konsequenzen und Wiederherstellungshinweise vor einer unumkehrbaren Änderung.',
    setupAssistantStepsLabel: 'Admin-Einrichtungsschritte',
    adminSignInHeading: 'Admin-Anmeldevertrag',
    adminSignInDescriptionStart: 'Anmeldung über OIDC/Keycloak-Client',
    adminSignInDescriptionEnd:
      'Diese Konsole ruft nur Weave-Backend-Admin-APIs auf; sie ruft Identitäts-, Chat-, Datei-, Office-, Aufgaben-, Meeting- oder andere Provider nicht direkt auf.',
    adminSignInIssuerLabel: 'Issuer',
    adminSignInOpenBrokerButton: 'Identity Broker öffnen',
    organizationOverviewHeading: 'Organisationsüberblick',
    organizationProviderSourceLabel: 'Provider Source of Truth',
    organizationBootstrapDefaultsLabel: 'Bootstrap-Defaults sind nur Vorschläge',
    organizationViewerRoleLabel: 'Aktuelle Viewer-Rolle',
    organizationMemberProviderConfigLabel: 'Mitglieder-Clients dürfen Provider konfigurieren',
    providerCategoriesHeading: 'Provider-Kategorien',
    providerStatusLabel: 'Status',
    providerSelectedAdapterLabel: 'Ausgewählter Adapter',
    providerRealityLevelLabel: 'Realitätsgrad',
    providerEvidenceFreshnessLabel: 'Evidenzfrische',
    providerMemberImpactLabel: 'Mitgliederwirkung',
    providerRequiredNextActionLabel: 'Erforderliche nächste Aktion',
    providerSafeNextActionLabel: 'Sichere nächste Aktion',
    providerSecretRefStatusLabel: 'SecretRef-Status',
    providerPolicyStateLabel: 'Richtlinienzustand',
    providerMigrationStateLabel: 'Migration-/Dry-Run-Zustand',
    providerEvidenceRefsLabel: 'Evidenz-Refs',
    providerRestartEvidenceLabel: 'Restart-Survival-Evidenz',
    providerBackendEvidenceRequired: 'Backend-Evidenz erforderlich',
    providerBackendRestartEvidenceRequired:
      'Backend-Restart-Evidenz vor Persistenzbehauptung erforderlich',
    providerCandidatesLabel: 'Kandidaten',
    readinessDashboardHeading: 'Bereitschafts-Dashboard',
    readinessDashboardDescription:
      'Domain-Bereitschaft ist für Admins und Operatoren handlungsfähig, aber standardmäßig support-sicher: Provider-Diagnosen sind redigiert, SecretRef-Handles bleiben aus Mitgliederverträgen heraus, und Mitgliederzustände bleiben provider-neutral.',
    readinessDashboardLabel: 'Domain-Bereitschafts-Dashboard',
    betaReadinessHeading: 'Beta-Einrichtungs- und Steuerungsbereitschaft',
    betaReadinessDescription:
      'Diese Admin-Konsolenvorschau verbindet IDM/RBAC, Provider-Adapter, Weaver-Berechtigung und Evidenzlage in einer screenreader-freundlichen Checkliste, bevor Mitglieder eingeladen werden. Sie ist support-sicher: Admins sehen Aktionslabels und Evidenz-Refs, keine rohen Provider-Payloads oder Secrets.',
    betaReadinessChecklistLabel: 'Beta-Einrichtungs- und Steuerungscheckliste',
    goLiveHeading: 'Organisations-Go-Live-Bereitschaft',
    goLiveStateLabel: 'Zustand',
    goLiveMemberPreviewLabel: 'Mitgliedervorschau',
    goLiveSetupControlsLabel: 'Einrichtungssteuerungen für normale Mitglieder sichtbar',
    goLiveRawDiagnosticsLabel: 'Rohdiagnosen von Providern sichtbar',
    goLiveBlockersLabel: 'Blocker',
    goLiveAdminActionsLabel: 'Admin-Aktionen',
    goLiveAuditRefsLabel: 'Audit-Refs',
    rcClaimHeading: 'RC-Claim-Steuerung',
    suiteFacadesHeading: 'Suite-Fassadenbereitschaft',
    suiteFacadesDescription:
      'Dateien/Dokumente, Boards/Aufgaben und Kalender werden über provider-neutrale Weave-Fassaden projiziert. Das Backend besitzt Provider-Zuordnungen; normale Mitgliederflüsse erhalten keine rohen Provider-Setup- oder credentialtragenden Konfigurationen.',
    identityReadinessHeading: 'Identitätsprovider-Bereitschaft',
    identityReadinessDescription:
      'Keycloak ist das führende Identitätssystem der Organisation. Diese Konsole liest support-sichere Bereitschaft über Weave-APIs; Mitglieder-Clients erhalten keine Realm-Interna, administrativen URLs, Rohfehler oder Zugangsdaten.',
    identityAuthorityNotice:
      'Keycloak ist als zentrale Identitätsinstanz festgelegt. LDAP, Active Directory oder externe OIDC-/SAML-Verbindungen werden im operatorverwalteten Keycloak-Setup konfiguriert; ein IDM-Wechsel zur Laufzeit wird nicht unterstützt.',
    providerSelectionHeading: 'Provider-Auswahl und Bereitschaft',
    providerSelectionDescription:
      'In der Admin-Konsole gewählte Zuordnungen sind Source of Truth. Secrets bleiben SecretRef-Handles; Bereitschaftstests laufen nur über Backend-Admin-APIs.',
    providerCategoryLabel: 'Provider-Kategorie',
    providerCategoryHelper:
      'Kategorie-erste kanonische Weave-Verträge bleiben von Adapterentscheidungen getrennt.',
    selectedProviderAdapterLabel: 'Ausgewählter Provider-Adapter',
    choiceModelLabel: 'Auswahlmodell',
    secretRefsLabel: 'SecretRefs',
    providerSecretWarning:
      'Keine rohen Secrets, Bearer Tokens, Provider-URLs mit Credentials oder Downstream-Diagnosen einfügen.',
    providerApplyPrefix: 'Provider-Apply ist',
    providerApplySuffix:
      'durch Backend-Gates, aktuelle Dry-Run-Evidenz und explizite Konsequenzbestätigung.',
    providerApplyAllGatesPassed: 'Alle erforderlichen Evidenz-Gates sind bestanden.',
    providerApplyMissingGates: 'Fehlende Gates',
    providerDryRunEvidencePrefix: 'Aktuelle Dry-Run-Evidenz ist',
    providerDryRunFreshTrusted: 'frisch und vertrauenswürdig',
    providerDryRunMissing:
      'fehlend, veraltet oder nicht vertrauenswürdig',
    providerDryRunPrompt:
      'Vor Apply einen Dry-Run für die ausgewählte Kategorie, den Adapter und das Auswahlmodell ausführen.',
    providerConsequenceConfirmLabel:
      'Ich bestätige, dass ich Mitgliederwirkung, Rollback-Evidenz und Provider-Wechsel-Konsequenzen für diesen Dry-Run geprüft habe.',
    dryRunProviderSelectionButton: 'Provider-Auswahl als Dry-Run prüfen',
    applySelectedProviderButton: 'Ausgewählten Provider anwenden',
    testReadinessButton: 'Bereitschaft über Backend testen',
    replacementHeading: 'Provider-Ersatz-Dry-Run-Ergebnisse',
    replacementSummary:
      'Ersatz-Dry-Runs validieren Adapterwechsel vor Apply. Ergebnisse zeigen verlustbehaftete Zuordnung, Cutover-Gates, Lifecycle-Erwartungen und Mitgliederwirkung erst nach Backend-Redaktion.',
    replacementButton: 'Ersatzvertrag als Dry-Run prüfen',
    replacementEmpty:
      'Führe einen Ersatz-Dry-Run aus, um support-sichere Evidenz vor Provider-Änderungen zu prüfen.',
    replacementStatusSuccess: 'Ersatz-Dry-Run abgeschlossen',
    weaverProjectionEligibilityLabel: 'Berechtigungsvorschau',
    weaverProjectionBlockedWithoutPolicyLabel: 'Blockiert ohne Richtlinie',
    weaverProjectionBlockedWithoutGroupLabel: 'blockiert ohne Gruppe',
    weaverProjectionProfileVersionLabel: 'Profilversion',
    weaverProjectionRuntimeHashLabel: 'RuntimeProfile-Hash',
    weaverProjectionExpiresLabel: 'läuft ab',
    weaverProjectionAuditRefsLabel: 'Audit-Receipt-Refs',
    weaverProjectionRevocationRefsLabel: 'Widerrufs-Refs',
    weaverProjectionEligibilityBlockersLabel: 'Berechtigungsblocker',
    weaverProjectionHeading: 'Weaver RuntimeProfile-Projektion',
    weaverProjectionSummary:
      'Die Admin-Konsole zeigt nur Label-Projektionen für Chat, Modelle, Tools, Skills und MCP vor der Profilregeneration. Rohe Provider-Secrets, Downstream-Diagnosen, Runtime-Konfigurationsdateien und credentialtragende URLs werden nie angezeigt.',
    weaverDistributionHeading: 'Weaver-Verteilungsrichtlinie',
    weaverDistributionDescription:
      'Die Admin-Konsole ist Source of Truth für Weaver Chat-, Modell-, Tool-, Skill- und MCP-Verteilungsrichtlinien. RuntimeProfile-Regeneration bleibt blockiert, bis Bereitschaft, Migration, wirksame Richtlinie, Widerruf und Audit-Konsequenzen vor Apply sichtbar sind.',
    weaverChatProviderLabel: 'Weaver Chat-Domain-Provider',
    weaverChatProviderHelper:
      'Mitglieder behalten die stabile channels.matrix-Fassade; dies wählt Backend-Chat-Routing/providerRef für profile vNext.',
    weaverModelAliasesLabel: 'Modell-Aliase (alias=provider/model selectable|locked)',
    weaverDefaultModelLabel: 'Standardmodell-Alias',
    weaverFallbackModelsLabel: 'Fallback-Modell-Aliase',
    weaverAllowedToolsLabel: 'Erlaubte Weaver-Tools',
    weaverAllowedToolsHelper:
      'Nur kanonische Weave-Domain-Tools, z. B. chat.search_messages oder notifications.create_action_request.',
    weaverAllowedSkillsLabel: 'Erlaubte Weaver-Skills',
    weaverAllowedMcpLabel: 'Erlaubte MCP-Server (server=tool1,tool2 approval-required)',
    weaverMcpRegistryHeading: 'Admin-gebundene MCP-Server-Registry',
    weaverMcpRegistryDescription:
      'Admins binden Streamable-HTTP-MCP-Server für Weaver hier; Mitglieder verdrahten nie rohe MCP-Endpunkte oder Runtime-Tokens.',
    weaverEffectivePolicyPreviewHeading: 'Wirksame RuntimeProfile-Richtlinienvorschau',
    weaverPolicyConfirmLabel:
      'Ich bestätige die wirksame Weaver-Richtlinienvorschau, Chat-Migrationskonsequenzen, Modell-Fallback-Reihenfolge, Tool-/Skill-/MCP-Gewährungen, Widerruf und Audit-Refs vor Apply.',
    saveWeaverPolicyButton: 'Weaver-Verteilungsrichtlinie speichern',
    revokeRuntimeProfileButton: 'Aktives RuntimeProfile widerrufen',
    policyWhitelistHeading: 'Richtlinie und Whitelist',
    policyWhitelistDescription:
      'Richtlinie ist deny-by-default. Pro Zeile nur eine kanonische Weave-Fähigkeit hinzufügen, nachdem die Organisation sie freigegeben hat.',
    allowedCapabilitiesLabel: 'Erlaubte Fähigkeiten',
    allowedCapabilitiesHelper:
      'Beispiel: files.read. Keine Secrets, Provider-Tokens, Rohdiagnosen oder provider-spezifischen Payloads einfügen.',
    saveWhitelistPolicyButton: 'Whitelist-Richtlinie speichern',
    blockedExamplesLabel: 'Blockierte Beispiele',
    secretRefInventoryHeading: 'SecretRef-Inventar',
    secretRefInventoryLabel: 'Support-sichere SecretRef-Handles',
    auditTrailHeading: 'Audit-Trail',
    auditTrailLabel: 'Aktuelle Admin-Auditereignisse',
    footerText:
      'Mitgliederverhalten prüfen? Nutze den provider-agnostischen Weave Client. Admin-/Provider-Setup gehört hierher und in die Backend-Richtlinie.',
    organizationManifestLink: 'Organisationsmanifest',
  },
} as const;

export type AdminConsoleLocale = keyof typeof adminConsoleMessages;

export function adminCopy(locale: AdminConsoleLocale = 'en') {
  return adminConsoleMessages[locale];
}
