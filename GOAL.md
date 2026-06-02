# GOAL: SellerKeyboard — Core IME, Inline Autofill, Voice Typing, Clipboard

> **Mục đích file này**: Đây là nguồn sự thật duy nhất (single source of truth) mô tả toàn bộ mục tiêu, trạng thái hiện tại, lộ trình phát triển, và tiêu chuẩn chất lượng của dự án. Mọi PR/task đều phải tham chiếu về đây.

---

## 1. Mục tiêu

Xây dựng SellerKeyboard thành bàn phím Android chuyên cho người bán hàng, tập trung vào 4 nhóm tính năng cần thiết:

1. **Core keyboard / IME** — gõ text + Telex tiếng Việt ổn định
2. **Inline Autofill Suggestions** — hiển thị gợi ý autofill từ hệ thống
3. **Voice typing** — nhập tin nhắn dài bằng giọng nói
4. **Clipboard** — clipboard riêng với lịch sử, pin, phân loại nội dung seller

---

## 2. Nguyên tắc sản phẩm

SellerKeyboard không cần clone Gboard. Mục tiêu là một bàn phím **nhẹ, nhanh, ổn định** và hữu ích cho seller.

### Ưu tiên

- Gõ tiếng Việt Telex ổn định.
- Chèn snippet nhanh.
- Quản lý clipboard riêng cho nội dung bán hàng.
- Voice typing để nhập tin nhắn dài.
- Tích hợp inline autofill để keyboard trông tự nhiên trong Android.
- Tôn trọng riêng tư: không gửi clipboard, nội dung gõ hoặc voice transcript ra server nếu chưa có tính năng yêu cầu rõ ràng.

### Không ưu tiên

- AI reply / rewrite.
- GIF / Sticker / Emoji Kitchen.
- Swipe typing.
- Autocorrect tổng quát kiểu Gboard.
- Dịch tự động.
- Smart reply bằng ML Kit.
- Cloud sync / multi-device sync.
- Gợi ý từ bằng language model.

---

## 3. Thông tin kỹ thuật cơ bản

