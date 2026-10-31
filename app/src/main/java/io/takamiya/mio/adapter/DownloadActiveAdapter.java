package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.panda.utils.library.HttpUtils.utils.HttpUtils;
import io.takamiya.mio.bean.DownloadTask;
import io.takamiya.mio.databinding.ItemDownloadActiveBinding;

public class DownloadActiveAdapter extends RecyclerView.Adapter<DownloadActiveAdapter.ViewHolder> {

    private final List<DownloadTask> list = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private boolean selectionMode = false;

    public interface OnActionListener {
        void onPauseClick(DownloadTask task, int position);
        void onRemoveClick(DownloadTask task, int position);
        void onLongClick(DownloadTask task, int position);
        void onSelectionChanged(int count);
    }

    private OnActionListener listener;

    public void setOnActionListener(OnActionListener listener) {
        this.listener = listener;
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void enterSelectionMode() {
        selectionMode = true;
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public void exitSelectionMode() {
        selectionMode = false;
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public void toggleSelection(int position) {
        if (!selectionMode) return;
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        if (listener != null) listener.onSelectionChanged(selectedPositions.size());
    }

    public List<DownloadTask> getSelectedTasks() {
        List<DownloadTask> result = new ArrayList<>();
        for (int pos : selectedPositions) {
            if (pos >= 0 && pos < list.size()) {
                result.add(list.get(pos));
            }
        }
        return result;
    }

    public int getSelectedCount() {
        return selectedPositions.size();
    }

    public void setData(List<DownloadTask> data) {
        list.clear();
        if (data != null) list.addAll(data);
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public void updateTask(DownloadTask task) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == task.getId()) {
                list.set(i, task);
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void removeTask(int taskId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == taskId) {
                list.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDownloadActiveBinding binding = ItemDownloadActiveBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemDownloadActiveBinding binding;

        ViewHolder(ItemDownloadActiveBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DownloadTask task) {
            int pos = getBindingAdapterPosition();
            boolean isSelected = selectedPositions.contains(pos);

            binding.tvTitle.setText(task.getTitle());
            binding.tvPercent.setText(task.getPercent() + "%");
            binding.progressIndicator.setProgress(task.getPercent());

            String current = HttpUtils.INSTANCE.formatFileSize(task.getCurrentSize());
            String total = HttpUtils.INSTANCE.formatFileSize(task.getTotalSize());
            String speed = HttpUtils.INSTANCE.formatFileSize(task.getSpeed()) + "/s";
            binding.tvStatus.setText("下载中 · " + current + " / " + total);
            binding.tvSubtitle.setText(speed + " · " + "剩余 " + formatEta(task));

            if (selectionMode) {
                binding.checkSelect.setVisibility(View.VISIBLE);
                binding.checkSelect.setChecked(isSelected);
                binding.checkSelect.setOnCheckedChangeListener((btn, checked) -> {
                    toggleSelection(pos);
                });
                binding.btnPause.setVisibility(View.GONE);
                binding.btnDelete.setVisibility(View.GONE);
                itemView.setOnClickListener(v -> toggleSelection(pos));
            } else {
                binding.checkSelect.setVisibility(View.GONE);
                binding.checkSelect.setOnCheckedChangeListener(null);
                binding.btnPause.setVisibility(View.VISIBLE);
                binding.btnDelete.setVisibility(View.VISIBLE);
                binding.btnPause.setOnClickListener(v -> {
                    if (listener != null) listener.onPauseClick(task, pos);
                });
                binding.btnDelete.setOnClickListener(v -> {
                    if (listener != null) listener.onRemoveClick(task, pos);
                });
                itemView.setOnClickListener(null);
                itemView.setOnLongClickListener(v -> {
                    if (listener != null) listener.onLongClick(task, pos);
                    return true;
                });
            }

            itemView.setActivated(isSelected);
        }

        private String formatEta(DownloadTask task) {
            if (task.getSpeed() <= 0) return "计算中...";
            long remaining = task.getTotalSize() - task.getCurrentSize();
            long seconds = remaining / task.getSpeed();
            if (seconds < 60) return seconds + " 秒";
            return (seconds / 60) + " 分 " + (seconds % 60) + " 秒";
        }
    }
}
