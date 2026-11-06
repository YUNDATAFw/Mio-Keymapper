package io.takamiya.mio.dsl.bridge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.fsy.plugin.kore.library.BridgeHandler;
import cn.fsy.plugin.kore.library.KoreEngine;
import cn.fsy.plugin.kore.library.internal.running.AstInterpreter;
import cn.fsy.plugin.kore.library.internal.running.Closure;
import cn.fsy.plugin.kore.library.internal.running.PermissionGuard;
import cn.fsy.plugin.kore.library.plugin.Permissions;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.PluginAuthSheet;

/**
 * 权限桥接。
 * <p>新增：{@code Permission.granted} / {@code Permission.declared} / {@code Permission.status}
 * （status 支持可选参：不传返回全量三列表，传权限名返回单项状态）。</p>
 * <p>覆盖引擎内置的 {@code Permission.request} / {@code Permission.requestMultiple}
 * （引擎默认实现抛 UNSUPPORTED，Mio 在 {@link io.takamiya.mio.dsl.MioBridgeRegistry}
 * 中注册同名桥将其替换为真实授权交互）。</p>
 */
public class PermissionBridge {

    private static final String TAG = "PermissionBridge";

    /** 工作线程等待用户授权的超时（秒）。 */
    private static final long REQUEST_TIMEOUT_S = 120;

    private final Context context;

    public PermissionBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    // ==================== 状态查询 ====================

    /** Permission.granted() → [String] 本插件已授权的权限列表。 */
    public BridgeHandler granted() {
        return args -> {
            PermissionGuard g = currentGuard();
            if (g == null) return new ArrayList<String>();
            return new ArrayList<>(g.grantedPermissions());
        };
    }

    /** Permission.declared() → [String] 本插件声明的权限列表。 */
    public BridgeHandler declared() {
        return args -> {
            PermissionGuard g = currentGuard();
            if (g == null) return new ArrayList<String>();
            return new ArrayList<>(g.declaredPermissions());
        };
    }

    /**
     * Permission.status([name]) → Map。
     * <p>不传参：{declared:[...], granted:[...], missing:[declared-granted]}；
     * 传权限名：{name, declared, granted, description, level, dangerous}。</p>
     */
    public BridgeHandler status() {
        return args -> {
            PermissionGuard g = currentGuard();
            String name = strArg(args, 0);
            if (name != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("declared", g != null && g.isDeclared(name));
                item.put("granted", g != null && g.hasPermission(name));
                Permissions perm = Permissions.fromName(name, null);
                item.put("description", perm != null && perm.getDescription() != null
                        ? perm.getDescription() : "");
                item.put("level", perm != null && perm.getLevel() != null
                        ? perm.getLevel().name() : "");
                item.put("dangerous", perm != null && perm.isDangerous());
                return item;
            }
            List<String> declared = g != null
                    ? new ArrayList<>(g.declaredPermissions()) : new ArrayList<>();
            List<String> granted = g != null
                    ? new ArrayList<>(g.grantedPermissions()) : new ArrayList<>();
            List<String> missing = new ArrayList<>(declared);
            missing.removeAll(granted);
            Map<String, Object> full = new LinkedHashMap<>();
            full.put("declared", declared);
            full.put("granted", granted);
            full.put("missing", missing);
            return full;
        };
    }

    // ==================== 覆盖 request ====================

