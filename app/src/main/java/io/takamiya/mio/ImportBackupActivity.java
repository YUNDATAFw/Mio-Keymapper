package io.takamiya.mio;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Iterator;

import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.databinding.ActivityImportBackupBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class ImportBackupActivity extends AppCompatActivity {

    private ActivityImportBackupBinding binding;

    private final ActivityResultLauncher<String[]> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::importFromUri
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImportBackupBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnImport.setOnClickListener(v -> {
            filePickerLauncher.launch(new String[]{"application/json"});
        });
    }

    private void importFromUri(Uri uri) {
        if (uri == null) return;

        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }

                JSONObject root = new JSONObject(sb.toString());
                int version = root.optInt("version", 0);
                if (version != 1) {
                    runOnUiThread(() -> Toast.makeText(this, "不支持的备份版本: " + version, Toast.LENGTH_SHORT).show());
                    return;
                }

                JSONObject data = root.getJSONObject("data");
                Registry reg = RegistryUtils.get();
                if (reg == null) {
                    runOnUiThread(() -> Toast.makeText(this, "注册表未初始化", Toast.LENGTH_SHORT).show());
                    return;
                }

                int restored = 0;
                int skipped = 0;

                Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        DataManage.Tag tag = DataManage.Tag.fromValue(key);
                        String strVal = data.getString(key);
                        switch (tag.getType()) {
                            case STRING:
                                reg.set(key, strVal);
                                break;
                            case INTEGER:
                                reg.set(key, Integer.parseInt(strVal));
                                break;
                            case BOOLEAN:
                                reg.set(key, Boolean.parseBoolean(strVal));
                                break;
                        }
                        restored++;
                    } catch (IllegalArgumentException e) {
                        skipped++;
                    }
                }

                reg.saveAsync();

                final int finalRestored = restored;
                final int finalSkipped = skipped;
                runOnUiThread(() -> {
                    String msg = "导入完成。共恢复 " + finalRestored + " 项";
                    if (finalSkipped > 0) {
                        msg += "，跳过 " + finalSkipped + " 项未知数据。";
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("导入结果")
                            .setMessage(msg)
                .setPositiveButton("确定", null)
                            .show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void applyWindowInsets(ActivityImportBackupBinding binding) {
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
