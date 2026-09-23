# GOAL: Convex Backend Auth and Snippet Backup

> File này được tạo tự động bởi create-goal skill.
> Agent thực thi: đọc toàn bộ file này trước khi làm bất kỳ thứ gì.
> Scope của file này là tạo backend integration goal mới, không thay thế `GOAL.md` roadmap hiện tại.

---

## Objective

Tích hợp Convex backend vào SellerKeyboard để người dùng có thể đăng nhập và sao lưu/khôi phục thủ công dữ liệu snippet text lên cloud, không dùng realtime sync trong MVP, đồng thời giữ kiến trúc đủ mở để sau này thêm AI features.

---

## Context

- **Lý do**: App hiện lưu snippet trong SQLite local. Người dùng cần đăng nhập và backup dữ liệu snippet để tránh mất dữ liệu và làm nền cho các tính năng cloud/AI sau này.
- **Ưu tiên**: correctness > speed.
- **Người thực hiện**: AI Agent, không có human review từng bước.
- **Ngày tạo**: 2026-06-17.
- **Auth note quan trọng**: User đã explicitly approve bỏ Convex Auth native Android vì docs không có đường Kotlin rõ ràng. Implement MVP bằng basic self-auth trong Convex: email/password, server-issued `sessionToken`, Android lưu token local, mọi backup/restore truyền token và backend tự enforce ownership. Không tự chuyển sang Auth0/Clerk/WorkOS.

---

## Current State

| Item | Giá trị |
|------|---------|
| App runtime | Android IME app, Jetpack Compose + Material 3 |
| Language | Kotlin cho Android; TypeScript scaffold cho Convex ở root |
| Package manager | Gradle wrapper cho Android; Bun/npm-compatible tooling cho root |
| Android module | `app/` |
| Android dependencies chính | AGP 9.1.0, Kotlin 2.2.10, Compose BOM 2026.02.01, Material3 1.4.0, Gson 2.13.2, SQLiteOpenHelper |
| Existing backend scaffold | `package.json` có `convex: ^1.27.0`; `convex/_generated/*` tồn tại |
| Convex schema/functions | Chưa có `convex/schema.ts` hoặc function app-specific cho self-auth/snippet backup |
| Android Convex integration | Chưa có `dev.convex:android-convexmobile`, chưa có `ConvexClient`, chưa có repository dùng Convex |
| Auth state | Chưa có login/logout UI hoặc authenticated backend calls |
| Snippet storage | Raw SQLite qua `DatabaseHelper`, `SnippetsTable`, `SnippetsDao` |
| Snippet model | `Snippets(id: Int, title: String, content: String, imageUrls: List<String>)` |
| Snippet CRUD entry points | `HomeScreen.kt`, `AddSnippetScreen.kt`, `EditSnippetScreen.kt`, `SnippetsKeyboard.kt` |
| Existing Convex URL | `R.string.convex_url = https://wandering-eel-278.convex.cloud` trong `values*` |
| Network permission | `android.permission.INTERNET` đã có trong `AndroidManifest.xml` |
| Test setup | JVM unit tests, Android lint, debug assemble |
| Worktree note | Repo có thể đang dirty từ các goals trước; không revert thay đổi không thuộc task |

---

## Target State

| Item | Giá trị |
|------|---------|
| Backend | Convex project có schema và functions cho user-owned snippet backup |
| Auth | Login/logout/register trong app bằng basic self-auth trên Convex, không dùng Convex Auth |
| Android client | Một app-lifetime Convex client trong `MyApplication` hoặc data-layer provider |
| Data layer | `backend/` hoặc `sync/` package tách rõ Convex DTO/repository khỏi UI và SQLite DAO |
| Backup mode | Manual backup/restore only; không realtime background sync |
| Source of truth | SQLite local vẫn là source of truth cho keyboard/snippet CRUD trong MVP |
| Ownership | Mọi snippet backup gắn với user từ self-auth session token đã validate server-side |
| Future scale | Schema/function boundaries đủ mở để thêm AI actions sau này nhưng không implement AI trong goal này |
| Thứ KHÔNG thay đổi | IME service identity, keyboard behavior, snippet CRUD UX, calculator, Telex, clipboard, voice, inline autofill |

