package io.takamiya.mio.navigation;

import android.content.Intent;
import android.widget.LinearLayout;

import io.takamiya.mio.CheckUpdateActivity;
import io.takamiya.mio.ClearDataActivity;
import io.takamiya.mio.DonateActivity;
import io.takamiya.mio.HelpActivity;
import io.takamiya.mio.R;

public class MoreSettingsFragment extends RowSettingsFragment {

    @Override
    protected void populateRows(LinearLayout layoutRows) {
        addRow(layoutRows, R.drawable.material_symbols_cached_rounded,
                "检查更新", "保持最新版本，体验更好",
                v -> startActivity(new Intent(requireContext(), CheckUpdateActivity.class)));

        addRow(layoutRows, R.drawable.material_symbols_emoji_objects_outline_rounded,
                "帮助和提示", "遇到困惑？我们为您准备了答案",
                v -> startActivity(new Intent(requireContext(), HelpActivity.class)));

        addRow(layoutRows, R.drawable.material_symbols_emoji_food_beverage_outline_rounded,
                "打赏我们", "您可以请我们喝一杯咖啡",
                v -> startActivity(new Intent(requireContext(), DonateActivity.class)));

        addRow(layoutRows, R.drawable.material_symbols_delete_outline_rounded,
                "清空设置", "清除所有数据，恢复初始状态",
                v -> startActivity(new Intent(requireContext(), ClearDataActivity.class)));
    }
}