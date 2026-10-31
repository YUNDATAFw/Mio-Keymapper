package io.takamiya.mio;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.adapter.RunLogAdapter;
import io.takamiya.mio.databinding.ActivityRunLogBinding;

public class RunLogActivity extends AppCompatActivity {

    private ActivityRunLogBinding binding;
    private RunLogAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRunLogBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new RunLogAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.refreshLayout.setOnRefreshListener(this::loadLogs);
        binding.refreshLayout.setColorSchemeResources(R.color.colorPrimary);

        binding.fabExport.setOnClickListener(v -> exportLogs());

        loadLogs();
    }

    private void loadLogs() {
        binding.tvEmpty.setVisibility(View.GONE);
        binding.fabExport.setVisibility(View.GONE);
        new Thread(() -> {
            List<AppLogger.LogEntry> logs = AppLogger.getInstance().getLogs();
            Collections.reverse(logs);
            runOnUiThread(() -> {
                adapter.setData(logs);
                binding.refreshLayout.setRefreshing(false);
                boolean empty = logs == null || logs.isEmpty();
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.fabExport.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    private void exportLogs() {
        List<AppLogger.LogEntry> logs = adapter.getLogs();
        if (logs == null || logs.isEmpty()) return;

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (AppLogger.LogEntry entry : logs) {
                sb.append(RunLogAdapter.TIME_FORMAT.format(new Date(entry.timestamp)))
                  .append(" [").append(getLevelLabel(entry.level)).append("]")
                  .append(" [").append(entry.tag).append("]")
                  .append(" ").append(entry.message);
                if (entry.throwable != null && !entry.throwable.isEmpty()) {
                    sb.append("\n").append(entry.throwable);
                }
                sb.append("\n");
            }

            try {
                File logDir = new File(getCacheDir(), "logs");
                logDir.mkdirs();
                String fileName = "mio_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
                File logFile = new File(logDir, fileName);
                try (FileWriter fw = new FileWriter(logFile)) {
                    fw.write(sb.toString());
                }

                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                            this, getPackageName() + ".fileprovider", logFile));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "导出日志"));
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String getLevelLabel(int level) {
        if (level >= 0 && level < RunLogAdapter.LEVEL_LABELS.length) {
            return RunLogAdapter.LEVEL_LABELS[level];
        }
        return "?";
    }

    private void applyWindowInsets(ActivityRunLogBinding binding) {
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
