package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import com.terasumi.sellerkeyboard.voice.VoiceTypingError
import com.terasumi.sellerkeyboard.voice.VoiceTypingState

@Composable
fun VoiceReviewBar(
    state: VoiceTypingState,
    myColor: KeyboardColors,
    onInsert: (String) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit
) {
    val status = state.accessibleStatus()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = status
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state is VoiceTypingState.Processing) {
            CircularProgressIndicator(modifier = Modifier.height(24.dp))
        }
        Text(
            text = status,
            color = myColor.iconColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        when (state) {
            VoiceTypingState.Idle -> Unit
            VoiceTypingState.RequestingPermission -> {
                TextButton(onClick = onDiscard) { Text("Close") }
            }
            is VoiceTypingState.Listening,
            is VoiceTypingState.Processing -> {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            is VoiceTypingState.Result -> {
                TextButton(onClick = { onInsert(state.transcript) }) { Text("Insert") }
                TextButton(onClick = onDiscard) { Text("Discard") }
            }
            is VoiceTypingState.Error -> {
                TextButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onDiscard) { Text("Close") }
            }
        }
    }
}

private fun VoiceTypingState.accessibleStatus(): String = when (this) {
    VoiceTypingState.Idle -> "Voice input idle"
    VoiceTypingState.RequestingPermission -> "Microphone permission is required"
    is VoiceTypingState.Listening -> {
        val provider = if (usingOnDevice) "on device" else "provider may use the network"
        partialTranscript?.takeIf(String::isNotBlank)?.let { "Listening $provider: $it" }
            ?: "Listening $provider"
    }
    is VoiceTypingState.Processing -> if (usingOnDevice) {
        "Processing speech on device"
    } else {
        "Processing speech; provider may use the network"
    }
    is VoiceTypingState.Result -> {
        val provider = if (usingOnDevice) "on-device result" else "provider result"
        "Review $provider: $transcript"
    }
    is VoiceTypingState.Error -> when (error) {
        VoiceTypingError.Disabled -> "Voice input is disabled in settings"
        VoiceTypingError.DisabledForField -> "Voice input is unavailable for this field"
        VoiceTypingError.PermissionDenied -> "Microphone permission was denied"
        VoiceTypingError.NoRecognitionService -> "No speech recognition provider is available"
        VoiceTypingError.Busy -> "Speech recognition is busy"
        VoiceTypingError.NoMatch -> "No speech was recognized"
        VoiceTypingError.Network -> "Speech recognition network error"
        VoiceTypingError.Client -> "Speech recognition client error"
        VoiceTypingError.Unknown -> "Speech recognition failed"
    }
}
