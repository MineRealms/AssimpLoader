# Commit Date Correction

Applied to `feat/oculus-injector`, counted from the root commit with
`git rev-list --reverse --topo-order HEAD`. The history was linear.

- Commits 1-31: author and committer year changed from 2026 to 2025.
- Commits 32-51: author and committer year/month changed to December 2025.
- Day, clock time and UTC offset preserved independently for both timestamps.
- Commits 52-346: both timestamps preserved byte for byte.
- Every tree, author identity, committer identity, commit message and other header preserved.
- Descendant hashes changed because their parent hashes changed.
- Other local branches and remote `main` were not rewritten.
- Dirty working tree and index content were verified unchanged.

Boundary examples:

| Ordinal | Corrected Author Time |
| --- | --- |
| 1 | 2025-04-13 01:07:02 +08:00 |
| 31 | 2025-04-15 23:40:26 +08:00 |
| 32 | 2025-12-20 03:44:15 +08:00 |
| 51 | 2025-12-22 06:50:13 +08:00 |
| 52 | 2026-04-22 07:00:27 +08:00, unchanged |

Before: `a18b197fa7401c2f9b584a58bf6b148b37c5c784`.
After: `7f99d044dcc27256d9b288e753c4915f914c58fa`.
The remote branch was updated using a lease against its observed previous head
`ac563aa36448d505ab52af7da5c239d767c382b6`.

Local recovery bundle: `build/history-rewrite/before.bundle`.
Full per-commit old/new mapping and timestamp audit: `build/history-rewrite/audit.json`.
These recovery artifacts are intentionally outside the tracked source tree.
