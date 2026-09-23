# Privacy/network typing probe — 2026-07-11

On the API 33 Xiaomi device, `scripts/capture-privacy-network-evidence.ps1` cleared logcat, polled SellerKeyboard UID 10299 network accounting, entered the synthetic canary `qwerty` through SellerKeyboard, polled again, and restored the original IME.

Result: PASS for this narrow probe.

- RX delta: 0 bytes.
- TX delta: 0 bytes.
- Canary occurrences in UID-scoped logcat: 0.
- Android added one UID accounting line after launch, but it contained zero traffic; the gate evaluates byte deltas.

Machine-readable results are in `docs/evidence/device-2026-07-11-privacy/summary.json`, with the UID-scoped logs and matching netstats lines beside it.

This does not replace a release privacy matrix across apps, profiles, offline/online states, sensitive editors, voice/translation flows, or an independent packet capture. It only demonstrates no UID-accounted traffic or typed-canary logging during one synthetic normal typing workflow on this device.
