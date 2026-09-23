# Keyboard Behavior Specification

Status: foundation specification, 2026-07-11

This document defines semantic behavior independently from labels and composable layout. Numeric gesture values remain product estimates until measured on the reference device.

## Editor session

Each `onStartInput` creates a new monotonically identified session containing editor class/variation, action, multiline permission, sensitivity, caps mode, selection, package name, supported content MIME types, language, active surface, and candidate source. A service-owned runtime coordinator retains immutable Shift/key-surface state across input-view recreation and resets it at the editor-session boundary. Callbacks and asynchronous results carrying an older session identity are ignored.

`onFinishInput`, service destruction, or a new editor session must finish or cancel composition, stop repeats and gestures, clear inline/completion views, and discard bounded surrounding context.

## Shift state machine

States are `Lowercase`, `OneShotShift`, and `CapsLock`. Layout/surface selection is separate from Shift.

| Current state | Event | Next state | Effect |
|---|---|---|---|
| Lowercase | Shift tap | OneShotShift | Render uppercase letters |
| OneShotShift | Shift tap within system double-tap timeout | CapsLock | Render uppercase; expose locked state |
| OneShotShift | Shift tap after timeout | Lowercase | Render lowercase |
| CapsLock | Shift tap | Lowercase | Unlock |
| OneShotShift | Commit alphabetic code point | Auto caps evaluation | Commit uppercase and recompute cursor caps mode |
| OneShotShift | Space, delete, cursor move, layout switch, tool open | OneShotShift unless editor caps reconciliation supersedes it | Non-letter actions do not consume the state |
| Any | New/restarted editor | Editor-derived state | Seed from `initialCapsMode`, field policy, then cursor caps mode |
| Any | External selection/edit update | Reconciled state | Ignore stale callback; preserve explicit CapsLock unless session changes |

Email, URI, password, SKU/order/tracking-like contexts do not receive automatic capitalization. Manual Shift remains available where the layout permits it.

## Semantic actions

UI keys emit typed actions: insert text/code point, delete backward, delete word or selection, move cursor, set shift mode, perform editor action, switch surface/layout, accept candidate, undo correction, open seller tool, insert reviewed template, and commit rich content with fallback. Display labels are never used as the protocol.

All multi-step replacements use a balanced batch edit. Input-connection calls stay ordered and on the IME dispatch path; candidate, database, model, image, and network work do not run there.

## Field and action policy

| Editor signal | Key field | Candidate/seller policy | Enter/action behavior |
|---|---|---|---|
| Text, normal | Alphabet | Allowed unless private profile | Declared action; newline only if multiline/no-enter-action |
| Email | Email layout with `@` and period | No destructive correction | Declared action |
| URI | URI layout with slash and period | No destructive correction | Go or declared action |
| Password/visible password | Conservative alphabet or number password | No learning, context, clipboard, voice, translation, seller tools, rich content | Declared action only |
| Number | Integer or signed/decimal variant | No language candidates | Declared action |
| Phone | Phone layout | No language candidates | Declared action |
| Date/time | Date/time numeric variant | No language candidates | Declared action |
| Multiline | Alphabet | Allowed unless sensitive | Insert newline when no explicit action consumes Enter |
| `TYPE_NULL`/unusual | Conservative limited layout | No retained context | Key events/fallback only |

Actions `GO`, `SEARCH`, `SEND`, `NEXT`, `PREVIOUS`, and `DONE` call the matching `performEditorAction`. A seller tool only inserts a reviewable draft; it never invokes Send.

## Delete and gestures

Deletion order is: active selection, composing token/cluster, preceding grapheme cluster, preceding Unicode code point as documented fallback. Never split a surrogate pair. ZWJ sequences, variation selectors, regional-indicator flags, skin tones, and combining marks are corpus cases.

