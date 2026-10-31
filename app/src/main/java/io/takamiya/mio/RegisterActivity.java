package io.takamiya.mio;

import android.os.Bundle;
import android.os.CountDownTimer;
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
import io.takamiya.mio.databinding.ActivityRegisterBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.SecureStrings;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    private ActivityRegisterBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private CountDownTimer sendCodeTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        setupSendCode();
        setupRegister();
        setupBackToLogin();
    }

    // ══════════════════════════════════════════════
    //  发送验证码
    // ══════════════════════════════════════════════

    private void setupSendCode() {
        binding.btnSendCode.setOnClickListener(v -> {
            String email = binding.editEmail.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                showError("请输入邮箱");
                return;
            }
            if (!email.contains("@")) {
                showError("请输入正确的邮箱");
                return;
            }

            binding.btnSendCode.setEnabled(false);

            Map<String, Object> data = new HashMap<>();
            data.put("type", "register");
            data.put("account", email);

            client.post(UrlBuilder.getInstance().longPath(SecureStrings.path(5))
                            .param(SecureStrings.path(95), AppToken.getToken()).build())
                    .json(data)
                    .onSuccess((code, body) -> {
                        try {
                            JSONObject info = new JSONObject(body);
                            if (code == 200 && info.getInt("code") == 200) {
                                runOnUiThread(this::startCodeCountdown);
                            } else {
                                String errMsg = info.getString("msg");
                                AppLogger.e(TAG, "send code failed: " + errMsg);
                                runOnUiThread(() -> {
                                    binding.btnSendCode.setEnabled(true);
                                    showError(errMsg);
                                });
                            }
                        } catch (Exception e) {
                            AppLogger.e(TAG, "send code parse error: " + e.getMessage());
                            runOnUiThread(() -> {
                                binding.btnSendCode.setEnabled(true);
                                showError("发送失败");
                            });
                        }
                    })
                    .onFailure((code, err) -> {
                        AppLogger.e(TAG, "send code request failed: " + err);
                        runOnUiThread(() -> {
                            binding.btnSendCode.setEnabled(true);
                            showError("网络异常");
                        });
                    })
                    .execute();
        });
    }

    private void startCodeCountdown() {
        binding.btnSendCode.setEnabled(false);

        sendCodeTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                binding.btnSendCode.setText(getString(R.string.register_resend_code, seconds));
            }

            @Override
            public void onFinish() {
                binding.btnSendCode.setEnabled(true);
                binding.btnSendCode.setText(getString(R.string.register_send_code));
            }
        }.start();
    }

    // ══════════════════════════════════════════════
    //  注册
    // ══════════════════════════════════════════════

    private void setupRegister() {
        binding.btnRegister.setOnClickListener(v -> {
            String username = binding.editUsername.getText().toString().trim();
            String nickname = binding.editNickname.getText().toString().trim();
            String email = binding.editEmail.getText().toString().trim();
            String code = binding.editCode.getText().toString().trim();
            String password = binding.editPassword.getText().toString().trim();
            String confirmPassword = binding.editConfirmPassword.getText().toString().trim();

            if (TextUtils.isEmpty(username)) {
                showError("请输入用户名");
                return;
            }
            if (username.length() < 3 || username.length() > 20) {
                showError("用户名长度必须为3-20位");
                return;
            }
            if (TextUtils.isEmpty(email) || !email.contains("@")) {
                showError("请输入正确的邮箱");
                return;
            }
            if (TextUtils.isEmpty(code) || code.length() != 8) {
                showError("请输入8位数字验证码");
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                showError("密码长度不能少于6位");
                return;
            }
            if (!password.equals(confirmPassword)) {
                showError("两次密码输入不一致");
                return;
            }

            setLoading(true);

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("email", email);
            data.put("password", password);
            data.put("code", code);
            if (!TextUtils.isEmpty(nickname)) {
                data.put("nickname", nickname);
            }

            client.post(UrlBuilder.getInstance().longPath(SecureStrings.path(4))
                            .param(SecureStrings.path(95), AppToken.getToken()).build())
                    .json(data)
                    .onSuccess((code2, body) -> {
                        try {
                            JSONObject info = new JSONObject(body);
                            if (code2 == 200 && info.getInt("code") == 200) {
                                runOnUiThread(() -> {
                                    setLoading(false);
                                    finish();
                                });
                            } else {
                                String errMsg = info.getString("msg");
                                AppLogger.e(TAG, "register failed: " + errMsg);
                                runOnUiThread(() -> {
                                    setLoading(false);
                                    showError(errMsg);
                                });
                            }
                        } catch (Exception e) {
                            AppLogger.e(TAG, "register parse error: " + e.getMessage());
                            runOnUiThread(() -> {
                                setLoading(false);
                                showError("注册失败");
                            });
                        }
                    })
                    .onFailure((code2, err) -> {
                        AppLogger.e(TAG, "register request failed: " + err);
                        runOnUiThread(() -> {
                            setLoading(false);
                            showError("网络异常");
                        });
                    })
                    .execute();
        });
    }

    // ══════════════════════════════════════════════
    //  返回登录
    // ══════════════════════════════════════════════

    private void setupBackToLogin() {
        binding.btnBackToLogin.setOnClickListener(v -> finish());
    }

    // ══════════════════════════════════════════════
    //  错误提示
    // ══════════════════════════════════════════════

    public void showError(String message) {
        runOnUiThread(()->{
            new MaterialAlertDialogBuilder(this)
                    .setTitle("错误")
                    .setMessage(message)
                    .setNegativeButton("关闭", null)
                    .show();
        });
    }

    // ══════════════════════════════════════════════
    //  Loading 状态
    // ══════════════════════════════════════════════

    public void setLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        binding.btnBackToLogin.setEnabled(!loading);
        binding.btnSendCode.setEnabled(!loading);
        binding.editUsername.setEnabled(!loading);
        binding.editEmail.setEnabled(!loading);
        binding.editPassword.setEnabled(!loading);
        binding.editConfirmPassword.setEnabled(!loading);
        binding.editCode.setEnabled(!loading);
        binding.editNickname.setEnabled(!loading);

        if (loading) {
            binding.progressRegister.show();
        } else {
            binding.progressRegister.hide();
        }
    }

    // ══════════════════════════════════════════════
    //  WindowInsets
    // ══════════════════════════════════════════════

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sendCodeTimer != null) {
            sendCodeTimer.cancel();
        }
    }
}
