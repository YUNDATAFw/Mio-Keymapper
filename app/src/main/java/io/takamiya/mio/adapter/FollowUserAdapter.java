package io.takamiya.mio.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.databinding.ItemFollowUserBinding;
import io.takamiya.mio.utils.RelativeTimeUtil;

public class FollowUserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_LOADING = 1;

    public static class FollowUser {
        public final int userId;
        public final String username;
        public final String nickname;
        public final String avatar;
        public final int memberLevel;
        public final String followTime;

        public FollowUser(int userId, String username, String nickname, String avatar,
                          int memberLevel, String followTime) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.avatar = avatar;
            this.memberLevel = memberLevel;
            this.followTime = followTime;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(FollowUser item);
    }

    private final List<FollowUser> list = new ArrayList<>();
    private boolean loadingMore;
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<FollowUser> incoming) {
        list.clear();
        if (incoming != null) list.addAll(incoming);
        notifyDataSetChanged();
    }

    public void appendData(List<FollowUser> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        int start = list.size();
        list.addAll(incoming);
        notifyItemRangeInserted(start, incoming.size());
    }

    public void setLoadingMore(boolean loading) {
        if (this.loadingMore == loading) return;
        int footerPos = list.size();
        this.loadingMore = loading;
        if (loading) {
            notifyItemInserted(footerPos);
        } else {
            notifyItemRemoved(footerPos);
        }
    }

    public void clearLoadingMore() {
        if (loadingMore) {
            setLoadingMore(false);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position >= list.size() ? TYPE_LOADING : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_LOADING) {
            View view = inflater.inflate(R.layout.item_loading_footer, parent, false);
            return new LoadingHolder(view);
        }
        ItemFollowUserBinding binding = ItemFollowUserBinding.inflate(inflater, parent, false);
        return new FollowViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadingHolder) return;
        ((FollowViewHolder) holder).bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size() + (loadingMore ? 1 : 0);
    }

    class FollowViewHolder extends RecyclerView.ViewHolder {
        private final ItemFollowUserBinding binding;

        FollowViewHolder(ItemFollowUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION && pos < list.size()) {
                    listener.onItemClick(list.get(pos));
                }
            });
        }

        void bind(FollowUser item) {
            Context ctx = binding.getRoot().getContext();
            binding.tvNickname.setText(item.nickname);
            binding.tvNickname.setTextColor(item.memberLevel > 0
                    ? ContextCompat.getColor(ctx, R.color.colorPrimary)
                    : ContextCompat.getColor(ctx, R.color.colorOnSecondaryContainer));
            binding.tvUsername.setText("@" + item.username);

            String time = item.followTime;
            if (time != null && !time.isEmpty()) {
                binding.tvFollowTime.setText("关注于 " + RelativeTimeUtil.toRelativeTimeSafe(time));
                binding.tvFollowTime.setVisibility(View.VISIBLE);
            } else {
                binding.tvFollowTime.setVisibility(View.GONE);
            }

            if (item.avatar != null && !item.avatar.isEmpty()) {
                Glide.with(ctx)
                        .load(item.avatar)
                        .placeholder(R.drawable.material_symbols_account_circle_outline)
                        .circleCrop()
                        .into(binding.ivAvatar);
            } else {
                binding.ivAvatar.setImageTintList(null);
                binding.ivAvatar.setImageResource(R.drawable.material_symbols_account_circle_outline);
            }
        }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(View itemView) {
            super(itemView);
        }
    }
}