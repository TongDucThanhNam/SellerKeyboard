# Connected emulator API matrix — 2026-07-11

`scripts/provision-and-run-emulator-api.ps1` provisions an official Google APIs x86_64 image and
AVD when missing, starts it headlessly, builds and installs the current debug/test APKs, executes
AndroidJUnitRunner directly, restores temporary settings, and shuts the AVD down.

| API | Android | Results | Identified credential skips | Runner time | Artifact |
|---:|---:|---:|---:|---:|---|
| 26 | 8.0 | 18 | 2 | 42.590 s | `verification/emulator-connected/SellerKeyboardApi26/latest/` |
| 30 | 11 | 18 | 2 | 44.750 s | `verification/emulator-connected/SellerKeyboardApi30/latest/` |
| 31 | 12 | 18 | 2 | 48.318 s | `verification/emulator-connected/SellerKeyboardApi31/latest/` |
| 34 | 14 | 18 | 2 | 92.136 s | `verification/emulator-connected/SellerKeyboardApi34/latest/` |
| 35 | 15 | 18 | 2 | 45.719 s | `verification/emulator-connected/SellerKeyboardApi35/latest/` |
| 36 | 16 | 18 | 2 | 44.184 s | `verification/emulator-connected/latest/` |

Every runner ended with `OK (18 tests)`. The two assumptions are the explicitly credential-gated
live Convex backup tests. API 33 is covered separately by the Xiaomi physical-device artifacts.
The current API 26 and API 36 artifacts also exercise hot editor-contract transitions across plain,
multiline, search, email, URI, password, OTP/authentication, phone, integer, signed decimal,
date/time, and `TYPE_NULL` fields; commit/readback through a local Chromium `contenteditable` field;
and Android incomplete-`InputConnection` fallback to paired DEL key events.
The same endpoint runs also complete 100 editor show/hide/configuration cycles, explicitly recreate
the host activity, reconnect in landscape, reconnect again in portrait, and finish with an attached,
focused multiline editor.

Both endpoint artifacts now additionally pass the live normal-to-password state reset, real system
clipboard privacy lifecycle, cross-UID rich-content accept/reject/unsupported matrix, and real IME
connection switching between SellerKeyboard and the neutral host. They validate distinct
package-bound effective profiles, remove the host binding, and prove the global fallback after
reconnection. API 26's
older logcat lacks `--uid`; its fallback verifies package-manager UID `10077` against live
`/proc/<pid>/status` entries before filtering threadtime logs by PID. The resulting artifact contains
real filtered lines, zero canary occurrences, and supported netstats deltas of `0/0` bytes.

This closes core connected-test API-level coverage only. Emulator evidence does not replace
physical-device interaction, Gboard performance, OEM behavior, target-app accounts, physical
form-factor acceptance, navigation modes, or accessibility acceptance.

## API 36 hardware profiles

The same connected gate passes on four API 36 hardware profiles:

| Profile | Resolution / density | RAM | Results | Runner time | Artifact |
|---|---|---:|---:|---:|---|
| Small Phone | 720×1280 / 320 dpi | 1 GB | 18 | 32.649 s | `verification/emulator-connected/SellerKeyboardApi36Small/latest/` |
| Medium Phone | 1080×2400 / 420 dpi | 2 GB | 18 | 44.184 s | `verification/emulator-connected/latest/` |
| Pixel 6 Pro / tall | 1440×3120 / 560 dpi | 2 GB | 18 | 38.064 s | `verification/emulator-connected/SellerKeyboardApi36Tall/latest/` |
| Medium Tablet | 2560×1600 / 320 dpi | 1907 MB | 18 | 39.901 s | `verification/emulator-connected/SellerKeyboardApi36Tablet/latest/` |

These profiles close basic emulator compact/mainstream/tall/tablet connected coverage. Separate
evidence under
`verification/emulator-large-window/tablet-foldable-width-2026-07-12/` now covers a cold-booted
Medium Tablet at native portrait and landscape plus a live 600 dp foldable-width resize, including
full-window hierarchies, 48 dp labeled controls, exact touch typing, and post-resize recovery. The
AVD exposes only the `DEFAULT` device state and no hinge sensor, so fold-posture testing is not
claimed from that tablet profile.

A separate cold-booted API 36 `SellerKeyboardApi36Fold` AVD uses Android's `pixel_fold` hardware
profile with `hw.sensor.hinge=yes`. Evidence under
`verification/emulator-foldable-posture/pixel-fold-api36-v4-2026-07-12/` records the declared AVD
configuration, all supported device states, four screenshots/hierarchies, and exact editor hashes.
SellerKeyboard remains attached without a host relaunch while the emulator moves through OPENED at
180° (2208×1840), HALF_OPENED at 90°, CLOSED at 0° on the 1080×2092 outer display, and back to the
OPENED inner display. Each posture retains at least 48 dp labeled controls and accepts an exact
four-key consonant-only canary; no SellerKeyboard or host fatal log is recorded. This closes emulator
hinge/posture coverage, not physical foldable acceptance.

