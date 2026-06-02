# GOAL: Inline Autofill Suggestions

> File này được tạo tự động bởi create-goal skill.
> Agent thực thi: đọc toàn bộ file này trước khi làm bất kỳ thứ gì.

---

## Objective

Integrate Android inline autofill suggestions into SellerKeyboard so API 30+ can show provider-supplied suggestions in the keyboard suggestion strip without exposing private suggestion contents to the IME.

---

## Context

- **Lý do**: Android 11+ lets IMEs display autofill suggestions inline. SellerKeyboard should behave like a modern keyboard while respecting sensitive autofill data.
- **Ưu tiên**: correctness > speed.
- **Người thực hiện**: AI Agent, không có human review từng bước.
- **Ngày tạo**: 2026-06-02.

---

## Current State

| Item | Giá trị |
|------|---------|
| Framework / Runtime | Android IME app |
| Language | Kotlin |
| IME service | `service/SellerKeyboard.kt` |
| Input method XML | `app/src/main/res/xml/custom_method.xml` has `settingsActivity` and a voice subtype; no inspected inline suggestion support attribute yet |
| Inline autofill status | No inspected `autofill/` package or inline suggestion implementation |
| Keyboard UI | Toolbar + pages in `keyboard/KeyboardContent.kt`; no candidate/suggestion strip yet |
| Target API | `compileSdk = 36`, `minSdk = 26` |

---

## Target State

| Item | Giá trị |
|------|---------|
| Package | `autofill/` or equivalent |
| IME XML | `android:supportsInlineSuggestions="true"` when supported |
| Request flow | Implement `InputMethodService.onCreateInlineSuggestionsRequest()` for API 30+ |
| Response flow | Implement `onInlineSuggestionsResponse()` and inflate provider suggestions safely |
| UI | Smart/candidate bar renders inline suggestion views with stable sizing |
| Degrade path | API < 30 and no-provider cases keep keyboard behavior unchanged |
| Thứ KHÔNG thay đổi | No custom password manager, no credential storage, no reading/logging suggestion text |

---

## Constraints

> Đây là phần quan trọng nhất. Agent PHẢI tuân theo.

- [ ] Requires goal `01-core-ime-foundation_goal.md`; if no safe IME service/policy boundary exists, DỪNG và báo dependency.
- [ ] KHÔNG inspect, log, store or transform autofill suggestion private data.
- [ ] KHÔNG implement an AutofillService or password manager.
- [ ] KHÔNG require API 30 at runtime; API 26-29 must degrade gracefully.
- [ ] KHÔNG show fake suggestions when provider returns none.
- [ ] KHÔNG break normal keyboard, snippets or calculator pages.
- [ ] KHÔNG expose inline suggestion content in screenshots/logcat/debug UI.
- [ ] If Android API method signatures differ from assumptions, verify official docs/source first and DỪNG if unclear.

---

## Success Criteria

Agent chỉ kết thúc task khi TẤT CẢ các tiêu chí sau đều pass:

- [ ] Input method XML declares inline suggestion support correctly.
- [ ] API 30+ request flow returns an `InlineSuggestionsRequest` with bounded count and presentation specs.
- [ ] API 30+ response flow renders suggestions by inflating provider-supplied views.
- [ ] API 26-29 builds and runs without inline-autofill crashes.
- [ ] Empty/no-provider response does not show stale suggestions.
- [ ] Tapping suggestion delegates to platform autofill behavior.
- [ ] Keyboard does not log/store/read suggestion text.
- [ ] Candidate/smart bar layout does not clip keyboard rows in portrait or landscape.
- [ ] Manual test with Chrome/Gmail/password-manager flow passes [cần xác nhận because provider/device setup is external].
- [ ] `./gradlew :app:assembleDebug` pass.
- [ ] `./gradlew :app:testDebugUnitTest` pass.
- [ ] Không có regression so với Current State.

---

## Execution Plan

> Thực hiện theo thứ tự. Báo cáo sau mỗi bước trước khi tiếp tục.

1. Đọc `GOAL.md`, file này, `docs/goals/01-core-ime-foundation_goal.md`, `service/SellerKeyboard.kt`, `keyboard/KeyboardContent.kt`, `app/src/main/res/xml/custom_method.xml`, `app/src/main/AndroidManifest.xml`.
2. Verify official Android inline autofill APIs for current compileSdk.
3. Add inline suggestion support attribute to input method XML with API-safe handling.
4. Implement request creation in IME service for API 30+ with presentation specs matching keyboard bar dimensions.
5. Implement response handling in service/controller and expose state to Compose safely.
6. Add candidate/smart bar UI with stable height and no content logging.
7. Ensure API < 30 path compiles and no-ops.
8. Add tests where possible for state clearing, empty response behavior and API guards; document manual test gaps.
9. Run Gradle build/test gates.
10. Manual test with an autofill provider/password manager [cần xác nhận device/provider availability].

---

## Out of Scope

- Không implement an AutofillService.
- Không store credentials.
- Không integrate Credential Manager.
- Không implement general word suggestions.
- Không implement clipboard/voice/Telex.
- Không rebrand app.

---

## References

- `docs/goals/01-core-ime-foundation_goal.md`
- `app/src/main/java/com/terasumi/sellerkeyboard/service/SellerKeyboard.kt`
- `app/src/main/res/xml/custom_method.xml`
- Android inline autofill for IMEs: https://developer.android.com/identity/autofill/ime-autofill
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
