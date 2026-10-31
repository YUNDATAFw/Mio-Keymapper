package io.takamiya.mio.navigation;

import android.content.Intent;
import android.widget.LinearLayout;

import io.takamiya.mio.BackupActivity;
import io.takamiya.mio.ImportBackupActivity;
import io.takamiya.mio.R;

public class DataSettingsFragment extends RowSettingsFragment {

    @Override
    protected void populateRows(LinearLayout layoutRows) {
        addRow(layoutRows, R.drawable.material_symbols_cached_rounded,
                "备份设置", "为您的配置数据留一份备份",
                v -> startActivity(new Intent(requireContext(), BackupActivity.class)));

        addRow(layoutRows, R.drawable.material_symbols_cached_rounded,
                "导入备份", "从备份中恢复您的配置数据",
                v -> startActivity(new Intent(requireContext(), ImportBackupActivity.class)));
    }
}