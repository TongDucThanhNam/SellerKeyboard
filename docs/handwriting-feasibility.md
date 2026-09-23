# Stylus Handwriting Feasibility

Research date: 2026-07-11

Decision: strategic deferral; handwriting is not represented as shipped in this release.

Android 14+ exposes IME handwriting sessions through `InputMethodService`, including start/finish callbacks, stylus motion delivery, the handwriting window, touchable-region control, session timeout, and newer connectionless/delegated handwriting paths. Standard `EditText` and `WebView` fields can initiate handwriting on compatible Android 14+ stylus devices when the active IME supports it.

Primary sources:

- https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/stylus-input-in-text-fields
- https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/custom-text-editors

## Implementation boundary

Platform event delivery is feasible, but SellerKeyboard has no handwriting recognizer, handwriting UI/window, VI/EN handwritten-text quality corpus, model lifecycle, or physical-stylus evidence. Adding a drawing surface alone would not satisfy typing trust. A future funded spike must decide local recognizer/model licensing, language quality targets, memory/startup budget, sensitive-field policy, cancellation semantics, and whether connectionless handwriting is in scope.

## Required spike evidence

1. API 34, 35, and 36 prototype implementing session start/finish and stylus motion without affecting ordinary key dispatch.
2. Compatible physical stylus plus the official touch-simulation fallback for development-only testing.
3. Vietnamese/English recognition corpus with protected seller identifiers and zero unintended auto-send.
4. Latency, memory, model-size, privacy, accessibility, and editor compatibility results.
5. Explicit product approval before the competitive matrix can move from `Deferred`.
