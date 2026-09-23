# API 36 emulator connected verification — 2026-07-11

The dedicated `SellerKeyboardSmoke` AVD (`emulator-5554`, Android 16/API 36,
Google APIs x86_64) ran `scripts/run-emulator-connected.ps1` successfully. The wrapper starts the
AVD headlessly, waits for boot, invokes `verify-emulator-connected.ps1`, and shuts it down.

- Both current debug and Android-test APKs installed without physical-device approval.
- The latest AndroidJUnitRunner completed all 18 results in 36.873 seconds.
- Sixteen tests passed normally; two live Convex credential tests were identified JUnit assumptions.
- `ImeBenchmarkActivityInstrumentedTest` passed, proving initial and hot replacement editor contracts.
- Original background-activity-start settings are restored by the script.

Artifacts: `docs/evidence/verification/emulator-connected/latest/`.

This is emulator-only API/editor/lifecycle evidence. It does not replace physical-device touch,
haptic, OEM, battery/memory, accessibility acceptance, or same-device Gboard performance evidence.

The same zero-touch verifier also passes on the dedicated Android 15/API 35 AVD:
`docs/evidence/verification/emulator-connected/SellerKeyboardApi35/latest/summary.json` records
18 results, 2 identified opt-in live-backend skips, and a 45.719-second runner duration.

Android 14/API 34 passes the same gate at
`docs/evidence/verification/emulator-connected/SellerKeyboardApi34/latest/summary.json`: 18 results,
2 identified opt-in live-backend skips, and a 92.136-second runner duration.

## Accessibility observation

TalkBack bound successfully with touch exploration enabled, and SellerKeyboard became visible in
the benchmark editor. Injected external-Tab navigation caused alternating headless captures to lose
foreground glyphs while surfaces remained. The identical sequence reproduced on the bundled Gboard,
so this is now classified as a headless emulator/TalkBack capture artifact rather than a
SellerKeyboard-specific failure. See `emulator-talkback-control-2026-07-11.md`.

This is still not an accessibility pass. No traversal, spoken-action, Switch Access, or physical
external-keyboard claim is made from the headless screenshots.
