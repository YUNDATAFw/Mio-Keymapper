package io.takamiya.mio.injector;

import android.os.SystemClock;
import android.view.KeyEvent;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.utils.PDebug;
import io.takamiya.mio.shizuku.IShellService;
import io.takamiya.mio.shizuku.ShizukuHelper;

public class ShizukuKeyInjector implements KeyInjector {

    private static final String TAG = "ShizukuKeyInjector";
    private static final long BIND_WAIT_MS = 3000L;

    private volatile boolean repeatRunning = false;
    private Thread repeatThread;

    public static boolean isShizukuRunning() {
        boolean result = ShizukuHelper.getInstance().isRunning();
        PDebug.d(TAG, "isShizukuRunning -> " + result);
        return result;
    }

    public static boolean isPermissionGranted() {
        boolean result = ShizukuHelper.getInstance().isGranted();
        PDebug.d(TAG, "isPermissionGranted -> " + result);
        return result;
    }

    public static boolean isServiceBound() {
        boolean result = ShizukuHelper.getInstance().isServiceBound();
        PDebug.d(TAG, "isServiceBound -> " + result);
        return result;
    }

    private static volatile IShellService cachedService;
    private static volatile long cachedServiceCheckTime;

    public static void invalidateServiceCache() {
        cachedService = null;
    }

    private IShellService getService() throws InjectionException {
        long now = SystemClock.uptimeMillis();
        IShellService s = cachedService;
        if (s != null && (now - cachedServiceCheckTime) < 2000L) {
            try {
                if (isShellAlive(s)) {
                    return s;
                }
            } catch (Exception ignored) {
            }
            cachedService = null;
        }

        ShizukuHelper helper = ShizukuHelper.getInstance();
        helper.ensureBound();
        IShellService service = helper.getShellService();
        if (service != null && isShellAlive(service)) {
            cachedService = service;
            cachedServiceCheckTime = now;
            return service;
        }
        throw new InjectionException("Shizuku ShellService not connected");
    }

    private static boolean isShellAlive(IShellService service) {
        try {
            boolean alive = service.asBinder().pingBinder();
            PDebug.d(TAG, "isShellAlive -> " + alive);
            return alive;
        } catch (Exception e) {
            AppLogger.w(TAG, "isShellAlive: pingBinder failed", e);
            return false;
        }
    }

