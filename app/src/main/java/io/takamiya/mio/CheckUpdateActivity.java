package io.takamiya.mio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.DeviceInfoUtils;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.databinding.ActivityCheckUpdateBinding;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.NetworkUtils;
import io.takamiya.mio.utils.SecureStrings;

import io.noties.markwon.Markwon;

public class CheckUpdateActivity extends AppCompatActivity {

    private static final String TAG = "CheckUpdate";
    private ActivityCheckUpdateBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private Markwon markwon;
    private String downloadUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckUpdateBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        markwon = MarkdownProvider.create(this);

        binding.btnUpdate.setOnClickListener(v -> {
            if (downloadUrl != null && !downloadUrl.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)));
            }
        });

        checkUpdate();
    }

    private void checkUpdate() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showError("网络不可用，请检查网络连接");
            return;
        }

        showLoading();
        hideErrorInfo();

        binding.tvVersion.setText("版本:" + DeviceInfoUtils.getAppVersionName(this));

        client.get(UrlBuilder.getInstance().longPath(SecureStrings.path(11)).build())
                .query("app_id", Mio.APP_ID)
                .query("platform", "1")
                .query("version_code", String.valueOf(DeviceInfoUtils.getAppVersionCode(this)))
                .onSuccess((code, body) -> {
                    if (code == 200) {
                        try {
                            JSONObject info = new JSONObject(body.trim());
                            int resultCode = info.getInt("code");
                            if (resultCode == 200) {
                                JSONObject infoData = info.getJSONObject("data");
                                boolean needUpdate = infoData.getBoolean("need_update");
                                if (needUpdate) {
                                    String currentVersion = infoData.getJSONObject("current_version")
                                            .getString("version_name");
                                    String latestVersion = infoData.getJSONObject("latest_version")
                                            .getString("version_name");
                                    JSONObject updateInfo = infoData.getJSONObject("update_info");

                                    String title = updateInfo.getString("title");
                                    String description = updateInfo.getString("description");
                                    String changelog = updateInfo.getString("changelog");
                                    downloadUrl = updateInfo.getString("download_url");

                                    runOnUiThread(() -> showUpdateAvailable(title, description, changelog, currentVersion, latestVersion));
                                } else {
                                    runOnUiThread(CheckUpdateActivity.this::showUpToDate);
                                }
                            } else if (resultCode == 404) {
                                runOnUiThread(() -> showInfo("暂无版本信息"));
                            } else {
                                String errMsg = info.optString("msg", "未知错误");
                                runOnUiThread(() -> showError(errMsg));
                            }
                        } catch (JSONException e) {
                            AppLogger.e(TAG, "json parse error: " + e.getMessage());
                            runOnUiThread(() -> showError("数据解析失败"));
                        }
                    } else {
                        AppLogger.e(TAG, "http code: " + code);
                        runOnUiThread(() -> showError("服务器异常"));
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "request failed: " + err);
                    runOnUiThread(() -> showError("请求失败"));
                })
                .execute();
    }

    private void showLoading() {
        binding.cardUpdateInfo.setVisibility(View.VISIBLE);
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.layoutUpdateAvailable.setVisibility(View.GONE);
        binding.layoutUpToDate.setVisibility(View.GONE);
        binding.btnUpdate.setVisibility(View.GONE);
    }

    private void showUpdateAvailable(String title, String description, String changelog,
                                     String currentVersion, String latestVersion) {
        binding.cardUpdateInfo.setVisibility(View.VISIBLE);
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutUpdateAvailable.setVisibility(View.VISIBLE);
        binding.layoutUpToDate.setVisibility(View.GONE);
        binding.btnUpdate.setVisibility(View.VISIBLE);

        binding.tvShizukuVersion.setText(title);
        binding.tvNewVersion.setText(String.format("发现有新版本: %s > %s", currentVersion, latestVersion));
        markwon.setMarkdown(binding.tvUpdateDescription, description);
        markwon.setMarkdown(binding.tvChangelog, changelog);
    }

    private void showUpToDate() {
        binding.cardUpdateInfo.setVisibility(View.VISIBLE);
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutUpdateAvailable.setVisibility(View.GONE);
        binding.layoutUpToDate.setVisibility(View.VISIBLE);
        binding.btnUpdate.setVisibility(View.GONE);
        binding.tvShizukuVersion.setText("更新信息");
    }

    private void showError(String msg) {
        hideErrorInfo();
        binding.cardUpdateInfo.setVisibility(View.GONE);
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutUpdateAvailable.setVisibility(View.GONE);
        binding.layoutUpToDate.setVisibility(View.GONE);
        binding.btnUpdate.setVisibility(View.GONE);
        binding.cardError.setVisibility(View.VISIBLE);
        binding.tvErrorMsg.setText(msg);
    }

    private void showInfo(String msg) {
        hideErrorInfo();
        binding.cardUpdateInfo.setVisibility(View.GONE);
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutUpdateAvailable.setVisibility(View.GONE);
        binding.layoutUpToDate.setVisibility(View.GONE);
        binding.btnUpdate.setVisibility(View.GONE);
        binding.cardInfo.setVisibility(View.VISIBLE);
        binding.tvInfoMsg.setText(msg);
    }

    private void hideErrorInfo() {
        binding.cardError.setVisibility(View.GONE);
        binding.cardInfo.setVisibility(View.GONE);
    }

    private void applyWindowInsets(ActivityCheckUpdateBinding binding) {
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
