package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.databinding.ItemBuildingCommandBinding;

/**
 * 命令列表项：序号 + 单行显示（超长截断 ...）+ 复制按钮；
 * 支持点击单选/再点取消（最多一项选中，可全不选）。
 */
public class BuildingCommandAdapter extends RecyclerView.Adapter<BuildingCommandAdapter.ViewHolder> {

    /** 用户点击复制按钮。 */
    public interface OnCopyListener {
        void onCopy(String command);
    }

    private final List<String> items = new ArrayList<>();
    private OnCopyListener copyListener;
    /** 单选：当前选中的行，-1 表示全部未选中。 */
    private int selectedIndex = RecyclerView.NO_POSITION;

    public void setOnCopyListener(OnCopyListener listener) {
        this.copyListener = listener;
    }

    /** 全量替换（切换 Tab 时），选中态清空。 */
    public void setData(List<String> data) {
        items.clear();
        if (data != null) items.addAll(data);
        selectedIndex = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    /** 追加（分页加载）。 */
    public void appendData(List<String> data) {
        if (data == null || data.isEmpty()) return;
        int start = items.size();
        items.addAll(data);
        notifyItemRangeInserted(start, data.size());
    }

    public List<String> getData() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemBuildingCommandBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        /** 显示用最大字符数：超长命令截断，避免大量长文本测量卡顿（复制仍用完整命令）。 */
        private static final int MAX_DISPLAY_CHARS = 140;

        private final ItemBuildingCommandBinding binding;
        /** 默认背景（ripple），取消选中时恢复。 */
        private final android.graphics.drawable.Drawable defaultBg;

        ViewHolder(ItemBuildingCommandBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.defaultBg = binding.getRoot().getBackground();
        }

        void bind(String command, int position) {
            // 序号与 sqlite seq 一致（1-based，分页追加后连续）
            binding.tvIndex.setText(String.valueOf(position + 1));

            String display = command;
            if (display.length() > MAX_DISPLAY_CHARS) {
                display = display.substring(0, MAX_DISPLAY_CHARS) + "...";
            }
            binding.tvCommand.setText(display);

            applySelected(position == selectedIndex);

            // 点击 item：选中/取消（单选）
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                int old = selectedIndex;
                selectedIndex = (old == pos) ? RecyclerView.NO_POSITION : pos;
                if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
                if (selectedIndex != RecyclerView.NO_POSITION) notifyItemChanged(selectedIndex);
            });

            binding.btnCopyCommand.setOnClickListener(v -> {
                if (copyListener != null) copyListener.onCopy(command);
            });
        }

        private void applySelected(boolean selected) {
            if (selected) {
                itemView.setSelected(true);
                itemView.setBackgroundColor(ContextCompat.getColor(
                        itemView.getContext(), R.color.colorSecondaryContainer));
            } else {
                itemView.setSelected(false);
                itemView.setBackground(defaultBg);
            }
        }
    }
}