| Thuộc tính | Giá trị |
|---|---|
| `applicationId` | `com.terasumi.sellerkeyboard` |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` / `compileSdk` | 36 |
| Version hiện tại | `1.3.5` (versionCode 25) |
| Ngôn ngữ | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Database | SQLiteOpenHelper (raw) |
| License | MIT |

---

## 4. Trạng thái hiện tại (Baseline — 2026-06-02)

### 4.1. Đã hoàn thành ✅

| Feature | Trạng thái | File chính |
|---|---|---|
| **IME Service cơ bản** | ✅ Hoạt động | `service/SellerKeyboard.kt`, `service/LifecycleInputMethodService.kt` |
| **Keyboard layout (QWERTY)** | ✅ Portrait + Landscape | `keyboard/Keyboard.kt`, `keyboard/Keycap.kt` |
| **Keyboard content switching** | ✅ Nhiều page (text, snippets, calculator) | `keyboard/KeyboardContent.kt` |
| **Snippet management** | ✅ CRUD đầy đủ | `main/AddSnippetScreen.kt`, `main/EditSnippetScreen.kt`, `main/HomeScreen.kt` |
| **Snippet keyboard** | ✅ Gửi snippet từ keyboard | `keyboard/SnippetsKeyboard.kt` |
| **Calculator keyboard** | ✅ Tính toán + gửi kết quả | `keyboard/CalculatorKeyboard.kt`, `keyboard/CalculatorEngine.kt` |
| **Image commit** | ✅ Gửi hình ảnh qua InputConnection | `service/SellerKeyboard.kt` (`doCommitContent`) |
| **Haptic feedback** | ✅ Với setting on/off | `service/SellerKeyboard.kt` (`performKeyHapticFeedback`) |
| **SQLite Database** | ✅ Snippets table | `database/SnippetDao.kt`, `database/SnippetsTable.kt` |
| **Boarding/Onboarding** | ✅ Hướng dẫn setup | `main/BoardingScreen.kt` |
| **Voice input (legacy)** | ✅ Google Voice Typing | `libs/voiceimeutils.jar` |
| **Responsive layout** | ✅ Constraint-driven sizing | `keyboard/Keyboard.kt` |
| **Dark/Light theme** | ✅ System theme detection | `keyboard/theme/` |

### 4.2. Chưa bắt đầu ❌ (Nằm trong scope GOAL này)

| Feature | Ưu tiên | Ghi chú |
|---|---|---|
| **Gõ tiếng Việt Telex** | 🔴 Cao | Chưa có code — spec chi tiết tại §6 |
| **Clipboard riêng** | 🔴 Cao | Chưa có code — spec tại §9 |
| **Voice typing (SpeechRecognizer)** | 🟡 Trung bình | Legacy dùng `voiceimeutils.jar`, cần chuyển sang `SpeechRecognizer` — spec tại §8 |
| **Inline Autofill Suggestions** | 🟡 Trung bình | Chưa có code — spec tại §7 |

### 4.3. Ngoài scope hiện tại

| Feature | Ghi chú |
|---|---|
| Rebrand → Eragear Keyboard Copilot | Sau khi 4 feature tracks hoàn thành |
| AI Copilot / suggestion | Phase xa |
| Snippet categories/tags | Enhancement sau MVP |
| Snippet cloud sync (Convex) | Convex scaffold tồn tại nhưng chưa có schema |

### 4.4. Module hiện tại

```text
com.terasumi.sellerkeyboard/
├── service/                     # Android IME Service layer
│   ├── SellerKeyboard.kt        # InputMethodService chính
│   └── LifecycleInputMethodService.kt
├── keyboard/                    # Keyboard UI + Logic
│   ├── Keyboard.kt              # QWERTY layout (Compose)
│   ├── KeyboardContent.kt       # Page switching (text/snippets/calculator)
│   ├── KeyboardContentView.kt   # ComposeView wrapper
│   ├── Keycap.kt                # Key rendering
│   ├── FunctionKey.kt           # Function key definitions
│   ├── FunctionKeyIcon.kt       # Function key icons
│   ├── SpecialFunctionKey.kt    # Special keys (shift, backspace, etc.)
│   ├── SpecialFunctionKeyIcon.kt
│   ├── CalculatorKeyboard.kt    # Calculator UI
│   ├── CalculatorEngine.kt      # Expression evaluator (exp4j)
│   ├── SnippetsKeyboard.kt      # Snippet browser in keyboard
│   ├── KeyboardAction.kt        # Action definitions
│   ├── KeyboardImeActions.kt    # IME action handling
│   ├── KeyboardState.kt         # UI state model
│   └── theme/                   # Material 3 theme tokens
├── database/                    # SQLite persistence (raw SQLiteOpenHelper)
│   ├── DatabaseHelper.kt        # DB singleton
│   ├── SnippetDao.kt            # DAO cho snippets
│   ├── Snippets.kt              # Entity
│   └── SnippetsTable.kt         # Table definition
├── main/                        # App screens (not keyboard)
│   ├── HomeScreen.kt            # Snippet list + management
│   ├── HomeScreenComponents.kt  # Reusable components
│   ├── AddSnippetScreen.kt      # Tạo snippet mới
│   ├── EditSnippetScreen.kt     # Chỉnh sửa snippet
│   ├── BoardingScreen.kt        # Onboarding flow
│   ├── SnippetFormComponents.kt # Shared form components
│   └── SnippetFormFieldDefaults.kt
├── ui/                          # Theme + shared UI
├── HomeActivity.kt              # Main Activity (Navigation host)
├── MyApplication.kt             # Application class
└── NavigationViewModel.kt       # Navigation state
```

---

## 5. Kiến trúc mục tiêu

```text
com.terasumi.sellerkeyboard/
├── ime/                              # [MỚI] IME abstraction layer
│   ├── SellerKeyboardService.kt      # InputMethodService chính (refactor từ service/)
│   ├── InputConnectionOps.kt         # Bọc thao tác InputConnection
│   ├── EditorInfoPolicy.kt           # Bật/tắt feature theo input type
│   ├── ImeLifecycleObserver.kt       # Lifecycle tracking
│   └── ImeActionDispatcher.kt        # Route action → controller
│
├── keyboard/
│   ├── ui/                           # [REFACTOR] Tách UI
│   │   ├── KeyboardScreen.kt
│   │   ├── Keycap.kt
│   │   ├── CandidateBar.kt          # [MỚI] Smart bar (autofill + suggestions)
│   │   ├── ClipboardBar.kt          # [MỚI] Clipboard strip
│   │   └── VoiceTypingPanel.kt      # [MỚI] Voice typing UI
│   ├── mvi/                          # [MỚI] MVI pattern
│   │   ├── KeyboardState.kt
│   │   ├── KeyboardEvent.kt
│   │   ├── KeyboardEffect.kt
│   │   └── KeyboardViewModel.kt
│   ├── domain/
│   │   ├── KeyboardAction.kt
│   │   ├── KeyboardMode.kt
│   │   └── KeyboardLayout.kt
│   ├── CalculatorKeyboard.kt
│   ├── CalculatorEngine.kt
│   └── SnippetsKeyboard.kt
│
├── vietnamese/                       # [MỚI] Vietnamese input engine
│   ├── TelexEngine.kt               # Pure Kotlin, no Android deps
│   ├── TelexProcessor.kt            # State + IME mapping
│   ├── VietnameseInputController.kt
│   ├── VietnameseCharMap.kt
│   ├── TonePlacementPolicy.kt
│   └── VietnameseInputSettings.kt
│
├── autofill/                         # [MỚI] Inline autofill
│   ├── InlineAutofillController.kt
│   ├── InlineSuggestionRenderer.kt
│   └── InlineAutofillState.kt
│
├── voice/                            # [MỚI] Voice typing
│   ├── VoiceTypingController.kt
│   ├── SpeechRecognizerRepository.kt
│   ├── VoiceTypingState.kt
│   └── VoicePermissionHandler.kt
│
├── clipboard/                        # [MỚI] Clipboard manager
│   ├── ClipboardRepository.kt
│   ├── SystemClipboardDataSource.kt
│   ├── LocalClipboardDataSource.kt
│   ├── ClipboardClassifier.kt
│   ├── ClipboardItem.kt
│   └── ClipboardSettings.kt
│
├── settings/                         # [MỚI] Settings infrastructure
│   ├── KeyboardSettingsRepository.kt
│   └── KeyboardSettingsDataStore.kt
│
├── database/                         # (giữ nguyên + mở rộng cho clipboard)
├── main/                             # (giữ nguyên)
└── ui/                               # (giữ nguyên)
```

---

## 6. Core keyboard / IME

### 6.1. Mục tiêu kỹ thuật

Core IME phải ổn định trước khi thêm tính năng khác. Mọi tính năng như Telex, clipboard, voice typing và autofill đều cuối cùng phải đi qua `InputConnection`.

### 6.2. Thành phần chính

#### `SellerKeyboardService`

Trách nhiệm:

- Kế thừa `InputMethodService`.
- Tạo Compose view cho keyboard.
- Cung cấp `currentInputConnection` cho controller.
- Quản lý lifecycle:
  - `onCreateInputView()`
  - `onStartInput()`
  - `onStartInputView()`
  - `onFinishInput()`
  - `onDestroy()`

#### `InputConnectionOps`

Trách nhiệm:

- Bọc thao tác với `InputConnection`.
- Không để logic IME lặp lại ở nhiều nơi.
- Dùng batch edit cho thao tác nhiều bước.

API đề xuất:

```kotlin
package com.terasumi.sellerkeyboard.ime

