package io.takamiya.mio.ws;

import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONObject;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * MC WebSocket 引擎静态持有者（单例）。
 *
 * <p>独立于面板/悬浮窗服务生命周期：面板关闭不影响引擎；插件可通过 {@code Ws.*} 类
 * 直接启停/操作。面板层 {@link WsViewModel} 只是本类的薄门面。</p>
 *
 * <p>WS 游戏事件到达后，除内置兜底处理（聊天记录）外，还会把
 * 原始 JSON 原样转发给已注册的 {@link WsEventListener}（由 DslPluginManager 注册，
 * 用于向 DSL 插件广播）。人机校验、游戏规则/黑名单等业务逻辑由 DSL 插件实现，
 * 宿主不再内置。</p>
 */
public class WsEngineHolder {

    private static final String TAG = "WsEngineHolder";
    private static final int DEFAULT_PORT = 8000;
    private static final int MAX_CHAT_LINES = 200;
    private static final int MAX_LOG_LINES = 500;

    /** 连接后自动订阅的全量事件（供插件广播监听，与 McbeWebSocket 快捷订阅同名）。 */
    private static final String[] ALL_EVENTS = {
            "PlayerMessage", "PlayerJoin", "PlayerLeave",
            "BlockPlaced", "BlockBroken", "PlayerTravelled", "PlayerTransform",
            "PlayerTeleported", "PlayerDied", "PlayerBounced",
            "ItemDropped", "ItemUsed", "ItemSmelted", "ItemAcquired",
            "ItemCrafted", "ItemDestroyed", "ItemEnchanted", "PotionBrewed",
            "MobKilled", "EndOfDay", "PortalBuilt", "PortalUsed",
            "VehicleExited", "UploadSkin", "AwardAchievement", "CauldronUsed",
            "JukeboxUsed", "CraftingSessionCompleted", "EntitySpawned"
    };

    private static final WsEngineHolder INSTANCE = new WsEngineHolder();

    public static WsEngineHolder get() {
        return INSTANCE;
    }

    /** WS 事件监听器：收到原始 WS 事件 JSON 时回调（插件广播 / Java 功能分发共用）。 */
    public interface WsEventListener {
        void onWsEvent(String eventName, String rawJson);
    }

    /** WS 生命周期监听器（服务启停 / 连接断开），供 Java 功能分发。 */
    public interface WsLifecycleListener {
        void onServerStarted();
        void onServerStopped();
        void onConnected();
        void onDisconnected();
    }

    private final java.util.concurrent.CopyOnWriteArrayList<WsEventListener> eventListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<WsLifecycleListener> lifecycleListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addWsEventListener(WsEventListener listener) {
        if (listener != null) eventListeners.addIfAbsent(listener);
    }

    public void removeWsEventListener(WsEventListener listener) {
        eventListeners.remove(listener);
    }

    public void addWsLifecycleListener(WsLifecycleListener listener) {
        if (listener != null) lifecycleListeners.addIfAbsent(listener);
    }

