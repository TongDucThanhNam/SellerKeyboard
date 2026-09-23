package com.terasumi.sellerkeyboard.voice.fixture;

import android.content.Intent;
import android.content.ContextParams;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.List;

/** Instrumentation-only speech provider exercising the real SpeechRecognizer binder. */
public class DeterministicRecognitionService extends RecognitionService {
    public static final String LANGUAGE_RESULT = "x-sellerkeyboard-result";
    public static final String LANGUAGE_NETWORK_ERROR = "x-sellerkeyboard-network";
    public static final String LANGUAGE_NO_MATCH = "x-sellerkeyboard-no-match";
    public static final String PARTIAL_TRANSCRIPT = "xin chào";
    public static final String FINAL_TRANSCRIPT = "xin chào khách hàng";

    private static final long PARTIAL_DELAY_MILLIS = 200L;
    private static final long PROCESSING_DELAY_MILLIS = 500L;
    private static final long RESULT_DELAY_MILLIS = 800L;
    private static final int MAX_CONCURRENT_TEST_SESSIONS = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> pendingCallbacks = new ArrayList<>();

    @Override
    public int getMaxConcurrentSessionsCount() {
        return MAX_CONCURRENT_TEST_SESSIONS;
    }

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        clearPendingCallbacks();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createAttributionContext(callback);
        }
        invokeRemote(() -> callback.readyForSpeech(new Bundle()));
        String language = recognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        if (LANGUAGE_NETWORK_ERROR.equals(language)) {
            schedule(RESULT_DELAY_MILLIS, () ->
                    invokeRemote(() -> callback.error(SpeechRecognizer.ERROR_NETWORK)));
        } else if (LANGUAGE_NO_MATCH.equals(language)) {
            schedule(PROCESSING_DELAY_MILLIS, () ->
                    invokeRemote(callback::endOfSpeech));
            schedule(RESULT_DELAY_MILLIS, () ->
                    invokeRemote(() -> callback.error(SpeechRecognizer.ERROR_NO_MATCH)));
        } else {
            schedule(PARTIAL_DELAY_MILLIS, () ->
                    invokeRemote(() -> callback.partialResults(resultsBundle(PARTIAL_TRANSCRIPT))));
            schedule(PROCESSING_DELAY_MILLIS, () ->
                    invokeRemote(callback::endOfSpeech));
            schedule(RESULT_DELAY_MILLIS, () ->
                    invokeRemote(() -> callback.results(resultsBundle(FINAL_TRANSCRIPT))));
        }
    }

    @Override
    protected void onStopListening(Callback callback) {
        clearPendingCallbacks();
    }

    @Override
    protected void onCancel(Callback callback) {
        clearPendingCallbacks();
        invokeRemote(() -> callback.error(SpeechRecognizer.ERROR_CLIENT));
    }

    @Override
    public void onDestroy() {
        clearPendingCallbacks();
        super.onDestroy();
    }

    private void schedule(long delayMillis, Runnable action) {
        Runnable pending = new Runnable() {
            @Override
            public void run() {
                pendingCallbacks.remove(this);
                action.run();
            }
        };
        pendingCallbacks.add(pending);
        handler.postDelayed(pending, delayMillis);
    }

    private void clearPendingCallbacks() {
        for (Runnable pending : pendingCallbacks) {
            handler.removeCallbacks(pending);
        }
        pendingCallbacks.clear();
    }

    private static Bundle resultsBundle(String transcript) {
        Bundle bundle = new Bundle();
        ArrayList<String> results = new ArrayList<>();
        results.add(transcript);
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, results);
        return bundle;
    }

    private void createAttributionContext(Callback callback) {
        createContext(new ContextParams.Builder()
                .setNextAttributionSource(callback.getCallingAttributionSource())
                .build());
    }

    private static void invokeRemote(RemoteCall call) {
        try {
            call.run();
        } catch (RemoteException ignored) {
            // The client may have canceled or destroyed its recognizer.
        }
    }

    private interface RemoteCall {
        void run() throws RemoteException;
    }
}
