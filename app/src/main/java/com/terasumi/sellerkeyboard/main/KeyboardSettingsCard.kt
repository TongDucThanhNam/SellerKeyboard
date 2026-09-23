package com.terasumi.sellerkeyboard.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.terasumi.sellerkeyboard.settings.KeyboardHeightPreset
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import com.terasumi.sellerkeyboard.settings.KeyboardThemeMode
import com.terasumi.sellerkeyboard.settings.OneHandedSide

@Composable
fun KeyboardSettingsCard(
    preferences: KeyboardPreferences,
    onChange: (KeyboardPreferences) -> Unit,
    onReset: () -> Unit,
    onClearPersonalDictionary: () -> Unit,
    personalWords: Set<String>,
    onAddPersonalWord: (String) -> Unit,
    onRemovePersonalWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newPersonalWord by remember { mutableStateOf("") }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Keyboard preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ChoiceRow(
                label = "Theme",
                values = KeyboardThemeMode.entries,
                selected = preferences.themeMode,
                onSelect = { onChange(preferences.copy(themeMode = it)) }
            )
            ChoiceRow(
                label = "Height",
                values = KeyboardHeightPreset.entries,
                selected = preferences.heightPreset,
                onSelect = { onChange(preferences.copy(heightPreset = it)) }
            )
            ChoiceRow(
                label = "One-handed",
                values = OneHandedSide.entries,
                selected = preferences.oneHandedSide,
                onSelect = { onChange(preferences.copy(oneHandedSide = it)) }
            )
            ChoiceRow(
                label = "Language",
                values = KeyboardLanguageMode.entries,
                selected = preferences.languageMode,
                onSelect = { onChange(preferences.copy(languageMode = it)) }
            )
            Text("Toolbar order", style = MaterialTheme.typography.labelLarge)
            KeyboardPreferences.DEFAULT_TOOLBAR_ORDER.forEach { tool ->
                val enabled = tool in preferences.toolbarOrder
                val index = preferences.toolbarOrder.indexOf(tool)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tool.replaceFirstChar(Char::uppercase),
                        modifier = Modifier.weight(1f)
                    )
                    if (enabled) {
                        TextButton(
                            enabled = index > 0,
                            onClick = {
                                val reordered = preferences.toolbarOrder.toMutableList()
                                reordered[index] = reordered[index - 1].also { reordered[index - 1] = tool }
                                onChange(preferences.copy(toolbarOrder = reordered))
                            }
                        ) { Text("↑") }
                        TextButton(
                            enabled = index in 0 until preferences.toolbarOrder.lastIndex,
                            onClick = {
                                val reordered = preferences.toolbarOrder.toMutableList()
                                reordered[index] = reordered[index + 1].also { reordered[index + 1] = tool }
                                onChange(preferences.copy(toolbarOrder = reordered))
                            }
                        ) { Text("↓") }
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            val order = if (checked) preferences.toolbarOrder + tool
                            else preferences.toolbarOrder - tool
                            onChange(preferences.copy(toolbarOrder = order))
                        }
                    )
                }
            }
            HorizontalDivider()
            PreferenceSwitch("Number row", preferences.numberRow) {
                onChange(preferences.copy(numberRow = it))
            }
            PreferenceSwitch("Key borders", preferences.keyBorders) {
                onChange(preferences.copy(keyBorders = it))
            }
            PreferenceSwitch("High contrast", preferences.highContrast) {
                onChange(preferences.copy(highContrast = it))
            }
            PreferenceSwitch("Popup preview", preferences.popupPreview) {
                onChange(preferences.copy(popupPreview = it))
            }
            PreferenceSwitch("Key sound", preferences.soundEnabled) {
                onChange(preferences.copy(soundEnabled = it))
            }
            PreferenceSwitch("Haptic feedback", preferences.hapticEnabled) {
                onChange(preferences.copy(hapticEnabled = it))
            }
            PreferenceSwitch("Learn personal words", preferences.learningEnabled) {
                onChange(preferences.copy(learningEnabled = it))
            }
            Text(
                text = "Personal dictionary",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newPersonalWord,
                    onValueChange = { newPersonalWord = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Word") }
                )
                Button(
                    onClick = {
                        onAddPersonalWord(newPersonalWord)
                        newPersonalWord = ""
                    },
                    enabled = newPersonalWord.isNotBlank()
                ) {
                    Text("Add")
                }
            }
            if (personalWords.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(personalWords.sorted(), key = { it }) { word ->
                        FilterChip(
                            selected = false,
                            onClick = { onRemovePersonalWord(word) },
                            label = { Text("$word ×") }
                        )
                    }
                }
            }
            OutlinedButton(onClick = onClearPersonalDictionary) {
                Text("Clear personal dictionary")
            }
            OutlinedButton(onClick = onReset) {
                Text("Reset keyboard preferences")
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T : Enum<T>> ChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(values, key = { it.name }) { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = {
                        Text(value.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase))
                    }
                )
            }
        }
    }
}
