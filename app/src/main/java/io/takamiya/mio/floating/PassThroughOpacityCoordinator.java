package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;

/**
 * 穿透悬浮窗的组合不透明度预算协调器。
 *
 * Android 12+ 会拦截穿过"组合不透明度 > 上限（默认 0.8）"区域的触摸。
 * 单个窗口钳制到 0.8 即可通过，但两个 0.8 的窗口叠加后组合值约 0.96，
 * 重叠区域（如全宽水印 × 功能列表顶部）仍会被系统丢弃触摸。
 *
 * 各穿透窗口在 attach/detach/alpha 变化时向本协调器上报，
 * 协调器按几何重叠深度统一求解每个窗口允许的最大 alpha：
 * d 个等 alpha 窗口叠加时组合值为 1-(1-a)^d ≤ 0.8，即 a ≤ 1-0.2^(1/d)。
 */
public final class PassThroughOpacityCoordinator {

    private static final String TAG = "PassThroughOpacity";
    private static final float EPSILON = 0.002f;

    private static volatile PassThroughOpacityCoordinator sInstance;

    public static final String ID_WATERMARK = "watermark";
    public static final String ID_STATUS_BAR = "statusBar";
    public static final String ID_FEATURE_LIST = "featureList";
    public static final String ID_LEGACY_LIST = "legacyList";
    public static final String ID_CPS = "cps";
    public static final String ID_CROSSHAIR = "crosshair";
    public static final String ID_TOAST = "toast";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Entry> entries = new ArrayList<>();
    private final Runnable scheduled = this::recomputeInternal;
    private Context appContext;

    private static final class Entry {
        final String id;
        final FrameProvider frameProvider;
        final AlphaApplier applier;
        final boolean touchable;
        float target;
        float applied = Float.NaN;

        Entry(String id, FrameProvider frameProvider, AlphaApplier applier,
              boolean touchable, float target) {
            this.id = id;
            this.frameProvider = frameProvider;
            this.applier = applier;
            this.touchable = touchable;
            this.target = target;
        }
    }

    public interface FrameProvider {
        Rect get();
    }

    public interface AlphaApplier {
        void apply(float alpha);
    }

    public static PassThroughOpacityCoordinator get() {
        if (sInstance == null) {
            synchronized (PassThroughOpacityCoordinator.class) {
                if (sInstance == null) {
                    sInstance = new PassThroughOpacityCoordinator();
                }
            }
        }
        return sInstance;
    }

    public static void init(Context context) {
        get().appContext = context.getApplicationContext();
    }

    public static void notifyAttached(String id, FrameProvider frame,
                                      AlphaApplier applier, boolean touchable,
                                      float target) {
        get().upsert(new Entry(id, frame, applier, touchable, target));
    }

    public static void notifyDetached(String id) {
        PassThroughOpacityCoordinator c = sInstance;
        if (c == null) return;
        synchronized (c.entries) {
            for (int i = c.entries.size() - 1; i >= 0; i--) {
                if (c.entries.get(i).id.equals(id)) {
                    c.entries.remove(i);
                }
            }
        }
        c.schedule();
    }

    public static void notifyTargetChanged(String id, float target) {
        PassThroughOpacityCoordinator c = sInstance;
        if (c == null) return;
        synchronized (c.entries) {
            for (Entry e : c.entries) {
                if (e.id.equals(id)) {
                    e.target = target;
                    break;
                }
            }
        }
        c.schedule();
    }

    /** 窗口位置/尺寸变化可能改变重叠关系，重新求解。 */
    public static void notifyGeometryChanged() {
        get().schedule();
    }

