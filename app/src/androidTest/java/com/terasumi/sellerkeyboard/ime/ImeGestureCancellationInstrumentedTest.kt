package com.terasumi.sellerkeyboard.ime

import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImeGestureCancellationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private var previousIme = ""
    private var previousShowImeWithHardKeyboard = ""
    private var activeDownTime = 0L
    private var activePoint: PointF? = null

    @Before
    fun openNeutralHostWithSellerKeyboard() {
        previousIme = shell("settings get secure default_input_method").trim()
        previousShowImeWithHardKeyboard = shell(
            "settings get secure show_ime_with_hard_keyboard"
        ).trim()
        shell("settings put secure show_ime_with_hard_keyboard 1")
        if (Build.VERSION.SDK_INT >= 30) shell("ime reset")
        shell("ime enable $SELLER_IME")
        shell("ime set $SELLER_IME")
        shell("am start -W --activity-clear-top --activity-single-top -n $HOST_ACTIVITY")
        device.findObject(By.text("Wait"))?.click()
        assertTrue(
            "Neutral host did not become active",
            waitUntil(HOST_TIMEOUT_MILLIS) { hostSnapshot().active }
        )
    }

    @After
    fun restoreDeviceState() {
        activePoint?.let { point ->
            injectSinglePointer(
                MotionEvent.ACTION_CANCEL,
                activeDownTime,
                point,
                requireAccepted = false
            )
        }
        activePoint = null
        if (previousIme.isNotBlank() && previousIme != "null") {
            shell("ime set $previousIme")
        }
        if (previousShowImeWithHardKeyboard.matches(Regex("\\d+"))) {
            shell(
                "settings put secure show_ime_with_hard_keyboard " +
                    previousShowImeWithHardKeyboard
            )
        } else {
            shell("settings delete secure show_ime_with_hard_keyboard")
        }
        shell("am force-stop $HOST_PACKAGE")
    }

    @Test
    fun multiPointerAndInputViewTeardown_cancelActiveGestures() {
        verifySpaceMultiPointerCancellation()
        verifyDeleteMultiPointerCancellation()
        verifyDeleteRepeatStopsWhenInputViewTearsDown()
    }

    private fun verifySpaceMultiPointerCancellation() {
        seedFixture("cursor_middle")
        val before = hostSnapshot()
        assertEquals(3, before.selectionStart)
        assertEquals(3, before.selectionEnd)
        val bounds = awaitDescriptionBounds(Pattern.compile("^Space(?:\\.|$).*"), "Space")
        val start = PointF(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        val moved = PointF(
            (bounds.centerX() + 120).coerceAtMost(bounds.right - 12).toFloat(),
            bounds.centerY().toFloat()
        )
        beginPointer(start)
        movePointer(moved)
        assertTrue(
            "Space drag did not move the cursor before second-pointer cancellation",
            waitUntil(GESTURE_TIMEOUT_MILLIS) { hostSnapshot().selectionEnd > 3 }
        )
        addSecondPointerAndRelease(
            first = moved,
            second = PointF(moved.x, (moved.y - 36f).coerceAtLeast(bounds.top + 4f))
        )
        assertTrue(
            "Space multi-pointer cancellation did not restore the exact cursor",
            waitUntil(GESTURE_TIMEOUT_MILLIS) {
                hostSnapshot().let { snapshot ->
                    snapshot.textSha256 == before.textSha256 &&
                        snapshot.selectionStart == 3 && snapshot.selectionEnd == 3
                }
            }
        )
    }

    private fun verifyDeleteMultiPointerCancellation() {
        seedFixture("cursor_end")
        val before = hostSnapshot()
        assertEquals(6, before.textLength)
        assertEquals(6, before.selectionEnd)
        val bounds = awaitDescriptionBounds(Pattern.compile("^Delete$"), "Delete")
        // Start well inside the key. MIUI expands accessibility bounds near the
        // right screen edge, where an injected DOWN can otherwise land outside
        // the Compose pointer target even though it is inside the semantics node.
        val start = PointF(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        val moved = PointF((bounds.left + 4).toFloat(), bounds.centerY().toFloat())
        beginPointer(start)
        movePointerThrough(start, moved, steps = 2)
        assertTrue(
            "Delete swipe did not preview a selection before second-pointer cancellation",
            waitUntil(GESTURE_TIMEOUT_MILLIS) {
                hostSnapshot().let { it.selectionStart < 6 && it.selectionEnd == 6 }
            }
        )
        addSecondPointerAndRelease(
            first = moved,
            second = PointF(
                (moved.x + 36f).coerceAtMost(bounds.right - 4f),
                (moved.y - 24f).coerceAtLeast(bounds.top + 4f)
            )
        )
        assertTrue(
            "Delete multi-pointer cancellation changed text or failed to restore the cursor",
            waitUntil(GESTURE_TIMEOUT_MILLIS) {
                hostSnapshot().let { snapshot ->
                    snapshot.textSha256 == before.textSha256 &&
                        snapshot.textLength == 6 &&
                        snapshot.selectionStart == 6 && snapshot.selectionEnd == 6
                }
            }
        )
    }

    private fun verifyDeleteRepeatStopsWhenInputViewTearsDown() {
        val alternativeIme = shell("ime list -s")
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotBlank() && it != SELLER_IME }
            ?: error("A second installed IME is required for teardown cancellation evidence")
        seedFixture("delete_repeat")
        val before = hostSnapshot()
        assertEquals(20, before.textLength)
        val bounds = awaitDescriptionBounds(Pattern.compile("^Delete$"), "Delete")
        val point = PointF(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        beginPointer(point)
        assertTrue(
            "Hold-delete did not begin repeating before teardown",
            waitUntil(HOLD_REPEAT_TIMEOUT_MILLIS) { hostSnapshot().textLength < 20 }
        )
        shell("ime set $alternativeIme")
        SystemClock.sleep(TEARDOWN_SETTLE_MILLIS)
        val afterTeardown = hostSnapshot()
        assertNotEquals(20, afterTeardown.textLength)
        SystemClock.sleep(STOP_ASSERTION_MILLIS)
        val afterWait = hostSnapshot()
        assertEquals(afterTeardown.textLength, afterWait.textLength)
        assertEquals(afterTeardown.textSha256, afterWait.textSha256)
        activePoint?.let {
            injectSinglePointer(
                MotionEvent.ACTION_CANCEL,
                activeDownTime,
                it,
                requireAccepted = false
            )
        }
        activePoint = null
        shell("ime set $SELLER_IME")
    }

    private fun seedFixture(name: String) {
        val result = shell(
            "content call --uri $HOST_BRIDGE --method seed_core_fixture --arg $name"
        )
        assertEquals("true", bundleValue(result, "seeded"))
        assertEquals(name, bundleValue(result, "fixture"))
        assertTrue("Delete key did not become visible after seeding $name", waitUntil(
            HOST_TIMEOUT_MILLIS
        ) { device.hasObject(By.desc("Delete")) })
    }

    private fun hostSnapshot(): HostSnapshot {
        val result = shell("content call --uri $HOST_BRIDGE --method snapshot")
        return HostSnapshot(
            active = bundleValue(result, "active") == "true",
            textSha256 = bundleValue(result, "text_sha256").orEmpty(),
            textLength = bundleValue(result, "text_length")?.toIntOrNull() ?: -1,
            selectionStart = bundleValue(result, "selection_start")?.toIntOrNull() ?: -1,
            selectionEnd = bundleValue(result, "selection_end")?.toIntOrNull() ?: -1
        )
    }

    private fun awaitDescriptionBounds(description: Pattern, name: String): Rect {
        val deadline = SystemClock.elapsedRealtime() + HOST_TIMEOUT_MILLIS
        do {
            val bounds = runCatching {
                val output = ByteArrayOutputStream()
                device.dumpWindowHierarchy(output)
                NODE_PATTERN.findAll(output.toString(Charsets.UTF_8.name()))
                    .firstOrNull { description.matcher(it.groupValues[1]).matches() }
                    ?.let { match ->
                        Rect(
                            match.groupValues[2].toInt(),
                            match.groupValues[3].toInt(),
                            match.groupValues[4].toInt(),
                            match.groupValues[5].toInt()
                        )
                    }
            }.getOrNull()
            if (bounds != null && !bounds.isEmpty) return bounds
            SystemClock.sleep(POLL_MILLIS)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Timed out waiting for stable $name bounds in the installed IME window")
    }

    private fun beginPointer(point: PointF) {
        activeDownTime = SystemClock.uptimeMillis()
        activePoint = point
        injectSinglePointer(MotionEvent.ACTION_DOWN, activeDownTime, point)
        SystemClock.sleep(POINTER_EVENT_DELAY_MILLIS)
    }

    private fun movePointer(point: PointF) {
        activePoint = point
        injectSinglePointer(MotionEvent.ACTION_MOVE, activeDownTime, point)
        SystemClock.sleep(POINTER_EVENT_DELAY_MILLIS)
    }

    private fun movePointerThrough(start: PointF, end: PointF, steps: Int = 4) {
        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            movePointer(
                PointF(
                    start.x + (end.x - start.x) * fraction,
                    start.y + (end.y - start.y) * fraction
                )
            )
        }
    }

    private fun addSecondPointerAndRelease(first: PointF, second: PointF) {
        injectPointers(
            action = MotionEvent.ACTION_POINTER_DOWN or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            downTime = activeDownTime,
            points = listOf(first, second)
        )
        SystemClock.sleep(POINTER_EVENT_DELAY_MILLIS)
        injectPointers(
            action = MotionEvent.ACTION_POINTER_UP or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            downTime = activeDownTime,
            points = listOf(first, second)
        )
        injectSinglePointer(MotionEvent.ACTION_UP, activeDownTime, first)
        activePoint = null
        SystemClock.sleep(POINTER_EVENT_DELAY_MILLIS)
    }

    private fun injectSinglePointer(
        action: Int,
        downTime: Long,
        point: PointF,
        requireAccepted: Boolean = true
    ) {
        injectPointers(action, downTime, listOf(point), requireAccepted)
    }

    private fun injectPointers(
        action: Int,
        downTime: Long,
        points: List<PointF>,
        requireAccepted: Boolean = true
    ) {
        val properties = Array(points.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(points.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = points[index].x
                y = points[index].y
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            points.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        try {
            val accepted = instrumentation.uiAutomation.injectInputEvent(event, true)
            if (requireAccepted) {
                assertTrue(
                    "UiAutomation rejected pointer action ${event.actionMasked}",
                    accepted
                )
            }
        } finally {
            event.recycle()
        }
    }

    private fun waitUntil(timeoutMillis: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        do {
            if (runCatching(predicate).getOrDefault(false)) return true
            SystemClock.sleep(POLL_MILLIS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private fun bundleValue(bundle: String, key: String): String? {
        return Regex("(?:\\{|,\\s*)${Regex.escape(key)}=([^,}]*)")
            .find(bundle)
            ?.groupValues
            ?.get(1)
            ?.trim()
    }

    private data class HostSnapshot(
        val active: Boolean,
        val textSha256: String,
        val textLength: Int,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    private companion object {
        const val SELLER_IME = "com.terasumi.sellerkeyboard/.service.SellerKeyboard"
        const val HOST_PACKAGE = "com.terasumi.sellerkeyboard.benchmarkhost"
        const val HOST_ACTIVITY = "$HOST_PACKAGE/.ImeBenchmarkActivity"
        const val HOST_BRIDGE = "content://$HOST_PACKAGE.host-test-bridge"
        const val HOST_TIMEOUT_MILLIS = 15_000L
        const val GESTURE_TIMEOUT_MILLIS = 5_000L
        const val HOLD_REPEAT_TIMEOUT_MILLIS = 3_000L
        const val TEARDOWN_SETTLE_MILLIS = 300L
        const val STOP_ASSERTION_MILLIS = 600L
        const val POINTER_EVENT_DELAY_MILLIS = 80L
        const val POLL_MILLIS = 50L
        val NODE_PATTERN = Regex(
            "<node[^>]*content-desc=\"([^\"]*)\"[^>]*" +
                "bounds=\"\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]\""
        )
    }
}
