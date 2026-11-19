# Development lint audit — 2026-10-31

## Scope and result

The original debug report contained **135 warnings**, no errors, and one
additional state-autoboxing hint. These were not 135 functional bugs.

The cleanup report contains **0 errors and 6 warnings**, all dependency-update
advisories listed below. No lint baseline, global rule disable, severity downgrade,
or abortOnError=false was added. Intentional local exceptions are documented
at their call sites and explained here.

Application ID, minSdk 26, compile/target SDK 36, version code/name, signing
configuration, and production backend configuration are unchanged.

## Original warnings

| Category | Count | Treatment |
| --- | ---: | --- |
| Kotlin extensions (UseKtx) | 49 | Converted 40 cases; retained 9 synchronous commits that must return success/failure. |
| Dependency updates | 24 | Removed unused dependencies, updated compatible libraries, aligned app/test Compose BOMs; 6 advisories remain. |
| Unused resources | 19 | Removed unused strings, colors, legacy layouts, styles and bitmaps, including locale overrides. Live launcher artwork is unchanged. |
| Ellipsis typography | 9 | Used the Unicode ellipsis in loading text. |
| Quantity strings | 8 | Added English/Vietnamese plurals and separate insert/skip quantities in the restore dialog. Vietnamese correctly has only other. |
| Inlined newer API constants | 5 | Used the documented backward-compatible clipboard metadata key and the stable shell UID. The previously inlined values were not runtime API calls. |
| Exported providers | 3 | Preserved UID/signature-protected debug bridges; explicitly made ML Kit initialization non-exported. |
| Compose modifier position | 3 | Made modifier the first optional parameter; callers use named arguments. |
| Obsolete SDK checks/qualifiers | 3 | Removed unreachable branches and redundant v26 launcher qualification. |
| Unclosed trace sections | 3 | Guaranteed key-dispatch trace closure even if composition cleanup throws; added activity-destroy cleanup; documented two cross-call trace contracts. |
| Bitmap location / newer attributes | 4 | Removed unused densityless bitmaps; qualified inline suggestions with xml-v30; documented profileable's API-29 behavior. |
| Private color, unused layout wrapper, hardcoded layout text, JavaScript, vector path | 5 | Removed legacy resources, hardened the local test WebView, and split independent vector glyphs without changing any coordinates. |

The extra state hint was also fixed with mutableIntStateOf and intValue at
all reads/writes, avoiding the follow-on boxing warnings.

## Intentional local exceptions

- Nine UseKtx suggestions concern commit() calls whose Boolean result is checked
  or returned. SharedPreferences.edit(commit=true) returns Unit; substituting it
  would lose rollback/persistence failure checks.
- PerformanceTracer.begin is paired by the scoped helper's finally block.
  The benchmark's cross-callback section is paired by IME-visible insets or
  activity destruction. Existing OS trace labels/measurement contracts remain.
- Two providers are intentionally exported **only in debug/fixture manifests**
  for adb shell and trusted, signature-checked test callers. Their Binder checks
  remain in place; a manifest signature permission would block the shell caller.
- JavaScript is necessary for the fixed local contenteditable test fixture.
  Network loads, external navigation, file access, and content access are blocked.
  The exception is confined to that fixture's initialization.

These are method/provider-level exceptions, not switches that hide unrelated
future findings.

## Why six dependency advisories remain

| Dependency | Kept | Available at audit | Verified reason |
| --- | --- | --- | --- |
| Lifecycle runtime KTX | 2.10.0 | 2.11.0 | Upgrading the group selects Compose runtime/viewmodel artifacts requiring compileSdk 37. |
| Lifecycle service | 2.10.0 | 2.11.0 | Same group-alignment constraint. |
| Navigation Compose | 2.9.7 | 2.10.2 | Android artifact requires compileSdk 37. |
| Glide | 4.16.0 | 5.0.9 | Glide and its gifdecoder/disklrucache artifacts require compileSdk 37. |
| Kotlin serialization JSON | 1.9.0 | 1.12.0 | 1.9.0 is built against Kotlin 2.2.0; 1.12.0 is built against Kotlin 2.4.10, beyond the project's Kotlin/serialization compiler 2.2.10. |
| Tracing | 1.3.0 | 2.0.5 | Retained the system-trace path used by existing evidence gates; adopting the new major in-process pipeline needs separate capture/benchmark validation. |

The attempted Lifecycle/Glide upgrade was rejected by Gradle's AAR metadata
check and was reverted without bypassing that check. AGP 9.1.0 reports a maximum
recommended compile SDK of 36. The newer Compose BOM 2026.09.00 also selects UI
1.12.1, whose AAR requires SDK 37; both app and instrumentation therefore share
compatible BOM 2026.02.01 rather than mixing 2026 and 2024 BOMs.

Primary artifacts checked:

- [Navigation Android AAR](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose-android/2.10.2/navigation-compose-android-2.10.2.aar)
- [Lifecycle Compose Android AAR](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose-android/2.11.0/lifecycle-runtime-compose-android-2.11.0.aar)
- [Glide AAR](https://repo.maven.apache.org/maven2/com/github/bumptech/glide/glide/5.0.9/glide-5.0.9.aar)
- [Serialization 1.12.0 POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.12.0/kotlinx-serialization-json-jvm-1.12.0.pom)
- [Compose BOM POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom)
- [Tracing release notes](https://developer.android.com/jetpack/androidx/releases/tracing)

## Verification

- Debug APK, instrumentation APK, and **197 JVM tests** passed.
- Debug lint passed with 0 errors / 6 dependency advisories.
- Release AAB passed, including R8 and release vital lint. This is a local
  unsigned build, not a publication or upload.
- Device result: awaiting the two IME fixture retests; 18 other device tests passed.

Commands from the repository root:

    .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
    .\gradlew.bat :app:bundleRelease
    # Install the neutral host before running the IME gesture test:
    $env:ANDROID_SERIAL = 'emulator-5554'
    .\gradlew.bat :benchmark-host:installBenchmark

Device verification targets settings persistence, database migrations, Android
input contracts, WebView input/security settings, key geometry, image loading,
localized quantities, gesture cancellation, and 100 show/hide/editor-switch cycles.

Two first-run device prerequisites were diagnosed rather than skipped:
the neutral benchmark host was not installed, and this Android 9 image returned
a stale default_input_method setting despite the live service and rendered
keyboard being SellerKeyboard. The lifecycle assertion now polls the live
InputMethodManager service ID and still requires the exact expected component.

The device run is intentionally scoped, not a claim that backend/cloud or
API-30+ autofill/voice integration suites passed on this API-28 emulator.

## Separate build-tool deprecations

AGP opt-outs/legacy DSL warnings and the existing Material3 swipe callback
deprecation are not part of the 135 lint warnings. They remain visible and were
not silenced. A toolchain/Compose migration should handle those separately;
this cleanup does not claim that the entire Gradle console is warning-free.

The Windows setup script's SDK drive-letter escaping was also corrected.
Machine-local local.properties remains ignored by Git.
