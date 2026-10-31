# D multi-node current state and rollback points — 2026-10-30

The fresh read found all three SoulGem containers already running and healthy; no container was started or restarted. A, B and C all use image ID `sha256:1f6fd76b942af40b751ede535ade03604de858ca4a40054406d4afb56fa5c1d4`. API ports remain loopback-only: A `18110`, B `18111`, C `18112`; each node retains its own Docker bridge and PostgreSQL data directory.

Before creating any B/C pairing or adding Homura mesh nodes, private, mode-0600 database backups were made and `pg_restore --list` parsed 267 archive entries for each:

- B: 113,964 bytes, SHA-256 `c84d383dc896a801e7cece5b830c8ad53bc088ae20f47e526f47ec38fd51ff83`.
- C: 113,956 bytes, SHA-256 `efcbb6477b20874314e8bfe0c5e12504ef21420ee9d45cdfac1737746c05bc3c`.

Paired app-state rollback points were also made before the mesh change, in private mode-0600 storage outside the repository:

- Homura app data: 745,984 bytes, SHA-256 `b2b5897d68e867110cfa8065c28e1ca02a458885ac27ae9bdb06ef3edb945697`.
- H2 app data: 181,248 bytes, SHA-256 `9fb58d4474121a58233a3a39fbba8313ed02e7650c6fbcf3c841ee271ec9aa86`.

The intended change is limited to adding test-node pairings on these existing test databases and mesh configuration on the test emulators. No application source, image, or system permission is changed. Rollback is to remove only the new test nodes/pairings; if that cannot restore the baseline, restore the matching app archive and the specific node's SQL dump. A full database restore removes later test/audit evidence, so preserve report snapshots first. No production service or unrelated container is touched.
