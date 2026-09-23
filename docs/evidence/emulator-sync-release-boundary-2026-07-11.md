# API 36 deferred-sync release boundary — 2026-07-11

Cloud backup is not exposed by the current production Home route (`showCloudBackup=false`). The
canonical API 36 gate makes that boundary measurable rather than relying on source inspection.

A shell-restricted debug provider in the default app process snapshots each lazy dependency, seeds
a controlled local snippet, and removes it afterward. The ordinary `HomeActivity` route is opened
between snapshots. The gate proves:

- the controlled snippet exists locally before and after Home launch;
- Cloud backup, Backup now, and Restore backup controls are absent;
- Convex client, authentication repository, and backup repository remain uninitialized;
- exact SellerKeyboard UID `10216` RX/TX-byte deltas remain `0/0`;
- the controlled title and body occur zero times in UID-scoped logs;
- cleanup restores the pre-test local database state.

Artifacts are under
`verification/emulator-connected/latest/sync-release-boundary/`, and the parent summary records
`syncDeferredReleaseBoundaryVerified=true` plus `syncCloudDependenciesInitialized=false`.

This proves sync is deferred and cold in the current release route. It is not evidence for a future
enabled sync implementation. Re-enabling requires an approved template-only schema, explicit
preview/consent, credentialed upload/restore/delete tests, network/log attribution, retention and
account-deletion review, and updated Play disclosures.