---

## Proposed Architecture

### Backend Files

- `convex/schema.ts`
  - Define user-owned snippet backup table.
  - Recommended MVP table: `snippetBackups`.
  - Fields: `ownerToken: string`, `clientId: string`, `title: string`, `content: string`, `imageUrls: v.array(v.string())`, `deleted: boolean`, `updatedAt: number`, `schemaVersion: number`.
  - Indexes: `by_owner`, `by_owner_clientId`.
- `convex/auth.ts`
  - Basic self-auth functions.
  - Required functions:
    - `auth:register`
    - `auth:login`
    - `auth:logout`
    - `auth:whoAmI`
  - Store password hashes only; never store raw passwords.
  - Store hashed session tokens server-side and return the raw session token only once to Android.
- `convex/snippets.ts`
  - Mutations/queries for manual backup and restore.
  - Required functions:
    - `snippets:backupBatch`
    - `snippets:listBackup`
    - `snippets:deleteBackupItem` or deletion support in `backupBatch`
    - `snippets:whoAmI` or equivalent session smoke test.

### Android Files

- `app/build.gradle.kts`
  - Add Kotlin serialization plugin and Convex Android dependencies only.
- `MyApplication.kt`
  - Own one app-lifetime Convex client or explicitly documented provider.
- `backend/` or `sync/`
  - `ConvexClientProvider`
  - `ConvexAuthRepository` for self-auth/session state
  - `SnippetBackupRepository`
  - DTOs annotated with `@Serializable`, using `@SerialName` where needed.
- UI integration
  - Add a small account/backup section in main app screen or settings.
  - Manual actions: Login, Logout, Backup now, Restore from backup.
  - No keyboard surface should perform network calls while typing.

---

## Constraints

> Đây là phần quan trọng nhất. Agent PHẢI tuân theo.

- [ ] CHỈ tạo integration cho auth + manual snippet backup/restore. Không implement realtime sync.
- [ ] KHÔNG đổi `applicationId` (`com.terasumi.sellerkeyboard`).
- [ ] KHÔNG rebrand app.
- [ ] KHÔNG thay thế SQLite bằng Convex. SQLite vẫn là source of truth trong MVP.
- [ ] KHÔNG rewrite toàn bộ snippet CRUD; chỉ thêm repository/adapter đủ để backup/restore.
- [ ] KHÔNG làm network call từ IME typing path hoặc trong `SellerKeyboard` service trừ khi user thao tác rõ ràng ngoài keyboard surface.
- [ ] KHÔNG backup clipboard history, voice transcript, typed text, password/autofill data, calculator state, Telex composition, hoặc keyboard telemetry.
- [ ] KHÔNG implement AI, smart reply, rewrite, translation, embeddings, vector search, file storage hoặc cloud sync trong goal này.
- [ ] KHÔNG upload snippet image files trong MVP. Current `imageUrls` là local cache paths; agent chỉ được backup text snippet fields và có thể lưu `imageUrls` as-is/empty để preserve schema. Nếu user muốn backup ảnh thật, DỪNG và tạo goal riêng cho Convex File Storage.
- [ ] KHÔNG log auth tokens, snippet content, Convex responses chứa private data, hoặc debug logs có sensitive payload trong release builds.
- [ ] KHÔNG commit secrets, `.env.local`, JWT private keys, OAuth secrets, generated key material, password hashes, hoặc session tokens.
- [ ] Convex deployment URL phải đi qua resource/build config; nếu cần secret/local deployment config thì dùng ignored properties file.
- [ ] KHÔNG dùng Convex Auth native Android trong MVP này. User đã approve self-auth cơ bản. Không tự chuyển sang Auth0, Clerk, WorkOS hoặc custom OIDC provider khi chưa được user đồng ý.
- [ ] Nếu Android Convex dependency/plugin conflict với Kotlin 2.2.10 hoặc AGP 9.1.0, DỪNG và báo blocker với exact error.
- [ ] Nếu schema migration có thể làm mất remote data, DỪNG và báo risk trước khi tiếp tục.
- [ ] Giữ nguyên behavior: tạo/sửa/xóa/list snippet local vẫn hoạt động khi offline hoặc chưa login.
- [ ] Backup/restore phải là thao tác user-triggered, có loading/error/success state rõ ràng.
- [ ] Restore không được silently overwrite local snippets nếu có conflict; phải có policy rõ: merge by `clientId`, hoặc preview/confirm before replace.

