package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.translation.MlKitTranslationController
import com.terasumi.sellerkeyboard.translation.TranslationDirection
import com.terasumi.sellerkeyboard.translation.TranslationState

@Composable
fun TranslationKeyboard(
    controller: MlKitTranslationController,
    selectedText: String,
    selectionEnd: Int,
    isSensitive: Boolean,
    onInsert: (text: String, selectionEnd: Int) -> Unit,
    onReplaceSelection: (String) -> Unit,
    onRestoreOriginal: (original: String, translated: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialSelectedText = remember { selectedText }
    val initialSelectionEnd = remember { selectionEnd }
    var draft by remember { mutableStateOf(initialSelectedText) }
    var direction by remember { mutableStateOf(TranslationDirection.VI_TO_EN) }
    var replacedTranslation by remember { mutableStateOf<String?>(null) }
    var draftEditing by remember { mutableStateOf(false) }
    val translationAvailable = remember(controller) { controller.isAvailableOnThisDevice() }
    val state by controller.state.collectAsState()

    LaunchedEffect(isSensitive) {
        if (isSensitive) controller.cancel()
    }
    LaunchedEffect((state as? TranslationState.Preview)?.translated) {
        replacedTranslation = null
    }

    if (draftEditing) {
        Column(
            modifier = modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InImeTextField(
                value = draft,
                label = "Selected or drafted text",
                accessibilityLabel = "Translation draft text",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
            CompactInImeTextKeypad(
                value = draft,
                onValueChange = { draft = it },
                onDone = { draftEditing = false }
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TranslationDirection.entries.forEach { item ->
                FilterChip(
                    selected = direction == item,
                    onClick = {
                        direction = item
                        controller.clearPreview()
                        replacedTranslation = null
                    },
                    label = {
                        Text(
                            if (item == TranslationDirection.VI_TO_EN) {
                                "VI → EN"
                            } else {
                                "EN → VI"
                            }
                        )
                    }
                )
            }
        }
        when (val current = state) {
            is TranslationState.Preview -> {
                Text("Original: ${current.original}")
                Text("Translation: ${current.translated}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val insertion = if (initialSelectedText.isNotBlank()) {
                                "\n${current.translated}"
                            } else {
                                current.translated
                            }
                            onInsert(insertion, initialSelectionEnd)
                            controller.clearPreview()
                        }
                    ) {
                        Text("Insert")
                    }
                    if (initialSelectedText.isNotBlank()) {
                        Button(
                            onClick = {
                                onReplaceSelection(current.translated)
                                replacedTranslation = current.translated
                            }
                        ) {
                            Text("Replace selection")
                        }
                        OutlinedButton(
                            enabled = replacedTranslation != null,
                            onClick = {
                                onRestoreOriginal(
                                    current.original,
                                    replacedTranslation ?: return@OutlinedButton
                                )
                                replacedTranslation = null
                            }
                        ) {
                            Text("Restore original")
                        }
                    }
                }
            }
            else -> {
                InImeTextField(
                    value = draft,
                    label = "Selected or drafted text",
                    accessibilityLabel = "Translation draft text",
                    onClick = { if (!isSensitive) draftEditing = true },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (translationAvailable) {
                        "The language model downloads on Wi-Fi, is stored on device, and can be canceled or deleted."
                    } else {
                        "Translation requires Android 11 or newer so private provider logging can be contained."
                    }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { controller.translate(draft, direction, isSensitive) },
                        enabled = draft.isNotBlank() && !isSensitive && translationAvailable
                    ) {
                        Text("Translate")
                    }
                    OutlinedButton(onClick = controller::cancel) {
                        Text("Cancel")
                    }
                    OutlinedButton(
                        enabled = translationAvailable && !isSensitive &&
                            state !is TranslationState.DeletingModel,
                        onClick = { controller.deleteDownloadedModel(direction) }
                    ) {
                        Text("Delete model")
                    }
                    if (state is TranslationState.DownloadingModel ||
                        state is TranslationState.Translating ||
                        state is TranslationState.DeletingModel) {
                        CircularProgressIndicator()
                    }
                }
                when (current) {
                    is TranslationState.ModelDeleted -> Text("Offline model deleted.")
                    is TranslationState.Error -> Text(current.message)
                    else -> Unit
                }
            }
        }
        Text("Machine translation can be inaccurate; review the draft before sending.")
    }
}
