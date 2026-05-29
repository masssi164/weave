# Calendar Feature Instructions

`calendar` owns CalDAV parsing and normalization before event data reaches UI code.

Rules:
- keep CalDAV payload parsing, DTOs, and repository code in `data/`
- model normalized event data in `domain/` before presentation consumes it
- do not make presentation widgets responsible for reparsing raw CalDAV fields

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