import android.os.Build
import android.view.inputmethod.InputConnection

/** Các thao tác an toàn với InputConnection. */
object InputConnectionOps {

    /** Gộp nhiều thao tác editor để giảm nhấp nháy. */
    inline fun InputConnection.executeBatch(block: InputConnection.() -> Unit) {
        beginBatchEdit()
        try {
            block()
        } finally {
            endBatchEdit()
        }
    }

    /** Xóa số code point trước con trỏ. */
    fun InputConnection.deleteBeforeCursor(count: Int) {
        if (count <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleteSurroundingTextInCodePoints(count, 0)
            return
        }
        deleteSurroundingText(count, 0)
    }

    /** Commit text vào editor hiện tại. */
    fun InputConnection.commitPlainText(text: String) {
        if (text.isEmpty()) return
        commitText(text, 1)
    }

    /** Đặt composing text cho tiếng đang gõ. */
    fun InputConnection.setCurrentComposingText(text: String) {
        setComposingText(text, 1)
    }
}
```

#### `EditorInfoPolicy`

Trách nhiệm:

- Quyết định tính năng nào được bật theo `EditorInfo.inputType`.
- Tắt Telex/clipboard suggestion/voice suggestion trong field nhạy cảm.

Policy đề xuất:

```kotlin
package com.terasumi.sellerkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Chính sách bật/tắt tính năng theo loại ô nhập. */
object EditorInfoPolicy {

