# Office Provider Architecture: ONLYOFFICE Community Default

Status: planning recommendation for issue [#235](https://github.com/masssi164/weave/issues/235)
Date: 2026-05-22
Scope: Weave Office provider architecture, default provider choice, launch/capability boundary, and first integration slice

## Decision

Use **ONLYOFFICE Docs Community** as the default Office provider candidate for Weave because it is free, self-hostable, and commercially usable for normal internal/commercial-organization use when AGPL obligations and branding/trademark constraints are respected.

Classify **Collabora Online / CODE** as a non-default, later adapter candidate with licensing/product-use risk for the current requirement. Collabora's source-code licensing is open, but Collabora-published executable forms are described by Collabora as carrying additional proprietary conditions; CODE is explicitly the development edition, "perfect for testing, home use or small teams" and "not recommended for production environments." That does not meet Weave's requirement for a clear free commercially usable default.

For the first integration slice, use the **Nextcloud ONLYOFFICE connector app** as the upstream integration route, but keep it behind a Weave backend facade. Flutter must never talk directly to ONLYOFFICE, Nextcloud connector internals, or provider-specific config endpoints.

## Product rule: fail closed until configured

Weave must not claim that office editing works until a provider is configured and verified.

Initial UI states should be:

- `unavailable`: no Office provider configured.
- `configuring`: backend knows a provider candidate, but health/capability checks are incomplete.
- `viewOnly`: provider can open/view the file, but edit readiness is false or user/file permissions do not allow editing.
- `editable`: provider is healthy, file type is supported for the requested edit mode, user has edit permission, and a launch session can be created.
- `error`: support-safe reason such as `provider_unreachable`, `document_server_unreachable`, `unsupported_file_type`, `permission_denied`, `license_or_limit_unverified`, or `session_not_ready`.

Until the state is `editable`, CTA text must be explicit: "Office provider not configured" or "Open/view only" rather than "Edit".

## Provider boundary

The backend owns provider setup, health, capabilities, launch sessions, and provider-specific error translation. Flutter only consumes Weave product state.

```text
Flutter Office surface
  -> Weave backend Office facade
     -> provider adapter: nextcloud_onlyoffice (default)
        -> Nextcloud ONLYOFFICE app
           -> ONLYOFFICE Docs Community Document Server
     -> provider adapter: collabora (non-default/later)
```

Flutter receives normalized state only:

- available actions: open/view/edit/comment/review/fill forms/download/print/copy
- resolved file support and conversion warning
- user-facing readiness state
- launch descriptor returned by the backend
- support-safe diagnostics id/message

Provider-specific details remain backend-only:

- ONLYOFFICE Document Server URL/JWT secret
- Nextcloud connector settings and `occ onlyoffice:documentserver --check` result
- ONLYOFFICE editor config, callback URL, document key, and save callback handling
- provider health probes and version/license observations
- provider-specific permissions and conversion settings

## `OfficeProvider` contract

The backend facade should expose a provider-neutral contract before any UI claims editing support.

### Provider identity and policy

- `providerId`: stable id, e.g. `nextcloud_onlyoffice`, `onlyoffice_direct`, `collabora_wopi`.
- `providerName`: display name for diagnostics, not a product-model dependency.
- `defaultEligible`: true only when the provider currently meets Weave's default criteria.
- `licenseProfile`: `agpl_with_branding`, `commercial`, `mpl_source_with_proprietary_binaries`, or `unknown`.
- `licenseRisk`: `low`, `review_required`, `blocked_for_default`.
- `configured`: whether admin configuration exists.
- `verifiedAt`: timestamp of last health/capability verification.

### Capabilities

- `open`: provider can create a launch session for the file in any supported mode.
- `view`: provider can show the file without mutation.
- `edit`: provider can persist edits for this file and user.
- `comment`: provider supports comments for this file/mode.
- `review`: provider supports review/track-changes for this file/mode.
- `fillForms`: provider supports form filling for this file/mode.
- `coEdit`: provider supports collaborative editing; include mode, e.g. `fast`, `strict`, or provider-native.
- `conversion`: provider can convert legacy/ODF formats for editing; include `lossRisk` and target format.
- `export`: provider can export/download converted output.
- `preview`: provider can generate thumbnails/previews, if enabled.

Capabilities are per provider plus per file because `docx` edit readiness is not the same as `pdf` view or `odt` conversion-to-edit readiness.

### Supported file types

Minimum normalized groups:

- `wordNativeEdit`: `docx` as the first safe edit target.
- `spreadsheetNativeEdit`: `xlsx` as the first safe edit target.
- `presentationNativeEdit`: `pptx` as the first safe edit target.
- `textEdit`: `txt`, `csv` where provider settings allow it.
- `pdf`: view by default; fill/edit only when provider capability and file type allow it.
- `openDocumentConversion`: `odt`, `ods`, `odp`, `rtf` via conversion to OOXML, with data-loss warning.
- `legacyConversion`: `doc`, `xls`, `ppt` and template/macro variants via conversion/export only after explicit backend capability declaration.
- `viewOnly`: `pdf`, diagrams, or any provider-declared view-only type.

The first implementation should enable editing only for configured, verified OOXML-safe formats (`docx`, `xlsx`, `pptx`) plus `txt`/`csv` if the Nextcloud connector setting says they open for editing. Other formats should surface conversion warnings or view-only state.

### Permissions

Map upstream permissions into Weave actions:

- `canView`
- `canEdit`
- `canComment`
- `canReview`
- `canFillForms`
- `canDownload`
- `canPrint`
- `canCopy`
- `canShare`
- `canUseMacros`

Permission output must include a support-safe denial reason. Do not expose raw provider secrets, URLs with tokens, or document keys.

### Lock and session readiness

Expose explicit readiness instead of assuming that a file can be edited once it is visible:

- `sessionState`: `not_configured`, `provider_unhealthy`, `file_unsupported`, `permission_denied`, `lock_pending`, `ready`, `active`, `saving`, `save_failed`, `closed`.
- `lockState`: `none`, `provider_lock`, `coedit_session`, `conflict`, `unknown`.
- `lockOwner`: optional provider-neutral display metadata, redacted where needed.
- `coEditMode`: `fast`, `strict`, `none`, or `unknown`.
- `saveModel`: e.g. `callback_after_close`, `autosave`, `manual_save`, `wopi_save`.
- `documentVersionKey`: opaque backend-issued key or revision reference.
- `expiresAt`: launch/session expiry.
- `requiresRefresh`: session can no longer be trusted and must be recreated.

### Provider health

- `overall`: `healthy`, `degraded`, `unhealthy`, `unknown`.
- `checks`: document server reachability, connector reachability, backend callback reachability, JWT/token alignment, supported version, storage read/write callback path, and optional conversion availability.
- `version`: observed provider/document-server version when available.
- `licenseObserved`: community/commercial/unknown when available; never infer commercial rights from a running container alone.
- `limitObserved`: any discovered concurrency/user/connection limit or `unknown`.
- `lastError`: redacted category and correlation id.

### Launch mode

- `nextcloud_app`: backend returns a launch descriptor that opens the Nextcloud ONLYOFFICE route; Nextcloud app creates the ONLYOFFICE editor config and handles callbacks.
- `backend_mediated_onlyoffice`: backend constructs the ONLYOFFICE editor config, signs it, serves file URLs, and handles callback saves directly.
- `wopi`: backend implements WOPI host semantics and launches a WOPI client.
- `external`: backend returns a safe external URL when native embedding is not available.

Launch descriptors must be short-lived and must not contain reusable provider admin secrets.

## Recommended first slice: Nextcloud ONLYOFFICE connector behind the backend facade

Prefer the Nextcloud ONLYOFFICE app for the first integration because:

- Weave already has a Nextcloud-oriented files direction, so storage, auth, sharing, and file permissions can remain anchored in Nextcloud for the first slice.
- ONLYOFFICE's official Nextcloud integration already prepares editor config, callback URLs, document keys, JWT-secured traffic, connector settings, supported formats, and save callback flow.
- The connector exposes admin-level connection checks (`occ onlyoffice:documentserver --check`) and documented settings for default editable formats and conversion behavior.
- It avoids building a direct document-storage/callback service before Weave has proven the product UX and capability contract.

The backend still mediates product state:

1. Discover whether Nextcloud has the ONLYOFFICE app enabled and configured.
2. Run or consume a health/capability check for the Document Server and connector.
3. Resolve file type, user permissions, and connector settings.
4. Return `OfficeFileState` plus a launch descriptor only when ready.
5. Translate provider failures into stable Weave errors.

Do **not** implement a direct backend-mediated ONLYOFFICE launch first unless the Nextcloud app blocks accessibility, mobile, auth, or file-permission requirements. Direct launch is a later slice that requires Weave to own document download URLs, editor config signing, callback handling, save semantics, locks/conflicts, and conversion policy.

## ONLYOFFICE Community constraints to document in-product/admin docs

- License: ONLYOFFICE DocumentServer and the Nextcloud connector are AGPL-3.0 licensed in their public repositories.
- Commercial use: ONLYOFFICE states that common commercial organizations can freely use ONLYOFFICE Open Source Community internally/on internet or intranet servers.
- AGPL network obligation: if Weave modifies ONLYOFFICE or creates derivative work made available over a network, the corresponding modified/derivative source obligations must be reviewed. Treat unmodified separate-service deployment as lower risk, but legal review is still required before bundling/distribution.
- Branding/logo: Community builds must retain ONLYOFFICE branding and copyright/legal notices; white-labeling/logo removal requires commercial licensing. The 9.4 release also states trademark rights are governed separately by ONLYOFFICE trademark policy.
- Support/SLA: Community products do not include guaranteed support, SLA, clustering, or enterprise scalability.
- Community limits: official pages conflict as of 2026-05-22. The Community Licensing FAQ still says Docs Community is limited to 20 simultaneous document editing connections, while the ONLYOFFICE Docs 9.4 release post says the open-source Community version removes the 20 simultaneous connection limit starting with version 9.4. Weave must record the observed installed version and not promise unlimited concurrent editing until the running provider/version confirms it.
- Edition comparison: ONLYOFFICE's comparison page still says Community has "up to 20 recommended" users, AGPL v3 license, community support, no admin panel/white label/mobile web editors in Community, and config-file based security/WOPI/HTTPS settings.

## Collabora non-default rationale

Collabora remains technically interesting, especially for WOPI-oriented deployments, but it is not the default for #235:

- CODE is Collabora Online Development Edition, not the production product; Collabora's own CODE page says it is suitable for testing/home/small teams and not recommended for production environments.
- Collabora's MPLv2 terms say Source Code Form is primarily MPLv2, while Executable Forms are distributed with additional conditions under a proprietary license.
- Collabora's EULA/subscription terms grant organization use subject to subscription entitlements and include a 30-day evaluation license path for evaluation versions.
- That creates ambiguity for a "free and commercially usable default" when relying on Collabora-published binaries rather than building, branding, and supporting the MPL source ourselves.

Future Collabora work should be a separate provider spike with legal/product review, not a hidden default fallback.

## Follow-up slices

1. Add backend `OfficeProvider` capability DTOs and support-safe error taxonomy.
2. Add Nextcloud ONLYOFFICE adapter discovery and health checks.
3. Add `OfficeFileState` endpoint for a single file id/path.
4. Add launch descriptor endpoint with short-lived sessions and redacted diagnostics.
5. Add Flutter fail-closed Office actions using only backend product state.
6. Add integration tests with a mocked provider: unconfigured, view-only, unsupported, permission denied, healthy editable.
7. Add an operational admin doc: ONLYOFFICE Community version, AGPL notice, branding requirements, support/SLA limitations, and observed concurrency/user limit.
8. Open a later direct-ONLYOFFICE/WOPI architecture spike only if the Nextcloud connector route blocks required UX or deployment needs.

## Sources

- Issue #235: https://github.com/masssi164/weave/issues/235
- ONLYOFFICE Docs Community Licensing FAQ: https://helpcenter.onlyoffice.com/docs/faq/docs-community.aspx
- ONLYOFFICE Docs 9.4 release/license update: https://www.onlyoffice.com/blog/2026/05/onlyoffice-docs-9-4
- ONLYOFFICE License FAQ: https://www.onlyoffice.com/license-faq
- ONLYOFFICE Docs edition comparison: https://www.onlyoffice.com/compare-editions
- ONLYOFFICE Nextcloud integration guide: https://helpcenter.onlyoffice.com/integration/nextcloud.aspx
- ONLYOFFICE DocumentServer repository/license: https://github.com/ONLYOFFICE/DocumentServer/blob/master/LICENSE
- ONLYOFFICE Nextcloud connector repository/license: https://github.com/ONLYOFFICE/onlyoffice-nextcloud/blob/master/LICENSE
- ONLYOFFICE Docs API config: https://api.onlyoffice.com/docs/docs-api/usage-api/config/
- ONLYOFFICE Docs API editor config: https://api.onlyoffice.com/docs/docs-api/usage-api/config/editor/
- ONLYOFFICE Docs API document permissions: https://api.onlyoffice.com/docs/docs-api/usage-api/config/document/permissions/
- ONLYOFFICE Docs API saving flow: https://api.onlyoffice.com/docs/docs-api/get-started/how-it-works/saving-file/
- Collabora CODE page: https://www.collaboraonline.com/code/
- Collabora Online MPLv2 terms: https://www.collaboraonline.com/terms/collabora-online-mplv2/
- Collabora End User License and Subscription Agreement: https://www.collaboraonline.com/end-user-license-and-subscription-agreement/

NotebookLM was not used; direct official docs and repository license checks were sufficient for this planning slice.
