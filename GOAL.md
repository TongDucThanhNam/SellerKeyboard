# Goal: Make SellerKeyboard a Seller-Competitive Android Keyboard

## Objective

Deliver an Android input method that is trustworthy enough to replace Gboard for daily Vietnamese and English typing, while being materially faster than Gboard for repetitive seller communication.

The product must:

1. Pass a Typing Trust Gate: core typing, layout, field adaptation, suggestions, correction, privacy, accessibility, and performance must feel predictable enough for daily use.
2. Pass a Seller Advantage Gate: seller-specific tasks must require substantially fewer interactions than the same tasks performed with Gboard.
3. Pass a Release Evidence Gate: every competitive claim must be supported by same-device comparisons, automated tests, device tests, privacy checks, and reproducible benchmarks.

This goal is not a request to copy Gboard branding or every Gboard feature. It is a request to match the interaction quality users rely on, then differentiate through seller customization.

## Product Thesis

Gboard wins through trust: users expect every key, field, gesture, correction, and editor action to behave correctly without thought. SellerKeyboard cannot compensate for weak fundamentals with more seller buttons.

SellerKeyboard wins when it turns repeated commercial communication into a fast, reviewable workflow:

- reusable replies that are searchable and parameterized;
- product, price, promotion, shipping, return, and order-status tools;
- seller-aware clipboard parsing and identifier protection;
- per-app tool profiles for the app currently being typed into;
- explicit, privacy-safe assistance that never passively reads conversations;
- local-first data and deterministic insertion before optional AI.

The competitive promise for this release is:

> Gboard-like confidence for ordinary typing, with seller workflows that Gboard does not specialize in.

Do not claim complete Gboard feature parity. Glide typing, handwriting, GIF/sticker breadth, and massive language coverage remain strategic roadmap items unless separately funded and validated.

## Why This Goal Replaces the Previous Scope

The previous goal concentrated on visual layout, Shift behavior, and basic editor integration. Those are necessary but not sufficient for a keyboard that can compete for daily use.

Official Gboard documentation establishes a wider trust baseline: suggestions and autocorrection, cursor gestures, gesture deletion, long-press alternatives, clipboard fragments and pinning, voice input, emoji, multilingual typing, field-aware actions, themes, sound, and haptics.

The Android input-method platform also exposes more capability than the current implementation uses: editor metadata, surrounding text, code-point-safe editing, completion/correction objects, cursor-anchor updates, inline autofill, rich content, subtypes, content MIME negotiation, and handwriting entry points.

This revised goal therefore treats visual parity as one workstream inside a complete IME product system.

## Research Sources and Evidence Rules

Use primary and official sources for platform behavior, policy, and competitor capability claims.

Primary sources used to define this goal:

- Android input-method package reference:
  https://developer.android.com/reference/kotlin/android/view/inputmethod/package-summary
- Android InputConnection:
  https://developer.android.com/reference/android/view/inputmethod/InputConnection
- Android EditorInfo:
  https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- Android InputMethodService:
  https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- Android inline autofill for IMEs:
  https://developer.android.com/identity/autofill/ime-autofill
- Android secure clipboard handling:
  https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling
- Android 10 clipboard privacy changes:
  https://developer.android.com/about/versions/10/privacy/changes
- Android slow-rendering guidance:
  https://developer.android.com/topic/performance/vitals/render
- Android Macrobenchmark:
  https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Android Emoji2:
  https://developer.android.com/jetpack/androidx/releases/emoji2
- Google Play Gboard listing:
  https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin
- Gboard keyboard usage and gestures:
  https://support.google.com/gboard/answer/2842292
- Gboard suggestions and correction:
  https://support.google.com/gboard/answer/7068415
- Gboard clipboard:
  https://support.google.com/gboard/answer/10742542
- Gboard themes, sound, and vibration:
  https://support.google.com/gboard/answer/6102154
- Gboard writing tools:
  https://support.google.com/gboard/answer/16515540
- Shopify Inbox:
  https://help.shopify.com/en/manual/inbox
- Shopify Inbox quick replies:
  https://help.shopify.com/en/manual/inbox/configure-inbox/quick-replies
- Shopify Inbox instant answers:
  https://help.shopify.com/en/manual/inbox/chat-settings-and-appearance/instant-answers
- Shopify Inbox conversation management:
  https://help.shopify.com/en/manual/inbox/conversations
- Meta Business Suite Inbox:
  https://www.facebook.com/help/messenger-app/294426838452244
- Google Play User Data policy:
  https://support.google.com/googleplay/android-developer/answer/10144311
- ML Kit Smart Reply:
  https://developers.google.com/ml-kit/language/smart-reply/android
- ML Kit Language Identification:
  https://developers.google.com/ml-kit/language/identification/android
- ML Kit Translation:
  https://developers.google.com/ml-kit/language/translation/android
- ML Kit GenAI overview:
  https://developers.google.com/ml-kit/genai

Research rules for implementation agents:

- Record the source URL, access date, Gboard version, Android version, device model, locale, and keyboard settings for every competitive observation.
- Treat screenshots and remembered behavior as hypotheses until reproduced on the reference device.
- Mark unresolved behavior with [needs confirmation].
- Mark numeric targets derived from product judgment rather than an external standard with [estimate].
- Do not silently reduce scope because a platform API is difficult; document the constraint, fallback, and evidence.
- Do not claim a feature is equivalent to Gboard merely because a control exists. Verify behavior, error handling, state transitions, accessibility, and performance.

## Current Repository Baseline

### Build and architecture

- Android app module: app/.
- Kotlin and Jetpack Compose UI.
- Minimum SDK 26, compile/target SDK 36.
- Input method service: app/src/main/java/com/terasumi/sellerkeyboard/service/SellerKeyboard.kt.
- Keyboard UI and layouts: app/src/main/java/com/terasumi/sellerkeyboard/keyboard/.
- Material 3 theme tokens: app/src/main/java/com/terasumi/sellerkeyboard/ui/theme/.
- Room-backed local data and an existing Convex-backed snippet path.
- Existing JVM baseline: 76 tests in 14 suites, with zero failures, errors, or skips at the time this goal was researched.
- Existing debug build and Android lint passed at the time this goal was researched.
- Device and instrumented verification were not run because no emulator or physical device was connected.

