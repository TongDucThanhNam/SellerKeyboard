# Seller-Competitive Implementation Evidence — 2026-07-11

## Static gate result

The repository currently passes all hardware-independent gates:

- `:app:testDebugUnitTest`: 156 tests, 50 suites, zero failures, errors, or skips.
- `:app:candidateCorpusTest`: 800 VI/EN labeled cases, top-3 recall `0.9875`; 200 protected identifiers, zero destructive mutations.
- `:app:sellerWorkflowBenchmark`: 40 modeled scenarios; Gboard/manual median 34.5 interactions, customized SellerKeyboard median 4.5, estimated reduction `86.96%`.
- `:app:keyDispatchPerformanceTest`: 10,000 measured dispatches after warm-up; latest verification host p95 `1,000 ns`. This is an internal host measurement, not an IME/device latency claim.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, and `:benchmark:assembleBenchmark` pass.
- Compose compiler metrics and stability reports are generated under `app/build/compose_compiler/` and copied by the verification script.

The deterministic latest run is recorded under `docs/evidence/verification/latest/`. The overall result is intentionally `FAIL`: connected Android tests and Macrobenchmark execution were blocking skips because `adb devices` reported no authorized device. No missing-device exception is approved.

## Performance instrumentation

The benchmark module targets the non-debuggable, release-derived `benchmark` app variant and measures cold benchmark-editor launch plus IME display with startup and frame-timing metrics over ten iterations. The app is shell-profileable for benchmark tracing. Perfetto section names are stable and text-free:

- `SellerKeyboard.keyDispatch`
- `SellerKeyboard.candidateQuery`
- `SellerKeyboard.surfaceSwitch`
- `SellerKeyboard.templateInsertion`

No typed, candidate, template, clipboard, or selected content is placed in trace labels.

## Privacy architecture changes

- Editor privacy is centralized and fail-closed for passwords, detectable auth/OTP fields, writing-tools opt-out, and user-private profiles.
- Sensitive mode disables candidates, learning, clipboard, seller tools, voice, translation, and rich content; system-owned inline autofill remains separately gated.
- Cloud repositories are lazy and are not constructed when the IME process starts.
- Android automatic cloud backup is disabled.
- The app-wide media read permissions were removed; user-selected image content uses scoped document grants.
- System-marked sensitive clips are rejected rather than retained.
- Clipboard retention is limited to 20 unpinned items plus user pins, with 30-day expiry and clear/delete controls.
- Rich-content loading captures the editor session ID and refuses stale insertion or stale fallback after a field switch.

## Evidence still required

Release cannot be declared until the following are run and linked:

1. Same-device Gboard behavior/geometry and 40-scenario timed seller comparison with version/settings captured.
2. Connected tests across the required API/editor/app/orientation/navigation matrix.
3. Macrobenchmark and Perfetto on one lower-midrange and one mainstream physical device, including Gboard ratio, frame/frozen-frame, memory, and 100 lifecycle cycles.
4. Network and log inspection for normal typing and every explicit network-capable utility.
5. TalkBack, Switch Access, font scale, contrast, reduced-animation, and external-keyboard audits.
6. Product decisions still marked `[needs confirmation]` in `GOAL.md`, including reference devices/apps, dictionary licensing, voice-provider acceptance, stale product policy, and image/cache ownership.

The modeled seller interaction benchmark is useful regression evidence but is not represented as the required same-device competitive result.
