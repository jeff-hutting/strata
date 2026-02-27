# Conventional Commits — Strata

Run `git status` and `git diff --staged` to review staged changes, then write and run a commit using the format below.

## Format

```
<type>(<scope>): <short description>

[optional body — explain WHY, not what the diff already shows]

Co-Authored-By: Claude <model> <noreply@anthropic.com>
```

## Types

- `feat` — new feature or content
- `fix` — bug fix
- `docs` — documentation only (spec updates, review files, guide changes)
- `refactor` — code restructure without behavior change
- `test` — adding or updating tests
- `chore` — build config, tooling, dependency updates

## Scopes (Strata-specific)

Match the module name exactly:
- `strata-core`
- `strata-world`
- `strata-creator`
- `strata-rpg`
- `strata-structures`
- `workflow` or `docs` for cross-module documentation changes

## Rules

- Summary under 72 characters, lowercase after the colon, no trailing period
- Imperative mood: "add", not "added" or "adds"
- Body explains WHY — the diff shows what
- One logical unit per commit — don't bundle role output with implementation
- Build must pass before committing implementation changes

## Examples

```
feat(strata-world): add VerdantHighlands biome
fix(strata-core): fix asset registry NPE on null identifier
docs(strata-world): update SPEC to reflect generateInExistingWorlds removal
test(strata-world): add Phase 1 acceptance criteria test suite
refactor(strata-core): extract BiomeUtils from StrataWorldgen
chore: update Fabric Loader 0.18.1 → 0.18.4
docs(review): add strata-world Phase 1 code review findings
```

Now propose the commit message for the staged changes, run `git commit`, then `git push`.