Re-run all baselines before implementation and record any drift. The numbers above are historical evidence, not a substitute for fresh verification.

### Existing strengths

- InputMethodService and Compose input view are already integrated.
- Vietnamese Telex composition exists.
- Shift currently has three states.
- Clipboard history, pinning, deletion, and expiry exist.
- Snippets, calculator, voice entry, inline autofill, and rich-content primitives exist in partial form.
- Portrait and landscape layouts already differ.
- The codebase has behavior-oriented JVM tests that can be expanded.

### Material gaps

- Layout geometry, field-specific layouts, action keys, symbol pages, and visual state are not yet consistently Gboard-like.
- Shift state is not fully driven by EditorInfo, cursor caps mode, selection updates, or all non-letter actions.
- Delete operates on UTF-16 units in paths that must be code-point safe.
- Long-press alternatives, spacebar cursor control, gesture deletion, and full repeat behavior are absent or incomplete.
- Suggestion-strip arbitration, bilingual predictions, autocorrection, dictionary learning, and correction undo are absent.
- Emoji, voice, rich content, inline autofill, clipboard, and seller tools do not yet form one stable-height interaction model.
- Snippets are limited to title, content, and image URLs; there is no search, categories, tags, favorites, usage ranking, slash command, variable filling, or per-app profile.
- Seller calculators and smart commercial entities are incomplete.
- Rich-image insertion does not yet have robust MIME negotiation and fallback behavior.
- Privacy boundaries are not encoded as one central policy.
- Performance has not been benchmarked against Gboard on the same device.

## Competitive Capability Matrix

The implementation must create and maintain docs/competitive-keyboard-matrix.md. The initial target matrix is:

| Capability | Gboard baseline | Current SellerKeyboard | Required for this release | Evidence required |
|---|---|---|---|---|
| Core alphabet layout | Mature field-aware layouts | Partial string-driven layout | Yes | Screenshot geometry plus behavior tests |
| One-shot Shift and Caps Lock | One-shot Shift; double-tap Caps Lock | Partial three-state logic | Yes | State-machine tests and device video |
| Automatic capitalization | Editor and cursor aware | Incomplete | Yes | EditorInfo and selection test matrix |
| Delete and repeat | Character-aware repeat and gesture deletion | Partial repeat, unsafe edge cases | Yes | Unicode corpus and stress tests |
| Spacebar cursor gesture | Supported | Missing | Yes | Gesture tests and device comparison |
| Swipe-left deletion | Supported | Missing | Yes | Selection-preview and commit tests |
| Long-press alternatives | Supported | Missing | Yes | Locale key map and UI tests |
| Suggestions and autocorrection | Core feature | Missing | Yes | Offline bilingual corpus benchmark |
| Undo autocorrection | Backspace restores original | Missing | Yes | Behavioral tests |
| Personal dictionary | Add/remove learned words | Missing | Yes | Persistence and privacy tests |
| Vietnamese and English typing | Multilingual | Vietnamese composition only | Yes | Mixed-language corpus |
| Clipboard history and fragments | History, pin, parsed fragments | Partial | Yes | Entity parser and expiry tests |
| Emoji | Search, recents, variants | Missing | Yes, basic competitive panel | UI and compatibility tests |
| Voice input | Supported | Partial | Yes | availability, permission, and fallback tests |
| Translation | Supported | Missing | Yes for explicit VI/EN workflow | model and replacement tests |
| Theme, key borders, sound, haptics | Configurable | Partial | Yes | settings persistence and device tests |
| Height and one-handed mode | Configurable | Incomplete | Yes | portrait/landscape/device tests |
| Inline autofill | Platform-supported | Partial | Yes | Android 11+ integration tests |
| Rich content | App-dependent | Partial | Yes for seller product/image flow | MIME and fallback matrix |
| Quick replies | Generic clipboard/text tools | Basic snippets | Seller differentiator | seller task benchmark |
| Parameterized templates | Not a seller-specialized core | Missing | Seller differentiator | unresolved-variable safety tests |
| Product and promotion packs | Not a seller-specialized core | Missing/partial | Seller differentiator | cross-app insertion tests |
| Per-app seller profiles | General preferences only | Missing | Seller differentiator | package-scoped behavior tests |
| Seller calculator | General calculator | Partial calculator | Seller differentiator | deterministic finance tests |
| Smart seller entities | Clipboard fragments are general | Limited parser | Seller differentiator | commercial entity corpus |
| Glide typing | Supported | Missing | Deferred | Roadmap only |
| Handwriting | Supported | Missing | Deferred | Roadmap and API spike |
| GIF/sticker breadth | Supported | Missing | Deferred | Roadmap only |
| Massive language coverage | Supported | Missing | Deferred | Honest product boundary |
| Generative Vietnamese rewriting | Availability varies | Missing | Deferred | Language/device support is insufficient |

Before release, update every row with:

- current implementation status;
- exact test or benchmark artifact;
- known limitation;
- Gboard comparison version;
- pass, conditional pass, or fail.

## Android Input-Method Capability Map

Use the platform deliberately instead of rebuilding editor semantics inside composables.

