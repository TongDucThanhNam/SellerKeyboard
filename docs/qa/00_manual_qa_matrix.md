# Competitive Release Manual QA Matrix

Status: partial API 33 physical-device and API 36 emulator execution complete; Chrome URL/search,
Google Messages search and an unsent SMS compose draft, and TalkBack/Switch Access traversal and
actions now have direct API 36 evidence; remaining physical devices/peripherals and
product-confirmed apps are pending.

This matrix is release evidence for `GOAL.md`, not a generic smoke checklist. Copy
`docs/evidence/device-evidence-template.md` for every device/build/configuration run and link all
screenshots, videos, traces, network captures, and logs. A blank cell is unverified, not a pass.

## Required configurations

Cover API 26, 30, 31, 33, 34, 35, and 36 where tooling/device availability permits. The final
set must include a lower-midrange physical phone and a current mainstream physical phone.

| Dimension | Required coverage | Result/artifact |
|---|---|---|
| API level | 26, 30, 31, 33, 34, 35, 36 | Core connected suite passes on API 26/30/31/34/35/36 emulators and API 33 physical; `docs/evidence/emulator-connected-matrix-2026-07-11.md` |
| Form factor | Compact phone, mainstream phone, tall phone, tablet/foldable width | API 36 Small Phone, Medium Phone, Pixel 6 Pro and Medium Tablet each pass 18 connected results; cold-booted Medium Tablet native portrait/landscape and a live 600 dp foldable-width resize have direct screenshots/hierarchies, 48 dp controls, exact touch typing, and post-resize IME recovery; a separate cold-booted Pixel Fold AVD declares a hinge sensor and passes Android `OPENED` 180°, `HALF_OPENED` 90°, `CLOSED` 0° outer-display, and reopened transitions without host relaunch, preserving 48 dp semantics and exact visible-key text | Physical foldable acceptance pending |
| Orientation | Portrait and landscape | API 33 pass: `docs/evidence/device-2026-07-11-api33-matrix.md` |
| Navigation | Gesture and three-button | API 33 physical gesture capture; API 36 emulator connected suite passes in gestural and three-button modes; physical three-button acceptance pending |
| Display | 60 Hz and high refresh where available | API 33 pass at actual 60/90 Hz; remaining devices pending |
| Theme | Light, dark, system, and high contrast | API 33 light/dark captured; system/high-contrast pending |
| Accessibility | 1.0x/1.3x/2.0x font, TalkBack, Switch Access, touch exploration, reduced animation | API 26/36 semantics pass; API 33 font/reduced-animation captured; reversible API 36 evidence independently binds TalkBack with touch exploration and Switch Access while SellerKeyboard is the visible IME for a distinct-UID editor with 46 clickable semantic controls; a windowed API 36 run uses a genuine host-generated TalkBack swipe/double-tap to focus and activate SellerKeyboard key `1`; a configured two-switch run uses Android `uinput` USB input classified `KEYBOARD \| EXTERNAL` to scan the toolbar/numeric row and commit exact `1`; real physical external-keyboard/peripheral acceptance remains pending |
| Lifecycle | Rotation, low-memory process recreation, input-view recreation, 100 editor/show-hide cycles | API 33 physical 100-cycle/hot-transition pass; API 26/36 connected 100-cycle + activity recreation + landscape/portrait reconnect pass; API 36 `TRIM_MEMORY_COMPLETE` + documented background-process kill records PID disappearance/new PID, clean host recreation, IME reattachment, exact visible-key recovery text, screenshots/hierarchies, and zero fatal logs; physical comparison video pending |
| Language | Vietnamese, English, bilingual mode; platform subtype and long-press Space paths |  |

Record any unavailable configuration as a named release exception request. Do not silently drop
it from the matrix.

## Reference and app configuration

For every comparison, record SellerKeyboard SHA/version, Gboard version, Android build fingerprint,
device model, locale, keyboard languages, number row, suggestions, autocorrection, clipboard,
height, theme, sound, haptics, target app version, and editor type.

Required app categories:

- AOSP/reference editor or the benchmark activity;
- Chrome WebView/contenteditable, URL, and search fields (API 36 Chrome URL/search exact draft passes; WebView remains covered only by the local Chromium bridge);
- Gmail or equivalent email recipient/subject/body fields;
- confirmed Vietnamese seller messaging apps;
- confirmed commerce seller apps;
- a password manager supplying inline autofill;
- one editor accepting `image/jpeg` content and one rejecting it;
- an editor with incomplete or unusual `InputConnection` behavior.

