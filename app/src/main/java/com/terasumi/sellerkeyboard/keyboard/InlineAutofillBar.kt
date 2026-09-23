package com.terasumi.sellerkeyboard.keyboard

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.terasumi.sellerkeyboard.autofill.InlineAutofillRequestConfig
import com.terasumi.sellerkeyboard.autofill.InlineAutofillSuggestion
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@Composable
fun InlineAutofillBar(
    suggestions: List<InlineAutofillSuggestion<View>>,
    myColor: KeyboardColors,
    isLandscape: Boolean
) {
    if (suggestions.isEmpty()) {
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                if (isLandscape) {
                    InlineAutofillRequestConfig.BAR_HEIGHT_LANDSCAPE_DP.dp
                } else {
                    InlineAutofillRequestConfig.BAR_HEIGHT_PORTRAIT_DP.dp
                }
            )
            .background(myColor.keyboardBackground.copy(alpha = 0.98f))
            .padding(horizontal = if (isLandscape) 6.dp else 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(suggestions, key = { suggestion -> suggestion.id }) { suggestion ->
                InlineAutofillChip(
                    view = suggestion.content,
                    isLandscape = isLandscape
                )
            }
        }
    }
}

@Composable
private fun InlineAutofillChip(
    view: View,
    isLandscape: Boolean
) {
    AndroidView(
        factory = {
            detachFromParent(view)
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            view
        },
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 112.dp, max = if (isLandscape) 320.dp else 260.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}

private fun detachFromParent(view: View) {
    (view.parent as? ViewGroup)?.removeView(view)
}