| Platform surface | Required product use | Compatibility and fallback |
|---|---|---|
| EditorInfo.inputType | Choose alphabet, email, URI, password-safe, phone, number, decimal, signed, date/time, and multiline behavior | For TYPE_NULL or unusual editors, expose a conservative limited layout |
| EditorInfo.imeOptions | Render Done, Go, Next, Previous, Search, Send, or Enter and call the correct editor action | If no action is declared, insert a newline only when multiline is permitted |
| EditorInfo.initialCapsMode | Seed capitalization state on input start | Recompute with InputConnection.getCursorCapsMode after edits and cursor moves |
| EditorInfo.packageName | Apply the local profile for only the active editor application | Never request broad app inventory solely for this feature |
| EditorInfo.contentMimeTypes | Decide whether an app can receive product images or rich content | Fall back to text and product URL when the target MIME type is unsupported |
| EditorInfo initial surrounding text | Seed context without unnecessary IPC on supported Android versions | Use small, bounded InputConnection queries on older versions |
| InputConnection.getSurroundingText | Read bounded local context for corrections and explicit seller assistance | Available from API 31; minimize IPC and never retain sensitive context |
| InputConnection.deleteSurroundingTextInCodePoints | Delete full Unicode code points | Available within the repository minimum SDK; use grapheme-aware logic where needed |
| InputConnection.getSelectedText | Power explicit selected-text actions | Disable the action if the editor does not return selection text |
| InputConnection.setComposingText and finishComposingText | Maintain Vietnamese and correction composition | Finish composing on editor transitions and incompatible actions |
| InputConnection.performEditorAction | Execute the action represented by the Enter key | Fall back safely when the editor rejects it |
| InputConnection.beginBatchEdit and endBatchEdit | Make multi-step replacements atomic | Always balance calls, including error paths |
| InputConnection.requestCursorUpdates | Support cursor-aware gestures and candidate placement where needed | Request only necessary filters to control CPU cost |
| CompletionInfo and onDisplayCompletions | Show editor-provided completion candidates when present | Arbitrate above local text suggestions without layout jumps |
| CorrectionInfo | Notify compatible editors about corrections | Treat editor support as optional |
| InlineSuggestionsRequest and response | Render autofill suggestions without exposing sensitive values before selection | Android 11+ only; preserve privacy and fall back to normal keyboard |
| InputContentInfo | Commit supported product images/content with user action | Validate URI permission, MIME type, and text/link fallback |
| InputMethodSubtype | Model supported language and layout variants | Introduce only after bilingual behavior is stable |
| onUpdateSelection | Reconcile composition, auto-caps, correction state, and cursor moves | Ignore stale updates using connection/session identity |
| onUpdateCursorAnchorInfo | Consume only when a feature has explicitly requested cursor data | Disable request when the feature closes |
| onComputeInsets | Keep touchable and visible regions correct | Verify edge-to-edge, gesture navigation, and floating overlays |
| onEvaluateFullscreenMode | Avoid accidental disruptive extract UI in landscape unless deliberately designed | Define and test the chosen landscape behavior |
| Handwriting APIs | Future stylus roadmap | Do not block this release; add an API feasibility artifact |
| EditorInfo.isWritingToolsEnabled | Respect editor opt-out for writing assistance on API 36+ | Disable relevant writing tools when opted out |

## UX Architecture

### Stable surface model

The keyboard must not jump vertically as suggestions, autofill, clipboard items, seller tools, or validation messages appear.

Use three stable regions:

1. Context strip: one fixed-height row that can host inline autofill, editor completions, text candidates, or a compact seller suggestion.
2. Key field: the active alphabet, symbols, number, phone, emoji, clipboard, or seller layout.
3. Utility edge: optional navigation/one-handed affordance that never reduces key targets below the accepted accessibility boundary.

The context strip uses deterministic priority:

1. Sensitive field: no retained context and no learned or seller suggestions.
2. Inline autofill response.
3. Editor-provided CompletionInfo values.
4. Explicit seller workflow that the user opened.
5. Text correction and next-word candidates.
6. Clipboard suggestion.
7. Passive seller-topic suggestion derived only from text the user typed or explicitly copied/selected.

Lower-priority providers must not animate in front of a higher-priority provider or change keyboard height.

### Input session model

Represent one active editor session with an immutable model containing:

- session identity;
- input class and variation;
- editor action;
- multiline capability;
- sensitive state;
- initial and current caps mode;
- language mode;
- composing range and text;
- selection range;
- target package name;
- supported MIME types;
- active per-app profile;
- active surface and candidate source.

Platform callbacks update the session model through a single coordinator. Composables render state and emit semantic actions; they do not directly own durable IME behavior.

### Semantic actions

Keys and tools emit semantic actions such as:

- InsertText;
- InsertCodePoint;
- DeleteBackward;
- DeleteWordOrSelection;
- MoveCursor;
- SetShiftMode;
- PerformEditorAction;
- SwitchLayout;
- AcceptCandidate;
- UndoCorrection;
- OpenSellerTool;
- InsertTemplate;
- CommitRichContent.

Do not use display strings as the behavioral protocol. Render labels from immutable key definitions.

## Gate A: Typing Trust

All requirements in this section are release blocking.

### A1. Layouts and adaptive geometry

- Reproduce the functional geometry of the configured Gboard reference on the same device without copying protected visual branding.
- Provide alphabet, shifted alphabet, symbols page 1, symbols page 2, number, decimal/signed number, phone, email, URI, password-safe, and multiline variants.
- Use weighted rows and bounded key widths rather than fixed assumptions that break on tablets or landscape.
- Preserve portrait number-row preference and define the landscape policy explicitly.
- Support keyboard height presets and a one-handed layout with left/right anchoring.
- Keep all primary key touch targets at least 48 dp where the available IME height permits.
- If an extreme window size makes 48 dp impossible, document the compact exception and preserve at least the visual and semantic hit area [needs confirmation].
- Respect navigation bars, display cutouts, edge-to-edge insets, gesture navigation, and orientation changes.
- Avoid unexpected fullscreen extract mode in landscape.

### A2. Shift, Caps Lock, and auto-capitalization

Implement an explicit state machine:

- Lowercase: letters commit lowercase.
- OneShotShift: the next committed alphabetic character is uppercase, then state returns to automatic lowercase/caps evaluation.
- CapsLock: subsequent alphabetic characters remain uppercase.

Transitions:

- First Shift tap from Lowercase enters OneShotShift.
- Second Shift tap within the tested double-tap interval enters CapsLock.
- A Shift tap while CapsLock is active returns to Lowercase.
- A Shift tap after the double-tap interval toggles between Lowercase and OneShotShift.
- Non-letter actions do not consume OneShotShift unless same-device Gboard evidence proves otherwise for that action.
- Cursor movement, selection changes, external edits, input restarts, and editor switches reconcile against current caps mode.
- Sentence starts and field rules use EditorInfo.initialCapsMode and InputConnection.getCursorCapsMode.
- Email, URI, password, code-like, SKU, tracking, and other identifier contexts must not receive unwanted capitalization.

