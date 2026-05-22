# DevOps-Provider: GitLab CE/FOSS self-managed vs. Forgejo

Status: research recommendation for issue #232  
Date: 2026-05-22  
Scope: free-to-run, commercially usable, self-hostable DevOps providers behind Weave backend facades

## 1. Recommendation summary

**Recommendation:** Start the Weave DevOps provider work with **GitLab CE/FOSS self-managed** as the primary adapter target, and keep **Forgejo** as a first-class alternative adapter from the first backend model cut.

Why GitLab first:

- GitLab is the more professional/familiar DevOps suite for the target use case.
- GitLab CE/FOSS exposes a broad, documented REST API for projects, groups, issues, merge requests, CI pipelines/jobs, releases/tags, artifacts, packages, container registry, webhooks, OAuth/OIDC, and access tokens.
- Read-only MVP coverage is strong without needing Premium/Ultimate.

Why Forgejo stays first-class:

- Forgejo is lighter, clearly self-hostable, and GPLv3 Free Software.
- It covers the important forge primitives: repositories, organizations, users, issues, pull requests, releases, tags, packages/container registry, webhooks, OAuth2/OIDC, API tokens, and Forgejo Actions.
- Its API and operational footprint make it a good sovereignty/minimalism option, especially when Weave only needs provider data rather than a full DevOps suite.

Product boundary:

- Weave should **not** become a full forge UI clone. The app should show a compact, accessible DevOps view: project/repository overview, issue/MR status, latest pipeline/action status, jobs/log links or safe excerpts, releases/tags, and provider links.
- All provider access should go through a **Java backend facade**. Flutter must not receive provider tokens, raw provider errors, or provider-specific transport DTOs.
- Build the domain around provider-neutral ports first, then implement GitLab and Forgejo adapters behind those ports.

## 2. Sources list

GitLab official/source URLs:

- GitLab self-managed install overview: https://about.gitlab.com/install/
- GitLab FOSS license: https://gitlab.com/gitlab-org/gitlab-foss/-/raw/master/LICENSE
- GitLab REST API overview: https://docs.gitlab.com/api/rest/
- GitLab Projects API: https://docs.gitlab.com/api/projects/
- GitLab Groups API: https://docs.gitlab.com/api/groups/
- GitLab Issues API: https://docs.gitlab.com/api/issues/
- GitLab Merge Requests API: https://docs.gitlab.com/api/merge_requests/
- GitLab Pipelines API: https://docs.gitlab.com/api/pipelines/
- GitLab Jobs API: https://docs.gitlab.com/api/jobs/
- GitLab Job Artifacts API: https://docs.gitlab.com/api/job_artifacts/
- GitLab Releases API: https://docs.gitlab.com/api/releases/
- GitLab Tags API: https://docs.gitlab.com/api/tags/
- GitLab Packages API: https://docs.gitlab.com/api/packages/
- GitLab Container Registry API: https://docs.gitlab.com/api/container_registry/
- GitLab Project Webhooks API: https://docs.gitlab.com/api/project_webhooks/
- GitLab OAuth provider/OIDC scopes: https://docs.gitlab.com/integration/oauth_provider/
- GitLab personal access tokens: https://docs.gitlab.com/user/profile/personal_access_tokens/
- GitLab project access tokens API: https://docs.gitlab.com/api/project_access_tokens/
- GitLab roles and permissions: https://docs.gitlab.com/user/permissions/

Forgejo official/source URLs:

- Forgejo installation overview: https://forgejo.org/docs/latest/admin/installation/
- Forgejo license: https://codeberg.org/forgejo/forgejo/raw/branch/forgejo/LICENSE
- Forgejo API usage: https://forgejo.org/docs/latest/user/api-usage/
- Forgejo OpenAPI/Swagger document: https://code.forgejo.org/swagger.v1.json
- Forgejo Actions reference: https://forgejo.org/docs/latest/user/actions/reference/
- Forgejo Actions admin guide: https://forgejo.org/docs/latest/admin/actions/
- Forgejo Package Registry: https://forgejo.org/docs/latest/user/packages/
- Forgejo Webhooks: https://forgejo.org/docs/latest/user/webhooks/
- Forgejo OAuth2 provider/OIDC: https://forgejo.org/docs/latest/user/oauth2-provider/
- Forgejo configuration cheat sheet: https://forgejo.org/docs/latest/admin/config-cheat-sheet/

