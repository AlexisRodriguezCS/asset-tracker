# Design skills

Third-party skills for frontend design work, vendored here rather than installed
through their own installers.

| source | skills | what it is |
|---|---|---|
| [emilkowalski/skills](https://github.com/emilkowalski/skills) | `emil-design-eng`, `apple-design`, `animate`, `review-animations`, `find-animation-opportunities`, `animation-vocabulary`, `prototype`, `pick-ui-library` | animation and design-engineering guidance |
| [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill) | `taste-*` | design-taste rules and style variants |
| [pbakaus/impeccable](https://github.com/pbakaus/impeccable) | `impeccable` | design commands + anti-pattern detection |

## Why vendored

The upstream installers (`npx skills add …`, `npx impeccable install`) download and
execute third-party code. Fetching the published skill files over the GitHub API
and committing them gets the same result, with two advantages: the content is
reviewable in a diff, and it cannot change under us without a commit.

`write-swift`, `animate-expo` and `ask-sonner` were skipped - Swift, React Native
and a toast library this project does not use.

## Note on `impeccable`

Its setup step wants to run `scripts/impeccable`, a binary that downloads itself
on first run. That directory is deliberately not vendored. The skill documents a
fallback for exactly this case: read `PRODUCT.md` / `DESIGN.md`, then follow the
relevant `reference/*.md` playbook. Use that path.

## Updating

These are pinned by virtue of being committed. To refresh one, re-fetch its
`SKILL.md` from the source repo above and review the diff.
