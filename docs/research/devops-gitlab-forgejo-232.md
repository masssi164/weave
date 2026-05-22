# DevOps-Provider für Weave: GitLab CE/FOSS und Forgejo (#232)

Status: Research- und Architekturvertrag vor Adapter-Implementierung  
Datum: 2026-05-22  
Scope: GitLab CE/FOSS self-managed als primärer DevOps-Provider, Forgejo als gleichwertige Alternative. Providerzugriff läuft ausschließlich über Java-Backend-Fassaden; Flutter bleibt providerneutral.

## Kurzfazit

- **Empfehlung:** GitLab CE/FOSS zuerst integrieren, aber das Backend-Port-Modell sofort so schneiden, dass Forgejo ohne UI-Neubau nachziehen kann.
- **Startumfang:** read-only Kontext für verlinkte Projekte/Repos, offene Issues, offene Merge/Pull Requests, Pipeline-/Jobstatus, Releases/Tags, ausgewählte Artefakt-/Package-/Container-Metadaten und Provider-Readiness.
- **Produktgrenze:** Weave darf keine vollständige Forge-UI klonen. Weave zeigt projekt- und channelrelevanten Engineering-Kontext und delegiert Detail-/Adminflows an den Forge.
- **Sicherheitsgrenze:** Provider-Tokens, Provider-spezifische Fehlermeldungen, Webhook-Secrets und Adminfunktionen bleiben im Backend. Flutter bekommt nur providerneutrale DTOs, Capabilities und support-sichere Fehlercodes.
- **Maschinenlesbarer Vertrag:** `devops-provider-capability-matrix-232.json` hält die Capability-Matrix, Ports, Fehlercodes und Provider-Grenzen fest.

## Provider-Capability-Matrix

### GitLab CE/FOSS self-managed

- **Rolle:** Primärkandidat.
- **Lizenz/Self-hosting:** GitLab-FOSS-Repository ist für relevanten Code außerhalb der dokumentierten Ausnahmen MIT-Expat-lizenziert; self-managed Installation ist offiziell vorgesehen.
- **Projekte/Repos:** REST API `/api/v4`, Projekte, Repository-Dateien, Branches, Commits und Tags sind verfügbar. Achtung: GitLab unterscheidet globale `id` und projektlokale `iid`; Issues/MRs werden typischerweise per `iid` adressiert.
- **Groups/Users/Permissions:** Groups, Members und Access-Level sind API-seitig vorhanden. Für Weave reicht initial `Reporter`-Leserecht auf Zielprojekten/-gruppen; Admin-/Sudo-Flows sind aus dem Produktumfang auszuschließen.
- **Issues:** Issues API deckt offene Issues, Labels, Milestones, Assignees, Kommentare/Notes und Status ab. Premium-Felder wie Weight, Epic, Iteration, Health Status nicht voraussetzen.
- **Merge Requests:** MR API deckt Listen, Details, Status, Commits, Diffs/Changes und Merge-Status ab. Diffs/Changes können asynchron vorbereitet, limitiert oder zu groß sein; Weave sollte im MVP keine vollständige Review-UI bauen.
- **CI:** Pipelines API und Jobs API sind stark genug für Status, Stage/Job-Liste, Logs/Trace-Metadaten, Retry/Cancel später. Jobs-Endpunkte können eigene Rate-Limits haben.
- **Releases/Tags:** Release API und Tags API reichen für Releaseübersicht, Tag-Liste und Assets/Links.
- **Artifacts/Packages/Container Registry:** Job Artifacts, Packages und Container Registry APIs sind vorhanden. Initial nur Metadaten/Links lesen; Lösch-/Mutationsflows vermeiden.
- **Webhooks:** Project Webhooks API unterstützt Events, Secret Token/HMAC-Verifikation, Custom Headers und URL-Variablen. Weave sollte einen Backend-Webhook-Ingress mit Provider-Signaturprüfung und Event-Normalisierung nutzen.
- **OAuth/OIDC/Tokens:** OAuth2 und Personal Access Tokens sind verfügbar; Projekt-/Gruppen-Access-Tokens können später Service-Account-ähnliche Setups ermöglichen. Token-Speicherung nur backendseitig.
- **Rate Limits/Pagination:** REST API nutzt Pagination; Self-managed Rate-Limits sind admin-konfigurierbar und 429-Antworten können `Retry-After`/RateLimit-Header liefern. Backend muss cachen, paginieren und throttlen.
- **Paid-only Ausschlüsse:** MR Approvals/Approval Rules, Code Owners Approval Enforcement, Epics, Issue Weights, Iterations, Health Status, Merge Trains, externe/status-check Approval Gates, Custom Roles, LDAP/SAML Enterprise-Sync/Inventar, Advanced Security/Vulnerability Dashboards/Enforcement, Secret Push Protection Enforcement, Duo/AI.

