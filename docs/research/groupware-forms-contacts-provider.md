# Groupware Forms and Contacts Provider Recommendation

Status: provider research and backend-facade architecture recommendation
Date: 2026-05-22
Issue: [masssi164/weave#234](https://github.com/masssi164/weave/issues/234)
Scope: Forms and Contacts as future Weave groupware capabilities behind `weave-backend`

## 1. Recommendation summary

Use **Nextcloud Forms** and **Nextcloud Contacts/CardDAV** as the first provider candidates for Weave Forms and Contacts, but expose them only through provider-neutral backend facade contracts.

Recommended direction:

- **Forms provider:** Nextcloud Forms API v3 behind a `FormsProvider` backend port.
- **Contacts provider:** Nextcloud Contacts as the UI/app package and Nextcloud Server CardDAV as the protocol/backend behind a `ContactsProvider` backend port.
- **Frontend boundary:** Flutter consumes Weave-owned models and backend endpoints only. It must not call Nextcloud Forms, OCS, CardDAV, or raw DAV URLs directly.
- **Backend boundary:** `weave-backend` owns provider authentication, authorization, provider ID mapping, pagination/cursors, export jobs, support-safe errors, and redaction.
- **Product model:** keep Forms and Contacts provider-neutral. Nextcloud IDs, DAV paths, OCS metadata, vCard internals, app passwords, bearer tokens, and raw provider URLs stay out of normal frontend responses.

This should be implemented as an incremental, read-first integration. Safe writes can follow once backend-owned actor/delegation, audit, and idempotency rules are explicit.

## 2. Sources

### Nextcloud Forms

- Nextcloud Forms repository: <https://github.com/nextcloud/forms>
- Forms API v3 documentation: <https://github.com/nextcloud/forms/blob/main/docs/API_v3.md>
- Forms API v3 machine-readable OpenAPI source: <https://github.com/nextcloud/forms/blob/main/openapi.json>
- Forms API data structure documentation: <https://github.com/nextcloud/forms/blob/main/docs/DataStructure.md>
- Forms API controller source: <https://github.com/nextcloud/forms/blob/main/lib/Controller/ApiController.php>
- Forms share API controller source: <https://github.com/nextcloud/forms/blob/main/lib/Controller/ShareApiController.php>
- Forms license/source metadata: <https://github.com/nextcloud/forms/blob/main/README.md>, <https://github.com/nextcloud/forms/blob/main/COPYING>

Key API facts from the Forms docs/source:

- Base URL for Forms API calls is `<nextcloud_base_url>/ocs/v2.php/apps/forms`.
- OCS API requests require `OCS-APIRequest: true`; JSON responses require `Accept: application/json`.
- API v3 docs identify `openapi.json` as the preferred source of truth for exact request/response shapes.
- Useful first-slice endpoints include:
  - `GET /api/v3/forms[?type=owned]`
  - `GET /api/v3/forms?type=shared`
  - `GET /api/v3/forms/{formId}`
  - `GET /api/v3/forms/{formId}/questions`
  - `GET /api/v3/forms/{formId}/submissions`
  - `GET /api/v3/forms/{formId}/submissions?fileFormat={fileFormat}` for download/export
  - `POST /api/v3/forms/{formId}/submissions/export` for cloud export

### Nextcloud Contacts and CardDAV

- Nextcloud Contacts repository: <https://github.com/nextcloud/contacts>
- Contacts README/license/source notes: <https://github.com/nextcloud/contacts/blob/main/README.md>, <https://github.com/nextcloud/contacts/blob/main/COPYING>
- Nextcloud Contacts user docs: <https://docs.nextcloud.com/server/latest/user_manual/en/groupware/contacts.html>
- Nextcloud iOS CardDAV setup docs: <https://docs.nextcloud.com/server/latest/user_manual/en/groupware/sync_ios.html>
- Nextcloud Android/DAVx5 setup docs: <https://docs.nextcloud.com/server/latest/user_manual/en/groupware/sync_android.html>
- Nextcloud Server DAV root/source wiring: <https://github.com/nextcloud/server/blob/master/apps/dav/lib/RootCollection.php>
- Nextcloud Server CardDAV backend source: <https://github.com/nextcloud/server/blob/master/apps/dav/lib/CardDAV/CardDavBackend.php>
- Nextcloud Server license/source metadata: <https://github.com/nextcloud/server/blob/master/README.md>, <https://github.com/nextcloud/server/blob/master/COPYING>

Key Contacts/CardDAV facts from docs/source:

- The Contacts app is a Nextcloud app for syncing and editing contacts and is based on SabreDAV.
- The Contacts README states that the repository manages the frontend and that CardDAV backend issues belong to Nextcloud Server.
- The user docs state Contacts is not enabled by default in Nextcloud and must be installed separately from the App Store.
- The user docs state the system address book may be read-only and that CardDAV URLs are available in Contacts settings.
- Nextcloud CardDAV setup examples use `/remote.php/dav/principals/users/{username}/` for principal discovery and `/remote.php/dav` as the DAV base URL.
- Nextcloud Server DAV source wires `principals/users` and CardDAV address book roots through `apps/dav`; `CardDavBackend` exposes address-book/card CRUD and sync methods such as `getAddressBooksForUser`, `getCards`, `getMultipleCards`, `createCard`, `updateCard`, `deleteCard`, and `getChangesForAddressBook`.

## 3. License and commercial-use summary

Both recommended provider families are free/open-source and commercially usable, with copyleft obligations.

- **Nextcloud Forms:** repository metadata uses AGPL-family licensing. Current source headers/docs show `AGPL-3.0-only` in app metadata and docs; package metadata should be rechecked before vendoring or modifying because at least one package file advertises `AGPL-3.0-or-later`.
- **Nextcloud Contacts:** repository README uses `SPDX-License-Identifier: AGPL-3.0-or-later`.
- **Nextcloud Server DAV/CardDAV:** repository README uses `SPDX-License-Identifier: AGPL-3.0-or-later` and `COPYING` contains the GNU Affero General Public License.

Commercial use is allowed: AGPL permits running, copying, modifying, conveying, and charging for software, subject to license obligations. Important obligations for Weave planning:

- preserve license notices when distributing provider code;
- publish corresponding source for modified AGPL-covered provider code when the AGPL network-use/distribution conditions apply;
- avoid copying provider implementation code into proprietary/non-compatible Weave code unless licensing is reviewed;
- prefer protocol/API integration from `weave-backend` over vendoring or modifying provider code.

This is an engineering summary, not legal advice. Exact obligations should be reviewed before shipping modified Nextcloud apps, modified Nextcloud Server, or bundled provider distributions.

## 4. Provider-neutral product models

### 4.1 Forms model

Use Weave-owned concepts and keep provider details in diagnostic-only metadata.

```text
FormDefinition
- id: Weave form id
- title
- description
- status: draft | open | closed | archived | unavailable
- ownerRef: Weave actor/context reference
- contextRef: workspace/team/channel/profile context
- createdAt, updatedAt, expiresAt?
- permissions: canView | canEdit | canShare | canSubmit | canViewResults | canExport
- questionCount
- submissionCount?
- providerRefs: support-safe opaque refs only

FormQuestion
- id
- formId
- order
- type: shortText | longText | singleChoice | multipleChoice | dropdown | date | file | rating | unknown
- label
- description?
- required
- options[]
- validationHints
- providerRefs

FormOption
- id
- questionId
- label
- order
- providerRefs

FormSubmissionSummary
- formId
- totalCount
- filteredCount?
- latestSubmittedAt?
- submittedByMode: named | anonymous | mixed | unknown
- questionSummaries[]
- exportAvailable: boolean
- providerRefs

FormSubmissionExport
- id
- formId
- format: csv | ods | xlsx
- status: queued | running | ready | failed | expired
- filename?
- sizeBytes?
- createdAt
- expiresAt?
- downloadRef: backend-owned, short-lived reference; never raw Nextcloud URL/token
```

Avoid provider leakage:

- do not expose Nextcloud `hash`, raw form URLs, OCS envelopes, `ownerId`, internal share IDs, or file paths as product fields;
- preserve provider refs only as opaque support-safe references such as `provider=forms:nextcloud`, `externalIdHash`, `etag`, and `lastSyncedAt`;
- normalize unknown provider question types to `unknown` with backend capability flags so the UI can fail closed.

### 4.2 Contacts model

Use address books and contact summaries without exposing vCard as the primary Flutter model.

```text
AddressBook
- id: Weave address-book id
- displayName
- description?
- scope: personal | workspace | team | channel | system | external | unknown
- readOnly
- canSearch
- canCreate
- canUpdate
- canDelete
- syncState: current | stale | unavailable | unknown
- providerRefs

ContactSummary
- id: Weave contact id
- addressBookId
- displayName
- givenName?
- familyName?
- organization?
- jobTitle?
- avatarRef?
- primaryEmail?
- primaryPhone?
- tags[]
- groups[]
- updatedAt?
- source: userAddressBook | systemAddressBook | sharedAddressBook | imported | unknown
- providerRefs

ContactDetail
- summary fields
- emails[]
- phones[]
- addresses[]
- urls[]
- notes?
- birthday?
- pronouns?
- customFields[]: typed, redacted by default unless explicitly supported
- versionRef: backend-owned optimistic concurrency reference

ContactSearchResult
- contactId
- displayName
- matchedFields: name | email | phone | organization | tag | unknown
- snippet: support-safe, no raw vCard dump
- addressBookId
```

Avoid provider leakage:

- Flutter should not receive raw `addressbooks/users/{user}/{book}/{card}.vcf` paths;
- raw vCard can be exported later through a backend export endpoint, not embedded in normal UI DTOs;
- contact photos should be backend-mediated `avatarRef`/media endpoints or stable cache refs, not raw CardDAV URLs.

## 5. Backend provider ports

These are backend-internal ports, not frontend APIs. Public endpoint shape should be specified in `weave-backend` once the first implementation slice is selected.

### 5.1 `FormsProvider`

```text
FormsProvider
- capabilities(actor, context): FormsCapabilities
- listForms(actor, context, filter, page): Page<FormDefinition>
- getForm(actor, context, formId): FormDefinition
- listQuestions(actor, context, formId): List<FormQuestion>
- getSubmissionSummary(actor, context, formId, filter): FormSubmissionSummary
- requestSubmissionExport(actor, context, formId, format, filter): FormSubmissionExport
- getExportStatus(actor, exportId): FormSubmissionExport

Safe-write candidates after read slice:
- createDraftForm(actor, context, command): FormDefinition
- updateDraftForm(actor, context, formId, patch, versionRef): FormDefinition
- publishForm(actor, context, formId, versionRef): FormDefinition
- closeForm(actor, context, formId, versionRef): FormDefinition
- submitResponse(actor, context, formId, command): SubmissionReceipt
```

Port requirements:

- enforce Weave context authorization before provider calls;
- translate provider permissions (`edit`, `results`, `submit`) into Weave permissions;
- apply idempotency keys for create/export/submit commands;
- return opaque pagination/export refs;
- redact all downstream auth and raw paths from logs and errors.

### 5.2 `ContactsProvider`

```text
ContactsProvider
- capabilities(actor, context): ContactsCapabilities
- listAddressBooks(actor, context, filter): List<AddressBook>
- searchContacts(actor, context, query, page): Page<ContactSearchResult>
- getContactSummary(actor, context, contactId): ContactSummary
- getContactDetail(actor, context, contactId, fieldPolicy): ContactDetail
- listContactGroups(actor, context, addressBookId): List<ContactGroup>

Safe-write candidates after read slice:
- createContact(actor, context, addressBookId, command, idempotencyKey): ContactDetail
- updateContact(actor, context, contactId, patch, versionRef): ContactDetail
- archiveOrDeleteContact(actor, context, contactId, versionRef): ContactMutationResult
- importVCard(actor, context, addressBookId, uploadRef, mode): ContactImportResult
```

Port requirements:

- treat system/shared/read-only address books as read-only unless provider capabilities prove otherwise;
- never use a backend service account to mutate arbitrary private user address books without explicit delegation/sharing/user consent;
- map CardDAV/vCard ETags or sync tokens to backend-owned `versionRef`/sync cursors;
- normalize DAV status codes and SabreDAV errors into Weave errors.

## 6. Initial read-only and safe-write scopes

### 6.1 Forms

Initial read-only scope:

- list accessible owned/shared forms;
- fetch form detail/questions/options;
- fetch submission summary/counts and latest submission metadata;
- request/export results as CSV/ODS/XLSX through a backend job/ref;
- expose capability flags for unsupported question types, anonymous mode, file questions, result visibility, and export support.

Safe-write candidates for the next slice:

- create draft form in a Weave context;
- update draft metadata/questions/options before publishing;
- close/reopen form if supported and authorized;
- submit a response only after product UX, validation, anti-abuse, file-upload handling, and anonymous/named-submission policy are specified.

Explicitly out of first slice:

- deleting forms or submissions;
- changing ownership;
- exposing public link-share URLs as normal product fields;
- raw file-question uploads from Flutter to Nextcloud;
- arbitrary share mutations until Weave context sharing is mapped.

### 6.2 Contacts

Initial read-only scope:

- list visible address books with `readOnly` and scope metadata;
- search contacts by name/email/organization where provider supports it;
- show contact summaries and details under a backend field policy;
- expose contact groups/tags as labels when available;
- identify system address book entries as read-only.

Safe-write candidates for the next slice:

- create/update contacts only in a Weave-managed or explicitly delegated writable address book;
- import vCard into a selected writable address book through backend upload/import flow;
- delete/archive only with confirmation, audit, and recovery semantics specified.

Explicitly out of first slice:

- bulk delete;
- direct CardDAV credentials in the app;
- arbitrary private-address-book mutation through backend service credentials;
- raw vCard editing in the Flutter UI;
- contact photo upload until media handling/redaction is specified.

## 7. Error and redaction model

Use the existing backend error-shape direction:

```json
{
  "code": "provider_unavailable",
  "message": "Contacts are temporarily unavailable.",
  "details": {
    "module": "contacts",
    "retryable": true,
    "capability": "searchContacts"
  },
  "requestId": "..."
}
```

Recommended normalized error codes:

- `unauthorized`
- `forbidden`
- `not_found`
- `validation`
- `conflict`
- `rate_limited`
- `offline`
- `provider_unavailable`
- `provider_misconfigured`
- `unsupported_capability`
- `export_unavailable`
- `payload_too_large`
- `unknown`

Redaction rules:

- never return raw Nextcloud credentials, bearer tokens, app passwords, CSRF tokens, cookies, DAV authorization headers, public-share secrets, or signed download URLs to Flutter;
- never log raw vCard bodies, form answers, uploaded files, or submission exports by default;
- redact provider URLs to host/module/path class, e.g. `files.weave.local/remote.php/dav/[redacted]`;
- hash or alias provider IDs in support diagnostics unless an admin-only diagnostic endpoint explicitly requires them;
- cap error details to provider, capability, support-safe status, retryability, and request/correlation IDs;
- preserve provider raw failures only in protected backend logs with secret scanners/redaction filters.

## 8. Auth and secret handling boundary

Backend owns all provider auth and downstream actor selection.

Allowed backend-held auth models to evaluate:

- per-user delegated OIDC/token exchange if Nextcloud and Keycloak support the required audience/session semantics;
- revocable per-user app password or DAV credential generated and stored by backend only;
- backend service account only for Weave-managed shared resources, not arbitrary private user data;
- explicit shared address books/forms where Nextcloud permissions grant the backend actor the needed access.

Frontend boundary:

- Flutter receives only Weave API responses, backend-owned `downloadRef`/`avatarRef`/`exportId`, and support-safe error codes;
- Flutter must not receive Nextcloud app passwords, DAV base URLs for product calls, OCS endpoints, raw CardDAV paths, cookies, or bearer tokens;
- any future external-client setup UI must be a separate secret-reviewed bridge, not the product Forms/Contacts integration path.

Infra/backend requirements:

- secrets live in backend/infra secret stores or environment-managed local-dev configuration, not in repo docs or generated client code;
- provider configuration must fail closed when Forms/Contacts apps are unavailable, DAV discovery fails, or credential delegation is missing;
- module status should report capability readiness without revealing secret material.

## 9. Risks and open questions

- **Nextcloud Forms API maturity:** API v3 is documented and has OpenAPI, but Weave should pin tested Forms versions and verify API stability across Nextcloud app releases.
- **Forms license metadata mismatch:** some Forms files show `AGPL-3.0-only` while package metadata mentions `AGPL-3.0-or-later`; legal/release review is needed before bundling or modifying provider code.
- **Contacts app vs CardDAV backend:** Contacts is primarily UI; backend integration must target Nextcloud Server CardDAV semantics and vCard compatibility, not scrape Contacts UI.
- **Actor model:** per-user delegated auth vs service-account/shared-resource access must be decided before writes. Service accounts must not imply access to private user address books or forms.
- **System address book:** useful for profile/contact discovery, but read-only and visibility-limited; product UX must distinguish it from writable team/workspace address books.
- **Search semantics:** CardDAV search behavior, indexing, and privacy constraints need runtime verification before promising global search.
- **Submission privacy:** forms can be anonymous/named and may contain sensitive data. Summaries and exports need stricter role checks than form detail reads.
- **File questions/contact photos:** both require backend-mediated media handling and redaction before safe mobile/desktop UX.
- **Provider-neutral write semantics:** Nextcloud supports operations that may not map cleanly to Weave concepts such as form ownership transfer, raw public shares, or direct DAV path mutation.
- **E2E environment:** infra must install/enable Forms and Contacts apps in local/dev before backend contract tests can exercise real providers.

## 10. Follow-up implementation issue breakdown

Suggested implementation sequence:

1. **Spec update: Forms/Contacts backend facade contract**
   - Update workspace specs for groupware Forms/Contacts route ownership, module status flags, DTO shapes, errors, and auth model.
   - Owner: cross-repo.

2. **Infra capability gate: enable/verify Nextcloud Forms and Contacts in local/dev**
   - Add optional app installation/readiness checks for Forms and Contacts.
   - Report app versions and DAV availability through support-safe smoke artifacts.
   - Owner: `weave-infra`.

3. **Backend contracts: provider-neutral DTOs and ports**
   - Add `FormsProvider` and `ContactsProvider` interfaces, capability DTOs, normalized errors, redaction tests, and no-runtime fake providers.
   - Owner: `weave-backend`.

4. **Backend Nextcloud Forms read adapter**
   - Implement Forms API v3 list/detail/questions/submission-summary/export-status slice.
   - Include OCS envelope handling, `OCS-APIRequest: true`, pagination/filter tests, and secret redaction.
   - Owner: `weave-backend`.

5. **Backend Nextcloud CardDAV read adapter**
   - Implement address-book list, contact search/summary/detail via CardDAV/vCard parsing.
   - Include ETag/version mapping, read-only system-address-book handling, and DAV error normalization.
   - Owner: `weave-backend`.

6. **Frontend disabled/preview surfaces**
   - Add Forms/Contacts module capability states and accessible unavailable/empty/error states in Flutter without raw Nextcloud calls.
   - Owner: `weave`.

7. **Safe write slices**
   - Forms: draft create/update/close, then submit response after validation/file policy.
   - Contacts: create/update in Weave-managed address book, then import/export after audit/recovery semantics.
   - Owner: `weave-backend` and `weave`, gated by spec and E2E.

8. **End-to-end validation**
   - Add smoke/E2E flows proving no credentials leak to frontend responses, support-safe errors, app-disabled fail-closed behavior, and at least one read-only happy path for Forms and Contacts.
   - Owner: cross-repo.

## 11. Backend skeleton decision for this retry

This retry intentionally keeps the PR to one repository (`weave`) and adds the requested architecture/research artifact only.

A backend skeleton should be a separate `weave-backend` PR after the workspace spec update because the provider ports affect public API/module-status planning, backend package structure, auth actor selection, and redaction tests. The current local `weave-backend` checkout also has unrelated untracked workspace files, so mixing backend edits into this documentation retry would increase review risk without proving a runtime contract.
