package io.takamiya.mio.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.takamiya.mio.R;
import io.takamiya.mio.bean.ControlActionItem;
import io.takamiya.mio.databinding.ItemFeatureListBinding;

public class FeatureListAdapter extends RecyclerView.Adapter<FeatureListAdapter.ViewHolder> {

    private List<ControlActionItem> items;
    private final Set<String> activeKeys = new HashSet<>();

    public FeatureListAdapter(List<ControlActionItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public void updateItems(List<ControlActionItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<ControlActionItem> getItems() {
        return items;
    }

    public void setActiveKey(String key) {
        String old = activeKeys.size() == 1 ? activeKeys.iterator().next() : null;
        activeKeys.clear();
        if (key != null && !key.isEmpty()) {
            activeKeys.add(key);
        }
        for (int i = 0; i < items.size(); i++) {
            ControlActionItem item = items.get(i);
            String itemKey = item.getPackageName() + "." + item.getActionId();
            if (itemKey.equals(old) || itemKey.equals(key)) {
                notifyItemChanged(i);
            }
        }
    }

    public void setActiveState(String key, boolean active) {
        String itemKey = key != null && !key.isEmpty() ? key : null;
        boolean changed;
        if (itemKey == null) {
            if (activeKeys.isEmpty()) return;
            activeKeys.clear();
            changed = true;
        } else if (active) {
            changed = activeKeys.add(itemKey);
        } else {
            changed = activeKeys.remove(itemKey);
        }
        if (!changed) return;
        for (int i = 0; i < items.size(); i++) {
            ControlActionItem item = items.get(i);
            if ((item.getPackageName() + "." + item.getActionId()).equals(itemKey) || itemKey == null) {
                notifyItemChanged(i);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeatureListBinding binding = ItemFeatureListBinding.inflate(
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

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemFeatureListBinding binding;

        public ViewHolder(@NonNull ItemFeatureListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ControlActionItem item) {
            View root = binding.getRoot();
            root.clearAnimation();
            root.setAlpha(1f);
            root.setTranslationX(0f);
            root.setTranslationY(0f);
            Context context = root.getContext();
            String key = item.getPackageName() + "." + item.getActionId();
            boolean isActive = activeKeys.contains(key);

            root.setTag(key);
            binding.tvFeatureActionName.setText(item.getTitle());
            binding.tvFeatureActionName.setTextColor(ContextCompat.getColor(
                    context, isActive ? R.color.colorPrimary : R.color.colorOnSurfaceVariant));
        }
    }
}
