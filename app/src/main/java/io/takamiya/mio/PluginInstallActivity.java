package io.takamiya.mio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import cn.fsy.plugin.sdl.library.PluginCallback;
import cn.fsy.plugin.sdl.library.SdlEngine;
import cn.fsy.plugin.sdl.library.SdlException;
import cn.fsy.plugin.sdl.library.plugin.PluginItem;
import cn.panda.utils.library.AppLogger;
import java.nio.charset.StandardCharsets;
import io.takamiya.mio.config.lhrb.LhrbReader;

/**
 * 通过系统"分享/打开方式"接收 ZIP 插件文件并快速安装。
 */
public class PluginInstallActivity extends AppCompatActivity {

    private static final String TAG = "PluginInstallActivity";

    private final java.util.ArrayDeque<Uri> queue = new java.util.ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.i(TAG, "onCreate");
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        AppLogger.i(TAG, "onNewIntent");
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        List<Uri> uris = extractUris(intent);
        if (uris.isEmpty()) {
            AppLogger.w(TAG, "no file uri in intent: " + (intent != null ? intent.getAction() : "null"));
            Toast.makeText(this, "未识别到插件文件", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        queue.addAll(uris);
        processQueue();
    }

    private List<Uri> extractUris(Intent intent) {
        List<Uri> result = new ArrayList<>();
        if (intent == null) return result;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                result.add(intent.getData());
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                result.add(uri);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) {
                result.addAll(streams);
            }
        }
        AppLogger.d(TAG, "extracted " + result.size() + " uri(s), action=" + action);
        return result;
    }

    private void processQueue() {
        if (queue.isEmpty()) {
            AppLogger.d(TAG, "queue empty, finishing");
            finish();
            return;
        }
        Uri uri = queue.poll();
        AppLogger.i(TAG, "installing file: " + uri);
        readAndInstall(uri);
    }

    private void readAndInstall(Uri uri) {
        byte[] data;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                AppLogger.w(TAG, "openInputStream null for: " + uri);
                toastAndNext("无法读取文件: " + uri.getLastPathSegment());
                return;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            data = bos.toByteArray();
        } catch (Exception e) {
            AppLogger.e(TAG, "read failed for " + uri + ": " + e.getMessage(), e);
            toastAndNext("读取文件失败: " + uri.getLastPathSegment());
            return;
        }

        if (data == null || data.length == 0) {
            toastAndNext("文件为空: " + uri.getLastPathSegment());
            return;
        }

        if (isZipFile(data)) {
            AppLogger.d(TAG, "detected ZIP format, installing via SDL engine");
            installZipFromUri(uri);
        } else if (LhrbReader.isLhrb(data)) {
            alertNext("检测到配置文件", "该文件是配置文件(lhrb)，请通过「安装配置」打开");
        } else if (looksLikeJson(data)) {
            alertNext("检测到配置文件", "该文件是配置文件(json)，请通过「安装配置」打开");
        } else {
            alertNext("不支持的格式", "仅支持 ZIP 格式的 DSL 插件");
        }
    }

    private boolean looksLikeJson(byte[] data) {
        String head = new String(data, 0, Math.min(data.length, 64), StandardCharsets.UTF_8).trim();
        return head.startsWith("{") || head.startsWith("[");
    }

    private boolean isZipFile(byte[] data) {
        return data.length > 4
                && data[0] == 0x50 && data[1] == 0x4B
                && data[2] == 0x03 && data[3] == 0x04;
    }

    private void installZipFromUri(Uri uri) {
        try {
            File importDir = new File(getFilesDir(), "import");
            if (!importDir.exists() && !importDir.mkdirs()) {
                throw new IllegalStateException("无法创建导入目录");
            }
            File zipFile = new File(importDir, "pending_install.zip");
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(zipFile)) {
                if (is == null) {
                    toastAndNext("无法读取文件: " + uri.getLastPathSegment());
                    return;
                }
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }

            Toast.makeText(this, "正在安装插件...", Toast.LENGTH_SHORT).show();

            io.takamiya.mio.dsl.PluginResInstaller.install(this, zipFile.getAbsolutePath(), new PluginCallback() {
                @Override
                public void onStart(Path installPath) {
                    AppLogger.d(TAG, "ZIP install started: " + installPath);
                }

                @Override
                public void onProgress(int progressPercent) {
                }

                @Override
                public void onSuccess(PluginItem item) {
                    runOnUiThread(() -> {
                        new MaterialAlertDialogBuilder(PluginInstallActivity.this)
                                .setTitle("安装成功")
                                .setMessage("插件 \"" + item.getPluginName() + "\" v"
                                        + item.getPluginVersion() + " 已安装")
                                .setPositiveButton("确定", (d, w) -> {
                                    showAuthSheet(item);
                                    processQueue();
                                })
                                .setCancelable(false)
                                .show();
                    });
                }

                @Override
                public void onFailure(SdlException error) {
                    runOnUiThread(() ->
                        alertNext("安装失败", io.takamiya.mio.dsl.InstallErrors.describe(error))
                    );
                }

                @Override
                public void onCancel() {
                    AppLogger.d(TAG, "ZIP install cancelled");
                    processQueue();
                }
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "installZipFromUri failed", e);
            toastAndNext("安装失败: " + e.getMessage());
        }
    }

    private void alertNext(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", (d, w) -> processQueue())
                .setCancelable(false)
                .show();
    }

    private void toastAndNext(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        processQueue();
    }

    private void showAuthSheet(PluginItem item) {
        if (item == null) return;
        PluginAuthSheet sheet = new PluginAuthSheet(this, item.getPluginPackage(), item.getPluginName());
        sheet.show();
    }
}