Backspace repeat starts after the platform long-press timeout [estimate pending same-device comparison], repeats at a bounded interval, and stops on up, cancel, session change, pointer-policy exit, input-view finish, or service destruction. Haptic/sound feedback is emitted once per accepted deletion, never for a canceled gesture.

Backspace swipe-left previews a progressively larger selection and commits only on release. Space drag moves the cursor without inserting a space after touch slop is crossed. Velocity never bypasses the distance threshold. Pointer exit or multi-pointer input cancels the gesture; if cursor preview already moved, cancellation restores the original cursor before ending. Thresholds must be density-aware and covered by deterministic gesture tests before device tuning.

## Candidate and correction behavior

Context-strip priority is sensitive suppression, inline autofill, editor completion, explicit seller workflow, text candidates, clipboard suggestion, then explicit-source seller-topic suggestion. Provider changes never alter keyboard height.

Protected tokens (email, URL, phone, money, SKU, order/tracking/discount IDs, product slugs, hashtags, mentions) are never destructively corrected. Immediate Backspace after autocorrection atomically restores the exact typed token. A cursor move, unrelated edit, session change, or accepted subsequent token closes that undo window.

## Template and rich-content behavior

A parameterized template is committed only after every required variable is resolved and the user confirms the review surface. The insert is atomic and opens an exact-suffix, session-scoped immediate-Backspace undo window; a changed suffix, cursor move, unrelated action, or session change closes it. Rich content is offered only when MIME negotiation succeeds; rejection or unsupported MIME falls back to reviewed product text and URL without losing data.

## Voice review

Voice is an explicit context-strip workflow. Before the Android microphone prompt, the companion activity explains microphone use, possible network processing by the installed recognition provider, and that recognition produces a reviewable draft rather than sending or inserting automatically. The fixed-height strip represents listening, processing, result, and error states. A final transcript remains in memory only for explicit Insert, Discard, Retry, or field/session reset. Completion, cancellation, and discard restore the surface that was active before voice opened.

When the provider reports on-device recognition, the strip says so; otherwise it warns that provider network use may occur. Voice remains absent in sensitive/private sessions.

## Translation privacy boundary

Translation accepts only explicit drafted, selected, or pasted text. On API 30+, a scoped native
log guard silences the process log sink only while ML Kit executes the translation task and restores
the previous threshold on every completion/cancel path. On API 26–29, translation is unavailable:
the platform has no supported process-minimum logging API, and the bundled provider was observed
emitting full source text. Model download and SDK operational-metrics disclosures remain visible;
the translated draft is previewed and never auto-inserted or sent.

Translation entry is local state inside the IME and does not attempt to open the IME recursively.
When the source came from an active selection, Insert collapses that selection at its end, preserves
the source, and appends the translated result on a new line. Replace substitutes the exact selected
source; Restore returns that exact original while the replacement session remains valid. Direction
changes clear stale previews, and no translation action invokes the target app's Send command.

## Stable surfaces

The context strip has a fixed 52 dp height and keeps its interactive contents at least 48 dp tall. The key field switches among alphabet, symbols, numeric, phone, emoji, clipboard, and seller layouts within the configured IME height. Utility/one-handed affordances may narrow the field only while primary targets remain at least 48 dp where available. Compact exceptions require measured device evidence and product approval.

The standard preset uses 48 dp rows in portrait and landscape; tall uses 56/52 dp. The explicit compact preset is the documented exception at 44 dp portrait and 40 dp landscape and remains release-conditional until same-device accessibility acceptance. Portrait honors the number-row preference; landscape removes the number row to protect editor visibility. Extract/fullscreen IME mode is always disabled so landscape remains an in-place editor experience.

Language switching supports both platform and in-keyboard paths: Android exposes Vietnamese (`vi-VN`) and English (`en-US`) keyboard subtypes, while long-press Space toggles VI/EN only when the active profile is bilingual. A profile fixed to Vietnamese or English suppresses the in-keyboard toggle.
