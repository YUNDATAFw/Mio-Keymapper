package io.takamiya.mio.floating;

import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.config.ShortcutEventType;
import io.takamiya.mio.config.TriggerModel;

public class FloatingShortcutBar {

    public interface OnKeyActionListener {
        void onKeyAction(String key, TriggerModel triggerModel, boolean active);
        void onKeyEvent(String key, TriggerModel triggerModel, ShortcutEventType eventType);
    }

    private static final String TAG = "FloatingShortcutBar";

    private final Context context;
    private final Map<String, FloatingShortcutKey> keyWindows = new HashMap<>();
    private OnKeyActionListener keyActionListener;

    public FloatingShortcutBar(Context context) {
        this.context = context;
    }

    public void setOnKeyActionListener(OnKeyActionListener listener) {
        this.keyActionListener = listener;
    }

    public void refresh(List<ControlActionItem> items, Map<String, Boolean> visibility) {
        Set<String> visibleKeys = new HashSet<>();
        if (items != null && visibility != null) {
            for (ControlActionItem item : items) {
                String key = keyOf(item);
                if (Boolean.TRUE.equals(visibility.get(key))) {
                    visibleKeys.add(key);
                }
            }
        }

        if (items != null) {
            for (ControlActionItem item : items) {
                String key = keyOf(item);
                if (!visibleKeys.contains(key)) continue;

                FloatingShortcutKey shortcutKey = keyWindows.get(key);
                if (shortcutKey == null) {
                    shortcutKey = new FloatingShortcutKey(context, key);
                    shortcutKey.setTitle(item.getTitle());
                    shortcutKey.setTriggerModel(item.getTriggerModel());
                    shortcutKey.setHoldThresholdMs(item.getHoldThresholdMs() != null ? item.getHoldThresholdMs() : 0);
                    shortcutKey.setReleaseDelayMs(item.getReleaseDelayMs() != null ? item.getReleaseDelayMs() : 0);
                    shortcutKey.setCardStyle(item.getCardWidthDp(), item.getCardHeightDp(),
                            item.getCardBgNormal(), item.getCardBgActive());
                    shortcutKey.setOnKeyActionListener(new FloatingShortcutKey.OnKeyActionListener() {
                        @Override
                        public void onKeyAction(FloatingShortcutKey key) {
                            FloatingShortcutBar.this.onKeyAction(key);
                        }
                        @Override
                        public void onKeyEvent(FloatingShortcutKey key, ShortcutEventType eventType) {
                            FloatingShortcutBar.this.onKeyEvent(key, eventType);
                        }
                    });
                    shortcutKey.attach();
                    keyWindows.put(key, shortcutKey);
                    AppLogger.d(TAG, "shortcut key added: " + key);
                }
            }
        }

        Iterator<Map.Entry<String, FloatingShortcutKey>> it = keyWindows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FloatingShortcutKey> entry = it.next();
            if (!visibleKeys.contains(entry.getKey())) {
                entry.getValue().detach();
                it.remove();
                AppLogger.d(TAG, "shortcut key removed: " + entry.getKey());
            }
        }
    }

    public void detach() {
        for (FloatingShortcutKey shortcutKey : keyWindows.values()) {
            shortcutKey.detach();
        }
        keyWindows.clear();
    }

    public void bringToFront() {
        for (FloatingShortcutKey shortcutKey : keyWindows.values()) {
            shortcutKey.bringToFront();
        }
    }

    public void updateScale(float scale) {
        for (FloatingShortcutKey shortcutKey : keyWindows.values()) {
            shortcutKey.updateScale(scale);
        }
    }

    public void reClampAll() {
        for (FloatingShortcutKey shortcutKey : keyWindows.values()) {
            shortcutKey.reClampPosition();
        }
    }

    public void reloadSounds() {
        for (FloatingShortcutKey shortcutKey : keyWindows.values()) {
            shortcutKey.reloadSound();
        }
    }

    public void updateAlpha(float alpha) {
        for (FloatingShortcutKey shortcutKey : keyWindows.values()) {
            shortcutKey.updateAlpha(alpha);
        }
    }

    public void setEditMode(boolean enabled) {
        for (FloatingShortcutKey shortcutKey : keyWindows.values()) {
            shortcutKey.setEditMode(enabled);
        }
    }

    private void onKeyAction(FloatingShortcutKey shortcutKey) {
        AppLogger.d(TAG, "shortcut key action: " + shortcutKey.getKey()
                + " trigger=" + shortcutKey.getTriggerModel()
                + " active=" + shortcutKey.isActive());
        if (keyActionListener != null) {
            keyActionListener.onKeyAction(
                    shortcutKey.getKey(),
                    shortcutKey.getTriggerModel(),
                    shortcutKey.isActive());
        }
    }

    private void onKeyEvent(FloatingShortcutKey shortcutKey, ShortcutEventType eventType) {
        AppLogger.d(TAG, "shortcut key event: " + shortcutKey.getKey()
                + " trigger=" + shortcutKey.getTriggerModel()
                + " event=" + eventType);
        if (keyActionListener != null) {
            keyActionListener.onKeyEvent(
                    shortcutKey.getKey(),
                    shortcutKey.getTriggerModel(),
                    eventType);
        }
    }

    private String keyOf(ControlActionItem item) {
        return item.getPackageName() + "." + item.getActionId();
    }
}
