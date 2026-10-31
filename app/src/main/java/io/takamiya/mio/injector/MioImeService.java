package io.takamiya.mio.injector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;

import cn.panda.utils.library.AppLogger;

public class MioImeService extends InputMethodService {

    private static final String TAG = "MioImeService";
    private static MioImeService instance;

    public static final String ACTION_COMMIT_TEXT = "io.takamiya.mio.ACTION_COMMIT_TEXT";
    public static final String EXTRA_TEXT = "io.takamiya.mio.EXTRA_TEXT";

    private InputConnection inputConnection;
    private BroadcastReceiver commitTextReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        commitTextReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_COMMIT_TEXT.equals(intent.getAction())) {
                    String text = intent.getStringExtra(EXTRA_TEXT);
                    if (text != null && inputConnection != null) {
                        try {
                            inputConnection.commitText(text, 1);
                            setResultCode(1);
                            setResultData("ok");
                        } catch (Exception e) {
                            AppLogger.e(TAG, "commitTextReceiver: failed", e);
                            setResultCode(-1);
                            setResultData("error:" + e.getMessage());
                        }
                    } else {
                        setResultCode(-1);
                        setResultData(text == null ? "null_text" : "no_connection");
                    }
                }
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            registerReceiver(commitTextReceiver, new IntentFilter(ACTION_COMMIT_TEXT),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commitTextReceiver, new IntentFilter(ACTION_COMMIT_TEXT));
        }

        AppLogger.d(TAG, "onCreate: IME service created, pid=" + android.os.Process.myPid());
    }

    @Override
    public void onDestroy() {
        AppLogger.d(TAG, "onDestroy: IME service destroyed");
        if (commitTextReceiver != null) {
            unregisterReceiver(commitTextReceiver);
            commitTextReceiver = null;
        }
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onBindInput() {
        super.onBindInput();
        InputBinding binding = getCurrentInputBinding();
        AppLogger.d(TAG, "onBindInput: binding=" + binding
                + " connection=" + (binding != null ? binding.getConnection() : null));
    }

    @Override
    public void onUnbindInput() {
        AppLogger.d(TAG, "onUnbindInput: inputConnection=" + inputConnection);
        inputConnection = null;
        super.onUnbindInput();
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        inputConnection = getCurrentInputConnection();
        AppLogger.d(TAG, "onStartInput: restarting=" + restarting
                + " connection=" + (inputConnection != null)
                + " package=" + (info != null ? info.packageName : "null")
                + " inputType=" + (info != null ? info.inputType : -1)
                + " fieldId=" + (info != null ? info.fieldId : -1));
    }

    @Override
    public void onFinishInput() {
        AppLogger.d(TAG, "onFinishInput: clearing inputConnection=" + inputConnection);
        inputConnection = null;
        super.onFinishInput();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        AppLogger.d(TAG, "onStartInputView: restarting=" + restarting
                + " package=" + (info != null ? info.packageName : "null"));
    }

    public static MioImeService getInstance() {
        return instance;
    }

    public boolean hasInputConnection() {
        boolean result = instance != null && inputConnection != null;
        AppLogger.d(TAG, "hasInputConnection: instance=" + (instance != null)
                + " inputConnection=" + (inputConnection != null) + " -> " + result);
        return result;
    }

    public void sendKeyCode(int keyCode) throws InjectionException {
        AppLogger.d(TAG, "sendKeyCode: " + keyName(keyCode) + " (" + keyCode + ")");
        if (inputConnection == null) {
            AppLogger.e(TAG, "sendKeyCode: InputConnection is null, key=" + keyName(keyCode));
            throw new InjectionException(keyCode, "InputConnection is null");
        }
        sendKeyEvent(keyCode, KeyEvent.ACTION_DOWN, 0);
        sendKeyEvent(keyCode, KeyEvent.ACTION_UP, 0);
        AppLogger.d(TAG, "sendKeyCode: down+up sent");
    }

    public void keyDown(int keyCode) throws InjectionException {
        AppLogger.d(TAG, "keyDown: " + keyName(keyCode) + " (" + keyCode + ")");
        if (inputConnection == null) {
            AppLogger.e(TAG, "keyDown: InputConnection is null, key=" + keyName(keyCode));
            throw new InjectionException(keyCode, "InputConnection is null");
        }
        sendKeyEvent(keyCode, KeyEvent.ACTION_DOWN, 0);
    }

    public void keyUp(int keyCode) throws InjectionException {
        AppLogger.d(TAG, "keyUp: " + keyName(keyCode) + " (" + keyCode + ")");
        if (inputConnection == null) {
            AppLogger.e(TAG, "keyUp: InputConnection is null, key=" + keyName(keyCode));
            throw new InjectionException(keyCode, "InputConnection is null");
        }
        sendKeyEvent(keyCode, KeyEvent.ACTION_UP, 0);
    }

    public void commitText(String text) throws InjectionException {
        AppLogger.d(TAG, "commitText: text=" + text + " (len=" + (text != null ? text.length() : -1) + ")");
        if (inputConnection == null) {
            AppLogger.e(TAG, "commitText: InputConnection is null");
            throw new InjectionException("InputConnection is null");
        }
        inputConnection.commitText(text, 1);
        AppLogger.d(TAG, "commitText: committed");
    }

    public void sendKeyCombo(int keyCode, int[] modifierKeyCodes) throws InjectionException {
        AppLogger.d(TAG, "sendKeyCombo: key=" + keyName(keyCode)
                + " modifiers=" + keyCodesName(modifierKeyCodes));
        if (inputConnection == null) {
            AppLogger.e(TAG, "sendKeyCombo: InputConnection is null, key=" + keyName(keyCode));
            throw new InjectionException(keyCode, "InputConnection is null");
        }
        int meta = io.takamiya.mio.config.KeyMapping.metaStateFor(modifierKeyCodes);
        AppLogger.d(TAG, "sendKeyCombo: computed meta=0x" + Integer.toHexString(meta));
        if (modifierKeyCodes != null) {
            for (int mod : modifierKeyCodes) {
                if (mod != 0 && mod != keyCode) {
                    sendKeyEvent(mod, KeyEvent.ACTION_DOWN, meta);
                }
            }
            if (modifierKeyCodes.length > 0) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    AppLogger.w(TAG, "sendKeyCombo: sleep interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        sendKeyEvent(keyCode, KeyEvent.ACTION_DOWN, meta);
        sendKeyEvent(keyCode, KeyEvent.ACTION_UP, meta);
        if (modifierKeyCodes != null) {
            for (int i = modifierKeyCodes.length - 1; i >= 0; i--) {
                if (modifierKeyCodes[i] != 0 && modifierKeyCodes[i] != keyCode) {
                    int upMeta = metaWithout(modifierKeyCodes, i);
                    sendKeyEvent(modifierKeyCodes[i], KeyEvent.ACTION_UP, upMeta);
                }
            }
        }
        AppLogger.d(TAG, "sendKeyCombo: done");
    }

    private static int metaWithout(int[] modifierKeyCodes, int skipIndex) {
        int meta = io.takamiya.mio.config.KeyMapping.metaStateFor(modifierKeyCodes);
        meta &= ~io.takamiya.mio.config.KeyMapping.metaStateFor(new int[]{modifierKeyCodes[skipIndex]});
        return meta;
    }

    private void sendKeyEvent(int keyCode, int action, int metaState) {
        if (inputConnection == null) {
            AppLogger.w(TAG, "sendKeyEvent: InputConnection is null, dropping "
                    + keyName(keyCode) + " " + actionName(action));
            return;
        }

        long eventTime = SystemClock.uptimeMillis();
        KeyEvent keyEvent = new KeyEvent(
                eventTime,
                eventTime,
                action,
                keyCode,
                0,
                metaState,
                findKeyboardDeviceId(),
                0,
                KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD
        );
        AppLogger.d(TAG, "sendKeyEvent: " + keyName(keyCode) + " " + actionName(action)
                + " meta=0x" + Integer.toHexString(metaState)
                + " event=" + keyEvent);
        boolean sent = inputConnection.sendKeyEvent(keyEvent);
        AppLogger.d(TAG, "sendKeyEvent: sendKeyEvent returned " + sent);
    }

    private static int findKeyboardDeviceId() {
        try {
            int[] ids = InputDevice.getDeviceIds();
            for (int id : ids) {
                if (id == KeyCharacterMap.VIRTUAL_KEYBOARD) continue;
                InputDevice device = InputDevice.getDevice(id);
                if (device != null
                        && (device.getSources() & InputDevice.SOURCE_KEYBOARD) != 0
                        && (device.getSources() & InputDevice.SOURCE_TOUCHSCREEN) == 0) {
                    AppLogger.d(TAG, "findKeyboardDeviceId: using device " + device.getName() + " (id=" + id + ")");
                    return id;
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "findKeyboardDeviceId failed: " + e.getMessage());
        }
        return KeyCharacterMap.BUILT_IN_KEYBOARD;
    }

    private static String actionName(int action) {
        switch (action) {
            case KeyEvent.ACTION_DOWN: return "DOWN";
            case KeyEvent.ACTION_UP: return "UP";
            case KeyEvent.ACTION_MULTIPLE: return "MULTIPLE";
            default: return "ACTION_" + action;
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
