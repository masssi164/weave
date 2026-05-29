export const adminConsoleMessages = {
  en: {
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
    memberStateLabel: 'Member state',
    memberStateDescription:
      'Members see only the stable capability state for this product area.',
    replacementHeading: 'Provider replacement dry-run results',
    replacementSummary:
      'Dry-run replacement checks validate adapter swaps before apply. Results show lossy mapping, cutover gates, lifecycle expectations, and member impact only after backend redaction.',
    replacementButton: 'Dry-run replacement contract',
    replacementEmpty:
      'Run a replacement dry-run to review support-safe evidence before applying provider changes.',
    replacementStatusSuccess: 'Replacement dry-run completed',
  },
} as const;

export type AdminConsoleLocale = keyof typeof adminConsoleMessages;

export function adminCopy(locale: AdminConsoleLocale = 'en') {
  return adminConsoleMessages[locale];
}