    /** Kiểm tra ô nhập có phải trường nhạy cảm không. */
    fun isSensitiveInput(editorInfo: EditorInfo): Boolean {
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    /** Cho phép hiển thị clipboard khi không phải trường nhạy cảm. */
    fun canShowClipboard(editorInfo: EditorInfo): Boolean = !isSensitiveInput(editorInfo)

    /** Cho phép voice typing khi không phải trường nhạy cảm. */
    fun canUseVoiceTyping(editorInfo: EditorInfo): Boolean = !isSensitiveInput(editorInfo)

    /** Cho phép Telex khi không phải trường nhạy cảm. */
    fun canUseVietnameseInput(editorInfo: EditorInfo): Boolean = !isSensitiveInput(editorInfo)
}
```

### 6.3. Core IME — Composing text chuẩn

Dùng composing text đúng chuẩn Android IME:

- `setComposingText()` khi người dùng đang gõ một tiếng.
- `commitText()` khi gặp space, enter hoặc separator.
- `finishComposingText()` khi kết thúc composing.
- `deleteSurroundingTextInCodePoints()` nếu API ≥ 24, fallback `deleteSurroundingText()`.
- `getSurroundingText()` từ API 30, fallback `getTextBeforeCursor()`.
- `beginBatchEdit()` / `endBatchEdit()` cho thao tác nhiều bước.

### 6.4. Core IME — Chặn/tắt Telex trong input type nhạy cảm

Tắt Telex mặc định khi:

- password
- visible password
- number password
- web password
- phone/password-like fields

---

## 7. Telex tiếng Việt (trong Core IME)

Telex là phần thuộc Core IME, không phải tính năng suggestion nâng cao.

### 7.1. Shape keys

| Input | Output | Ghi chú |
|---|---|---|
| `aa` | `â` | giữ hoa/thường: `Aa/AA → Â` theo policy |
| `aw` | `ă` | |
| `ee` | `ê` | |
| `oo` | `ô` | |
| `ow` | `ơ` | |
| `uw` | `ư` | |
| `w` | `ư` | optional, theo setting STelex |
| `dd` | `đ` | `DD → Đ` |

### 7.2. Tone keys

| Key | Tone | Ví dụ |
|---|---|---|
| `s` | sắc | `as → á` |
| `f` | huyền | `af → à` |
| `r` | hỏi | `ar → ả` |
| `x` | ngã | `ax → ã` |
| `j` | nặng | `aj → ạ` |
| `z` | xóa dấu | `asz → a` |

Yêu cầu:

- Phím dấu cuối cùng thắng.
- Nhấn đôi phím dấu để escape ký tự thường: `her → hẻ`, `herr → her`.
- `z` xóa dấu nhưng không xóa shape.

### 7.3. Vị trí đặt dấu (`TonePlacementPolicy`)

Policy mặc định: `MODERN`

| Rule | Ví dụ |
|---|---|
| Tiếng có `iê` → dấu trên `ê` | `tieesng → tiếng` |
| Tiếng có `uô` → dấu trên `ô` | `muoons → muốn` |
| Tiếng có `ươ` → dấu trên `ơ` | `thuwowng → thương` |
| `qu` → không dấu lên `u` phụ âm | `quas → quá` |
| `gi` → không dấu lên `i` phụ âm | `gias → giá` |
| `oa/oe/uy` → hỗ trợ cả 2 kiểu | `hoaf → hoà` hoặc `hòa` theo setting |

```kotlin
enum class TonePlacementMode {
    MODERN,
    LEGACY
}
```

### 7.4. Boundary của một tiếng

Tiếng kết thúc khi gặp: space, newline, tab, dấu câu, emoji/symbol, selection thay đổi, editor/action commit.

Cần xử lý đúng cho seller use cases:

- Mã đơn: `DH-12345`
- SKU: `SP_AO_DEN_M`
- Tiền: `120k`, `1.250.000đ`
- URL, email, số điện thoại → auto-disable Telex.

### 7.5. State model

```kotlin
data class VietnameseInputState(
    val isEnabled: Boolean = true,
    val rawSyllable: String = "",
    val composedSyllable: String = "",
    val tonePlacementMode: TonePlacementMode = TonePlacementMode.MODERN,
    val telexVariant: TelexVariant = TelexVariant.STANDARD,
    val isComposing: Boolean = false
)

enum class TelexVariant {
    STANDARD,
    SIMPLE_W_AS_U_HORN
}
```

### 7.6. TelexEngine — nguyên tắc

- Pure Kotlin, không phụ thuộc Android SDK.
- Không đặt trong UI/Composable.
- Có unit test ≥ 100 case.
- Hàm chính ngắn, rõ, dễ test.

---

## 8. Inline Autofill Suggestions

### 8.1. Mục tiêu

Khi Android Autofill framework cung cấp inline suggestions, SellerKeyboard hiển thị chúng trong smart bar/candidate bar giống keyboard hệ thống.

### 8.2. Giới hạn quan trọng

- Chỉ khả dụng từ Android 11/API 30 trở lên.
- IME chỉ render suggestion do Autofill service cung cấp.
- Không tự đọc dữ liệu nhạy cảm bên trong suggestion trước khi user chọn.
- Không thay thế password manager.
- Không cố tự làm autofill service trong MVP.

### 8.3. UI đề xuất

```text
┌──────────────────────────────────────────┐
│ [Autofill: email@example.com] [Address]  │
├──────────────────────────────────────────┤
│ q w e r t y u i o p                     │
│ a s d f g h j k l                       │
│ ⇧ z x c v b n m ⌫                      │
│ 123   space        .                    │
└──────────────────────────────────────────┘
```

### 8.4. Thành phần

#### `InlineAutofillController`

Trách nhiệm:

- Nhận inline suggestion request/response flow từ IME integration.
- Cập nhật state cho `CandidateBar`.
- Ẩn suggestion khi input field không còn phù hợp.

#### `InlineSuggestionRenderer`

Trách nhiệm:

- Render inline suggestion item.
- Giữ style thống nhất với Material 3.
- Không tự tạo text nhạy cảm.

### 8.5. Acceptance criteria

- Trên Android 11+, autofill suggestion xuất hiện trong smart bar nếu app/autofill service hỗ trợ.
- Tap suggestion điền được vào field.
- Trên Android thấp hơn, keyboard vẫn hoạt động bình thường.
- Không crash nếu Autofill service trả suggestion rỗng.

---

## 9. Voice Typing

### 9.1. Mục tiêu

Cho phép seller nhập câu dài bằng giọng nói, sau đó commit transcript vào app đang nhập.

### 9.2. Hướng triển khai

Dùng Android `SpeechRecognizer`.

Ưu tiên:

1. Kiểm tra on-device recognition nếu có (`createOnDeviceSpeechRecognizer()`).
2. Fallback sang recognition service mặc định của hệ thống (`createSpeechRecognizer()`).
3. Chèn kết quả bằng `InputConnection.commitText()`.
4. Xin quyền `RECORD_AUDIO` đúng lúc.

### 9.3. State machine

```text
Idle
  └── TapMic → RequestPermission
RequestPermission
  ├── Granted → Listening
  └── Denied → Error
Listening
  ├── PartialResult → Listening(partialText)
  ├── FinalResult → CommitText → Idle
  ├── Stop → Idle
  └── Error → Error
Error
  └── Dismiss → Idle
```

### 9.4. Kotlin skeleton

```kotlin
package com.terasumi.sellerkeyboard.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Điều phối nhận dạng giọng nói cho keyboard. */
class VoiceTypingController(
    private val context: Context,
    private val locale: Locale = Locale("vi", "VN")
) {
    private val mutableState = MutableStateFlow<VoiceTypingState>(VoiceTypingState.Idle)
    val state: StateFlow<VoiceTypingState> = mutableState

    private var recognizer: SpeechRecognizer? = null

    /** Bắt đầu nghe giọng nói. */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            mutableState.value = VoiceTypingState.Error("Thiết bị không hỗ trợ nhận dạng giọng nói")
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
            startListening(createIntent())
        }
        mutableState.value = VoiceTypingState.Listening(partialText = "")
    }

    /** Dừng nghe và giải phóng recognizer. */
    fun stopListening() {
        recognizer?.stopListening()
        releaseRecognizer()
        mutableState.value = VoiceTypingState.Idle
    }

    /** Giải phóng tài nguyên khi IME bị hủy. */
    fun releaseRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun createIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.getBestResult()
            mutableState.value = VoiceTypingState.Listening(partialText = text)
        }

        override fun onResults(results: Bundle?) {
            val text = results.getBestResult()
            mutableState.value = VoiceTypingState.FinalResult(text)
            releaseRecognizer()
        }

        override fun onError(error: Int) {
            mutableState.value = VoiceTypingState.Error("Không nhận dạng được giọng nói")
            releaseRecognizer()
        }
    }

    private fun Bundle?.getBestResult(): String {
        return this
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
    }
}
```

```kotlin
package com.terasumi.sellerkeyboard.voice

