package io.takamiya.mio;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.noties.markwon.Markwon;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.databinding.ActivityUserAgreementBinding;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.SecureStrings;

public class UserAgreementActivity extends AppCompatActivity {

    private HttpClientJava httpClientJava = HttpManage.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityUserAgreementBinding binding = ActivityUserAgreementBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        boolean fromToolbar = getIntent().getBooleanExtra("from_toolbar", false);
        if (fromToolbar) {
            binding.toolbar.setNavigationIcon(R.drawable.material_symbols_arrow_back_rounded);
            binding.btnAgree.setVisibility(View.GONE);
            binding.btnDisagree.setVisibility(View.GONE);
        }

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        Markwon markwon = MarkdownProvider.create(this);

        // 离线兜底：先把本地协议文本展示出来并允许同意，
        // 网络成功后再用服务端内容覆盖，避免断网时按钮一直不可点。
        binding.progressBar.setVisibility(View.GONE);
        binding.scrollView.setVisibility(View.VISIBLE);
        markwon.setMarkdown(binding.markdownContent, OFFLINE_AGREEMENT_TEXT);
        binding.btnAgree.setEnabled(true);

        httpClientJava.get(UrlBuilder.getInstance().longPath(SecureStrings.path(16)).build())
                .onSuccess((code, body) -> {
                    String markdown = body.trim();
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(android.view.View.GONE);
                        binding.scrollView.setVisibility(android.view.View.VISIBLE);
                        markwon.setMarkdown(binding.markdownContent, markdown);
                        binding.btnAgree.setEnabled(true);
                    });
                })
                .onFailure((code, error) -> {
                    runOnUiThread(() -> {
                        // 保留本地兜底文本，按钮保持可点击
                        binding.progressBar.setVisibility(android.view.View.GONE);
                        binding.scrollView.setVisibility(android.view.View.VISIBLE);
                        binding.btnAgree.setEnabled(true);
                    });
                }).execute();

        binding.btnAgree.setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });

        binding.btnDisagree.setOnClickListener(v -> finish());
    }

    private static final String OFFLINE_AGREEMENT_TEXT =
            "# 用户协议\n\n"
                    + "欢迎使用 Mio。\n\n"
                    + "1. 本应用为本地调试版本，部分在线服务可能不可用。\n"
                    + "2. 使用本应用产生的数据仅保存在本机，卸载应用后将被清除。\n"
                    + "3. 请勿将本应用用于任何违法违规用途。\n"
                    + "4. 继续使用即表示你已阅读并同意本协议。\n\n"
                    + "> 当前处于离线模式，以上为内置协议文本。";

    private void applyWindowInsets(ActivityUserAgreementBinding binding) {
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
