package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;

public class BuildingListAdapter extends RecyclerView.Adapter<BuildingListAdapter.ViewHolder> {

    public static class BuildingItem {
        public final String name;
        public final String meta;

        public BuildingItem(String name, String meta) {
            this.name = name;
            this.meta = meta;
        }
    }

    public interface OnItemActionListener {
        void onDelete(int position);
        void onItemClick(int position);
    }

    private List<BuildingItem> items = new ArrayList<>();
    private OnItemActionListener listener;

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<BuildingItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addItem(BuildingItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public void removeItem(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
    }

    public List<BuildingItem> getData() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_building_list, parent, false);
        return new ViewHolder(view);
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
        private final View cardRoot;
        private final TextView tvName;
        private final TextView tvMeta;
        private final View btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(BuildingItem item, int position) {
            tvName.setText(item.name);
            tvMeta.setText(item.meta);

            cardRoot.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(position);
            });
            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(position);
            });
        }
    }
}