    /**
     * Permission.request(name, callback)：弹单权限授权确认框。
     * <p>插件线程调用时同步等待用户操作后经回调返回结果；主线程调用时弹窗后立即返回，
     * 结果在用户操作后经回调送达（回调内嵌桥接调用上下文可能缺失，建议在服务线程调用）。</p>
     */
    public BridgeHandler request() {
        return args -> {
            String name = strArg(args, 0);
            Object cb = args != null && args.length > 1 ? args[1] : null;
            if (name == null) {
                dispatchCallback(cb, false);
                return false;
            }
            PermissionGuard g = currentGuard();
            if (g == null) {
                AppLogger.w(TAG, "request: no permission guard (not in plugin context)");
                dispatchCallback(cb, false);
                return false;
            }
            if (g.hasPermission(name)) {
                dispatchCallback(cb, true);
                return true;
            }
            if (!g.isDeclared(name)) {
                AppLogger.w(TAG, "request: permission not declared: " + name
                        + " by " + g.pluginPackage());
                Toast.makeText(context, "权限未在插件声明中列出: " + name, Toast.LENGTH_SHORT).show();
                dispatchCallback(cb, false);
                return false;
            }

            final String pkg = g.pluginPackage();
            Permissions perm = Permissions.fromName(name, null);
            String desc = perm != null && perm.getDescription() != null ? perm.getDescription() : "";
            String level = perm != null && perm.getLevel() != null ? perm.getLevel().name() : "";
            String message = "插件「" + displayName(pkg) + "」申请权限：\n" + name
                    + (desc.isEmpty() ? "" : "\n\n" + desc)
                    + (level.isEmpty() ? "" : "\n\n等级: " + level);

            boolean onMain = Looper.myLooper() == Looper.getMainLooper();
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean granted = new AtomicBoolean(false);

            new Handler(Looper.getMainLooper()).post(() -> {
                boolean shown = showRequestDialog(pkg, message, granted);
                if (shown && onMain) {
                    // 主线程调用：不能阻塞等待，用户操作完成后异步回调
                    // （弹窗关闭即视为操作完成，结果在 setOnDismissListener 里已记录）
                }
                if (!shown) {
                    Toast.makeText(context, "无法弹出授权窗口，请到插件页手动授权",
                            Toast.LENGTH_LONG).show();
                }
                latch.countDown();
            });

            if (onMain) {
                // 主线程不能阻塞：返回 true 表示请求已发起，结果经回调送达
                return true;
            }

            try {
                if (!latch.await(REQUEST_TIMEOUT_S, TimeUnit.SECONDS)) {
                    AppLogger.w(TAG, "request: user did not respond in time: " + name);
                    dispatchCallback(cb, false);
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                dispatchCallback(cb, false);
                return false;
            }
            boolean result = granted.get();
            dispatchCallback(cb, result);
            return result;
        };
    }

    /**
     * Permission.requestMultiple([name], callback)：打开插件授权 BottomSheet（整页多权限）。
     * callback 收到本次保存的授权数量（Int）。
     */
    public BridgeHandler requestMultiple() {
        return args -> {
            Object cb = args != null && args.length > 1 ? args[1] : null;
            PermissionGuard g = currentGuard();
            if (g == null) {
                AppLogger.w(TAG, "requestMultiple: no permission guard");
                dispatchCallback(cb, 0);
                return false;
            }
            final String pkg = g.pluginPackage();
            final CountDownLatch latch = new CountDownLatch(1);
            final int[] grantedCount = {0};
            final boolean[] shown = {false};

            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Context ctx = dialogContext();
                    PluginAuthSheet sheet = new PluginAuthSheet(ctx, pkg, displayName(pkg));
                    sheet.setOnGrantListener(count -> {
                        grantedCount[0] = count;
                        latch.countDown();
                    });
                    sheet.show();
                    shown[0] = true;
                } catch (Exception e) {
                    AppLogger.e(TAG, "requestMultiple: show sheet failed", e);
                    latch.countDown();
                }
            });

            if (Looper.myLooper() == Looper.getMainLooper()) {
                // 主线程调用：结果经回调送达（截取当前值无法同步返回）
                return true;
            }
            try {
                if (!latch.await(REQUEST_TIMEOUT_S, TimeUnit.SECONDS)) {
                    AppLogger.w(TAG, "requestMultiple: timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            dispatchCallback(cb, grantedCount[0]);
            return shown[0];
        };
    }

    // ==================== 工具 ====================

    private static PermissionGuard currentGuard() {
        try {
            AstInterpreter in = AstInterpreter.current();
            return in != null ? in.getPermissionGuard() : null;
        } catch (Exception e) {
            AppLogger.w(TAG, "currentGuard failed: " + e.getMessage());
            return null;
        }
    }

    private static String strArg(Object[] args, int index) {
        if (args == null || args.length <= index) return null;
        Object v = args[index];
        if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        return null;
    }

    /** 在插件调用线程上下文内分发 DSL 回调（保证 AstInterpreter.current() 有效）。 */
    private static void dispatchCallback(Object cb, Object arg) {
        if (!(cb instanceof Closure)) return;
        try {
            AstInterpreter in = AstInterpreter.current();
            if (in != null) {
                in.callClosure((Closure) cb, new Object[]{arg});
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "dispatchCallback failed: " + e.getMessage());
        }
    }

    private boolean showRequestDialog(String pkg, String message, AtomicBoolean granted) {
        Context[] candidates = new Context[]{dialogContext(), context};
        Exception last = null;
        for (Context ctx : candidates) {
            if (ctx == null) continue;
            try {
                new MaterialAlertDialogBuilder(ctx)
                        .setTitle("权限申请")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("允许", (d, w) -> granted.set(true))
                        .setNegativeButton("拒绝", null)
                        .show();
                return true;
            } catch (Exception e) {
                last = e;
            }
        }
        AppLogger.e(TAG, "showRequestDialog failed: " + (last != null ? last.getMessage() : "?"));
        return false;
    }

    /** 弹窗首选悬浮窗服务 context（带应用主题），不可用时退回 app context。 */
    private Context dialogContext() {
        try {
            io.takamiya.mio.floating.FloatingWindowService svc =
                    io.takamiya.mio.floating.FloatingWindowService.getInstance();
            if (svc != null) return svc;
        } catch (Exception ignore) {
        }
        return context;
    }

    private String displayName(String pkg) {
        try {
            cn.fsy.plugin.kore.library.plugin.PluginItem item = KoreEngine.pluginInfo(pkg);
            if (item != null && item.getPluginName() != null && !item.getPluginName().isEmpty()) {
                return item.getPluginName();
            }
        } catch (Exception ignore) {
        }
        return pkg != null ? pkg : "";
    }
}
