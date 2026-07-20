---
name: changelog
description: Add an entry to CHANGELOG.md for a user-visible change. Use whenever finishing a fix, feature, or behavior change in this repo - CLAUDE.md requires a changelog entry for every major change. Triggers on "update the changelog", "add a changelog entry", or after committing user-facing work.
---

# Updating CHANGELOG.md

Entries go under `## [Unreleased]` in [Keep a Changelog](https://keepachangelog.com) format.
**Never create a version heading or bump a version** - the CI deploy action owns releases.

## Sections

Use the existing section under `[Unreleased]`, creating it only if absent. Order as they appear
in the file: `### Added`, `### Modified`, `### Fixed`.

| Section | Use for |
|---|---|
| `Added` | New capability that did not exist before |
| `Modified` | Existing behavior that now works differently (including intentional behavior changes) |
| `Fixed` | Something that was broken and now is not |

## Writing the entry

Write for a plugin *user* reading release notes, not for a reviewer reading the diff. State the
symptom they would have seen, then what changed. Skip class names unless they are the only way to
identify the area; never reference commit hashes or branch names.

Append `(#NNN)` when a GitHub issue exists.

Good:

```markdown
- Fixed Karate 2.x runs dying immediately with "Test framework quit unexpectedly" and an empty test tree. The runner installed its logback console appender unconditionally, but Karate 2.x drops the transitive logback dependency Karate 1.x provided.
```

Bad - describes the diff, not the user's experience:

```markdown
- Moved logback calls into UppercutLogbackAppender and made KarateTestRunner call it reflectively.
```

One bullet per user-visible change. An internal refactor with no user-visible effect needs no entry;
test-only changes never do.

## Do not log bugs that never shipped

Only record a fix if the broken behavior exists in a **released** version. A bug introduced and
fixed within the same `[Unreleased]` cycle is invisible to users, and listing it advertises a
problem nobody could have had.

The test: check out the last release tag and ask whether a user could hit it there.

- Feature added this cycle, then corrected before release → no entry. The feature's own `Added`
  bullet describes the shipped behavior; that is the whole story.
- Bug that predates the cycle → entry, even if the code was touched for other reasons this cycle.

The same applies to a fix for a regression this cycle introduced: the net effect for users is
nothing changed, so there is nothing to announce.

## Checking your work

```bash
sed -n '/## \[Unreleased\]/,/^## /p' CHANGELOG.md
```