### Forgejo

- **Rolle:** First-class Alternative für leichtere/schlankere Deployments.
- **Lizenz/Self-hosting:** GPL-3.0-or-later; kostenlos self-hostbar. Kommerziell nutzbar, aber Copyleft-Pflichten bei Distribution beachten.
- **Projekte/Repos:** REST API unter `/api/v1`, OpenAPI/Swagger verfügbar. Repositories, Branches, Commits, Contents, Tags, Releases, Collaborators und Hooks sind abgedeckt.
- **Organisationen/Users/Permissions:** Orgs, Teams, Members, Collaborators und Permissions sind API-seitig vorhanden. Admin- und `sudo`-Funktionen existieren, sind aber für Weave-MVP zu verbieten.
- **Issues:** Issues, Comments, Labels, Deadlines, Dependencies, Attachments, Reactions, Subscriptions, Timeline und Times sind API-seitig vorhanden. Initial nur offene Issues + Kommentare/Labels lesen.
- **Pull Requests:** Pulls API deckt Listen, Details, Commits, Files, Reviews, Requested Reviewers und Merge ab. Weave sollte initial Status/Übersicht zeigen, nicht Review-UI klonen.
- **CI/Actions:** Forgejo Actions sind verfügbar und ähneln GitHub Actions; Swagger zeigt Runs/Tasks/Runners/Workflow-Dispatch-Endpunkte. Caveat: Actions können serverseitig deaktiviert sein und Scheduler/DST-Verhalten ist operativ relevant.
- **Releases/Tags:** Releases, Release Assets und Tags sind native API-Flächen.
- **Artifacts/Packages/Container Registry:** Package API und Container/Package Registry sind vorhanden; Repo Units können serverseitig deaktiviert sein. Initial nur Metadaten/Links.
- **Webhooks:** Repository- und Org-Webhooks sind verfügbar, plus Chat-Integrationen. Backend muss Signaturen/Secrets normalisieren und Events idempotent verarbeiten.
- **OAuth/OIDC/Tokens:** Forgejo kann OAuth2/OIDC Provider sein und PKCE unterstützen. Kritischer Caveat: OAuth2 Token Scopes sind laut Forgejo-Doku noch nicht umgesetzt; OAuth-Apps können dadurch zu breite Rechte bekommen. Für Weave deshalb initial scoped Personal Access Tokens mit spezifischen Repositories bevorzugen.
- **Rate Limits/Pagination:** API-Nutzung und Pagination sind dokumentiert; konkrete Limits sind instanzkonfigurationsabhängig. Backend muss 429/Retry-After und Provider-Ausfälle support-sicher kapseln.
- **Paid-only Ausschlüsse:** Keine GitLab-ähnlichen kommerziellen Tier-Grenzen gefunden. Trotzdem müssen deaktivierbare Repo Units, Actions/Packages-Verfügbarkeit und Instanzkonfiguration als Capabilities behandelt werden.

## Backend-Ports

Alle Ports leben konzeptionell im Java-Backend. Flutter darf nur gegen Weave-eigene DTOs/Capabilities sprechen.

