package io.takamiya.mio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

import io.takamiya.mio.adapter.DownloadActiveAdapter;
import io.takamiya.mio.adapter.DownloadDoneAdapter;
import io.takamiya.mio.bean.DownloadTask;
import io.takamiya.mio.databinding.ActivityDownloadManagerBinding;
import io.takamiya.mio.utils.DownloadManager;

public class DownloadManagerActivity extends AppCompatActivity {

    private ActivityDownloadManagerBinding binding;
    private DownloadActiveAdapter activeAdapter;
    private DownloadDoneAdapter doneAdapter;
    private int currentTab = 0;

    private final DownloadManager.Listener dmListener = new DownloadManager.Listener() {
        @Override
        public void onTaskAdded(DownloadTask task) {
            runOnUiThread(() -> refreshCurrentTab());
        }

        @Override
        public void onTaskUpdated(DownloadTask task) {
            runOnUiThread(() -> {
                if (task.isFinished()) {
                    activeAdapter.removeTask(task.getId());
                    refreshEmpty();
                } else {
                    activeAdapter.updateTask(task);
                }
            });
        }

        @Override
        public void onTaskRemoved(DownloadTask task) {
            runOnUiThread(() -> refreshCurrentTab());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDownloadManagerBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        activeAdapter = new DownloadActiveAdapter();
        activeAdapter.setOnActionListener(new DownloadActiveAdapter.OnActionListener() {
            @Override
            public void onPauseClick(DownloadTask task, int position) {
                DownloadManager.getInstance().pause(task);
            }

            @Override
            public void onRemoveClick(DownloadTask task, int position) {
                confirmRemove(task);
            }

            @Override
            public void onLongClick(DownloadTask task, int position) {
                enterSelectionMode();
            }

            @Override
            public void onSelectionChanged(int count) {
                updateSelectionToolbar();
            }
        });

        doneAdapter = new DownloadDoneAdapter();
        doneAdapter.setOnActionListener(new DownloadDoneAdapter.OnActionListener() {
            @Override
            public void onOpenClick(DownloadTask task, int position) {
                openFile(task);
            }

            @Override
            public void onRemoveClick(DownloadTask task, int position) {
                confirmRemove(task);
            }

            @Override
            public void onLongClick(DownloadTask task, int position) {
                enterSelectionMode();
            }

            @Override
            public void onSelectionChanged(int count) {
                updateSelectionToolbar();
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(activeAdapter);

        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                if (activeAdapter.isSelectionMode() || doneAdapter.isSelectionMode()) {
                    exitSelectionMode();
                }
                refreshCurrentTab();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }
        });

        binding.btnClearDone.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("清理已完成")
                    .setMessage("确定清理所有已完成的下载任务？\n\n⚠ 清理后本地文件也会被删除，无法恢复。")
                    .setPositiveButton("清理", (d, w) -> {
                        DownloadManager.getInstance().clearDone();
                        refreshCurrentTab();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        binding.selectionToolbar.setNavigationOnClickListener(v -> exitSelectionMode());

        binding.btnDeleteSelected.setOnClickListener(v -> {
            int count = currentTab == 0 ? activeAdapter.getSelectedCount() : doneAdapter.getSelectedCount();
            if (count == 0) {
                Toast.makeText(this, "请先选择要删除的任务", Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("删除选中任务")
                    .setMessage("确定删除选中的 " + count + " 个任务？\n\n⚠ 本地文件也会一并删除，无法恢复。")
                    .setPositiveButton("删除", (d, w) -> deleteSelected())
                    .setNegativeButton("取消", null)
                    .show();
        });

        binding.btnSelectAll.setOnClickListener(v -> {
            if (currentTab == 0) {
                List<DownloadTask> all = DownloadManager.getInstance().getActiveTasks();
                if (activeAdapter.getSelectedCount() == all.size()) {
                    activeAdapter.exitSelectionMode();
                    enterSelectionMode();
                } else {
                    activeAdapter.exitSelectionMode();
                    activeAdapter.enterSelectionMode();
                    for (int i = 0; i < all.size(); i++) {
                        activeAdapter.toggleSelection(i);
                    }
                }
            } else {
                List<DownloadTask> all = DownloadManager.getInstance().getCompletedTasks();
                if (doneAdapter.getSelectedCount() == all.size()) {
                    doneAdapter.exitSelectionMode();
                    enterSelectionMode();
                } else {
                    doneAdapter.exitSelectionMode();
                    doneAdapter.enterSelectionMode();
                    for (int i = 0; i < all.size(); i++) {
                        doneAdapter.toggleSelection(i);
                    }
                }
            }
        });

        DownloadManager.getInstance().addListener(dmListener);
        refreshCurrentTab();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DownloadManager.getInstance().removeListener(dmListener);
    }

    @Override
    public void onBackPressed() {
        if (activeAdapter.isSelectionMode() || doneAdapter.isSelectionMode()) {
            exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    private void enterSelectionMode() {
        if (currentTab == 0) {
            activeAdapter.enterSelectionMode();
        } else {
            doneAdapter.enterSelectionMode();
        }
        binding.toolbar.setVisibility(View.GONE);
        binding.selectionToolbar.setVisibility(View.VISIBLE);
        binding.btnClearDone.setVisibility(View.GONE);
        binding.tabLayout.setVisibility(View.GONE);
        updateSelectionToolbar();
    }

    private void exitSelectionMode() {
        activeAdapter.exitSelectionMode();
        doneAdapter.exitSelectionMode();
        binding.toolbar.setVisibility(View.VISIBLE);
        binding.selectionToolbar.setVisibility(View.GONE);
        binding.btnClearDone.setVisibility(View.VISIBLE);
        binding.tabLayout.setVisibility(View.VISIBLE);
    }

    private void updateSelectionToolbar() {
        int count = currentTab == 0 ? activeAdapter.getSelectedCount() : doneAdapter.getSelectedCount();
        binding.selectionToolbar.setTitle("已选 " + count + " 项");

        int total = currentTab == 0
                ? DownloadManager.getInstance().getActiveTasks().size()
                : DownloadManager.getInstance().getCompletedTasks().size();
        binding.btnSelectAll.setText(count == total ? "取消全选" : "全选");
    }

    private void deleteSelected() {
        List<DownloadTask> selected = currentTab == 0
                ? activeAdapter.getSelectedTasks()
                : doneAdapter.getSelectedTasks();
        if (!selected.isEmpty()) {
            DownloadManager.getInstance().removeBatch(selected);
        }
        exitSelectionMode();
        refreshCurrentTab();
    }

    private void refreshCurrentTab() {
        if (currentTab == 0) {
            List<DownloadTask> active = DownloadManager.getInstance().getActiveTasks();
            binding.recyclerView.setAdapter(activeAdapter);
            activeAdapter.setData(active);
        } else {
            List<DownloadTask> done = DownloadManager.getInstance().getCompletedTasks();
            binding.recyclerView.setAdapter(doneAdapter);
            doneAdapter.setData(done);
        }
        refreshEmpty();
    }

    private void refreshEmpty() {
        boolean empty = (currentTab == 0 && activeAdapter.getItemCount() == 0)
                || (currentTab == 1 && doneAdapter.getItemCount() == 0);
        binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void confirmRemove(DownloadTask task) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除任务")
                .setMessage("确定删除「" + task.getTitle() + "」？\n\n⚠ 本地文件也会被删除。")
                .setPositiveButton("删除", (d, w) -> DownloadManager.getInstance().remove(task))
                .setNegativeButton("取消", null)
                .show();
    }

    private void openFile(DownloadTask task) {
        try {
            File file = new File(task.getFilePath());
            if (!file.exists()) {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getContentResolver().getType(uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyWindowInsets(ActivityDownloadManagerBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.selectionToolbar.setPadding(
                    binding.selectionToolbar.getPaddingLeft(),
                    bars.top,
                    binding.selectionToolbar.getPaddingRight(),
                    binding.selectionToolbar.getPaddingBottom());
            binding.content.setPadding(
                    binding.content.getPaddingLeft(),
                    binding.content.getPaddingTop(),
                    binding.content.getPaddingRight(),
                    bars.bottom);
            return insets;
        });
    }
}
