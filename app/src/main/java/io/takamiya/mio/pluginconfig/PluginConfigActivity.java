package io.takamiya.mio.pluginconfig;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.fsy.plugin.sdl.library.SdlEngine;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.R;
import io.takamiya.mio.databinding.ActivityPluginConfigBinding;
import io.takamiya.mio.dsl.DslPluginManager;
import io.takamiya.mio.floating.FloatingWindowService;

public class PluginConfigActivity extends AppCompatActivity {

    public static final String EXTRA_PLUGIN_NAME = "io.takamiya.mio.extra.PLUGIN_NAME";
    public static final String EXTRA_PLUGIN_PACKAGE = "io.takamiya.mio.extra.PLUGIN_PACKAGE";
    public static final String EXTRA_SAVED_VALUES = "io.takamiya.mio.extra.SAVED_VALUES";

    private static final String TAG = "PluginConfigActivity";

    private ActivityPluginConfigBinding binding;
    private PluginConfigAdapter adapter;
    private PluginConfigScheme scheme;
    private DslPluginManager dslManager;

    public static void open(Context context, @Nullable String pluginName, @Nullable String packageName) {
        open(context, pluginName, packageName, null);
    }

    public static void open(Context context, @Nullable String pluginName, @Nullable String packageName,
                            @Nullable Map<String, Object> savedValues) {
        Intent intent = new Intent(context, PluginConfigActivity.class);
        intent.putExtra(EXTRA_PLUGIN_NAME, pluginName);
        intent.putExtra(EXTRA_PLUGIN_PACKAGE, packageName);
        if (savedValues instanceof Serializable) {
            intent.putExtra(EXTRA_SAVED_VALUES, (Serializable) savedValues);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPluginConfigBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        String name = getIntent().getStringExtra(EXTRA_PLUGIN_NAME);
        String pkg = getIntent().getStringExtra(EXTRA_PLUGIN_PACKAGE);

        scheme = PluginConfigScheme.registeredFor(pkg);
        if (scheme == null) {
            AppLogger.w(TAG, "no config scheme registered for " + pkg + ", showing empty state");
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.toolbar.setSubtitle("未声明配置方案"
                    + (pkg != null ? " · " + pkg : ""));
            Toast.makeText(this, "该插件未声明配置方案", Toast.LENGTH_SHORT).show();
        } else {
            applySavedValues(pkg);
            binding.toolbar.setSubtitle("配置方案"
                    + (pkg != null ? " · " + pkg : ""));
        }

        binding.toolbar.setTitle(name != null && !name.isEmpty() ? name : "插件配置");
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.toolbar.inflateMenu(R.menu.menu_plugin_config);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveConfiguration();
                return true;
            }
            return false;
        });

        adapter = new PluginConfigAdapter();
        adapter.setOnButtonAction((key, title) -> {
            if (pkg == null) return;
            Toast.makeText(this, "触发: " + title, Toast.LENGTH_SHORT).show();
            PluginConfigDeclaration.callButton(DslPluginManager.get(), pkg, key);
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        TabLayout tabs = binding.tabs;
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showCategory(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        if (scheme != null) {
            for (PluginConfigScheme.Category category : scheme.getCategories()) {
                tabs.addTab(tabs.newTab().setText(category.title));
            }
        }
        if (tabs.getTabCount() > 0) {
            showCategory(0);
        } else {
            binding.emptyView.setVisibility(View.VISIBLE);
        }
    }

    /** 用插件 HostData 已保存的值覆盖 item 默认值(key → 值)。 */
    @SuppressWarnings("unchecked")
    private void applySavedValues(@Nullable String pkg) {
        if (pkg == null) return;
        Object extra = getIntent().getSerializableExtra(EXTRA_SAVED_VALUES);
        Map<String, Object> values = extra instanceof Map ? (Map<String, Object>) extra : null;
        if (values == null || values.isEmpty()) return;

        for (PluginConfigScheme.Category category : scheme.getCategories()) {
            for (PluginConfigScheme.Item item : category.items) {
                if (item.key == null || !values.containsKey(item.key)) continue;
                Object v = values.get(item.key);
                if (v == null) continue;
                try {
                    switch (item.type) {
                        case SWITCH:
                        case CHECKBOX:
                            item.boolValue = Boolean.TRUE.equals(v)
                                    || (v instanceof Number && ((Number) v).intValue() != 0)
                                    || "true".equalsIgnoreCase(String.valueOf(v));
                            break;
                        case TEXT_FIELD:
                            if ("int".equals(item.inputType) && v instanceof Number) {
                                item.textValue = String.valueOf(((Number) v).intValue());
                            } else if ("decimal".equals(item.inputType) && v instanceof Number) {
                                item.textValue = String.valueOf(((Number) v).doubleValue());
                            } else {
                                item.textValue = String.valueOf(v);
                            }
                            break;
                        case RADIO:
                            item.radioValue = String.valueOf(v);
                            break;
                        case CHIP:
                            item.chipValues.clear();
                            for (String s : String.valueOf(v).split(",")) {
                                String t = s.trim();
                                if (!t.isEmpty()) item.chipValues.add(t);
                            }
                            break;
                        default:
                            break;
                    }
                } catch (RuntimeException e) {
                    AppLogger.e(TAG, "applySavedValues failed: key=" + item.key, e);
                }
            }
        }
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.content.setPadding(
                    binding.content.getPaddingLeft(),
                    binding.content.getPaddingTop(),
                    binding.content.getPaddingRight(),
                    bars.bottom);
            return insets;
        });
    }

    private void showCategory(int index) {
        List<PluginConfigScheme.Category> cats = scheme.getCategories();
        if (index < 0 || index >= cats.size()) {
            return;
        }
        PluginConfigScheme.Category category = cats.get(index);
        binding.tvCategoryTitle.setText(category.title);
        binding.tvCategoryCount.setText(category.items.size() + " 项");

        boolean empty = category.items.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.submit(category.items);
    }

    private void saveConfiguration() {
        String pkg = getIntent().getStringExtra(EXTRA_PLUGIN_PACKAGE);
        if (pkg == null || scheme == null) {
            Toast.makeText(this, pkg == null ? "缺少插件包名，无法保存" : "该插件未声明配置方案，无法保存", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Map<String, Object> batch = new HashMap<>();
            for (PluginConfigScheme.Category category : scheme.getCategories()) {
                for (PluginConfigScheme.Item item : category.items) {
                    if (item.key == null || item.key.isEmpty()) continue;
                    Object value = collectValue(item);
                    if (value == NULL_SENTINEL) continue;
                    batch.put(item.key, value);
                }
            }
            if (batch.isEmpty()) {
                Toast.makeText(this, "没有可保存的配置项", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                SdlEngine.setHostDataBatch(pkg, batch);
            } catch (RuntimeException first) {
                // 存储损坏：隔离备份后重试一次（与 HostData.get 桥同一自愈路径）
                if (!io.takamiya.mio.dsl.HostDataRepair.looksCorrupt(first)
                        || !io.takamiya.mio.dsl.HostDataRepair.heal(
                                cn.fsy.plugin.sdl.library.internal.manage.open.OpenManager
                                        .pluginDir(getFilesDir(), pkg), first)) {
                    throw first;
                }
                SdlEngine.setHostDataBatch(pkg, batch);
            }
            Toast.makeText(this, "已保存，插件即将重启生效", Toast.LENGTH_SHORT).show();
            reloadPlugin(pkg);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "保存失败：请检查数值输入格式", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            AppLogger.e(TAG, "saveConfiguration failed: " + e.getMessage(), e);
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 标记 item 无持久化值(button/progress/textView)。 */
    private static final Object NULL_SENTINEL = new Object();

    /** 收集单个 item 的持久化值;不可持久化的类型返回 NULL_SENTINEL。 */
    private Object collectValue(PluginConfigScheme.Item item) {
        switch (item.type) {
            case SWITCH:
            case CHECKBOX:
                return item.boolValue;
            case TEXT_FIELD:
                return parseTextValue(item);
            case RADIO:
                return item.radioValue == null ? "" : item.radioValue;
            case CHIP:
                return String.join(",", item.chipValues);
            default:
                return NULL_SENTINEL;
        }
    }

    private Object parseTextValue(PluginConfigScheme.Item item) {
        String inputType = item.inputType == null ? "" : item.inputType;
        switch (inputType) {
            case "int": {
                int v = Integer.parseInt(item.textValue.trim());
                if (item.min != Integer.MIN_VALUE && v < item.min) {
                    throw new NumberFormatException(item.title + " 不能小于 " + item.min);
                }
                if (item.max != Integer.MAX_VALUE && v > item.max) {
                    throw new NumberFormatException(item.title + " 不能大于 " + item.max);
                }
                return v;
            }
            case "decimal": {
                double v = Double.parseDouble(item.textValue.trim());
                if (item.min != Integer.MIN_VALUE && v < item.min) {
                    throw new NumberFormatException(item.title + " 不能小于 " + item.min);
                }
                if (item.max != Integer.MAX_VALUE && v > item.max) {
                    throw new NumberFormatException(item.title + " 不能大于 " + item.max);
                }
                return v;
            }
            default:
                return item.textValue;
        }
    }

    /** 保存成功后自动重启插件：关闭 → 重开并启动服务（委托给 FloatingWindowService）。 */
    private void reloadPlugin(String pkg) {
        try {
            FloatingWindowService service = FloatingWindowService.getInstance();
            if (service != null) {
                service.reloadDslPlugin(pkg);
                return;
            }
            if (dslManager == null) {
                dslManager = DslPluginManager.get();
            }
            if (dslManager != null) {
                dslManager.close(pkg);
                dslManager.open(pkg);
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "reloadPlugin failed: " + pkg + " - " + e.getMessage(), e);
        }
    }
}