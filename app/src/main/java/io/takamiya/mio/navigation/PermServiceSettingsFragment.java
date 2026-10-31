package io.takamiya.mio.navigation;

import android.content.Intent;
import android.provider.Settings;
import android.widget.LinearLayout;

import androidx.fragment.app.Fragment;

import io.takamiya.mio.PermissionActivity;
import io.takamiya.mio.R;
import io.takamiya.mio.SecurityActivity;
import io.takamiya.mio.ShizukuManageActivity;

public class PermServiceSettingsFragment extends RowSettingsFragment {

    @Override
    protected void populateRows(LinearLayout layoutRows) {
        addRow(layoutRows, R.drawable.material_symbols_permdevice_information_outline_rounded,
                "权限管理", "查看和管理应用所需的各项权限",
                v -> startActivity(new Intent(requireContext(), PermissionActivity.class)));

        addRow(layoutRows, R.drawable.shizuku_svgrepo_com,
                "Shizuku授权", "管理Shizuku服务的授权状态",
                v -> startActivity(new Intent(requireContext(), ShizukuManageActivity.class)));

        addRow(layoutRows, R.drawable.material_symbols_keyboard_outline_rounded,
                "键盘管理", "打开系统键盘设置",
                v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        // 外接键鼠监听：随悬浮窗服务启动 Shizuku getevent 监听，可整体关闭
        final io.takamiya.mio.monitor.InputMonitorEngine engine =
                io.takamiya.mio.monitor.InputMonitorEngine.getInstance();
        addSwitchRow(layoutRows, R.drawable.material_symbols_keyboard_outline_rounded,
                "外接键鼠监听", "随悬浮窗服务监听外接键盘鼠标按键（Shizuku）",
                engine.isPrefEnabled(),
                (buttonView, isChecked) -> {
                    engine.setPrefEnabled(isChecked);
                    if (isChecked) {
                        if (io.takamiya.mio.floating.FloatingWindowService.isRunning()) {
                            engine.start();
                        }
                    } else {
                        engine.stop();
                    }
                });

        addRow(layoutRows, R.drawable.material_symbols_security_rounded,
                "安全服务", "守护您的使用安全",
                v -> startActivity(new Intent(requireContext(), SecurityActivity.class)));
    }
}