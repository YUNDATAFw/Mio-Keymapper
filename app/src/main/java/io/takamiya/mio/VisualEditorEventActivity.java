package io.takamiya.mio;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.adapter.ActionEventAdapter;
import io.takamiya.mio.config.Action;
import io.takamiya.mio.config.ActionGroup;
import io.takamiya.mio.config.ActionItem;
import io.takamiya.mio.config.ActionType;
import io.takamiya.mio.config.ConfigLoader;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.ShortcutEventType;
import io.takamiya.mio.configManage.ConfigPackageManager;
import io.takamiya.mio.configManage.InstallResult;
import io.takamiya.mio.databinding.ActivityVisualEditorEventBinding;

public class VisualEditorEventActivity extends AppCompatActivity {

    private static final String TAG = "VisualEditorEventActivity";
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    public static final String EXTRA_ACTION_ID = "extra_action_id";
    public static final String EXTRA_EVENT_TYPE = "extra_event_type";

    private ActivityVisualEditorEventBinding binding;
    private ConfigPackageManager manager;
    private String packageName;
    private String actionId;
    private String eventType;
    private Plugin plugin;
    private List<Action> actionList;
    private ActionGroup editingGroup;
    private ActionItem editingItem;
    private boolean editingFromActionArray = false;
    private ActionEventAdapter adapter;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVisualEditorEventBinding.inflate(getLayoutInflater());
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

        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        actionId = getIntent().getStringExtra(EXTRA_ACTION_ID);
        eventType = getIntent().getStringExtra(EXTRA_EVENT_TYPE);

