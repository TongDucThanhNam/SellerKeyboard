# GOAL: Seller Clipboard

> File này được tạo tự động bởi create-goal skill.
> Agent thực thi: đọc toàn bộ file này trước khi làm bất kỳ thứ gì.

---

## Objective

Implement clipboard riêng cho seller với history, pin/delete/clear, phân loại nội dung phổ biến và keyboard bar để paste nhanh, tuân thủ privacy của Android clipboard.

---

## Context

- **Lý do**: Seller cần paste nhanh số điện thoại, link, mã vận đơn, giá và nội dung bán hàng; GOAL cũ xếp clipboard là ưu tiên cao.
- **Ưu tiên**: correctness > speed.
- **Người thực hiện**: AI Agent, không có human review từng bước.
- **Ngày tạo**: 2026-06-02.

---

## Current State

| Item | Giá trị |
|------|---------|
| Framework / Runtime | Android IME app |
| Language | Kotlin |
| Persistence | Raw `SQLiteOpenHelper`, currently snippets table only |
| Existing clipboard status | README roadmap claims clipboard support, but current inspected Kotlin packages do not contain a dedicated `clipboard/` implementation [ước lượng] |
| Keyboard UI | `KeyboardContent.kt` page switching for keyboard/snippets/calculator |
| Sensitive field handling | Password field detection exists in keyboard path; should be centralized by goal 01 |
| Permissions | Manifest already has broad media/audio/vibrate permissions; no extra permission should be needed for normal clipboard access |

---

## Target State

| Item | Giá trị |
|------|---------|
| Package | `clipboard/` or equivalent |
| Repository | `ClipboardRepository` exposing observe/list/save/pin/delete/clear |
| Data sources | System clipboard reader scoped to active IME and local storage for history |
| Data model | `ClipboardItem`, `ClipboardItemType`, `ClipboardSource`, timestamps, pinned flag |
| Classifier | Phone number, URL, price, tracking code, plain text |
| UI | `ClipboardBar` or candidate strip integrated into keyboard, hidden in sensitive fields |
| Settings | `clipboardHistoryEnabled`, `clipboardAutoDeleteDays` |
| Thứ KHÔNG thay đổi | Snippet CRUD table behavior, calculator behavior, image commit behavior |

---

## Constraints

> Đây là phần quan trọng nhất. Agent PHẢI tuân theo.

- [ ] Requires goal `01-core-ime-foundation_goal.md`; if no sensitive field policy exists, DỪNG và báo dependency.
- [ ] KHÔNG read clipboard in background.
- [ ] KHÔNG show clipboard in password/sensitive fields.
- [ ] KHÔNG store duplicate item consecutively.
- [ ] KHÔNG store or render obviously sensitive values if policy marks the field/content sensitive [cần xác nhận exact heuristic].
- [ ] KHÔNG break existing snippets database; if adding tables, use additive migration only.
- [ ] KHÔNG add cloud sync.
- [ ] KHÔNG send clipboard content to server.
- [ ] KHÔNG require permissions unrelated to clipboard.
- [ ] Nếu gặp blocker về Android clipboard restrictions: DỪNG và mô tả OS/API behavior cụ thể.

---

## Success Criteria

Agent chỉ kết thúc task khi TẤT CẢ các tiêu chí sau đều pass:

- [ ] Clipboard history can save and observe recent text items while keyboard is active.
- [ ] Pin/delete/clear works.
- [ ] Pinned items are ordered before unpinned items.
- [ ] Duplicate consecutive item is ignored.
- [ ] Classifier passes: `0912345678 -> PhoneNumber`, `https://shopee.vn/... -> Url`, `250.000đ -> Price`, `SPXVN12345678 -> TrackingCode`, `hello world -> PlainText`.
- [ ] Clipboard bar renders in normal text field.
- [ ] Clipboard bar is hidden in password/sensitive field.
- [ ] Tapping clipboard item commits text through `InputConnection`.
- [ ] Clipboard history can be disabled by setting.
- [ ] Auto-delete setting is represented and tested [ước lượng: implementation may be scheduled job or cleanup on read/write].
- [ ] `./gradlew :app:assembleDebug` pass.
- [ ] `./gradlew :app:testDebugUnitTest` pass.
- [ ] Không có regression so với Current State.

---

## Execution Plan

> Thực hiện theo thứ tự. Báo cáo sau mỗi bước trước khi tiếp tục.

1. Đọc `GOAL.md`, file này, `docs/goals/01-core-ime-foundation_goal.md`, `database/DatabaseHelper.kt`, `database/SnippetDao.kt`, `keyboard/KeyboardContent.kt`, `keyboard/SnippetsKeyboard.kt`.
2. Decide storage path: additive SQLite table using current helper, or isolated persistence layer that does not disrupt snippets.
3. Define `ClipboardItem` model, classifier and repository tests first.
4. Implement classifier with deterministic regex/tests.
5. Implement local data source with duplicate suppression, pin ordering and delete/clear.
6. Implement system clipboard data source scoped to active IME lifecycle only.
7. Wire `ClipboardBar` into keyboard UI behind sensitive field policy and settings.
8. Commit selected item text via `InputConnection` abstraction.
9. Add tests for classifier, repository ordering, duplicate behavior and sensitive field policy.
10. Run Gradle build/test gates.
11. Manual test clipboard behavior on Android 10+ and Android 13+ [cần xác nhận nếu device/emulator unavailable].

---

## Out of Scope

- Không implement snippets categories/tags.
- Không implement cloud sync or Convex.
- Không implement AI classification.
- Không handle images in clipboard history in MVP unless required by existing snippet/image path.
- Không rebrand app.

---

## References

- `docs/goals/01-core-ime-foundation_goal.md`
- `app/src/main/java/com/terasumi/sellerkeyboard/database/DatabaseHelper.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/database/SnippetsTable.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/keyboard/KeyboardContent.kt`
- Android 10 clipboard privacy: https://developer.android.com/about/versions/10/privacy/changes
- Secure clipboard handling: https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling

---

## Agent Instructions

1. Đọc toàn bộ file này trước khi làm bất kỳ thứ gì.
2. Tuân theo Constraints tuyệt đối, không có ngoại lệ.
3. Thực hiện Execution Plan theo thứ tự, từng bước một.
4. Sau mỗi bước: báo cáo ngắn gọn kết quả trước khi tiếp tục.
5. Nếu phát hiện conflict giữa Constraints và Execution Plan: ưu tiên Constraints.
6. Nếu gặp thứ gì không có trong GOAL này: DỪNG và hỏi, không tự assume.
7. Khi xong: verify toàn bộ Success Criteria và báo cáo từng item.
