package io.takamiya.mio.floating.toolBoxFragment;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.takamiya.mio.databinding.ItemToolsBinding;

import java.util.List;

public class ToolboxListAdapter extends RecyclerView.Adapter<ToolboxListAdapter.ViewHolder> {

    private final List<ToolboxTab> items;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ToolboxTab tab);
    }

    public ToolboxListAdapter(List<ToolboxTab> items) {
        this.items = items;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemToolsBinding binding = ItemToolsBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ToolboxTab tab = items.get(position);
        holder.bind(tab);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(tab);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemToolsBinding binding;

        ViewHolder(ItemToolsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ToolboxTab tab) {
            binding.tvToolName.setText(tab.getName());
            binding.tvToolSub.setText("工具");
            binding.tagCard.setVisibility(android.view.View.GONE);
        }
    }
}
