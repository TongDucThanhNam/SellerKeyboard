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
   silently installs through ADB, runs all 19 connected results, verifies a live sensitive-field
   transition, exercises the real clipboard-listener privacy lifecycle, verifies
   accepting/rejecting/unsupported rich-content reference editors, switches real IME sessions
   across package-bound profiles and verifies unbound global fallback, drives favorite/category/recent/
   slash-shortcut/reply/parameter/command/calculator seller flows into a distinct-package editor on
   API 36, then drives one-shot Shift, Caps Lock, punctuation auto-cap, Space cursor drag,
   swipe-delete, pointer-exit restoration, hold-delete repeat, stop-on-release, multi-pointer
   cancellation, and repeat shutdown during IME input-view teardown against that
   editor; it enforces exact-UID zero-network/
   zero-controlled-log attribution around clipboard, local/remote-image, and seller sequences, exercises
   configured voice and ML Kit translation providers with exact UID attribution, proves a real
   distinct-package EN→VI visible preview plus source-preserving Insert, selection Replace, exact
   Restore, repeat determinism, and zero source/output log canaries, repeats VI→EN translation in a
   non-debuggable minified APK, verifies the current release keeps Convex sync UI/dependencies
   absent and local data network-silent, runs the editor privacy probe, and shuts down an AVD it started. No Xiaomi installer
   approval is required. Run `connectedDebugAndroidTest` separately on physical devices; if Xiaomi
   returns `INSTALL_FAILED_USER_RESTRICTED`, classify it as an OEM install-permission blocker and
   schedule an attended approval window rather than blocking emulator automation. Use
   `prepare-xiaomi-attended-batch.ps1` beforehand and `run-xiaomi-attended-batch.ps1` during that
   window so all five APK prompts occur consecutively before automatic direct evidence capture.

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

## Manual QA Gates

All features must also be verified against the shared [Manual QA Matrix](00_manual_qa_matrix.md),
with one `docs/evidence/device-evidence-template.md` record per device/configuration.
