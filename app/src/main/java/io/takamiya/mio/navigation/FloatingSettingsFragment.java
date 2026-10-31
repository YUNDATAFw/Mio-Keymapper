package io.takamiya.mio.navigation;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FragmentSettingFloatingBinding;
import io.takamiya.mio.floating.FloatingFeatureList;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;
import io.takamiya.mio.utils.ThemeManager;

public class FloatingSettingsFragment extends Fragment {

    private static final String TAG = "FloatingSettings";

    private FragmentSettingFloatingBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingFloatingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initSnapToEdgeSwitch();
        initPanelPortrait();
        initAutoOpenGame();
        initImportBuilding();
        initThemeMode();
        initFeatureListStyle();
        initFeatureListEngine();
        initFeatureListWatermark();
    }

    private void initSnapToEdgeSwitch() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            boolean snapToEdge = true;
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_BALL_SNAP_TO_EDGE.getValue())) {
                    snapToEdge = reg.getBoolean(DataManage.Tag.APP_CONFIG_FLOAT_BALL_SNAP_TO_EDGE.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load snap to edge: " + e.getMessage());
            }
            binding.switchSnapToEdge.setChecked(snapToEdge);
        }
        binding.switchSnapToEdge.setOnCheckedChangeListener((buttonView, isChecked) -> persistSnapToEdge(isChecked));
    }

    private void persistSnapToEdge(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_FLOAT_BALL_SNAP_TO_EDGE.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save snap to edge failed: " + e.getMessage(), e);
        }
    }

    private void initPanelPortrait() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            boolean enabled = false;
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_PANEL_PORTRAIT.getValue())) {
                    enabled = reg.getBoolean(DataManage.Tag.APP_CONFIG_PANEL_PORTRAIT.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load panel portrait: " + e.getMessage());
            }
            binding.switchPanelPortrait.setChecked(enabled);
        }
        binding.switchPanelPortrait.setOnCheckedChangeListener((buttonView, isChecked) -> persistPanelPortrait(isChecked));
    }

    private void persistPanelPortrait(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_PANEL_PORTRAIT.getValue(), enabled);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save panel portrait failed: " + e.getMessage(), e);
        }
    }

    private void initImportBuilding() {
        View.OnClickListener open = v -> {
            if (!FloatingWindowService.isRunning()) {
                Toast.makeText(requireContext(), "请先启动悬浮窗服务", Toast.LENGTH_SHORT).show();
                return;
            }
            FloatingWindowService.openImportBuildingPanel();
        };
        binding.cardImportBuilding.setOnClickListener(open);
        binding.settingBtnImportBuilding.setOnClickListener(open);

        View.OnClickListener openList = v -> {
            Intent intent = new Intent(requireContext(), io.takamiya.mio.HomeActivity.class);
            intent.putExtra("navigate_to", "building_list");
            startActivity(intent);
        };
        binding.cardBuildingList.setOnClickListener(openList);
        binding.settingBtnBuildingList.setOnClickListener(openList);
    }

    private void initAutoOpenGame() {
        Registry reg = RegistryUtils.get();
        boolean enabled = false;
        if (reg != null) {
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME.getValue())) {
                    enabled = reg.getBoolean(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load auto open game: " + e.getMessage());
            }
            binding.switchAutoOpenGame.setChecked(enabled);
        }
        updateAutoOpenGameSummary();
        binding.switchAutoOpenGame.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                showAutoOpenGameDialog();
            } else {
                persistAutoOpenGame(false, autoOpenGamePackage());
            }
        });
    }

    private void showAutoOpenGameDialog() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_auto_open_game, null);
        EditText edit = view.findViewById(R.id.edit_game_package);
        String pkg = autoOpenGamePackage();
        if (!pkg.isEmpty()) {
            edit.setText(pkg);
            edit.setSelection(edit.getText().length());
        }
        view.findViewById(R.id.btn_preset_international).setOnClickListener(v ->
                fillGamePackage(edit, "com.mojang.minecraftpe"));
        view.findViewById(R.id.btn_preset_netease).setOnClickListener(v ->
                fillGamePackage(edit, "com.netease.x19"));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("自动打开游戏")
                .setView(view)
                .setPositiveButton("确定", (dialog, which) -> {
                    String input = edit.getText().toString().trim();
                    if (input.isEmpty()) {
                        binding.switchAutoOpenGame.setChecked(false);
                        return;
                    }
                    if (requireContext().getPackageManager().getLaunchIntentForPackage(input) == null) {
                        Toast.makeText(requireContext(), "未检测到已安装的应用，请检查包名", Toast.LENGTH_SHORT).show();
                        binding.switchAutoOpenGame.setChecked(false);
                        return;
                    }
                    persistAutoOpenGame(true, input);
                    if (FloatingWindowService.isRunning()) {
                        FloatingWindowService.launchGame(requireContext());
                    }
                })
                .setNegativeButton("取消", null)
                .setOnDismissListener(d -> {
                    if (!isAutoOpenGameEnabled()) {
                        binding.switchAutoOpenGame.setChecked(false);
                    }
                })
                .show();
    }

    private void fillGamePackage(EditText edit, String pkg) {
        edit.setText(pkg);
        edit.setSelection(pkg.length());
    }

    private boolean isAutoOpenGameEnabled() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return false;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME.getValue())) {
                return reg.getBoolean(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME.getValue());
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load auto open game: " + e.getMessage());
        }
        return false;
    }

    private String autoOpenGamePackage() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return "";
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME_PACKAGE.getValue())) {
                return reg.getString(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME_PACKAGE.getValue());
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load auto open game package: " + e.getMessage());
        }
        return "";
    }

    private void persistAutoOpenGame(boolean enabled, String pkg) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME.getValue(), enabled);
            reg.set(DataManage.Tag.APP_CONFIG_AUTO_OPEN_GAME_PACKAGE.getValue(), pkg);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save auto open game failed: " + e.getMessage(), e);
        }
        updateAutoOpenGameSummary();
    }

    private void updateAutoOpenGameSummary() {
        if (binding.tvAutoOpenGameSummary == null) return;
        if (isAutoOpenGameEnabled()) {
            binding.tvAutoOpenGameSummary.setText("已配置: " + autoOpenGamePackage());
        } else {
            binding.tvAutoOpenGameSummary.setText("");
        }
    }

    private void initThemeMode() {
        int mode = ThemeManager.getMode(RegistryUtils.get());
        binding.tvThemeModeSummary.setText(themeModeLabel(mode));

        View.OnClickListener open = v -> showThemeModeDialog();
        binding.cardThemeMode.setOnClickListener(open);
        binding.settingBtnThemeMode.setOnClickListener(open);
    }

    private void showThemeModeDialog() {
        int current = ThemeManager.getMode(RegistryUtils.get());
        String[] items = {"跟随系统", "暗色模式", "浅色模式"};
        int checked = current == ThemeManager.MODE_DARK ? 1
                : current == ThemeManager.MODE_LIGHT ? 2 : 0;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("主题模式")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    int mode;
                    if (which == 1) {
                        mode = ThemeManager.MODE_DARK;
                    } else if (which == 2) {
                        mode = ThemeManager.MODE_LIGHT;
                    } else {
                        mode = ThemeManager.MODE_SYSTEM;
                    }
                    ThemeManager.setMode(mode);
                    binding.tvThemeModeSummary.setText(themeModeLabel(mode));
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String themeModeLabel(int mode) {
        if (mode == ThemeManager.MODE_DARK) {
            return "暗色模式";
        }
        if (mode == ThemeManager.MODE_LIGHT) {
            return "浅色模式";
        }
        return "跟随系统";
    }

    private void initFeatureListStyle() {
        int style = FloatingFeatureList.savedDisplayStyle();
        binding.tvFeatureListStyleSummary.setText(featureListStyleLabel(style));

        View.OnClickListener open = v -> showFeatureListStyleDialog();
        binding.cardFeatureListStyle.setOnClickListener(open);
        binding.settingBtnFeatureListStyle.setOnClickListener(open);
    }

    private void showFeatureListStyleDialog() {
        int current = FloatingFeatureList.savedDisplayStyle();
        String[] items = {"轮廓描边", "右侧竖条", "分隔条", "纯文本"};
        int checked = Math.max(0, Math.min(items.length - 1, current));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("功能列表样式")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    persistFeatureListStyle(which);
                    binding.tvFeatureListStyleSummary.setText(featureListStyleLabel(which));
                    FloatingWindowService.applyFeatureListDisplayStyle();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void persistFeatureListStyle(int style) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setInt(DataManage.Tag.APP_CONFIG_FLOAT_FEATURE_LIST_STYLE.getValue(), style);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list style failed: " + e.getMessage(), e);
        }
    }

    private String featureListStyleLabel(int style) {
        switch (style) {
            case 0:
        return "圆角描边";
            case 1:
                return "右侧竖条";
            case 3:
                return "纯文本";
            default:
                return "分隔符";
        }
    }

    private void initFeatureListEngine() {
        binding.tvFeatureListEngineSummary.setText(featureListEngineLabel());

        View.OnClickListener open = v -> showFeatureListEngineDialog();
        binding.cardFeatureListEngine.setOnClickListener(open);
        binding.settingBtnFeatureListEngine.setOnClickListener(open);
    }

    private void showFeatureListEngineDialog() {
        boolean useNew = FloatingFeatureList.savedUseNewFeatureList();
        String[] items = {"新版 (GL 渲染)", "旧版 (传统视图)"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("功能列表渲染引擎")
                .setSingleChoiceItems(items, useNew ? 0 : 1, (dialog, which) -> {
                    persistFeatureListEngine(which == 0);
                    binding.tvFeatureListEngineSummary.setText(which == 0 ? items[0] : items[1]);
                    FloatingWindowService.applyFeatureListEngine();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void persistFeatureListEngine(boolean useNew) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_USE_NEW_FEATURE_LIST.getValue(), useNew);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list engine failed: " + e.getMessage(), e);
        }
    }

    private String featureListEngineLabel() {
        return FloatingFeatureList.savedUseNewFeatureList() ? "新版 (GL 渲染)" : "旧版 (传统视图)";
    }

    private void initFeatureListWatermark() {
        updateFeatureListWatermarkSummary();
        View.OnClickListener open = v -> showFeatureListWatermarkDialog();
        binding.cardFeatureListWatermark.setOnClickListener(open);
        binding.settingBtnFeatureListWatermark.setOnClickListener(open);
    }

    private void updateFeatureListWatermarkSummary() {
        binding.tvFeatureListWatermarkSummary.setText(FloatingFeatureList.savedFeatureListWatermarkText());
    }

    private void showFeatureListWatermarkDialog() {
        String defaultText = "Mio";
        String current = FloatingFeatureList.savedFeatureListWatermarkText();
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_text, null);
        EditText edit = view.findViewById(R.id.editInput);
        edit.setHint(defaultText);
        edit.setSingleLine();
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_NORMAL);
        edit.setText(current);
        edit.setSelection(edit.getText().length());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("功能列表水印")
                .setView(view)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = edit.getText().toString().trim();
                    if (!text.isEmpty() && !text.matches("[a-zA-Z0-9_-]+")) {
                        Toast.makeText(requireContext(), "仅支持英文字母、数字和下划线", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    persistFeatureListWatermarkText(text.isEmpty() ? "Mio" : text);
                    updateFeatureListWatermarkSummary();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void persistFeatureListWatermarkText(String text) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            String defaultValue = getString(R.string.app_name);
            reg.set(DataManage.Tag.APP_CONFIG_FEATURE_LIST_WATERMARK_TEXT.getValue(),
                    text.isEmpty() ? defaultValue : text);
            reg.saveAsync();
            FloatingWindowService.applyFeatureListDisplayStyle();
        } catch (Exception e) {
            AppLogger.e(TAG, "save feature list watermark text failed: " + e.getMessage(), e);
        }
    }
}
