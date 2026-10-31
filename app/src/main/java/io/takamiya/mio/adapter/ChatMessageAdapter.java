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
import io.takamiya.mio.bean.ChatMessage;
import io.takamiya.mio.databinding.ItemChatMessageLeftBinding;
import io.takamiya.mio.databinding.ItemChatMessageRightBinding;
import io.takamiya.mio.utils.RelativeTimeUtil;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.Holder> {

    public interface OnPeerAvatarClickListener {
        void onPeerAvatarClick();
    }

    private static final int TYPE_LEFT = 0;
    private static final int TYPE_RIGHT = 1;

    private final List<ChatMessage> list = new ArrayList<>();
    private String peerAvatar = "";
    private String myAvatar = "";
    private OnPeerAvatarClickListener onPeerAvatarClickListener;

    public void setOnPeerAvatarClickListener(OnPeerAvatarClickListener listener) {
        this.onPeerAvatarClickListener = listener;
    }

    public void setAvatarUrls(String peerAvatar, String myAvatar) {
        this.peerAvatar = peerAvatar == null ? "" : peerAvatar;
        this.myAvatar = myAvatar == null ? "" : myAvatar;
        notifyDataSetChanged();
    }

    public void setData(List<ChatMessage> incoming) {
        list.clear();
        if (incoming != null) list.addAll(incoming);
        notifyDataSetChanged();
    }

    public void appendData(List<ChatMessage> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        int start = list.size();
        list.addAll(incoming);
        notifyItemRangeInserted(start, incoming.size());
    }

    public void prependData(List<ChatMessage> older) {
        if (older == null || older.isEmpty()) return;
        int insertCount = older.size();
        list.addAll(0, older);
        notifyItemRangeInserted(0, insertCount);
    }

    public List<ChatMessage> getData() {
        return list;
    }

    @Override
    public int getItemViewType(int position) {
        return list.get(position).isMine() ? TYPE_RIGHT : TYPE_LEFT;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_RIGHT) {
            return new Holder(ItemChatMessageRightBinding.inflate(inflater, parent, false));
        }
        return new Holder(ItemChatMessageLeftBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        View.OnClickListener avatarClick = onPeerAvatarClickListener != null
                ? v -> onPeerAvatarClickListener.onPeerAvatarClick() : null;
        holder.bind(list.get(position), peerAvatar, myAvatar, avatarClick);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        private ItemChatMessageLeftBinding leftBinding;
        private ItemChatMessageRightBinding rightBinding;

        Holder(ItemChatMessageLeftBinding binding) {
            super(binding.getRoot());
            this.leftBinding = binding;
        }

        Holder(ItemChatMessageRightBinding binding) {
            super(binding.getRoot());
            this.rightBinding = binding;
        }

        void bind(ChatMessage message, String peerAvatar, String myAvatar,
                  View.OnClickListener avatarClickListener) {
            String time = RelativeTimeUtil.toRelativeTimeSafe(message.getCreatedAt());
            if (leftBinding != null) {
                leftBinding.tvContent.setText(message.getContent());
                leftBinding.tvTime.setText(time);
                leftBinding.cardFailed.setVisibility(message.isFailed() ? View.VISIBLE : View.GONE);
                loadAvatar(leftBinding.ivAvatar, peerAvatar);
                leftBinding.ivAvatar.setOnClickListener(avatarClickListener);
            } else if (rightBinding != null) {
                rightBinding.tvContent.setText(message.getContent());
                rightBinding.tvTime.setText(time);
                rightBinding.cardFailed.setVisibility(message.isFailed() ? View.VISIBLE : View.GONE);
                loadAvatar(rightBinding.ivAvatar, myAvatar);
            }
        }

        private void loadAvatar(android.widget.ImageView imageView, String url) {
            if (url != null && !url.isEmpty()) {
                imageView.setImageTintList(null);
                Glide.with(imageView.getContext())
                        .load(url)
                        .placeholder(R.drawable.material_symbols_account_circle_outline)
                        .circleCrop()
                        .into(imageView);
            } else {
                imageView.setImageTintList(null);
                imageView.setImageResource(R.drawable.material_symbols_account_circle_outline);
                imageView.setImageTintList(
                        android.content.res.ColorStateList.valueOf(
                                imageView.getContext().getColor(R.color.colorOnSecondaryContainer)));
            }
        }
    }
}