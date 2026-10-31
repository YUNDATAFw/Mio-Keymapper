package io.takamiya.mio.ws.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.ws.WsEngineHolder;

/**
 * 宿主内置 WS 功能中心：注册表 + 事件分发器。
 *
 * <p>挂到 {@link WsEngineHolder} 同时接收 WS 事件与生命周期回调；
 * 首次注册功能时自动挂载，与 DSL 插件的 WS 事件广播并存互不影响。</p>
 */
public final class WsFeatureCenter implements WsEngineHolder.WsEventListener,
        WsEngineHolder.WsLifecycleListener {

    private static final String TAG = "WsFeatureCenter";

    private static final WsFeatureCenter INSTANCE = new WsFeatureCenter();

    public static WsFeatureCenter get() {
        return INSTANCE;
    }

    private final Map<String, WsFeature> features = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<WsFeature, String>> eventHandlers =
            new ConcurrentHashMap<>();
    private volatile boolean attached;

    private WsFeatureCenter() {
        registerEventHandlers();
    }

    private void registerEventHandlers() {
        eventHandlers.put("PlayerMessage", WsFeature::onPlayerMessage);
        eventHandlers.put("PlayerJoin", WsFeature::onPlayerJoin);
        eventHandlers.put("PlayerLeave", WsFeature::onPlayerLeave);
        eventHandlers.put("PlayerDied", WsFeature::onPlayerDied);
        eventHandlers.put("BlockPlaced", WsFeature::onBlockPlaced);
        eventHandlers.put("BlockBroken", WsFeature::onBlockBroken);
        eventHandlers.put("PlayerTravelled", WsFeature::onPlayerTravelled);
        eventHandlers.put("PlayerTransform", WsFeature::onPlayerTransform);
        eventHandlers.put("PlayerTeleported", WsFeature::onPlayerTeleported);
        eventHandlers.put("PlayerBounced", WsFeature::onPlayerBounced);
        eventHandlers.put("ItemDropped", WsFeature::onItemDropped);
        eventHandlers.put("ItemUsed", WsFeature::onItemUsed);
        eventHandlers.put("ItemSmelted", WsFeature::onItemSmelted);
        eventHandlers.put("ItemAcquired", WsFeature::onItemAcquired);
        eventHandlers.put("ItemCrafted", WsFeature::onItemCrafted);
        eventHandlers.put("ItemDestroyed", WsFeature::onItemDestroyed);
        eventHandlers.put("ItemEnchanted", WsFeature::onItemEnchanted);
        eventHandlers.put("PotionBrewed", WsFeature::onPotionBrewed);
        eventHandlers.put("MobKilled", WsFeature::onMobKilled);
        eventHandlers.put("EndOfDay", WsFeature::onEndOfDay);
        eventHandlers.put("PortalBuilt", WsFeature::onPortalBuilt);
        eventHandlers.put("PortalUsed", WsFeature::onPortalUsed);
        eventHandlers.put("VehicleExited", WsFeature::onVehicleExited);
        eventHandlers.put("UploadSkin", WsFeature::onUploadSkin);
        eventHandlers.put("AwardAchievement", WsFeature::onAwardAchievement);
        eventHandlers.put("CauldronUsed", WsFeature::onCauldronUsed);
        eventHandlers.put("JukeboxUsed", WsFeature::onJukeboxUsed);
        eventHandlers.put("CraftingSessionCompleted", WsFeature::onCraftingSessionCompleted);
        eventHandlers.put("EntitySpawned", WsFeature::onEntitySpawned);
    }

    private synchronized void attachIfNeeded() {
        if (attached) return;
        WsEngineHolder h = WsEngineHolder.get();
        h.addWsEventListener(this);
        h.addWsLifecycleListener(this);
        attached = true;
    }

    /** 注册一个内置功能（同 key 后注册覆盖）。 */
    public void register(WsFeature feature) {
        if (feature == null) return;
        attachIfNeeded();
        features.put(feature.key(), feature);
        try {
            feature.onInit();
        } catch (Exception e) {
            AppLogger.e(TAG, "feature onInit failed: " + feature.key() + " - " + e.getMessage());
        }
    }

    /** 集中注册全部内置功能：新功能在这里加一行即可，元数据作为参数传入。 */
    public synchronized void registerBuiltins() {
        register(new TestCommand(
                "ws_command",
                "WSCommand",
                "测试命令"));
    }

    public void unregister(String key) {
        if (key != null) features.remove(key);
    }

    /** 全部内置功能（供功能列表展示）。 */
    public List<WsFeature> all() {
        return new ArrayList<>(features.values());
    }

    public boolean contains(String key) {
        return key != null && features.containsKey(key);
    }

    /** 切换内置功能开关（Java 侧，与插件 routeFeatureToggle 互斥路由）。 */
    public boolean toggle(String key, boolean on) {
        WsFeature f = features.get(key);
        if (f == null) return false;
        try {
            f.switchEnabled(on);
        } catch (Exception e) {
            AppLogger.e(TAG, "feature toggle failed: " + key + " - " + e.getMessage());
        }
        return true;
    }

    // ==================== 事件分发 ====================

    @Override
    public void onWsEvent(String eventName, String rawJson) {
        BiConsumer<WsFeature, String> handler = eventHandlers.get(eventName);
        if (handler == null) return;
        for (WsFeature f : features.values()) {
            if (!f.isEnabled()) continue;
            try {
                handler.accept(f, rawJson);
            } catch (Exception e) {
                AppLogger.e(TAG, "feature " + f.key() + " handle " + eventName
                        + " failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void onServerStarted() {
        dispatchAll(WsFeature::onServerStarted);
    }

    @Override
    public void onServerStopped() {
        dispatchAll(WsFeature::onServerStopped);
    }

    @Override
    public void onConnected() {
        dispatchAll(WsFeature::onConnected);
    }

    @Override
    public void onDisconnected() {
        dispatchAll(WsFeature::onDisconnected);
    }

    private void dispatchAll(Consumer<WsFeature> dispatch) {
        for (WsFeature f : features.values()) {
            if (!f.isEnabled()) continue;
            try {
                dispatch.accept(f);
            } catch (Exception e) {
                AppLogger.e(TAG, "feature " + f.key() + " lifecycle failed: " + e.getMessage());
            }
        }
    }
}
