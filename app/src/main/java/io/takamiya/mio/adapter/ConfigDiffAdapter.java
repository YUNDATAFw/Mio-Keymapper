package io.takamiya.mio.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.configManage.ConfigDiffHelper;
import io.takamiya.mio.databinding.ItemConfigDiffBinding;

public class ConfigDiffAdapter extends RecyclerView.Adapter<ConfigDiffAdapter.ViewHolder> {

    private final List<ConfigDiffHelper.DiffLine> lines;

    public ConfigDiffAdapter(List<ConfigDiffHelper.DiffLine> lines) {
        this.lines = lines;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConfigDiffBinding binding = ItemConfigDiffBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(lines.get(position));
    }

    @Override
    public int getItemCount() {
        return lines == null ? 0 : lines.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemConfigDiffBinding binding;

        public ViewHolder(@NonNull ItemConfigDiffBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ConfigDiffHelper.DiffLine line) {
            Context context = binding.getRoot().getContext();
            binding.tvDiffPrefix.setText(line.getPrefix());
            binding.tvDiffText.setText(line.getText());

            int color;
            switch (line.getType()) {
                case ConfigDiffHelper.TYPE_ADDED:
                    color = ContextCompat.getColor(context, R.color.colorPrimary);
                    break;
                case ConfigDiffHelper.TYPE_REMOVED:
                    color = ContextCompat.getColor(context, R.color.colorError);
                    break;
                default:
                    color = ContextCompat.getColor(context, R.color.colorOnSurfaceVariant);
                    break;
            }
            binding.tvDiffPrefix.setTextColor(color);
            binding.tvDiffText.setTextColor(color);
        }
    }
}
