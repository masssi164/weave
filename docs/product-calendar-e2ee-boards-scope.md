# Product scope: calendar hierarchy, Matrix E2EE, and Boards

Weave is past the old Release 1-only framing. The active product track is a self-hosted, accessibility-first collaboration workspace with chat, files, Teams-like shared calendars, Matrix E2EE, and Boards/Tasks behind clear product gates.

This document is binding product direction for the Flutter client. Backend and infra work should keep their own contracts aligned with these scopes.

## Teams-like calendar hierarchy

Calendar is not a private personal-calendar ingestion feature. The product model follows Weave collaboration structure:

1. **Workspace/organization calendar**
   - Shared organization-wide events, maintenance windows, onboarding sessions, all-hands meetings, and cross-team deadlines.
   - Visible from the workspace context and usable by admins or explicitly permitted roles.

2. **Team calendar**
   - Events owned by a team/space, such as sprint rituals, team availability, release planning, or support rotations.
   - Members can discover team events without connecting private personal CalDAV accounts.

3. **Channel calendar and meeting threads**
   - Events can belong to a channel/room context.
   - A channel event should have a linked conversation/meeting thread so agenda, files, decisions, and follow-up tasks stay in the same collaboration context.
   - The UI must not require pointer-only drag/drop to inspect, edit, or move event context.

4. **Task/deadline linkage**
   - Boards/Tasks can reference calendar dates and channel meetings.
   - Calendar entries may expose related board/task IDs through backend product metadata, not provider-specific Flutter transport.

The first implementation may still expose a single backend `workspace` scope while the API grows, but copy, tests, and specs must describe it as the first scope in a workspace/team/channel hierarchy rather than as a private-user calendar stepping stone.

## Explicitly out of scope: private personal calendars

Private personal calendar ingestion is not a current product goal. Weave must not position itself around pulling users' private CalDAV calendars into the product shell.

Allowed:

- shared workspace/team/channel calendars;
- backend-facade calendar APIs with explicit scope metadata;
- secret-free setup metadata;
- future free/busy-style availability only if a separate product decision approves it.

Not allowed without a new decision:

- direct Flutter-to-CalDAV private calendar fallback;
- exposing Nextcloud, app passwords, CalDAV bearer tokens, or backend actor secrets to Flutter;
- marketing private personal calendars as coming-soon core product scope.

## Matrix E2EE as active architecture scope

Weave should use native Matrix E2EE. Do not invent custom cryptography.

Active requirements:

- Matrix device identity, crypto bootstrap, key backup/recovery, and user verification must stay in the chat/Matrix integration boundary.
- The UI must expose understandable security/recovery states for screen-reader and keyboard users.
- New devices must be explicit; verification/recovery prompts must be actionable and not rely on color or icon-only signals.
- Agents/bots are security-relevant participants. If a bot/agent can read an encrypted room, it is effectively a device or member with consent/audit implications.
- Server-readable metadata boundaries must be honest: room membership, timestamps, sender IDs, event types, push/routing metadata, and unencrypted feature data may remain visible even when message bodies are encrypted.
- E2EE enablement must be tested with real Matrix crypto state and must fail closed if the SDK or homeserver cannot provide required crypto support.

## Boards/Tasks as active product scope

Boards are no longer only distant future copy. They are an active Weave scope behind clear gates.

Product requirements:

- Weave owns the board/task domain language: boards, columns, tasks, assignees, labels, due dates, comments, attachments, activity, conflicts.
- The UI must be usable without drag-and-drop: move menus, keyboard focus, screen-reader summaries, and deterministic ordering are required.
- Flutter talks to the Weave backend facade, not directly to Vikunja, Deck, OpenProject, or another provider.
- Provider adapters may later map Vikunja, Nextcloud Deck, OpenProject, or imports into the Weave model, but no provider defines the product UX.
- Preview/facade states must clearly say when no live provider is connected.

## Validation expectations

A change that promotes any of these scopes should update at least one of:

- app copy or route/module gating;
- architecture/product docs;
- backend facade contracts;
- feature tests or architecture tests;
- deterministic screenshots if README/marketing claims change.

Do not merge claims that overstate implementation state. Feature-gated active scope is fine; pretending a live provider or full E2EE is complete is not.
