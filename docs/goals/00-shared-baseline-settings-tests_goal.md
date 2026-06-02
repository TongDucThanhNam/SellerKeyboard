# GOAL: Shared Baseline, Settings Contract, Test Gates

> File này được tạo tự động bởi create-goal skill.
> Agent thực thi: đọc toàn bộ file này trước khi làm bất kỳ thứ gì.

---

## Objective

Thiết lập baseline dùng chung cho các feature IME tiếp theo: settings contract, test gates, manual QA matrix và quy ước state, nhưng không triển khai Telex, clipboard, voice typing hoặc inline autofill.

---

## Context

- **Lý do**: GOAL cũ có settings và test plan nằm xen giữa nhiều feature. Tách phần nền này giúp mỗi feature sau có acceptance gate rõ ràng.
- **Ưu tiên**: correctness > speed.
- **Người thực hiện**: AI Agent, không có human review từng bước.
- **Ngày tạo**: 2026-06-02.

---

## Current State

| Item | Giá trị |
|------|---------|
| Framework / Runtime | Android IME app |
| Language | Kotlin |
| Package manager | Gradle wrapper |
| Main module | `app/` |
| Existing settings | Haptic setting stored through `SellerKeyboard.PREFS_NAME` / `PREF_HAPTIC_ENABLED` |
| Existing tests | JUnit4 and AndroidJUnit4 configured; current tests include calculator/layout/state tests |
| Entry points | `service/SellerKeyboard.kt`, `keyboard/KeyboardContent.kt`, `HomeActivity.kt` |
| Config files | `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties` |

---

## Target State

| Item | Giá trị |
|------|---------|
| Settings contract | Central `KeyboardSettingsRepository` or equivalent API for feature flags |
| Persistence | Preserve existing haptic preference semantics; DataStore is optional and only if scoped |
| Test gates | Documented unit, instrumentation and manual QA gates reused by all feature goals |
| Feature defaults | `vietnameseInputEnabled`, `telexVariant`, `voiceTypingEnabled`, `clipboardHistoryEnabled`, `clipboardAutoDeleteDays`, `inlineAutofillEnabled` |
| Thứ KHÔNG thay đổi | `applicationId`, snippet CRUD, calculator, image commit, keyboard layout behavior |

---

## Constraints

> Đây là phần quan trọng nhất. Agent PHẢI tuân theo.

- [ ] KHÔNG implement Telex engine.
- [ ] KHÔNG implement clipboard storage/history.
- [ ] KHÔNG replace legacy voice trigger in this goal.
- [ ] KHÔNG implement inline autofill flow in this goal.
- [ ] KHÔNG migrate snippets database or delete `DatabaseHelper`.
- [ ] KHÔNG change `applicationId`.
- [ ] KHÔNG add dependencies unless the settings persistence choice requires it and `./gradlew :app:assembleDebug` verifies it.
- [ ] Nếu gặp blocker: DỪNG và mô tả blocker, KHÔNG tự workaround bằng cách kéo feature scope vào goal này.

---

## Success Criteria

Agent chỉ kết thúc task khi TẤT CẢ các tiêu chí sau đều pass:

- [ ] Có một settings contract rõ ràng cho các feature flags trong code hoặc tài liệu thực thi nội bộ.
- [ ] Haptic setting hiện có vẫn đọc/ghi đúng như trước.
- [ ] Có danh sách test command bắt buộc cho các goal sau.
- [ ] Có manual QA matrix cho portrait/landscape và các app chính: Messenger, Zalo, Shopee Seller, Chrome, Gmail.
- [ ] `./gradlew :app:assembleDebug` pass.
- [ ] `./gradlew :app:testDebugUnitTest` pass.
- [ ] `./gradlew :app:lintDebug` pass hoặc lint failures được phân loại rõ.
- [ ] Không có regression so với Current State.

---

## Execution Plan

> Thực hiện theo thứ tự. Báo cáo sau mỗi bước trước khi tiếp tục.

1. Đọc `GOAL.md`, file này, `docs/eragear-keyboard-copilot-plan.md`, `app/build.gradle.kts`, `service/SellerKeyboard.kt`, `keyboard/KeyboardContent.kt`.
2. Inventory settings hiện có, đặc biệt `PREF_HAPTIC_ENABLED` và UI/settings entry hiện tại.
3. Thiết kế settings contract tối thiểu cho feature flags mà không bắt feature sau phải implement ngay.
4. Nếu cần code, thêm repository/API nhỏ và migrate haptic access qua API đó trong phạm vi an toàn.
5. Cập nhật hoặc tạo test cho settings contract nếu có code mới.
6. Ghi manual QA matrix dùng chung cho các goal sau.
7. Chạy `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`.

---

## Out of Scope

- Không implement bất kỳ feature mới nào.
- Không tạo screen settings lớn nếu chỉ cần repository/API.
- Không đổi database engine.
- Không rebrand app.
- Không thêm cloud sync hoặc Convex integration.

---

## References

- `GOAL.md`
- `docs/eragear-keyboard-copilot-plan.md`
- `app/src/main/java/com/terasumi/sellerkeyboard/service/SellerKeyboard.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/keyboard/KeyboardContent.kt`
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