/** Trạng thái voice typing. */
sealed interface VoiceTypingState {
    data object Idle : VoiceTypingState
    data class Listening(val partialText: String) : VoiceTypingState
    data class FinalResult(val text: String) : VoiceTypingState
    data class Error(val message: String) : VoiceTypingState
}
```

### 9.5. Acceptance criteria

- Tap mic lần đầu xin quyền microphone.
- Sau khi có transcript, commit được vào editor hiện tại.
- Nếu user chuyển app/đổi field, recognizer được hủy an toàn.
- Không crash khi không có recognition service.
- Không hoạt động trong password field.

---

## 10. Clipboard

### 10.1. Mục tiêu

Tạo clipboard riêng cho seller, giúp paste nhanh nội dung hay dùng và lịch sử copy gần đây.

### 10.2. Quy tắc privacy

- Không đọc clipboard trong field nhạy cảm.
- Không lưu clipboard item trùng lặp liên tục.
- Có setting bật/tắt clipboard history.
- Có nút xóa toàn bộ lịch sử.
- Có cơ chế pin item.
- Có thể tự động xóa item cũ sau N ngày.

### 10.3. Data model

```kotlin
package com.terasumi.sellerkeyboard.clipboard

import java.time.Instant

/** Một item trong clipboard riêng của SellerKeyboard. */
data class ClipboardItem(
    val id: String,
    val content: String,
    val type: ClipboardItemType,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isPinned: Boolean = false,
    val source: ClipboardSource = ClipboardSource.System
)

/** Loại nội dung clipboard đã phân loại. */
enum class ClipboardItemType {
    PlainText,
    PhoneNumber,
    Url,
    Price,
    TrackingCode,
    AddressCandidate
}

/** Nguồn tạo clipboard item. */
enum class ClipboardSource {
    System,
    UserPinned,
    Snippet,
    Manual
}
```

### 10.4. Repository

```kotlin
package com.terasumi.sellerkeyboard.clipboard

import kotlinx.coroutines.flow.Flow

/** Repository quản lý clipboard riêng của SellerKeyboard. */
interface ClipboardRepository {

    /** Theo dõi danh sách item mới nhất. */
    fun observeItems(): Flow<List<ClipboardItem>>

    /** Lưu item mới nếu hợp lệ. */
    suspend fun saveItem(content: String, source: ClipboardSource = ClipboardSource.System)

    /** Ghim hoặc bỏ ghim item. */
    suspend fun setPinned(itemId: String, isPinned: Boolean)

    /** Xóa một item. */
    suspend fun deleteItem(itemId: String)

    /** Xóa toàn bộ item không được ghim. */
    suspend fun clearUnpinnedItems()
}
```

### 10.5. Classifier

```kotlin
package com.terasumi.sellerkeyboard.clipboard

/** Phân loại nhanh nội dung clipboard. */
object ClipboardClassifier {

    private val phoneRegex = Regex("""(?:\+84|0)(?:\d[ .-]?){8,10}""")
    private val urlRegex = Regex("""https?://\S+|www\.\S+""")
    private val priceRegex = Regex("""\b\d{1,3}(?:[.,]\d{3})*(?:\s?đ|\s?vnd|k)?\b""", RegexOption.IGNORE_CASE)
    private val trackingRegex = Regex("""\b[A-Z0-9]{8,20}\b""")

    /** Xác định loại nội dung clipboard. */
    fun classify(content: String): ClipboardItemType {
        val text = content.trim()
        return when {
            urlRegex.containsMatchIn(text) -> ClipboardItemType.Url
            phoneRegex.containsMatchIn(text) -> ClipboardItemType.PhoneNumber
            priceRegex.containsMatchIn(text) -> ClipboardItemType.Price
            trackingRegex.matches(text) -> ClipboardItemType.TrackingCode
            else -> ClipboardItemType.PlainText
        }
    }
}
```

### 10.6. Pin use cases cho seller

- Số tài khoản ngân hàng
- Hotline / SĐT shop
- Địa chỉ shop / kho
- Link sản phẩm
- Câu trả lời mẫu

### 10.7. UI đề xuất

```text
┌──────────────────────────────────────────┐
│ Clipboard: [SĐT] [STK] [Địa chỉ] [Link]│
├──────────────────────────────────────────┤
│ q w e r t y u i o p                     │
│ a s d f g h j k l                       │
│ ⇧ z x c v b n m ⌫                      │
│ 123    🎙    space    📋    .           │
└──────────────────────────────────────────┘
```

### 10.8. Acceptance criteria

- Copy text từ app khác → mở keyboard → item mới xuất hiện (nếu Android cho phép).
- Tap item sẽ paste vào editor via `commitText()`.
- Pin item giữ item ở đầu danh sách.
- Không hiển thị clipboard trong password field.
- Có thể xóa từng item và xóa toàn bộ item không ghim.
- Không lưu field nhạy cảm khi đang nhập password.

---

## 11. MVI State tổng

```kotlin
package com.terasumi.sellerkeyboard.keyboard.mvi

import com.terasumi.sellerkeyboard.clipboard.ClipboardItem
import com.terasumi.sellerkeyboard.voice.VoiceTypingState

/** State tổng của keyboard. */
data class KeyboardState(
    val isVietnameseEnabled: Boolean = true,
    val isShiftEnabled: Boolean = false,
    val isCapsLockEnabled: Boolean = false,
    val isSensitiveInput: Boolean = false,
    val clipboardItems: List<ClipboardItem> = emptyList(),
    val voiceTypingState: VoiceTypingState = VoiceTypingState.Idle,
    val inlineAutofillItems: List<Any> = emptyList()
)