## Navigation modes

API 36 Medium Phone passes the 18-result connected gate in both system navigation modes:

- Gestural navigation: `navigation_mode=2` (baseline API 36 artifact).
- Three-button navigation: `navigation_mode=0`, 18 results in 41.392 seconds at
  `verification/emulator-connected/SellerKeyboardApi36ThreeButton/latest/`.

The three-button overlay was restored to gestural before the AVD was shut down.

## API 36 low-memory process recreation

Evidence under `verification/emulator-low-memory/api36-complete-v2-2026-07-12/` starts a real
SellerKeyboard session in the distinct-UID neutral host, enters exact `skqvz` through visible keys,
backgrounds the host, delivers `TRIM_MEMORY_COMPLETE`, and invokes Android's documented `am kill`
background-process command. The host PID disappears; Android process-exit history records PID
`28934` with `subreason=24 (KILL BACKGROUND)`, and relaunch creates PID `29199`. The host's
transient editor correctly recreates empty, no stale canary appears, SellerKeyboard reattaches as the
visible IME, and exact fresh `mnbvc` typing succeeds with zero SellerKeyboard/host fatal logs.

This is deliberately scoped to emulator process-death recovery. Draft persistence is owned by the
target editor, not the IME, so the empty recreated neutral editor is not represented as keyboard
data loss or preservation.

## Integrated privacy/editor probe

A fresh snapshot-disabled API 36 AVD (`SellerKeyboardApi36Privacy`) and the recreated default
`SellerKeyboardSmoke` AVD both pass the integrated gate. The current default artifact records all
18 connected results in 44.184 seconds, proves the live normal-to-password state transition,
passes a system ClipboardManager lifecycle covering enabled capture, disabled non-persistence,
marked-sensitive rejection, password listener shutdown, and clear,
passes three cross-UID rich-content target contracts (granted JPEG URI read and accepted, JPEG
rejected with fallback, and unsupported MIME with fallback) with exact-UID `0/0` RX/TX-byte deltas
and zero fallback-text log occurrences,
passes a second cache-resistant remote-image matrix with one fixture request for accept, one for
reject, none for unsupported MIME, and zero URL/fallback log occurrences,
passes real cross-app package-profile selection and unbound global fallback,
drives real seller reply, parameter review/fill, command search, general calculator, and seller
calculator UI into a distinct-package editor while retaining all-window XML/screenshots; the
clipboard and seller sequences each have supported exact-UID `0/0` RX/TX-byte deltas and zero
controlled-text occurrences in scoped logs,
drives the installed IME through initial/manual one-shot Shift, Caps Lock double-tap/persistence/
exit, punctuation auto-cap, Space cursor movement and pointer-exit restoration, swipe-delete commit
and pointer-exit restoration, hold-delete repeat, and stop-on-release against the same neutral host,
verifies plain/password/OTP editor input against exact app UID `10216`, and finds `0` UID-log canary
occurrences. It also exercises real on-device voice start/cancel with IME/provider attribution and
real ML Kit VI→EN model deletion/download/Preview with IME/Google Play services attribution, then
executes reviewed EN→VI Insert/Replace/Restore in the real UI and repeats VI→EN translation in a
non-debuggable minified APK. Both translation paths contain an observed
provider-native full-text logging defect and now produce zero controlled-text log occurrences. The
current release sync boundary additionally proves local data remains present while cloud UI and all
Convex dependencies stay absent/cold with `0/0` bytes and zero controlled-text logs. The
current final run also records supported UID accounting with `0/0` RX/TX deltas in the measured
windows. See
`emulator-privacy-2026-07-11.md`, `emulator-core-interactions-2026-07-11.md`,
`verification/emulator-connected/latest/`, and
`verification/emulator-connected/SellerKeyboardApi36Privacy/latest/`.

Separate account-free named-target evidence at
`verification/emulator-named-apps/full-privacy-2026-07-12/` exercises Chrome
`133.0.6943.137` URL/search plus Google Messages search, fictional recipient `5550100`, and an
unsent sentence-cased `Skqvz` SMS draft entirely through visible SellerKeyboard key taps. The flow
is bracketed by exact SellerKeyboard-UID accounting and records supported `0/0` RX/TX deltas plus
zero `skqvz`, `Skqvz`, or `5550100` occurrences in UID-scoped logs. No query or message is
submitted, and both named apps are force-stopped after controlled drafts are cleared.
