package io.takamiya.mio.ws.feature;

import java.util.List;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.ws.WsEngineHolder;

/**
 * 宿主内置 WS 功能基类。
 *
 * <p>直接继承它编写 Java 功能，重写关心的 WS 事件监听方法即可，
 * 无需任何 DSL 插件。事件方法参数为事件原始 JSON 文本（字段平铺在根下，
 * 可用 {@code org.json.JSONObject} 解析）。全部方法默认为空实现。</p>
 *
 * <p>开关状态不持久化，功能中心（{@link WsFeatureCenter}）每次启动
 * 都以关闭态注册。</p>
 */
public abstract class WsFeature {

    private final String key;
    private final String name;
    private final String desc;

    private volatile boolean enabled;

    private static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * @param key  功能标识（全局唯一，与插件注册的 feature key 区分前缀）。
     * @param name 功能显示名（功能列表展示）。
     * @param desc 功能说明。
     */
    protected WsFeature(String key, String name, String desc) {
        this.key = key;
        this.name = name;
        this.desc = desc;
    }

    public final String key() {
        return key;
    }

    public final String name() {
        return name;
    }

    public final String desc() {
        return desc;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    /** 由功能中心调用：变更开关状态并回调 onEnable/onDisable。 */
    final void switchEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        if (on) {
            onEnable();
        } else {
            onDisable();
        }
    }

    // ==================== 生命周期（可重写） ====================

    /** 注册到功能中心后调用一次。 */
    public void onInit() {
    }

    /** 开关打开时调用。 */
    public void onEnable() {
    }

    /** 开关关闭时调用。 */
    public void onDisable() {
    }

    /** WS 服务启动后调用。 */
    public void onServerStarted() {
    }

    /** WS 服务停止后调用。 */
    public void onServerStopped() {
    }

    /** 与游戏建立连接后调用。 */
    public void onConnected() {
    }

    /** 与游戏断开连接后调用。 */
    public void onDisconnected() {
    }

    // ==================== WS 游戏事件（可重写，参数为事件 JSON 文本） ====================

    public void onPlayerMessage(String json) {
    }

    public void onPlayerJoin(String json) {
    }

    public void onPlayerLeave(String json) {
    }

    public void onPlayerDied(String json) {
    }

    public void onBlockPlaced(String json) {
    }

    public void onBlockBroken(String json) {
    }

    public void onPlayerTravelled(String json) {
    }

    public void onPlayerTransform(String json) {
    }

    public void onPlayerTeleported(String json) {
    }

    public void onPlayerBounced(String json) {
    }

    public void onItemDropped(String json) {
    }

    public void onItemUsed(String json) {
    }

    public void onItemSmelted(String json) {
    }

    public void onItemAcquired(String json) {
    }

    public void onItemCrafted(String json) {
    }

    public void onItemDestroyed(String json) {
    }

    public void onItemEnchanted(String json) {
    }

    public void onPotionBrewed(String json) {
    }

    public void onMobKilled(String json) {
    }

    public void onEndOfDay(String json) {
    }

    public void onPortalBuilt(String json) {
    }

    public void onPortalUsed(String json) {
    }

    public void onVehicleExited(String json) {
    }

    public void onUploadSkin(String json) {
    }

    public void onAwardAchievement(String json) {
    }

    public void onCauldronUsed(String json) {
    }

    public void onJukeboxUsed(String json) {
    }

    public void onCraftingSessionCompleted(String json) {
    }

    public void onEntitySpawned(String json) {
    }

    // ==================== 便捷助手（未连接时自动忽略） ====================

    protected boolean isRunning() {
        return holder().isRunning();
    }

    protected boolean isConnected() {
        return holder().isConnected();
    }

    protected List<String> players() {
        return holder().getPlayers();
    }

    /** 向游戏公开聊天框发言。 */
    protected void sendChat(String message) {
        holder().sendChat(message);
    }

    /** 后台执行游戏指令，成功返回 requestId。 */
    protected String sendCommand(String commandLine) {
        return holder().sendCommand(commandLine);
    }

    /** /tell 私聊。 */
    protected void sendTell(String target, String message) {
        holder().sendTell(target, message);
    }

    /** 向全体广播一条聊天消息（/tell @a）。 */
    protected void sendToAll(String message) {
        holder().sendToAll(message);
    }

    /** 发送标题（target 如玩家名或 @a，副标题可传空串）。 */
    protected void sendTitle(String target, String title, String subtitle) {
        holder().sendTitle(target, title, subtitle);
    }

    protected void sendActionbar(String target, String text) {
        holder().sendActionbar(target, text);
    }

    protected void sendTellraw(String target, String text) {
        holder().sendTellraw(target, text);
    }

    protected void sendChatAs(String player, String message) {
        holder().sendChatAs(player, message);
    }

    protected void sendMe(String action) {
        holder().sendMe(action);
    }

    protected void playSound(String sound, String target, float volume, float pitch) {
        holder().playSound(sound, target, volume, pitch);
    }

    protected void stopSound(String target, String sound) {
        holder().stopSound(target, sound);
    }

    /** 记录一条到 WS 面板日志（tag 自动带功能 key）。 */
    protected void log(String message) {
        AppLogger.i(tag(), message);
    }

    /** 悬浮窗 Toast（走 FloatingWindowService，实例为空时自动忽略）。 */
    protected void showToast(String text) {
        MAIN.post(() -> {
            FloatingWindowService svc = FloatingWindowService.getInstance();
            if (svc != null) {
                svc.showToast(text);
            }
        });
    }

    protected String tag() {
        return "WsFeature-" + key();
    }

    private static WsEngineHolder holder() {
        return WsEngineHolder.get();
    }
}