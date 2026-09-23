# API 36 installed-IME core interactions — 2026-07-11

The canonical API 36 runner drives the installed SellerKeyboard window with real shell touch input
while a neutral, distinct-package editor owns the `InputConnection`. The editor bridge accepts only
shell/same-UID calls and exposes hashes, lengths, and cursor positions rather than plaintext.

The connected flow proves:

- an empty sentence editor starts with one-shot Shift, commits one uppercase letter, then clears;
- a manual Shift repeats the same one-shot behavior;
- two taps inside Android's platform double-tap timeout enter Caps Lock, multiple letters preserve
  it, the next Shift exits it, and the following letter is lowercase;
- restarting after `done. ` re-arms sentence auto-cap and commits the next uppercase letter;
- a distance-gated Space drag moves the cursor without changing the text hash;
- leaving the Space bounds after movement restores the exact original cursor;
- a left Delete swipe previews and commits deletion;
- leaving Delete bounds after a swipe restores the original cursor without deleting;
- holding Delete repeats, and editor length/hash remain stable after release.

The run found two defects that isolated unit tests did not expose. First, an editor can continue
reporting sentence caps while the first Telex word is a composing span, which re-armed one-shot
Shift after every letter. Shift reconciliation now treats active composition as already inside a
word while preserving explicit Caps Lock. Second, `selectionEnd` was a Compose `pointerInput` key;
cursor or swipe-preview updates therefore restarted the gesture coroutine and lost its origin.
Space and Delete now use stable pointer effects, fresh callbacks, and a per-gesture cursor snapshot.

The same verifier passes against the optimized, non-debuggable benchmark APK and is part of both
the zero-touch API 36 runner and the five-approval Xiaomi attended batch. Canonical artifacts are
under `verification/emulator-connected/latest/core-interactions/`. This is strong connected behavior
evidence, but it does not replace physical multi-touch, service-teardown, TalkBack touch-exploration,
or comparison-video acceptance.