## 3. License/commercial-use summary

- **GitLab CE/FOSS:** GitLab FOSS source is licensed mostly under the MIT Expat license, with docs under CC BY-SA 4.0 and special directories such as `ee/` or `jh/` governed by their own licenses if present. MIT permits commercial use, modification, distribution, sublicensing, and sale, provided copyright/license notices are retained. For Weave, treat only CE/FOSS APIs/features as in scope and avoid `ee/`/Premium/Ultimate assumptions.
- **Forgejo:** Forgejo is licensed under GPLv3. Commercial use and self-hosted operation are allowed. If Weave distributes modified Forgejo itself, GPLv3 source/distribution obligations apply. If Weave only integrates with a self-hosted Forgejo instance over its API, Weave does not become a Forgejo derivative just by using the API. Keep branding/trademark questions separate from software-license compatibility.

Both candidates satisfy the hard constraints: free to run, commercially usable, and self-hostable.

## 4. Capability matrix

| Capability | GitLab CE/FOSS self-managed | Forgejo |
|---|---|---|
| Projects/repos | Strong. Projects API covers metadata, visibility, repository URLs, feature flags, archive state, container registry settings, and related links. | Strong. OpenAPI exposes repository search and `/repos/{owner}/{repo}` plus repository subresources. Lighter model than GitLab. |
| Groups/orgs | Strong. Groups API supports visible groups, nested paths, group projects, members/permissions context. Avoid Premium-only group metadata. | Good. Organizations are first-class (`/orgs`, `/orgs/{org}`), with teams/members in API. Less enterprise hierarchy than GitLab. |
| Users/permissions | Strong. Default roles include Guest, Planner, Reporter, Developer, Maintainer, Owner; APIs expose user and member context. Read-only MVP can map provider roles to coarse Weave capabilities. | Good. Users/org/team permissions exist; API tokens support scopes. Permissions are simpler but enough for MVP capability mapping. |
| Issues | Strong. Issues API supports list/search/filter, labels, assignees, milestones, state, comments/notes links. Avoid Premium/Ultimate fields like epics, iterations, weights, health status. | Good. Issues are core forge objects; OpenAPI exposes repo issues and comments. Adequate for list/detail/status/links. |
| Merge requests / pull requests | Strong. Merge Requests API supports list/detail/filter by state, author, assignee, reviewer, source/target branch, draft state, labels, and merge status. Avoid Premium approval-rule assumptions. | Good. Pull requests are exposed via `/repos/{owner}/{repo}/pulls` and related endpoints. Adequate for PR overview/status. |
| CI/pipelines | Strong. Pipelines API supports list, detail, latest pipeline by ref, status, source, ref, SHA, duration/status fields. | Medium-good. Forgejo Actions is available and enabled by default since v1.21, but requires separate Forgejo Runner. API exposes actions runs. Semantics differ from GitHub Actions and GitLab CI. |
| Jobs/logs | Strong. Jobs API lists project jobs and pipeline jobs with status, stage, runner, timings, artifacts, and web URL. Job traces/logs are available through CI job endpoints. | Medium. Forgejo Actions stores logs server-side; OpenAPI exposes action runs/jobs/logs routes. Need a spike against a live instance to confirm log pagination, redaction, and retention behavior. |
| Releases/tags | Strong. Releases API and Tags API are Free-tier documented. Tags can link release data and pipeline status. | Strong. Releases and tags are available through repository API and UI. Release events can trigger actions/webhooks. |
| Artifacts | Strong. Job Artifacts API supports archive download, single-file download, and artifact tree listing in Free tier. | Good for Actions artifacts. Admin docs state job logs and artifacts are stored in Forgejo with retention settings. API exposes action artifact endpoints. |
| Packages | Good. Packages API lists project/group packages and package files for supported package types. | Strong. Package Registry supports many package managers including generic, npm, Maven, PyPI, Cargo, Helm, Debian/RPM, Pub, and more. Packages belong to user/org owners and can be linked to repositories. |
| Container registry | Good. Container Registry API is Free-tier documented for project/group registry repositories and tags, if the registry is enabled/configured. | Good. Forgejo Package Registry includes Container packages for OCI-compliant clients. Treat as package-registry-backed rather than a GitLab-style project registry. |
| Webhooks | Strong. Project Webhooks API supports push, issues, MR, tag, note, job, pipeline, release, deployment, and other events. Requires Maintainer/Owner/admin for setup. | Strong. Supports repository, organization, and system webhooks. Raw Forgejo/Gitea/Gogs payloads and chat/integration targets are available. Headers include `X-Forgejo-Event` and signatures. |
| OAuth/OIDC/service tokens | Strong. OAuth2/OIDC provider supports OAuth apps and scopes such as `read_api`, `read_user`, `read_repository`, `read_registry`, `openid`, `profile`, `email`. Personal/project access tokens support scoped API/repository/registry access. | Good with caveat. OAuth2 provider supports Authorization Code, PKCE, and OIDC endpoints. API tokens support scopes. Forgejo docs warn OAuth2 scopes are not yet implemented for OAuth tokens; scoped application/API tokens are preferable for security-sensitive integrations. |

