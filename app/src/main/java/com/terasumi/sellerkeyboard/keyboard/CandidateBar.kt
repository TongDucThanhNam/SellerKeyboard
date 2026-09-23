package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import com.terasumi.sellerkeyboard.suggestion.TextCandidate

@Composable
fun CandidateBar(
    candidates: List<TextCandidate>,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onCandidate: (TextCandidate) -> Unit,
    onDisplayed: () -> Unit = {}
) {
    SideEffect(onDisplayed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(candidates, key = { "${it.kind}:${it.text}" }) { candidate ->
                Text(
                    text = candidate.text,
                    color = myColor.iconColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics { contentDescription = "Candidate ${candidate.text}" }
                        .clickable { onCandidate(candidate) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
