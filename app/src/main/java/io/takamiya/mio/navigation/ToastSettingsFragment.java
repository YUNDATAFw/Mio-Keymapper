package io.takamiya.mio.navigation;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FragmentSettingToastBinding;
import io.takamiya.mio.floating.FloatingFeatureList;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class ToastSettingsFragment extends Fragment {

    private static final String TAG = "ToastSettings";

    private FragmentSettingToastBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingToastBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initUseNewToast();
        initActionToast();
        initNewToastColor();
        initNewToastPosition();
    }

    private void initUseNewToast() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            boolean enabled = false;
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_USE_NEW_TOAST.getValue())) {
                    enabled = reg.getBoolean(DataManage.Tag.APP_CONFIG_USE_NEW_TOAST.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load use new toast: " + e.getMessage());
            }
            binding.switchUseNewToast.setChecked(enabled);
        }
        binding.switchUseNewToast.setOnCheckedChangeListener((buttonView, isChecked) -> persistUseNewToast(isChecked));
    }

    private void persistUseNewToast(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_USE_NEW_TOAST.getValue(), enabled);
            reg.saveAsync();
            FloatingWindowService.applyNewToastLayout();
        } catch (Exception e) {
            AppLogger.e(TAG, "save use new toast failed: " + e.getMessage(), e);
        }
    }

    private void initActionToast() {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            boolean enabled = true;
            try {
                if (reg.exists(DataManage.Tag.APP_CONFIG_ACTION_TOAST_ENABLED.getValue())) {
                    enabled = reg.getBoolean(DataManage.Tag.APP_CONFIG_ACTION_TOAST_ENABLED.getValue());
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "failed to load action toast: " + e.getMessage());
            }
            binding.switchActionToast.setChecked(enabled);
        }
        binding.switchActionToast.setOnCheckedChangeListener((buttonView, isChecked) -> persistActionToast(isChecked));
    }

    private void persistActionToast(boolean enabled) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setBoolean(DataManage.Tag.APP_CONFIG_ACTION_TOAST_ENABLED.getValue(), enabled);
            reg.saveAsync();
            FloatingWindowService.applyActionToast(enabled);
        } catch (Exception e) {
            AppLogger.e(TAG, "save action toast failed: " + e.getMessage(), e);
        }
    }

    private void initNewToastColor() {
        updateNewToastColorSummary();
        View.OnClickListener open = v -> showNewToastColorDialog();
        binding.cardNewToastColor.setOnClickListener(open);
        binding.settingBtnNewToastColor.setOnClickListener(open);
    }

    private void updateNewToastColorSummary() {
        int color = FloatingFeatureList.savedNotifyColor(requireContext());
        if (color == ContextCompat.getColor(requireContext(), R.color.colorPrimary)) {
            binding.tvNewToastColorSummary.setText("跟随软件主题");
        } else {
            binding.tvNewToastColorSummary.setText(String.format("#%08X", color));
        }
    }

    private void showNewToastColorDialog() {
        final int[] palette = {
                0xFF000000, 0xFFFFFFFF, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0,
                0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
                0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFFFEB3B, 0xFFFF9800,
                0xFFFF5722, 0xFF795548, 0xFF607D8B, 0xFF37474F, 0xFFB0BEC5
        };
        int currentColor = FloatingFeatureList.savedNotifyColor(requireContext());
        GridLayout grid = new GridLayout(requireContext());
        grid.setColumnCount(5);
        int swatchSize = requireContext().getResources().getDimensionPixelSize(R.dimen.color_swatch_size);
        int spacing = requireContext().getResources().getDimensionPixelSize(R.dimen.color_swatch_spacing);
        for (int color : palette) {
            View swatch = new View(requireContext());
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            if (currentColor == color) {
                bg.setStroke(requireContext().getResources().getDimensionPixelSize(R.dimen.color_swatch_stroke), Color.WHITE);
            }
            swatch.setBackground(bg);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = swatchSize;
            lp.height = swatchSize;
            lp.setMargins(spacing, spacing, spacing, spacing);
            grid.addView(swatch, lp);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("新提示气泡颜色")
                .setView(grid)
                .setNegativeButton("跟随软件主题", (d, which) -> {
                    persistNewToastColor(null);
                    updateNewToastColorSummary();
                })
                .show();
        for (int i = 0; i < grid.getChildCount(); i++) {
            final int color = palette[i];
            grid.getChildAt(i).setOnClickListener(v -> {
                persistNewToastColor(color);
                updateNewToastColorSummary();
                dialog.dismiss();
            });
        }
    }

    private void persistNewToastColor(Integer color) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setInt(DataManage.Tag.APP_CONFIG_NEW_TOAST_COLOR.getValue(),
                    color != null ? color : FloatingFeatureList.NEW_TOAST_COLOR_DEFAULT);
            reg.saveAsync();
            FloatingWindowService.applyNotifyColor();
        } catch (Exception e) {
            AppLogger.e(TAG, "save new toast color failed: " + e.getMessage(), e);
        }
    }

    private void initNewToastPosition() {
        updateNewToastPositionSummary();
        View.OnClickListener open = v -> showNewToastPositionDialog();
        binding.cardNewToastPosition.setOnClickListener(open);
        binding.settingBtnNewToastPosition.setOnClickListener(open);
    }

    private void updateNewToastPositionSummary() {
        binding.tvNewToastPositionSummary.setText(
                FloatingFeatureList.savedNewToastPosition() == 1 ? "顶部" : "底部");
    }

    private void showNewToastPositionDialog() {
        int current = FloatingFeatureList.savedNewToastPosition();
        String[] items = {"底部", "顶部"};
        int checked = current == 1 ? 1 : 0;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("气泡显示位置")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    persistNewToastPosition(which);
                    binding.tvNewToastPositionSummary.setText(which == 1 ? "顶部" : "底部");
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void persistNewToastPosition(int position) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.setInt(DataManage.Tag.APP_CONFIG_NEW_TOAST_POSITION.getValue(), position);
            reg.saveAsync();
            FloatingWindowService.applyNotifyStyle();
        } catch (Exception e) {
            AppLogger.e(TAG, "save new toast position failed: " + e.getMessage(), e);
        }
    }
}
