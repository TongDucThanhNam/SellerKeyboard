package com.terasumi.sellerkeyboard

import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SnippetsKeyboard(listSnippets: List<Snippets>) {
    val context = LocalContext.current

    Box(modifier = Modifier.height(300.dp)) {
        //Gridview of Keyboard
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
        ) {
            //Voice Input Button
            item {
                FilledTonalButton(
                    onClick = {
                        // Input Method Service
                        (context as SellerKeyboard).mVoiceRecognitionTrigger!!.startVoiceRecognition(
                            context.imeLanguage
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Voice",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        fontStyle = MaterialTheme.typography.bodySmall.fontStyle,
                        fontWeight = MaterialTheme.typography.bodySmall.fontWeight
                    )
                }
            }

            items(listSnippets.size) { index ->
                FilledTonalButton(
                    onClick = {
                        Thread {
                            try {
                                // Input Method Service
                                Log.d("Snippet", "Snippet Clicked ${listSnippets[index].title}")
                                Log.d("Snippet", "Snippet Clicked ${listSnippets[index].content}")
                                Log.d("Snippet", "Snippet Clicked ${listSnippets[index].imageUrls}")

                                //InputConnection
                                val inputConnection =
                                    (context as SellerKeyboard).currentInputConnection
                                inputConnection?.commitText(listSnippets[index].content, 1)
                                inputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEND)

                                Thread.sleep(500)

                                //doCommitContent
                                listSnippets[index].imageUrls.forEach { imageUrl ->
                                    context.doCommitContent(imageUrl)
                                    Thread.sleep(500)
                                }

                                Log.d("Snippet", "Snippet Clicked ${listSnippets[index].title}")
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            }
                        }.start()

                    },
                    modifier = Modifier
                        .fillMaxWidth()


                ) {
                    Text(
                        text = listSnippets[index].title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
//                                    modifier = Modifier.fillMaxWidth(),
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        fontStyle = MaterialTheme.typography.bodySmall.fontStyle,
                        fontWeight = MaterialTheme.typography.bodySmall.fontWeight
                    )
                }
            }
        }
    }
}

//Preview
@Preview()
@Composable
fun SnippetKeyboardPreview() {
    SnippetsKeyboard(listSnippets = listOf(Snippets(0, "Title", "Content", listOf())))
}