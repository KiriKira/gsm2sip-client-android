# Duplicate mesh-node removal risk — before change

Recorded 2026-10-29 22:06 UTC, before removing any node.

GPT-6.1 Sol's read-only comparison of the pre-D PostgreSQL backups and the saved Homura app archive found two original, active B/C mesh sessions already present before this round's `mesh_add` calls. The calls added two duplicate secondary identities at mesh priorities 3 and 4. The originals at priorities 1 and 2 still have their retained pairing vaults and active SIM bindings; their sync status is healthy. Current UI shows the two later rows failing with `SIM_ID_CONFLICT`.

Planned action: use Homura's normal Settings → Other nodes → Remove controls for only the rows at priorities 4 and 3, in that order. Each removal revokes the duplicate gateway credential on its test node, marks that local mesh row removed, and clears only its duplicate local session vault. It does not delete the original B/C rows, SIM bindings, or databases. No SQL editing, pairing replay, application-data clear, root/Magisk, or server restart is planned. This is a reversible cleanup of this round's duplicate test pairings; if the selection cannot be unambiguously tied to priorities 3 and 4, stop without removing anything.

Rollback points already exist from before this round's mesh additions: Homura app archive `gw-before-mesh.tar` (SHA-256 `b2b5897d68e867110cfa8065c28e1ca02a458885ac27ae9bdb06ef3edb945697`) and the pre-D B/C database dumps (hashes in `d-multinode-current-baseline-20261030.md`). Restoring those snapshots would rewind later test events, so first preserve current SQL/evidence snapshots if a rollback is needed. The plan is limited to the three existing test nodes and their test emulator; no production service or unrelated container is involved.
