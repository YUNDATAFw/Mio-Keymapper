package io.takamiya.mio;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import io.takamiya.mio.databinding.ActivityCategoryBinding;
import io.takamiya.mio.navigation.DataSettingsFragment;
import io.takamiya.mio.navigation.FloatingSettingsFragment;
import io.takamiya.mio.navigation.MoreSettingsFragment;
import io.takamiya.mio.navigation.PermServiceSettingsFragment;
import io.takamiya.mio.navigation.ToastSettingsFragment;
import io.takamiya.mio.navigation.ToolsSettingsFragment;

public class CategoryActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY = "category";

    private ActivityCategoryBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCategoryBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        Fragment fragment = createFragment(category);
        binding.toolbar.setTitle(titleFor(category));

        getSupportFragmentManager().beginTransaction()
                .replace(binding.container.getId(), fragment)
                .commit();
    }

    private Fragment createFragment(String category) {
        if ("floating".equals(category)) {
            return new FloatingSettingsFragment();
        }
        if ("toast".equals(category)) {
            return new ToastSettingsFragment();
        }
        if ("perm".equals(category)) {
            return new PermServiceSettingsFragment();
        }
        if ("tools".equals(category)) {
            return new ToolsSettingsFragment();
        }
        if ("data".equals(category)) {
            return new DataSettingsFragment();
        }
        return new MoreSettingsFragment();
    }

    private String titleFor(String category) {
        if ("floating".equals(category)) {
            return "悬浮球与面板";
        }
        if ("toast".equals(category)) {
            return "提示气泡";
        }
        if ("perm".equals(category)) {
            return "权限与服务";
        }
        if ("tools".equals(category)) {
            return "工具与日志";
        }
        if ("data".equals(category)) {
            return "数据";
        }
        return "更多";
    }

    private void applyWindowInsets(ActivityCategoryBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.container.setPadding(
                    binding.container.getPaddingLeft(),
                    binding.container.getPaddingTop(),
                    binding.container.getPaddingRight(),
                    bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}