### `SourceControlProvider`

- Read-only MVP:
  - `listLinkedProjects`
  - `getRepositorySummary`
  - `listBranches`
  - `listTags`
  - `listRecentCommits`
  - `getFileMetadata`
- Spätere Writes nur nach expliziter Capability/Policy:
  - `createBranch`
  - `createCommitOrFileChange`
  - `openMergeOrPullRequest`

### `IssueTrackerProvider`

- Read-only MVP:
  - `listOpenIssues`
  - `getIssue`
  - `listIssueComments`
  - `listLabels`
  - `listMilestones`
- Spätere Writes:
  - `createIssueComment`
  - `setIssueLabels`
  - `updateIssueState`

### `CiProvider`

- Read-only MVP:
  - `listPipelinesOrRuns`
  - `getPipelineOrRun`
  - `listJobs`
  - `getJobLogMetadata`
  - `listArtifactsMetadata`
- Spätere Writes:
  - `retryJob`
  - `cancelPipelineOrRun`
  - `triggerPipelineOrWorkflow`

### `ReleaseProvider`

- Read-only MVP:
  - `listReleases`
  - `getRelease`
  - `listTags`
  - `listPackages`
  - `listContainerImages`
- Spätere Writes:
  - `createReleaseDraftOrRelease`
  - `attachReleaseAsset`

## Fail-closed Vertragsregeln

- Adapter melden fehlende Providerfunktionen als Capability `false`/`provider_feature_unavailable`, nicht als UI-Crash.
- Flutter erhält niemals Provider-Token, Webhook-Secrets, Raw HTTP Bodies oder ungefilterte Provider-Fehler.
- Fehler werden auf diese Codes normalisiert: `unauthorized`, `forbidden`, `not_found`, `conflict`, `rate_limited`, `offline`, `validation`, `provider_unavailable`, `provider_feature_unavailable`, `unknown`.
- Mutationen sind standardmäßig deaktiviert. Jede Write-Operation braucht Provider-Capability, Backend-Policy, Audit-Log und least-privilege Token/Role.
- Webhooks werden backendseitig signiert/verifiziert, dedupliziert und in providerneutrale Events übersetzt.
- API-Pagination, Rate-Limit-Header, `Retry-After`, Zeitouts und Backoff liegen im Backend.

## Minimaler read-only Einstieg

1. Provider-Verbindung im Backend anlegen: Base URL, Provider-Typ, Token-Reference aus Secret Store, erlaubte Projekt-/Repo-IDs.
2. Readiness prüfen: Version/API erreichbar, Token gültig, Zielprojekt lesbar, Capabilities ermittelt.
3. Projektkontext für Weave liefern:
   - Repo-Metadaten: Name, URL, Default Branch, letzter Commit.
   - Offene Issues: Titel, Status, Labels, Assignee, aktualisiert am.
   - Offene MR/PRs: Titel, Branches, Status, Draft/WIP, Pipeline/Checks-Status soweit verfügbar.
   - CI: letzte Pipeline/Run-Liste, Jobs, Status, Link ins Forge.
   - Releases/Tags: letzte Releases und Tags.
4. UI bleibt bewusst schmal: Kontextkarten, Listen, Deep Links. Keine vollständige Issue-/MR-/CI-Adminoberfläche.

## Sichere spätere Write-Scope-Erweiterung

- **GitLab:** separate Service-/Bot-Identität oder Projekt-/Gruppen-Access-Tokens; Rollen minimal halten. Writes zuerst auf Kommentare/Labels beschränken, danach MR/Branch/File-Operationen. Keine Admin-/Sudo-Flows.
- **Forgejo:** scoped PAT mit Specific Repositories; zunächst `write:issue`, später `write:repository` nur für klar abgegrenzte PR/Branch/File-Flows. OAuth für Weave erst nutzen, wenn Scope-Enforcement für das Zielrelease belastbar ist.
- **Gemeinsam:** Feature Flags, Audit Log, Preview/Dry-run bei riskanteren Aktionen, idempotente Requests, Support-Safe Error Mapping.