    @Override
    public void pressKey(int keyCode) throws InjectionException {
        PDebug.d(TAG, "pressKey: " + keyName(keyCode) + " (" + keyCode + ")");
        try {
            boolean ok = getService().injectKeyEvent(keyCode, 0);
            PDebug.d(TAG, "pressKey: injectKeyEvent -> " + ok);
            if (!ok) throw new InjectionException(keyCode, "injectKeyEvent returned false");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "pressKey failed: " + keyName(keyCode), e);
            throw e;
        } catch (Exception e) {
            AppLogger.e(TAG, "pressKey exception: " + keyName(keyCode), e);
            throw new InjectionException(keyCode, "Shizuku inject failed", e);
        }
    }

    @Override
    public void pressKey(int keyCode, long holdDurationMs) throws InjectionException {
        PDebug.d(TAG, "pressKey: " + keyName(keyCode) + " (" + keyCode + ")"
                + " holdDuration=" + holdDurationMs + "ms");
        try {
            boolean ok = getService().injectKeyEventWithDuration(keyCode, 0, (int) holdDurationMs);
            PDebug.d(TAG, "pressKey: injectKeyEventWithDuration -> " + ok);
            if (!ok) throw new InjectionException(keyCode, "injectKeyEventWithDuration returned false");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "pressKey failed: " + keyName(keyCode), e);
            throw e;
        } catch (Exception e) {
            AppLogger.e(TAG, "pressKey exception: " + keyName(keyCode), e);
            throw new InjectionException(keyCode, "Shizuku inject failed", e);
        }
    }

    @Override
    public void pressKey(int... keyCodes) throws InjectionException {
        if (keyCodes == null || keyCodes.length == 0) {
            PDebug.d(TAG, "pressKey: empty keyCodes, ignored");
            return;
        }
        PDebug.d(TAG, "pressKey: combo=" + keyCodesName(keyCodes));
        for (int key : keyCodes) keyDown(key);
        for (int i = keyCodes.length - 1; i >= 0; i--) keyUp(keyCodes[i]);
        PDebug.d(TAG, "pressKey: combo sent");
    }

    @Override
    public void pressKeyCombo(int keyCode, int... modifierKeyCodes) throws InjectionException {
        PDebug.d(TAG, "pressKeyCombo: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes));
        for (int attempt = 0; ; attempt++) {
            try {
                boolean ok = getService().injectCombo(keyCode, modifierKeyCodes);
                PDebug.d(TAG, "pressKeyCombo: injectCombo -> " + ok + " (attempt=" + attempt + ")");
                if (!ok) throw new InjectionException(keyCode, "injectCombo returned false");
                return;
            } catch (InjectionException e) {
                if (attempt == 0 && isShellUnreachable(e)) {
                    AppLogger.w(TAG, "Combo blocked by dead shell, rebinding and retrying once");
                    ShizukuHelper.getInstance().resetShellState();
                    continue;
                }
                AppLogger.e(TAG, "pressKeyCombo failed: " + keyName(keyCode), e);
                throw e;
            } catch (Exception e) {
                if (attempt == 0 && e instanceof android.os.DeadObjectException) {
                    AppLogger.w(TAG, "Combo blocked by dead shell, rebinding and retrying once");
                    ShizukuHelper.getInstance().resetShellState();
                    continue;
                }
                AppLogger.e(TAG, "pressKeyCombo exception: " + keyName(keyCode), e);
                throw new InjectionException(keyCode, "Shizuku combo inject failed", e);
            }
        }
    }

    @Override
    public void pressKeyCombo(int keyCode, long holdDurationMs, int... modifierKeyCodes) throws InjectionException {
        PDebug.d(TAG, "pressKeyCombo: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes)
                + " holdDuration=" + holdDurationMs + "ms");
        for (int attempt = 0; ; attempt++) {
            try {
                boolean ok = getService().injectComboWithDuration(keyCode, modifierKeyCodes, (int) holdDurationMs);
                PDebug.d(TAG, "pressKeyCombo: injectComboWithDuration -> " + ok + " (attempt=" + attempt + ")");
                if (!ok) throw new InjectionException(keyCode, "injectComboWithDuration returned false");
                return;
            } catch (InjectionException e) {
                if (attempt == 0 && isShellUnreachable(e)) {
                    AppLogger.w(TAG, "Combo blocked by dead shell, rebinding and retrying once");
                    ShizukuHelper.getInstance().resetShellState();
                    continue;
                }
                AppLogger.e(TAG, "pressKeyCombo failed: " + keyName(keyCode), e);
                throw e;
            } catch (Exception e) {
                if (attempt == 0 && e instanceof android.os.DeadObjectException) {
                    AppLogger.w(TAG, "Combo blocked by dead shell, rebinding and retrying once");
                    ShizukuHelper.getInstance().resetShellState();
                    continue;
                }
                AppLogger.e(TAG, "pressKeyCombo exception: " + keyName(keyCode), e);
                throw new InjectionException(keyCode, "Shizuku combo inject failed", e);
            }
        }
    }

    private static boolean isShellUnreachable(InjectionException e) {
        boolean result = e.getCause() instanceof android.os.DeadObjectException
                || "Shizuku ShellService not connected".equals(e.getMessage());
        PDebug.d(TAG, "isShellUnreachable: message=" + e.getMessage()
                + " cause=" + (e.getCause() != null ? e.getCause().getClass().getSimpleName() : "null")
                + " -> " + result);
        return result;
    }

    @Override
    public void inputText(String text) throws InjectionException {
        inputText(text, 0);
    }

    private static final String MIO_IME_ID = "io.takamiya.mio/.injector.MioImeService";
    private static final long IME_SWITCH_TIMEOUT_MS = 3000L;
    private static final long IME_POLL_INTERVAL_MS = 100L;
    private static final String ACTION_COMMIT_TEXT = MioImeService.ACTION_COMMIT_TEXT;
    private static final String EXTRA_TEXT = MioImeService.EXTRA_TEXT;
    private String savedImeIdForSession;

    @Override
    public void inputText(String text, long keyHoldDurationMs) throws InjectionException {
        if (text == null || text.isEmpty()) {
            PDebug.d(TAG, "inputText: empty text, ignored");
            return;
        }
        PDebug.d(TAG, "inputText: text=" + text + " (len=" + text.length() + ")"
                + " holdDuration=" + keyHoldDurationMs + "ms");
        try {
            String result = getService().execCommandWithArgs(new String[]{"input", "text", text});
            int resultLen = result != null ? result.length() : 0;
            PDebug.d(TAG, "inputText: shell result(len=" + resultLen + ") first200="
                    + (result != null ? result.substring(0, Math.min(resultLen, 200)) : "null"));
            if (result != null && resultLen > 0) {
                AppLogger.w(TAG, "inputText: shell returned output, trying IME hybrid fallback");
                inputTextViaIme(text);
                return;
            }
            PDebug.d(TAG, "inputText: done, " + text.length() + " char(s) committed");
        } catch (Exception e) {
            AppLogger.e(TAG, "inputText failed: text=" + text + ", trying IME hybrid fallback", e);
            inputTextViaIme(text);
        }
    }

    private void inputTextViaIme(String text) throws InjectionException {
        boolean hasNonAscii = false;
        for (char c : text.toCharArray()) {
            if (c > 127) { hasNonAscii = true; break; }
        }
        if (!hasNonAscii) {
            PDebug.d(TAG, "inputTextViaIme: ASCII-only text, using charToKeyCode fallback");
            inputTextFallback(text, 0);
            return;
        }
        PDebug.d(TAG, "inputTextViaIme: non-ASCII text detected");
        if (inputTextViaBroadcast(text)) {
            PDebug.d(TAG, "inputTextViaIme: broadcast succeeded, no IME switch needed");
            return;
        }
        if (savedImeIdForSession != null) {
            PDebug.d(TAG, "inputTextViaIme: already in IME session, committing directly");
            MioImeService ime = MioImeService.getInstance();
            if (ime != null && ime.hasInputConnection()) {
                ime.commitText(text);
                PDebug.d(TAG, "inputTextViaIme: text committed (session)");
                return;
            }
            AppLogger.w(TAG, "inputTextViaIme: session IME lost connection, re-switching");
        }
        if (savedImeIdForSession == null) {
            PDebug.d(TAG, "inputTextViaIme: switching to Mio IME");
            MioImeService imeCtx = MioImeService.getInstance();
            savedImeIdForSession = ImeAutoSwitchHelper.getCurrentImeId(
                    imeCtx != null ? imeCtx.getApplicationContext() : null);
            PDebug.d(TAG, "inputTextViaIme: savedImeId=" + savedImeIdForSession);
        } else {
            PDebug.d(TAG, "inputTextViaIme: re-switching to Mio IME, keeping saved=" + savedImeIdForSession);
        }
        if (!ImeAutoSwitchHelper.restoreIme(MIO_IME_ID)) {
            AppLogger.e(TAG, "inputTextViaIme: failed to switch to Mio IME");
            throw new InjectionException("Failed to switch to Mio IME");
        }
        long deadline = SystemClock.uptimeMillis() + IME_SWITCH_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            MioImeService ime = MioImeService.getInstance();
            if (ime != null && ime.hasInputConnection()) {
                PDebug.d(TAG, "inputTextViaIme: IME ready, committing text");
                ime.commitText(text);
                PDebug.d(TAG, "inputTextViaIme: text committed");
                return;
            }
            try { Thread.sleep(IME_POLL_INTERVAL_MS); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new InjectionException("IME switch interrupted");
            }
        }
        AppLogger.e(TAG, "inputTextViaIme: IME not ready within " + IME_SWITCH_TIMEOUT_MS + "ms");
        throw new InjectionException("Mio IME InputConnection not ready");
    }

    @Override
    public void restoreSessionIme() {
        PDebug.d(TAG, "restoreSessionIme: savedImeIdForSession=" + savedImeIdForSession);
        if (savedImeIdForSession == null || savedImeIdForSession.isEmpty()) {
            PDebug.d(TAG, "restoreSessionIme: no session to restore");
            return;
        }
        String toRestore = savedImeIdForSession;
        savedImeIdForSession = null;
        PDebug.d(TAG, "restoreSessionIme: restoring to " + toRestore);
        boolean ok = ImeAutoSwitchHelper.restoreIme(toRestore);
        PDebug.d(TAG, "restoreSessionIme: restoreIme returned " + ok);
    }

    private boolean inputTextViaBroadcast(String text) {
        try {
            MioImeService ime = MioImeService.getInstance();
            if (ime == null) {
                PDebug.d(TAG, "inputTextViaBroadcast: MioImeService not running");
                return false;
            }
            String escaped = text.replace("'", "'\\''");
            String cmd = "am broadcast -a " + ACTION_COMMIT_TEXT
                    + " --es " + EXTRA_TEXT + " '" + escaped + "'";
            String result = getService().execCommand(cmd);
            PDebug.d(TAG, "inputTextViaBroadcast: result=" + result);
            if (result != null && (result.contains("result=1") || result.contains("Result code: 1"))) {
                return true;
            }
            AppLogger.w(TAG, "inputTextViaBroadcast: unexpected result, will try IME switch");
            return false;
        } catch (Exception e) {
            AppLogger.e(TAG, "inputTextViaBroadcast failed", e);
            return false;
        }
    }

    private void inputTextFallback(String text, long keyHoldDurationMs) throws InjectionException {
        PDebug.d(TAG, "inputTextFallback: text=" + text + " (len=" + text.length() + ")");
        for (char c : text.toCharArray()) {
            int[] key = charToKeyCode(c);
            if (key == null) {
                AppLogger.w(TAG, "inputTextFallback: no keycode for char '"
                        + c + "' (0x" + Integer.toHexString(c) + "), skipping");
                continue;
            }
            try {
                boolean ok;
                if (key[1] != 0) {
                    ok = keyHoldDurationMs > 0
                            ? getService().injectComboWithDuration(key[0], new int[]{KeyEvent.KEYCODE_SHIFT_LEFT}, (int) keyHoldDurationMs)
                            : getService().injectCombo(key[0], new int[]{KeyEvent.KEYCODE_SHIFT_LEFT});
                } else {
                    ok = keyHoldDurationMs > 0
                            ? getService().injectKeyEventWithDuration(key[0], 0, (int) keyHoldDurationMs)
                            : getService().injectKeyEvent(key[0], 0);
                }
                if (!ok) throw new InjectionException(key[0], "inputTextFallback: inject returned false");
            } catch (InjectionException e) {
                AppLogger.e(TAG, "inputTextFallback: char '" + c + "' failed", e);
                throw e;
            } catch (Exception e) {
                AppLogger.e(TAG, "inputTextFallback: char '" + c + "' exception", e);
                throw new InjectionException(key[0], "Shizuku inputTextFallback failed", e);
            }
        }
        PDebug.d(TAG, "inputTextFallback: done, " + text.length() + " char(s) processed");
    }

    @Override
    public void keyDown(int keyCode) throws InjectionException {
        PDebug.d(TAG, "keyDown: " + keyName(keyCode) + " (" + keyCode + ")");
        try {
            boolean ok = getService().injectKeyDown(keyCode);
            PDebug.d(TAG, "keyDown: injectKeyDown -> " + ok);
            if (!ok) throw new InjectionException(keyCode, "injectKeyDown returned false");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "keyDown failed: " + keyName(keyCode), e);
            throw e;
        } catch (Exception e) {
            AppLogger.e(TAG, "keyDown exception: " + keyName(keyCode), e);
            throw new InjectionException(keyCode, "Shizuku keyDown failed", e);
        }
    }

    @Override
    public void keyUp(int keyCode) throws InjectionException {
        PDebug.d(TAG, "keyUp: " + keyName(keyCode) + " (" + keyCode + ")");
        try {
            boolean ok = getService().injectKeyUp(keyCode);
            PDebug.d(TAG, "keyUp: injectKeyUp -> " + ok);
            if (!ok) throw new InjectionException(keyCode, "injectKeyUp returned false");
        } catch (InjectionException e) {
            AppLogger.e(TAG, "keyUp failed: " + keyName(keyCode), e);
            throw e;
        } catch (Exception e) {
            AppLogger.e(TAG, "keyUp exception: " + keyName(keyCode), e);
            throw new InjectionException(keyCode, "Shizuku keyUp failed", e);
        }
    }

    @Override
    public void longPress(int keyCode, long durationMs) throws InjectionException {
        PDebug.d(TAG, "longPress: key=" + keyName(keyCode) + " duration=" + durationMs + "ms");
        keyDown(keyCode);
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            AppLogger.w(TAG, "longPress: sleep interrupted", e);
            Thread.currentThread().interrupt();
        }
        keyUp(keyCode);
        PDebug.d(TAG, "longPress: done");
    }

    @Override
    public void delay(long ms) throws InjectionException {
        PDebug.d(TAG, "delay: " + ms + "ms");
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            AppLogger.w(TAG, "delay: interrupted", e);
            Thread.currentThread().interrupt();
            throw new InjectionException("delay interrupted");
        }
    }

    @Override
    public void repeatKey(long intervalMs, int keyCode) throws InjectionException {
        PDebug.d(TAG, "repeatKey: key=" + keyName(keyCode)
                + " interval=" + intervalMs + "ms (stopping any previous repeat)");
        stopRepeat();
        repeatRunning = true;
        repeatThread = new Thread(() -> {
            PDebug.d(TAG, "repeatKey: loop started, keyCode=" + keyCode);
            while (repeatRunning) {
                try {
                    pressKey(keyCode);
                    Thread.sleep(intervalMs);
                } catch (InjectionException e) {
                    AppLogger.e(TAG, "repeatKey inject failed", e);
                    break;
                } catch (InterruptedException e) {
                    AppLogger.w(TAG, "repeatKey: sleep interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            PDebug.d(TAG, "repeatKey loop ended, keyCode=" + keyCode);
        }, "repeatKey-" + keyCode);
        repeatThread.start();
    }

    public void stopRepeat() {
        PDebug.d(TAG, "stopRepeat: repeatRunning=" + repeatRunning
                + " thread=" + (repeatThread != null ? repeatThread.getName() : "null"));
        repeatRunning = false;
        if (repeatThread != null) {
            repeatThread.interrupt();
            repeatThread = null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            boolean running = isShizukuRunning();
            boolean granted = isPermissionGranted();
            boolean bound = isServiceBound();
            boolean result = running && granted && bound;
            PDebug.d(TAG, "isAvailable: running=" + running + " granted=" + granted
                    + " bound=" + bound + " -> " + result);
            return result;
        } catch (Exception e) {
            PDebug.d(TAG, "isAvailable: exception -> false");
            return false;
        }
    }

    @Override
    public ServiceState getServiceState() {
        boolean running = isShizukuRunning();
        if (!running) {
            PDebug.d(TAG, "getServiceState: NO_SERVICE (Shizuku not running)");
            return ServiceState.NO_SERVICE;
        }
        if (!isPermissionGranted()) {
            PDebug.d(TAG, "getServiceState: NOT_READY (permission not granted)");
            return ServiceState.NOT_READY;
        }
        if (!isServiceBound()) {
            PDebug.d(TAG, "getServiceState: NO_SERVICE (ShellService not bound)");
            return ServiceState.NO_SERVICE;
        }
        PDebug.d(TAG, "getServiceState: READY");
        return ServiceState.READY;
    }

    @Override
    public String getName() {
        return "Shizuku";
    }

    private static int[] charToKeyCode(char c) {
        if (c >= 'a' && c <= 'z') {
            return new int[]{KeyEvent.KEYCODE_A + (c - 'a'), 0};
        }
        if (c >= 'A' && c <= 'Z') {
            return new int[]{KeyEvent.KEYCODE_A + (c - 'A'), KeyEvent.META_SHIFT_ON};
        }
        if (c >= '0' && c <= '9') {
            return new int[]{KeyEvent.KEYCODE_0 + (c - '0'), 0};
        }
        switch (c) {
            case ' ': return new int[]{KeyEvent.KEYCODE_SPACE, 0};
            case '.': return new int[]{KeyEvent.KEYCODE_PERIOD, 0};
            case ',': return new int[]{KeyEvent.KEYCODE_COMMA, 0};
            case '@': return new int[]{KeyEvent.KEYCODE_AT, 0};
            case '#': return new int[]{KeyEvent.KEYCODE_POUND, 0};
            case '*': return new int[]{KeyEvent.KEYCODE_STAR, 0};
            case '-': return new int[]{KeyEvent.KEYCODE_MINUS, 0};
            case '+': return new int[]{KeyEvent.KEYCODE_PLUS, 0};
            case '=': return new int[]{KeyEvent.KEYCODE_EQUALS, 0};
            case '/': return new int[]{KeyEvent.KEYCODE_SLASH, 0};
            case '\\': return new int[]{KeyEvent.KEYCODE_BACKSLASH, 0};
            case ';': return new int[]{KeyEvent.KEYCODE_SEMICOLON, 0};
            case '\'': return new int[]{KeyEvent.KEYCODE_APOSTROPHE, 0};
            case '\n': return new int[]{KeyEvent.KEYCODE_ENTER, 0};
            case '\t': return new int[]{KeyEvent.KEYCODE_TAB, 0};
            case '!': return new int[]{KeyEvent.KEYCODE_1, KeyEvent.META_SHIFT_ON};
            case '$': return new int[]{KeyEvent.KEYCODE_4, KeyEvent.META_SHIFT_ON};
            case '%': return new int[]{KeyEvent.KEYCODE_5, KeyEvent.META_SHIFT_ON};
            case '^': return new int[]{KeyEvent.KEYCODE_6, KeyEvent.META_SHIFT_ON};
            case '&': return new int[]{KeyEvent.KEYCODE_7, KeyEvent.META_SHIFT_ON};
            case '(': return new int[]{KeyEvent.KEYCODE_9, KeyEvent.META_SHIFT_ON};
            case ')': return new int[]{KeyEvent.KEYCODE_0, KeyEvent.META_SHIFT_ON};
            case '_': return new int[]{KeyEvent.KEYCODE_MINUS, KeyEvent.META_SHIFT_ON};
            case '{': return new int[]{KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.META_SHIFT_ON};
            case '}': return new int[]{KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.META_SHIFT_ON};
            case '|': return new int[]{KeyEvent.KEYCODE_BACKSLASH, KeyEvent.META_SHIFT_ON};
            case ':': return new int[]{KeyEvent.KEYCODE_SEMICOLON, KeyEvent.META_SHIFT_ON};
            case '"': return new int[]{KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.META_SHIFT_ON};
            case '<': return new int[]{KeyEvent.KEYCODE_COMMA, KeyEvent.META_SHIFT_ON};
            case '>': return new int[]{KeyEvent.KEYCODE_PERIOD, KeyEvent.META_SHIFT_ON};
            case '?': return new int[]{KeyEvent.KEYCODE_SLASH, KeyEvent.META_SHIFT_ON};
            case '~': return new int[]{KeyEvent.KEYCODE_GRAVE, KeyEvent.META_SHIFT_ON};
            default: return null;
        }
    }

    private static String keyName(int keyCode) {
        try {
            return KeyEvent.keyCodeToString(keyCode);
        } catch (Exception e) {
            return String.valueOf(keyCode);
        }
    }

    private static String keyCodesName(int[] keyCodes) {
        if (keyCodes == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < keyCodes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(keyName(keyCodes[i]));
        }
        return sb.append("]").toString();
    }
}
