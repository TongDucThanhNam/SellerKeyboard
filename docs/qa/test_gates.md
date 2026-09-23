# Competitive Release Test Gates

These commands are subordinate to `GOAL.md` and `scripts/verify-competitive-keyboard.ps1`. Passing
only the host commands is not release completion.

## Required Test Commands

Run these from the repository root. On Windows, use `.\gradlew.bat`; on macOS/Linux, use `./gradlew`.

1. **Run Unit Tests**
   ```bash
   .\gradlew.bat :app:testDebugUnitTest
   ```
   *Gate:* All existing and new unit tests must pass.

2. **Build the debug APK**
   ```bash
   .\gradlew.bat :app:assembleDebug
   ```
   *Gate:* Must compile successfully.

3. **Run Lint**
   ```bash
   .\gradlew.bat :app:lintDebug
   ```
   *Gate:* Must pass. New lint errors should be fixed. Existing ones can be categorized.

4. **Run corpus, workflow, and dispatch gates**
   ```bash
   .\gradlew.bat :app:candidateCorpusTest
   .\gradlew.bat :app:sellerWorkflowBenchmark
   .\gradlew.bat :app:keyDispatchPerformanceTest
   ```
   *Gate:* Candidate/protected-token/undo targets, the 40-scenario model, action ordering, and host
   dispatch threshold must pass. The modeled workflow result does not replace timed device evidence.

5. **Confirm a connected Android device**
   ```bash
   adb devices
   adb shell getprop ro.product.manufacturer
   adb shell getprop ro.product.model
   adb shell getprop ro.build.version.release
   ```
   *Gate:* At least one device must be listed as `device`, not `unauthorized` or `offline`.

6. **Enable and select the IME for manual/device QA**
   ```bash
   adb shell ime list -s
   adb shell ime enable com.terasumi.sellerkeyboard/.service.SellerKeyboard
   adb shell ime set com.terasumi.sellerkeyboard/.service.SellerKeyboard
   ```
   *Gate:* Commands must succeed unless blocked by Android security policy. Record the exact blocker.

