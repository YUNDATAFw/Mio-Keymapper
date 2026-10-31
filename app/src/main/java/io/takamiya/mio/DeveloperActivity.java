package io.takamiya.mio;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.DeveloperAdapter;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.bean.Developer;
import io.takamiya.mio.databinding.ActivityDeveloperBinding;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.NetworkUtils;
import io.takamiya.mio.utils.SecureStrings;

public class DeveloperActivity extends AppCompatActivity {

    private static final String TAG = "Developer";

    private ActivityDeveloperBinding binding;
    private DeveloperAdapter adapter;
    private HttpClientJava client = HttpManage.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeveloperBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new DeveloperAdapter(new ArrayList<>());
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        fetchDevelopers();
    }

    private void fetchDevelopers() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showError("网络不可用，请检查网络连接");
            return;
        }

        client.get(UrlBuilder.getInstance().longPath(SecureStrings.path(12)).build())
                .query("action", "developers")
                .query("app_id", Mio.APP_ID)
                .onSuccess((code, body) -> {
                    if (code == 200) {
                        try {
                            JSONObject info = new JSONObject(body.trim());
                            int resultCode = info.getInt("code");
                            if (resultCode == 200) {
                                JSONArray developers = info.getJSONObject("data").getJSONArray("developers");
                                List<Developer> list = new ArrayList<>();
                                for (int i = 0; i < developers.length(); i++) {
                                    JSONObject item = developers.getJSONObject(i);
                                    list.add(parseDeveloper(item));
                                }
                                runOnUiThread(() -> adapter.updateData(list));
                            } else {
                                String errMsg = info.optString("msg", "获取开发者信息失败");
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

    private Developer parseDeveloper(JSONObject item) throws JSONException {
        String logoUrl = item.optString("logo_url");
        if (logoUrl.isEmpty() || "null".equals(logoUrl)) {
            logoUrl = null;
        } else if (!logoUrl.startsWith("http")) {
            logoUrl = UrlBuilder.getInstance().longPath(logoUrl).build();
        }
        return new Developer(
                item.optString("name"),
                item.optString("tag"),
                item.optString("intro"),
                logoUrl);
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void applyWindowInsets(ActivityDeveloperBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.recyclerView.setPadding(
                    binding.recyclerView.getPaddingLeft(),
                    binding.recyclerView.getPaddingTop(),
                    binding.recyclerView.getPaddingRight(),
                    bars.bottom);
            return insets;
        });
    }
}