Named messaging/commerce apps and accounts remain product-confirmation items. Google Messages
search plus a real unsent SMS compose draft to Android's fictional `555-0100` are supplemental
account-free evidence, not acceptance of a product-confirmed seller conversation.
Suggested starting set is Messenger, Zalo, Shopee Seller, Chrome, and Gmail; only Chrome URL/search
from that suggestion currently has direct named-app release evidence.

## Editor field matrix

Run each row on every applicable API/app. Verify visible layout, action label, committed behavior,
candidate/seller availability, rotation, and restart behavior.

| Editor | Expected key field/action | Privacy and feature expectation | Result/artifact |
|---|---|---|---|
| Plain text | Alphabet; declared action | Candidates, Telex, seller tools allowed |  |
| Multiline chat | Alphabet; newline unless explicit action | Review drafts only; no automatic Send | API 26/36 benchmark contract hot-switch pass; API 36 Google Messages real SMS composer accepts exact sentence-cased `Skqvz` through five SellerKeyboard draft-key taps after seven recipient-key taps to fictional `555-0100`; the explicit Send action and Send SMS control are present but never invoked; product-confirmed seller-app behavior remains pending |
| Search | Alphabet; Search action | `performEditorAction(SEARCH)` | API 26/36 benchmark contract hot-switch pass; API 36 Chrome URL/search and Google Messages search accept exact `skqvz` drafts through five SellerKeyboard key taps; neither query is submitted |
| Email address | `@` and period; Next/Done | No destructive correction | API 33 capture; API 26/36 benchmark contract hot-switch pass |
| URL | slash and period; Go | No destructive correction | API 33 capture including hot email → URI transition; API 26/36 benchmark contract pass; Chrome `133.0.6943.137` URL/search exact account-free draft pass |
| Password/visible password | Conservative text layout | No context, learning, clipboard, voice, translation, seller, or rich content | API 33 password capture; API 26/36 password contract pass; API 36 password node/raw-text masking and zero UID network/log leakage pass; prohibited-surface observation pending |
| OTP/authentication | Conservative field layout | Same fail-closed suppression as sensitive mode | API 26/36 authentication contract pass; API 36 verified numeric/private-option editor plus zero UID network/log leakage; prohibited-surface observation pending |
| Phone | Phone layout | No language candidates | API 33 capture; API 26/36 benchmark contract pass |
| Integer | Numeric layout | No language candidates | API 26/36 benchmark contract pass; target-app behavior pending |
| Decimal/signed | Decimal/sign keys as declared | No language candidates | API 33 capture including hot phone transition; API 26/36 benchmark contract pass |
| Date/time | Date/time numeric variant | No language candidates | API 26/36 benchmark contract pass; target-app behavior pending |
| WebView/contenteditable | Field-appropriate fallback | No crash or stale session result | API 26/36 local Chromium bridge creates text connection and commits exact DOM text; named browser/app pending |
| `TYPE_NULL`/incomplete connection | Conservative fallback | Key-event/fallback path; no retained context | API 26/36 `TYPE_NULL` contract plus Android incomplete-connection DEL fallback pass |

## Typing Trust Gate

### Layout, lifecycle, and actions

- [ ] Standard/tall rows meet 48 dp targets; compact exception is measured and approved.
- [ ] Number row follows the documented portrait/landscape policy.
- [ ] One-handed left/right anchoring does not clip keys or editor content.
- [ ] Gesture navigation, three-button navigation, cutouts, and insets do not obscure controls.
- [ ] Landscape never enters disruptive fullscreen extract mode.
- [ ] Done, Go, Next, Previous, Search, Send, and multiline Enter produce the declared action.
- [x] API 26/36 connected rotation/input-view recreation retains an attached, focused session; physical visual acceptance remains in the device matrix.

### Shift, delete, and gestures

API 36 automation now covers real installed-IME initial/manual one-shot Shift, Caps Lock
double-tap/persistence/exit, punctuation auto-cap, space cursor movement and pointer-exit restore,
swipe-delete commit and pointer-exit restore, hold-delete repeat, and stop-on-release against a
distinct-package editor. A second API 36 test injects real two-pointer sequences and proves exact
cursor/text restoration for Space and Delete, then tears down the input view while Delete is held
and proves repeat stops. The unchecked items below remain the physical/manual acceptance matrix.

