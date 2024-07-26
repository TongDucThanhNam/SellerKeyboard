package com.example.sellerkeyboard;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.View;
import android.view.inputmethod.InputConnection;

public class CustomKeyboardApp extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    @Override
    public View onCreateInputView() {
        KeyboardView keyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.custom_keyboard_layout, null);
        Keyboard keyboard = new Keyboard(this, R.xml.custom_keyboard);
        keyboardView.setKeyboard(keyboard);

        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    @Override
    public void onPress(int i) {
    }

    @Override
    public void onRelease(int i) {
    }

    @Override
    public void onKey(int i, int[] ints) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null){
            return;
        }
        inputConnection.commitText(String.valueOf((char) i),1);
    }

    @Override
    public void onText(CharSequence charSequence) {
    }

    @Override
    public void swipeLeft() {
    }

    @Override
    public void swipeRight() {
    }

    @Override
    public void swipeDown() {
    }

    @Override
    public void swipeUp() {
    }
}