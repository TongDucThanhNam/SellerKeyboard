# Privacy Boundaries

Status: release policy foundation, 2026-07-11

SellerKeyboard is a privileged input method. Ordinary typing must remain local, and every feature provider must consult one central editor privacy decision before receiving context or exposing an action.

## Data classes and allowed handling

| Data | Default handling | Retention | Network |
|---|---|---|---|
| Typed/composing/surrounding/selected text | Process only for the active editor session | None beyond bounded session state | Never during normal typing |
| Passwords, visible passwords, OTP/auth values | Do not read, learn, suggest, log, or retain | None | Never |
| Clipboard history | Local and user-configurable; system-marked sensitive clips are rejected, while seller identifiers are obscured until chosen | 20 recent unpinned items and 30-day expiry; pins persist until removal | Never by default; excluded from template sync |
| Templates/profiles/default variables | Local-first seller-owned data; sync UI deferred from current release | Until edit/delete/reset/export | None in current release; future explicit template-only sync requires preview, consent, and separate approval |
| Personal dictionary/usage ranking/emoji recents | Local and resettable | Until remove/reset | Never by default |
| Voice audio/transcript | Explicit user action through device recognition provider | No app-side audio retention; final transcript remains only in a review state until explicit insert/discard or session reset | Provider-dependent, disclosed before the system permission prompt and in the review state |
| Translation input/model | Explicit selected/drafted/pasted text only; Android 11+ only while the native privacy guard is available | No text retention; model storage disclosed | Input/output remains on-device; model/config updates and ML Kit operational metrics may contact Google and require disclosure |
| Product images/URLs | Explicit seller action plus an ownership/authorization attestation; only HTTPS sources are accepted | Shared-content cache is capped at five files and evicts oldest first; records remain local and image files are not synced; final ownership/host/deletion policy still needs product approval | Only an explicit authorized HTTPS commit path; unsafe or legacy sources preserve the reviewed text/link fallback without fetching the image |
| Package name | Select local app profile only | Local binding | Not used for conversation/customer inference |

## Sensitive mode

Sensitive mode is entered for password and visible-password variations, numeric password, detectable OTP/auth fields, editors opting out of writing tools on supported Android versions, or a user-marked private app/profile.

Sensitive mode disables candidates, correction, learning, surrounding-text retention, clipboard suggestions/history capture, seller tools, translation, voice, rich content, passive topic suggestions, and text-bearing analytics. It retains only a conservative field layout and the declared editor action. Inline autofill may preserve the Android system security boundary only if hidden values remain unavailable to the IME until the user selects them; otherwise it is suppressed.

## Provider contract

Before accessing context, every provider receives an immutable privacy decision describing only allowed capabilities. Denial is fail-closed. Providers do not independently reinterpret `inputType`. Session-scoped context is bounded, never logged, and discarded on editor switch, finish, or service destruction. Stale asynchronous results are rejected by session identity.

## Explicit-action boundary

The keyboard may analyze text the seller is typing, text explicitly selected, or clipboard content the seller explicitly chooses to analyze. It must not use AccessibilityService, screen scraping, notifications, passive incoming-message reads, broad package inventory, or automatic sending. Seller assistance inserts a reviewable draft and never invokes the target app's Send action.

## Logging and diagnostics

Logs must not contain typed, composing, selected, surrounding, clipboard, voice transcript, template body, resolved variable, identifier, or image URL content. Diagnostics may include non-text session IDs, feature state, duration, result category, API level, and error class. Crash reports require the same redaction.

ML Kit Translation 17.0.3 was observed logging full source text from its bundled native translator
in both debuggable and non-debuggable minified builds. SellerKeyboard now raises the process-native
log threshold only around the translation task and restores it on success, failure, cancellation,
and teardown. Android exposes the supported set/restore API only from API 30, so translation is
fail-closed on API 26–29 rather than accepting text-bearing provider logs.

## Storage, clearing, export, and sync

Local stores have item/size/age bounds and explicit clear/reset controls. Import/export uses a versioned schema, validation result, and preview. Existing snippets and clipboard pins must migrate without silent loss. Sync is opt-in and limited to explicitly selected template/reply data after schema, privacy, ownership, retention, and deletion review; typed text, context, clipboard history, dictionary, and usage signals are excluded.

Android automatic cloud backup is disabled. The Convex client and repositories remain lazy and the current release Home route does not expose cloud controls; a connected local-data probe proves Home launch leaves all three dependencies uninitialized. Re-enabling sync requires a separately approved template-only schema and credentialed attribution. Translation model/config updates and ML Kit operational metrics, voice provider use, and remote product-image loading remain explicit or disclosed network-capable behavior. Google states that ML Kit translation input and output stay on-device, while SDK metrics include device/app information, performance, input/output sizes, event/error types, and configured source/destination languages.

## Verification

Release evidence must include field-policy unit tests, feature-provider suppression tests, log inspection, and device network capture for typing, candidates, clipboard, seller tools, translation, voice, and sync. Normal typing must produce no network traffic. Any skipped release-blocking privacy check requires an explicit approved exception and causes the verification script to fail otherwise.

The API 36 personal-dictionary gate drives the real settings UI and installed IME: it adds a controlled word, proves persistence across app/IME process recreation, exposes and exactly commits the personal candidate in a distinct-package editor, then proves a masked sensitive editor reports zero candidates and never exposes that word. Product-UI Remove and Clear each persist across recreation, the pre-test dictionary is restored exactly, and exact app-UID attribution records `0/0` RX/TX bytes plus zero controlled-word log occurrences. Physical OEM presentation and persistence acceptance remain separate release evidence.

The API 36 emoji gate drives the real installed IME without another software keyboard: it enters a controlled search through the local keypad, commits the exact search result and a full skin-tone sequence, proves recents and product-UI Clear across process recreation, and verifies that masked editors expose neither the Emoji toolbar action nor retained emoji UI. Exact app-UID attribution records `0/0` RX/TX bytes and zero controlled query/emoji log occurrences. Its reversible fixture restores both the ordered recent list and Emoji2's separate sticky-variant preference exactly; physical OEM/font presentation remains separate release evidence.

The API 36 emulator additionally runs an instrumentation-only recognition provider through Android's real `SpeechRecognizer` binder. It deterministically covers partial, processing, review-result, network/no-match, and cancel/no-late-result paths while exact attribution records zero network bytes and zero controlled-transcript log occurrences for both the app and provider UIDs. This does not replace physical real-microphone quality, OEM permission presentation, offline behavior, or configured-provider acceptance.

The API 36 permission UI is also exercised end to end without a pre-grant: the prominent disclosure states microphone timing, possible provider networking, transcript review, and no automatic send; dismissal leaves permission denied; retry repeats the disclosure; only an explicit Android system action grants audio; and the configured provider can then start and cancel back to Idle. Physical OEM presentation, real acoustic quality, and offline/provider acceptance remain separate release evidence.
