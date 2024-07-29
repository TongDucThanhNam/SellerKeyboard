//package com.example.sellerkeyboard;
//
//import android.content.Context;
//import android.inputmethodservice.InputMethodService;
//import android.widget.AdapterView;
//import android.widget.Button;
//import android.widget.LinearLayout;
//
//import com.google.android.filament.View;
//
//import java.util.List;
//
//public class CandidateView extends LinearLayout {
//
//    private InputMethodService mService;
//
//    public CandidateView(Context context) {
//        super(context);
//        setOrientation(HORIZONTAL); // Arrange buttons horizontally
//    }
//
//    public void setService(InputMethodService service) {
//        mService = service;
//    }
//
//    public void setCandidates(List<String> candidates) {
//        removeAllViews(); // Clear previous candidates
//
//        for (final String candidate : candidates) {
//            Button button = new Button(getContext());
//            button.setText(candidate);
//            button.setOnClickListener(v -> mService.getCurrentInputConnection().commitText(candidate, 1));
//            addView(button);
//        }
//    }
//
//    private AdapterView.OnItemClickListener mOnItemClickListener;
//
//    public void setOnItemClickListener(AdapterView.OnItemClickListener onItemClickListener) {
//        mOnItemClickListener = onItemClickListener;
//    }
//}