package io.takamiya.mio.navigation;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.adapter.BuildingListAdapter;
import io.takamiya.mio.adapter.BuildingListAdapter.BuildingItem;
import io.takamiya.mio.building.BuildingInfo;
import io.takamiya.mio.building.BuildingRepository;
import io.takamiya.mio.building.BuildingStore;
import io.takamiya.mio.databinding.FragmentBuildingListBinding;

/**
 * 建筑列表管理页：真实数据展示 + 详情 + 删除 + SAF 导入蓝图（选文件 → 命名 → 转化落盘）。
 */
public class BuildingListFragment extends Fragment {

    private static final String TAG = "BuildingListFragment";

    private FragmentBuildingListBinding binding;
    private BuildingListAdapter adapter;
    private BuildingStore store;
    private List<BuildingInfo> currentInfos = new ArrayList<>();

    private AlertDialog progressDialog;
    private ProgressBar progressDialogBar;
    private TextView progressDialogLabel;

    private final ActivityResultLauncher<String[]> blueprintPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onBlueprintPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBuildingListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        store = BuildingStore.get(requireContext());

        adapter = new BuildingListAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        adapter.setOnItemActionListener(new BuildingListAdapter.OnItemActionListener() {
            @Override
            public void onDelete(int position) {
                BuildingInfo info = infoAt(position);
                if (info != null) confirmDelete(info);
            }

            @Override
            public void onItemClick(int position) {
                BuildingInfo info = infoAt(position);
                if (info != null) showInfoDialog(info);
            }
        });

        binding.fabAdd.setOnClickListener(v ->
                blueprintPicker.launch(new String[]{"*/*"}));

        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    // ==================== 列表 ====================

    private BuildingInfo infoAt(int position) {
        return position >= 0 && position < currentInfos.size() ? currentInfos.get(position) : null;
    }

    private void reload() {
        if (binding == null) return;
        store.io(() -> {
            List<BuildingInfo> list;
            try {
                list = store.repository().listBuildings();
            } catch (Exception e) {
                AppLogger.w(TAG, "list buildings failed: " + e.getMessage());
                list = new ArrayList<>();
            }
            List<BuildingInfo> result = list;
            store.main(() -> applyList(result));
        });
    }

