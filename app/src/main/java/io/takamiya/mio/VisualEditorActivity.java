package io.takamiya.mio;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.adapter.ActionItemAdapter;
import io.takamiya.mio.config.ActionItem;
import io.takamiya.mio.config.ConfigLoader;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.PluginConfig;
import io.takamiya.mio.config.ShortcutEventType;
import io.takamiya.mio.config.lhrb.LhrbCompiler;
import io.takamiya.mio.configManage.ConfigPackageManager;
import io.takamiya.mio.configManage.InstallResult;
import io.takamiya.mio.databinding.ActivityVisualEditorBinding;
import io.takamiya.mio.navigation.CompileLogDialogFragment;
import io.takamiya.mio.navigation.CompileResultDialogFragment;

public class VisualEditorActivity extends AppCompatActivity {

    private static final String TAG = "VisualEditorActivity";
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    private ActivityVisualEditorBinding binding;
    private ConfigPackageManager manager;
    private String packageName;
    private boolean configExpanded = false;
    private Plugin plugin;
    private List<ActionItem> actionItems;
    private ActionItemAdapter adapter;
    private boolean dirty = false;

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) {
                        exportToJson(uri);
                    }
                }
            });

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (dirty) {
                showUnsavedDialog();
            } else {
                finish();
            }
        }
    };

    private final ActivityResultLauncher<Intent> eventActivityLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    reloadPlugin();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVisualEditorBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        manager = new ConfigPackageManager(this);

        getOnBackPressedDispatcher().addCallback(this, backCallback);

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (dirty) {
                showUnsavedDialog();
            } else {
                finish();
            }
        });
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        binding.btnExpandConfig.setRotation(configExpanded ? 180f : 0f);
        binding.btnExpandConfig.setOnClickListener(v -> {
            configExpanded = !configExpanded;
            binding.configFieldsContainer.setVisibility(configExpanded ? View.VISIBLE : View.GONE);
            ObjectAnimator.ofFloat(binding.btnExpandConfig, "rotation", configExpanded ? 0f : 180f, configExpanded ? 180f : 0f)
                    .setDuration(200)
                    .start();
        });

        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName != null) {
            binding.toolbar.setSubtitle(packageName);
            loadPlugin();
        } else {
            binding.toolbar.setSubtitle("新建配置");
        }

        binding.fabAdd.setOnClickListener(v -> showAddActionItemDialog());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_visual_editor, menu);
        return true;
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            savePlugin();
            return true;
        } else if (item.getItemId() == R.id.action_export) {
            String fileName = packageName != null ? packageName + ".json" : "plugin.json";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            exportLauncher.launch(intent);
            return true;
        } else if (item.getItemId() == R.id.action_edit) {
            if (packageName != null) {
                Intent intent = new Intent(this, ConfigEditActivity.class);
                intent.putExtra(ConfigEditActivity.EXTRA_PACKAGE_NAME, packageName);
                startActivity(intent);
            }
            return true;
        } else if (item.getItemId() == R.id.action_compile) {
            startCompile();
            return true;
        }
        return false;
    }

    private void startCompile() {
        if (plugin == null) {
            Toast.makeText(this, "请先加载或保存配置", Toast.LENGTH_SHORT).show();
            return;
        }
        plugin.setActionList(actionItems);

        String json = ConfigLoader.toJson(plugin);
        if (json == null) {
            Toast.makeText(this, "序列化失败", Toast.LENGTH_SHORT).show();
            return;
        }

        CompileLogDialogFragment logDialog = CompileLogDialogFragment.newInstance();
        logDialog.show(getSupportFragmentManager(), "compile_log");

        new Thread(() -> {
            try {
                LhrbCompiler.Result result = LhrbCompiler.compile(this, plugin, json, logDialog::appendLog);
                runOnUiThread(() -> {
                    if (logDialog.isAdded()) {
                        logDialog.dismissAllowingStateLoss();
                    }
                    if (!isFinishing() && !isDestroyed()) {
                        CompileResultDialogFragment.newInstance(result)
                                .show(getSupportFragmentManager(), "compile_result");
                    }
                });
            } catch (Exception e) {
                AppLogger.e(TAG, "compile failed", e);
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                logDialog.appendLog("编译失败: " + err);
                runOnUiThread(() -> {
                    if (logDialog.isAdded()) {
                        logDialog.dismissAllowingStateLoss();
                    }
                    if (!isFinishing() && !isDestroyed()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("编译失败")
                                .setMessage(err)
                                .setPositiveButton("确定", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private void loadPlugin() {
        binding.loadingLayout.setVisibility(View.VISIBLE);
        binding.scrollView.setVisibility(View.INVISIBLE);
        binding.fabAdd.setVisibility(View.INVISIBLE);
        long startTime = System.currentTimeMillis();

        new Thread(() -> {
            Plugin loaded = manager.get(packageName);
            long elapsed = System.currentTimeMillis() - startTime;
            long delay = Math.max(0, 500 - elapsed);
            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            runOnUiThread(() -> {
                if (loaded == null) {
                    Toast.makeText(this, "配置不存在", Toast.LENGTH_SHORT).show();
                    binding.loadingLayout.setVisibility(View.GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    binding.fabAdd.setVisibility(View.VISIBLE);
                    finish();
                    return;
                }
                plugin = loaded;
                fillConfigFields();
                setupActionList();
                binding.scrollView.setVisibility(View.VISIBLE);
                binding.fabAdd.setVisibility(View.VISIBLE);
                binding.recyclerView.post(() -> binding.loadingLayout.setVisibility(View.GONE));
            });
        }).start();
    }

    private void reloadPlugin() {
        new Thread(() -> {
            Plugin loaded = manager.get(packageName);
            runOnUiThread(() -> {
                if (loaded != null) {
                    plugin = loaded;
                    setupActionList();
                }
            });
        }).start();
    }

    private void fillConfigFields() {
        PluginConfig config = plugin.getConfig();
        binding.etConfigName.setText(config.getName());
        binding.etPackageName.setText(config.getPackageName());
        binding.etDescription.setText(config.getDescription());
        binding.etAuthor.setText(config.getAuthor());
        binding.etDisplayVersion.setText(config.getVersionName());
        binding.etVersionCode.setText(config.getVersionCode() != null ? String.valueOf(config.getVersionCode()) : "");
        binding.etMinRuntimeEngine.setText(config.getMinEngine() != null ? String.valueOf(config.getMinEngine()) : "");
        binding.etUpdateApi.setText(config.getUpdateUrl());
        binding.etKeyHoldDuration.setText(config.getKeyHoldDuration() != null ? String.valueOf(config.getKeyHoldDuration()) : "");
        binding.switchDefaultEnabled.setChecked(config.isEnable());

        binding.etConfigName.addTextChangedListener(configWatcher(config::setName));
        binding.etDescription.addTextChangedListener(configWatcher(config::setDescription));
        binding.etAuthor.addTextChangedListener(configWatcher(config::setAuthor));
        binding.etDisplayVersion.addTextChangedListener(configWatcher(config::setVersionName));
        binding.etUpdateApi.addTextChangedListener(configWatcher(config::setUpdateUrl));
        binding.etVersionCode.addTextChangedListener(configWatcher(s -> {
            String text = s.toString().trim();
            try { config.setVersionCode(text.isEmpty() ? null : Integer.parseInt(text)); } catch (NumberFormatException ignored) {}
        }));
        binding.etMinRuntimeEngine.addTextChangedListener(configWatcher(s -> {
            String text = s.toString().trim();
            try { config.setMinEngine(text.isEmpty() ? null : Integer.parseInt(text)); } catch (NumberFormatException ignored) {}
        }));
        binding.etKeyHoldDuration.addTextChangedListener(configWatcher(s -> {
            String text = s.toString().trim();
            try { config.setKeyHoldDuration(text.isEmpty() ? null : Long.parseLong(text)); } catch (NumberFormatException ignored) {}
        }));
        binding.switchDefaultEnabled.setOnCheckedChangeListener((btn, checked) -> {
            config.setEnable(checked);
            dirty = true;
        });
    }

    private TextWatcher configWatcher(ConfigSetter setter) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                setter.set(s.toString());
                dirty = true;
            }
        };
    }

    interface ConfigSetter { void set(String value); }

    private void setupActionList() {
        actionItems = plugin.getActionList() != null ? plugin.getActionList() : new ArrayList<>();
        adapter = new ActionItemAdapter(this, actionItems);
        adapter.setOnActionEditListener(item -> {
            binding.loadingLayout.setVisibility(View.VISIBLE);
            new Thread(() -> {
                InstallResult result = persistPlugin();
                runOnUiThread(() -> {
                    binding.loadingLayout.setVisibility(View.GONE);
                    if (result == null) {
                        Toast.makeText(this, "序列化失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!result.isSuccess()) {
                        Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(this, VisualEditorEventActivity.class);
                    intent.putExtra(VisualEditorEventActivity.EXTRA_PACKAGE_NAME, packageName);
                    intent.putExtra(VisualEditorEventActivity.EXTRA_ACTION_ID, item.getId());
                    eventActivityLauncher.launch(intent);
                });
            }).start();
        });
        adapter.setOnEventEditListener((item, eventType) -> {
            binding.loadingLayout.setVisibility(View.VISIBLE);
            new Thread(() -> {
                InstallResult result = persistPlugin();
                runOnUiThread(() -> {
                    binding.loadingLayout.setVisibility(View.GONE);
                    if (result == null) {
                        Toast.makeText(this, "序列化失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!result.isSuccess()) {
                        Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(this, VisualEditorEventActivity.class);
                    intent.putExtra(VisualEditorEventActivity.EXTRA_PACKAGE_NAME, packageName);
                    intent.putExtra(VisualEditorEventActivity.EXTRA_ACTION_ID, item.getId());
                    intent.putExtra(VisualEditorEventActivity.EXTRA_EVENT_TYPE, eventType.name());
                    eventActivityLauncher.launch(intent);
                });
            }).start();
        });
        adapter.setOnActionItemChangedListener(new ActionItemAdapter.OnActionItemChangedListener() {
            @Override
            public void onActionItemChanged(int position, ActionItem item) {
                dirty = true;
            }

            @Override
            public void onActionItemDeleteRequested(int position, ActionItem item) {
                showDeleteConfirmDialog(position, item);
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView recyclerView,
                    RecyclerView.ViewHolder viewHolder,
                    RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                ActionItem item = actionItems.remove(from);
                actionItems.add(to, item);
                adapter.notifyItemMoved(from, to);
                dirty = true;
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        touchHelper.attachToRecyclerView(binding.recyclerView);
    }

    private void showAddActionItemDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_action_item, null);
        EditText etTitle = dialogView.findViewById(R.id.etDialogTitle);
        EditText etId = dialogView.findViewById(R.id.etDialogId);

        new MaterialAlertDialogBuilder(this)
                .setTitle("新建活动")
                .setView(dialogView)
                .setPositiveButton("创建", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String id = etId.getText().toString().trim();
                    if (id.isEmpty()) {
                        id = UUID.randomUUID().toString().substring(0, 8);
                    }
                    ActionItem newItem = new ActionItem();
                    newItem.setTitle(title.isEmpty() ? "新活动" : title);
                    newItem.setId(id);
                    newItem.setDescription("");
                    actionItems.add(newItem);
                    adapter.notifyItemInserted(actionItems.size() - 1);
                    binding.recyclerView.scrollToPosition(actionItems.size() - 1);
                    dirty = true;
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteConfirmDialog(int position, ActionItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除确认")
                .setMessage("确定要删除活动 \"" + item.getTitle() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    actionItems.remove(position);
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, actionItems.size() - position);
                    dirty = true;
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private InstallResult persistPlugin() {
        if (plugin == null) {
            plugin = new Plugin();
            plugin.setConfig(new PluginConfig());
            plugin.setActionList(new ArrayList<>());
        }
        plugin.setActionList(actionItems);

        String json = ConfigLoader.toJson(plugin);
        if (json == null) {
            return null;
        }
        return manager.install(json, true);
    }

    private void savePlugin() {
        binding.loadingLayout.setVisibility(View.VISIBLE);
        new Thread(() -> {
            InstallResult result = persistPlugin();
            runOnUiThread(() -> {
                binding.loadingLayout.setVisibility(View.GONE);
                if (result == null) {
                    Toast.makeText(this, "序列化失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                switch (result.getStatus()) {
                    case SUCCESS:
                        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                        dirty = false;
                        finish();
                        break;
                    case FORMAT_ERROR:
                        String errMsg = "JSON格式错误";
                        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                            errMsg = result.getErrors().get(0).getMessage();
                        }
                        Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                        break;
                    default:
                        Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                        break;
                }
            });
        }).start();
    }

    private void showUnsavedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("未保存的更改")
                .setMessage("当前有未保存的更改，是否保存？")
                .setPositiveButton("保存", (dialog, which) -> {
                    savePlugin();
                })
                .setNegativeButton("抛弃", (dialog, which) -> {
                    dirty = false;
                    finish();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void exportToJson(android.net.Uri uri) {
        if (plugin == null) {
            Toast.makeText(this, "无数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        plugin.setActionList(actionItems);

        new Thread(() -> {
            String json = ConfigLoader.toJson(plugin);
            if (json == null) {
                runOnUiThread(() -> Toast.makeText(this, "序列化失败", Toast.LENGTH_SHORT).show());
                return;
            }

            try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    runOnUiThread(() -> Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                AppLogger.e(TAG, "Export failed", e);
                runOnUiThread(() -> Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
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
}