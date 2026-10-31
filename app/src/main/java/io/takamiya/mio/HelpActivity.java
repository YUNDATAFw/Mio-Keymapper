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
import io.takamiya.mio.databinding.ActivityHelpBinding;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.SecureStrings;

public class HelpActivity extends AppCompatActivity {

    private HttpClientJava httpClientJava = HttpManage.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityHelpBinding binding = ActivityHelpBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        Markwon markwon = MarkdownProvider.create(this);

        binding.scrollView.setVisibility(View.GONE);

        httpClientJava.get(UrlBuilder.getInstance().longPath(SecureStrings.path(13)).build())
                .onSuccess((code, body) -> {
                    String markdown = body.trim();
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.scrollView.setVisibility(View.VISIBLE);
                        markwon.setMarkdown(binding.markdownContent, markdown);
                    });
                })
                .onFailure((code, error) -> {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.scrollView.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                        binding.markdownContent.setText("加载帮助内容失败，请检查网络后重试。");
                    });
                }).execute();
    }

    private void applyWindowInsets(ActivityHelpBinding binding) {
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
