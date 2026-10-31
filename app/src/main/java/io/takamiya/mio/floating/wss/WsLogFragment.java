package io.takamiya.mio.floating.wss;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.takamiya.mio.databinding.ItemWsLogBinding;
import io.takamiya.mio.databinding.LayoutWsPageLogBinding;
import io.takamiya.mio.ws.WsViewModel;

public class WsLogFragment extends WsBaseFragment {

    private static final Pattern LOG_PATTERN =
            Pattern.compile("^\\[(.*?)\\](?:\\s+)?(\\S+)/(\\S+): (.*)$");

    private LayoutWsPageLogBinding binding;
    private WsViewModel viewModel;
    private LogAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = LayoutWsPageLogBinding.inflate(inflater, container, false);

        adapter = new LogAdapter();
        binding.rvWsLog.setLayoutManager(new LinearLayoutManager(inflater.getContext()));
        binding.rvWsLog.setAdapter(adapter);

        binding.btnCopyLog.setOnClickListener(v -> {
            if (binding == null || adapter == null || adapter.getItemCount() == 0) {
                showToast("暂无日志");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (String line : adapter.getAll()) {
                sb.append(line).append("\n");
            }
            Context context = getContext();
            if (context == null || sb.length() == 0) return;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("WsLog", sb.toString()));
            showToast("日志已复制到剪贴板");
        });
        binding.btnClearLog.setOnClickListener(v -> {
            if (viewModel != null) {
                viewModel.clearLogs();
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view) {
        LifecycleOwner owner = getLifecycleOwner();
        viewModel = (owner == null)
                ? null
                : new ViewModelProvider((androidx.lifecycle.ViewModelStoreOwner) owner)
                        .get(WsViewModel.class);
        if (viewModel == null) return;

        viewModel.logLines().observe(owner, lines -> {
            if (binding == null || adapter == null) return;
            adapter.updateData(lines);
            int count = adapter.getItemCount();
            if (count > 0) {
                binding.rvWsLog.scrollToPosition(count - 1);
            }
        });
    }

    @Override
    public void onDetach() {
        viewModel = null;
        binding = null;
        adapter = null;
        super.onDetach();
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

        private final List<String> lines = new ArrayList<>();

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemWsLogBinding binding;

            ViewHolder(@NonNull ItemWsLogBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemWsLogBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String line = lines.get(position);
            Matcher m = LOG_PATTERN.matcher(line);
            if (m.find()) {
                holder.binding.tvLogTag.setText(m.group(2));
                holder.binding.tvLogLevel.setText(m.group(3));
                holder.binding.tvLogMsg.setText(m.group(4));
            } else {
                holder.binding.tvLogTag.setText("");
                holder.binding.tvLogLevel.setText("");
                holder.binding.tvLogMsg.setText(line);
            }
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }

        void updateData(List<String> newLines) {
            final List<String> target = (newLines == null)
                    ? new ArrayList<>() : new ArrayList<>(newLines);
            androidx.recyclerview.widget.DiffUtil.DiffResult diff =
                    androidx.recyclerview.widget.DiffUtil.calculateDiff(
                            new androidx.recyclerview.widget.DiffUtil.Callback() {
                                @Override
                                public int getOldListSize() {
                                    return lines.size();
                                }

                                @Override
                                public int getNewListSize() {
                                    return target.size();
                                }

                                @Override
                                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                                    return lines.get(oldItemPosition)
                                            .equals(target.get(newItemPosition));
                                }

                                @Override
                                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                                    return true;
                                }
                            });
            lines.clear();
            lines.addAll(target);
            diff.dispatchUpdatesTo(this);
        }

        List<String> getAll() {
            return lines;
        }
    }
}