/** Event từ UI keyboard. */
sealed interface KeyboardEvent {
    data class TapCharacter(val value: Char) : KeyboardEvent
    data object TapBackspace : KeyboardEvent
    data object TapSpace : KeyboardEvent
    data object TapEnter : KeyboardEvent
    data object TapShift : KeyboardEvent
    data object TapMic : KeyboardEvent
    data object StopVoiceTyping : KeyboardEvent
    data class TapClipboardItem(val itemId: String) : KeyboardEvent
    data class TapSeparator(val value: Char) : KeyboardEvent
    data object ToggleVietnamese : KeyboardEvent
}

/** Effect một lần cho IME hoặc permission. */
sealed interface KeyboardEffect {
    data class CommitText(val text: String) : KeyboardEffect
    data object RequestRecordAudioPermission : KeyboardEffect
    data class ShowError(val message: String) : KeyboardEffect
}
```

---

## 12. Settings tổng hợp

### 12.1. Settings đã có

| Setting | Storage | Trạng thái |
|---|---|---|
| Haptic on/off | `SharedPreferences` (`keyboard_prefs`) | ✅ Done |

### 12.2. Settings cần thêm (MVP)

| Setting | Kiểu | Mặc định | Feature |
|---|---|---|---|
| `vietnameseInputEnabled` | `Boolean` | `true` | Core IME |
| `tonePlacementMode` | `TonePlacementMode` | `MODERN` | Core IME |
| `telexVariant` | `TelexVariant` | `STANDARD` | Core IME |
| `autoDisableInPassword` | `Boolean` | `true` | Core IME |
| `clipboardHistoryEnabled` | `Boolean` | `true` | Clipboard |
| `clipboardAutoDeleteDays` | `Int` | `30` | Clipboard |
| `voiceTypingLocale` | `String` | `vi-VN` | Voice |

### 12.3. UI Settings đề xuất

```text
Tiếng Việt
├── [x] Bật gõ Telex
├── Kiểu gõ: Telex / Telex đơn giản
├── Kiểu đặt dấu: Mới / Cũ
└── [x] Tự tắt trong mật khẩu, email, URL

Clipboard
├── [x] Bật lịch sử clipboard
├── Tự xóa sau: 7 / 14 / 30 ngày
└── [Xóa toàn bộ lịch sử]

Giọng nói
├── Ngôn ngữ: Tiếng Việt / English
└── [x] Ưu tiên nhận dạng offline

Giao diện
├── [x] Rung khi nhấn phím
└── Theme: Sáng / Tối / Theo hệ thống
```

---

## 13. Roadmap triển khai

### Phase 1: Core IME + Telex ❌

| Task | Acceptance Criteria |
|---|---|
| Refactor `SellerKeyboardService` từ `service/` sang `ime/` | Service hoạt động đúng |
| Tạo `InputConnectionOps` | Batch edit, delete, commit, composing |
| Tạo `EditorInfoPolicy` | Disable Telex trong password field |
| Implement `TelexEngine` (pure Kotlin) | Unit test ≥ 100 case |
| Implement `TelexProcessor` + `VietnameseInputController` | Composing text đúng chuẩn |
| Keyboard UI toggle VI/EN | Hiển thị trạng thái rõ ràng |
| Manual test Messenger, Zalo, Shopee, Chrome, Gmail | Không duplicate, không mất dấu, không kẹt composing |

**Definition of Done:**

- Gõ text bình thường ổn định.
- Gõ Telex ổn định.
- Backspace/space/dấu câu không lỗi composing.
- Không bật Telex trong password field.

### Phase 2: Clipboard ❌

| Task | Acceptance Criteria |
|---|---|
| Tạo `ClipboardRepository` + `LocalClipboardDataSource` | CRUD + observe |
| Tạo `SystemClipboardDataSource` | Đọc system clipboard khi được phép |
| Tạo `ClipboardClassifier` | Phân loại đúng SĐT, URL, giá, tracking code |
| Tạo `ClipboardBar` UI | Hiển thị trong keyboard, ẩn trong password field |
| Pin/delete/clear | Hoạt động đúng |
| Privacy setting | Bật/tắt history, xóa toàn bộ |

**Definition of Done:**

- Paste nhanh item clipboard.
- Pin item hoạt động.
- Không hiện clipboard trong field nhạy cảm.
- Không lưu trùng item liên tục.

### Phase 3: Voice Typing ❌

| Task | Acceptance Criteria |
|---|---|
| Tạo `VoiceTypingController` | `SpeechRecognizer` lifecycle đúng |
| Xin quyền `RECORD_AUDIO` | Đúng lúc, không crash khi denied |
| Voice typing UI (idle/listening/processing/error) | Clear state transitions |
| Commit transcript vào editor | `commitText()` |
| Hủy recognizer theo lifecycle | Không leak khi đổi app/field |

**Definition of Done:**

- Tap mic, nói tiếng Việt, chèn transcript được.
- Có fallback khi recognition service không có.
- Không crash khi quyền bị từ chối.

### Phase 4: Inline Autofill Suggestions ❌

| Task | Acceptance Criteria |
|---|---|
| Tích hợp inline autofill flow cho IME | API 30+ |
| Render suggestion trong smart bar | Material 3 style |
| Degrade gracefully dưới API 30 | Keyboard hoạt động bình thường |
| Manual test với password manager phổ biến | Autofill điền đúng |

**Definition of Done:**

- Autofill suggestion xuất hiện khi hệ thống cung cấp.
- Tap suggestion điền được field.
- Keyboard không đọc/leak dữ liệu autofill.

---

## 14. Test Plan

### 14.1. Unit test

#### Telex

| Category | Cases |
|---|---|
| Shape keys | `aa→â`, `aw→ă`, `ee→ê`, `oo→ô`, `ow→ơ`, `uw→ư`, `dd→đ`, `DD→Đ` |
| Tone keys | `as→á`, `af→à`, `ar→ả`, `ax→ã`, `aj→ạ` |
| Xóa dấu | `asz→a` |
| Tone cuối thắng | `asf→à`, `afj→ạ` |
| Shape + tone | `aas→ấ`, `aaf→ầ`, `awr→ẳ`, `ees→ế`, `oos→ố`, `ows→ớ`, `uws→ứ` |
| Common words | `ddawng→đăng`, `nhaan→nhân`, `tieesng→tiếng`, `muoons→muốn`, `thuwowng→thương`, `khachs→khách`, `hangf→hàng`, `quas→quá`, `gias→giá` |
| Escape | `her→hẻ`, `herr→her`, `tax→tã`, `taxx→tax` |
| Separator/commit | `tiếng ` (with space), `đăng,` (with comma) |
| Backspace | recompose from raw, no crash on empty |
| Uppercase | hoa/thường preservation |

#### Clipboard

| Case | Expected |
|---|---|
| Classify `0912345678` | `PhoneNumber` |
| Classify `https://shopee.vn/...` | `Url` |
| Classify `250.000đ` | `Price` |
| Classify `SPXVN12345678` | `TrackingCode` |
| Classify `hello world` | `PlainText` |
| Ignore duplicate | Không lưu trùng liên tục |
| Pin ordering | Pinned items ở đầu |