## 5. GitLab paid-only features to avoid for MVP

Do not assume any GitLab Premium/Ultimate-only feature for the MVP. Specifically avoid designing against:

- Epics, epic boards, roadmaps, multi-level planning hierarchy.
- Iterations, issue weights, issue health status, and other Premium/Ultimate issue fields.
- Merge request approval rules, approver filters, code owner approval workflows, or compliance approvals.
- Group-level advanced governance/compliance features: audit streams, compliance frameworks, advanced compliance center, security policy project management.
- Advanced application-security/vulnerability dashboards and dependency list features that are Premium/Ultimate scoped.
- Premium-only group/project metadata such as certain deletion/retention/runner quota attributes.
- Push rules or protected-tag deletion prevention where docs identify Premium/Ultimate requirements.
- Any GitLab Duo/AI feature.
- Any feature only present in the Enterprise Edition `ee/` code path or labeled Premium/Ultimate in docs.

MVP rule: if a GitLab docs page marks a capability as Free/Premium/Ultimate, use only the Free-visible baseline fields and treat extra fields as optional provider metadata.

## 6. Minimal read-only integration

Implement the first slice as read-only and provider-neutral:

- Provider connection record:
  - provider type: `gitlab` or `forgejo`
  - base URL
  - display name
  - backend-owned credential reference
  - optional default namespace/project filters
- Project/repository list:
  - id, path/full name, web URL, clone URLs, visibility if available, default branch, archived/fork flags, last activity/update timestamp
- Project detail summary:
  - repository URL and default branch
  - open issue count if available
  - open MR/PR count if available
  - latest pipeline/action run status for default branch
  - latest release/tag
- Issues:
  - list open/closed issues with title, provider id/iid/number, state, labels, assignees, author, updated timestamp, web URL
- Merge requests/pull requests:
  - list open/merged/closed items with title, source/target branch, state, draft flag if available, author, reviewers/assignees if available, updated timestamp, web URL
- CI:
  - list latest pipeline/action runs and jobs with status, stage/name, ref, SHA, timings, web URL
  - do not stream full logs into Flutter by default; expose safe excerpts or links first
- Releases/tags:
  - list tags and releases with name/tag, timestamp, commit SHA, web URL, asset/source links if available
- Webhooks:
  - read/list webhook configuration only if token has permission; do not create hooks in MVP

Authentication for MVP:

- GitLab: prefer `read_api` plus `read_repository` only when repository file/clone metadata requires it. Add `read_registry` only if container registry data is in scope.
- Forgejo: prefer scoped API/application token with read-only scopes where supported. Avoid OAuth2 bearer tokens for broad provider API use until scopes are implemented/enforced for OAuth tokens.

## 7. Safe later write scopes

Add writes only after the read model, audit logging, permission checks, and support-safe errors are stable.

Safe later GitLab writes:

