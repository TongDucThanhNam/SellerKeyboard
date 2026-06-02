# GOAL: Core IME Foundation

> File này được tạo tự động bởi create-goal skill.
> Agent thực thi: đọc toàn bộ file này trước khi làm bất kỳ thứ gì.

---

## Objective

Tạo nền IME an toàn cho các feature sau bằng cách tách thao tác `InputConnection`, policy theo `EditorInfo`, và action dispatch khỏi Compose UI, giữ nguyên hành vi keyboard hiện có.

---

## Context

- **Lý do**: Telex, clipboard, voice typing và inline autofill đều phụ thuộc vào lifecycle IME và `InputConnection`; nếu không có foundation riêng, các feature sẽ trộn logic vào `KeyboardContent.kt` và `SellerKeyboard.kt`.
- **Ưu tiên**: correctness > speed.
- **Người thực hiện**: AI Agent, không có human review từng bước.
- **Ngày tạo**: 2026-06-02.

---

## Current State

| Item | Giá trị |
|------|---------|
| Framework / Runtime | Android IME app, Jetpack Compose |
| Language | Kotlin |
| Package manager | Gradle wrapper |
| IME service | `app/src/main/java/com/terasumi/sellerkeyboard/service/SellerKeyboard.kt` |
| Compose wrapper | `keyboard/KeyboardContentView.kt` |
| Keyboard UI/action handling | `keyboard/KeyboardContent.kt` handles page switching and direct key actions |
| InputConnection access | `keyboard/KeyboardImeActions.kt` casts `Context` to `SellerKeyboard` |
| Existing completed behavior | QWERTY keyboard, snippets keyboard, calculator keyboard, image commit, haptic feedback |
| Test setup | JUnit4 unit tests and Android instrumentation scaffold |

---

## Target State

| Item | Giá trị |
|------|---------|
| IME abstraction | `ime/` package or equivalent boundary around service-level IME operations |
| InputConnection ops | Null-safe wrapper for commit, delete, composing, finish composing and batch edit |
| EditorInfo policy | Central policy for sensitive fields, numeric fields, email/URL fields and feature enablement |
| Action dispatch | Keyboard actions routed through a small controller/dispatcher, not scattered in UI composables |
| Thứ KHÔNG thay đổi | Visual keyboard layout, snippets CRUD, calculator semantics, image commit, haptic setting |

---

## Constraints

> Đây là phần quan trọng nhất. Agent PHẢI tuân theo.

- [ ] KHÔNG implement Telex transformations in this goal.
- [ ] KHÔNG add clipboard history or clipboard bar.
- [ ] KHÔNG replace voice typing implementation.
- [ ] KHÔNG implement inline autofill.
- [ ] KHÔNG change `applicationId` or manifest service identity unless required by a verified IME refactor.
- [ ] KHÔNG move `SellerKeyboard.kt` package path if doing so risks manifest/service breakage; introducing `ime/` helpers is acceptable.
- [ ] KHÔNG remove `voiceimeutils.jar` yet.
- [ ] Giữ nguyên existing image commit behavior through `doCommitContent`.
- [ ] Nếu gặp blocker: DỪNG và mô tả blocker, KHÔNG tự workaround bằng feature scope.

---

## Success Criteria

Agent chỉ kết thúc task khi TẤT CẢ các tiêu chí sau đều pass:

- [ ] Character keys, delete, space, enter, shift and state switching still work.
- [ ] `InputConnection` calls are guarded against null connection and invalid editor state.
- [ ] Batch edit is used for multi-step editor mutations where applicable.
- [ ] `EditorInfoPolicy` or equivalent exposes `canUseTelex`, `canShowClipboard`, `canUseVoiceTyping`, `canUseInlineAutofill`.
- [ ] Password and sensitive input types are classified correctly by tests.
- [ ] Numeric/phone/date input types still switch keyboard state appropriately.
- [ ] `./gradlew :app:assembleDebug` pass.
- [ ] `./gradlew :app:testDebugUnitTest` pass.
- [ ] Không có regression so với Current State.

---

## Execution Plan

> Thực hiện theo thứ tự. Báo cáo sau mỗi bước trước khi tiếp tục.

1. Đọc `GOAL.md`, file này, `service/SellerKeyboard.kt`, `keyboard/KeyboardContent.kt`, `keyboard/KeyboardImeActions.kt`, `keyboard/KeyboardState.kt`, `keyboard/KeyboardAction.kt`.
2. Inventory các thao tác `InputConnection` hiện tại: `commitText`, `deleteSurroundingText`, `sendKeyEvent`, image commit.
3. Tạo `InputConnectionOps` hoặc abstraction tương đương, gồm null-safe commit/delete/composing/finish/batch operations.
4. Tạo `EditorInfoPolicy` hoặc equivalent cho sensitive/password/numeric/email/url behavior.
5. Tách action dispatch khỏi Compose UI ở mức tối thiểu để `KeyboardContent.kt` chỉ gửi event/action.
6. Thêm unit tests cho policy và action behavior không cần Android framework nặng.
7. Chạy `./gradlew :app:assembleDebug` và `./gradlew :app:testDebugUnitTest`.
8. Manual smoke test keyboard mở trong text field thường, password field, number field [cần xác nhận nếu không có thiết bị/emulator].

---

## Out of Scope

- Không viết Telex engine.
- Không thêm smart/candidate bar ngoài phần placeholder nếu thật sự cần cho dispatch.
- Không rewrite toàn bộ keyboard sang MVI đầy đủ nếu minimal dispatcher đã đủ.
- Không đổi theme/visual style.
- Không đổi database.

---

## References

- `app/src/main/java/com/terasumi/sellerkeyboard/service/SellerKeyboard.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/keyboard/KeyboardContent.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/keyboard/KeyboardImeActions.kt`
- `app/src/main/AndroidManifest.xml`
- Android IME guide: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- Android `InputConnection`: https://developer.android.com/reference/android/view/inputmethod/InputConnection

---

## Agent Instructions

1. Đọc toàn bộ file này trước khi làm bất kỳ thứ gì.
2. Tuân theo Constraints tuyệt đối, không có ngoại lệ.
3. Thực hiện Execution Plan theo thứ tự, từng bước một.
4. Sau mỗi bước: báo cáo ngắn gọn kết quả trước khi tiếp tục.
5. Nếu phát hiện conflict giữa Constraints và Execution Plan: ưu tiên Constraints.
6. Nếu gặp thứ gì không có trong GOAL này: DỪNG và hỏi, không tự assume.
7. Khi xong: verify toàn bộ Success Criteria và báo cáo từng item.