## Risiken und Gegenmaßnahmen

- **Lizenz:** GitLab FOSS MIT ist unkompliziert; Forgejo GPL ist für Nutzung/Self-hosting okay, bei Distribution von abgeleitetem Servercode aber rechtlich sauber prüfen.
- **API-Gaps:** GitLab CE hat starke APIs, aber einige Felder/Flows sind Premium/Ultimate. Forgejo hat gute API-Flächen, aber Actions/Packages/Repo Units können deaktiviert sein.
- **Operations:** GitLab ist schwergewichtiger, braucht Runner/Backups/Upgrades; Forgejo ist leichter, aber Actions Runner/Package Registry müssen separat operativ betrachtet werden.
- **Auth/Secrets:** Forgejo OAuth Scopes sind der rote Marker; PATs bevorzugen. GitLab Token-Rechte über Rolle/Scope klein halten. Secrets nie an Flutter.
- **Webhooks:** Signaturprüfung, Replay-Schutz, Deduplikation und Event-Versionierung sind Pflicht.
- **Rate Limits:** Backend muss paginieren, cachen, drosseln und Retry-After respektieren.
- **Accessibility/Product UX:** Externe Forge-Detail-UI kann unterschiedlich zugänglich sein; Weave sollte nur die wichtigsten Kontexte selbst barrierearm darstellen und Deep Links für Details nutzen.

## Quellen

NotebookLM:

- Notebook: `Weave #232 DevOps providers: GitLab CE and Forgejo`
- Notebook-ID: `cd826607-5d0e-4872-8412-c5d3ee7a80c1`
- Query Conversation: `c740bd2a-817c-434d-af8d-99c654e9f5dd`

GitLab:

- `https://docs.gitlab.com/api/rest/`
- `https://docs.gitlab.com/api/projects/`
- `https://docs.gitlab.com/api/groups/`
- `https://docs.gitlab.com/api/members/`
- `https://docs.gitlab.com/api/group_members/`
- `https://docs.gitlab.com/api/issues/`
- `https://docs.gitlab.com/api/merge_requests/`
- `https://docs.gitlab.com/api/pipelines/`
- `https://docs.gitlab.com/api/jobs/`
- `https://docs.gitlab.com/api/releases/`
- `https://docs.gitlab.com/api/tags/`
- `https://docs.gitlab.com/api/job_artifacts/`
- `https://docs.gitlab.com/api/packages/`
- `https://docs.gitlab.com/api/container_registry/`
- `https://docs.gitlab.com/api/project_webhooks/`
- `https://docs.gitlab.com/api/oauth2/`
- `https://docs.gitlab.com/integration/oauth_provider/`
- `https://docs.gitlab.com/user/profile/personal_access_tokens/`
- `https://docs.gitlab.com/administration/settings/user_and_ip_rate_limits/`
- `https://docs.gitlab.com/subscriptions/feature_comparison/`
- `https://docs.gitlab.com/user/project/merge_requests/approvals/`
- `https://docs.gitlab.com/user/project/codeowners/`
- `https://docs.gitlab.com/user/group/epics/`
- `https://gitlab.com/gitlab-org/gitlab-foss/-/raw/master/LICENSE`

Forgejo:

- `https://forgejo.org/docs/latest/user/api-usage/`
- `https://forgejo.org/docs/latest/user/token-scope/`
- `https://forgejo.org/docs/latest/user/oauth2-provider/`
- `https://forgejo.org/docs/latest/user/webhooks/`
- `https://forgejo.org/docs/latest/user/actions/reference/`
- `https://try.next.forgejo.org/swagger.v1.json`
- `https://forgejo.org/docs/latest/admin/installation/`
- `https://forgejo.org/docs/latest/admin/config-cheat-sheet/#api-api`
- `https://codeberg.org/forgejo/forgejo/raw/branch/forgejo/LICENSE`
