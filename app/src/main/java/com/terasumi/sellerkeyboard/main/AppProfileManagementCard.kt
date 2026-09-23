package com.terasumi.sellerkeyboard.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.profile.AppProfileBinding
import com.terasumi.sellerkeyboard.profile.KeyboardProfile
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import com.terasumi.sellerkeyboard.settings.KeyboardHeightPreset
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.settings.KeyboardThemeMode
import java.util.UUID

@Composable
fun AppProfileManagementCard(
    bindings: List<AppProfileBinding>,
    onSaveAndBind: (KeyboardProfile, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var numberRow by remember { mutableStateOf(true) }
    var languageMode by remember { mutableStateOf(KeyboardLanguageMode.VIETNAMESE_ENGLISH) }
    var themeMode by remember { mutableStateOf(KeyboardThemeMode.SYSTEM) }
    var heightPreset by remember { mutableStateOf(KeyboardHeightPreset.STANDARD) }
    var preferredReplyPackId by remember { mutableStateOf("") }
    var enabledTools by remember {
        mutableStateOf(KeyboardPreferences.DEFAULT_TOOLBAR_ORDER.toSet())
    }
    var toolOrder by remember {
        mutableStateOf(KeyboardPreferences.DEFAULT_TOOLBAR_ORDER)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Per-app keyboard profiles")
            Text("Enter the exact package identifier shown by the target app; no app inventory permission is used.")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Profile name") }
            )
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("App package (for example com.example.seller)") }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Private profile", modifier = Modifier.weight(1f))
                Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Number row", modifier = Modifier.weight(1f))
                Switch(checked = numberRow, onCheckedChange = { numberRow = it })
            }
            OutlinedTextField(
                value = preferredReplyPackId,
                onValueChange = { preferredReplyPackId = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Preferred reply pack (optional)") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        languageMode = KeyboardLanguageMode.entries[
                            (languageMode.ordinal + 1) % KeyboardLanguageMode.entries.size
                        ]
                    }
                ) { Text("Language: ${languageMode.name}") }
                OutlinedButton(
                    onClick = {
                        themeMode = KeyboardThemeMode.entries[
                            (themeMode.ordinal + 1) % KeyboardThemeMode.entries.size
                        ]
                    }
                ) { Text("Theme: ${themeMode.name}") }
            }
            OutlinedButton(
                onClick = {
                    heightPreset = KeyboardHeightPreset.entries[
                        (heightPreset.ordinal + 1) % KeyboardHeightPreset.entries.size
                    ]
                }
            ) { Text("Height: ${heightPreset.name}") }
            Text("Profile tools")
            toolOrder.forEachIndexed { index, toolId ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(toolId, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        enabled = index > 0,
                        onClick = {
                            val reordered = toolOrder.toMutableList()
                            reordered[index - 1] = toolId
                            reordered[index] = toolOrder[index - 1]
                            toolOrder = reordered
                        }
                    ) { Text("↑") }
                    OutlinedButton(
                        enabled = index < toolOrder.lastIndex,
                        onClick = {
                            val reordered = toolOrder.toMutableList()
                            reordered[index + 1] = toolId
                            reordered[index] = toolOrder[index + 1]
                            toolOrder = reordered
                        }
                    ) { Text("↓") }
                    Switch(
                        checked = toolId in enabledTools,
                        onCheckedChange = { enabled ->
                            enabledTools = if (enabled) enabledTools + toolId else enabledTools - toolId
                        }
                    )
                }
            }
            Button(
                enabled = name.isNotBlank() && packageName.contains('.'),
                onClick = {
                    val profile = KeyboardProfile(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        toolbarOrder = toolOrder,
                        preferredReplyPackId = preferredReplyPackId.trim().ifBlank { null },
                        languageMode = languageMode,
                        numberRow = numberRow,
                        themeOverride = themeMode,
                        heightOverride = heightPreset,
                        enabledSellerTools = enabledTools,
                        isPrivate = isPrivate
                    )
                    onSaveAndBind(profile, packageName.trim())
                    name = ""
                    packageName = ""
                    preferredReplyPackId = ""
                    isPrivate = false
                }
            ) { Text("Save and assign") }
            bindings.forEach { binding ->
                Text("${binding.packageName} → ${binding.profileId}")
            }
        }
    }
}