---

## Success Criteria

> `/goal` chỉ mark complete khi evidence PROVES completion. Không dừng vì "có vẻ đủ".

### Required Evidence per Criterion

| # | Tiêu chí | Verification Command | Expected Output / Signal |
|---|----------|---------------------|--------------------------|
| 1 | Convex Android dependency và Kotlin serialization được thêm đúng scope | `rg -n "android-convexmobile|plugin.serialization|kotlinx-serialization-json" app/build.gradle.kts build.gradle.kts settings.gradle.kts` | Thấy dependency `dev.convex:android-convexmobile:0.8.0@aar` hoặc version đã verify mới hơn, và serialization plugin/dependency |
| 2 | Convex backend có schema typed cho snippet backup | `Test-Path convex\\schema.ts; rg -n "snippetBackups|defineSchema|defineTable|by_owner|by_owner_clientId" convex\\schema.ts` | `True` và output có table/index cần thiết |
| 3 | Backend functions enforce authenticated owner | `rg -n "requireSession|sessionToken|Unauthenticated|ownerToken|backupBatch|listBackup|whoAmI" convex` | Functions validate self-auth session trước khi đọc/ghi |
| 4 | Basic self-auth setup được implement | `Test-Path convex\\auth.ts; rg -n "register|login|logout|whoAmI|passwordHash|sessionToken|hashSessionToken" convex\\auth.ts` | Có self-auth functions, password hashing, session token hashing |
| 5 | Android app có app-lifetime Convex client/auth repository | `rg -n "ConvexClient|ConvexClientWithAuth|ConvexAuthRepository|SnippetBackupRepository|convex_url" app\\src\\main\\java app\\src\\main\\res` | Có provider/repository rõ ràng, không instantiate client rải rác trong composables |
| 6 | Manual backup/restore UI tồn tại ngoài IME typing path | `rg -n "Backup now|Restore|Sao lưu|Khôi phục|login|logout|Convex" app\\src\\main\\java app\\src\\main\\res` | Có strings/UI action cho login/logout/backup/restore trong app screen/settings |
| 7 | Không dùng realtime sync làm source of truth | `rg -n "subscribe<|webSocketStateFlow|collect \\{|launchIn" app\\src\\main\\java\\com\\terasumi\\sellerkeyboard` | Không có long-lived snippet sync subscription; nếu dùng `subscribe` để list once, code phải cancel/first-result rõ ràng |
| 8 | Local snippet CRUD vẫn dùng được khi offline/chưa login | `rg -n "SnippetsDao|DatabaseHelper|fetchDataFromSQLite|insertSnippet|updateSnippetById|deleteSnippetById" app\\src\\main\\java\\com\\terasumi\\sellerkeyboard` | Existing SQLite DAO path còn tồn tại; backup repository không thay thế CRUD trực tiếp |
| 9 | No sensitive logging/secrets | `rg -n "JWT_PRIVATE_KEY|JWKS=|AUTH_SECRET|console\\.log\\(|Log\\.(d|i|v).*token|Log\\.(d|i|v).*snippet|initConvexLogging" .` | Không có secrets committed; debug logging chỉ allowed trong debug guarded code nếu có |
| 10 | Convex TypeScript passes | `bun install && bunx tsc --noEmit` | Exit code 0 |
| 11 | Convex codegen/deploy validation passes | `bunx convex codegen` | Exit code 0; generated types updated without schema/type errors |
| 12 | Android unit tests pass | `.\\gradlew.bat :app:testDebugUnitTest` | `BUILD SUCCESSFUL` |
| 13 | Android debug APK builds | `.\\gradlew.bat :app:assembleDebug` | `BUILD SUCCESSFUL` |
| 14 | Android lint passes | `.\\gradlew.bat :app:lintDebug` | `BUILD SUCCESSFUL` or every remaining warning/error documented as pre-existing with exact file/line |
| 15 | Manual auth and backup smoke test passes | Manual on emulator/device with test Convex deployment | User can login, tap Backup now, see success, clear/reinstall or delete local snippet, restore backup, and see restored title/content in HomeScreen and keyboard snippet page |

