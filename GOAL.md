# GOAL.md

> File này được tạo tự động bởi create-goal skill.
> Agent thực thi: đọc file này trước, sau đó chỉ chọn một goal chi tiết trong `docs/goals/`.

---

## Objective

Tách GOAL cũ của SellerKeyboard thành nhiều goal độc lập, nhỏ hơn, có thứ tự phụ thuộc rõ ràng để một AI Agent có thể thực thi từng phần mà không kéo toàn bộ roadmap vào cùng một task.

---

## Context

- **Lý do**: GOAL cũ gom Core IME, Telex, clipboard, voice typing, inline autofill, settings và test plan vào một file 1213 dòng nên quá rộng cho một agent chạy end-to-end an toàn.
- **Ưu tiên**: correctness > speed.
- **Người thực hiện**: AI Agent, không có human review từng bước.
- **Ngày tạo**: 2026-06-02.

---

## Current State

| Item | Giá trị |
|------|---------|
| Framework / Runtime | Android IME app, Jetpack Compose + Material 3 |
| Language | Kotlin, một scaffold TypeScript/Convex ở root [ước lượng: ngoài scope Android MVP] |
| Package manager | Gradle wrapper cho Android, Bun cho scaffold root |
| Android module | `app/` |
| Dependencies chính | Android Gradle Plugin 9.1.0, Kotlin 2.2.10, Compose BOM 2026.02.01, Material3 1.4.0, Glide 4.16.0, Gson 2.13.2, exp4j 0.4.8 |
| Entry point | `app/src/main/java/com/terasumi/sellerkeyboard/service/SellerKeyboard.kt`, `app/src/main/java/com/terasumi/sellerkeyboard/HomeActivity.kt` |
| Keyboard UI | `keyboard/KeyboardContent.kt`, `keyboard/Keyboard.kt`, `keyboard/Keycap.kt` |
| Persistence | Raw `SQLiteOpenHelper` for snippets in `database/` |
| Test setup | JUnit4 unit tests and AndroidJUnit4 instrumentation tests are configured |
| Existing broad docs | `docs/eragear-keyboard-copilot-plan.md` and old GOAL content split into the files below |

---

## Split Goals

Thực thi theo thứ tự này. Mỗi goal có boundary riêng và không tự động bao gồm các goal phía sau.

| Order | Goal file | Scope |
|------:|-----------|-------|
| 00 | `docs/goals/00-shared-baseline-settings-tests_goal.md` | Baseline, settings contract, test gates, shared manual QA matrix |
| 01 | `docs/goals/01-core-ime-foundation_goal.md` | IME service boundary, InputConnection wrapper, sensitive field policy, action dispatch |
| 02 | `docs/goals/02-vietnamese-telex_goal.md` | Pure Kotlin Telex engine and IME integration |
| 03 | `docs/goals/03-seller-clipboard_goal.md` | Seller clipboard history, classifier, privacy, keyboard bar |
| 04 | `docs/goals/04-voice-typing-speechrecognizer_goal.md` | Replace legacy voice trigger with `SpeechRecognizer` flow |
| 05 | `docs/goals/05-inline-autofill_goal.md` | Android inline autofill suggestions for IME |

---

## Global Constraints

> Các constraint này áp dụng cho mọi goal chi tiết.

- [ ] KHÔNG thay đổi `applicationId`: giữ `com.terasumi.sellerkeyboard`.
- [ ] KHÔNG rebrand sang Eragear trong các goal này; rebrand nằm sau các feature track.
- [ ] KHÔNG triển khai AI reply/rewrite, ML Kit smart reply, translate, GIF/sticker, swipe typing, cloud sync, multi-device sync hoặc language-model word suggestion.
- [ ] KHÔNG gửi clipboard, nội dung gõ hoặc voice transcript ra server nếu goal không yêu cầu rõ ràng và chưa có privacy review.
- [ ] KHÔNG upgrade dependency không liên quan trực tiếp đến goal đang làm.
- [ ] KHÔNG xóa hoặc rewrite snippet CRUD, calculator keyboard, image commit, haptic behavior nếu goal không nêu rõ.
- [ ] Mọi thao tác với editor phải đi qua `InputConnection` hoặc abstraction quanh `InputConnection`, không hack bằng raw key event trừ trường hợp Enter/fallback đã được verify.
- [ ] Nếu gặp blocker trong permission, Android API behavior, IME lifecycle hoặc data migration: DỪNG và mô tả blocker, không tự workaround làm rộng scope.

---

## Global Success Criteria

Agent chỉ kết thúc một goal chi tiết khi tiêu chí của goal đó pass và không làm hỏng baseline sau:

- [ ] `./gradlew :app:assembleDebug` pass.
- [ ] `./gradlew :app:testDebugUnitTest` pass.
- [ ] `./gradlew :app:lintDebug` pass hoặc mọi lint failure còn lại được ghi rõ là pre-existing.
- [ ] Keyboard vẫn mở được trong text field thường.
- [ ] Snippet keyboard vẫn gửi được snippet text.
- [ ] Calculator keyboard vẫn gửi được expression/result.
- [ ] Haptic setting hiện có vẫn hoạt động.
- [ ] Không có regression rõ ràng trong portrait/landscape keyboard layout.

---

## Out of Scope

- Không triển khai code trong bước tách goal này.
- Không viết migration Play Store/rebrand.
- Không setup CI/CD mới.
- Không đổi database engine trừ khi một goal chi tiết yêu cầu trực tiếp.
- Không xóa scaffold Convex root.

---

## References

- `docs/goals/00-shared-baseline-settings-tests_goal.md`
- `docs/goals/01-core-ime-foundation_goal.md`
- `docs/goals/02-vietnamese-telex_goal.md`
- `docs/goals/03-seller-clipboard_goal.md`
- `docs/goals/04-voice-typing-speechrecognizer_goal.md`
- `docs/goals/05-inline-autofill_goal.md`
- `docs/eragear-keyboard-copilot-plan.md`
- Android IME guide: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- Android `InputConnection`: https://developer.android.com/reference/android/view/inputmethod/InputConnection
- Android inline autofill for IMEs: https://developer.android.com/identity/autofill/ime-autofill
- Android `SpeechRecognizer`: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Android 10 clipboard privacy: https://developer.android.com/about/versions/10/privacy/changes
- Secure clipboard handling: https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling

---

## Agent Instructions

1. Đọc toàn bộ `GOAL.md` này trước khi làm bất kỳ thứ gì.
2. Chọn đúng một file goal trong `docs/goals/` và chỉ thực thi scope của file đó.
3. Tuân thủ Global Constraints và Constraints riêng của goal; nếu conflict, ưu tiên constraint chặt hơn.
4. Thực hiện Execution Plan theo thứ tự.
5. Sau mỗi bước: báo cáo ngắn gọn kết quả trước khi tiếp tục.
6. Khi xong: verify Success Criteria của goal và Global Success Criteria.
7. Nếu phát hiện task cần một goal khác để hoàn thành: DỪNG và báo cáo dependency, không tự mở rộng scope.
