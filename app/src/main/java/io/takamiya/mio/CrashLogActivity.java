package io.takamiya.mio;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.takamiya.mio.adapter.CrashLogAdapter;
import io.takamiya.mio.bean.CrashLogEntry;
import io.takamiya.mio.databinding.ActivityCrashLogBinding;

public class CrashLogActivity extends AppCompatActivity {

    private ActivityCrashLogBinding binding;
    private CrashLogAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCrashLogBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new CrashLogAdapter();
        adapter.setOnItemActionListener(new CrashLogAdapter.OnItemActionListener() {
            @Override
            public void onDelete(int position) {
                deleteCrashLog(position);
            }

            @Override
            public void onShare(int position) {
                shareCrashLog(position);
            }

            @Override
            public void onViewDetail(int position) {
                showCrashDetail(position);
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.refreshLayout.setOnRefreshListener(this::loadCrashLogs);
        binding.refreshLayout.setColorSchemeResources(R.color.colorPrimary);

        binding.fabExport.setOnClickListener(v -> exportAllCrashLogs());

        loadCrashLogs();
    }

    private void loadCrashLogs() {
        binding.tvEmpty.setVisibility(View.GONE);
        binding.fabExport.setVisibility(View.GONE);
        new Thread(() -> {
            List<CrashLogEntry> entries = new ArrayList<>();
            File crashDir = new File(getCacheDir(), "crash");
            File[] files = crashDir.listFiles((dir, name) ->
                    name.endsWith(".txt") || name.endsWith(".log"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File file : files) {
                    CrashLogEntry entry = parseCrashFile(file);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            runOnUiThread(() -> {
                adapter.setData(entries);
                binding.refreshLayout.setRefreshing(false);
                boolean empty = entries.isEmpty();
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.fabExport.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    private CrashLogEntry parseCrashFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String fullContent;
            try {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                fullContent = sb.toString();
            } catch (IOException e) {
                return null;
            }

            String crashTime = "";
            String exceptionMsg = "";
            String[] lines = fullContent.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i];
                if (l.startsWith("Crash Time: ")) {
                    crashTime = l.substring("Crash Time: ".length()).trim();
                } else if (l.trim().equals("Crash Stack Trace:") && i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    if (!nextLine.isEmpty()) {
                        exceptionMsg = nextLine;
                        if (i + 2 < lines.length) {
                            String thirdLine = lines[i + 2].trim();
                            if (thirdLine.startsWith("at ")) {
                                exceptionMsg += " " + thirdLine;
                            }
                        }
                    }
                }
            }
            return new CrashLogEntry(file, crashTime, exceptionMsg, fullContent);
        } catch (IOException e) {
            return null;
        }
    }

    private void deleteCrashLog(int position) {
        CrashLogEntry entry = adapter.getData().get(position);
        if (entry.file.delete()) {
            List<CrashLogEntry> data = new ArrayList<>(adapter.getData());
            data.remove(position);
            adapter.setData(data);
            boolean empty = data.isEmpty();
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.fabExport.setVisibility(empty ? View.GONE : View.VISIBLE);
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCrashLog(int position) {
        CrashLogEntry entry = adapter.getData().get(position);
        shareFiles(new ArrayList<>(java.util.Collections.singletonList(entry.file)));
    }

    private void exportAllCrashLogs() {
        List<CrashLogEntry> data = adapter.getData();
        if (data.isEmpty()) return;
        List<File> files = new ArrayList<>();
        for (CrashLogEntry entry : data) {
            files.add(entry.file);
        }
        shareFiles(files);
    }

    private void shareFiles(List<File> files) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : files) {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            uris.add(uri);
        }
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/plain");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享崩溃日志"));
    }

    private void showCrashDetail(int position) {
        CrashLogEntry entry = adapter.getData().get(position);
        new MaterialAlertDialogBuilder(this)
                .setTitle("崩溃详情 - " + entry.crashTime)
                .setMessage(entry.fullContent)
                .setPositiveButton("关闭", null)
                .setNegativeButton("分享", (d, w) -> shareCrashLog(position))
                .show();
    }

    private void applyWindowInsets(ActivityCrashLogBinding binding) {
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
