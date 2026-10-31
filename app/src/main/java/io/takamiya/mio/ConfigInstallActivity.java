package io.takamiya.mio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.config.lhrb.LhrbReader;
import io.takamiya.mio.configManage.ConfigPackageManager;
import io.takamiya.mio.configManage.InstallResult;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * 通过系统"分享/打开方式"接收 json 或 lhrb 配置并快速安装。
 * 支持 ACTION_VIEW（content:// 或 file://）与 ACTION_SEND / ACTION_SEND_MULTIPLE（EXTRA_STREAM）。
 */
public class ConfigInstallActivity extends AppCompatActivity {

    private static final String TAG = "ConfigInstallActivity";

    private final ArrayDeque<Uri> queue = new ArrayDeque<>();
    private ConfigPackageManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.i(TAG, "onCreate");
        manager = new ConfigPackageManager(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        AppLogger.i(TAG, "onNewIntent");
        setIntent(intent);
        if (manager == null) {
            manager = new ConfigPackageManager(this);
        }
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        List<Uri> uris = extractUris(intent);
        if (uris.isEmpty()) {
            AppLogger.w(TAG, "no file uri in intent: " + (intent != null ? intent.getAction() : "null"));
            Toast.makeText(this, "未识别到配置文件", Toast.LENGTH_SHORT).show();
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
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
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
            alertNext("检测到插件安装包", "该文件是 ZIP 插件包，请通过「安装插件」打开");
            return;
        }

        boolean isLhrb = LhrbReader.isLhrb(data);
        AppLogger.d(TAG, "detected format: " + (isLhrb ? "LHRB" : "JSON")
                + ", bytes=" + data.length);

        if (isLhrb) {
            startInstall(data, null, true, false, uri);
        } else {
            String json = new String(data, StandardCharsets.UTF_8);
            if (json.trim().isEmpty()) {
                toastAndNext("文件为空: " + uri.getLastPathSegment());
                return;
            }
            startInstall(null, json, false, false, uri);
        }
    }

    private void startInstall(byte[] lhrb, String json, boolean isLhrb, boolean force, Uri uri) {
        InstallResult result;
        if (isLhrb) {
            result = force ? manager.installLhrb(lhrb, true) : manager.installLhrb(lhrb, false);
        } else {
            result = force ? manager.install(json, true) : manager.install(json, false);
        }
        AppLogger.d(TAG, "install result status: " + result.getStatus()
                + " for " + uri.getLastPathSegment());
        handleResult(result, lhrb, json, isLhrb, uri);
    }

    private void handleResult(InstallResult result, byte[] lhrb, String json,
                              boolean isLhrb, Uri uri) {
        String fileLabel = uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "配置";
        switch (result.getStatus()) {
            case SUCCESS:
                AppLogger.i(TAG, "install success: " + result.getPackageName()
                        + " v" + result.getNewVersionName());
                new MaterialAlertDialogBuilder(this)
                        .setTitle("安装成功")
                        .setMessage("配置 \"" + result.getPackageName() + "\" v"
                                + result.getNewVersionName() + " 已安装")
                        .setPositiveButton("确定", (d, w) -> processQueue())
                        .setCancelable(false)
                        .show();
                break;
            case VERSION_EQUAL:
                confirmOverwrite("版本相同", "已安装相同版本 v" + result.getNewVersionName()
                        + "，是否覆盖？", lhrb, json, isLhrb, uri);
                break;
            case DOWNGRADE:
                if (isDowngradeAllowed()) {
                    startInstall(lhrb, json, isLhrb, true, uri);
                } else {
                    confirmOverwrite("已安装更新版本", fileLabel + "\n当前已安装 v"
                            + result.getInstalledVersionName() + " ≥ 新版本 v"
                            + result.getNewVersionName() + "，是否覆盖？", lhrb, json, isLhrb, uri);
                }
                break;
            case PACKAGE_MISMATCH:
                alertNext("安装失败", "包名不匹配");
                break;
            case FORMAT_ERROR:
            default:
                String errMsg = "格式错误";
                if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                    errMsg = result.getErrors().get(0).getMessage();
                }
                alertNext("安装失败", fileLabel + "\n" + errMsg);
                break;
        }
    }

    private void confirmOverwrite(String title, String message, byte[] lhrb, String json,
                                  boolean isLhrb, Uri uri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("覆盖", (d, w) -> startInstall(lhrb, json, isLhrb, true, uri))
                .setNegativeButton("取消", (d, w) -> processQueue())
                .setCancelable(false)
                .show();
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

    private boolean isZipFile(byte[] data) {
        return data.length > 4
                && data[0] == 0x50 && data[1] == 0x4B
                && data[2] == 0x03 && data[3] == 0x04;
    }

    private boolean isDowngradeAllowed() {
        try {
            Registry reg = RegistryUtils.get();
            return reg != null && reg.getBoolean(
                    DataManage.Tag.APP_CONFIG_DOWNGRADE_INSTALLATION.getValue());
        } catch (Exception e) {
            AppLogger.w(TAG, "read downgrade flag failed: " + e.getMessage());
            return false;
        }
    }
}