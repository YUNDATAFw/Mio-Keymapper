package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.adapter.ControlActionAdapter;
import io.takamiya.mio.animator.SlideLeftItemAnimator;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.dsl.DslEventDispatcher;
import io.takamiya.mio.databinding.FloatingWindowControlPanelBinding;
import io.takamiya.mio.config.ActionItem;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.configManage.ConfigPackageManager;

public class FloatingControlPanel {

    public interface Listener {
        void onActionsLoaded(List<ControlActionItem> items);
        void onActionVisibilityChanged(ControlActionItem item, boolean visible);
        void onGlobalKeyHoldDuration(long duration);
    }

    private static final String TAG = "FloatingControlPanel";
    private static final String DEFAULT_GROUP = "默认分类";

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowControlPanelBinding binding;

    private final ControlActionAdapter adapter;
    private final Map<String, Boolean> shortcutState;
    private DslEventDispatcher dslEventDispatcher;

    private Listener listener;
    private boolean closing;
    private int loadGeneration;
    private final List<ControlActionItem> allItems = new ArrayList<>();
    private int selectedTabPosition = 0;
    private boolean buildingTabs = false;

    private final ConfigPackageManager manager;

    public FloatingControlPanel(Context context, Map<String, Boolean> shortcutState) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowControlPanelBinding.inflate(LayoutInflater.from(context));
        this.shortcutState = shortcutState;
        this.manager = new ConfigPackageManager(context);
        this.adapter = new ControlActionAdapter(new ArrayList<>());
        this.adapter.setOnActionStateChangeListener(this::onActionStateChanged);

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        binding.rvActions.setLayoutManager(new LinearLayoutManager(context));
        binding.rvActions.setAdapter(adapter);
        binding.rvActions.setItemAnimator(new SlideLeftItemAnimator());

        binding.controlPanelRoot.setOnClickListener(v -> close());
        binding.controlBtnClose.setOnClickListener(v -> close());

        applySystemBarInsets(binding.controlPanelCard);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setDslEventDispatcher(DslEventDispatcher dispatcher) {
        this.dslEventDispatcher = dispatcher;
    }

    public void attach() {
        if (isAttached()) return;
        closing = false;
        loadActions();
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "control panel attached");
            binding.getRoot().post(this::animateOpen);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "control panel detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
        resetAnimState();
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    public void close() {
        if (closing) return;
        closing = true;
        animateClose();
    }

    private void loadActions() {
        List<ControlActionItem> items = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();

        // 传统配置（旧系统 json/lhrb）
        long globalKeyHoldDuration = 0;
        if (manager != null) {
            List<Plugin> plugins = manager.list();
            AppLogger.d(TAG, "loadActions: config plugins=" + plugins.size());
            for (Plugin plugin : plugins) {
                if (plugin == null || plugin.getConfig() == null || !plugin.isEnabled()) continue;
                Long pluginHoldDuration = plugin.getConfig().getKeyHoldDuration();
                if (pluginHoldDuration != null && pluginHoldDuration > globalKeyHoldDuration) {
                    globalKeyHoldDuration = pluginHoldDuration;
                }
                List<ActionItem> actionList = plugin.getActionList();
                if (actionList == null || actionList.isEmpty()) continue;
                for (int i = 0; i < actionList.size(); i++) {
                    ActionItem action = actionList.get(i);
                    String actionId = action.getId();
                    if (actionId == null || actionId.isEmpty()) {
                        actionId = "index_" + i;
                    }
                    String key = keyOf(plugin.getConfig().getPackageName(), actionId);
                    if (!seenKeys.add(key)) continue;
                    ControlActionItem item = new ControlActionItem(
                            plugin.getConfig().getPackageName(),
                            actionId,
                            action.getTitle(),
                            action.getDescription(),
                            shortcutState != null && Boolean.TRUE.equals(shortcutState.get(key)),
                            "配置",
                            plugin.getConfig().getName());
                    item.setTriggerModel(action.getTriggerModel());
                    item.setGroup(action.getGroup());
                    item.setHoldThresholdMs(action.getHoldThresholdMs());
                    item.setReleaseDelayMs(action.getReleaseDelayMs());
                    item.setActionItem(action);
                    items.add(item);
                }
            }
        }

        // 加载 DSL 注册的快捷键
        if (dslEventDispatcher != null) {
            List<ControlActionItem> dslActions = dslEventDispatcher.getAllDslShortcutActions();
            if (dslActions != null) {
                for (ControlActionItem item : dslActions) {
                    String key = keyOf(item.getPackageName(), item.getActionId());
                    if (!seenKeys.add(key)) continue;
                    Boolean existing = shortcutState != null ? shortcutState.get(key) : null;
                    boolean checked = existing != null && existing;
                    item.setEnabled(checked);
                    items.add(item);
                }
            }
        }

        AppLogger.d(TAG, "loadActions: " + items.size() + " action(s), hold=" + globalKeyHoldDuration);

        adapter.clear();
        allItems.clear();
        allItems.addAll(items);
        final int generation = ++loadGeneration;
        if (listener != null) {
            listener.onGlobalKeyHoldDuration(globalKeyHoldDuration);
            listener.onActionsLoaded(items);
        }
        AppLogger.i(TAG, "loadActions: " + items.size() + " action(s) total");

        setupTabs();

        List<ControlActionItem> visible = filterForTab(allItems, selectedTabPosition);
        final int staggerDelay = 100;
        for (int i = 0; i < visible.size(); i++) {
            final ControlActionItem item = visible.get(i);
            final int index = i;
            binding.rvActions.postDelayed(() -> {
                if (generation == loadGeneration) {
                    adapter.addItem(item);
                }
            }, index * staggerDelay);
        }
    }

