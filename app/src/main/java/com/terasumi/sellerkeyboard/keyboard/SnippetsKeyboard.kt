package com.terasumi.sellerkeyboard.keyboard

import android.content.ClipDescription
import android.content.ContentValues.TAG
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.core.view.inputmethod.EditorInfoCompat
import com.terasumi.sellerkeyboard.database.Snippets
import com.terasumi.sellerkeyboard.main.fetchDataFromSQLite
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor

@Composable
fun SnippetsKeyboard(myColor: Array<Color>) {
    val context = LocalContext.current

    val listSnippets = remember { mutableStateOf(listOf<Snippets>()) }

    LaunchedEffect(context) {
        listSnippets.value = fetchDataFromSQLite(context)
    }

    Box(
        modifier = Modifier
            .height(300.dp)
            .padding(4.dp)
    ) {
        //Gridview of Keyboard
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
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
                    colors = ButtonDefaults.buttonColors(
                        contentColor = myColor[5],
                        containerColor = myColor[4]
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 6.dp,
                        vertical = 6.dp
                    ), // Custom content padding
                    content = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice",
                                    tint = myColor[5],
                                    modifier = Modifier
                                        .size(18.dp)
                                )

                                Text(
                                    text = "Voice",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontStyle = MaterialTheme.typography.labelSmall.fontStyle,
                                    fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
                                    modifier = Modifier
                                )
                            }
                        )
                    },

                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
            //Delete Button
            item {
                FilledTonalButton(

                    onClick = {
                        // Input Method Service
                        (context as SellerKeyboard).currentInputConnection?.deleteSurroundingText(
                            1,
                            0
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        contentColor = myColor[5],
                        containerColor = myColor[4]
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 6.dp,
                        vertical = 6.dp
                    ), // Custom content padding
                    content = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = myColor[5],
                                    modifier = Modifier
                                        .size(18.dp)
                                )

                                Text(
                                    text = "Delete",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontStyle = MaterialTheme.typography.labelSmall.fontStyle,
                                    fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
                                    modifier = Modifier
                                )
                            }
                        )
                    },

                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            //Sync Button
            item {
                FilledTonalButton(
                    colors = ButtonDefaults.buttonColors(
                        contentColor = myColor[5],
                        containerColor = myColor[4]
                    ),
                    onClick = {
                        // Sync -> Refetch Snippets
                    },
                    contentPadding = PaddingValues(
                        horizontal = 6.dp,
                        vertical = 6.dp
                    ), // Custom content padding
                    content = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Delete",
                                    tint = myColor[5], modifier = Modifier
                                        .size(18.dp)
                                )

                                Text(
                                    text = "Sync",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontStyle = MaterialTheme.typography.labelSmall.fontStyle,
                                    fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
                                    modifier = Modifier
                                )
                            }
                        )
                    },

                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            //Snippets
            items(listSnippets.value.size) { index ->
                FilledTonalButton(
                    colors = ButtonDefaults.buttonColors(
                        contentColor = myColor[5],
                        containerColor = myColor[4]
                    ),
                    onClick = {
                        Thread {
                            try {
                                val inputConnection =
                                    (context as SellerKeyboard).currentInputConnection
                                val currentInputEditorInfo =
                                    context.currentInputEditorInfo

                                inputConnection?.apply {
                                    commitText(listSnippets.value[index].content, 1)
                                    performEditorAction(EditorInfo.IME_ACTION_SEND)
                                }

                                val mimeTypes =
                                    EditorInfoCompat.getContentMimeTypes(currentInputEditorInfo)
                                val isMimeSupported = mimeTypes.any {
                                    ClipDescription.compareMimeTypes(it, "image/jpg")
                                            || ClipDescription.compareMimeTypes(it, "image/jpeg")
                                            || ClipDescription.compareMimeTypes(it, "image/*")
                                }

                                if (isMimeSupported) {
                                    listSnippets.value[index].imageUrls.forEach { imageUrl ->
                                        Thread.sleep(1000)
                                        inputConnection?.apply {
                                            context.doCommitContent(imageUrl)
                                            Thread.sleep(1000)
                                        }
                                    }
                                } else {
                                    Log.i(TAG, "MIME type is not supported")
                                }
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            }
                        }.start()
                    },
                    shape = MaterialTheme.shapes.small,

                    contentPadding = PaddingValues(
                        horizontal = 6.dp,
                        vertical = 6.dp
                    ), // Custom content padding

                    content = {
                        Text(
                            text = listSnippets.value[index].title,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontStyle = MaterialTheme.typography.labelSmall.fontStyle,
                            fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
            }
        }
    }
}

//Preview
@Preview
@Composable
fun SnippetKeyboardPreview() {
    val myColor = LightCustomColor
    SnippetsKeyboard(myColor)
}