#### EditorInfoPolicy

| Input type | Clipboard | Voice | Telex |
|---|---|---|---|
| Password | ❌ | ❌ | ❌ |
| Normal text | ✅ | ✅ | ✅ |
| Number | ✅ | ✅ | ❌ |
| Email | ✅ | ✅ | ⚠️ (theo setting) |

### 14.2. Instrumentation test

- IME opens in text field.
- Character key commits/composes correctly.
- Backspace works in composing and committed text.
- Voice typing permission flow does not crash.
- Clipboard bar hides in password field.

### 14.3. Manual regression matrix

| App | Core IME | Telex | Clipboard | Voice | Autofill |
|---|---|---|---|---|---|
| Messenger | ✅ | ✅ | ✅ | ✅ | N/A |
| Zalo | ✅ | ✅ | ✅ | ✅ | N/A |
| Shopee Seller | ✅ | ✅ | ✅ | ✅ | N/A |
| Lazada Seller | ✅ | ✅ | ✅ | ✅ | N/A |
| TikTok Shop | ✅ | ✅ | ✅ | ✅ | N/A |
| Chrome | ✅ | ✅ | ✅ | ✅ | ✅ |
| Gmail | ✅ | ✅ | ✅ | ✅ | ✅ |
| Google Sheets | ✅ | ✅ | ✅ | ✅ | N/A |
| Password field | ✅ | N/A | hidden | disabled | ✅ |

---

## 15. Rủi ro kỹ thuật

### 15.1. Editor không tuân thủ InputConnection đầy đủ

**Severity:** S1 — có thể crash hoặc duplicate text.

**Mitigation:**
- Null-safe toàn bộ InputConnection call.
- Fallback plain commit khi IC trả null.
- Reset composing khi `onUpdateSelection` lệch state.
- Compatibility mode theo package nếu cần.

### 15.2. Không phục dựng được raw input từ composed text

**Severity:** S2 — backspace/edit có thể sai.

**Mitigation:**
- Giữ `rawSyllable` nội bộ trong `TelexProcessor`.
- Reset raw buffer khi selection/focus đổi.
- Khi không chắc context, dùng visible syllable + safe compose hoặc commit plain.

### 15.3. License từ repo tham khảo

**Severity:** S0 — có thể vi phạm GPL nếu copy code.

**Mitigation:**
- Không copy source từ UniKey/ibus/fcitx.
- Viết engine Kotlin độc lập dựa trên quy tắc gõ phổ thông.
- Ghi lại nguồn tham khảo và quyết định clean-room.

### 15.4. Performance regression khi thêm Telex processing

**Severity:** S2 — lag khi gõ nhanh.

**Mitigation:**
- TelexEngine allocation-free trên hot path.
- Batch edit cho mọi replace/commit.
- Profile compose/commit cycle target < 4ms.

### 15.5. Clipboard privacy trên Android 10+

**Severity:** S1 — Android 10+ hạn chế clipboard access.

**Mitigation:**
- Chỉ đọc clipboard khi keyboard đang active (foreground IME).
- Respect `ClipboardManager` restrictions.
- Không đọc clipboard trong background.
- Có setting bật/tắt clipboard history.

### 15.6. Voice typing — SpeechRecognizer lifecycle

**Severity:** S2 — leak hoặc crash nếu không destroy đúng.

**Mitigation:**
- `destroy()` recognizer trong `onFinishInput()` và `onDestroy()`.
- Null-check trước mọi thao tác.
- Handle `ERROR_RECOGNIZER_BUSY` gracefully.

### 15.7. Inline autofill — API version fragmentation

**Severity:** S3 — chức năng không khả dụng trên device cũ.

**Mitigation:**
- Feature flag kiểm tra API level.
- Degrade gracefully: keyboard hoạt động bình thường trên < API 30.
- Không crash nếu autofill service không có hoặc trả rỗng.

---

## 16. Repo GitHub nên học

### 16.1. FlorisBoard

