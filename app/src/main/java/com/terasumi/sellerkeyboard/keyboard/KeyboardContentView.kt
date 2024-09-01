package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView

class KeyboardContentView(context: Context) : AbstractComposeView(context) {
    @Composable
    override fun Content() {
        KeyboardContent()
    }
}