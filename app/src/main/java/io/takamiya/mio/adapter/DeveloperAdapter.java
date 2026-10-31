package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import io.takamiya.mio.bean.Developer;
import io.takamiya.mio.databinding.ItemDeveloperBinding;

public class DeveloperAdapter extends RecyclerView.Adapter<DeveloperAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Developer developer, int position);
    }

    private List<Developer> developerList;
    private OnItemClickListener listener;

    public DeveloperAdapter(List<Developer> developerList) {
        this.developerList = developerList;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDeveloperBinding binding = ItemDeveloperBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(developerList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return developerList == null ? 0 : developerList.size();
    }

    public void updateData(List<Developer> newList) {
        this.developerList = newList;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemDeveloperBinding binding;

        public ViewHolder(@NonNull ItemDeveloperBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Developer developer, int position) {
            binding.tvName.setText(developer.getName());
            binding.tvRole.setText(developer.getRole());
            binding.tvDescription.setText(developer.getDescription());

            if (developer.getAvatarUrl() != null && !developer.getAvatarUrl().isEmpty()) {
                Glide.with(binding.ivAvatar.getContext())
                        .load(developer.getAvatarUrl())
                        .into(binding.ivAvatar);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(developer, position);
                }
            });
        }
    }
}
