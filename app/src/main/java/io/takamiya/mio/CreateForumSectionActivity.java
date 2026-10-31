package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.databinding.ActivityCreateForumSectionBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class CreateForumSectionActivity extends AppCompatActivity {

    private static final String TAG = "CreateForumSection";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCreateForumSectionBinding binding = ActivityCreateForumSectionBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnSubmit.setOnClickListener(v -> {
            String name = binding.etName.getText().toString().trim();
            String intro = binding.etIntro.getText().toString().trim();

            if (name.isEmpty()) {
                binding.tilName.setError("板块名称不能为空");
                return;
            }
            binding.tilName.setError(null);

            int type = 0;
            int checkedId = binding.rgType.getCheckedRadioButtonId();
            if (checkedId == R.id.rbResource) {
                type = 1;
            } else if (checkedId == R.id.rbChat) {
                type = 2;
            }

            binding.btnSubmit.setEnabled(false);

            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            if (!intro.isEmpty()) {
                data.put("intro", intro);
            }
            data.put("type", type);

            String url = UrlBuilder.getInstance()
                    .longPath(SecureStrings.path(48))
                    .param("action", "create")
                    .param(SecureStrings.path(95), AppToken.getToken())
                    .build();

            HttpManage.getInstance().post(url)
                    .header(SecureStrings.path(96), UserToken.getToken())
                    .json(data)
                    .onSuccess((code, body) -> runOnUiThread(() -> {
                        try {
                            JSONObject info = new JSONObject(body);
                            int respCode = info.getInt("code");
                            if (code == 200 && respCode == 200) {
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle("创建成功")
                                        .setMessage("板块创建成功，等待管理员审核")
                                        .setCancelable(false)
                                        .setPositiveButton("知道了", (d, w) -> finish())
                                        .show();
                            } else {
                                String errMsg = info.optString("msg", "创建失败");
                                binding.btnSubmit.setEnabled(true);
                                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            binding.btnSubmit.setEnabled(true);
                            Toast.makeText(this, "解析响应失败", Toast.LENGTH_SHORT).show();
                        }
                    }))
                    .onFailure((code, err) -> runOnUiThread(() -> {
                        binding.btnSubmit.setEnabled(true);
                        Toast.makeText(this, "网络异常: " + err, Toast.LENGTH_SHORT).show();
                    }))
                    .execute();
        });
    }

    private void applyWindowInsets(ActivityCreateForumSectionBinding binding) {
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