- [ ] Single Shift is one-shot; platform-timeout double tap enters Caps Lock; next tap exits.
- [ ] Cursor caps-mode updates reconcile auto-capitalization without cancelling explicit Caps Lock.
- [ ] Rapid Shift taps do not skip or duplicate transitions.
- [ ] Backspace deletes selection/composition/grapheme/code point without splitting emoji or Vietnamese text.
- [ ] Hold-delete repeats and stops on up, cancel, pointer exit, field change, and service teardown.
- [ ] Swipe-left delete previews selection and commits only on release; cancel restores content.
- [ ] Space drag activates only after distance slop; cancel/pointer exit restores the original cursor.
- [x] Multi-touch cancels delete and cursor gestures on API 36; physical acceptance remains pending.
- [ ] Long-press alternatives expose correct VI/EN characters and remain reachable with TalkBack.

Unicode corpus samples must include surrogate pairs, combining marks, Vietnamese diacritics, ZWJ
emoji, flags, skin tones, variation selectors, and mixed-direction text.

### Candidates and dictionary

Automated API 36 evidence now proves that a normal session can populate candidate/completion/inline
state and that a hot switch to password mode clears it and rejects stale late results. The checks
below remain manual target-app/physical acceptance.

- [ ] VI/EN/bilingual candidates remain responsive and match the offline corpus behavior.
- [ ] Protected email, URL, phone, price, SKU, order, tracking, discount, hashtag, and mention tokens are unchanged.
- [ ] Immediate Backspace after correction restores the exact original; cursor/unrelated edit closes undo.
- [ ] Add/remove/clear personal dictionary persists across service recreation and stays local.
- [ ] Sensitive/private fields show no candidates or learning.

### Clipboard and emoji

Automated API 36 evidence now covers the real system clipboard listener: normal capture,
history-disabled non-persistence, system-marked-sensitive rejection, listener removal on password
transition, and final repository clear. Pin/use/delete/expiry UI and physical OEM behavior remain
manual.

- [ ] Clipboard capture is optional; marked-sensitive clips are rejected.
- [ ] Twenty-item unpinned bound, 30-day expiry, pin/unpin/use/delete/clear work across recreation.
- [ ] Phone, URL, address, price, order, tracking, date/time, and discount fragments are correct.
- [ ] Clipboard UI and retained context disappear in sensitive/private fields.
- [ ] Emoji categories/search/recents/variants commit full sequences; recents clear locally.
- [ ] Unsupported editor/font rendering degrades without corrupting text.

### Voice and translation

- [ ] Pre-permission voice disclosure appears before the Android microphone prompt.
- [ ] Denied permission, missing provider, listening, processing, final result, cancellation, and error are visible and interruptible.
- [ ] Final voice transcript is not inserted automatically; Insert commits it, Discard does not.
- [ ] Provider/on-device network status is visible and the previous keyboard surface is restored.
- [ ] Voice is unavailable in sensitive/private fields and leaves no transcript after field change.
- [ ] Translation accepts only explicit draft/selection/paste and shows model download/progress/cancel/retry/delete.
- [ ] VI↔EN original and translated text are reviewed before insert/replace.
- [ ] Restore succeeds only while the exact translated suffix remains; stale replacement is refused safely.
- [ ] Translation is unavailable in sensitive/private fields.

### Autofill, completion, and rich content

Automated API 36 evidence covers a real IME connection to a neutral app under a distinct UID in
three modes: advertised JPEG accepted after reading the granted URI, advertised JPEG rejected with
full text/link fallback, and unsupported MIME with the same fallback. Named third-party app
acceptance remains manual.

- [ ] Android 11+ inline suggestions render without exposing hidden values before selection.
- [ ] Empty/provider change/field change clears stale inline suggestions.
- [ ] Editor `CompletionInfo` has the documented context-strip priority and correct commit fallback.
- [ ] Accepting `image/jpeg` editor receives the selected product image and fallback metadata.
- [ ] Rejecting/unsupported editor offers and commits reviewed product text/link fallback only.
- [ ] Field/session change prevents late image loads from inserting into the wrong editor.

### Settings and accessibility

The scoped API 36 artifacts under `docs/evidence/verification/emulator-accessibility/` prove service
binding, visible IME coexistence, active-editor ownership, exposed semantics, real host-generated
TalkBack traversal/action, and configured two-switch scanning/action from an emulated USB device
that Android classifies as external. The AVD keyboard itself remains internal; the evidence does not
claim a real physical external keyboard or assistive-switch peripheral.

