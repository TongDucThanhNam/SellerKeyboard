# API 36 emulator privacy/editor probe — 2026-07-11

AVD: `SellerKeyboardApi36Privacy`, Android 16 / API 36. The AVD was created from the official
Google APIs x86_64 image and started without loading or saving Quick Boot snapshots.

`scripts/verify-emulator-connected.ps1` completed the 18-result connected suite and then ran
`scripts/verify-ime-sensitive-transition.ps1` followed by
`scripts/capture-privacy-network-evidence.ps1` against the exact package-manager UID `10216`.

The live transition starts in a normal editor, populates text candidates, editor completions, and
inline autofill, then hot-switches the same host to a password field. The connected snapshot proves
that candidates, completions, inline suggestions, seller shortcuts, and clipboard listening are
cleared; voice and translation are idle; the active surface is forced to the keyboard; and late
completion/inline results cannot repopulate sensitive state. The raw state summary is at
`verification/emulator-connected/latest/ime-sensitive-transition/summary.json`.

The probe used three resolution-independent synthetic editor cases:

- plain text: `sellerprobeplain` was present in the focused editor node;
- password: the node reported `password=true` and the raw synthetic password was absent;
- OTP/authentication: the numeric value was present and
  `privateImeOptions=sellerkeyboard.authentication` configured the field.

Across those cases, UID-accounted RX and TX deltas were both zero and none of the three canaries
appeared in SellerKeyboard's UID-scoped logcat. The integrated result is at
`verification/emulator-connected/SellerKeyboardApi36Privacy/latest/summary.json`; per-case XML,
netstats output, UID logcat, and the detailed privacy summary are in its
`privacy-network-probe/` directory.

The integrated gate now applies the same exact-UID boundary around two feature sequences. The real
clipboard lifecycle records `0/0` RX/TX-byte deltas and zero scoped-log occurrences for its four
controlled values on API 26 and API 36. The full API 36 seller sequence—favorite/category/recent/
shortcut flows, reply, reviewed parameter, command search, general calculation, and seller
calculation—also records `0/0` and zero occurrences for its four controlled seller strings. The detailed artifacts are under each run's
`clipboard-lifecycle/network-attribution/` and `seller-workflow/network-attribution/` directories.

The API 36 gate additionally starts and cancels the configured on-device Google speech provider,
measuring both SellerKeyboard and `com.google.android.tts` UIDs, then deletes the ML Kit VI→EN
model, performs an explicit download/translation to a verified Preview, and measures both
SellerKeyboard and `com.google.android.gms`. A first run exposed four full-source occurrences from
ML Kit's native translator in both debug and non-debuggable builds. A scoped API 30+ native log
guard now contains that provider behavior; reruns record zero controlled-text occurrences in every
attributed log and restore Idle after cancellation. The non-debuggable minified proof is under
`translation-release-attribution/`.

The separate EN→VI UI gate then extracts the real visible translation from the synthetic UI tree,
matches it to the controller SHA-256, and completes source-preserving Insert, selection Replace,
and exact Restore in a distinct-package editor. Both the controlled English source and extracted
Vietnamese output occur zero times in the IME- and Play-services-scoped logs. Its result is under
`translation-ui/`.

The remote-image matrix additionally uses unique cache-busting fixture URLs: accept and reject each
produce one server request, unsupported MIME produces none, target outcomes remain exact, and
URL/fallback canaries remain absent from IME logs.

This proves the plain/password/OTP, clipboard, local/remote-image, seller-tool, provider-start voice,
VI→EN translation attribution, and EN→VI reviewed UI actions within controlled emulator sequences. It does not prove a
deterministic spoken transcript, future re-enabled sync, production image hosts, named apps, or an independent
packet-capture matrix.

The current release sync boundary is tested separately: a controlled snippet is written locally,
the ordinary Home route exposes no cloud controls, the Convex client/auth/backup lazies remain
uninitialized before and after launch, exact UID netstats stay at `0/0`, both title/body canaries are
absent from logs, and test data is removed. See `sync-release-boundary/`.

The default `SellerKeyboardSmoke` AVD was then recreated and the ordinary
`scripts/run-emulator-connected.ps1` entry point reproduced the same integrated result at
`verification/emulator-connected/latest/`; no special AVD argument or installer approval is
required for future runs. This is emulator evidence and does not replace Xiaomi/OEM acceptance.

The expanded endpoint gate also passes on Android 8 / API 26. Because that platform's logcat does
not implement `--uid`, the capture resolves the exact package-manager UID `10077`, verifies each
current SellerKeyboard PID through `/proc/<pid>/status`, and filters threadtime logcat using only
those PIDs. The corrected artifact contains real filtered lines, zero canary occurrences, and
supported UID netstats deltas of `0/0` bytes under
`verification/emulator-connected/SellerKeyboardApi26/latest/privacy-network-probe/`.
The current multi-ABI build also completes all API 26 connected gates with the native translation
privacy helper packaged; translation itself is fail-closed because Android 8 does not expose the
supported process-minimum logging API required to contain the observed provider logs.