    public void removeWsLifecycleListener(WsLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<Boolean> runningLive = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> connectedLive = new MutableLiveData<>(false);
    private final MutableLiveData<String> remoteAddressLive = new MutableLiveData<>();
    private final MutableLiveData<Integer> remotePortLive = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> portLive = new MutableLiveData<>(loadPort());
    private final MutableLiveData<String> playerNamesLive = new MutableLiveData<>();
    private final MutableLiveData<List<String>> playersLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<WsViewModel.CmdResult> commandResponseLive = new MutableLiveData<>();
    private final MutableLiveData<List<WsViewModel.ChatLine>> chatLinesLive =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> logLinesLive = new MutableLiveData<>(new ArrayList<>());

    private McbeWebSocket ws;
    private volatile boolean running;
    private volatile boolean connected;
    private volatile String remoteAddress;
    private volatile int remotePort;

    private final Set<String> subscribedEvents = new HashSet<>();

    private WsEngineHolder() {
    }

    private void postMain(Runnable r) {
        mainHandler.post(r);
    }

    // ==================== 对外状态（LiveData，供面板） ====================

    public LiveData<Boolean> running() {
        return runningLive;
    }

    public LiveData<Boolean> connected() {
        return connectedLive;
    }

    public LiveData<String> remoteAddress() {
        return remoteAddressLive;
    }

    public LiveData<Integer> remotePort() {
        return remotePortLive;
    }

    public LiveData<Integer> port() {
        return portLive;
    }

    public LiveData<String> playerNames() {
        return playerNamesLive;
    }

    public LiveData<List<String>> players() {
        return playersLive;
    }

    public LiveData<WsViewModel.CmdResult> commandResponse() {
        return commandResponseLive;
    }

    public LiveData<List<WsViewModel.ChatLine>> chatLines() {
        return chatLinesLive;
    }

    public LiveData<List<String>> logLines() {
        return logLinesLive;
    }

    // ==================== 查询（线程安全） ====================

    public boolean isRunning() {
        return running;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public int getPort() {
        Integer v = portLive.getValue();
        return v == null ? DEFAULT_PORT : v;
    }

    /** 获取 /connect 命令（未运行时按当前配置端口给出预览） */
    public String getConnectCommand() {
        if (ws != null) return ws.getConnectCommand();
        return "/connect " + McbeWebSocket.getWifiIp() + ":" + getPort();
    }

    /** 当前在线玩家名单文本（未连接时返回 null）。 */
    public String getPlayerNames() {
        String v = playerNamesLive.getValue();
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** 当前在线玩家列表（供桥接返回 [String]）。 */
    public List<String> getPlayers() {
        List<String> v = playersLive.getValue();
        return v == null ? new ArrayList<String>() : new ArrayList<>(v);
    }

    // ==================== 命令 / 聊天（任意线程安全） ====================

    /** 发送一条命令。未连接返回 null，成功返回 requestId。 */
    public String sendCommand(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) return null;
        if (!running || ws == null || !ws.isConnected()) return null;
        return ws.command(commandLine.trim());
    }

    /** 向游戏公开聊天框发言（ws run say，效果同聊天框回车）。 */
    public boolean sendChat(String message) {
        if (message == null || message.trim().isEmpty()) return false;
        if (!running || ws == null || !ws.isConnected()) return false;
        try {
            ws.sendChatAsSelf(message.trim());
            return true;
        } catch (Exception e) {
            AppLogger.w(TAG, "send chat failed: " + e.getMessage());
            return false;
        }
    }

    // ==================== 底层发送（供桥接，任意线程安全） ====================

    public void sendTell(String target, String message) {
        if (ws == null || !connected) return;
        try {
            ws.sendTell(target, message);
        } catch (Exception e) {
            AppLogger.w(TAG, "sendTell failed: " + e.getMessage());
        }
    }

    public void sendToAll(String message) {
        sendTell("@a", message);
    }

    public void sendTitle(String target, String title, String subtitle) {
        if (ws == null || !connected) return;
        try {
            ws.sendTitle(target, title, subtitle);
        } catch (Exception e) {
            AppLogger.w(TAG, "sendTitle failed: " + e.getMessage());
        }
    }

    public void sendActionbar(String target, String text) {
        if (ws == null || !connected) return;
        try {
            ws.sendActionbar(target, text);
        } catch (Exception e) {
            AppLogger.w(TAG, "sendActionbar failed: " + e.getMessage());
        }
    }

    public void sendTellraw(String target, String text) {
        if (ws == null || !connected) return;
        try {
            ws.sendTellraw(target, text);
        } catch (Exception e) {
            AppLogger.w(TAG, "sendTellraw failed: " + e.getMessage());
        }
    }

    public void sendChatAs(String player, String message) {
        if (ws == null || !connected) return;
        try {
            ws.sendChatAs(player, message);
        } catch (Exception e) {
            AppLogger.w(TAG, "sendChatAs failed: " + e.getMessage());
        }
    }

    public void sendMe(String action) {
        if (ws == null || !connected) return;
        try {
            ws.sendMe(action);
        } catch (Exception e) {
            AppLogger.w(TAG, "sendMe failed: " + e.getMessage());
        }
    }

    public void playSound(String sound, String target, float volume, float pitch) {
        if (ws == null || !connected) return;
        try {
            ws.playSound(sound, target, volume, pitch);
        } catch (Exception e) {
            AppLogger.w(TAG, "playSound failed: " + e.getMessage());
        }
    }

    public void stopSound(String target, String sound) {
        if (ws == null || !connected) return;
        try {
            ws.stopSound(target, sound);
        } catch (Exception e) {
            AppLogger.w(TAG, "stopSound failed: " + e.getMessage());
        }
    }

    public void sendTitleTimes(String target, int fadeIn, int stay, int fadeOut) {
        if (ws == null || !connected) return;
        try {
            ws.sendTitleTimes(target, fadeIn, stay, fadeOut);
        } catch (Exception e) {
            AppLogger.w(TAG, "sendTitleTimes failed: " + e.getMessage());
        }
    }

    public void clearTitle(String target) {
        if (ws == null || !connected) return;
        try {
            ws.clearTitle(target);
        } catch (Exception e) {
            AppLogger.w(TAG, "clearTitle failed: " + e.getMessage());
        }
    }

    // ==================== 生命周期 ====================

    public void start(int port) {
        postMain(() -> applyStart(port));
    }

    private synchronized void applyStart(int port) {
        portLive.setValue(port);
        savePort(port);
        subscribedEvents.clear();
        if (ws != null && ws.getPort() != port) {
            tearDown();
        }
        if (ws == null) {
            ws = new McbeWebSocket(port, new McbeWebSocket.Callback() {
                @Override public void onMessage(String json) {
                }

                @Override public void onCommandResponse(String requestId, int statusCode, String message) {
                }

                @Override public void onRawCommandResponse(
                        String requestId, int statusCode, String message, String rawJson) {
                    commandResponseLive.setValue(
                            new WsViewModel.CmdResult(requestId, statusCode, message));
                }

                @Override public void onConnect() {
                }

                @Override public void onDisconnect() {
                }

                @Override public void onError(Exception e) {
                }

                @Override public void onEvent(String eventName, String propertiesJson) {
                    handleEvent(eventName, propertiesJson);
                }

                @Override public void onBlockData(String requestId, String dataJson) {
                }

                @Override public void onItemData(String requestId, String dataJson) {
                }

                @Override public void onMobData(String requestId, String dataJson) {
                }
            });
            ws.setLogCallback((level, logTag, message) -> appendLog(level, logTag, message));
            List<McbeWebSocket.LogEntry> initial = ws.getRecentLogs(MAX_LOG_LINES);
            for (McbeWebSocket.LogEntry e : initial) {
                appendLog(e.level, e.tag, e.message);
            }
            ws.setConnectionCallback(new McbeWebSocket.ConnectionCallback() {
                @Override public void onConnect(String address, int rPort) {
                    connected = true;
                    remoteAddress = address;
                    remotePort = rPort;
                    connectedLive.setValue(true);
                    remoteAddressLive.setValue(address);
                    remotePortLive.setValue(rPort);
                    AppLogger.i(TAG, "MC connected: " + address + ":" + rPort);
                    subscribeAllEvents();
                    sendConnectHint();
                    queryPlayerNames();
                    notifyLifecycle(WsLifecycleListener::onConnected);
                }

                @Override public void onDisconnect() {
                    connected = false;
                    remoteAddress = null;
                    remotePort = 0;
                    connectedLive.setValue(false);
                    remoteAddressLive.setValue(null);
                    remotePortLive.setValue(0);
                    AppLogger.i(TAG, "MC disconnected");
                    notifyLifecycle(WsLifecycleListener::onDisconnected);
                }
            });
        }
        if (running) {
            AppLogger.w(TAG, "start ignored, already running");
            return;
        }
        running = true;
        connected = false;
        remoteAddress = null;
        remotePort = 0;
        connectedLive.setValue(false);
        remoteAddressLive.setValue(null);
        remotePortLive.setValue(0);
        ws.start();
        runningLive.setValue(true);
        AppLogger.i(TAG, "WS service starting on port " + port);
        notifyLifecycle(WsLifecycleListener::onServerStarted);
    }

    public void stop() {
        postMain(this::applyStop);
    }

    private synchronized void applyStop() {
        running = false;
        connected = false;
        remoteAddress = null;
        remotePort = 0;
        connectedLive.setValue(false);
        remoteAddressLive.setValue(null);
        remotePortLive.setValue(0);
        if (ws != null) {
            ws.stop();
        }
        runningLive.setValue(false);
        AppLogger.i(TAG, "WS service stopped");
        notifyLifecycle(WsLifecycleListener::onServerStopped);
    }

    /** 向所有生命周期监听器分发状态回调。 */
    private void notifyLifecycle(java.util.function.Consumer<WsLifecycleListener> dispatch) {
        for (WsLifecycleListener l : lifecycleListeners) {
            try {
                dispatch.accept(l);
            } catch (Exception e) {
                AppLogger.w(TAG, "ws lifecycle forward failed: " + e.getMessage());
            }
        }
    }

    /** 连接成功后在游戏内广播一条提示消息，并播放连接提示音。 */
    private void sendConnectHint() {
        if (ws == null || !connected) return;
        try {
            ws.sendToAll("§8[§bMio WebSocket§8] §f已连接到 Mio WebSocket 服务");
        } catch (Exception e) {
            AppLogger.w(TAG, "send connect hint failed: " + e.getMessage());
        }
        try {
            ws.playSound("note.harp", "@a", 100.0f, 1.0f);
        } catch (Exception e) {
            AppLogger.w(TAG, "play connect sound failed: " + e.getMessage());
        }
    }

    /** 连接成功：订阅全部事件（供面板与插件广播共同使用）。 */
    private void subscribeAllEvents() {
        for (String event : ALL_EVENTS) {
            if (subscribedEvents.add(event)) {
                ws.subscribe(event);
            }
        }
    }

    /** 连接成功后查询一次在线玩家名单（testfor @a）。 */
    private void queryPlayerNames() {
        if (ws == null || !connected) return;
        try {
            ws.getOnlinePlayers((requestId, players) -> {
                List<String> list = players == null ? new ArrayList<>() : players;
                playersLive.setValue(list);
                playerNamesLive.setValue(list.isEmpty() ? "" : String.join(", ", list));
            });
        } catch (Exception e) {
            AppLogger.w(TAG, "query player names failed: " + e.getMessage());
        }
    }

    private void handleEvent(String eventName, String propertiesJson) {
        // 原始 JSON 原样广播给所有监听者（插件广播 / Java 功能分发）
        for (WsEventListener listener : eventListeners) {
            try {
                listener.onWsEvent(eventName, propertiesJson);
            } catch (Exception e) {
                AppLogger.w(TAG, "ws event forward failed: " + e.getMessage());
            }
        }
        if (propertiesJson == null) return;
        try {
            JSONObject o = new JSONObject(propertiesJson);
            if ("PlayerMessage".equals(eventName)) {
                String sender = o.optString("sender", "").trim();
                String receiver = o.optString("receiver", "").trim();
                String type = o.optString("type", "").trim();
                String msg = o.optString("message", "");
                if (msg.isEmpty()) return;
                if ("title".equals(type)) {
                    msg = extractRawtext(msg);
                }
                msg = stripFormatCodes(msg);
                if (msg.isEmpty()) return;
                appendChat(nameForChat(sender, type), decorateChat(receiver, type, msg));
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "handle event failed: " + e.getMessage());
        }
    }

    /** 聊天消息发言名：sender 为空时按类型给占位（广播/系统消息）。 */
    private String nameForChat(String sender, String type) {
        if (!sender.isEmpty()) return sender;
        return "title".equals(type) ? "广播" : "系统";
    }

    /** 私聊（msg/tell/whisper 且带 receiver）时在消息前标注接收者。 */
    private String decorateChat(String receiver, String type, String msg) {
        if (!receiver.isEmpty() && isPrivateType(type)) {
            return "[私聊 → " + receiver + "] " + msg;
        }
        return msg;
    }

    private boolean isPrivateType(String type) {
        return "msg".equals(type) || "tell".equals(type) || "whisper".equals(type);
    }

    /** 去掉基岩版格式符 §x 与空行，合并多余空白，便于单行展示。 */
    private String stripFormatCodes(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        boolean lastSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '搂') {
                i++;
                continue;
            }
            if (c == '\n' || c == '\r' || c == '\t') {
                c = ' ';
            }
            if (Character.isISOControl(c)) continue;
            if (c == ' ') {
                if (lastSpace) continue;
                lastSpace = true;
            } else {
                lastSpace = false;
            }
            sb.append(c);
        }
        String out = sb.toString().trim();
        return " ".equals(out) ? "" : out;
    }