7. **Run Instrumented Tests**
   ```bash
   .\scripts\run-emulator-connected.ps1
   ```
   *Gate:* This is the default zero-touch gate. It provisions/starts the configured AVD when needed,
   silently installs through ADB, runs all 22 connected results, verifies a live sensitive-field
   transition, exercises the real clipboard-listener privacy lifecycle, verifies
   accepting/rejecting/unsupported rich-content reference editors, switches real IME sessions
   across package-bound profiles and verifies unbound global fallback, drives favorite/category/recent/
   slash-shortcut/reply/parameter/command/calculator seller flows into a distinct-package editor on
   API 36, then drives one-shot Shift, Caps Lock, punctuation auto-cap, Space cursor drag,
   swipe-delete, pointer-exit restoration, hold-delete repeat, stop-on-release, multi-pointer
   cancellation, and repeat shutdown during IME input-view teardown against that
   editor; it enforces exact-UID zero-network/
   zero-controlled-log attribution around clipboard, local/remote-image, and seller sequences, exercises
   configured voice and ML Kit translation providers with exact UID attribution, drives the complete
   voice disclosure/system-permission flow, sends byte-exact controlled PCM through the configured
   speech provider into review state without changing the editor, restores permission/IME state,
   proves a real
   distinct-package EN→VI visible preview plus source-preserving Insert, selection Replace, exact
   Restore, repeat determinism, and zero source/output log canaries, repeats VI→EN translation in a
   non-debuggable minified APK, verifies the current release keeps Convex sync UI/dependencies
   absent and local data network-silent, runs the editor privacy probe, and shuts down an AVD it started. No Xiaomi installer
   approval is required. Run `connectedDebugAndroidTest` separately on physical devices; if Xiaomi
   returns `INSTALL_FAILED_USER_RESTRICTED`, classify it as an OEM install-permission blocker and
   schedule an attended approval window rather than blocking emulator automation. Use
   `prepare-xiaomi-attended-batch.ps1` beforehand and `run-xiaomi-attended-batch.ps1` during that
   window for five APK prompts total. Prompts 1–3 occur first; the connected suite then runs and an
   audible cue announces back-to-back prompts 4–5. Before prompt 4, a reversible fixture captures
   the exact touched user state. Prompt 1 installs a debug-signed version-24 source and prompt 4
   upgrades it to the optimized version-25 candidate. That upgrade must preserve a legacy reply/body/image,
   pinned clipboard item, keyboard settings, and a minified versioned export; the fixture is restored
   exactly afterward. After prompt 5, the physical voice disclosure/system-permission and controlled
   configured-provider audio gates, Emoji flow, and all remaining direct evidence capture run
   automatically with no additional APK install. Preparation packages the shell-guarded
   evidence bridge only in the non-debuggable benchmark APK, proves it is absent from the release
   APK, and the attended runner rejects any APK whose hash changed after preparation.

   Run the unattended multi-API compatibility matrix after changing connected-test code or APKs:
   ```bash
   .\scripts\verify-emulator-api-matrix.ps1 -ReusePassingEvidence
   ```
   It cold-boots API 26/30/31/34/35 one at a time on a separate emulator port, preserves any
   already-running emulator, silently installs through ADB, requires all 22 current results and
   connected privacy probes, restores both app and instrumentation microphone-permission state,
   and writes `docs/evidence/verification/emulator-api-matrix/latest/summary.json`. Reuse is allowed
   only when the per-API evidence is newer than the current instrumentation APK. This remains
   emulator compatibility evidence and cannot satisfy physical-device or OEM acceptance gates.

   The standalone personal-dictionary evidence gate is also unattended and emulator-only:
   ```bash
   .\scripts\verify-personal-dictionary-flow.ps1 -Serial emulator-5554 -OutputDirectory docs\evidence\verification\emulator-dictionary\api36-<run>
   ```
   It uses the real product settings UI and installed IME for add, process persistence, personal
   candidate commit, sensitive suppression, remove, and clear; brackets the flow with exact-UID
   network/log attribution; and restores the original dictionary and language/learning settings.

   The standalone emoji evidence gate is unattended and emulator-only:
   ```bash
   .\scripts\verify-emoji-flow.ps1 -Serial emulator-5554 -OutputDirectory docs\evidence\verification\emulator-emoji\api36-<run>
   ```
   It drives the real installed IME through toolbar open, local-keypad search, exact result and
   skin-tone variant commits, recents recreation, product-UI Clear recreation, masked-editor
   suppression, semantics/touch-height checks, and exact-UID network/log attribution. Its fixture
   restores the original ordered recents and Emoji2 sticky-variant preference exactly.

8. **Run connected performance evidence**
   ```bash
   .\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
   ```
   *Gate:* Retain Macrobenchmark JSON and Perfetto/frame/memory artifacts. Execute the 100-cycle IME
   lifecycle test and compare show-to-interactive against the recorded same-device Gboard setup.

9. **Run the authoritative verifier**
   ```bash
   powershell -ExecutionPolicy Bypass -File .\scripts\verify-competitive-keyboard.ps1
   ```
   *Gate:* Must exit zero on the release candidate. Missing hardware, credentials, connected-test
   skips, or performance execution remain blocking unless a documented product exception is passed
   explicitly to the script.

The named password-manager Autofill artifact can be reproduced independently on a clean API 36
emulator before the authoritative verifier:

```bash
.\scripts\verify-named-password-manager-autofill.ps1 -Serial emulator-5554
```

The script pins KeePassDX 4.4.5, verifies its official APK hash and signing certificate, creates
only a fictional local vault, requires exact two-field fill and zero UID traffic/log leakage, and
restores the prior Autofill service, IME, package presence, and fixture-vault presence.

## Manual QA Gates

All features must also be verified against the shared [Manual QA Matrix](00_manual_qa_matrix.md),
with one `docs/evidence/device-evidence-template.md` record per device/configuration.