- Create/update issue: requires API write scope and sufficient project role.
- Comment on issue/MR: API write scope, but low blast radius if clearly attributed to the user/service account.
- Create MR from existing branch: only after repository/branch safety checks.
- Trigger pipeline: pipeline endpoint write with explicit user confirmation and branch/ref display.
- Create release/tag: only behind release-specific confirmation and permission checks.
- Register/update webhooks: Maintainer/Owner/admin only; should be an admin setup flow, not normal app interaction.

Safe later Forgejo writes:

- Create/update issue or comment.
- Create/update pull request from existing branch.
- Trigger/dispatch Actions run if API and instance version support it.
- Create release/tag.
- Configure webhooks only in an admin setup flow.

Write-scope guardrails for both:

- Backend stores credentials; Flutter receives only capabilities and redacted errors.
- Every write endpoint maps to a Weave intent with explicit project/ref/resource display.
- Use least privilege and per-provider capability discovery.
- Prefer service/project tokens for automation; prefer user OAuth/session only for user-initiated actions.
- Log provider request id/status and normalized operation id, not token values or raw sensitive payloads.

## 8. Provider-neutral backend model and ports

Keep provider DTOs inside adapters. The Java backend should expose Weave domain objects through narrow ports.

### Core model

- `DevOpsProviderConnection`
  - `id`, `type`, `baseUrl`, `displayName`, `credentialRef`, `enabledCapabilities`, `createdAt`, `updatedAt`
- `DevOpsProjectRef`
  - `providerConnectionId`, `providerProjectId`, `namespace`, `name`, `path`, `webUrl`, `defaultBranch`, `visibility`, `archived`
- `DevOpsUserRef`
  - `providerUserId`, `username`, `displayName`, `webUrl`, optional avatar URL handled as remote/media-safe data
- `DevOpsIssueRef`
  - `projectRef`, `providerIssueId`, `numberOrIid`, `title`, `state`, `labels`, `assignees`, `updatedAt`, `webUrl`
- `DevOpsChangeRequestRef`
  - `projectRef`, `providerRequestId`, `numberOrIid`, `title`, `state`, `sourceBranch`, `targetBranch`, `draft`, `author`, `reviewers`, `updatedAt`, `webUrl`
- `DevOpsPipelineRef`
  - `projectRef`, `providerPipelineId`, `status`, `ref`, `sha`, `source`, `startedAt`, `finishedAt`, `duration`, `webUrl`
- `DevOpsJobRef`
  - `pipelineRef`, `providerJobId`, `name`, `stage`, `status`, `startedAt`, `finishedAt`, `duration`, `webUrl`, `hasArtifacts`, `hasLog`
- `DevOpsReleaseRef`
  - `projectRef`, `tagName`, `name`, `releasedAt`, `commitSha`, `webUrl`, `assets`

### Ports

`SourceControlProvider`

- `listProjects(connection, filter, page)`
- `getProject(connection, projectRef)`
- `listBranches(connection, projectRef, page)`
- `listTags(connection, projectRef, page)`
- `getCommit(connection, projectRef, sha)`
- Later write methods: `createBranch`, `createTag`, repository file operations only after separate review.

`IssueTrackerProvider`

- `listIssues(connection, projectRef, filter, page)`
- `getIssue(connection, issueRef)`
- `listIssueComments(connection, issueRef, page)`
- Later write methods: `createIssue`, `updateIssue`, `addIssueComment`.

`CiProvider`

- `listPipelines(connection, projectRef, filter, page)`
- `getPipeline(connection, pipelineRef)`
- `listJobs(connection, pipelineRef, page)`
- `getJobLogExcerpt(connection, jobRef, limits)`
- `listArtifacts(connection, jobRef, page)`
- Later write methods: `triggerPipeline`, `retryJob`, `cancelJob` only behind explicit capability checks.

`ReleaseProvider`

- `listReleases(connection, projectRef, page)`
- `getRelease(connection, projectRef, tagName)`
- `listTags(connection, projectRef, page)`
- Later write methods: `createRelease`, `updateRelease`, `deleteRelease` after separate confirmation UX.

Cross-cutting backend concerns:

- `ProviderCapabilityService` to detect and cache supported features per connection/version.
- `ProviderErrorMapper` to normalize provider errors into support-safe Weave errors.
- `ProviderRateLimitPolicy` to respect pagination, rate limits, and backoff.
- `ProviderAuditLog` for write attempts and credential/capability changes.

