package io.takamiya.mio;

import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.panda.utils.library.AppLogger;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse;
import io.takamiya.mio.configManage.ConfigPackageManager;
import io.takamiya.mio.configManage.InstallResult;
import io.takamiya.mio.databinding.ActivityConfigEditBinding;

public class ConfigEditActivity extends AppCompatActivity {

    private static final String TAG = "ConfigEditActivity";

    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    private ActivityConfigEditBinding binding;
    private ConfigPackageManager manager;
    private String packageName;
    private boolean hasUnsavedChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConfigEditBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        manager = new ConfigPackageManager(this);

        binding.toolbar.setNavigationOnClickListener(v -> confirmAndFinish());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName != null) {
            binding.toolbar.setSubtitle(packageName);
            loadConfig();
        } else {
            binding.toolbar.setSubtitle("新建配置");
        }

        int nightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            binding.editor.setColorScheme(new SchemeDarcula());
        } else {
            binding.editor.setColorScheme(new SchemeEclipse());
        }

        binding.editor.setTypefaceText(Typeface.MONOSPACE);
        EditorColorScheme scheme = binding.editor.getColorScheme();
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, getColor(R.color.colorBackground));

        binding.editor.subscribeEvent(ContentChangeEvent.class, (event, source) -> {
            if (event.getAction() == ContentChangeEvent.ACTION_INSERT
                    || event.getAction() == ContentChangeEvent.ACTION_DELETE) {
                hasUnsavedChanges = true;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmAndFinish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.editor.release();
    }

    private void loadConfig() {
        AppLogger.i(TAG, "loadConfig: package=" + packageName);
        String json = manager.getPluginJson(packageName);
        if (json != null) {
            binding.editor.setText(json);
            AppLogger.d(TAG, "loadConfig: loaded " + json.length() + " chars");
        } else {
            AppLogger.w(TAG, "loadConfig: plugin not found: " + packageName);
            Toast.makeText(this, "配置不存在", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmAndFinish() {
        if (!hasUnsavedChanges) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("未保存的更改")
                .setMessage("当前有未保存的更改，确定要退出吗？")
                .setPositiveButton("保存并退出", (d, w) -> saveConfig())
                .setNeutralButton("直接退出", (d, w) -> finish())
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            saveConfig();
            return true;
        }
        return false;
    }

    private void saveConfig() {
        String json = binding.editor.getText().toString();
        if (json == null || json.trim().isEmpty()) {
            AppLogger.w(TAG, "saveConfig: editor content is empty");
            Toast.makeText(this, "内容为空", Toast.LENGTH_SHORT).show();
            return;
        }

        AppLogger.i(TAG, "saveConfig: saving " + json.length() + " chars");

        InstallResult result = manager.install(json, true);
        switch (result.getStatus()) {
            case SUCCESS:
                AppLogger.i(TAG, "saveConfig: SUCCESS");
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                hasUnsavedChanges = false;
                finish();
                break;
            case FORMAT_ERROR:
                String errMsg = "JSON格式错误";
                if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                    errMsg = result.getErrors().get(0).getMessage();
                }
                AppLogger.w(TAG, "saveConfig: FORMAT_ERROR - " + errMsg);
                Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                break;
            default:
                AppLogger.w(TAG, "saveConfig: unexpected status " + result.getStatus());
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void applyWindowInsets() {
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
