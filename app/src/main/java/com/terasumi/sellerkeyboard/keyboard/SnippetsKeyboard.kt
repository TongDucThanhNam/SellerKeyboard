package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.database.Snippets
import com.terasumi.sellerkeyboard.main.fetchDataFromSQLite
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SnippetsKeyboard(
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onAction: (KeyboardAction) -> Unit
) {
    val context = LocalContext.current
    val listSnippets = remember { mutableStateOf(listOf<Snippets>()) }

    LaunchedEffect(Unit) {
        listSnippets.value = withContext(Dispatchers.IO) {
            fetchDataFromSQLite(context)
        }
    }

    Box(
        modifier = Modifier
            .height(if (isLandscape) 164.dp else 300.dp)
            .padding(6.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(listSnippets.value.size) { index ->
                val snippet = listSnippets.value[index]
                FilledTonalButton(
                    colors = ButtonDefaults.buttonColors(
                        contentColor = myColor.iconColor,
                        containerColor = myColor.spaceBarBackground
                    ),
                    onClick = {
                        onAction(KeyboardAction.KeyPress(snippet.content))
                        // Note: If image support is needed, we should add a dedicated KeyboardAction.InsertSnippet
                    },
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = snippet.title,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SnippetKeyboardPreview() {
    SnippetsKeyboard(LightKeyboardColors, false, onAction = {})
}