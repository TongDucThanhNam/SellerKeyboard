package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import android.view.inputmethod.InputConnection
import com.terasumi.sellerkeyboard.service.SellerKeyboard

internal fun Context.currentInputConnectionOrNull(): InputConnection? {
    return (this as? SellerKeyboard)?.currentInputConnection
}

internal fun Context.performKeyboardHaptic() {
    (this as? SellerKeyboard)?.performKeyHapticFeedback()
}
