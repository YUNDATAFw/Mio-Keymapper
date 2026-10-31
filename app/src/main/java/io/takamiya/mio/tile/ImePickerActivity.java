package io.takamiya.mio.tile;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.InputMethodManager;

import cn.panda.utils.library.AppLogger;

public class ImePickerActivity extends Activity {

    private static final String TAG = "ImePickerActivity";
    private static final long SHOW_DELAY_MS = 200;
    private static final long MAX_LIFETIME_MS = 20_000;
    private static final int MAX_ATTEMPTS = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean picking = false;
    private boolean chosen = false;
    private int attempts = 0;

    private final Runnable showPickerTask = new Runnable() {
        @Override
        public void run() {
            if (chosen || isFinishing() || isDestroyed()) {
                return;
            }
            if (picking) {
                return;
            }
            if (attempts >= MAX_ATTEMPTS) {
                AppLogger.w(TAG, "picker show attempts exhausted, finishing");
                finish();
                return;
            }
            attempts++;
            try {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showInputMethodPicker();
                picking = true;
                AppLogger.d(TAG, "input method picker shown, attempt=" + attempts);
            } catch (Exception e) {
                AppLogger.w(TAG, "show input method picker failed: " + e.getMessage());
                handler.postDelayed(showPickerTask, SHOW_DELAY_MS);
            }
        }
    };

    private final Runnable finishTask = () -> {
        if (!isFinishing() && !isDestroyed()) {
            AppLogger.w(TAG, "picker lifetime exceeded, finishing");
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler.postDelayed(finishTask, MAX_LIFETIME_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(showPickerTask, SHOW_DELAY_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(showPickerTask);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (chosen) {
                AppLogger.d(TAG, "picker dismissed, finishing");
                finish();
            } else if (!picking) {
                handler.postDelayed(showPickerTask, SHOW_DELAY_MS);
            }
        } else if (picking) {
            chosen = true;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(showPickerTask);
        handler.removeCallbacks(finishTask);
        super.onDestroy();
    }
}
