package io.takamiya.mio.navigation;

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

import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FragmentSettingSimpleBinding;

public abstract class RowSettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return FragmentSettingSimpleBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentSettingSimpleBinding binding = FragmentSettingSimpleBinding.bind(view);
        populateRows(binding.layoutRows);
    }

    protected abstract void populateRows(LinearLayout layoutRows);

    protected final void addRow(LinearLayout layoutRows, int iconRes, String title, String subtitle,
                                View.OnClickListener listener) {
        View row = getLayoutInflater().inflate(R.layout.item_setting_row, layoutRows, false);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        TextView subtitleView = row.findViewById(R.id.rowSubtitle);
        if (subtitle == null || subtitle.isEmpty()) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
        }
        if (listener != null) {
            row.findViewById(R.id.rowArrow).setOnClickListener(listener);
        }
        layoutRows.addView(row);
    }

    /**
     * 带开关的设置行：隐藏箭头，行尾放一个 MaterialSwitch。
     * 注意开关必须挂到 CardView 内层的 LinearLayout（child 0），
     * 直接加到 CardView 会与内容层叠。
     */
    protected final com.google.android.material.materialswitch.MaterialSwitch addSwitchRow(
            LinearLayout layoutRows, int iconRes, String title, String subtitle,
            boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        View row = getLayoutInflater().inflate(R.layout.item_setting_row, layoutRows, false);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        TextView subtitleView = row.findViewById(R.id.rowSubtitle);
        if (subtitle == null || subtitle.isEmpty()) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
        }
        row.findViewById(R.id.rowArrow).setVisibility(View.GONE);
        com.google.android.material.materialswitch.MaterialSwitch switchView =
                new com.google.android.material.materialswitch.MaterialSwitch(requireContext());
        switchView.setChecked(checked);
        if (listener != null) {
            switchView.setOnCheckedChangeListener(listener);
        }
        ((ViewGroup) ((ViewGroup) row).getChildAt(0)).addView(switchView);
        layoutRows.addView(row);
        return switchView;
    }
}