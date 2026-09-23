# API 36 connected clipboard lifecycle — 2026-07-11

The default `SellerKeyboardSmoke` AVD ran `scripts/verify-clipboard-lifecycle.ps1` inside the
ordinary zero-touch `scripts/run-emulator-connected.ps1` gate. The test uses Android's real
`ClipboardManager` and the active IME listener; it does not call `ClipboardRepository.saveText`
directly.

The connected sequence proves:

- a normal editor starts the listener and persists an ordinary system clip;
- disabling clipboard history prevents a newly observed clip from being persisted, including after
  history is re-enabled;
- a clip carrying `ClipDescription.EXTRA_IS_SENSITIVE=true` reaches the listener event boundary but
  is rejected from storage;
- switching the same host to a password editor stops the listener, and a subsequent system clipboard
  change is neither observed nor retained;
- explicit clear finishes with zero repository items.

Evidence stores only counts, feature state, event counters, and fixed-match booleans. It does not
emit clipboard text. The machine-readable result is at
`verification/emulator-connected/latest/clipboard-lifecycle/summary.json`; the parent summary
records `clipboardLifecycleVerified: true`.

The lifecycle is wrapped by exact package-UID attribution. On both API 26 and API 36, supported
UID netstats remain at `0/0` RX/TX-byte deltas, and none of the four controlled clipboard values
occur in SellerKeyboard-scoped logcat. API 26 first verifies package-manager UID `10077` against
each live `/proc/<pid>/status` entry before applying its PID-filter fallback; API 36 uses exact
UID-scoped logcat for UID `10216`. Raw and summarized evidence is retained under each artifact's
`clipboard-lifecycle/network-attribution/` directory.

This is emulator system-service evidence. Physical OEM clipboard overlays and connected
pin/use/delete/expiry UI remain release work.

The same lifecycle passes on the API 26 endpoint artifact at
`verification/emulator-connected/SellerKeyboardApi26/latest/clipboard-lifecycle/`.
