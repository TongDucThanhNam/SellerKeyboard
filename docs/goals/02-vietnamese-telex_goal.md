# GOAL: Vietnamese Telex Input

> File này được tạo tự động bởi create-goal skill.
> Agent thực thi: đọc toàn bộ file này trước khi làm bất kỳ thứ gì.

---

## Objective

Implement gõ tiếng Việt Telex ổn định cho SellerKeyboard bằng pure Kotlin engine và IME composing integration, không thêm autocorrect hoặc suggestion model.

---

## Context

- **Lý do**: SellerKeyboard cần gõ tiếng Việt tốt cho seller; GOAL cũ xếp Telex là ưu tiên cao trong Core IME.
- **Ưu tiên**: correctness > speed.
- **Người thực hiện**: AI Agent, không có human review từng bước.
- **Ngày tạo**: 2026-06-02.

---

## Current State

| Item | Giá trị |
|------|---------|
| Framework / Runtime | Android IME app |
| Language | Kotlin |
| Keyboard state | `keyboard/KeyboardState.kt` handles caps/numbers/symbols |
| Key actions | `KeyboardAction.KeyPress`, `Delete`, `Space`, `Enter`, `Shift` |
| Telex status | Chưa có Vietnamese engine code trong repo |
| Sensitive policy | Password detection exists in keyboard path; should be centralized by goal 01 |
| Test setup | JUnit4 unit tests available |

---

## Target State

| Item | Giá trị |
|------|---------|
| Package | `vietnamese/` or equivalent clean package |
| Engine | Pure Kotlin `TelexEngine` with no Android dependencies |
| Processor | State holder mapping raw syllable to composing text through `InputConnection` abstraction |
| Settings | `vietnameseInputEnabled`, `telexVariant` with standard default |
| UI | Clear VI/EN toggle or state indicator in keyboard toolbar/candidate area |
| Thứ KHÔNG thay đổi | No autocorrect, no LM suggestion, no cloud calls, no snippet/calculator changes |

---

## Constraints

> Đây là phần quan trọng nhất. Agent PHẢI tuân theo.

- [ ] Requires goal `01-core-ime-foundation_goal.md`; if no safe `InputConnection` abstraction/policy exists, DỪNG và báo dependency.
- [ ] KHÔNG copy source code từ UniKey, ibus, fcitx, FlorisBoard, AnySoftKeyboard, OpenBoard hoặc repo GPL khác.
- [ ] KHÔNG implement general autocorrect.
- [ ] KHÔNG implement word prediction.
- [ ] KHÔNG send typed text to server.
- [ ] KHÔNG enable Telex in password fields, number-only fields, phone fields or other sensitive input types.
- [ ] KHÔNG break uppercase/lowercase behavior and existing shift semantics.
- [ ] Nếu gặp blocker về composing mismatch hoặc editor incompatibility: DỪNG và mô tả app/field cụ thể.

---

## Success Criteria

Agent chỉ kết thúc task khi TẤT CẢ các tiêu chí sau đều pass:

- [ ] Shape keys pass: `aa->â`, `aw->ă`, `ee->ê`, `oo->ô`, `ow->ơ`, `uw->ư`, `dd->đ`, uppercase equivalents.
- [ ] Tone keys pass: `as->á`, `af->à`, `ar->ả`, `ax->ã`, `aj->ạ`.
- [ ] `z` removes tone for current syllable.
- [ ] Last tone wins: `asf->à`, `afj->ạ`.
- [ ] Shape + tone words pass: `aas->ấ`, `aaf->ầ`, `awr->ẳ`, `ees->ế`, `oos->ố`, `ows->ớ`, `uws->ứ`.
- [ ] Common words pass: `ddawng->đăng`, `nhaan->nhân`, `tieesng->tiếng`, `muoons->muốn`, `thuwowng->thương`, `khachs->khách`, `hangf->hàng`, `quas->quá`, `gias->giá`.
- [ ] Escape/double-key behavior is deterministic, including `herr->her` and similar cases.
- [ ] Space, punctuation and Enter commit composing text cleanly.
- [ ] Backspace works in composing and after commit without crash.
- [ ] Telex disabled in password/sensitive fields.
- [ ] Unit test matrix has at least 100 cases [ước lượng: include engine, processor, boundary, backspace, uppercase].
- [ ] `./gradlew :app:assembleDebug` pass.
- [ ] `./gradlew :app:testDebugUnitTest` pass.
- [ ] Không có regression so với Current State.

---

## Execution Plan

> Thực hiện theo thứ tự. Báo cáo sau mỗi bước trước khi tiếp tục.

1. Đọc `GOAL.md`, file này, `docs/goals/01-core-ime-foundation_goal.md`, `keyboard/KeyboardAction.kt`, `keyboard/KeyboardContent.kt`, and the IME abstraction created by goal 01.
2. Define Telex rules in tests first: shape keys, tone keys, tone placement, boundary, escape, backspace, uppercase.
3. Implement pure Kotlin engine that converts raw syllable state to display syllable.
4. Implement processor/controller that owns raw syllable, committed state, composing lifecycle and reset behavior on selection/focus/input type change.
5. Wire key press/delete/space/punctuation/enter through the processor only when policy allows Telex.
6. Add VI/EN toggle or visible state indicator with setting persistence.
7. Run unit tests until the full matrix passes.
8. Run Gradle build/test gates.
9. Manual test Messenger, Zalo, Shopee Seller, Chrome, Gmail [cần xác nhận nếu không có thiết bị/emulator].

---

## Out of Scope

- Không implement clipboard.
- Không implement voice typing.
- Không implement inline autofill.
- Không implement AI/smart reply/autocorrect.
- Không migrate database.
- Không rebrand app.

---

## References

- `docs/goals/01-core-ime-foundation_goal.md`
- `app/src/main/java/com/terasumi/sellerkeyboard/keyboard/KeyboardAction.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/keyboard/KeyboardState.kt`
- Android `InputConnection`: https://developer.android.com/reference/android/view/inputmethod/InputConnection
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
