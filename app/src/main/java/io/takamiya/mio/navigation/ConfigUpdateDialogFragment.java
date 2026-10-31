package io.takamiya.mio.navigation;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.adapter.ConfigDiffAdapter;
import io.takamiya.mio.configManage.ConfigDiffHelper;
import io.takamiya.mio.databinding.DialogConfigUpdateBinding;

public class ConfigUpdateDialogFragment extends DialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_CONFIG_NAME = "config_name";
    private static final String ARG_DESCRIPTION = "description";
    private static final String ARG_AUTHOR = "author";
    private static final String ARG_CURRENT_VERSION = "current_version";
    private static final String ARG_REMOTE_VERSION = "remote_version";
    private static final String ARG_UPDATE_URL = "update_url";
    private static final String ARG_HAS_UPDATE = "has_update";

    private static ConfigUpdateCallback sCallback;

    public static ConfigUpdateDialogFragment newInstance(
            String title, String configName, String description, String author,
            String currentVersion, String remoteVersion, String updateUrl,
            boolean hasUpdate) {
        ConfigUpdateDialogFragment f = new ConfigUpdateDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_CONFIG_NAME, configName);
        args.putString(ARG_DESCRIPTION, description);
        args.putString(ARG_AUTHOR, author);
        args.putString(ARG_CURRENT_VERSION, currentVersion);
        args.putString(ARG_REMOTE_VERSION, remoteVersion);
        args.putString(ARG_UPDATE_URL, updateUrl);
        args.putBoolean(ARG_HAS_UPDATE, hasUpdate);
        f.setArguments(args);
        return f;
    }

    public static void setCallback(ConfigUpdateCallback callback) {
        sCallback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args == null) return super.onCreateDialog(savedInstanceState);

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_config_update, null);
        DialogConfigUpdateBinding binding = DialogConfigUpdateBinding.bind(dialogView);

        binding.tvDialogConfigName.setText(args.getString(ARG_CONFIG_NAME, ""));
        binding.tvDialogDescription.setText(args.getString(ARG_DESCRIPTION, ""));
        binding.tvDialogCurrentVersion.setText(args.getString(ARG_CURRENT_VERSION, ""));
        binding.tvDialogRemoteVersion.setText(args.getString(ARG_REMOTE_VERSION, ""));
        binding.tvDialogUpdateUrl.setText(args.getString(ARG_UPDATE_URL, ""));
        String author = args.getString(ARG_AUTHOR, "");
        if (author != null && !author.isEmpty()) {
            binding.tvDialogAuthor.setText(author);
        }

        if (args.getBoolean(ARG_HAS_UPDATE)) {
            List<ConfigDiffHelper.DiffLine> diff = ConfigUpdateDiffHolder.diff;
            if (diff != null && !diff.isEmpty()) {
                boolean hasChanges = false;
                for (ConfigDiffHelper.DiffLine line : diff) {
                    if (line.getType() != ConfigDiffHelper.TYPE_UNCHANGED) {
                        hasChanges = true;
                        break;
                    }
                }
                if (hasChanges) {
                    binding.cardChangelog.setVisibility(View.VISIBLE);
                    binding.rvChangelog.setLayoutManager(
                            new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
                    binding.rvChangelog.setAdapter(new ConfigDiffAdapter(diff));

                    int maxHeight = (int) (200 * getResources().getDisplayMetrics().density);
                    ViewGroup.LayoutParams lp = binding.rvChangelog.getLayoutParams();
                    lp.height = maxHeight;
                    binding.rvChangelog.setLayoutParams(lp);
                }
            }
        }

        String title = args.getString(ARG_TITLE, "检查更新");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(dialogView);

        if (args.getBoolean(ARG_HAS_UPDATE)) {
            builder.setPositiveButton("更新", (d, w) -> {
                if (sCallback != null) {
                    sCallback.onUpdateConfirmed();
                }
            });
            builder.setNegativeButton("取消", null);
        } else {
            builder.setPositiveButton("确定", null);
        }

        return builder.create();
    }

    public interface ConfigUpdateCallback {
        void onUpdateConfirmed();
    }

    public static class ConfigUpdateDiffHolder {
        public static List<ConfigDiffHelper.DiffLine> diff;
        public static String remoteJson;
        public static byte[] remoteLhrbBytes;
    }
}
