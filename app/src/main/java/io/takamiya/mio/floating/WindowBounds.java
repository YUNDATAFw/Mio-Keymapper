package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * 悬浮窗边界工具：计算安全区域（扣除状态栏、导航栏、刘海屏），
 * 防止悬浮窗跑到屏幕外 / 被系统栏遮挡。
 */
public final class WindowBounds {

    private WindowBounds() {
    }

    public static int topInset(Context context, View referenceView) {
        return screenInsets(context, referenceView)[1];
    }

    public static int bottomInset(Context context, View referenceView) {
        return screenInsets(context, referenceView)[3];
    }

    public static int leftInset(Context context, View referenceView) {
        return screenInsets(context, referenceView)[0];
    }

    public static int rightInset(Context context, View referenceView) {
        return screenInsets(context, referenceView)[2];
    }

    /**
     * 全屏稳定 insets [left, top, right, bottom]。
     * 悬浮窗自身的 root insets 依赖窗口帧位置（小球在屏幕中间时返回 0），
     * 会导致空气墙边界漂移，因此 API 30+ 改用 getMaximumWindowMetrics 读取
     * 全屏窗口的 insets（与窗口位置无关）；低版本退回参考视图 + 资源兜底。
     */
    private static int[] screenInsets(Context context, View referenceView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    WindowInsets insets = wm.getMaximumWindowMetrics().getWindowInsets();
                    int left = insets.getInsets(WindowInsets.Type.displayCutout()).left;
                    int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                    int right = insets.getInsets(WindowInsets.Type.displayCutout()).right;
                    int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                    if (top <= 0) top = resourceDimen(context, "status_bar_height");
                    if (bottom <= 0) bottom = bottomInsetFallback(context);
                    return new int[]{left, top, right, bottom};
                }
            } catch (Exception ignored) {
            }
        }
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;
        if (referenceView != null) {
            WindowInsets insets = referenceView.getRootWindowInsets();
            if (insets != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                    bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                    left = insets.getInsets(WindowInsets.Type.displayCutout()).left;
                    right = insets.getInsets(WindowInsets.Type.displayCutout()).right;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft();
                    right = insets.getSystemWindowInsetRight();
                }
            }
        }
        if (top <= 0) top = resourceDimen(context, "status_bar_height");
        if (bottom <= 0) bottom = bottomInsetFallback(context);
        return new int[]{left, top, right, bottom};
    }

    private static int bottomInsetFallback(Context context) {
        int h = resourceDimen(context, "navigation_bar_height");
        if (h > 0) return h;
        int g = resourceDimen(context, "navigation_bar_height_gesture");
        if (g > 0) return g;
        return resourceDimen(context, "navigation_bar_height_landscape");
    }

    public static Point realSize(Context context) {
        Point p = new Point();
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                wm.getDefaultDisplay().getRealSize(p);
            }
        } catch (Exception ignored) {
        }
        if (p.x <= 0 || p.y <= 0) {
            p.set(1080, 2340);
        }
        return p;
    }

    /**
     * 安全区域（窗口坐标 TOP|START），悬浮窗应完全位于其中。
     */
    public static Rect safeRect(Context context, View referenceView) {
        Point screen = realSize(context);
        int left = leftInset(context, referenceView);
        int top = topInset(context, referenceView);
        int right = screen.x - rightInset(context, referenceView);
        int bottom = screen.y - bottomInset(context, referenceView);
        if (right < left) right = left;
        if (bottom < top) bottom = top;
        return new Rect(left, top, right, bottom);
    }

    public static int clampX(Rect safe, int x, int width) {
        int min = safe.left;
        int max = safe.right - width;
        if (max < min) max = min;
        return Math.max(min, Math.min(x, max));
    }

    public static int clampY(Rect safe, int y, int height) {
        int min = safe.top;
        int max = safe.bottom - height;
        if (max < min) max = min;
        return Math.max(min, Math.min(y, max));
    }

    private static int resourceDimen(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "dimen", "android");
        if (id > 0) {
            return context.getResources().getDimensionPixelSize(id);
        }
        return 0;
    }
}
