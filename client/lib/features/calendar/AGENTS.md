# Calendar Feature Instructions

`calendar` consumes the generated Weave User Calendar API and maps its typed values into presentation models. CalDAV belongs inside Server provider adapters.

Rules:
- keep generated transport mapping and repository code in `data/`; do not add handwritten HTTP DTOs
- model normalized event data in `domain/` before presentation consumes it
- do not make presentation widgets responsible for reparsing raw protocol fields

Recurrence and time:
- treat recurrence and timezone handling as correctness-sensitive
- do not flatten recurring events in ways that lose recurrence intent
- do not apply timezone fixes ad hoc in widgets
- keep all-day and timed events distinct in the model layer

Accessibility:
- agenda and list rows must read date, time, and title in a sensible spoken order
- recurring, all-day, and timezone-shifted events should remain understandable to screen reader users
- group row semantics when separate labels would cause fragmented announcements

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
