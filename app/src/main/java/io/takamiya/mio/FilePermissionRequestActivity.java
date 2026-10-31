package io.takamiya.mio;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import cn.panda.utils.library.AppLogger;

/**
 * 低版本（Android 10 及以下）使用的文件权限请求中转页。
 *
 * <p>仅用于在缺少 Activity 的悬浮窗场景下弹出运行时
 * READ/WRITE_EXTERNAL_STORAGE 授权，结果由发起方在窗口恢复焦点后
 * 用 checkSelfPermission 复查，本 Activity 不持有结果。</p>
 */
public class FilePermissionRequestActivity extends Activity {

    private static final String TAG = "FilePermissionRequestActivity";
    private static final int REQ_STORAGE = 7001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            finish();
            return;
        }
        boolean granted = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            finish();
            return;
        }
        AppLogger.d(TAG, "requesting storage permission");
        requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AppLogger.d(TAG, "storage permission result, finishing");
        finish();
    }
}