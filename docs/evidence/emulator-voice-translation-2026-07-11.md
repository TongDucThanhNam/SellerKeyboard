# API 36 voice and translation attribution — 2026-07-11

The canonical zero-touch API 36 gate exercises production voice and translation controllers with
exact package-UID network and log boundaries.

Voice grants microphone permission temporarily, starts the configured
`com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService`,
observes real on-device `Listening`, cancels, and verifies restored `Idle`. SellerKeyboard UID
`10216` and provider UID `10150` each record supported `0/0` RX/TX-byte deltas and zero occurrences
of the controlled editor-context canary. This headless path does not synthesize a spoken transcript.

Translation deletes the VI→EN model, performs an explicit ML Kit download/translation, verifies
that Preview retains the exact controlled original, cancels, and restores Idle. SellerKeyboard UID
`10216` and Google Play services UID `10145` are both measured. An initial run found four full-source
occurrences emitted by ML Kit 17.0.3's bundled native translator. SellerKeyboard now uses Android's
API 30+ process-minimum native logging API to silence logs only around the translation task and
restore the prior threshold on success, failure, cancellation, or teardown. API 26–29 translation
is fail-closed because that supported platform API is unavailable.

The corrected debug-controller run and a separate non-debuggable, minified benchmark-variant run
both reach verified Preview and record zero translation-canary occurrences for the IME and Google
Play services UIDs. Evidence is retained under:

- `verification/emulator-connected/latest/voice-translation-attribution/`
- `verification/emulator-connected/latest/translation-release-attribution/`

The canonical run also drives the real Compose translation surface EN→VI against a neutral editor
owned by a distinct package. It selects `Thank you for your order` in that editor, deletes the
target model, waits for the real translated Preview, and proves the visible preview matches the
controller SHA-256. The first reviewed Insert preserves the selected English text and appends the
Vietnamese result on a new line. A fresh session produces the same preview; Replace changes the
selection to the translated text, and Restore returns the exact original. The controller finishes
Idle, and neither the controlled source nor the extracted visible result occurs in the scoped IME
or Google Play services logs. Evidence is under
`verification/emulator-connected/latest/translation-ui/`.

This execution found and fixed two interaction defects: a platform `OutlinedTextField` could not
reliably accept input from inside the IME itself, and `commitText` made Insert indistinguishable
from Replace while a source selection was active. Translation now uses local state-hoisted in-IME
entry and a dedicated atomic insertion action at the selection end.

Google's official terms state that ML Kit input/output processing stays on-device, while the SDK
may contact Google for models/configuration and sends operational metrics. The final Data safety
declaration must include those metrics. Sources: [ML Kit Terms & Privacy](https://developers.google.com/ml-kit/terms)
and [Android data disclosure guidance](https://developers.google.com/ml-kit/android-data-disclosure).

This closes controlled emulator provider attribution and both translation directions' automated
review flow. Physical microphone/result/error flows, physical translation/model-storage acceptance,
provider-network acceptance, named third-party apps, and independent capture remain release work.