The double-tap interval must be configurable from Android timing guidance or measured Gboard behavior; do not keep an unexplained hardcoded 400 ms value [needs confirmation].

Visual states:

- Lowercase letters are visibly lowercase.
- OneShotShift has a clear active Shift icon.
- CapsLock uses a distinct locked indicator.
- State remains legible in light, dark, high-contrast, and key-border modes.

### A3. Enter and editor actions

- Map IME_ACTION_GO, SEARCH, SEND, NEXT, PREVIOUS, DONE, and custom labels/actions to the correct key presentation and InputConnection behavior.
- Insert newline only for fields that allow it.
- Preserve composing text before a compatible editor action and finalize it safely when required.
- Never send a message automatically from a seller tool; SellerKeyboard inserts a draft and the user invokes Send.

### A4. Delete, repeat, cursor, and selection gestures

- Delete selection first.
- Otherwise delete a full Unicode code point and avoid splitting surrogate pairs.
- Treat grapheme clusters such as emoji sequences, combining marks, and flags predictably; document intentional differences from Gboard.
- Long-press Backspace begins after a measured delay, repeats at a controlled interval, accelerates only if validated, and stops on release, cancellation, pointer exit policy, session change, or service teardown.
- Swipe left from Backspace selects a progressively larger deletion range and commits deletion only on release, with cancellation support.
- Horizontal dragging on Space moves the cursor proportionally without inserting spaces.
- Optional selection mode may be entered from the space gesture only if it does not conflict with language switching [needs confirmation].
- Every gesture has touch-slop, velocity, multi-pointer, cancellation, and accessibility behavior tests.

### A5. Long press, popup preview, and key feedback

- Show a non-obscuring key preview while pressing printable keys, with a preference to disable it.
- Long-press letters and punctuation reveal locale-appropriate alternatives.
- Long-press period exposes commonly used punctuation.
- Long-press symbol hints are rendered only when the symbol is actually available.
- Support configurable key borders, sound, volume, haptic feedback, and haptic strength within platform constraints.
- Respect system accessibility and reduced-motion expectations.
- Do not play feedback for canceled gestures or duplicate it during repeat.

### A6. Vietnamese and English language intelligence

Provide a local-first, replaceable candidate-engine boundary. The UI must not depend on a single spell-check or machine-learning implementation.

Required behavior:

- Vietnamese Telex composition remains deterministic and testable.
- English and Vietnamese candidates can coexist in mixed-language seller messages.
- Candidate sources distinguish typed word, normalized Telex result, dictionary word, correction, next word, user word, and protected token.
- Autocorrection occurs only above an evidence-backed confidence threshold.
- Backspace immediately after an autocorrection restores the exact original token.
- Tapping a candidate replaces only the intended composing/token range.
- User-added words persist locally and can be removed or reset.
- Learned data can be disabled and cleared.
- Password, visible-password, one-time-code, and other sensitive fields never learn or expose candidates.
- Email addresses, URLs, phone numbers, prices, SKUs, order IDs, tracking codes, discount codes, product slugs, hashtags, and mentions are protected from destructive correction.
- Candidate queries use bounded surrounding context and do not block key dispatch.
- Any optional system spell-check provider is treated as untrusted for sensitive text and is not the only engine.
- No normal typing content leaves the device.

Create a privacy-safe synthetic evaluation corpus with at least:

- 600 Vietnamese seller utterances [estimate];
- 200 English or mixed-language utterances [estimate];
- 200 protected-token cases covering identifiers, numbers, money, URLs, email, Unicode, and emoji [estimate].

Compare SellerKeyboard and the reference Gboard version on the same corpus where automation and licensing permit. Record manual protocol for cases that cannot be automated.

Targets:

- Protected-token destructive mutation rate: 0%.
- Autocorrection undo correctness: 100% in the deterministic corpus.
- Top-3 candidate recall: no more than 5 percentage points below Gboard on the same agreed corpus [estimate].
- Alternatively, if Gboard output cannot be captured reproducibly, meet an independently labeled top-3 recall threshold of at least 85% and keep the Gboard comparison as a manual evidence set [needs confirmation].
- Key dispatch remains independent of candidate-engine latency.

### A7. Clipboard

- Preserve history, pin, delete, clear, and configurable expiry.
- Unpinned entries expire automatically; pinned entries persist until removed.
- Mark and visually obscure sensitive clipboard entries using Android sensitivity conventions.
- Never sync clipboard history by default.
- Parse useful fragments without modifying source text:
  email, URL, phone, address, date, time, price, order ID, tracking code, discount code, and product link.
- Let the user paste the whole item or one parsed fragment.
- Deduplicate normalized entries without merging semantically distinct values.
- Bound item count, text size, image metadata, and database growth.
- Explain Android-version clipboard limitations and foreground/default-IME behavior.
- Disable clipboard suggestions in sensitive fields.

### A8. Emoji

- Add a stable-height emoji panel using current Emoji2-compatible data/components.
- Include categories, search, recents, skin-tone or supported variants, and return-to-letters.
- Keep recents local and clearable.
- Commit full emoji sequences without corrupting grapheme clusters.
- Handle editors/fonts that render unsupported emoji gracefully.
- GIF and sticker search are not required for this release.

### A9. Voice

- Provide a visible voice action when recognition is available and permitted.
- Request microphone permission through an understandable, interruptible flow.
- Show listening, processing, result, cancellation, and error states.
- Insert a reviewable transcript; never auto-send.
- Do not expose voice in password or otherwise sensitive fields.
- Make network/offline behavior clear to the user because recognition capability varies by provider and device.
- Restore the previous keyboard state after completion or cancellation.

### A10. Explicit VI/EN translation

