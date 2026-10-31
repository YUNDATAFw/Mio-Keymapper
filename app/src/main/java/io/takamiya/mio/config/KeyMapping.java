package io.takamiya.mio.config;

import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyMapping {
    private static final Map<String, Integer> KEY_MAP = new HashMap<>();

    static {
        KEY_MAP.put("KEYCODE_UNKNOWN", KeyEvent.KEYCODE_UNKNOWN);
        KEY_MAP.put("KEYCODE_HOME", KeyEvent.KEYCODE_HOME);
        KEY_MAP.put("KEYCODE_BACK", KeyEvent.KEYCODE_BACK);
        KEY_MAP.put("KEYCODE_CALL", KeyEvent.KEYCODE_CALL);
        KEY_MAP.put("KEYCODE_ENDCALL", KeyEvent.KEYCODE_ENDCALL);
        KEY_MAP.put("KEYCODE_0", KeyEvent.KEYCODE_0);
        KEY_MAP.put("KEYCODE_1", KeyEvent.KEYCODE_1);
        KEY_MAP.put("KEYCODE_2", KeyEvent.KEYCODE_2);
        KEY_MAP.put("KEYCODE_3", KeyEvent.KEYCODE_3);
        KEY_MAP.put("KEYCODE_4", KeyEvent.KEYCODE_4);
        KEY_MAP.put("KEYCODE_5", KeyEvent.KEYCODE_5);
        KEY_MAP.put("KEYCODE_6", KeyEvent.KEYCODE_6);
        KEY_MAP.put("KEYCODE_7", KeyEvent.KEYCODE_7);
        KEY_MAP.put("KEYCODE_8", KeyEvent.KEYCODE_8);
        KEY_MAP.put("KEYCODE_9", KeyEvent.KEYCODE_9);
        KEY_MAP.put("KEYCODE_STAR", KeyEvent.KEYCODE_STAR);
        KEY_MAP.put("KEYCODE_POUND", KeyEvent.KEYCODE_POUND);
        KEY_MAP.put("KEYCODE_DPAD_UP", KeyEvent.KEYCODE_DPAD_UP);
        KEY_MAP.put("KEYCODE_DPAD_DOWN", KeyEvent.KEYCODE_DPAD_DOWN);
        KEY_MAP.put("KEYCODE_DPAD_LEFT", KeyEvent.KEYCODE_DPAD_LEFT);
        KEY_MAP.put("KEYCODE_DPAD_RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT);
        KEY_MAP.put("KEYCODE_DPAD_CENTER", KeyEvent.KEYCODE_DPAD_CENTER);
        KEY_MAP.put("KEYCODE_VOLUME_UP", KeyEvent.KEYCODE_VOLUME_UP);
        KEY_MAP.put("KEYCODE_VOLUME_DOWN", KeyEvent.KEYCODE_VOLUME_DOWN);
        KEY_MAP.put("KEYCODE_POWER", KeyEvent.KEYCODE_POWER);
        KEY_MAP.put("KEYCODE_CAMERA", KeyEvent.KEYCODE_CAMERA);
        KEY_MAP.put("KEYCODE_CLEAR", KeyEvent.KEYCODE_CLEAR);
        KEY_MAP.put("KEYCODE_A", KeyEvent.KEYCODE_A);
        KEY_MAP.put("KEYCODE_B", KeyEvent.KEYCODE_B);
        KEY_MAP.put("KEYCODE_C", KeyEvent.KEYCODE_C);
        KEY_MAP.put("KEYCODE_D", KeyEvent.KEYCODE_D);
        KEY_MAP.put("KEYCODE_E", KeyEvent.KEYCODE_E);
        KEY_MAP.put("KEYCODE_F", KeyEvent.KEYCODE_F);
        KEY_MAP.put("KEYCODE_G", KeyEvent.KEYCODE_G);
        KEY_MAP.put("KEYCODE_H", KeyEvent.KEYCODE_H);
        KEY_MAP.put("KEYCODE_I", KeyEvent.KEYCODE_I);
        KEY_MAP.put("KEYCODE_J", KeyEvent.KEYCODE_J);
        KEY_MAP.put("KEYCODE_K", KeyEvent.KEYCODE_K);
        KEY_MAP.put("KEYCODE_L", KeyEvent.KEYCODE_L);
        KEY_MAP.put("KEYCODE_M", KeyEvent.KEYCODE_M);
        KEY_MAP.put("KEYCODE_N", KeyEvent.KEYCODE_N);
        KEY_MAP.put("KEYCODE_O", KeyEvent.KEYCODE_O);
        KEY_MAP.put("KEYCODE_P", KeyEvent.KEYCODE_P);
        KEY_MAP.put("KEYCODE_Q", KeyEvent.KEYCODE_Q);
        KEY_MAP.put("KEYCODE_R", KeyEvent.KEYCODE_R);
        KEY_MAP.put("KEYCODE_S", KeyEvent.KEYCODE_S);
        KEY_MAP.put("KEYCODE_T", KeyEvent.KEYCODE_T);
        KEY_MAP.put("KEYCODE_U", KeyEvent.KEYCODE_U);
        KEY_MAP.put("KEYCODE_V", KeyEvent.KEYCODE_V);
        KEY_MAP.put("KEYCODE_W", KeyEvent.KEYCODE_W);
        KEY_MAP.put("KEYCODE_X", KeyEvent.KEYCODE_X);
        KEY_MAP.put("KEYCODE_Y", KeyEvent.KEYCODE_Y);
        KEY_MAP.put("KEYCODE_Z", KeyEvent.KEYCODE_Z);
        KEY_MAP.put("KEYCODE_COMMA", KeyEvent.KEYCODE_COMMA);
        KEY_MAP.put("KEYCODE_PERIOD", KeyEvent.KEYCODE_PERIOD);
        KEY_MAP.put("KEYCODE_ALT_LEFT", KeyEvent.KEYCODE_ALT_LEFT);
        KEY_MAP.put("KEYCODE_ALT_RIGHT", KeyEvent.KEYCODE_ALT_RIGHT);
        KEY_MAP.put("KEYCODE_SHIFT_LEFT", KeyEvent.KEYCODE_SHIFT_LEFT);
        KEY_MAP.put("KEYCODE_SHIFT_RIGHT", KeyEvent.KEYCODE_SHIFT_RIGHT);
        KEY_MAP.put("KEYCODE_TAB", KeyEvent.KEYCODE_TAB);
        KEY_MAP.put("KEYCODE_SPACE", KeyEvent.KEYCODE_SPACE);
        KEY_MAP.put("KEYCODE_SYM", KeyEvent.KEYCODE_SYM);
        KEY_MAP.put("KEYCODE_EXPLORER", KeyEvent.KEYCODE_EXPLORER);
        KEY_MAP.put("KEYCODE_ENVELOPE", KeyEvent.KEYCODE_ENVELOPE);
        KEY_MAP.put("KEYCODE_ENTER", KeyEvent.KEYCODE_ENTER);
        KEY_MAP.put("KEYCODE_DEL", KeyEvent.KEYCODE_DEL);
        KEY_MAP.put("KEYCODE_MENU", KeyEvent.KEYCODE_MENU);
        KEY_MAP.put("KEYCODE_SEARCH", KeyEvent.KEYCODE_SEARCH);
        KEY_MAP.put("KEYCODE_MEDIA_PLAY_PAUSE", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        KEY_MAP.put("KEYCODE_MEDIA_STOP", KeyEvent.KEYCODE_MEDIA_STOP);
        KEY_MAP.put("KEYCODE_MEDIA_NEXT", KeyEvent.KEYCODE_MEDIA_NEXT);
        KEY_MAP.put("KEYCODE_MEDIA_PREVIOUS", KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        KEY_MAP.put("KEYCODE_PAGE_UP", KeyEvent.KEYCODE_PAGE_UP);
        KEY_MAP.put("KEYCODE_PAGE_DOWN", KeyEvent.KEYCODE_PAGE_DOWN);
        KEY_MAP.put("KEYCODE_ESCAPE", KeyEvent.KEYCODE_ESCAPE);
        KEY_MAP.put("KEYCODE_F1", KeyEvent.KEYCODE_F1);
        KEY_MAP.put("KEYCODE_F2", KeyEvent.KEYCODE_F2);
        KEY_MAP.put("KEYCODE_F3", KeyEvent.KEYCODE_F3);
        KEY_MAP.put("KEYCODE_F4", KeyEvent.KEYCODE_F4);
        KEY_MAP.put("KEYCODE_F5", KeyEvent.KEYCODE_F5);
        KEY_MAP.put("KEYCODE_F6", KeyEvent.KEYCODE_F6);
        KEY_MAP.put("KEYCODE_F7", KeyEvent.KEYCODE_F7);
        KEY_MAP.put("KEYCODE_F8", KeyEvent.KEYCODE_F8);
        KEY_MAP.put("KEYCODE_F9", KeyEvent.KEYCODE_F9);
        KEY_MAP.put("KEYCODE_F10", KeyEvent.KEYCODE_F10);
        KEY_MAP.put("KEYCODE_F11", KeyEvent.KEYCODE_F11);
        KEY_MAP.put("KEYCODE_F12", KeyEvent.KEYCODE_F12);
        KEY_MAP.put("KEYCODE_Z", KeyEvent.KEYCODE_Z);
        KEY_MAP.put("KEYCODE_MOVE_HOME", KeyEvent.KEYCODE_MOVE_HOME);
        KEY_MAP.put("KEYCODE_MOVE_END", KeyEvent.KEYCODE_MOVE_END);
        KEY_MAP.put("KEYCODE_INSERT", KeyEvent.KEYCODE_INSERT);
        KEY_MAP.put("KEYCODE_BREAK", KeyEvent.KEYCODE_BREAK);
        KEY_MAP.put("KEYCODE_CTRL_LEFT", KeyEvent.KEYCODE_CTRL_LEFT);
        KEY_MAP.put("KEYCODE_CTRL_RIGHT", KeyEvent.KEYCODE_CTRL_RIGHT);
        KEY_MAP.put("KEYCODE_CAPS_LOCK", KeyEvent.KEYCODE_CAPS_LOCK);
        KEY_MAP.put("KEYCODE_SCROLL_LOCK", KeyEvent.KEYCODE_SCROLL_LOCK);
        KEY_MAP.put("KEYCODE_FUNCTION", KeyEvent.KEYCODE_FUNCTION);
        KEY_MAP.put("KEYCODE_NUM_LOCK", KeyEvent.KEYCODE_NUM_LOCK);
        KEY_MAP.put("KEYCODE_NUMPAD_0", KeyEvent.KEYCODE_NUMPAD_0);
        KEY_MAP.put("KEYCODE_NUMPAD_1", KeyEvent.KEYCODE_NUMPAD_1);
        KEY_MAP.put("KEYCODE_NUMPAD_2", KeyEvent.KEYCODE_NUMPAD_2);
        KEY_MAP.put("KEYCODE_NUMPAD_3", KeyEvent.KEYCODE_NUMPAD_3);
        KEY_MAP.put("KEYCODE_NUMPAD_4", KeyEvent.KEYCODE_NUMPAD_4);
        KEY_MAP.put("KEYCODE_NUMPAD_5", KeyEvent.KEYCODE_NUMPAD_5);
        KEY_MAP.put("KEYCODE_NUMPAD_6", KeyEvent.KEYCODE_NUMPAD_6);
        KEY_MAP.put("KEYCODE_NUMPAD_7", KeyEvent.KEYCODE_NUMPAD_7);
        KEY_MAP.put("KEYCODE_NUMPAD_8", KeyEvent.KEYCODE_NUMPAD_8);
        KEY_MAP.put("KEYCODE_NUMPAD_9", KeyEvent.KEYCODE_NUMPAD_9);
        KEY_MAP.put("KEYCODE_NUMPAD_DIVIDE", KeyEvent.KEYCODE_NUMPAD_DIVIDE);
        KEY_MAP.put("KEYCODE_NUMPAD_MULTIPLY", KeyEvent.KEYCODE_NUMPAD_MULTIPLY);
        KEY_MAP.put("KEYCODE_NUMPAD_SUBTRACT", KeyEvent.KEYCODE_NUMPAD_SUBTRACT);
        KEY_MAP.put("KEYCODE_NUMPAD_ADD", KeyEvent.KEYCODE_NUMPAD_ADD);
        KEY_MAP.put("KEYCODE_NUMPAD_DOT", KeyEvent.KEYCODE_NUMPAD_DOT);
        KEY_MAP.put("KEYCODE_NUMPAD_COMMA", KeyEvent.KEYCODE_NUMPAD_COMMA);
        KEY_MAP.put("KEYCODE_NUMPAD_ENTER", KeyEvent.KEYCODE_NUMPAD_ENTER);
        KEY_MAP.put("KEYCODE_NUMPAD_EQUALS", KeyEvent.KEYCODE_NUMPAD_EQUALS);
        KEY_MAP.put("KEYCODE_AT", KeyEvent.KEYCODE_AT);
        KEY_MAP.put("KEYCODE_SLASH", KeyEvent.KEYCODE_SLASH);
        KEY_MAP.put("KEYCODE_BACKSLASH", KeyEvent.KEYCODE_BACKSLASH);
        KEY_MAP.put("KEYCODE_EQUALS", KeyEvent.KEYCODE_EQUALS);
        KEY_MAP.put("KEYCODE_MINUS", KeyEvent.KEYCODE_MINUS);
        KEY_MAP.put("KEYCODE_SEMICOLON", KeyEvent.KEYCODE_SEMICOLON);
        KEY_MAP.put("KEYCODE_APOSTROPHE", KeyEvent.KEYCODE_APOSTROPHE);
        KEY_MAP.put("KEYCODE_GRAVE", 68);
        KEY_MAP.put("KEYCODE_LEFT_BRACKET", KeyEvent.KEYCODE_LEFT_BRACKET);
        KEY_MAP.put("KEYCODE_RIGHT_BRACKET", KeyEvent.KEYCODE_RIGHT_BRACKET);
        KEY_MAP.put("GAMEPAD_A", KeyEvent.KEYCODE_BUTTON_A);
        KEY_MAP.put("GAMEPAD_B", KeyEvent.KEYCODE_BUTTON_B);
        KEY_MAP.put("GAMEPAD_X", KeyEvent.KEYCODE_BUTTON_X);
        KEY_MAP.put("GAMEPAD_Y", KeyEvent.KEYCODE_BUTTON_Y);
        KEY_MAP.put("GAMEPAD_L1", KeyEvent.KEYCODE_BUTTON_L1);
        KEY_MAP.put("GAMEPAD_R1", KeyEvent.KEYCODE_BUTTON_R1);
        KEY_MAP.put("GAMEPAD_L2", KeyEvent.KEYCODE_BUTTON_L2);
        KEY_MAP.put("GAMEPAD_R2", KeyEvent.KEYCODE_BUTTON_R2);
        KEY_MAP.put("GAMEPAD_SELECT", KeyEvent.KEYCODE_BUTTON_SELECT);
        KEY_MAP.put("GAMEPAD_START", KeyEvent.KEYCODE_BUTTON_START);
        KEY_MAP.put("GAMEPAD_LS", KeyEvent.KEYCODE_BUTTON_THUMBL);
        KEY_MAP.put("GAMEPAD_RS", KeyEvent.KEYCODE_BUTTON_THUMBR);
        KEY_MAP.put("GAMEPAD_DPAD_UP", KeyEvent.KEYCODE_DPAD_UP);
        KEY_MAP.put("GAMEPAD_DPAD_DOWN", KeyEvent.KEYCODE_DPAD_DOWN);
        KEY_MAP.put("GAMEPAD_DPAD_LEFT", KeyEvent.KEYCODE_DPAD_LEFT);
        KEY_MAP.put("GAMEPAD_DPAD_RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    public static int getKeyCode(String name) {
        if (name == null) return 0;
        Integer code = KEY_MAP.get(name);
        return code != null ? code : 0;
    }

    public static int metaStateFor(int[] modifierKeyCodes) {
        int meta = 0;
        if (modifierKeyCodes == null) return meta;
        for (int code : modifierKeyCodes) {
            switch (code) {
                case KeyEvent.KEYCODE_SHIFT_LEFT:
                    meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
                    break;
                case KeyEvent.KEYCODE_SHIFT_RIGHT:
                    meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON;
                    break;
                case KeyEvent.KEYCODE_ALT_LEFT:
                    meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
                    break;
                case KeyEvent.KEYCODE_ALT_RIGHT:
                    meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_RIGHT_ON;
                    break;
                case KeyEvent.KEYCODE_CTRL_LEFT:
                    meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
                    break;
                case KeyEvent.KEYCODE_CTRL_RIGHT:
                    meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_RIGHT_ON;
                    break;
                case KeyEvent.KEYCODE_META_LEFT:
                    meta |= KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
                    break;
                case KeyEvent.KEYCODE_META_RIGHT:
                    meta |= KeyEvent.META_META_ON | KeyEvent.META_META_RIGHT_ON;
                    break;
                case KeyEvent.KEYCODE_SYM:
                    meta |= KeyEvent.META_SYM_ON;
                    break;
                case KeyEvent.KEYCODE_FUNCTION:
                    meta |= KeyEvent.META_FUNCTION_ON;
                    break;
                case KeyEvent.KEYCODE_CAPS_LOCK:
                    meta |= KeyEvent.META_CAPS_LOCK_ON;
                    break;
                case KeyEvent.KEYCODE_NUM_LOCK:
                    meta |= KeyEvent.META_NUM_LOCK_ON;
                    break;
                case KeyEvent.KEYCODE_SCROLL_LOCK:
                    meta |= KeyEvent.META_SCROLL_LOCK_ON;
                    break;
            }
        }
        return meta;
    }

    public static String getKeyName(int keyCode) {
        return KeyEvent.keyCodeToString(keyCode);
    }

    public static String[] getAllKeyNames() {
        List<String> keys = new ArrayList<>(KEY_MAP.keySet());
        Collections.sort(keys);
        return keys.toArray(new String[0]);
    }
}
