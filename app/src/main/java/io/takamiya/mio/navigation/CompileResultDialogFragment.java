package io.takamiya.mio.navigation;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Locale;

import io.takamiya.mio.R;
import io.takamiya.mio.config.lhrb.LhrbCompiler;

/**
 * 编译打包完成弹窗，展示文件详细信息并提供分享。
 */
public class CompileResultDialogFragment extends DialogFragment {

    private static final String ARG_FILE_PATH = "file_path";
    private static final String ARG_PACKAGE = "package";
    private static final String ARG_VERSION_NAME = "version_name";
    private static final String ARG_VERSION_CODE = "version_code";
    private static final String ARG_MIN_ENGINE = "min_engine";
    private static final String ARG_FILE_SHA = "file_sha";
    private static final String ARG_SIG_SHA = "sig_sha";
    private static final String ARG_FINGERPRINT = "fingerprint";
    private static final String ARG_SELF_CHECK = "self_check";

    public static CompileResultDialogFragment newInstance(LhrbCompiler.Result result) {
        CompileResultDialogFragment f = new CompileResultDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FILE_PATH, result.file.getAbsolutePath());
        args.putString(ARG_PACKAGE, result.packageName);
        args.putString(ARG_VERSION_NAME, result.versionName);
        args.putInt(ARG_VERSION_CODE, result.versionCode);
        args.putInt(ARG_MIN_ENGINE, result.minEngine);
        args.putString(ARG_FILE_SHA, result.fileSha256);
        args.putString(ARG_SIG_SHA, result.signatureSha256);
        args.putString(ARG_FINGERPRINT, result.pubKeyFingerprint);
        args.putBoolean(ARG_SELF_CHECK, result.selfCheckOk);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_compile_result, null);
        TextView tvResult = view.findViewById(R.id.tvCompileResult);

        String filePath = args != null ? args.getString(ARG_FILE_PATH, "") : "";
        File file = new File(filePath);
        long size = file.exists() ? file.length() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("文件: ").append(file.getName()).append('\n');
        sb.append("路径: ").append(filePath).append('\n');
        sb.append("大小: ").append(formatSize(size)).append('\n');
        sb.append("自校验: ").append(args != null && args.getBoolean(ARG_SELF_CHECK)
                ? "通过 (CRC32 + 签名 + 指纹)" : "未通过").append('\n');
        sb.append('\n');
        sb.append("SHA-256: ").append(nz(args, ARG_FILE_SHA)).append('\n');
        sb.append("签名哈希: ").append(nz(args, ARG_SIG_SHA)).append('\n');
        sb.append("公钥指纹: ").append(nz(args, ARG_FINGERPRINT)).append('\n');
        sb.append('\n');
        sb.append("包名: ").append(nz(args, ARG_PACKAGE)).append('\n');
        sb.append("版本: ").append(nz(args, ARG_VERSION_NAME)).append(" (")
                .append(args != null ? args.getInt(ARG_VERSION_CODE) : 0).append(")\n");
        sb.append("最小引擎: v").append(args != null ? args.getInt(ARG_MIN_ENGINE) : 1);
        tvResult.setText(sb.toString());

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编译打包完成")
                .setView(view)
                .setPositiveButton("分享", (d, w) -> shareFile(file))
                .setNegativeButton("关闭", null)
                .create();
    }

    private String nz(Bundle args, String key) {
        String v = args != null ? args.getString(key, "") : "";
        return v == null || v.isEmpty() ? "-" : v;
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "分享 LHRB 文件"));
        } catch (Exception e) {
            // file missing
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.2f KB", kb);
        }
        return String.format(Locale.US, "%.2f MB", kb / 1024.0);
    }
}
