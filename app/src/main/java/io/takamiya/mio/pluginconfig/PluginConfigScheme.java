package io.takamiya.mio.pluginconfig;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PluginConfigScheme implements Serializable {

    public enum Type {
        SWITCH, TEXT_FIELD, PROGRESS, RADIO, TEXT_VIEW, BUTTON, CHIP, CHECKBOX
    }

    public static final class Option implements Serializable {
        public final String label;
        public final String value;

        public Option(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public Option(String label) {
            this(label, label);
        }
    }

    public static final class Item implements Serializable {
        public final Type type;
        public final String key;
        public final String title;
        public final String subtitle;

        public boolean boolValue;
        public String textValue = "";
        public String hint;
        public String inputType = "";
        public int min = Integer.MIN_VALUE;
        public int max = Integer.MAX_VALUE;
        public boolean indeterminate;
        public int progress;
        public int progressMax = 100;
        public String progressText = "";
        public List<Option> options = new ArrayList<>();
        public String radioValue;
        public Set<String> chipValues = new LinkedHashSet<>();
        public String bodyText = "";
        public String buttonLabel = "";
        public transient Runnable action;

        Item(Type type, String key, String title, String subtitle) {
            this.type = type;
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public static final class Category implements Serializable {
        public final String title;
        public final List<Item> items = new ArrayList<>();

        Category(String title) {
            this.title = title;
        }
    }

    private final List<Category> categories = new ArrayList<>();

    public List<Category> getCategories() {
        return categories;
    }

    public int totalItems() {
        int n = 0;
        for (Category c : categories) n += c.items.size();
        return n;
    }

    private static final java.util.Map<String, PluginConfigScheme> REGISTRY =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    public static PluginConfigScheme registeredFor(@Nullable String packageName) {
        return packageName == null ? null : REGISTRY.get(packageName);
    }

    public static void register(@Nullable String packageName, @Nullable PluginConfigScheme scheme) {
        if (packageName != null && scheme != null) {
            REGISTRY.put(packageName, scheme);
        }
    }

    public static void unregister(@Nullable String packageName) {
        if (packageName != null) {
            REGISTRY.remove(packageName);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PluginConfigScheme scheme = new PluginConfigScheme();

        public SectionBuilder section(String title) {
            Category cat = new Category(title);
            scheme.categories.add(cat);
            return new SectionBuilder(this, cat);
        }

        public PluginConfigScheme build() {
            return scheme;
        }
    }

    public static class SectionBuilder {
        private final Builder root;
        private final Category cat;

        SectionBuilder(Builder root, Category cat) {
            this.root = root;
            this.cat = cat;
        }

        private Item add(Type type, String key, String title, String subtitle) {
            Item item = new Item(type, key, title, subtitle);
            cat.items.add(item);
            return item;
        }

        public SectionBuilder addSwitch(String key, String title, String subtitle, boolean def) {
            add(Type.SWITCH, key, title, subtitle).boolValue = def;
            return this;
        }

        public SectionBuilder addTextField(String key, String title, String subtitle, String def, @Nullable String hint) {
            Item item = add(Type.TEXT_FIELD, key, title, subtitle);
            item.textValue = def == null ? "" : def;
            item.hint = hint;
            return this;
        }

        public SectionBuilder addProgress(String key, String title, String subtitle, int current, int max, String text) {
            Item item = add(Type.PROGRESS, key, title, subtitle);
            item.indeterminate = false;
            item.progress = current;
            item.progressMax = max > 0 ? max : 100;
            item.progressText = text == null ? "" : text;
            return this;
        }

        public SectionBuilder addIndeterminateProgress(String key, String title, String subtitle, String text) {
            Item item = add(Type.PROGRESS, key, title, subtitle);
            item.indeterminate = true;
            item.progressText = text == null ? "" : text;
            return this;
        }

        public SectionBuilder addRadio(String key, String title, String subtitle, int defIndex, String... labels) {
            Item item = add(Type.RADIO, key, title, subtitle);
            if (labels != null) {
                for (String label : labels) item.options.add(new Option(label));
                if (defIndex >= 0 && defIndex < labels.length) item.radioValue = labels[defIndex];
            }
            return this;
        }

        public SectionBuilder addTextView(String key, String title, String subtitle, String body) {
            add(Type.TEXT_VIEW, key, title, subtitle).bodyText = body == null ? "" : body;
            return this;
        }

        public SectionBuilder addButton(String key, String title, String subtitle, String label, @Nullable Runnable action) {
            Item item = add(Type.BUTTON, key, title, subtitle);
            item.buttonLabel = label == null ? "" : label;
            item.action = action;
            return this;
        }

        public SectionBuilder addChip(String key, String title, String subtitle, String[] labels, @Nullable String[] defSelected) {
            Item item = add(Type.CHIP, key, title, subtitle);
            if (labels != null) {
                for (String label : labels) item.options.add(new Option(label));
                if (defSelected != null) {
                    for (String s : defSelected) item.chipValues.add(s);
                }
            }
            return this;
        }

        public SectionBuilder addCheckbox(String key, String title, String subtitle, boolean def) {
            add(Type.CHECKBOX, key, title, subtitle).boolValue = def;
            return this;
        }

        public Builder and() {
            return root;
        }

        /** 返回本节最近添加的项(用于在链式添加后再补充字段)。 */
        public Item lastAdded() {
            if (cat.items.isEmpty()) return null;
            return cat.items.get(cat.items.size() - 1);
        }
    }
}