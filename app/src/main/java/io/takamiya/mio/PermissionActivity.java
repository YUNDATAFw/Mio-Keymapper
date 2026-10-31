package io.takamiya.mio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.takamiya.mio.databinding.ActivityPermissionBinding;

public class PermissionActivity extends AppCompatActivity {

    private ActivityPermissionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPermissionBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.swipeRefresh.setColorSchemeResources(
                com.google.android.material.R.color.design_default_color_primary);
        binding.swipeRefresh.setOnRefreshListener(this::checkPermissions);

        binding.btnOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        binding.btnAccessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        binding.btnBatteryOptimization.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void checkPermissions() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean accessibility = isAccessibilityServiceEnabled();
        boolean battery = isIgnoringBatteryOptimizations();

        updateButton(binding.btnOverlay, overlay);
        updateButton(binding.btnAccessibility, accessibility);
        updateButton(binding.btnBatteryOptimization, battery);

        binding.swipeRefresh.setRefreshing(false);
    }

    private void updateButton(android.widget.Button button, boolean granted) {
        if (granted) {
            button.setText("已授予");
            button.setEnabled(false);
        } else {
            button.setText("授权");
            button.setEnabled(true);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        String packageName = getPackageName();
        for (String service : enabledServices.split(":")) {
            if (service.startsWith(packageName + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void applyWindowInsets(ActivityPermissionBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.swipeRefresh.setProgressViewOffset(false,
                    binding.swipeRefresh.getProgressViewStartOffset(),
                    binding.swipeRefresh.getProgressViewEndOffset() + bars.top);
            binding.content.setPadding(
                    binding.content.getPaddingLeft(),
                    binding.content.getPaddingTop(),
                    binding.content.getPaddingRight(),
                    bars.bottom);
            return insets;
        });
    }
}
