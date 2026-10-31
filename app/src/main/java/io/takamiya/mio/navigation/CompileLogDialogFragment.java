package io.takamiya.mio.navigation;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.takamiya.mio.R;

/**
 * 编译打包实时日志弹窗。日志可从任意线程追加，自动滚动到底部。
 */
public class CompileLogDialogFragment extends DialogFragment {

    private TextView tvLog;
    private ScrollView svLog;
    private final StringBuilder buffer = new StringBuilder();

    public static CompileLogDialogFragment newInstance() {
        return new CompileLogDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_compile_log, null);
        tvLog = view.findViewById(R.id.tvCompileLog);
        svLog = view.findViewById(R.id.svCompileLog);
        flush();
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编译打包")
                .setView(view)
                .create();
    }

    /** 追加一行日志，可从后台线程调用。 */
    public void appendLog(String line) {
        synchronized (buffer) {
            buffer.append(line).append('\n');
        }
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(this::flush);
    }

    private void flush() {
        if (tvLog == null) {
            return;
        }
        synchronized (buffer) {
            if (buffer.length() > 0) {
                tvLog.append(buffer.toString());
                buffer.setLength(0);
            }
        }
        if (svLog != null) {
            svLog.post(() -> svLog.fullScroll(View.FOCUS_DOWN));
        }
    }
}
