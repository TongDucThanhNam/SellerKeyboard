package com.terasumi.sellerkeyboard.benchmark

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMacrobenchmarkApi::class)
class ImeShowBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()
    private var previousIme: String? = null
    private var sellerBenchmarkSeeded = false

    @After
    fun restoreBenchmarkState() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (sellerBenchmarkSeeded) {
            device.executeShellCommand("ime enable $SELLER_IME_COMPONENT")
            device.executeShellCommand("ime set $SELLER_IME_COMPONENT")
            val launchResult = device.executeShellCommand(
                "am start -W -n $TARGET_PACKAGE/$BENCHMARK_ACTIVITY"
            )
            check("Status: ok" in launchResult) {
                "Benchmark host restoration launch failed: $launchResult"
            }
            check(
                device.wait(
                    Until.hasObject(By.pkg(SELLER_PACKAGE)),
                    IME_VISIBILITY_TIMEOUT_MILLIS
                )
            ) { "SellerKeyboard did not become visible for fixture restoration" }
            val restoreResult = InstrumentationRegistry.getInstrumentation().context
                .contentResolver.call(
                    Uri.parse(SELLER_TEST_BRIDGE),
                    "restore_seller_benchmark",
                    null,
                    null
                )
            check(restoreResult?.getBoolean("seller_benchmark_restored") == true) {
                "Seller benchmark fixture restoration failed: $restoreResult"
            }
            sellerBenchmarkSeeded = false
        }
        previousIme?.takeIf(String::isNotBlank)?.let { ime ->
            device.executeShellCommand("ime set $ime")
        }
    }

    @Test
    fun coldEditorLaunchAndSellerKeyboardImeShow() = measureImeShow(SELLER_IME_COMPONENT)

    @Test
    fun coldEditorLaunchAndGboardImeShowReference() = measureImeShow(GBOARD_IME_COMPONENT)

    @OptIn(ExperimentalMetricApi::class)
    private fun measureImeShow(imeComponent: String) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            TraceSectionMetric(
                sectionName = IME_SHOW_TRACE_SECTION,
                mode = TraceSectionMetric.Mode.First
            )
        ),
        // The installed optimized target is identical for the SellerKeyboard and
        // Gboard runs. Ignore prevents Macrobenchmark from reinstalling it between
        // tests, which would invoke protected OEM installer UI and change the
        // variable under measurement from IME choice to package-install policy.
        compilationMode = CompilationMode.Ignore(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            if (previousIme == null) {
                previousIme = device.executeShellCommand(
                    "settings get secure default_input_method"
                ).trim()
            }
            selectImeOrThrow(imeComponent)
            pressHome()
        }
    ) {
        startActivityAndWait(
            Intent().apply {
                component = ComponentName(TARGET_PACKAGE, BENCHMARK_ACTIVITY)
            }
        )
        device.waitForIdle()
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun oneHundredImeShowHideCyclesTrackFramesAndMemory() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(
                mode = MemoryUsageMetric.Mode.Max,
                subMetrics = listOf(
                    MemoryUsageMetric.SubMetric.HeapSize,
                    MemoryUsageMetric.SubMetric.RssAnon
                )
            )
        ),
        compilationMode = CompilationMode.Ignore(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            if (previousIme == null) {
                previousIme = device.executeShellCommand(
                    "settings get secure default_input_method"
                ).trim()
            }
            selectImeOrThrow(SELLER_IME_COMPONENT)
            pressHome()
            // Keep benchmark-host startup, EGL initialization, and its first-frame
            // shader compilation outside the show/hide-cycle measurement. The
            // stress metric is intended to cover only settled IME transitions.
            startActivityAndWait(
                Intent().apply {
                    component = ComponentName(TARGET_PACKAGE, BENCHMARK_ACTIVITY)
                }
            )
            awaitSellerImeVisibility(visible = true, phase = "setup")
        }
    ) {
        repeat(CYCLES_PER_ITERATION) { cycle ->
            device.pressBack()
            awaitSellerImeVisibility(visible = false, phase = "cycle ${cycle + 1} hide")
            device.click(device.displayWidth / 2, device.displayHeight / 3)
            awaitSellerImeVisibility(visible = true, phase = "cycle ${cycle + 1} show")
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun productionKeyCandidateSurfaceAndTemplateFlowsHavePerfettoSections() {
        prepareSellerFeatureBenchmarkFixture()
        benchmarkRule.measureRepeated(
            packageName = SELLER_PACKAGE,
            metrics = listOf(
                TraceSectionMetric(KEY_DISPATCH_TRACE, TraceSectionMetric.Mode.Max),
                TraceSectionMetric(CANDIDATE_QUERY_TRACE, TraceSectionMetric.Mode.Max),
                TraceSectionMetric(CANDIDATE_DISPLAY_TRACE, TraceSectionMetric.Mode.Max),
                TraceSectionMetric(SURFACE_SWITCH_TRACE, TraceSectionMetric.Mode.Max),
                TraceSectionMetric(TEMPLATE_INSERTION_TRACE, TraceSectionMetric.Mode.Max)
            ),
            compilationMode = CompilationMode.Ignore(),
            startupMode = StartupMode.WARM,
            iterations = 3,
            setupBlock = {
                if (previousIme == null) {
                    previousIme = device.executeShellCommand(
                        "settings get secure default_input_method"
                    ).trim()
                }
                selectImeOrThrow(SELLER_IME_COMPONENT)
                pressHome()
                device.executeShellCommand("am force-stop $TARGET_PACKAGE")
                launchBenchmarkHostWithoutTargetFrameWait()
                awaitSellerImeVisibility(visible = true, phase = "feature trace setup")
            }
        ) {
            val letterKey = device.wait(
                Until.findObject(By.desc("S")),
                UI_OBJECT_TIMEOUT_MILLIS
            ) ?: device.wait(
                Until.findObject(By.desc("s")),
                UI_OBJECT_TIMEOUT_MILLIS
            )
            checkNotNull(letterKey) { "Could not find the production S key" }.click()
            val candidateSeedResult = InstrumentationRegistry.getInstrumentation().context
                .contentResolver.call(
                    Uri.parse(SELLER_TEST_BRIDGE),
                    "seed_candidate_trace",
                    null,
                    null
                )
            check(candidateSeedResult?.getBoolean("active") == true) {
                "Candidate trace fixture failed: $candidateSeedResult"
            }
            awaitCandidateCommit("measured candidate display")

            val replies = device.wait(
                Until.findObject(By.desc("Snippet List")),
                LOCALIZED_UI_OBJECT_TIMEOUT_MILLIS
            ) ?: device.wait(
                Until.findObject(By.desc("Snippets")),
                LOCALIZED_UI_OBJECT_TIMEOUT_MILLIS
            )
            checkNotNull(replies) { "Could not find the Replies toolbar action" }.click()
            checkNotNull(
                device.wait(
                    Until.findObject(By.desc("Search seller replies and commands")),
                    UI_OBJECT_TIMEOUT_MILLIS
                )
            ) { "Replies surface did not settle" }

            val reply = device.wait(
                Until.findObject(By.descStartsWith("Reply Benchmark ")),
                UI_OBJECT_TIMEOUT_MILLIS
            )
            checkNotNull(reply) { "Could not find a benchmark reply" }.click()
            val insert = device.wait(
                Until.findObject(By.text("Insert reviewed reply")),
                UI_OBJECT_TIMEOUT_MILLIS
            ) ?: device.wait(
                Until.findObject(By.desc("Insert reviewed reply")),
                LOCALIZED_UI_OBJECT_TIMEOUT_MILLIS
            ) ?: device.wait(
                Until.findObject(By.textContains("Insert")),
                LOCALIZED_UI_OBJECT_TIMEOUT_MILLIS
            )
            if (insert != null) {
                insert.click()
            } else {
                // Compose IME button text is not consistently indexed by UiAutomator on API 36.
                // The review action has a stable portrait position in this benchmark fixture.
                device.click(
                    device.displayWidth * TEMPLATE_INSERT_X_PERCENT / 100,
                    device.displayHeight * TEMPLATE_INSERT_Y_PERCENT / 100
                )
            }
            device.waitForIdle()
        }
    }

    private fun prepareSellerFeatureBenchmarkFixture() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (previousIme == null) {
            previousIme = device.executeShellCommand(
                "settings get secure default_input_method"
            ).trim()
        }
        val enableResult = device.executeShellCommand("ime enable $SELLER_IME_COMPONENT")
        val setResult = device.executeShellCommand("ime set $SELLER_IME_COMPONENT")
        check("now enabled" in enableResult || "already enabled" in enableResult) {
            "IME enable failed for feature trace setup: $enableResult"
        }
        check("selected" in setResult) {
            "IME selection failed for feature trace setup: $setResult"
        }
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        val launchResult = device.executeShellCommand(
            "am start -W -n $TARGET_PACKAGE/$BENCHMARK_ACTIVITY"
        )
        check("Status: ok" in launchResult) {
            "Benchmark host fixture launch failed: $launchResult"
        }
        check(
            device.wait(
                Until.hasObject(By.pkg(SELLER_PACKAGE)),
                IME_VISIBILITY_TIMEOUT_MILLIS
            )
        ) { "SellerKeyboard did not become visible for fixture seeding" }
        val seedResult = InstrumentationRegistry.getInstrumentation().context
            .contentResolver.call(
                Uri.parse(SELLER_TEST_BRIDGE),
                "seed_seller_benchmark",
                null,
                null
            )
        check(
            seedResult?.getBoolean("seller_benchmark_seeded") == true &&
                seedResult.getInt("seller_benchmark_template_count") == 40
        )
        {
            "Seller benchmark fixture seeding failed: $seedResult"
        }
        sellerBenchmarkSeeded = true
        val candidatePrewarmResult = InstrumentationRegistry.getInstrumentation().context
            .contentResolver.call(
                Uri.parse(SELLER_TEST_BRIDGE),
                "seed_candidate_trace",
                null,
                null
            )
        check(candidatePrewarmResult?.getBoolean("active") == true) {
            "Candidate provider prewarm failed: $candidatePrewarmResult"
        }
        awaitCandidateCommit("candidate provider prewarm")
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    }

    private fun awaitCandidateCommit(phase: String) {
        val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
        val deadline = SystemClock.uptimeMillis() + CANDIDATE_UI_TIMEOUT_MILLIS
        var snapshot = resolver.call(Uri.parse(SELLER_TEST_BRIDGE), "snapshot", null, null)
        while (
            SystemClock.uptimeMillis() < deadline &&
            (snapshot?.getString("candidate_source") != "TEXT_CANDIDATE" ||
                snapshot.getInt("candidate_count") <= 0)
        ) {
            SystemClock.sleep(CANDIDATE_POLL_MILLIS)
            snapshot = resolver.call(Uri.parse(SELLER_TEST_BRIDGE), "snapshot", null, null)
        }
        check(
            snapshot?.getString("candidate_source") == "TEXT_CANDIDATE" &&
                snapshot.getInt("candidate_count") > 0
        ) { "$phase did not commit the candidate strip: $snapshot" }
    }

    private fun benchmarkHostIntent() = Intent().apply {
        component = ComponentName(TARGET_PACKAGE, BENCHMARK_ACTIVITY)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope
        .launchBenchmarkHostWithoutTargetFrameWait() {
        val result = device.executeShellCommand(
            "am start -W -n $TARGET_PACKAGE/$BENCHMARK_ACTIVITY"
        )
        check("Status: ok" in result) {
            "Benchmark host launch failed: $result"
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.awaitSellerImeVisibility(
        visible: Boolean,
        phase: String
    ) {
        val sellerWindow = By.pkg(SELLER_PACKAGE)
        val reachedExpectedState = if (visible) {
            device.wait(Until.hasObject(sellerWindow), IME_VISIBILITY_TIMEOUT_MILLIS)
        } else {
            device.wait(Until.gone(sellerWindow), IME_VISIBILITY_TIMEOUT_MILLIS)
        }
        check(reachedExpectedState) {
            "SellerKeyboard IME did not become ${if (visible) "visible" else "hidden"} " +
                "during $phase"
        }
        // Accessibility-window visibility changes before every next action, and
        // this idle wait prevents a new transition from reversing an in-flight
        // system insets animation.
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.selectImeOrThrow(
        imeComponent: String
    ) {
        val enableResult = device.executeShellCommand("ime enable $imeComponent")
        val setResult = device.executeShellCommand("ime set $imeComponent")
        val selectedIme = device.executeShellCommand(
            "settings get secure default_input_method"
        ).trim()
        check("now enabled" in enableResult || "already enabled" in enableResult) {
            "IME enable failed for $imeComponent: $enableResult"
        }
        check("selected" in setResult) {
            "IME selection failed for $imeComponent: $setResult"
        }
        check(selectedIme == imeComponent) {
            "Expected $imeComponent but Android selected $selectedIme"
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.terasumi.sellerkeyboard.benchmarkhost"
        private const val SELLER_PACKAGE = "com.terasumi.sellerkeyboard"
        private const val SELLER_TEST_BRIDGE =
            "content://com.terasumi.sellerkeyboard.ime-test-bridge"
        private const val SELLER_IME_COMPONENT =
            "com.terasumi.sellerkeyboard/.service.SellerKeyboard"
        private const val GBOARD_IME_COMPONENT =
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        private const val BENCHMARK_ACTIVITY =
            "com.terasumi.sellerkeyboard.benchmarkhost.ImeBenchmarkActivity"
        private const val IME_SHOW_TRACE_SECTION =
            "SellerKeyboard.ImeShowToInteractive"
        private const val KEY_DISPATCH_TRACE = "SellerKeyboard.keyDispatch"
        private const val CANDIDATE_QUERY_TRACE = "SellerKeyboard.candidateQuery"
        private const val CANDIDATE_DISPLAY_TRACE = "SellerKeyboard.candidateDisplay"
        private const val SURFACE_SWITCH_TRACE = "SellerKeyboard.surfaceSwitch"
        private const val TEMPLATE_INSERTION_TRACE = "SellerKeyboard.templateInsertion"
        private const val CYCLES_PER_ITERATION = 20
        private const val IME_VISIBILITY_TIMEOUT_MILLIS = 30_000L
        private const val UI_OBJECT_TIMEOUT_MILLIS = 10_000L
        private const val CANDIDATE_UI_TIMEOUT_MILLIS = 30_000L
        private const val CANDIDATE_POLL_MILLIS = 50L
        private const val TEMPLATE_INSERT_X_PERCENT = 45
        private const val TEMPLATE_INSERT_Y_PERCENT = 70
        private const val LOCALIZED_UI_OBJECT_TIMEOUT_MILLIS = 2_000L

        @BeforeClass
        @JvmStatic
        fun normalizeImeCompilation() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            listOf(
                "com.terasumi.sellerkeyboard",
                "com.google.android.inputmethod.latin"
            ).forEach { packageName ->
                val result = device.executeShellCommand(
                    "cmd package compile -f -m speed $packageName"
                ).trim()
                check(result == "Success") {
                    "Failed to normalize ART compilation for $packageName: $result"
                }
            }
            println("IME_COMPILATION seller=speed gboard=speed")
        }
    }
}
