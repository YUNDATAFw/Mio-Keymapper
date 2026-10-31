package io.takamiya.mio;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

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
                        binding.progressBar.setVisibility(android.view.View.GONE);
                        binding.scrollView.setVisibility(android.view.View.VISIBLE);
                        Toast.makeText(this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                        binding.markdownContent.setText("加载用户协议失败，请检查网络后重试。");
                    });
                }).execute();

        binding.scrollView.setVisibility(android.view.View.GONE);
        binding.btnAgree.setEnabled(false);
        binding.btnAgree.setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });

        binding.btnDisagree.setOnClickListener(v -> finish());
    }

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
