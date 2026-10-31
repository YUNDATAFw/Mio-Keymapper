package io.takamiya.mio.ws;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

/**
 * MC WebSocket 服务状态/引擎的薄门面（面板层）。
 *
 * <p>自 v2 起引擎逻辑全部收敛到 {@link WsEngineHolder} 单例，本类只做委托转发，
 * 并保留 {@link CmdResult}/{@link ChatLine} 等结构类型与旧方法签名，保证各 WS 面板
 * fragment 无需改动即可编译。</p>
 *
 * <p>引擎独立于面板生命周期：本 ViewModel 被清空（面板关闭）不会停止引擎，
 * 插件可通过 {@code Ws.*} 桥自主启停。</p>
 */
public class WsViewModel extends ViewModel {

    private final WsEngineHolder holder = WsEngineHolder.get();

    /** 一次命令执行的结果。 */
    public static class CmdResult {
        public final String requestId;
        public final int statusCode;
        public final String message;

        public CmdResult(String requestId, int statusCode, String message) {
            this.requestId = requestId;
            this.statusCode = statusCode;
            this.message = message;
        }
    }

    /** 一条聊天消息（玩家名 + 内容）。 */
    public static class ChatLine {
        public final String name;
        public final String message;

        ChatLine(String name, String message) {
            this.name = name == null ? "" : name;
            this.message = message == null ? "" : message;
        }
    }

    // ==================== 对外状态 ====================

    public LiveData<Boolean> running() {
        return holder.running();
    }

    public LiveData<Boolean> connected() {
        return holder.connected();
    }

    public LiveData<String> remoteAddress() {
        return holder.remoteAddress();
    }

    public LiveData<Integer> remotePort() {
        return holder.remotePort();
    }

    public LiveData<Integer> port() {
        return holder.port();
    }

    /** 当前在线玩家名单（逗号分隔，未取得时为空）。 */
    public LiveData<String> playerNames() {
        return holder.playerNames();
    }

    /** 当前在线玩家列表（下拉框数据源）。 */
    public LiveData<List<String>> players() {
        return holder.players();
    }

    /** 最近一条命令执行响应。 */
    public LiveData<CmdResult> commandResponse() {
        return holder.commandResponse();
    }

    /** 聊天记录（结构化玩家名 + 消息）。 */
    public LiveData<List<ChatLine>> chatLines() {
        return holder.chatLines();
    }

    /** 引擎运行日志行。 */
    public LiveData<List<String>> logLines() {
        return holder.logLines();
    }

    public boolean isRunning() {
        return holder.isRunning();
    }

    public boolean isConnected() {
        return holder.isConnected();
    }

    public String getRemoteAddress() {
        return holder.getRemoteAddress();
    }

    public int getRemotePort() {
        return holder.getRemotePort();
    }

    public int getPort() {
        return holder.getPort();
    }

    /** 获取 /connect 命令（未运行时按当前配置端口给出预览）。 */
    public String getConnectCommand() {
        return holder.getConnectCommand();
    }

    /** 当前在线玩家名单文本（未连接时返回 null）。 */
    public String getPlayerNames() {
        return holder.getPlayerNames();
    }

    /** 发送一条命令。未连接时返回 null，成功返回 requestId。 */
    public String sendCommand(String commandLine) {
        return holder.sendCommand(commandLine);
    }

    /** 向游戏公开聊天框发言（以命令执行者 @s 身份 run say，效果同聊天框回车）。 */
    public boolean sendChat(String message) {
        return holder.sendChat(message);
    }

    // ==================== 生命周期 ====================

    public void start(int port) {
        holder.start(port);
    }

    public void stop() {
        holder.stop();
    }

    /** 清空日志页显示的日志（同时清引擎历史）。 */
    public void clearLogs() {
        holder.clearLogs();
    }

    @Override
    protected void onCleared() {
        // 引擎独立于面板生命周期，Board 关闭不停止 WS。
        super.onCleared();
    }
}