### Reference Artifacts

- `convex-backend-goal.md` — source of truth for this task.
- `GOAL.md` — global repository constraints.
- `docs/goals/00-shared-baseline-settings-tests_goal.md` — baseline/test constraints.
- `docs/qa/test_gates.md` — existing test gate reference.
- `docs/qa/00_manual_qa_matrix.md` — manual regression checklist.

### Completion Condition

Agent kết thúc khi và chỉ khi:

- [ ] Tất cả verification commands từ #1 đến #14 pass hoặc blocker hợp lệ được báo trước khi implement tiếp.
- [ ] Manual criterion #15 pass trên emulator/device hoặc được user explicitly waive.
- [ ] Không có regression so với Current State.
- [ ] Không có committed secret hoặc private auth material.
- [ ] Self-auth feasibility đã được proven bằng working login/register + authenticated backend call; nếu chưa proven thì goal chưa complete.

---

## Execution Plan

> Thực hiện theo thứ tự. Báo cáo sau mỗi bước trước khi tiếp tục.

1. Đọc toàn bộ `convex-backend-goal.md`, `GOAL.md`, `docs/goals/00-shared-baseline-settings-tests_goal.md`, `app/src/main/java/com/terasumi/sellerkeyboard/database/*`, `HomeScreen.kt`, `AddSnippetScreen.kt`, `EditSnippetScreen.kt`, `SnippetsKeyboard.kt`, `MyApplication.kt`, `app/build.gradle.kts`, `package.json`, `tsconfig.json`.
2. Verify official docs/API hiện tại cho Convex Android client. Không dùng Convex Auth native Android trong MVP này vì user đã approve self-auth cơ bản.
3. Implement basic self-auth threat model tối thiểu: email/password, password hash server-side, random session token, hashed session token storage, logout invalidates session, no sensitive logging.
4. Setup Convex backend dependencies only if missing: package scripts/dev deps for TypeScript typecheck/codegen; do not commit secrets.
5. Add Convex backend schema for user-owned snippet backups with explicit versioning and indexes.
6. Add self-auth backend file (`auth.ts`) with register/login/logout/whoAmI.
7. Add backend functions for authenticated smoke test, backup batch, list backup, and delete/mark deleted. Enforce owner isolation through validated session in every function.
8. Add Android Gradle dependency/plugin for Convex client and Kotlin serialization. Keep versions minimal and directly related to Convex.
9. Add Android backend client provider in `MyApplication` or data layer. Ensure one client instance per app process.
10. Add Kotlin serializable DTOs and `SnippetBackupRepository` that maps local `Snippets` to remote backup payloads. Do not expose Convex DTOs to keyboard UI.
11. Add register/login/logout repository and UI state. Store only the server-issued session token locally; never log it.
12. Add manual backup/restore UI in `HomeScreen` settings/menu or a small settings screen. Include loading, success, unauthenticated, and error states.
13. Restore policy: implement explicit merge/replace behavior. Prefer merge by stable `clientId` if added locally; if not, require user confirmation before inserting restored snippets.
14. Add focused tests for mapping, repository behavior with fakes, auth-required errors, and conflict policy. Do not require live Convex for JVM unit tests.
15. Run TypeScript/Convex validation and Android gates from Success Criteria.
16. Run manual device/emulator smoke test for login, backup, restore, offline local CRUD, and keyboard snippet insertion.
17. Report every Success Criteria row with evidence. If any criterion lacks evidence, continue work or report blocker; do not mark complete.

