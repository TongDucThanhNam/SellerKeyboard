package com.terasumi.sellerkeyboard.keyboard

import android.content.ClipDescription
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import android.view.inputmethod.EditorInfo
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
import androidx.core.view.inputmethod.EditorInfoCompat
import com.terasumi.sellerkeyboard.database.Snippets
import com.terasumi.sellerkeyboard.main.fetchDataFromSQLite
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor

@Composable
fun SnippetsKeyboard(
    myColor: Array<Color>,
    context: Context,
    isLandscape: Boolean,
) {

    val listSnippets = remember { mutableStateOf(listOf<Snippets>()) }

    LaunchedEffect(context) {
        listSnippets.value = fetchDataFromSQLite(context)
    }

    Box(
        modifier =
        if (isLandscape)
            Modifier
                .height(164.dp)
                .padding(6.dp)
        else Modifier
            .height(300.dp)
            .padding(6.dp)
    ) {
        //Gridview of Keyboard
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
        ) {
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
    val context = LocalContext.current
    SnippetsKeyboard(myColor, context, false)
}

@Preview(showSystemUi = true, device = "spec:width=1280dp,height=800dp,dpi=480")
@Composable
fun SnippetKeyboardLandscapePreview() {
    val myColor = LightCustomColor
    val context = LocalContext.current
    SnippetsKeyboard(myColor, context, true)
}