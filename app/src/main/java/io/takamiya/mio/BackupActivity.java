package io.takamiya.mio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.databinding.ActivityBackupBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class BackupActivity extends AppCompatActivity {

    private ActivityBackupBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBackupBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnExport.setOnClickListener(v -> showExportDialog());
    }

    private void showExportDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("导出备份")
                .setSingleChoiceItems(new String[]{"仅导出非敏感数据", "导出全部数据（含敏感信息）"}, 0, null)
                .setPositiveButton("导出", (d, w) -> {
                    int selected = ((androidx.appcompat.app.AlertDialog) d).getListView().getCheckedItemPosition();
                    exportBackup(selected == 0);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportBackup(boolean excludeSensitive) {
        Registry reg = RegistryUtils.get();
        if (reg == null) {
            Toast.makeText(this, "注册表未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                JSONObject data = new JSONObject();
                List<String> skipped = new ArrayList<>();

                for (DataManage.Tag tag : DataManage.Tag.getAllTags()) {
                    String key = tag.getValue();
                    if (key.isEmpty() || !reg.exists(key)) continue;
                    if (excludeSensitive && tag.isSensitive()) {
                        skipped.add(key);
                        continue;
                    }
                    switch (tag.getType()) {
                        case STRING:
                            data.put(key, reg.getString(key));
                            break;
                        case INTEGER:
                            data.put(key, reg.getInt(key));
                            break;
                        case BOOLEAN:
                            data.put(key, reg.getBoolean(key));
                            break;
                    }
                }

                JSONObject root = new JSONObject();
                root.put("version", 1);
                root.put("export_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                root.put("data", data);

                File backupDir = new File(getCacheDir(), "backup");
                if (backupDir.exists()) {
                    for (File f : backupDir.listFiles()) {
                        if (f.isFile()) f.delete();
                    }
                }
                backupDir.mkdirs();

                String fileName = "mio_backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json";
                File backupFile = new File(backupDir, fileName);
                try (FileWriter fw = new FileWriter(backupFile)) {
                    fw.write(root.toString(4));
                }

                runOnUiThread(() -> {
                    String summary = "备份完成。";
                    if (!skipped.isEmpty()) {
                        summary += "\n已跳过 " + skipped.size() + " 项敏感数据。";
                    }
                    Toast.makeText(this, summary, Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/json");
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", backupFile);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "分享备份"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void applyWindowInsets(ActivityBackupBinding binding) {
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