- Translate only text the user explicitly selects, drafts, or pastes into the translation surface.
- Support Vietnamese-to-English and English-to-Vietnamese as the initial product pair.
- Prefer on-device ML Kit translation with explicit model download, storage disclosure, progress, cancellation, and retry.
- Preview original and translated text before insert or replace.
- Preserve undo/recovery for replacement.
- Never auto-translate passwords, one-time codes, or hidden/sensitive text.
- Do not market translation as human-equivalent; seller remains responsible for correctness.

### A11. Inline autofill and editor completions

- Request and render inline autofill on Android 11+ without reading hidden values.
- Preserve the system security boundary: sensitive content remains unavailable to the IME until the user selects it.
- Render editor-provided completions when apps supply them.
- Apply the context-strip priority model and keep height stable.
- Destroy stale inline/completion models when the editor session changes.
- Verify password managers and common browsers on the device matrix.

### A12. Themes and settings

- Offer system, light, and dark themes.
- Offer key borders, high-contrast option, number row, popup preview, sound, haptics, keyboard height, one-handed side, toolbar order, and language mode.
- Settings changes apply predictably and persist across service recreation.
- Include a reset-to-default path.
- Theme customization must preserve contrast and state legibility.

## Gate B: Seller Advantage

All requirements in this section are release blocking. Generic typing parity alone does not complete this goal.

### B1. Seller toolbar and command palette

- Provide a customizable toolbar whose tools can be reordered, hidden, and reset.
- Initial tools: Replies, Products, Clipboard, Calculator, Translate, Voice, Emoji, and Settings.
- Provide one searchable command palette so the user can reach any template or seller action without navigating multiple grids.
- Support favorites, recents, usage-based ranking, categories, tags, and explicit sorting.
- Keep usage ranking local and resettable.
- Never change toolbar order automatically without user confirmation.

### B2. Per-app local profiles

- Use only the active EditorInfo.packageName to select a local profile for the current editor.
- Do not request QUERY_ALL_PACKAGES only to discover target apps.
- Let the user create named profiles and assign them when typing in an app.
- A profile may define toolbar order, preferred reply pack, default language, number row, theme/height override, and enabled seller tools.
- Always provide a global fallback profile.
- Clearly display when an app-specific profile is active.
- Never infer customer identity or conversation content from the package name.

### B3. Searchable quick replies

- Evolve snippets into reply templates with stable IDs, title, body, category, tags, favorite state, usage metadata, optional shortcut, optional media/link attachments, and profile membership.
- Search title, content, tag, shortcut, and normalized Vietnamese text.
- Support slash shortcuts such as /ship, /return, /cod, /warranty, and /thanks.
- A shortcut suggestion may appear only after the user types the slash sequence; no passive chat reading is allowed.
- Insertion always remains user initiated.
- Provide create, edit, duplicate, archive, import, export, and delete with undo or confirmation where destructive.

### B4. Parameterized templates

Initial supported variables:

- {{customer_name}}
- {{order_id}}
- {{tracking_code}}
- {{product_name}}
- {{product_url}}
- {{price}}
- {{discount_code}}
- {{shop_name}}
- {{shipping_date}}

Requirements:

- Opening a parameterized reply presents a compact fill-and-review surface.
- Prefill only from explicit user input, explicitly selected text, explicitly chosen clipboard fragments, or saved seller defaults.
- Highlight required and unresolved variables.
- Never commit a template containing an unresolved placeholder.
- Preserve punctuation, line breaks, Unicode, and Telex composition.
- Allow optional variables and conditional text only after a deterministic syntax is specified and tested [needs confirmation].
- Insert the final draft atomically and keep an undo path.

### B5. Product and promotion reply packs

- Model a product reply as structured text plus optional product URL, price, image, and promotion information.
- Allow one-tap selection of a product followed by a reviewable insertion choice.
- Check EditorInfo.contentMimeTypes before offering rich-image commit.
- If the target app cannot receive the image, fall back to product name, price, and link without data loss.
- Support reusable promotion blocks with discount code, validity note, and terms.
- Never fabricate stock, price, policy, or delivery information.
- Mark stale manually maintained product data so the seller can review it [needs confirmation for stale policy].
- Do not introduce store-platform scraping in this goal.

### B6. Seller calculator

Add deterministic tools for:

- percentage discount and final price;
- markup and margin;
- platform fee;
- shipping fee allocation;
- quantity times unit price;
- simple profit estimate;
- VND formatting and rounding.

Requirements:

- Define formulas and rounding modes in code-independent product documentation.
- Show inputs, result, and copied/inserted representation.
- Preserve the existing general calculator.
- Avoid presenting estimates as accounting or tax advice.
- Test boundary values, zero, negative inputs where allowed, large values, decimal rounding, and locale formatting.

### B7. Smart commercial clipboard

- Extend parsing to the entities listed in Gate A plus seller-specific product, order, tracking, discount, and money patterns.
- Keep raw source visible and allow exact paste.
- Let the user map a parsed fragment into a template variable.
- Protect identifiers from autocorrection and Telex transformation.
- Never upload clipboard content for classification.
- Treat addresses, phone numbers, order identifiers, and tracking numbers as potentially sensitive.

### B8. Explicit seller-topic assistance

Seller conversation topics include:

- checkout and payment;
- edit or cancel order;
- order status and tracking;
- price and discount;
- product details and availability;
- returns and warranty;
- shipping;
- store information;
- wholesale;
- greeting and follow-up.

The keyboard may suggest a saved reply category only from:

- text the seller is currently typing;
- text the seller explicitly selected;
- text the seller explicitly copied and chose to analyze.

The keyboard must not:

- use AccessibilityService to inspect other apps;
- scrape the screen or notification content;
- passively read incoming messages;
- infer a customer profile without explicit integration and consent;
- automatically send a reply.

If no trustworthy topic match exists, show search instead of a speculative reply.

### B9. Local-first data and optional sync

