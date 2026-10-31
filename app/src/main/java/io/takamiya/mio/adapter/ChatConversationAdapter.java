package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.bean.ChatConversation;
import io.takamiya.mio.databinding.ItemChatConversationBinding;
import io.takamiya.mio.databinding.ItemChatMyMessagesBinding;
import io.takamiya.mio.utils.RelativeTimeUtil;

public class ChatConversationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ChatConversation conversation);
    }

    public interface OnMyMessagesClickListener {
        void onMyMessagesClick();
    }

    private static final int TYPE_MY_MESSAGES = 0;
    private static final int TYPE_CONVERSATION = 1;

    private final List<ChatConversation> list = new ArrayList<>();
    private OnItemClickListener listener;
    private OnMyMessagesClickListener myMessagesListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnMyMessagesClickListener(OnMyMessagesClickListener listener) {
        this.myMessagesListener = listener;
    }

    public void setData(List<ChatConversation> incoming) {
        list.clear();
        if (incoming != null) list.addAll(incoming);
        notifyDataSetChanged();
    }

    public void appendData(List<ChatConversation> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        int start = list.size();
        list.addAll(incoming);
        notifyItemRangeInserted(start + 1, incoming.size());
    }

    public List<ChatConversation> getData() {
        return list;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_MY_MESSAGES : TYPE_CONVERSATION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MY_MESSAGES) {
            ItemChatMyMessagesBinding binding = ItemChatMyMessagesBinding.inflate(inflater, parent, false);
            return new MyMessagesHolder(binding);
        }
        ItemChatConversationBinding binding = ItemChatConversationBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MyMessagesHolder) return;
        if (holder instanceof ViewHolder) {
            ((ViewHolder) holder).bind(list.get(position - 1));
        }
    }

    @Override
    public int getItemCount() {
        return list.size() + 1;
    }

    class MyMessagesHolder extends RecyclerView.ViewHolder {
        MyMessagesHolder(ItemChatMyMessagesBinding binding) {
            super(binding.getRoot());
            itemView.setOnClickListener(v -> {
                if (myMessagesListener != null) {
                    myMessagesListener.onMyMessagesClick();
                }
            });
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatConversationBinding binding;

        ViewHolder(ItemChatConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                int itemIndex = pos - 1;
                if (listener != null && pos != RecyclerView.NO_POSITION && itemIndex >= 0 && itemIndex < list.size()) {
                    listener.onItemClick(list.get(itemIndex));
                }
            });
        }

        void bind(ChatConversation item) {
            binding.tvNickname.setText(item.getNickname());
            binding.tvNickname.setTextColor(item.getMemberLevel() > 0
                    ? androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), R.color.colorPrimary)
                    : androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), R.color.colorOnSurface));
            binding.tvLastMessage.setText(item.getLastMessage());
            binding.tvTime.setText(RelativeTimeUtil.toRelativeTimeSafe(item.getLastTime()));

            int unread = item.getUnreadCount();
            if (unread > 0) {
                binding.tvUnread.setVisibility(View.VISIBLE);
                binding.tvUnread.setText(unread > 99 ? "99+" : String.valueOf(unread));
            } else {
                binding.tvUnread.setVisibility(View.GONE);
            }

            if (item.getAvatar() != null && !item.getAvatar().isEmpty()) {
                binding.ivAvatar.setImageTintList(null);
                Glide.with(binding.getRoot().getContext())
                        .load(item.getAvatar())
                        .placeholder(R.mipmap.ic_launcher_round)
                        .circleCrop()
                        .into(binding.ivAvatar);
            } else {
                binding.ivAvatar.setImageTintList(null);
                binding.ivAvatar.setImageResource(R.drawable.material_symbols_account_circle_outline);
            }
        }
    }
}