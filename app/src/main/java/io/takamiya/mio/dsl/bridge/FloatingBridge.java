package io.takamiya.mio.dsl.bridge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import cn.fsy.plugin.kore.library.BridgeHandler;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.floating.FloatingCrosshair;
import io.takamiya.mio.floating.FloatingStatusBar;
import io.takamiya.mio.floating.FloatingToast;
import io.takamiya.mio.floating.FloatingWindowService;

/**
 * Floating 命名空间桥接实现。
 * 提供悬浮窗 UI 控制：Toast、准心、状态栏。
 * 注意：UI 操作需在主线程执行。
 */
public class FloatingBridge {

    private static final String TAG = "FloatingBridge";

    private final Context context;
    private final Handler mainHandler;

    public FloatingBridge(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * showToast - 显示浮动 Toast
     * args[0] = text (String)
     * args[1] = durationMs (Int, 可选, 默认 2000)
     */
    public BridgeHandler showToast() {
        return (args) -> {
            String text = toString(args[0]);
            int durationMs = args.length > 1 ? toInt(args[1]) : 2000;
            mainHandler.post(() -> {
                try {
                    FloatingWindowService svc = FloatingWindowService.getInstance();
                    if (svc != null) {
                        svc.showToast(text);
                    }
                } catch (Exception e) {
                    AppLogger.e(TAG, "showToast failed: " + e.getMessage());
                }
            });
            return null;
        };
    }

    /**
     * setCrosshairVisible - 准心显隐
     * args[0] = visible (Boolean)
     */
    public BridgeHandler setCrosshairVisible() {
        return (args) -> {
            boolean visible = toBool(args[0]);
            mainHandler.post(() -> {
                try {
                    FloatingWindowService svc = FloatingWindowService.getInstance();
                    if (svc != null) {
                        // 通过反射或直接访问 crosshair 字段
                        // FloatingWindowService 的 crosshair 字段
                        java.lang.reflect.Field field = FloatingWindowService.class.getDeclaredField("crosshair");
                        field.setAccessible(true);
                        FloatingCrosshair crosshair = (FloatingCrosshair) field.get(svc);
                        if (crosshair != null) {
                            if (visible && !crosshair.isAttached()) {
                                crosshair.attach();
                            } else if (!visible && crosshair.isAttached()) {
                                crosshair.detach();
                            }
                        }
                    }
                } catch (Exception e) {
                    AppLogger.e(TAG, "setCrosshairVisible failed: " + e.getMessage());
                }
            });
            return null;
        };
    }

    /**
     * setCrosshairPosition - 准心位置
     * args[0] = x (Int, px)
     * args[1] = y (Int, px)
     */
    public BridgeHandler setCrosshairPosition() {
        return (args) -> {
            int x = toInt(args[0]);
            int y = toInt(args[1]);
            mainHandler.post(() -> {
                try {
                    FloatingWindowService svc = FloatingWindowService.getInstance();
                    if (svc != null) {
                        java.lang.reflect.Field field = FloatingWindowService.class.getDeclaredField("crosshair");
                        field.setAccessible(true);
                        FloatingCrosshair crosshair = (FloatingCrosshair) field.get(svc);
                        if (crosshair != null && crosshair.isAttached()) {
                            crosshair.center();
                            // center() 将准心移到屏幕中心，如需精确位置需额外实现
                        }
                    }
                } catch (Exception e) {
                    AppLogger.e(TAG, "setCrosshairPosition failed: " + e.getMessage());
                }
            });
            return null;
        };
    }

    /**
     * updateStatusBar - 更新状态栏附加文字
     * args[0] = text (String)
     */
    public BridgeHandler updateStatusBar() {
        return (args) -> {
            String text = toString(args[0]);
            mainHandler.post(() -> {
                try {
                    FloatingWindowService svc = FloatingWindowService.getInstance();
                    if (svc != null) {
                        java.lang.reflect.Field field = FloatingWindowService.class.getDeclaredField("statusBar");
                        field.setAccessible(true);
                        FloatingStatusBar statusBar = (FloatingStatusBar) field.get(svc);
                        if (statusBar != null && statusBar.isAttached()) {
                            statusBar.setCpsText(text);
                        }
                    }
                } catch (Exception e) {
                    AppLogger.e(TAG, "updateStatusBar failed: " + e.getMessage());
                }
            });
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

    private static boolean toBool(Object obj) {
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).intValue() != 0;
        return Boolean.parseBoolean(obj.toString());
    }
}