- Templates, profiles, favorites, usage metadata, variables, clipboard history, dictionary, and recent emoji are local by default.
- Typed text, surrounding text, selected text, and clipboard history are never part of normal sync.
- Existing Convex sync may carry only explicitly chosen template/reply data after schema and privacy review.
- Image sync must preserve content ownership, storage limits, MIME metadata, deletion behavior, and offline fallback.
- Provide export/import for seller-owned reply packs before relying on cloud sync.
- Team packs, shared catalogs, CRM/order sync, and cloud AI require a separate goal with authorization and data-governance design.

## Seller Workflow Benchmark

Create a privacy-safe benchmark with at least 40 representative scenarios [estimate], distributed across:

- greeting and closing;
- price and discount;
- product details;
- product link or image;
- shipping estimate;
- order status;
- tracking code;
- returns and warranty;
- payment/COD;
- address or phone reuse;
- bilingual reply;
- calculator-assisted quote;
- app-specific reply pack.

For each scenario, measure:

- taps;
- keystrokes;
- gesture count;
- time to a reviewable draft;
- correction count;
- wrong insertion or unresolved variable;
- whether the task can be completed offline.

Compare:

1. reference Gboard with its native features and a fair clipboard/manual baseline;
2. SellerKeyboard default profile;
3. SellerKeyboard customized seller profile.

Release targets:

- Median taps plus keystrokes for seller scenarios are at least 50% lower than Gboard [estimate].
- A fixed saved reply is reachable within two interactions after opening the seller/reply surface [estimate].
- A parameterized reply is committed within four interactions after opening it when required values are already available as saved defaults or explicit clipboard fragments [estimate].
- Product text/link fallback succeeds in every tested target app.
- Unresolved placeholders committed: zero.
- Auto-sent messages: zero.
- Seller benchmark task completion rate: 100%.

Document fair-comparison settings so the benchmark cannot be optimized through hidden preconfiguration.

## Gate C: Release Evidence

### C1. Correctness

- Unit-test every input state transition, layout resolver, field policy, candidate arbitration rule, template parser, entity parser, calculator formula, and privacy gate.
- Instrument editor-session lifecycle, selection updates, composing text, editor actions, inline autofill, rich content, rotation, and service recreation.
- Include Unicode cases: surrogate pairs, combining marks, Vietnamese diacritics, ZWJ emoji, flags, skin tones, variation selectors, and mixed-direction text.
- Run a 10,000-action deterministic key stress sequence with zero loss, duplication, or ordering error [estimate].
- Run 100 show/hide and editor-switch cycles with no stale insertion, crashed composition, or unbounded memory growth [estimate].
- Verify rapid Shift tapping, hold-delete cancellation, pointer cancellation, and multi-touch.

### C2. Performance

Measure on at least one lower-midrange and one current mainstream physical Android device [needs confirmation].

Required artifacts:

- Macrobenchmark module or equivalent reproducible IME launch/show scenario.
- Perfetto traces for key dispatch, candidate query, candidate display, surface switch, and seller template insertion.
- Compose compiler stability reports and recomposition inspection for the key field.
- Baseline profile evaluation if it materially improves IME startup.

Targets:

- Internal semantic key dispatch p95 completes within one 60 Hz frame and preferably below 16 ms [estimate].
- Candidate generation never blocks the input-connection dispatch path.
- IME show-to-interactive latency is no worse than 1.25 times the reference Gboard result on the same device and configuration [estimate].
- No frozen frame above 700 ms during the benchmark.
- No application-not-responding event.
- Key-field recomposition is scoped; pressing one key must not rebuild unrelated static rows without evidence that cost is negligible.
- No unbounded heap growth across the show/hide stress test.

Performance targets are product thresholds, not claims that Android guarantees these numbers.

### C3. Privacy and security

Centralize an EditorPrivacyPolicy that all candidate and seller providers must consult.

Sensitive mode includes at minimum:

- password and visible-password variations;
- one-time codes and authentication values where detectable;
- editors that disable writing tools on supported Android versions;
- any app/profile explicitly marked private by the user.

In sensitive mode:

- no candidates, learning, clipboard suggestions, seller tools, translation, voice, or rich-content actions;
- no surrounding-text retention;
- no analytics payload containing text;
- conservative keyboard layout and editor action only.

Global requirements:

- Normal typing produces no network traffic.
- Network and model download actions are explicit and attributable.
- Logs never contain typed, selected, surrounding, template-variable, clipboard, or voice content.
- Local databases use bounded retention and support clear/reset.
- Export/sync is explicit and includes a data preview.
- Complete Google Play Data safety and privacy-policy review before release.
- Provide prominent disclosure and consent for any behavior users would not reasonably expect.
- Do not use AccessibilityService for seller assistance.
- Do not sell or repurpose user data.
- Run network inspection during typing, suggestions, clipboard, seller tools, translation, voice, and sync tests.

### C4. Accessibility

- Every interactive control has a meaningful role, label, state, and TalkBack order.
- Shift, Caps Lock, active profile, selected candidate, pinned clipboard item, and unresolved variable expose state semantically.
- Touch targets meet the geometry requirement.
- Contrast meets applicable WCAG guidance for text/icons and interactive states.
- Font scaling does not clip settings, tool sheets, or template review surfaces.
- Switch Access, TalkBack, external keyboard coexistence, and touch exploration are tested.
- Sound and haptic feedback are never the only state signal.
- Reduced animation remains usable.

### C5. Device and app matrix

At minimum test:

- API 26, 30, 31, 33, 34, 35, and 36 where tooling/device availability permits;
- portrait and landscape;
- gesture and three-button navigation;
- light and dark mode;
- compact phone, mainstream phone, tall phone, tablet/foldable width [needs confirmation];
- 60 Hz and high-refresh device where available;
- low-memory process recreation.

Target editor classes:

- plain text;
- multiline chat;
- search;
- email;
- URL;
- password;
- phone;
- integer;
- decimal/signed number;
- date/time;
- WebView/contenteditable;
- editors that return incomplete InputConnection behavior.

Target app categories:

- AOSP or reference editor;
- Chrome;
- common messaging apps used by Vietnamese sellers;
- commerce seller apps;
- password manager inline autofill;
- one app that accepts image MIME content;
- one app that rejects it and requires fallback.

