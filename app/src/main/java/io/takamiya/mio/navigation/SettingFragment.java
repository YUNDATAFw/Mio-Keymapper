package io.takamiya.mio.navigation;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import cn.panda.utils.library.DeviceInfoUtils;
import io.takamiya.mio.CategoryActivity;
import io.takamiya.mio.DeveloperActivity;
import io.takamiya.mio.R;
import io.takamiya.mio.SubscriptionActivity;
import io.takamiya.mio.databinding.FragmentSettingBinding;

public class SettingFragment extends Fragment {

    private FragmentSettingBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.tvVersion.setText("版本:" + DeviceInfoUtils.getAppVersionName(requireContext()));

        binding.settingBtnDeveloper.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DeveloperActivity.class)));

        binding.settingBtnSubscription.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SubscriptionActivity.class)));

        setupCategories();
    }

    private void setupCategories() {
        CategoryDef[] categories = {
                new CategoryDef("floating", R.drawable.material_symbols_arrow_circle_right_outline_rounded,
                        "悬浮球与面板", "悬浮球、功能面板与主题样式"),
                new CategoryDef("toast", R.drawable.material_symbols_notifications_outline_rounded,
                        "提示气泡", "新提示气泡的开关、颜色与位置"),
                new CategoryDef("perm", R.drawable.material_symbols_privacy_tip_outline_rounded,
                        "权限与服务", "系统权限、Shizuku与服务"),
                new CategoryDef("tools", R.drawable.material_symbols_build_outline_rounded,
                        "工具与日志", "开发者工具与应用日志"),
                new CategoryDef("data", R.drawable.material_symbols_data_array_rounded,
                        "数据", "备份与恢复配置数据"),
                new CategoryDef("more", R.drawable.material_symbols_list_rounded,
                        "更多", "更新、帮助、打赏与更多")
        };

        for (CategoryDef def : categories) {
            View card = getLayoutInflater().inflate(R.layout.item_setting_category, binding.layoutCategories, false);
            ((ImageView) card.findViewById(R.id.catIcon)).setImageResource(def.iconRes);
            ((TextView) card.findViewById(R.id.catTitle)).setText(def.title);
            ((TextView) card.findViewById(R.id.catSubtitle)).setText(def.subtitle);
            card.findViewById(R.id.catArrow).setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), CategoryActivity.class);
                intent.putExtra(CategoryActivity.EXTRA_CATEGORY, def.key);
                startActivity(intent);
            });
            binding.layoutCategories.addView(card);
        }
    }

    private static class CategoryDef {
        final String key;
        final int iconRes;
        final String title;
        final String subtitle;

        CategoryDef(String key, int iconRes, String title, String subtitle) {
            this.key = key;
            this.iconRes = iconRes;
            this.title = title;
            this.subtitle = subtitle;
        }
    }
}