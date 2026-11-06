package io.takamiya.mio.navigation;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cn.fsy.plugin.kore.library.PluginCallback;
import cn.fsy.plugin.kore.library.KoreEngine;
import cn.fsy.plugin.kore.library.KoreException;
import cn.fsy.plugin.kore.library.plugin.PluginItem;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.R;
import io.takamiya.mio.PluginAuthSheet;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.PluginConfig;
import io.takamiya.mio.configManage.IPluginManager;
import io.takamiya.mio.databinding.FragmentPluginBinding;
import io.takamiya.mio.databinding.ItemPluginsBinding;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.dsl.DslPluginHandle;
import io.takamiya.mio.dsl.DslPluginManager;
import io.takamiya.mio.pluginconfig.PluginConfigActivity;
import io.takamiya.mio.pluginconfig.PluginConfigDeclaration;
import io.takamiya.mio.utils.DownloadManager;

public class PluginFragment extends Fragment {

    private static final String TAG = "PluginFragment";

    private FragmentPluginBinding binding;
    private PluginListAdapter adapter;
    private List<Plugin> pluginList;
    private IPluginManager manager;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPluginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        manager = new IPluginManager(requireContext());

        binding.swipeRefresh.setOnRefreshListener(this::refreshList);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        binding.fab.setOnClickListener(v -> {
            filePickerLauncher.launch(new String[]{"application/zip", "application/octet-stream", "*/*"});
        });

        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        List<Plugin> plugins = manager.list();
        pluginList = new ArrayList<>();

        for (Plugin plugin : plugins) {
            pluginList.add(plugin);
        }

        if (adapter == null) {
            adapter = new PluginListAdapter();
            binding.recyclerView.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }

        binding.swipeRefresh.setRefreshing(false);

