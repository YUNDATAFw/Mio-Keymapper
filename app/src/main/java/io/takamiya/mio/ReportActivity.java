package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.databinding.ActivityReportBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class ReportActivity extends AppCompatActivity {

    static class ReportType {
        String code;
        String name;
    }

    private ActivityReportBinding binding;
    private int targetType;
    private int targetId;
    private String targetContent;
    private final List<ReportType> types = new ArrayList<>();
    private int selectedIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        targetType = getIntent().getIntExtra("target_type", 0);
        targetId = getIntent().getIntExtra("target_id", 0);
        targetContent = getIntent().getStringExtra("target_content");

        binding = ActivityReportBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.reportTargetInfo.setText(buildTargetInfo());
        binding.reportTypeRow.setOnClickListener(v -> showTypePicker());
        binding.btnSubmitReport.setOnClickListener(v -> submit());

        loadTypes();
    }

    private String buildTargetInfo() {
        String typeName;
        switch (targetType) {
            case 1: typeName = "帖子"; break;
            case 2: typeName = "资源"; break;
            case 3: typeName = "评论"; break;
            case 4: typeName = "用户"; break;
            default: typeName = "内容";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(typeName).append(" #").append(targetId);
        if (targetContent != null && !targetContent.isEmpty()) {
            String snippet = targetContent.length() > 80
                    ? targetContent.substring(0, 80) + "..."
                    : targetContent;
            sb.append("\n").append(snippet);
        }
        return sb.toString();
    }

    private void loadTypes() {
        String category;
        switch (targetType) {
            case 1: category = "post"; break;
            case 2: category = "resource"; break;
            case 3: category = "comment"; break;
            case 4: category = "user"; break;
            default: category = "comment";
        }
        HttpManage.getInstance().get(UrlBuilder.getInstance()
                        .longPath(SecureStrings.path(56))
                        .param("action", "types")
                        .param(SecureStrings.path(95), AppToken.getToken())
                        .param("category", category)
                        .build())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        if (code != 200 || resp.getInt("code") != 200) {
                            String msg = resp.optString("msg", "获取举报类型失败");
                            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                            return;
                        }
                        JSONArray arr = resp.getJSONObject("data").getJSONArray("types");
                        types.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            ReportType t = new ReportType();
                            t.code = item.optString("code", "");
                            t.name = item.optString("name", "");
                            types.add(t);
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> Toast.makeText(this, "解析举报类型失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> runOnUiThread(() ->
                        Toast.makeText(this, "获取举报类型失败", Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private void showTypePicker() {
        if (types.isEmpty()) {
            Toast.makeText(this, "举报类型暂未加载，请稍候重试", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[types.size()];
        for (int i = 0; i < types.size(); i++) {
            names[i] = types.get(i).name;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择举报类型")
                .setSingleChoiceItems(names, selectedIndex, (d, which) -> {
                    selectedIndex = which;
                    binding.reportTypeField.setText(names[which]);
                    binding.reportTypeField.setTextColor(
                            ContextCompat.getColor(this, R.color.colorOnSecondaryContainer));
                })
                .setPositiveButton("确定", null)
                .show();
    }

    private void submit() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedIndex < 0) {
            Toast.makeText(this, "请先选择举报类型", Toast.LENGTH_SHORT).show();
            return;
        }
        ReportType t = types.get(selectedIndex);
        String extra = binding.reportContentField.getText() == null
                ? "" : binding.reportContentField.getText().toString().trim();

        Map<String, Object> body = new HashMap<>();
        body.put("target_type", targetType);
        body.put("target_id", targetId);
        body.put("report_type", t.code);
        body.put("report_content", extra);

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(56))
                .param("action", "submit")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        HttpManage.getInstance().post(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> {
                    try {
                        JSONObject respJson = new JSONObject(resp);
                        if (code == 200 && respJson.getInt("code") == 200) {
                            String ticketNo = respJson.optJSONObject("data") == null
                                    ? "" : respJson.optJSONObject("data").optString("ticket_no", "");
                            runOnUiThread(() -> showSubmitSuccessDialog(ticketNo));
                        } else {
                            String msg = respJson.optString("msg", "举报提交失败");
                            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> Toast.makeText(this, "举报提交失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> runOnUiThread(() ->
                        Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private void showSubmitSuccessDialog(String ticketNo) {
        String message = "感谢您的反馈，我们会尽快审核处理。";
        if (ticketNo != null && !ticketNo.isEmpty()) {
            message = "您的举报工单号：" + ticketNo + "\n\n" + message;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("举报提交成功")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("知道了", (d, w) -> finish())
                .show();
    }

    private void applyWindowInsets(ActivityReportBinding binding) {
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