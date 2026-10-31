package io.takamiya.mio;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.databinding.ActivityClearDataBinding;
import io.takamiya.mio.utils.RegistryUtils;

public class ClearDataActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityClearDataBinding binding = ActivityClearDataBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnClearAll.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("确认清空")
                    .setMessage("清空后将自动重启应用，所有设置将恢复默认。")
                    .setPositiveButton("清空", (d, w) -> clearAndRestart())
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void clearAndRestart() {
        Mio.setClearingData(true);

        Registry reg = RegistryUtils.get();
        if (reg != null) {
            List<String> keys = new ArrayList<>(reg.list());
            for (String key : keys) {
                reg.delete(key);
            }
            reg.saveAsync();
        }

        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 500, pendingIntent);

        Runtime.getRuntime().exit(0);
    }

    private void applyWindowInsets(ActivityClearDataBinding binding) {
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
