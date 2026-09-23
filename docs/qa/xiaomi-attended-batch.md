# Xiaomi attended install batch

MIUI's ADB installer approval is treated as an attended OEM boundary. The workflow does not rely on
the unreliable **Always allow** option and does not attempt to bypass Xiaomi security controls.
Ordinary development and privacy verification remain zero-touch on the Android emulator.

## Prepare without the phone owner

```powershell
.\scripts\prepare-xiaomi-attended-batch.ps1
```

This runs the static gates and builds/hashes every APK before the approval window. It also inspects
the packaged manifests: the non-debuggable benchmark APK must contain the shell/same-signature-runner-guarded evidence
bridge, while the non-debuggable release APK must not contain that bridge. Preparation does not
connect to or modify the phone.

## One attended window

Connect and unlock the Xiaomi phone with USB debugging authorized, then run:

```powershell
.\scripts\run-xiaomi-attended-batch.ps1 -Serial <adb-serial>
```

Before triggering the first prompt, the runner verifies all five APK hashes against the prepared
manifest and rechecks the recorded benchmark/release security boundary. A changed or stale APK fails
before the attended window is consumed.

Approve five MIUI prompts in one attended window:

1. SellerKeyboard debug-signed version-24 upgrade fixture target.
2. Connected-test runner.
3. Neutral benchmark editor host.
4. Release-optimized SellerKeyboard benchmark target.
5. Macrobenchmark runner.

Prompts 1–3 occur first. The script runs the 22-result connected suite, emits an audible cue, and
then captures a reversible upgrade fixture before presenting prompts 4–5 back-to-back. After prompt
5, the owner can leave. The non-debuggable,
debug-signed benchmark target contains the same shell/same-signature-runner-guarded evidence bridge as the debug test
target; the release APK does not. The remaining automatic work covers the physical Emoji
search/variant/recents/clear/sensitive/privacy flow; real one-shot Shift, Caps Lock, punctuation
auto-cap, Space cursor, swipe-delete, pointer-exit, and hold-delete interactions; the three
Macrobenchmarks; exact-UID normal-typing privacy capture; and the reversible API 33 physical
configuration/editor/TalkBack capture. It also drives the SellerKeyboard disclosure dismissal and
retry, taps the Android microphone-permission action in English or Vietnamese, starts/cancels the
configured speech provider, and sends a byte-exact controlled PCM fixture through that provider into
the review-only result state. These post-approval checks reuse the already installed optimized APK;
they do not trigger a sixth install prompt. They restore the original microphone grant, default IME,
hard-keyboard IME setting, and private fixture exactly. Approval 4 is a real version-code upgrade from the prepared
version-24 fixture to the optimized version-25 candidate and additionally proves that
a legacy reply body and image URL, a pinned clipboard item, keyboard settings, and a versioned export
survive in the optimized APK. The touched records and preferences are restored to their exact
pre-test values before the remaining flows run. An emergency restore also runs from `finally` if the
batch stops after seeding. The batch restores the previous IME and the two temporary MIUI test
settings in `finally`, including on failure.

The batch intentionally does not claim to automate named third-party accounts, visible spoken
TalkBack/Switch Access acceptance, a real physical-microphone/acoustic-quality result, an external
keyboard, or the timed 40-scenario human Gboard
comparison. Those still require an attended/manual evidence session, but no additional
SellerKeyboard APK build is needed while the prepared benchmark target remains installed.
