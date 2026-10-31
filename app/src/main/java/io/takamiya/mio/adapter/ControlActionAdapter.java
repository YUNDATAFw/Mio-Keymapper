package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.databinding.ItemControlPanelBinding;

public class ControlActionAdapter extends RecyclerView.Adapter<ControlActionAdapter.ViewHolder> {

    public interface OnActionStateChangeListener {
        void onActionStateChanged(ControlActionItem item, boolean isChecked);
    }

    private List<ControlActionItem> items;
    private OnActionStateChangeListener stateChangeListener;

    public ControlActionAdapter(List<ControlActionItem> items) {
        this.items = items;
    }

    public void setOnActionStateChangeListener(OnActionStateChangeListener listener) {
        this.stateChangeListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemControlPanelBinding binding = ItemControlPanelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public void updateData(List<ControlActionItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void addItem(ControlActionItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public void clear() {
        int size = items.size();
        items.clear();
        notifyItemRangeRemoved(0, size);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemControlPanelBinding binding;

        public ViewHolder(@NonNull ItemControlPanelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ControlActionItem item) {
            binding.tvActionTitle.setText(item.getTitle());
            binding.tvActionDescription.setText(item.getDescription());
            binding.source.setText(item.getSource() + "-" + item.getName());

            binding.switchAction.setOnCheckedChangeListener(null);
            binding.switchAction.setChecked(item.isEnabled());
            binding.switchAction.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.setEnabled(isChecked);
                if (stateChangeListener != null) {
                    stateChangeListener.onActionStateChanged(item, isChecked);
                }
            });
        }
    }
}
