package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.takamiya.mio.bean.Config;
import io.takamiya.mio.databinding.ItemConfigBinding;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    private List<Config> configList;

    public ConfigAdapter(List<Config> configList) {
        this.configList = configList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConfigBinding binding = ItemConfigBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(configList.get(position));
    }

    @Override
    public int getItemCount() {
        return configList == null ? 0 : configList.size();
    }

    public void updateData(List<Config> newList) {
        this.configList = newList;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemConfigBinding binding;

        public ViewHolder(@NonNull ItemConfigBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Config config) {
            binding.tvConfigName.setText(config.getName());
            binding.tvConfigDescription.setText(config.getDescription());
            binding.tvVersion.setText("版本: " + config.getVersion());
            binding.tvPackageName.setText("包名: " + config.getPackageName());

            boolean locked = config.isLocked();
            boolean incompatible = config.isIncompatible();
            binding.tvLocked.setVisibility(locked || incompatible ? View.VISIBLE : View.GONE);
            if (incompatible) {
                binding.tvLocked.setText("不适配当前引擎");
            }
            binding.btnEdit.setVisibility(locked || incompatible ? View.GONE : View.VISIBLE);

            if (config.getWarning() != null && !config.getWarning().isEmpty()) {
                binding.cardWarning.setVisibility(View.VISIBLE);
                binding.tvWarning.setText(config.getWarning());
            } else {
                binding.cardWarning.setVisibility(View.GONE);
            }

            binding.switchEnable.setOnCheckedChangeListener(null);
            binding.switchEnable.setChecked(config.isEnabled());
            binding.switchEnable.setEnabled(!incompatible);
            binding.switchEnable.setAlpha(incompatible ? 0.4f : 1f);
            binding.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (config.getOnEnableChangeListener() != null) {
                    config.getOnEnableChangeListener().onEnableChanged(config, isChecked);
                }
            });

            binding.btnEdit.setOnClickListener(v -> {
                if (config.getOnEditClickListener() != null) {
                    config.getOnEditClickListener().onClick(config);
                }
            });

            binding.btnCheckUpdate.setOnClickListener(v -> {
                if (config.getOnCheckUpdateClickListener() != null) {
                    config.getOnCheckUpdateClickListener().onClick(config);
                }
            });

            binding.btnExport.setOnClickListener(v -> {
                if (config.getOnExportClickListener() != null) {
                    config.getOnExportClickListener().onClick(config);
                }
            });

            binding.btnDelete.setOnClickListener(v -> {
                if (config.getOnDeleteClickListener() != null) {
                    config.getOnDeleteClickListener().onClick(config);
                }
            });
        }
    }
}