- [ ] System/light/dark/high-contrast, borders, popup preview, number row, height, and one-handed settings persist.
- [ ] Sound/haptic enablement and strength/volume work and are never the only state signal.
- [ ] Every key/control exposes a role, label, state, and logical traversal order.
- [ ] Shift/Caps Lock, active profile, selected candidate, pinned clip, and unresolved variable speak state.
- [ ] TalkBack typing, touch exploration, Switch Access, and external keyboard coexistence work.
- [ ] 2.0x font scaling does not clip settings, tool sheets, or template review; scrolling remains possible.
- [ ] Reduced animation preserves usable state transitions.

## Seller Advantage Gate

- [ ] Toolbar reorder/hide/reset persists globally and per profile without automatic reordering.
- [ ] Search reaches Replies, Products, Clipboard, Calculator, Translate, Voice, Emoji, and Settings.
- [ ] Reply search covers normalized title/body/category/tag/shortcut; all four sort modes and ranking reset work.
- [ ] Create/edit/duplicate/archive/delete/import/export flows preserve stable IDs and require destructive confirmation.
- [ ] Slash shortcuts appear only after explicit slash typing and insert only on user action.
- [ ] Parameterized replies highlight unresolved fields and cannot insert until every required value resolves.
- [ ] Reviewed template insert is atomic; immediate exact-suffix Backspace undoes it safely.
- [ ] Product reply review includes price/link/promotion/stale warning and MIME-safe fallback.
- [ ] Seller margin/markup/discount/fee/VAT/shipping/profit results match the formula specification.
- [ ] Exact active package selects the expected profile without package inventory permission.
- [ ] Active profile name, language, number row, theme/height, ordered tools, and preferred pack update on app switch.
- [ ] No seller action reads incoming conversation content or invokes Send.

Execute all 40 scenarios in `docs/seller-workflow-benchmark.md` with Gboard, SellerKeyboard default,
and SellerKeyboard customized on the same device. Capture taps, keystrokes, gestures, time to a
reviewable draft, corrections, wrong/unresolved insertion, offline completion, and video timestamp.

## Privacy, performance, migration, and stability

- [ ] Network capture shows zero normal-typing/candidate/clipboard/seller traffic. API 33 physical normal typing and the API 36 synthetic matrix pass; a fresh account-free Chrome/Google Messages run also records exact SellerKeyboard-UID `0/0` RX/TX across URL/search, Messages search, recipient, and unsent SMS-draft typing (`docs/evidence/verification/emulator-named-apps/full-privacy-2026-07-12/summary.json`). Production-host and independent capture remain pending.
- [ ] Voice, translation model download, remote image, and optional sync traffic is explicit and attributable.
- [ ] Logcat contains no typed, selected, surrounding, template, clipboard, transcript, identifier, or URL text. Current exact-UID named-app evidence finds zero occurrences of `skqvz`, sentence-cased `Skqvz`, and fictional recipient `5550100`; deterministic spoken-transcript and independent capture remain pending.
- [ ] Sensitive-mode matrix proves no prohibited surface, retention, or late async result.
- [ ] Upgrade a representative backup and verify snippets, images, clipboard pins, profiles, dictionary, and settings.
- [ ] Run connected tests with zero unapproved skips.
- [ ] Run 100 show/hide/editor-switch cycles across text, multiline, email, decimal, and password fields.
- [ ] Macrobenchmark records frame timing, max heap/RSS, Perfetto traces, frozen frames, and ANRs.
- [ ] SellerKeyboard show-to-interactive is no worse than 1.25× same-device/config Gboard.
- [ ] Heap has no unbounded growth and no stale insertion/crashed composition occurs.

## Result rules

- `Pass`: direct artifact proves the row on the recorded build/configuration.
- `Conditional`: implementation exists but required coverage or an approved exception is absent; not a release pass.
- `Fail`: observed contradiction, crash, missed target, privacy violation, or mandatory skipped gate.
- `Not available`: requires an explicit exception request and does not become a pass automatically.

The release passes only when every GOAL success criterion has direct evidence and the verifier exits
successfully. A smoke test on one emulator cannot substitute for the required physical-device,
same-device Gboard, accessibility, privacy, and app matrices.