---

## Out of Scope

- Không implement realtime sync.
- Không sync clipboard history.
- Không sync typed text, voice transcripts, autofill suggestions, calculator state, Telex composing state, or keyboard usage analytics.
- Không implement AI/rewrite/smart reply/translation/vector search/agents.
- Không upload snippet image files to Convex File Storage.
- Không replace SQLite with Convex.
- Không migrate from raw SQLiteOpenHelper to Room.
- Không build web dashboard/admin portal.
- Không setup CI/CD mới.
- Không rotate production secrets.
- Không change IME manifest identity or Play Store application ID.

---

## Future Extension Notes

- AI features should be added through separate backend actions/modules after this goal is stable.
- Keep `snippets` backup schema versioned so future AI metadata can be additive, e.g. `tags`, `intent`, `embeddingId`, `lastAiRewriteAt`.
- If image backup is required later, create a separate Convex File Storage goal and avoid storing local cache paths as durable cloud references.
- If realtime sync is required later, create a separate conflict-resolution goal with a clear local/remote source-of-truth policy.

---

## References

- Convex Android Kotlin overview: https://docs.convex.dev/client/android/overview
- Convex Android Kotlin data types: https://docs.convex.dev/client/android/data-types
- Convex Android quickstart: https://docs.convex.dev/quickstart/android
- Convex Auth overview: https://docs.convex.dev/auth/convex-auth
- Convex Auth labs docs: https://labs.convex.dev/auth
- Convex Auth manual setup: https://labs.convex.dev/auth/setup/manual
- Convex Auth in functions: https://docs.convex.dev/auth/functions-auth
- Convex schemas: https://docs.convex.dev/database/schemas
- Convex mutations: https://docs.convex.dev/functions/mutation-functions
- Maven Central `dev.convex:android-convexmobile`: https://central.sonatype.com/artifact/dev.convex/android-convexmobile
- `app/src/main/java/com/terasumi/sellerkeyboard/database/DatabaseHelper.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/database/SnippetDao.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/database/Snippets.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/main/HomeScreen.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/main/AddSnippetScreen.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/main/EditSnippetScreen.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/keyboard/SnippetsKeyboard.kt`
- `app/src/main/java/com/terasumi/sellerkeyboard/MyApplication.kt`
- `app/build.gradle.kts`
- `package.json`

---

## Agent Instructions

### Execution

1. Đọc toàn bộ file này trước khi làm bất kỳ thứ gì.
2. Tuân theo Constraints tuyệt đối, không có ngoại lệ.
3. Thực hiện Execution Plan theo thứ tự, từng bước một.
4. Sau mỗi bước: báo cáo ngắn gọn kết quả trước khi tiếp tục.
5. Nếu phát hiện conflict giữa Constraints và Execution Plan: ưu tiên Constraints.
6. Nếu gặp thứ gì không có trong file này: DỪNG và hỏi, không tự assume.
7. Khi xong: verify toàn bộ Success Criteria và báo cáo từng item.

### Anti-bias Instructions

**Chống Scope Shrink:**

- KHÔNG redefine "done" thành chỉ thêm dependency hoặc chỉ tạo backend schema.
- KHÔNG bỏ login vì auth khó.
- KHÔNG gọi backup complete nếu chưa có restore/manual verification.
- KHÔNG biến manual backup thành realtime sync để dễ dùng Convex subscriptions.

**Chống Uncertainty Stop:**

- Nếu không có proof self-auth hoạt động với native Android, treat as not achieved.
- Nếu không có proof authenticated backend call thấy current user/session, auth chưa xong.
- Nếu không có proof local CRUD vẫn offline được, integration chưa xong.

**Chống Memory Trust:**

- Không tin previous conversation là evidence.
- Luôn inspect current file tree, current Gradle output, current Convex output.
- Chỉ claim complete khi command/manual evidence hiện tại chứng minh.
