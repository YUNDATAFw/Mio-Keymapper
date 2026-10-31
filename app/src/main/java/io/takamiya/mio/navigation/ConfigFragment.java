package io.takamiya.mio.navigation;

import android.content.Intent;
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
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.RegistryManager;
import io.takamiya.mio.R;
import io.takamiya.mio.VisualEditorActivity;
import io.takamiya.mio.adapter.ConfigAdapter;
import io.takamiya.mio.bean.Config;
import io.takamiya.mio.config.Plugin;
import io.takamiya.mio.config.PluginConfig;
import io.takamiya.mio.config.lhrb.LhrbReader;
import io.takamiya.mio.configManage.ConfigDiffHelper;
import io.takamiya.mio.configManage.ConfigUpdateChecker;
import io.takamiya.mio.configManage.ConfigPackageManager;
import io.takamiya.mio.configManage.InstallResult;
import io.takamiya.mio.databinding.FragmentConfigBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class ConfigFragment extends Fragment implements ConfigUpdateDialogFragment.ConfigUpdateCallback {

    private static final String TAG = "ConfigFragment";

    private FragmentConfigBinding binding;
    private ConfigAdapter adapter;
    private List<Config> configList;
    private ConfigPackageManager manager;
    private Registry registry;
    private InstallSource pendingSource;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentConfigBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        manager = new ConfigPackageManager(requireContext());

        registry = RegistryUtils.get();

        binding.swipeRefresh.setOnRefreshListener(this::refreshList);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        binding.fab.setOnClickListener(v -> {
            String[] options = {"空白模板", "从JSON文件导入", "从LHRB文件导入"};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("新建配置")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            createBlankTemplate();
                        } else if (which == 1) {
                            AppLogger.d(TAG, "FAB clicked, launching JSON file picker");
                            filePickerLauncher.launch(new String[]{"application/json", "*/*"});
                        } else {
                            AppLogger.d(TAG, "FAB clicked, launching LHRB file picker");
                            filePickerLauncher.launch(new String[]{"application/octet-stream", "*/*"});
                        }
                    })
                    .show();
        });

        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        AppLogger.d(TAG, "refreshList() called");
        List<Plugin> plugins = manager.list();
        configList = new ArrayList<>();

        for (int i = 0; i < plugins.size(); i++) {
            Plugin plugin = plugins.get(i);
            PluginConfig pluginConfig = plugin.getConfig();

            String warning = null;
            if (plugin.isIncompatible()) {
                warning = "不适配当前引擎（旧格式 v" + 
                        (pluginConfig.getMinEngine() != null ? pluginConfig.getMinEngine() : "?") + "），仅可导出";
            } else if (!pluginConfig.isEnable()) {
                warning = "该配置已禁用";
            }

            Config config = new Config(
                    pluginConfig.getName(),
                    pluginConfig.getDescription(),
                    pluginConfig.getVersionName(),
                    pluginConfig.getPackageName(),
                    pluginConfig.isEnable(),
                    warning
            );
            config.setLocked(plugin.isLocked());
            config.setIncompatible(plugin.isIncompatible());

            config.setOnEditClickListener(c -> {
                if (c.isIncompatible() || c.isLocked()) return;
                Intent intent = new Intent(requireContext(), VisualEditorActivity.class);
                intent.putExtra(VisualEditorActivity.EXTRA_PACKAGE_NAME, c.getPackageName());
                startActivity(intent);
            });

            config.setOnExportClickListener(c -> exportConfig(c));

            config.setOnDeleteClickListener(c -> {
                AppLogger.i(TAG, "Delete clicked for: " + c.getPackageName());
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除配置")
                        .setMessage("确定要删除配置 \"" + c.getName() + "\" 吗？该操作不可恢复。")
                        .setPositiveButton("删除", (dialog, which) -> {
                            manager.uninstall(c.getPackageName());
                            Toast.makeText(requireContext(), "已删除: " + c.getName(), Toast.LENGTH_SHORT).show();
                            refreshList();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            config.setOnCheckUpdateClickListener(c -> {
                if (c.isIncompatible()) return;
                AppLogger.i(TAG, "Check update clicked for: " + c.getPackageName());
                checkSingleUpdate(c);
            });

            config.setOnEnableChangeListener((c, isChecked) -> {
                if (c.isIncompatible()) return;
                AppLogger.i(TAG, "Enable toggle for " + c.getPackageName() + " -> " + isChecked);
                boolean ok = manager.setEnabled(c.getPackageName(), isChecked);
                if (ok) {
                    c.setEnabled(isChecked);
                    c.setWarning(isChecked ? null : "该配置已禁用");
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(requireContext(), "切换失败", Toast.LENGTH_SHORT).show();
                }
            });

            configList.add(config);
        }

        if (adapter == null) {
            adapter = new ConfigAdapter(configList);
            binding.recyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(configList);
        }

        AppLogger.i(TAG, "refreshList: " + configList.size() + " plugin(s) displayed");

        binding.swipeRefresh.setRefreshing(false);

        boolean empty = configList.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) {
            AppLogger.d(TAG, "File picker cancelled");
            return;
        }

        AppLogger.i(TAG, "File picked: " + uri.toString());

        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            if (is == null) {
                AppLogger.w(TAG, "Failed to open input stream for: " + uri);
                Toast.makeText(requireContext(), "无法读取文件", Toast.LENGTH_SHORT).show();
                return;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            byte[] data = bos.toByteArray();

            if (data == null || data.length == 0) {
                AppLogger.w(TAG, "Empty file picked: " + uri);
                Toast.makeText(requireContext(), "文件为空", Toast.LENGTH_SHORT).show();
                return;
            }

            AppLogger.d(TAG, "File content read, bytes=" + data.length);

            InstallSource source;
            if (LhrbReader.isLhrb(data)) {
                AppLogger.d(TAG, "Detected LHRB file, installing as LHRB");
                source = new InstallSource(null, data);
            } else {
                String json = new String(data, StandardCharsets.UTF_8);
                if (json.trim().isEmpty()) {
                    Toast.makeText(requireContext(), "文件为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                AppLogger.d(TAG, "Detected JSON file, installing as JSON");
                source = new InstallSource(json, null);
            }

            pendingSource = source;
            handleInstallResult(source.install(manager, false));
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to install plugin from file: " + uri, e);
            Toast.makeText(requireContext(), "安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleInstallResult(InstallResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                Toast.makeText(requireContext(), "安装成功: " + result.getNewVersionName(), Toast.LENGTH_SHORT).show();
                refreshList();
                break;
            case FORMAT_ERROR:
                String errMsg = "格式错误";
                if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                    errMsg = result.getErrors().get(0).getMessage();
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("校验失败")
                        .setMessage(errMsg)
                        .setPositiveButton("确定", null)
                        .show();
                break;
            case DOWNGRADE:
                if (registry.getBoolean(DataManage.Tag.APP_CONFIG_DOWNGRADE_INSTALLATION.getValue())) {
                    InstallResult overwriteResult = pendingSource.install(manager, true);
                    if (overwriteResult.isSuccess()) {
                        Toast.makeText(requireContext(), "覆盖安装成功: " + overwriteResult.getNewVersionName(), Toast.LENGTH_SHORT).show();
                        refreshList();
                    } else {
                        String downgradeErrMsg = "降级安装失败";
                        if (overwriteResult.getErrors() != null && !overwriteResult.getErrors().isEmpty()) {
                            downgradeErrMsg = overwriteResult.getErrors().get(0).getMessage();
                        }
                        Toast.makeText(requireContext(), downgradeErrMsg, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(requireContext(),
                            "已安装版本更新，无法降级 (已安装" + result.getInstalledVersionCode()
                                    + " ≥ " + result.getNewVersionCode() + ")", Toast.LENGTH_LONG).show();
                }
                break;
            case VERSION_EQUAL:
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("版本相同")
                        .setMessage("已安装相同版本" + result.getNewVersionName() + "，是否覆盖？")
                        .setPositiveButton("覆盖", (d, w) -> {
                            InstallResult overwriteResult = pendingSource.install(manager, true);
                            if (overwriteResult.isSuccess()) {
                                Toast.makeText(requireContext(), "覆盖成功", Toast.LENGTH_SHORT).show();
                                refreshList();
                            } else {
                                String overwriteErrMsg = "覆盖失败";
                                if (overwriteResult.getErrors() != null && !overwriteResult.getErrors().isEmpty()) {
                                    overwriteErrMsg = overwriteResult.getErrors().get(0).getMessage();
                                }
                                Toast.makeText(requireContext(), overwriteErrMsg, Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                break;
            case PACKAGE_MISMATCH:
                Toast.makeText(requireContext(), "包名不匹配", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private static class InstallSource {
        private final String json;
        private final byte[] lhrb;

        InstallSource(String json, byte[] lhrb) {
            this.json = json;
            this.lhrb = lhrb;
        }

        InstallResult install(ConfigPackageManager manager, boolean force) {
            if (lhrb != null) {
                return manager.installLhrb(lhrb, force);
            }
            return manager.install(json, force);
        }
    }

    /** 导出配置：仅 JSON 可导出；LHRB（locked）一律不导出。读取源文件原始字节。 */
    private void exportConfig(Config config) {
        if (config.isLocked()) {
            Toast.makeText(requireContext(), "只读配置（LHRB）不支持导出", Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] data = manager.getPluginRawFile(config.getPackageName());
        if (data == null || data.length == 0) {
            Toast.makeText(requireContext(), "导出失败：无法读取配置源文件", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(requireContext().getCacheDir(), "export");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("无法创建导出目录");
            }
            String base = config.getName() != null && !config.getName().trim().isEmpty()
                    ? config.getName().trim()
                    : config.getPackageName();
            String safeName = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("^\\.+|\\.+$", "");
            if (safeName.isEmpty()) {
                safeName = config.getPackageName();
            }
            File out = new File(dir, safeName + ".json");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(data);
            }
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", out);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "导出配置"));
        } catch (Exception e) {
            AppLogger.e(TAG, "export failed for " + config.getPackageName(), e);
            Toast.makeText(requireContext(), "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void createBlankTemplate() {        String json = "{\n" +
                "  \"plugin\": {\n" +
                "    \"config\": {\n" +
                "      \"name\": \"新配置\",\n" +
                "      \"package\": \"com.new.config." + System.currentTimeMillis() + "\",\n" +
                "      \"description\": \"\",\n" +
                "      \"author\": \"\",\n" +
                "      \"updateUrl\": \"\",\n" +
                "      \"versionName\": \"1.0.0\",\n" +
                "      \"versionCode\": 1,\n" +
                "      \"minEngine\": 4,\n" +
                "      \"enable\": true,\n" +
                "      \"keyHoldDuration\": 0\n" +
                "    },\n" +
                "    \"actionList\": []\n" +
                "  }\n" +
                "}";

        InstallResult result = manager.install(json);
        if (result.isSuccess()) {
            Toast.makeText(requireContext(), "空白模板创建成功", Toast.LENGTH_SHORT).show();
            refreshList();
        } else {
            String errMsg = "创建失败";
            if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                errMsg = result.getErrors().get(0).getMessage();
            }
            Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show();
        }
    }

    private void checkSingleUpdate(Config config) {
        Plugin plugin = manager.get(config.getPackageName());
        if (plugin == null) {
            Toast.makeText(requireContext(), "未找到配置", Toast.LENGTH_SHORT).show();
            return;
        }

        PluginConfig pluginConfig = plugin.getConfig();
        if (pluginConfig.getUpdateUrl() == null || pluginConfig.getUpdateUrl().isEmpty()) {
            Toast.makeText(requireContext(), "该配置未设置更新地址", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show();

        List<Plugin> singleList = new ArrayList<>();
        singleList.add(plugin);

        ConfigUpdateChecker.checkAll(singleList, manager.getTrustedFingerprints(), results -> {
            if (!isAdded() || requireActivity().isFinishing()) return;

            if (results.isEmpty()) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("检查更新")
                        .setMessage("检查失败，未获取到结果")
                        .setPositiveButton("确定", null)
                        .show();
                return;
            }

            ConfigUpdateChecker.UpdateResult result = results.get(0);

            if (result.hasError()) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("检查更新失败")
                        .setMessage(result.getError())
                        .setPositiveButton("确定", null)
                        .show();
                return;
            }

            if (!result.hasUpdate()) {
                ConfigUpdateDialogFragment dialog = ConfigUpdateDialogFragment.newInstance(
                        "检查更新", config.getName(), config.getDescription(), "",
                        result.getCurrentVersionName(), result.getCurrentVersionName(),
                        pluginConfig.getUpdateUrl(), false);
                dialog.show(getParentFragmentManager(), "config_update");
                return;
            }

            String localJson = manager.getPluginJson(config.getPackageName());
            List<ConfigDiffHelper.DiffLine> diff = ConfigDiffHelper.computeDiff(localJson, result.getRemoteJson());
            ConfigUpdateDialogFragment.ConfigUpdateDiffHolder.diff = diff;
            ConfigUpdateDialogFragment.ConfigUpdateDiffHolder.remoteJson = result.getRemoteJson();
            ConfigUpdateDialogFragment.ConfigUpdateDiffHolder.remoteLhrbBytes = result.getRemoteLhrbBytes();
            ConfigUpdateDialogFragment.setCallback(this);

            ConfigUpdateDialogFragment dialog = ConfigUpdateDialogFragment.newInstance(
                    "发现新版本", config.getName(), config.getDescription(),
                    result.getRemoteAuthor() != null ? result.getRemoteAuthor() : "",
                    result.getCurrentVersionName(), result.getRemoteVersionName(),
                    pluginConfig.getUpdateUrl(), true);
            dialog.show(getParentFragmentManager(), "config_update");
        });
    }

    @Override
    public void onUpdateConfirmed() {
        String remoteJson = ConfigUpdateDialogFragment.ConfigUpdateDiffHolder.remoteJson;
        byte[] remoteLhrbBytes = ConfigUpdateDialogFragment.ConfigUpdateDiffHolder.remoteLhrbBytes;
        if (remoteJson == null && remoteLhrbBytes == null) return;

        InstallResult ir;
        if (remoteLhrbBytes != null) {
            ir = manager.installLhrb(remoteLhrbBytes, true);
        } else {
            ir = manager.install(remoteJson, true);
        }
        if (ir.isSuccess()) {
            Toast.makeText(requireContext(), "更新成功: " + ir.getNewVersionName(), Toast.LENGTH_SHORT).show();
            refreshList();
        } else {
            String errMsg = "更新失败";
            if (ir.getErrors() != null && !ir.getErrors().isEmpty()) {
                errMsg = ir.getErrors().get(0).getMessage();
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("更新失败")
                    .setMessage(errMsg)
                    .setPositiveButton("确定", null)
                    .show();
        }
    }
}