Exact named messaging and commerce apps require product confirmation and available test accounts [needs confirmation].

## Compose and Architecture Constraints

- Preserve package boundaries between service/runtime, keyboard UI, seller domain, persistence, and settings.
- Hoist durable keyboard state out of leaf composables.
- Render immutable, stable UI models.
- Keep platform callbacks, InputConnection calls, repositories, and ML/model lifecycle out of pure UI components.
- Use one event coordinator for ordered semantic input actions.
- Use lifecycle-aware collection where an Android LifecycleOwner is valid.
- Use rememberUpdatedState for long-lived effects that need fresh callbacks.
- Key every effect to its true editor session and cancel it on session change.
- Do not use GlobalScope.
- Do not perform database, parsing, candidate generation, image loading, network, or model work on the key-dispatch main-thread path.
- Measure recomposition before adding speculative derivedStateOf or custom stability annotations.
- Use Material 3 tokens and the existing design system; do not create an unrelated design language.
- Keep accessibility semantics explicit for custom key surfaces.

## Data Model Direction

The final schema may differ after migration design, but it must represent these concepts without flattening everything into snippet content:

- KeyboardProfile
- SellerReplyTemplate
- TemplateVariableDefinition
- ReplyPack
- ProductReply
- PromotionBlock
- SellerToolPreference
- UsageSignal
- ClipboardItem
- ParsedClipboardEntity
- PersonalDictionaryEntry
- AppProfileBinding

Requirements:

- Stable IDs and migration tests.
- No destructive migration for existing snippets or clipboard pins.
- Existing snippets convert into non-parameterized SellerReplyTemplate records.
- Existing image URLs remain attached where valid.
- Usage signals are local-only unless separately consented.
- App profile bindings contain package identifiers but no conversation data.
- Import/export has an explicit version and validation result.

## Delivery Phases

Implementation agents may parallelize safe independent work, but completion is defined by gates, not by finishing a subset of phases.

### Phase 0: Reproduce and freeze evidence

- Re-run build, JVM tests, lint, and connected tests if a device is available.
- Record current keyboard screenshots and videos for all layouts.
- Record reference Gboard version, settings, device, and comparable screenshots/behaviors.
- Create docs/competitive-keyboard-matrix.md.
- Create docs/keyboard-behavior-spec.md for exact state transitions and gesture thresholds.
- Create docs/privacy-boundaries.md.
- Mark all unknowns explicitly.

### Phase 1: Input architecture and policy foundation

- Introduce immutable editor-session, layout, key, candidate, and privacy models.
- Centralize EditorInfo resolution and editor-action behavior.
- Introduce semantic action dispatch and ordered InputConnection effects.
- Make deletion code-point safe.
- Reconcile auto-caps and composition through lifecycle callbacks.
- Add provider arbitration for context strip.
- Preserve existing functionality with migration and regression tests.

### Phase 2: Gboard-like key field and gestures

- Complete field-aware layouts and adaptive geometry.
- Complete Shift/Caps Lock behavior.
- Complete editor action key behavior.
- Add popup preview, long-press alternatives, delete repeat, swipe deletion, and spacebar cursor gesture.
- Add height, one-handed, key-border, sound, haptic, and theme settings.
- Verify portrait, landscape, insets, and fullscreen policy.

### Phase 3: Language intelligence

- Define and implement local candidate-provider boundary.
- Add Vietnamese/English dictionaries, protected tokens, candidates, autocorrection, undo, and personal dictionary.
- Build the synthetic corpus and comparison harness.
- Tune only against held-out data and preserve deterministic regression fixtures.
- Ensure candidate work never delays key dispatch.

### Phase 4: General competitive utilities

- Complete secure clipboard and fragment parsing.
- Add Emoji2 panel.
- Complete voice states and fallback behavior.
- Add explicit VI/EN translation.
- Complete inline autofill and editor completions.
- Harden rich-content MIME negotiation and fallback.

### Phase 5: Seller advantage

- Migrate snippets to seller reply templates.
- Add toolbar customization, command palette, search, tags, categories, favorites, recents, and slash shortcuts.
- Add parameterized templates and review flow.
- Add product/promotion reply packs.
- Add seller calculator and smart commercial clipboard.
- Add per-app profiles.
- Add explicit seller-topic suggestions under the privacy boundary.
- Add import/export and review optional template-only sync.

### Phase 6: Hardening and comparison

- Run the seller workflow benchmark against Gboard.
- Run performance, stress, memory, accessibility, privacy, and network tests.
- Run the full API/device/app/editor matrix.
- Resolve every fail or document an approved product exception.
- Update screenshots, videos, matrix, privacy disclosure, and release notes.

### Phase 7: Completion audit

- Run scripts/verify-competitive-keyboard.ps1.
- Confirm every success criterion has a linked artifact.
- Confirm all [needs confirmation] items have a decision.
- Confirm no strategic-defer item is represented as shipped.
- Perform a final same-device Gboard comparison.
- Do not declare completion while any release-blocking row is fail or undocumented.

## Success Criteria

