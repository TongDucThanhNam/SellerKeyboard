# API 36 TalkBack external-keyboard control — 2026-07-11

Device: `sdk_gphone64_x86_64`, Android 16 / API 36, build
`BE2A.250530.026.F3`, headless SwiftShader AVD.

TalkBack was enabled with touch exploration active. Its first-run notification permission dialog
was dismissed before the comparison. The same benchmark editor, external `KEYCODE_TAB` sequence,
two-second settle interval, and `adb screencap` path were used for SellerKeyboard and the bundled
Gboard.

## Observation

In alternating captures, foreground glyphs disappear while key/background shapes remain. The
behavior reproduces on both IMEs:

- SellerKeyboard visible: `verification/emulator-connected/latest/talkback-keyed-0.png`
- SellerKeyboard after Tab: `verification/emulator-connected/latest/talkback-keyed-tab1.png`
- SellerKeyboard alternating capture: `verification/emulator-connected/latest/talkback-keyed-tab2.png`
- Gboard visible: `verification/emulator-connected/latest/talkback-gboard-0.png`
- Gboard after Tab: `verification/emulator-connected/latest/talkback-gboard-tab1.png`
- Gboard alternating capture: `verification/emulator-connected/latest/talkback-gboard-tab2.png`

Replacing SellerKeyboard's whole-keyboard custom draw invalidation with a session-keyed Compose
subtree did not alter the alternating capture. The Gboard control then reproduced the same effect.
Therefore the screenshot loss is classified as a headless emulator/TalkBack rendering or capture
artifact, not evidence of a SellerKeyboard-only defect.

## Claim boundary

This control removes the prior SellerKeyboard-specific failure attribution. It does not prove
TalkBack acceptance. Spoken labels, traversal order, activation, touch exploration, Switch Access,
and physical external-keyboard coexistence still require a visible emulator or physical-device
interaction audit.
