package io.takamiya.mio.ws;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * MCBE WebSocket 工具类 v4（纯 Java，Android / 桌面通用）
 *
 * Full-featured WebSocket client for Minecraft Bedrock Edition.
 *
 * Protocol reference: https://zh.minecraft.wiki/w/Tutorial:WebSocket (Chinese Minecraft Wiki)
 * Also based on community reverse-engineering projects:
 *   - sanand0/minecraft-websocket
 *   - Valcyle/bedrockws
 *   - biyuehu/mcwss
 *   - WavePlayz/bedrock-websocket-server
 *
 * NOTE: This is an unofficial, undocumented API. Microsoft has stated they do
 * not plan to support WebSockets as a formal external-facing API.
 *
 * Features:
 *   - Non-blocking start(), runs on daemon thread
 *   - Callbacks delivered on main thread (safe for UI updates)
 *   - Auto-reconnect with exponential backoff on disconnect
 *   - Auto-resubscribe to events after reconnect
 *   - Built-in log storage with optional real-time log callback
 *   - Command queue management (100 command limit)
 *   - Event subscription system with typed callbacks
 *   - Data query API (block, item, mob) - experimental, mainly Education Edition
 *   - Hidden command wrappers (getTopSolidBlock, queryTarget, etc.)
 *   - Player list queries: online players / players holding an item (testfor + hasitem)
 *   - Messaging: sendTell/sendToAll, playSound, titleraw (actionbar/title)
 *   - getWifiIp() / getConnectCommand() helpers
 *
 * Usage:
 *   McbeWebSocket ws = new McbeWebSocket(8000, callback);
 *   ws.start();
 *
 *   // Commands
 *   ws.command("/kill @e");
 *   ws.commands("/say hi", "/time set day");
 *
 *   // Events
 *   ws.subscribe("PlayerMessage");
 *   ws.onPlayerMessage(new McbeWebSocket.EventCallback() { ... });
 *
 *   // Data queries (experimental)
 *   ws.queryBlock("~ ~ ~");
 *   ws.queryItem();
 *   ws.queryMob();
 *
 *   // Hidden commands
 *   ws.getTopSolidBlock(100, 64, 200);
 *   ws.queryTarget("@e[type=armor_stand]");
 *   ws.getPlayerName();
 *   ws.disconnect();
 *
 *   // Player lists
 *   ws.getOnlinePlayers(new McbeWebSocket.PlayerListCallback() {
 *       @Override public void onPlayers(String requestId, List<String> players) {
 *           // players = ["Steve", "Alex", ...]
 *       }
 *   });
 *   ws.getPlayersHolding("diamond_sword", cb); // mainhand
 *   ws.getPlayersWithItem("apple", "slot.inventory", 3, -1, cb);
 *
 *   // Messaging
 *   ws.sendToAll("§ahi");
 *   ws.playSound("note.pling", "@a", 100, 1.0f);
 *   ws.sendActionbar("@a", "§e进度 50%");
 *   ws.sendTitle("@a", "§6开始", null);
 *
 *   // Connection status
 *   ws.setConnectionCallback(new McbeWebSocket.ConnectionCallback() {
 *       @Override public void onConnect(String addr, int remotePort) {
 *           // MC 客户端已连上：addr=设备的 WiFi IP, remotePort=游戏端端口
 *       }
 *       @Override public void onDisconnect() {
 *           // 客户端断开
 *       }
 *   });
 */
public class McbeWebSocket {

    private static final String TAG = "McbeWS";
    private static final long INITIAL_RECONNECT_DELAY_MS = 2000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;
    private static final int MAX_LOG_ENTRIES = 2000;
    private static final int MAX_CONCURRENT_COMMANDS = 95; // MC limit is 100, leave some margin

    /**
     * 命令格式版本。参考 Minecraft Wiki:
     * "body.version 可以调整命令的格式版本，一些命令会因此受到影响（例如：/execute）。
     *  任意 CommandResponse 都会返回当前版本的 version 值。当 version 的值为较新版本时，
     *  通常命令版本也是新的。例如，当 version 的值为 17039360 时（基岩版 1.21.2），
     *  /execute 命令的格式为新版。"
     */
    private int commandVersion = 1;

    // ==================== 回调接口 ====================

    /** 统一大接口：包含所有回调。 */
    public interface Callback {
        void onMessage(String json);
        void onCommandResponse(String requestId, int statusCode, String message);

        /**
         * 收到 commandResponse 且附带完整原始 JSON 帧时的回调。
         * 需要按 JSON 解析 body.value / body.details 等精确字段时覆写此方法。
         */
        default void onRawCommandResponse(String requestId, int statusCode, String message, String rawJson) {
        }

        void onConnect();
        void onDisconnect();
        void onError(Exception e);
        void onEvent(String eventName, String propertiesJson);
        void onBlockData(String requestId, String dataJson);
        void onItemData(String requestId, String dataJson);
        void onMobData(String requestId, String dataJson);
    }

    /** 独立事件回调接口。 */
    public interface EventCallback {
        void onEvent(String eventName, String propertiesJson);
    }

    /** 独立数据查询回调接口。 */
    public interface DataCallback {
        void onBlockData(String requestId, String dataJson);
        void onItemData(String requestId, String dataJson);
        void onMobData(String requestId, String dataJson);
    }

    /** 玩家名单查询回调接口。 */
    public interface PlayerListCallback {
        /**
         * 收到玩家名单结果。
         * 命令失败或无人匹配时 players 为空列表，并会输出错误日志。
         *
         * @param requestId 对应查询的 requestId（与 getOnlinePlayers 等返回值一致）
         * @param players   玩家名称列表，无需匹配顺序
         */
        void onPlayers(String requestId, List<String> players);
    }

    /** 日志实时回调（可选）。 */
    public interface LogCallback {
        void onLog(String level, String tag, String message);
    }

    /** 客户端连接状态回调（轻量，只监听连接/断开）。 */
    public interface ConnectionCallback {
        /** MC 客户端连接成功（WebSocket 握手完成后触发）。 */
        void onConnect(String remoteAddress, int remotePort);
        /** MC 客户端断开连接。 */
        void onDisconnect();
    }

    // ==================== 日志条目 ====================

    public static class LogEntry {
        public final long timestamp;
        public final String level;
        public final String tag;
        public final String message;

        public LogEntry(long timestamp, String level, String tag, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        @Override
        public String toString() {
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(timestamp));
            return "[" + time + "] " + level + "/" + tag + ": " + message;
        }
    }

    // ==================== 主线程派发器 ====================

    /**
     * 回调主线程派发器（纯 Java，无 Android 编译期依赖）。
     * - Android 上运行时通过反射把回调投递到 UI 主线程（保持原设计语义）
     * - 桌面 / 纯 Java 环境下退化为内部专用派发线程，保证纯 javac/java 即可编译运行
     */
    private static final class MainPoster {
        private final Object androidHandler;
        private final Object androidMainLooper;
        private final java.lang.reflect.Method postMethod;
        private final java.lang.reflect.Method postDelayedMethod;
        private final java.lang.reflect.Method currentLooperMethod;
        private final java.util.concurrent.BlockingQueue<Runnable> queue =
                new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        private final Thread dispatcherThread;
        private final java.util.concurrent.ScheduledExecutorService delayExecutor;

