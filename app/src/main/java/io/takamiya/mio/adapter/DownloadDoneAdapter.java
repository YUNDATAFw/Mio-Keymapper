package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.panda.utils.library.HttpUtils.utils.HttpUtils;
import io.takamiya.mio.bean.DownloadTask;
import io.takamiya.mio.databinding.ItemDownloadDoneBinding;

public class DownloadDoneAdapter extends RecyclerView.Adapter<DownloadDoneAdapter.ViewHolder> {

    private final List<DownloadTask> list = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private boolean selectionMode = false;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public interface OnActionListener {
        void onOpenClick(DownloadTask task, int position);
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
        ItemDownloadDoneBinding binding = ItemDownloadDoneBinding.inflate(
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
        private final ItemDownloadDoneBinding binding;

        ViewHolder(ItemDownloadDoneBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DownloadTask task) {
            int pos = getBindingAdapterPosition();
            boolean isSelected = selectedPositions.contains(pos);

            binding.tvTitle.setText(task.getTitle());
            binding.tvSubtitle.setText("已完成");
            binding.tvSize.setText(HttpUtils.INSTANCE.formatFileSize(task.getTotalSize()));
            binding.tvStatus.setText(SDF.format(new Date(task.getCompletedAt())) + " 下载完成");

            if (selectionMode) {
                binding.checkSelect.setVisibility(View.VISIBLE);
                binding.checkSelect.setChecked(isSelected);
                binding.checkSelect.setOnCheckedChangeListener((btn, checked) -> {
                    toggleSelection(pos);
                });
                binding.btnOpen.setVisibility(View.GONE);
                binding.btnDelete.setVisibility(View.GONE);
                itemView.setOnClickListener(v -> toggleSelection(pos));
            } else {
                binding.checkSelect.setVisibility(View.GONE);
                binding.checkSelect.setOnCheckedChangeListener(null);
                binding.btnOpen.setVisibility(View.VISIBLE);
                binding.btnDelete.setVisibility(View.VISIBLE);
                binding.btnOpen.setOnClickListener(v -> {
                    if (listener != null) listener.onOpenClick(task, pos);
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
    }
}
