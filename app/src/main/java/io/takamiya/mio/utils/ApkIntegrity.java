package io.takamiya.mio.utils;

import android.app.Activity;
import android.app.Service;
import android.content.Context;

public final class ApkIntegrity {

    static {
        System.loadLibrary("integrity-lib");
    }

    private ApkIntegrity() {
    }

    /**
     * debug 构建直接放行，便于本地开发调试；release 构建才走 native 门禁。
     */
    public static boolean gateAllowed(Context context) {
        String[] r = nativeCheckVerified(context);
        return r != null && "ok".equals(r[0]);
    }

    /**
     * 把门禁决策权从 Java 移到 native。Activity 在 onCreate / onStart 中调用：
     * native 内部完成 marker + dex 指纹校验，通过则静默返回；
     * 失败则由 native 自行 finish Activity + 弹 Toast。
     * 即使 Java 层被 Smali patch 删掉 finish 指令也无济于事。
     * debug 构建请勿调用（Java 端自行 gate）。
     */
    public static native void nativeEnforceGate(Activity activity);

    /**
     * Service 路径的门禁执行入口。返回 true 表示验证通过,Service 可继续 onCreate;
     * 返回 false 表示 native 已在内部 stopSelf + Toast,Java 应直接 return 不再继续。
     * 必须在主线程调用。
     */
    public static native boolean nativeEnforceService(Service service);

    /**
     * 启动 native 主动监控线程。
     * 实际上 so 加载时 __attribute__((constructor)) 已经自启,这里只是冗余入口,
     * 攻击者即使删 Smali 也关不掉 constructor 启动的线程(除非 Frida hook pthread)。
     */
    public static native void nativeStartIntegrityMonitor();

    public static native String apkIntegrityHash(String apkPath);

    public static native String[] dexIntegrityHashList(String apkPath);

    /**
     * 服务器校验（native）。返回 String[]{status, token, msg}。
     * status: ok / no_network / internal_error / http_error / server_error / rejected / parse_error
     */
    public static native String[] nativeVerifyServer(Context context);

    /**
     * native 门禁检查（native）。返回 String[]{status, msg}。
     * status: ok / not_verified / expired / tampered / internal_error
     */
    public static native String[] nativeCheckVerified(Context context);
}