| ID | Criterion | Required evidence |
|---|---|---|
| SC-01 | All fresh Gradle build, unit, lint, and connected test commands pass | Command log and reports |
| SC-02 | All required editor field layouts and actions behave correctly | Resolver tests plus device matrix |
| SC-03 | Shift, one-shot Shift, Caps Lock, and auto-caps pass the complete transition matrix | Unit/instrumented tests and video |
| SC-04 | Unicode deletion, repeat, swipe deletion, and space cursor gestures are correct and cancelable | Corpus, gesture tests, and comparison video |
| SC-05 | Layout remains usable across orientation, insets, height, and one-handed modes | Screenshots and UI tests |
| SC-06 | Vietnamese/English candidates, autocorrection, undo, and dictionary meet corpus targets | Benchmark report |
| SC-07 | Protected-token destructive mutation rate is zero | Corpus report |
| SC-08 | Clipboard history, fragments, pinning, expiry, sensitivity, and clearing work without default sync | Unit/device/privacy tests |
| SC-09 | Emoji, voice, translation, autofill, editor completion, and rich-content fallback pass their capability matrices | Integration artifacts |
| SC-10 | Seller toolbar, command palette, search, categories, favorites, recents, and shortcuts are complete | UI and behavior tests |
| SC-11 | Parameterized templates never commit unresolved variables | Parser/property tests and device flows |
| SC-12 | Product replies preserve text/link fallback when rich content is unavailable | Cross-app MIME matrix |
| SC-13 | Seller calculator formulas and VND formatting are deterministic | Formula specification and unit tests |
| SC-14 | Per-app profiles use only the active editor package and have a global fallback | Session/profile tests |
| SC-15 | Seller workflow benchmark meets the 50% median interaction-reduction target | Same-device comparison report |
| SC-16 | Auto-sent messages and passive conversation reads are both zero | Architecture audit and device/network tests |
| SC-17 | Key dispatch, IME show, frame, stress, and memory targets pass | Macrobenchmark/Perfetto/stress reports |
| SC-18 | Sensitive fields disable all prohibited surfaces and retention | Privacy-policy test matrix |
| SC-19 | Normal typing generates no network traffic or text-bearing logs | Network and log inspection |
| SC-20 | Accessibility and device/app/editor matrices pass or contain approved exceptions | Audit reports |
| SC-21 | Existing user snippets, images, clipboard pins, and settings migrate without silent loss | Migration tests and sample backup |
| SC-22 | Competitive matrix is fully populated with Gboard versioned evidence | Final docs/competitive-keyboard-matrix.md |
| SC-23 | Verification script exits successfully on the release candidate | scripts/verify-competitive-keyboard.ps1 output |

## Mandatory Verification Commands

At minimum, the final verification script must run the applicable equivalents of:

- .\gradlew.bat :app:testDebugUnitTest
- .\gradlew.bat :app:assembleDebug
- .\gradlew.bat :app:lintDebug
- .\gradlew.bat :app:connectedDebugAndroidTest
- dedicated corpus and seller-benchmark tasks introduced by the implementation
- Macrobenchmark or connected performance tasks introduced by the implementation

The script must:

- stop on failure;
- print the device/API/configuration used;
- save reports under a deterministic path;
- identify tests skipped due to missing hardware or credentials;
- return non-zero when a release-blocking check is skipped without an approved exception.

## Strategic Deferrals

These items are valuable but are not required to complete this seller-competitive release:

- glide typing;
- stylus handwriting;
- GIF and sticker search;
- floating keyboard;
- split tablet keyboard;
- broad multilingual coverage beyond the agreed Vietnamese/English scope;
- generative Vietnamese rewriting or proofreading;
- passive order, CRM, notification, or conversation ingestion;
- automatic message sending;
- team-shared catalogs and enterprise administration;
- cloud AI that receives typed or conversation text.

Each deferred item must remain visible in the competitive matrix. Deferral means an honest boundary, not permission to claim parity.

ML Kit Smart Reply is not the primary solution for this goal because official support is English-centric and the API expects conversation history that a privacy-safe generic IME does not possess.

ML Kit generative writing APIs are not a release dependency because language and device availability do not currently provide reliable Vietnamese coverage. If evaluated later, they require capability detection, explicit user action, deterministic fallback, and a separate quality/privacy gate.

## Risks and Decisions Requiring Confirmation

- Exact reference devices and named commerce/messaging apps [needs confirmation].
- Product owner acceptance of the quantitative candidate and interaction-reduction thresholds [needs confirmation].
- Whether voice provider behavior is acceptable when it requires network access [needs confirmation].
- Dictionary/licensing source for Vietnamese and English candidate data [needs confirmation].
- Product-image ownership, caching, and optional sync retention [needs confirmation].
- Whether optional template sync remains in the release or is deferred after schema/privacy review [needs confirmation].
- Exact one-handed and compact-layout minimum key sizes on very small windows [needs confirmation].
- Whether language switching uses long-press Space, a globe key, or both [needs confirmation].
- The stale-data policy for manually maintained product/price entries [needs confirmation].
- Availability of test accounts for target seller apps and rich-content flows [needs confirmation].

None of these uncertainties authorizes an implementation agent to stop all progress. Build the independently testable foundation, record the decision needed, and continue with unaffected work.

## Completion Definition

This goal is complete only when:

- Gate A, Gate B, and Gate C all pass;
- every success criterion has reproducible evidence;
- same-device Gboard comparisons are versioned and documented;
- the keyboard is demonstrably reliable for ordinary Vietnamese/English typing;
- the seller benchmark demonstrates the agreed productivity advantage;
- privacy and sensitive-field constraints pass;
- migration preserves current user data;
- scripts/verify-competitive-keyboard.ps1 succeeds;
- all remaining limitations are explicit and do not contradict the release claim.

A polished screenshot, a working Shift cycle, or a larger snippets grid is not completion.

## Instructions for Future Agents

- Read this entire file, AGENTS.md, docs/competitive-keyboard-matrix.md, docs/keyboard-behavior-spec.md, and docs/privacy-boundaries.md before changing code.
- Re-run the current baseline before trusting the research snapshot.
- Preserve unrelated user changes in the working tree.
- Work from semantic behavior and measurable outcomes, not visual imitation alone.
- Do not shrink the goal to the easiest subset.
- Do not stop at the first uncertainty; mark it and continue on unaffected requirements.
- Do not trust memory for Gboard behavior; reproduce it and record the version.
- Do not add screen scraping, AccessibilityService chat reading, passive message capture, or auto-send.
- Do not make cloud AI a hidden dependency.
- Do not weaken sensitive-field behavior to improve demos.
- Keep normal typing local and fast.
- Add tests with every state-machine, parser, migration, layout, and platform behavior change.
- Update the competitive matrix and evidence artifacts continuously, not only at the end.
- If an Android API is unavailable or unreliable in a target editor, implement the documented fallback and test both paths.
- If a success criterion cannot be met, report the exact evidence and request an explicit product decision; never silently redefine success.