    /** 从 titleraw 的 {"rawtext":[...]} 里尽量提取纯文本。 */
    private String extractRawtext(String msg) {
        try {
            if (msg.trim().startsWith("{")) {
                JSONObject root = new JSONObject(msg);
                StringBuilder sb = new StringBuilder();
                org.json.JSONArray arr = root.optJSONArray("rawtext");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.optJSONObject(i);
                        if (item != null) {
                            String t = item.optString("text", "");
                            if (!t.isEmpty()) sb.append(t);
                        }
                    }
                    if (sb.length() > 0) return sb.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return msg;
    }

    private synchronized void tearDown() {
        running = false;
        connected = false;
        remoteAddress = null;
        remotePort = 0;
        connectedLive.setValue(false);
        remoteAddressLive.setValue(null);
        remotePortLive.setValue(0);
        if (ws != null) {
            ws.release();
            ws = null;
        }
        runningLive.setValue(false);
        AppLogger.i(TAG, "WS engine released");
    }

    // ==================== 内部数据更新 ====================

    private void appendChat(String name, String message) {
        List<WsViewModel.ChatLine> cur = new ArrayList<>(
                chatLinesLive.getValue() == null
                        ? new ArrayList<>() : chatLinesLive.getValue());
        cur.add(new WsViewModel.ChatLine(name, message));
        if (cur.size() > MAX_CHAT_LINES) {
            cur = new ArrayList<>(cur.subList(cur.size() - MAX_CHAT_LINES, cur.size()));
        }
        chatLinesLive.setValue(cur);
    }

    private final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private void appendLog(String level, String logTag, String message) {
        String time = logTimeFormat.format(new Date());
        String line = "[" + time + "] " + level + "/" + logTag + ": " + message;
        List<String> cur = new ArrayList<>(
                logLinesLive.getValue() == null ? new ArrayList<>() : logLinesLive.getValue());
        cur.add(line);
        if (cur.size() > MAX_LOG_LINES) {
            cur = new ArrayList<>(cur.subList(cur.size() - MAX_LOG_LINES, cur.size()));
        }
        logLinesLive.setValue(cur);
    }

    /** 清空日志（同时清引擎历史）。 */
    public void clearLogs() {
        postMain(() -> {
            logLinesLive.setValue(new ArrayList<>());
            if (ws != null) {
                ws.clearLogs();
            }
        });
    }

    // ==================== 持久化 ====================

    private int loadPort() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return DEFAULT_PORT;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_WS_PORT.getValue())) {
                return reg.getInt(DataManage.Tag.APP_CONFIG_WS_PORT.getValue());
            }
        } catch (RegistryException e) {
            AppLogger.w(TAG, "failed to load ws port: " + e.getMessage());
        }
        return DEFAULT_PORT;
    }

    private void savePort(int port) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_WS_PORT.getValue(), port);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save ws port failed: " + e.getMessage(), e);
        }
    }
}