        if (packageName == null || actionId == null) {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.toolbar.setTitle(getEventTitle());
        binding.toolbar.setSubtitle(actionId);

        loadPlugin();

        binding.fabAdd.setOnClickListener(v -> showAddActionDialog());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_visual_editor_event, menu);
        return true;
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            savePlugin();
            return true;
        }
        return false;
    }

    private void loadPlugin() {
        new Thread(() -> {
            Plugin loaded = manager.get(packageName);
            runOnUiThread(() -> {
                if (loaded == null) {
                    Toast.makeText(this, "配置不存在", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                plugin = loaded;

                ActionItem targetItem = null;
                List<ActionItem> actionItems = plugin.getActionList();
                if (actionItems != null) {
                    for (ActionItem item : actionItems) {
                        if (actionId.equals(item.getId())) {
                            targetItem = item;
                            break;
                        }
                    }
                }

                if (targetItem == null) {
                    Toast.makeText(this, "活动不存在", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                binding.toolbar.setTitle(getEventTitle());
                binding.toolbar.setSubtitle(targetItem.getTitle() + " - " + actionId);

                editingGroup = resolveEditingGroup(targetItem);
                actionList = new ArrayList<>();
                if (editingGroup != null && editingGroup.getActions() != null) {
                    actionList.addAll(editingGroup.getActions());
                }
                setupRecyclerView();
            });
        }).start();
    }

    /** 解析要编辑的 ActionGroup：每个作用域统一为单组；旧多组数据归并到第一个组且保存时即为单组。 */
    private ActionGroup resolveEditingGroup(ActionItem targetItem) {
        editingItem = targetItem;
        List<ActionGroup> groups;
        if (eventType != null && !eventType.isEmpty()) {
            ShortcutEventType type = ShortcutEventType.valueOf(eventType);
            java.util.Map<ShortcutEventType, List<ActionGroup>> events = targetItem.getEvents();
            if (events == null) {
                events = new java.util.HashMap<>();
                targetItem.setEvents(events);
            }
            groups = events.get(type);
            if (groups == null) {
                groups = new ArrayList<>();
                events.put(type, groups);
            }
            editingFromActionArray = false;
        } else {
            List<Action> flatActions = targetItem.getAction();
            if (flatActions != null && !flatActions.isEmpty()) {
                editingFromActionArray = true;
                ActionGroup temp = new ActionGroup();
                temp.setActions(new ArrayList<>(flatActions));
                return temp;
            }
            editingFromActionArray = false;
            groups = targetItem.getGroups();
            if (groups == null) {
                groups = new ArrayList<>();
                targetItem.setGroups(groups);
            }
        }
        return collapseToSingle(groups);
    }

    /** 将作用域内的组归并为单个组：合并所有动作到第一个组，其余丢弃（保存时即为单组）。 */
    private ActionGroup collapseToSingle(List<ActionGroup> groups) {
        if (groups.isEmpty()) {
            ActionGroup g = new ActionGroup();
            groups.add(g);
            return g;
        }
        ActionGroup first = groups.get(0);
        if (groups.size() > 1) {
            List<Action> merged = new ArrayList<>(
                    first.getActions() != null ? first.getActions() : new ArrayList<>());
            for (int i = 1; i < groups.size(); i++) {
                ActionGroup g = groups.get(i);
                if (g.getActions() != null) {
                    merged.addAll(g.getActions());
                }
            }
            first.setActions(merged);
            groups.clear();
            groups.add(first);
        }
        return first;
    }

    private void setupRecyclerView() {
        adapter = new ActionEventAdapter(this, actionList);
        adapter.setOnActionChangedListener(new ActionEventAdapter.OnActionChangedListener() {
            @Override
            public void onActionChanged(int position, Action action) {
                dirty = true;
            }

            @Override
            public void onActionDeleteRequested(int position, Action action) {
                showDeleteConfirmDialog(position, action);
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
                Action item = actionList.remove(from);
                actionList.add(to, item);
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

    private void showAddActionDialog() {
        String[] types = {"INPUT_KEY", "INPUT_KEY_GROUP", "INPUT_TEXT", "TOAST"};
        String[] labels = {"输入按键 (INPUT_KEY)", "按键组合 (INPUT_KEY_GROUP)", "输入文本 (INPUT_TEXT)", "悬浮提示 (TOAST)"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择动作类型")
                .setSingleChoiceItems(labels, 0, (dialog, which) -> {
                    Action newAction = new Action();
                    ActionType newType = ActionType.fromString(types[which]);
                    newAction.setType(newType);
                    if (newType == ActionType.INPUT_KEY || newType == ActionType.INPUT_KEY_GROUP) {
                        newAction.setKey("KEYCODE_A");
                    }
                    actionList.add(newAction);
                    adapter.notifyItemInserted(actionList.size() - 1);
                    binding.recyclerView.scrollToPosition(actionList.size() - 1);
                    dirty = true;
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteConfirmDialog(int position, Action action) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除确认")
                .setMessage("确定要删除这个动作吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    actionList.remove(position);
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, actionList.size() - position);
                    dirty = true;
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void savePlugin() {
        if (plugin == null || editingGroup == null || editingItem == null) return;

        if (editingFromActionArray) {
            editingItem.setAction(new ArrayList<>(actionList));
        } else {
            editingGroup.setActions(new ArrayList<>(actionList));
        }

        new Thread(() -> {
            String json = ConfigLoader.toJson(plugin);
            if (json == null) {
                runOnUiThread(() -> Toast.makeText(this, "序列化失败", Toast.LENGTH_SHORT).show());
                return;
            }

            InstallResult result = manager.install(json, true);
            runOnUiThread(() -> {
                switch (result.getStatus()) {
                    case SUCCESS:
                        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                        dirty = false;
                        setResult(RESULT_OK);
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
        if (plugin == null || editingGroup == null || editingItem == null) {
            Toast.makeText(this, "无数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingFromActionArray) {
            editingItem.setAction(new ArrayList<>(actionList));
        } else {
            editingGroup.setActions(new ArrayList<>(actionList));
        }

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

    private String getEventTitle() {
        if (eventType == null || eventType.isEmpty()) {
            return "编辑动作";
        }
        return "事件编辑 · " + ShortcutEventType.valueOf(eventType).getDisplayName();
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