        MainPoster() {
            Object handler = null;
            Object mainLooper = null;
            java.lang.reflect.Method post = null;
            java.lang.reflect.Method delayed = null;
            java.lang.reflect.Method current = null;
            try {
                Class<?> looperCls = Class.forName("android.os.Looper");
                java.lang.reflect.Method getMain = looperCls.getMethod("getMainLooper");
                mainLooper = getMain.invoke(null);
                current = looperCls.getMethod("myLooper");
                Class<?> handlerCls = Class.forName("android.os.Handler");
                java.lang.reflect.Constructor<?> ctor = handlerCls.getConstructor(looperCls);
                handler = ctor.newInstance(mainLooper);
                post = handlerCls.getMethod("post", Runnable.class);
                delayed = handlerCls.getMethod("postDelayed", Runnable.class, long.class);
            } catch (Exception ignore) {
                handler = null;
                mainLooper = null;
                post = null;
                delayed = null;
                current = null;
            }
            this.androidHandler = handler;
            this.androidMainLooper = mainLooper;
            this.postMethod = post;
            this.postDelayedMethod = delayed;
            this.currentLooperMethod = current;

            this.delayExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                    new java.util.concurrent.ThreadFactory() {
                        @Override public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "McbeWS-Delay");
                            t.setDaemon(true);
                            return t;
                        }
                    });

            this.dispatcherThread = new Thread(new Runnable() {
                @Override public void run() {
                    for (;;) {
                        try {
                            queue.take().run();
                        } catch (InterruptedException e) {
                            break;
                        } catch (Throwable t) {
                            System.err.println("McbeWS callback error: " + t);
                        }
                    }
                }
            }, "McbeWS-Main");
            this.dispatcherThread.setDaemon(true);
            this.dispatcherThread.start();
        }

        /** 关闭延迟调度线程池并中断派发线程（实例销毁时调用）。 */
        void shutdown() {
            try {
                delayExecutor.shutdownNow();
            } catch (Throwable ignored) {
            }
            if (dispatcherThread != null) {
                dispatcherThread.interrupt();
            }
            queue.clear();
        }

        /** 判断当前线程是否已是目标"主"线程。 */
        private boolean onMainThread() {
            if (androidHandler != null) {
                try {
                    Object cur = currentLooperMethod.invoke(null);
                    return cur != null && cur == androidMainLooper;
                } catch (Exception ignore) {
                    return false;
                }
            }
            return Thread.currentThread() == dispatcherThread;
        }

        void post(Runnable r) {
            if (onMainThread()) {
                r.run();
                return;
            }
            if (androidHandler != null) {
                try {
                    postMethod.invoke(androidHandler, r);
                    return;
                } catch (Exception ignore) {
                    // fall through to local queue
                }
            }
            queue.offer(r);
        }

        void postDelayed(Runnable r, long delayMs) {
            if (androidHandler != null) {
                try {
                    postDelayedMethod.invoke(androidHandler, r, delayMs);
                    return;
                } catch (Exception ignore) {
                    // fall through to local scheduler
                }
            }
            delayExecutor.schedule(new Runnable() {
                @Override public void run() { post(r); }
            }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    // ==================== 命令队列项 ====================

    private static class CommandEntry {
        final String requestId;
        final String json;
        final long timestamp;

        CommandEntry(String requestId, String json) {
            this.requestId = requestId;
            this.json = json;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // ==================== 待响应回调项 ====================

    /** 收到 commandResponse 时的回调。 */
    private interface PendingReply {
        void onResponse(String requestId, String json, int statusCode, String statusMessage);
    }

    private static class PendingCommand {
        final long issuedAt;
        final PendingReply reply;

        PendingCommand(PendingReply reply) {
            this.issuedAt = System.currentTimeMillis();
            this.reply = reply;
        }
    }

    /** 命令未在超时时间内收到响应时的清理策略。 */
    private static final long PENDING_COMMAND_TIMEOUT_MS = 10000;

    // ==================== 成员变量 ====================

    private final int port;
    private final Callback callback;
    private final MainPoster poster = new MainPoster();
    private final CopyOnWriteArrayList<LogEntry> logEntries = new CopyOnWriteArrayList<LogEntry>();
    private final Set<String> subscribedEvents = Collections.synchronizedSet(new HashSet<String>());
    private final ConcurrentLinkedQueue<CommandEntry> commandQueue = new ConcurrentLinkedQueue<CommandEntry>();
    private final Map<String, PendingCommand> pendingCommands = new ConcurrentHashMap<String, PendingCommand>();
    private final Object logLock = new Object();
    private File fileLog;

    private LogCallback logCallback;
    private EventCallback eventCallback;
    private DataCallback dataCallback;
    private ConnectionCallback connectionCallback;
    private ServerSocket serverSocket;
    private Socket mcSocket;
    private BufferedOutputStream out;
    private final Object sendLock = new Object();
    /** 串行发送线程：所有 socket 写都在此线程执行，避免主线程触发 NetworkOnMainThreadException。 */
    private final java.util.concurrent.ExecutorService sendExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(
                    new java.util.concurrent.ThreadFactory() {
                        @Override public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "McbeWS-Send");
                            t.setDaemon(true);
                            return t;
                        }
                    });
    private volatile boolean running = false;
    private Thread serverThread;

    // ==================== 构造 ====================

    public McbeWebSocket(int port, Callback callback) {
        this.port = port;
        this.callback = callback;
    }

    /** 设置实时日志回调。 */
    public void setLogCallback(LogCallback logCallback) {
        this.logCallback = logCallback;
    }

    /** 设置独立事件回调。 */
    public void setEventCallback(EventCallback eventCallback) {
        this.eventCallback = eventCallback;
    }

    /** 设置独立数据查询回调。 */
    public void setDataCallback(DataCallback dataCallback) {
        this.dataCallback = dataCallback;
    }

    /** 设置客户端连接状态回调（onConnect / onDisconnect）。 */
    public void setConnectionCallback(ConnectionCallback connectionCallback) {
        this.connectionCallback = connectionCallback;
    }

    // ==================== 生命周期 ====================

    /** 启动服务端（非阻塞）。 */
    public synchronized void start() {
        if (running) {
            logW("Already running, ignoring duplicate start() call");
            return;
        }
        running = true;
        commandQueue.clear();
        serverThread = new Thread(new ServerRunnable(), "McbeWS-Server");
        serverThread.setDaemon(true);
        serverThread.start();
        logI("Service start() called, listening thread launched");
    }

    /** 停止服务端并清理所有资源。 */
    public synchronized void stop() {
        if (!running) {
            logW("Service not running, ignoring stop() call");
            return;
        }
        running = false;
        closeQuietly(mcSocket);
        mcSocket = null;
        closeQuietly(serverSocket);
        serverSocket = null;
        commandQueue.clear();
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        logI("Service stopped, all resources released");
    }

    /**
     * 完全释放：stop() + 关闭发送/调度线程池与派发线程。
     * 实例不再复用时调用（端口切换 / 引擎拆除），避免线程泄漏。
     */
    public synchronized void release() {
        stop();
        try {
            sendExecutor.shutdownNow();
        } catch (Throwable ignored) {
        }
        poster.shutdown();
    }

    /** 是否已连上 MC。 */
    public boolean isConnected() {
        return mcSocket != null && !mcSocket.isClosed();
    }

    /** 服务是否已启动（无论是否连上 MC）。 */
    public boolean isRunning() {
        return running;
    }

    /** 获取当前监听端口。 */
    public int getPort() {
        return port;
    }

    /**
     * 设置命令格式版本（推荐使用游戏本身返回的 version 值）。
     * 例如 MC 1.21.2 的命令版本为 17039360（/execute 使用新语法）。
     */
    public void setCommandVersion(int version) {
        this.commandVersion = version;
        logI("Command format version set to: " + version);
    }

    /** 获取当前命令格式版本。 */
    public int getCommandVersion() {
        return commandVersion;
    }

    /** 获取当前已订阅的事件列表。 */
    public Set<String> getSubscribedEvents() {
        return new HashSet<String>(subscribedEvents);
    }

    // ==================== 命令 API ====================

    /** 发送任意 JSON 文本帧给 MC（异步，由发送线程串行执行以保证保序）。 */
    public void send(final String json) {
        if (!isConnected()) {
            logW("Cannot send: not connected to MC");
            return;
        }
        final byte[] body;
        try {
            byte[] payload = json.getBytes("UTF-8");
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            f.write(0x81);

            if (payload.length < 126) {
                f.write(payload.length);
            } else if (payload.length < 65536) {
                f.write(126);
                f.write((payload.length >> 8) & 0xFF);
                f.write(payload.length & 0xFF);
            } else {
                f.write(127);
                long len = payload.length;
                for (int i = 7; i >= 0; i--)
                    f.write((int) ((len >> (8 * i)) & 0xFF));
            }
            f.write(payload);
            body = f.toByteArray();
        } catch (Exception e) {
            logE("Frame build failed: " + e.getMessage());
            return;
        }
        final String brief = json.length() > 200
                ? json.substring(0, 200) + "...(" + json.length() + " chars)"
                : json;
        sendExecutor.execute(new Runnable() {
            @Override public void run() {
                if (!running) return;
                synchronized (sendLock) {
                    if (out == null) return;
                    try {
                        out.write(body);
                        out.flush();
                        logD("Sent frame (" + body.length + " bytes): " + brief);
                    } catch (IOException e) {
                        logE("Send failed: " + e.getMessage());
                        fireError(e);
                    }
                }
            }
        });
    }

    /**
     * 向 MC 发送一条指令（带队列管理）。
     * MC 限制最多 100 条并发命令，超出会报错。
     * 此方法会自动管理队列，等待前序命令完成。
     *
     * @return 本次请求的 requestId（可用于在 onCommandResponse 中对应结果），无法发送时返回 ""
     */
    public String command(String commandLine) {
        if (commandLine == null || commandLine.isEmpty()) {
            logW("command() called with empty string, ignoring");
            return "";
        }
        if (commandQueue.size() >= MAX_CONCURRENT_COMMANDS) {
            logW("Command queue full (" + commandQueue.size()
                    + "/" + MAX_CONCURRENT_COMMANDS + "), waiting...");
        }
        String requestId = uuid();
        logI("Queuing command: " + commandLine + " (id=" + requestId + ")");
        String json = buildCommandRequest(requestId, commandLine);
        commandQueue.offer(new CommandEntry(requestId, json));
        send(json);
        return requestId;
    }

    /** 构建 commandRequest 的完整 JSON。 */
    private String buildCommandRequest(String requestId, String commandLine) {
        return "{\"header\":{"
                + "\"requestId\":\"" + requestId + "\","
                + "\"messagePurpose\":\"commandRequest\","
                + "\"version\":1,"
                + "\"messageType\":\"commandRequest\""
                + "},\"body\":{"
                + "\"origin\":{\"type\":\"player\"},"
                + "\"commandLine\":" + jstr(commandLine) + ","
                + "\"version\":" + commandVersion
                + "}}";
    }

    /**
     * 发送指令并在收到对应 commandResponse 时触发回调。
     * 内置超时清理，避免无响应时内存泄漏。
     *
     * @return 本次请求的 requestId
     */
    private String sendCommandWithReply(String commandLine, PendingReply reply) {
        purgePendingCommands();
        String requestId = uuid();
        logI("Queuing tracked command: " + commandLine + " (id=" + requestId + ")");
        String json = buildCommandRequest(requestId, commandLine);
        pendingCommands.put(requestId, new PendingCommand(reply));
        commandQueue.offer(new CommandEntry(requestId, json));
        send(json);
        return requestId;
    }

    /** 清理超时未响应的待回调项。 */
    private void purgePendingCommands() {
        if (pendingCommands.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingCommand>> it = pendingCommands.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingCommand> e = it.next();
            if (now - e.getValue().issuedAt > PENDING_COMMAND_TIMEOUT_MS) {
                logW("Command response timeout, dropping pending: " + e.getKey());
                it.remove();
            }
        }
    }

    /** 批量执行多条指令。 */
    public void commands(String... commandLines) {
        for (String cmd : commandLines) {
            command(cmd);
        }
    }

    /**
     * 给指定玩家发送一条聊天消息（tell 命令）。
     * 自动处理引号转义，支持多行消息。
     *
     * @param target  目标选择器，如 "@a"、"@p" 或玩家名
     * @param message 消息内容（支持 § 颜色代码和 \n 换行）
     *
     * Examples:
     *   ws.sendTell("@a", "§a欢迎来到服务器");
     *   ws.sendTell("@p", "第一行\n第二行");
     */
    public void sendTell(String target, String message) {
        if (target == null || target.isEmpty()) {
            logW("sendTell() called with empty target, ignoring");
            return;
        }
        if (message == null) {
            logW("sendTell() called with null message, ignoring");
            return;
        }
        // 转义顺序与 mc-websocket-player/MusicPlayer.py 完全一致：
        // 先转义双引号，再转义反斜杠，最后用引号包裹并前置换行符
        String escaped = message.replace("\"", "\\\"").replace("\\", "\\\\");
        String cmd = "tell " + target + " \"\n" + escaped + "\"";
        logI("Tell to " + target + ": " + message.replace("\n", "\\n"));
        command(cmd);
    }

    /**
     * 给所有玩家发送消息（tell 到 @a）。
     */
    public void sendToAll(String message) {
        sendTell("@a", message);
    }

    /**
     * 向选择器指定的玩家播放音效。
     *
     * Examples:
     *   ws.playSound("note.harp", "@a", 100.0f, 1.0f);
     *   ws.playSound("note.pling", "@p", 50.0f, 1.2f);
     *
     * @param sound  音效名，如 "note.harp"、"note.pling"、"note.bell"
     * @param target 目标选择器
     * @param volume 音量（0.0-100.0）
     * @param pitch  音高
     */
    public void playSound(String sound, String target, float volume, float pitch) {
        if (sound == null || sound.isEmpty()) {
            logW("playSound() called with empty sound name, ignoring");
            return;
        }
        String cmd = "playsound " + sound + " " + target + " ~ ~ ~ "
                + volume + " " + pitch + " " + volume;
        logI("Playing sound: " + sound + " to " + target);
        command(cmd);
    }

    /**
     * 向指定选择器的玩家显示动作栏消息（titleraw actionbar）。
     * 常用于进度条、临时通知，不会刷屏聊天。
     *
     * Examples:
     *   ws.sendActionbar("@a", "§a播放中 §e50%");
     *   ws.sendActionbar("@p", "§c过热！");
     */
    public void sendActionbar(String target, String text) {
        if (target == null || target.isEmpty() || text == null) {
            logW("sendActionbar() called with empty target/text, ignoring");
            return;
        }
        String json = "{\"rawtext\":[{\"text\":" + jstr(text) + "}]}";
        command("titleraw " + target + " actionbar " + json);
    }

    /**
     * 向指定选择器的玩家发送标题（titleraw title / subtitle）。
     * subtitle 为 null 时只发 title。
     *
     * Examples:
     *   ws.sendTitle("@a", "§6§l挑战开始", "§7击败末影龙");
     *   ws.sendTitle("@a", "§a完成！", null);
     */
    public void sendTitle(String target, String title, String subtitle) {
        if (target == null || target.isEmpty() || title == null) {
            logW("sendTitle() called with empty target/title, ignoring");
            return;
        }
        String json = "{\"rawtext\":[{\"text\":" + jstr(title) + "}]}";
        command("titleraw " + target + " title " + json);
        if (subtitle != null) {
            String subJson = "{\"rawtext\":[{\"text\":" + jstr(subtitle) + "}]}";
            command("titleraw " + target + " subtitle " + subJson);
        }
    }

    /**
     * 以"玩家本人"身份在全局聊天框发言（say 命令）。
     * 显示效果与在游戏聊天框敲回车一模一样：&lt;玩家名&gt; 消息。
     *
     * 注意：基岩版把以 [ 开头的消息当作目标选择器，会报语法错误，
     * 请避免消息以 [ 开头。
     *
     * Examples:
     *   ws.sendChat("大家好，我是自动消息");
     *   ws.sendChat("§a绿色广播");
     */
    public void sendChat(String message) {
        if (message == null || message.isEmpty()) {
            logW("sendChat() called with empty message, ignoring");
            return;
        }
        logI("Chat: " + message.replace("\n", "\\n"));
        command("say " + message);
    }

    /**
     * 在当前命令执行者（@s）的身份下发言（execute as @s run say）。
     * 常用于命令方块/服务器命令上下文中模拟当前目标玩家发言。
     */
    public void sendChatAsSelf(String message) {
        sendChatAs("@s", message);
    }

    /**
     * 以指定玩家的身份在聊天框发言（用 execute 代替对方执行 say）。
     * 效果：消息显示为 &lt;玩家&gt; 消息，且发送者不再是 "[外部]"。
     *
     * 默认使用新版 execute 语法（MC 1.19.50+）：
     *   execute as &lt;玩家&gt; run say &lt;消息&gt;
     * 老版本（1.19.50 之前）请用三参重载，会生成旧语法：
     *   execute &lt;玩家&gt; ~ ~ ~ say &lt;消息&gt;
     *
     * Examples:
     *   ws.sendChatAs("AIR114514CH", "大家好");
     */
    public void sendChatAs(String player, String message) {
        sendChatAs(player, message, true);
    }

    /**
     * 以指定玩家的身份发言，可指定 execute 语法。
     *
     * @param player     玩家名或目标选择器
     * @param message    消息内容
     * @param newSyntax  true = execute as &lt;玩家&gt; run say …（1.19.50+）
     *                   false = execute &lt;玩家&gt; ~ ~ ~ say …（1.19.50 之前）
     */
    public void sendChatAs(String player, String message, boolean newSyntax) {
        if (player == null || player.isEmpty()) {
            logW("sendChatAs() called with empty player, ignoring");
            return;
        }
        if (message == null || message.isEmpty()) {
            logW("sendChatAs() called with empty message, ignoring");
            return;
        }
        String cmd;
        if (newSyntax) {
            cmd = "execute as " + player + " run say " + message;
        } else {
            cmd = "execute " + player + " ~ ~ ~ say " + message;
        }
        logI("ChatAs " + player + ": " + message.replace("\n", "\\n"));
        command(cmd);
    }

    /**
     * 发送动作消息（me 命令），显示为 * 玩家名 动作。
     *
     * Examples:
     *   ws.sendMe("正在睡觉");
     *   → 聊天框显示: * AIR114514CH 正在睡觉
     */
    public void sendMe(String action) {
        if (action == null || action.isEmpty()) {
            logW("sendMe() called with empty action, ignoring");
            return;
        }
        logI("Me: " + action);
        command("me " + action);
    }

    /**
     * 用富文本 JSON 向目标玩家发聊天消息（tellraw 命令）。
     * 可显示到聊天框，支持 § 颜色代码与 select/translate/score 组件。
     *
     * Examples:
     *   ws.sendTellraw("@a", "§6公告");
     *   ws.tellRaw("@a", "{\"rawtext\":[{\"selector\":\"@p\"},{\"text\":\" 上线了\"}]}");
     *
     * @param target 目标选择器
     * @param text   纯文本内容（自动包成 rawtext/text 组件）
     */
    public void sendTellraw(String target, String text) {
        if (target == null || target.isEmpty() || text == null) {
            logW("sendTellraw() called with empty target/text, ignoring");
            return;
        }
        String json = "{\"rawtext\":[{\"text\":" + jstr(text) + "}]}";
        command("tellraw " + target + " " + json);
    }

    /**
     * 用裸 raw JSON 消息调用 tellraw（高阶用法，自行构造 rawtext 组件）。
     */
    public void tellRaw(String target, String rawJson) {
        if (target == null || target.isEmpty() || rawJson == null || rawJson.isEmpty()) {
            logW("tellRaw() called with empty target/json, ignoring");
            return;
        }
        command("tellraw " + target + " " + rawJson);
    }

    /**
     * 停止播放音效（stopsound 命令）。
     * sound 为 null 时停止该玩家所有音效。
     *
     * Examples:
     *   ws.stopSound("@a", "note.pling");
     *   ws.stopSound("@a", null);
     */
    public void stopSound(String target, String sound) {
        if (target == null || target.isEmpty()) {
            logW("stopSound() called with empty target, ignoring");
            return;
        }
        String cmd = "stopsound " + target + (sound == null || sound.isEmpty() ? "" : " " + sound);
        logI("Stopping sound" + (sound == null ? "(all)" : ": " + sound) + " for " + target);
        command(cmd);
    }

    /**
     * 设置标题显示时长（title times）。
     *
     * Examples:
     *   ws.sendTitleTimes("@a", 10, 60, 20);
     */
    public void sendTitleTimes(String target, int fadeIn, int stay, int fadeOut) {
        if (target == null || target.isEmpty()) {
            logW("sendTitleTimes() called with empty target, ignoring");
            return;
        }
        command("title " + target + " times " + fadeIn + " " + stay + " " + fadeOut);
    }

    /**
     * 清除当前标题（title clear），下次发送的标题立即用 times 默认值。
     */
    public void clearTitle(String target) {
        if (target == null || target.isEmpty()) {
            logW("clearTitle() called with empty target, ignoring");
            return;
        }
        command("title " + target + " clear");
    }

    // ==================== 事件订阅 API ====================

    /** 订阅 MC 事件。断线重连后会自动重新订阅。 */
    public void subscribe(String eventName) {
        if (eventName == null || eventName.isEmpty()) {
            logW("subscribe() called with empty event name, ignoring");
            return;
        }
        subscribedEvents.add(eventName);
        logI("Subscribing to event: " + eventName);
        String json = "{\"header\":{"
                + "\"requestId\":\"" + uuid() + "\","
                + "\"messagePurpose\":\"subscribe\","
                + "\"version\":1,"
                + "\"messageType\":\"commandRequest\""
                + "},\"body\":{"
                + "\"eventName\":" + jstr(eventName)
                + "}}";
        send(json);
    }

    /** 取消订阅 MC 事件。 */
    public void unsubscribe(String eventName) {
        if (eventName == null || eventName.isEmpty()) {
            logW("unsubscribe() called with empty event name, ignoring");
            return;
        }
        subscribedEvents.remove(eventName);
        logI("Unsubscribing from event: " + eventName);
        String json = "{\"header\":{"
                + "\"requestId\":\"" + uuid() + "\","
                + "\"messagePurpose\":\"unsubscribe\","
                + "\"version\":1,"
                + "\"messageType\":\"commandRequest\""
                + "},\"body\":{"
                + "\"eventName\":" + jstr(eventName)
                + "}}";
        send(json);
    }

    /** 重新订阅所有之前订阅过的事件（内部使用）。 */
    private void resubscribeAll() {
        if (subscribedEvents.isEmpty()) {
            logD("No events to resubscribe");
            return;
        }
        logI("Resubscribing to " + subscribedEvents.size() + " events after reconnect");
        // Copy to avoid ConcurrentModificationException
        String[] events = subscribedEvents.toArray(new String[0]);
        for (String eventName : events) {
            subscribe(eventName);
        }
    }

    // ==================== 快捷订阅方法 ====================

    /** 订阅玩家聊天事件（快捷方式）。 */
    public void onPlayerMessage(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PlayerMessage");
    }

    /** 订阅方块放置事件。 */
    public void onBlockPlaced(EventCallback cb) {
        setEventCallback(cb);
        subscribe("BlockPlaced");
    }

    /** 订阅方块破坏事件。 */
    public void onBlockBroken(EventCallback cb) {
        setEventCallback(cb);
        subscribe("BlockBroken");
    }

    /** 订阅玩家移动事件。 */
    public void onPlayerTravelled(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PlayerTravelled");
    }

    /** 订阅玩家位置变化事件。 */
    public void onPlayerTransform(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PlayerTransform");
    }

    /** 订阅玩家传送事件。 */
    public void onPlayerTeleported(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PlayerTeleported");
    }

    /** 订阅玩家死亡事件。 */
    public void onPlayerDied(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PlayerDied");
    }

    /** 订阅玩家弹跳事件。 */
    public void onPlayerBounced(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PlayerBounced");
    }

    /** 订阅丢弃物品事件。 */
    public void onItemDropped(EventCallback cb) {
        setEventCallback(cb);
        subscribe("ItemDropped");
    }

    /** 订阅使用物品事件。 */
    public void onItemUsed(EventCallback cb) {
        setEventCallback(cb);
        subscribe("ItemUsed");
    }

    /** 订阅烧炼物品事件。 */
    public void onItemSmelted(EventCallback cb) {
        setEventCallback(cb);
        subscribe("ItemSmelted");
    }

    /** 订阅击杀生物事件。 */
    public void onMobKilled(EventCallback cb) {
        setEventCallback(cb);
        subscribe("MobKilled");
    }

    /** 订阅一天结束事件。 */
    public void onEndOfDay(EventCallback cb) {
        setEventCallback(cb);
        subscribe("EndOfDay");
    }

    /** 订阅玩家加入游戏事件。 */
    public void onPlayerJoin(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PlayerJoin");
    }

    /** 订阅玩家离开游戏事件。 */
    public void onPlayerLeave(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PlayerLeave");
    }

    /** 订阅获得物品事件（捡起掉落物、拿起合成物品）。 */
    public void onItemAcquired(EventCallback cb) {
        setEventCallback(cb);
        subscribe("ItemAcquired");
    }

    /** 订阅合成物品事件。 */
    public void onItemCrafted(EventCallback cb) {
        setEventCallback(cb);
        subscribe("ItemCrafted");
    }

    /** 订阅物品损坏/耗尽事件。 */
    public void onItemDestroyed(EventCallback cb) {
        setEventCallback(cb);
        subscribe("ItemDestroyed");
    }

    /** 订阅物品附魔事件。 */
    public void onItemEnchanted(EventCallback cb) {
        setEventCallback(cb);
        subscribe("ItemEnchanted");
    }

    /** 订阅从酿造台拿出药水事件。 */
    public void onPotionBrewed(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PotionBrewed");
    }

    /** 订阅创建传送门事件。 */
    public void onPortalBuilt(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PortalBuilt");
    }

    /** 订阅使用传送门事件。 */
    public void onPortalUsed(EventCallback cb) {
        setEventCallback(cb);
        subscribe("PortalUsed");
    }

    /** 订阅离开矿车事件。 */
    public void onVehicleExited(EventCallback cb) {
        setEventCallback(cb);
        subscribe("VehicleExited");
    }

    /** 订阅玩家更新皮肤事件。 */
    public void onUploadSkin(EventCallback cb) {
        setEventCallback(cb);
        subscribe("UploadSkin");
    }

    /** 订阅玩家获得成就事件。 */
    public void onAwardAchievement(EventCallback cb) {
        setEventCallback(cb);
        subscribe("AwardAchievement");
    }

    /** 订阅炼药锅交互事件。 */
    public void onCauldronUsed(EventCallback cb) {
        setEventCallback(cb);
        subscribe("CauldronUsed");
    }

    /** 订阅唱片机放入唱片事件。 */
    public void onJukeboxUsed(EventCallback cb) {
        setEventCallback(cb);
        subscribe("JukeboxUsed");
    }

    /** 订阅关闭工作台/背包界面事件。 */
    public void onCraftingSessionCompleted(EventCallback cb) {
        setEventCallback(cb);
        subscribe("CraftingSessionCompleted");
    }

    /** 订阅通过命令生成实体事件。 */
    public void onEntitySpawned(EventCallback cb) {
        setEventCallback(cb);
        subscribe("EntitySpawned");
    }

    // ==================== 数据查询 API（实验性） ====================

    /**
     * 查询指定坐标的方块数据。
     * 注意：主要在 Education Edition 可用，普通基岩版可能无响应。
     *
     * @param coordinates 坐标，如 "~ ~ ~" 或 "100 64 200"
     */
    public void queryBlock(String coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            logW("queryBlock() called with empty coordinates, ignoring");
            return;
        }
        logI("Querying block data at: " + coordinates);
        String requestId = uuid();
        String json = "{\"header\":{"
                + "\"requestId\":\"" + requestId + "\","
                + "\"messagePurpose\":\"data:block\","
                + "\"version\":1,"
                + "\"messageType\":\"commandRequest\""
                + "},\"body\":{"
                + "\"coordinates\":" + jstr(coordinates)
                + "}}";
        send(json);
    }

    /**
     * 查询当前手持物品数据。
     * 注意：主要在 Education Edition 可用。
     */
    public void queryItem() {
        logI("Querying current held item data");
        String requestId = uuid();
        String json = "{\"header\":{"
                + "\"requestId\":\"" + requestId + "\","
                + "\"messagePurpose\":\"data:item\","
                + "\"version\":1,"
                + "\"messageType\":\"commandRequest\""
                + "},\"body\":{"
                + "\"invokes\":[\"\"]"
                + "}}";
        send(json);
    }

    /**
     * 查询当前指向/最近实体数据。
     * 注意：主要在 Education Edition 可用。
     */
    public void queryMob() {
        logI("Querying current target mob data");
        String requestId = uuid();
        String json = "{\"header\":{"
                + "\"requestId\":\"" + requestId + "\","
                + "\"messagePurpose\":\"data:mob\","
                + "\"version\":1,"
                + "\"messageType\":\"commandRequest\""
                + "},\"body\":{"
                + "\"invokes\":[\"\"]"
                + "}}";
        send(json);
    }

    // ==================== 隐藏命令封装 ====================

    /**
     * 获取指定坐标上方最高的实体方块坐标。
     * 封装隐藏命令 ./gettopsolidblock
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @param z Z 坐标
     */
    public void getTopSolidBlock(int x, int y, int z) {
        logI("Querying top solid block at: " + x + " " + y + " " + z);
        command("./gettopsolidblock " + x + " " + y + " " + z);
    }

    /**
     * 获取实体的精确浮点坐标。
     * 封装隐藏命令 ./querytarget
     *
     * @param target 目标选择器，如 "@e[type=armor_stand]"
     */
    public void queryTarget(String target) {
        if (target == null || target.isEmpty()) {
            logW("queryTarget() called with empty target, ignoring");
            return;
        }
        logI("Querying target coordinates for: " + target);
        command("./querytarget " + target);
    }

    /**
     * 获取本地玩家名称。
     * 封装隐藏命令 ./getlocalplayername。
     * 注意：实测普通版 MC（1.21.x）会返回语法错误"意外的 ."，
     * 该命令仅基岩代码教育版（EDU）等特制环境支持。
     * 普通版想取玩家名请用 getOnlinePlayers() / testfor 命令。
     */
    public void getPlayerName() {
        logI("Querying local player name");
        command("./getlocalplayername");
    }

    /**
     * 获取客户端版本信息。
     * 封装隐藏命令 ./geteduclientinfo
     */
    public void getClientInfo() {
        logI("Querying client info");
        command("./geteduclientinfo");
    }

    /**
     * 关闭 WebSocket 连接。
     * 封装隐藏命令 ./closewebsocket
     */
    public void disconnect() {
        logI("Requesting WebSocket disconnect via ./closewebsocket");
        command("./closewebsocket");
    }

    // ==================== 玩家名单查询 ====================

    /**
     * 获取当前全部在线玩家名称。
     * 内部发送 "testfor @a"，解析响应的 body.victim 名单。
     * 需要游戏开启作弊，且基岩版支持对玩家执行 testfor。
     *
     * @return 本次请求的 requestId（用于在 PlayerListCallback 中对应结果），无法发送时返回 ""
     */
    public String getOnlinePlayers(PlayerListCallback cb) {
        return queryPlayersFor("testfor @a", cb);
    }

    /**
     * 获取手持指定物品的玩家名称（默认检查主手）。
     * 内部发送 "testfor @a[hasitem={item=X,location=slot.weapon.mainhand}]"。
     * hasitem 需要基岩版 1.16.100+。
     *
     * Examples:
     *   ws.getPlayersHolding("diamond_sword", cb);       // 主手持钻石剑
     *   ws.getPlayersHolding("minecraft:iron_pickaxe", cb);
     *
     * @param item 物品 ID，如 "diamond_sword"、"minecraft:apple"
     */
    public String getPlayersHolding(String item, PlayerListCallback cb) {
        return getPlayersWithItem(item, "slot.weapon.mainhand", 0, -1, cb);
    }

    /**
     * 以完整参数查询持有指定物品的玩家。
     * location 可选值（如 null 或 "" 则匹配任意位置）：
     *   slot.weapon.mainhand  主手
     *   slot.weapon.offhand   副手
     *   slot.hotbar           快捷栏
     *   slot.inventory        背包
     *   slot.armor.head/torso/legs/feet  装备栏
     * count 释义：
     *   count &gt; 0 时匹配数量区间，如 count=3 表示至少有 3 个；count=0 表示不限制数量
     * data 释义：
     *   data &gt;= 0 时匹配物品数据值；data=-1 表示不限制数据值
     *
     * @return 本次请求的 requestId
     */
    public String getPlayersWithItem(String item, String location,
                                     int count, int data, PlayerListCallback cb) {
        if (item == null || item.trim().isEmpty()) {
            logW("getPlayersWithItem() called with empty item, ignoring");
            return "";
        }
        StringBuilder sel = new StringBuilder("@a[hasitem={item=").append(item.trim());
        if (location != null && !location.isEmpty()) {
            sel.append(",location=").append(location);
        }
        if (count > 0) {
            sel.append(",count=").append(count);
        }
        if (data >= 0) {
            sel.append(",data=").append(data);
        }
        sel.append("}]");
        return queryPlayersFor("testfor " + sel.toString(), cb);
    }

    /**
     * 通用玩家名单查询：发 testfor 命令，收到响应后从 body.victim 提取名单。
     */
    private String queryPlayersFor(final String commandLine, final PlayerListCallback cb) {
        return sendCommandWithReply(commandLine, new PendingReply() {
            @Override public void onResponse(final String requestId, final String json,
                                             final int statusCode, final String statusMessage) {
                final List<String> players = extractStringArray(json, "victim");
                if (statusCode != 0) {
                    logW("Player query failed (" + commandLine + "): code=" + statusCode
                            + " msg=" + statusMessage);
                } else {
                    logI("Player query OK (" + commandLine + "): " + players.size() + " result(s)");
                }
                if (cb != null) {
                    post(new Runnable() {
                        @Override public void run() {
                            try { cb.onPlayers(requestId, players); }
                            catch (Exception e) { logE("PlayerListCallback failed: " + e.getMessage()); }
                        }
                    });
                }
            }
        });
    }

    // ==================== 工具方法 ====================

    /** 获取本机 WiFi IP 地址。 */
    public static String getWifiIp() {
        try {
            List<NetworkInterface> interfaces = Collections.list(
                    NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface iface : interfaces) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                if (!iface.getName().toLowerCase(Locale.US).contains("wlan")) continue;
                List<InetAddress> addrs = Collections.list(iface.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (addr.isLoopbackAddress()) continue;
                    String ip = addr.getHostAddress();
                    if (ip != null && ip.contains(".")) return ip;
                }
            }
        } catch (Exception ignore) {}
        return "0.0.0.0";
    }

    /** 获取 /connect 完整命令（直接复制粘贴到 MC 聊天框）。 */
    public String getConnectCommand() {
        return "/connect " + getWifiIp() + ":" + port;
    }

    // ==================== 日志 API ====================

    /** 获取所有日志（每行一条，带时间戳）。 */
    public String getLogs() {
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : logEntries) {
            sb.append(entry.toString()).append("\n");
        }
        return sb.toString();
    }

    /** 获取所有日志条目对象。 */
    public List<LogEntry> getLogEntries() {
        return new ArrayList<LogEntry>(logEntries);
    }

    /** 获取最近 N 条日志。 */
    public List<LogEntry> getRecentLogs(int count) {
        int size = logEntries.size();
        int from = Math.max(0, size - count);
        return new ArrayList<LogEntry>(logEntries.subList(from, size));
    }

    /** 清空所有日志。 */
    public void clearLogs() {
        logEntries.clear();
        logI("Log history cleared");
    }

    /** 自动白名单：把内存中已有的日志一次性写入 txt。成功返回 true。 */
    public boolean saveLogsToFile(File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            java.io.FileWriter out = new java.io.FileWriter(file, false);
            try {
                out.write(getLogs());
            } finally {
                out.close();
            }
            logI("Logs exported to " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            logE("Failed to export logs to " + file + ": " + e);
            return false;
        }
    }

    /** 开启自动追加：此后每条日志实时写入 txt 文件。重复调用同一路径自动忽略。 */
    public void enableFileLog(File file) {
        synchronized (logLock) {
            if (fileLog != null && fileLog.getAbsolutePath().equals(file.getAbsolutePath())) {
                return;
            }
            disableFileLog();
            fileLog = file;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
        }
        logI("File log enabled: " + file.getAbsolutePath());
    }

    /** 关闭自动追加写文件。 */
    public void disableFileLog() {
        synchronized (logLock) {
            fileLog = null;
        }
        logI("File log disabled");
    }

    // ==================== 内部日志系统 ====================

    private void logI(String msg) { addLog("I", TAG, msg); }
    private void logW(String msg) { addLog("W", TAG, msg); }
    private void logE(String msg) { addLog("E", TAG, msg); }
    private void logD(String msg) { addLog("D", TAG, msg); }

    private void addLog(String level, String tag, String message) {
        LogEntry entry = new LogEntry(System.currentTimeMillis(), level, tag, message);
        logEntries.add(entry);
        while (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.remove(0);
        }
        synchronized (logLock) {
            if (fileLog != null) {
                try {
                    java.io.FileWriter w = new java.io.FileWriter(fileLog, true);
                    try {
                        w.write(entry.toString());
                        w.write(System.lineSeparator());
                    } finally {
                        w.close();
                    }
                } catch (Exception ignore) {
                    // 写盘失败不影响主流程
                }
            }
        }
        if (logCallback != null) {
            final String fLevel = level;
            final String fTag = tag;
            final String fMsg = message;
            post(new Runnable() {
                @Override public void run() {
                    logCallback.onLog(fLevel, fTag, fMsg);
                }
            });
        }
    }

    // ==================== 后台 ServerRunnable ====================

    private class ServerRunnable implements Runnable {
        long delay = INITIAL_RECONNECT_DELAY_MS;

        @Override
        public void run() {
            logI("Server thread started, attempting to bind to port " + port);

            while (running) {
                try {
                    serverSocket = new ServerSocket(port, 0,
                            InetAddress.getByName("0.0.0.0"));
                    delay = INITIAL_RECONNECT_DELAY_MS;
                    logI("WebSocket server listening on 0.0.0.0:" + port);
                    logI("Use this command in Minecraft: " + getConnectCommand());

                    while (running) {
                        logD("Waiting for MC client to connect...");
                        Socket s = serverSocket.accept();

                        if (mcSocket != null && !mcSocket.isClosed()) {
                            logW("Another client tried to connect from "
                                    + s.getInetAddress().getHostAddress()
                                    + ", rejecting (only one connection allowed)");
                            s.close();
                            continue;
                        }

                        mcSocket = s;
                        out = new BufferedOutputStream(s.getOutputStream());
                        logI("Incoming connection from "
                                + s.getInetAddress().getHostAddress()
                                + ":" + s.getPort());

                        if (doHandshake(s)) {
                            logI("WebSocket handshake successful, MC client connected");
                            logI("Connection is now ready to send/receive commands");
                            commandQueue.clear();
                            final String remoteAddr = s.getInetAddress().getHostAddress();
                            final int remotePort = s.getPort();
                            fireConnect(remoteAddr, remotePort);
                            // Auto-resubscribe after reconnect
                            postDelayed(new Runnable() {
                                @Override public void run() {
                                    resubscribeAll();
                                }
                            }, 500);
                            new Reader(s).start();
                        } else {
                            logW("WebSocket handshake failed, closing connection");
                            s.close();
                            mcSocket = null;
                        }
                    }
                } catch (Exception e) {
                    if (!running) break;
                    logE("Server error: " + e.getClass().getSimpleName()
                            + " - " + e.getMessage());
                    fireError(e);
                } finally {
                    closeQuietly(serverSocket);
                    serverSocket = null;
                }

                if (!running) break;
                logI("Server will attempt to reconnect in " + delay + "ms...");
                try { Thread.sleep(delay); } catch (InterruptedException e) {
                    logI("Reconnect sleep interrupted, exiting");
                    break;
                }
                delay = Math.min(delay * 2, MAX_RECONNECT_DELAY_MS);
                logI("Retrying bind to port " + port + "...");
            }

            logI("Server thread exiting");
        }
    }

    // ==================== 握手 ====================

    private boolean doHandshake(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        java.io.BufferedReader r =
                new java.io.BufferedReader(new java.io.InputStreamReader(in));
        String key = null;
        String line;
        while ((line = r.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase(Locale.US).startsWith("sec-websocket-key:")) {
                key = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        if (key == null) {
            logE("Handshake failed: sec-websocket-key header not found");
            return false;
        }

        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8"));
        String accept = base64(md.digest());

        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 101 Switching Protocols\r\n");
        sb.append("Upgrade: websocket\r\n");
        sb.append("Connection: Upgrade\r\n");
        sb.append("Sec-WebSocket-Accept: ").append(accept).append("\r\n\r\n");
        out.write(sb.toString().getBytes("UTF-8"));
        out.flush();
        logD("Handshake response sent with Sec-WebSocket-Accept");
        return true;
    }

    // ==================== 读帧 ====================

    private class Reader extends Thread {
        private final Socket s;
        private final InputStream in;

        Reader(Socket s) throws IOException {
            super("McbeWS-Reader");
            setDaemon(true);
            this.s = s;
            this.in = s.getInputStream();
        }

        @Override
        public void run() {
            logI("Frame reader thread started");
            try {
                while (running) {
                    int b1 = in.read();
                    if (b1 < 0) break;
                    int b2 = in.read();
                    if (b2 < 0) break;

                    int opcode = b1 & 0x0F;
                    boolean maskBit = (b2 & 0x80) != 0;
                    long len = (b2 & 0x7F);

                    if (len == 126) {
                        len = ((long) in.read() << 8) | in.read();
                    } else if (len == 127) {
                        long l = 0;
                        for (int i = 0; i < 8; i++) l = (l << 8) | in.read();
                        len = l;
                    }

                    byte[] mask = new byte[4];
                    if (maskBit) readFull(mask);

                    if (len < 0 || len > 16 * 1024 * 1024) {
                        logE("Invalid frame length: " + len + ", closing reader");
                        break;
                    }
                    byte[] payload = new byte[(int) len];
                    readFull(payload, 0, (int) len);

                    if (maskBit) {
                        for (int i = 0; i < len; i++)
                            payload[i] ^= mask[i & 3];
                    }

                    switch (opcode) {
                        case 1:
                            String msg = new String(payload, 0, (int) len, "UTF-8");
                            logD("Received text frame (" + len + " bytes): "
                                    + (msg.length() > 200
                                    ? msg.substring(0, 200) + "..."
                                    : msg));
                            dispatchMessage(msg);
                            break;
                        case 8:
                            logI("Received CLOSE frame from MC, disconnecting");
                            return;
                        case 9:
                            logD("Received PING, sending PONG");
                            sendPong(payload, (int) len);
                            break;
                        default:
                            logD("Received frame with opcode " + opcode + ", ignoring");
                            break;
                    }
                }
            } catch (IOException e) {
                if (running) {
                    logE("Reader IO error: " + e.getMessage());
                    fireError(e);
                }
            } finally {
                closeQuietly(s);
                mcSocket = null;
                fireDisconnect();
                logI("Frame reader thread stopped, connection closed");
            }
        }

        private void readFull(byte[] buf) throws IOException {
            readFull(buf, 0, buf.length);
        }

        private void readFull(byte[] buf, int off, int length) throws IOException {
            int got = 0;
            while (got < length) {
                int n = in.read(buf, off + got, length - got);
                if (n <= 0) throw new IOException("Connection closed unexpectedly");
                got += n;
            }
        }
    }

    // ==================== Pong ====================

    private void sendPong(byte[] payload, int length) {
        if (!isConnected()) return;
        synchronized (sendLock) {
            try {
                ByteArrayOutputStream f = new ByteArrayOutputStream();
                f.write(0x8A);
                if (length < 126) {
                    f.write(length);
                } else {
                    f.write(126);
                    f.write((length >> 8) & 0xFF);
                    f.write(length & 0xFF);
                }
                f.write(payload, 0, length);
                out.write(f.toByteArray());
                out.flush();
            } catch (IOException e) {
                logE("Failed to send PONG: " + e.getMessage());
                fireError(e);
            }
        }
    }

    // ==================== 消息分发 ====================

    /**
     * 根据 messagePurpose 分发消息到对应回调。
     */
    private void dispatchMessage(final String json) {
        if (callback == null && eventCallback == null && dataCallback == null) return;

        // 用游戏端返回的 header.version 自动同步命令格式版本，
        // 否则新版 MC 的 /execute 等命令会因 body.version 过旧而出问题。
        syncCommandVersion(json);

        // Always fire onMessage with raw JSON
        if (callback != null) {
            post(new Runnable() {
                @Override public void run() { callback.onMessage(json); }
            });
        }

        // Parse messagePurpose
        String purpose = between(json, "\"messagePurpose\":\"", "\"");

        if (purpose == null) {
            logD("Received message with no messagePurpose, ignoring");
            return;
        }

        logD("Dispatching message with purpose: " + purpose);

        switch (purpose) {
            case "commandResponse":
                dispatchCommandResponse(json);
                break;
            case "error":
                dispatchError(json);
                break;
            case "event":
                dispatchEvent(json);
                break;
            case "data:block":
                dispatchBlockData(json);
                break;
            case "data:item":
                dispatchItemData(json);
                break;
            case "data:mob":
                dispatchMobData(json);
                break;
            default:
                logD("Unhandled messagePurpose: " + purpose);
                break;
        }
    }

    /**
     * 从游戏端返回的帧里提取 header.version（就是 MC 当前期望的命令格式版本），
     * 用于同步 body.version，避免 /execute 等新版命令因版本旧报错。
     */
    private void syncCommandVersion(String json) {
        try {
            String v = between(json, "\"version\":", ",");
            if (v == null) v = between(json, "\"version\":", "}");
            if (v == null) return;
            final int version = Integer.parseInt(v.trim());
            if (version > 0 && version != commandVersion) {
                commandVersion = version;
                logI("Command format version auto-synced from game: " + version);
            }
        } catch (Exception ignore) {
        }
    }

    private void dispatchCommandResponse(final String json) {
        final String requestId = between(json, "\"requestId\":\"", "\"");
        int statusCode = 0;
        String msg = between(json, "\"statusMessage\":\"", "\"");
        String code = between(json, "\"statusCode\":", ",");
        if (code == null) code = between(json, "\"statusCode\":", "}");
        try { if (code != null) statusCode = Integer.parseInt(code.trim()); }
        catch (Exception ignore) {}
        final String fMsg = msg != null ? msg : "";
        final int fCode = statusCode;

        // Remove from queue
        if (requestId != null) {
            Iterator<CommandEntry> it = commandQueue.iterator();
            while (it.hasNext()) {
                if (requestId.equals(it.next().requestId)) {
                    it.remove();
                    break;
                }
            }

            // Fire tracked callbacks (e.g. player-list queries)
            PendingCommand pend = pendingCommands.remove(requestId);
            if (pend != null) {
                final String rid = requestId;
                final PendingReply reply = pend.reply;
                post(new Runnable() {
                    @Override public void run() {
                        try { reply.onResponse(rid, json, fCode, fMsg); }
                        catch (Exception e) { logE("Pending reply failed: " + e.getMessage()); }
                    }
                });
            }
        }

        if (statusCode != 0) {
            logW("Command response: statusCode=" + statusCode + " msg=" + fMsg);
        } else {
            logD("Command response: OK (statusCode=0)");
        }

        if (callback != null) {
            post(new Runnable() {
                @Override public void run() {
                    callback.onCommandResponse(requestId, fCode, fMsg);
                    callback.onRawCommandResponse(requestId, fCode, fMsg, json);
                }
            });
        }
    }

    private void dispatchError(final String json) {
        final String statusCode = between(json, "\"statusCode\":", ",");
        final String statusMsg = between(json, "\"statusMessage\":\"", "\"");
        logE("Error received: code=" + statusCode + " msg=" + statusMsg);

        if (callback != null) {
            post(new Runnable() {
                @Override public void run() {
                    callback.onError(new Exception("MCBE Error: code=" + statusCode + " msg=" + statusMsg));
                }
            });
        }
    }

    private void dispatchEvent(final String json) {
        final String eventName = between(json, "\"eventName\":\"", "\"");
        // 事件消息的字段有两种位置：有的版本放在 body.properties，
        // 实测基岩版事件字段直接放 body 根下（PlayerMessage/BlockPlaced/PlayerTravelled 等），
        // 所以 properties 缺失时回退到整个 body，否则 onEvent 只能收到 "{}"。
        String payload = extractProperties(json);
        if (payload == null) payload = extractBody(json);
        final String properties = payload;

        if (eventName == null) {
            logD("Received event with no eventName, ignoring");
            return;
        }

        logI("Event received: " + eventName);

        // Callback interface
        if (callback != null) {
            post(new Runnable() {
                @Override public void run() {
                    callback.onEvent(eventName, properties != null ? properties : "{}");
                }
            });
        }

        // EventCallback interface
        if (eventCallback != null) {
            post(new Runnable() {
                @Override public void run() {
                    eventCallback.onEvent(eventName, properties != null ? properties : "{}");
                }
            });
        }
    }

    private void dispatchBlockData(final String json) {
        final String requestId = between(json, "\"requestId\":\"", "\"");
        logI("Block data received for request: " + requestId);

        if (callback != null) {
            post(new Runnable() {
                @Override public void run() { callback.onBlockData(requestId, json); }
            });
        }
        if (dataCallback != null) {
            post(new Runnable() {
                @Override public void run() { dataCallback.onBlockData(requestId, json); }
            });
        }
    }

    private void dispatchItemData(final String json) {
        final String requestId = between(json, "\"requestId\":\"", "\"");
        logI("Item data received for request: " + requestId);

        if (callback != null) {
            post(new Runnable() {
                @Override public void run() { callback.onItemData(requestId, json); }
            });
        }
        if (dataCallback != null) {
            post(new Runnable() {
                @Override public void run() { dataCallback.onItemData(requestId, json); }
            });
        }
    }

    private void dispatchMobData(final String json) {
        final String requestId = between(json, "\"requestId\":\"", "\"");
        logI("Mob data received for request: " + requestId);

        if (callback != null) {
            post(new Runnable() {
                @Override public void run() { callback.onMobData(requestId, json); }
            });
        }
        if (dataCallback != null) {
            post(new Runnable() {
                @Override public void run() { dataCallback.onMobData(requestId, json); }
            });
        }
    }

    /**
     * Extract the properties object from event JSON.
     * Handles nested JSON properly by counting braces.
     */
    private String extractProperties(String json) {
        int idx = json.indexOf("\"properties\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int start = json.indexOf('{', colon);
        if (start < 0) return null;

        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 提取 JSON 中 body 键对应的完整对象（含嵌套）。实测基岩版事件消息字段
     * 直接放在 body 根下（无 properties），用这个把整个 body 取出来传给回调。
     * 遍历时跳过字符串字面量，避免消息内容里的 { } 干扰括号配对。
     */
    private String extractBody(String json) {
        int idx = json.indexOf("\"body\":");
        if (idx < 0) return null;
        int start = json.indexOf('{', idx);
        if (start < 0) return null;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Extract a JSON string array under the given key, e.g.:
     *   {"victim":["Alice","Bob"]}  ->  ["Alice","Bob"]
     * Handles escaped quotes (\" ) and whitespace. Returns empty list if absent.
     */
    private static List<String> extractStringArray(String json, String key) {
        List<String> out = new ArrayList<String>();
        if (json == null || key == null) return out;
        String keyPat = "\"" + key + "\"";
        int idx = json.indexOf(keyPat);
        if (idx < 0) return out;
        int i = idx + keyPat.length();
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != ':') return out;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '[') return out;
        i++;
        StringBuilder cur = new StringBuilder();
        boolean inStr = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    cur.append(c);
                    if (i + 1 < json.length()) {
                        cur.append(json.charAt(i + 1));
                        i++;
                    }
                } else if (c == '"') {
                    out.add(cur.toString());
                    cur.setLength(0);
                    inStr = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == ']') {
                break;
            }
            i++;
        }
        return out;
    }

    // ==================== 回调派发（主线程） ====================

    private void fireConnect(final String remoteAddress, final int remotePort) {
        if (callback != null) {
            post(new Runnable() {
                @Override public void run() { callback.onConnect(); }
            });
        }
        if (connectionCallback != null) {
            post(new Runnable() {
                @Override public void run() { connectionCallback.onConnect(remoteAddress, remotePort); }
            });
        }
    }

    private void fireDisconnect() {
        if (callback != null) {
            post(new Runnable() {
                @Override public void run() { callback.onDisconnect(); }
            });
        }
        if (connectionCallback != null) {
            post(new Runnable() {
                @Override public void run() { connectionCallback.onDisconnect(); }
            });
        }
    }

    private void fireError(final Exception e) {
        if (callback == null) return;
        post(new Runnable() {
            @Override public void run() { callback.onError(e); }
        });
    }

    private void post(Runnable r) {
        poster.post(r);
    }

    private void postDelayed(Runnable r, long delayMs) {
        poster.postDelayed(r, delayMs);
    }

    private void closeQuietly(java.io.Closeable c) {
        try { if (c != null) c.close(); } catch (Exception ignore) {}
    }

    // ==================== 工具 ====================

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static String jstr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:   sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }

    private static String base64(byte[] d) {
        final char[] T = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
                .toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i += 3) {
            int v = (d[i] & 0xFF) << 16
                    | (i + 1 < d.length ? (d[i + 1] & 0xFF) << 8 : 0)
                    | (i + 2 < d.length ? (d[i + 2] & 0xFF) : 0);
            sb.append(T[(v >> 18) & 63]).append(T[(v >> 12) & 63]);
            sb.append(i + 1 < d.length ? T[(v >> 6) & 63] : '=');
            sb.append(i + 2 < d.length ? T[v & 63] : '=');
        }
        return sb.toString();
    }

    private static String between(String s, String a, String b) {
        if (s == null) return null;
        int i = s.indexOf(a);
        if (i < 0) return null;
        i += a.length();
        int j = s.indexOf(b, i);
        if (j < 0) return null;
        return s.substring(i, j);
    }
}