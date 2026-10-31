package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import static java.lang.Integer.parseInt;

import android.os.Bundle;
import android.os.CountDownTimer;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.databinding.ActivityForgotPasswordBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;

public class ForgotPasswordActivity extends AppCompatActivity {

    private CountDownTimer countDownTimer;
    private HttpClientJava client = HttpManage.getInstance();
    private String TAG = "ForgotPasswordActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityForgotPasswordBinding binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnForgotPass.setOnClickListener(v -> {
            String accountOrEmail = Objects.requireNonNull(binding.etAccountOrEmail.getText())
                    .toString().trim();
            int code = parseInt(Objects.requireNonNull(binding.etCode.getText()).toString().trim());
            String newPass = Objects.requireNonNull(binding.etNewPass.getText()).toString().trim();
            String reEnterPass = Objects.requireNonNull(binding.etReEnterPass.getText()).toString()
                    .trim();

            Map<String, Object> data = new HashMap<>();
            data.put("account", accountOrEmail);
            data.put("code", code);
            data.put("newPassword", newPass);
            data.put("confirmPassword", reEnterPass);

            client.post(UrlBuilder.getInstance().longPath(SecureStrings.path(59)).build())
                    .header("X-APP-TOKEN", AppToken.getToken())
                    .json(data)
                    .onSuccess((statusCode, body) -> {

                        try {
                            JSONObject bodyJson = new JSONObject(body.trim());
                            int code1 = bodyJson.getInt("code");
                            String msg = bodyJson.getString("msg");
                            if (statusCode == 200 && code1 == 200) {
                                runOnUiThread(()->{
                                    AppLogger.d(TAG, "User successfully retrieved password");
                                    new MaterialAlertDialogBuilder(this)
                                            .setTitle("找回成功")
                                            .setMessage(msg)
                                            .setNegativeButton("关闭", (d,i) -> finish())
                                            .show();
                                });
                                return;
                            }
                            runOnUiThread(()-> {
                                AppLogger.e(TAG, "The user failed in a password recovery attempt due to:" + msg);
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle("找回失败")
                                        .setMessage("["+ code1 +"]" + msg)
                                        .setNegativeButton("取消", null)
                                        .show();
                            });

                        } catch (JSONException e) {
                            runOnUiThread(() -> {
                                AppLogger.e(TAG, e.getMessage());
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle("发生错误")
                                        .setMessage("数据无法解析")
                                        .setNegativeButton("取消", null)
                                        .show();
                            });
                        }

                    })
                    .onFailure((statusCode, error) -> {
                        runOnUiThread(()->{
                            AppLogger.e(TAG, "A network error occurred when the user requested the password recovery interface:" + "[" + statusCode + "]" +error);
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("网络错误")
                                    .setMessage("["+ code +"]" + error)
                                    .setNegativeButton("取消", null)
                                    .show();
                        });
                    }).execute();

        });

        binding.btnGetCode.setOnClickListener(v -> {

            String accountOrEmail = Objects.requireNonNull(binding.etAccountOrEmail.getText())
                    .toString().trim();

            Map<String, Object> data = new HashMap<>();
            data.put("type", "reset");
            data.put("account", accountOrEmail);

            client.post(UrlBuilder.getInstance().longPath(SecureStrings.path(5)).build())
                    .header("X-APP-TOKEN", AppToken.getToken())
                    .json(data)
                    .onSuccess((code, body) -> {
                        try {
                            JSONObject bodyJson = new JSONObject(body.trim());
                            int code1 = bodyJson.getInt("code");
                            String msg = bodyJson.getString("msg");

                            runOnUiThread(() -> {
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle(code1 == 200 && code == 200 ? "发送成功" : "发送失败")
                                        .setMessage(msg)
                                        .setNegativeButton("取消", null)
                                        .show();
                                AppLogger.d(TAG, new StringBuilder().append("User sent verification code Result: ")
                                        .append(code).append(";").append(code1).append("Details:")
                                        .append(msg).toString());
                            });
                        } catch (JSONException e) {
                            runOnUiThread(() -> {
                                AppLogger.e(TAG, e.getMessage());
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle("发生错误")
                                        .setMessage("数据无法解析")
                                        .setNegativeButton("取消", null)
                                        .show();
                            });
                        }
                    })
                    .onFailure((code, error) -> {
                        runOnUiThread(()-> {
                            AppLogger.e(TAG, "Network error occurred when requesting to send the verification code:" + "["+ code +"]" + error);
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("网络错误")
                                    .setMessage("["+ code +"]" + error)
                                    .setNegativeButton("取消", null)
                                    .show();
                        });
                    }).execute();
        });
    }

    private void startCountDown(ActivityForgotPasswordBinding binding) {
        binding.btnGetCode.setEnabled(false);

        countDownTimer = new CountDownTimer(60 * 1000, 1000) {
            @Override
            public void onFinish() {
                // 计时结束
                runOnUiThread(()->{
                    binding.btnGetCode.setEnabled(true);
                    binding.btnGetCode.setText("发送验证码");
                });
            }

            @Override
            public void onTick(long l) {
                runOnUiThread(() -> {
                    binding.btnGetCode.setText((l / 1000) + "秒");
                });
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        super.onDestroy();
    }

    private void applyWindowInsets(ActivityForgotPasswordBinding binding) {
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
