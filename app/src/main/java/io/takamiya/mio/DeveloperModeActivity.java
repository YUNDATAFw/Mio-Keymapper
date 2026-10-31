package io.takamiya.mio;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.databinding.ActivityDeveloperModeBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class DeveloperModeActivity extends AppCompatActivity {

    private static final String TAG = "DeveloperMode";
    private ActivityDeveloperModeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeveloperModeBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        Registry reg = RegistryUtils.get();
        boolean enabled = reg != null && reg.getBoolean(DataManage.Tag.APP_CONFIG_DEVELOPER_MODE.getValue());
        binding.switchDevMode.setChecked(enabled);
        updateVisibility(enabled);

        binding.switchDevMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (reg != null) {
                reg.set(DataManage.Tag.APP_CONFIG_DEVELOPER_MODE.getValue(), isChecked);
                reg.saveAsync();
            }
            updateVisibility(isChecked);
        });

        if (enabled) loadView(reg);
    }

    private void loadView(Registry reg) {
        binding.btnCrash.setOnClickListener(v -> {
            AppLogger.e(TAG, "测试手动崩溃");
            throw new RuntimeException("测试手动崩溃");
        });

        binding.switchDowngradeInstallation.setChecked(reg.getBoolean(DataManage.Tag.APP_CONFIG_DOWNGRADE_INSTALLATION.getValue()));
        binding.switchDowngradeInstallation.setOnCheckedChangeListener((view, isChecked) -> {
            reg.set(DataManage.Tag.APP_CONFIG_DOWNGRADE_INSTALLATION.getValue(), isChecked);
            reg.saveAsync();
        });
    }

    private void updateVisibility(boolean enabled) {
        binding.devContect.setVisibility(enabled ? View.VISIBLE : View.GONE);
        binding.layoutDevModeDisabled.setVisibility(enabled ? View.GONE : View.VISIBLE);
        binding.btnCrash.setEnabled(enabled);
        binding.switchDowngradeInstallation.setEnabled(enabled);
    }

    private void applyWindowInsets(ActivityDeveloperModeBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.content.setPadding(
                    binding.content.getPaddingLeft(),
                    binding.content.getPaddingTop(),
                    binding.content.getPaddingRight(),
                    bars.bottom);
            return insets;
        });
    }
}
