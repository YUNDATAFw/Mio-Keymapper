package io.takamiya.mio.terminal.commands;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.takamiya.mio.FilePermissionRequestActivity;
import io.takamiya.mio.terminal.command.CommandCallback;
import io.takamiya.mio.terminal.command.CommandResult;
import io.takamiya.mio.terminal.command.CommandState;
import io.takamiya.mio.terminal.command.ICommand;
import io.takamiya.mio.terminal.command.ParamDef;

/**
 * 终端命令 allfiles —— 申请"所有文件访问"权限（plugin-utils build 写 build/ 目录需要）。
 * Android 11+ 跳到系统的 MANAGE_EXTERNAL_STORAGE 授权页，低版本弹运行时文件权限；
 * 授权页返回后自动轮询复查，输出 true/false。
 */
public class AllFilesCommand implements ICommand {

    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long POLL_TIMEOUT_MS = 120_000L;

    private final Context appContext;

    public AllFilesCommand(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "allfiles";
    }

    @Override
    public String getDescription() {
        return "申请所有文件访问权限 (plugin-utils 构建需先授权)";
    }

    @Override
    public String getUsage() {
        return "allfiles";
    }

    @Override
    public List<ParamDef> getParams() {
        return Collections.emptyList();
    }

    @Override
    public void execute(Map<String, Object> params, CommandCallback callback) {
        if (isGranted()) {
            callback.onResult(CommandResult.text("true"));
            callback.onStateChange(CommandState.SUCCESS);
            return;
        }
        if (!launchRequest()) {
            callback.onResult(CommandResult.text("false（无法打开授权页）"));
            callback.onStateChange(CommandState.SUCCESS);
            return;
        }
        callback.onStateChange(CommandState.EXECUTING);
        new Thread(() -> waitForResult(callback), "allfiles-wait").start();
    }

    private boolean launchRequest() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + appContext.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(intent);
            } else {
                Intent intent = new Intent(appContext, FilePermissionRequestActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(intent);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForResult(CommandCallback callback) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        boolean granted = false;
        while (!granted && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
            granted = isGranted();
        }
        String text = granted
                ? "已授权: true"
                : "未授权: false（请到设置中手动开启\"所有文件访问\"）";
        callback.onResult(CommandResult.text(text));
        callback.onStateChange(granted ? CommandState.SUCCESS : CommandState.SUCCESS);
    }

    private boolean isGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }
}