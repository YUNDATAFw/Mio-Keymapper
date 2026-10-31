package io.takamiya.mio.dsl.bridge;

import android.content.Context;

import cn.fsy.plugin.sdl.library.BridgeHandler;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.gamepad.GamepadButton;
import io.takamiya.mio.gamepad.GamepadEngine;

/**
 * Gamepad 命名空间桥接实现。
 * 提供虚拟手柄控制：摇杆、扳机、方向键、按钮。
 */
public class GamepadBridge {

    private static final String TAG = "GamepadBridge";

    private final Context context;

    public GamepadBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * setLeftStick - 左摇杆
     * args[0] = x (Int, -32767~32767)
     * args[1] = y (Int, -32767~32767)
     */
    public BridgeHandler setLeftStick() {
        return (args) -> {
            int x = toInt(args[0]);
            int y = toInt(args[1]);
            GamepadEngine.get(context).setLeftStick(clamp(x, -32767, 32767), clamp(y, -32767, 32767));
            return null;
        };
    }

    /**
     * setRightStick - 右摇杆
     * args[0] = x (Int, -32767~32767)
     * args[1] = y (Int, -32767~32767)
     */
    public BridgeHandler setRightStick() {
        return (args) -> {
            int x = toInt(args[0]);
            int y = toInt(args[1]);
            GamepadEngine.get(context).setRightStick(clamp(x, -32767, 32767), clamp(y, -32767, 32767));
            return null;
        };
    }

    /**
     * setTrigger - 扳机
     * args[0] = lt (Int, 0~255)
     * args[1] = rt (Int, 0~255)
     */
    public BridgeHandler setTrigger() {
        return (args) -> {
            int lt = toInt(args[0]);
            int rt = toInt(args[1]);
            GamepadEngine.get(context).setTrigger(clamp(lt, 0, 255), clamp(rt, 0, 255));
            return null;
        };
    }

    /**
     * setDpad - 十字键
     * args[0] = x (Int, -1/0/1)
     * args[1] = y (Int, -1/0/1)
     */
    public BridgeHandler setDpad() {
        return (args) -> {
            int x = toInt(args[0]);
            int y = toInt(args[1]);
            GamepadEngine.get(context).setDpad(clamp(x, -1, 1), clamp(y, -1, 1));
            return null;
        };
    }

    /**
     * pressButton - 按钮按下
     * args[0] = button (String, 如 "A"/"GAMEPAD_A"/"LB" 等)
     */
    public BridgeHandler pressButton() {
        return (args) -> {
            String name = toString(args[0]);
            GamepadButton button = GamepadButton.fromName(name);
            if (button != null) {
                GamepadEngine.get(context).pressButton(button);
            } else {
                AppLogger.w(TAG, "pressButton: unknown button=" + name);
            }
            return null;
        };
    }

    /**
     * releaseButton - 按钮释放
     * args[0] = button (String)
     */
    public BridgeHandler releaseButton() {
        return (args) -> {
            String name = toString(args[0]);
            GamepadButton button = GamepadButton.fromName(name);
            if (button != null) {
                GamepadEngine.get(context).releaseButton(button);
            } else {
                AppLogger.w(TAG, "releaseButton: unknown button=" + name);
            }
            return null;
        };
    }

    // ==================== 工具方法 ====================

    private static String toString(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private static int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
