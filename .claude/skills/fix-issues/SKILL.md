---
name: fix-issues
description: Triage open GitHub issues, fix the ones that are real bugs, and open a PR for each without merging. Use on "fix issues", "look at the issues", "work through the bug reports", or when handed an issue number to fix.
---

# Working through GitHub issues

One issue, one branch, one PR. Never merge, never close an issue directly - a PR that says
`Fixes #N` closes it when a human merges.

## Pick what to work on

```bash
gh issue list --state open --limit 30 --json number,title,labels,author,createdAt
gh issue view <n> --comments
```

With no issue named, work the oldest unlabelled bug first and stop after one unless told otherwise.
Fixing several at once produces a PR nobody can review.

## Triage before touching code

Fix only **reproducible defects**. Everything else gets a comment saying what it is and why, and no
branch:

| Not a fix | What it looks like |
|---|---|
| Feature request | "it would be great if", a Karate feature the plugin has never supported, anything on `site/status.md` as "Not planned" |
| Question or support | a Karate question rather than a plugin one; a broken build or classpath in the reporter's project |
| Needs a decision | two defensible behaviours and no obvious right one - ask in the issue, do not pick one silently |
| Not reproducible | say exactly what you tried, including versions, and ask for the missing piece |

A bug report that turns out to be **documented behaviour** is still not a fix: if the docs say it and
the reporter missed it, the gap is usually the docs, so fix those instead.

## Reproduce first, always

A fix without a reproduction is a guess. In rough order of cost:

1. A **unit test** in `src/test/java` (or `KarateTestRunner/src/test/java`) that fails for the reported
   reason. Cheapest, and it stays as the regression guard.
2. The **fixture** at `testProjects/karate-versions` - a v1 and a v2 module. Add a feature file that
   shows the problem rather than editing `sample/users*.feature`, whose line numbers other tests pin.
3. The **runner harnesses** for anything about runs or debugging: `:v1:debugHarness`,
   `:v2:debugHarness`, `:v2:eventProbe`, `:v2:debugProbe`. See `docs/DEBUGGER.md` and the
   `debug-test-runner` skill.
4. **`./gradlew runIde`** for anything that only exists on screen - gutter icons, tool windows,
   dialogs, breakpoints. Ask the user to drive it and tell you what they see; you cannot.

Reproducing in the reporter's Karate version matters: `gradle.properties` pins v1, and v2 is whatever
`karate-junit6` the module has. Most "it works for me" reports are version differences.

## Fix it the way the repo does

- `CLAUDE.md` has the build commands, the style rules and the architecture. Follow the file you are
  editing more than any general instinct.
- Keep the change the size of the bug. A refactor that would help belongs in its own issue.
- `./gradlew check` must pass. Run `./gradlew integrationTest --tests '*Karate2UITest'` as well when
  the change touches run configurations, the runner, the debugger or `plugin.xml` - it is the only
  thing that exercises a real IDE.
- A user-visible change needs a `CHANGELOG.md` entry (the `changelog` skill) and, when it changes what
  works or what a message says, the matching page under `site/`.
- Verify your edits landed. A scripted replace that silently matches nothing is the most common way to
  "fix" something and ship it unchanged.

## Open the PR

```bash
git checkout -b <named-after-the-change> origin/main   # never "claude" anywhere in the name
git commit                                             # no Co-Authored-By or session trailers
git push -u origin <branch>
gh pr create --title "..." --body "..."                # never --merge, never gh pr merge
```

The PR body says what was broken, why it happened, how it was fixed, and how it was verified - then
`Fixes #N`. No attribution footer.

Comment on the issue with a one-line summary and the PR link, so the reporter sees it without reading
a diff.

## When you cannot finish

Say so in the issue and stop. A half-fix with a passing build is worse than an honest comment: it
looks addressed and is not. If the repro works but the fix needs a product decision, put the
reproduction in the issue - that is the expensive half, and it makes the decision easy for whoever
takes it.