    private void applyList(List<BuildingInfo> list) {
        if (binding == null) return;
        currentInfos = list;
        List<BuildingItem> items = new ArrayList<>(list.size());
        for (BuildingInfo info : list) {
            items.add(new BuildingItem(info.name, metaOf(info)));
        }
        adapter.setData(items);
        binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String metaOf(BuildingInfo info) {
        return info.sx + "×" + info.sy + "×" + info.sz
                + " · " + info.commandCount + " 条命令 · " + humanSize(info.fileBytes);
    }

    static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb);
        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0);
    }

    // ==================== 详情 / 删除 ====================

    private void showInfoDialog(BuildingInfo info) {
        if (!isAdded()) return;
        String msg = "尺寸：" + info.sx + "×" + info.sy + "×" + info.sz
                + "\n方块数：" + info.blockCount
                + "\n命令数：" + info.commandCount
                + "\n文件大小：" + humanSize(info.fileBytes)
                + "\n敏感词处理：" + info.sensitiveFixCount
                + "\n跳过方块：" + info.skippedBlocks
                + "\n源文件：" + info.sourceFile
                + "\n创建时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(new java.util.Date(info.createdAt));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(info.name)
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .setNegativeButton("删除", (d, w) -> confirmDelete(info))
                .show();
    }

    private void confirmDelete(BuildingInfo info) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除建筑")
                .setMessage("确定删除「" + info.name + "」？此操作不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    store.io(() -> {
                        try {
                            store.repository().delete(info.name);
                        } catch (Exception e) {
                            AppLogger.w(TAG, "delete failed: " + e.getMessage());
                        }
                        store.main(() -> {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), "已删除 " + info.name,
                                    Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 导入（选文件 → 命名 → 转化） ====================

    private void onBlueprintPicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        String displayName = queryDisplayName(uri);
        if (!isSupportedBlueprint(displayName)) {
            Toast.makeText(requireContext(), "仅支持 .schem / .schematic / .litematic",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        showNameDialog(uri, displayName);
    }

    private static boolean isSupportedBlueprint(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".schem") || lower.endsWith(".schematic") || lower.endsWith(".litematic");
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void showNameDialog(Uri uri, String displayName) {
        if (!isAdded()) return;
        Context ctx = requireContext();
        String suggested = baseName(displayName);

        EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("建筑名称");
        input.setText(suggested);
        input.setSelection(suggested.length());

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(ctx);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = pad;
        lp.rightMargin = pad;
        input.setLayoutParams(lp);
        container.addView(input);

        AlertDialog dlg = new MaterialAlertDialogBuilder(ctx)
                .setTitle("导入建筑")
                .setMessage("文件：" + displayName)
                .setView(container)
                .setPositiveButton("开始转化", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText() != null ? input.getText().toString() : "";
            try {
                BuildingRepository.sanitizeName(name);
            } catch (IllegalArgumentException e) {
                Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            dlg.dismiss();
            startConvert(uri, displayName, name);
        }));
        dlg.show();
    }

    private void startConvert(Uri uri, String sourceFileName, String name) {
        if (!isAdded()) return;
        ContentResolver resolver = requireContext().getContentResolver();
        showProgressDialog(name);

        store.io(() -> {
            try {
                byte[] raw = readBytes(resolver, uri);
                store.repository().convertAsync(raw, sourceFileName, name,
                        new BuildingRepository.ConvertListener() {
                            @Override
                            public void onProgress(int percent) {
                                store.main(() -> updateProgress(percent));
                            }

                            @Override
                            public void onSuccess(BuildingInfo info) {
                                store.main(() -> {
                                    dismissProgressDialog();
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(),
                                            "已导入 " + info.name + "（" + info.commandCount + " 条命令）",
                                            Toast.LENGTH_SHORT).show();
                                    reload();
                                });
                            }

                            @Override
                            public void onError(Exception e) {
                                store.main(() -> {
                                    dismissProgressDialog();
                                    showConvertError(e);
                                });
                            }
                        });
            } catch (Exception e) {
                store.main(() -> {
                    dismissProgressDialog();
                    showConvertError(e);
                });
            }
        });
    }

    private void showConvertError(Exception e) {
        if (!isAdded()) return;
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
        AppLogger.e(TAG, "convert failed: " + msg, e);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("转化失败")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show();
    }

    // ==================== 进度对话框 ====================

    private void showProgressDialog(String name) {
        if (!isAdded()) return;
        dismissProgressDialog();
        Context ctx = requireContext();

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * ctx.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView label = new TextView(ctx);
        label.setText("准备中…");
        layout.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ProgressBar bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(100);
        bar.setProgress(0);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = pad / 2;
        layout.addView(bar, barLp);

        progressDialogLabel = label;
        progressDialogBar = bar;

        AlertDialog dlg = new MaterialAlertDialogBuilder(ctx)
                .setTitle("正在转化：" + name)
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton("转入后台", (d, w) -> { /* 转化继续，完成后 Toast 提示 */ })
                .create();
        dlg.setOnDismissListener(d -> {
            progressDialogBar = null;
            progressDialogLabel = null;
            if (progressDialog == d) progressDialog = null;
        });
        progressDialog = dlg;
        dlg.show();
    }

    private void updateProgress(int percent) {
        if (progressDialogBar != null) progressDialogBar.setProgress(percent);
        if (progressDialogLabel != null) progressDialogLabel.setText(percent + "%");
    }

    private void dismissProgressDialog() {
        AlertDialog d = progressDialog;
        progressDialog = null;
        if (d != null && d.isShowing()) {
            d.dismiss();
        } else {
            progressDialogBar = null;
            progressDialogLabel = null;
        }
    }

    // ==================== 工具 ====================

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor c = requireContext().getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && !c.isNull(idx)) {
                    String s = c.getString(idx);
                    if (s != null && !s.isEmpty()) return s;
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "query display name failed: " + e.getMessage());
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "blueprint.schem";
    }

    private static byte[] readBytes(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("无法打开文件");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissProgressDialog();
        binding = null;
    }
}