        boolean empty = pluginList.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) return;

        try {
            File importDir = new File(requireContext().getFilesDir(), "import");
            if (!importDir.exists() && !importDir.mkdirs()) {
                throw new IllegalStateException("无法创建导入目录");
            }
            File zipFile = new File(importDir, "pending_install.zip");
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(zipFile)) {
                if (is == null) {
                    Toast.makeText(requireContext(), "无法读取文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }

            Toast.makeText(requireContext(), "正在安装插件...", Toast.LENGTH_SHORT).show();

            DslPluginManager.get().installFromFile(zipFile.getAbsolutePath(), new PluginCallback() {
                @Override
                public void onStart(java.nio.file.Path installPath) {}

                @Override
                public void onProgress(int progressPercent) {}

                @Override
                public void onSuccess(cn.fsy.plugin.kore.library.plugin.PluginItem item) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "安装成功: " + item.getPluginName() + " v" + item.getPluginVersion(),
                                Toast.LENGTH_SHORT).show();
                        refreshList();
                        showAuthSheet(item);
                    });
                }

                @Override
                public void onFailure(cn.fsy.plugin.kore.library.KoreException error) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "安装失败: " + io.takamiya.mio.dsl.InstallErrors.describe(error),
                                Toast.LENGTH_LONG).show()
                    );
                }

                @Override
                public void onCancel() {}
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "install failed", e);
            Toast.makeText(requireContext(), "安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openPluginConfig(String pluginName, String pkg) {
        if (PluginConfigDeclaration.configModule(pkg) == null) {
            // config.json 未声明 CONFIG 服务：开发者不需要配置页
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("无配置页")
                    .setMessage("该插件未声明配置页（config.json 的 service 中无 tag=CONFIG 项），无需编辑配置。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        DslPluginManager manager = DslPluginManager.get();
        if (manager == null) {
            // 悬浮窗服务未启动时管理器不存在：引擎已就绪，直接用 app context 兜底创建，
            // 否则配置页会因 manager==null 落入空态。
            manager = new DslPluginManager(requireContext().getApplicationContext());
        }
        try {
            // 解析插件 config.dsl 声明的方案 + 读取已保存值;失败时以空方案打开
            PluginConfigDeclaration.resolve(manager, pkg);
            java.util.Map<String, Object> values = PluginConfigDeclaration.values(manager, pkg);
            PluginConfigActivity.open(requireContext(), pluginName, pkg, values);
        } catch (Exception e) {
            AppLogger.e(TAG, "openPluginConfig failed: " + pkg + " - " + e.getMessage(), e);
            PluginConfigActivity.open(requireContext(), pluginName, pkg);
        }
    }

    private void showAuthSheet(PluginItem item) {
        if (!isAdded() || item == null) return;
        PluginAuthSheet sheet = new PluginAuthSheet(requireContext(),
                item.getPluginPackage(), item.getPluginName());
        sheet.show();
    }

    /**
     * 删除插件：优先走 DslPluginManager.uninstall（onUninstall 钩子 → 关句柄 → 引擎卸载），
     * 悬浮窗服务从未运行（管理器不存在）时回退为 IPluginManager 裸删。
     */
    private void uninstallPlugin(String pkg) {
        DslPluginManager dslManager = DslPluginManager.get();
        if (dslManager != null) {
            dslManager.uninstall(pkg, new PluginCallback.Empty());
        } else {
            manager.uninstall(pkg);
        }
    }

    /**
     * 插件页「检查更新」：后台线程调用插件导出的 checkUpdate()（内部可联网，可能阻塞），
     * 解析返回的 JSON 字符串/Map 后弹窗展示；句柄未打开时临时 open，结束关闭。
     */
    private void checkPluginUpdate(String pkg, String pluginName) {
        if (!KoreEngine.isEnabled(pkg)) {
            Toast.makeText(requireContext(), "插件未启用，无法检查更新", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show();

        final FragmentActivity act = requireActivity();
        final Context appCtx = requireContext().getApplicationContext();

        new Thread(() -> {
            DslPluginManager m = DslPluginManager.get();
            if (m == null) {
                m = new DslPluginManager(appCtx);
            }
            final DslPluginManager dm = m;
            DslPluginHandle handle = dm.getHandle(pkg);
            boolean openedHere = false;
            if (handle == null) {
                handle = dm.open(pkg);
                openedHere = handle != null;
            }
            final boolean available = handle != null;
            Object result = available ? handle.callIfExported("checkUpdate") : null;
            if (openedHere) {
                dm.close(pkg);
            }
            final Object r = result;
            act.runOnUiThread(() -> showCheckUpdateResult(act, pluginName, available, r));
        }, "check-update").start();
    }

    /** 展示 checkUpdate 结果：null=未实现；hasUpdate=false 已最新；否则提供下载。 */
    private void showCheckUpdateResult(FragmentActivity act, String pluginName,
                                       boolean available, Object result) {
        if (!isAdded()) return;
        if (!available) {
            Toast.makeText(act, "打开插件失败，无法检查更新", Toast.LENGTH_SHORT).show();
            return;
        }
        if (result == null) {
            Toast.makeText(act, "该插件未实现检测更新", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject obj = toJsonObject(result);
            if (obj == null) {
                Toast.makeText(act, "检测更新返回格式无法解析", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!obj.optBoolean("hasUpdate", false)) {
                String cur = obj.optString("version", "");
                new MaterialAlertDialogBuilder(act)
                        .setTitle("检查更新")
                        .setMessage("「" + pluginName + "」已是最新版本" + (cur.isEmpty() ? "" : "（v" + cur + "）"))
                        .setPositiveButton("知道了", null)
                        .show();
                return;
            }
            String version = obj.optString("version", "");
            String description = obj.optString("description", "");
            String url = obj.optString("url", "");
            String msg = (description.isEmpty() ? "发现新版本" : description)
                    + (version.isEmpty() ? "" : "\n版本: " + version)
                    + (url.isEmpty() ? "" : "\n" + url);
            new MaterialAlertDialogBuilder(act)
                    .setTitle("发现新版本")
                    .setMessage(msg)
                    .setPositiveButton("下载", (d, w) -> {
                        if (url.isEmpty()) {
                            Toast.makeText(act, "更新地址为空，无法下载", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String fileName = fileNameFromUrl(url, pluginName + "-update.zip");
                        DownloadManager.getInstance().addTask(url, fileName);
                        Toast.makeText(act, "已加入下载: " + fileName, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            AppLogger.e(TAG, "showCheckUpdateResult failed: " + e.getMessage(), e);
            Toast.makeText(act, "检测更新解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** checkUpdate 返回值 → JSONObject：支持 JSON 字符串 / Map 两种形态。 */
    private static JSONObject toJsonObject(Object result) {
        if (result instanceof JSONObject) return (JSONObject) result;
        if (result instanceof String) {
            String s = ((String) result).trim();
            if (s.isEmpty()) return null;
            try {
                return new JSONObject(s);
            } catch (Exception e) {
                AppLogger.e(TAG, "toJsonObject: invalid JSON string: " + e.getMessage());
                return null;
            }
        }
        if (result instanceof java.util.Map) {
            return new JSONObject((java.util.Map<?, ?>) result);
        }
        return null;
    }

    private static String fileNameFromUrl(String url, String fallback) {
        try {
            String path = url;
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash < path.length() - 1) {
                String name = path.substring(slash + 1);
                if (!name.isEmpty()) return name;
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }

    // ==================== 内部 Adapter ====================

    private class PluginListAdapter extends RecyclerView.Adapter<PluginListAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemPluginsBinding b = ItemPluginsBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Plugin plugin = pluginList.get(position);
            PluginConfig config = plugin.getConfig();
            String pkg = config.getPackageName();

            holder.binding.tvConfigName.setText(config.getName());
            holder.binding.tvConfigDescription.setText(config.getDescription());
            holder.binding.tvVersion.setText("版本: " + config.getVersionName());
            holder.binding.tvPackageName.setText("包名: " + pkg);

            boolean enabled = bindPluginState(holder, pkg);
            bindEnableSwitch(holder, pkg, config, enabled);

            holder.binding.btnConfigure.setOnClickListener(v ->
                    openPluginConfig(config.getName(), config.getPackageName()));

            holder.binding.btnCheckUpdate.setOnClickListener(v ->
                    checkPluginUpdate(pkg, config.getName()));

            holder.binding.btnEdit.setOnClickListener(v -> {
                PluginAuthSheet sheet = new PluginAuthSheet(requireContext(), pkg, config.getName());
                sheet.show();
            });

            holder.binding.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除插件")
                        .setMessage("确定要删除插件 \"" + config.getName() + "\" 吗？该操作不可恢复。")
                        .setPositiveButton("删除", (d, w) -> {
                            uninstallPlugin(config.getPackageName());
                            Toast.makeText(requireContext(), "已删除: " + config.getName(), Toast.LENGTH_SHORT).show();
                            refreshList();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }

        private boolean bindPluginState(@NonNull VH holder, String pkg) {
            boolean enabled = true;
            File iconFile = null;
            try {
                enabled = KoreEngine.isEnabled(pkg);
                iconFile = KoreEngine.pluginIconFile(pkg);
            } catch (Exception e) {
                AppLogger.e(TAG, "read plugin state failed: " + pkg + " - " + e.getMessage());
            }
            if (iconFile != null && iconFile.isFile()) {
                holder.binding.ivConfigIcon.setImageURI(Uri.fromFile(iconFile));
            } else {
                holder.binding.ivConfigIcon.setImageResource(R.mipmap.ic_launcher);
            }
            holder.binding.ivConfigIcon.setAlpha(enabled ? 1.0f : 0.4f);
            return enabled;
        }

        private void bindEnableSwitch(@NonNull VH holder, String pkg, PluginConfig config, boolean enabled) {
            holder.binding.switchEnable.setOnCheckedChangeListener(null);
            holder.binding.switchEnable.setChecked(enabled);
            holder.binding.switchEnable.setOnCheckedChangeListener((btn, checked) -> {
                try {
                    FloatingWindowService service = FloatingWindowService.getInstance();
                    if (checked) {
                        KoreEngine.enablePlugin(pkg);
                        // 悬浮窗运行中：立即把插件重新加载进服务（关闭 → 打开 + 启动服务）
                        if (service != null) {
                            service.reloadDslPlugin(pkg);
                            // 生命周期钩子：启用（未导出时静默跳过）
                            DslPluginManager dm = DslPluginManager.get();
                            DslPluginHandle h = dm != null ? dm.getHandle(pkg) : null;
                            if (h != null) h.callIfExported("onEnable");
                        }
                    } else {
                        // 生命周期钩子：禁用（句柄还活着，须在关闭前回调）
                        DslPluginManager dm = DslPluginManager.get();
                        DslPluginHandle h = dm != null ? dm.getHandle(pkg) : null;
                        if (h != null) h.callIfExported("onDisable");
                        KoreEngine.disablePlugin(pkg);
                        if (service != null) {
                            service.closeDslPlugin(pkg);
                        } else {
                            DslPluginManager m = DslPluginManager.get();
                            if (m != null) {
                                m.close(pkg);
                            }
                        }
                    }
                } catch (Exception e) {
                    AppLogger.e(TAG, "toggle plugin state failed: " + pkg + " - " + e.getMessage());
                    Toast.makeText(requireContext(), "切换失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btn.setChecked(!checked);
                    return;
                }
                Toast.makeText(requireContext(),
                        checked ? "已启用: " + config.getName() : "已禁用: " + config.getName(),
                        Toast.LENGTH_SHORT).show();
                refreshList();
            });
        }

        @Override
        public int getItemCount() {
            return pluginList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemPluginsBinding binding;
            VH(ItemPluginsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