Repo: `florisboard/florisboard`

Nên học:
- Kiến trúc keyboard hiện đại.
- Smartbar/candidate bar.
- Clipboard UI và privacy mindset.
- Cách tổ chức layout, theme, settings.

License: Apache-2.0. Có thể học kiến trúc thoải mái hơn GPL.

### 16.2. AnySoftKeyboard

Repo: `AnySoftKeyboard/AnySoftKeyboard`

Nên học:
- IME lâu năm, nhiều edge case.
- Multi-layout, language packs.
- Clipboard improvements, direct boot, gesture typing.
- Cách xử lý input type khác nhau.

### 16.3. OpenBoard

Repo: `openboard-team/openboard`

Nên học:
- Fork AOSP LatinIME gần với keyboard truyền thống.
- Cách xử lý composing, keyboard layout, dictionary.

### 16.4. THEAccess/compose-keyboard-ime

Repo: `THEAccess/compose-keyboard-ime`

Nên học:
- Ví dụ nhỏ về IME bằng Jetpack Compose.
- Cách attach Compose UI vào `InputMethodService`.

License: MIT. Phù hợp để tham khảo.

### 16.5. Vietnamese-LatinIME

Repo: `AgeOfMobile/Vietnamese-LatinIME`

Nên học:
- Cách một fork LatinIME hỗ trợ tiếng Việt.
- Logic Telex/tiếng Việt.

Cảnh báo: Clean-room implement Telex bằng Kotlin, không copy code.

---

## 17. Nguồn tài liệu chính thức

| Tài liệu | URL |
|---|---|
| Android `InputMethodService` | https://developer.android.com/reference/kotlin/android/inputmethodservice/InputMethodService |
| Android `InputConnection` | https://developer.android.com/reference/android/view/inputmethod/InputConnection |
| Create an input method | https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method |
| IME Autofill guide | https://developer.android.com/identity/autofill/ime-autofill |
| `SpeechRecognizer` | https://developer.android.com/reference/android/speech/SpeechRecognizer |
| Android 10 clipboard privacy | https://developer.android.com/about/versions/10/privacy/changes |
| Secure clipboard handling | https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling |
| Image keyboard support | https://developer.android.com/develop/ui/views/touch-and-input/image-keyboard |
| `BaseInputConnection` source | https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/view/inputmethod/BaseInputConnection.java |

---

## 18. Definition of Done (toàn dự án)

### Core IME + Telex
- [ ] Gõ text bình thường ổn định (chữ hoa, chữ thường, số, dấu câu, enter, backspace, space).
- [ ] Gõ Telex pass test matrix ≥ 100 case.
- [ ] Composing text đúng chuẩn: `setComposingText` / `commitText` / `finishComposingText`.
- [ ] Tích hợp qua `InputConnection`, không hack raw key event.
- [ ] Không crash khi connection null hoặc editor trả null.
- [ ] Không duplicate text khi nhập nhanh.
- [ ] Không kẹt composing sau space/dấu câu/focus change.
- [ ] Không bật Telex trong password field.
- [ ] Manual QA pass trên ≥ 5 app/field phổ biến.

### Clipboard
- [ ] Clipboard history hoạt động.
- [ ] Pin/delete/clear hoạt động.
- [ ] Classifier phân loại đúng SĐT, URL, giá, tracking code.
- [ ] Không hiển thị trong password field.
- [ ] Không lưu trùng item liên tục.
- [ ] Privacy setting hoạt động.

### Voice Typing
- [ ] Tap mic xin quyền đúng lúc.
- [ ] Commit transcript vào editor.
- [ ] Hủy recognizer theo lifecycle.
- [ ] Không crash khi service không có hoặc quyền bị từ chối.
- [ ] Không hoạt động trong password field.

### Inline Autofill
- [ ] Autofill suggestion xuất hiện trên API 30+.
- [ ] Tap suggestion điền được field.
- [ ] Keyboard không đọc/leak dữ liệu autofill.
- [ ] Degrade gracefully dưới API 30.

### Code Quality
- [ ] Clean architecture: `ime/`, `vietnamese/`, `clipboard/`, `voice/`, `autofill/` packages riêng.
- [ ] MVI cho keyboard logic.
- [ ] Tuân thủ naming convention: `PascalCase` types, `camelCase` vars, `UPPER_SNAKE_CASE` constants.
- [ ] Không secret trong code/repo.
- [ ] Unit test coverage cho engine logic.
- [ ] Lint pass (`./gradlew :app:lintDebug`).

### Deployment
- [ ] `applicationId` không đổi (`com.terasumi.sellerkeyboard`).
- [ ] R8 enabled trong release build.
- [ ] ProGuard rules cho Gson/Glide/exp4j.

---

## 19. Quyết định scope cuối cùng

SellerKeyboard hiện tại chỉ tập trung vào:

- ✅ **Core keyboard / IME**
- ✅ **Telex tiếng Việt trong Core IME**
- ✅ **Inline Autofill Suggestions**
- ✅ **Voice typing**
- ✅ **Clipboard riêng cho seller**

Không triển khai trong giai đoạn này:

- ❌ AI rewrite / reply
- ❌ ML Kit smart reply
- ❌ Translate
- ❌ GIF / sticker search
- ❌ Swipe typing
- ❌ General autocorrect kiểu Gboard
- ❌ Cloud sync / multi-device sync
- ❌ Gợi ý từ bằng language model
- ❌ Rebrand (chờ features hoàn thành)

---

*Cập nhật lần cuối: 2026-06-02*
*Maintainer: @tongducthanhnam*