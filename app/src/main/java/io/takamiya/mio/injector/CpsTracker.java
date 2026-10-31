package io.takamiya.mio.injector;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.utils.PDebug;

public class CpsTracker implements KeyInjector {

    private static final String TAG = "CpsTracker";
    private static final long WINDOW_NS = 1_000_000_000L;
    private static final long UPDATE_INTERVAL_MS = 100;
    private static final int MAX_HISTORY = 20; // 2 seconds at 100ms intervals

    public interface Listener {
        void onCpsChanged(float cps, List<Float> history);
    }

    private KeyInjector delegate;
    private final LinkedList<long[]> window = new LinkedList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = this::tick;

    private Listener listener;
    private float lastReportedCps = -1f;
    private volatile boolean running = false;
    private final List<Float> cpsHistory = new ArrayList<>();

    public CpsTracker(KeyInjector delegate) {
        this.delegate = delegate;
        PDebug.d(TAG, "constructor: delegate=" + (delegate != null ? delegate.getName() : "null"));
    }

    public void setDelegate(KeyInjector delegate) {
        this.delegate = delegate;
        PDebug.d(TAG, "setDelegate: " + (delegate != null ? delegate.getName() : "null"));
        reset();
    }

    public void setOnCpsChangedListener(Listener listener) {
        this.listener = listener;
        PDebug.d(TAG, "setOnCpsChangedListener: " + (listener != null ? "set" : "null"));
    }

    public void start() {
        if (running) return;
        running = true;
        mainHandler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS);
        PDebug.d(TAG, "start");
    }

    public void stop() {
        running = false;
        mainHandler.removeCallbacks(updateRunnable);
        PDebug.d(TAG, "stop");
    }

    public float getCps() {
        long now = System.nanoTime();
        long cutoff = now - WINDOW_NS;
        long totalChars = 0;
        long earliest = now;
        long latest = 0;
        synchronized (window) {
            Iterator<long[]> it = window.iterator();
            while (it.hasNext()) {
                long[] entry = it.next();
                if (entry[0] < cutoff) {
                    it.remove();
                } else {
                    totalChars += entry[1];
                    if (entry[0] < earliest) earliest = entry[0];
                    if (entry[0] > latest) latest = entry[0];
                }
            }
            if (totalChars == 0) {
                return 0f;
            }
            float elapsedSec = (latest - earliest) / 1_000_000_000f;
            if (elapsedSec < 0.001f) {
                return totalChars / 0.001f;
            }
            return totalChars / elapsedSec;
        }
    }

    public void reset() {
        synchronized (window) {
            window.clear();
        }
        cpsHistory.clear();
        lastReportedCps = -1f;
        PDebug.d(TAG, "reset");
    }

    private void record(int charCount) {
        long now = System.nanoTime();
        synchronized (window) {
            window.addLast(new long[]{now, charCount});
        }
        PDebug.d(TAG, "record: count=" + charCount + ", windowSize=" + window.size());
    }

    /** 供DSL 框架等外部注入计数（脚本按键动作算入CPS，不触发注入） */
    public void recordDslInject(int charCount) {
        if (charCount <= 0) return;
        record(charCount);
    }

    private void tick() {
        if (!running) return;
        float cps = Math.max(0f, Math.min(50f, getCps()));
        cpsHistory.add(cps);
        if (cpsHistory.size() > MAX_HISTORY) {
            cpsHistory.remove(0);
        }
        if (listener != null) {
            lastReportedCps = cps;
            listener.onCpsChanged(cps, new ArrayList<>(cpsHistory));
        }
        mainHandler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS);
    }

    @Override
    public void inputText(String text) throws InjectionException {
        delegate.inputText(text);
        if (text != null) {
            record(text.length());
        }
    }

    @Override
    public void inputText(String text, long keyHoldDurationMs) throws InjectionException {
        delegate.inputText(text, keyHoldDurationMs);
        if (text != null) {
            record(text.length());
        }
    }

    @Override
    public void pressKey(int keyCode) throws InjectionException {
        delegate.pressKey(keyCode);
        record(1);
    }

    @Override
    public void pressKey(int keyCode, long holdDurationMs) throws InjectionException {
        delegate.pressKey(keyCode, holdDurationMs);
        record(1);
    }

    @Override
    public void pressKey(int... keyCodes) throws InjectionException {
        delegate.pressKey(keyCodes);
        record(keyCodes.length);
    }

    @Override
    public void pressKeyCombo(int keyCode, int... modifierKeyCodes) throws InjectionException {
        delegate.pressKeyCombo(keyCode, modifierKeyCodes);
        record(1);
    }

    @Override
    public void pressKeyCombo(int keyCode, long holdDurationMs, int... modifierKeyCodes) throws InjectionException {
        delegate.pressKeyCombo(keyCode, holdDurationMs, modifierKeyCodes);
        record(1);
    }

    @Override
    public void keyDown(int keyCode) throws InjectionException {
        delegate.keyDown(keyCode);
        record(1);
    }

    @Override
    public void keyUp(int keyCode) throws InjectionException {
        delegate.keyUp(keyCode);
    }

    @Override
    public void longPress(int keyCode, long durationMs) throws InjectionException {
        delegate.longPress(keyCode, durationMs);
        record(1);
    }

    @Override
    public void delay(long ms) throws InjectionException {
        delegate.delay(ms);
    }

    @Override
    public void repeatKey(long intervalMs, int keyCode) throws InjectionException {
        delegate.repeatKey(intervalMs, keyCode);
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public ServiceState getServiceState() {
        return delegate.getServiceState();
    }

    @Override
    public void restoreSessionIme() {
        delegate.restoreSessionIme();
    }

    @Override
    public String getName() {
        return delegate.getName() + "(Cps)";
    }
}