## 9. Risks and open questions

- **Version drift:** GitLab and Forgejo APIs evolve. Forgejo API compatibility is per major version; GitLab REST v4 has deprecations and future API v5 removals. Adapters need version/capability probing.
- **CE vs EE ambiguity:** Many GitLab docs pages include Free/Premium/Ultimate together. Adapter code must not require fields that appear only in paid tiers.
- **Forgejo Actions maturity:** Actions are available, but runner setup, log/artifact retention, and API behavior need a live-instance spike before promising parity with GitLab CI.
- **Auth model mismatch:** GitLab OAuth scopes are mature. Forgejo OAuth2 currently lacks fine-grained OAuth scopes; API tokens are safer for least privilege.
- **Permission mapping:** Provider roles do not map 1:1. Weave should expose coarse capabilities, not provider role names as authorization decisions.
- **Logs and artifacts:** Logs may include secrets. MVP should prefer links/status and small redacted excerpts over bulk log ingestion.
- **Registries/packages:** GitLab package/container registry and Forgejo packages have different ownership models. Treat them as optional `PackageRegistryCapability`, not core source-control data.
- **Webhook setup:** Webhooks require elevated privileges and public/reachable callback URLs. Defer automatic setup until Weave has a secure inbound event design.
- **Accessibility:** Weave should own accessible presentation and not embed upstream forge pages as the primary UI.
- **Open question:** Should the first implementation use user tokens only, service/project tokens only, or support both with policy hints?
- **Open question:** Do we need local caching/sync history for offline/recent activity, or is live read-through enough for MVP?
- **Open question:** Which Java backend framework/module owns the facade: existing sync/provider module or a new DevOps bounded context?

## 10. Concrete implementation issue breakdown

Suggested GitHub issues after #232:

1. **Define DevOps provider domain contract**
   - Add Java backend interfaces for `SourceControlProvider`, `IssueTrackerProvider`, `CiProvider`, and `ReleaseProvider`.
   - Add provider-neutral DTOs and pagination/filter contracts.
   - Acceptance: no Flutter dependency on GitLab/Forgejo DTOs.

2. **Credential and connection model for DevOps providers**
   - Persist provider connection metadata and backend-only credential references.
   - Add redacted connection diagnostics.
   - Acceptance: Flutter can list configured connections without seeing secrets.

3. **GitLab CE read-only adapter spike**
   - Implement projects, issues, merge requests, pipelines, jobs, releases/tags.
   - Use only Free/CE-compatible fields.
   - Acceptance: tested against GitLab CE/FOSS or documented self-managed Free instance.

4. **Forgejo read-only adapter spike**
   - Implement repositories, orgs, issues, pull requests, actions runs/jobs, releases/tags.
   - Use Swagger/OpenAPI generated or hand-written client with version probing.
   - Acceptance: tested against a local Forgejo instance with sample repo/actions.

5. **Provider capability discovery**
   - Detect API version, enabled CI/actions, package/container registry availability, webhook permissions.
   - Acceptance: UI receives capability flags and hides unsupported actions.

6. **DevOps overview backend endpoint**
   - Compose project summary: open issues, open MRs/PRs, latest CI status, latest release/tag.
   - Acceptance: one endpoint powers the initial accessible overview screen.

7. **Support-safe error and rate-limit mapping**
   - Normalize 401/403/404/rate-limit/network/provider-version errors.
   - Strip tokens and sensitive provider payloads.
   - Acceptance: tests cover common GitLab and Forgejo error bodies.

8. **Accessible Flutter DevOps overview UI**
   - Project list/detail, status summaries, issue/MR/CI/release sections.
   - Acceptance: screen-reader labels, deterministic focus order, no color-only status, no forge-page iframe dependency.

9. **CI logs/artifacts safety spike**
   - Decide whether MVP exposes links, excerpts, downloadable artifacts, or all three.
   - Add redaction/size limits.
   - Acceptance: no bulk secret-bearing logs in app state by default.

10. **Later write-intent design**
    - Design explicit confirmation flows for comments, issue updates, pipeline triggers, and release creation.
    - Acceptance: write scopes are not requested until this design is reviewed.
