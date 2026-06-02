# GOAL: Voice Typing With SpeechRecognizer

> File này được tạo tự động bởi create-goal skill.
> Agent thực thi: đọc toàn bộ file này trước khi làm bất kỳ thứ gì.

---

## Objective

Replace legacy voice typing trigger with an Android `SpeechRecognizer`-based voice typing flow that records Vietnamese speech, commits transcript through `InputConnection`, and handles lifecycle/permission failures safely.

---

## Context

- **Lý do**: Current code initializes `VoiceRecognitionTrigger` from `libs/voiceimeutils.jar`; GOAL cũ yêu cầu chuyển sang `SpeechRecognizer` để kiểm soát lifecycle, state UI và fallback.
- **Ưu tiên**: correctness > speed.
- **Người thực hiện**: AI Agent, không có human review từng bước.
- **Ngày tạo**: 2026-06-02.

---

## Current State

| Item | Giá trị |
|------|---------|
| Framework / Runtime | Android IME app |
| Language | Kotlin |
| Voice dependency | `implementation(files("libs/voiceimeutils.jar"))` |
| Current voice code | `SellerKeyboard.mVoiceRecognitionTrigger` and toolbar mic action in `KeyboardContent.kt` |
| Permission | `android.permission.RECORD_AUDIO` already declared in manifest |
| IME language | `SellerKeyboard.imeLanguage` returns `vi-VN` |
| Test setup | JUnit4 and Android instrumentation scaffold |

---

## Target State

| Item | Giá trị |
|------|---------|
| Package | `voice/` or equivalent |
| Controller | `VoiceTypingController` owns lifecycle and state transitions |
| Repository | `SpeechRecognizerRepository` or equivalent wrapper around Android `SpeechRecognizer` |
| State | Idle, requesting permission, listening, processing, result, error |
| Recognition | Prefer on-device recognition when available; fallback to system recognition service |
| Commit | Final transcript committed through `InputConnection` abstraction |
| UI | Keyboard mic button and compact voice panel/state |
| Thứ KHÔNG thay đổi | No AI rewrite, no server transcription, no clipboard changes |

---

## Constraints

> Đây là phần quan trọng nhất. Agent PHẢI tuân theo.

- [ ] Requires goal `01-core-ime-foundation_goal.md`; if no safe `InputConnection` abstraction/policy exists, DỪNG và báo dependency.
- [ ] KHÔNG send audio or transcript to any custom server.
- [ ] KHÔNG implement AI rewrite/summarize/reply.
- [ ] KHÔNG run voice typing in password/sensitive fields.
- [ ] KHÔNG crash when permission is denied.
- [ ] KHÔNG crash when no recognition service is available.
- [ ] KHÔNG remove `voiceimeutils.jar` until the new flow is verified and all references are removed safely.
- [ ] `SpeechRecognizer` listener/start/cancel/destroy calls must be lifecycle-aware and main-thread safe.
- [ ] Nếu permission request from IME context is blocked or awkward, DỪNG and document exact Android UX blocker.

---

## Success Criteria

Agent chỉ kết thúc task khi TẤT CẢ các tiêu chí sau đều pass:

- [ ] Mic action enters requesting/listening state when allowed.
- [ ] Permission denied path shows safe error state and does not crash.
- [ ] No recognition service path shows safe error state and does not crash.
- [ ] On-device recognition is used when API/service supports it; otherwise system default recognition is used.
- [ ] Final Vietnamese transcript commits into current editor through `InputConnection`.
- [ ] Cancel/finish input destroys or cancels recognizer and releases listener.
- [ ] Voice UI/state resets on field change and IME destroy.
- [ ] Voice typing is disabled in password/sensitive fields.
- [ ] Legacy `VoiceRecognitionTrigger` usage is removed only after new flow passes.
- [ ] `./gradlew :app:assembleDebug` pass.
- [ ] `./gradlew :app:testDebugUnitTest` pass.
- [ ] Không có regression so với Current State.

---

## Execution Plan

> Thực hiện theo thứ tự. Báo cáo sau mỗi bước trước khi tiếp tục.

1. Đọc `GOAL.md`, file này, `docs/goals/01-core-ime-foundation_goal.md`, `service/SellerKeyboard.kt`, `keyboard/KeyboardContent.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`.
2. Inventory all references to `VoiceRecognitionTrigger` and `voiceimeutils.jar`.
3. Design voice state machine and permission flow suitable for IME context.
4. Implement `SpeechRecognizer` wrapper with availability checks and listener registration before `startListening`.
5. Implement controller lifecycle hooks for start/cancel/destroy and field changes.
6. Wire toolbar mic action to controller and compact UI state.
7. Commit final transcript through `InputConnection` abstraction.
8. Remove legacy trigger references only after tests/build pass.
9. Add unit tests for state reducer/controller where possible; document instrumentation/manual tests for actual recognizer.
10. Run Gradle build/test gates.
11. Manual test on device/emulator with Vietnamese recognition [cần xác nhận if recognition service unavailable].

---

## Out of Scope

- Không implement offline speech model.
- Không implement server-side speech-to-text.
- Không implement AI rewrite.
- Không add clipboard or autofill behavior.
- Không rebrand app.

---

## References

- `docs/goals/01-core-ime-foundation_goal.md`
- `app/src/main/java/com/terasumi/sellerkeyboard/service/SellerKeyboard.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/keyboard/KeyboardContent.kt`
- `app/src/main/AndroidManifest.xml`
- Android `SpeechRecognizer`: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Android IME guide: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method

---

## Agent Instructions

1. Đọc toàn bộ file này trước khi làm bất kỳ thứ gì.
2. Tuân theo Constraints tuyệt đối, không có ngoại lệ.
3. Thực hiện Execution Plan theo thứ tự, từng bước một.
4. Sau mỗi bước: báo cáo ngắn gọn kết quả trước khi tiếp tục.
5. Nếu phát hiện conflict giữa Constraints và Execution Plan: ưu tiên Constraints.
6. Nếu gặp thứ gì không có trong GOAL này: DỪNG và hỏi, không tự assume.
7. Khi xong: verify toàn bộ Success Criteria và báo cáo từng item.
