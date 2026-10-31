package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.bean.Message;
import io.takamiya.mio.databinding.ItemMyMessagesBinding;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {

    private final List<Message> list = new ArrayList<>();

    public interface OnMessageActionListener {
        void onDetailClick(int position);
        void onDeleteClick(int position);
    }

    private OnMessageActionListener listener;

    public void setOnMessageActionListener(OnMessageActionListener listener) {
        this.listener = listener;
    }

    public List<Message> getData() {
        return list;
    }

    public void setData(List<Message> incoming) {
        list.clear();
        if (incoming != null) {
            list.addAll(incoming);
        }
        notifyDataSetChanged();
    }

    public void appendData(List<Message> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        int start = list.size();
        list.addAll(incoming);
        notifyItemRangeInserted(start, incoming.size());
    }

    public void markAllRead() {
        boolean changed = false;
        for (Message message : list) {
            if (message.getIsRead() == 0) {
                message.setIsRead(1);
                changed = true;
            }
        }
        if (changed) {
            notifyDataSetChanged();
        }
    }

    public void removeAt(int position) {
        if (position < 0 || position >= list.size()) return;
        list.remove(position);
        notifyItemRemoved(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMyMessagesBinding binding = ItemMyMessagesBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(list.get(position), position);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemMyMessagesBinding binding;

        ViewHolder(ItemMyMessagesBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Message message, int position) {
            if (message == null) return;
            binding.tvTitle.setText(message.getTitle());
            binding.tvContent.setText(message.getContent());
            binding.tvTime.setText(message.getCreatedAt());

            boolean read = message.getIsRead() == 1;
            binding.ivUnreadDot.setVisibility(read ? View.GONE : View.VISIBLE);
            binding.tvTitle.setAlpha(read ? 0.7f : 1f);

            binding.cardMessage.setOnClickListener(v -> {
                if (listener != null) listener.onDetailClick(getBindingAdapterPosition());
            });
            binding.btnDetail.setOnClickListener(v -> {
                if (listener != null) listener.onDetailClick(getBindingAdapterPosition());
            });
            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(getBindingAdapterPosition());
            });
        }
    }
}
