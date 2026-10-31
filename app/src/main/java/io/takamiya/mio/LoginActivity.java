package io.takamiya.mio;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.databinding.ActivityLoginBinding;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;
import io.takamiya.mio.utils.SecureStrings;
import io.takamiya.mio.utils.UserToken;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private HttpClientJava client = HttpManage.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        setupLogin();

        binding.btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
        binding.btnForgotPassword.setOnClickListener(v -> {
            startActivity(new Intent(this, ForgotPasswordActivity.class));
        });
    }

    // ==================== 登录 ====================

    private void setupLogin() {
        binding.btnLogin.setOnClickListener(v -> {
            String account = binding.editAccount.getText().toString().trim();
            String password = binding.editPassword.getText().toString().trim();

            if (TextUtils.isEmpty(account)) {
                showError(getString(R.string.login_error_empty_account));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                showError(getString(R.string.login_error_empty_password));
                return;
            }

            setLoading(true);

            Map<String, Object> data = new HashMap<>();
            data.put("loginName", account);
            data.put("password", password);

            client.post(UrlBuilder.getInstance().longPath(SecureStrings.path(3))
                            .param(SecureStrings.path(95), AppToken.getToken()).build())
                    .json(data)
                    .onSuccess((code, body) -> {
                        try {
                            JSONObject info = new JSONObject(body);
                            if (code == 200 && info.getInt("code") == 200) {
                                JSONObject infoData = info.getJSONObject("data");
                                String token = infoData.getString("token");

                                if (!UserToken.parseAndStore(token)) {
                                    runOnUiThread(() -> {
                                        setLoading(false);
                                        showError("Token解析失败");
                                    });
                                    return;
                                }

                                Registry reg = RegistryUtils.get();
                                reg.set(DataManage.Tag.USER_ACCOUNT.getValue(), account);
                                reg.set(DataManage.Tag.USER_PASSWORD.getValue(), password);
                                reg.saveAsync();

                                runOnUiThread(() -> {
                                    setLoading(false);
                                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                                    finish();
                                });
                            } else {
                                String errMsg = info.getString("msg");
                                AppLogger.e(TAG, "login failed: " + errMsg);
                                runOnUiThread(() -> {
                                    setLoading(false);
                                    showError(errMsg);
                                });
                            }
                        } catch (Exception e) {
                            AppLogger.e(TAG, "login parse error: " + e.getMessage());
                            runOnUiThread(() -> {
                                setLoading(false);
                                showError("登录失败");
                            });
                        }
                    })
                    .onFailure((code, err) -> {
                        AppLogger.e(TAG, "login request failed: " + err);
                        runOnUiThread(() -> {
                            setLoading(false);
                            showError("网络异常");
                        });
                    })
                    .execute();
        });
    }

    // ==================== 错误提示 ====================

    public void showError(String message) {
        runOnUiThread(()->{
            new MaterialAlertDialogBuilder(this)
                    .setTitle("错误")
                    .setMessage(message)
                    .setNegativeButton("关闭", null)
                    .show();
        });
    }

    // ==================== Loading状态 ====================

    public void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnRegister.setEnabled(!loading);
        binding.btnForgotPassword.setEnabled(!loading);
        binding.editAccount.setEnabled(!loading);
        binding.editPassword.setEnabled(!loading);

        if (loading) {
            binding.progressLogin.show();
        } else {
            binding.progressLogin.hide();
        }
    }

    // ==================== WindowInsets ====================

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });
    }
}
