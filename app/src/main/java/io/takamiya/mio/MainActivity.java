package io.takamiya.mio;

import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.DeviceInfoUtils;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.StringUtils;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.databinding.ActivityMainBinding;
import io.takamiya.mio.utils.ApkIntegrity;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.NetworkUtils;
import io.takamiya.mio.utils.RegistryUtils;
import io.takamiya.mio.utils.SecureStrings;
import io.takamiya.mio.utils.UserToken;

import io.noties.markwon.Markwon;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    // 本地调试：跳过服务器校验 / 版本检查，直接进入用户流程
    private static final boolean SKIP_STARTUP_NETWORK_CHECK = true;
    private ActivityMainBinding binding;
    private HttpClientJava client = HttpManage.getInstance();

    private final ActivityResultLauncher<Intent> userAgreementLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Registry reg = RegistryUtils.get();
                    reg.set(DataManage.Tag.APP_CONFIG_USER_AGREEMENT_ACCEPTED.getValue(), true);
                    reg.saveAsync();
                    // 离线模式：不区分登录状态，直接进入主页
                    Intent intent = new Intent(this, HomeActivity.class);
                    startActivity(intent);
                }
                finish();
            });

    private boolean needForceUpdate = false;
    private boolean updateDialogShowing = false;
    private String updateTitle;
    private String updateDescription;
    private String updateChangelog;
    private String updateDownloadUrl;
    private String updateFileSize;
    private String updateMinOsVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();
        startLoading();
    }

    private void startLoading() {
        binding.loadingView.setVisibility(View.VISIBLE);
        if (SKIP_STARTUP_NETWORK_CHECK) {
            AppLogger.w(TAG, "startup network check skipped, go to user flow directly");
            onUser();
            return;
        }
        verifyServerStatus();
    }

    private void onServer(String appToken) {
        if (!AppToken.parseAndStore(appToken)) {
            showErrorDialog("启动失败", "token解析失败");
            return;
        }
        syncConfig();
    }

    private void onCheck() {
        checkUpdate();
    }

    private void onUser() {
        Anim.hide(binding.loadingView);

        Registry registry = RegistryUtils.get();
        if (registry == null || !registry.getBoolean(
                DataManage.Tag.APP_CONFIG_USER_AGREEMENT_ACCEPTED.getValue())) {
            userAgreementLauncher.launch(new Intent(this, UserAgreementActivity.class));
            return;
        }

        // 离线模式：跳过登录/注册/会员等在线入口，直接进入主页
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    private void onUpdate() {
        runOnUiThread(this::syncUser);
    }

    private void onError() {
        runOnUiThread(() -> Anim.hide(binding.loadingView));
    }

    private void showErrorDialog(String title, String message) {
        runOnUiThread(() -> {
            Anim.hide(binding.loadingView);
            if (isFinishing() || isDestroyed()) return;
            new MaterialAlertDialogBuilder(this)
                    .setIcon(tintedDialogIcon(R.drawable.material_symbols_close_rounded, R.color.colorError))
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("退出", (d, w) -> finishAffinity())
                    .show();
        });
    }

    private void showAutoLoginFailedDialog(String message) {
        runOnUiThread(() -> {
            Anim.hide(binding.loadingView);
            if (isFinishing() || isDestroyed()) return;
            new MaterialAlertDialogBuilder(this)
                    .setIcon(tintedDialogIcon(R.drawable.material_symbols_close_rounded, R.color.colorError))
                    .setTitle("自动登录失败")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("重新登录", (d, w) -> {
                        Intent intent = new Intent(this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    })
                    .show();
        });
    }

    // ==================== 第1步：校验服务器 ====================
    private static final int MAX_VERIFY_RETRIES = 2;

    private void verifyServerStatus() {
        // 离线模式：跳过服务器校验，直接进行用户检查
        if (!NetworkUtils.isNetworkAvailable(this)) {
            AppLogger.w(TAG, "offline mode: skipping server verification");
            runOnUiThread(this::onCheck);
            return;
        }
        verifyServerWithRetry(0);
    }

    private void verifyServerWithRetry(int attempt) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            AppLogger.e(TAG, "network is not available");
            showErrorDialog("启动失败", "未联网");
            return;
        }

        new Thread(() -> {
            String[] res;
            try {
                res = ApkIntegrity.nativeVerifyServer(this);
            } catch (Exception e) {
                AppLogger.e(TAG, "native verify failed: " + e.getMessage());
                res = null;
            }
            if (res == null || res.length < 3) {
                if (attempt < MAX_VERIFY_RETRIES) {
                    AppLogger.w(TAG, "verify returned null, retrying (" + (attempt + 1) + "/" + MAX_VERIFY_RETRIES + ")");
                    sleepAndRetry(attempt);
                    return;
                }
                showErrorDialog("启动失败", "无法校验");
                return;
            }
            String status = res[0];
            String token = res[1];
            String msg = res[2];
            switch (status) {
                case "ok":
                    runOnUiThread(() -> onServer(token));
                    break;
                case "no_network":
                    AppLogger.e(TAG, "verify: no network");
                    showErrorDialog("启动失败", "未联网");
                    break;
                case "server_error":
                    if (attempt < MAX_VERIFY_RETRIES) {
                        AppLogger.w(TAG, "verify: server_error, retrying (" + (attempt + 1) + "/" + MAX_VERIFY_RETRIES + "): " + msg);
                        sleepAndRetry(attempt);
                        return;
                    }
                    AppLogger.e(TAG, "verify: http code: " + msg);
                    showErrorDialog("启动失败", "服务器异常");
                    break;
                case "rejected":
                    AppLogger.e(TAG, "verify: server code: " + msg);
                    showErrorDialog("启动失败", "校验失败");
                    break;
                case "parse_error":
                    if (attempt < MAX_VERIFY_RETRIES) {
                        AppLogger.w(TAG, "verify: parse_error, retrying (" + (attempt + 1) + "/" + MAX_VERIFY_RETRIES + "): " + msg);
                        sleepAndRetry(attempt);
                        return;
                    }
                    AppLogger.e(TAG, "verify: parse error: " + msg);
                    showErrorDialog("启动失败", "解析失败");
                    break;
                case "internal_error":
                    if (attempt < MAX_VERIFY_RETRIES) {
                        AppLogger.w(TAG, "verify: internal_error, retrying (" + (attempt + 1) + "/" + MAX_VERIFY_RETRIES + "): " + msg);
                        sleepAndRetry(attempt);
                        return;
                    }
                    AppLogger.e(TAG, "verify: internal error: " + msg);
                    showErrorDialog("启动失败", "无法校验");
                    break;
                default:
                    AppLogger.e(TAG, "verify: failed: " + msg);
                    showErrorDialog("启动失败", "无法校验");
                    break;
            }
        }).start();
    }

    private void sleepAndRetry(int attempt) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
        runOnUiThread(() -> verifyServerWithRetry(attempt + 1));
    }

    // ==================== 第2步：同步配置 ====================
    private void syncConfig() {
        AppLogger.e(TAG, "syncConfig: skip, calling onCheck directly");
        onCheck();
    }

    // ==================== 第3步：用户数据 ====================
    private void syncUser() {
        if (UserToken.isLoggedIn()) {
            AppLogger.d(TAG, "UserToken still valid, skip login");
            runOnUiThread(this::onUser);
            return;
        }

        AppLogger.d(TAG, "UserToken expired or not found, need login");

        Registry registry = RegistryUtils.get();
        if (!registry.exists(DataManage.Tag.USER_ACCOUNT.getValue()) || !registry.exists(DataManage.Tag.USER_PASSWORD.getValue())) {
            AppLogger.e(TAG, "no stored account, skip login");
            runOnUiThread(() -> {
                Intent intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
                finish();
            });
            return;
        }

        String account = registry.getString(DataManage.Tag.USER_ACCOUNT.getValue());
        String password = registry.getString(DataManage.Tag.USER_PASSWORD.getValue());

        if (StringUtils.isBlank(account) || StringUtils.isBlank(password)) {
            AppLogger.e(TAG, "account or password is blank, skip login");
            runOnUiThread(() -> {
                Intent intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
                finish();
            });
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("loginName", account);
        data.put("password", password);

        client.post(UrlBuilder.getInstance().longPath(SecureStrings.path(3))
                        .param(SecureStrings.path(95), AppToken.getToken()).build())
                .json(data)
                .onSuccess((code, body) -> {
                    if (code == 200) {
                        try {
                            JSONObject info = new JSONObject(body);
                            if (info.getInt("code") == 200) {
                                JSONObject infoData = info.getJSONObject("data");
                                String userToken = infoData.getString("token");
                                UserToken.parseAndStore(userToken);
                                runOnUiThread(this::onUser);
                            } else {
                                String errMsg = info.getString("msg");
                                AppLogger.e(TAG, "login failed: " + errMsg);
                                showAutoLoginFailedDialog(errMsg);
                            }
                        } catch (JSONException e) {
                            AppLogger.e(TAG, "login json parse error: " + e.getMessage());
                            runOnUiThread(() -> {
                                Intent intent = new Intent(this, LoginActivity.class);
                                startActivity(intent);
                                finish();
                            });
                        }
                    } else {
                        AppLogger.e(TAG, "login http code: " + code);
                        runOnUiThread(() -> {
                            Intent intent = new Intent(this, LoginActivity.class);
                            startActivity(intent);
                            finish();
                        });
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "login request failed: " + err);
                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    });
                })
                .execute();
    }

    private void checkUpdate() {
        client.get(UrlBuilder.getInstance().longPath(SecureStrings.path(11)).build())
                .query("app_id", Mio.APP_ID)
                .query("platform", "1")
                .query("version_code", String.valueOf(DeviceInfoUtils.getAppVersionCode(this)))
                .onSuccess((code, body) -> {
                    if (code == 200) {
                        try {
                            JSONObject info = new JSONObject(body.trim());
                            if (info.getInt("code") == 200) {
                                JSONObject infoData = info.getJSONObject("data");
                                boolean needUpdate = infoData.getBoolean("need_update");
                                boolean forceUpdate = infoData.getBoolean("force_update");
                                if (needUpdate) {
                                    String currentVersionName = infoData.getJSONObject("current_version")
                                            .getString("version_name");
                                    String latestVersionName = infoData.getJSONObject("latest_version")
                                            .getString("version_name");
                                    JSONObject updateInfo = infoData.getJSONObject("update_info");

                                    needForceUpdate = forceUpdate;
                                    updateTitle = String.format("%s (%s>%s)", updateInfo.getString("title"), currentVersionName, latestVersionName);
                                    updateDescription = updateInfo.getString("description");
                                    updateChangelog = updateInfo.getString("changelog");
                                    updateDownloadUrl = updateInfo.getString("download_url");
                                    updateFileSize = StringUtils.formatFileSize(updateInfo.getLong("file_size"), 1);
                                    updateMinOsVersion = updateInfo.getString("min_os_version");

                                    runOnUiThread(this::showUpdateDialog);
                                } else {
                                    runOnUiThread(this::onUpdate);
                                }
                            } else if (info.getInt("code") == 404) {
                                String errMsg = info.optString("msg", "版本不存在");
                                AppLogger.e(TAG, "check update: version not found, block entry: " + errMsg);
                                UrlBuilder.getInstance().init("");
                                runOnUiThread(() -> showVersionErrorDialog(errMsg));
                            } else {
                                String errMsg = info.getString("msg");
                                AppLogger.e(TAG, "check update failed: " + errMsg);
                                showErrorDialog("启动失败", errMsg);
                            }
                        } catch (JSONException e) {
                            AppLogger.e(TAG, "check update json parse error: " + e.getMessage());
                            showErrorDialog("启动失败", "解析失败");
                        }
                    } else {
                        AppLogger.e(TAG, "check update http code: " + code);
                        showErrorDialog("启动失败", "检查更新失败");
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "check update request failed: " + err);
                    showErrorDialog("启动失败", "网络异常");
                })
                .execute();
    }

    private Drawable tintedDialogIcon(int drawableRes, int colorRes) {
        Drawable icon = AppCompatResources.getDrawable(this, drawableRes);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon).mutate();
            DrawableCompat.setTint(icon, ContextCompat.getColor(this, colorRes));
        }
        return icon;
    }

    private void showVersionErrorDialog(String message) {
        runOnUiThread(() -> {
            Anim.hide(binding.loadingView);
            if (isFinishing() || isDestroyed()) return;
            new MaterialAlertDialogBuilder(this)
                    .setIcon(tintedDialogIcon(R.drawable.material_symbols_cached_rounded, R.color.colorPrimary))
                    .setTitle("版本校验失败")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("退出", (d, w) -> finishAffinity())
                    .show();
        });
    }

    private void showUpdateDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (updateDialogShowing) return;
        updateDialogShowing = true;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_update, null);
        MaterialTextView tvDescription = dialogView.findViewById(R.id.tvDialogDescription);
        MaterialTextView tvChangelog = dialogView.findViewById(R.id.tvDialogChangelog);
        MaterialTextView tvExtra = dialogView.findViewById(R.id.tvDialogExtra);

        Markwon markwon = MarkdownProvider.create(this);
        markwon.setMarkdown(tvDescription, updateDescription != null ? updateDescription : "");
        markwon.setMarkdown(tvChangelog, updateChangelog != null ? updateChangelog : "");
        tvExtra.setText(String.format("安装包大小: %s\n最低系统版本: %s",
                updateFileSize != null ? updateFileSize : "-",
                updateMinOsVersion != null ? updateMinOsVersion : "-"));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setIcon(tintedDialogIcon(R.drawable.material_symbols_cached_rounded, R.color.colorSecondary))
                .setTitle(updateTitle)
                .setView(dialogView)
                .setPositiveButton("更新", (d, w) -> {
                    updateDialogShowing = false;
                    if (updateDownloadUrl == null || updateDownloadUrl.trim().isEmpty()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("提示")
                                .setMessage("暂无下载链接，请稍后再试")
                                .setPositiveButton("确定", null)
                                .show();
                    } else {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateDownloadUrl));
                        startActivity(intent);
                    }
                    if (needForceUpdate) {
                        UrlBuilder.getInstance().init("");
                    }
                });

        if (!needForceUpdate) {
            builder.setNegativeButton("取消", (d, w) -> {
                updateDialogShowing = false;
                needForceUpdate = false;
                onUpdate();
            });
        }

        builder.setCancelable(!needForceUpdate);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needForceUpdate && !updateDialogShowing) {
            showUpdateDialog();
        }
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });
    }

    static class Anim {
        private static final AccelerateDecelerateInterpolator ACCEL_DECEL =
                new AccelerateDecelerateInterpolator();

        static void hide(View view) {
            ObjectAnimator a = ObjectAnimator.ofFloat(view, View.ALPHA, 0f);
            a.setDuration(400);
            a.setInterpolator(ACCEL_DECEL);
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    view.setVisibility(View.GONE);
                }
            });
            a.start();
        }
    }
}
