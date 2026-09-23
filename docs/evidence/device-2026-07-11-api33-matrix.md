# API 33 device configuration matrix — 2026-07-11

Device: Xiaomi Redmi 2201117TG (`spes`), Android 13 / API 33, serial `af339028`.

The reversible capture script `scripts/capture-api33-device-matrix.ps1` completed successfully and restored Gboard, 60 Hz, font scale 1.0, portrait rotation, dark mode, normal animations, and disabled accessibility services.

## Covered configurations

- Portrait and landscape; light and dark theme.
- Verified physical display modes at 60 Hz (`mActiveModeId=2`) and 90 Hz (`mActiveModeId=1`).
- Font scales 1.0, 1.3, and 2.0.
- Normal and disabled system animations.
- TalkBack enabled, including the visible accessibility focus/activation overlay.
- Text, email, URI, phone, signed-decimal, and password editor contracts.
- Hot editor transitions with equal row counts (email → URI and phone → signed-decimal).

The matrix found two defects that were fixed and recaptured: clipping at font scale 2.0, and unchanged key labels not redrawing on this OEM after `restartInput`. The final captures show complete layouts, including URI and signed-decimal hot transitions.

Artifacts are in `docs/evidence/device-2026-07-11-api33-matrix/`. Each named variant has a PNG plus device/display/IME state in TXT; UIAutomator XML describes the host activity but does not expose the IME virtual semantics tree.

## Remaining limitations

This is one physical device/API and gesture navigation only. It is not evidence for the required second device, API 26/30/35 coverage, three-button navigation, Switch Access, external keyboard behavior, or full TalkBack traversal and action announcements.
