package io.takamiya.mio;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.databinding.ActivityDonateBinding;
import io.takamiya.mio.utils.CustomQRCodeUtil;

public class DonateActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityDonateBinding binding = ActivityDonateBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        loadQRCode(binding);
    }

    private void loadQRCode(ActivityDonateBinding binding) {
        CustomQRCodeUtil.QRConfig config = new CustomQRCodeUtil.QRConfig();
        config.width = 600;
        config.height = 600;
        config.margin = 2;
        config.foregroundColor = ContextCompat.getColor(this, R.color.colorPrimary);
        config.backgroundColor = ContextCompat.getColor(this, R.color.colorBackground);

        config.pixelShape = CustomQRCodeUtil.PixelShape.CIRCLE;
        config.pixelScale = 0.85f;

        config.detectStyle = CustomQRCodeUtil.DetectStyle.CIRCLE;
        config.detectOutColor = ContextCompat.getColor(this, R.color.colorPrimary);
        config.detectInnerColor = ContextCompat.getColor(this, R.color.colorPrimary);

        config.borderWidth = 0;
        config.borderColor = ContextCompat.getColor(this, R.color.colorPrimary);
        config.borderRadius = 20;

        final String qrUrl = UrlBuilder.getInstance().longPath("Mio/Page/").build();
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "QrCode");
            t.setDaemon(true);
            return t;
        }).execute(() -> {
            Bitmap qr = CustomQRCodeUtil.createQRCode(qrUrl, config);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.qrcode.setImageBitmap(qr);
            });
        });
    }

    private void applyWindowInsets(ActivityDonateBinding binding) {
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