    private void upsert(Entry entry) {
        synchronized (entries) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id.equals(entry.id)) {
                    entries.set(i, entry);
                    schedule();
                    return;
                }
            }
            entries.add(entry);
        }
        schedule();
    }

    private void schedule() {
        mainHandler.removeCallbacks(scheduled);
        mainHandler.post(scheduled);
    }

    private void recomputeInternal() {
        List<Entry> snapshot;
        synchronized (entries) {
            snapshot = new ArrayList<>(entries);
        }
        if (snapshot.isEmpty()) return;

        List<Rect> frames = new ArrayList<>(snapshot.size());
        for (Entry e : snapshot) {
            try {
                Rect r = e.frameProvider.get();
                frames.add(r != null && !r.isEmpty() ? r : null);
            } catch (Exception ex) {
                frames.add(null);
            }
        }

        for (int i = 0; i < snapshot.size(); i++) {
            Rect self = frames.get(i);
            Entry e = snapshot.get(i);
            float eff;
            if (!e.touchable || self == null) {
                eff = e.target;
            } else {
                int depth = maxStackDepth(self, frames);
                eff = depth <= 1 ? e.target : Math.min(e.target, allowPerWindow(depth));
            }
            if (Float.isNaN(e.applied) || Math.abs(eff - e.applied) > EPSILON) {
                e.applied = eff;
                try {
                    e.applier.apply(eff);
                } catch (Exception ex) {
                    AppLogger.w(TAG, "apply alpha failed for " + e.id + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * 求 self 区域内被多少个窗口（含自身）同时覆盖的最大层数。
     */
    private static int maxStackDepth(Rect self, List<Rect> frames) {
        List<Long> xs = new ArrayList<>();
        for (Rect r : frames) {
            if (r == null || r.isEmpty()) continue;
            if (r.left >= self.left && r.left <= self.right) xs.add((long) r.left);
            if (r.right >= self.left && r.right <= self.right) xs.add((long) r.right);
        }
        if (xs.isEmpty()) xs.add((long) self.centerX());

        int maxDepth = 1;
        for (long x : xs) {
            List<long[]> ys = new ArrayList<>();
            for (Rect r : frames) {
            if (r == null || r.isEmpty()) continue;
                if (x >= r.left && x <= r.right) {
                    ys.add(new long[]{r.top, r.bottom});
                }
            }
            ys.sort((a, b) -> Long.compare(a[0], b[0]));
            int n = ys.size();
            for (int i = 0; i < n; i++) {
                int depth = 1;
                long end = ys.get(i)[1];
                for (int j = i + 1; j < n && ys.get(j)[0] < end; j++) {
                    depth++;
                }
                if (depth > maxDepth) maxDepth = depth;
            }
        }
        return maxDepth;
    }

    /** d 层等 alpha 叠加时，每层允许的最大 alpha，使组合值恰好为 0.8。 */
    private static float allowPerWindow(int depth) {
        double allow = 1.0 - Math.pow(0.2, 1.0 / depth);
        return (float) Math.max(0.05, Math.min(0.8, allow));
    }

    /** 由 LayoutParams 还原窗口在屏幕上的绝对矩形（供各窗口注册时使用）。 */
    public static Rect frameOf(Context context, WindowManager.LayoutParams p) {
        int dw = context.getResources().getDisplayMetrics().widthPixels;
        int dh = context.getResources().getDisplayMetrics().heightPixels;
        int w = p.width > 0 ? p.width : dw;
        int h = p.height > 0 ? p.height : dh;
        int g = Gravity.getAbsoluteGravity(p.gravity, View.LAYOUT_DIRECTION_LTR);
        int hMask = Gravity.HORIZONTAL_GRAVITY_MASK;
        int vMask = Gravity.VERTICAL_GRAVITY_MASK;
        int left;
        int top;
        switch (g & hMask) {
            case Gravity.RIGHT:
                left = dw - w - p.x;
                break;
            case Gravity.CENTER_HORIZONTAL:
                left = (dw - w) / 2 + p.x;
                break;
            default:
                left = p.x;
        }
        switch (g & vMask) {
            case Gravity.BOTTOM:
                top = dh - h - p.y;
                break;
            case Gravity.CENTER_VERTICAL:
                top = (dh - h) / 2 + p.y;
                break;
            default:
                top = p.y;
        }
        return new Rect(left, top, left + w, top + h);
    }
}