    private void setupTabs() {
        buildingTabs = true;
        binding.tabGroups.removeOnTabSelectedListener(tabSelectedListener);
        binding.tabGroups.removeAllTabs();

        Set<String> groups = new LinkedHashSet<>();
        boolean hasDefault = false;
        for (ControlActionItem item : allItems) {
            String group = normalizeGroup(item.getGroup());
            if (group == null) {
                hasDefault = true;
            } else {
                groups.add(group);
            }
        }

        if (hasDefault || groups.isEmpty()) {
            binding.tabGroups.addTab(binding.tabGroups.newTab().setText(DEFAULT_GROUP));
        }
        for (String group : groups) {
            binding.tabGroups.addTab(binding.tabGroups.newTab().setText(group));
        }

        int tabCount = binding.tabGroups.getTabCount();
        boolean showTabs = tabCount >= 2;
        binding.tabGroups.setVisibility(showTabs ? View.VISIBLE : View.GONE);
        AppLogger.i(TAG, "setupTabs: groups=" + groups + " hasDefault=" + hasDefault
                + " tabCount=" + tabCount + " showTabs=" + showTabs);

        selectedTabPosition = 0;
        buildingTabs = false;
        binding.tabGroups.addOnTabSelectedListener(tabSelectedListener);
    }

    private final TabLayout.OnTabSelectedListener tabSelectedListener = new TabLayout.OnTabSelectedListener() {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            if (buildingTabs) return;
            selectedTabPosition = tab.getPosition();
            loadGeneration++;
            adapter.updateData(filterForTab(allItems, selectedTabPosition));
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {
        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
        }
    };

    private List<ControlActionItem> filterForTab(List<ControlActionItem> source, int tabPosition) {
        if (tabPosition < 0 || tabPosition >= binding.tabGroups.getTabCount()) {
            return new ArrayList<>(source);
        }
        String tabText = binding.tabGroups.getTabAt(tabPosition).getText().toString();
        List<ControlActionItem> filtered = new ArrayList<>();
        for (ControlActionItem item : source) {
            String group = normalizeGroup(item.getGroup());
            boolean matches;
            if (tabText.equals(DEFAULT_GROUP)) {
                matches = (group == null);
            } else {
                matches = tabText.equals(group);
            }
            if (matches) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private String normalizeGroup(String group) {
        if (group == null) return null;
        String trimmed = group.trim();
        if (trimmed.isEmpty() || trimmed.equals(DEFAULT_GROUP)) return null;
        return trimmed;
    }

    private void onActionStateChanged(ControlActionItem item, boolean isChecked) {
        String key = keyOf(item.getPackageName(), item.getActionId());
        if (shortcutState != null) {
            shortcutState.put(key, isChecked);
        }
        item.setEnabled(isChecked);
        if (listener != null) {
            listener.onActionVisibilityChanged(item, isChecked);
        }
        AppLogger.d(TAG, "action visibility: " + key + "=" + isChecked);
    }

    private String keyOf(String packageName, String actionId) {
        return packageName + "." + actionId;
    }

    private void animateOpen() {
        FloatingAnimator.fadeIn(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
        FloatingAnimator.slideInFromStart(
                binding.controlPanelCard, slideDistance(), FloatingAnimator.NORMAL_MS, null);
    }

    private void animateClose() {
        FloatingAnimator.slideOutToStart(
                binding.controlPanelCard, slideDistance(), FloatingAnimator.NORMAL_MS, this::detach);
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
    }

    private float slideDistance() {
        int width = binding.controlPanelCard.getWidth();
        if (width <= 0) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        return -width;
    }

    private void resetAnimState() {
        closing = false;
        binding.getRoot().setAlpha(1f);
        binding.controlPanelCard.setAlpha(1f);
        binding.controlPanelCard.setTranslationX(0f);
    }

    private void applySystemBarInsets(View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
