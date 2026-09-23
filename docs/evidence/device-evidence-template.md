# Device Evidence Record

Copy this file per device/run. Do not overwrite evidence from a different build or configuration.

## Configuration

- Date/time:
- SellerKeyboard commit/build SHA:
- SellerKeyboard version:
- Device manufacturer/model:
- Android/API/build fingerprint:
- RAM/refresh rate/display size/density:
- Navigation mode: gesture / three-button
- Orientation/window mode:
- System locale/font scale/dark mode/reduced animation:
- Gboard version:
- Gboard languages and settings: number row, multilingual, suggestions, autocorrection, clipboard, height, theme, sound, haptics
- SellerKeyboard profile/settings:
- Target app/version/editor type:
- Test account/credential exception ID, if any:

## Artifact index

| Gate | Result | Artifact path | Notes |
|---|---|---|---|
| Layout screenshots/video |  |  |  |
| Shift/caps/auto-cap |  |  |  |
| Unicode/delete/gestures |  |  |  |
| Candidate/correction/dictionary |  |  |  |
| Clipboard/emoji/voice/translation |  |  |  |
| Inline autofill/completions |  |  |  |
| Rich-content accept/reject fallback |  |  |  |
| Seller reply/product/profile flows |  |  |  |
| TalkBack/Switch Access/font/contrast |  |  |  |
| Network and text-free log capture |  |  |  |
| Macrobenchmark/Perfetto/frame/memory |  |  |  |
| 100 show/hide/editor-switch cycles |  |  |  |

## Seller scenario measurements

Record each of the 40 scenarios for Gboard, SellerKeyboard default, and SellerKeyboard customized. Count only actions after both keyboards have the same factual data available. Retain screen recording and note every correction or failed insertion.

| Scenario | Keyboard/config | Taps | Keystrokes | Gestures | Time to reviewable draft (ms) | Corrections | Wrong/unresolved | Offline | Video timestamp |
|---|---|---:|---:|---:|---:|---:|---:|---|---|
|  |  |  |  |  |  |  |  |  |  |

## Performance result

- SellerKeyboard IME show-to-interactive median/p95:
- Gboard same-editor/config median/p95:
- Ratio:
- Frozen frames above 700 ms:
- ANR events:
- Key dispatch/candidate/surface/template trace findings:
- Heap before/after 100 cycles:
- Baseline profile present/effect:

## Sign-off

- Tester:
- Product exception IDs:
- Release-blocking failures:
- Final result: pass / conditional / fail
