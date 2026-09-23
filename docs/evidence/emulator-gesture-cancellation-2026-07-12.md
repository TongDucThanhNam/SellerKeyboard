# API 36 gesture cancellation evidence

Date: 2026-07-12

Device: `SellerKeyboardApi36Privacy`, Android API 36 x86_64 emulator (`emulator-5554`).

The connected test `ImeGestureCancellationInstrumentedTest` drives the installed SellerKeyboard
against the distinct-package neutral benchmark editor. It injects platform `MotionEvent` sequences,
not Compose test callbacks.

Verified behaviors:

- Space drag moves the cursor, a second pointer cancels the gesture, and the exact original cursor
  and text hash are restored.
- Swipe-delete previews a selection, a second pointer cancels the gesture, and the exact original
  cursor, length, and text hash are restored.
- Hold-delete begins repeating; switching to another installed IME tears down SellerKeyboard's input
  view; text length and hash remain unchanged for the subsequent 600 ms, proving repeat stopped.

Focused command result: `OK (1 test)` in 24.494 seconds.

The complete AndroidJUnitRunner result immediately afterward was `OK (19 tests)` in 53.932 seconds,
with the same two explicit credential-gated assumptions and no failures.
