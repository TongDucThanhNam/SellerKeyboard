package com.terasumi.sellerkeyboard.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

@Composable
fun snippetFormFieldColors(): TextFieldColors {
    val colorScheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colorScheme.onSurface,
        unfocusedTextColor = colorScheme.onSurface,
        disabledTextColor = colorScheme.onSurface.copy(alpha = 0.38f),
        errorTextColor = colorScheme.onSurface,
        focusedLabelColor = colorScheme.onSurface,
        unfocusedLabelColor = colorScheme.onSurfaceVariant,
        disabledLabelColor = colorScheme.onSurface.copy(alpha = 0.38f),
        errorLabelColor = colorScheme.error,
        focusedPlaceholderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        unfocusedPlaceholderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        disabledPlaceholderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        errorPlaceholderColor = colorScheme.error,
        focusedTrailingIconColor = colorScheme.onSurfaceVariant,
        unfocusedTrailingIconColor = colorScheme.onSurfaceVariant,
        disabledTrailingIconColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        errorTrailingIconColor = colorScheme.error,
        cursorColor = colorScheme.primary,
        errorCursorColor = colorScheme.error,
        focusedBorderColor = colorScheme.primary,
        unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.6f),
        disabledBorderColor = colorScheme.outline.copy(alpha = 0.4f),
        errorBorderColor = colorScheme.error,
        focusedContainerColor = colorScheme.surface,
        unfocusedContainerColor = colorScheme.surface,
        disabledContainerColor = colorScheme.surface,
        errorContainerColor = colorScheme.surface
    )
}
