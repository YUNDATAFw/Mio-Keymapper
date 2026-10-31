package io.takamiya.mio.navigation;

import android.content.Intent;
import android.widget.LinearLayout;

import io.takamiya.mio.CrashLogActivity;
import io.takamiya.mio.DeveloperModeActivity;
import io.takamiya.mio.R;
import io.takamiya.mio.RunLogActivity;
import io.takamiya.mio.TerminalActivity;

public class ToolsSettingsFragment extends RowSettingsFragment {

    @Override
    protected void populateRows(LinearLayout layoutRows) {
        addRow(layoutRows, R.drawable.material_symbols_build_outline_rounded,
                "开发者模式", "为高级用户准备的调试工具",
                v -> startActivity(new Intent(requireContext(), DeveloperModeActivity.class)));

        addRow(layoutRows, R.drawable.material_symbols_ad,
                "命令终端", "执行Shell命令的便捷入口",
                v -> startActivity(new Intent(requireContext(), TerminalActivity.class)));

        addRow(layoutRows, R.drawable.material_symbols_data_table_outline_rounded,
                "App运行日志", "实时查看应用的运行状态",
                v -> startActivity(new Intent(requireContext(), RunLogActivity.class)));

        addRow(layoutRows, R.drawable.material_symbols_description_outline_rounded,
                "App崩溃日志", "记录每一次异常，便于排查问题",
                v -> startActivity(new Intent(requireContext(), CrashLogActivity.class)));
    }
}