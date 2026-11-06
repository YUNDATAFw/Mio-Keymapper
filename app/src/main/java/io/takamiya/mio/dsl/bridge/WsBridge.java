package io.takamiya.mio.dsl.bridge;

import java.util.List;

import cn.fsy.plugin.kore.library.BridgeHandler;
import cn.fsy.plugin.kore.library.KoreException;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.dsl.MioBridgeRegistry;
import io.takamiya.mio.dsl.WsFeatureRegistry;
import io.takamiya.mio.ws.WsEngineHolder;

/**
 * Ws 命名空间桥接实现。
 * 把 MC WebSocket 引擎（{@link WsEngineHolder}）的全部能力暴露给 DSL 插件：
 * 状态查询、启停、命令/聊天、底层消息发送，以及动态功能注册（registerFeature）。
 */
public class WsBridge {

    private static final String TAG = "WsBridge";

    private final WsEngineHolder holder = WsEngineHolder.get();

    // ==================== 状态查询 ====================

    public BridgeHandler isRunning() {
        return (args) -> holder.isRunning();
    }

    public BridgeHandler isConnected() {
        return (args) -> holder.isConnected();
    }

    public BridgeHandler getPort() {
        return (args) -> holder.getPort();
    }

    public BridgeHandler getConnectCommand() {
        return (args) -> holder.getConnectCommand();
    }

    public BridgeHandler getPlayerNames() {
        return (args) -> holder.getPlayerNames();
    }

    public BridgeHandler getPlayers() {
        return (args) -> {
            List<String> list = holder.getPlayers();
            return new java.util.ArrayList<>(list);
        };
    }

    // ==================== 启停 ====================

    public BridgeHandler start() {
        return (args) -> {
            holder.start(holder.getPort());
            return true;
        };
    }

    public BridgeHandler startWithPort() {
        return (args) -> {
            holder.start(num(args, 0, "startWithPort"));
            return true;
        };
    }

    public BridgeHandler stop() {
        return (args) -> {
            holder.stop();
            return true;
        };
    }

    // ==================== 命令 / 聊天 ====================

    public BridgeHandler sendCommand() {
        return (args) -> holder.sendCommand(str(args, 0, "sendCommand"));
    }

    public BridgeHandler sendChat() {
        return (args) -> holder.sendChat(str(args, 0, "sendChat"));
    }

    // ==================== 底层消息 ====================

    public BridgeHandler sendTell() {
        return (args) -> {
            holder.sendTell(str(args, 0, "sendTell"), str(args, 1, "sendTell"));
            return true;
        };
    }

    public BridgeHandler sendToAll() {
        return (args) -> {
            holder.sendToAll(str(args, 0, "sendToAll"));
            return true;
        };
    }

    public BridgeHandler sendTitle() {
        return (args) -> {
            holder.sendTitle(str(args, 0, "sendTitle"), str(args, 1, "sendTitle"),
                    optStr(args, 2));
            return true;
        };
    }

    public BridgeHandler sendActionbar() {
        return (args) -> {
            holder.sendActionbar(str(args, 0, "sendActionbar"), str(args, 1, "sendActionbar"));
            return true;
        };
    }

    public BridgeHandler sendTellraw() {
        return (args) -> {
            holder.sendTellraw(str(args, 0, "sendTellraw"), str(args, 1, "sendTellraw"));
            return true;
        };
    }

    public BridgeHandler sendChatAs() {
        return (args) -> {
            holder.sendChatAs(str(args, 0, "sendChatAs"), str(args, 1, "sendChatAs"));
            return true;
        };
    }

    public BridgeHandler sendMe() {
        return (args) -> {
            holder.sendMe(str(args, 0, "sendMe"));
            return true;
        };
    }

    public BridgeHandler playSound() {
        return (args) -> {
            holder.playSound(str(args, 0, "playSound"), str(args, 1, "playSound"),
                    fnum(args, 2, "playSound"), fnum(args, 3, "playSound"));
            return true;
        };
    }

    public BridgeHandler stopSound() {
        return (args) -> {
            holder.stopSound(str(args, 0, "stopSound"), optStr(args, 1));
            return true;
        };
    }

    public BridgeHandler sendTitleTimes() {
        return (args) -> {
            holder.sendTitleTimes(str(args, 0, "sendTitleTimes"),
                    num(args, 1, "sendTitleTimes"), num(args, 2, "sendTitleTimes"),
                    num(args, 3, "sendTitleTimes"));
            return true;
        };
    }

    public BridgeHandler clearTitle() {
        return (args) -> {
            holder.clearTitle(str(args, 0, "clearTitle"));
            return true;
        };
    }

    // ==================== 功能注册 ====================

    /**
     * registerFeature(key, name, desc) - 动态注册一个 WS 功能开关。
     * 必须在 WS_SERVICE 服务的 serverInit 入口内调用，归属自动取自注册线程上下文。
     */
    public BridgeHandler registerFeature() {
        return (args) -> {
            String key = str(args, 0, "registerFeature");
            String name = str(args, 1, "registerFeature");
            String desc = optStr(args, 2);

            MioEngFunBridge eng = MioBridgeRegistry.getMioEngFunBridge();
            String packageName = eng != null ? eng.getCurrentShortcutPackage() : null;
            String serviceName = eng != null ? eng.getCurrentShortcutOwner() : null;
            if (serviceName == null) {
                throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                        "registerFeature 必须在 WS_SERVICE 服务的 serverInit 中调用");
            }
            WsFeatureRegistry.register(key, packageName, serviceName, name, desc);
            AppLogger.i(TAG, "registerFeature: key=" + key + " name=" + name
                    + " owner=" + packageName + "/" + serviceName);
            return true;
        };
    }

    // ==================== 参数解析 ====================

    private static String str(Object[] args, int index, String fn) {
        if (args == null || index >= args.length || args[index] == null) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 缺少第 " + (index + 1) + " 个参数");
        }
        return args[index].toString();
    }

    private static String optStr(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) return null;
        String s = args[index].toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static int num(Object[] args, int index, String fn) {
        if (args == null || index >= args.length || args[index] == null) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 缺少第 " + (index + 1) + " 个参数");
        }
        Object o = args[index];
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 第 " + (index + 1) + " 个参数必须为整数");
        }
    }

    private static float fnum(Object[] args, int index, String fn) {
        if (args == null || index >= args.length || args[index] == null) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 缺少第 " + (index + 1) + " 个参数");
        }
        Object o = args[index];
        if (o instanceof Number) return ((Number) o).floatValue();
        try {
            return Float.parseFloat(o.toString());
        } catch (NumberFormatException e) {
            throw new KoreException(KoreException.ErrorCode.INVALID_ARGUMENT,
                    fn + ": 第 " + (index + 1) + " 个参数必须为数字");
        }
    }
}